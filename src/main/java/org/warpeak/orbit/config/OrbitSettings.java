package org.warpeak.orbit.config;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.warpeak.orbit.Orbit;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Typed access to the live plugin configuration. Values are read on demand, so
 * settings changed by /orbit reload are used by subsequent gameplay actions.
 */
public final class OrbitSettings {

    private final Orbit plugin;
    private final Set<String> warnedPaths = new HashSet<>();

    public OrbitSettings(Orbit plugin) {
        this.plugin = plugin;
    }

    public int integer(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public int integer(String path, int fallback, int min, int max) {
        return clamp(integer(path, fallback), min, max);
    }

    public long longValue(String path, long fallback, long min, long max) {
        return clamp(plugin.getConfig().getLong(path, fallback), min, max);
    }

    public double decimal(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        return Double.isFinite(value) ? value : fallback;
    }

    public double decimal(String path, double fallback, double min, double max) {
        double value = decimal(path, fallback);
        return Math.max(min, Math.min(max, value));
    }

    public float decimalFloat(String path, float fallback, float min, float max) {
        return (float) decimal(path, fallback, min, max);
    }

    public boolean bool(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    public String text(String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        return ChatColor.translateAlternateColorCodes('&', value == null ? fallback : value);
    }

    public List<String> textList(String path, List<String> fallback) {
        List<String> values = plugin.getConfig().getStringList(path);
        if (values.isEmpty()) return fallback.stream()
                .map(value -> ChatColor.translateAlternateColorCodes('&', value))
                .toList();
        return values.stream()
                .map(value -> ChatColor.translateAlternateColorCodes('&', value))
                .toList();
    }

    public Material material(String path, Material fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.isBlank()) return fallback;
        Material material = Material.matchMaterial(configured.trim());
        if (material != null) return material;
        warnOnce(path, "неизвестный материал '" + configured + "', использую " + fallback);
        return fallback;
    }

    public Sound sound(String path, Sound fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.isBlank()) return fallback;
        try {
            return Sound.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warnOnce(path, "неизвестный звук '" + configured + "', использую " + fallback);
            return fallback;
        }
    }

    public Particle particle(String path, Particle fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.isBlank()) return fallback;
        try {
            Particle particle = Particle.valueOf(configured.trim().toUpperCase(Locale.ROOT));
            if (particle.getDataType() != Void.class) {
                warnOnce(path, "частице '" + configured + "' нужны дополнительные данные, использую " + fallback);
                return fallback;
            }
            return particle;
        } catch (IllegalArgumentException exception) {
            warnOnce(path, "неизвестная частица '" + configured + "', использую " + fallback);
            return fallback;
        }
    }

    /** Reads a six-digit RGB color such as "FF3355" or "#FF3355". */
    public Color color(String path, Color fallback) {
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.isBlank()) return fallback;
        String hex = configured.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            warnOnce(path, "ожидался RGB-цвет из шести hex-цифр, использую значение по умолчанию");
            return fallback;
        }
        int rgb = Integer.parseInt(hex, 16);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    public int secondsToTicks(String path, int fallbackSeconds) {
        int seconds = integer(path, fallbackSeconds, 1, 86_400);
        return seconds * 20;
    }

    public long secondsToMillis(String path, int fallbackSeconds) {
        int seconds = integer(path, fallbackSeconds, 0, 86_400);
        return seconds * 1_000L;
    }

    public void clearWarnings() {
        warnedPaths.clear();
    }

    private void warnOnce(String path, String message) {
        if (warnedPaths.add(path)) {
            plugin.getLogger().warning("Некорректная настройка '" + path + "': " + message + ".");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
