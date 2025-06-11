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

    private DRCommand drCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.drCommand = new DRCommand(this);

        // --- Programmatic Command Registration (Corrected Version) ---
        try {
            // Get the server's internal command map
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            // Create a new command object using reflection, as the constructor is not public
            // This is the standard way to accomplish this.
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("dr", this);

            // Set the command's properties
            command.setAliases(List.of("dims", "dreset"));
            command.setDescription("Main command for DimensionsReset.");
            command.setUsage("/dr <reset|cancel|confirm|status|reload>");
            command.setPermission("dimensionsreset.admin");

            // Register our new command
            commandMap.register(this.getDescription().getName(), command);

            // Set our DRCommand class as the executor for this command
            command.setExecutor(this.drCommand);

        } catch (Exception e) {
            getLogger().severe("Could not register command! Your server version may be incompatible.");
            e.printStackTrace();
            return;
        }

        getLogger().info("DimensionsReset has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (drCommand != null) {
            drCommand.cancelAllTasks();
            getLogger().info("Cancelled all scheduled dimension resets.");
        }
        getLogger().info("DimensionsReset has been disabled.");
    }
}
