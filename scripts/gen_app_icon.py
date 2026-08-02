"""Generate the LiteLink app icon (with a background tile) + .ico file.

Composites the logo glyph (recoloured to the light-blue accent) onto a dark
ink tile so it's visible on the Windows taskbar and title bar. Writes:
  - desktop/assets/litelink-icon.png    (500x500 source)
  - desktop/assets/litelink.ico         (16..256 multi-size for taskbar/exe)
Run: python scripts/gen_app_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent.parent / "desktop" / "assets"
LOGO = ASSETS / "litelink-logo-transparent.png"
ICON_PNG = ASSETS / "litelink-icon.png"
ICON_ICO = ASSETS / "litelink.ico"

TILE = (38, 50, 56)          # app ink #263238
TILE_HI = (18, 24, 28)       # subtle darker edge for depth
GLYPH_TINT = (0, 150, 136)   # teal #009688
SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def _tint_logo(logo):
    """Recolour the logo glyph to the light-blue accent."""
    r, g, b, a = logo.split()
    tinted = Image.new("RGBA", logo.size, GLYPH_TINT + (255,))
    tinted.putalpha(a)
    return tinted


def rounded_tile(size, radius_ratio=0.22):
    """A dark rounded-square tile (with a slightly darker border)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = int(size * radius_ratio)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=TILE)
    d.rounded_rectangle([2, 2, size - 3, size - 3], radius=max(1, r - 2),
                        outline=TILE_HI, width=1)
    return img


def build_icon_png():
    logo = Image.open(LOGO).convert("RGBA")
    logo = _tint_logo(logo)
    pad = 0.12
    content = logo.resize(
        (int(500 * (1 - 2 * pad)), int(500 * (1 - 2 * pad))), Image.LANCZOS)
    tile = rounded_tile(500)
    x = (500 - content.width) // 2
    y = (500 - content.height) // 2
    tile.alpha_composite(content, (x, y))
    tile.save(ICON_PNG)
    return tile


def build_ico(tile):
    frames = [tile.resize(s, Image.LANCZOS) for s in SIZES]
    frames[-1].save(ICON_ICO, format="ICO", sizes=SIZES)


def main():
    tile = build_icon_png()
    build_ico(tile)
    print(f"Wrote {ICON_PNG} and {ICON_ICO} ({len(SIZES)} sizes)")


if __name__ == "__main__":
    main()
