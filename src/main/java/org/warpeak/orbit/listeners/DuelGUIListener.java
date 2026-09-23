package org.warpeak.orbit.listeners;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.warpeak.orbit.duel.DuelManager;
import org.warpeak.orbit.gui.DuelSelectGUI;

public class DuelGUIListener implements Listener {

    private final DuelManager duelManager;

    public DuelGUIListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(DuelSelectGUI.TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        OfflinePlayer target = meta.getOwningPlayer();
        if (target == null || !target.isOnline()) return;

        Player sender = (Player) event.getWhoClicked();
        Player targetPlayer = (Player) target;

        if (sender.equals(targetPlayer)) return;

        duelManager.sendRequest(sender, targetPlayer);
        sender.closeInventory();
    }
}