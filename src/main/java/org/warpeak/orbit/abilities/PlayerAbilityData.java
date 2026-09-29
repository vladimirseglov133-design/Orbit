package org.warpeak.orbit.abilities;

import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public int monsterAuraTask = -1;
    public int monsterAuraEndTask = -1;
    public int monsterWaveTask = -1;
    public int archangelAuraTask = -1;
    public int archangelHealTask = -1;
    public int archangelEndTask = -1;
    public int territoryParticleTask = -1;
    public int territoryEffectTask = -1;
    public int territoryBoomTask = -1;
    public int territoryDomeRemoveTask = -1;
    public int ultraInstinctAuraTask = -1;

    public final Set<Integer> expandingRingTasks = new HashSet<>();

    // Блоки купола домена (для восстановления после исчезновения)
    public final List<BlockState> territoryBlockStates = new ArrayList<>();

    public boolean hasAbility(Ability ability) {
        return ability != null && (ability == tier1 || ability == tier2 || ability == tier3 || ability == tier4);
    }
}