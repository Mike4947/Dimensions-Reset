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

public class DRCommand implements CommandExecutor, TabCompleter {

    // All fields and the constructor are correct and unchanged
    private record PlayerState(Location location, GameMode gameMode) {}
    private final Map<UUID, PlayerState> previewingPlayers = new HashMap<>();
    private final DimensionsReset plugin;
    private final DataManager dataManager;
    private final RegionManager regionManager;
    private final ResetHandler resetHandler;
    private final WandManager wandManager;
    private final Map<String, BukkitTask> scheduledResetTasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> scheduledCountdownTasks = new HashMap<>();
    private final Map<String, Instant> scheduledResetTimes = new HashMap<>();
    private String activeScheduleId = null;
    private final Map<CommandSender, List<String>> manualConfirmMap = new HashMap<>();
    private BukkitTask manualConfirmTask = null;
    private CommandSender wildernessConfirmer1 = null;
    private CommandSender wildernessConfirmer2 = null;
    private String wildernessWorldToReset = null;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");
    private final Set<String> justResetDimensions = new HashSet<>();

    public DRCommand(DimensionsReset plugin, DataManager dataManager, RegionManager regionManager, ResetHandler resetHandler, WandManager wandManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.regionManager = regionManager;
        this.resetHandler = resetHandler;
        this.wandManager = wandManager;
    }
    
    // This public method is needed by the PlayerListener
    public boolean wasDimensionJustReset(String dimensionName) {
        return justResetDimensions.contains(dimensionName.toLowerCase());
    }
    
    // This public method is needed by the PlayerListener
    public void acknowledgeReset(String dimensionName) {
        justResetDimensions.remove(dimensionName.toLowerCase());
    }

