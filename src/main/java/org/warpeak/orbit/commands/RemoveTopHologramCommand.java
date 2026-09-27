package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.hologram.TopHologram;

public class RemoveTopHologramCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /" + label + " <kills|coins>");
            return true;
        }

        TopHologram.Type type;
        if (args[0].equalsIgnoreCase("kills")) {
            type = TopHologram.Type.KILLS;
        } else if (args[0].equalsIgnoreCase("coins")) {
            type = TopHologram.Type.COINS;
        } else {
            sender.sendMessage("§cНеверный тип. Используйте: kills или coins");
            return true;
        }

        Orbit.get().getTopHologramManager().removeHologram(type);
        sender.sendMessage("§aГолограмма топа (" + args[0] + ") удалена!");
        return true;
    }
}