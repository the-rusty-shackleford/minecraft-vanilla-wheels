# Sound sources

Every sound shipped by this mod is cut from a recording taken from freesound.org under the
Creative Commons Zero (CC0 1.0) public-domain dedication, which permits use, modification and
redistribution without attribution. The recordists are credited here anyway, because they deserve
it. The files in this directory are the recordings as downloaded (Freesound's high-quality Vorbis
previews); `build.py` cuts, loops and normalizes them into
`src/main/resources/assets/vanillawheels/sounds/` (see `SOUNDS` there for the edits).

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `450821-pickup-horn-honks.ogg` | truck pickup horn honk aggressive annoyed various1.wav | kyles | https://freesound.org/people/kyles/sounds/450821/ | CC0 1.0 |
| `453741-performance-car-idle.ogg` | auto performance car rumbly engine idle close bassy.flac | kyles | https://freesound.org/people/kyles/sounds/453741/ | CC0 1.0 |
| `71741-nissan-maxima-handbrake-turn.ogg` | Nissan Maxima handbrake turn (04-25-2009).wav | audible-edge | https://freesound.org/people/audible-edge/sounds/71741/ | CC0 1.0 |
| `504626-body-fall-heavy-dirt.ogg` | BODY FALL - V HVY - DIRT | leonelmail | https://freesound.org/people/leonelmail/sounds/504626/ | CC0 1.0 |
| `835173-wrench-impact.ogg` | wrench_impact.wav | Mihacappy | https://freesound.org/people/Mihacappy/sounds/835173/ | CC0 1.0 |
| `386145-forge-adding-coal.ogg` | Forge - Adding coal shortest | ldezem | https://freesound.org/people/ldezem/sounds/386145/ | CC0 1.0 |

| Shipped sound | Built from |
|---|---|
| `horn_truck` | 0.85 s from the middle of the pickup horn's first blast |
| `engine_petrol` | 4 s of the idle's steady middle, crossfaded into a loop |
| `skid` | 0.75 s of the handbrake turn's squeal at its loudest |
| `thud` | the body fall's impact and settle |
| `wrench` | the wrench impact, whole |
| `fuel` | the coal into the forge, whole |
