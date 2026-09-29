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
package com.chunkworks.vanillawheels.client;

import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.BakedMesh;
import com.chunkworks.vanillawheels.domain.Dial;
import com.chunkworks.vanillawheels.domain.Mesh;
import com.chunkworks.vanillawheels.domain.Parts;
import com.chunkworks.vanillawheels.domain.Rotation;
import com.chunkworks.vanillawheels.domain.Transform;
import com.chunkworks.vanillawheels.domain.Vec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * A profile's meshes, mirrored into the game's frame, oriented, cut into
 * the parts the renderer moves or colours, and baked: the body (paint
 * goes on it), the lamps (lit or not), the glass (drawn last, translucent),
 * each gauge's needle with its dial, each door with its hinge, the rest,
 * and the wheel with where it goes. Built once per profile and kept until
 * the meshes reload; every vehicle of that profile draws the same one.
 */
public final class Appearance {
    private static final Map<VehicleProfile, Appearance> CACHE = new ConcurrentHashMap<>();

    /** A needle and the dial it turns on, in blocks. */
    public record Needle(BakedMesh mesh, Dial dial, VehicleProfile.GaugeKind kind) {}

    /** A door:what swings on the hinge, split into the painted part (drawn with the paint), the lamps (drawn bright when lit) and the rest. */
    public record Hinge(BakedMesh mesh, BakedMesh painted, BakedMesh lamps, Rotation open) {}

    /** A wheel's place in blocks, whether it steers, and which side it is on (right-side wheels are turned round). */
    public record WheelSlot(Vec at, boolean steers, boolean right) {}

    public final ResourceLocation texture;
    /** The wheel's texture: its own Blockbench project's if it has one, else the body's. */
    public final ResourceLocation wheelTexture;
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    public final BakedMesh rest;
    public final BakedMesh body;
    public final BakedMesh lamps;
    public final BakedMesh glass;
    /** The cage, frame and mirrors: not drawn for whoever looks out from aboard in first person. */
    public final BakedMesh cockpit;
    public final List<Needle> needles;
    public final List<Hinge> doors;
    public final BakedMesh wheel;
    public final List<WheelSlot> wheels;
    public final double wheelRadius;
    public final double cullRadius;
    public final double extent;

    private Appearance(VehicleProfile p, Mesh frame, Mesh wheelMesh) {
        Transform t = p.toLocal();
        double scale = p.scale();
        Mesh local = frame.transformed(t).orientedOutward();
        // Each piece is cut from what the pieces before it left (Parts), so a door's lenses and
        // painted panels swing with the door and are not drawn a second time on the body.
        Parts cut = Parts.cut(local,
                p.gauges().stream().map(g -> g.part().transformed(t).selector()).toList(),
                p.doors().stream().map(d -> d.part().transformed(t).selector()).toList(),
                p.headlights().flatMap(VehicleProfile.Headlights::part).map(sel -> sel.transformed(t).selector()),
                p.glass().map(sel -> sel.transformed(t).selector()),
                p.cockpit().map(sel -> sel.transformed(t).selector()),
                p.paint().map(paint -> paint.part().transformed(t).selector()));
        List<Needle> ns = new ArrayList<>();
        for (int i = 0; i < p.gauges().size(); i++) {
            VehicleProfile.Gauge g = p.gauges().get(i);
            Dial dial = g.dial(t);
            ns.add(new Needle(BakedMesh.of(cut.needles().get(i), scale), new Dial(dial.pivot().times(scale), dial.axis(), dial.rest(), dial.sweep()), g.kind()));
        }
        List<Hinge> ds = new ArrayList<>();
        for (int i = 0; i < p.doors().size(); i++) {
            VehicleProfile.Door d = p.doors().get(i);
            Parts.Door door = cut.doors().get(i);
            Rotation open = new Rotation(d.hinge(), d.axis(), d.open()).mirrored(t);
            ds.add(new Hinge(BakedMesh.of(door.rest(), scale), BakedMesh.of(door.painted(), scale), BakedMesh.of(door.lamps(), scale),
                    new Rotation(open.pivot().times(scale), open.axis(), open.radians())));
        }
        this.texture = p.texture().or(() -> MeshLibrary.INSTANCE.embeddedTexture(p.mesh())).orElse(MISSING);
        this.wheelTexture = p.wheelMesh().flatMap(MeshLibrary.INSTANCE::embeddedTexture).orElse(this.texture);
        this.rest = BakedMesh.of(cut.rest(), scale);
        this.body = BakedMesh.of(cut.body(), scale);
        this.lamps = BakedMesh.of(cut.lamps(), scale);
        this.glass = BakedMesh.of(cut.glass(), scale);
        this.cockpit = BakedMesh.of(cut.cockpit(), scale);
        this.needles = List.copyOf(ns);
        this.doors = List.copyOf(ds);
        Mesh wheelLocal = wheelMesh.transformed(t).orientedOutward();
        this.wheel = BakedMesh.of(wheelLocal, scale);
        List<WheelSlot> ws = new ArrayList<>();
        for (VehicleProfile.WheelPosition w : p.wheels().positions()) {
            // A wheel's place is given as forward/right, the vehicle's own
            // sense, not as a mesh vector: it needs no mirror. The game's
            // local frame is right-handed with +Z forward, so its +X is the
            // vehicle's left and a wheel on the right sits at negative x.
            Vec at = new Vec(-w.right() * scale, w.up(p.wheels().radius()) * scale, w.forward() * scale);
            ws.add(new WheelSlot(at, w.steers(), at.x() < 0));
        }
        this.wheels = List.copyOf(ws);
        this.wheelRadius = p.blocks(p.wheels().radius());
        this.extent = Math.max(Math.max(p.body().width(), p.body().length()), p.body().height());
        this.cullRadius = Math.hypot(p.body().length() / 2, p.body().width() / 2) + 0.5;
    }

    /** effects: returns the appearance of {@code profile}, building it on first sight */
    public static Appearance of(VehicleProfile profile) {
        return CACHE.computeIfAbsent(profile, p -> new Appearance(p, MeshLibrary.INSTANCE.get(p.mesh()),
                MeshLibrary.INSTANCE.get(p.wheelMesh().orElse(p.mesh()))));
    }

    /** effects: forgets every appearance, so the next draw rebuilds from fresh meshes */
    public static void invalidate() {
        CACHE.clear();
    }
}
