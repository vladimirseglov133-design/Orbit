package org.warpeak.orbit.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.warpeak.orbit.Orbit;

import java.util.EnumMap;
import java.util.Map;

public class TopHologramManager {

    private final Orbit plugin;
    private final Map<TopHologram.Type, TopHologram> holograms = new EnumMap<>(TopHologram.Type.class);

    public TopHologramManager(Orbit plugin) {
        this.plugin = plugin;
    }

    public void loadFromConfig() {
        loadOne(TopHologram.Type.KILLS, "top-holograms.kills");
        loadOne(TopHologram.Type.COINS, "top-holograms.coins");
    }

    private void loadOne(TopHologram.Type type, String path) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.contains(path + ".world")) {
            plugin.getLogger().info("[TopHologram] Координаты для " + type + " не заданы в config.yml, пропускаю.");
            return;
        }

        String worldName = cfg.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[TopHologram] Мир '" + worldName + "' не найден для голограммы " + type + ".");
            return;
        }

        double x = cfg.getDouble(path + ".x");
        double y = cfg.getDouble(path + ".y");
        double z = cfg.getDouble(path + ".z");

        Location loc = new Location(world, x, y, z);

        // Принудительно прогружаем чанк, иначе spawn() может создать сущность,
        // которая тут же будет выгружена сервером
        loc.getChunk().setForceLoaded(true);

        setHologram(type, loc, false);
        plugin.getLogger().info("[TopHologram] Голограмма " + type + " загружена в " + worldName
                + " (" + x + ", " + y + ", " + z + ")");
    }

    public void setHologram(TopHologram.Type type, Location loc, boolean saveToConfig) {
        TopHologram existing = holograms.remove(type);
        if (existing != null) existing.remove();

        loc.getChunk().setForceLoaded(true);

        TopHologram holo = new TopHologram(type, loc);
        holo.spawn();
        holograms.put(type, holo);

        if (saveToConfig) {
            String path = type == TopHologram.Type.KILLS ? "top-holograms.kills" : "top-holograms.coins";
            FileConfiguration cfg = plugin.getConfig();
            cfg.set(path + ".world", loc.getWorld().getName());
            cfg.set(path + ".x", loc.getX());
            cfg.set(path + ".y", loc.getY());
            cfg.set(path + ".z", loc.getZ());
            plugin.saveConfig();
        }
    }

    public void removeHologram(TopHologram.Type type) {
        TopHologram existing = holograms.remove(type);
        if (existing != null) existing.remove();

        String path = type == TopHologram.Type.KILLS ? "top-holograms.kills" : "top-holograms.coins";
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
    }

    public void updateAll() {
        if (holograms.isEmpty()) {
            plugin.getLogger().warning("[TopHologram] updateAll() вызван, но карта голограмм пуста! " +
                    "Проверьте, что loadFromConfig() был вызван и координаты заданы в config.yml.");
            return;
        }
        for (TopHologram holo : holograms.values()) {
            holo.update();
        }
    }

    public void removeAll() {
        for (TopHologram holo : holograms.values()) {
            holo.remove();
        }
        holograms.clear();
    }

    public int purgeAllInWorld(World world) {
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e.getType() == EntityType.TEXT_DISPLAY
                    && e.getPersistentDataContainer().has(TopHologram.getHoloKey(), PersistentDataType.STRING)) {
                e.remove();
                removed++;
            }
        }
        holograms.clear();
        plugin.getLogger().info("[TopHologram] Полная зачистка мира '" + world.getName() + "': удалено " + removed + " сущностей.");
        return removed;
    }

    public int purgeNear(Location center, int radiusBlocks) {
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        int chunkRadius = (radiusBlocks >> 4) + 1;

        int removed = 0;
        int scanned = 0;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                boolean wasLoaded = world.isChunkLoaded(cx, cz);
                Chunk chunk = world.getChunkAt(cx, cz); // force-load
                scanned++;

                for (Entity e : chunk.getEntities()) {
                    if (e.getType() == EntityType.TEXT_DISPLAY) {
                        e.remove();
                        removed++;
                    }
                }

                if (!wasLoaded) {
                    chunk.unload(false);
                }
            }
        }

        holograms.clear();
        plugin.getLogger().info("[TopHologram] Зачистка возле (" + center.getBlockX() + "," + center.getBlockZ()
                + "), радиус " + radiusBlocks + " блоков, просканировано чанков: " + scanned + ", удалено сущностей: " + removed);
        return removed;
    }

    public void startAutoUpdate() {
        // Первое обновление - сразу через 5 секунд после старта, далее каждые 15 секунд
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L * 5, 20L * 15);
    }
}