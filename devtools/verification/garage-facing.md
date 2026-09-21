# Garage door facing — 1.9.1

Rusty acknowledged four-direction placement and withdrew the separate chaining
report. Full-rectangle construction, interaction messages and redstone behavior
are retained. Initial local validation is recorded below; see the
[release and deployment record](release-1.9.1.md) for the published version.

## Reproduction and compatibility

The first-panel regression ran through `ServerPlayerGameMode.useItemOn` against
unchanged 1.9.0 production code and failed: four headings produced two states.
The fix retains the saved width axis and adds a default-false reversal flag, so
old X and Z palettes retain their former rendered outside directions. New tests
cover all facings, Survival consumption, top-down construction, inherited facing,
redstone motion/collision, palette save/load, rotations, mirrors and refused
bridges between opposite-facing doors.

## Test fixture correction

The first expanded full-suite run passed all new door checks but failed the
existing lift-build placement test. A second run reproduced it; temporary
diagnostics identified dirt in the controller cell. `VehicleGameTests.layFloor`
always wrote 48 blocks even when three callers used the 15-block arena, spilling
into neighboring tests. Its floor length now respects the fixture bounds.
Temporary lift diagnostics were removed. No lift production behavior changed.

## Validation

`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build --offline`
passed **92 JUnit tests, all 61 real-server GameTests and the complete shader
client booth**. The corrected fixture passes alongside all four new door tests.
The actual client placement packets chose north, east, south and west correctly,
consuming exactly one Survival panel each. Existing garage opening, vehicle
obstruction, collision-aware vehicle passage and closure checks also passed.

The host had no other rendering Minecraft client. Software-rendering overrides
were removed, and the client log verified **NVIDIA GeForce RTX 4070**, Iris/Sodium
and Complementary Unbound 5.8.1. The booth muted master volume and exited itself.
No personal client or live-server files were changed.

All eight side views were inspected in three-times-enlarged crops. Tracks, slat
edges, handle, roll and header backing consistently exchange sides with the
chosen facing. Lighting varies with compass direction under the shaders. The
existing framed door was compared with its prior capture at twice enlargement;
its silhouette, slats, roll and frame joints remain visually consistent.

Each pair below shows **outside on the left, inside on the right**:

- [North](garage-facing/north-outside-inside.png)
- [East](garage-facing/east-outside-inside.png)
- [South](garage-facing/south-outside-inside.png)
- [West](garage-facing/west-outside-inside.png)
- [Original door before/after](garage-facing/legacy-before-after.png)

Artifact: `vanillawheels-1.9.1.jar` (verification fixtures excluded).
SHA-256: `3440e4c315f71a3d6bc35db61c58cbfd8aa54807e402107ca065b075b3855a58`.

The published release uses this exact artifact. No independent multiplayer
observer or upgrade of a live-world copy is claimed; legacy compatibility was
verified through the actual vanilla block-state palette serialization path.

## Release-build camera correction

The first clean release build passed every server and garage check but failed
the existing blue-to-red vehicle paint assertion. Its captured car was correctly
red; the camera had drifted off-centre and the pixel counter included shader sky.
The automated booth now releases mouse capture while in-world, keeping desktop
mouse movement and window-manager cursor warps out of its scripted camera. This
changes only the verification harness. The clean release rerun is recorded in
the release verification record.
