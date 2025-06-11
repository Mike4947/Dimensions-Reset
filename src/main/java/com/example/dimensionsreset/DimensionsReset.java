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

    // These fields will hold the instances of our other classes
    private DRCommand drCommand;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        // First, save the default config file
        saveDefaultConfig();

        // Second, initialize our new Data Manager
        this.dataManager = new DataManager(this);

        // Third, create the command handler, passing BOTH the plugin and dataManager
        this.drCommand = new DRCommand(this, this.dataManager);

        // Command Registration (this part is unchanged)
        try {
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());

            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("dr", this);

            command.setAliases(List.of("dims", "dreset"));
            command.setDescription("Main command for DimensionsReset.");
            command.setUsage("/dr <subcommand>");
            command.setPermission("dimensionsreset.admin");
            
            commandMap.register(this.getDescription().getName(), command);
            
            command.setExecutor(this.drCommand);
            command.setTabCompleter(this.drCommand);

        } catch (Exception e) {
            getLogger().severe("Could not register command! Your server version may be incompatible.");
            e.printStackTrace();
            return;
        }
        
        // Register the event listener to handle players quitting in preview mode
        getServer().getPluginManager().registerEvents(new PlayerListener(this.drCommand), this);

        // Start our new Scheduler Task and pass it all the required components
        // It will run every minute (1200 ticks = 60 seconds * 20 ticks/sec)
        new SchedulerTask(this, dataManager, drCommand).runTaskTimer(this, 0L, 1200L);
        
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
