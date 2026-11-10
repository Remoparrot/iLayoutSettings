"""Generate launcher icon candidates for iLayoutSettings.

Renders at 4x and downsamples, so the 48 px mdpi asset stays smooth.
Candidates:
  A  liquid-glass: blue/violet gradient, frosted glass plate, white gear
  B  line art: warm white plate, thin ink gear, single blue accent
  C  sliders: soft graphite plate, iOS-style glass sliders
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "dbg", "icons")
os.makedirs(OUT, exist_ok=True)

S = 1024  # working size
SS = 4    # supersample


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, top, bottom, diagonal=True):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * size) if diagonal else y / size
            px[x, y] = lerp(top, bottom, t)
    return img


def gear_points(cx, cy, teeth, outer, inner, tooth_w=0.52, hole=0.34):
    """Polygon for a gear: alternating outer (tooth) and inner (gap) arcs."""
    pts = []
    step = 2 * math.pi / teeth
    for i in range(teeth):
        base = i * step
        half = step * tooth_w / 2
        # tooth: flat-ish outer arc
        for k in range(5):
            a = base - half + (2 * half) * k / 4
            pts.append((cx + outer * math.cos(a), cy + outer * math.sin(a)))
        # gap: inner arc
        gap_half = step * (1 - tooth_w) / 2
        for k in range(5):
            a = base + half + (2 * gap_half) * k / 4
            pts.append((cx + inner * math.cos(a), cy + inner * math.sin(a)))
    return pts, hole


def draw_gear(draw, cx, cy, radius, color, teeth=8, tooth_w=0.42, hole=0.38):
    outer = radius
    inner = radius * 0.79
    pts, _ = gear_points(cx, cy, teeth, outer, inner, tooth_w=tooth_w)
    draw.polygon(pts, fill=color)
    r = radius * hole
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(0, 0, 0, 0))


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def shadow_layer(size, draw_fn, blur, alpha):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    a = layer.split()[3].point(lambda v: v * alpha // 255)
    layer.putalpha(a)
    return layer


# ---------------------------------------------------------------- candidate A
def candidate_a():
    size = S * SS
    base = gradient(size, (61, 90, 254), (124, 77, 255)).convert("RGBA")

    # soft top-left light
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-size * 0.35, -size * 0.45, size * 0.85, size * 0.75),
               fill=(255, 255, 255, 92))
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.06))
    base = Image.alpha_composite(base, glow)

    # frosted plate (uniform tint + rim; the base already carries the light)
    inset = int(size * 0.135)
    plate = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pd = ImageDraw.Draw(plate)
    pd.rounded_rectangle((inset, inset, size - inset, size - inset),
                         radius=int(size * 0.155), fill=(255, 255, 255, 30))
    pd.rounded_rectangle((inset, inset, size - inset, size - inset),
                         radius=int(size * 0.155), outline=(255, 255, 255, 120),
                         width=max(2, size // 340))
    base = Image.alpha_composite(base, plate)

    # gear shadow + gear
    cx = cy = size // 2
    r = size * 0.225
    sh = shadow_layer(size, lambda d: d.ellipse(
        (cx - r * 1.02, cy - r * 0.95, cx + r * 1.02, cy + r * 1.12),
        fill=(24, 30, 90, 170)), size * 0.014, 210)
    base = Image.alpha_composite(base, sh)

    gear = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdr = ImageDraw.Draw(gear)
    draw_gear(gdr, cx, cy, r, (255, 255, 255, 248))
    base = Image.alpha_composite(base, gear)
    return base


# ---------------------------------------------------------------- candidate B
def candidate_b():
    size = S * SS
    base = Image.new("RGBA", (size, size), (246, 244, 238, 255))
    # paper grain: a few very soft warm blobs
    warm = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    wd = ImageDraw.Draw(warm)
    wd.ellipse((-size * 0.2, -size * 0.3, size * 0.7, size * 0.5),
               fill=(255, 236, 200, 120))
    wd.ellipse((size * 0.55, size * 0.6, size * 1.15, size * 1.1),
               fill=(214, 226, 255, 90))
    warm = warm.filter(ImageFilter.GaussianBlur(size * 0.08))
    base = Image.alpha_composite(base, warm)

    # thin ink ring around the gear
    cx = cy = size // 2
    r = size * 0.245
    ink = (24, 24, 27, 255)
    ring = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring)
    draw_gear(rd, cx, cy, r, ink)
    hole = int(r * 0.34)
    rd.ellipse((cx - hole, cy - hole, cx + hole, cy + hole), fill=(0, 0, 0, 0))
    rd.ellipse((cx - hole, cy - hole, cx + hole, cy + hole),
               outline=ink, width=max(2, size // 260))
    base = Image.alpha_composite(base, ring)

    # blue accent dot bottom-right, clipped by the plate
    dot = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dd = ImageDraw.Draw(dot)
    dr = size * 0.052
    dx, dy = size * 0.735, size * 0.735
    dd.ellipse((dx - dr, dy - dr, dx + dr, dy + dr), fill=(10, 132, 255, 255))
    base = Image.alpha_composite(base, dot)
    return base


# ---------------------------------------------------------------- candidate C
def candidate_c():
    size = S * SS
    base = gradient(size, (44, 46, 58), (18, 19, 26)).convert("RGBA")
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-size * 0.4, -size * 0.5, size * 0.8, size * 0.6),
               fill=(120, 150, 255, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.09))
    base = Image.alpha_composite(base, glow)

    # glass track + three slider rows
    left = size * 0.22
    right = size * 0.78
    rows = [0.36, 0.50, 0.64]
    knobs = [0.36, 0.62, 0.48]
    colors = [(10, 132, 255), (52, 199, 89), (255, 159, 10)]

    for y, kx, col in zip(rows, knobs, colors):
        cy = size * y
        sh = shadow_layer(size, lambda d, cy=cy: d.rounded_rectangle(
            (left, cy - size * 0.012, right, cy + size * 0.012),
            radius=size * 0.012, fill=(0, 0, 0, 120)), size * 0.008, 220)
        base = Image.alpha_composite(base, sh)
        tr = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        td = ImageDraw.Draw(tr)
        td.rounded_rectangle((left, cy - size * 0.012, right, cy + size * 0.012),
                             radius=size * 0.012, fill=(255, 255, 255, 46),
                             outline=(255, 255, 255, 70), width=max(1, size // 420))
        fill_w = left + (right - left) * kx
        td.rounded_rectangle((left, cy - size * 0.012, fill_w, cy + size * 0.012),
                             radius=size * 0.012, fill=col + (235,))
        base = Image.alpha_composite(base, tr)

        knob = size * 0.042
        kxc = left + (right - left) * kx
        ksh = shadow_layer(size, lambda d, kxc=kxc, cy=cy: d.ellipse(
            (kxc - knob, cy - knob + size * 0.008, kxc + knob, cy + knob + size * 0.008),
            fill=(0, 0, 0, 140)), size * 0.006, 230)
        base = Image.alpha_composite(base, ksh)
        k = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        kd = ImageDraw.Draw(k)
        kd.ellipse((kxc - knob, cy - knob, kxc + knob, cy + knob),
                   fill=(255, 255, 255, 255))
        kd.ellipse((kxc - knob, cy - knob, kxc + knob, cy + knob),
                   outline=(0, 0, 0, 40), width=max(1, size // 500))
        base = Image.alpha_composite(base, k)
    return base


def finish(img, radius_ratio=0.225):
    """Downsample and add the transparency outside the squircle (legacy mask)."""
    small = img.resize((512, 512), Image.LANCZOS)
    mask = rounded_mask(512, int(512 * radius_ratio))
    small.putalpha(mask)
    return small


def contact_sheet(cands):
    cell = 300
    gap = 24
    w = cell * len(cands) + gap * (len(cands) + 1)
    sheet = Image.new("RGB", (w, cell + gap * 2), (245, 245, 247))
    for i, im in enumerate(cands):
        im = im.resize((cell, cell), Image.LANCZOS)
        x = gap + i * (cell + gap)
        sheet.paste(im, (x, gap), im)
    sheet.save(os.path.join(OUT, "candidates.png"))


if __name__ == "__main__":
    a = finish(candidate_a())
    b = finish(candidate_b())
    c = finish(candidate_c())
    for name, im in (("a", a), ("b", b), ("c", c)):
        im.save(os.path.join(OUT, "icon_%s_512.png" % name))
    contact_sheet([a, b, c])

    # res/mipmap-*/ic_launcher.png 鈥?standard launcher densities.
    res = os.path.join(os.path.dirname(__file__), "..", "module", "res")
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        folder = os.path.join(res, "mipmap-%s" % density)
        os.makedirs(folder, exist_ok=True)
        a.resize((px, px), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher.png"), optimize=True)
    print("written", OUT, "and mipmap-* densities")
