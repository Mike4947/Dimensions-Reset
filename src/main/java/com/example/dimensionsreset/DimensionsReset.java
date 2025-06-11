package com.example.dimensionsreset;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DimensionsReset extends JavaPlugin {

    private DRCommand drCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.drCommand = new DRCommand(this);
        Objects.requireNonNull(getCommand("dr")).setExecutor(drCommand);

        getLogger().info("DimensionsReset has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Cancel any pending tasks on shutdown to prevent issues
        if (drCommand != null) {
            drCommand.cancelAllTasks();
            getLogger().info("Cancelled all scheduled dimension resets.");
        }
        getLogger().info("DimensionsReset has been disabled.");
    }
}
