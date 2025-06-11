package com.example.dimensionsreset;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerListener implements Listener {

    private final DRCommand commandHandler;

    public PlayerListener(DRCommand commandHandler) {
        this.commandHandler = commandHandler;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (commandHandler.isPreviewing(player)) {
            commandHandler.exitPreviewMode(player, false);
        }
    }

    // --- NEW PORTAL INTERCEPTION LOGIC ---
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        // Only handle teleports to The End or The Nether
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL &&
            event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        String dimensionName = (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) ? "the_end" : "the_nether";

        // Check if this dimension was just recently reset by our plugin
        if (commandHandler.wasDimensionJustReset(dimensionName)) {
            event.setCancelled(true); // Stop the broken vanilla teleport

            // Find the new world and teleport the player to its spawn
            World newWorld = commandHandler.findWorld(dimensionName);
            if (newWorld != null) {
                Location destination = newWorld.getSpawnLocation();
                // For The End, make sure the spawn platform is generated
                if (newWorld.getEnvironment() == World.Environment.THE_END) {
                    destination = new Location(newWorld, 100, 50, 0); // Standard End platform location
                }
                event.getPlayer().teleport(destination);
                
                // Once one player has gone through, we can assume the link is re-established for others.
                commandHandler.acknowledgeReset(dimensionName);
            }
        }
    }
}
