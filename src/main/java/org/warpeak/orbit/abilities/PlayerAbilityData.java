package org.warpeak.orbit.abilities;

import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

public class PlayerAbilityData {

    public Ability tier1;
    public Ability tier2;
    public Ability tier3;
    public Ability tier4;

    public int hitCounterTier1 = 0;
    public int hitCounterTier2 = 0;
    public int hitCounterTier3 = 0;

    // Уклонение
    public boolean dodgeArmed = false;
    public long dodgeArmedUntil = 0;
    // FIX (баг #1): кулдаун выставляется В МОМЕНТ АКТИВАЦИИ (activateDodge),
    // а не после истечения окна вооружения. Поэтому здесь также храним
    // момент последней активации — для отладочной атрибуции в логах.
    public long dodgeActivatedAt = 0;
    public long dodgeCooldownUntil = 0;

    // ТИР 3
    public long healBurstCooldownUntil = 0;
    public long knockbackWaveCooldownUntil = 0;
    public long teleportSwapCooldownUntil = 0;

    // Возрождение Феникса — работает 1 раз за дуэль
    public boolean phoenixUsed = false;

    // ТИР 4
    public long monsterAuraCooldownUntil = 0;
    public long archangelCooldownUntil = 0;
    public long territoryCooldownUntil = 0;
    public long ultraInstinctCooldownUntil = 0;

    public boolean monsterAuraActive = false;
    public boolean ultraInstinctActive = false;
    /**
     * Момент последнего уворота Ультра Инстинктом (System.currentTimeMillis()).
     * Внутренний интервал (см. ULTRA_INSTINCT_DODGE_INTERNAL_CD_MS в
     * AbilityManager) не позволяет цепочке уворотов при множественных хитах
     * в одном тике визуально выглядеть как неуязвимость.
     */
    public long ultraInstinctLastDodgeAt = 0;

    public int monsterAuraTask = -1;
    public int monsterWaveTask = -1;
    public int territoryParticleTask = -1;
    public int territoryEffectTask = -1;
    public int territoryBoomTask = -1;
    public int territoryDomeRemoveTask = -1;
    public int ultraInstinctAuraTask = -1;

    // Блоки купола домена (для восстановления после исчезновения)
    public final List<BlockState> territoryBlockStates = new ArrayList<>();

    // === Отладочная атрибуция "почему урон не прошёл" (System.currentTimeMillis) ===
    /** Момент последнего Teleport Swap — для обоих участников обмена. */
    public long lastSwapAt = 0;
    /** Момент последнего случайного телепорта уворота (Dodge тир3 / Ультра Инстинкт). */
    public long lastDodgeTeleportAt = 0;

    public boolean hasAbility(Ability ability) {
        return ability != null && (ability == tier1 || ability == tier2 || ability == tier3 || ability == tier4);
    }
}