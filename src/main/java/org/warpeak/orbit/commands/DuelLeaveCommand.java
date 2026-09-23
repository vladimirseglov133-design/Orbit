package org.warpeak.orbit.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

public class DuelLeaveCommand implements CommandExecutor {

    private final DuelManager duelManager;

    public DuelLeaveCommand(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (!duelManager.isInDuel(p)) {
            p.sendMessage(ChatColor.RED + "Ты не в дуэли.");
            return true;
        }

        Duel duel = duelManager.getDuel(p);
        Player winner = duel.getOpponent(p);
        duel.finish(winner, p);

        return true;
    }
}