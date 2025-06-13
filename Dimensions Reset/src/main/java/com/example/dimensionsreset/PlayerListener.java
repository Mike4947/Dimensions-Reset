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

    // It now uses the PortalManager "contract"
    private final PortalManager portalManager;
    private final DRCommand commandHandler;

    public PlayerListener(DRCommand commandHandler) {
        this.commandHandler = commandHandler;
        this.portalManager = commandHandler; // DRCommand fulfills the contract
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (commandHandler.isPreviewing(player)) {
            commandHandler.exitPreviewMode(player, false);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        String dimensionName = (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) ? "the_end" : "the_nether";

        // This call is now guaranteed to exist because of the PortalManager contract.
        if (portalManager.wasDimensionJustReset(dimensionName)) {
            event.setCancelled(true);

            World newWorld = portalManager.findWorld(dimensionName);
            if (newWorld != null) {
                Location destination = newWorld.getSpawnLocation();
                if (newWorld.getEnvironment() == World.Environment.THE_END) {
                    destination = new Location(newWorld, 100.5, 50.0, 0.5, 90, 0); // Face West on the platform
                    // This ensures the obsidian platform generates if it doesn't exist
                    destination.getBlock().getRelative(0, -1, 0).setType(org.bukkit.Material.OBSIDIAN);
                }
                event.getPlayer().teleport(destination);

                // This call is also guaranteed to exist.
                portalManager.acknowledgeReset(dimensionName);
            }
        }
    }
}