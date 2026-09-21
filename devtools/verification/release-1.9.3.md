# Vanilla Wheels 1.9.3

The garage-panel item has its own larger, front-facing held and inventory poses.
The old 1.9.2 JAR contained the item model, but its thin dark geometry inherited
Minecraft's small 45-degree block pose. World geometry, placement and vehicle
protocol 5 are unchanged.

Rusty explicitly requested immediate release, batched with a new world on seed
1000820165. They subsequently specified a full fresh start: no old inventory,
animals, blocks, stats or world data are migrated. The old save is backed up for
rollback only. Public publication and pack 1.54.3 deployment are authorized.

`./gradlew jar compileGametestJava --offline --no-watch-fs` passed on Java 21.
The production JAR contains the item model transforms, and no test fixtures.
Rusty's personal Prism client remains active; they requested release without
waiting for the GPU held-item visual capture. Actual on-screen appearance is
therefore unverified in this release.

Artifact: `vanillawheels-1.9.3.jar` (527049 bytes).
SHA-1: `c70003d8479a7f206437eb18bd35a00b88c1acbb`.
SHA-256: `465aa7fd02450f722e0893b03d74cc70ce97e54df29eb7746bde2ca9f12fb1de`.

Deployment details will be recorded after the fresh world is verified.
