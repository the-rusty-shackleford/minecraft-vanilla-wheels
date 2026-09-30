"""What D-0028 measured: Vehicle.footprintBlockedAt and footprintClamp modelled on flat ground, with the
wall rule swapped among four.

  bounds   1.10.0: a block is one box, its shape's bounds
  slice    D-0028: the rectangle bounding the boxes that span the climb line (domain/CrossSection)
  box      a box that spans the line holds the point
  column   the point's column (lowest to highest solid box over the point), for walls and ground

Shapes are the game's: fences (post and arms 6/16..10/16, 1.5 high), walls (post 4..12, arms 5..11),
an open garage door's cells (housing on the top row, side tracks on the edge columns). The box's own
collision is not modelled, so what stops a body here is the footprint alone.

Run: uv run --no-project python devtools/footprint/compare_rules.py  (a few minutes; seeds fixed)
"""

import math
import random

P = 1 / 16

def fence(n=False, s=False, w=False, e=False):
    b = [(6*P, 0, 6*P, 10*P, 1.5, 10*P)]
    if n: b.append((6*P, 0, 0, 10*P, 1.5, 10*P))
    if s: b.append((6*P, 0, 6*P, 10*P, 1.5, 1))
    if w: b.append((0, 0, 6*P, 10*P, 1.5, 10*P))
    if e: b.append((6*P, 0, 6*P, 1, 1.5, 10*P))
    return b

def door_cell(across, width, row, height, axis_x=True):
    """open door cell (panel rolled up): housing on the top row, tracks on the edge columns"""
    b = []
    if row == height - 1: b.append((0, .5, .2, 1, 1, .8))
    if across == 0: b.append((0, 0, .35, .1, 1, .65))
    if across == width - 1: b.append((.9, 0, .35, 1, 1, .65))
    if not axis_x:
        b = [(z0, y0, x0, z1, y1, x1) for (x0, y0, z0, x1, y1, z1) in b]
    return b

class World:
    def __init__(self):
        self.cells = {}
    def put(self, x, y, z, boxes):
        self.cells[(x, y, z)] = boxes
    def boxes(self, x, y, z):
        return self.cells.get((x, y, z), [])

def bounds(bs):
    return (min(b[0] for b in bs), min(b[1] for b in bs), min(b[2] for b in bs),
            max(b[3] for b in bs), max(b[4] for b in bs), max(b[5] for b in bs))

def blocked(world, rule, x, y, z, yaw_deg, width, length, climb, height):
    hw, hl = width / 2, length / 2
    yaw = math.radians(yaw_deg); c, s = math.cos(yaw), math.sin(yaw)
    climbl = max(0, climb) + .05
    for (px, pz) in [(-hw, hl), (hw, hl), (0, hl), (-hw, -hl), (hw, -hl), (0, -hl)]:
        dx = px * c - pz * s; dz = pz * c + px * s
        steps = max(1, math.ceil(math.hypot(dx, dz) / 1.0))
        ground = y
        for i in range(1, steps + 1):
            sx, sz = x + dx * i / steps, z + dz * i / steps
            line = ground + climbl
            bx, bz = math.floor(sx), math.floor(sz)
            top = -math.inf
            by = math.floor(line + height)
            while by >= math.floor(ground) - 2:
                bs = world.boxes(bx, by, bz)
                by -= 1
                if not bs: continue
                lx, lz = sx - bx, sz - bz
                ly = line - (by + 1)
                if rule == 'column':
                    col = [b for b in bs if b[0] <= lx <= b[3] and b[2] <= lz <= b[5]]
                    if not col: continue
                    lo, hi = min(b[1] for b in col), max(b[4] for b in col)
                    if hi > ly and lo <= ly: return True
                    if hi <= ly: top = max(top, hi + by + 1)
                    continue
                B = bounds(bs)
                if not (B[0] <= lx <= B[3] and B[2] <= lz <= B[5]): continue
                if B[4] > ly and B[1] <= ly:
                    if rule == 'bounds': return True
                    span = [b for b in bs if b[1] <= ly < b[4]]
                    if rule == 'slice' and span:
                        S = bounds(span)
                        if S[0] <= lx <= S[3] and S[2] <= lz <= S[5]: return True
                    if rule == 'box' and any(b[0] <= lx <= b[3] and b[2] <= lz <= b[5] for b in span): return True
                if B[4] <= ly: top = max(top, B[4] + by + 1)
            if top > -math.inf: ground = top
    return False

