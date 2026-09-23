package org.warpeak.orbit.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.items.ItemsUtil;

public class CompassRespawnListener implements Listener {

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();

        // Выдаём через тик, после того как сервер закончит стандартную обработку респавна
        Bukkit.getScheduler().runTaskLater(Orbit.get(), () -> {
            if (!p.isOnline()) return;

            if (!p.getInventory().contains(Material.COMPASS)) {
                p.getInventory().addItem(ItemsUtil.createCompass());
            }

            if (!hasPrefixMenuItem(p)) {
                p.getInventory().addItem(PrefixMenuListener.createMenuItem());
            }
        }, 2L);
    }

    private boolean hasPrefixMenuItem(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer()
                    .has(PrefixMenuListener.ITEM_KEY_PUBLIC)) {
                return true;
            }
        }
        return false;
    }
}