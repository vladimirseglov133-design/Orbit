package org.warpeak.orbit.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.warpeak.orbit.Orbit;

public class StrongFishingRodListener implements Listener {

    private static final NamespacedKey ROD_KEY = new NamespacedKey(Orbit.get(), "strong_fishing_rod");

    // Множитель силы притяжения. Ванильный крюк тянет слабо (~0.2),
    // мы делаем в разы сильнее.
    private static final double PULL_STRENGTH = 1.8;
    private static final double PULL_Y_BOOST = 0.35;

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        if (!(event.getCaught() instanceof LivingEntity caught)) return;

        Player fisher = event.getPlayer();
        ItemStack rod = fisher.getInventory().getItemInMainHand();

        if (!isStrongRod(rod)) {
            rod = fisher.getInventory().getItemInOffHand();
            if (!isStrongRod(rod)) return;
        }

        Vector direction = fisher.getLocation().toVector()
                .subtract(caught.getLocation().toVector());

        if (direction.lengthSquared() < 0.0001) return;

        direction.normalize().multiply(PULL_STRENGTH);
        direction.setY(Math.max(direction.getY(), PULL_Y_BOOST));

        caught.setVelocity(direction);

        caught.getWorld().playSound(caught.getLocation(),
                org.bukkit.Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
    }

    private boolean isStrongRod(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ROD_KEY, PersistentDataType.BYTE);
    }
}