package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

public class CombatAbilityListener implements Listener {

    private final DuelManager duelManager;

    public CombatAbilityListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (!duelManager.isInDuel(victim) || !duelManager.isInDuel(attacker)) return;

        Duel duel = duelManager.getDuel(victim);
        if (duel == null || !duel.getOpponent(victim).equals(attacker)) return;

        boolean dodged = Orbit.get().getAbilityManager().tryDodge(victim, attacker, event);
        if (dodged) return;

        boolean instinctDodged = Orbit.get().getAbilityManager().tryUltraInstinctDodge(victim, attacker, event);
        if (instinctDodged) return;

        Orbit.get().getAbilityManager().onHit(attacker, victim, event);
    }
}