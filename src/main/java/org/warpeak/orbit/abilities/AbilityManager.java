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
        }, 20L * 60);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && dataMap.get(p.getUniqueId()) == data) {
                grantTier(p, data, AbilityTier.TIER3);
            }
        }, 20L * 120);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && dataMap.get(p.getUniqueId()) == data) {
                grantTier(p, data, AbilityTier.TIER4);
            }
        }, 20L * 180);
    }

    private void grantTier(Player p, PlayerAbilityData data, AbilityTier tier) {
        List<Ability> pool = Ability.byTier(tier);
        if (pool.isEmpty()) return;
        Ability chosen = pool.get(random.nextInt(pool.size()));

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
                addMaxHealth(p, 4.0);
                p.setHealth(Math.min(p.getHealth() + 4.0, p.getAttribute(Attribute.MAX_HEALTH).getValue()));
            }
            case JUMP_BOOST -> p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, true, false, false));
            case SPEED_BOOST -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false, false));
            case ANTI_KNOCKBACK -> setKnockbackResistance(p, 1.0);
            case SUPER_SPEED -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, false));
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
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, true, false, false));
        }

        if (data.hasAbility(Ability.SUPER_SPEED)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false, false));
        } else if (data.hasAbility(Ability.SPEED_BOOST)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false, false));
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
        String text = "§7Новая способность: " + ability.getDisplayName();
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text)), 40L);
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
            bonusDamage += 1.0;
        }

        if (atkData.hasAbility(Ability.BERSERK)) {
            double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            double missingRatio = 1 - (attacker.getHealth() / max);
            bonusDamage += missingRatio * 4.0;
        }

        if (bonusDamage > 0) {
            event.setDamage(event.getDamage() + bonusDamage);
        }

        if (atkData.hasAbility(Ability.HUNGER_DRAIN)) {
            atkData.hitCounterTier1++;
            if (atkData.hitCounterTier1 % 3 == 0) {
                victim.setFoodLevel(Math.max(0, victim.getFoodLevel() - 1));
                attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + 1));
            }
        }

        if (atkData.hasAbility(Ability.HEALTH_DRAIN)) {
            atkData.hitCounterTier2++;
            if (atkData.hitCounterTier2 % 3 == 0) {
                double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
                double healAmount = 2.0;
                attacker.setHealth(Math.min(max, attacker.getHealth() + healAmount));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0);
            }
        }

        if (atkData.hasAbility(Ability.POISON_TOUCH)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 3 * 20, 0));
        }

        if (atkData.hasAbility(Ability.STUN_HITS)) {
            atkData.hitCounterTier3++;
            if (atkData.hitCounterTier3 % 5 == 0) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 1));
            }
        }

        if (atkData.monsterAuraActive) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 2 * 20, 1));
            spawnMonsterHitBurst(victim);
        }
    }

    // ==================== Уклонение (ручное, тир3) ====================

    public boolean tryDodge(Player victim, EntityDamageByEntityEvent event) {
        PlayerAbilityData data = getData(victim);
        if (data == null) return false;
        if (!data.hasAbility(Ability.DODGE)) return false;
        if (!data.dodgeArmed) return false;
        if (System.currentTimeMillis() > data.dodgeArmedUntil) return false;

        long now = System.currentTimeMillis();

        event.setCancelled(true);
        data.dodgeArmed = false;
        data.dodgeCooldownUntil = Math.max(data.dodgeCooldownUntil, now + 5000);

        performRandomHorizontalTeleport(victim);
        victim.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§dУклонение сработало!"));

        return true;
    }

    // ==================== Ультра Инстинкт: пассивный уворот (тир4) ====================

    public boolean tryUltraInstinctDodge(Player victim, EntityDamageByEntityEvent event) {
        PlayerAbilityData data = getData(victim);
        if (data == null || !data.ultraInstinctActive) return false;
        if (random.nextDouble() >= 0.5) return false;

        event.setCancelled(true);
        performRandomHorizontalTeleport(victim);
        victim.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§lУльтра Инстинкт: уклонение!"));
        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.8f);
        return true;
    }

    private void performRandomHorizontalTeleport(Player victim) {
        Location vLoc = victim.getLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = Math.cos(angle);
        double z = Math.sin(angle);

        Location target = vLoc.clone().add(x, 0, z);
        target.setY(vLoc.getY());
        target.setDirection(vLoc.getDirection());

        victim.getWorld().spawnParticle(Particle.SMOKE, vLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        victim.teleport(target);
        victim.getWorld().spawnParticle(Particle.SMOKE, target.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
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
        p.setHealth(Math.max(1.0, max / 2.0));
        p.setFireTicks(0);

        spawnPhoenixRebirthEffect(p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§6§l🔥 ВОЗРОЖДЕНИЕ ФЕНИКСА! 🔥"));

        return true;
    }

    private void spawnPhoenixRebirthEffect(Player p) {
        World w = p.getWorld();
        Location loc = p.getLocation().add(0, 1, 0);

        w.spawnParticle(Particle.FLAME, loc, 60, 0.6, 1.0, 0.6, 0.05);
        w.spawnParticle(Particle.DUST, loc, 40, 0.6, 1.0, 0.6, 0,
                new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.8f));
        w.spawnParticle(Particle.DUST, loc, 40, 0.6, 1.0, 0.6, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.8f));
        w.spawnParticle(Particle.DUST, loc, 30, 0.6, 1.0, 0.6, 0,
                new Particle.DustOptions(Color.fromRGB(255, 140, 0), 1.8f));
        w.spawnParticle(Particle.LAVA, loc, 8, 0.4, 0.6, 0.4, 0);

        w.playSound(loc, Sound.ENTITY_BLAZE_HURT, 1f, 1f);
        w.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        w.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
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
            long msLeft = data.dodgeCooldownUntil - now;
            long secondsLeft = (msLeft / 1000) + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cУклонение перезаряжается: " + secondsLeft + "с"));
            return;
        }

        // === FIX (баг #1) ===
        // Кулдаун выставляется В МОМЕНТ АКТИВАЦИИ — независимо от исхода
        // (попасть в окно или нет), а не после истечения 1-секундного окна
        // вооружения, как было ранее.
        //
        // Старый баг: dodgeCooldownUntil оставался 0/истёкшим на всё время
        // окна, поэтому спам F быстрее 1 сек проходил проверку кулдауна и
        // бесконечно пересоздавал вооружение (re-arm). Все удары в таком
        // бесконечно продлённом окне отменялись (в логах: 3 хита за секунду,
        // все cancelled=true) — фактически постоянная неуязвимость при спаме.
        //
        // Теперь: окно вооружения (1с) и кулдаун (5с) идут НЕЗАВИСИМО —
        // кулдаун запущен в момент нажатия и никаким последующим нажатием,
        // истечением окна или срабатыванием уворота не сбрасывается
        // (в tryDodge — только Math.max, т.е. только продление).
        data.dodgeCooldownUntil = now + 5000;
        data.dodgeArmed = true;
        data.dodgeArmedUntil = now + 1000;

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§dУклонение активно 1 сек!"));
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.6f, 1.5f);

        // Таймер снимает ТОЛЬКО вооружение, если за окно хит не пришёл.
        // Кулдаун этим таском НЕ трогается — он уже идёт с момента активации,
        // поэтому пересеков/сбросов окно↔кулдаун быть не может.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (data.dodgeArmed) {
                data.dodgeArmed = false;
            }
        }, 20L);
    }

    private void activateHealBurst(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.healBurstCooldownUntil) {
            long secondsLeft = (data.healBurstCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cПерезарядка: " + secondsLeft + "с"));
            return;
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5 * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1));

        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + 4.0));

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aВсплеск исцеления!"));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0);

        data.healBurstCooldownUntil = now + 20000;
    }

    // ==================== Ударная волна (ТИР 3) ====================

    private void activateKnockbackWave(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.knockbackWaveCooldownUntil) {
            long secondsLeft = (data.knockbackWaveCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cПерезарядка: " + secondsLeft + "с"));
            return;
        }

        data.knockbackWaveCooldownUntil = now + 15000;

        Location center = p.getLocation().clone();
        double maxRadius = 3.0;

        p.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.2f);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§fУдарная волна!"));

        startExpandingRing(p, data, center, maxRadius, Color.fromRGB(180, 180, 180), Color.WHITE,
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
            target.damage(6.0, p);

            direction.normalize().multiply(1.4);
            direction.setY(0.35);

            target.setVelocity(direction);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
        }

        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
    }

    // ==================== Обмен местами (ТИР 3) ====================

    private void activateTeleportSwap(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.teleportSwapCooldownUntil) {
            long secondsLeft = (data.teleportSwapCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cПерезарядка: " + secondsLeft + "с"));
            return;
        }

        Player target = getTargetPlayer(p, 20.0);
        if (target == null) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cНет цели впереди!"));
            return;
        }

        data.teleportSwapCooldownUntil = now + 5000;

        Location pLoc = p.getLocation().clone();
        Location tLoc = target.getLocation().clone();

        p.getWorld().spawnParticle(Particle.PORTAL, pLoc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().spawnParticle(Particle.PORTAL, tLoc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);

        // ДО УМА: после обмена игроки смотрят друг на друга
        tLoc.setDirection(pLoc.toVector().subtract(tLoc.toVector()));
        pLoc.setDirection(tLoc.toVector().subtract(pLoc.toVector()));

        p.teleport(tLoc);
        target.teleport(pLoc);

        // Останавливаем старый импульс: иначе после длинного телепорта игрок
        // сохраняет предтелепортационную скорость и может "пролететь" арену
        p.setVelocity(new Vector());
        target.setVelocity(new Vector());

        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));

        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§bОбмен местами!"));
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

    // ==================== Аура Монстра и расширяющиеся кольца ====================

    private static final int EXPANDING_RING_DURATION_TICKS = 20;
    private static final double EXPANDING_RING_START_RADIUS = 0.35;

    private void activateMonsterAura(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.monsterAuraCooldownUntil) {
            long left = (data.monsterAuraCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cАура Монстра перезаряжается: " + left + "с"));
            return;
        }

        data.monsterAuraCooldownUntil = now + 120_000;
        data.monsterAuraActive = true;

        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1));

        World world = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, start, 1);
        world.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f));
        world.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.0f));

        data.monsterAuraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    this.cancel();
                    return;
                }
                spawnMonsterAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        data.monsterWaveTask = new BukkitRunnable() {
            private int waves = 0;

            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    this.cancel();
                    return;
                }
                if (++waves > 3) {
                    this.cancel();
                    return;
                }
                spawnMonsterWave(p, data);
            }
        }.runTaskTimer(plugin, 100L, 100L).getTaskId();

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
        }, 15 * 20L).getTaskId();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§4§lАура Монстра активирована!"));
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
    }

    /**
     * Small Dust scale shortens vanilla particle lifetime, keeping consecutive
     * frames close together instead of leaving a long particle trail.
     */
    private static final float RING_PARTICLE_SCALE = 0.35f;

    private void spawnParticleRing(Location center, double radius, double yOffset, int points,
                                   Color firstColor, Color secondColor) {
        World world = center.getWorld();
        Particle.DustOptions first = new Particle.DustOptions(firstColor, RING_PARTICLE_SCALE);
        Particle.DustOptions second = new Particle.DustOptions(secondColor, RING_PARTICLE_SCALE);

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(
                    Math.cos(angle) * radius,
                    yOffset,
                    Math.sin(angle) * radius
            );
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    (i % 2 == 0) ? first : second);
        }
    }

    private int getRingPointCount(double radius) {
        return Math.max(48, (int) Math.ceil(2 * Math.PI * radius / 0.12));
    }

    private void spawnMonsterAuraParticles(Player p) {
        double radius = 1.3;
        spawnParticleRing(p.getLocation(), radius, 0.2, getRingPointCount(radius),
                Color.fromRGB(255, 0, 0), Color.fromRGB(80, 0, 0));
    }

    private void startExpandingRing(Player source, PlayerAbilityData data, Location center,
                                    double maxRadius, Color firstColor, Color secondColor,
                                    Runnable onComplete) {
        Location waveCenter = center.clone();
        BukkitRunnable animation = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!source.isOnline() || dataMap.get(source.getUniqueId()) != data) {
                    finish();
                    return;
                }

                tick++;
                double progress = Math.min(1.0, tick / (double) EXPANDING_RING_DURATION_TICKS);
                double easedProgress = 1.0 - (1.0 - progress) * (1.0 - progress);
                double radius = EXPANDING_RING_START_RADIUS
                        + (maxRadius - EXPANDING_RING_START_RADIUS) * easedProgress;
                spawnParticleRing(waveCenter, radius, 0.1, getRingPointCount(radius), firstColor, secondColor);

                if (tick >= EXPANDING_RING_DURATION_TICKS) {
                    try {
                        onComplete.run();
                    } finally {
                        finish();
                    }
                }
            }

            private void finish() {
                data.expandingRingTasks.remove(getTaskId());
                cancel();
            }
        };
        data.expandingRingTasks.add(animation.runTaskTimer(plugin, 1L, 1L).getTaskId());
    }

    private void spawnMonsterHitBurst(Player victim) {
        World world = victim.getWorld();
        Location loc = victim.getLocation().add(0, 1.2, 0);
        world.spawnParticle(Particle.DUST, loc, 12, 0.3, 0.4, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.4f));
        world.spawnParticle(Particle.DUST, loc, 10, 0.25, 0.35, 0.25, 0,
                new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.4f));
    }

    private void spawnMonsterWave(Player p, PlayerAbilityData data) {
        Location center = p.getLocation().clone();
        double maxRadius = 5.0;
        p.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1f, 1.2f);

        startExpandingRing(p, data, center, maxRadius, Color.fromRGB(80, 0, 0), Color.fromRGB(255, 0, 0),
                () -> applyMonsterWaveDamage(p, center, maxRadius));
    }

    private void applyMonsterWaveDamage(Player p, Location center, double radius) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.6f));
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.6f));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(p)) continue;
            if (target.getLocation().distance(center) > radius) continue;

            target.damage(4.0, p);

            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            direction.normalize().multiply(1.2);
            direction.setY(0.3);
            target.setVelocity(direction);

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
        }
    }

    // ==================== ТИР 4: Защита Архангела (бело-жёлтое кольцо) ====================

    private void activateArchangelProtection(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.archangelCooldownUntil) {
            long left = (data.archangelCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cЗащита Архангела перезаряжается: " + left + "с"));
            return;
        }

        data.archangelCooldownUntil = now + 120_000;

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 15 * 20, 1));

        World world = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.FLASH, start, 1);
        world.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f));
        world.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.WHITE, 2.0f));

        data.archangelAuraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    this.cancel();
                    return;
                }
                spawnArchangelAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        data.archangelHealTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || dataMap.get(p.getUniqueId()) != data) {
                    this.cancel();
                    return;
                }
                double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                p.setHealth(Math.min(max, p.getHealth() + 4.0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

                Location loc = p.getLocation().add(0, 1.2, 0);
                p.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.6, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 230, 120), RING_PARTICLE_SCALE));
                p.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.6, 0.5, 0,
                        new Particle.DustOptions(Color.WHITE, RING_PARTICLE_SCALE));
            }
        }.runTaskTimer(plugin, 100L, 100L).getTaskId();

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
        }, 15 * 20L).getTaskId();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§e§lЗащита Архангела активирована!"));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
    }

    private void spawnArchangelAuraParticles(Player p) {
        double radius = 1.2;
        spawnParticleRing(p.getLocation(), radius, 0.9, getRingPointCount(radius),
                Color.fromRGB(255, 215, 0), Color.WHITE);
    }

    // ==================== ТИР 4: Ультра Инстинкт (бело-голубая аура) ====================

    private static final long ULTRA_INSTINCT_DURATION_TICKS = 15 * 20L; // 15 секунд

    private void activateUltraInstinct(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.ultraInstinctCooldownUntil) {
            long left = (data.ultraInstinctCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cУльтра Инстинкт перезаряжается: " + left + "с"));
            return;
        }

        data.ultraInstinctCooldownUntil = now + 120_000;
        data.ultraInstinctActive = true;

        // Скорость 2 (амплифаер 1)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) ULTRA_INSTINCT_DURATION_TICKS, 1));

        World w = p.getWorld();
        Location start = p.getLocation().add(0, 1, 0);
        w.spawnParticle(Particle.FLASH, start, 1);
        w.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.6f));
        w.spawnParticle(Particle.DUST, start, 50, 1.0, 1.0, 1.0, 0,
                new Particle.DustOptions(Color.WHITE, 1.6f));

        int auraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }
                spawnUltraInstinctAuraParticles(p);
            }
        }.runTaskTimer(plugin, 0L, 2L).getTaskId();
        data.ultraInstinctAuraTask = auraTask;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // ДО УМА: если игрок умер/ушёл офлайн (или дуэль завершена и clear()
            // уже снял эффекты) — не применяем passive-эффекты к трупу/оффлайну.
            if (!p.isOnline() || p.isDead()) return;
            data.ultraInstinctActive = false;
            reapplyPassiveEffects(p, data);
            if (data.ultraInstinctAuraTask != -1) Bukkit.getScheduler().cancelTask(data.ultraInstinctAuraTask);
            data.ultraInstinctAuraTask = -1;
        }, ULTRA_INSTINCT_DURATION_TICKS);

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§lУльтра Инстинкт активирован!"));
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.6f);
    }

    /**
     * Маленькое бело-голубое кольцо вокруг игрока — визуально отличается от других аур
     * меньшим радиусом и более быстрым вращением.
     */
    private void spawnUltraInstinctAuraParticles(Player p) {
        World world = p.getWorld();
        Location base = p.getLocation().clone();
        double rot = (System.currentTimeMillis() % 2000) / 2000.0 * 2 * Math.PI;

        double r = 0.9;
        int points = 18;

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + rot;
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            Location loc = base.clone().add(x, 0.5, z);

            Color color = (i % 2 == 0) ? Color.fromRGB(120, 220, 255) : Color.fromRGB(255, 255, 255);

            world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.5f));
        }
    }

    // ==================== ТИР 4: Расширение территории (купол вокруг себя) ====================

    private static final int DOME_RADIUS = 5;

    private void activateTerritoryExpansion(Player p, PlayerAbilityData data) {
        long now = System.currentTimeMillis();
        if (now < data.territoryCooldownUntil) {
            long left = (data.territoryCooldownUntil - now) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cРасширение территории перезаряжается: " + left + "с"));
            return;
        }

        data.territoryCooldownUntil = now + 120_000;

        // Купол строится вокруг САМОГО СЕБЯ, цель не требуется — можно "промазать" по врагу
        Location center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        long start = System.currentTimeMillis();
        int durationTicks = 15 * 20;

        buildTerritoryDome(center, DOME_RADIUS, data);

        p.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 1);
        p.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 1f);

        int particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                if (elapsed >= 15) {
                    this.cancel();
                    return;
                }
                spawnTerritoryInsideParticles(center, DOME_RADIUS);
            }
        }.runTaskTimer(plugin, 0L, 3L).getTaskId();

        int effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                if (elapsed >= 15) {
                    this.cancel();
                    return;
                }
                applyTerritoryZoneEffects(p, center, DOME_RADIUS);
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        int boomTask = new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                if (elapsed >= 15) {
                    this.cancel();
                    return;
                }
                spawnTerritoryBoom(p, center, DOME_RADIUS);
            }
        }.runTaskTimer(plugin, 40L, 40L).getTaskId();

        data.territoryParticleTask = particleTask;
        data.territoryEffectTask = effectTask;
        data.territoryBoomTask = boomTask;

        int removeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().cancelTask(particleTask);
            Bukkit.getScheduler().cancelTask(effectTask);
            Bukkit.getScheduler().cancelTask(boomTask);
            removeTerritoryDome(center, DOME_RADIUS, data);
        }, durationTicks).getTaskId();
        data.territoryDomeRemoveTask = removeTask;

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§0§lРасширение территории активировано вокруг тебя!"));
    }

    /**
     * Каждую секунду накладывает Замедление 1 и Иссушение 1 на всех игроков (кроме каста),
     * оказавшихся внутри купола НА ДАННЫЙ МОМЕНТ. Если враг не зашёл внутрь — эффекта не будет,
     * это и есть "промах" по домену.
     */
    private void applyTerritoryZoneEffects(Player caster, Location center, double radius) {
        World world = center.getWorld();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(caster)) continue;
            if (target.getLocation().distance(center) > radius) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 30, 0, true, false));
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
                    Material mat = isWhite ? Material.WHITE_STAINED_GLASS : Material.BLACK_STAINED_GLASS;
                    block.setType(mat, false);
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

        world.spawnParticle(Particle.CLOUD, center.clone().add(0, radius / 2.0, 0), 40, radius * 0.4, radius * 0.4, radius * 0.4, 0.02);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
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

        for (int i = 0; i < 30; i++) {
            double x = (random.nextDouble() - 0.5) * radius * 1.8;
            double y = random.nextDouble() * (radius + 1);
            double z = (random.nextDouble() - 0.5) * radius * 1.8;

            if (x * x + z * z > radius * radius) continue;

            Location loc = center.clone().add(x, y, z);
            Color color = random.nextBoolean() ? Color.fromRGB(0, 0, 0) : Color.fromRGB(255, 255, 255);

            world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.6f));
        }
    }

    private void spawnTerritoryBoom(Player p, Location center, double radius) {
        World world = center.getWorld();

        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0,
                new Particle.DustOptions(Color.BLACK, 1.6f));
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0,
                new Particle.DustOptions(Color.WHITE, 1.6f));
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player victim)) continue;
            if (victim.equals(p)) continue;
            if (victim.getLocation().distance(center) > radius) continue;

            victim.damage(6.0, p);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 3 * 20, 2));

            Vector dir = victim.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() < 0.0001) {
                dir = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            dir.normalize().multiply(0.5);
            dir.setY(0.2);
            victim.setVelocity(dir);
        }
    }
}