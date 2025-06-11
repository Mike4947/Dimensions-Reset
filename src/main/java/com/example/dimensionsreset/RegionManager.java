package com.example.dimensionsreset;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RegionManager {

    // A simple record to hold the two corners of our protected region
    public record Cuboid(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(Location loc) {
            return loc.getWorld().equals(world) &&
                    loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                    loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                    loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }
    }

    private final DimensionsReset plugin;
    private final File regionsFile;
    private final FileConfiguration regionsConfig;
    private final Map<String, Cuboid> protectedRegions = new HashMap<>();

    public RegionManager(DimensionsReset plugin) {
        this.plugin = plugin;
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            try {
                regionsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
        loadRegions();
    }

    private void loadRegions() {
        ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
        if (regionsSection == null) return;

        for (String regionName : regionsSection.getKeys(false)) {
            ConfigurationSection sec = regionsSection.getConfigurationSection(regionName);
            if (sec == null) continue;
            World world = plugin.getServer().getWorld(sec.getString("world"));
            if (world == null) {
                plugin.getLogger().warning("Could not load protected region '" + regionName + "' because world '" + sec.getString("world") + "' is not loaded.");
                continue;
            }
            int x1 = sec.getInt("pos1.x");
            int y1 = sec.getInt("pos1.y");
            int z1 = sec.getInt("pos1.z");
            int x2 = sec.getInt("pos2.x");
            int y2 = sec.getInt("pos2.y");
            int z2 = sec.getInt("pos2.z");

            Cuboid cuboid = new Cuboid(
                    world,
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
            );
            protectedRegions.put(regionName.toLowerCase(), cuboid);
        }
        plugin.getLogger().info("Loaded " + protectedRegions.size() + " protected regions.");
    }

    public boolean saveRegion(String name, Location pos1, Location pos2) {
        if (pos1.getWorld() != pos2.getWorld()) return false;
        String path = "regions." + name.toLowerCase();
        regionsConfig.set(path + ".world", pos1.getWorld().getName());
        regionsConfig.set(path + ".pos1.x", pos1.getBlockX());
        regionsConfig.set(path + ".pos1.y", pos1.getBlockY());
        regionsConfig.set(path + ".pos1.z", pos1.getBlockZ());
        regionsConfig.set(path + ".pos2.x", pos2.getBlockX());
        regionsConfig.set(path + ".pos2.y", pos2.getBlockY());
        regionsConfig.set(path + ".pos2.z", pos2.getBlockZ());
        try {
            regionsConfig.save(regionsFile);
            loadRegions(); // Reload from disk to update memory
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void deleteRegion(String name) {
        regionsConfig.set("regions." + name.toLowerCase(), null);
        try {
            regionsConfig.save(regionsFile);
            protectedRegions.remove(name.toLowerCase());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getRegionNames() {
        return protectedRegions.keySet();
    }

    public boolean regionExists(String name) {
        return protectedRegions.containsKey(name.toLowerCase());
    }

    /**
     * Checks if a specific X,Z coordinate is inside any protected region in a given world.
     * This is the core method for the reset handler.
     */
    public boolean isInsideProtectedRegion(String worldName, int x, int z) {
        for (Cuboid region : protectedRegions.values()) {
            if (!region.world().getName().equalsIgnoreCase(worldName)) continue;
            if (x >= region.minX() && x <= region.maxX() && z >= region.minZ() && z <= region.maxZ()) {
                return true;
            }
        }
        return false;
    }
}