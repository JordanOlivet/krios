#!/usr/bin/env python3
"""Palette-A draft textures for Krios: Industry (graphics pass).

Generates every texture the mod ships (blocks, items, GUIs, KOIOS face
atlas) in the frozen "direction A" palette: cyan = software/AI, amber and
copper = hardware/heat, blue-grey gunmetal metal, near-black screen.

Pure stdlib (zlib + struct PNG writer). Output is a DRAFT meant to be
polished by hand in Aseprite afterwards; keep this script as the record
of layouts (GUI slot frames, face atlas regions, conduit UV strips).

Run from anywhere: python mods/industry/tools/gen_textures.py
"""

import struct
import zlib
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets/krios_industry/textures"

# ---------------------------------------------------------------- palette

METAL_EDGE = (0x3A, 0x41, 0x4E)
METAL = (0x26, 0x2B, 0x35)
JOINT = (0x17, 0x1B, 0x22)
SCREEN = (0x10, 0x14, 0x1B)
CYAN_HI = (0x9F, 0xF0, 0xFF)
CYAN = (0x31, 0xD8, 0xFF)
CYAN_DIM = (0x0A, 0x6E, 0x87)
ENERGY = (0xFF, 0xB0, 0x3A)
AMBER_D = (0xE8, 0x84, 0x3B)
COPPER = (0xC9, 0x75, 0x2E)
COPPER_D = (0x7A, 0x43, 0x1C)
ERROR = (0xE5, 0x48, 0x4D)
GUI_BG = (0x1B, 0x20, 0x2A)
GUI_SLOT = (0x12, 0x15, 0x1B)
GUI_DARKEST = (0x0C, 0x0E, 0x13)
TEXT_HI = (0xE8, 0xEA, 0xF0)

STONE = [(0x6B, 0x6B, 0x6B), (0x7E, 0x7E, 0x7E), (0x8D, 0x8D, 0x8D)]
DEEPSLATE = [(0x3E, 0x3E, 0x46), (0x50, 0x50, 0x5A), (0x5E, 0x5E, 0x68)]
ENERGY_HI = (0xFF, 0xD9, 0x8F)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2654435761) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFF) / 255.0


# ---------------------------------------------------------------- canvas


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = (c[0], c[1], c[2], a)

    def rect(self, x, y, w, h, c, a=255):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c, a)

    def outline(self, x, y, w, h, c, a=255):
        for xx in range(x, x + w):
            self.set(xx, y, c, a)
            self.set(xx, y + h - 1, c, a)
        for yy in range(y, y + h):
            self.set(x, yy, c, a)
            self.set(x + w - 1, yy, c, a)

    def noise_rect(self, x, y, w, h, base, amp, seed):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                n = (noise(xx, yy, seed) - 0.5) * 2 * amp
                self.set(xx, yy, tuple(max(0, min(255, round(v + n))) for v in base))

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(
            b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in self.px
        )
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))
        png = (b"\x89PNG\r\n\x1a\n"
               + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(raw, 9))
               + chunk(b"IEND", b""))
        path.write_bytes(png)
        print(f"  {path.relative_to(ASSETS)}")


# ---------------------------------------------------------------- blocks


def bevel(img, inset=0):
    s = inset
    e = 16 - inset
    for i in range(s, e):
        img.set(i, s, METAL_EDGE)
        img.set(s, i, METAL_EDGE)
        img.set(i, e - 1, JOINT)
        img.set(e - 1, i, JOINT)


def neural_casing():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 5, 11)
    bevel(img)
    img.outline(2, 2, 12, 12, JOINT)
    for rx, ry in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        img.set(rx, ry, METAL_EDGE)
        img.set(rx + (1 if rx < 8 else -1), ry + 1, JOINT)
    for y in (7, 9):
        img.rect(5, y, 6, 1, JOINT)
        img.set(5, y + 1 if y == 9 else y, JOINT)
    return img


