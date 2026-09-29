package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.warpeak.orbit.duel.DuelManager;

public class TeleportGuardListener implements Listener {

    private final DuelManager duelManager;

    public TeleportGuardListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        String duelWorld = org.warpeak.orbit.Orbit.get().getConfig().getString("arena.world", "duels_world");
        if (!event.getTo().getWorld().getName().equals(duelWorld)) return;

        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) {
            event.setCancelled(true);
            p.sendMessage(org.warpeak.orbit.Orbit.get().getSettings().text(
                    "messages.duel.world-access-denied", "&cТы не можешь телепортироваться в мир дуэлей."));
        }
    }
}