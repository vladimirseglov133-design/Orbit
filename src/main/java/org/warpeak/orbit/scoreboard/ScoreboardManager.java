package org.warpeak.orbit.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.stats.PlayerStats;

import java.util.List;

public class ScoreboardManager {

    private static final List<String> DEFAULT_LINES = List.of(
            "&7 ",
            "&eИгрок: &f{player}",
            "&eНаиграно: &f{time}",
            "&aПобед: &f{wins}",
            "&cСмертей: &f{deaths}",
            "&6Монеты: &f{coins}",
            "&8 ",
            "&borbit.mcmagic.space"
    );

    private final Orbit plugin;
    private int updateTaskId = -1;

    public ScoreboardManager(Orbit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
    }

    /** Restarts the updater and applies the current scoreboard settings immediately. */
    public void reload() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
        updateTaskId = -1;

        if (!plugin.getSettings().bool("scoreboard.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) remove(player);
            return;
        }

        int interval = plugin.getSettings().integer("scoreboard.update-interval-seconds", 1, 1, 60);
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 0L, interval * 20L).getTaskId();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (!plugin.getSettings().bool("scoreboard.enabled", true)) {
            remove(player);
            return;
        }

        PlayerStats stats = plugin.getStatsManager().getStats(player);
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective("orbit_stats");
        String title = plugin.getSettings().text("scoreboard.title", "&6&lORBIT DUELS");
        if (objective == null) {
            objective = board.registerNewObjective("orbit_stats", Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.setDisplayName(title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries()) board.resetScores(entry);

        List<String> lines = plugin.getSettings().textList("scoreboard.lines", DEFAULT_LINES);
        int score = Math.min(15, lines.size());
        for (String template : lines.stream().limit(15).toList()) {
            String line = template
                    .replace("{player}", player.getName())
                    .replace("{time}", stats.getFormattedPlayTime())
                    .replace("{wins}", Integer.toString(stats.kills))
                    .replace("{deaths}", Integer.toString(stats.deaths))
                    .replace("{coins}", Long.toString(stats.coins));
            setLine(objective, line, score--);
        }
    }

    private void setLine(Objective objective, String text, int score) {
        String uniquePrefix = switch (Math.floorMod(score, 10)) {
            case 0 -> ChatColor.RESET.toString();
            case 1 -> ChatColor.BLACK.toString();
            case 2 -> ChatColor.DARK_BLUE.toString();
            case 3 -> ChatColor.DARK_GREEN.toString();
            case 4 -> ChatColor.DARK_AQUA.toString();
            case 5 -> ChatColor.DARK_RED.toString();
            case 6 -> ChatColor.DARK_PURPLE.toString();
            case 7 -> ChatColor.GOLD.toString();
            case 8 -> ChatColor.GRAY.toString();
            default -> ChatColor.DARK_GRAY.toString();
        };
        objective.getScore(uniquePrefix + text).setScore(score);
    }

    public void remove(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
