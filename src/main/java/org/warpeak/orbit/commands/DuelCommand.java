package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.duel.DuelManager;

public class DuelCommand implements CommandExecutor {

    private final DuelManager duelManager;
    private final boolean accept;

    public DuelCommand(DuelManager duelManager, boolean accept) {
        this.duelManager = duelManager;
        this.accept = accept;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (accept) duelManager.accept(p);
        else duelManager.decline(p);

        return true;
    }
}