package com.example.dimensionsreset;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    private final GUIManager guiManager;

    public GUIListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Check if the clicked inventory is one of our GUIs
        if (!title.equals(GUIManager.MAIN_MENU_TITLE) &&
                !title.equals(GUIManager.RESET_SELECT_TITLE) &&
                !title.equals(GUIManager.ANALYTICS_TITLE)) { // Add analytics title to check
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Route clicks based on which GUI is open
        if (title.equals(GUIManager.MAIN_MENU_TITLE)) {
            handleMainMenuClick(player, clickedItem);
        } else if (title.equals(GUIManager.RESET_SELECT_TITLE)) {
            handleResetSelectMenuClick(player, clickedItem);
        } else if (title.equals(GUIManager.ANALYTICS_TITLE)) {
            handleAnalyticsMenuClick(player, clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case ENDER_EYE:
                guiManager.openResetSelectMenu(player);
                break;
            case CLOCK:
                player.performCommand("dr status all");
                player.closeInventory();
                break;
            case BARRIER:
                player.performCommand("dr cancel all");
                player.closeInventory();
                break;
            // NEW: Handle click on the analytics book
            case BOOK:
                guiManager.openAnalyticsGUI(player);
                break;
        }
    }

    private void handleResetSelectMenuClick(Player player, ItemStack item) {
        // Unchanged
    }

    // --- NEW METHOD TO HANDLE CLICKS IN THE ANALYTICS GUI ---
    private void handleAnalyticsMenuClick(Player player, ItemStack item) {
        if (item.getType() == Material.ARROW) {
            // The only clickable item is the "Back" button
            guiManager.openMainMenu(player);
        }
    }
}