package org.warpeak.orbit.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.warpeak.orbit.Orbit;

public class StatsScoreboardListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Orbit.get().getScoreboardManager().update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Orbit.get().getStatsManager().saveAndRemove(event.getPlayer());
    }
}