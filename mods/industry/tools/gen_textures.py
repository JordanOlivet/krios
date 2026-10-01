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
import sys
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

# Vanilla stone/deepslate pixels (palette + hex index grid, extracted from
# the 1.21.1 client jar with tools no longer needed): the ore blocks sit on
# the exact vanilla background so they read like every vanilla ore.

VANILLA_STONE_PALETTE = [
    (0x8F, 0x8F, 0x8F),
    (0x7F, 0x7F, 0x7F),
    (0x74, 0x74, 0x74),
    (0x68, 0x68, 0x68),
]
VANILLA_STONE_GRID = """
0000122123221111
1121211111133212
1233222322211111
1100101100111222
2112221210000211
1011111112232320
2110002002221111
2233232211222111
0001011000111112
1101110001100001
3121223321211222
1111100111110010
1222101221133232
0012211222112220
1221111111110000
1111112210011211
"""

VANILLA_DEEPSLATE_PALETTE = [
    (0x79, 0x79, 0x79),
    (0x64, 0x64, 0x64),
    (0x51, 0x51, 0x51),
    (0x3D, 0x3D, 0x43),
    (0x2F, 0x2F, 0x37),
]
VANILLA_DEEPSLATE_GRID = """
0122223311122322
2344333321223323
1101122444433433
3212233321001112
4322333211101123
0021224211212221
0112243322334432
1233331011234322
2323332012123332
3344222122323443
1212322334432200
1123100023233201
1233201112333211
3334421123434433
0011233430122110
0012122420112211
"""


def grid_pixels(palette, grid):
    return [[palette[int(ch, 16)] for ch in line] for line in grid.split()]


VANILLA_STONE_PX = grid_pixels(VANILLA_STONE_PALETTE, VANILLA_STONE_GRID)
VANILLA_DEEPSLATE_PX = grid_pixels(VANILLA_DEEPSLATE_PALETTE, VANILLA_DEEPSLATE_GRID)


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
    """Formed shell: near-black mass crossed by CONTINUOUS digital veins.
    The main lines run edge to edge at fixed coordinates, so they connect
    across neighboring casings and read as circuitry spanning the whole
    structure, not a per-block motif."""
    # Plain near-black mass: the circuit veins are BER-drawn by the core so
    # the pattern spans the whole structure (texture veins only repeated a
    # per-block motif).
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, SCREEN, 2, 23)
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


def generator_side():
    """Bands match generator_base.json: 0-2 chimney/vents, 2-4 deck, 4-12 body, 12-16 skirt."""
    img = Img(16, 16)
    img.rect(0, 0, 16, 2, METAL_EDGE)
    for x in range(0, 16, 2):
        img.set(x, 1, JOINT)
    img.noise_rect(0, 2, 16, 2, METAL, 4, 51)
    img.rect(0, 2, 16, 1, METAL_EDGE)
    img.noise_rect(0, 4, 16, 8, METAL, 5, 53)
    for x in (2, 13):
        img.rect(x, 4, 1, 8, COPPER_D)
        img.set(x, 5, COPPER)
    for y in (9, 10):
        img.rect(4, y, 8, 1, JOINT)
    img.rect(0, 12, 16, 4, JOINT)
    img.rect(0, 12, 16, 1, METAL_EDGE)
    for x in (2, 13):
        img.set(x, 14, METAL_EDGE)
    return img


def generator_front(lit):
    img = generator_side()
    # Window (4,5)-(12,11): copper frame, amber core inside.
    img.rect(4, 5, 8, 6, COPPER)
    img.rect(5, 6, 6, 4, JOINT)
    if lit:
        img.rect(6, 7, 4, 2, ENERGY)
        img.set(7, 7, ENERGY_HI)
        img.set(8, 7, ENERGY_HI)
        img.outline(5, 6, 6, 4, AMBER_D)
    else:
        img.rect(6, 7, 4, 2, COPPER_D)
        img.set(7, 7, AMBER_D)
        img.set(8, 8, AMBER_D)
    return img


def generator_top():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 57)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.outline(2, 2, 12, 12, JOINT)
    # Chimney mouth (9,9)-(13,13).
    img.rect(9, 9, 4, 4, JOINT)
    img.outline(9, 9, 4, 4, METAL_EDGE)
    img.rect(10, 10, 2, 2, (0, 0, 0))
    # Vent grid (3,3)-(7,7).
    for y in range(3, 7):
        for x in range(3, 7):
            img.set(x, y, JOINT if (x + y) % 2 == 0 else METAL_EDGE)
    return img


