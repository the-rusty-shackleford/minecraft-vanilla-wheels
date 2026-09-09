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

import com.chunkworks.vanillawheels.domain.Mesh;
import com.chunkworks.vanillawheels.domain.Obj;
import com.chunkworks.vanillawheels.domain.ObjFormatException;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every mesh in every resource pack, parsed once per reload (F3+T
 * included): {@code assets/<ns>/vanillawheels/mesh/<name>.obj} is the
 * mesh {@code <ns>:<name>}. A file that is not a mesh is logged with its
 * pack and line and stands in as a placeholder box, so a broken vehicle
 * mod shows a box where the vehicle is, not nothing. Parsing is pure and
 * runs on the reload's worker; the appearances built from meshes are
 * cached per profile in {@link Appearance}.
 */
public final class MeshLibrary extends SimplePreparableReloadListener<Map<ResourceLocation, Mesh>> {
    private static final Logger LOG = LoggerFactory.getLogger("Vanilla Wheels");
    public static final MeshLibrary INSTANCE = new MeshLibrary();
    private static final String PREFIX = "vanillawheels/mesh/";
    private static final String SUFFIX = ".obj";

    private volatile Map<ResourceLocation, Mesh> meshes = Map.of();
    private final Mesh placeholder = Mesh.placeholder(1.0);

    private MeshLibrary() {}

    /** effects: returns the mesh {@code id} names, or the placeholder box (logged once) */
    public Mesh get(ResourceLocation id) {
        Mesh m = meshes.get(id);
        if (m == null) {
            LOG.warn("vanillawheels: no mesh {}; drawing a box", id);
            return placeholder;
        }
        return m;
    }

    /** effects: returns whether {@code id} is a mesh the packs carry */
    public boolean has(ResourceLocation id) {
        return meshes.containsKey(id);
    }

    @Override
    protected Map<ResourceLocation, Mesh> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Mesh> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(PREFIX.substring(0, PREFIX.length() - 1), id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String name = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
            try (Reader reader = e.getValue().openAsReader()) {
                String text = readAll(reader);
                Obj.Parsed parsed = Obj.parse(text);
                for (String warning : parsed.warnings()) {
                    LOG.warn("vanillawheels: mesh {} ({}): {}", id, e.getValue().sourcePackId(), warning);
                }
                out.put(id, parsed.mesh());
            } catch (ObjFormatException ex) {
                LOG.error("vanillawheels: mesh {} ({}) is not a mesh: {}; drawing a box", id, e.getValue().sourcePackId(), ex.getMessage());
                out.put(id, placeholder);
            } catch (IOException | RuntimeException ex) {
                LOG.error("vanillawheels: cannot read mesh {} ({}): {}", id, e.getValue().sourcePackId(), ex.toString());
                out.put(id, placeholder);
            }
        }
        return Map.copyOf(out);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    @Override
    protected void apply(Map<ResourceLocation, Mesh> prepared, ResourceManager manager, ProfilerFiller profiler) {
        meshes = prepared;
        Appearance.invalidate();
        LOG.info("vanillawheels: {} meshes loaded", prepared.size());
    }
}
