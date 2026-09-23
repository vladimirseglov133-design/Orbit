package org.warpeak.orbit.abilities;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Ability {

    // ===== ТИР 1 =====
    HEART_BOOST(AbilityTier.TIER1, "§c+2 Сердца"),
    DAMAGE_BOOST(AbilityTier.TIER1, "§6+1 Урон"),
    HUNGER_DRAIN(AbilityTier.TIER1, "§eПохититель голода"),
    JUMP_BOOST(AbilityTier.TIER1, "§bПрыгучесть"),
    SPEED_BOOST(AbilityTier.TIER1, "§bСкорость"),

    // ===== ТИР 2 =====
    HEALTH_DRAIN(AbilityTier.TIER2, "§4Похититель здоровья"),
    POISON_TOUCH(AbilityTier.TIER2, "§2Ядовитое прикосновение"),
    ANTI_KNOCKBACK(AbilityTier.TIER2, "§7Стойкость"),
    SUPER_SPEED(AbilityTier.TIER2, "§bСверхскорость"),

    // ===== ТИР 3 =====
    BERSERK(AbilityTier.TIER3, "§4Берсерк"),
    STUN_HITS(AbilityTier.TIER3, "§5Оглушающие удары"),
    DODGE(AbilityTier.TIER3, "§dУклонение §7(клавиша F)"),
    HEAL_BURST(AbilityTier.TIER3, "§aВсплеск исцеления §7(клавиша F)"),
    KNOCKBACK_WAVE(AbilityTier.TIER3, "§fУдарная волна §7(клавиша F)"),
    TELEPORT_SWAP(AbilityTier.TIER3, "§bОбмен местами §7(клавиша F)"),
    PHOENIX_REBIRTH(AbilityTier.TIER3, "§6§lВозрождение Феникса"),

    // ===== ТИР 4 (Финальный) =====
    AURA_MONSTER(AbilityTier.TIER4, "§4Аура Монстра §7(Shift+F)"),
    PROTECTION_ARCHANGEL(AbilityTier.TIER4, "§fЗащита Архангела §7(Shift+F)"),
    TERRITORY_EXPANSION(AbilityTier.TIER4, "§0Расширение территории §7(Shift+F)"),
    ULTRA_INSTINCT(AbilityTier.TIER4, "§b§lУльтра Инстинкт §7(Shift+F)");

    private final AbilityTier tier;
    private final String displayName;

    Ability(AbilityTier tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    public AbilityTier getTier() { return tier; }
    public String getDisplayName() { return displayName; }

    public static List<Ability> byTier(AbilityTier tier) {
        return Arrays.stream(values()).filter(a -> a.tier == tier).collect(Collectors.toList());
    }
}