def clamp_along(f, x, z, dx, dz):
    if abs(dx) < 1e-7 and abs(dz) < 1e-7: return 0.0
    if not f(x + dx, z + dz): return 1.0
    lo, hi = 0.0, 1.0
    for _ in range(7):
        mid = (lo + hi) / 2
        if f(x + dx * mid, z + dz * mid): hi = mid
        else: lo = mid
    return max(0.0, lo - .02)

def drive(world, rule, x, z, yaw_deg, step, n, width, length, climb, height, y=0.0):
    f = lambda xx, zz: blocked(world, rule, xx, y, zz, yaw_deg, width, length, climb, height)
    yaw = math.radians(yaw_deg)
    dx, dz = -math.sin(yaw) * step, math.cos(yaw) * step
    for _ in range(n):
        if f(x, z):
            mx, mz = dx, dz   # starts inside: let through (backing out)
        elif not f(x + dx, z + dz):
            mx, mz = dx, dz
        else:
            t = clamp_along(f, x, z, dx, dz); tx = clamp_along(f, x, z, dx, 0); tz = clamp_along(f, x, z, 0, dz)
            along = t * math.hypot(dx, dz); xo = tx * abs(dx); zo = tz * abs(dz)
            if xo > along and xo >= zo: mx, mz = dx * tx, 0
            elif zo > along: mx, mz = 0, dz * tz
            else: mx, mz = dx * t, dz * t
        x += mx; z += mz
    return x, z

def overlap(world, x, z, yaw_deg, width, length, lo, hi, y=0.0):
    """deepest penetration (blocks) of the body's rectangle into any box spanning heights lo..hi over y, by sampling"""
    yaw = math.radians(yaw_deg); c, s = math.cos(yaw), math.sin(yaw)
    worst = 0.0; where = None
    for i in range(0, 61):
        for j in range(0, 181):
            px = -width / 2 + width * i / 60; pz = -length / 2 + length * j / 180
            wx = x + px * c - pz * s; wz = z + pz * c + px * s
            bx, bz = math.floor(wx), math.floor(wz)
            for by in range(math.floor(y + lo) - 1, math.floor(y + hi) + 1):
                for b in world.boxes(bx, by, bz):
                    if b[1] + by < y + hi and b[4] + by > y + lo:
                        lx, lz = wx - bx, wz - bz
                        if b[0] < lx < b[3] and b[2] < lz < b[5]:
                            d = min(lx - b[0], b[3] - lx, lz - b[2], b[5] - lz)
                            if d > worst: worst, where = d, (round(wx, 2), round(wz, 2), by)
    return worst, where


TRUCK = dict(width=1.84, length=4.64, climb=1.0, height=1.73)   # box_truck: the Trailblazer's body
CAR = dict(width=1.5, length=3.0, climb=2.0, height=1.6)        # box_car
RULES = ['bounds', 'slice', 'box', 'column']

def wall(n=False, s=False, w=False, e=False, post=True):
    b = []
    if post: b.append((4*P, 0, 4*P, 12*P, 1.5, 12*P))
    if n: b.append((5*P, 0, 0, 11*P, 1.5, 11*P))
    if s: b.append((5*P, 0, 5*P, 11*P, 1.5, 1))
    if w: b.append((0, 0, 5*P, 11*P, 1.5, 11*P))
    if e: b.append((5*P, 0, 5*P, 1, 1.5, 11*P))
    return b

def doors():
    """the box car and the truck driven +z through open doors, 60 moves of 0.2; the nose where each stops"""
    print('doors: nose z after 60 moves of 0.2 from z 5.5 (the door is the row z 10..11)')
    for name, v, w, h in [('box car, 3x3', CAR, 3, 3), ('box car, 3x4', CAR, 3, 4), ('truck, 3x3', TRUCK, 3, 3), ('truck, 4x4', TRUCK, 4, 4)]:
        W = World()
        for r in range(h):
            for a in range(w):
                W.put(a, r, 10, door_cell(a, w, r, h))
        print(' ', name, {r: round(drive(W, r, w / 2, 5.5, 0, .2, 60, **v)[1] + v['length'] / 2, 2) for r in RULES})

