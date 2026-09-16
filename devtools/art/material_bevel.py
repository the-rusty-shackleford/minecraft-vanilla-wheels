"""Small deterministic material bevels on original authored inventory silhouettes.

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""

from collections.abc import Sequence
from typing import Literal

type Pixel = tuple[int, int, int, int]
type Raster = tuple[tuple[Pixel, ...], ...]


def bevel(
    pixels: Sequence[Sequence[Pixel]], material: Literal["steel", "wood"] = "steel"
) -> Raster:
    """requires: square 16-pixel RGBA artwork; no masks or atlas regions.

    effects: returns an immutable raster retaining every alpha and silhouette pixel,
    with a narrow upper-left highlight and lower-right shade. Existing authored ports,
    grain and rivets retain their relative colours. throws: ValueError for invalid input.
    """
    if len(pixels) != 16 or any(len(row) != 16 for row in pixels):
        raise ValueError("Inventory bevels require a 16 by 16 icon")
    if any(
        len(p) != 4 or any(not 0 <= c <= 255 for c in p) for row in pixels for p in row
    ):
        raise ValueError("Expected byte-valued RGBA pixels")

    def opaque(x: int, y: int) -> bool:
        return 0 <= x < 16 and 0 <= y < 16 and pixels[y][x][3] != 0

    rows: list[tuple[Pixel, ...]] = []
    for y, row in enumerate(pixels):
        result: list[Pixel] = []
        for x, (r, g, b, a) in enumerate(row):
            if a == 0:
                result.append((r, g, b, a))
                continue
            # Keep the dark edge, lighting the inward bevel rather than tracing
            # every edge white. Broad faces have two quiet, deliberate planes.
            delta = 0
            if opaque(x, y - 1) and not opaque(x, y - 2):
                delta = 22
            elif opaque(x - 1, y) and not opaque(x - 2, y):
                delta = 12
            elif not opaque(x + 1, y) or not opaque(x, y + 1):
                delta = -12
            elif r + g + b > 160:
                delta = 7 if y < 8 else -4
            red = max(0, min(255, r + delta))
            green = max(
                0, min(255, g + (delta * 3 // 4 if material == "wood" else delta))
            )
            blue = max(0, min(255, b + (delta // 2 if material == "wood" else delta)))
            result.append((red, green, blue, a))
        rows.append(tuple(result))
    return tuple(rows)
