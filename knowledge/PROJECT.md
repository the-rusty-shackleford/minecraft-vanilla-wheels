---
title: Vanilla Wheels — project
type: overview
layer: store
tags: [overview]
---

# Vanilla Wheels

## 1.13.0 — built 2026-10-07, unreleased (the submarines; D-0031)

nfx's brief for two submarines; Rusty: "it's probably an extension of vanilla wheels. It's a new
vehicle." The plan is `~/.claude/plans/peppy-scribbling-lollipop.md`: a submarine protocol layered
here (D-0030), as Rotorcraft is. Rusty chose (2026-10-07) that what both need move here from
Rotorcraft rather than be copied. That is D-0031:
- the hull (`domain/Hull`, `hullPoints`, `hullClamp`, and `hullClampAxes` for a hull that rests on a floor);
- crash judging (`domain/Crash`, `crashes`, `ownChange`, `crashed`);
- the vertical controls: Up, Down and Get out, with the Shift mixin (`verticalControls`, `getOut`,
  `Keys.RidingKey`);
- plus a fuel rate (`fuelRate`, `domain/FuelDebt`) and `VehicleItem.place`;
- and, for the submarines' ports and bubble, the cockpit's glass: glass the `cockpit` names too is
  not drawn from a rider's own eyes (`Parts.cockpitGlass`).

