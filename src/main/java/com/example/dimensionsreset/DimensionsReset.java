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

    // --- NEW: Manager instances ---
    private DRCommand commandHandler;
    private RegionManager regionManager;
    private WandManager wandManager;
    private ResetHandler resetHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // --- NEW: Initialize all our manager classes ---
        this.regionManager = new RegionManager(this);
        this.resetHandler = new ResetHandler(this);
        // The command handler now needs access to our other managers
        this.commandHandler = new DRCommand(this, regionManager, resetHandler);
        this.wandManager = new WandManager(this.commandHandler);

        // Register commands (same as before)
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
            command.setExecutor(this.commandHandler);
            command.setTabCompleter(this.commandHandler);
        } catch (Exception e) {
            getLogger().severe("Could not register command!");
            e.printStackTrace();
            return;
        }

        // Register event listeners for the wand and player quit events
        getServer().getPluginManager().registerEvents(this.wandManager, this);
        // getServer().getPluginManager().registerEvents(new PlayerListener(this.commandHandler), this); // We can re-add this if needed later

        getLogger().info("DimensionsReset has been enabled successfully with Wilderness Reset features!");
    }

    // This method is for the command handler to use
    public DRCommand getCommandHandler() {
        return commandHandler;
    }

    // onDisable is unchanged
    @Override
    public void onDisable() {
        getLogger().info("DimensionsReset has been disabled.");
    }
}
