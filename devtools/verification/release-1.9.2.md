# Vanilla Wheels 1.9.2

Ordinary right-click now places panels against other garage panels without
crouching. The warning handler and its unused text are removed under D-0017.
The existing four-facing construction test now uses real server game-mode edge
clicks. It reproduced the placement failure on 1.9.1 and passed after the fix,
including sixteen Survival panel consumptions, four 2-by-2 assemblies, retained
facing, redstone opening/closing and collision.

Rusty explicitly requested a small fix, immediate release and limited testing.
`./gradlew jar runGameTestServer -PgameTestNamespaces=vanillawheels_chaining
--offline --no-watch-fs` passed on Java 21: **one selected real-server test**.
The initial test launch hit the desktop inotify limit; config watching was then
disabled only in the disposable test server. The full JUnit/server suite and GPU
booth were not rerun for this interaction-only patch. Fixture classes are absent
from the production jar. No independent multiplayer observer result is claimed.

Artifact: `vanillawheels-1.9.2.jar` (526901 bytes).
SHA-1: `f54e7946e164cb5e88efce2fbb07da4c86d367e5`.
SHA-256: `e900c1608f13d5a263b278ec254c9b48753b68ba9984a4de35f19076dc42ac55`.

Public release and shared pack 1.54.2 deployment are explicitly authorized,
including disconnection after the previously requested full two-minute warning.
Both pack archives and their HTTP downloads match the assembled candidates;
only Vanilla Wheels and the version change from 1.54.1.

## Deployed

The in-game two-minute warning was acknowledged at 2026-09-21T02:18:09.460773+00:00.
After 131.9 seconds, 1 remaining players were
disconnected as explicitly authorized. RCON confirmed zero players, flushed the
world save, and confirmed zero again before restart at 2026-09-21T02:20:22.208603+00:00.
Fresh startup completed: `[21Sep2026 02:20:36.597] [Server thread/INFO] [net.minecraft.server.dedicated.DedicatedServer/]: Done (2.603s)! For help, type "help"`.

The installed 1.9.2 jar matches the tested GitHub asset; the old 1.9.1 jar is absent.
The correct version loaded, Mod Hub reports pack/server parity, and RCON reports
20 TPS overall and in every dimension. Startup retained the same
36 baseline errors with none added. World `world-20260919`, view distance
24, simulation distance 12, entity broadcast range 200%, operators, whitelist,
Distant Horizons and Chunky settings are preserved. Chunky remains paused.
C.A.M.P. 0.3.0 and Craftlight 0.1.0 retain their prior hashes. Rusty updates Prism
through Mod Hub; their personal client and the reusable test instance were untouched.
