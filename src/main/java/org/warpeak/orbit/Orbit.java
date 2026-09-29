package org.warpeak.orbit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.warpeak.orbit.abilities.AbilityManager;
import org.warpeak.orbit.arena.ArenaManager;
import org.warpeak.orbit.cases.CaseManager;
import org.warpeak.orbit.cases.PrefixManager;
import org.warpeak.orbit.config.OrbitSettings;
import org.warpeak.orbit.commands.*;
import org.warpeak.orbit.duel.DuelManager;
import org.warpeak.orbit.hologram.TopHologramManager;
import org.warpeak.orbit.listeners.*;
import org.warpeak.orbit.scoreboard.ScoreboardManager;
import org.warpeak.orbit.stats.StatsManager;

public final class Orbit extends JavaPlugin {

    private static Orbit instance;
    private DuelManager duelManager;
    private ArenaManager arenaManager;
    private AbilityManager abilityManager;
    private StatsManager statsManager;
    private ScoreboardManager scoreboardManager;
    private CaseManager caseManager;
    private PrefixManager prefixManager;
    private TopHologramManager topHologramManager;
    private OrbitSettings settings;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        settings = new OrbitSettings(this);

        arenaManager = new ArenaManager(this);
        arenaManager.setupWorld();


        setWorldSpawnToLobby();

        duelManager = new DuelManager(this, arenaManager);
        abilityManager = new AbilityManager(this);
        statsManager = new StatsManager(this);
        scoreboardManager = new ScoreboardManager(this);
        scoreboardManager.start();
        prefixManager = new PrefixManager(this);
        caseManager = new CaseManager(this);
        topHologramManager = new TopHologramManager(this);
        getServer().getPluginManager().registerEvents(new StrongFishingRodListener(), this);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            topHologramManager.loadFromConfig();
            topHologramManager.startAutoUpdate();
        }, 40L);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new CompassListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new DuelGUIListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new DuelDeathListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new BlockProtectListener(), this);
        getServer().getPluginManager().registerEvents(new TeleportGuardListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new VoidFallListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new CombatAbilityListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new SwapHandsAbilityListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new StatsScoreboardListener(), this);
        getServer().getPluginManager().registerEvents(new CompassRespawnListener(), this);
        getServer().getPluginManager().registerEvents(new CaseChestListener(), this);
        getServer().getPluginManager().registerEvents(new CaseGUIListener(), this);
        getServer().getPluginManager().registerEvents(new PrefixMenuListener(), this);
        getServer().getPluginManager().registerEvents(new PhoenixRebirthListener(duelManager), this);

        getCommand("duelaccept").setExecutor(new DuelCommand(duelManager, true));
        getCommand("dueldecline").setExecutor(new DuelCommand(duelManager, false));
        getCommand("duelleave").setExecutor(new DuelLeaveCommand(duelManager));
        getCommand("setlobby").setExecutor(new SetLobbyCommand());
        getCommand("setcase").setExecutor(new SetCaseCommand());
        getCommand("addcoins").setExecutor(new AddCoinsCommand());
        getCommand("removecoins").setExecutor(new AddCoinsCommand());
        getCommand("settophologram").setExecutor(new SetTopHologramCommand());
        getCommand("removetophologram").setExecutor(new RemoveTopHologramCommand());
        getCommand("purgetopholograms").setExecutor(new PurgeTopHologramsCommand());
        OrbitCommand orbitCommand = new OrbitCommand(this);
        getCommand("orbit").setExecutor(orbitCommand);
        getCommand("orbit").setTabCompleter(orbitCommand);

        getLogger().info("Orbit Duel Plugin включен!");
    }

    @Override
    public void onDisable() {
        if (statsManager != null) statsManager.saveAll();
        if (prefixManager != null) prefixManager.saveAll();
        if (topHologramManager != null) topHologramManager.removeAll();
        if (abilityManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                abilityManager.clear(player);
            }
        }
        getLogger().info("Orbit Duel Plugin выключен!");
    }

    /** Reloads live settings and rebuilds managers that cache presentation data. */
    public void reloadPluginConfig() {
        String oldArenaWorld = getConfig().getString("arena.world", "duels_world");
        String oldSchematic = getConfig().getString("arena.schematic", "schematics/arena.schem");

        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (settings != null) settings.clearWarnings();

        setWorldSpawnToLobby();
        if (scoreboardManager != null) scoreboardManager.reload();
        if (caseManager != null) caseManager.reloadFromConfig();
        if (topHologramManager != null) topHologramManager.reloadFromConfig();

        String newArenaWorld = getConfig().getString("arena.world", "duels_world");
        String newSchematic = getConfig().getString("arena.schematic", "schematics/arena.schem");
        boolean arenaWorldUnchanged = oldArenaWorld.equals(newArenaWorld);
        if (arenaManager != null) arenaManager.reloadRuntimeSettings(arenaWorldUnchanged);
        if (!arenaWorldUnchanged) {
            getLogger().warning("Изменение arena.world применится после перезапуска сервера.");
        } else if (!oldSchematic.equals(newSchematic)) {
            getLogger().info("Новая схематика арены загружена из " + newSchematic + ".");
        }
    }

    private void setWorldSpawnToLobby() {
        String worldName = getConfig().getString("lobby.world", "world");
        World world = getServer().getWorld(worldName);
        if (world == null) {
            getLogger().warning("Мир лобби '" + worldName + "' не найден, спавн не установлен.");
            return;
        }

        double x = getConfig().getDouble("lobby.x", 0);
        double y = getConfig().getDouble("lobby.y", 100);
        double z = getConfig().getDouble("lobby.z", 0);

        world.setSpawnLocation((int) x, (int) y, (int) z);
        getLogger().info("Мировой спавн установлен на точку лобби: " + x + ", " + y + ", " + z);
    }

    public static Orbit get() { return instance; }
    public OrbitSettings getSettings() { return settings; }
    public DuelManager getDuelManager() { return duelManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public CaseManager getCaseManager() { return caseManager; }
    public PrefixManager getPrefixManager() { return prefixManager; }
    public TopHologramManager getTopHologramManager() { return topHologramManager; }
}