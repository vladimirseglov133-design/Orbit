package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.hologram.TopHologram;

public class SetTopHologramCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cНедостаточно прав.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cИспользование: /" + label + " <kills|coins>");
            return true;
        }

        TopHologram.Type type;
        if (args[0].equalsIgnoreCase("kills")) {
            type = TopHologram.Type.KILLS;
        } else if (args[0].equalsIgnoreCase("coins")) {
            type = TopHologram.Type.COINS;
        } else {
            player.sendMessage("§cНеверный тип. Используйте: kills или coins");
            return true;
        }

        Orbit.get().getTopHologramManager().setHologram(type, player.getLocation(), true);
        player.sendMessage("§aГолограмма топа (" + args[0] + ") установлена на текущую позицию!");
        return true;
    }
}