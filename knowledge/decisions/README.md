---
title: Vanilla Wheels — decisions log
type: index
layer: store
tags: [index]
---

# Vanilla Wheels — decisions log

**Append-only.** Never edit an entry's rationale; supersede via a new entry with
`supersedes: D-NNNN`. Every entry has a status: `Active` / `Superseded` / `Rejected`.

| Id | Topic |
|----|-------|
| D-0001 | A vehicle is data: one entity type, a flat profile, mirrored once at load |
| D-0002 | The driver's client drives; climbing is vanilla's step; rolling resistance |
| D-0003 | The mesh renderer: our OBJ parser, normals from geometry, paint as vertex colour |
| D-0004 | The Mechanic Lift: the assembly is the profile; a 5 x 7 deck; the index finds the controller |
| D-0005 | Towing: the server follows its own tower; the axle chases the hitch; animals are passengers |
| D-0006 | A vehicle is authored in Blockbench: the `.bbmodel` is read as it is; the dye is lifted; the glass clears for the rider |
| D-0007 | The playtest against Automobility: only vehicles are walls; the server's re-run stands on the ground; the boost is a timed surge; riders lean and turn with the body |
| D-0008 | nfx's patch layer ported into source: the terrain pose (plane fit, springs), the tow tick order, the trailer gestures; ceilings are never ground; the lift raises the vehicle |
| D-0009 | Fuel is the gas can; creative driving requires and spends no fuel |
| D-0010 | Lift guidance, material bevels and a restrained continuous sound mix |
| D-0011 | Shared contacts and bounded, protected fragile destruction through the existing driving model |
| D-0012 | One Vanilla Wheels Creative tab groups every vehicle, chassis and shared part |

- [D-0013](D-0013.md): Require separately installed Metals and Materials.

- [D-0014](D-0014.md): Persistent cargo, lift repairs and paired recovery keys.

- [D-0015](D-0015.md): Connected redstone rolling garage doors.

- [D-0016](D-0016.md): Four-direction garage doors preserving existing inside/outside.

- [D-0017](D-0017.md): Normal garage panel clicks allow adjacent placement without warnings.

- [D-0018](D-0018.md): The suspension takes seven blocks of a fall; riders feel the rest.

- [D-0019](D-0019.md): What a vehicle hands back (a disc, a lead, a repair ingredient) goes where a give goes, a carried bag included, through Carried.

- [D-0020](D-0020.md): *Superseded by D-0025.* A crowbar (the wrench, renamed and aliased) pries a vehicle loose, crouching or not; a player's own blow does nothing to a vehicle.

- [D-0021](D-0021.md): A vehicle's condition is a row of wrenches above the hunger bar, blinking and jiggling as hearts do; no red hurt tint.

- [D-0022](D-0022.md): A trailer let go by hand rolls back a hair, and the vehicle it left cannot catch it again until the two have parted.

- [D-0023](D-0023.md): *Superseded by D-0025.* Every vehicle has its own crowbar in a one-slot toolbox; lost, dropped or left anywhere but on a person, it goes home. Stack components go through `HeldStack`.

- [D-0025](D-0025.md): Punch a vehicle six times in a row to pack it as it is (knocks, not wear); right-click a damaged one to repair it 2.5% a click for hunger scaled by its repair job, then board. After Immersive Aircraft; the crowbar and toolbox go.

- [D-0026](D-0026.md): One key per vehicle, named for it and banded in its paint; a paired key never leaves its owner (goes home from anywhere, waits for a respawn or a login); a blank in the air replaces one that is gone.

- [D-0027](D-0027.md): The garage door declares a dynamic shape. The cached per-state shape was the closed panel, and a vehicle's footprint read it, so no vehicle could drive through an open door at a driver's pace. Its "Not fixed" section is superseded by D-0028.

- [D-0028](D-0028.md): A block is a wall to the footprint where its cross-section at the climb line (the rectangle bounding its boxes that span the line) holds the point, not its whole bounds: a garage door's housing no longer walls its doorway, and fences, walls and stairs stop exactly where they did. The terrain fit's walks start from the body's own ground, so a door's track column is no longer ground three blocks up under a passing wheel.

- [D-0029](D-0029.md): Cargo is a registry of rules (`api/CargoRules`), animals the first; another mod's rule says what rides, what loads, what leads it and what becomes of its tether. An empty-handed click loads whatever the player leads that a rule admits (a captive's chain is on the captive). Serfdom's captives are the first other rule.

- [D-0030](D-0030.md): A protocol may be layered on this one: `api/VehicleKinds` lets it claim profiles for its own entity types, whose classes extend `Vehicle` and override its hooks (the wheel's tick, towing, policies, the engine loop); the renderer draws its extras; `wheels.drawn: false` is skids or feet. Rotorcraft is the first.

- [D-0031](D-0031.md): A body that moves in three dimensions (an aircraft in the air, a submarine) shares its hull (`domain/Hull`, `Vehicle.hullClamp`), its crashes (`domain/Crash` with a protocol's own speeds, judged from reported moves), and its keys (Up, Down, Get out; Shift never dismounts, a mixin) from here, moved out of Rotorcraft on Rusty's call; an engine may burn at a rate (`fuelRate`, `FuelDebt`); a protocol may set a vehicle down anywhere (`VehicleItem.place`). Network 7.

- [D-0032](D-0032.md): A key that acts once (the lights, getting out; Rotorcraft's hook and sprayer) acts on its press, not on the keyboard's repeats (`client/Keys.Press`): held past the repeat delay, H cycled the lights through every mode.
