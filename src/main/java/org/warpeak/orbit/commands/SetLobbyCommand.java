package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

public class SetLobbyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.isOp()) {
            p.sendMessage("§cТолько для операторов.");
            return true;
        }

        Orbit.get().getConfig().set("lobby.world", p.getWorld().getName());
        Orbit.get().getConfig().set("lobby.x", p.getLocation().getX());
        Orbit.get().getConfig().set("lobby.y", p.getLocation().getY());
        Orbit.get().getConfig().set("lobby.z", p.getLocation().getZ());
        Orbit.get().getConfig().set("lobby.yaw", (double) p.getLocation().getYaw());
        Orbit.get().getConfig().set("lobby.pitch", (double) p.getLocation().getPitch());
        Orbit.get().saveConfig();

        p.sendMessage("§aЛобби установлено: " +
                (int) p.getLocation().getX() + ", " + (int) p.getLocation().getY() + ", " + (int) p.getLocation().getZ());

        return true;
    }
}