    // --- THIS IS THE CORRECTED METHOD ---
    // It is now public so other classes, like PlayerListener, can use it.
    public World findWorld(String dimensionName) {
        World.Environment env = getEnvironmentFromName(dimensionName);
        if (env == null) return null;
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == env).findFirst().orElse(null);
    }
    
    // --- The rest of the file is identical to the last version ---
    // Includes onCommand, all handle... methods, other public methods for the scheduler, etc.
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "DimensionsReset - Final Version");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "confirm" -> handleConfirm(sender);
            case "lastconfirm" -> handleLastConfirm(sender);
            case "reset", "cancel", "status" -> handleDimensionCommands(sender, sub, args);
            case "preview" -> handlePreviewCommand(sender, args);
            case "wand", "region", "resetwilderness" -> handleWildernessCommands(sender, sub, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(ChatColor.RED + "Unknown command.");
        }
        return true;
    }
    
    private void handleDimensionCommands(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return; }
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /dr " + sub + " <the_end|the_nether|all>"); return; }
        List<String> dimensions = parseDimensionNames(args[1]);
        if (dimensions.isEmpty()) { sender.sendMessage(ChatColor.RED + "Invalid dimension name(s). Use 'the_end', 'the_nether', or 'all'."); return; }
        
        if (sub.equals("reset")) {
            if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /dr reset <dimension|all> <time|now>"); return; }
            handleDimensionReset(sender, dimensions, args[2]);
        } else if (sub.equals("cancel")) {
            handleDimensionCancel(sender, dimensions);
        } else if (sub.equals("status")) {
            handleDimensionStatus(sender, dimensions);
        }
    }

    private void handleWildernessCommands(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("dimensionsreset.admin")) { noPerm(sender); return; }
        if (sub.equals("wand")) handleWand(sender);
        else if (sub.equals("region")) handleRegion(sender, args);
        else if (sub.equals("resetwilderness")) handleResetWilderness(sender, args);
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
    
    public void cancelAllDimensionResets() {
        new ArrayList<>(scheduledResetTasks.keySet()).forEach(this::cancelAllTasksForDim);
    }
    
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
        if (sender == wildernessConfirmer1) {
            this.wildernessConfirmer1 = null;
            this.wildernessConfirmer2 = sender;
            sender.sendMessage(getMessage("wilderness_reset.reset.confirm2"));
            return;
        }
        sender.sendMessage(getMessage("dimension_reset_messages.confirmation.not_required"));
    }

    private void handleLastConfirm(CommandSender sender) {
        if (sender != wildernessConfirmer2) {
            sender.sendMessage(ChatColor.RED + "You don't have a wilderness reset awaiting final confirmation.");
            return;
        }
        World world = Bukkit.getWorld(this.wildernessWorldToReset);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "The world to reset is no longer loaded. Aborting.");
            return;
        }
        
        Bukkit.broadcastMessage(getMessage("wilderness_reset.reset.start").replace("%world%", this.wildernessWorldToReset));
        resetHandler.performWildernessReset(world, regionManager, () -> Bukkit.broadcastMessage(getMessage("wilderness_reset.reset.complete")));
        
        this.wildernessConfirmer2 = null;
        this.wildernessWorldToReset = null;
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

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(getMessage("wilderness_reset.error_players_only")); return; }
        if (!player.hasPermission("dimensionsreset.admin")) { noPerm(sender); return; }
        player.getInventory().addItem(new ItemStack(WandManager.WAND_ITEM));
        player.sendMessage(getMessage("wilderness_reset.wand_give"));
    }

    private void handleRegion(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /dr region <save|delete|list> [name]"); return; }
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            Set<String> regionNames = regionManager.getRegionNames();
            if (regionNames.isEmpty()) { sender.sendMessage(getMessage("wilderness_reset.region_list_empty")); return; }
            sender.sendMessage(getMessage("wilderness_reset.region_list_header"));
            regionNames.forEach(name -> sender.sendMessage(getMessage("wilderness_reset.region_list_item").replace("%name%", name)));
            return;
        }
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /dr region <save|delete> <name>"); return; }
        String regionName = args[2];
        if (action.equals("save")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(getMessage("wilderness_reset.error_players_only")); return; }
            Location pos1 = wandManager.getPos1(player);
            Location pos2 = wandManager.getPos2(player);
            if (pos1 == null || pos2 == null) { sender.sendMessage(getMessage("wilderness_reset.error_no_selection")); return; }
            if (!Objects.equals(pos1.getWorld(), pos2.getWorld())) { sender.sendMessage(ChatColor.RED + "Both selection points must be in the same world."); return; }
            if (regionManager.regionExists(regionName)) { sender.sendMessage(getMessage("wilderness_reset.error_region_exists").replace("%name%", regionName)); return; }
            regionManager.saveRegion(regionName, pos1, pos2);
            sender.sendMessage(getMessage("wilderness_reset.region_saved").replace("%name%", regionName));
        } else if (action.equals("delete")) {
            if (!regionManager.regionExists(regionName)) { sender.sendMessage(getMessage("wilderness_reset.error_region_not_found").replace("%name%", regionName)); return; }
            regionManager.deleteRegion(regionName);
            sender.sendMessage(getMessage("wilderness_reset.region_deleted").replace("%name%", regionName));
        }
    }

    private void handleResetWilderness(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /dr resetwilderness <world_name>"); return; }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            sender.sendMessage(ChatColor.RED + "Error: '" + worldName + "' is not a valid, loaded overworld.");
            return;
        }
        this.wildernessConfirmer1 = sender;
        this.wildernessWorldToReset = worldName;
        sender.sendMessage(getMessage("wilderness_reset.reset.confirm1").replace("%world%", worldName));
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
            
            plugin.getLogger().info("SUCCESS: " + getCapitalizedName(dimensionName) + " has been reset. No restart needed.");

            if (scheduleId != null) {
                dataManager.setLastResetTime(scheduleId, Instant.now().getEpochSecond());
                plugin.getLogger().info("Updated last reset time for schedule: " + scheduleId);
            }
            cleanupTasksForDim(dimensionName);
        }, 20L);
    }
    
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file);
                    }
                }
            }
            if (!directory.delete()) {
                throw new IOException("Failed to delete directory: " + directory);
            }
        }
    }
    
    private void enterPreviewMode(Player player, String dimensionName) {
        World worldToPreview = findWorld(dimensionName);
        if (worldToPreview == null) {
            worldToPreview = Bukkit.createWorld(new WorldCreator(dimensionName));
            if(worldToPreview == null){
                player.sendMessage(getMessage("preview.error_dimension_not_found").replace("%dimension%", dimensionName));
                return;
            }
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
    
    public boolean isPreviewing(Player player) {
        return previewingPlayers.containsKey(player.getUniqueId());
    }
    
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
        if (arg.equalsIgnoreCase("all")) {
            return plugin.getConfig().getStringList("reset-all-dimensions");
        }
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
    
    private void noPerm(CommandSender sender) {
        sender.sendMessage(getMessage("generic_messages.error_no_permission"));
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
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        final List<String> allCommands = new ArrayList<>(Arrays.asList("reload", "confirm", "lastconfirm"));
        if(sender.hasPermission("dimensionsreset.admin")) {
            allCommands.addAll(Arrays.asList("reset", "cancel", "status", "wand", "region", "resetwilderness"));
        }
        if(sender.hasPermission("dimensionsreset.preview")) {
            allCommands.add("preview");
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], allCommands, completions);
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("reset", "cancel", "status").contains(subCmd)) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("the_end", "the_nether", "all"), completions);
            } else if (subCmd.equals("preview")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("the_end", "the_nether", "seed"), completions);
            } else if (subCmd.equals("region")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("save", "delete", "list"), completions);
            } else if (subCmd.equals("resetwilderness")) {
                List<String> worldNames = Bukkit.getWorlds().stream()
                        .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                        .map(World::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], worldNames, completions);
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("reset")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("now", "1h", "30m"), completions);
            } else if (subCmd.equals("preview") && !args[1].equalsIgnoreCase("seed")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("before", "exit"), completions);
            } else if (subCmd.equals("region") && (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("save"))) {
                 StringUtil.copyPartialMatches(args[2], regionManager.getRegionNames(), completions);
            }
        }
        
        return completions;
    }
}
