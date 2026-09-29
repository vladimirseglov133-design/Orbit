package org.warpeak.orbit.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.warpeak.orbit.Orbit;

import java.util.List;

public final class OrbitCommand implements CommandExecutor, TabCompleter {

    private final Orbit plugin;

    public OrbitCommand(Orbit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("orbit.admin.config")) {
                sender.sendMessage(plugin.getSettings().text("messages.no-permission", "&cНедостаточно прав."));
                return true;
            }

            try {
                plugin.reloadPluginConfig();
                sender.sendMessage(plugin.getSettings().text(
                        "messages.config-reloaded", "&aКонфигурация Orbit перезагружена."));
            } catch (Exception exception) {
                plugin.getLogger().severe("Не удалось перезагрузить config.yml: " + exception.getMessage());
                sender.sendMessage(plugin.getSettings().text(
                        "messages.config-reload-failed", "&cОшибка перезагрузки config.yml. Проверьте консоль."));
            }
            return true;
        }

        sender.sendMessage(plugin.getSettings().text("messages.command-usage", "&eИспользование: /orbit reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("orbit.admin.config")) {
            return List.of("reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
