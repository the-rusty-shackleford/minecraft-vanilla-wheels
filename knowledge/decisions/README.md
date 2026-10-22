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

- [D-0033](D-0033.md): Aboard a body with vertical controls (D-0031) the boarding line names the get-out key ("Press R to Dismount"), not Shift, which goes down there (Rusty, in the submarine playtest: the subs and the helicopters said Left Shift); a client mixin on the passengers packet; a car's line is untouched.

- [D-0034](D-0034.md): A profile's `durability` (absent 1) divides every blow but a player's own punch (`domain/Blows`); crashes are not divided. An explosion is taken once, at its largest piece, and a bolt once: both reached the body and every hit box (part entities), and a rocket or a creeper wrecked anything. The creative tab lists the ground vehicles only; a protocol's vehicles are in its own tab (`VehicleKinds.claimed`).

- [D-0035](D-0035.md): The Vehicle Sensor gives full power, strongly, while any vehicle (ours, a part of one, or anything not alive carrying a player) is within its range, a cube of 1..32 blocks (default 8; an empty-hand click steps 2/4/8/12/16/20/24/32), looking every 2 ticks and letting go half a second after; Rusty: "range based and not 'tripwire' based". The tripwire built first is gone.

- [D-0036](D-0036.md): The Hidden Door is the garage door's assembly with a sinking curtain (`Shutter.Kind.SINKING`, half a block a tick: a four-high door open in 0.4 s), every panel drawn as a solid block given to it; no Create contraption, which flickered under Rusty's shaders. Found: a panel's load tick named the garage door block, so a hidden door went dead after a restart; fixed for both.

- [D-0037](D-0037.md): The Floor Hatch: housings in a floor lay real plate blocks over the opening in front of them and take them away when it opens, so a Create car rising never meets them (a car takes a block it meets with it); open while powered, while something not alive rises from below, or while the opening is filled; never under a foot unless the car is within 1.5 blocks; shut only over an empty opening; an iron-bar guard on the open edge while the hole is open and empty. For Rusty's elevator nook.

- [D-0038](D-0038.md): A rider keeps their seat: leaving frees only their own, nobody moves up, and a passenger takes a free driver's seat with **U** (*Take the controls*); with nobody at the controls the server brings the vehicle to rest itself. Every player aboard keeps their vehicle and seat in their own save; logging back in not aboard, they are seated in it again wherever it is (if loaded), else stood on the ground under where they left or at the water's surface over them, never on a harm. OtatopMalloy's death (2026-10-11): the game keeps a vehicle in a save only for its one player aboard, so the pilot was saved in the air and the Huey went with the crew. Network 8.

- [D-0039](D-0039.md): The server re-runs a driver's reported move at the heading the client made it at (a mixin on `handleMoveVehicle`, `Vehicle.faceReported`). Every vehicle turns, then moves, but the game re-ran the move at the old heading, so a footprint or hull that turns with the body was tested a tick's turn behind: the Huey's "moved wrongly" resets on the box (2026-10-11). Immersive Aircraft's own resets remain.

- [D-0040](D-0040.md): The Mechanic Lift docks a vehicle left standing on its deck (nobody at the wheel, not towing, still for half a second): the rams run and through the hold it glides to its spot, backed up to the lift's new back wall (three cells, indices 34..36 appended; an old lift grows it on load), centred, facing the front, and is held there until a throttle lets it go. Build stands a new vehicle docked. Refused and said in the menu: towing, too big (the Pickup just fits, the Huey never), taken, blocked, a driver aboard. `Vehicle.docks()` lets a protocol refuse.

- [D-0041](D-0041.md): Every key lives on a keyring made by itself with the first key, one a player, never crafted; the ring cannot be dropped, refuses every container, and comes back after a death; using it opens a screen with a row a vehicle (paint, name, where it is and how far, the share of a tank a recall costs) and Recall, the exact quote, Confirm and Cancel. Loose keys from before join the ring at login. Network 9. Supersedes D-0026's loose key and its "no keyring".

- [D-0042](D-0042.md): The Mechanic Lift is eight long (five wide as before), so the Farmer's Pickup parks whole on the deck, its nose 0.62 inside the front edge; a lift keeps the shape it was laid with (`Footprint.SHORT` or `LONG`, a `long` state on the controller and every part; a block saved before reads short, its cells in the order they always were) and is never regrown long: break it and place it again. Network 10, since the lift's blocks have a new state.

- [D-0043](D-0043.md): A parked vehicle sits centred between the deck's front edge and the wall's face, the same margin at both ends (any body that leaves a quarter block at each end; the whole fleet on a long lift); a body too long for that backs up to the wall with its nose over the front edge, as every vehicle did under D-0040. Rusty, on the re-laid garage: the Trailblazer "is too far back".

- [D-0044](D-0044.md): The keys on a ring compare by value (`Keyring.Keys`, vanilla's `listMatches` and `hashStackList`), so a ring is the same item with the same components after the network and a save; saved and sent as the plain list it was. A Warehouse Manager's index could not hand Rusty's ring back.
