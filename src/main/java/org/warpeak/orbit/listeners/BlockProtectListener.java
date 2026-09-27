package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockProtectListener implements Listener {

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isDuelWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isDuelWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isDuelWorld(Player p) {
        return p.getWorld().getName().equals("duels_world");
    }
}