D-0032 rides in it: a key that acts once (the lights, getting out; Rotorcraft's hook and sprayer)
acts on its press, not on the keyboard's repeats (`Keys.Press`). Held past the repeat delay, H
had cycled the lights through every mode since 1.0.0; that was the booth's "H with Left Control
held cycles the lights" flake (two runs in four), now a check that it cycles once, which the old
code fails.

D-0033 rides in it too, from Rusty's submarine playtest: aboard anything with vertical controls,
the boarding line names the get-out key ("Press R to Dismount"), not Shift; the helicopters had
said Shift since Rotorcraft 1.0.0.

Network 7.

- Gate (2026-10-07): 147 JUnit, 103 gametests (`ThreeDimensionGameTests` new, six, each beside a
  control and run against its mutation), the booth's 55 lines (54 checks and its last) green twice
  running. The repair check, which once read the car unrepaired four ticks after the clicks, is
  now judged on the server as the clicks land and on the client within twelve ticks, so a repeat
  says which side was behind.
- Rotorcraft 1.1.0 moved onto it with no change in behaviour (its gate green, the real-key booth
  included); the Huey's and the Chinook's gametests pass on it.
- Ships with the submarines, on Rusty's go. Not released.

## 1.12.0 — released 2026-10-07 in pack 1.75.0 (Rotorcraft's helicopters; layering, D-0030; the horn)

Released with Rotorcraft 1.0.0, the Huey and the Chinook on Rusty's "looks good, fix the latent key
bug then release": sha1 `823f41da` on GitHub and on the server, protocol 6 unchanged (the server
repo's `knowledge/releases/pack-1.75.0.md`). Not yet seen in play: the horn.

Rusty asked for a Huey and a Chinook (2026-10-06), and for Rotorcraft to be a protocol of its own
rather than flight inside this one. The plan is `~/.claude/plans/i-want-to-add-curious-locket.md`.
This version is what Rotorcraft builds on, and changes nothing for a car, a trailer or the network
(protocol still 6):
- `api/VehicleKinds`: a protocol claims profiles for its own entity types, whose classes extend
  `Vehicle`, and `Vehicle.create` asks it first.
- Overridable hooks in `Vehicle`, each defaulting to what it always did.
- `VehicleRenderer`'s `appearance` and `drawExtras` hooks; `Appearance.of(profile, extras)`.
- `wheels.drawn: false` for skids and feet (the lift asks no wheels).
- Sounds a data mod's own `sounds.json` defines, and `sounds.pitch` / `sounds.volume` spans for the
  engine loop (a rotor's note barely climbs; absent, a car's 0.75 to 1.6).
- `drawExtras` gets the body's buffer asked for again after wheels of their own texture: the
  Chinook's booth crashed every frame without it ("Not building!").
- A profile's `camera`: how far behind a rider's eye the third-person camera stands (absent, 1.5
  and 1.5 a block of length, the number a car always had). The length rule put it 20 behind a
  Huey's pilot, the helicopter a speck; the Huey names 16, the Chinook 22.

- **The horn sounds** (found through Rotorcraft's playtest, 2026-10-07, where Left Shift never
  descended). NeoForge judges a key bound with no modifier as up while Shift, Control or Alt is
  held, in every context but the game's own; the driver's keys have their own, and the horn is
  Left Control, so it never sounded from 1.0.0 to 1.11.0, and H under a held Control did nothing.
  The driver's keys now judge their modifier as the game's keys do (`Keys.DrivingKey`) and are let
  go whenever the player is not driving. That exposed a second fault: the horn sounded on after its
  driver got out with it held (the client stops reporting, the server hears the key only from a
  driver), so a driver getting out now stops it (`Vehicle.removePassenger`; GameTest
  `theHornStopsWhenItsDriverGetsOut`). The booth presses real keys through XTEST
  (`devtools/booth/xkey.py`): on 1.11.0's keys it failed the horn and H under Control; without the
  let-go, the horn held into the next drive; without the server's stop, the GameTest failed. In
  one booth run of four, "three clicks repair the car" read 6500 (no repair); not reproduced since,
  its failure line now names the player's food, mode, crouch and vehicle.

Gate (2026-10-07, after the keys): 126 JUnit, 97 GameTests and the booth (53 checks) green. Before
them: 126 JUnit, 96 GameTests and the booth (47 checks) green; the Huey's and the
Chinook's booths draw the layered vehicles. The three new gametests use the tests' own kind,
`SkidVehicle`. Each was run against its mutation and caught it. The gametest server now loads
Backpacks+ 0.7.1, the pack's; it had pinned 0.7.0, which the sibling build no longer has, and two
bag tests failed for it. The README no longer claims crashes wear a vehicle: none ever did. The
booth's run predates the `camera` commit (496ec4a); a car's distance is the same expression as
before, and the Huey's and the Chinook's booths film the named one.

## 1.11.0 — released 2026-10-06 in pack 1.73.0 (Serfdom's captives in the trailer)

Released with Serfdom 0.8.0 and Village Law 1.1.0 on Rusty's go ("release all three"), from the
2026-10-04 gate's jar: sha1 `fbc86b18` on GitHub and on the server, protocol 6 unchanged (the
server repo's `knowledge/releases/pack-1.73.0.md`). Not yet seen in play: a captive loaded on an
empty hand.

For Serfdom's phase 2a (its D-0003): **other mods' cargo** (D-0029). `api/CargoRules` is a
registry of what rides in a vehicle's cargo; animals on a lead are its first rule and behave as
before. A rule says what rides as cargo (both sides), what a player may load now, which item leads
it, and what becomes of its tether as it boards. Every check that asked `instanceof Animal` asks
the registry (`cargoAboard()`; `animals()` kept for Trailer's tests). An **empty-handed click**
now loads whatever the player leads that a rule admits, cows included, since a captive's chain is
on the captive; with nothing led it is no load and says nothing. The message for nothing led
reads "Nothing on your leads within ten blocks". Two GameTests, with the test mod's own rule (a
villager tagged as cargo, led by a chain): another mod's cargo boards beside a cow on one empty
hand, takes an adult's room, keeps no lead back, the full trailer says so, a crouch with its own
lead item lets it out; and an empty hand with nothing led is no load while a led cow still gets
its lead back. Gate (`./gradlew clean build`, 2026-10-04): 124 JUnit, 91 GameTests, the booth's
47 checks, jar sha1 `fbc86b18`. Each new rule was run against a mutation and caught: no
empty-hand load, only a lead unloading, only animals riding as cargo.

## 1.10.1 — released 2026-09-30 in pack 1.70.0 (Rusty, 2026-09-29: "I want that second garage door wall bug fixed")

Released on Rusty's "release this and the other shit from a previous session that isnt released
yet", with Warehouse Manager 0.8.0 as pack 1.70.0: 124 JUnit, 89 GameTests and the booth (47
checks) green in the release gate, sha1 `5c9a4ea3` on the server (the server repo's
`knowledge/releases/pack-1.70.0.md`). The protocol is still 6; a 1.10.0 client keeps the old
footprint and pose for its own vehicle until it updates.

