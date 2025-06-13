package com.example.dimensionsreset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.*;
import java.util.Locale;
import java.util.Objects;

public class SchedulerTask extends BukkitRunnable {

    private final DimensionsReset plugin;
    private final DataManager dataManager;
    private final DRCommand commandHandler;

    public SchedulerTask(DimensionsReset plugin, DataManager dataManager, DRCommand commandHandler) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.commandHandler = commandHandler;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("automated_resets.enabled", false)) {
            return;
        }

        ConfigurationSection schedulesSection = plugin.getConfig().getConfigurationSection("automated_resets.schedules");
        if (schedulesSection == null) return;

        long now = Instant.now().getEpochSecond();

        // Loop through each schedule defined in the config
        for (String key : schedulesSection.getKeys(false)) {
            ConfigurationSection schedule = schedulesSection.getConfigurationSection(key);
            if (schedule == null) continue;

            String dimension = schedule.getString("dimension");
            if (dimension == null || dimension.isBlank()) {
                plugin.getLogger().warning("Automated reset schedule '" + key + "' is missing a 'dimension'. Skipping.");
                continue;
            }

            // --- THIS IS THE CORRECTED LOGIC ---
            // Before trying to schedule a reset for this dimension, first check if one is already running.
            // If so, we 'continue' to the next schedule in the config.
            if (commandHandler.isResetScheduled(dimension)) {
                continue;
            }
            // --- End of fix ---

            String scheduleId = schedule.getString("id");
            if (scheduleId == null) {
                plugin.getLogger().warning("Automated reset schedule '" + key + "' is missing a unique 'id'. Skipping.");
                continue;
            }

            String announcementTimeStr = schedule.getString("announcement_time", "1h");
            long announcementSeconds = commandHandler.parseTime(announcementTimeStr);

            String scheduleTypeStr = schedule.getString("schedule.type");
            String scheduleValue = schedule.getString("schedule.value");

            long nextResetTime = getNextResetTime(scheduleId, scheduleTypeStr, scheduleValue);

            if (nextResetTime > 0 && now >= (nextResetTime - announcementSeconds)) {
                plugin.getLogger().info("Automated reset for '" + dimension + "' ('" + scheduleId + "') is due. Triggering scheduled reset.");
                commandHandler.scheduleReset(dimension, announcementSeconds, scheduleId);
                // Break after triggering one to avoid potential conflicts within the same run.
                break;
            }
        }
    }

    private long getNextResetTime(String scheduleId, String type, String value) {
        if (type == null || value == null) return -1;

        long lastReset = dataManager.getLastResetTime(scheduleId);

        if (type.equalsIgnoreCase("INTERVAL")) {
            long intervalSeconds = commandHandler.parseTime(value);
            if (lastReset == 0) { // If it's never run, the "last reset" time is now, for calculation purposes.
                lastReset = Instant.now().getEpochSecond();
                dataManager.setLastResetTime(scheduleId, lastReset);
            }
            return lastReset + intervalSeconds;
        }
        else if (type.equalsIgnoreCase("DAY_AND_TIME")) {
            try {
                String[] parts = value.split("-");
                DayOfWeek day = DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT));
                LocalTime time = LocalTime.parse(parts[1]);

                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                ZonedDateTime nextOccurrence = now.with(day).with(time);

                // If the calculated time is in the past for this week, get the occurrence for next week.
                if (now.isAfter(nextOccurrence)) {
                    nextOccurrence = nextOccurrence.plusWeeks(1);
                }

                // If the last reset happened AFTER the most recent valid reset time, it means we need to find the NEXT one.
                if (lastReset >= nextOccurrence.toEpochSecond()) {
                    nextOccurrence = nextOccurrence.plusWeeks(1);
                }

                return nextOccurrence.toEpochSecond();
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid DAY_AND_TIME format for schedule '" + scheduleId + "'. Expected format: DAYOFWEEK-HH:MM (e.g., FRIDAY-20:00)");
                return -1;
            }
        }
        return -1;
    }
}