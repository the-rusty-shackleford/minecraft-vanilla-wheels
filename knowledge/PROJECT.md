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
snap; `Impact`, `Tank`; `Tow` -- a trailer's axle dragged along the line to the hitch;
`Cargo` -- adults and young against a trailer's room; and the mesh library (`Obj.parse`, `Mesh` with Newell normals
oriented outward per convex piece and parts by `Selector`, `Transform`/`Rotation`/`Dial`/
`WheelSpin`/`BodyPose`, `BakedMesh`). `main`: `api/VehicleProfile` (the contract, a flat
JSON split into `Look`/`Kit` map codecs to stay under the sixteen-field limit, its RI in
the compact constructor), the single `Vehicle` entity (vanilla's `VehicleEntity`, NeoForge
`PartEntity` hit boxes, `ContainerEntity` chest, `HasCustomInventoryScreen`), the items,
payloads, config, the `lift` package (controller and part blocks with the index in the
state, one block entity, a menu whose verdicts ride data slots, the placing item), and
the client (renderer, mesh library with placeholder on a bad OBJ, keys with a riding-only
conflict context, engine loop, radio, Luminance headlamps, the lift renderer and screen).
`gametest`: the box car and box trailer, nineteen gametests, the photo booth.

## How it is verified

`./gradlew check`: 52 JUnit tests on the pure layer (the Trailblazer bundle's OBJs are
fixtures); nineteen gametests on a headless server driving a scripted box car, towing
the box trailer, loading cows, and working a lift through a mock player; the photo booth
on a real client (paint, the dash from the driver's seat, the lamps at night with the
beam through Luminance, the lift placed, its menu, raised with the built car, painted,
the trailer hitched with cows aboard), read off the frame.

## Decisions

D-0001 the contract: one entity type, data-only vehicles, flat JSON, mirrored once at
load. D-0002 authority and climbing: the driver's client drives, the server re-runs the
same step, `maxUpStep` climbs and the suspension hides it; rolling resistance stops a
coasting car. D-0003 the renderer: OBJ through our own parser, normals from geometry not
winding, paint as vertex colour. D-0004 the lift: the assembly is the profile's own part
list, not a recipe type; a 5 x 7 deck; the index in the part's state finds the controller.
D-0005 towing: the server tows its own copy from its own tower, no client payload; the
trailer's axle follows the hitch line; animals are passengers on cargo slots.

## Next

1.0.0 (2026-09-09): the core. 1.1.0 (2026-09-09): the Mechanic Lift and the recipes.
1.2.0 (2026-09-09): towing, doors, animals. Then the Trailblazer and Trailer repos with
their art pipeline (the cab repair), and a tuning session with Rusty.
