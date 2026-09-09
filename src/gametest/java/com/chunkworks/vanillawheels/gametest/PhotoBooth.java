/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.vanillawheels.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.client.lift.LiftScreen;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The protocol on film, with the box car as the sitter: a flat world, the
 * car a few blocks ahead of the player. Each act is photographed and a
 * mechanical verdict read from the frame: the stock car's side shows its
 * light-blue paint; painted red, the blue is gone and red is there; from
 * the driver's seat looking down at the dash the red needles are in view
 * (the physical gauges, in first person); at night the ground ahead is
 * dark with the lamps off, and with them on the lamp faces glow and the
 * ground ahead is lit (the lamps through Luminance); a Mechanic Lift is
 * placed through its item and drawn, its menu opens, Build raises the deck
 * with the car on it, Paint turns it red. One {@code booth:
 * PASS} or {@code booth: FAIL} line per check; the Gradle task reads them.
 * Client only, active only under {@code vanillawheels.photobooth}.
 *
 * <p>Ticks between acts are generous: the ground is re-meshed by chunk
 * workers, slow under a software renderer, and the photo must come after.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class PhotoBooth {
    private PhotoBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Vanilla Wheels booth");
    private static final boolean ACTIVE = Boolean.getBoolean("vanillawheels.photobooth");
    private static final ResourceLocation BOX_CAR = ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "box_car");

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    /** Ticks for the world to settle after loading, and between a change and its photo. */
    private static final int HOLD = 100;
    private static final int SETTLE = 60;
    /** Where the player stands; the car sits AHEAD blocks south of it. */
    private static final double X = 0.5;
    private static final double Z = 0.5;
    private static final double AHEAD = 6.0;

    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID car;
    private static BlockPos liftPos = BlockPos.ZERO;
    private static double groundDark = -1.0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    // No HUD: the crosshair, inverted over a dark body, reads as a lit lamp.
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, PhotoBooth::setUp);
                }
            }
            case PLACING -> {
                // The count starts once the client has the player on the mark and the car in view:
                // on a slow renderer the teleport and the spawn land some frames after they are sent.
                if (mc.player != null && mc.player.onGround() && mc.player.distanceToSqr(X, mc.player.getY(), Z) < 0.25 && carId(mc) != -1) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("Vanilla Wheels booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("vanillawheels-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the player on the grass facing south; the box car ahead, side on, facing east, half a tank in. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        double y = level.getMinBuildHeight() + 5;
        sp.teleportTo(level, X, y, Z, 0.0f, 12.0f);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        Vehicle v = Vehicle.create(level, BOX_CAR, new Vec3(X, y, Z + AHEAD), -90.0f);
        if (v == null) {
            LOG.error("booth: FAIL the box car profile is registered -- Vehicle.create returned null");
            return;
        }
        v.setFuel(v.tank().capacity() / 2);
        level.addFreshEntity(v);
        car = v.getUUID();
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        s.add(new Step(t, () -> {
            int blue = count(mc, PhotoBooth::lightBlue);
            int red = count(mc, PhotoBooth::red);
            shoot(mc, "booth-side-stock");
            verdict("the stock box car's side shows its light-blue paint", () -> blue > 1500 ? null : "light-blue pixels " + blue);
            verdict("and nothing red", () -> red < 150 ? null : "red pixels " + red);
        }));
        s.add(new Step(t += 2, () -> withCar(mc, v -> v.setPaint(DyeColor.RED))));
        s.add(new Step(t += SETTLE / 2, () -> {
            int blue = count(mc, PhotoBooth::lightBlue);
            int red = count(mc, PhotoBooth::red);
            shoot(mc, "booth-side-red");
            verdict("painted red, the side is red", () -> red > 1500 ? null : "red pixels " + red);
            verdict("and the blue is gone", () -> blue < 150 ? null : "light-blue pixels " + blue);
        }));
        // Into the driver's seat, looking down at the dash.
        s.add(new Step(t += 2, () -> withCar(mc, v -> {
            v.setPaint(DyeColor.LIGHT_BLUE);
            ServerPlayer sp = v.getServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                sp.startRiding(v, true);
            }
        })));
        s.add(new Step(t += 10, () -> {
            if (mc.player != null && mc.player.getVehicle() instanceof Vehicle v) {
                mc.player.setYRot(v.getYRot());
                mc.player.setXRot(40.0f);
                mc.player.yRotO = v.getYRot();
                mc.player.xRotO = 40.0f;
            }
        }));
        s.add(new Step(t += SETTLE / 2, () -> {
            int needles = count(mc, PhotoBooth::needleRed);
            shoot(mc, "booth-dash");
            verdict("the driver is aboard", () -> mc.player != null && mc.player.getVehicle() instanceof Vehicle ? null : "vehicle " + (mc.player == null ? null : mc.player.getVehicle()));
            verdict("from the driver's seat the dash needles are in view", () -> needles > 40 ? null : "needle pixels " + needles);
        }));
        // Out, up and behind the car for the night shots: the car turned
        // south so its beams run away from the camera, the player hovering
        // behind it looking down over the roof at the ground ahead.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            sp.stopRiding();
            ServerLevel level = sp.serverLevel();
            level.setDayTime(18000L);
            double y = level.getMinBuildHeight() + 5;
            // A fresh car facing south: the server steers a driverless car
            // by its own drive state, so turning the entity would not hold.
            if (level.getEntity(car) instanceof Vehicle old) {
                old.discard();
            }
            Vehicle v = Vehicle.create(level, BOX_CAR, new Vec3(X, y, Z + AHEAD + 1.0), 0.0f);
            if (v != null) {
                level.addFreshEntity(v);
                car = v.getUUID();
            }
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(level, X, y + 3.5, Z, 0.0f, 32.0f);
        })));
        s.add(new Step(t += SETTLE, () -> {
            groundDark = brightness(mc);
            int lamps = count(mc, PhotoBooth::lampGlow);
            shoot(mc, "booth-night-lamps-off");
            verdict("at night with the lamps off the ground ahead is dark", () -> groundDark < 60 ? null : "brightness " + groundDark);
            verdict("and the lamp faces are unlit", () -> lamps < 30 ? null : "glowing lamp pixels " + lamps);
        }));
        s.add(new Step(t += 2, () -> withCar(mc, Vehicle::cycleLights)));
        s.add(new Step(t += SETTLE + 20, () -> {
            double lit = brightness(mc);
            shoot(mc, "booth-night-lamps-on");
            verdict("the lamps are on", () -> mc.level != null && mc.level.getEntity(carId(mc)) instanceof Vehicle v && v.lit() ? null : "not lit");
            verdict("and the ground ahead is lit through Luminance", () -> lit > groundDark + 15 ? null : "dark " + groundDark + ", lit " + lit);
        }));
        // Round to the front, five blocks off the nose, for the lamp faces.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            ServerLevel level = sp.serverLevel();
            double y = level.getMinBuildHeight() + 5;
            sp.teleportTo(level, X, y + 0.5, Z + AHEAD + 1.0 + 6.5, 180.0f, 8.0f);
        })));
        s.add(new Step(t += SETTLE, () -> {
            int lamps = count(mc, PhotoBooth::lampGlow);
            shoot(mc, "booth-night-lamps-front");
            // Two 4 x 3 px lamp faces five blocks off are about 100 pixels each at 480 rows.
            verdict("with the lamps on the lamp faces glow", () -> lamps > 30 ? null : "glowing lamp pixels " + lamps);
        }));
        // The Mechanic Lift: noon again, the car gone, a lift placed through
        // the real item facing the player, who hovers over its front.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            ServerLevel level = sp.serverLevel();
            level.setDayTime(6000L);
            if (level.getEntity(car) instanceof Vehicle old) {
                old.discard();
            }
            double y = level.getMinBuildHeight() + 5;
            sp.teleportTo(level, X, y + 3.5, Z - 1.0, 0.0f, 35.0f);
            sp.setYRot(0.0f);
            ItemStack lift = new ItemStack(ModContent.LIFT_ITEM.get());
            sp.setItemInHand(InteractionHand.MAIN_HAND, lift);
            // The ground's top block is the one under the player's feet.
            BlockPos floor = BlockPos.containing(X, y, Z + 4.0).below();
            while (level.getBlockState(floor).canBeReplaced()) {
                floor = floor.below();
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
            BlockPlaceContext ctx = new BlockPlaceContext(level, sp, InteractionHand.MAIN_HAND, lift, hit);
            liftPos = ctx.getClickedPos();
            InteractionResult r = ModContent.LIFT_ITEM.get().place(ctx);
            if (!r.consumesAction()) {
                LOG.error("booth: FAIL the lift is placed -- {}", r);
            }
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        })));
        s.add(new Step(t += SETTLE, () -> {
            int steel = count(mc, PhotoBooth::steel);
            int hazard = count(mc, PhotoBooth::hazard);
            shoot(mc, "booth-lift");
            verdict("the lift is drawn: a steel deck", () -> steel > 3000 ? null : "steel pixels " + steel);
            verdict("with its hazard stripe", () -> hazard > 100 ? null : "hazard pixels " + hazard);
        }));
        // Its menu, opened the way a click opens it.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            if (sp.serverLevel().getBlockEntity(liftPos) instanceof LiftBlockEntity lift) {
                sp.openMenu(lift);
            } else {
                LOG.error("booth: FAIL the lift's block entity is at {} -- {}", liftPos, sp.serverLevel().getBlockState(liftPos));
            }
        })));
        s.add(new Step(t += 20, () -> {
            shoot(mc, "booth-lift-menu");
            verdict("the lift's screen opens", () -> mc.screen instanceof LiftScreen ? null : "screen " + mc.screen);
        }));
        // Build the box car from its parts, through the menu's own button, and photograph the deck up mid-job.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            if (sp.containerMenu instanceof LiftMenu menu) {
                menu.getSlot(LiftMenu.CHASSIS).set(ModContent.chassisStack(BOX_CAR));
                menu.getSlot(LiftMenu.WHEELS).set(new ItemStack(ModContent.WHEEL.get(), 4));
                menu.getSlot(LiftMenu.ENGINE).set(new ItemStack(ModContent.ENGINE.get()));
                menu.broadcastChanges();
                if (!menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON)) {
                    LOG.error("booth: FAIL build is accepted -- status {}", menu.buildStatus());
                }
                sp.closeContainer();
            } else {
                LOG.error("booth: FAIL the menu is open on the server -- {}", sp.containerMenu);
            }
        })));
        s.add(new Step(t += LiftMotion.UP + 5, () -> {
            double up = mc.level != null && mc.level.getBlockEntity(liftPos) instanceof LiftBlockEntity lift ? lift.raise(0.0f) : -1.0;
            int blue = count(mc, PhotoBooth::lightBlue);
            shoot(mc, "booth-lift-raised");
            verdict("the client draws the deck up mid-job", () -> up > LiftMotion.LIFT * 0.9 ? null : "raise " + up);
            verdict("with the built car on it", () -> blue > 300 ? null : "light-blue pixels " + blue);
        }));
        // Paint it red through the menu.
        s.add(new Step(t += LiftMotion.JOB, () -> onServer(mc, sp -> {
            if (sp.serverLevel().getBlockEntity(liftPos) instanceof LiftBlockEntity lift) {
                sp.openMenu(lift);
                if (sp.containerMenu instanceof LiftMenu menu) {
                    menu.getSlot(LiftMenu.DYE).set(new ItemStack(Items.RED_DYE));
                    menu.broadcastChanges();
                    if (!menu.clickMenuButton(sp, LiftMenu.PAINT_BUTTON)) {
                        LOG.error("booth: FAIL paint is accepted -- status {}", menu.paintStatus());
                    }
                    sp.closeContainer();
                }
            }
        })));
        s.add(new Step(t += LiftMotion.JOB + 10, () -> {
            int red = count(mc, PhotoBooth::red);
            int blue = count(mc, PhotoBooth::lightBlue);
            shoot(mc, "booth-lift-painted");
            verdict("the lift painted the car red", () -> red > 300 && blue < 150 ? null : "red " + red + ", light-blue " + blue);
        }));
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /** The lift's steel: a grey with little tint, mid-bright, lit by day. */
    private static boolean steel(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 60 && r < 200 && Math.abs(r - g) < 14 && b > r - 4 && b < r + 30;
    }

    /** The hazard stripe's yellow. */
    private static boolean hazard(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 170 && g > 120 && b < 90 && r > b + 100;
    }

    private static int carId(Minecraft mc) {
        if (mc.level == null) {
            return -1;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Vehicle && e.getUUID().equals(car)) {
                return e.getId();
            }
        }
        return -1;
    }

    // --- reading the frame -----------------------------------------------

    /** The body swatch (grey, noised) under light-blue dye: blue well above red, green between. */
    private static boolean lightBlue(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return b > r + 50 && g > r + 20 && b > 90;
    }

    /** The same swatch under red dye, on a lit or a shaded face; not the hazard stripe's yellow. */
    private static boolean red(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 80 && g < 110 && r > g + 40 && r > b + 40;
    }

    /** The needle swatch (220, 40, 40), lit by the cab's daylight. */
    private static boolean needleRed(int rgb) {
        return red(rgb);
    }

    /** The lamp swatch (250, 240, 170) at full brightness: a warm near-white, which nothing else in a night frame is. */
    private static boolean lampGlow(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 200 && g > 190 && b > 120 && b < r - 30;
    }

    /**
     * effects: returns how many pixels of the frame's middle satisfy
     * {@code test} (rgb, no alpha): rows 36..70 % and columns 20..80 %,
     * which is below the sky and above the hotbar and the hand, and where
     * every shot puts the car
     */
    private static int count(Minecraft mc, IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = (int) (h * 0.36); y < (int) (h * 0.70); y++) {
                for (int x = (int) (w * 0.2); x < (int) (w * 0.8); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    if (test.test(rgb)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    /**
     * The mean brightness (0..255) of the patch of frame where the beams
     * land: from the night camera (3.5 blocks up, 32 degrees down, the car
     * 7 blocks ahead facing away) the ground 9..19 blocks out spans 20..36 %
     * of the frame's height, and the two beams the middle sixth of its width.
     */
    private static double brightness(Minecraft mc) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            long sum = 0;
            int n = 0;
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = (int) (h * 0.2); y < (int) (h * 0.36); y += 2) {
                for (int x = (int) (w * 0.42); x < (int) (w * 0.58); x += 2) {
                    int abgr = image.getPixelRGBA(x, y);
                    sum += (abgr & 0xFF) + ((abgr >> 8) & 0xFF) + ((abgr >> 16) & 0xFF);
                    n += 3;
                }
            }
            return n == 0 ? 0.0 : (double) sum / n;
        }
    }

    // --- plumbing --------------------------------------------------------

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void withCar(Minecraft mc, Consumer<Vehicle> action) {
        onServer(mc, sp -> {
            if (sp.serverLevel().getEntity(car) instanceof Vehicle v) {
                action.accept(v);
            } else {
                LOG.error("booth: FAIL the car is in the level -- gone");
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
