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
    private RegionManager regionManager;
    private WandManager wandManager;
    private ResetHandler resetHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize all our manager and handler classes in the correct order
        this.dataManager = new DataManager(this);
        this.regionManager = new RegionManager(this);
        this.resetHandler = new ResetHandler(this);
        this.wandManager = new WandManager(this); // WandManager is created here
        // The command handler now gets the single, correct instance of every manager
        this.commandHandler = new DRCommand(this, dataManager, regionManager, resetHandler, wandManager);

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

        // Register event listeners for both the wand and player portal/quit events
        getServer().getPluginManager().registerEvents(this.wandManager, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this.commandHandler), this);

        // Start the automated scheduler task
        new SchedulerTask(this, dataManager, commandHandler).runTaskTimerAsynchronously(this, 0L, 1200L);

        getLogger().info("DimensionsReset has been enabled with ALL features!");
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
