#!/usr/bin/env python3
"""Generate iOS-style rounded-square icons for second-level Settings rows."""
import math
import os

from PIL import Image, ImageDraw

S = 4                     # supersample
SIZE = 84                 # px at xxhdpi -> 28dp intrinsic (matches miuix row icon)
W = SIZE * S
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "module", "res", "drawable-xxhdpi")
os.makedirs(OUT, exist_ok=True)


def shade(hex_color, k):
    hex_color = hex_color.lstrip("#")
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (0, 2, 4))
    return (int(r * k), int(g * k), int(b * k))


def base(color):
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # vertical gradient
    grad = Image.new("RGBA", (W, W))
    d = ImageDraw.Draw(grad)
    top = shade(color, 1.08)
    bottom = shade(color, 0.86)
    for y in range(W):
        t = y / (W - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c + (255,))
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1],
                                           radius=int(W * 0.235), fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def gd(img):
    return ImageDraw.Draw(img)


WHITE = (255, 255, 255, 255)
LW = int(S * 9)
BOX = (int(W * 0.24), int(W * 0.24), int(W * 0.76), int(W * 0.76))


def stroke(d, shape, width=LW, fill=WHITE):
    d.line(shape, fill=fill, width=width, joint="curve")
    return d


def icon_clock(d):
    c = W // 2
    r = int(W * 0.25)
    d.ellipse([c - r, c - r, c + r, c + r], outline=WHITE, width=LW)
    stroke(d, [(c, c - r + LW, ), (c, c), (c + r * 0.55, c + r * 0.45)])


def icon_keyboard(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.32), int(W * 0.78), int(W * 0.68)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.06), outline=WHITE, width=LW)
    for i in range(3):
        for j in range(2):
            cx = x0 + int((x1 - x0) * (0.20 + 0.30 * i))
            cy = y0 + int((y1 - y0) * (0.32 + 0.34 * j))
            rr = int(W * 0.028)
            d.rounded_rectangle([cx - rr * 2, cy - rr, cx + rr * 2, cy + rr],
                                radius=rr // 2, fill=WHITE)
    d.rounded_rectangle([int(W * 0.36), int(W * 0.60), int(W * 0.64), int(W * 0.645)],
                        radius=int(W * 0.02), fill=WHITE)


def icon_globe(d):
    c = W // 2
    r = int(W * 0.25)
    d.ellipse([c - r, c - r, c + r, c + r], outline=WHITE, width=LW)
    d.ellipse([c - r // 2, c - r, c + r // 2, c + r], outline=WHITE, width=LW)
    stroke(d, [(c - r, c), (c + r, c)])


def icon_magnifier(d):
    c = int(W * 0.45)
    r = int(W * 0.19)
    d.ellipse([c - r, c - r, c + r, c + r], outline=WHITE, width=LW)
    stroke(d, [(c + r * 0.72, c + r * 0.72), (int(W * 0.78), int(W * 0.78))])


def icon_light(d):
    c = W // 2
    r = int(W * 0.17)
    d.ellipse([c - r, int(W * 0.26), c + r, int(W * 0.26) + 2 * r], outline=WHITE, width=LW)
    stroke(d, [(c - int(W * 0.09), int(W * 0.62)), (c + int(W * 0.09), int(W * 0.62))])
    stroke(d, [(c - int(W * 0.07), int(W * 0.70)), (c + int(W * 0.07), int(W * 0.70))])


def icon_game(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.36), int(W * 0.78), int(W * 0.68)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.14), outline=WHITE, width=LW)
    cx = int(W * 0.38)
    cy = W // 2
    arm = int(W * 0.055)
    stroke(d, [(cx - arm, cy), (cx + arm, cy)])
    stroke(d, [(cx, cy - arm), (cx, cy + arm)])
    rr = LW // 2
    d.ellipse([int(W * 0.62) - rr, cy - int(W * 0.04) - rr,
               int(W * 0.62) + rr, cy - int(W * 0.04) + rr], fill=WHITE)
    d.ellipse([int(W * 0.66) - rr, cy + int(W * 0.04) - rr,
               int(W * 0.66) + rr, cy + int(W * 0.04) + rr], fill=WHITE)


def icon_camera(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.34), int(W * 0.78), int(W * 0.68)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.08), outline=WHITE, width=LW)
    r = int(W * 0.11)
    c = (W // 2, int(W * 0.51))
    d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=WHITE, width=LW)


