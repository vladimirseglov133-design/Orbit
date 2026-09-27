package org.warpeak.orbit.commands;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

public class SetCaseCommand implements CommandExecutor {

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

        Block target = player.getTargetBlockExact(6);
        if (target == null || (target.getType() != Material.CHEST
                && target.getType() != Material.ENDER_CHEST
                && target.getType() != Material.TRAPPED_CHEST)) {
            player.sendMessage("§cСмотри на сундук или эндер-сундук (в пределах 6 блоков).");
            return true;
        }

        long price = 100;
        if (args.length >= 1) {
            try {
                price = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cЦена должна быть числом. Пример: /setcase 150");
                return true;
            }
        }

        Orbit.get().getCaseManager().setCaseLocation(target.getLocation(), price);
        player.sendMessage("§aКейс префиксов установлен! Цена: " + price + " монет.");
        return true;
    }
}