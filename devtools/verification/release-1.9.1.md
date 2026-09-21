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

Pack assembly, downloaded-asset comparison and live deployment are recorded
after verification. Independent multiplayer observer testing is not claimed.
