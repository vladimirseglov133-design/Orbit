package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.duel.DuelManager;

public class SwapHandsAbilityListener implements Listener {

    private final DuelManager duelManager;

    public SwapHandsAbilityListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) return;

        event.setCancelled(true);

        if (p.isSneaking()) {
            // Shift + F = ульта (тир 4)
            Orbit.get().getAbilityManager().onActivateUltimate(p);
        } else {
            // Обычный F = тир 3
            Orbit.get().getAbilityManager().onActivate(p);
        }
    }
}