def furnace_side():
    """Bands match furnace_base.json: 0-3 stacks, 3-5 top slab, 5-16 body."""
    img = Img(16, 16)
    img.rect(0, 0, 16, 3, METAL_EDGE)
    for x in range(1, 16, 3):
        img.set(x, 1, JOINT)
    img.noise_rect(0, 3, 16, 2, METAL, 4, 111)
    img.rect(0, 3, 16, 1, METAL_EDGE)
    img.noise_rect(0, 5, 16, 11, METAL, 5, 113)
    for x in (1, 14):
        img.rect(x, 5, 1, 11, COPPER_D)
    for y in (8, 9):
        img.rect(4, y, 8, 1, JOINT)
    img.rect(0, 15, 16, 1, JOINT)
    return img


def furnace_front(lit, frame=0):
    img = furnace_side()
    # Arched door (3,7)-(13,15): copper frame, dark interior, amber window.
    img.rect(3, 7, 10, 8, COPPER)
    img.rect(4, 8, 8, 7, COPPER_D)
    img.rect(5, 9, 6, 5, JOINT)
    if lit:
        if frame == 1:
            img.rect(5, 9, 6, 5, ENERGY)
            img.rect(6, 10, 4, 3, ENERGY_HI)
        elif frame == 2:
            img.rect(5, 9, 6, 5, AMBER_D)
            img.rect(6, 10, 4, 3, ENERGY)
            img.set(7, 11, ENERGY_HI)
        else:
            img.rect(6, 10, 4, 3, ENERGY)
            img.set(7, 11, ENERGY_HI)
            img.set(8, 12, ENERGY_HI)
    else:
        img.rect(6, 12, 4, 1, AMBER_D)
    return img


def furnace_top():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 117)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.outline(1, 1, 14, 14, JOINT)
    # Twin rear stack mouths (2,9)-(6,13) and (10,9)-(14,13).
    for sx in (2, 10):
        img.rect(sx, 9, 4, 4, JOINT)
        img.outline(sx, 9, 4, 4, METAL_EDGE)
        img.rect(sx + 1, 10, 2, 2, (0, 0, 0))
    return img


def crusher_side():
    """Bands match crusher_base.json: 0-4 jaw zone, 4-5 collar, 5-16 body."""
    img = Img(16, 16)
    img.rect(0, 0, 16, 4, METAL_EDGE)
    img.rect(0, 4, 16, 1, METAL_EDGE)
    img.noise_rect(0, 5, 16, 11, METAL, 5, 67)
    for x in (2, 3, 12, 13):
        img.rect(x, 5, 1, 11, COPPER_D if x in (2, 13) else COPPER)
    # Hazard strip near the ground.
    for x in range(4, 12):
        img.set(x, 14, ENERGY if x % 2 == 0 else JOINT)
    img.rect(0, 15, 16, 1, JOINT)
    return img


def crusher_front():
    img = crusher_side()
    # Status screen (5,7)-(11,12): dark panel, cyan readout.
    img.rect(5, 7, 6, 5, JOINT)
    img.outline(5, 7, 6, 5, METAL_EDGE)
    img.rect(6, 8, 3, 1, CYAN_DIM)
    img.rect(6, 10, 4, 1, CYAN_DIM)
    img.set(9, 8, CYAN)
    return img


def crusher_top():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 69)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.rect(4, 4, 8, 8, JOINT)
    return img


def crusher_jaw():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 5, 71)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    for y in (5, 10):
        img.rect(1, y, 14, 1, JOINT)
    # Teeth edge.
    for x in range(0, 16, 2):
        img.set(x, 14, METAL_EDGE)
        img.set(x + 1, 14, JOINT)
        img.set(x, 15, JOINT)
        img.set(x + 1, 15, METAL_EDGE)
    return img


def crusher_maw(lit, frame=0):
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 73)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.rect(3, 3, 10, 10, JOINT)
    for y in range(4, 12, 2):
        for x in range(4, 12, 2):
            img.set(x, y, METAL_EDGE)
            if lit and noise(x, y, 83 + frame * 7) > 0.35:
                img.set(x + 1, y + 1, ENERGY if noise(x, y, 89 + frame) > 0.5 else AMBER_D)
    if lit:
        if frame % 2 == 0:
            img.rect(6, 6, 4, 4, ENERGY)
            img.rect(7, 7, 2, 2, ENERGY_HI)
        else:
            img.rect(7, 7, 2, 2, ENERGY)
    return img


