package com.example.dimensionsreset;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GUIManager {

    public static final String MAIN_MENU_TITLE = "§8DimensionsReset Control Panel";
    public static final String RESET_SELECT_TITLE = "§8Select Dimension to Reset";
    public static final String ANALYTICS_TITLE = "§8Reset Analytics & History";

    // --- NEW: Added the DataManager field ---
    private final DataManager dataManager;

    // --- NEW: Updated the constructor to accept the DataManager ---
    public GUIManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void openMainMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);

        mainMenu.setItem(10, createGUIItem(Material.ENDER_EYE, "§aReset a Dimension", "§7Click to choose which", "§7dimension to reset."));
        mainMenu.setItem(12, createGUIItem(Material.CLOCK, "§eCheck Reset Status", "§7Click to check the status", "§7of a scheduled reset."));
        mainMenu.setItem(14, createGUIItem(Material.BARRIER, "§cCancel a Reset", "§7Click to cancel a", "§7scheduled reset."));
        mainMenu.setItem(16, createGUIItem(Material.BOOK, "§bReset Analytics", "§7Click to view statistics", "§7and reset history."));

        player.openInventory(mainMenu);
    }

    public void openResetSelectMenu(Player player) {
        Inventory resetMenu = Bukkit.createInventory(null, 27, RESET_SELECT_TITLE);
        resetMenu.setItem(11, createGUIItem(Material.END_STONE, "§dReset The End", "§7Reset The End dimension."));
        resetMenu.setItem(13, createGUIItem(Material.NETHERRACK, "§cReset The Nether", "§7Reset The Nether dimension."));
        resetMenu.setItem(15, createGUIItem(Material.NETHER_STAR, "§bReset All Dimensions", "§7Reset both The End and", "§7The Nether at the same time."));
        resetMenu.setItem(18, createGUIItem(Material.ARROW, "§7Back to Main Menu"));
        player.openInventory(resetMenu);
    }

    public void openAnalyticsGUI(Player player) {
        Inventory analyticsMenu = Bukkit.createInventory(null, 54, ANALYTICS_TITLE);

        List<Long> endHistory = dataManager.getResetHistory("the_end");
        long lastEndReset = dataManager.getLatestResetTimeFromHistory("the_end");
        String lastEndResetDate = (lastEndReset == 0) ? "Never" : Instant.ofEpochSecond(lastEndReset).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        analyticsMenu.setItem(10, createGUIItem(Material.END_STONE, "§dThe End Analytics",
                "§7Total Resets: §e" + endHistory.size(),
                "§7Last Reset: §e" + lastEndResetDate
        ));

        List<Long> netherHistory = dataManager.getResetHistory("the_nether");
        long lastNetherReset = dataManager.getLatestResetTimeFromHistory("the_nether");
        String lastNetherResetDate = (lastNetherReset == 0) ? "Never" : Instant.ofEpochSecond(lastNetherReset).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        analyticsMenu.setItem(12, createGUIItem(Material.NETHERRACK, "§cThe Nether Analytics",
                "§7Total Resets: §e" + netherHistory.size(),
                "§7Last Reset: §e" + lastNetherResetDate
        ));

        analyticsMenu.setItem(36, createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§8↑ Reset History per Day ↑"));
        Map<DayOfWeek, Integer> dailyCounts = getDailyResetCounts(endHistory, netherHistory);
        addHistogramBars(analyticsMenu, 45, dailyCounts);

        analyticsMenu.setItem(49, createGUIItem(Material.ARROW, "§7Back to Main Menu"));
        player.openInventory(analyticsMenu);
    }

    private Map<DayOfWeek, Integer> getDailyResetCounts(List<Long>... histories) {
        Map<DayOfWeek, Integer> counts = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            counts.put(day, 0);
        }
        for (List<Long> history : histories) {
            for (long timestamp : history) {
                DayOfWeek day = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).getDayOfWeek();
                counts.put(day, counts.get(day) + 1);
            }
        }
        return counts;
    }

    private void addHistogramBars(Inventory inv, int startSlot, Map<DayOfWeek, Integer> counts) {
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};
        Material[] panes = {Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE};

        for (int i = 0; i < days.length; i++) {
            DayOfWeek day = days[i];
            int count = counts.get(day);
            if (count > 0) {
                int stackSize = Math.max(1, Math.min(count, 64));
                String dayName = day.toString().substring(0, 1) + day.toString().substring(1).toLowerCase();
                inv.setItem(startSlot + i, createGUIItem(panes[i], "§a" + dayName, "§7Total Resets: §e" + count));
                inv.getItem(startSlot + i).setAmount(stackSize);
            }
        }
    }

    // --- CORRECTED createGUIItem METHOD ---
    private ItemStack createGUIItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = Arrays.stream(lore)
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        // This 'return' is now outside the 'if' block, guaranteeing it always runs.
        return item;
    }
}