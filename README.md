# Vanilla Wheels

A vehicle protocol for NeoForge 1.21.1. A vehicle is a datapack entry, a mesh and a
texture; this mod owns every line of Java. It drives, climbs, carries riders and cargo,
burns fuel, shows its gauges on a physical dash, lights the road, honks, plays records,
runs things over, and comes apart into parts and back again. A vehicle mod ships data and
assets and nothing else -- and still has gametests, because the gametest source set is a
mod of its own.

Sister mods: [Luminance](https://github.com/the-rusty-shackleford/minecraft-luminance)
(nested; the headlamps light the world through it) and
[Metals and Materials](https://github.com/the-rusty-shackleford/minecraft-metals-and-materials)
(steel, for the recipes).

## Driving

Right-click a vehicle to board the nearest free seat, the driver's first. Movement keys
drive and steer; **jump** held while turning above a third of top speed drifts -- the
tail comes out, the wheels get more lock, a charge builds, and releasing pays a boost
proportional to it. **Left Control** is the horn while held; **H** cycles the headlights
off, on, auto (auto lights below a configurable darkness). Both keys are live only while
riding one of these vehicles, so Left Control stays sprint everywhere else. Crouch to
dismount, as from a boat.

A vehicle climbs any ledge up to its profile's `climb` (two blocks for a pickup) without
a jump: the collision box steps up the way the game steps a player up a slab, and the
suspension eases the body and the riders up over a few ticks so nothing snaps. Off the
throttle, drag and rolling resistance bring it to rest within a few seconds; it never
rolls backward on its own.

The driver's client drives (the boat rule), the server re-runs the same move and resets a
client that disagrees by more than a quarter block, and every other client is told the
speed, steer and drift for its wheels and engine sound.

## Fuel, storage, records, the wrench

- **Fuel**: right-click with anything a furnace burns (coal, planks, a lava bucket) and it
  goes in the tank, whole, if it fits; the action bar shows the level. The engine burns
  one tick of fuel per tick of throttle; idling and coasting burn nothing. An empty tank
  refuses the throttle. `fuelRequired = false` in the config turns all of this off.
- **Chest**: crouch and right-click the chest region (a pickup's bed) for the vehicle's
  chest; a rider presses the inventory key instead, since crouching dismounts. Contents
  ride with the vehicle and spill when it is wrenched or destroyed.
- **Records**: crouch and right-click a vehicle that has a radio while holding a music
  disc, and it plays for everyone in range the way a jukebox does, with the now-playing
  toast; crouch and right-click the radio empty-handed to eject it. The disc stays until
  ejected, as in a jukebox.
- **Wrench**: crouch and right-click with the wrench to take the vehicle back into the
  hand with its paint, fuel and disc; the chest spills. Right-click the ground with the
  item to place it facing you.

## Running things over

Above a walking pace (0.15 blocks a tick) a vehicle hurts what it hits with the
`vanillawheels:run_over` damage type -- `mass × 8 × (speed / top speed)²`, so a pickup at
full speed does eleven and a half -- and knocks it down the road by `0.4 + 2.2 × speed /
top speed`. The vehicle loses speed to what it hit, more to a cow than a chicken; a victim
is not hit again for half a second. `runOver` and `damageScale` in the config govern it.

## Headlights and the dash

The lamp faces draw fullbright when lit. With Luminance present (it is nested, so always
for the pack; a server-only install and a client without it both work) each lamp casts a
beam ten blocks down the road, a line light the terrain and entities take. The dash is
part of the mesh: a speed needle and a fuel needle rotate about their pivots by the
speed fraction and the tank fraction, seen from the driver's seat in first person.

## The contract

`data/<ns>/vanillawheels/vehicle/<name>.json`, decoded into the synced datapack registry
`vanillawheels:vehicle`; every bound is checked at load and a bad file is refused naming
its field. Vectors are in mesh units (`scale` turns them into blocks; a pixel mesh uses
`0.0625`). `handedness: left` says the mesh has +X to the vehicle's right with +Z forward,
and it is mirrored once at load, vectors and angles with it.

```jsonc
{
  "mesh": "trailblazer:trailblazer",                  // assets/<ns>/vanillawheels/mesh/<name>.obj
  "wheel_mesh": "trailblazer:trailblazer_wheel",
  "texture": "trailblazer:textures/entity/trailblazer.png",
  "scale": 0.0625, "handedness": "left",
  "body": {"width": 2.75, "length": 5.4, "height": 1.9,
           "parts": [{"at": [0, 3, 21], "width": 2.75, "height": 1.55}]},   // hit boxes
  "seats": [{"at": [-8, 10, 10], "driver": true}, {"at": [8, 10, 10]}],
  "wheels": {"radius": 12, "positions": [{"forward": 24, "right": -16, "steers": true}, {"forward": -23, "right": 16}]},   // "up" defaults to the radius: a wheel on the ground
  "engine": {"max_speed": 0.9, "acceleration": 0.02, "reverse_speed": 0.3, "brake": 0.05, "drag": 0.01},
  "handling": {"grip": 0.85, "steer_degrees": 32, "drift_grip": 0.4, "drift_boost": 0.3, "drift_charge_ticks": 40},
  "climb": 2.0, "mass": 1.45,
  "fuel": {"capacity": 24000},                        // burn ticks, as the furnace counts them
  "storage": {"rows": 6, "region": {"z_max": -12.5}}, // the chest and where to click for it
  "gauges": [{"kind": "speed", "part": {"material": "needle", "x_max": -5}, "pivot": [-8, 17, 21.9], "axis": [0, 0, 1], "zero": 0.3, "sweep": 4.7}],
  "headlights": {"at": [[-13, 15.5, 40.5], [13, 15.5, 40.5]], "part": {"material": "gauge"}, "range": 10},
  "horn": "vanillawheels:horn.truck",
  "radio": {"at": [0, 17, 21]},
  "hitch": {"rear": [0, 4, -34]},                     // a trailer: {"front": [0, 6, 42]}
  "cargo": {"adults": 4, "young": 8, "slots": [[-8, 3, 5], [8, 3, 5], [-8, 3, -12], [8, 3, -12]]},   // a trailer
  "doors": [{"part": {"group": "left_door"}, "hinge": [-15, 20, -40], "axis": [0, 1, 0], "open": -1.9}],   // radians
  "paint": {"part": {"material": ["body_blue", "body_blue_dark"]}, "default": "light_blue"},
  "glass": {"material": "glass"},
  "sounds": {"engine": "vanillawheels:engine.petrol"}
}
```

A trailer is a profile with no `engine`, no `seats`, and a `hitch.front`. Parts are
selected by material name, group name, or both, narrowed by an axis range, because one
generator's OBJ has no groups and another's has them. The mesh is Wavefront OBJ with
texture coordinates (normals are computed, n-gons fanned); face winding may be
inconsistent, since normals are oriented away from each convex piece's centre. Paint
parts are drawn with the dye as the vertex colour, so grey swatches take the colour.
`assets/<ns>/lang/en_us.json` names the vehicle under `vehicle.<ns>.<name>`. Keep every
texture coordinate inside its swatch: the body is drawn through the cutout shader, and a
coordinate on a swatch's edge samples the neighbour or the atlas's empty padding, whose
alpha is zero, which drops the whole face -- a generator's polygon caps, which carry one
coordinate on a corner for every vertex, vanish that way.

## Towing and animals

A trailer is a profile with no engine, no seats and a `hitch.front`, its tongue. Back a
vehicle's rear hitch (`hitch.rear`) to within half a block of a loose trailer's tongue
while moving and it catches with a clunk; crouch and right-click the tongue to let go, and
the trailer rolls to a stop on its own. A hitched trailer goes where its tower goes: its
axle is dragged along the line to the hitch, so it tracks a turn the way a real trailer
does and never folds past a right angle, and it climbs and drops with the ground like
anything else. The driver's client moves it with the car and the server moves its own
copy from its own copy of the car; nothing about the trailer is ever taken from a client.
A trailer that the world holds back until its tongue is a block from the hitch is let go.
Trailers can tow trailers. The link is saved by UUID and comes back after a reload.

A trailer with `cargo` carries animals: crouch and right-click a door to open it, then
right-click the trailer holding a lead and every animal on your leads within ten blocks
boards while there is room -- an adult takes a whole share, a young one a half, so room
for four adults is room for eight calves or two cows and four calves -- and each lead
comes back to you. Crouch and right-click an open door with animals aboard to let them
out behind; empty and open, the same click shuts it. Animals never board through shut
doors. `doors` swing about their hinges when open.

## Parts, recipes, the Mechanic Lift

A vehicle is built from a **chassis** (the vehicle mod's own recipe; the item names the
vehicle), one **wheel** per wheel position, and an **engine** if the profile has one -- no
more, no less. Wheels are eight leather around a steel ingot; an engine is two redstone, a
redstone torch, a diamond and five steel; the wrench two steel and two iron; the lift two
pistons, three iron blocks, a redstone block and three smooth stone. Steel is Metals and
Materials' (nested, so always present).

The **Mechanic Lift** is one item that places a whole lift: a deck five blocks wide and
seven long with a two-block post on each corner, forty-three blocks laid at once facing
you, refused with a message unless every cell is clear and every deck cell has solid
ground under it. Break any block of it and the whole lift goes, dropping one lift item
(none in creative); replace a block with a command and the rest goes without a drop; a
vehicle standing on it is left where it is. Right-click any block of it for the menu: a
slot each for the chassis, the wheels, the engine and a dye. **Build** spawns the vehicle
on the deck facing the front, takes exactly its parts, and refuses when a job is running,
when the parts match no vehicle, or when the deck is not clear -- the button is greyed
with the server's verdict and the status line says which. **Paint** colours the vehicle
standing on the deck with the dye in the slot and takes one; a chassis in the slot is
never painted, since paint only ever finds an entity on the deck. Either job raises the
deck half a block for two seconds with the rams' sound. Closing the menu hands the parts
back.

`vanillawheels:vehicle` and `vanillawheels:chassis` carry the profile id in the
`vanillawheels:vehicle` component (paint, fuel and the disc in their own); one of each per
registered profile appears in the Tools tab, the lift in Functional Blocks.

## Configuration

`config/vanillawheels-common.toml`: `runOver`, `damageScale`, `fuelRequired`,
`autoLightsBelow`.

## Layout

`src/domain` (JDK-only, plain JUnit): `Drive` (the step: throttle, drag, rolling,
steering by the bicycle rule, grip, drift), `Suspension`, `Impact`, `Tank`, `Tow` (the
trailer's kinematics), `Cargo` (the animals' room), the lift's `Footprint`,
`LiftMotion`, `LiftStatus` and `Assembly`, and the mesh library (`Obj`,
`Mesh`, `Transform`, `Rotation`, `Dial`, `WheelSpin`, `BodyPose`, `BakedMesh`).
`src/main`: `api` (`VehicleProfile` and its codec, `VanillaWheels`), the `Vehicle` entity
and its parts, the items, `net/Payloads`, `WheelsConfig`, `lift` (the two blocks, the
block entity, the menu, the item), and `client` (`VehicleRenderer`, `MeshLibrary`,
`Appearance`, `Controls`, `Keys`, `EngineSound`, `Radio`, `Headlamps`, the item renderer,
`lift/LiftRenderer`, `lift/LiftScreen`). `src/gametest`: a box car of its own (mesh,
texture and profile generated by `devtools/art/build.py`) and a box trailer, nineteen
gametests and the photo booth -- a mod of its own, never shipped.

## Building and looking at it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test                  # the pure layer
./gradlew check                 # plus the gametests and the photo booth (needs a display; -PskipBooth, -PskipGameTests)
```

The gametests drive the box car on a runway: it reaches speed and coasts to a stop,
climbs a two-block step and settles level on top, hurts and shoves a cow at speed and
nothing at a walk, takes coal and refuses a coal block and a throttle with an empty tank,
keeps its chest across a wrench and a placement, takes and ejects a disc, cycles its lamps
and lights them by itself at night, and its profile round-trips through the codec; a
lift is placed through its item facing each way and refused over a blocked cell, a hole
and a cow, broken from any block for one drop, worked through its real menu by a mock
player (Build spawns the car facing the front and takes exactly its parts; Paint colours
the car on the deck and refuses one beside it), and keeps its job across a save; the car
catches the trailer's tongue, tows it straight and through a turn with the tongue on the
hitch, lets go on a click; a lead loads a cow and two calves through open doors and no
more, shut doors refuse, a door click unloads them behind; the tow link survives a save.
The booth photographs the stock car, a red one, the dash from the driver's seat, the lamps at
night from behind (the beam on the ground, through Luminance) and from the front (the
faces aglow), and the lift: placed, its menu, the deck up with the car just built on it,
and the car painted red, and the trailer hitched with its doors open and two cows
aboard; its `booth: PASS/FAIL` lines are the assertion. Headless: `Xephyr
:7 -screen 1280x720 -ac -br -noreset` on another display, then `DISPLAY=:7
__GLX_VENDOR_LIBRARY_NAME=mesa LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe ./gradlew
check`.

Sounds are cut from CC0 recordings by the art script; `devtools/art/sounds/SOURCES.md`
credits them.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
