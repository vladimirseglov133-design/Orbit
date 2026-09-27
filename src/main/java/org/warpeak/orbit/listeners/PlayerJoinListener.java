package org.warpeak.orbit.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.warpeak.orbit.items.ItemsUtil;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.getInventory().contains(Material.COMPASS)) {
            p.getInventory().addItem(ItemsUtil.createCompass());
        }
    }
}