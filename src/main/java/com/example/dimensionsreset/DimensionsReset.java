package com.example.dimensionsreset;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

public final class DimensionsReset extends JavaPlugin {

    // This field will hold the instance of our command handler
    private DRCommand drCommand;

    @Override
    public void onEnable() {
        // Save the default config.yml to the plugin's folder
        saveDefaultConfig();

        // Create the class that will handle all the command's logic
        this.drCommand = new DRCommand(this);

        // --- Programmatic Command Registration ---
        try {
            // Get the server's internal "command map"
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            // Create our command object using reflection
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("dr", this);

            // Set the command's properties
            command.setAliases(List.of("dims", "dreset"));
            command.setDescription("Main command for DimensionsReset.");
            command.setUsage("/dr <subcommand>");
            command.setPermission("dimensionsreset.admin");

            // Register our new command in the server's command map
            commandMap.register(this.getDescription().getName(), command);

            // Set our DRCommand class as the one that executes the command's logic
            command.setExecutor(this.drCommand);
            // Set our DRCommand class to also handle tab completion
            command.setTabCompleter(this.drCommand);

        } catch (Exception e) {
            getLogger().severe("Could not register command! Your server version may be incompatible.");
            e.printStackTrace();
            return;
        }

        // --- Register the new event listener ---
        // This handles players logging out while in preview mode.
        getServer().getPluginManager().registerEvents(new PlayerListener(this.drCommand), this);


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
