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
        if (event.getTo() == null) return;
        if (!event.getTo().getWorld().getName().equals("duels_world")) return;

        Player p = event.getPlayer();
        if (!duelManager.isInDuel(p)) {
            event.setCancelled(true);
            p.sendMessage("§cТы не можешь телепортироваться в мир дуэлей.");
        }
    }
}