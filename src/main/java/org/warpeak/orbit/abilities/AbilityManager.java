package org.warpeak.orbit.abilities;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.warpeak.orbit.Orbit;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AbilityManager {

    private final Orbit plugin;
    private final Random random = new Random();
    private final Map<UUID, PlayerAbilityData> dataMap = new HashMap<>();

    private final NamespacedKey MAX_HEALTH_MOD_KEY;
    private final NamespacedKey KNOCKBACK_MOD_KEY;

    public AbilityManager(Orbit plugin) {
        this.plugin = plugin;
        this.MAX_HEALTH_MOD_KEY = new NamespacedKey(plugin, "orbit_max_health");
        this.KNOCKBACK_MOD_KEY = new NamespacedKey(plugin, "orbit_knockback");
    }

    private long cooldownMillis(String path, int defaultSeconds) {
        return plugin.getSettings().secondsToMillis(path, defaultSeconds);
    }

    private int durationTicks(String path, int defaultSeconds) {
        return plugin.getSettings().secondsToTicks(path, defaultSeconds);
    }

    private int intervalTicks(String path, int defaultTicks) {
        return plugin.getSettings().integer(path, defaultTicks, 1, 12_000);
    }

    private float particleScale(String path, float defaultScale) {
        return plugin.getSettings().decimalFloat(path, defaultScale, 1.0f, 4.0f);
    }

    public PlayerAbilityData getData(Player p) {
        return dataMap.get(p.getUniqueId());
    }

    // ==================== Выдача способностей ====================

    public void startForPlayer(Player p) {
        PlayerAbilityData data = new PlayerAbilityData();
        dataMap.put(p.getUniqueId(), data);

        grantTier(p, data, AbilityTier.TIER1);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && dataMap.get(p.getUniqueId()) == data) {
                grantTier(p, data, AbilityTier.TIER2);
            }
        }, durationTicks("abilities.tier-unlocks.tier2-seconds", 60));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && dataMap.get(p.getUniqueId()) == data) {
                grantTier(p, data, AbilityTier.TIER3);
            }
        }, durationTicks("abilities.tier-unlocks.tier3-seconds", 120));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && dataMap.get(p.getUniqueId()) == data) {
                grantTier(p, data, AbilityTier.TIER4);
            }
        }, durationTicks("abilities.tier-unlocks.tier4-seconds", 180));
    }

    private void grantTier(Player p, PlayerAbilityData data, AbilityTier tier) {
        List<Ability> pool = Ability.byTier(tier);
        int totalWeight = 0;
        for (Ability ability : pool) {
            totalWeight += plugin.getSettings().integer(
                    "abilities.weights." + tier.name().toLowerCase(Locale.ROOT) + "." + ability.name(),
                    1, 0, 1_000_000);
        }
        if (totalWeight <= 0) {
            plugin.getLogger().warning("Все веса способностей для " + tier + " равны 0; тир пропущен.");
            return;
        }

        int roll = random.nextInt(totalWeight);
        Ability chosen = null;
        int cursor = 0;
        for (Ability ability : pool) {
            cursor += plugin.getSettings().integer(
                    "abilities.weights." + tier.name().toLowerCase(Locale.ROOT) + "." + ability.name(),
                    1, 0, 1_000_000);
            if (roll < cursor) {
                chosen = ability;
                break;
            }
        }
        if (chosen == null) return;

        switch (tier) {
            case TIER1 -> data.tier1 = chosen;
            case TIER2 -> data.tier2 = chosen;
            case TIER3 -> data.tier3 = chosen;
            case TIER4 -> data.tier4 = chosen;
        }

        applyPassiveEffect(p, chosen);
        announce(p, chosen);
    }

    private void applyPassiveEffect(Player p, Ability ability) {
        switch (ability) {
            case HEART_BOOST -> {
                double extraHealth = plugin.getSettings().decimal("abilities.passives.heart-boost.extra-health", 4.0, 0.0, 40.0);
                addMaxHealth(p, extraHealth);
                p.setHealth(Math.min(p.getHealth() + extraHealth, p.getAttribute(Attribute.MAX_HEALTH).getValue()));
            }
            case JUMP_BOOST -> p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.jump-boost.amplifier", 1, 0, 255), true, false, false));
            case SPEED_BOOST -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.speed-boost.amplifier", 0, 0, 255), true, false, false));
            case ANTI_KNOCKBACK -> setKnockbackResistance(p,
                    plugin.getSettings().decimal("abilities.passives.anti-knockback.resistance", 1.0, 0.0, 1.0));
            case SUPER_SPEED -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.super-speed.amplifier", 1, 0, 255), true, false, false));
            default -> { }
        }
    }

    /**
     * Восстанавливает пассивную скорость/прыгучесть игрока после того,
     * как временный эффект ультимативки (Аура Монстра / Ультра Инстинкт) закончился.
     * Без этого метода removePotionEffect(SPEED) сносил бы и базовую пассивку тоже.
     */
    private void reapplyPassiveEffects(Player p, PlayerAbilityData data) {
        if (!p.isOnline()) return;

        if (data.hasAbility(Ability.JUMP_BOOST)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.jump-boost.amplifier", 1, 0, 255), true, false, false));
        }

        if (data.hasAbility(Ability.SUPER_SPEED)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.super-speed.amplifier", 1, 0, 255), true, false, false));
        } else if (data.hasAbility(Ability.SPEED_BOOST)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    plugin.getSettings().integer("abilities.passives.speed-boost.amplifier", 0, 0, 255), true, false, false));
        } else {
            p.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    private void addMaxHealth(Player p, double amount) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeModifierIfExists(attr, MAX_HEALTH_MOD_KEY);
        attr.addModifier(new AttributeModifier(MAX_HEALTH_MOD_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void setKnockbackResistance(Player p, double amount) {
        AttributeInstance attr = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr == null) return;
        removeModifierIfExists(attr, KNOCKBACK_MOD_KEY);
        attr.addModifier(new AttributeModifier(KNOCKBACK_MOD_KEY, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void removeModifierIfExists(AttributeInstance attr, NamespacedKey key) {
        attr.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .ifPresent(attr::removeModifier);
    }

    private void announce(Player p, Ability ability) {
        String text = plugin.getSettings().text("messages.new-ability", "&7Новая способность: {ability}")
                .replace("{ability}", ability.getDisplayName());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.announcement-sound", Sound.ENTITY_PLAYER_LEVELUP), 1f, 1.5f);

        int repeatDelay = plugin.getSettings().integer("abilities.announcement-repeat-delay-seconds", 2, 0, 60);
        if (repeatDelay > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
            }, repeatDelay * 20L);
        }
    }

    // ==================== Очистка после дуэли ====================

    public void clear(Player p) {
        PlayerAbilityData data = dataMap.remove(p.getUniqueId());
        if (data == null) return;

        if (data.monsterAuraTask != -1) Bukkit.getScheduler().cancelTask(data.monsterAuraTask);
        if (data.monsterAuraEndTask != -1) Bukkit.getScheduler().cancelTask(data.monsterAuraEndTask);
        if (data.monsterWaveTask != -1) Bukkit.getScheduler().cancelTask(data.monsterWaveTask);
        if (data.archangelAuraTask != -1) Bukkit.getScheduler().cancelTask(data.archangelAuraTask);
        if (data.archangelHealTask != -1) Bukkit.getScheduler().cancelTask(data.archangelHealTask);
        if (data.archangelEndTask != -1) Bukkit.getScheduler().cancelTask(data.archangelEndTask);
        for (int taskId : data.expandingRingTasks) Bukkit.getScheduler().cancelTask(taskId);
        data.expandingRingTasks.clear();
        if (data.territoryParticleTask != -1) Bukkit.getScheduler().cancelTask(data.territoryParticleTask);
        if (data.territoryEffectTask != -1) Bukkit.getScheduler().cancelTask(data.territoryEffectTask);
        if (data.territoryBoomTask != -1) Bukkit.getScheduler().cancelTask(data.territoryBoomTask);
        if (data.territoryDomeRemoveTask != -1) Bukkit.getScheduler().cancelTask(data.territoryDomeRemoveTask);
        if (data.ultraInstinctAuraTask != -1) Bukkit.getScheduler().cancelTask(data.ultraInstinctAuraTask);

        restoreDomeBlocks(data);

        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WITHER);
        p.removePotionEffect(PotionEffectType.BLINDNESS);

        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) removeModifierIfExists(maxHealthAttr, MAX_HEALTH_MOD_KEY);

        AttributeInstance kbAttr = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) removeModifierIfExists(kbAttr, KNOCKBACK_MOD_KEY);
    }

    // ==================== Обработка ударов ====================

    public void onHit(Player attacker, Player victim, EntityDamageByEntityEvent event) {
        PlayerAbilityData atkData = getData(attacker);
        if (atkData == null) return;

        double bonusDamage = 0;

        if (atkData.hasAbility(Ability.DAMAGE_BOOST)) {
            bonusDamage += plugin.getSettings().decimal("abilities.passives.damage-boost.bonus-damage", 1.0, 0.0, 40.0);
        }

        if (atkData.hasAbility(Ability.BERSERK)) {
            double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            double missingRatio = 1 - (attacker.getHealth() / max);
            bonusDamage += missingRatio * plugin.getSettings().decimal(
                    "abilities.passives.berserk.max-bonus-damage", 4.0, 0.0, 40.0);
        }

        if (bonusDamage > 0) {
            event.setDamage(event.getDamage() + bonusDamage);
        }

        if (atkData.hasAbility(Ability.HUNGER_DRAIN)) {
            atkData.hitCounterTier1++;
            int hits = plugin.getSettings().integer("abilities.passives.hunger-drain.hits-per-trigger", 3, 1, 100);
            if (atkData.hitCounterTier1 % hits == 0) {
                int food = plugin.getSettings().integer("abilities.passives.hunger-drain.food-per-trigger", 1, 0, 20);
                victim.setFoodLevel(Math.max(0, victim.getFoodLevel() - food));
                attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + food));
            }
        }

        if (atkData.hasAbility(Ability.HEALTH_DRAIN)) {
            atkData.hitCounterTier2++;
            int hits = plugin.getSettings().integer("abilities.passives.health-drain.hits-per-trigger", 3, 1, 100);
            if (atkData.hitCounterTier2 % hits == 0) {
                double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
                double healAmount = plugin.getSettings().decimal("abilities.passives.health-drain.heal-amount", 2.0, 0.0, 40.0);
                attacker.setHealth(Math.min(max, attacker.getHealth() + healAmount));
                int particleCount = plugin.getSettings().integer("abilities.passives.health-drain.particle-count", 5, 0, 100);
                attacker.getWorld().spawnParticle(Particle.HEART,
                        attacker.getLocation().add(0, 1.5, 0), particleCount, 0.2, 0.2, 0.2, 0);
            }
        }

        if (atkData.hasAbility(Ability.POISON_TOUCH)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                    durationTicks("abilities.passives.poison-touch.duration-seconds", 3),
                    plugin.getSettings().integer("abilities.passives.poison-touch.amplifier", 0, 0, 255)));
        }

        if (atkData.hasAbility(Ability.STUN_HITS)) {
            atkData.hitCounterTier3++;
            int hits = plugin.getSettings().integer("abilities.passives.stun-hits.hits-per-trigger", 5, 1, 100);
            if (atkData.hitCounterTier3 % hits == 0) {
                int duration = durationTicks("abilities.passives.stun-hits.duration-seconds", 2);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration,
                        plugin.getSettings().integer("abilities.passives.stun-hits.blindness-amplifier", 0, 0, 255)));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration,
                        plugin.getSettings().integer("abilities.passives.stun-hits.slowness-amplifier", 1, 0, 255)));
            }
        }

        if (atkData.monsterAuraActive) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    durationTicks("abilities.monster-aura.hit-wither-duration-seconds", 2),
                    plugin.getSettings().integer("abilities.monster-aura.hit-wither-amplifier", 1, 0, 255)));
            spawnMonsterHitBurst(victim);
        }
    }

    // ==================== Уклонение (ручное, тир3) ====================

    public boolean tryDodge(Player victim, EntityDamageByEntityEvent event) {
        PlayerAbilityData data = getData(victim);
        if (data == null || !data.hasAbility(Ability.DODGE)) return false;
        if (!data.dodgeArmed || System.currentTimeMillis() > data.dodgeArmedUntil) return false;

        long now = System.currentTimeMillis();
        event.setCancelled(true);
        data.dodgeArmed = false;
        data.dodgeCooldownUntil = Math.max(data.dodgeCooldownUntil,
                now + plugin.getSettings().secondsToMillis("abilities.dodge.cooldown-seconds", 5));

        performRandomHorizontalTeleport(victim);
        victim.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(plugin.getSettings().text("abilities.dodge.success-message", "&dУклонение сработало!")));
        return true;
    }

    // ==================== Ультра Инстинкт: пассивный уворот (тир4) ====================

    public boolean tryUltraInstinctDodge(Player victim, EntityDamageByEntityEvent event) {
        PlayerAbilityData data = getData(victim);
        if (data == null || !data.ultraInstinctActive) return false;
        if (random.nextDouble() >= plugin.getSettings().decimal(
                "abilities.ultra-instinct.dodge-chance", 0.5, 0.0, 1.0)) return false;

        event.setCancelled(true);
        performRandomHorizontalTeleport(victim);
        victim.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.ultra-instinct.dodge-message", "&b&lУльтра Инстинкт: уклонение!")));
        victim.playSound(victim.getLocation(), plugin.getSettings().sound(
                "abilities.ultra-instinct.dodge-sound", Sound.ENTITY_ENDERMAN_TELEPORT), 0.6f, 1.8f);
        return true;
    }

    private void performRandomHorizontalTeleport(Player victim) {
        Location from = victim.getLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = plugin.getSettings().decimal("abilities.dodge.teleport-distance", 1.0, 0.25, 8.0);
        Location target = from.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        target.setDirection(from.getDirection());

        int particles = plugin.getSettings().integer("abilities.dodge.teleport-particle-count", 20, 0, 500);
        victim.getWorld().spawnParticle(Particle.SMOKE, from.clone().add(0, 1, 0),
                particles, 0.3, 0.5, 0.3, 0.02);
        victim.teleport(target);
        victim.getWorld().spawnParticle(Particle.SMOKE, target.clone().add(0, 1, 0),
                particles, 0.3, 0.5, 0.3, 0.02);
    }

    // ==================== Возрождение Феникса (тир3, реактивная) ====================

    /**
     * Пытается воскресить игрока при смертельном уроне.
     * Возвращает true, если воскрешение произошло (событие урона нужно отменить снаружи).
     */
    public boolean tryPhoenixRebirth(Player p) {
        PlayerAbilityData data = getData(p);
        if (data == null) return false;
        if (!data.hasAbility(Ability.PHOENIX_REBIRTH)) return false;
        if (data.phoenixUsed) return false;

        data.phoenixUsed = true;

        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthFraction = plugin.getSettings().decimal(
                "abilities.phoenix-rebirth.health-fraction", 0.5, 0.05, 1.0);
        p.setHealth(Math.max(1.0, max * healthFraction));
        p.setFireTicks(0);

        spawnPhoenixRebirthEffect(p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.phoenix-rebirth.activation-message", "&6&l🔥 ВОЗРОЖДЕНИЕ ФЕНИКСА! 🔥")));

        return true;
    }

    private void spawnPhoenixRebirthEffect(Player p) {
        World w = p.getWorld();
        Location loc = p.getLocation().add(0, 1, 0);

        w.spawnParticle(Particle.FLAME, loc,
                plugin.getSettings().integer("abilities.phoenix-rebirth.flame-particle-count", 60, 0, 1000),
                0.6, 1.0, 0.6, 0.05);
        float scale = particleScale("abilities.visuals.burst-dust-scale", 1.8f);
        w.spawnParticle(Particle.DUST, loc,
                plugin.getSettings().integer("abilities.phoenix-rebirth.dust-particle-count", 40, 0, 1000),
                0.6, 1.0, 0.6, 0, new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.phoenix-rebirth.colors.fire", Color.fromRGB(255, 80, 0)), scale));
        w.spawnParticle(Particle.DUST, loc,
                plugin.getSettings().integer("abilities.phoenix-rebirth.dust-particle-count", 40, 0, 1000),
                0.6, 1.0, 0.6, 0, new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.phoenix-rebirth.colors.gold", Color.fromRGB(255, 215, 0)), scale));
        w.spawnParticle(Particle.DUST, loc,
                plugin.getSettings().integer("abilities.phoenix-rebirth.orange-particle-count", 30, 0, 1000),
                0.6, 1.0, 0.6, 0, new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.phoenix-rebirth.colors.orange", Color.fromRGB(255, 140, 0)), scale));
        w.spawnParticle(Particle.LAVA, loc,
                plugin.getSettings().integer("abilities.phoenix-rebirth.lava-particle-count", 8, 0, 1000),
                0.4, 0.6, 0.4, 0);

        w.playSound(loc, plugin.getSettings().sound("abilities.phoenix-rebirth.sound-primary", Sound.ENTITY_BLAZE_HURT), 1f, 1f);
        w.playSound(loc, plugin.getSettings().sound("abilities.phoenix-rebirth.sound-secondary", Sound.ITEM_FIRECHARGE_USE), 1f, 0.7f);
        w.playSound(loc, plugin.getSettings().sound("abilities.phoenix-rebirth.sound-success", Sound.ENTITY_PLAYER_LEVELUP), 1f, 0.8f);
    }

    // ==================== Активация ТИР 3 через F ====================

    public void onActivate(Player p) {
        PlayerAbilityData data = getData(p);
        if (data == null) return;

        if (data.hasAbility(Ability.DODGE)) {
            activateDodge(p, data);
        } else if (data.hasAbility(Ability.HEAL_BURST)) {
            activateHealBurst(p, data);
        } else if (data.hasAbility(Ability.KNOCKBACK_WAVE)) {
            activateKnockbackWave(p, data);
        } else if (data.hasAbility(Ability.TELEPORT_SWAP)) {
            activateTeleportSwap(p, data);
        }
    }

    // ==================== Активация ТИР 4 через Shift+F ====================

    public void onActivateUltimate(Player p) {
        PlayerAbilityData data = getData(p);
        if (data == null || data.tier4 == null) return;

        switch (data.tier4) {
            case AURA_MONSTER -> activateMonsterAura(p, data);
            case PROTECTION_ARCHANGEL -> activateArchangelProtection(p, data);
            case TERRITORY_EXPANSION -> activateTerritoryExpansion(p, data);
            case ULTRA_INSTINCT -> activateUltraInstinct(p, data);
            default -> { }
        }
    }

    private void activateDodge(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.dodgeCooldownUntil) {
            long secondsLeft = (data.dodgeCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.dodge.cooldown-message", "&cУклонение перезаряжается: {seconds}с")
                    .replace("{seconds}", Long.toString(secondsLeft))));
            return;
        }

        int windowTicks = plugin.getSettings().secondsToTicks("abilities.dodge.window-seconds", 1);
        data.dodgeCooldownUntil = now + plugin.getSettings().secondsToMillis("abilities.dodge.cooldown-seconds", 5);
        data.dodgeArmed = true;
        data.dodgeArmedUntil = now + windowTicks * 50L;

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.dodge.armed-message", "&dУклонение активно {seconds} сек!")
                .replace("{seconds}", Integer.toString(plugin.getSettings().integer(
                        "abilities.dodge.window-seconds", 1, 1, 60)))));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.dodge.activation-sound", Sound.ITEM_TRIDENT_RIPTIDE_1), 0.6f, 1.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (data.dodgeArmed) data.dodgeArmed = false;
        }, windowTicks);
    }

    private void activateHealBurst(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.healBurstCooldownUntil) {
            long secondsLeft = (data.healBurstCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.heal-burst.cooldown-message", "&cПерезарядка: {seconds}с")
                    .replace("{seconds}", Long.toString(secondsLeft))));
            return;
        }

        int effectDuration = durationTicks("abilities.heal-burst.effect-duration-seconds", 5);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, effectDuration,
                plugin.getSettings().integer("abilities.heal-burst.resistance-amplifier", 0, 0, 255)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, effectDuration,
                plugin.getSettings().integer("abilities.heal-burst.regeneration-amplifier", 1, 0, 255)));

        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healAmount = plugin.getSettings().decimal("abilities.heal-burst.heal-amount", 4.0, 0.0, 40.0);
        p.setHealth(Math.min(max, p.getHealth() + healAmount));

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.heal-burst.activation-message", "&aВсплеск исцеления!")));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0),
                plugin.getSettings().integer("abilities.heal-burst.particle-count", 10, 0, 500), 0.3, 0.3, 0.3, 0);

        data.healBurstCooldownUntil = now + cooldownMillis("abilities.heal-burst.cooldown-seconds", 20);
    }

    // ==================== Ударная волна (ТИР 3) ====================

    private void activateKnockbackWave(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.knockbackWaveCooldownUntil) {
            long secondsLeft = (data.knockbackWaveCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(plugin.getSettings().text("abilities.knockback-wave.cooldown-message",
                            "&cПерезарядка: {seconds}с").replace("{seconds}", Long.toString(secondsLeft))));
            return;
        }

        data.knockbackWaveCooldownUntil = now + cooldownMillis("abilities.knockback-wave.cooldown-seconds", 15);
        Location center = p.getLocation().clone();
        double maxRadius = plugin.getSettings().decimal("abilities.knockback-wave.radius", 3.0, 0.5, 32.0);

        p.playSound(center, plugin.getSettings().sound(
                "abilities.knockback-wave.activation-sound", Sound.ENTITY_EVOKER_CAST_SPELL),
                (float) plugin.getSettings().decimal("abilities.knockback-wave.sound-volume", 1.0, 0.0, 2.0),
                (float) plugin.getSettings().decimal("abilities.knockback-wave.sound-pitch", 1.2, 0.5, 2.0));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(plugin.getSettings().text("abilities.knockback-wave.activation-message", "&fУдарная волна!")));

        Color innerColor = plugin.getSettings().color(
                "abilities.knockback-wave.colors.inner", Color.fromRGB(180, 180, 180));
        Color outerColor = plugin.getSettings().color("abilities.knockback-wave.colors.outer", Color.WHITE);
        startExpandingRing(p, data, center, maxRadius, innerColor, outerColor,
                () -> applyKnockbackWaveEffect(p, center, maxRadius));
    }

    private void applyKnockbackWaveEffect(Player p, Location center, double radius) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target) || target.equals(p)) continue;
            if (target.getLocation().distance(center) > radius + 0.5) continue;

            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.01) {
                direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            target.damage(plugin.getSettings().decimal("abilities.knockback-wave.damage", 6.0, 0.0, 2048.0), p);
            direction.normalize().multiply(plugin.getSettings().decimal(
                    "abilities.knockback-wave.knockback-horizontal", 1.4, 0.0, 10.0));
            direction.setY(plugin.getSettings().decimal(
                    "abilities.knockback-wave.knockback-vertical", 0.35, 0.0, 5.0));
            target.setVelocity(direction);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    durationTicks("abilities.knockback-wave.blindness-duration-seconds", 1),
                    plugin.getSettings().integer("abilities.knockback-wave.blindness-amplifier", 0, 0, 255)));
        }
    }

    private void startExpandingRing(Player source, PlayerAbilityData data, Location center,
                                    double maxRadius, Color firstColor, Color secondColor,
                                    Runnable onComplete) {
        Location waveCenter = center.clone();
        int duration = intervalTicks("abilities.visuals.expanding-wave-duration-ticks", 20);
        spawnExpandingParticleRing(waveCenter, maxRadius, firstColor, secondColor, duration);

        BukkitRunnable completionTask = new BukkitRunnable() {
            @Override
            public void run() {
                data.expandingRingTasks.remove(getTaskId());
                if (!source.isOnline() || dataMap.get(source.getUniqueId()) != data) return;
                onComplete.run();
            }
        };
        data.expandingRingTasks.add(completionTask.runTaskLater(plugin, duration).getTaskId());
    }

    private void spawnExpandingParticleRing(Location center, double maxRadius,
                                           Color firstColor, Color secondColor, int durationTicks) {
        World world = center.getWorld();
        int points = getRingPointCount(maxRadius);
        double maxStartRadius = Math.max(0.01, maxRadius * 0.5);
        double startRadius = plugin.getSettings().decimal(
                "abilities.visuals.expanding-wave-start-radius", 0.35, 0.01, maxStartRadius);
        double yOffset = plugin.getSettings().decimal("abilities.visuals.expanding-wave-y-offset", 0.1, -2.0, 4.0);
        // Each TRAIL particle has one exact destination and a fixed arrival time.
        int travelTicks = Math.max(1, durationTicks);

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double directionX = Math.cos(angle);
            double directionZ = Math.sin(angle);
            Location start = center.clone().add(directionX * startRadius, yOffset, directionZ * startRadius);
            Location target = center.clone().add(directionX * maxRadius, yOffset, directionZ * maxRadius);
            Color color = (i % 2 == 0) ? firstColor : secondColor;
            world.spawnParticle(Particle.TRAIL, start, 1, 0, 0, 0,
                    new Particle.Trail(target, color, travelTicks));
        }
    }

    private int getRingPointCount(double radius) {
        double spacing = plugin.getSettings().decimal("abilities.visuals.ring-point-spacing", 0.12, 0.04, 1.0);
        int minimum = plugin.getSettings().integer("abilities.visuals.ring-min-points", 48, 8, 512);
        int maximum = plugin.getSettings().integer("abilities.visuals.ring-max-points", 256, minimum, 1024);
        int points = (int) Math.ceil(2 * Math.PI * radius / spacing);
        return Math.max(minimum, Math.min(maximum, points));
    }

    // ==================== Обмен местами (ТИР 3) ====================

    private void activateTeleportSwap(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.teleportSwapCooldownUntil) {
            long secondsLeft = (data.teleportSwapCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.teleport-swap.cooldown-message", "&cПерезарядка: {seconds}с")
                    .replace("{seconds}", Long.toString(secondsLeft))));
            return;
        }

        double range = plugin.getSettings().decimal("abilities.teleport-swap.target-range", 20.0, 1.0, 128.0);
        Player target = getTargetPlayer(p, range);
        if (target == null) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.teleport-swap.no-target-message", "&cНет цели впереди!")));
            return;
        }

        data.teleportSwapCooldownUntil = now + cooldownMillis("abilities.teleport-swap.cooldown-seconds", 5);
        Location pLoc = p.getLocation().clone();
        Location tLoc = target.getLocation().clone();
        int particleCount = plugin.getSettings().integer("abilities.teleport-swap.particle-count", 30, 0, 500);
        p.getWorld().spawnParticle(Particle.PORTAL, pLoc.clone().add(0, 1, 0), particleCount, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().spawnParticle(Particle.PORTAL, tLoc.clone().add(0, 1, 0), particleCount, 0.3, 0.5, 0.3, 0.05);

        tLoc.setDirection(pLoc.toVector().subtract(tLoc.toVector()));
        pLoc.setDirection(tLoc.toVector().subtract(pLoc.toVector()));
        p.teleport(tLoc);
        target.teleport(pLoc);
        p.setVelocity(new Vector());
        target.setVelocity(new Vector());

        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                durationTicks("abilities.teleport-swap.blindness-duration-seconds", 1),
                plugin.getSettings().integer("abilities.teleport-swap.blindness-amplifier", 0, 0, 255)));
        Sound sound = plugin.getSettings().sound("abilities.teleport-swap.sound", Sound.ENTITY_ENDERMAN_TELEPORT);
        p.playSound(p.getLocation(), sound, 1f, 1f);
        target.playSound(target.getLocation(), sound, 1f, 1f);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.teleport-swap.activation-message", "&bОбмен местами!")));
    }

    private Player getTargetPlayer(Player p, double maxDistance) {
        RayTraceResult result = p.getWorld().rayTraceEntities(
                p.getEyeLocation(),
                p.getEyeLocation().getDirection(),
                maxDistance,
                0.4,
                entity -> entity instanceof Player && !entity.equals(p)
        );
        if (result == null || result.getHitEntity() == null) return null;
        return (Player) result.getHitEntity();
    }

    // ==================== ТИР 4: Аура Монстра (чёрно-красное кольцо) ====================

    private void activateMonsterAura(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.monsterAuraCooldownUntil) {
            long left = (data.monsterAuraCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.monster-aura.cooldown-message", "&cАура Монстра перезаряжается: {seconds}с")
                    .replace("{seconds}", Long.toString(left))));
            return;
        }

        data.monsterAuraCooldownUntil = now + cooldownMillis("abilities.monster-aura.cooldown-seconds", 120);
        data.monsterAuraActive = true;
        int duration = durationTicks("abilities.monster-aura.duration-seconds", 15);
        int auraUpdateTicks = intervalTicks("abilities.visuals.aura-update-interval-ticks", 1);
        int waveInterval = durationTicks("abilities.monster-aura.wave-interval-seconds", 5);
        int waveCount = plugin.getSettings().integer("abilities.monster-aura.wave-count", 3, 0, 100);

        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration,
                plugin.getSettings().integer("abilities.monster-aura.strength-amplifier", 1, 0, 255)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration,
                plugin.getSettings().integer("abilities.monster-aura.speed-amplifier", 1, 0, 255)));

        World world = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        if (plugin.getSettings().bool("abilities.monster-aura.activation-explosion-particle", true)) {
            world.spawnParticle(Particle.EXPLOSION, start, 1);
        }
        int burstCount = plugin.getSettings().integer("abilities.visuals.activation-dust-count", 50, 0, 500);
        float burstScale = particleScale("abilities.visuals.activation-dust-scale", 2.0f);
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.inner", Color.fromRGB(255, 0, 0)), burstScale));
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.outer", Color.fromRGB(0, 0, 0)), burstScale));

        data.monsterAuraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    cancel();
                    return;
                }
                spawnMonsterAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, auraUpdateTicks).getTaskId();

        data.monsterWaveTask = new BukkitRunnable() {
            private int waves;

            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data || ++waves > waveCount) {
                    cancel();
                    return;
                }
                spawnMonsterWave(p, data);
            }
        }.runTaskTimer(plugin, waveInterval, waveInterval).getTaskId();

        data.monsterAuraEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dataMap.get(p.getUniqueId()) != data) return;
            p.removePotionEffect(PotionEffectType.STRENGTH);
            reapplyPassiveEffects(p, data);
            data.monsterAuraActive = false;
            if (data.monsterAuraTask != -1) Bukkit.getScheduler().cancelTask(data.monsterAuraTask);
            if (data.monsterWaveTask != -1) Bukkit.getScheduler().cancelTask(data.monsterWaveTask);
            data.monsterAuraTask = -1;
            data.monsterWaveTask = -1;
            data.monsterAuraEndTask = -1;
        }, duration).getTaskId();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.monster-aura.activation-message", "&4&lАура Монстра активирована!")));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.monster-aura.activation-sound", Sound.ENTITY_WITHER_SPAWN), 1f, 1f);
    }

    /** Emit a rotating particle ring that follows the player's current motion. */
    private void spawnParticleRing(Player player, double radius, double yOffset, int points,
                                   Color firstColor, Color secondColor) {
        World world = player.getWorld();
        Location center = player.getLocation();
        Vector velocity = player.getVelocity().multiply(plugin.getSettings().decimal(
                "abilities.visuals.aura-velocity-scale", 1.0, 0.0, 2.0));
        float scale = particleScale("abilities.visuals.aura-dust-scale", 1.0f);
        Particle.DustOptions first = new Particle.DustOptions(firstColor, scale);
        Particle.DustOptions second = new Particle.DustOptions(secondColor, scale);

        long rotationPeriod = plugin.getSettings().integer(
                "abilities.visuals.aura-rotation-period-ticks", 80, 1, 12_000) * 50L;
        double rotation = (System.currentTimeMillis() % rotationPeriod) / (double) rotationPeriod * 2.0 * Math.PI;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + rotation;
            Location point = center.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 0,
                    velocity.getX(), velocity.getY(), velocity.getZ(), 1.0,
                    (i % 2 == 0) ? first : second);
        }
    }

    private void spawnMonsterAuraParticles(Player p) {
        double radius = plugin.getSettings().decimal("abilities.monster-aura.aura-radius", 1.3, 0.1, 8.0);
        double yOffset = plugin.getSettings().decimal("abilities.monster-aura.aura-height", 0.2, -2.0, 4.0);
        spawnParticleRing(p, radius, yOffset, getRingPointCount(radius),
                plugin.getSettings().color("abilities.monster-aura.colors.inner", Color.fromRGB(255, 0, 0)),
                plugin.getSettings().color("abilities.monster-aura.colors.outer", Color.fromRGB(0, 0, 0)));
    }

    private void spawnMonsterHitBurst(Player victim) {
        World world = victim.getWorld();
        Location loc = victim.getLocation().add(0, 1.2, 0);
        float scale = particleScale("abilities.monster-aura.hit-burst-dust-scale", 1.4f);
        world.spawnParticle(Particle.DUST, loc,
                plugin.getSettings().integer("abilities.monster-aura.hit-burst-inner-count", 12, 0, 500),
                0.3, 0.4, 0.3, 0, new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.hit-inner", Color.fromRGB(200, 0, 0)), scale));
        world.spawnParticle(Particle.DUST, loc,
                plugin.getSettings().integer("abilities.monster-aura.hit-burst-outer-count", 10, 0, 500),
                0.25, 0.35, 0.25, 0, new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.hit-outer", Color.BLACK), scale));
    }

    private void spawnMonsterWave(Player p, PlayerAbilityData data) {
        Location center = p.getLocation().clone();
        double radius = plugin.getSettings().decimal("abilities.monster-aura.wave-radius", 5.0, 0.5, 32.0);
        p.playSound(center, plugin.getSettings().sound(
                "abilities.monster-aura.wave-sound", Sound.ENTITY_WITHER_SHOOT), 1f, 1.2f);
        Color inner = plugin.getSettings().color("abilities.monster-aura.colors.wave-inner", Color.fromRGB(80, 0, 0));
        Color outer = plugin.getSettings().color("abilities.monster-aura.colors.wave-outer", Color.fromRGB(255, 0, 0));
        startExpandingRing(p, data, center, radius, inner, outer,
                () -> applyMonsterWaveDamage(p, center, radius));
    }

    private void applyMonsterWaveDamage(Player p, Location center, double radius) {
        World world = center.getWorld();
        if (plugin.getSettings().bool("abilities.monster-aura.wave-explosion-particle", true)) {
            world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1);
        }
        int burstCount = plugin.getSettings().integer("abilities.monster-aura.wave-burst-particle-count", 20, 0, 500);
        float burstScale = particleScale("abilities.visuals.burst-dust-scale", 1.0f);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), burstCount, 0.5, 0.6, 0.5, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.wave-outer", Color.fromRGB(255, 0, 0)), burstScale));
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), burstCount, 0.5, 0.6, 0.5, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.monster-aura.colors.wave-inner", Color.fromRGB(80, 0, 0)), burstScale));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(p)) continue;
            if (target.getLocation().distance(center) > radius) continue;

            target.damage(plugin.getSettings().decimal("abilities.monster-aura.wave-damage", 4.0, 0.0, 2048.0), p);

            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            direction.normalize().multiply(plugin.getSettings().decimal(
                    "abilities.monster-aura.wave-knockback-horizontal", 1.2, 0.0, 10.0));
            direction.setY(plugin.getSettings().decimal(
                    "abilities.monster-aura.wave-knockback-vertical", 0.3, 0.0, 5.0));
            target.setVelocity(direction);

            int duration = durationTicks("abilities.monster-aura.wave-effect-duration-seconds", 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration,
                    plugin.getSettings().integer("abilities.monster-aura.wave-slowness-amplifier", 0, 0, 255)));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration,
                    plugin.getSettings().integer("abilities.monster-aura.wave-blindness-amplifier", 0, 0, 255)));
        }
    }

    // ==================== ТИР 4: Защита Архангела (бело-жёлтое кольцо) ====================

    private void activateArchangelProtection(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.archangelCooldownUntil) {
            long left = (data.archangelCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.archangel.cooldown-message", "&cЗащита Архангела перезаряжается: {seconds}с")
                    .replace("{seconds}", Long.toString(left))));
            return;
        }

        data.archangelCooldownUntil = now + cooldownMillis("abilities.archangel.cooldown-seconds", 120);
        int duration = durationTicks("abilities.archangel.duration-seconds", 15);
        int auraUpdateTicks = intervalTicks("abilities.visuals.aura-update-interval-ticks", 1);
        int healInterval = durationTicks("abilities.archangel.heal-interval-seconds", 5);

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration,
                plugin.getSettings().integer("abilities.archangel.resistance-amplifier", 1, 0, 255)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration,
                plugin.getSettings().integer("abilities.archangel.regeneration-amplifier", 1, 0, 255)));

        World world = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        if (plugin.getSettings().bool("abilities.visuals.activation-flash", true)) {
            world.spawnParticle(Particle.FLASH, start, 1);
        }
        int burstCount = plugin.getSettings().integer("abilities.visuals.activation-dust-count", 50, 0, 500);
        float burstScale = particleScale("abilities.visuals.activation-dust-scale", 2.0f);
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.archangel.colors.inner", Color.fromRGB(255, 215, 0)), burstScale));
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color("abilities.archangel.colors.outer", Color.WHITE), burstScale));

        data.archangelAuraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    cancel();
                    return;
                }
                spawnArchangelAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, auraUpdateTicks).getTaskId();

        data.archangelHealTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    cancel();
                    return;
                }
                double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                double healAmount = plugin.getSettings().decimal("abilities.archangel.heal-amount", 4.0, 0.0, 40.0);
                p.setHealth(Math.min(max, p.getHealth() + healAmount));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        durationTicks("abilities.archangel.heal-regeneration-duration-seconds", 5),
                        plugin.getSettings().integer("abilities.archangel.heal-regeneration-amplifier", 0, 0, 255)));
                p.playSound(p.getLocation(), plugin.getSettings().sound(
                        "abilities.archangel.heal-sound", Sound.ENTITY_PLAYER_LEVELUP),
                        (float) plugin.getSettings().decimal("abilities.archangel.heal-sound-volume", 0.8, 0.0, 2.0),
                        (float) plugin.getSettings().decimal("abilities.archangel.heal-sound-pitch", 1.5, 0.5, 2.0));

                Location loc = p.getLocation().add(0, 1.2, 0);
                int particleCount = plugin.getSettings().integer("abilities.archangel.heal-particle-count", 15, 0, 500);
                float scale = particleScale("abilities.visuals.aura-dust-scale", 1.0f);
                p.getWorld().spawnParticle(Particle.DUST, loc, particleCount, 0.5, 0.6, 0.5, 0,
                        new Particle.DustOptions(plugin.getSettings().color(
                                "abilities.archangel.colors.heal-inner", Color.fromRGB(255, 230, 120)), scale));
                p.getWorld().spawnParticle(Particle.DUST, loc, particleCount, 0.5, 0.6, 0.5, 0,
                        new Particle.DustOptions(plugin.getSettings().color("abilities.archangel.colors.outer", Color.WHITE), scale));
            }
        }.runTaskTimer(plugin, healInterval, healInterval).getTaskId();

        data.archangelEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dataMap.get(p.getUniqueId()) == data) {
                p.removePotionEffect(PotionEffectType.RESISTANCE);
                p.removePotionEffect(PotionEffectType.REGENERATION);
                if (data.archangelAuraTask != -1) Bukkit.getScheduler().cancelTask(data.archangelAuraTask);
                if (data.archangelHealTask != -1) Bukkit.getScheduler().cancelTask(data.archangelHealTask);
            }
            data.archangelAuraTask = -1;
            data.archangelHealTask = -1;
            data.archangelEndTask = -1;
        }, duration).getTaskId();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.archangel.activation-message", "&e&lЗащита Архангела активирована!")));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.archangel.activation-sound", Sound.ENTITY_ENDER_DRAGON_GROWL), 1f, 1.4f);
    }

    private void spawnArchangelAuraParticles(Player p) {
        double radius = plugin.getSettings().decimal("abilities.archangel.aura-radius", 1.2, 0.1, 8.0);
        double yOffset = plugin.getSettings().decimal("abilities.archangel.aura-height", 0.9, -2.0, 4.0);
        spawnParticleRing(p, radius, yOffset, getRingPointCount(radius),
                plugin.getSettings().color("abilities.archangel.colors.inner", Color.fromRGB(255, 215, 0)),
                plugin.getSettings().color("abilities.archangel.colors.outer", Color.WHITE));
    }

    // ==================== ТИР 4: Ультра Инстинкт (бело-голубая аура) ====================

    private void activateUltraInstinct(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.ultraInstinctCooldownUntil) {
            long left = (data.ultraInstinctCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.ultra-instinct.cooldown-message", "&cУльтра Инстинкт перезаряжается: {seconds}с")
                    .replace("{seconds}", Long.toString(left))));
            return;
        }

        data.ultraInstinctCooldownUntil = now + cooldownMillis("abilities.ultra-instinct.cooldown-seconds", 120);
        data.ultraInstinctActive = true;
        int duration = durationTicks("abilities.ultra-instinct.duration-seconds", 15);
        int auraUpdateTicks = intervalTicks("abilities.visuals.ultra-aura-update-interval-ticks", 2);

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration,
                plugin.getSettings().integer("abilities.ultra-instinct.speed-amplifier", 1, 0, 255)));

        World world = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        if (plugin.getSettings().bool("abilities.visuals.activation-flash", true)) {
            world.spawnParticle(Particle.FLASH, start, 1);
        }
        int burstCount = plugin.getSettings().integer("abilities.visuals.activation-dust-count", 50, 0, 500);
        float burstScale = particleScale("abilities.visuals.activation-dust-scale", 2.0f);
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color(
                        "abilities.ultra-instinct.colors.inner", Color.fromRGB(120, 220, 255)), burstScale));
        world.spawnParticle(Particle.DUST, start, burstCount, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(plugin.getSettings().color("abilities.ultra-instinct.colors.outer", Color.WHITE), burstScale));

        data.ultraInstinctAuraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    cancel();
                    return;
                }
                spawnUltraInstinctAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, auraUpdateTicks).getTaskId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.isDead() || dataMap.get(p.getUniqueId()) != data) return;
            data.ultraInstinctActive = false;
            reapplyPassiveEffects(p, data);
            if (data.ultraInstinctAuraTask != -1) Bukkit.getScheduler().cancelTask(data.ultraInstinctAuraTask);
            data.ultraInstinctAuraTask = -1;
        }, duration);

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.ultra-instinct.activation-message", "&b&lУльтра Инстинкт активирован!")));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.ultra-instinct.activation-sound", Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR), 1f, 1.6f);
    }

    private void spawnUltraInstinctAuraParticles(Player p) {
        double radius = plugin.getSettings().decimal("abilities.ultra-instinct.aura-radius", 0.9, 0.1, 8.0);
        double yOffset = plugin.getSettings().decimal("abilities.ultra-instinct.aura-height", 0.5, -2.0, 4.0);
        int points = plugin.getSettings().integer("abilities.ultra-instinct.aura-points", 36, 8, 512);
        spawnParticleRing(p, radius, yOffset, points,
                plugin.getSettings().color("abilities.ultra-instinct.colors.inner", Color.fromRGB(120, 220, 255)),
                plugin.getSettings().color("abilities.ultra-instinct.colors.outer", Color.WHITE));
    }

    // ==================== ТИР 4: Расширение территории (купол вокруг себя) ====================

    private void activateTerritoryExpansion(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.territoryCooldownUntil) {
            long left = (data.territoryCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                    "abilities.territory.cooldown-message", "&cРасширение территории перезаряжается: {seconds}с")
                    .replace("{seconds}", Long.toString(left))));
            return;
        }

        data.territoryCooldownUntil = now + cooldownMillis("abilities.territory.cooldown-seconds", 120);
        Location center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        long startTime = System.currentTimeMillis();
        int radius = plugin.getSettings().integer("abilities.territory.dome-radius", 5, 1, 12);
        int duration = durationTicks("abilities.territory.duration-seconds", 15);
        long durationMillis = duration * 50L;
        buildTerritoryDome(center, radius, data);

        if (plugin.getSettings().bool("abilities.visuals.activation-flash", true)) {
            p.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 1);
        }
        p.playSound(center, plugin.getSettings().sound(
                "abilities.territory.activation-sound", Sound.BLOCK_BEACON_ACTIVATE),
                (float) plugin.getSettings().decimal("abilities.territory.activation-sound-volume", 1.0, 0.0, 2.0),
                (float) plugin.getSettings().decimal("abilities.territory.activation-sound-pitch", 0.6, 0.5, 2.0));
        p.playSound(p.getLocation(), plugin.getSettings().sound(
                "abilities.territory.secondary-sound", Sound.ENTITY_ARMOR_STAND_BREAK),
                (float) plugin.getSettings().decimal("abilities.territory.secondary-sound-volume", 1.0, 0.0, 2.0),
                (float) plugin.getSettings().decimal("abilities.territory.secondary-sound-pitch", 1.0, 0.5, 2.0));

        int particleInterval = plugin.getSettings().integer(
                "abilities.territory.particle-update-interval-ticks", 3, 1, 1200);
        int effectInterval = durationTicks("abilities.territory.effect-interval-seconds", 1);
        int burstInterval = durationTicks("abilities.territory.burst-interval-seconds", 2);

        data.territoryParticleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data
                        || System.currentTimeMillis() - startTime >= durationMillis) {
                    cancel();
                    return;
                }
                spawnTerritoryInsideParticles(center, radius);
            }
        }.runTaskTimer(plugin, 0L, particleInterval).getTaskId();

        data.territoryEffectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data
                        || System.currentTimeMillis() - startTime >= durationMillis) {
                    cancel();
                    return;
                }
                applyTerritoryZoneEffects(p, center, radius);
            }
        }.runTaskTimer(plugin, 0L, effectInterval).getTaskId();

        data.territoryBoomTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data
                        || System.currentTimeMillis() - startTime >= durationMillis) {
                    cancel();
                    return;
                }
                spawnTerritoryBoom(p, center, radius);
            }
        }.runTaskTimer(plugin, burstInterval, burstInterval).getTaskId();

        data.territoryDomeRemoveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (data.territoryParticleTask != -1) Bukkit.getScheduler().cancelTask(data.territoryParticleTask);
            if (data.territoryEffectTask != -1) Bukkit.getScheduler().cancelTask(data.territoryEffectTask);
            if (data.territoryBoomTask != -1) Bukkit.getScheduler().cancelTask(data.territoryBoomTask);
            if (dataMap.get(p.getUniqueId()) == data) removeTerritoryDome(center, radius, data);
            data.territoryParticleTask = -1;
            data.territoryEffectTask = -1;
            data.territoryBoomTask = -1;
            data.territoryDomeRemoveTask = -1;
        }, duration).getTaskId();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(plugin.getSettings().text(
                "abilities.territory.activation-message", "&0&lРасширение территории активировано вокруг тебя!")));
    }

    private void applyTerritoryZoneEffects(Player caster, Location center, double radius) {
        World world = center.getWorld();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(caster)) continue;
            if (target.getLocation().distance(center) > radius) continue;

            int effectTicks = plugin.getSettings().integer("abilities.territory.zone-effect-duration-ticks", 30, 1, 1200);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectTicks,
                    plugin.getSettings().integer("abilities.territory.slowness-amplifier", 0, 0, 255), true, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, effectTicks,
                    plugin.getSettings().integer("abilities.territory.wither-amplifier", 0, 0, 255), true, false));
        }
    }

    private void buildTerritoryDome(Location center, int radius, PlayerAbilityData data) {
        data.territoryBlockStates.clear();

        int r = radius;
        int rSquaredOuter = r * r;
        int rSquaredInner = (r - 1) * (r - 1);

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int distSq = x * x + y * y + z * z;
                    if (distSq > rSquaredOuter || distSq < rSquaredInner) continue;

                    Block block = center.clone().add(x, y, z).getBlock();

                    BlockState originalState = block.getState();
                    data.territoryBlockStates.add(originalState);

                    boolean isWhite = ((x + y + z) & 1) == 0;
                    Material material = isWhite
                            ? plugin.getSettings().material("abilities.territory.dome-material-a", Material.WHITE_STAINED_GLASS)
                            : plugin.getSettings().material("abilities.territory.dome-material-b", Material.BLACK_STAINED_GLASS);
                    if (!material.isBlock()) material = isWhite ? Material.WHITE_STAINED_GLASS : Material.BLACK_STAINED_GLASS;
                    block.setType(material, false);
                }
            }
        }
    }

    private void removeTerritoryDome(Location center, int radius, PlayerAbilityData data) {
        if (data.territoryBlockStates.isEmpty()) return;

        World world = center.getWorld();
        for (BlockState state : data.territoryBlockStates) {
            state.update(true, false);
        }
        data.territoryBlockStates.clear();

        world.spawnParticle(Particle.CLOUD, center.clone().add(0, radius / 2.0, 0),
                plugin.getSettings().integer("abilities.territory.dome-remove-particle-count", 40, 0, 1000),
                radius * 0.4, radius * 0.4, radius * 0.4, 0.02);
        world.playSound(center, plugin.getSettings().sound(
                "abilities.territory.dome-remove-sound", Sound.BLOCK_GLASS_BREAK),
                (float) plugin.getSettings().decimal("abilities.territory.dome-remove-sound-volume", 1.0, 0.0, 2.0),
                (float) plugin.getSettings().decimal("abilities.territory.dome-remove-sound-pitch", 0.6, 0.5, 2.0));
    }

    private void restoreDomeBlocks(PlayerAbilityData data) {
        if (data.territoryBlockStates.isEmpty()) return;
        for (BlockState state : data.territoryBlockStates) {
            state.update(true, false);
        }
        data.territoryBlockStates.clear();
    }

    private void spawnTerritoryInsideParticles(Location center, double radius) {
        World world = center.getWorld();

        int particleCount = plugin.getSettings().integer("abilities.territory.inside-particle-count", 30, 0, 1000);
        Color colorA = plugin.getSettings().color("abilities.territory.colors.particle-a", Color.BLACK);
        Color colorB = plugin.getSettings().color("abilities.territory.colors.particle-b", Color.WHITE);
        float scale = particleScale("abilities.visuals.burst-dust-scale", 1.6f);
        for (int i = 0; i < particleCount; i++) {
            double x = (random.nextDouble() - 0.5) * radius * 1.8;
            double y = random.nextDouble() * (radius + 1);
            double z = (random.nextDouble() - 0.5) * radius * 1.8;

            if (x * x + z * z > radius * radius) continue;

            Location loc = center.clone().add(x, y, z);
            Color color = random.nextBoolean() ? colorA : colorB;

            world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, scale));
        }
    }

    private void spawnTerritoryBoom(Player p, Location center, double radius) {
        World world = center.getWorld();
        Location effectLocation = center.clone().add(0, 1, 0);
        if (plugin.getSettings().bool("abilities.territory.burst-explosion-particle", true)) {
            world.spawnParticle(Particle.EXPLOSION, effectLocation, 1);
        }
        int particleCount = plugin.getSettings().integer("abilities.territory.burst-particle-count", 30, 0, 1000);
        float scale = particleScale("abilities.visuals.burst-dust-scale", 1.6f);
        world.spawnParticle(Particle.DUST, effectLocation, particleCount, 0.6, 0.6, 0.6, 0,
                new Particle.DustOptions(plugin.getSettings().color("abilities.territory.colors.particle-a", Color.BLACK), scale));
        world.spawnParticle(Particle.DUST, effectLocation, particleCount, 0.6, 0.6, 0.6, 0,
                new Particle.DustOptions(plugin.getSettings().color("abilities.territory.colors.particle-b", Color.WHITE), scale));
        world.playSound(center, plugin.getSettings().sound(
                "abilities.territory.burst-sound", Sound.ENTITY_GENERIC_EXPLODE),
                (float) plugin.getSettings().decimal("abilities.territory.burst-sound-volume", 0.8, 0.0, 2.0),
                (float) plugin.getSettings().decimal("abilities.territory.burst-sound-pitch", 1.2, 0.5, 2.0));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player victim) || victim.equals(p)) continue;
            if (victim.getLocation().distance(center) > radius) continue;

            victim.damage(plugin.getSettings().decimal("abilities.territory.burst-damage", 6.0, 0.0, 2048.0), p);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    durationTicks("abilities.territory.wither-duration-seconds", 3),
                    plugin.getSettings().integer("abilities.territory.burst-wither-amplifier", 2, 0, 255)));

            Vector direction = victim.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            direction.normalize().multiply(plugin.getSettings().decimal(
                    "abilities.territory.knockback-horizontal", 0.5, 0.0, 10.0));
            direction.setY(plugin.getSettings().decimal("abilities.territory.knockback-vertical", 0.2, 0.0, 5.0));
            victim.setVelocity(direction);
        }
    }
}