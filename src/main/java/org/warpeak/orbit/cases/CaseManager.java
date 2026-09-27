package org.warpeak.orbit.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.warpeak.orbit.Orbit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class CaseManager {

    private final Orbit plugin;
    private final Random random = new Random();

    private Location caseLocation;
    private long price = 100;

    private final Set<UUID> playersOpening = new HashSet<>();

    public CaseManager(Orbit plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    private void loadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        price = cfg.getLong("case.price", 100);

        if (!cfg.contains("case.world")) return;

        String worldName = cfg.getString("case.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир для кейса '" + worldName + "' не найден.");
            return;
        }

        double x = cfg.getDouble("case.x");
        double y = cfg.getDouble("case.y");
        double z = cfg.getDouble("case.z");
        this.caseLocation = new Location(world, x, y, z);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (caseLocation.getWorld() != null) {
                CaseHologram.spawnOrUpdate(caseLocation, price);
            }
        }, 40L);
    }

    public void setCaseLocation(Location loc, long newPrice) {
        this.caseLocation = loc.getBlock().getLocation();
        this.price = newPrice;

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("case.world", caseLocation.getWorld().getName());
        cfg.set("case.x", caseLocation.getBlockX());
        cfg.set("case.y", caseLocation.getBlockY());
        cfg.set("case.z", caseLocation.getBlockZ());
        cfg.set("case.price", newPrice);
        plugin.saveConfig();

        CaseHologram.spawnOrUpdate(caseLocation, newPrice);
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
        int totalWeight = Arrays.stream(CasePrize.values()).mapToInt(CasePrize::getWeight).sum();
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (CasePrize prize : CasePrize.values()) {
            cursor += prize.getWeight();
            if (roll < cursor) return prize;
        }
        return CasePrize.values()[0];
    }

    public boolean tryCharge(Player p) {
        long coins = Orbit.get().getStatsManager().getStats(p).coins;
        if (coins < price) return false;
        Orbit.get().getStatsManager().removeCoins(p, price);
        return true;
    }

    /** Выдаёт приз игроку: разблокирует, при необходимости - компенсирует дубликат. */
    public void giveReward(Player p, CasePrize prize) {
        if (!Orbit.get().getPrefixManager().hasLuckPerms()) {
            p.sendMessage("§cLuckPerms не установлен, префикс не выдан. Обратитесь к администрации.");
            return;
        }

        boolean isNew = Orbit.get().getPrefixManager().unlock(p, prize);

        if (!isNew) {
            long refund = price / 2;
            Orbit.get().getStatsManager().addCoins(p, refund);
            p.sendMessage("§7У вас уже есть этот приз! §fКомпенсация: §e" + refund + " монет.");
        } else {
            CasePrize equipped = Orbit.get().getPrefixManager().getEquipped(p);
            if (equipped == prize) {
                p.sendMessage("§aПрефикс " + prize.getColoredDisplay() + " §aполучен и надет автоматически!");
            } else {
                p.sendMessage("§aПрефикс " + prize.getColoredDisplay() + " §aполучен! Наденьте его через меню префиксов.");
            }
        }
    }
}