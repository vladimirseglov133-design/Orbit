package org.warpeak.orbit.cases;

import org.bukkit.Material;

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

    private final String rawName;
    private final String coloredDisplay;
    private final Material material;
    private final int weight;

    CasePrize(String rawName, String colorOrGradient, Material material, int weight) {
        this.rawName = rawName;
        this.material = material;
        this.weight = weight;

        long colorCodes = colorOrGradient.chars().filter(c -> c == '§').count();
        if (colorCodes > 1) {
            // уже готовый градиент (Шут, Мёртвый, Клоун, Механик, Король, Гриб, Терминатор)
            this.coloredDisplay = colorOrGradient;
        } else {
            this.coloredDisplay = colorOrGradient + rawName;
        }
    }

    public String getRawName() { return rawName; }
    public String getColoredDisplay() { return coloredDisplay; }
    public Material getMaterial() { return material; }
    public int getWeight() { return weight; }

    /** Финальный формат префикса, который увидят все в чате/над головой */
    public String getLuckPermsPrefix() {
        return "§7[" + coloredDisplay + "§7] ";
    }
}