package org.warpeak.orbit.cases;

import org.bukkit.Material;
import org.warpeak.orbit.Orbit;

public enum CasePrize {

    FISHERMAN("Рыбак", "§b", Material.LIGHT_BLUE_SHULKER_BOX, 25),
    GOBLIN("Гоблин", "§a", Material.GREEN_SHULKER_BOX, 25),
    WARRIOR("Воин", "§e", Material.YELLOW_SHULKER_BOX, 25),
    JESTER("Шут", "§d§lШ§e§lу§c§lт", Material.MAGENTA_SHULKER_BOX, 10),
    CLEANER("Уборщик", "§2", Material.LIME_SHULKER_BOX, 20),
    WAIFU("Тян", "§d", Material.PINK_SHULKER_BOX, 15),
    DEAD("Мёртвый", "§7§lМёртвый", Material.BLACK_SHULKER_BOX, 5),
    HERMIT("Отшельник", "§c", Material.RED_SHULKER_BOX, 20),
    SLAVE("Раб", "§b", Material.LIGHT_BLUE_SHULKER_BOX, 25),
    CLOWN("Клоун", "§aК§cл§aо§cу§aн", Material.LIME_SHULKER_BOX, 10),
    MECHANIC("Механик", "§f§lМ§7§lе§f§lх§7§lа§f§lн§7§lи§f§lк", Material.WHITE_SHULKER_BOX, 5),
    KING("Король", "§e§lК§6§lо§e§lр§6§lо§e§lл§6§lь", Material.YELLOW_SHULKER_BOX, 8),
    MUSHROOM("Гриб", "§f§lГ§c§lр§f§lи§c§lб", Material.WHITE_SHULKER_BOX, 10),
    TERMINATOR("Терминатор", "§4Т§cе§4р§cм§4и§cн§4а§cт§4о§cр", Material.GRAY_SHULKER_BOX, 5);

    private final String defaultRawName;
    private final String defaultDisplay;
    private final Material defaultMaterial;
    private final int defaultWeight;

    CasePrize(String rawName, String colorOrGradient, Material material, int weight) {
        this.defaultRawName = rawName;
        this.defaultMaterial = material;
        this.defaultWeight = weight;

        long colorCodes = colorOrGradient.chars().filter(c -> c == '§').count();
        this.defaultDisplay = colorCodes > 1 ? colorOrGradient : colorOrGradient + rawName;
    }

    private String configPath(String key) {
        return "case.prizes." + name() + "." + key;
    }

    public String getRawName() {
        Orbit plugin = Orbit.get();
        return plugin == null || plugin.getSettings() == null
                ? defaultRawName
                : plugin.getSettings().text(configPath("raw-name"), defaultRawName);
    }

    public String getColoredDisplay() {
        Orbit plugin = Orbit.get();
        return plugin == null || plugin.getSettings() == null
                ? defaultDisplay
                : plugin.getSettings().text(configPath("display-name"), defaultDisplay);
    }

    public Material getMaterial() {
        Orbit plugin = Orbit.get();
        return plugin == null || plugin.getSettings() == null
                ? defaultMaterial
                : plugin.getSettings().material(configPath("material"), defaultMaterial);
    }

    public int getWeight() {
        Orbit plugin = Orbit.get();
        return plugin == null || plugin.getSettings() == null
                ? defaultWeight
                : plugin.getSettings().integer(configPath("weight"), defaultWeight, 0, 1_000_000);
    }

    /** Final prefix format displayed in chat and above the player. */
    public String getLuckPermsPrefix() {
        Orbit plugin = Orbit.get();
        String format = plugin == null || plugin.getSettings() == null
                ? "§7[{prize}§7] "
                : plugin.getSettings().text("case.prefix-format", "&7[{prize}&7] ");
        return format.replace("{prize}", getColoredDisplay());
    }
}
