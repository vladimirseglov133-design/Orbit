package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.warpeak.orbit.Orbit;

import java.util.ArrayList;
import java.util.List;

public final class PrefixMenuGUI {

    private static final int DEFAULT_SIZE = 54;
    private static final int DEFAULT_UNEQUIP_SLOT = 49;
    private static final List<Integer> DEFAULT_PRIZE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );

    private PrefixMenuGUI() { }

    public static String getTitle() {
        return Orbit.get().getSettings().text("prefix-menu.title", "&5Мои префиксы");
    }

    public static int getUnequipSlot() {
        return Orbit.get().getSettings().integer(
                "prefix-menu.unequip.slot", DEFAULT_UNEQUIP_SLOT, 0, getInventorySize() - 1);
    }

    private static int getInventorySize() {
        int configured = Orbit.get().getSettings().integer("prefix-menu.size", DEFAULT_SIZE, 9, 54);
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }

    private static List<Integer> getPrizeSlots() {
        List<Integer> configured = Orbit.get().getConfig().getIntegerList("prefix-menu.prize-slots");
        if (configured.isEmpty()) configured = DEFAULT_PRIZE_SLOTS;
        int size = getInventorySize();
        List<Integer> valid = new ArrayList<>();
        for (Integer slot : configured) {
            if (slot != null && slot >= 0 && slot < size && slot != getUnequipSlot() && !valid.contains(slot)) {
                valid.add(slot);
            }
        }
        return valid;
    }

    public static Inventory build(org.bukkit.entity.Player player) {
        Orbit plugin = Orbit.get();
        Inventory inventory = Bukkit.createInventory(null, getInventorySize(), getTitle());

        ItemStack background = namedItem(
                plugin.getSettings().material("prefix-menu.background.material", Material.PURPLE_STAINED_GLASS_PANE),
                plugin.getSettings().text("prefix-menu.background.name", " "));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, background);

        CasePrize[] prizes = CasePrize.values();
        CasePrize equipped = plugin.getPrefixManager().getEquipped(player);
        List<Integer> prizeSlots = getPrizeSlots();
        for (int i = 0; i < prizes.length && i < prizeSlots.size(); i++) {
            CasePrize prize = prizes[i];
            boolean unlocked = plugin.getPrefixManager().isUnlocked(player, prize);
            inventory.setItem(prizeSlots.get(i), buildPrizeItem(prize, unlocked, prize == equipped));
        }

        int unequipSlot = getUnequipSlot();
        if (unequipSlot < inventory.getSize()) {
            inventory.setItem(unequipSlot, buildUnequipItem(equipped == null));
        }
        return inventory;
    }

    private static ItemStack buildPrizeItem(CasePrize prize, boolean unlocked, boolean equipped) {
        Orbit plugin = Orbit.get();
        Material material = unlocked ? prize.getMaterial()
                : plugin.getSettings().material("prefix-menu.locked.material", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore;
        if (!unlocked) {
            meta.setDisplayName(plugin.getSettings().text("prefix-menu.locked.name", "&7??? {prize} ???")
                    .replace("{prize}", org.bukkit.ChatColor.stripColor(prize.getColoredDisplay())));
            lore = plugin.getSettings().textList("prefix-menu.locked.lore", List.of(
                    "&cВы ещё не получили этот приз!", "&7Откройте кейс, чтобы получить шанс."));
        } else {
            meta.setDisplayName(prize.getColoredDisplay());
            if (equipped) {
                lore = plugin.getSettings().textList("prefix-menu.equipped.lore", List.of(
                        "&a✔ Сейчас надет", "&7Нажмите, чтобы снять"));
            } else {
                lore = plugin.getSettings().textList("prefix-menu.unlocked.lore", List.of(
                        "&eНажмите, чтобы надеть"));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUnequipItem(boolean currentlyNone) {
        Orbit plugin = Orbit.get();
        ItemStack item = new ItemStack(plugin.getSettings().material(
                "prefix-menu.unequip.material", Material.BARRIER));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String namePath = currentlyNone ? "prefix-menu.unequip.empty-name" : "prefix-menu.unequip.equipped-name";
        String defaultName = currentlyNone ? "&7Префикс не надет" : "&cСнять текущий префикс";
        meta.setDisplayName(plugin.getSettings().text(namePath, defaultName));
        meta.setLore(plugin.getSettings().textList("prefix-menu.unequip.lore",
                List.of("&7Вернуться к обычному рангу")));
        item.setItemMeta(meta);
        return item;
    }

    public static CasePrize getPrizeBySlot(int slot) {
        List<Integer> prizeSlots = getPrizeSlots();
        CasePrize[] prizes = CasePrize.values();
        for (int i = 0; i < prizeSlots.size() && i < prizes.length; i++) {
            if (prizeSlots.get(i) == slot) return prizes[i];
        }
        return null;
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
