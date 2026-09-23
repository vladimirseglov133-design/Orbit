package org.warpeak.orbit.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class DuelSelectGUI {

    public static final String TITLE = ChatColor.DARK_RED + "Выбери соперника";

    public static Inventory build(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + target.getName());
            head.setItemMeta(meta);

            inv.addItem(head);
        }
        return inv;
    }
}