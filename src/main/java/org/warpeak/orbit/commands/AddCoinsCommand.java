package org.warpeak.orbit.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

public class AddCoinsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbit.admin.coins") && !sender.isOp()) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /" + label + " <ник> <количество>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cИгрок '" + args[0] + "' не найден или не в сети.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cКоличество должно быть числом.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cКоличество должно быть больше нуля.");
            return true;
        }

        boolean remove = label.equalsIgnoreCase("removecoins");

        if (remove) {
            Orbit.get().getStatsManager().removeCoins(target, amount);
            sender.sendMessage("§aУ игрока " + target.getName() + " отнято §e" + amount + " §aмонет.");
            target.sendMessage("§cУ вас отнято §e" + amount + " §cмонет администратором.");
        } else {
            Orbit.get().getStatsManager().addCoins(target, amount);
            sender.sendMessage("§aИгроку " + target.getName() + " выдано §e" + amount + " §aмонет.");
            target.sendMessage("§aВам выдано §e" + amount + " §aмонет!");
        }

        Orbit.get().getScoreboardManager().update(target);
        return true;
    }
}