def neural_casing_formed():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 23)
    # Seamless mass: no bevel, only interior plate seams + dim traces.
    img.rect(2, 8, 5, 1, JOINT)
    img.rect(9, 4, 1, 5, JOINT)
    img.rect(4, 12, 7, 1, JOINT)
    # Circuit traces, fully interior so the block tiles cleanly.
    img.rect(3, 3, 4, 1, CYAN_DIM)
    img.rect(6, 3, 1, 4, CYAN_DIM)
    img.set(6, 6, CYAN)
    img.rect(10, 10, 3, 1, CYAN_DIM)
    img.rect(12, 7, 1, 4, CYAN_DIM)
    img.set(12, 10, CYAN)
    img.set(3, 13, CYAN_DIM)
    return img


def koios_core():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 5, 37)
    bevel(img)
    img.outline(2, 2, 12, 12, JOINT)
    img.rect(6, 6, 4, 4, CYAN_DIM)
    img.rect(7, 7, 2, 2, CYAN)
    img.set(7, 7, CYAN_HI)
    for dx, dy in [(5, 7), (10, 7), (7, 5), (7, 10)]:
        img.set(dx, dy, CYAN_DIM)
    return img


def koios_core_formed():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, SCREEN, 3, 41)
    img.outline(0, 0, 16, 16, JOINT)
    img.outline(5, 5, 6, 6, CYAN_DIM)
    img.rect(7, 7, 2, 2, CYAN)
    img.set(7, 7, CYAN_HI)
    img.set(8, 8, CYAN_HI)
    for dx, dy in [(3, 8), (12, 8), (8, 3), (8, 12)]:
        img.set(dx, dy, CYAN_DIM)
    return img


def generator():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 5, 53)
    bevel(img)
    img.outline(2, 2, 12, 12, COPPER)
    img.outline(3, 3, 10, 10, COPPER_D)
    img.rect(5, 5, 6, 6, JOINT)
    img.rect(7, 7, 2, 2, ENERGY)
    img.set(7, 7, ENERGY_HI)
    img.outline(6, 6, 4, 4, AMBER_D)
    for x in range(5, 11, 2):
        img.set(x, 13, JOINT)
    return img


def crusher():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 5, 67)
    bevel(img)
    img.rect(2, 2, 12, 3, COPPER)
    img.rect(2, 4, 12, 1, COPPER_D)
    img.rect(2, 11, 12, 3, COPPER)
    img.rect(2, 11, 12, 1, COPPER_D)
    img.rect(2, 6, 12, 4, JOINT)
    for x in range(2, 14, 2):
        img.set(x, 6, METAL_EDGE)
        img.set(x + 1, 9, METAL_EDGE)
    return img


def neural_conduit():
    img = Img(16, 16)
    # Vertical strip x5..10 (arm UV 5,0..11,y) — along-length pattern.
    for y in range(16):
        img.set(5, y, COPPER_D)
        img.set(10, y, COPPER_D)
        for x in (6, 9):
            img.set(x, y, COPPER)
        for x in (7, 8):
            img.set(x, y, CYAN if noise(x, y, 71) > 0.25 else CYAN_HI)
    # Horizontal strip y5..10 (arm UV 0,5..x,11).
    for x in range(16):
        if 5 <= x <= 10:
            continue
        img.set(x, 5, COPPER_D)
        img.set(x, 10, COPPER_D)
        for y in (6, 9):
            img.set(x, y, COPPER)
        for y in (7, 8):
            img.set(x, y, CYAN if noise(x, y, 71) > 0.25 else CYAN_HI)
    # Core box 5..10 (core UV 5,5..11,11): junction block.
    img.noise_rect(5, 5, 6, 6, METAL, 4, 73)
    img.outline(5, 5, 6, 6, METAL_EDGE)
    img.rect(7, 7, 2, 2, CYAN)
    img.set(7, 7, CYAN_HI)
    return img


def ore(base_ramp, gem, gem_hi, gem_out, seed):
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            img.set(x, y, base_ramp[0] if n < 0.3 else base_ramp[1] if n < 0.75 else base_ramp[2])
    clusters = [(3, 4), (10, 3), (5, 11), (12, 10)]
    for i, (cx, cy) in enumerate(clusters):
        img.set(cx, cy, gem)
        img.set(cx + 1, cy, gem)
        img.set(cx, cy + 1, gem)
        img.set(cx + 1, cy + 1, gem_hi if i % 2 == 0 else gem)
        img.set(cx - 1, cy, gem_out)
        img.set(cx + 2, cy + 1, gem_out)
        img.set(cx, cy - 1, gem_out)
    return img


