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
        if (!title.equals(GUIManager.MAIN_MENU_TITLE) && !title.equals(GUIManager.RESET_SELECT_TITLE)) {
            return;
        }

        event.setCancelled(true); // Prevent players from taking items out

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Handle clicks in the Main Menu
        if (title.equals(GUIManager.MAIN_MENU_TITLE)) {
            handleMainMenuClick(player, clickedItem);
        }
        // Handle clicks in the Reset Selection Menu
        else if (title.equals(GUIManager.RESET_SELECT_TITLE)) {
            handleResetSelectMenuClick(player, clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case ENDER_EYE:
                // Open the sub-menu for resetting dimensions
                guiManager.openResetSelectMenu(player);
                break;
            case CLOCK:
                // For simplicity, we make the player run the command.
                // This reuses our existing command logic and permissions.
                player.performCommand("dr status the_end,the_nether");
                player.closeInventory();
                break;
            case BARRIER:
                player.performCommand("dr cancel the_end,the_nether");
                player.closeInventory();
                break;
        }
    }

    private void handleResetSelectMenuClick(Player player, ItemStack item) {
        switch (item.getType()) {
            case END_STONE:
                // For safety, we always trigger the confirmation flow.
                player.performCommand("dr reset the_end now");
                player.closeInventory();
                break;
            case NETHERRACK:
                player.performCommand("dr reset the_nether now");
                player.closeInventory();
                break;
            case NETHER_STAR:
                player.performCommand("dr reset all now");
                player.closeInventory();
                break;
            case ARROW:
                // Go back to the main menu
                guiManager.openMainMenu(player);
                break;
        }
    }
}