- **A door's top row is no wall** (D-0028, superseding D-0027's "Not fixed"). The footprint read a
  block as the bounds of its boxes; a door's housing and side track bound its whole top row. Now
  a block is a wall where its cross-section at the climb line holds the point
  (`domain/CrossSection`); ground is still the bounds. Fences, walls, slabs and full blocks are
  unchanged by construction, and `FootprintGameTests` (namespace `vanillawheels_footprint`, 9
  tests, the Trailblazer-bodied truck at speed and creeping) logs the same stops as 1.10.0 to four
  decimals. Each test was run against the rule it guards: per-box and per-column walls, no
  footprint, a lintel counted, the climb ignored. The per-column rule stops a creeping truck at
  a flight whose top step is the landing, which is the stair hazard D-0027 foresaw. The box car
  goes through a 3x3 door. `ShapeBoxes` makes the check allocation-free.
- **The body stays on its wheels in a doorway** (found while checking `columns()` for the brief).
  `Terrain.fit` took each walk's first sample unchecked, and `columns()` reads a door's edge cell
  whole, so a side track under a wheel was ground three blocks up. In pack 1.69.0 a Trailblazer in
  a 3- or 4-wide open door rises 1.24 to 1.45 blocks and pitches up to 7 degrees. Walks now start
  from the body's own ground. `TerrainTest` has the doorway, and the drive-through gametest
  asserts on its wheels and level. It also levels a body whose wheels overhang a wall it runs
  along: `TerrainTest.alongAWallItsWheelOverhangsTheBodyStaysLevelButACurbStillTiltsIt` (added
  2026-09-30 before the release) has the Trailblazer's shape beside a wall three high, its left
  wheel track in the wall's column, and rolls 35 degrees toward the wall on 1.10.0's `Terrain`,
  level on 1.10.1's; a curb one high under the same wheel still tilts it. Pinned in the pose model,
  not looked at in a render.
- Not run: the Trailblazer's `TrailblazerPlaytest`. It needs a display and resolves Vanilla Wheels
  1.8.0 from mavenLocal.

## 1.10.0 — released 2026-09-30 in pack 1.69.0 (Rusty's notes of 2026-09-28)

