package org.warpeak.orbit.duel;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.arena.ArenaManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DuelManager {

    private final Orbit plugin;
    private final ArenaManager arenaManager;

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, Duel> activeDuels = new HashMap<>();
    private final Set<UUID> startingLock = new HashSet<>();
    private final Map<UUID, DuelManagerPendingRespawn> pendingRespawns = new HashMap<>();

    public DuelManager(Orbit plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public void sendRequest(Player sender, Player target) {
        if (isInDuel(sender) || isInDuel(target)) {
            sender.sendMessage(ChatColor.RED + "Игрок уже занят.");
            return;
        }

        if (isLocked(sender) || isLocked(target)) {
            sender.sendMessage(ChatColor.RED + "Подожди, дуэль уже запускается.");
            return;
        }

        if (pendingRequests.containsKey(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Ты уже отправил запрос, дождись ответа.");
            return;
        }

        UUID reverseRequester = getRequesterFor(sender);
        if (reverseRequester != null && reverseRequester.equals(target.getUniqueId())) {
            pendingRequests.remove(reverseRequester);
            sender.sendMessage(ChatColor.GREEN + "У вас встречные запросы - дуэль начинается сразу!");
            target.sendMessage(ChatColor.GREEN + "У вас встречные запросы - дуэль начинается сразу!");
            startDuel(sender, target);
            return;
        }

        pendingRequests.put(sender.getUniqueId(), target.getUniqueId());

        sender.sendMessage(ChatColor.GREEN + "Запрос отправлен игроку " + target.getName());

        TextComponent msg = new TextComponent(ChatColor.YELLOW + sender.getName() + " вызывает тебя на дуэль! ");
        TextComponent accept = new TextComponent(ChatColor.GREEN + "[Принять]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duelaccept"));
        TextComponent decline = new TextComponent(ChatColor.RED + " [Отклонить]");
        decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dueldecline"));

        msg.addExtra(accept);
        msg.addExtra(decline);
        target.spigot().sendMessage(msg);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.remove(sender.getUniqueId()) != null) {
                sender.sendMessage(ChatColor.RED + "Запрос дуэли истёк.");
            }
        }, 20L * 30);
    }

    public synchronized void accept(Player target) {
        UUID requesterId = getRequesterFor(target);
        if (requesterId == null) {
            target.sendMessage(ChatColor.RED + "Нет активных запросов.");
            return;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            pendingRequests.remove(requesterId);
            target.sendMessage(ChatColor.RED + "Игрок больше не в сети.");
            return;
        }

        if (isInDuel(requester) || isInDuel(target)) {
            pendingRequests.remove(requesterId);
            purgeAllRequestsInvolving(requester.getUniqueId());
            purgeAllRequestsInvolving(target.getUniqueId());
            target.sendMessage(ChatColor.RED + "Дуэль уже началась, запрос отменён.");
            return;
        }

        if (isLocked(requester) || isLocked(target)) {
            target.sendMessage(ChatColor.RED + "Дуэль уже запускается, подожди секунду.");
            return;
        }

        pendingRequests.remove(requesterId);
        purgeAllRequestsInvolving(requester.getUniqueId());
        purgeAllRequestsInvolving(target.getUniqueId());

        startDuel(requester, target);
    }

    public void decline(Player target) {
        UUID requesterId = getRequesterFor(target);
        if (requesterId == null) return;

        pendingRequests.remove(requesterId);
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester != null) requester.sendMessage(ChatColor.RED + target.getName() + " отклонил дуэль.");
        target.sendMessage(ChatColor.RED + "Дуэль отклонена.");
    }

    private UUID getRequesterFor(Player target) {
        for (Map.Entry<UUID, UUID> entry : pendingRequests.entrySet()) {
            if (entry.getValue().equals(target.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void purgeAllRequestsInvolving(UUID playerId) {
        pendingRequests.entrySet().removeIf(entry ->
                entry.getKey().equals(playerId) || entry.getValue().equals(playerId));
    }

    private boolean isLocked(Player p) {
        return startingLock.contains(p.getUniqueId());
    }

    private synchronized void startDuel(Player p1, Player p2) {
        if (isInDuel(p1) || isInDuel(p2) || isLocked(p1) || isLocked(p2)) {
            return;
        }

        startingLock.add(p1.getUniqueId());
        startingLock.add(p2.getUniqueId());

        try {
            Location arenaLoc = arenaManager.claimArena();

            Duel duel = new Duel(p1, p2, arenaLoc, arenaManager);
            activeDuels.put(p1.getUniqueId(), duel);
            activeDuels.put(p2.getUniqueId(), duel);

            duel.start();
        } finally {
            startingLock.remove(p1.getUniqueId());
            startingLock.remove(p2.getUniqueId());
        }
    }

    public boolean isInDuel(Player p) {
        return activeDuels.containsKey(p.getUniqueId());
    }

    public Duel getDuel(Player p) {
        return activeDuels.get(p.getUniqueId());
    }

    public void endDuel(Duel duel) {
        activeDuels.remove(duel.getPlayer1().getUniqueId());
        activeDuels.remove(duel.getPlayer2().getUniqueId());
        arenaManager.releaseArena(duel.getArenaLocation());
    }

    public void addPendingRespawn(Player p, Location loc, ItemStack[] inventory, GameMode mode) {
        pendingRespawns.put(p.getUniqueId(), new DuelManagerPendingRespawn(loc, inventory, mode));
    }

    public PendingRespawn consumePendingRespawn(Player p) {
        DuelManagerPendingRespawn internal = pendingRespawns.remove(p.getUniqueId());
        if (internal == null) return null;
        return new PendingRespawn(internal.location, internal.inventory, internal.gameMode);
    }

    public static class PendingRespawn {
        public final Location location;
        public final ItemStack[] inventory;
        public final GameMode gameMode;

        public PendingRespawn(Location location, ItemStack[] inventory, GameMode gameMode) {
            this.location = location;
            this.inventory = inventory;
            this.gameMode = gameMode;
        }
    }

    private static class DuelManagerPendingRespawn {
        final Location location;
        final ItemStack[] inventory;
        final GameMode gameMode;

        DuelManagerPendingRespawn(Location location, ItemStack[] inventory, GameMode gameMode) {
            this.location = location;
            this.inventory = inventory;
            this.gameMode = gameMode;
        }
    }
}