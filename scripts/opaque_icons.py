
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/client/resources/assets/selective_rendering/textures/gui"
threshold = int(sys.argv[1]) if len(sys.argv) > 1 else 0

for path in sorted(ROOT.rglob("*.png")):
    image = Image.open(path).convert("RGBA")
    pixels = image.load()
    changed = 0

    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if threshold < a < 255:
                pixels[x, y] = (r, g, b, 255)
                changed += 1

    image.save(path)
    print(f"{path.relative_to(ROOT)}: {changed} px")
