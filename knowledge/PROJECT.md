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
