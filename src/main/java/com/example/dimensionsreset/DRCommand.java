package com.example.dimensionsreset;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DRCommand implements CommandExecutor, TabCompleter, PortalManager {

    private record PlayerState(Location location, GameMode gameMode) {}
    private final Map<UUID, PlayerState> previewingPlayers = new HashMap<>();
    private final DimensionsReset plugin;
    private final DataManager dataManager;
    private final GUIManager guiManager;
    private final Map<String, BukkitTask> scheduledResetTasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> scheduledCountdownTasks = new HashMap<>();
    private final Map<String, Instant> scheduledResetTimes = new HashMap<>();
    private String activeScheduleId = null;
    private final Map<CommandSender, List<String>> manualConfirmMap = new HashMap<>();
    private BukkitTask manualConfirmTask = null;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");
    private final Set<String> justResetDimensions = new HashSet<>();

    public DRCommand(DimensionsReset plugin, DataManager dataManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.guiManager = guiManager;
    }

    // --- This is the only method with a major change ---
    private void resetDimension(String dimensionName, @Nullable String scheduleId) {
        World worldToReset = findWorld(dimensionName);
        if (worldToReset == null) {
            plugin.getLogger().severe("RESET FAILED: Could not find any loaded world for '" + dimensionName + "'");
            cleanupTasksForDim(dimensionName);
            return;
        }

        Bukkit.broadcastMessage(getMessage("dimension_reset_messages.reset_now").replace("%dimension%", getCapitalizedName(dimensionName)));
        playSound(plugin.getConfig().getString("sounds.reset_success"));

        plugin.getLogger().info("[Reset Stage 1/2] Teleporting players and unloading dimension...");
        Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : new ArrayList<>(worldToReset.getPlayers())) {
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.GREEN + getCapitalizedName(dimensionName) + " is resetting! You have been teleported to safety.");
        }

        File worldFolder = worldToReset.getWorldFolder();
        String worldKey = worldToReset.getKey().toString();
        World.Environment env = worldToReset.getEnvironment();

        if (!Bukkit.unloadWorld(worldToReset, false)) {
            plugin.getLogger().severe("RESET FAILED: Failed to unload " + worldToReset.getName());
            cleanupTasksForDim(dimensionName);
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("[Reset Stage 2/2] Deleting world files for " + worldFolder.getName());
            try { deleteDirectory(worldFolder); } catch (IOException e) {
                plugin.getLogger().severe("RESET FAILED: Could not delete world files.");
                e.printStackTrace();
                cleanupTasksForDim(dimensionName);
                return;
            }

            plugin.getLogger().info("[Reset Stage 3/3] Recreating dimension " + worldKey);
            Bukkit.createWorld(new WorldCreator(worldKey).environment(env));
            justResetDimensions.add(dimensionName.toLowerCase());

            plugin.getLogger().info("SUCCESS: " + getCapitalizedName(dimensionName) + " has been reset.");

            // --- NEW: Record the reset in our history ---
            dataManager.addResetToHistory(dimensionName);
            plugin.getLogger().info("Reset for '" + dimensionName + "' has been recorded in history.");

            // If this was an automated reset, we still need to update the *last* reset time for that specific schedule
            if (scheduleId != null) {
                // We use the new getLatestResetTime which gets the most recent entry from the history we just added.
                // NOTE: This assumes we change the method in DataManager as well. For now, let's keep the old method too.
                dataManager.setLastResetTime(scheduleId, Instant.now().getEpochSecond());
                plugin.getLogger().info("Updated last reset time for schedule: " + scheduleId);
            }

            cleanupTasksForDim(dimensionName);
        }, 20L);
    }

    // --- All other methods are unchanged from the last working version ---
    // Includes onCommand, all handlers, and helper methods.
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "DimensionsReset v1.4.1 by Mike4947");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "gui" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(getMessage("preview.error_players_only")); return true; }
                if (!player.hasPermission("dimensionsreset.admin")) { noPerm(sender); return true; }
                guiManager.openMainMenu(player);
            }
            case "confirm" -> handleConfirm(sender);
            case "reset", "cancel", "status" -> handleDimensionCommands(sender, sub, args);
            case "preview" -> handlePreviewCommand(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(ChatColor.RED + "Unknown command. Use /dr gui for help.");
        }
        return true;
    }
    private void handleDimensionCommands(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /dr " + sub + " <the_end|the_nether|all>"); return; }
        List<String> dimensions = parseDimensionNames(args[1]);
        if (dimensions.isEmpty()) { sender.sendMessage(ChatColor.RED + "Invalid dimension name(s)."); return; }
        if (sub.equals("reset")) {
            if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /dr reset <dimension|all> <time|now>"); return; }
            handleDimensionReset(sender, dimensions, args[2]);
        } else if (sub.equals("cancel")) {
            handleDimensionCancel(sender, dimensions);
        } else if (sub.equals("status")) {
            handleDimensionStatus(sender, dimensions);
        }
    }
    @Override
    public boolean wasDimensionJustReset(String dimensionName) { return justResetDimensions.contains(dimensionName.toLowerCase()); }
    @Override
    public void acknowledgeReset(String dimensionName) { justResetDimensions.remove(dimensionName.toLowerCase()); }
    @Override
    public World findWorld(String dimensionName) {
        World.Environment env = getEnvironmentFromName(dimensionName);
        if (env == null) return null;
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == env).findFirst().orElse(null);
    }
    public boolean isResetScheduled(String dimension) { return scheduledResetTasks.containsKey(dimension.toLowerCase()); }
    public long parseTime(String timeString) {
        long totalSeconds = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        boolean matchFound = false;
        while (matcher.find()) {
            matchFound = true;
            int amount = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 'h' -> totalSeconds += TimeUnit.HOURS.toSeconds(amount);
                case 'm' -> totalSeconds += TimeUnit.MINUTES.toSeconds(amount);
                case 's' -> totalSeconds += amount;
            }
        }
        if (!matchFound) throw new IllegalArgumentException("Invalid time format");
        return totalSeconds;
    }
    public void scheduleReset(String dimension, long timeInSeconds, @Nullable String scheduleId) {
        String dimKey = dimension.toLowerCase();
        if (isResetScheduled(dimKey)) return;
        long timeInTicks = timeInSeconds * 20L;
        scheduledResetTimes.put(dimKey, Instant.now().plusSeconds(timeInSeconds));
        this.activeScheduleId = scheduleId;
        String scheduledMessage = getMessage("dimension_reset_messages.reset_scheduled").replace("%dimension%", getCapitalizedName(dimKey)).replace("%time%", formatTime(timeInSeconds));
        Bukkit.broadcastMessage(scheduledMessage);
        playSound(plugin.getConfig().getString("sounds.reset_scheduled"));
        BukkitTask mainTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> resetDimension(dimKey, scheduleId), timeInTicks);
        scheduledResetTasks.put(dimKey, mainTask);
        List<BukkitTask> countdowns = new ArrayList<>();
        List<Integer> countdownTimes = plugin.getConfig().getIntegerList("countdown_broadcast_times");
        for (int countdownSeconds : countdownTimes) {
            if (timeInSeconds >= countdownSeconds) {
                long delayTicks = (timeInSeconds - countdownSeconds) * 20L;
                BukkitTask countdownTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    String countdownMessage = getMessage("dimension_reset_messages.reset_scheduled").replace("%dimension%", getCapitalizedName(dimKey)).replace("%time%", formatTime(countdownSeconds));
                    Bukkit.broadcastMessage(countdownMessage);
                    playSound(plugin.getConfig().getString("sounds.countdown_tick"));
                }, delayTicks);
                countdowns.add(countdownTask);
            }
        }
        scheduledCountdownTasks.put(dimKey, countdowns);
    }
    public void cancelAllDimensionResets() { new ArrayList<>(scheduledResetTasks.keySet()).forEach(this::cancelAllTasksForDim); }
    private void handleDimensionReset(CommandSender sender, List<String> dimensions, String timeArg) {
        for (String dim : dimensions) {
            if (isResetScheduled(dim)) {
                sender.sendMessage(ChatColor.RED + "A reset for '" + dim + "' is already scheduled!");
                return;
            }
        }
        if (timeArg.equalsIgnoreCase("now")) {
            manualConfirmMap.put(sender, dimensions);
            sender.sendMessage(getMessage("dimension_reset_messages.confirmation.required").replace("%dimension%", "these dimensions"));
            if (manualConfirmTask != null) manualConfirmTask.cancel();
            manualConfirmTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (manualConfirmMap.remove(sender) != null) sender.sendMessage(getMessage("dimension_reset_messages.confirmation.expired"));
            }, 300L);
        } else {
            try {
                long timeInSeconds = parseTime(timeArg);
                dimensions.forEach(dim -> scheduleReset(dim, timeInSeconds, null));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(getMessage("generic_messages.error_invalid_time"));
            }
        }
    }
    private void handleConfirm(CommandSender sender) {
        List<String> dimensionsToReset = manualConfirmMap.remove(sender);
        if (dimensionsToReset != null) {
            if (manualConfirmTask != null) manualConfirmTask.cancel();
            sender.sendMessage(getMessage("dimension_reset_messages.confirmation.success"));
            dimensionsToReset.forEach(dim -> resetDimension(dim, null));
            return;
        }
        sender.sendMessage(getMessage("dimension_reset_messages.confirmation.not_required"));
    }
    private void handleDimensionCancel(CommandSender sender, List<String> dimensions) {
        dimensions.forEach(dim -> {
            if (isResetScheduled(dim)) {
                cancelAllTasksForDim(dim);
                Bukkit.broadcastMessage(getMessage("dimension_reset_messages.reset_cancelled").replace("%dimension%", getCapitalizedName(dim)));
                sender.sendMessage(ChatColor.GREEN + "Scheduled reset for '" + dim + "' has been cancelled.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No reset was scheduled for '" + dim + "'.");
            }
        });
    }
    private void handleDimensionStatus(CommandSender sender, List<String> dimensions) {
        dimensions.forEach(dim -> {
            String dimKey = dim.toLowerCase();
            if (isResetScheduled(dimKey)) {
                long remainingSeconds = Duration.between(Instant.now(), scheduledResetTimes.get(dimKey)).getSeconds();
                if (remainingSeconds < 0) remainingSeconds = 0;
                sender.sendMessage(getMessage("dimension_reset_messages.status_scheduled").replace("%dimension%", getCapitalizedName(dimKey)).replace("%time%", formatTime(remainingSeconds)));
            } else {
                sender.sendMessage(getMessage("dimension_reset_messages.status_not_scheduled").replace("%dimension%", getCapitalizedName(dimKey)));
            }
        });
    }
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dimensionsreset.reload")) { noPerm(sender); return; }
        plugin.reloadConfig();
        sender.sendMessage(getMessage("generic_messages.config_reloaded"));
    }
    private void handlePreviewCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dimensionsreset.preview")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /dr preview <dimension|seed> [before|exit]"); return; }
        String action = args[1].toLowerCase();
        if (action.equals("seed")) {
            World overworld = Bukkit.getWorlds().get(0);
            sender.sendMessage(getMessage("preview.seed_message").replace("%seed%", String.valueOf(overworld.getSeed())));
            return;
        }
        List<String> validDims = List.of("the_end", "the_nether");
        if (!validDims.contains(action)) {
            sender.sendMessage(ChatColor.RED + "Invalid dimension. Use 'the_end' or 'the_nether'.");
            return;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(getMessage("preview.error_players_only")); return; }
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /dr preview " + action + " <before|exit>"); return; }
        String subAction = args[2].toLowerCase();
        if (subAction.equals("before")) enterPreviewMode(player, action);
        else if (subAction.equals("exit")) exitPreviewMode(player, true);
    }
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) deleteDirectory(file);
                    else if (!file.delete()) throw new IOException("Failed to delete file: " + file);
                }
            }
            if (!directory.delete()) throw new IOException("Failed to delete directory: " + directory);
        }
    }
    private void enterPreviewMode(Player player, String dimensionName) {
        World worldToPreview = findWorld(dimensionName);
        if (worldToPreview == null) {
            player.sendMessage(getMessage("preview.error_dimension_not_found").replace("%dimension%", dimensionName));
            return;
        }
        if (isPreviewing(player)) {
            player.sendMessage(getMessage("preview.error_already_in_preview"));
            return;
        }
        PlayerState originalState = new PlayerState(player.getLocation(), player.getGameMode());
        previewingPlayers.put(player.getUniqueId(), originalState);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(worldToPreview.getSpawnLocation());
        player.sendMessage(getMessage("preview.enter_message").replace("%dimension%", getCapitalizedName(dimensionName)));
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
    public boolean isPreviewing(Player player) { return previewingPlayers.containsKey(player.getUniqueId()); }
    public void cancelAllTasksForDim(String dimension) {
        String dimKey = dimension.toLowerCase();
        if (scheduledResetTasks.containsKey(dimKey)) scheduledResetTasks.remove(dimKey).cancel();
        if (scheduledCountdownTasks.containsKey(dimKey)) {
            scheduledCountdownTasks.get(dimKey).forEach(BukkitTask::cancel);
            scheduledCountdownTasks.remove(dimKey);
        }
        cleanupTasksForDim(dimKey);
    }
    private void cleanupTasksForDim(String dimension) {
        String dimKey = dimension.toLowerCase();
        scheduledResetTasks.remove(dimKey);
        scheduledCountdownTasks.remove(dimKey);
        scheduledResetTimes.remove(dimKey);
        activeScheduleId = null;
    }
    private List<String> parseDimensionNames(String arg) {
        if (arg.equalsIgnoreCase("all")) return plugin.getConfig().getStringList("reset-all-dimensions");
        return Arrays.stream(arg.toLowerCase().split(",")).collect(Collectors.toList());
    }
    private String getCapitalizedName(String dimensionName) {
        return switch (dimensionName.toLowerCase()) {
            case "the_end" -> "The End";
            case "the_nether" -> "The Nether";
            default -> dimensionName;
        };
    }
    private World.Environment getEnvironmentFromName(String name) {
        return switch (name.toLowerCase()) {
            case "the_end" -> World.Environment.THE_END;
            case "the_nether" -> World.Environment.NETHER;
            default -> null;
        };
    }
    private void playSound(String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) { return; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
        }
    }
    public String getMessage(String path) {
        String message = plugin.getConfig().getString(path, "&cMessage not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    private void noPerm(CommandSender sender) { sender.sendMessage(getMessage("generic_messages.error_no_permission")); }
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
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        List<String> allCommands = new ArrayList<>(List.of("confirm", "reload", "gui"));
        if(sender.hasPermission("dimensionsreset.admin")) {
            allCommands.addAll(Arrays.asList("reset", "cancel", "status"));
        }
        if(sender.hasPermission("dimensionsreset.preview")) {
            allCommands.add("preview");
        }
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], allCommands, completions);
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("reset", "cancel", "status", "preview").contains(subCmd)) {
                List<String> suggestions = new ArrayList<>(Arrays.asList("the_end", "the_nether"));
                if (!subCmd.equals("preview")) suggestions.add("all");
                else suggestions.add("seed");
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("reset")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("now", "1h", "30m"), completions);
            } else if (subCmd.equals("preview") && !args[1].equalsIgnoreCase("seed")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("before", "exit"), completions);
            }
        }
        return completions;
    }
}