"""Original empty-slot glyphs; immutable pixels, drawn at Minecraft's native density.

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""

type Pixel = tuple[int, int, int, int]
type Raster = tuple[tuple[Pixel, ...], ...]

# Each glyph occupies a centred 12 x 12 field inside the sixteen-pixel slot.
PATTERNS: dict[str, tuple[str, ...]] = {
    "chassis": (
        "............",
        "..#......#..",
        "..#+....#+..",
        "..########..",
        "..#+....#+..",
        "..#+....#+..",
        "..#+....#+..",
        "..########..",
        "..#+....#+..",
        "..#......#..",
        "............",
        "............",
    ),
    "wheels": (
        "....####....",
        "..##++++##..",
        ".#++....++#.",
        ".#+..##..+#.",
        "#+..#++#..+#",
        "#+.#++++#.#+",
        "#+.#++++#.#+",
        "#+..#++#..+#",
        ".#+..##..+#.",
        ".#++....++#.",
        "..##++++##..",
        "....####....",
    ),
    "engine": (
        "............",
        "...#.#.#....",
        "...#.#.#....",
        ".#########..",
        ".#+++++++#..",
        ".#+##++#+#..",
        ".#+##++#+###",
        ".#+++++++#++",
        ".#########..",
        "..#.....#...",
        "............",
        "............",
    ),
    "dye": (
        ".....#......",
        "....#+#.....",
        "....#+#.....",
        "...#+++#....",
        "...#+++#....",
        "..#+++++#...",
        ".#+++++++#..",
        ".#++#++++#..",
        ".#+++##++#..",
        "..#+++++#...",
        "...#####....",
        "............",
    ),
}


def icons() -> dict[str, Raster]:
    """effects: returns four immutable 16-square RGBA glyphs.

    throws: ValueError if an authored row is malformed; no external inputs or I/O.
    """
    palette: dict[str, Pixel] = {
        ".": (0, 0, 0, 0),
        "#": (99, 99, 99, 255),
        "+": (169, 169, 169, 255),
    }
    result: dict[str, Raster] = {}
    for name, rows in PATTERNS.items():
        if len(rows) != 12 or any(
            len(row) != 12 or any(c not in palette for c in row) for row in rows
        ):
            raise ValueError(f"Invalid slot glyph: {name}")
        blank = "." * 16
        result[name] = tuple(
            tuple(palette[c] for c in row)
            for row in (
                blank,
                blank,
                *(".." + row + ".." for row in rows),
                blank,
                blank,
            )
        )
    return result
