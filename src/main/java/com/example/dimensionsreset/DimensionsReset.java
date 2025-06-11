package com.example.dimensionsreset;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;

public final class DimensionsReset extends JavaPlugin {

    private DRCommand drCommand;

    @Override
    public void onEnable() {
        // Save the default config.yml to the plugin's folder
        saveDefaultConfig();

        // Create the class that will handle all the command's logic
        this.drCommand = new DRCommand(this);

        // --- Programmatic Command Registration ---
        // This is the modern way to register commands, which avoids all startup errors.
        try {
            // Get the server's internal "command map" which holds all registered commands
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            // Create our command object with all its properties
            Command command = new org.bukkit.command.PluginCommand("dr", this);
            command.setAliases(List.of("dims", "dreset"));
            command.setDescription("Main command for DimensionsReset.");
            command.setUsage("/dr <reset|cancel|confirm|status|reload>");
            command.setPermission("dimensionsreset.admin"); // You can set a base permission here

            // Register our new command in the server's command map
            commandMap.register(this.getDescription().getName(), command);

            // Set our DRCommand class as the one that executes the command's logic
            command.setExecutor(this.drCommand);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().severe("Could not register command! Your server version may be incompatible.");
            e.printStackTrace();
            return; // Stop the plugin from enabling if command registration fails
        }

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
