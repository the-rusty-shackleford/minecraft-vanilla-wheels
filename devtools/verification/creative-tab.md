# Vanilla Wheels Creative tab — 2026-09-18

Local and unreleased. Rusty requested dedicated Creative tabs for item-adding mods,
then chose one shared Vanilla Wheels page for all vehicles and their assembly supplies.

Java 21: `./gradlew --no-watch-fs build -PskipBooth -Pmod_version=1.7.1` passed, including **36 real-server
GameTests**. All 83 domain JUnit tests also ran and passed. The existing backpack/steel tab checks were extended to cover their new pages.
Other gameplay behavior was not changed.

One muted, isolated full-pack 1.39.0 client/server pair loaded all four updated mods.
This mod ran as 1.7.1 local candidate. Candidate version overrides selected the modified jars
instead of older nested dependencies; they are not published releases. No production
pack or personal client was updated. Iris/Complementary shaders were enabled.

Used native keyboard/mouse input to open Creative inventory, navigate its pages and
select **Vanilla Wheels**. Viewed the [actual capture](creative-tab.png): the title and icon
render correctly, and the tab contains all three vehicles, all three chassis, the lift, wrench, wheel, engine and full/empty gas cans.

Starting with an empty inventory, ordinary Creative clicks retrieved all three finished vehicles and all three chassis with their correct profile components.
The dedicated server's player inventory confirmed the resulting item IDs and, for
vehicles/chassis, the `vanillawheels:vehicle` profile component. No give commands or
crafting ingredients were used. Across all four tabs, nine selected items reached
the server correctly.

The full-pack server had the same 38 startup ERROR lines as the previous local baseline,
with no added messages. This does not claim the third-party pack is error-free.
The test client exited successfully; the isolated server was stopped after confirming
zero connected players. The release and deployment remain held.

Scratch logs, staged artifact hashes, inventory readback and error comparison:
`/tmp/codex-creative-tabs-20260918/`.