Rusty's notes on the trailer, some met by Bobandy_. The plan is
`~/.claude/plans/some-changes-needed-to-zazzy-whale.md`. Released on Rusty's "After that, go to
release" once the garage fix below was verified: 115 JUnit, 80 GameTests and the booth in the
release gate; deployed with Backpacks+ 0.7.0 and Ranged Weapons Mod 2.10.0, sha1 `ab3ed58d` on the
server (the server repo's `knowledge/releases/pack-1.69.0.md`). Keys paired under 1.9.5 keep their
pairing and stay unmarked until clicked at their vehicle or used to recall it. Not yet seen in play.

- **Punch to pack, right-click to repair** (done, D-0025; 2026-09-29, Rusty: his friends hated
  the crowbar and the breakdown; "more like Immersive Aircraft"). Immersive Aircraft's 1.21.1
  source was read first (the decision records what it does). Six punches in a row pack a vehicle
  as it is (`domain/Knocks`); a click that would board repairs a damaged one 2.5% for hunger
  scaled by its profile's `repair.full_cost` (`domain/HandRepair`), then boards; a blow rocks it.
  A paired vehicle, or one hitched behind it, packs only for its key's owner or a creative
  player. The crowbar, its tag and recipe, and the own-crowbar toolbox are gone (never shipped);
  the 1.9.5 wrench id loads as an iron ingot. JUnit 9, gametests 5 (`PunchAndRepairGameTests`),
  the older suites punch where they pried. The D-0024 field patches (crouching crowbar, steel
  from a bag, a lowered ceiling) were built, then shelved unreleased in `git stash` when Rusty
  changed course.
- **One key per vehicle, marked, never lost** (done, D-0026). `RecoveryData` by binding, not by
  player; a key named for its vehicle and banded in its paint (`key_colour`, a tinted band on the
  fob's model); `KeyFobs` (from `OwnCrowbars`) sends a live key home to its owner from the ground,
  any container, a dropped bag, a stranger, a frame, a pot and a death, and holds it for a login
  or a respawn. Gametests 5 (`KeyFobGameTests`).

- **Vehicles drive through an open garage door** (done, D-0027; 2026-09-29, Rusty: his buddy
  "cannot drive his trailblazer through an open garage door"). The door had no `dynamicShape()`,
  so its cached per-state collision shape was the closed fallback panel. The vehicle's footprint
  and terrain pose read that cache, and every vehicle driven at a driver's pace stopped at the
  doorway (the Trailblazer-sized truck and the box car alike, nose at z 10.37). The old checks
  read the block entity's shape or jumped six blocks in one move. Gametest
  `aTruckDrivesThroughAnOpenDoorAtDrivingSpeed` (namespace `vanillawheels_garage_drive`,
  `box_truck` = the Trailblazer's body) failed without the fix. The booth drives through in
  fifth-of-a-block steps now. A door's top row still counted as a wall to a vehicle whose climb
  reached it (door under climb + 2 high); fixed in 1.10.1 (D-0028) without changing the footprint
  at fences or stairs.
- **Door lamps drawn once** (done). The trailer's rear reflectors were drawn twice since 1.6.0:
  once swinging with the door, and once standing in the doorway where the shut door was. The
  Farmer's Pickup left a painted ghost tailgate the same way. `Appearance` cut the body's lamps,
  glass, cockpit and paint from the whole mesh, and a group selector matches any name on a face's
  path, so a door's nested `lenses`/`paint` were cut twice. `domain/Parts` now cuts each piece
  from what the pieces before it left. `PartsTest` runs over the three shipped models and their
  profiles (copies in `src/test/resources/fixtures`). It failed on the old cutting: 6 reflector
  faces drawn twice, 12 ghost tailgate faces. No other shipped element matched two roles, so
  nothing else changes. The trailer booth's doors-open frames show the doorway empty (before and
  after compared at 3×).
- **The wrench row, no red tint** (done, D-0021). `domain/WrenchRow` (JUnit: fills, the jiggle,
  and a blink pattern identical to the game's hearts) and `client/WrenchBar` above the hunger bar.
  The booth's `wrenchPlan` photographs it, and its checks read the HUD's exact steel pixels. On
  foot looking at the trailer, a full row: the crosshair met a cow aboard first, so a rider's
  vehicle counts too. Hurt, it blinks with the lost part pale, and no red tint, the trailer's red
  count unchanged. Worn to a tenth, it jiggles. Driving, two rows. Under software rendering a
  screenshot can repeat the last frame drawn, so the first two hurt frames still show the row
  before the hit.
- **The crowbar; a player's blow does nothing** (done, D-0020; superseded before release by D-0025). The wrench is renamed and its
  id aliased. The `pries_vehicles` tag takes Automobility's crowbar too. Its icon is drawn by
  `build.py` and was judged in hand in the booth. The gametests: an uncrouched pry keeps the
  cargo, paint, fuel and wear; an old `vanillawheels:wrench` stack loads as a crowbar; six sword
  blows (survival and creative) leave a car unworn while a zombie's and a player's arrow wear it.
  With the rule removed, that test failed at 7498.
- **Unhitch roll-back** (done, D-0022). A trailer let go by hand rolls back ¾ block (`Tow.letGoSpeed`,
  from the drive's own coasting) and holds a release from that tower until the two have parted
  (`Tow.catches`/`stillReleased`, JUnit). Gametests: roll back, drive off alone, back on and
  re-hitch; a wall behind the trailer stops the roll and the release alone keeps it. Both failed
  with the old plain unhitch (the wall one exactly as Bobandy_ saw it: re-hitched and dragged).
- **Cow loading, step 1** (Bobandy_: a lead in hand, nothing happened). A gametest sends the
  client's two packets through the server's own `handleInteract` at the trailer's hit box, with
  NeoForge's events on the way. It passes, so the server route in Vanilla Wheels alone is clean.
  (Its first version sent them the tick the trailer spawned, before it had placed its hit boxes:
  the server's reach check dropped the click.)
- **Cow loading, step 2** (not reproduced). The box's 97 jars (sha1-checked) were loaded into this
  repo's gametest server, `run/mods`. Connector, Forgified Fabric API and Old Cannons cannot run
  in a dev environment; Distant Horizons casts the gametest server to a dedicated one; Immersive
  Aircraft and Man of Many Planes, then Moonlight, send payloads at join that a mock player's
  connection refuses. So every mock-player test fails at spawn, and the full pack cannot be
  exercised this way without teaching the mock connection NeoForge's channel negotiation. Read
  statically, nothing in the pack takes a lead click on a non-`Leashable` entity. Rusty's call
  (2026-09-29): stop digging and make the click say why when it does nothing. A lead click with
  no led animal within ten blocks now says so, like "Open the doors first" (gametest; it failed
  with the message removed). Shut doors, the likeliest cause, already had a message.
- **Every vehicle's own crowbar** (D-0023; superseded before release by D-0025, its guard reused for the keys). `OwnCrowbars` (the mark, the waiting record, the
  guard), `ToolboxMenu`/`ToolboxScreen` (a plain menu, the id in four data slots), `HeldStack` for
  the stack components (the radio's `disc` one failed the dev check once a disc was packed).
  Eight gametests (`OwnCrowbarGameTests`): birth and first load, the toolbox's slot rules and
  hold, toss and death, chest at once and ender chest at close, a carried bag keeps it, an item
  frame refused and a decorated pot made to give it up, packed inside by its own crowbar, and a
  crowbar waiting for its packed vehicle. With the guard's listeners removed, the four guard tests
  failed. Protocol "6".

## Carried — 1.9.5, released 2026-09-29 in pack 1.68.0

Rusty's one change for every mod that looks at a player's inventory: the Carried protocol.
[D-0019](decisions/D-0019.md): the disc, the leads and a stranded repair ingredient go where a give
goes, a carried bag included, before the ground. 93 JUnit, 63 GameTests with Backpacks+ 0.6.0 on
the gametest server and the booth, green in the release gate; the radio's new check failed on
1.9.4. Deployed with Carried and Backpacks+ 0.6.0, sha1 `31d417ab` on the server; Trailblazer,
Trailer and Farmer's Truck nest an older Vanilla Wheels, which the pack's own 1.9.5 outranks (the
usual JarJar warning in the log). Not yet seen in play.

## Riders and falls — 1.9.4

A friend was hurt driving the Trailblazer downhill. The cause was the vehicle's own fall,
which the game hands whole to its riders, not the rider's collision.
[D-0018](decisions/D-0018.md) lets the suspension take the first seven blocks, so a hill
at speed costs nothing and a cliff still hurts past ten blocks. Two gametests on a new
hillside template reproduced the damage on 1.9.3 and pass on 1.9.4. Rusty authorized the
release on 2026-09-28. It is published and deployed alone in pack **1.67.1**; see
[release verification](../devtools/verification/release-1.9.4.md). Open question for Rusty:
a one-in-one slope at full speed still hurts, because the car clears it like a cliff.

## Garage facing — 1.9.1

Version **1.9.1** is published and deployed in pack **1.54.1**. The installed
jar matches the public release, Mod Hub reports parity, and the server runs at
20 TPS with no new startup errors after the authorized warning and restart.

[D-0016](decisions/D-0016.md) adds four-direction garage door placement: outside
faces the first panel's placer, extensions inherit it, and legacy doors keep
their appearance. Full-rectangle construction and interaction behavior remain.
All 92 domain tests, 61 real-server GameTests and the complete RTX 4070 shader
booth passed. Verification is recorded in [garage facing](../devtools/verification/garage-facing.md).
Rusty authorized public release and deployment, with a full two-minute warning
and disconnection of remaining players before restart. See
[release verification](../devtools/verification/release-1.9.1.md).

The previous **1.9.0** release was deployed in pack **1.54.0**.
See [release and deployment verification](../devtools/verification/release-1.9.0.md).

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
`gametest`: the box car and box trailer, real-server gametests, the photo booth.

## How it is verified

`./gradlew check`: JUnit tests on the pure layer (the Trailblazer bundle's OBJs are
fixtures); real gametests on a headless server driving a scripted box car, towing
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
did not reproduce in the playtest's first-person run. And the pose is synced: the driver's
client shares the tilt and lift it computed, for its truck and its trailer, and the server
and every other client draw and seat with them. Storage is a list of chests, each the
game's double chest at its own place, scale and rows, opened by clicking it (Rusty: a
chest in the Trailblazer's bed, one along each side of the pickup's). Two gametests that
failed one run in ten were the world's random offset, not load: a lift test swept every
item within sixteen blocks of its controller, into the next runway, and took the
chest-spill test's apples when it had spilled first (the sweep now takes lift items in its
own bounds); and the lead loader took the herd in entity-section order, so two adults
sometimes filled the trailer before the calves (it loads nearest first now). The spill
test failed once more after that fix, and the second taker was found by hooking every
item's removal and pickup with a stack trace in the gametest mod (the hooks stay): a mock
player is made at the world's origin, and a riding player's pickup sweep is the box round
itself and its vehicle together, so another test's mock rider, still at the origin on its
first tick aboard, swept the millions of blocks between and took any spilled item on the
origin's side of its car -- which side the grid fell on was the random position. Mock
riders now stand at the car before boarding (`VehicleGameTests.riderAt`). After the 1.31.0 release (2026-09-13): the
third-person camera clips on the visual shape (grass no longer stutters it; a booth check
across a meadow), a chest opens along the click's line from outside (`RayBox`), the
footprint's wall rule walks the ground out to each point (hillsides of one-block risers
climb; two Trailblazer gametests), the clamp slides along a slanted wall and the stall is
proportional (`Drive.slowed`).

Creative needs nothing (Rusty, 2026-09-14, a rule for every mod): a driver with the game's
infinite materials drives on an empty tank and burns none (`Vehicle.fuelRequired`); the
Ranged Weapons Mod already shoots without rounds in creative and Dynamite's `consume`
spends nothing there, so the car was the one holdout.

2026-09-14, Rusty's four notes: fuel is the gas can (`GasCanItem`: an empty can of eight
iron, four coals fill it to a tank's worth, hold right-click at a vehicle to pour, the empty
can comes back; coal alone fuels nothing), a door's optional box (`Door.from/to`) so a
crouching click anywhere on the door toggles it (the trailer's, the Trailblazer's, the
pickup's tailgate carry one), a door's own lens part glowing with the tower's lights (the
trailer's rear reflectors ride on its doors), and the harnesses launch silent. Protocol
additions, so 1.6.0. And the road's texture (D-0008 addendum): a body within its climb of
the ground is grounded for the wheel and the step, and the server keeps the driver's
ground flag on a reported step; the rugged playtest lane runs stall-free at top speed.

2026-09-16 verification of the held 1.6.0 checkout: `build -PskipBooth` passed
77 JUnit and 27 real-server tests, including speed-scaled damage, fuel pouring,
creative fuel exemption, terrain and towing. The local Maven jar’s entries match the
current built jar exactly. Trailblazer’s D-0004 records the repaired real-client course:
forty boost ticks, no grounded steering pauses, no stuck reports or server move
rejections under Iris/Complementary. Production physics and tuning are unchanged;
subjective adjustments await review of the complete baseline. Release remains held.

2026-09-16 presentation polish: D-0010 adds empty-slot guidance, tooltips, bevelled engine/can icons, smoothed quieter
engine/skid output and a liquid fuel-pour cue. The shader booth now samples the actual
visible lamps/stripe and separates painted body from blue glass/deck pixels. The
preferred bundled steel is 1.0.1. Driving physics and release authorization are unchanged.


## Release approval - 2026-09-16

Rusty approved the final review, completing their earlier conditional release go.
Version 1.6.0 was published on 2026-09-16 and deployed in pack 1.35.1
after the clean release build and asset verification. The deployed server matched
the published pack and ran at 20 TPS. This supersedes the earlier release holds
and pending presentation/listening review recorded above.

## Held follow-up — 2026-09-16

D-0011 adds shared contacts and protected fragile destruction; the existing original
headlamp Line appearance now follows interpolated vehicle position/yaw through Luminance.
Production release is held. See README for current gates and remaining multiplayer work.

## Release authorization — 2026-09-17

Rusty approved the final vehicle cosmetics, then explicitly requested the release.
Version 1.7.0 is the coordinated release version, superseding the prior hold.
The release set is Luminance 1.1.0, Vanilla Wheels 1.7.0 (network protocol 4),
Trailblazer 1.7.0, Farmer's Pickup 1.3.0 and Trailer 2.3.0, targeting pack 1.36.0.
All peers must update together. Vehicle artwork changes leave the existing gameplay
profiles, recipes, seats and interaction anchors unchanged; the separately approved
collision and moving-light changes ship in the shared libraries.

Independent driver/observer multiplayer, the historical live movement-warning route,
and representative 4–8-player tracking/DH capacity remain open follow-ups. Local tests
do not establish those results. Release authorization does not claim those checks passed.

## Published release — 2026-09-17

[Version 1.7.0](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels/releases/tag/v1.7.0) is published and deployed in pack 1.36.0.
The coordinated set passed 96 JUnit tests, 59 real-server GameTests and all five
Iris/Complementary booths on clean release builds. Downloaded release assets match
the validated jars; nested dependencies are the exact newly built artifacts.
The three cosmetic vehicle profiles remain identical to their preserved references.

Both pack archives were verified against the source. Deployment occurred with zero
players online; installed server hashes match, and Mod Hub reports pack parity.
The initial empty-server sample was 20 TPS. Startup retained the same 36 pre-existing
third-party error messages, with none added. This does not close the multiplayer,
historical movement-warning or representative capacity follow-ups above.


## Dedicated Creative tabs — 2026-09-18, unreleased

Rusty requested a separate Creative inventory page for each item-adding mod, then
explicitly chose to group all vehicles in Vanilla Wheels.
D-0012 adds one Vanilla Wheels Creative tab for all installed vehicles, their chassis,
and shared tools and parts. Trailblazer, Trailer and Farmer's Pickup share that page;
vehicle packs remain data-only.
No release or deployment is authorized by this follow-up.
Validation: 36 real-server GameTests and native full-pack Creative tab navigation/
item pickup passed; see [evidence](../devtools/verification/creative-tab.md).

## Release authorization — 2026-09-18

Rusty explicitly requested deployment: "Deploy it! I wanna play with it".
Version 1.7.1 is approved for publication and deployment in pack 1.39.1,
superseding the Creative-tab release hold above. Existing gameplay and world data
are preserved. Clean release builds and pack/hash verification gate deployment.

## Published release — 2026-09-18

Version 1.7.1 is published at
[GitHub Releases](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels/releases/tag/v1.7.1)
and deployed to the server and Prism client in **pack 1.39.2**.
Clean release builds and real-server checks passed; the full-pack client verified
Creative tabs and item pickup. The downloaded release jar exactly matched the build.
The live server loaded the correct version and matched the published pack at 20 TPS.

Metals and Materials 1.0.2 is explicitly included in the pack: the first 1.39.1 startup
selected an older nested copy despite the updated Vanilla Wheels bundle. The 1.39.2
correction matches the directly installed materials jar used in full-pack testing.
Final startup verified all four updated mod versions; world, operators and DH settings
were preserved. This supersedes the historical release holds above.

## Shared materials dependency — 2026-09-18, unreleased

Version 1.7.2 implements [D-0013](decisions/D-0013.md): Metals and Materials
is required and installed separately, with no embedded copy. Items, recipes,
steel aliases and gameplay are unchanged. Unit/server checks, recursive jar/payload audits and complete-pack startup passed; release is held.

Validation: see Metals and Materials `devtools/verification/separate-dependency.md`;
all six packaging builds and the complete-pack client/server check passed.

## Vehicle recovery — 2026-09-18, local review

Version 1.8.0 implements [D-0014](decisions/D-0014.md): persistent condition,
cargo-preserving packed vehicles and wrecks, proportional lift repairs, and one
paired recovery fob per owner. Recall transfers the actual vehicle/drop and hitched
trailer, paying distance-based fuel with half-rate condition loss for shortfall.
The repair section appears only for damaged mounted vehicles. Protocol 5 requires
matching clients and server. The separate-materials dependency from D-0013 remains.

Publication and deployment are held pending Rusty’s review. See
[local verification](../devtools/verification/vehicle-recovery.md) for current tests
and limitations; historical multiplayer/capacity follow-ups above remain open.

## Release authorization — 2026-09-19

Rusty explicitly requested: "Deploy it all so I can test that stuff."
Version 1.8.0 is authorized for public source/jar publication and deployment
in pack 1.47.0, superseding the earlier local-review and dependency-packaging holds.
Clean release builds, exact jar checks, staged pack comparison and an empty-server
restart gate deployment. The other vehicle mods are updated together for protocol 5.

## Published and deployed — 2026-09-19

Rusty explicitly approved public publication for all five coordinated releases.
Version 1.8.0 is published on GitHub and deployed in pack 1.47.0.
Clean release checks passed: 88 shared domain tests, 81 real-server GameTests
across the release set, and all four shader client booths. Downloaded release
assets match the tested builds; installed server jars match those assets.
All five loaded versions were confirmed after an empty-server restart; Mod Hub
reports pack/server parity and RCON measured 20 TPS. The client and server packs
change only the five mod downloads and version label; shared preferences are
preserved. Rusty imports the client update in Prism for multiplayer playtesting.
The earlier release holds above are superseded.

## Rolling garage doors — 2026-09-20, local review

Version 1.9.0 adds connected redstone rolling doors under D-0015. The existing
vehicle protocol remains 5. The complete local server suite and shader booth
passed; see [evidence](../devtools/verification/garage-door.md). Publication,
pack updates and deployment require Rusty's explicit release instruction.

## Release authorization — September 20, 2026

Rusty requested: "release it all, 2 minute server warning". Version 1.9.0 is
authorized for public source/jar publication and pack 1.54.0 deployment. The clean
build passed 92 JUnit tests, 57 real-server GameTests and the complete
GPU shader booth. See [release verification](../devtools/verification/release-1.9.0.md).
Earlier release holds are superseded for this version.

## Garage chaining — 1.9.2

Rusty requested and authorized immediate release of ordinary garage-panel edge
placement, then approved removing the instruction message entirely. D-0017 removes
the consuming interaction handler. The existing construction regression now uses
normal clicks and can run alone with `-PgameTestNamespaces=vanillawheels_chaining`.
Validation and deployment are recorded in
[release verification](../devtools/verification/release-1.9.2.md).

Version 1.9.2 is now published and deployed in pack 1.54.2. The installed jar
matches the release; Mod Hub reports parity and the server runs at 20 TPS with
no new startup errors after the full warning and authorized disconnection.

## Held garage panel — 1.9.3 release

Rusty reported that a garage panel appears absent from the player's hand. The
published 1.9.2 JAR contains the item model, and the active client logs no garage
model load failure. The model inherits Minecraft's small angled block-item pose.
An item-only display transform enlarges and faces the panel toward the camera.
The garage GPU booth now captures empty and held first-person reference frames
with the GUI visible; it awaits the personal client's closure for visual review.
Rusty then explicitly requested release batched with a new world on seed
1000820165. The item-only build is complete and Rusty superseded the migration request:
all players start from scratch, with no inventories, animals or structures
carried into the new save. They requested release while their personal client
remains active, so the GPU held-item capture is not a gate. See
[release verification](../devtools/verification/release-1.9.3.md).

Version 1.9.3 is published and deployed in pack 1.54.3. Installed bytes match
the public release; pack parity and 20 TPS are verified. The held-item GPU
appearance remains the stated unverified limitation.
