"""The art, as code: the part icons, the lift's textures, the sounds, and the box car the gametests and the booth drive.

Run from the repository root:

    uv run --no-project python devtools/art/build.py

Everything it writes is committed; this script is the source of truth for
those files. The icons land under src/main/resources/assets/vanillawheels/;
the box car -- two OBJ meshes and a swatch texture -- under
src/gametest/resources/assets/vanillawheels_gametest/, a vehicle plain
enough to test with and nothing anyone would drive for fun. All original
work. Sounds are cut from CC0 field recordings by `sounds` (see
devtools/art/sounds/SOURCES.md) and need ffmpeg on the PATH.
"""
from __future__ import annotations

import json
import math
import struct
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODID = "vanillawheels"
ASSETS = ROOT / "src/main/resources/assets" / MODID
TEST_ASSETS = ROOT / "src/gametest/resources/assets" / (MODID + "_gametest")


# ---------------------------------------------------------------- PNG writing

def write_png(path: Path, width: int, height: int, pixels) -> None:
    """pixels: rows of (r, g, b, a) tuples, top row first."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


class Noise:
    """A deterministic grain so flat colours read as material, not plastic."""

    def __init__(self, seed: int) -> None:
        self.state = seed & 0xFFFFFFFF

    def next(self) -> float:
        self.state = (1664525 * self.state + 1013904223) & 0xFFFFFFFF
        return self.state / 0xFFFFFFFF


def shade(rgb, delta):
    return tuple(max(0, min(255, c + delta)) for c in rgb)


def _canvas():
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = (*c, 255)

    return px, put


# ------------------------------------------------------------------- the icons

STEEL_LIGHT, STEEL, STEEL_DARK = (150, 158, 170), (104, 112, 124), (58, 64, 74)
RUBBER, RUBBER_DARK = (40, 40, 44), (22, 22, 26)


def wheel_icon():
    """A tyre seen side-on: a dark ring around a steel hub."""
    px, put = _canvas()
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 7.2:
                if d > 4.6:
                    put(x, y, RUBBER if (x + y) % 3 else RUBBER_DARK)
                elif d > 3.4:
                    put(x, y, STEEL_DARK)
                elif d > 1.2:
                    put(x, y, STEEL if (x - y) % 4 else STEEL_LIGHT)
                else:
                    put(x, y, STEEL_DARK)
    return px


def engine_icon():
    """A block of an engine: a steel block with a redstone heart and a diamond gleam."""
    px, put = _canvas()
    for y in range(4, 14):
        for x in range(2, 14):
            put(x, y, STEEL if (x + y) % 5 else STEEL_DARK)
    for y in range(2, 5):
        for x in range(4, 12, 2):
            put(x, y, STEEL_LIGHT)
    for (x, y) in ((6, 8), (7, 8), (8, 8), (9, 8), (7, 7), (8, 7), (7, 9), (8, 9)):
        put(x, y, (190, 30, 30))
    put(11, 6, (120, 220, 235))
    put(12, 5, (200, 245, 250))
    return px


def wrench_icon():
    """An open-end wrench lying diagonally."""
    px, put = _canvas()
    for i in range(3, 13):
        put(i, 15 - i, STEEL)
        put(i + 1, 15 - i, STEEL_LIGHT)
        put(i, 16 - i, STEEL_DARK)
    for (x, y) in ((11, 1), (12, 1), (13, 1), (11, 2), (13, 2), (10, 3), (11, 3), (13, 3), (14, 3), (12, 4), (13, 4)):
        put(x, y, STEEL_LIGHT)
    for (x, y) in ((1, 13), (2, 13), (1, 14), (3, 14), (2, 15), (3, 12)):
        put(x, y, STEEL_LIGHT)
    return px


def lift_icon():
    """The lift from the front: a deck plate on two posts, hazard-striped."""
    px, put = _canvas()
    for x in range(1, 15):
        put(x, 9, STEEL_LIGHT)
        put(x, 10, (230, 180, 40) if (x // 2) % 2 else RUBBER)
        put(x, 11, STEEL_DARK)
    for x in (2, 3, 12, 13):
        for y in range(3, 9):
            put(x, y, STEEL if x in (2, 12) else STEEL_DARK)
        put(x, 12, STEEL_DARK)
        put(x, 13, STEEL_DARK)
    for x in range(1, 15):
        put(x, 14, STEEL_DARK)
    return px


ICONS = {"wheel": wheel_icon, "engine": engine_icon, "wrench": wrench_icon, "mechanic_lift": lift_icon}


# ---------------------------------------------------------------- the lift's textures

def lift_plate():
    """A steel plate with rivets in the corners: the posts and the deck's sides."""
    noise = Noise(0x11F7)
    px = [[(0, 0, 0, 255) for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = shade(STEEL, int((noise.next() - 0.5) * 18))
            if x == 0 or y == 0:
                c = STEEL_LIGHT
            if x == 15 or y == 15:
                c = STEEL_DARK
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                c = STEEL_DARK
            if (x, y) in ((3, 3), (14, 3), (3, 14), (14, 14)):
                c = STEEL_LIGHT
            px[y][x] = (*c, 255)
    return px


def lift_deck():
    """The deck's top: diamond plate."""
    noise = Noise(0xDECC)
    px = [[(0, 0, 0, 255) for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = shade(STEEL, int((noise.next() - 0.5) * 14))
            if (x + y) % 4 == 0 and (x - y) % 4 == 0:
                c = STEEL_LIGHT
            elif (x + y) % 4 == 1 and (x - y) % 4 == 1:
                c = STEEL_DARK
            px[y][x] = (*c, 255)
    return px


def lift_stripe():
    """A hazard band, diagonal yellow and black, running along U: laid along the deck's outer edge."""
    px = [[(0, 0, 0, 255) for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = (230, 180, 40) if ((x + y) // 4) % 2 == 0 else (30, 30, 34)
            px[y][x] = (*c, 255)
    return px


def lift_gui():
    """The menu's background: a 176 x 184 panel on a 256 x 256 sheet, slots at the menu's positions."""
    W, H = 176, 184
    px = [[(0, 0, 0, 0) for _ in range(256)] for _ in range(256)]
    panel, light, dark, slot = (198, 198, 198), (255, 255, 255), (85, 85, 85), (139, 139, 139)
    for y in range(H):
        for x in range(W):
            c = panel
            if x < 3 or y < 3:
                c = light if not ((x < 3 and y >= H - 3) or (y < 3 and x >= W - 3)) else panel
            if x >= W - 3 or y >= H - 3:
                c = dark if not ((x >= W - 3 and y < 3) or (y >= H - 3 and x < 3)) else panel
            if (x, y) in ((0, 0), (0, 1), (1, 0), (W - 1, H - 1), (W - 2, H - 1), (W - 1, H - 2), (0, H - 1), (W - 1, 0)):
                c = None
            if c is not None:
                px[y][x] = (*c, 255)

    def slot_at(sx, sy):
        for y in range(18):
            for x in range(18):
                c = slot
                if x == 0 or y == 0:
                    c = dark
                if x == 17 or y == 17:
                    c = light
                px[sy - 1 + y][sx - 1 + x] = (*c, 255)

    for sx in (26, 62, 98, 134):
        slot_at(sx, 24)
    for row in range(3):
        for col in range(9):
            slot_at(8 + col * 18, 102 + row * 18)
    for col in range(9):
        slot_at(8 + col * 18, 160)
    # the job bar's groove
    for y in range(80, 86):
        for x in range(17, 159):
            px[y][x] = (*(dark if y in (80, 85) or x in (17, 158) else (160, 160, 160)), 255)
    return px


# ---------------------------------------------------------------- the box car
#
# A mesh in pixels, +Z forward, +Y up, right-handed (+X is the car's left).
# Every part is a box with one swatch of a 4x4 atlas per material.

CELLS = {"body": (0, 0), "trim": (1, 0), "glass": (2, 0), "lamp": (3, 0),
         "needle": (0, 1), "dash": (1, 1), "tyre": (2, 1), "hub": (3, 1)}
COLOURS = {"body": (200, 200, 200), "trim": (70, 74, 80), "glass": (150, 200, 230), "lamp": (250, 240, 170),
           "needle": (220, 40, 40), "dash": (40, 40, 46), "tyre": (30, 30, 34), "hub": (140, 146, 156)}


def atlas():
    noise = Noise(0xB0C4)
    px = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]
    for name, (cx, cy) in CELLS.items():
        base = COLOURS[name]
        alpha = 160 if name == "glass" else 255
        for y in range(16):
            for x in range(16):
                px[cy * 16 + y][cx * 16 + x] = (*shade(base, int((noise.next() - 0.5) * 16)), alpha)
    return px


class ObjWriter:
    def __init__(self) -> None:
        self.lines = ["# generated by devtools/art/build.py: the box car"]
        self.v = 0
        self.vt = 0

    def box(self, material, group, x0, y0, z0, x1, y1, z1):
        """A closed box from (x0,y0,z0) to (x1,y1,z1), six quads on one swatch, wound outward."""
        cx, cy = CELLS[material]
        u0, v0 = cx / 4, 1 - (cy + 1) / 4
        u1, v1 = (cx + 1) / 4, 1 - cy / 4
        corners = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        for c in corners:
            self.lines.append("v %.3f %.3f %.3f" % c)
        for (u, vv) in ((u0, v0), (u1, v0), (u1, v1), (u0, v1)):
            self.lines.append("vt %.4f %.4f" % (u, vv))
        base_v = self.v
        base_t = self.vt
        self.lines.append(f"usemtl {material}")
        self.lines.append(f"g {group}")
        # each face: corner indices (1-based, relative to base) wound outward
        faces = [(1, 4, 3, 2), (5, 6, 7, 8), (1, 2, 6, 5), (3, 4, 8, 7), (2, 3, 7, 6), (1, 5, 8, 4)]
        for f in faces:
            self.lines.append("f " + " ".join(f"{base_v + i}/{base_t + k + 1}" for k, i in enumerate(f)))
        self.v += 8
        self.vt += 4

    def text(self):
        return "\n".join(self.lines) + "\n"


def box_car():
    w = ObjWriter()
    # body: 24 wide (x ±12), 48 long (z -24..24), floor at y 6, roof at y 25
    w.box("body", "body", -12, 6, -24, 12, 14, 24)            # the tub
    w.box("body", "body", -12, 14, -8, 12, 25, 12)            # the cab
    w.box("glass", "cab", -11, 16, 12.25, 11, 24, 12.75)      # windscreen, clear of the cab face
    w.box("trim", "bumper", -12, 5, 24, 12, 9, 26)            # front bumper
    w.box("trim", "bumper", -12, 5, -26, 12, 9, -24)          # rear bumper
    w.box("lamp", "lamps", -10, 9, 24, -6, 12, 24.5)          # left lamp
    w.box("lamp", "lamps", 6, 9, 24, 10, 12, 24.5)            # right lamp
    w.box("dash", "dash", -11, 14, 8, 11, 16, 11)             # dashboard
    w.box("needle", "dash", -6.4, 15.9, 9, -5.6, 18, 9.4)     # speed needle, pivot at (-6, 16, 9.2)
    w.box("needle", "dash", 5.6, 15.9, 9, 6.4, 18, 9.4)       # fuel needle, pivot at (6, 16, 9.2)
    w.box("trim", "bed", -11, 14, -24, 11, 15, -8)            # the bed floor
    w.box("trim", "chest", -8, 14, -22, 8, 20, -10)           # a chest in the bed
    frame = w.text()
    wheel = ObjWriter()
    wheel.box("tyre", "wheel", -2, -6, -6, 2, 6, 6)
    wheel.box("hub", "wheel", -2.5, -2.5, -2.5, 2.5, 2.5, 2.5)
    return frame, wheel.text()


def box_trailer():
    """A two-wheeled box on a tongue: walls, a roof, two rear doors on hinges at the outer edges."""
    w = ObjWriter()
    w.box("trim", "floor", -10, 6, -20, 10, 8, 16)               # the floor
    w.box("body", "wall", 9, 8, -20, 10, 22, 16)                 # left wall (+X)
    w.box("body", "wall", -10, 8, -20, -9, 22, 16)               # right wall
    w.box("body", "wall", -10, 8, 15, 10, 22, 16)                # front wall
    w.box("trim", "roof", -10, 22, -20, 10, 23, 16)              # the roof
    w.box("body", "door_left", 0.5, 8, -21, 10, 22, -20)         # left door, hinge at x = 10
    w.box("body", "door_right", -10, 8, -21, -0.5, 22, -20)      # right door, hinge at x = -10
    w.box("trim", "tongue", -1, 7, 16, 1, 9, 34)                 # the tongue
    w.box("hub", "tongue", -1.5, 6.5, 33, 1.5, 9.5, 35)          # the coupler: hitch at (0, 8, 34)
    return w.text()


BOX_CAR_PROFILE = {
    "mesh": "vanillawheels_gametest:box_car",
    "wheel_mesh": "vanillawheels_gametest:box_wheel",
    "texture": "vanillawheels_gametest:textures/entity/box_car.png",
    "scale": 0.0625,
    "handedness": "right",
    "body": {"width": 1.5, "length": 3.0, "height": 1.6,
             "parts": [{"at": [0, 6, 18], "width": 1.5, "height": 0.6}, {"at": [0, 6, -16], "width": 1.5, "height": 0.9}]},
    "seats": [{"at": [6, 6, 2], "driver": True}, {"at": [-6, 6, 2]}],
    "wheels": {"radius": 6, "positions": [{"forward": 16, "right": -12, "steers": True}, {"forward": 16, "right": 12, "steers": True},
                                         {"forward": -16, "right": -12}, {"forward": -16, "right": 12}]},
    "engine": {"max_speed": 0.9, "acceleration": 0.02, "reverse_speed": 0.3, "brake": 0.05, "drag": 0.01},
    "handling": {"grip": 0.85, "steer_degrees": 32, "drift_grip": 0.4, "drift_boost": 0.3, "drift_charge_ticks": 40},
    "climb": 2.0,
    "mass": 1.0,
    "fuel": {"capacity": 24000},
    "storage": {"rows": 3, "region": {"z_max": -8}},
    "gauges": [{"kind": "speed", "part": {"material": "needle", "x_max": 0}, "pivot": [-6, 16, 9.2], "axis": [0, 0, 1], "zero": 0.3, "sweep": -3.0},
               {"kind": "fuel", "part": {"material": "needle", "x_min": 0}, "pivot": [6, 16, 9.2], "axis": [0, 0, 1], "zero": -0.3, "sweep": 3.0}],
    "headlights": {"at": [[-8, 10.5, 24.5], [8, 10.5, 24.5]], "part": {"material": "lamp"}, "range": 10},
    "horn": "vanillawheels:horn.truck",
    "radio": {"at": [0, 15, 9]},
    "hitch": {"rear": [0, 7, -26]},
    "paint": {"part": {"material": "body"}, "default": "light_blue"},
    "glass": {"material": "glass"},
    "sounds": {"engine": "vanillawheels:engine.petrol"},
}


# ---------------------------------------------------------------- sounds

RATE = 44100
SOURCES = Path(__file__).resolve().parent / "sounds" / "src"


def decode(stem):
    """Reads sounds/src/<stem>.ogg through ffmpeg as mono float samples at RATE."""
    path = SOURCES / f"{stem}.ogg"
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "f32le", "-ac", "1",
                          "-ar", str(RATE), "pipe:1"], capture_output=True, check=True).stdout
    return list(struct.unpack(f"<{len(raw) // 4}f", raw))


def take(stem, start, end=None, at=0.0, gain=1.0, fade_in=0.003, fade_out=0.03):
    """One cut of a recording: the samples of `stem` from `start` to `end`
    seconds (`None`: its end), faded in and out over the given seconds so a
    cut never clicks, to be placed `at` seconds into the result at `gain`."""
    return (stem, start, end, at, gain, fade_in, fade_out)


def cut(stem, start, end):
    samples = decode(stem)
    return samples[int(start * RATE):len(samples) if end is None else min(len(samples), int(end * RATE))]


def assemble(takes, peak):
    """Mixes the takes into one clip and normalizes it to `peak`. The clip
    is as long as the latest take runs; nothing is added -- no reverb, no
    tone, no filtering -- so what is heard is the recordings and their
    timing."""
    cuts = []
    for stem, start, end, at, gain, fade_in, fade_out in takes:
        samples = cut(stem, start, end)
        n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
        for i in range(min(n_in, len(samples))):
            samples[i] *= i / n_in
        for i in range(min(n_out, len(samples))):
            samples[len(samples) - 1 - i] *= i / n_out
        cuts.append((int(at * RATE), [c * gain for c in samples]))
    out = [0.0] * max(i0 + len(c) for i0, c in cuts)
    for i0, c in cuts:
        for i, x in enumerate(c):
            out[i0 + i] += x
    return normalize(out, peak)


def loop(stem, start, end, crossfade, peak):
    """A seamless loop: the recording from `start` to `end`, with its last
    `crossfade` seconds blended into its first so the join is inaudible
    when the game plays it end to start. The result is `end - start -
    crossfade` seconds long."""
    samples = cut(stem, start, end)
    n = int(crossfade * RATE)
    body = samples[:len(samples) - n]
    tail = samples[len(samples) - n:]
    for i in range(n):
        t = i / n
        body[i] = body[i] * t + tail[i] * (1.0 - t)
    return normalize(body, peak)


def normalize(samples, peak=0.95):
    top = max(abs(x) for x in samples) or 1.0
    return [x * peak / top for x in samples]


def write_ogg(path: Path, samples) -> None:
    """Writes 16-bit mono PCM through ffmpeg into Ogg Vorbis. Bit-exact, so
    the same samples give the same bytes and an unchanged sound is an
    unchanged file in the diff."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                    "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", "-fflags", "+bitexact", "-flags", "+bitexact", str(path)],
                   input=pcm, check=True)


# The shipped sounds: which recording, which seconds of it. Times are in
# seconds, chosen from 100 ms RMS envelopes of the recordings. In-game
# volumes: the horn 1.2, the engine 0.35..0.9 by speed, the skid 0.6, the
# thud 0.9, the wrench 1.0, the fuel 0.8.
SOUNDS = {
    # A pickup's horn, leaned on: 0.85 s from the middle of the first blast,
    # faded at both ends. The game re-triggers it every 15 ticks while the
    # key is held, so the blasts overlap into one held note.
    "horn_truck": lambda: assemble([take("450821-pickup-horn-honks", 2.0, 2.85, fade_in=0.02, fade_out=0.06)], 0.9),
    # A performance car's idle, close and bassy: four seconds from the
    # steady middle of the take, looped with a 150 ms crossfade. The game
    # pitches it from 0.75 at idle to 1.6 at the top speed.
    "engine_petrol": lambda: loop("453741-performance-car-idle", 14.0, 18.15, 0.15, 0.8),
    # The tyres of a Nissan Maxima in a handbrake turn: the squeal at its
    # loudest, 0.75 s, re-triggered every 12 ticks while the tail is out.
    "skid": lambda: assemble([take("71741-nissan-maxima-handbrake-turn", 6.15, 6.9, fade_in=0.02, fade_out=0.08)], 0.9),
    # A heavy body landing on dirt: the impact and its settle.
    "thud": lambda: assemble([take("504626-body-fall-heavy-dirt", 0.38, 1.5, fade_out=0.15)], 0.95),
    # A wrench struck against metal, once.
    "wrench": lambda: assemble([take("835173-wrench-impact", 0.0, fade_out=0.05)], 0.9),
    # Coal shovelled into a forge: what filling a tank sounds like when the
    # fuel is what the furnace burns.
    "fuel": lambda: assemble([take("386145-forge-adding-coal", 0.0, fade_out=0.1)], 0.85),
}


# The trailer: no engine, no seats, a tongue in front, two cows or four calves behind two doors.
BOX_TRAILER_PROFILE = {
    "mesh": "vanillawheels_gametest:box_trailer",
    "wheel_mesh": "vanillawheels_gametest:box_wheel",
    "texture": "vanillawheels_gametest:textures/entity/box_car.png",
    "scale": 0.0625,
    "handedness": "right",
    "body": {"width": 1.25, "length": 3.5, "height": 1.45,
             "parts": [{"at": [0, 6, 0], "width": 1.25, "height": 1.0}, {"at": [0, 6, 26], "width": 0.4, "height": 0.3}]},
    "seats": [],
    "wheels": {"radius": 6, "positions": [{"forward": -4, "right": -11}, {"forward": -4, "right": 11}]},
    "climb": 2.0,
    "mass": 0.8,
    "hitch": {"front": [0, 8, 34]},
    "cargo": {"adults": 2, "young": 4, "slots": [[-4, 8, 2], [4, 8, 2]]},
    "doors": [{"part": {"group": "door_left"}, "hinge": [10, 15, -20.5], "axis": [0, 1, 0], "open": -1.9},
              {"part": {"group": "door_right"}, "hinge": [-10, 15, -20.5], "axis": [0, 1, 0], "open": 1.9}],
    "paint": {"part": {"material": "body"}, "default": "white"},
}


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main(argv) -> None:
    want = set(argv[1:]) or {"icons", "boxcar", "sounds"}
    if "icons" in want:
        for name, draw in ICONS.items():
            write_png(ASSETS / f"textures/item/{name}.png", 16, 16, draw())
        write_png(ASSETS / "textures/block/mechanic_lift.png", 16, 16, lift_plate())
        write_png(ASSETS / "textures/block/mechanic_lift_deck.png", 16, 16, lift_deck())
        write_png(ASSETS / "textures/block/mechanic_lift_stripe.png", 16, 16, lift_stripe())
        write_png(ASSETS / "textures/gui/mechanic_lift.png", 256, 256, lift_gui())
        print("wrote the icons and the lift's textures")
    if "boxcar" in want:
        frame, wheel = box_car()
        mesh_dir = TEST_ASSETS / "vanillawheels/mesh"
        mesh_dir.mkdir(parents=True, exist_ok=True)
        (mesh_dir / "box_car.obj").write_text(frame, encoding="utf-8")
        (mesh_dir / "box_wheel.obj").write_text(wheel, encoding="utf-8")
        (mesh_dir / "box_trailer.obj").write_text(box_trailer(), encoding="utf-8")
        write_json(ROOT / "src/gametest/resources/data/vanillawheels_gametest/vanillawheels/vehicle/box_trailer.json", BOX_TRAILER_PROFILE)
        write_png(TEST_ASSETS / "textures/entity/box_car.png", 64, 64, atlas())
        write_json(ROOT / "src/gametest/resources/data/vanillawheels_gametest/vanillawheels/vehicle/box_car.json", BOX_CAR_PROFILE)
        write_json(TEST_ASSETS / "lang/en_us.json", {"vehicle.vanillawheels_gametest.box_car": "Box Car", "vehicle.vanillawheels_gametest.box_trailer": "Box Trailer"})
        print("wrote the box car")
    if "sounds" in want:
        for name, build in SOUNDS.items():
            write_ogg(ASSETS / f"sounds/{name}.ogg", build())
        print("wrote the sounds")


if __name__ == "__main__":
    main(sys.argv)
