package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

public class PurgeTopHologramsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!p.hasPermission("orbit.admin")) {
            p.sendMessage("§cНет прав.");
            return true;
        }

        int radius = 150;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage("§cРадиус должен быть числом.");
                return true;
            }
        }

        p.sendMessage("§eЗачистка радиусом " + radius + " блоков вокруг тебя, подожди (может лагнуть на секунду)...");

        int removed = Orbit.get().getTopHologramManager().purgeNear(p.getLocation(), radius);
        p.sendMessage("§aУдалено сущностей TextDisplay: " + removed);

        Orbit.get().getTopHologramManager().loadFromConfig();
        p.sendMessage("§eАктуальные голограммы из config.yml пересозданы.");

        return true;
    }
}