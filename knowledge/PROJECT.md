---
title: Vanilla Wheels — project
type: overview
layer: store
tags: [overview]
---

# Vanilla Wheels

## What this is

A NeoForge 1.21.1 vehicle protocol, built the way the ranged-weapons protocol was: one
mod owns the mechanics, and a vehicle mod contributes a datapack entry, an OBJ mesh and a
texture. The first vehicles, the Trailblazer pickup and the Trailer, live in their own
data-only repos. Nothing here depends on Automobility, which the pack may drop.

## Shape

`domain` (JDK-only): `Drive` -- an arcade bicycle model stepped once a tick under an
`Input` and a `Tuning`, returning the next state and the effects to emit (skid, boost,
stalled); `Suspension` -- the body's smoothed lift, pitch and roll that hide the step-up
snap; `Impact`, `Tank`; and the mesh library (`Obj.parse`, `Mesh` with Newell normals
oriented outward per convex piece and parts by `Selector`, `Transform`/`Rotation`/`Dial`/
`WheelSpin`/`BodyPose`, `BakedMesh`). `main`: `api/VehicleProfile` (the contract, a flat
JSON split into `Look`/`Kit` map codecs to stay under the sixteen-field limit, its RI in
the compact constructor), the single `Vehicle` entity (vanilla's `VehicleEntity`, NeoForge
`PartEntity` hit boxes, `ContainerEntity` chest, `HasCustomInventoryScreen`), the items,
payloads, config, and the client (renderer, mesh library with placeholder on a bad OBJ,
keys with a riding-only conflict context, engine loop, radio, Luminance headlamps).
`gametest`: the box car, ten gametests, the photo booth.

## How it is verified

`./gradlew check`: 35 JUnit tests on the pure layer (the Trailblazer bundle's OBJs are
fixtures); ten gametests on a headless server driving a scripted box car; the photo booth
on a real client (paint, the dash from the driver's seat, the lamps at night with the beam
through Luminance), read off the frame.

## Decisions

D-0001 the contract: one entity type, data-only vehicles, flat JSON, mirrored once at
load. D-0002 authority and climbing: the driver's client drives, the server re-runs the
same step, `maxUpStep` climbs and the suspension hides it; rolling resistance stops a
coasting car. D-0003 the renderer: OBJ through our own parser, normals from geometry not
winding, paint as vertex colour.

## Next

1.0.0 (2026-09-09): the core. Then the Mechanic Lift (parts, recipes, the multi-block,
paint on the lift), towing and animals (`cargo`, `doors`, `hitch` are already decoded),
and the Trailblazer and Trailer repos with their art pipeline (the cab repair).
