"""Draw the inventory sprites and status effect icon at their native pixel sizes."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/theartofstockings/textures"
ITEMS = TEXTURES / "item"
ITEMS.mkdir(parents=True, exist_ok=True)


def draw_stocking(offset, fishnet=False, rear=False):
    sprite = Image.new("L", (32, 32))
    draw = ImageDraw.Draw(sprite)

    def polygon(points, shade):
        draw.polygon([(x + offset[0], y + offset[1]) for x, y in points], fill=shade)

    polygon([(7, 1), (17, 1), (19, 3), (16, 15), (15, 20), (15, 24),
             (12, 26), (3, 26), (1, 24), (2, 21), (6, 20), (9, 17), (8, 9)], 60)
    polygon([(8, 3), (17, 3), (16, 10), (14, 18), (14, 23), (12, 24),
             (3, 24), (3, 22), (7, 21), (10, 17), (9, 10)], 155)
    polygon([(9, 4), (15, 4), (14, 11), (12, 19), (10, 21), (6, 22),
             (8, 21), (11, 17), (10, 10)], 212)
    polygon([(10, 8), (12, 8), (12, 13), (11, 16), (11, 12)], 241)
    polygon([(8, 2), (17, 2), (18, 3), (17, 5), (9, 5)], 193)
    polygon([(9, 2), (16, 2), (16, 3), (9, 3)], 255)
    polygon([(3, 22), (5, 22), (5, 23), (3, 23)], 232)

    alpha = sprite.point(lambda pixel: 255 if pixel else 0)
    if fishnet:
        interior = alpha.filter(ImageFilter.MinFilter(3))
        for y in range(7, 21):
            for x in range(7, 20):
                px, py = x + offset[0], y + offset[1]
                if not interior.getpixel((px, py)):
                    continue
                if (x + y) % 6 > 1 and (x - y) % 6 > 1:
                    alpha.putpixel((px, py), 0)
                else:
                    sprite.putpixel((px, py), 241)

    if rear:
        sprite = sprite.point(lambda pixel: round(pixel * 0.77))
    return Image.merge("RGBA", (sprite, sprite, sprite, alpha))


classic = Image.alpha_composite(draw_stocking((0, 0), rear=True), draw_stocking((10, 3)))
classic.save(ITEMS / "stockings.png")
classic.save(ITEMS / "glow_stockings.png")

fishnet = Image.alpha_composite(draw_stocking((0, 0), fishnet=True, rear=True),
                              draw_stocking((10, 3), fishnet=True))
fishnet.save(ITEMS / "fishnet_stockings.png")

classic.save(ITEMS / "gradient_stockings.png")
bottom = classic.copy()
for y in range(32):
    amount = max(0, min(1, (y - 7) / 19))
    for x in range(32):
        r, g, b, alpha = bottom.getpixel((x, y))
        bottom.putpixel((x, y), (r, g, b, round(alpha * amount)))
bottom.save(ITEMS / "gradient_stockings_bottom.png")

tights = Image.new("RGBA", (32, 32))
draw = ImageDraw.Draw(tights)
draw.polygon([(6, 2), (25, 2), (26, 5), (23, 14), (22, 25), (24, 26), (24, 29),
              (17, 29), (16, 27), (16, 14), (15, 14), (14, 27), (13, 29), (6, 29),
              (6, 26), (8, 24), (8, 15), (5, 6)], fill="#3B3B3B")
draw.polygon([(7, 4), (24, 4), (24, 7), (21, 15), (21, 26), (23, 27), (23, 28),
              (18, 28), (17, 26), (17, 13), (16, 11), (14, 13), (12, 27), (11, 28),
              (7, 28), (7, 27), (9, 25), (10, 14), (7, 7)], fill="#A6A6A6")
draw.polygon([(8, 6), (13, 6), (14, 11), (12, 15), (11, 24), (10, 26), (10, 13)], fill="#E7E7E7")
draw.polygon([(18, 6), (23, 6), (21, 11), (19, 14), (19, 24), (18, 26), (18, 12)], fill="#D0D0D0")
draw.rectangle((7, 3, 24, 5), fill="#DCDCDC")
draw.line((8, 3, 23, 3), fill="#FFFFFF")
tights.save(ITEMS / "pantyhose.png")

sparkle = Image.new("RGBA", (32, 32))
draw = ImageDraw.Draw(sparkle)
draw.polygon([(26, 10), (27, 13), (30, 14), (27, 15), (26, 18), (25, 15),
              (22, 14), (25, 13)], fill="#63DACC")
draw.rectangle((25, 13, 27, 15), fill="#F0FFF7")
draw.point((29, 22), fill="#B2FAE4")
draw.point((20, 1), fill="#B2FAE4")
sparkle.save(ITEMS / "glow_stockings_sparkle.png")

palette = Image.new("RGBA", (32, 32))
draw = ImageDraw.Draw(palette)
draw.polygon([(5, 9), (11, 5), (18, 4), (25, 6), (29, 11), (29, 16), (26, 19),
              (22, 18), (20, 20), (21, 24), (17, 28), (10, 28), (4, 24), (2, 18), (2, 14)],
             fill="#513045")
draw.polygon([(6, 10), (12, 6), (18, 6), (24, 7), (27, 11), (27, 15), (25, 17),
              (21, 16), (18, 19), (19, 23), (16, 26), (10, 26), (5, 23), (4, 18), (4, 14)],
             fill="#C48A64")
draw.line([(5, 15), (7, 11), (13, 8), (19, 7), (24, 9)], fill="#F4C38D", width=2)
for x, y, color, highlight in [
    (7, 12, "#D65886", "#FF9BAF"), (13, 9, "#D99948", "#FFE295"),
    (20, 9, "#43AA99", "#A1EACC"), (6, 19, "#6579CC", "#ACB7FF"),
    (12, 21, "#935DB5", "#DCACEE"),
]:
    draw.rectangle((x, y, x + 4, y + 3), fill=color)
    draw.line((x + 1, y, x + 3, y), fill=highlight)
palette.save(ITEMS / "palette.png")

footbath = Image.new("RGBA", (32, 32))
draw = ImageDraw.Draw(footbath)
draw.polygon([(12, 5), (19, 5), (20, 10), (23, 13), (25, 17), (25, 24),
              (22, 28), (10, 28), (7, 25), (7, 17), (9, 13), (12, 10)], fill="#303B37")
draw.polygon([(13, 7), (18, 7), (18, 11), (22, 14), (23, 18), (23, 24),
              (21, 26), (11, 26), (9, 24), (9, 17), (11, 14), (13, 11)], fill="#BDCCC0")
draw.polygon([(9, 18), (12, 17), (17, 18), (21, 16), (23, 17),
              (23, 24), (21, 26), (11, 26), (9, 24)], fill="#727238")
draw.polygon([(10, 19), (14, 18), (18, 19), (22, 17), (22, 21), (20, 22), (12, 22), (10, 23)],
             fill="#A5A450")
draw.line([(13, 12), (11, 15), (11, 21)], fill="#EEF4CD", width=2)
draw.rectangle((11, 3, 20, 7), fill="#573F34")
draw.rectangle((12, 4, 19, 5), fill="#C18B63")
draw.point((16, 21), fill="#DEDB7B")
draw.point((20, 23), fill="#DEDB7B")
draw.point((13, 24), fill="#474B2E")
draw.line([(26, 15), (27, 13), (25, 11), (25, 9), (27, 7)], fill="#91AC58", width=2)
footbath.save(ITEMS / "footbath_water.png")

effects = TEXTURES / "mob_effect"
effects.mkdir(exist_ok=True)
odor = Image.new("RGBA", (18, 18))
draw = ImageDraw.Draw(odor)
draw.polygon([(4, 6), (10, 6), (10, 12), (8, 16), (2, 16), (1, 14), (2, 12), (4, 12)],
             fill="#353C26")
draw.polygon([(5, 7), (9, 7), (9, 12), (7, 15), (2, 15), (2, 14), (5, 13)],
             fill="#8A9852")
draw.line((5, 7, 9, 7), fill="#D0D38B")
draw.point((3, 14), fill="#D0D38B")
draw.line([(12, 12), (13, 10), (12, 8), (12, 6), (14, 4), (13, 2)], fill="#9FBE4D", width=2)
draw.line([(16, 10), (15, 8), (16, 6), (16, 4)], fill="#D7D87C")
odor.save(effects / "foot_odor.png")