def solar_panel_top():
    """Photovoltaic cell grid: 3x3 cyan cells in a gunmetal frame, one
    amber status pip bottom-right. Flat slab top (solar_panel.json)."""
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 3, 91)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    for cy in range(3):
        for cx in range(3):
            x = 1 + cx * 5
            y = 1 + cy * 5
            img.rect(x, y, 4, 4, CYAN_DIM)
            img.rect(x, y, 4, 1, CYAN)
            img.set(x + 3, y, CYAN_HI)
            img.set(x, y + 3, SCREEN)
    img.set(14, 14, ENERGY)
    return img


def solar_panel_side():
    """Full machine flank (full-cube model): rows 0-3 the cell deck
    edge-on, body with copper risers below, skirt at the base."""
    img = Img(16, 16)
    img.rect(0, 0, 16, 1, CYAN_DIM)
    img.rect(0, 1, 16, 1, METAL_EDGE)
    img.rect(0, 2, 16, 1, METAL)
    img.rect(0, 3, 16, 1, JOINT)
    img.noise_rect(0, 4, 16, 10, METAL, 4, 95)
    for x in (2, 13):
        img.rect(x, 5, 1, 8, COPPER_D)
        img.set(x, 6, COPPER)
    img.rect(0, 14, 16, 2, JOINT)
    img.rect(0, 14, 16, 1, METAL_EDGE)
    return img


def solar_panel_bottom():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, JOINT, 3, 93)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.outline(4, 4, 8, 8, METAL_EDGE)
    return img


def stack_frames(frames):
    """Vertical strip for a vanilla animated texture (+ .mcmeta)."""
    img = Img(16, 16 * len(frames))
    for i, frame in enumerate(frames):
        for y in range(16):
            for x in range(16):
                img.px[i * 16 + y][x] = frame.px[y][x]
    return img


def generator_front_lit_frames():
    """Strong visible pulse: the whole window interior breathes."""
    frames = []
    for f in range(3):
        img = generator_front(True)
        if f == 1:
            img.rect(5, 6, 6, 4, ENERGY)
            img.rect(6, 7, 4, 2, ENERGY_HI)
        elif f == 2:
            img.rect(5, 6, 6, 4, AMBER_D)
            img.rect(6, 7, 4, 2, ENERGY)
            img.set(7, 7, ENERGY_HI)
        frames.append(img)
    return frames


def koios_core_frame():
    img = Img(16, 16)
    img.noise_rect(0, 0, 16, 16, METAL, 4, 77)
    img.outline(0, 0, 16, 16, METAL_EDGE)
    img.outline(2, 2, 12, 12, JOINT)
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        img.set(x, y, CYAN_DIM)
    return img


def koios_core_crystal():
    img = Img(16, 16)
    for x in range(16):
        if x in (7, 8):
            c = mix(CYAN, CYAN_HI, 0.5)
        elif x in (6, 9):
            c = CYAN
        else:
            c = CYAN_DIM
        for y in range(16):
            n = (noise(x, y, 79) - 0.5) * 14
            img.set(x, y, tuple(max(0, min(255, round(v + n))) for v in c))
    for y in range(5, 10):
        img.set(7, y, CYAN_HI)
        img.set(8, y, CYAN_HI)
    img.rect(0, 0, 16, 1, CYAN_DIM)
    img.rect(0, 15, 16, 1, CYAN_DIM)
    return img


def neural_conduit():
    """4 px profile: strips x6..9 / y6..9 (sheath 6 and 9, cyan core 7-8)."""
    img = Img(16, 16)
    # Vertical strip (arm UV 6,0..10,y) — along-length pattern.
    for y in range(16):
        img.set(6, y, COPPER_D)
        img.set(9, y, COPPER_D)
        for x in (7, 8):
            img.set(x, y, CYAN if noise(x, y, 71) > 0.25 else CYAN_HI)
    # Horizontal strip (arm side UV 0,6..x,10).
    for x in range(16):
        if 6 <= x <= 9:
            continue
        img.set(x, 6, COPPER_D)
        img.set(x, 9, COPPER_D)
        for y in (7, 8):
            img.set(x, y, CYAN if noise(x, y, 71) > 0.25 else CYAN_HI)
    # Core box 6..9 (core UV 6,6..10,10): junction node.
    img.outline(6, 6, 4, 4, METAL_EDGE)
    img.rect(7, 7, 2, 2, CYAN)
    img.set(7, 7, CYAN_HI)
    return img


