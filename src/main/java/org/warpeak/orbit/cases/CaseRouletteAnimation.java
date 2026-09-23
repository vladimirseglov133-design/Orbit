package org.warpeak.orbit.cases;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.warpeak.orbit.Orbit;

import java.util.*;

public class CaseRouletteAnimation {

    // ==== Настройки внешнего вида ====
    private static final double RADIUS = 1.5;
    private static final double CENTER_HEIGHT = -0.2;
    private static final double FRONT_OFFSET = 0.0;
    private static final int TOTAL_TICKS = 200;
    private static final int EXTRA_SPINS = 5;

    // Сколько призов показываем на колесе за один раз (не все 14, а случайная выборка)
    private static final int WHEEL_SIZE = 7;

    private static final Random random = new Random();
    private static final Queue<AnimationRequest> queue = new LinkedList<>();
    private static boolean running = false;

    private record AnimationRequest(Player player, Location chestLoc, CasePrize winner) {}

    public static void play(Player player, Location chestLoc, CasePrize winner) {
        queue.add(new AnimationRequest(player, chestLoc, winner));
        if (!running) runNext();
    }

    private static void runNext() {
        AnimationRequest req = queue.poll();
        if (req == null) {
            running = false;
            return;
        }
        running = true;
        startAnimation(req);
    }

    /** Выбирает WHEEL_SIZE случайных призов, гарантированно включая победителя. */
    private static List<CasePrize> pickWheelPrizes(CasePrize winner) {
        List<CasePrize> all = new ArrayList<>(Arrays.asList(CasePrize.values()));
        all.remove(winner);
        Collections.shuffle(all, random);

        List<CasePrize> selected = new ArrayList<>();
        selected.add(winner);

        int need = Math.min(WHEEL_SIZE - 1, all.size());
        for (int i = 0; i < need; i++) {
            selected.add(all.get(i));
        }

        Collections.shuffle(selected, random);
        return selected;
    }

    private static void startAnimation(AnimationRequest req) {
        Player player = req.player();
        Location chestLoc = req.chestLoc();
        CasePrize winner = req.winner();

        Block block = chestLoc.getBlock();
        BlockFace facing = BlockFace.NORTH;
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            facing = directional.getFacing();
        }

        Vector front = new Vector(facing.getModX(), 0, facing.getModZ());
        if (front.lengthSquared() < 0.01) front = new Vector(0, 0, 1);
        front.normalize();

        Vector right = new Vector(-front.getZ(), 0, front.getX());

        Location wheelCenter = chestLoc.getBlock().getLocation().add(0.5, CENTER_HEIGHT, 0.5)
                .add(front.clone().multiply(FRONT_OFFSET));

        // Формируем случайную выборку призов именно для этого открытия
        List<CasePrize> wheelPrizes = pickWheelPrizes(winner);
        int n = wheelPrizes.size();
        double slice = 360.0 / n;

        int winnerIndex = 0;
        for (int i = 0; i < n; i++) {
            if (wheelPrizes.get(i) == winner) { winnerIndex = i; break; }
        }

        double winnerBaseAngle = winnerIndex * slice;
        double totalOffset = EXTRA_SPINS * 360.0 + ((360.0 - winnerBaseAngle) % 360.0);

