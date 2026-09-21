# Held garage panel, 1.9.3 release

Rusty observed no visible panel in hand. The published 1.9.2 JAR contains
`assets/vanillawheels/models/item/garage_door.json`, and the active Prism client
reports no garage-model load error. The item inherits Minecraft's 0.4-scale,
45-degree block hand pose despite being a thin dark metal slab; that is the
working explanation, not a confirmed GPU rendering diagnosis.

The item-only display transforms enlarge and turn the existing slatted model
in first person, third person and inventory. The world block model and renderer
are unchanged. The garage booth now captures an empty-hand reference and the
held item with the hand visible. Inspect those screenshots before release.

`./gradlew jar compileGametestJava --offline --no-watch-fs` completed on Java 21.
Rusty explicitly authorized release batched with a new-world migration while
their personal Prism client is running. The GPU held-item capture is therefore
not run for this release; actual on-screen appearance remains unverified.
Rusty then explicitly removed the migration requirement: all players start from
scratch in the seed-1000820165 world. Public release and pack 1.54.3 deployment
are authorized. The GPU appearance remains unverified.
