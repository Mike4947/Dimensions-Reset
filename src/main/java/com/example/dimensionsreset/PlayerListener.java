package com.example.dimensionsreset;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * This class listens for player events to safely handle edge cases,
 * such as a player logging out while in preview mode.
 */
public class PlayerListener implements Listener {

    private final DRCommand commandHandler;

    public PlayerListener(DRCommand commandHandler) {
        this.commandHandler = commandHandler;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // If the player who is quitting was in preview mode,
        // force them to exit. This will restore their original gamemode and location
        // for when they log back in, preventing them from being stuck.
        if (commandHandler.isPreviewing(player)) {
            commandHandler.exitPreviewMode(player, false); // false = don't send exit message
        }
    }
}