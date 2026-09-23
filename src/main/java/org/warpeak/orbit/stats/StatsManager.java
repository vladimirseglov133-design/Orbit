package org.warpeak.orbit.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StatsManager {

    private final Orbit plugin;
    private final File file;
    private YamlConfiguration config;

    private final Map<UUID, PlayerStats> cache = new HashMap<>();

    public StatsManager(Orbit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать playerdata.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public PlayerStats getStats(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> loadFromConfig(p));
    }

    private PlayerStats loadFromConfig(Player p) {
        PlayerStats stats = new PlayerStats();
        String path = p.getUniqueId().toString();

        stats.kills = config.getInt(path + ".kills", 0);
        stats.deaths = config.getInt(path + ".deaths", 0);
        stats.coins = config.getLong(path + ".coins", 0);
        stats.playTimeSeconds = config.getLong(path + ".playtime", 0);
        stats.sessionStart = System.currentTimeMillis();

        return stats;
    }

    public void save(Player p) {
        PlayerStats stats = cache.get(p.getUniqueId());
        if (stats == null) return;

        String path = p.getUniqueId().toString();
        config.set(path + ".kills", stats.kills);
        config.set(path + ".deaths", stats.deaths);
        config.set(path + ".coins", stats.coins);
        config.set(path + ".playtime", stats.getTotalPlayTimeSeconds());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить статистику: " + e.getMessage());
        }
    }

    public void saveAndRemove(Player p) {
        save(p);
        cache.remove(p.getUniqueId());
    }

    public void saveAll() {
        for (UUID id : cache.keySet()) {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(id);
            if (p != null) save(p);
        }
    }

    public void addKill(Player p) {
        getStats(p).kills++;
    }

    public void addDeath(Player p) {
        getStats(p).deaths++;
    }

    public void addCoins(Player p, long amount) {
        getStats(p).coins += amount;
    }

    public void removeCoins(Player p, long amount) {
        PlayerStats stats = getStats(p);
        stats.coins = Math.max(0, stats.coins - amount);
    }

    // ==================== ТОП СТАТИСТИКИ ====================

    public List<TopEntry> getTopByCoins(int limit) {
        return getTop(limit, "coins");
    }

    public List<TopEntry> getTopByKills(int limit) {
        return getTop(limit, "kills");
    }

    private List<TopEntry> getTop(int limit, String field) {
        // Актуализируем конфиг данными из кэша (онлайн-игроки могли ещё не сохраниться на диск)
        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            String path = entry.getKey().toString();
            config.set(path + ".kills", entry.getValue().kills);
            config.set(path + ".deaths", entry.getValue().deaths);
            config.set(path + ".coins", entry.getValue().coins);
        }

        Set<String> keys = config.getKeys(false);

        List<TopEntry> result = new ArrayList<>();
        for (String key : keys) {
            try {
                UUID uuid = UUID.fromString(key);
                long value = config.getLong(key + "." + field, 0);
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "Неизвестный";
                result.add(new TopEntry(uuid, name, value));
            } catch (IllegalArgumentException ignored) {
                // ключ не UUID (например, случайный мусор в файле) - пропускаем
            }
        }

        result.sort((a, b) -> Long.compare(b.value, a.value));

        return result.size() > limit ? result.subList(0, limit) : result;
    }

    public static class TopEntry {
        public final UUID uuid;
        public final String name;
        public final long value;

        public TopEntry(UUID uuid, String name, long value) {
            this.uuid = uuid;
            this.name = name;
            this.value = value;
        }
    }
}