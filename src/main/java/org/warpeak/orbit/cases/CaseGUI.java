package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.warpeak.orbit.Orbit;

import java.util.ArrayList;
import java.util.List;

public class CaseGUI {

    private static final int DEFAULT_SIZE = 27;
    private static final int DEFAULT_BUY_SLOT = 13;

    public static String getTitle() {
        return Orbit.get().getSettings().text("case.gui.title", "&5Кейс Префиксов");
    }

    public static int getBuySlot() {
        int size = getInventorySize();
        return Orbit.get().getSettings().integer("case.gui.buy-slot", DEFAULT_BUY_SLOT, 0, size - 1);
    }

    private static int getInventorySize() {
        int configured = Orbit.get().getSettings().integer("case.gui.size", DEFAULT_SIZE, 9, 54);
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }

    public static Inventory build(long price, long playerCoins) {
        Orbit plugin = Orbit.get();
        Inventory inv = Bukkit.createInventory(null, getInventorySize(), getTitle());

        ItemStack glass = namedItem(plugin.getSettings().material(
                "case.gui.background-material", Material.PURPLE_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        ItemStack chest = new ItemStack(plugin.getSettings().material("case.gui.buy-material", Material.CHEST));
        ItemMeta meta = chest.getItemMeta();
        meta.setDisplayName(plugin.getSettings().text("case.gui.buy-name", "&6&lКейс Префиксов"));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getSettings().textList("case.gui.buy-lore", List.of(
                "&7Цена: &e{price} монет",
                "&7Ваш баланс: &a{balance} монет",
                "",
                "{purchase-state}"
        ))) {
            lore.add(line
                    .replace("{price}", Long.toString(price))
                    .replace("{balance}", Long.toString(playerCoins))
                    .replace("{purchase-state}", playerCoins >= price
                            ? plugin.getSettings().text("case.gui.can-buy", "&aНажмите, чтобы открыть!")
                            : plugin.getSettings().text("case.gui.cannot-buy", "&cНедостаточно монет!")));
        }
        meta.setLore(lore);
        chest.setItemMeta(meta);

        inv.setItem(getBuySlot(), chest);
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
