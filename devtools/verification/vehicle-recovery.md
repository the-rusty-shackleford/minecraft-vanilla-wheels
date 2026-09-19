# Vehicle recovery — local review, 2026-09-19

Candidate set: Vanilla Wheels 1.8.0, Trailblazer 1.8.0, Farmer's Pickup 1.4.0,
Trailer 2.4.0. Local `vehicle-recovery` branches; no publication, pack change,
production replacement or restart. This candidate includes the previously held
separate-materials dependency change. All peers require protocol 5.

## Verified in this session

- 88 JUnit tests passed, including distance/cap boundaries, shortfall rounding,
  half-rate condition loss, broken state, creative exemption and proportional repair.
- 51 Vanilla Wheels real-server GameTests passed. The original 36-test baseline
  passed before implementation. Added cases exercise real vehicle/item/menu paths:
  packed cargo instead of spills; named/damaged items; 432 slots through item and
  entity serialization; breaking and command kill; recalled car plus hitched trailer;
  fuel shortfall reaching zero condition; full inventory; passengers; creative;
  replacement fobs; saved pairing data; stale packed copies; collected drops;
  another mod canceling the drop; disconnect during recall; repairs and spare-material
  return. A remote car and trailer actually unload with `UNLOADED_TO_CHUNK`, then
  reload from saved entity storage and transfer their cargo through the fob.
- The three vehicle integration builds passed 11 / 7 / 5 server tests respectively.
  Their only profile changes are the approved repair policies: 20 steel / 18 iron /
  12 steel. All three appearance-import suites passed four tests each against real
  filesystem copies. Models, textures and historical profile references are unchanged.
- Full Vanilla Wheels `build publishToMavenLocal` passed, including the real client
  under Iris 1.8.14-beta.1 and Complementary Unbound r5.8.1. The actual mouse-click
  on Repair synchronized to the server, repaired the car, returned spare ingots,
  and hid the repair section. Tooltips and the native fob model were inspected in
  full frames and enlarged crops. The existing paint, dashboard, lights, lift and
  towing checks also passed. Test volume was zero and the client exited afterward.
- Final jars were packaged after the small tooltip grammar correction to
  `Cargo slots filled: N`. Every vehicle jar embeds the exact final Vanilla Wheels
  jar, which embeds Luminance alone; no indirect Metals and Materials copy remains.
  [Exact candidate hashes](vehicle-recovery/artifact-hashes.json).

## Review images

- [Repair section, empty](vehicle-recovery/booth-lift-repair-empty.png)
- [Repair ready, with cost](vehicle-recovery/booth-lift-repair-ready.png)
- [Repair panel and original lift UI, enlarged](vehicle-recovery/repair-crop.png)
- [After repair: panel hidden](vehicle-recovery/booth-lift-repaired.png)
- [Paired key fob](vehicle-recovery/booth-key-fob.png)
- [Broken vehicle with cargo](vehicle-recovery/booth-broken-item.png)

The last image precedes the final cargo-tooltip grammar correction; behavior and
layout are unchanged. Visual assessment: panel alignment, borders, material count,
button and labels are clear at the tested GUI scale. The fob's red button faces the
inventory camera. These are review captures, not a claim of subjective user approval.

## Findings resolved and limits

Minecraft's inventory-add helper deletes creative overflow, so vehicle packing uses
an explicit free slot or a real drop. Its generic spawn helper ignores a canceled
entity join; the regression test reproduced cargo loss before explicit acceptance
checking fixed it. Spare repair materials now return immediately when the panel hides.

An early unloaded-chunk fixture timed out; subsequent instrumented runs confirmed
actual unload/reload and passed repeatedly. The initial cause was not pinned. The
GameTest server runs ticks without the normal sleep, so asynchronous disk work has
less wall time than a production 200-tick request. Production requests time out
without charging and can be retried; cold-storage latency is not benchmarked here.

The first shader booth failed an existing hazard-stripe pixel threshold at its
default 854×480 window size. Pinning the harness to 1280×720 made the intended
viewport reproducible, and the existing assertion passed without lowering it.

This verifies the local shared implementation and real individual vehicle packs.
A production/full-pack multiplayer trial, an abrupt-process-crash recovery test,
and representative simultaneous-recall performance have not been performed.
World/entity and SavedData serialization are covered; this is not a claim of a
transaction spanning a crash between world and player saves. A trailer detached
by breaking or wrenching remains a separate vehicle; only a currently hitched
trailer joins a deployed vehicle's recall. Existing multiplayer/capacity follow-ups
in the project store remain open.
