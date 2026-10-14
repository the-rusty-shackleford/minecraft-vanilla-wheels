"""The keyring's icon (D-0041): a 16 x 16 pixel ring of steel with two key fobs hanging from it,
one banded red and one light blue, in the key fob's palette. Flat, so it draws the same in a slot,
in the hand and on the ground.

    uv run --no-project --with pillow python devtools/art/keyring.py

Writes src/main/resources/assets/vanillawheels/textures/item/keyring.png.
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/vanillawheels/textures/item/keyring.png"

STEEL = (205, 207, 210, 255)
STEEL_DARK = (140, 143, 150, 255)
STEEL_LIGHT = (236, 238, 240, 255)
PLASTIC = (28, 28, 30, 255)
PLASTIC_LIGHT = (62, 62, 66, 255)
RED = (176, 46, 38, 255)
BLUE = (58, 175, 217, 255)
BUTTON = (190, 40, 40, 255)

def main() -> None:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    # The ring: a circle of radius 5 about (8, 6), two pixels thick, lit from the upper left.
    cx, cy = 7.5, 5.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 3.6 <= d <= 5.4:
                px[x, y] = STEEL_LIGHT if (x + y) < 11 else STEEL if (x + y) < 15 else STEEL_DARK
    # The opening where the fobs hang: the ring's bottom pixels under the two tags are the tags' loops.
    # Two fobs hanging from the lower half of the ring.
    def fob(x0: int, y0: int, band: tuple) -> None:
        for y in range(y0, y0 + 6):
            for x in range(x0, x0 + 3):
                px[x, y] = PLASTIC
        px[x0 + 1, y0 + 1] = PLASTIC_LIGHT
        px[x0 + 1, y0 + 3] = BUTTON
        for x in range(x0 - 0, x0 + 3):
            px[x, y0] = band
        # the loop into the ring
        px[x0 + 1, y0 - 1] = STEEL_DARK
    fob(3, 9, RED)
    fob(10, 10, BLUE)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    im.save(OUT)
    print("wrote", OUT)

if __name__ == "__main__":
    main()
