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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DRCommand implements CommandExecutor {

    private final DimensionsReset plugin;

    private CommandSender senderAwaitingConfirmation = null;
    private BukkitTask confirmationTask = null;
    private BukkitTask mainResetTask = null;
    private final List<BukkitTask> countdownTasks = new ArrayList<>();
    private Instant resetTime = null;

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");

    public DRCommand(DimensionsReset plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "DimensionsReset v2.0 by Mike4947");
            sender.sendMessage(ChatColor.GRAY + "Use /dr <reset|cancel|confirm|status|reload>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("confirm")) {
            handleConfirm(sender);
            return true;
        }

        switch (subCommand) {
            case "reset":
                if (!sender.hasPermission("dimensionsreset.admin")) {
                    sender.sendMessage(getMessage("messages.error-no-permission"));
                    return true;
                }
                handleReset(sender, args);
                break;
            case "cancel":
                if (!sender.hasPermission("dimensionsreset.admin")) {
                    sender.sendMessage(getMessage("messages.error-no-permission"));
                    return true;
                }
                handleCancel(sender);
                break;
            case "status":
                 if (!sender.hasPermission("dimensionsreset.admin")) {
                    sender.sendMessage(getMessage("messages.error-no-permission"));
                    return true;
                }
                handleStatus(sender);
                break;
            case "reload":
                if (!sender.hasPermission("dimensionsreset.reload")) {
                    sender.sendMessage(getMessage("messages.error-no-permission"));
                    return true;
                }
                handleReload(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /dr <reset|cancel|confirm|status|reload>");
                break;
        }
        return true;
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("the_end")) {
            sender.sendMessage(ChatColor.RED + "Usage: /dr reset the_end <time|now>");
            return;
        }
        if (mainResetTask != null) {
            sender.sendMessage(ChatColor.RED + "A reset is already scheduled! Use '/dr cancel' first.");
            return;
        }

        String timeArg = args[2];

        if (timeArg.equalsIgnoreCase("now")) {
            senderAwaitingConfirmation = sender;
            sender.sendMessage(getMessage("confirmation.required_message"));
            confirmationTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (senderAwaitingConfirmation != null) {
                    sender.sendMessage(getMessage("confirmation.expired_message"));
                    senderAwaitingConfirmation = null;
                }
            }, 300L); 
        } else {
            try {
                long timeInSeconds = parseTime(timeArg);
                scheduleReset(timeInSeconds);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(getMessage("messages.error-invalid-time"));
            }
        }
    }

    private void handleConfirm(CommandSender sender) {
        if (senderAwaitingConfirmation == null || senderAwaitingConfirmation != sender) {
            sender.sendMessage(getMessage("confirmation.not_required_message"));
            return;
        }
        sender.sendMessage(getMessage("confirmation.success_message"));
        senderAwaitingConfirmation = null;
        if (confirmationTask != null) confirmationTask.cancel();
        
        resetTheEnd();
    }
    
    private void handleCancel(CommandSender sender) {
        if (mainResetTask == null) {
            sender.sendMessage(ChatColor.RED + "There is no reset scheduled to cancel.");
            return;
        }
        
        cancelAllTasks();
        Bukkit.broadcastMessage(getMessage("messages.reset-cancelled"));
        sender.sendMessage(ChatColor.GREEN + "Scheduled reset has been successfully cancelled.");
    }

    private void handleStatus(CommandSender sender) {
        if (mainResetTask == null || resetTime == null) {
            sender.sendMessage(getMessage("messages.status-not-scheduled"));
            return;
        }
        
        long remainingSeconds = Duration.between(Instant.now(), resetTime).getSeconds();
        if (remainingSeconds < 0) remainingSeconds = 0;
        
        sender.sendMessage(getMessage("messages.status-scheduled").replace("%time%", formatTime(remainingSeconds)));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(getMessage("messages.config-reloaded"));
    }

    private void scheduleReset(long timeInSeconds) {
        long timeInTicks = timeInSeconds * 20L;
        resetTime = Instant.now().plusSeconds(timeInSeconds);

        String scheduledMessage = getMessage("messages.reset-scheduled").replace("%time%", formatTime(timeInSeconds));
        Bukkit.broadcastMessage(scheduledMessage);
        playSound(plugin.getConfig().getString("sounds.reset_scheduled", "entity.player.levelup"));

        mainResetTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::resetTheEnd, timeInTicks);

        List<Integer> countdownTimes = plugin.getConfig().getIntegerList("countdown_broadcast_times");
        for (int countdownSeconds : countdownTimes) {
            if (timeInSeconds >= countdownSeconds) {
                long delayTicks = (timeInSeconds - countdownSeconds) * 20L;
                BukkitTask countdownTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    String countdownMessage = getMessage("messages.reset-scheduled").replace("%time%", formatTime(countdownSeconds));
                    Bukkit.broadcastMessage(countdownMessage);
                    playSound(plugin.getConfig().getString("sounds.countdown_tick", "block.note_block.hat"));
                }, delayTicks);
                countdownTasks.add(countdownTask);
            }
        }
    }

    private void resetTheEnd() {
        Bukkit.broadcastMessage(getMessage("messages.reset-now"));
        playSound(plugin.getConfig().getString("sounds.reset_success", "entity.wither.death"));
        
        World endWorld = Bukkit.getWorld("the_end");
        if (endWorld == null) {
            plugin.getLogger().warning("The End dimension is not loaded. Cannot reset.");
            cleanupTasks();
            return;
        }

        Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : endWorld.getPlayers()) {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + "The End is resetting! You have been teleported to safety.");
        }

        if (!Bukkit.unloadWorld(endWorld, false)) {
            plugin.getLogger().severe("Failed to unload The End!");
            cleanupTasks();
            return;
        }

        try {
            Files.walk(endWorld.getWorldFolder().toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            plugin.getLogger().severe("An error occurred while deleting The End files.");
            e.printStackTrace();
            cleanupTasks();
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Recreating The End dimension...");
            Bukkit.createWorld(new WorldCreator("the_end").environment(World.Environment.THE_END));
            plugin.getLogger().info("The End dimension has been successfully reset.");
        }, 20L);

        cleanupTasks();
    }

    /** This is now public **/
    public void cancelAllTasks() {
        if (mainResetTask != null) mainResetTask.cancel();
        for (BukkitTask task : countdownTasks) task.cancel();
        cleanupTasks();
    }

    private void cleanupTasks() {
        mainResetTask = null;
        resetTime = null;
        countdownTasks.clear();
    }
    
    private void playSound(String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
        }
    }
    
    private String getMessage(String path) {
        String message = plugin.getConfig().getString(path, "&cMessage not found in config.yml: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private long parseTime(String timeString) {
        long totalSeconds = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        boolean matchFound = false;
        while (matcher.find()) {
            matchFound = true;
            int amount = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 'h': totalSeconds += TimeUnit.HOURS.toSeconds(amount); break;
                case 'm': totalSeconds += TimeUnit.MINUTES.toSeconds(amount); break;
                case 's': totalSeconds += amount; break;
            }
        }
        if (!matchFound) throw new IllegalArgumentException("Invalid time format");
        return totalSeconds;
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder formattedTime = new StringBuilder();
        if (hours > 0) formattedTime.append(hours).append("h ");
        if (minutes > 0) formattedTime.append(minutes).append("m ");
        if (seconds > 0 || formattedTime.length() == 0) formattedTime.append(seconds).append("s");
        return formattedTime.toString().trim();
    }
}
