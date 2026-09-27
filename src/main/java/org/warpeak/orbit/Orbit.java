package org.warpeak.orbit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.warpeak.orbit.abilities.AbilityManager;
import org.warpeak.orbit.abilities.DebugLog;
import org.warpeak.orbit.arena.ArenaManager;
import org.warpeak.orbit.cases.CaseManager;
import org.warpeak.orbit.cases.PrefixManager;
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

    @Override
    public void onEnable() {
        instance = this;

        // Отпечаток сборки в логе: видно, какой код реально загружен
        // (без пересборки jar + рестарта сервера поведение не меняется).
        DebugLog.log(this, "BUILD",
                "build=" + DebugLog.BUILD
                        + " api=" + Bukkit.getBukkitVersion()
                        + " java=" + System.getProperty("java.version"));

        saveDefaultConfig();

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
        getServer().getPluginManager().registerEvents(
                new VoidFallListener(duelManager, getConfig().getInt("arena.void-y-limit", 50)), this);
        getServer().getPluginManager().registerEvents(new CombatAbilityListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new SwapHandsAbilityListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new StatsScoreboardListener(), this);
        getServer().getPluginManager().registerEvents(new CompassRespawnListener(), this);
        getServer().getPluginManager().registerEvents(new CaseChestListener(), this);
        getServer().getPluginManager().registerEvents(new CaseGUIListener(), this);
        getServer().getPluginManager().registerEvents(new PrefixMenuListener(), this);
        getServer().getPluginManager().registerEvents(new PhoenixRebirthListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new DamageDebugListener(duelManager), this);
        getServer().getPluginManager().registerEvents(new SwapDamageEnforcerListener(duelManager), this);

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


        getLogger().info("Orbit Duel Plugin включен!");
    }

    @Override
    public void onDisable() {
        if (statsManager != null) statsManager.saveAll();
        if (prefixManager != null) prefixManager.saveAll();
        if (topHologramManager != null) topHologramManager.removeAll();
        getLogger().info("Orbit Duel Plugin выключен!");
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
    public DuelManager getDuelManager() { return duelManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public CaseManager getCaseManager() { return caseManager; }
    public PrefixManager getPrefixManager() { return prefixManager; }
    public TopHologramManager getTopHologramManager() { return topHologramManager; }
}