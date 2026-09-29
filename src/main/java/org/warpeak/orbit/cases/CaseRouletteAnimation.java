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

    private record AnimationSettings(double radius, double centerHeight, double frontOffset,
                                     int totalTicks, int extraSpins, int wheelSize, int cleanupTicks) { }

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
    private static List<CasePrize> pickWheelPrizes(CasePrize winner, int wheelSize) {
        List<CasePrize> all = new ArrayList<>(Arrays.asList(CasePrize.values()));
        all.remove(winner);
        Collections.shuffle(all, random);

        List<CasePrize> selected = new ArrayList<>();
        selected.add(winner);

        int need = Math.min(wheelSize - 1, all.size());
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
        Orbit orbit = Orbit.get();
        AnimationSettings settings = new AnimationSettings(
                orbit.getSettings().decimal("case.animation.radius", 1.5, 0.25, 10.0),
                orbit.getSettings().decimal("case.animation.center-height", -0.2, -10.0, 10.0),
                orbit.getSettings().decimal("case.animation.front-offset", 0.0, -5.0, 5.0),
                orbit.getSettings().integer("case.animation.duration-ticks", 200, 20, 1200),
                orbit.getSettings().integer("case.animation.extra-spins", 5, 0, 50),
                orbit.getSettings().integer("case.animation.wheel-size", 7, 2, CasePrize.values().length),
                orbit.getSettings().integer("case.animation.cleanup-delay-ticks", 60, 0, 1200)
        );

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

        Location wheelCenter = chestLoc.getBlock().getLocation().add(0.5, settings.centerHeight(), 0.5)
                .add(front.clone().multiply(settings.frontOffset()));

        // Формируем случайную выборку призов именно для этого открытия
        List<CasePrize> wheelPrizes = pickWheelPrizes(winner, settings.wheelSize());
        int n = wheelPrizes.size();
        double slice = 360.0 / n;

        int winnerIndex = 0;
        for (int i = 0; i < n; i++) {
            if (wheelPrizes.get(i) == winner) { winnerIndex = i; break; }
        }

        double winnerBaseAngle = winnerIndex * slice;
        double totalOffset = settings.extraSpins() * 360.0 + ((360.0 - winnerBaseAngle) % 360.0);

        List<OrbitSlot> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double baseAngle = i * slice;
            slots.add(new OrbitSlot(wheelPrizes.get(i), baseAngle, wheelCenter, right, settings.radius()));
        }

        Location pointerLoc = wheelCenter.clone().add(0, settings.radius() + 0.5, 0);
        TextDisplay pointer = spawnPointer(pointerLoc, orbit.getSettings().text(
                "case.animation.pointer-text", "&e▼ &fПРИЗ &e▼"));

        Orbit.get().getCaseManager().markOpening(player, true);
        player.playSound(wheelCenter, orbit.getSettings().sound(
                "case.animation.open-sound", Sound.BLOCK_CHEST_OPEN),
                (float) orbit.getSettings().decimal("case.animation.open-sound-volume", 1.0, 0.0, 2.0),
                (float) orbit.getSettings().decimal("case.animation.open-sound-pitch", 1.0, 0.5, 2.0));
        player.sendTitle("", orbit.getSettings().text(
                "case.animation.spin-subtitle", "&7Крутим барабан..."), 5, 20, 5);

        runTick(player, chestLoc, wheelCenter, slots, pointer, 0, totalOffset, winner, settings);
    }

    private static void runTick(Player player, Location chestLoc, Location wheelCenter,
                                List<OrbitSlot> slots, TextDisplay pointer,
                                int tick, double totalOffset, CasePrize winner,
                                AnimationSettings settings) {

        boolean playerLost = !player.isOnline() || player.getLocation().getWorld() != chestLoc.getWorld()
                || player.getLocation().distanceSquared(chestLoc) > 400;

        if (playerLost) {
            finishAnimation(slots, pointer, player, winner, true, wheelCenter, settings);
            return;
        }

        double t = (double) tick / settings.totalTicks();
        double eased = 1 - Math.pow(1 - t, 3);
        double offset = totalOffset * eased;

        for (OrbitSlot slot : slots) {
            slot.updatePosition(offset);
        }

        int tickInterval = Orbit.get().getSettings().integer("case.animation.tick-interval-ticks", 4, 1, 1200);
        if (tick % tickInterval == 0) {
            double startPitch = Orbit.get().getSettings().decimal("case.animation.tick-start-pitch", 1.0, 0.5, 2.0);
            double endPitch = Orbit.get().getSettings().decimal("case.animation.tick-end-pitch", 2.0, 0.5, 2.0);
            float pitch = (float) (startPitch + (endPitch - startPitch) * t);
            float volume = (float) Orbit.get().getSettings().decimal("case.animation.tick-volume", 0.5, 0.0, 2.0);
            player.playSound(chestLoc, Orbit.get().getSettings().sound(
                    "case.animation.tick-sound", Sound.UI_BUTTON_CLICK), volume, pitch);
        }

        if (tick >= settings.totalTicks()) {
            finishAnimation(slots, pointer, player, winner, false, wheelCenter, settings);
            return;
        }

        Bukkit.getScheduler().runTaskLater(Orbit.get(), () ->
                runTick(player, chestLoc, wheelCenter, slots, pointer, tick + 1, totalOffset, winner, settings), 1L);
    }

    private static void finishAnimation(List<OrbitSlot> slots, TextDisplay pointer, Player player,
                                        CasePrize winner, boolean cancelled, Location wheelCenter,
                                        AnimationSettings settings) {

        if (!cancelled) {
            Location winnerLoc = wheelCenter.clone().add(0, settings.radius(), 0);
            Orbit orbit = Orbit.get();

            Particle winParticle = orbit.getSettings().particle("case.animation.win-particle", Particle.TOTEM_OF_UNDYING);
            int winParticleCount = orbit.getSettings().integer("case.animation.win-particle-count", 40, 0, 2000);
            player.getWorld().spawnParticle(winParticle, winnerLoc, winParticleCount, 0.3, 0.4, 0.3, 0.3);
            player.playSound(winnerLoc, orbit.getSettings().sound(
                    "case.animation.win-sound", Sound.ENTITY_PLAYER_LEVELUP),
                    (float) orbit.getSettings().decimal("case.animation.win-sound-volume", 1.0, 0.0, 2.0),
                    (float) orbit.getSettings().decimal("case.animation.win-sound-pitch", 1.3, 0.5, 2.0));
            player.sendTitle(orbit.getSettings().text("case.animation.win-title", "&6Поздравляем!"),
                    orbit.getSettings().text("case.animation.win-subtitle", "&fВы получили: {prize}")
                            .replace("{prize}", winner.getColoredDisplay()),
                    orbit.getSettings().integer("case.animation.title-fade-in-ticks", 5, 0, 1200),
                    orbit.getSettings().integer("case.animation.title-stay-ticks", 60, 0, 1200),
                    orbit.getSettings().integer("case.animation.title-fade-out-ticks", 15, 0, 1200));
            player.sendMessage(orbit.getSettings().text("messages.case.reward-received",
                    "&7[&6Кейс&7] &fВы получили приз: {prize}")
                    .replace("{prize}", winner.getColoredDisplay()));

            for (OrbitSlot slot : slots) {
                if (slot.prize == winner) {
                    slot.armorStand.setGlowing(true);
                }
            }
        }

        Orbit.get().getCaseManager().markOpening(player, false);

        long cleanupDelay = cancelled ? 0L : settings.cleanupTicks();
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
        final double radius;
        final ArmorStand armorStand;
        final TextDisplay textDisplay;

        OrbitSlot(CasePrize prize, double baseAngle, Location center, Vector right, double radius) {
            this.prize = prize;
            this.baseAngle = baseAngle;
            this.center = center;
            this.right = right;
            this.radius = radius;

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
            double horizontal = Math.sin(rad) * radius;
            double vertical = Math.cos(rad) * radius;

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