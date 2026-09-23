package org.warpeak.orbit.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.cases.CaseGUI;
import org.warpeak.orbit.cases.CasePrize;
import org.warpeak.orbit.cases.CaseRouletteAnimation;

public class CaseGUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(CaseGUI.TITLE)) return;

        event.setCancelled(true);
        if (event.getRawSlot() != CaseGUI.BUY_SLOT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        handlePurchase(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(CaseGUI.TITLE)) {
            event.setCancelled(true);
        }
    }

    private void handlePurchase(Player player) {
        long price = Orbit.get().getCaseManager().getPrice();
        long coins = Orbit.get().getStatsManager().getStats(player).coins;

        if (coins < price) {
            player.sendMessage("§cНедостаточно монет! Нужно: " + price);
            player.closeInventory();
            return;
        }

        if (Orbit.get().getCaseManager().isOpening(player)) {
            player.sendMessage("§cДождитесь окончания текущей анимации!");
            player.closeInventory();
            return;
        }

        if (!Orbit.get().getCaseManager().tryCharge(player)) {
            player.sendMessage("§cОшибка списания монет.");
            return;
        }

        Orbit.get().getScoreboardManager().update(player);

        CasePrize winner = Orbit.get().getCaseManager().rollPrize();
        Orbit.get().getCaseManager().giveReward(player, winner);

        player.closeInventory();

        Location chestLoc = Orbit.get().getCaseManager().getCaseLocation();
        if (chestLoc != null && chestLoc.getWorld() != null) {
            CaseRouletteAnimation.play(player, chestLoc, winner);
        } else {
            player.sendMessage("§aВы получили приз: " + winner.getColoredDisplay());
        }
    }
}