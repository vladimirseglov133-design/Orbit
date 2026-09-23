package org.warpeak.orbit.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.stats.StatsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TopHologram {

    public enum Type {
        KILLS, COINS
    }

    // ВАЖНО: используем PersistentDataContainer, а не Metadata,
    // т.к. Metadata не переживает перезапуск сервера, а сущности (TextDisplay) - переживают.
    // Без этого при каждом рестарте создавались бы дубликаты голограмм.
    private static final NamespacedKey HOLO_KEY = new NamespacedKey(Orbit.get(), "orbit_top_holo");

    private static final double LINE_SPACING = 0.28;
    private static final int TOP_SIZE = 10;

    private final Type type;
    private final Location baseLocation;
    private final List<TextDisplay> lines = new ArrayList<>();

    public TopHologram(Type type, Location baseLocation) {
        this.type = type;
        this.baseLocation = baseLocation.clone();
    }

    public void spawn() {
        removeExisting(baseLocation);
        lines.clear();

        List<StatsManager.TopEntry> top = fetchTop();
        List<String> textLines = buildLines(top);

        double startY = baseLocation.getY() + (textLines.size() - 1) * LINE_SPACING;

        for (int i = 0; i < textLines.size(); i++) {
            Location lineLoc = baseLocation.clone();
            lineLoc.setY(startY - i * LINE_SPACING);
            lines.add(spawnLine(lineLoc, textLines.get(i)));
        }

        Orbit.get().getLogger().info("[TopHologram] " + type + " заспавнена, строк: " + lines.size());
    }

    public void update() {
        if (!baseLocation.getChunk().isLoaded()) {
            baseLocation.getChunk().load();
        }

        List<StatsManager.TopEntry> top = fetchTop();
        List<String> textLines = buildLines(top);

        boolean needRespawn = textLines.size() != lines.size();

        if (!needRespawn) {
            for (TextDisplay td : lines) {
                if (td == null || !td.isValid() || td.isDead()) {
                    needRespawn = true;
                    break;
                }
            }
        }

        if (needRespawn) {
            Orbit.get().getLogger().info("[TopHologram] " + type + " требует пересоздания.");
            spawn();
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setText(textLines.get(i));
        }
    }

    private List<StatsManager.TopEntry> fetchTop() {
        return type == Type.KILLS
                ? Orbit.get().getStatsManager().getTopByKills(TOP_SIZE)
                : Orbit.get().getStatsManager().getTopByCoins(TOP_SIZE);
    }

    private List<String> buildLines(List<StatsManager.TopEntry> top) {
        List<String> result = new ArrayList<>();

        String title = type == Type.KILLS
                ? "§c§lТОП ПО ПОБЕДАМ"
                : "§6§lТОП ПО БАЛАНСУ";
        result.add(title);
        result.add("§7");

        String[] placeColors = {"§6", "§7", "§c", "§f", "§f", "§f", "§f", "§f", "§f", "§f"};
        String unit = type == Type.KILLS ? " побед" : " монет";

        if (top.isEmpty()) {
            result.add("§7Пока никто не в топе");
        } else {
            for (int i = 0; i < top.size(); i++) {
                StatsManager.TopEntry entry = top.get(i);
                String color = i < placeColors.length ? placeColors[i] : "§f";
                result.add(color + "#" + (i + 1) + " §f" + entry.name + " §7- §e" + entry.value + unit);
            }
        }

        return result;
    }

    private TextDisplay spawnLine(Location loc, String text) {
        return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setShadowed(true);
            td.setDefaultBackground(true);
            td.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            td.setText(text);
            td.setPersistent(true);
            td.getPersistentDataContainer().set(HOLO_KEY, PersistentDataType.STRING, type.name());
        });
    }

    private void removeExisting(Location loc) {
        // Радиус чуть больше - на случай если голограмма была установлена
        // с бОльшим количеством строк в прошлой версии кода
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 3.0, 6.0, 3.0);
        int removedCount = 0;
        for (Entity e : nearby) {
            if (e.getType() == EntityType.TEXT_DISPLAY
                    && e.getPersistentDataContainer().has(HOLO_KEY, PersistentDataType.STRING)) {
                e.remove();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            Orbit.get().getLogger().info("[TopHologram] Удалено старых сущностей голограммы: " + removedCount);
        }
    }

    public void remove() {
        for (TextDisplay td : lines) {
            if (td != null && td.isValid()) td.remove();
        }
        lines.clear();
    }

    public static NamespacedKey getHoloKey() {
        return HOLO_KEY;
    }

    public Type getType() { return type; }
    public Location getBaseLocation() { return baseLocation; }
}