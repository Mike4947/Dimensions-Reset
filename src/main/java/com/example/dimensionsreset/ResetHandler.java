package com.example.dimensionsreset;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ResetHandler {

    private final DimensionsReset plugin;

    public ResetHandler(DimensionsReset plugin) {
        this.plugin = plugin;
    }

    public void performWildernessReset(World world, RegionManager regionManager, Runnable onComplete) {
        plugin.getLogger().info("Initiating wilderness reset for world: " + world.getName());

        // --- Step 1: Relocate players ---
        Location safeLobby = Bukkit.getWorlds().get(0).getSpawnLocation(); // A better implementation might be a dedicated lobby world
        List<Player> playersToRelocate = new ArrayList<>(world.getPlayers());
        playersToRelocate.forEach(p -> {
            p.teleport(safeLobby);
            p.sendMessage(plugin.getCommandHandler().getMessage("wand-protect.reset.players-moved"));
        });

        // --- Step 2: Perform deletion asynchronously to avoid freezing the server ---
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Scanning region files for deletion...");
                File regionFolder = new File(world.getWorldFolder(), "region");
                if (!regionFolder.exists() || !regionFolder.isDirectory()) {
                    plugin.getLogger().severe("Could not find region folder for " + world.getName());
                    return;
                }

                int deletedFiles = 0;
                File[] regionFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));
                if (regionFiles == null) return;

                for (File regionFile : regionFiles) {
                    try {
                        String[] parts = regionFile.getName().split("\\.");
                        int regionX = Integer.parseInt(parts[1]);
                        int regionZ = Integer.parseInt(parts[2]);

                        // Check if this entire region is outside all protected zones
                        if (!isRegionProtected(regionX, regionZ, world.getName(), regionManager)) {
                            if (regionFile.delete()) {
                                deletedFiles++;
                                plugin.getLogger().info("Deleted unprotected region file: " + regionFile.getName());
                            } else {
                                plugin.getLogger().warning("Failed to delete region file: " + regionFile.getName());
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Ignore files that aren't in the r.X.Z.mca format
                    }
                }

                plugin.getLogger().info("Wilderness reset scan complete. Deleted " + deletedFiles + " region files.");

                // --- Step 3: Teleport players back on the main thread ---
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playersToRelocate.forEach(p -> p.teleport(safeLobby)); // Teleport them to spawn again
                        onComplete.run(); // Run the completion callback
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private boolean isRegionProtected(int regionX, int regionZ, String worldName, RegionManager regionManager) {
        // Convert region coords to block coords
        int minBlockX = regionX << 9; // regionX * 512
        int minBlockZ = regionZ << 9; // regionZ * 512
        int maxBlockX = minBlockX + 511;
        int maxBlockZ = minBlockZ + 511;

        // We can check the four corners of the region file. If any corner is protected, we spare the whole file.
        // A more complex check could be done for partial overlaps, but this is safer and simpler.
        if (regionManager.isInsideProtectedRegion(worldName, minBlockX, minBlockZ)) return true;
        if (regionManager.isInsideProtectedRegion(worldName, maxBlockX, minBlockZ)) return true;
        if (regionManager.isInsideProtectedRegion(worldName, minBlockX, maxBlockZ)) return true;
        if (regionManager.isInsideProtectedRegion(worldName, maxBlockX, maxBlockZ)) return true;

        return false;
    }
}
