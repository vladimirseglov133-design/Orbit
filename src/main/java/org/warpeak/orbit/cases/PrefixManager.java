package org.warpeak.orbit.cases;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.integration.LuckPermsHook;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PrefixManager {

    private final Orbit plugin;
    private final File file;
    private final YamlConfiguration config;

    private final Map<UUID, PrefixData> cache = new HashMap<>();
    private LuckPermsHook luckPermsHook;

    public PrefixManager(Orbit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prefixes.yml");

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать prefixes.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        try {
            this.luckPermsHook = new LuckPermsHook();
            plugin.getLogger().info("LuckPerms найден, префиксы кейсов работают.");
        } catch (Throwable t) {
            plugin.getLogger().warning("LuckPerms не найден! Выдача префиксов из кейсов не будет работать.");
            this.luckPermsHook = null;
        }
    }

    public PrefixData getData(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> loadFromConfig(p));
    }

    private PrefixData loadFromConfig(Player p) {
        PrefixData data = new PrefixData();
        String path = p.getUniqueId().toString();

        List<String> unlockedNames = config.getStringList(path + ".unlocked");
        for (String name : unlockedNames) {
            try {
                data.unlocked.add(CasePrize.valueOf(name));
            } catch (IllegalArgumentException ignored) {}
        }

        String equippedName = config.getString(path + ".equipped", null);
        if (equippedName != null) {
            try {
                data.equipped = CasePrize.valueOf(equippedName);
            } catch (IllegalArgumentException ignored) {}
        }

        return data;
    }

    public void save(Player p) {
        PrefixData data = cache.get(p.getUniqueId());
        if (data == null) return;

        String path = p.getUniqueId().toString();
        List<String> names = new ArrayList<>();
        for (CasePrize prize : data.unlocked) names.add(prize.name());

        config.set(path + ".unlocked", names);
        config.set(path + ".equipped", data.equipped != null ? data.equipped.name() : null);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить prefixes.yml: " + e.getMessage());
        }
    }

    public void saveAndUnload(Player p) {
        save(p);
        cache.remove(p.getUniqueId());
    }

    public void saveAll() {
        for (UUID id : cache.keySet()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) save(p);
        }
    }

    /** Вызывается при получении приза из кейса. */
    public boolean unlock(Player p, CasePrize prize) {
        PrefixData data = getData(p);

        boolean isNew = data.unlocked.add(prize);

        if (isNew && data.equipped == null) {
            // это первый полученный приз - надеваем сразу
            equip(p, prize);
        }

        save(p);
        return isNew;
    }

    public boolean isUnlocked(Player p, CasePrize prize) {
        return getData(p).unlocked.contains(prize);
    }

    public CasePrize getEquipped(Player p) {
        return getData(p).equipped;
    }

    public void equip(Player p, CasePrize prize) {
        PrefixData data = getData(p);
        data.equipped = prize;

        if (luckPermsHook != null) {
            luckPermsHook.setPrefix(p, prize.getLuckPermsPrefix());
        }
        save(p);
    }

    public void unequip(Player p) {
        PrefixData data = getData(p);
        data.equipped = null;

        if (luckPermsHook != null) {
            luckPermsHook.clearPrefix(p);
        }
        save(p);
    }

    public boolean hasLuckPerms() {
        return luckPermsHook != null;
    }
}