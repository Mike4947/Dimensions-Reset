package com.example.dimensionsreset;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DimensionsReset extends JavaPlugin {

    private DRCommand drCommand;

    @Override
    public void onEnable() {
        // Save the default config.yml to the plugin's folder
        saveDefaultConfig();

        // Initialize and register the command executor
        // This simple line will now work perfectly because of the plugin.yml
        this.drCommand = new DRCommand(this);
        Objects.requireNonNull(getCommand("dr")).setExecutor(this.drCommand);

        getLogger().info("DimensionsReset has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (drCommand != null) {
            drCommand.cancelAllTasks();
            getLogger().info("Cancelled all scheduled dimension resets.");
        }
        getLogger().info("DimensionsReset has been disabled.");
    }
}
