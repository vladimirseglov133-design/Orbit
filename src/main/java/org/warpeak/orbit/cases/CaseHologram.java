package org.warpeak.orbit.cases;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.warpeak.orbit.Orbit;

import java.util.Collection;

public class CaseHologram {

    private static final String META_KEY = "orbit_case_holo";

    public static void spawnOrUpdate(Location chestLoc, long price) {
        Location base = chestLoc.clone().add(0.5, 1.3, 0.5);

        removeAt(chestLoc);

        Orbit plugin = Orbit.get();
        spawnLine(base.clone().add(0, 0.5, 0), plugin.getSettings().text(
                "case.hologram.title", "&6&lКейс Префиксов"));
        spawnLine(base.clone().add(0, 0.25, 0), plugin.getSettings().text(
                "case.hologram.price-line", "&eЦена: &f{price} монет").replace("{price}", Long.toString(price)));
        spawnLine(base.clone(), plugin.getSettings().text(
                "case.hologram.click-line", "&7ПКМ чтобы открыть"));
    }

    public static void removeAt(Location chestLoc) {
        if (chestLoc == null || chestLoc.getWorld() == null) return;
        Location base = chestLoc.clone().add(0.5, 1.3, 0.5);
        Collection<Entity> nearby = base.getWorld().getNearbyEntities(base, 1.5, 2.0, 1.5);
        for (Entity entity : nearby) {
            if (entity.getType() == EntityType.ARMOR_STAND && entity.hasMetadata(META_KEY)) {
                entity.remove();
            }
        }
    }

    private static void spawnLine(Location loc, String text) {
        loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.setCustomName(text);
            as.setMarker(true);
            as.setSmall(true);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setMetadata(META_KEY, new FixedMetadataValue(Orbit.get(), true));
        });
    }
}