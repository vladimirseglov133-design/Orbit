package org.warpeak.orbit.items;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.warpeak.orbit.Orbit;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemsUtil {

    private ItemsUtil() { }

    public static ItemStack createCompass() {
        Orbit plugin = Orbit.get();
        ItemStack item = new ItemStack(plugin.getSettings().material("items.compass.material", Material.COMPASS));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getSettings().text("items.compass.display-name", "&6Дуэльный компас"));
            meta.setLore(plugin.getSettings().textList("items.compass.lore",
                    List.of("&7ПКМ — открыть меню дуэлей")));
            meta.getPersistentDataContainer().set(compassKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isCompassMenuItem(ItemStack item) {
        if (item == null) return false;
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(compassKey(), PersistentDataType.BYTE)) return true;
        return Orbit.get().getSettings().material("items.compass.material", Material.COMPASS) == Material.COMPASS
                && item.getType() == Material.COMPASS;
    }

    public static boolean hasCompassMenuItem(org.bukkit.entity.Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCompassMenuItem(item)) return true;
        }
        return isCompassMenuItem(player.getInventory().getItemInOffHand());
    }

    private static NamespacedKey compassKey() {
        return new NamespacedKey(Orbit.get(), "duel_compass_menu");
    }

    public static void giveKit(org.bukkit.entity.Player player) {
        Orbit plugin = Orbit.get();
        PlayerInventory inventory = player.getInventory();
        if (plugin.getSettings().bool("duel-kit.clear-inventory", true)) {
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
        }

        inventory.setHelmet(createConfiguredItem("duel-kit.armor.helmet", Material.DIAMOND_HELMET, 1));
        inventory.setChestplate(createConfiguredItem("duel-kit.armor.chestplate", Material.DIAMOND_CHESTPLATE, 1));
        inventory.setLeggings(createConfiguredItem("duel-kit.armor.leggings", Material.DIAMOND_LEGGINGS, 1));
        inventory.setBoots(createConfiguredItem("duel-kit.armor.boots", Material.DIAMOND_BOOTS, 1));

        ConfigurationSection hotbar = plugin.getConfig().getConfigurationSection("duel-kit.hotbar");
        if (hotbar != null) for (String slotKey : hotbar.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                if (slot < 0 || slot > 8) continue;
                Material fallback = defaultHotbarMaterial(slot);
                ItemStack item = createConfiguredItem("duel-kit.hotbar." + slotKey, fallback,
                        defaultHotbarAmount(slot));
                if (slot == 8 && item.getType() == Material.FISHING_ROD) markStrongFishingRod(item);
                inventory.setItem(slot, item);
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Некорректный слот duel-kit.hotbar." + slotKey + ".");
            }
        }

        ItemStack offhand = createConfiguredItem("duel-kit.offhand", Material.SHIELD, 1);
        inventory.setItemInOffHand(offhand);

        for (Map<?, ?> itemData : plugin.getConfig().getMapList("duel-kit.extra-items")) {
            ItemStack item = createConfiguredItem(itemData);
            if (item != null) inventory.addItem(item);
        }
    }

    private static Material defaultHotbarMaterial(int slot) {
        return switch (slot) {
            case 0 -> Material.DIAMOND_SWORD;
            case 1 -> Material.DIAMOND_AXE;
            case 2 -> Material.BOW;
            case 3 -> Material.ARROW;
            case 4 -> Material.GOLDEN_APPLE;
            case 5 -> Material.COOKED_BEEF;
            case 6 -> Material.ENDER_PEARL;
            case 7 -> Material.WIND_CHARGE;
            case 8 -> Material.FISHING_ROD;
            default -> Material.AIR;
        };
    }

    private static int defaultHotbarAmount(int slot) {
        return switch (slot) {
            case 3 -> 4;
            case 4 -> 2;
            case 5 -> 32;
            case 6 -> 8;
            case 7 -> 16;
            default -> 1;
        };
    }

    private static ItemStack createConfiguredItem(String path, Material fallback, int defaultAmount) {
        Orbit plugin = Orbit.get();
        Material material = plugin.getSettings().material(path + ".material", fallback);
        int amount = plugin.getSettings().integer(path + ".amount", defaultAmount, 1, material.getMaxStackSize());
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = plugin.getConfig().getString(path + ".display-name");
        if (displayName != null) meta.setDisplayName(plugin.getSettings().text(path + ".display-name", displayName));
        List<String> lore = plugin.getConfig().getStringList(path + ".lore");
        if (!lore.isEmpty()) meta.setLore(plugin.getSettings().textList(path + ".lore", lore));
        ConfigurationSection enchantments = plugin.getConfig().getConfigurationSection(path + ".enchantments");
        applyEnchantments(meta, enchantments == null ? null : enchantments.getValues(false));
        applyPotionType(meta, plugin.getConfig().getString(path + ".potion-type"), path);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createConfiguredItem(Map<?, ?> data) {
        Object materialValue = data.get("material");
        if (materialValue == null) return null;
        Material material = Material.matchMaterial(materialValue.toString());
        if (material == null) {
            Orbit.get().getLogger().warning("Неизвестный материал в duel-kit.extra-items: " + materialValue);
            return null;
        }

        int amount = 1;
        Object amountValue = data.get("amount");
        if (amountValue instanceof Number number) {
            amount = Math.max(1, Math.min(material.getMaxStackSize(), number.intValue()));
        }
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Object name = data.get("display-name");
        if (name != null) meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name.toString()));
        Object loreValue = data.get("lore");
        if (loreValue instanceof List<?> lore) {
            meta.setLore(lore.stream().map(Object::toString)
                    .map(line -> org.bukkit.ChatColor.translateAlternateColorCodes('&', line)).toList());
        }
        Object enchantmentValue = data.get("enchantments");
        if (enchantmentValue instanceof Map<?, ?> enchantments) applyEnchantments(meta, enchantments);
        Object potionValue = data.get("potion-type");
        if (potionValue != null) applyPotionType(meta, potionValue.toString(), "duel-kit.extra-items");
        item.setItemMeta(meta);
        return item;
    }

    private static void applyEnchantments(ItemMeta meta, Map<?, ?> enchantments) {
        if (enchantments == null) return;
        for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
            String name = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
            if (enchantment == null || !(entry.getValue() instanceof Number level)) {
                Orbit.get().getLogger().warning("Неизвестное зачарование в конфигурации: " + entry.getKey());
                continue;
            }
            int safeLevel = Math.max(1, Math.min(255, level.intValue()));
            meta.addEnchant(enchantment, safeLevel, true);
        }
    }

    private static void applyPotionType(ItemMeta meta, String configuredType, String path) {
        if (!(meta instanceof PotionMeta potionMeta) || configuredType == null || configuredType.isBlank()) return;
        try {
            potionMeta.setBasePotionType(PotionType.valueOf(configuredType.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            Orbit.get().getLogger().warning("Неизвестный тип зелья в '" + path + ".potion-type': " + configuredType);
        }
    }

    public static ItemStack createFishingRod() {
        ItemStack rod = createConfiguredItem("duel-kit.fishing-rod", Material.FISHING_ROD, 1);
        markStrongFishingRod(rod);
        return rod;
    }

    private static void markStrongFishingRod(ItemStack rod) {
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(Orbit.get(), "strong_fishing_rod"),
                PersistentDataType.BYTE,
                (byte) 1
        );
        rod.setItemMeta(meta);
    }
}
