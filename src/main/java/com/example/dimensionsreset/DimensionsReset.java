package com.example.dimensionsreset;

import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

public final class DimensionsReset extends JavaPlugin {

    private DRCommand commandHandler;
    private DataManager dataManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize all our managers
        this.dataManager = new DataManager(this);
        // --- CORRECTED INITIALIZATION ---
        this.guiManager = new GUIManager(this.dataManager); // Pass the DataManager to the GUI Manager
        this.commandHandler = new DRCommand(this, dataManager, guiManager);

        // Register commands
        try {
            final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("dr", this);
            command.setAliases(List.of("dims", "dreset"));
            command.setDescription("Main command for DimensionsReset.");
            command.setPermission("dimensionsreset.admin");
            commandMap.register(this.getDescription().getName(), command);
            command.setExecutor(this.commandHandler);
            command.setTabCompleter(this.commandHandler);
        } catch (Exception e) {
            getLogger().severe("Could not register command!");
            e.printStackTrace();
            return;
        }

        // Register ALL event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this.commandHandler), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this.guiManager), this);

        // Start the automated scheduler task
        new SchedulerTask(this, dataManager, commandHandler).runTaskTimerAsynchronously(this, 0L, 1200L);

        getLogger().info("DimensionsReset has been enabled with GUI features!");
    }

    @Override
    public void onDisable() {
        if (commandHandler != null) {
            commandHandler.cancelAllDimensionResets();
            getLogger().info("Cancelled all scheduled dimension resets.");
        }
        getLogger().info("DimensionsReset has been disabled.");
    }

    public DRCommand getCommandHandler() {
        return commandHandler;
    }
}