package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

public class PlayerQuitListener implements Listener {

    private final DuelManager duelManager;

    public PlayerQuitListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) return;

        Duel duel = duelManager.getDuel(p);
        Player winner = duel.getOpponent(p);
        duel.finish(winner, p);
    }
}