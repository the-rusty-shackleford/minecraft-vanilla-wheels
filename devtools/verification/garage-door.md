# Rolling garage door verification — 2026-09-20

Local 1.9.0, unreleased. JDK 21, Minecraft 1.21.1, NeoForge 21.1.248.

- `./gradlew test runGameTestServer jar --offline`: **92 domain tests and
  57 required real-server GameTests passed**. Six door tests cover recipes and
  placement, topology/orientations/bounds, redstone travel, living/vehicle
  obstructions, save/load, and dismantling an open assembly.
- The new dismantling regression failed against the initial implementation:
  incomplete panels acquired a half-block housing on every row. Retaining the
  original local geometry fixed it; all 57 tests then passed together.
- The full existing client booth and the added door course passed using native
  NVIDIA GeForce RTX 4070 rendering. The focused `runPhotoBooth -PgarageBooth`
  course also passed after the exposed-light correction, with Iris/Sodium and
  Complementary Unbound 5.8.1. Sound was muted and the client closed itself.
- Real client lever interaction packets opened and closed the door. A real vehicle
  held closing open, moved through the opening via collision-aware movement, and
  the door closed after it left. Screenshots were inspected for slats, tracks,
  header joints, light and matching clearance.

[Closed](garage-door/closed.png) · [Rolling](garage-door/rolling.png) ·
[Open](garage-door/open.png) · [Obstruction](garage-door/obstruction.png) ·
[Reclosed](garage-door/reclosed.png)

The last code change preserves incomplete-panel geometry; its server regression
passed after the shader captures. No independent multiplayer observer or natural
chunk-unload/reload playthrough is claimed. Historical vehicle multiplayer and
capacity follow-ups remain separate and open.
