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
Deployment results are recorded after live verification.
