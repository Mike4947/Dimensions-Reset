package com.example.dimensionsreset;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WandManager implements Listener {

    public static final Material WAND_ITEM = Material.BLAZE_ROD; // The item used as the wand

    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();
    private final DRCommand commandHandler;

    public WandManager(DRCommand commandHandler) {
        this.commandHandler = commandHandler;
    }

    public Location getPos1(Player player) {
        return pos1Selections.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2Selections.get(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the player is holding the wand
        if (itemInHand.getType() != WAND_ITEM || !player.hasPermission("dimensionsreset.admin")) {
            return;
        }

        // Left-Click sets Position 1
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            pos1Selections.put(player.getUniqueId(), loc);
            String locString = String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    commandHandler.getMessage("wand-protect.pos1-set").replace("%location%", locString)));
        }
        // Right-Click sets Position 2
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            pos2Selections.put(player.getUniqueId(), loc);
            String locString = String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    commandHandler.getMessage("wand-protect.pos2-set").replace("%location%", locString)));
        }
    }
}