package org.warpeak.orbit.duel;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.arena.ArenaManager;
import org.warpeak.orbit.items.ItemsUtil;

public class Duel {

    private final Player player1;
    private final Player player2;
    private final Location arenaLocation;
    private final ArenaManager arenaManager;

    private Location p1SavedLocation, p2SavedLocation;
    private ItemStack[] p1SavedInventory, p2SavedInventory;
    private GameMode p1SavedGameMode, p2SavedGameMode;

    public Duel(Player p1, Player p2, Location arenaLocation, ArenaManager arenaManager) {
        this.player1 = p1;
        this.player2 = p2;
        this.arenaLocation = arenaLocation;
        this.arenaManager = arenaManager;
    }

    public void start() {
        p1SavedLocation = player1.getLocation();
        p2SavedLocation = player2.getLocation();
        p1SavedInventory = player1.getInventory().getContents();
        p2SavedInventory = player2.getInventory().getContents();
        p1SavedGameMode = player1.getGameMode();
        p2SavedGameMode = player2.getGameMode();

        sendStatus(player1, "§eПодготовка арены...");
        sendStatus(player2, "§eПодготовка арены...");

        boolean success = arenaManager.pasteArena(arenaLocation);

        if (!success) {
            player1.sendMessage(ChatColor.RED + "Ошибка создания арены! Дуэль отменена.");
            player2.sendMessage(ChatColor.RED + "Ошибка создания арены! Дуэль отменена.");
            Orbit.get().getDuelManager().endDuel(this);
            return;
        }

        sendStatus(player1, "§eТелепортация игроков...");
        sendStatus(player2, "§eТелепортация игроков...");

        FileConfiguration cfg = Orbit.get().getConfig();
        double s1x = cfg.getDouble("arena.spawn1.x", 5);
        double s1y = cfg.getDouble("arena.spawn1.y", 1);
        double s1z = cfg.getDouble("arena.spawn1.z", 0);
        double s2x = cfg.getDouble("arena.spawn2.x", -5);
        double s2y = cfg.getDouble("arena.spawn2.y", 1);
        double s2z = cfg.getDouble("arena.spawn2.z", 0);

        Location spawn1 = arenaLocation.clone().add(s1x, s1y, s1z);
        Location spawn2 = arenaLocation.clone().add(s2x, s2y, s2z);

        spawn1.setDirection(spawn2.toVector().subtract(spawn1.toVector()));
        spawn2.setDirection(spawn1.toVector().subtract(spawn2.toVector()));

        player1.teleport(spawn1);
        player2.teleport(spawn2);

        player1.setGameMode(GameMode.SURVIVAL);
        player2.setGameMode(GameMode.SURVIVAL);

        sendStatus(player1, "§eВыдача снаряжения...");
        sendStatus(player2, "§eВыдача снаряжения...");

        ItemsUtil.giveKit(player1);
        ItemsUtil.giveKit(player2);

        player1.setHealth(20);
        player2.setHealth(20);
        player1.setFoodLevel(20);
        player2.setFoodLevel(20);

        Orbit.get().getAbilityManager().startForPlayer(player1);
        Orbit.get().getAbilityManager().startForPlayer(player2);

        player1.sendTitle(ChatColor.RED + "Дуэль", ChatColor.GRAY + "против " + player2.getName(), 10, 40, 10);
        player2.sendTitle(ChatColor.RED + "Дуэль", ChatColor.GRAY + "против " + player1.getName(), 10, 40, 10);
    }

    private void restorePlayerState(Player p) {
        if (p == null || !p.isOnline()) return;

        ItemStack[] savedInv = p.equals(player1) ? p1SavedInventory : p2SavedInventory;
        GameMode savedMode = p.equals(player1) ? p1SavedGameMode : p2SavedGameMode;

        p.setGameMode(savedMode);
        p.getInventory().setContents(savedInv);
        p.setFoodLevel(20);

        // Гарантируем что компас всегда на месте после дуэли
        if (!p.getInventory().contains(org.bukkit.Material.COMPASS)) {
            p.getInventory().addItem(org.warpeak.orbit.items.ItemsUtil.createCompass());
        }
    }

