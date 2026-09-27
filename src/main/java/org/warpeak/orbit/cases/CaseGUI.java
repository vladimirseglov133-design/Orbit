package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CaseGUI {

    public static final String TITLE = ChatColor.DARK_PURPLE + "Кейс Префиксов";
    public static final int BUY_SLOT = 13;

    public static Inventory build(long price, long playerCoins) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        ItemStack glass = namedItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        meta.setDisplayName("§6§lКейс Префиксов");

        List<String> lore = new ArrayList<>();
        lore.add("§7Цена: §e" + price + " монет");
        lore.add("§7Ваш баланс: §a" + playerCoins + " монет");
        lore.add("");
        lore.add(playerCoins >= price ? "§aНажмите, чтобы открыть!" : "§cНедостаточно монет!");
        meta.setLore(lore);
        chest.setItemMeta(meta);

        inv.setItem(BUY_SLOT, chest);
        return inv;
    }

    private static ItemStack namedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}