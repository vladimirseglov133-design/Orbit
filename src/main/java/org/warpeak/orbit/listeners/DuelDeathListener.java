package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

public class DuelDeathListener implements Listener {

    private final DuelManager duelManager;

    public DuelDeathListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!duelManager.isInDuel(dead)) return;

        Duel duel = duelManager.getDuel(dead);
        if (duel == null) return;

        Player winner = duel.getOpponent(dead);

        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);

        try {
            duel.finishByDeath(winner, dead);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}