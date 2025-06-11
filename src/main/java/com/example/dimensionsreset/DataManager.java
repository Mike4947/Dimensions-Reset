package com.example.dimensionsreset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DataManager {

    private final DimensionsReset plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;

    public DataManager(DimensionsReset plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
    }

    public void reloadConfig() {
        if (this.configFile == null) {
            this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public FileConfiguration getConfig() {
        if (this.dataConfig == null) {
            reloadConfig();
        }
        return this.dataConfig;
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

    // --- Custom Methods for our data ---

    /**
     * Gets the last reset time for a given schedule ID.
     * @param scheduleId The unique ID of the schedule.
     * @return The timestamp of the last reset, or 0 if never run.
     */
    // ▼ THIS IS THE CORRECTED LINE ▼
    public long getLastResetTime(String scheduleId) {
        return getConfig().getLong("last-reset-times." + scheduleId, 0L);
    }

    /**
     * Sets the last reset time for a given schedule ID.
     * @param scheduleId The unique ID of the schedule.
     * @param timestamp The current timestamp to save.
     */
    public void setLastResetTime(String scheduleId, long timestamp) {
        getConfig().set("last-reset-times." + scheduleId, timestamp);
        saveConfig();
    }
}