def ore(base_px, gem, gem_hi, gem_out):
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            img.set(x, y, base_px[y][x])
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


def module(glyph_px, glyph_color, mk2=False):
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
    if mk2:
        # Mk II mark: two amber pips on the top edge of the body.
        img.set(10, 4, ENERGY)
        img.set(12, 4, ENERGY)
        img.set(12, 3, AMBER_D)
    return img


def compute_chip():
    """Breakthrough chip: wide dark package, copper pin rows, bright
    neurite die with cyan traces (software = crystal/cyan glow)."""
    img = Img(16, 16)
    img.rect(2, 4, 13, 11, SCREEN)
    img.outline(2, 4, 13, 11, METAL_EDGE)
    for x in range(3, 13, 2):
        img.set(x, 3, COPPER)
        img.set(x, 12, COPPER)
    img.rect(6, 6, 9, 9, CYAN_DIM)
    img.rect(7, 7, 8, 8, CYAN)
    img.set(7, 7, CYAN_HI)
    for x in (4, 11):
        img.set(x, 6, CYAN_DIM)
        img.set(x, 9, CYAN_DIM)
    return img


def upgrade_kit():
    """Tier upgrade kit: module-style cartridge, amber double chevron up
    (hardware = metal/amber), copper contact rows."""
    img = Img(16, 16)
    img.rect(3, 2, 12, 13, METAL)
    img.outline(3, 2, 12, 13, METAL_EDGE)
    img.set(3, 2, JOINT)
    for y in range(3, 13, 2):
        img.set(2, y, COPPER)
        img.set(13, y, COPPER)
    chevron = [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7),
               (7, 8), (8, 8), (6, 9), (9, 9), (5, 10), (10, 10)]
    for x, y in chevron:
        img.set(x, y, ENERGY)
    img.set(7, 4, ENERGY_HI)
    img.set(8, 4, ENERGY_HI)
    return img


SPEED_GLYPH = [(6, 6), (7, 7), (6, 8), (8, 6), (9, 7), (8, 8), (6, 9), (8, 9)]
# Lightning bolt (generator energy module).
ENERGY_GLYPH = [(8, 5), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9)]
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


def energy_fill_vertical(img, x, y, w, h):
    """Compact side gauge fill (drawn bottom-anchored by the screens)."""
    for yy in range(h):
        c = ENERGY_HI if yy == 0 else ENERGY if yy < h - 3 else AMBER_D
        for xx in range(w):
            img.set(x + xx, y + yy, c)
    for yy in range(0, h, 8):
        for xx in range(1, w - 1):
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
    # Compact vertical energy gauge on the left, inside the panel — the
    # center stays free for machine content (art direction feedback).
    gui_bar_frame(img, 7, 20, 10, 42)
    gui_slot(img, 55, 40)
    gui_slot(img, 115, 40)
    gui_slot(img, 151, 27, accent=CYAN_DIM)
    gui_slot(img, 151, 51, accent=CYAN_DIM)
    gui_inventory(img)
    arrow_shape(img, 80, 42, GUI_SLOT, GUI_DARKEST)
    # Fill sprites: vertical energy at v=166 (8x40), progress arrow at u=176.
    energy_fill_vertical(img, 0, 166, 8, 40)
    arrow_shape(img, 176, 0, CYAN, CYAN_DIM)
    img.set(177, 1, CYAN_HI)
    return img


