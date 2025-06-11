package com.example.dimensionsreset;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DimensionsReset extends JavaPlugin {

    private DRCommand drCommand;

    /**
     * The onLoad method runs BEFORE onEnable.
     * This is the new, correct place to register commands for modern Paper servers.
     */
    @Override
    public void onLoad() {
        // Initialize and register the command executor here
        this.drCommand = new DRCommand(this);
        Objects.requireNonNull(getCommand("dr")).setExecutor(this.drCommand);
        getLogger().info("DimensionsReset commands have been registered.");
    }

    @Override
    public void onEnable() {
        // Save the default config.yml to the plugin's folder
        saveDefaultConfig();

        // The main startup message
        getLogger().info("DimensionsReset has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        // This will now work correctly because drCommand was initialized in onLoad
        if (drCommand != null) {
            drCommand.cancelAllTasks();
            getLogger().info("Cancelled all scheduled dimension resets.");
        }
        getLogger().info("DimensionsReset has been disabled.");
    }
}
