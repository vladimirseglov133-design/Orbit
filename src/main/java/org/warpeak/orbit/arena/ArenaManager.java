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
    private final int ARENA_SIZE = 200;
    private final Deque<Integer> freeSlots = new ArrayDeque<>();

    private Clipboard cachedSchematic;

    public ArenaManager(Orbit plugin) {
        this.plugin = plugin;
    }

    public void setupWorld() {
        WorldCreator creator = new WorldCreator("duels_world");
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        duelsWorld = Bukkit.getWorld("duels_world");
        if (duelsWorld == null) {
            duelsWorld = creator.createWorld();
        }

        duelsWorld.setTime(6000);
        duelsWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        duelsWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        // Мгновенный респавн без экрана смерти - важно для быстрого возврата в лобби
        duelsWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);

        loadSchematic();
    }

    private void loadSchematic() {
        try {
            File file = new File(plugin.getDataFolder(), "schematics/arena.schem");
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
        int offset;
        if (!freeSlots.isEmpty()) {
            offset = freeSlots.poll();
        } else {
            offset = nextOffset;
            nextOffset += ARENA_SIZE;
        }
        return new Location(duelsWorld, offset, 100, 0);
    }

    public void releaseArena(Location loc) {
        freeSlots.add(loc.getBlockX());
    }

    private void preloadChunks(Location origin) {
        int radius = 3; // с запасом, покрывает арены до ~48 блоков в ширину
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