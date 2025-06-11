package com.example.dimensionsreset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.*;
import java.util.Locale;
import java.util.Map;
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
        // First, check if the whole system is enabled.
        if (!plugin.getConfig().getBoolean("automated_resets.enabled", false)) {
            return;
        }

        // Don't run if a manual reset is already in progress.
        if (commandHandler.isResetScheduled()) {
            return;
        }

        ConfigurationSection schedulesSection = plugin.getConfig().getConfigurationSection("automated_resets.schedules");
        if (schedulesSection == null) return;

        long now = Instant.now().getEpochSecond();

        for (String key : schedulesSection.getKeys(false)) {
            ConfigurationSection schedule = schedulesSection.getConfigurationSection(key);
            if (schedule == null) continue;

            String scheduleId = schedule.getString("id");
            if (scheduleId == null) {
                plugin.getLogger().warning("Automated reset schedule '" + key + "' is missing a unique 'id'. Skipping.");
                continue;
            }

            String dimension = schedule.getString("dimension");
            String announcementTimeStr = schedule.getString("announcement_time", "1h");
            long announcementSeconds = commandHandler.parseTime(announcementTimeStr);

            String scheduleTypeStr = schedule.getString("schedule.type");
            String scheduleValue = schedule.getString("schedule.value");

            long nextResetTime = getNextResetTime(scheduleId, scheduleTypeStr, scheduleValue);

            if (nextResetTime > 0 && now >= (nextResetTime - announcementSeconds)) {
                plugin.getLogger().info("Automated reset for '" + dimension + "' ('" + scheduleId + "') is due. Triggering scheduled reset.");
                // Use the main command handler to start the reset process
                commandHandler.scheduleReset(dimension, announcementSeconds, scheduleId);
                // Break after triggering one to avoid conflicts. The task will run again next minute.
                break;
            }
        }
    }

    private long getNextResetTime(String scheduleId, String type, String value) {
        if (type == null || value == null) return -1;

        long lastReset = dataManager.getLastResetTime(scheduleId);

        if (type.equalsIgnoreCase("INTERVAL")) {
            long intervalSeconds = commandHandler.parseTime(value);
            if (lastReset == 0) { // If it's never run, schedule it for the future
                return Instant.now().getEpochSecond() + intervalSeconds;
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

                if (now.isAfter(nextOccurrence)) {
                    nextOccurrence = nextOccurrence.plusWeeks(1);
                }

                // If it already reset since the last calculated occurrence, find the next one
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