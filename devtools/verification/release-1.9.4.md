# Vanilla Wheels 1.9.4

Riders feel only the part of a vehicle's fall past the suspension's seven blocks
([D-0018](../../knowledge/decisions/D-0018.md)). A friend of Rusty's was hurt driving the
Trailblazer downhill. The vehicle protocol (5), profiles and recipes are unchanged.

Rusty authorized the release on 2026-09-28 ("Release it").

The release gate was `clean build` through `tools/booth/run_iconified.sh`, with Rusty's
client not running. It passed:

- 93 JUnit tests.
- 63 real-server GameTests. They include the two `vanillawheels_falls` tests, which failed on
  1.9.3 with rider losses of 4 and 10 (expected 0 and 3).
- All 36 GPU booth checks.

The production jar contains no gametest classes.

Artifact: `vanillawheels-1.9.4.jar` (527504 bytes).
SHA-1: `46bace64cf5d6c023d36aa541ba3c0d242279603`.
SHA-256: `7798b6452e753af31815a03d89e05f861ffb6955fd3f98bf6cbc781de6d43f4f`.
The GitHub release asset, downloaded, matches by SHA-1.

Deployed in pack 1.67.1, alone. Nobody was online, so the restart was immediate. The
server's `/data/mods` holds `vanillawheels-1.9.4.jar` with the SHA-1 above, and 1.9.3 is gone.
The log shows "vanillawheels (version 1.9.3 -> 1.9.4)". Startup kept the 36 baseline error
lines and the usual single startup "can't keep up". TPS is 20, and Mod Hub reports parity.

Not verified: a player riding a vehicle downhill on the live server. The gametests' rider is a
villager (the game seats a player at the wheel, and the server leaves a player's driving to
its client). It takes the vehicle's fall through the same call a player does.
