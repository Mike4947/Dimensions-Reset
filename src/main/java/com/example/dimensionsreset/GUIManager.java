package com.example.dimensionsreset;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUIManager {

    public static final String MAIN_MENU_TITLE = "§8DimensionsReset Control Panel";
    public static final String RESET_SELECT_TITLE = "§8Select Dimension to Reset";

    // Method to open the main GUI menu for a player
    public void openMainMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        // Create the buttons for the main menu
        mainMenu.setItem(11, createGUIItem(Material.ENDER_EYE, "§aReset a Dimension", "§7Click to choose which", "§7dimension to reset."));
        mainMenu.setItem(13, createGUIItem(Material.CLOCK, "§eCheck Reset Status", "§7Click to check the status", "§7of a scheduled reset."));
        mainMenu.setItem(15, createGUIItem(Material.BARRIER, "§cCancel a Reset", "§7Click to cancel a", "§7scheduled reset."));

        player.openInventory(mainMenu);
    }

    // Method to open the dimension selection sub-menu
    public void openResetSelectMenu(Player player) {
        Inventory resetMenu = Bukkit.createInventory(null, 27, RESET_SELECT_TITLE);

        // Create buttons for each dimension
        resetMenu.setItem(11, createGUIItem(Material.END_STONE, "§dReset The End", "§7Reset The End dimension", "§7to its original state."));
        resetMenu.setItem(13, createGUIItem(Material.NETHERRACK, "§cReset The Nether", "§7Reset The Nether dimension", "§7to its original state."));
        resetMenu.setItem(15, createGUIItem(Material.NETHER_STAR, "§bReset All Dimensions", "§7Reset both The End and", "§7The Nether at the same time."));

        resetMenu.setItem(18, createGUIItem(Material.ARROW, "§7Back to Main Menu"));

        player.openInventory(resetMenu);
    }

    // Helper method to create a styled item for the GUI
    private ItemStack createGUIItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}