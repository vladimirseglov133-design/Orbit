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

    public VoidFallListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) return;
        if (p.getLocation().getY() >= Orbit.get().getSettings().integer("arena.void-y-limit", 50)) return;

        boolean revived = Orbit.get().getAbilityManager().tryPhoenixRebirth(p);
        if (revived) {
            Duel duel = duelManager.getDuel(p);
            if (duel != null) {
                int reviveOffset = Orbit.get().getSettings().integer(
                        "abilities.phoenix-rebirth.void-revive-offset", 20, 1, 128);
                Location safe = duel.getArenaLocation().clone().add(0, reviveOffset, 0);
                p.teleport(safe);
            }
            return;
        }

        p.setHealth(0); // засчитывается как смерть -> поражение
    }
}