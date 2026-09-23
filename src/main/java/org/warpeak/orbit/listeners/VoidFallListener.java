package org.warpeak.orbit.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

public class VoidFallListener implements Listener {

    private final DuelManager duelManager;
    private final int limitY;

    public VoidFallListener(DuelManager duelManager, int limitY) {
        this.duelManager = duelManager;
        this.limitY = limitY;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) return;
        if (p.getLocation().getY() >= limitY) return;

        boolean revived = Orbit.get().getAbilityManager().tryPhoenixRebirth(p);
        if (revived) {
            Duel duel = duelManager.getDuel(p);
            if (duel != null) {
                Location safe = duel.getArenaLocation().clone().add(0, 20, 0);
                p.teleport(safe);
            }
            return;
        }

        p.setHealth(0); // засчитывается как смерть -> поражение
    }
}