# ---------------------------------------------------------------- items


def neurite_ingot():
    img = Img(16, 16)
    body = mix(CYAN_DIM, CYAN, 0.45)
    for y in range(6, 12):
        img.rect(2 + (11 - y) // 3, y, 11, 1, body)
    img.rect(3, 5, 10, 1, mix(CYAN, CYAN_HI, 0.4))
    img.rect(2 + 2, 6, 10, 1, mix(body, CYAN_HI, 0.25))
    img.rect(2, 11, 11, 1, CYAN_DIM)
    img.set(4, 6, CYAN_HI)
    img.set(5, 6, CYAN_HI)
    return img


def raw_neurite():
    img = Img(16, 16)
    for y in range(4, 13):
        w = 10 - abs(8 - y)
        img.rect(8 - w // 2 - 1, y, w + 2, 1, STONE[0])
    img.noise_rect(4, 6, 8, 5, STONE[1], 8, 83)
    for x, y in [(6, 7), (9, 8), (7, 10), (10, 6)]:
        img.set(x, y, CYAN)
    img.set(8, 8, CYAN_HI)
    return img


def neurite_dust():
    img = Img(16, 16)
    for y in range(8, 13):
        w = (y - 6) * 2 + 1
        img.rect(8 - w // 2, y, w, 1, CYAN_DIM)
    for x, y in [(6, 10), (8, 9), (10, 11), (7, 12), (9, 12), (8, 11)]:
        img.set(x, y, CYAN)
    img.set(8, 10, CYAN_HI)
    return img


def chronite_shard():
    img = Img(16, 16)
    pts = [(11, 3), (12, 4), (11, 5), (10, 5)]
    for i in range(8):
        img.set(10 - i, 4 + i, ENERGY)
        img.set(11 - i, 4 + i, ENERGY)
        img.set(9 - i, 4 + i, AMBER_D)
    for x, y in pts:
        img.set(x, y, ENERGY_HI)
    for i in range(8):
        img.set(8 - i, 5 + i, COPPER_D)
    img.set(3, 12, COPPER_D)
    return img


def calibrator():
    img = Img(16, 16)
    for i in range(5):
        img.set(3 + i, 12 - i, COPPER)
        img.set(4 + i, 12 - i, COPPER_D)
    img.set(2, 13, COPPER_D)
    for i in range(4):
        img.rect(7 + i, 8 - i, 2, 2, METAL)
        img.set(7 + i, 8 - i, METAL_EDGE)
    img.rect(10, 3, 4, 4, METAL_EDGE)
    img.rect(11, 4, 2, 2, CYAN)
    img.set(11, 4, CYAN_HI)
    img.set(14, 2, CYAN_DIM)
    return img


def module(glyph_px, glyph_color):
    img = Img(16, 16)
    img.rect(3, 3, 10, 10, METAL)
    img.outline(3, 3, 10, 10, METAL_EDGE)
    img.set(3, 3, JOINT)  # keyed corner
    for x in range(4, 12, 2):
        img.set(x, 13, COPPER)
    for y in range(4, 12, 2):
        img.set(2, y, COPPER)
    img.rect(5, 5, 6, 6, GUI_SLOT)
    for x, y in glyph_px:
        img.set(x, y, glyph_color)
    return img


SPEED_GLYPH = [(6, 6), (7, 7), (6, 8), (8, 6), (9, 7), (8, 8), (6, 9), (8, 9)]
EFFICIENCY_GLYPH = [(8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9)]
YIELD_GLYPH = [(8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (6, 7), (7, 7), (9, 7), (10, 7)]


# ---------------------------------------------------------------- GUI


def gui_panel(img, w=176, h=166):
    img.rect(0, 0, w, h, GUI_BG)
    img.outline(0, 0, w, h, GUI_DARKEST)
    for i in range(w):
        img.set(i, 1, METAL_EDGE)
    for i in range(h):
        img.set(1, i, METAL_EDGE)
    for i in range(w):
        img.set(i, h - 2, SCREEN)
    for i in range(h):
        img.set(w - 2, i, SCREEN)
    for x, y in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        img.set(x, y, (0, 0, 0), 0)
    for x, y in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]:
        img.set(x, y, GUI_DARKEST)


def gui_slot(img, x, y, accent=None):
    """18x18 slot frame; (x, y) is the frame corner (menu item pos - 1)."""
    img.rect(x, y, 18, 18, GUI_SLOT)
    for i in range(18):
        img.set(x + i, y, GUI_DARKEST)
        img.set(x, y + i, GUI_DARKEST)
        img.set(x + i, y + 17, METAL_EDGE)
        img.set(x + 17, y + i, METAL_EDGE)
    if accent:
        for cx, cy in [(x, y), (x + 17, y), (x, y + 17), (x + 17, y + 17)]:
            img.set(cx, cy, accent)


def gui_inventory(img):
    for row in range(3):
        for col in range(9):
            gui_slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        gui_slot(img, 7 + col * 18, 141)


def gui_bar_frame(img, x, y, w, h):
    """Inset frame around a fill bar at (x+1, y+1, w-2, h-2)."""
    img.rect(x, y, w, h, GUI_DARKEST)
    for i in range(w):
        img.set(x + i, y + h - 1, METAL_EDGE)
    for i in range(h):
        img.set(x + w - 1, y + i, METAL_EDGE)


def energy_fill(img, x, y, w, h):
    for yy in range(h):
        c = ENERGY_HI if yy == 0 else ENERGY if yy < h - 2 else AMBER_D
        for xx in range(w):
            img.set(x + xx, y + yy, c)
    for xx in range(0, w, 8):
        for yy in range(1, h - 2):
            img.set(x + xx, y + yy, mix(ENERGY, AMBER_D, 0.5))


def xp_fill(img, x, y, w, h):
    for yy in range(h):
        c = CYAN_HI if yy == 0 else CYAN if yy < h - 2 else CYAN_DIM
        for xx in range(w):
            img.set(x + xx, y + yy, c)
    for xx in range(0, w, 8):
        for yy in range(1, h - 2):
            img.set(x + xx, y + yy, mix(CYAN, CYAN_DIM, 0.5))


def arrow_shape(img, ox, oy, body, edge):
    for x in range(14):
        img.rect(ox + x, oy + 5, 1, 5, body)
    for i in range(7):
        img.rect(ox + 14 + i, oy + i, 1, 15 - 2 * i, body)
    for x in range(14):
        img.set(ox + x, oy + 5, edge)
        img.set(ox + x, oy + 9, edge)
    return img


def gui_crusher():
    img = Img(256, 256)
    gui_panel(img)
    gui_bar_frame(img, 7, 16, 162, 10)
    gui_slot(img, 55, 40)
    gui_slot(img, 115, 40)
    gui_slot(img, 151, 27, accent=CYAN_DIM)
    gui_slot(img, 151, 51, accent=CYAN_DIM)
    gui_inventory(img)
    arrow_shape(img, 80, 42, GUI_SLOT, GUI_DARKEST)
    # Fill sprites: energy row at v=166, progress arrow at u=176.
    energy_fill(img, 0, 166, 160, 8)
    arrow_shape(img, 176, 0, CYAN, CYAN_DIM)
    img.set(177, 1, CYAN_HI)
    return img


def gui_generator():
    img = Img(256, 256)
    gui_panel(img)
    gui_bar_frame(img, 7, 16, 162, 10)
    gui_slot(img, 79, 40)
    gui_inventory(img)
    # Dim flame socket behind the burn indicator.
    for x, y in flame_pixels():
        img.set(81 + x, 26 + y, JOINT)
    energy_fill(img, 0, 166, 160, 8)
    for x, y in flame_pixels():
        img.set(176 + x, 0 + y, ENERGY)
    for x, y in [(6, 9), (7, 10), (6, 11), (7, 12)]:
        img.set(176 + x, 0 + y, ENERGY_HI)
    for x, y in [(4, 4), (9, 5), (3, 8), (10, 9)]:
        img.set(176 + x, 0 + y, AMBER_D)
    return img


def flame_pixels():
    rows = {
        1: range(6, 8), 2: range(6, 8), 3: range(5, 9), 4: range(4, 9),
        5: range(4, 10), 6: range(3, 10), 7: range(3, 11), 8: range(3, 11),
        9: range(4, 11), 10: range(4, 11), 11: range(5, 10), 12: range(5, 10),
        13: range(6, 9),
    }
    return [(x, y) for y, xs in rows.items() for x in xs]


def gui_koios_core():
    img = Img(256, 256)
    gui_panel(img)
    # Header divider + status zone; labels are drawn code-side.
    img.rect(7, 16, 162, 1, METAL_EDGE)
    img.rect(7, 57, 162, 1, JOINT)
    gui_bar_frame(img, 7, 125, 162, 12)
    xp_fill(img, 0, 166, 160, 10)
    return img


# ---------------------------------------------------------------- face atlas


def koios_face():
    img = Img(64, 64)
    # Screen background 48x48 at (0,0): plain near-black, darkened edge frame.
    for y in range(48):
        for x in range(48):
            c = SCREEN
            d = min(x, y, 47 - x, 47 - y)
            if d == 0:
                c = mix(c, (0, 0, 0), 0.5)
            elif d == 1:
                c = mix(c, (0, 0, 0), 0.25)
            img.set(x, y, c)
    # Eye sprite 8x8 at (48,0): rounded cyan block, drawn untinted.
    for y in range(8):
        for x in range(8):
            if (x in (0, 7) and y in (0, 7)):
                continue
            c = CYAN
            if x in (0, 7) or y in (0, 7):
                c = CYAN_DIM
            img.set(48 + x, y, c)
    img.rect(49 + 1, 1, 2, 2, CYAN_HI)
    # White patch 8x8 at (56,0): tinted by the renderer (mouth, pulse, panels).
    img.rect(56, 0, 8, 8, (255, 255, 255))
    # Static noise strip (0,48)-(48,63): scrolled by boot/fault renders.
    for y in range(48, 63):
        for x in range(48):
            n = noise(x, y, 97)
            if n > 0.92:
                img.set(x, y, CYAN_HI)
            elif n > 0.75:
                img.set(x, y, TEXT_HI)
            elif n > 0.5:
                img.set(x, y, METAL_EDGE)
            elif n > 0.3:
                img.set(x, y, METAL)
            else:
                img.set(x, y, SCREEN)
    return img


# ---------------------------------------------------------------- main


def main():
    out = {
        "block/neural_casing.png": neural_casing(),
        "block/neural_casing_formed.png": neural_casing_formed(),
        "block/koios_core.png": koios_core(),
        "block/koios_core_formed.png": koios_core_formed(),
        "block/generator.png": generator(),
        "block/crusher.png": crusher(),
        "block/neural_conduit.png": neural_conduit(),
        "block/neurite_ore.png": ore(STONE, CYAN, CYAN_HI, CYAN_DIM, 101),
        "block/deepslate_neurite_ore.png": ore(DEEPSLATE, CYAN, CYAN_HI, CYAN_DIM, 103),
        "block/chronite_ore.png": ore(STONE, ENERGY, ENERGY_HI, COPPER_D, 107),
        "block/deepslate_chronite_ore.png": ore(DEEPSLATE, ENERGY, ENERGY_HI, COPPER_D, 109),
        "block/koios_face.png": koios_face(),
        "item/neurite_ingot.png": neurite_ingot(),
        "item/raw_neurite.png": raw_neurite(),
        "item/neurite_dust.png": neurite_dust(),
        "item/chronite_shard.png": chronite_shard(),
        "item/calibrator.png": calibrator(),
        "item/speed_module.png": module(SPEED_GLYPH, CYAN),
        "item/efficiency_module.png": module(EFFICIENCY_GLYPH, ENERGY),
        "item/yield_module.png": module(YIELD_GLYPH, CYAN_HI),
        "gui/crusher.png": gui_crusher(),
        "gui/generator.png": gui_generator(),
        "gui/koios_core.png": gui_koios_core(),
    }
    print(f"Writing {len(out)} textures to {ASSETS}")
    for rel, img in out.items():
        img.save(ASSETS / rel)


if __name__ == "__main__":
    main()