def layouts():
    """random short fence and wall lines, met from a random heading: where each rule stops, against 1.10.0"""
    random.seed(7)
    def layout(kind):
        cells = set()
        for _ in range(random.randint(1, 4)):
            x, z = random.randint(8, 12), random.randint(8, 12)
            dx, dz = random.choice([(1, 0), (0, 1), (-1, 0), (0, -1)])
            for k in range(random.randint(1, 4)):
                cells.add((x + dx * k, z + dz * k))
        W = World()
        for (x, z) in cells:
            n, s, w, e = (x, z - 1) in cells, (x, z + 1) in cells, (x - 1, z) in cells, (x + 1, z) in cells
            if kind == 'fence': W.put(x, 0, z, fence(n, s, w, e))
            else: W.put(x, 0, z, wall(n, s, w, e, post=not ((n and s and not w and not e) or (w and e and not n and not s))))
        return W
    for kind in ['fence', 'wall']:
        diff = {'slice': 0, 'box': 0, 'column': 0}; deeper = {'slice': 0, 'box': 0, 'column': 0}
        for t in range(400):
            W = layout(kind)
            yaw = random.uniform(-180, 180); a = math.radians(yaw)
            fx, fz = -math.sin(a), math.cos(a)
            tx, tz = random.uniform(8, 13), random.uniform(8, 13)
            x0, z0 = tx - 6 * fx, tz - 6 * fz
            res = {}
            for r in RULES:
                x, z = drive(W, r, x0, z0, yaw, .2, 45, **TRUCK)
                d = overlap(W, x, z, yaw, TRUCK['width'], TRUCK['length'], .05, TRUCK['height'])[0] if t % 4 == 0 else 0
                res[r] = (x, z, d)
            for r in ['slice', 'box', 'column']:
                if abs(res[r][0] - res['bounds'][0]) > 1e-9 or abs(res[r][1] - res['bounds'][1]) > 1e-9: diff[r] += 1
                if res[r][2] > res['bounds'][2] + 1e-6: deeper[r] += 1
        print(kind, '400 runs: stops unlike 1.10.0', diff, '| deeper in a box than 1.10.0 (of the 100 measured)', deeper)

def pieces():
    """one fence piece, its neighbours open gates (no collision), met at a heading from -150 to -30 degrees"""
    random.seed(11)
    found = []
    shapes = {'corner N+W': dict(n=True, w=True), 'corner N+E': dict(n=True, e=True), 'end W': dict(w=True), 'post': {}, 'T N+S+E': dict(n=True, s=True, e=True)}
    for name, arms in shapes.items():
        W = World()
        W.put(10, 0, 10, fence(**arms))
        for t in range(3000):
            yaw = random.choice([-90 + a for a in range(-60, 61, 5)]); a = math.radians(yaw)
            fx, fz = -math.sin(a), math.cos(a); sx, sz = math.cos(a), math.sin(a)
            off = random.uniform(-1.2, 1.2)
            x0 = 10.5 - 5 * fx + off * sx; z0 = 10.5 - 5 * fz + off * sz
            res = {}
            for r in ['bounds', 'box']:
                x, z = drive(W, r, x0, z0, yaw, .2, 40, **TRUCK)
                res[r] = overlap(W, x, z, yaw, TRUCK['width'], TRUCK['length'], .05, TRUCK['height'])[0]
            if res['box'] > res['bounds'] + 0.03:
                found.append((res['box'] - res['bounds'], name, yaw, res['bounds'], res['box']))
    found.sort(reverse=True)
    print('single fence pieces, 15000 approaches: per-box ended >0.03 deeper than 1.10.0 in', len(found),
          '| deepest: bounds %.3f, box %.3f (%s, heading %d)' % (found[0][3], found[0][4], found[0][1], found[0][2]) if found else '')

if __name__ == '__main__':
    doors()
    layouts()
    pieces()
