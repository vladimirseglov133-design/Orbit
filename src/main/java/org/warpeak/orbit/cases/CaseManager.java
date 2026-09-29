package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class CaseManager {

    private final Orbit plugin;
    private final Random random = new Random();

    private Location caseLocation;
    private long price = 100;

    private final Set<UUID> playersOpening = new java.util.HashSet<>();

    public CaseManager(Orbit plugin) {
        this.plugin = plugin;
        reloadFromConfig();
    }

    /** Reloads the configured price/location and refreshes the case hologram. */
    public void reloadFromConfig() {
        Location previousLocation = caseLocation;
        FileConfiguration cfg = plugin.getConfig();
        price = plugin.getSettings().longValue("case.price", 100, 0, 1_000_000_000L);
        caseLocation = null;

        if (cfg.contains("case.world")) {
            String worldName = cfg.getString("case.world", "");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Мир для кейса '" + worldName + "' не найден.");
            } else {
                double x = cfg.getDouble("case.x", 0);
                double y = cfg.getDouble("case.y", 0);
                double z = cfg.getDouble("case.z", 0);
                caseLocation = new Location(world, x, y, z);
            }
        }

        if (previousLocation != null) CaseHologram.removeAt(previousLocation);
        if (caseLocation != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (caseLocation != null && caseLocation.getWorld() != null) {
                    CaseHologram.spawnOrUpdate(caseLocation, price);
                }
            }, 1L);
        }
    }

    public void setCaseLocation(Location loc, long newPrice) {
        if (caseLocation != null) CaseHologram.removeAt(caseLocation);
        this.caseLocation = loc.getBlock().getLocation();
        this.price = Math.max(0, Math.min(1_000_000_000L, newPrice));

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("case.world", caseLocation.getWorld().getName());
        cfg.set("case.x", caseLocation.getBlockX());
        cfg.set("case.y", caseLocation.getBlockY());
        cfg.set("case.z", caseLocation.getBlockZ());
        cfg.set("case.price", this.price);
        plugin.saveConfig();

        CaseHologram.spawnOrUpdate(caseLocation, this.price);
    }

    public Location getCaseLocation() { return caseLocation; }
    public long getPrice() { return price; }

    public boolean isCaseBlock(Location loc) {
        if (caseLocation == null || caseLocation.getWorld() == null) return false;
        if (loc.getWorld() == null) return false;
        return caseLocation.getWorld().equals(loc.getWorld())
                && caseLocation.getBlockX() == loc.getBlockX()
                && caseLocation.getBlockY() == loc.getBlockY()
                && caseLocation.getBlockZ() == loc.getBlockZ();
    }

    public boolean isOpening(Player p) {
        return playersOpening.contains(p.getUniqueId());
    }

    public void markOpening(Player p, boolean state) {
        if (state) playersOpening.add(p.getUniqueId());
        else playersOpening.remove(p.getUniqueId());
    }

    public CasePrize rollPrize() {
        List<CasePrize> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (CasePrize prize : CasePrize.values()) {
            int weight = prize.getWeight();
            if (weight <= 0) continue;
            eligible.add(prize);
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            plugin.getLogger().warning("У всех case.prizes.*.weight значение 0; выбран первый приз по умолчанию.");
            return CasePrize.values()[0];
        }

        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (CasePrize prize : eligible) {
            cursor += prize.getWeight();
            if (roll < cursor) return prize;
        }
        return eligible.get(eligible.size() - 1);
    }

    public boolean tryCharge(Player p) {
        long coins = plugin.getStatsManager().getStats(p).coins;
        if (coins < price) return false;
        plugin.getStatsManager().removeCoins(p, price);
        return true;
    }

    /** Unlocks a prize, or applies the configured compensation for a duplicate. */
    public void giveReward(Player p, CasePrize prize) {
        if (!plugin.getPrefixManager().hasLuckPerms()) {
            p.sendMessage(plugin.getSettings().text("messages.case.no-luckperms",
                    "&cLuckPerms не установлен, префикс не выдан. Обратитесь к администрации."));
            return;
        }

        boolean isNew = plugin.getPrefixManager().unlock(p, prize);
        if (!isNew) {
            int refundPercent = plugin.getSettings().integer("case.duplicate-refund-percent", 50, 0, 100);
            long refund = price * refundPercent / 100;
            plugin.getStatsManager().addCoins(p, refund);
            p.sendMessage(plugin.getSettings().text("messages.case.duplicate-refund",
                    "&7У вас уже есть этот приз! &fКомпенсация: &e{refund} монет.")
                    .replace("{refund}", Long.toString(refund)));
            return;
        }

        CasePrize equipped = plugin.getPrefixManager().getEquipped(p);
        String path = equipped == prize ? "messages.case.new-equipped" : "messages.case.new-prize";
        String fallback = equipped == prize
                ? "&aПрефикс {prize} &aполучен и надет автоматически!"
                : "&aПрефикс {prize} &aполучен! Наденьте его через меню префиксов.";
        p.sendMessage(plugin.getSettings().text(path, fallback).replace("{prize}", prize.getColoredDisplay()));
    }
}
