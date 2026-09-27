package org.warpeak.orbit.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.cases.CasePrize;
import org.warpeak.orbit.cases.PrefixMenuGUI;

public class PrefixMenuListener implements Listener {

    // Сделано публичным, чтобы CompassRespawnListener мог проверить наличие предмета
    public static final NamespacedKey ITEM_KEY_PUBLIC = new NamespacedKey(Orbit.get(), "prefix_menu_item");

    public static ItemStack createMenuItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dПрефиксы");
        meta.getPersistentDataContainer().set(ITEM_KEY_PUBLIC, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ITEM_KEY_PUBLIC, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(Orbit.get(), () -> {
            if (!p.isOnline()) return;
            boolean hasItem = false;
            for (ItemStack item : p.getInventory().getContents()) {
                if (isMenuItem(item)) { hasItem = true; break; }
            }
            if (!hasItem) {
                p.getInventory().addItem(createMenuItem());
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Orbit.get().getPrefixManager().saveAndUnload(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isMenuItem(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.openInventory(PrefixMenuGUI.build(player));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(PrefixMenuGUI.TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == PrefixMenuGUI.UNEQUIP_SLOT) {
            if (Orbit.get().getPrefixManager().getEquipped(player) != null) {
                Orbit.get().getPrefixManager().unequip(player);
                player.sendMessage("§7Префикс снят.");
                player.openInventory(PrefixMenuGUI.build(player));
            }
            return;
        }

        CasePrize prize = PrefixMenuGUI.getPrizeBySlot(slot);
        if (prize == null) return;

        if (!Orbit.get().getPrefixManager().isUnlocked(player, prize)) {
            player.sendMessage("§cВы ещё не получили этот приз из кейса!");
            return;
        }

        CasePrize equipped = Orbit.get().getPrefixManager().getEquipped(player);
        if (equipped == prize) {
            Orbit.get().getPrefixManager().unequip(player);
            player.sendMessage("§7Префикс снят.");
        } else {
            Orbit.get().getPrefixManager().equip(player, prize);
            player.sendMessage("§aНадет префикс: " + prize.getColoredDisplay());
        }

        player.openInventory(PrefixMenuGUI.build(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(PrefixMenuGUI.TITLE)) {
            event.setCancelled(true);
        }
    }
}