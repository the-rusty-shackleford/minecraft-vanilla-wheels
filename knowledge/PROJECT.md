---
title: Vanilla Wheels — project
type: overview
layer: store
tags: [overview]
---

# Vanilla Wheels

## What this is

A NeoForge 1.21.1 vehicle protocol, built the way the ranged-weapons protocol was: one
mod owns the mechanics, and a vehicle mod contributes a datapack entry and a Blockbench
project (or an OBJ mesh and a texture). The first vehicles, the Trailblazer pickup and the Trailer, live in their own
data-only repos. Nothing here depends on Automobility, which the pack may drop.

## Shape

`domain` (JDK-only): `Drive` -- an arcade bicycle model stepped once a tick under an
`Input` and a `Tuning`, returning the next state and the effects to emit (skid, boost,
stalled); `Suspension` -- the body's smoothed lift, pitch and roll that hide the step-up
snap; `Impact`, `Tank`; `Tow` -- a trailer's axle dragged along the line to the hitch;
`Terrain` -- the drawn pose on the ground, a plane through the footprint on springs, and
the towed lever pose; `Cargo` -- adults and young against a trailer's room; `Paint` -- the dye lifted toward
white; and the mesh library (`Obj.parse`, `BbModel.parse` over its own `Json` reader,
`Mesh` with Newell normals oriented outward per convex piece and parts by `Selector`,
`Transform`/`Rotation`/`Dial`/`WheelSpin`/`BodyPose`, `BakedMesh`). `main`: `api/VehicleProfile` (the contract, a flat
JSON split into `Look`/`Kit` map codecs to stay under the sixteen-field limit, its RI in
the compact constructor), the single `Vehicle` entity (vanilla's `VehicleEntity`, NeoForge
`PartEntity` hit boxes, `ContainerEntity` chest, `HasCustomInventoryScreen`), the items,
payloads, config, the `lift` package (controller and part blocks with the index in the
state, one block entity, a menu whose verdicts ride data slots, the placing item), and
the client (renderer with the game's double chest and the rider's glass fade, mesh
library with placeholder on a bad file, keys with a riding-only conflict context, the
lights indicator by the hotbar, engine loop, radio, Luminance headlamps, the lift
renderer and screen).
`gametest`: the box car and box trailer, twenty-two gametests, the photo booth.

## How it is verified

`./gradlew check`: 69 JUnit tests on the pure layer (the Trailblazer bundle's OBJs are
fixtures); twenty-two gametests on a headless server driving a scripted box car, towing
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
list, not a recipe type; a 5 x 7 deck (5 x 6 since 1.3.0); the index in the part's state finds the controller.
D-0005 towing: the server tows its own copy from its own tower, no client payload; the
trailer's axle follows the hitch line; animals are passengers on cargo slots. D-0006
Blockbench: the `.bbmodel` is read as saved, the dye is lifted a quarter toward white,
the glass clears for the rider. D-0007 the playtest: only vehicles are walls, the
server's re-run starts on the ground, the boost is a timed surge, riders lean and turn.

## Next

1.0.0 (2026-09-09): the core. 1.1.0 (2026-09-09): the Mechanic Lift and the recipes.
1.2.0 (2026-09-09): towing, doors, animals. 1.2.1: a trailer is caught every tick, not
every fifth, so a hitch that starts within reach of a tongue cannot slip past the check.
1.3.0 (2026-09-09), from Rusty's look at the shipped Trailblazer: the body cants on a
climb (ground probed under each wheel), the drift slides the kart way instead of
snapping, the chest is the game's double chest, a lights indicator by the hotbar, the
`.bbmodel` reader, the lifted paint, the rider's glass fade, and the lift cut to five by
six with one-block posts. 1.3.1 (2026-09-09): Blockbench cube faces wound so their
normals point out -- the truck had been lit inside out. 1.4.0 (2026-09-10, pack 1.30.0): a
profile's `factory` paint colour, an exact RGB an undyed vehicle wears, because the lifted
light-blue dye cannot reach the reference's blue; the `.bbmodel` reader takes a Blockbench 5
project's folder names from its `groups` list. The Trailblazer is now a hand-built Blockbench
project (its D-0003); the protocol's job is to read it as saved. 1.5.0 (2026-09-10), from
a scripted playtest beside an Automobility motorcar and the box's own log (D-0007): only
other vehicles are walls, the server stands its copy on the ground before re-running a
move (837 resets a day on the box, gone), boarding drives on from the vehicle's heading,
the drift's boost is a two-second surge the throttle cannot cancel with an afterburner
every client sees, the wheels re-centre on release, riders lean and turn with the body,
the camera stands back by the vehicle's length, the tyres loop, riders take no wall
damage, and a profile's chest has a `scale`. Next: the tuning session on speed, drift and
damage, watched in the booth. Then, 2026-09-13 (D-0008): nfx's handoff of the 11th --
two days of driving-feel work as bytecode patches over 1.4.0, plus his V4 truck, Trailer 2
and a Farmer's Pickup -- ported into source: `domain/Terrain` (his plane-fit pose and the
towed lever pose, under JUnit), turn-in-place, the tow tick order on a pass clock, lights
up the chain, flat catch distances, trailer placed by clicking a car with the item, the
door click only toggling and a lead unloading, solid trailer bodies, marker lamps for
unpowered vehicles; plus ceilings never read as ground and the lift raising its vehicle.
The seat's `eye`, `cockpit` and `rider_scale` fields stay in the contract, unused by the
Trailblazer (Rusty chose nfx's full-size truck and level camera for the view).

Later that day the open items from his handoff were closed: footprint collision (the
nose and tail stop at a wall the square box never reaches, and a wall stalls the drive),
terrain samples as deep as the fit's window, dyeable door panels; the first-person flip
did not reproduce in the playtest's first-person run. Not done, on purpose: syncing the
pose (the server keeps a level seat; every client runs the same pose for what it sees).