def icon_chat(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.28), int(W * 0.78), int(W * 0.60)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.10), outline=WHITE, width=LW)
    stroke(d, [(int(W * 0.36), y1), (int(W * 0.34), int(W * 0.72)), (int(W * 0.50), y1)])


def icon_sidebar(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.28), int(W * 0.78), int(W * 0.72)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.06), outline=WHITE, width=LW)
    stroke(d, [(int(W * 0.44), y0 + LW), (int(W * 0.44), y1 - LW)])
    rr = LW
    d.rounded_rectangle([int(W * 0.29), int(W * 0.40), int(W * 0.37), int(W * 0.46)],
                        radius=rr, fill=WHITE)


def icon_portal(d):
    x0, x1 = int(W * 0.30), int(W * 0.70)
    top, bottom = int(W * 0.28), int(W * 0.72)
    d.rounded_rectangle([x0, top, x1, bottom], radius=int(W * 0.18),
                        outline=WHITE, width=LW)
    d.rectangle([x0 + LW, int(W * 0.52), x1 - LW, bottom + LW], fill=None)
    # doorway opening
    d.rounded_rectangle([x0 + int(W * 0.10), int(W * 0.46),
                         x1 - int(W * 0.10), bottom], radius=int(W * 0.08),
                        outline=WHITE, width=LW)
    stroke(d, [(W // 2, int(W * 0.30)), (W // 2, int(W * 0.40))], width=LW // 2)


def icon_clipboard(d):
    x0, y0, x1, y1 = int(W * 0.28), int(W * 0.28), int(W * 0.72), int(W * 0.76)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.06), outline=WHITE, width=LW)
    d.rounded_rectangle([W // 2 - int(W * 0.09), int(W * 0.24),
                         W // 2 + int(W * 0.09), int(W * 0.33)],
                        radius=int(W * 0.03), fill=WHITE)


def icon_window(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.30), int(W * 0.66), int(W * 0.66)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.05), outline=WHITE, width=LW)
    x2, y2 = int(W * 0.36), int(W * 0.42)
    x3, y3 = int(W * 0.78), int(W * 0.74)
    d.rounded_rectangle([x2, y2, x3, y3], radius=int(W * 0.05), outline=WHITE, width=LW)


