package com.example.dimensionsreset;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DRCommand implements CommandExecutor {

    private final DimensionsReset plugin;
    private BukkitTask resetTask = null;

    // A pattern to parse time strings like "1h30m15s"
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");

    public DRCommand(DimensionsReset plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Permission Check
        if (!sender.hasPermission("dimensionsreset.admin")) {
            sender.sendMessage(getMessage("messages.error-no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0];
        String dimensionName = args[1];

        // We only support 'the_end' for now
        if (!dimensionName.equalsIgnoreCase("the_end")) {
            sender.sendMessage(getMessage("messages.error-invalid-dimension").replace("%dimension%", dimensionName));
            return true;
        }

        if (subCommand.equalsIgnoreCase("reset")) {
            handleReset(sender, args);
        } else if (subCommand.equalsIgnoreCase("cancel")) {
            handleCancel(sender);
        } else {
            sendUsage(sender);
        }

        return true;
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }

        if (resetTask != null) {
            sender.sendMessage(ChatColor.RED + "A reset is already scheduled! Use '/dr cancel the_end' first.");
            return;
        }

        String timeArg = args[2];

        if (timeArg.equalsIgnoreCase("now")) {
            resetEndDimension();
        } else {
            try {
                long timeInSeconds = parseTime(timeArg);
                scheduleReset(timeInSeconds);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(getMessage("messages.error-invalid-time"));
            }
        }
    }

    private void handleCancel(CommandSender sender) {
        if (cancelScheduledReset()) {
            Bukkit.broadcastMessage(getMessage("messages.reset-cancelled").replace("%dimension%", "End"));
        } else {
            sender.sendMessage(ChatColor.RED + "There is no reset scheduled to cancel.");
        }
    }

    private void scheduleReset(long timeInSeconds) {
        // Convert seconds to server ticks (20 ticks = 1 second)
        long timeInTicks = timeInSeconds * 20L;

        String scheduledMessage = getMessage("messages.reset-scheduled")
                .replace("%dimension%", "End")
                .replace("%time%", formatTime(timeInSeconds));

        Bukkit.broadcastMessage(scheduledMessage);

        resetTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::resetEndDimension, timeInTicks);
    }

    private void resetEndDimension() {
        World endWorld = Bukkit.getWorld("the_end");
        if (endWorld == null) {
            plugin.getLogger().warning("The End dimension is not loaded or does not exist. Cannot reset.");
            return;
        }

        // Broadcast reset message
        Bukkit.broadcastMessage(getMessage("messages.reset-now").replace("%dimension%", "End"));

        // Get the main overworld to teleport players to
        World overworld = Bukkit.getWorlds().get(0);
        Location spawnLocation = overworld.getSpawnLocation();

        // Safely teleport all players out of The End
        for (Player player : endWorld.getPlayers()) {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + "The End is resetting! You have been teleported to safety.");
        }

        // Unload the world. Bukkit handles moving remaining entities.
        if (!Bukkit.unloadWorld(endWorld, false)) {
            plugin.getLogger().severe("Failed to unload The End dimension! Is another plugin keeping it loaded?");
            return;
        }

        // Delete the world folder
        try {
            Path worldPath = endWorld.getWorldFolder().toPath();
            Files.walk(worldPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            plugin.getLogger().severe("An error occurred while deleting the End dimension files.");
            e.printStackTrace();
            return;
        }

        // Recreate the world with the same seed and environment
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Recreating The End dimension...");
            WorldCreator creator = new WorldCreator("the_end").environment(World.Environment.THE_END);
            Bukkit.createWorld(creator);
            plugin.getLogger().info("The End dimension has been successfully reset.");
            resetTask = null; // Mark task as complete
        }, 20L); // Wait 1 second before recreating to ensure files are released
    }

    public boolean cancelScheduledReset() {
        if (resetTask != null) {
            if (!resetTask.isCancelled()) {
                resetTask.cancel();
            }
            resetTask = null;
            return true;
        }
        return false;
    }

    private long parseTime(String timeString) throws IllegalArgumentException {
        long totalSeconds = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        boolean matchFound = false;

        while (matcher.find()) {
            matchFound = true;
            int amount = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);

            switch (unit) {
                case 'h':
                    totalSeconds += TimeUnit.HOURS.toSeconds(amount);
                    break;
                case 'm':
                    totalSeconds += TimeUnit.MINUTES.toSeconds(amount);
                    break;
                case 's':
                    totalSeconds += amount;
                    break;
            }
        }

        if (!matchFound) {
            throw new IllegalArgumentException("Invalid time format");
        }
        return totalSeconds;
    }

    private String formatTime(long totalSeconds) {
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        StringBuilder formattedTime = new StringBuilder();
        if (hours > 0) formattedTime.append(hours).append("h ");
        if (minutes > 0) formattedTime.append(minutes).append("m ");
        if (seconds > 0 || formattedTime.length() == 0) formattedTime.append(seconds).append("s");

        return formattedTime.toString().trim();
    }

    private String getMessage(String path) {
        String message = plugin.getConfig().getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void sendUsage(CommandSender sender) {
        List<String> helpMessages = plugin.getConfig().getStringList("messages.usage-help");
        for (String line : helpMessages) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }
}