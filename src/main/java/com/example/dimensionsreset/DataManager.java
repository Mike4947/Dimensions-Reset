package com.example.dimensionsreset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

// The class now officially implements the contract.
public class DataManager implements SchedulerDataHandler {

    private final DimensionsReset plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;

    public DataManager(DimensionsReset plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
    }

    // --- Methods from the SchedulerDataHandler "Contract" ---
    // These are now GUARANTEED to exist.
    @Override
    public long getLastResetTimeForSchedule(String scheduleId) {
        return getConfig().getLong("automated-schedules-last-run." + scheduleId, 0L);
    }

    @Override
    public void setLastResetTimeForSchedule(String scheduleId, long timestamp) {
        getConfig().set("automated-schedules-last-run." + scheduleId, timestamp);
        saveConfig();
    }


    // --- Other Data Methods for Analytics ---
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
        return history.isEmpty() ? 0L : Collections.max(history);
    }

    // --- Core File Handling Methods ---
    public FileConfiguration getConfig() {
        if (this.dataConfig == null) reloadConfig();
        return this.dataConfig;
    }
    public void reloadConfig() {
        if (this.configFile == null) this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(this.configFile);
    }
    public void saveConfig() {
        try {
            this.getConfig().save(this.configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data to " + this.configFile);
        }
    }
    public void saveDefaultConfig() {
        if (this.configFile == null) this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
        if (!this.configFile.exists()) this.plugin.saveResource("data.yml", false);
    }
}