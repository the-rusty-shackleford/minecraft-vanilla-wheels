/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.vanillawheels.garage;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Shutter;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.*;

/** Event-driven, bounded discovery of connected panels. AF: a scan is the loaded
 * coplanar component. RI: at most 257 visited panels; unloaded neighbors suspend
 * rebuilding rather than splitting a door or forcing chunks. No world scan in idle ticks. */
public final class GarageDoorAssembly {
    private GarageDoorAssembly() {}
    private record Scan(List<BlockPos> panels, boolean loaded, boolean overflow) {}

    /** requires: horizontal axis; effects: schedules local topology checks; throws: none. */
    public static void schedule(Level level, BlockPos pos, Direction.Axis axis) {
        var block = ModContent.GARAGE_DOOR.get();
        level.scheduleTick(pos, block, 1);
        level.scheduleTick(pos.above(), block, 1);
        level.scheduleTick(pos.below(), block, 1);
        var across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        level.scheduleTick(pos.relative(across), block, 1);
        level.scheduleTick(pos.relative(across.getOpposite()), block, 1);
    }

    private static Scan scan(Level level, BlockPos start, Direction.Axis axis, boolean adding) {
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var panels = new ArrayList<BlockPos>();
        var across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        var directions = List.of(Direction.UP, Direction.DOWN, across, across.getOpposite());
        queue.add(start); boolean loaded = true;
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            if (!level.hasChunkAt(pos)) { loaded = false; continue; }
            var state = level.getBlockState(pos);
            if (!(adding && pos.equals(start))
                    && (!state.is(ModContent.GARAGE_DOOR.get()) || state.getValue(GarageDoorBlock.AXIS) != axis)) continue;
            panels.add(pos.immutable());
            if (panels.size() > Shutter.MAX_PANELS) return new Scan(List.copyOf(panels), loaded, true);
            for (var direction : directions) queue.add(pos.relative(direction));
        }
        return new Scan(List.copyOf(panels), loaded, false);
    }

    /** requires: horizontal axis; effects: validates new panel against bounded loaded
     * neighbors; incomplete rectangles are allowed while building; throws: none. */
    public static boolean canAdd(Level level, BlockPos pos, Direction.Axis axis) {
        var scan = scan(level, pos, axis, true);
        if (!scan.loaded || scan.overflow) return false;
        var min = pos; var max = pos;
        for (var p : scan.panels) {
            min = new BlockPos(Math.min(min.getX(),p.getX()),Math.min(min.getY(),p.getY()),Math.min(min.getZ(),p.getZ()));
            max = new BlockPos(Math.max(max.getX(),p.getX()),Math.max(max.getY(),p.getY()),Math.max(max.getZ(),p.getZ()));
        }
        return max.getX()-min.getX() < Shutter.MAX_SIZE && max.getZ()-min.getZ() < Shutter.MAX_SIZE
                && max.getY()-min.getY() < Shutter.MAX_SIZE;
    }

    /** requires: loaded start; effects: elects the bottom/minimum-across panel as
     * controller of a filled rectangle, retaining existing clearance; throws: none. */
    public static void rebuild(ServerLevel level, BlockPos start) {
        var state = level.getBlockState(start);
        if (!state.is(ModContent.GARAGE_DOOR.get())) return;
        var axis = state.getValue(GarageDoorBlock.AXIS);
        var scan = scan(level, start, axis, false);
        if (!scan.loaded) return;
        var rectangle = scan.overflow ? Optional.<Shutter.Rectangle>empty() : Shutter.rectangle(scan.panels.stream()
                .map(p -> new Shutter.Cell(axis == Direction.Axis.X ? p.getX() : p.getZ(), p.getY())).toList());
        var parts = new ArrayList<GarageDoorBlockEntity>();
        int bottom = scan.panels.stream().mapToInt(BlockPos::getY).min().orElse(start.getY());
        int raisedEdge = bottom * Shutter.UNITS;
        for (var p : scan.panels) if (level.getBlockEntity(p) instanceof GarageDoorBlockEntity panel) {
            parts.add(panel);
            if (panel.lift() > 0) {
                raisedEdge = Math.max(raisedEdge, panel.origin().getY() * Shutter.UNITS + panel.lift());
            }
        }
        if (rectangle.isEmpty()) {
            // A partly dismantled open door remains passable until its rectangle is repaired.
            for (var part : parts) part.disassemble();
            return;
        }
        var r = rectangle.get();
        var origin = axis == Direction.Axis.X ? new BlockPos(r.left(),r.bottom(),start.getZ())
                : new BlockPos(start.getX(),r.bottom(),r.left());
        int lift = Math.min(r.fullyRaised(), Math.max(0, raisedEdge-r.bottom()*Shutter.UNITS));
        var immutable = List.copyOf(parts);
        for (var part : parts) part.configure(origin, r.width(), r.height(), true, lift,
                part.getBlockPos().equals(origin) ? immutable : List.of());
        if (level.getBlockEntity(origin) instanceof GarageDoorBlockEntity root) root.readPower();
    }
}
