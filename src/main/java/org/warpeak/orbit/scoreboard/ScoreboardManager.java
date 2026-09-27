package org.warpeak.orbit.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.stats.PlayerStats;

public class ScoreboardManager {

    private final Orbit plugin;

    public ScoreboardManager(Orbit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 0L, 20L);
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
        }
    }

    public void update(Player p) {
        PlayerStats stats = plugin.getStatsManager().getStats(p);

        Scoreboard board = p.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(board);
        }

        Objective objective = board.getObjective("orbit_stats");
        if (objective == null) {
            objective = board.registerNewObjective("orbit_stats", Criteria.DUMMY,
                    ChatColor.GOLD + "" + ChatColor.BOLD + "ORBIT DUELS");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Очищаем старые строки
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        int score = 10;

        setLine(objective, ChatColor.GRAY + "                    ", score--);
        setLine(objective, ChatColor.YELLOW + "Игрок: " + ChatColor.WHITE + p.getName(), score--);
        setLine(objective, ChatColor.YELLOW + "Наиграно: " + ChatColor.WHITE + stats.getFormattedPlayTime(), score--);
        setLine(objective, ChatColor.GREEN + "Побед: " + ChatColor.WHITE + stats.kills, score--);
        setLine(objective, ChatColor.RED + "Смертей: " + ChatColor.WHITE + stats.deaths, score--);
        setLine(objective, ChatColor.GOLD + "Монеты: " + ChatColor.WHITE + stats.coins, score--);
        setLine(objective, ChatColor.DARK_GRAY + "                     ", score--);
        setLine(objective, ChatColor.AQUA + "orbit.mcmagic.space", score--);
    }

    // Уникальные "строки" через невидимые цветовые коды, чтобы избежать дублей
    private final String[] uniquePrefixes = {
            ChatColor.RESET.toString(),
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString(),
            ChatColor.DARK_PURPLE.toString(),
            ChatColor.GOLD.toString(),
            ChatColor.GRAY.toString(),
            ChatColor.DARK_GRAY.toString()
    };

    private int prefixIndex = 0;

    private void setLine(Objective objective, String text, int score) {
        String uniqueText = uniquePrefixes[Math.abs((score) % uniquePrefixes.length)] + text;
        Team team = objective.getScoreboard().getTeam("line" + score);
        if (team == null) {
            team = objective.getScoreboard().registerNewTeam("line" + score);
            team.addEntry(uniqueText);
        }
        objective.getScore(uniqueText).setScore(score);
    }

    public void remove(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}