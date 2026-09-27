package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.warpeak.orbit.Orbit;

import java.util.ArrayList;
import java.util.List;

public class PrefixMenuGUI {

    public static final String TITLE = ChatColor.DARK_PURPLE + "Мои префиксы";
    public static final int UNEQUIP_SLOT = 49;

    // 14 слотов в две строки по 7 - ровно под количество призов
    private static final int[] PRIZE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    public static Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        ItemStack glass = namedItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        CasePrize[] prizes = CasePrize.values();
        CasePrize equipped = Orbit.get().getPrefixManager().getEquipped(player);

        for (int i = 0; i < prizes.length && i < PRIZE_SLOTS.length; i++) {
            CasePrize prize = prizes[i];
            boolean unlocked = Orbit.get().getPrefixManager().isUnlocked(player, prize);
            boolean isEquipped = prize == equipped;

            inv.setItem(PRIZE_SLOTS[i], buildPrizeItem(prize, unlocked, isEquipped));
        }

        inv.setItem(UNEQUIP_SLOT, buildUnequipItem(equipped == null));

        return inv;
    }

    private static ItemStack buildPrizeItem(CasePrize prize, boolean unlocked, boolean equipped) {
        ItemStack item = new ItemStack(unlocked ? prize.getMaterial() : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        List<String> lore = new ArrayList<>();

        if (!unlocked) {
            meta.setDisplayName("§7??? " + ChatColor.stripColor(prize.getColoredDisplay()) + " ???");
            lore.add("§cВы ещё не получили этот приз!");
            lore.add("§7Откройте кейс, чтобы получить шанс.");
        } else {
            meta.setDisplayName(prize.getColoredDisplay());
            if (equipped) {
                lore.add("§a✔ Сейчас надет");
                lore.add("§7Нажмите, чтобы снять");
            } else {
                lore.add("§eНажмите, чтобы надеть");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUnequipItem(boolean currentlyNone) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(currentlyNone ? "§7Префикс не надет" : "§cСнять текущий префикс");
        List<String> lore = new ArrayList<>();
        lore.add("§7Вернуться к обычному рангу");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static CasePrize getPrizeBySlot(int slot) {
        for (int i = 0; i < PRIZE_SLOTS.length; i++) {
            if (PRIZE_SLOTS[i] == slot) {
                CasePrize[] prizes = CasePrize.values();
                if (i < prizes.length) return prizes[i];
            }
        }
        return null;
    }

    private static ItemStack namedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}