        List<OrbitSlot> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double baseAngle = i * slice;
            slots.add(new OrbitSlot(wheelPrizes.get(i), baseAngle, wheelCenter, right));
        }

        Location pointerLoc = wheelCenter.clone().add(0, RADIUS + 0.5, 0);
        TextDisplay pointer = spawnPointer(pointerLoc, "§e▼ §fПРИЗ §e▼");

        Orbit.get().getCaseManager().markOpening(player, true);
        player.playSound(wheelCenter, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        player.sendTitle("", "§7Крутим барабан...", 5, 20, 5);

        runTick(player, chestLoc, wheelCenter, slots, pointer, 0, totalOffset, winner);
    }

    private static void runTick(Player player, Location chestLoc, Location wheelCenter,
                                List<OrbitSlot> slots, TextDisplay pointer,
                                int tick, double totalOffset, CasePrize winner) {

        boolean playerLost = !player.isOnline() || player.getLocation().getWorld() != chestLoc.getWorld()
                || player.getLocation().distanceSquared(chestLoc) > 400;

        if (playerLost) {
            finishAnimation(slots, pointer, player, winner, true, wheelCenter);
            return;
        }

        double t = (double) tick / TOTAL_TICKS;
        double eased = 1 - Math.pow(1 - t, 3);
        double offset = totalOffset * eased;

        for (OrbitSlot slot : slots) {
            slot.updatePosition(offset);
        }

        if (tick % 4 == 0) {
            float pitch = Math.min(2.0f, 1.0f + (float) t * 1.2f);
            player.playSound(chestLoc, Sound.UI_BUTTON_CLICK, 0.5f, pitch);
        }

        if (tick >= TOTAL_TICKS) {
            finishAnimation(slots, pointer, player, winner, false, wheelCenter);
            return;
        }

        Bukkit.getScheduler().runTaskLater(Orbit.get(), () ->
                runTick(player, chestLoc, wheelCenter, slots, pointer, tick + 1, totalOffset, winner), 1L);
    }

    private static void finishAnimation(List<OrbitSlot> slots, TextDisplay pointer, Player player,
                                        CasePrize winner, boolean cancelled, Location wheelCenter) {

        if (!cancelled) {
            Location winnerLoc = wheelCenter.clone().add(0, RADIUS, 0);

            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winnerLoc, 40, 0.3, 0.4, 0.3, 0.3);
            player.playSound(winnerLoc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            player.sendTitle("§6Поздравляем!", "§fВы получили: " + winner.getColoredDisplay(), 5, 60, 15);
            player.sendMessage("§7[§6Кейс§7] §fВы получили приз: " + winner.getColoredDisplay());

            for (OrbitSlot slot : slots) {
                if (slot.prize == winner) {
                    slot.armorStand.setGlowing(true);
                }
            }
        }

        Orbit.get().getCaseManager().markOpening(player, false);

        long cleanupDelay = cancelled ? 0L : 60L;
        Bukkit.getScheduler().runTaskLater(Orbit.get(), () -> {
            for (OrbitSlot slot : slots) slot.remove();
            pointer.remove();
            runNext();
        }, cleanupDelay);
    }

    private static TextDisplay spawnPointer(Location loc, String text) {
        return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setText(text);
        });
    }

    private static class OrbitSlot {
        final CasePrize prize;
        final double baseAngle;
        final Location center;
        final Vector right;
        final ArmorStand armorStand;
        final TextDisplay textDisplay;

        OrbitSlot(CasePrize prize, double baseAngle, Location center, Vector right) {
            this.prize = prize;
            this.baseAngle = baseAngle;
            this.center = center;
            this.right = right;

            Location initial = computePosition(baseAngle);
            this.armorStand = spawnArmorStand(initial, prize);
            this.textDisplay = spawnLabel(initial.clone().add(0, 0.4, 0), prize);
        }

        void updatePosition(double offset) {
            double angle = baseAngle + offset;
            Location loc = computePosition(angle);
            armorStand.teleport(loc);
            textDisplay.teleport(loc.clone().add(0, 0.4, 0));
        }

        Location computePosition(double angleDeg) {
            double rad = Math.toRadians(angleDeg);
            double horizontal = Math.sin(rad) * RADIUS;
            double vertical = Math.cos(rad) * RADIUS;

            return center.clone()
                    .add(right.clone().multiply(horizontal))
                    .add(0, vertical, 0);
        }

        void remove() {
            armorStand.remove();
            textDisplay.remove();
        }

        private static ArmorStand spawnArmorStand(Location loc, CasePrize prize) {
            return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.setSmall(true);
                as.setInvulnerable(true);
                as.setBasePlate(false);
                as.setArms(false);
                as.setCustomNameVisible(false);
                as.setCollidable(false);
                as.getEquipment().setHelmet(new ItemStack(prize.getMaterial()));
            });
        }

        private static TextDisplay spawnLabel(Location loc, CasePrize prize) {
            return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(true);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setText(prize.getColoredDisplay());
            });
        }
    }
}