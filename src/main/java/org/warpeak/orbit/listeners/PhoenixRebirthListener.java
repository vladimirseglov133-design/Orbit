package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.duel.DuelManager;

/**
 * Перехватывает ЛЮБОЙ смертельный урон (не только PvP) у игроков в дуэли
 * и пытается воскресить их через способность "Возрождение Феникса" ДО того,
 * как урон применится и духель засчитает поражение/победу.
 */
public class PhoenixRebirthListener implements Listener {

    private final DuelManager duelManager;

    public PhoenixRebirthListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!duelManager.isInDuel(p)) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage < p.getHealth()) return; // урон не смертельный - не трогаем

        boolean revived = Orbit.get().getAbilityManager().tryPhoenixRebirth(p);
        if (revived) {
            event.setCancelled(true);
        }
    }
}