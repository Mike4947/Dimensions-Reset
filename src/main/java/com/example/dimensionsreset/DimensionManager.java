package com.example.dimensionsreset;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DimensionManager {

    // This class now holds all state related to dimension resets
    private record PlayerState(Location location, GameMode gameMode) {}
    private final Map<UUID, PlayerState> previewingPlayers = new HashMap<>();
    private final Map<String, BukkitTask> scheduledResetTasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> scheduledCountdownTasks = new HashMap<>();
    private final Map<String, Instant> scheduledResetTimes = new HashMap<>();

    private final DimensionsReset plugin;
    private final DataManager dataManager;
    private String activeScheduleId = null;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");


    public DimensionManager(DimensionsReset plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    // --- Public Methods for DRCommand to call ---

    public boolean isResetScheduled(String dimension) {
        return scheduledResetTasks.containsKey(dimension.toLowerCase());
    }

    public void scheduleReset(String dimension, long timeInSeconds, @Nullable String scheduleId) {
        String dimKey = dimension.toLowerCase();
        if (isResetScheduled(dimKey)) return;

        long timeInTicks = timeInSeconds * 20L;
        scheduledResetTimes.put(dimKey, Instant.now().plusSeconds(timeInSeconds));
        this.activeScheduleId = scheduleId;

        String scheduledMessage = getMessage("messages.reset-scheduled").replace("%dimension%", getCapitalizedName(dimKey)).replace("%time%", formatTime(timeInSeconds));
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
                    String countdownMessage = getMessage("messages.reset-scheduled").replace("%dimension%", getCapitalizedName(dimKey)).replace("%time%", formatTime(countdownSeconds));
                    Bukkit.broadcastMessage(countdownMessage);
                    playSound(plugin.getConfig().getString("sounds.countdown_tick"));
                }, delayTicks);
                countdowns.add(countdownTask);
            }
        }
        scheduledCountdownTasks.put(dimKey, countdowns);
    }

    public void resetDimension(String dimensionName, @Nullable String scheduleId) {
        World worldToReset = findWorld(dimensionName);
        if (worldToReset == null) {
            plugin.getLogger().severe("RESET FAILED: Could not find any loaded world for '" + dimensionName + "'");
            cleanupTasksForDim(dimensionName);
            return;
        }

        Bukkit.broadcastMessage(getMessage("messages.reset-now").replace("%dimension%", getCapitalizedName(dimensionName)));
        playSound(plugin.getConfig().getString("sounds.reset_success"));

        plugin.getLogger().info("[Reset Stage 1/3] Teleporting players from " + worldToReset.getName());
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
            plugin.getLogger().info("[Reset Stage 2/3] Deleting world files for " + worldFolder.getName());
            try { deleteDirectory(worldFolder); } catch (IOException e) {
                plugin.getLogger().severe("RESET FAILED: Could not delete world files.");
                e.printStackTrace();
                cleanupTasksForDim(dimensionName);
                return;
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("[Reset Stage 3/3] Recreating dimension " + worldKey);
                Bukkit.createWorld(new WorldCreator(worldKey).environment(env));

                if (scheduleId != null) {
                    dataManager.setLastResetTime(scheduleId, Instant.now().getEpochSecond());
                    plugin.getLogger().info("Updated last reset time for schedule: " + scheduleId);
                }
                plugin.getLogger().info("SUCCESS: " + getCapitalizedName(dimensionName) + " has been reset.");
                cleanupTasksForDim(dimensionName);
            }, 20L);
        }, 20L);
    }

    public void enterPreviewMode(Player player, String dimensionName) {
        World worldToPreview = findWorld(dimensionName);
        if (worldToPreview == null) {
            player.sendMessage(getMessage("preview.error_end_not_found")); // This message needs to be generic now
            return;
        }
        if (previewingPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage(getMessage("preview.error_already_in_preview"));
            return;
        }
        PlayerState originalState = new PlayerState(player.getLocation(), player.getGameMode());
        previewingPlayers.put(player.getUniqueId(), originalState);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(worldToPreview.getSpawnLocation());
        player.sendMessage(getMessage("preview.enter_message"));
    }
    
    public void exitPreviewMode(Player player) {
        PlayerState originalState = previewingPlayers.remove(player.getUniqueId());
        if (originalState == null) {
            player.sendMessage(getMessage("preview.error_not_in_preview"));
            return;
        }
        player.teleport(originalState.location());
        player.setGameMode(originalState.gameMode());
        player.sendMessage(getMessage("preview.exit_message"));
    }

    public boolean isPreviewing(Player player) {
        return previewingPlayers.containsKey(player.getUniqueId());
    }

    public void cancelAllTasksForDim(String dimension) {
        String dimKey = dimension.toLowerCase();
        if (scheduledResetTasks.containsKey(dimKey)) scheduledResetTasks.remove(dimKey).cancel();
        if (scheduledCountdownTasks.containsKey(dimKey)) {
            scheduledCountdownTasks.remove(dimKey).forEach(BukkitTask::cancel);
            scheduledCountdownTasks.remove(dimKey);
        }
        cleanupTasksForDim(dimKey);
    }

    public Map<String, Instant> getScheduledResetTimes() {
        return scheduledResetTimes;
    }

    // --- Helper & Utility Methods ---

    private void cleanupTasksForDim(String dimension) {
        String dimKey = dimension.toLowerCase();
        scheduledResetTasks.remove(dimKey);
        scheduledCountdownTasks.remove(dimKey);
        scheduledResetTimes.remove(dimKey);
        activeScheduleId = null;
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

    public World findWorld(String dimensionName) {
        World.Environment env = getEnvironmentFromName(dimensionName);
        if (env == null) return null;
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == env).findFirst().orElse(null);
    }
    
    public World.Environment getEnvironmentFromName(String name) {
        return switch (name.toLowerCase()) {
            case "the_end" -> World.Environment.THE_END;
            case "the_nether" -> World.Environment.THE_NETHER;
            default -> null;
        };
    }

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

    private String getCapitalizedName(String dimensionName) {
        return switch (dimensionName.toLowerCase()) {
            case "the_end" -> "The End";
            case "the_nether" -> "The Nether";
            default -> dimensionName;
        };
    }

    private void playSound(String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
        }
    }

    private String getMessage(String path) {
        String message = plugin.getConfig().getString(path, "&cMessage not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String formatTime(long totalSeconds) {
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