def gui_generator():
    img = Img(256, 256)
    gui_panel(img)
    # Central bar kept for the generator, dropped below the header row.
    gui_bar_frame(img, 7, 20, 162, 10)
    # Slot and flame dropped below the (lowered) energy bar.
    gui_slot(img, 79, 46)
    gui_inventory(img)
    # Dim flame socket behind the burn indicator.
    for x, y in flame_pixels():
        img.set(81 + x, 32 + y, JOINT)
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
    # Eye sprite 8x8 at (48,0): SOLID square cyan, exactly like the
    # terminals' feed eyes (no rounding, no rim, no highlight).
    img.rect(48, 0, 8, 8, CYAN)
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
        "block/koios_core_frame.png": koios_core_frame(),
        "block/koios_core_crystal.png": koios_core_crystal(),
        "block/koios_core_formed.png": koios_core_formed(),
        "block/generator_side.png": generator_side(),
        "block/generator_front.png": generator_front(False),
        "block/generator_front_lit.png": stack_frames(generator_front_lit_frames()),
        "block/generator_top.png": generator_top(),
        "block/furnace_side.png": furnace_side(),
        "block/furnace_front.png": furnace_front(False),
        "block/furnace_front_lit.png": stack_frames([furnace_front(True, f) for f in range(3)]),
        "block/furnace_top.png": furnace_top(),
        "block/crusher_side.png": crusher_side(),
        "block/crusher_front.png": crusher_front(),
        "block/crusher_top.png": crusher_top(),
        "block/crusher_jaw.png": crusher_jaw(),
        "block/crusher_maw.png": crusher_maw(False),
        "block/crusher_maw_lit.png": stack_frames([crusher_maw(True, f) for f in range(3)]),
        "block/neural_conduit.png": neural_conduit(),
        "block/solar_panel_top.png": solar_panel_top(),
        "block/solar_panel_side.png": solar_panel_side(),
        "block/solar_panel_bottom.png": solar_panel_bottom(),
        "block/neurite_ore.png": ore(VANILLA_STONE_PX, CYAN, CYAN_HI, CYAN_DIM),
        "block/deepslate_neurite_ore.png": ore(VANILLA_DEEPSLATE_PX, CYAN, CYAN_HI, CYAN_DIM),
        "block/chronite_ore.png": ore(VANILLA_STONE_PX, ENERGY, ENERGY_HI, COPPER_D),
        "block/deepslate_chronite_ore.png": ore(VANILLA_DEEPSLATE_PX, ENERGY, ENERGY_HI, COPPER_D),
        "block/koios_face.png": koios_face(),
        "item/neurite_ingot.png": neurite_ingot(),
        "item/raw_neurite.png": raw_neurite(),
        "item/neurite_dust.png": neurite_dust(),
        "item/chronite_shard.png": chronite_shard(),
        "item/calibrator.png": calibrator(),
        "item/compute_chip.png": compute_chip(),
        "item/upgrade_kit_tier2.png": upgrade_kit(),
        "item/speed_module.png": module(SPEED_GLYPH, CYAN),
        "item/efficiency_module.png": module(EFFICIENCY_GLYPH, ENERGY),
        "item/yield_module.png": module(YIELD_GLYPH, CYAN_HI),
        "item/speed_module_mk2.png": module(SPEED_GLYPH, CYAN, mk2=True),
        "item/efficiency_module_mk2.png": module(EFFICIENCY_GLYPH, ENERGY, mk2=True),
        "item/yield_module_mk2.png": module(YIELD_GLYPH, CYAN_HI, mk2=True),
        "item/energy_module.png": module(ENERGY_GLYPH, ENERGY_HI),
        "gui/crusher.png": gui_crusher(),
        "gui/furnace.png": gui_crusher(),
        "gui/generator.png": gui_generator(),
    }
    # Most shipped textures are hand-polished in Aseprite by now: never run
    # this without a filter unless the user explicitly agreed to a full
    # regeneration. Filters are substrings matched against the output path.
    wanted = sys.argv[1:]
    if not wanted:
        print("No filter given: this OVERWRITES every texture, including "
              "hand-polished ones. Pass substrings (e.g. 'ore') to limit, "
              "or '--all' to really write everything.")
        return
    if wanted != ["--all"]:
        out = {rel: img for rel, img in out.items()
               if any(w in rel for w in wanted)}
    print(f"Writing {len(out)} textures to {ASSETS}")
    for rel, img in out.items():
        img.save(ASSETS / rel)
    # Vanilla animation metadata for the flickering lit textures.
    for rel in ("block/generator_front_lit.png", "block/crusher_maw_lit.png",
                "block/furnace_front_lit.png"):
        if rel in out:
            (ASSETS / (rel + ".mcmeta")).write_text('{"animation": {"frametime": 6}}\n')
            print(f"  {rel}.mcmeta")


if __name__ == "__main__":
    main()
