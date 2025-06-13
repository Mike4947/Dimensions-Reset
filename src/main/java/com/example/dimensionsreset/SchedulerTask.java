package com.example.dimensionsreset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.*;
import java.util.Locale;

public class SchedulerTask extends BukkitRunnable {

    private final DimensionsReset plugin;
    private final SchedulerDataHandler dataHandler; // Uses the contract, not the concrete class
    private final DRCommand commandHandler;

    public SchedulerTask(DimensionsReset plugin, SchedulerDataHandler dataHandler, DRCommand commandHandler) {
        this.plugin = plugin;
        this.dataHandler = dataHandler;
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

        for (String key : schedulesSection.getKeys(false)) {
            ConfigurationSection schedule = schedulesSection.getConfigurationSection(key);
            if (schedule == null) continue;

            String dimension = schedule.getString("dimension");
            if (dimension == null || dimension.isBlank()) continue;

            if (commandHandler.isResetScheduled(dimension)) {
                continue;
            }

            String scheduleId = schedule.getString("id");
            if (scheduleId == null) continue;

            String announcementTimeStr = schedule.getString("announcement_time", "1h");
            long announcementSeconds = commandHandler.parseTime(announcementTimeStr);
            String scheduleTypeStr = schedule.getString("schedule.type");
            String scheduleValue = schedule.getString("schedule.value");

            long nextResetTime = getNextResetTime(scheduleId, scheduleTypeStr, scheduleValue);

            if (nextResetTime > 0 && now >= (nextResetTime - announcementSeconds)) {
                plugin.getLogger().info("Automated reset for '" + dimension + "' ('" + scheduleId + "') is due. Triggering scheduled reset.");
                commandHandler.scheduleReset(dimension, announcementSeconds, scheduleId);
                break;
            }
        }
    }

    private long getNextResetTime(String scheduleId, String type, String value) {
        if (type == null || value == null) return -1;

        // This call is now guaranteed to work because of the contract
        long lastReset = dataHandler.getLastResetTimeForSchedule(scheduleId);

        if (type.equalsIgnoreCase("INTERVAL")) {
            long intervalSeconds = commandHandler.parseTime(value);
            if (lastReset == 0) {
                lastReset = Instant.now().getEpochSecond();
                // This call is also guaranteed to work
                dataHandler.setLastResetTimeForSchedule(scheduleId, lastReset);
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
                if (lastReset >= nextOccurrence.toEpochSecond()) {
                    nextOccurrence = nextOccurrence.plusWeeks(1);
                }
                return nextOccurrence.toEpochSecond();
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }
}