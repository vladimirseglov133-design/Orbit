package org.warpeak.orbit.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.warpeak.orbit.duel.DuelManager;
import org.warpeak.orbit.gui.DuelSelectGUI;

public class CompassListener implements Listener {

    private final DuelManager duelManager;

    public CompassListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.getPlayer().openInventory(DuelSelectGUI.build(event.getPlayer()));
    }
}