def icon_wallet(d):
    x0, y0, x1, y1 = int(W * 0.20), int(W * 0.34), int(W * 0.80), int(W * 0.70)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.09), outline=WHITE, width=LW)
    stroke(d, [(x0 + LW, int(W * 0.44)), (x1 - LW, int(W * 0.44))], width=LW // 2)
    # clasp
    d.rounded_rectangle([int(W * 0.62), int(W * 0.50), int(W * 0.80), int(W * 0.60)],
                        radius=int(W * 0.03), outline=WHITE, width=LW // 2)


def icon_record(d):
    r = int(W * 0.24)
    c = W // 2
    d.ellipse([c - r, c - r, c + r, c + r], outline=WHITE, width=LW)
    r2 = int(W * 0.10)
    d.ellipse([c - r2, c - r2, c + r2, c + r2], fill=WHITE)


def icon_desktop(d):
    x0, y0, x1, y1 = int(W * 0.22), int(W * 0.28), int(W * 0.78), int(W * 0.62)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.05), outline=WHITE, width=LW)
    stroke(d, [(W // 2, y1), (W // 2, int(W * 0.73))])
    stroke(d, [(int(W * 0.36), int(W * 0.73)), (int(W * 0.64), int(W * 0.73))])


def icon_memory(d):
    x0, y0, x1, y1 = int(W * 0.30), int(W * 0.30), int(W * 0.70), int(W * 0.70)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.05), outline=WHITE, width=LW)
    d.rounded_rectangle([W // 2 - int(W * 0.06), W // 2 - int(W * 0.06),
                         W // 2 + int(W * 0.06), W // 2 + int(W * 0.06)],
                        outline=WHITE, width=LW)
    for t in (0.40, 0.50, 0.60):
        p = int(W * t)
        stroke(d, [(x0 - int(W * 0.06), p), (x0, p)], width=LW // 2)
        stroke(d, [(x1, p), (x1 + int(W * 0.06), p)], width=LW // 2)
    for t in (0.40, 0.60):
        p = int(W * t)
        stroke(d, [(p, y0 - int(W * 0.06)), (p, y0)], width=LW // 2)
        stroke(d, [(p, y1), (p, y1 + int(W * 0.06))], width=LW // 2)


def person(d, cx, cy, r, fill=True):
    if fill:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
        d.pieslice([cx - int(r * 1.7), cy + int(r * 0.6),
                    cx + int(r * 1.7), cy + int(r * 3.4)], 180, 360, fill=WHITE)
    else:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=WHITE, width=LW // 2)
        d.arc([cx - int(r * 1.7), cy + int(r * 0.6),
               cx + int(r * 1.7), cy + int(r * 3.4)], 180, 360, fill=WHITE, width=LW // 2)


def icon_person(d):
    person(d, W // 2, int(W * 0.40), int(W * 0.10))


def icon_parent(d):
    person(d, int(W * 0.40), int(W * 0.38), int(W * 0.09))
    person(d, int(W * 0.62), int(W * 0.47), int(W * 0.07))


def icon_sync(d):
    c = W // 2
    r = int(W * 0.21)
    d.arc([c - r, c - r, c + r, c + r], 200, 340, fill=WHITE, width=LW)
    d.arc([c - r, c - r, c + r, c + r], 20, 160, fill=WHITE, width=LW)
    # arrow heads
    d.polygon([(c + r - int(W * 0.015), c - int(W * 0.10)),
               (c + r + int(W * 0.075), c - int(W * 0.015)),
               (c + r - int(W * 0.03), c + int(W * 0.075))], fill=WHITE)
    d.polygon([(c - r + int(W * 0.015), c + int(W * 0.10)),
               (c - r - int(W * 0.075), c + int(W * 0.015)),
               (c - r + int(W * 0.03), c - int(W * 0.075))], fill=WHITE)


def icon_ai(d):
    c = W // 2
    for dx, dy, s in ((0, 0, 0.26), (-0.15, 0.15, 0.12), (0.16, -0.14, 0.10)):
        cx, cy = c + int(W * dx), c + int(W * dy)
        r = int(W * s)
        d.polygon([(cx, cy - r), (cx + r * 0.28, cy - r * 0.28), (cx + r, cy),
                   (cx + r * 0.28, cy + r * 0.28), (cx, cy + r),
                   (cx - r * 0.28, cy + r * 0.28), (cx - r, cy),
                   (cx - r * 0.28, cy - r * 0.28)], fill=WHITE)


def icon_privacy(d):
    c = W // 2
    pts = [(c, int(W * 0.26)), (int(W * 0.74), int(W * 0.36)),
           (int(W * 0.70), int(W * 0.62)), (c, int(W * 0.78)),
           (int(W * 0.30), int(W * 0.62)), (int(W * 0.26), int(W * 0.36))]
    d.polygon(pts, outline=WHITE, width=LW)
    stroke(d, [(c - int(W * 0.06), int(W * 0.49)), (c, int(W * 0.56)),
               (c + int(W * 0.08), int(W * 0.44))])


def icon_edge(d):
    stroke(d, [(int(W * 0.30), int(W * 0.26)), (int(W * 0.30), int(W * 0.74))])
    stroke(d, [(int(W * 0.46), W // 2), (int(W * 0.74), W // 2)])
    stroke(d, [(int(W * 0.62), int(W * 0.40)), (int(W * 0.74), W // 2),
               (int(W * 0.62), int(W * 0.60))])


def icon_speaker(d):
    x0, y0, x1, y1 = int(W * 0.34), int(W * 0.24), int(W * 0.66), int(W * 0.76)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(W * 0.08), outline=WHITE, width=LW)
    for j in range(4):
        for i in range(3):
            cx = x0 + int((x1 - x0) * (0.22 + 0.28 * i))
            cy = y0 + int((y1 - y0) * (0.16 + 0.23 * j))
            rr = max(2, LW // 3)
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=WHITE)


def icon_cloud(d):
    """iCloud: puffy cloud on the classic blue tile (home row 15.6)."""
    c = W // 2
    for cx, cy, r in ((c - int(W * 0.15), int(W * 0.47), int(W * 0.085)),
                      (c - int(W * 0.01), int(W * 0.40), int(W * 0.115)),
                      (c + int(W * 0.16), int(W * 0.47), int(W * 0.08))):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    d.rounded_rectangle([int(W * 0.20), int(W * 0.47), int(W * 0.80), int(W * 0.63)],
                        radius=int(W * 0.08), fill=WHITE)


def icon_airpods(d):
    """AirPods: two earbuds (bud + stem), white glyph on the iOS grey tile."""
    for cx in (int(W * 0.36), int(W * 0.64)):
        r = int(W * 0.085)
        cy = int(W * 0.35)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
        stem = int(W * 0.033)
        d.rounded_rectangle([cx - stem, cy, cx + stem, int(W * 0.67)],
                            radius=stem, fill=WHITE)


ICONS = {
    "ic_sub_clock": ("#FF9500", icon_clock),
    "ic_sub_keyboard": ("#8E8E93", icon_keyboard),
    "ic_sub_globe": ("#007AFF", icon_globe),
    "ic_sub_search": ("#8E8E93", icon_magnifier),
    "ic_sub_light": ("#FFCC00", icon_light),
    "ic_sub_game": ("#5856D6", icon_game),
    "ic_sub_camera": ("#FF2D55", icon_camera),
    "ic_sub_chat": ("#34C759", icon_chat),
    "ic_sub_sidebar": ("#5AC8FA", icon_sidebar),
    "ic_sub_portal": ("#AF52DE", icon_portal),
    "ic_sub_clipboard": ("#FF9500", icon_clipboard),
    "ic_sub_window": ("#30B0C7", icon_window),
    "ic_sub_wallet": ("#1C1C1E", icon_wallet),
    "ic_sub_record": ("#FF3B30", icon_record),
    "ic_sub_desktop": ("#007AFF", icon_desktop),
    "ic_sub_memory": ("#34C759", icon_memory),
    "ic_sub_parent": ("#FF9500", icon_parent),
    "ic_sub_child": ("#34C759", icon_person),
    "ic_sub_sync": ("#007AFF", icon_sync),
    "ic_sub_ai": ("#AF52DE", icon_ai),
    "ic_sub_user": ("#8E8E93", icon_person),
    "ic_sub_privacy": ("#34C759", icon_privacy),
    "ic_sub_edge": ("#8E8E93", icon_edge),
    "ic_sub_speaker": ("#007AFF", icon_speaker),
    "ic_cloud": ("#3478F6", icon_cloud),
    "ic_sub_airpods": ("#8E8E93", icon_airpods),
}


def main():
    for name, (color, fn) in ICONS.items():
        img = base(color)
        fn(gd(img))
        img = img.resize((SIZE, SIZE), Image.LANCZOS)
        path = os.path.join(OUT, name + ".png")
        img.save(path)
    print("wrote", len(ICONS), "icons to", OUT)


if __name__ == "__main__":
    main()
