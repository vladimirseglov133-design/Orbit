package org.warpeak.orbit.arena;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.warpeak.orbit.Orbit;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class ArenaManager {

    private final Orbit plugin;
    private World duelsWorld;

    private int nextOffset = 0;
    private int arenaSpacing = 200;
    private int arenaBaseY = 100;
    private final Deque<Integer> freeSlots = new ArrayDeque<>();

    private Clipboard cachedSchematic;

    public ArenaManager(Orbit plugin) {
        this.plugin = plugin;
    }

    public void setupWorld() {
        arenaSpacing = plugin.getSettings().integer("arena.spacing", 200, 64, 4096);
        arenaBaseY = plugin.getSettings().integer("arena.base-y", 100, -64, 320);
        String worldName = plugin.getConfig().getString("arena.world", "duels_world");
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        duelsWorld = Bukkit.getWorld(worldName);
        if (duelsWorld == null) {
            duelsWorld = creator.createWorld();
        }
        if (duelsWorld == null) {
            plugin.getLogger().severe("Не удалось создать мир арен '" + worldName + "'.");
            return;
        }

        duelsWorld.setTime(plugin.getSettings().longValue("arena.world-time", 6000L, 0L, 24_000L));
        duelsWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,
                plugin.getSettings().bool("arena.game-rules.daylight-cycle", false));
        duelsWorld.setGameRule(GameRule.DO_WEATHER_CYCLE,
                plugin.getSettings().bool("arena.game-rules.weather-cycle", false));
        duelsWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN,
                plugin.getSettings().bool("arena.game-rules.immediate-respawn", true));

        loadSchematic();
    }

    /** Applies arena layout/game-rule settings that can be refreshed without recreating the world. */
    public void reloadRuntimeSettings(boolean reloadSchematic) {
        arenaSpacing = plugin.getSettings().integer("arena.spacing", 200, 64, 4096);
        arenaBaseY = plugin.getSettings().integer("arena.base-y", 100, -64, 320);
        if (duelsWorld != null) {
            duelsWorld.setTime(plugin.getSettings().longValue("arena.world-time", 6000L, 0L, 24_000L));
            duelsWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,
                    plugin.getSettings().bool("arena.game-rules.daylight-cycle", false));
            duelsWorld.setGameRule(GameRule.DO_WEATHER_CYCLE,
                    plugin.getSettings().bool("arena.game-rules.weather-cycle", false));
            duelsWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN,
                    plugin.getSettings().bool("arena.game-rules.immediate-respawn", true));
        }
        if (reloadSchematic) {
            cachedSchematic = null;
            loadSchematic();
        }
    }

    private void loadSchematic() {
        try {
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString(
                    "arena.schematic", "schematics/arena.schem"));
            if (!file.exists()) {
                plugin.getLogger().warning("Файл схематики арены не найден: " + file.getPath());
                return;
            }
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                cachedSchematic = reader.read();
            }
            plugin.getLogger().info("Схематика арены успешно загружена.");
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось загрузить схематику арены: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Location claimArena() {
        if (duelsWorld == null) throw new IllegalStateException("Мир арен не загружен.");
        int offset;
        if (!freeSlots.isEmpty()) {
            offset = freeSlots.poll();
        } else {
            offset = nextOffset;
            nextOffset += arenaSpacing;
        }
        return new Location(duelsWorld, offset, arenaBaseY, 0);
    }

    public void releaseArena(Location loc) {
        freeSlots.add(loc.getBlockX());
    }

    private void preloadChunks(Location origin) {
        int radius = plugin.getSettings().integer("arena.chunk-preload-radius", 3, 0, 16);
        int centerChunkX = origin.getBlockX() >> 4;
        int centerChunkZ = origin.getBlockZ() >> 4;

        World world = origin.getWorld();
        for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
            for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
    }

    /**
     * Синхронная вставка арены. Возвращает true если всё прошло успешно.
     * Если false - значит арену НЕ надо использовать, дуэль нужно отменить.
     */
    public boolean pasteArena(Location location) {
        if (cachedSchematic == null) {
            plugin.getLogger().warning("Попытка вставить арену, но схематика не загружена!");
            return false;
        }

        long start = System.currentTimeMillis();

        try {
            preloadChunks(location);

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());

            SideEffectSet sideEffectSet = SideEffectSet.defaults()
                    .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
                    .with(SideEffect.EVENTS, SideEffect.State.OFF)
                    .with(SideEffect.UPDATE, SideEffect.State.OFF)
                    .with(SideEffect.VALIDATION, SideEffect.State.OFF)
                    .with(SideEffect.ENTITY_AI, SideEffect.State.OFF)
                    .with(SideEffect.LIGHTING, SideEffect.State.ON);

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .build()) {

                editSession.setSideEffectApplier(sideEffectSet);

                Operation operation = new ClipboardHolder(cachedSchematic)
                        .createPaste(editSession)
                        .to(BlockVector3.at(location.getX(), location.getY(), location.getZ()))
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            long took = System.currentTimeMillis() - start;
            plugin.getLogger().info("Арена вставлена за " + took + " мс.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА при вставке арены: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}