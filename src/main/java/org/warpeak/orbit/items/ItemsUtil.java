package org.warpeak.orbit.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionType;

import java.util.List;

public class ItemsUtil {

    public static ItemStack createCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Дуэльный компас");
        meta.setLore(List.of(ChatColor.GRAY + "ПКМ — открыть меню дуэлей"));
        item.setItemMeta(meta);
        return item;
    }

    public static void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(null);

        inv.setHelmet(createArmorPiece(Material.DIAMOND_HELMET));
        inv.setChestplate(createArmorPiece(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(createArmorPiece(Material.DIAMOND_LEGGINGS));
        inv.setBoots(createArmorPiece(Material.DIAMOND_BOOTS));

        inv.setItem(0, createEnchantedSword());
        inv.setItem(1, new ItemStack(Material.DIAMOND_AXE));
        inv.setItem(2, new ItemStack(Material.BOW));
        inv.setItem(3, new ItemStack(Material.ARROW, 4));
        inv.setItem(4, new ItemStack(Material.GOLDEN_APPLE, 2));
        inv.setItem(5, new ItemStack(Material.COOKED_BEEF, 32));
        inv.setItem(6, new ItemStack(Material.ENDER_PEARL, 8));
        inv.setItem(7, new ItemStack(Material.WIND_CHARGE, 16));
        inv.setItem(8, createFishingRod());

        inv.setItemInOffHand(new ItemStack(Material.SHIELD));

        inv.addItem(createEnchantedMace());
        inv.addItem(new ItemStack(Material.TRIDENT));
        inv.addItem(createSplashPotion(PotionType.STRENGTH));
        inv.addItem(createSplashPotion(PotionType.HEALING));
        inv.addItem(new ItemStack(Material.WIND_CHARGE, 16));
        inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
    }

    private static ItemStack createArmorPiece(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createEnchantedSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 1, true);
        sword.setItemMeta(meta);
        return sword;
    }

    private static ItemStack createEnchantedMace() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.addEnchant(Enchantment.WIND_BURST, 2, true);
        mace.setItemMeta(meta);
        return mace;
    }

    private static ItemStack createSplashPotion(PotionType type) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(type);
        potion.setItemMeta(meta);
        return potion;
    }

    public static ItemStack createFishingRod() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "Удочка притяжения");
        meta.setLore(List.of(ChatColor.GRAY + "Сильно притягивает противника!"));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(org.warpeak.orbit.Orbit.get(), "strong_fishing_rod"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1
        );
        rod.setItemMeta(meta);
        return rod;
    }
}