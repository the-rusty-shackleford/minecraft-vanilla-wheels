# Vehicle recovery release verification — 2026-09-19

Release set: Vanilla Wheels 1.8.0, Trailblazer 1.8.0, Farmer's Pickup 1.4.0,
Trailer 2.4.0, Metals and Materials 1.0.3. User authorization: "Deploy it all so I can test that stuff."

The clean release builds passed 88 domain tests and 81 real-server GameTests
(51 / 11 / 7 / 5 / 7), plus each vehicle's actual client booth. Iris and
Complementary were enabled. The repair button, material return, hidden repaired
panel and final cargo tooltip were viewed in current captures. Consumer jars
were checked recursively for the exact shared Vanilla Wheels payload and absence
of nested Metals and Materials copies.

The initial Trailer invocation requested a nonexistent Maven publication task;
its supported `clean build` task was used. Its first shader booth ran on llvmpipe,
which missed the existing minimum frame samples. Using the native NVIDIA GPU
resolved that check. A copied Vanilla Wheels early-window setting had also changed
Trailer's established 854×480 viewport to 1280×720, causing a fixed pixel-count
check to fail over unpainted metal. Restoring the established viewport resolved it.
The mod code, assets and test assertions were unchanged in these reruns.

Local release logs and download/hash evidence are in
`/tmp/codex-vehicle-release-20260919/`. The earlier feature report describes the
cargo, repair and recall cases and their limits. This is not a full-pack multiplayer
playtest or a crash-atomicity guarantee; those remain distinct from the release checks.
