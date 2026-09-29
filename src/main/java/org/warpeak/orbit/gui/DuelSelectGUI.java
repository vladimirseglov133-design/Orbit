package org.warpeak.orbit.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.warpeak.orbit.Orbit;

public final class DuelSelectGUI {

    private DuelSelectGUI() { }

    public static String getTitle() {
        return Orbit.get().getSettings().text("duel-gui.title", "&4Выбери соперника");
    }

    private static int getSize() {
        int configured = Orbit.get().getSettings().integer("duel-gui.size", 54, 9, 54);
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }

    public static Inventory build(Player viewer) {
        Orbit plugin = Orbit.get();
        Inventory inventory = Bukkit.createInventory(null, getSize(), getTitle());
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (!(head.getItemMeta() instanceof SkullMeta meta)) continue;
            meta.setOwningPlayer(target);
            meta.setDisplayName(plugin.getSettings().text("duel-gui.player-name", "&e{player}")
                    .replace("{player}", target.getName()));
            head.setItemMeta(meta);
            inventory.addItem(head);
        }
        return inventory;
    }
}