    private void sendStatus(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    public void finish(Player winner, Player loser) {
        try {
            Orbit.get().getAbilityManager().clear(player1);
            Orbit.get().getAbilityManager().clear(player2);
        } catch (Exception e) {
            e.printStackTrace();
        }

        restorePlayerState(player1);
        restorePlayerState(player2);

        if (winner != null && loser != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + ChatColor.YELLOW + " победил в дуэли против " + loser.getName());
            giveRewards(winner, loser);
        }

        // Важно: убираем из активных дуэлей ДО принудительного килла,
        // чтобы DuelDeathListener не обработал эту смерть повторно
        Orbit.get().getDuelManager().endDuel(this);

        if (player1.isOnline()) player1.setHealth(0);
        if (player2.isOnline()) player2.setHealth(0);
    }

    public void finishByDeath(Player winner, Player loser) {
        try {
            Orbit.get().getAbilityManager().clear(player1);
            Orbit.get().getAbilityManager().clear(player2);
        } catch (Exception e) {
            e.printStackTrace();
        }

        restorePlayerState(winner);
        restorePlayerState(loser);

        if (winner != null && loser != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + ChatColor.YELLOW + " победил в дуэли против " + loser.getName());
            giveRewards(winner, loser);
        }

        // Убираем из активных дуэлей ДО килла победителя
        Orbit.get().getDuelManager().endDuel(this);

        if (winner != null && winner.isOnline()) {
            winner.setHealth(0);
        }
    }

    private void giveRewards(Player winner, Player loser) {
        try {
            Orbit.get().getStatsManager().addKill(winner);
            Orbit.get().getStatsManager().addDeath(loser);
            long coins = Orbit.get().getConfig().getLong("rewards.win-coins", 5);
            Orbit.get().getStatsManager().addCoins(winner, coins);
            Orbit.get().getScoreboardManager().update(winner);
            Orbit.get().getScoreboardManager().update(loser);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forceReturnToLobby(Player p) {
        if (p == null || !p.isOnline()) return;

        ItemStack[] savedInv = p.equals(player1) ? p1SavedInventory : p2SavedInventory;
        GameMode savedMode = p.equals(player1) ? p1SavedGameMode : p2SavedGameMode;

        Location target = getRestoreLocation(null); // всегда лобби, без варианта "исходное место"

        p.setGameMode(savedMode);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().setContents(savedInv);
        p.teleport(target);
    }

    private void applyRestore(Player p, Location loc, ItemStack[] inv, GameMode mode) {
        try {
            p.setGameMode(mode);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.getInventory().setContents(inv);
            p.teleport(loc);
        } catch (Exception e) {
            Orbit.get().getLogger().warning("Ошибка восстановления игрока " + p.getName() + ": " + e.getMessage());
        }
    }

    private Location getRestoreLocation(Location original) {
        FileConfiguration cfg = Orbit.get().getConfig();
        boolean returnOriginal = cfg.getBoolean("return-to-original-location", true);

        if (returnOriginal && original != null && original.getWorld() != null) {
            return original;
        }

        World lobbyWorld = Bukkit.getWorld(cfg.getString("lobby.world", "world"));
        if (lobbyWorld == null) {
            lobbyWorld = Bukkit.getWorlds().get(0);
        }
        double x = cfg.getDouble("lobby.x", 0);
        double y = cfg.getDouble("lobby.y", 100);
        double z = cfg.getDouble("lobby.z", 0);
        float yaw = (float) cfg.getDouble("lobby.yaw", 0);
        float pitch = (float) cfg.getDouble("lobby.pitch", 0);
        return new Location(lobbyWorld, x, y, z, yaw, pitch);
    }

    public Player getPlayer1() { return player1; }
    public Player getPlayer2() { return player2; }
    public Location getArenaLocation() { return arenaLocation; }

    public Player getOpponent(Player p) {
        return p.equals(player1) ? player2 : player1;
    }
}