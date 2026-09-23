package org.warpeak.orbit.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.cases.CaseGUI;

public class CaseChestListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        if (type != Material.CHEST && type != Material.ENDER_CHEST && type != Material.TRAPPED_CHEST) return;

        if (!Orbit.get().getCaseManager().isCaseBlock(event.getClickedBlock().getLocation())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (Orbit.get().getCaseManager().isOpening(player)) {
            player.sendMessage("§cДождитесь окончания текущей анимации!");
            return;
        }

        long price = Orbit.get().getCaseManager().getPrice();
        long coins = Orbit.get().getStatsManager().getStats(player).coins;

        player.openInventory(CaseGUI.build(price, coins));
    }
}