# Vanilla Wheels 1.9.1

Garage doors now support all four outside directions. The first panel faces its
placer, coplanar extensions inherit it, and old saved doors preserve their
orientation. Full-rectangle construction, redstone control and obstruction
handling remain unchanged. A test-fixture floor spill into neighboring tests is
also corrected. Vehicle protocol remains 5.

Rusty explicitly requested release, then instructed disconnection of remaining
players after a two-minute warning. This authorizes public publication to the
existing Vanilla Wheels repository and shared pack 1.54.1 deployment, superseding
the earlier local hold. No release of other mods is included.

`./gradlew clean build --offline` passed **92 JUnit tests, 61 real-server
GameTests and the complete muted RTX 4070 / Iris / Complementary booth**.
The clean jar is byte-identical to the reviewed candidate. The inspected
[inside/outside captures and compatibility tests](garage-facing.md) apply to
this exact artifact. Verification fixtures are excluded from the production jar.

Artifact: `vanillawheels-1.9.1.jar` (527393 bytes).
SHA-1: `f4370e1ab99f4835039a1347dde6a9dc39fd9871`.
SHA-256: `3440e4c315f71a3d6bc35db61c58cbfd8aa54807e402107ca065b075b3855a58`.

## Published and deployed in pack 1.54.1

The GitHub download matches the tested jar. Both assembled pack archives and
their HTTP downloads were verified; only Vanilla Wheels and the version change
from 1.54.0. Unrelated files and overrides are identical.

The warning reached in-game chat at **2026-09-21T02:02:48.066186+00:00**.
Rusty explicitly authorized disconnecting remaining players after two minutes.
After 140.3 seconds, 2 remaining players
were disconnected. RCON confirmed an empty server, `save-all flush` completed,
and a second empty-player check passed. Restart was requested at
**2026-09-21T02:05:09.372702+00:00**. Fresh startup completed:

`[21Sep2026 02:05:23.526] [Server thread/INFO] [net.minecraft.server.dedicated.DedicatedServer/]: Done (2.532s)! For help, type "help"`

The installed Vanilla Wheels 1.9.1 jar matches the tested and published artifact;
the old top-level 1.9.0 jar is absent. C.A.M.P. 0.3.0 and Craftlight 0.1.0 retain
their prior hashes. All three versions loaded. Mod Hub reports pack/server
parity, and RCON reports **20 TPS** overall and in every dimension. The complete
startup has the same 36 baseline error messages, with no added errors.
Existing third-party errors remain.

World settings, operator and whitelist files, Distant Horizons and Chunky
configuration retain their previous values/hashes. Chunky reports no tasks
running; pregeneration remains paused. Rusty's personal Prism instance and the
retained Magical Map test instance were not modified. Players update through
Mod Hub before rejoining.

Independent multiplayer observer testing is not claimed.
