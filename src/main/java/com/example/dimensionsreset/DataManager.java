package com.example.dimensionsreset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages all persistent data for the plugin, such as reset history.
 * Data is stored in data.yml.
 */
public class DataManager {

    private final DimensionsReset plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;

    public DataManager(DimensionsReset plugin) {
        this.plugin = plugin;
        // This ensures data.yml is created on first load
        saveDefaultConfig();
    }

    public FileConfiguration getConfig() {
        if (this.dataConfig == null) {
            reloadConfig();
        }
        return this.dataConfig;
    }

    public void reloadConfig() {
        if (this.configFile == null) {
            this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public void saveConfig() {
        if (this.dataConfig == null || this.configFile == null) {
            return;
        }
        try {
            this.getConfig().save(this.configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data to " + this.configFile);
            e.printStackTrace();
        }
    }

    public void saveDefaultConfig() {
        if (this.configFile == null) {
            this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
        }
        if (!this.configFile.exists()) {
            this.plugin.saveResource("data.yml", false);
        }
    }

    // --- Methods for Reset History & Analytics ---

    public void addResetToHistory(String dimensionName) {
        String path = "dimension-reset-history." + dimensionName.toLowerCase();
        List<Long> history = getConfig().getLongList(path);
        history.add(Instant.now().getEpochSecond());
        getConfig().set(path, history);
        saveConfig();
    }

    public List<Long> getResetHistory(String dimensionName) {
        return getConfig().getLongList("dimension-reset-history." + dimensionName.toLowerCase());
    }

    public long getLatestResetTimeFromHistory(String dimensionName) {
        List<Long> history = getResetHistory(dimensionName);
        if (history.isEmpty()) {
            return 0L;
        }
        return Collections.max(history);
    }

    // --- CORRECTED: Missing methods for the Automated Scheduler ---

    /**
     * Gets the last reset time for a specific automated schedule ID.
     * @param scheduleId The unique ID of the schedule from config.yml.
     * @return The timestamp of the last reset for that schedule, or 0 if never run.
     */
    public long getLastResetTime(String scheduleId) {
        return getConfig().getLong("automated-schedules-last-run." + scheduleId, 0L);
    }

    /**
     * Sets the last reset time for a specific automated schedule ID.
     * @param scheduleId The unique ID of the schedule.
     * @param timestamp The current timestamp to save.
     */
    public void setLastResetTime(String scheduleId, long timestamp) {
        getConfig().set("automated-schedules-last-run." + scheduleId, timestamp);
        saveConfig();
    }
}