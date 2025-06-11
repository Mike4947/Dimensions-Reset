package com.example.dimensionsreset;

// (All imports remain the same as the previous version)
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

public class DRCommand implements CommandExecutor, TabCompleter {

    // (All fields from the previous version are the same)
    private record PlayerState(Location location, GameMode gameMode) {}
    private final Map<UUID, PlayerState> previewingPlayers = new HashMap<>();
    private final DimensionsReset plugin;
    private CommandSender senderAwaitingConfirmation = null;
    private BukkitTask confirmationTask = null;
    private BukkitTask mainResetTask = null;
    private final List<BukkitTask> countdownTasks = new ArrayList<>();
    private Instant resetTime = null;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");

    public DRCommand(DimensionsReset plugin) { this.plugin = plugin; }

    // --- THIS IS THE ONLY METHOD WITH A MAJOR CHANGE ---
    private void resetTheEnd() {
        World endWorld = Bukkit.getWorld("the_end");
        if (endWorld == null) {
            plugin.getLogger().severe("RESET FAILED: The End dimension is not loaded or does not exist.");
            cleanupTasks();
            return;
        }

        // Announce and play sound first
        Bukkit.broadcastMessage(getMessage("messages.reset-now"));
        playSound(plugin.getConfig().getString("sounds.reset_success", "entity.wither.death"));

        // STAGE 1: Teleport all players and request the world unload.
        plugin.getLogger().info("[Reset Stage 1/3] Teleporting players and unloading The End...");
        Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        // Create a copy of the player list to avoid issues while iterating
        for (Player player : new ArrayList<>(endWorld.getPlayers())) {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + "The End is resetting! You have been teleported to safety.");
        }

        File worldFolder = endWorld.getWorldFolder();
        if (!Bukkit.unloadWorld(endWorld, false)) {
            plugin.getLogger().severe("RESET FAILED: Failed to unload The End! Another plugin may be preventing it.");
            cleanupTasks();
            return;
        }

        // STAGE 2: After a delay, delete the world files.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("[Reset Stage 2/3] Deleting world files for The End...");
            try {
                deleteDirectory(worldFolder);
            } catch (IOException e) {
                plugin.getLogger().severe("RESET FAILED: Could not delete The End world files. Check file permissions.");
                e.printStackTrace();
                cleanupTasks();
                return;
            }

            // STAGE 3: After another delay, recreate the world.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("[Reset Stage 3/3] Recreating The End dimension...");
                Bukkit.createWorld(new WorldCreator("the_end").environment(World.Environment.THE_END));
                plugin.getLogger().info("SUCCESS: The End dimension has been reset.");
                cleanupTasks();
            }, 20L); // 1 second delay for recreation

        }, 20L); // 1 second delay for deletion
    }
    
    /**
     * A helper method to recursively delete a directory and its contents.
     * This is more reliable than the previous Files.walk method.
     * @param directory The directory to delete.
     * @throws IOException if the deletion fails.
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            throw new IOException("Failed to delete file: " + file);
                        }
                    }
                }
            }
            if (!directory.delete()) {
                throw new IOException("Failed to delete directory: " + directory);
            }
        }
    }


    // --- (All other methods are identical to the previous version) ---
    // (onCommand, handlePreview, exitPreviewMode, etc. are all the same)
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "DimensionsReset v1.2.3 by Mike4947");
            sender.sendMessage(ChatColor.GRAY + "Use /dr <reset|cancel|confirm|status|reload|preview>");
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("confirm")) {
            handleConfirm(sender);
            return true;
        }
        switch (subCommand) {
            case "reset":
                if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return true; }
                handleReset(sender, args);
                break;
            case "cancel":
                if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return true; }
                handleCancel(sender);
                break;
            case "status":
                if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return true; }
                handleStatus(sender);
                break;
            case "reload":
                if (!sender.hasPermission("dimensionsreset.reload")) { noPerm(sender); return true; }
                handleReload(sender);
                break;
            case "preview":
                if (!sender.hasPermission("dimensionsreset.preview")) { noPerm(sender); return true; }
                handlePreview(sender, args);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /dr <reset|cancel|confirm|status|reload|preview>");
                break;
        }
        return true;
    }
    private void handlePreview(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dr preview <before|exit|seed>");
            return;
        }
        String previewAction = args[1].toLowerCase();
        if (previewAction.equals("seed")) {
            World overworld = Bukkit.getWorlds().get(0);
            long seed = overworld.getSeed();
            sender.sendMessage(getMessage("preview.seed_message").replace("%seed%", String.valueOf(seed)));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("preview.error_players_only"));
            return;
        }
        switch (previewAction) {
            case "before" -> enterPreviewMode(player);
            case "exit" -> exitPreviewMode(player, true);
            default -> sender.sendMessage(ChatColor.RED + "Unknown preview action. Use <before|exit|seed>");
        }
    }
    private void enterPreviewMode(Player player) {
        if (isPreviewing(player)) {
            player.sendMessage(getMessage("preview.error_already_in_preview"));
            return;
        }
        World endWorld = Bukkit.getWorld("the_end");
        if (endWorld == null) {
            player.sendMessage(getMessage("preview.error_end_not_found"));
            return;
        }
        PlayerState originalState = new PlayerState(player.getLocation(), player.getGameMode());
        previewingPlayers.put(player.getUniqueId(), originalState);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(new Location(endWorld, 0, 100, 0));
        player.sendMessage(getMessage("preview.enter_message"));
    }
    public void exitPreviewMode(Player player, boolean sendExitMessage) {
        PlayerState originalState = previewingPlayers.remove(player.getUniqueId());
        if (originalState == null) {
            if (sendExitMessage) player.sendMessage(getMessage("preview.error_not_in_preview"));
            return;
        }
        player.teleport(originalState.location());
        player.setGameMode(originalState.gameMode());
        if (sendExitMessage) player.sendMessage(getMessage("preview.exit_message"));
    }
    public boolean isPreviewing(Player player) {
        return previewingPlayers.containsKey(player.getUniqueId());
    }
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            final List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("dimensionsreset.admin")) { subCommands.addAll(Arrays.asList("reset", "cancel", "status", "confirm")); }
            if (sender.hasPermission("dimensionsreset.reload")) { subCommands.add("reload"); }
            if (sender.hasPermission("dimensionsreset.preview")) { subCommands.add("preview"); }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset")) {
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("the_end"), completions);
            } else if (args[0].equalsIgnoreCase("preview") && sender.hasPermission("dimensionsreset.preview")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("before", "exit", "seed"), completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("the_end")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("now", "1h", "30m", "10s"), completions);
            }
        }
        Collections.sort(completions);
        return completions;
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
        if (soundKey == null || soundKey.isEmpty()) { return; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
        }
    }
    private String getMessage(String path) {
        String message = plugin.getConfig().getString(path, "&cMessage not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    private void noPerm(CommandSender sender) {
        sender.sendMessage(getMessage("messages.error-no-permission"));
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
