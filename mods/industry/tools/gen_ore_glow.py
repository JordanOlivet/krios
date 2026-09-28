#!/usr/bin/env python3
"""Derive the emissive overlay textures (<ore>_glow.png) from the
hand-polished ore textures.

Every saturated (non-grey) or near-white pixel is treated as vein/crystal
and copied to the overlay; the grey stone/deepslate background becomes
transparent. The hand-made ore textures are the source of truth: this
script never writes them, only the *_glow.png derivatives, so it is safe
to re-run after the ore textures were retouched (do NOT hand-edit the
*_glow.png files without retiring this script).

Pure stdlib. Run from anywhere: python mods/industry/tools/gen_ore_glow.py
"""

import struct
import zlib
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_textures import ASSETS, Img  # noqa: E402

ORES = [
    "chronite_ore",
    "deepslate_chronite_ore",
    "neurite_ore",
    "deepslate_neurite_ore",
]

# Grey background stays below this chroma (vanilla deepslate peaks at 8).
CHROMA_MIN = 24
# Near-white crystal highlights: low chroma but far brighter than stone
# (brightest vanilla stone channel is 0x8F).
BRIGHT_MIN = 0xE0


def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def load_png(path):
    """Minimal PNG reader -> 2D list of (r, g, b, a). Handles color types
    0/2/3/6, bit depth 8 (and 4 for palette), no interlace."""
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: not a PNG"
    pos = 8
    ihdr = None
    plte = None
    trns = b""
    idat = b""
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", body)
        elif tag == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
    w, h, depth, ctype, _comp, _filt, interlace = ihdr
    assert interlace == 0, f"{path}: interlaced PNG unsupported"
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    if depth == 8:
        bpp = channels
        stride = w * channels
    elif depth == 4 and ctype == 3:
        bpp = 1
        stride = (w * 4 + 7) // 8
    else:
        raise SystemExit(f"{path}: unsupported depth {depth} ctype {ctype}")
    rows = []
    prev = bytearray(stride)
    pos = 0
    for _ in range(h):
        ftype = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if ftype == 1:
                line[i] = (line[i] + a) & 0xFF
            elif ftype == 2:
                line[i] = (line[i] + b) & 0xFF
            elif ftype == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif ftype == 4:
                line[i] = (line[i] + _paeth(a, b, c)) & 0xFF
        rows.append(bytes(line))
        prev = line
    px = []
    for row in rows:
        out = []
        if ctype == 3:
            idx = []
            if depth == 4:
                for byte in row:
                    idx.append(byte >> 4)
                    idx.append(byte & 0xF)
            else:
                idx = list(row)
            for i in idx[:w]:
                r, g, b = plte[i]
                a = trns[i] if i < len(trns) else 255
                out.append((r, g, b, a))
        elif ctype == 2:
            for x in range(w):
                out.append((*row[x * 3:x * 3 + 3], 255))
        elif ctype == 6:
            for x in range(w):
                out.append(tuple(row[x * 4:x * 4 + 4]))
        elif ctype == 0:
            for x in range(w):
                out.append((row[x], row[x], row[x], 255))
        px.append(out)
    return px


def main():
    for name in ORES:
        src = ASSETS / f"block/{name}.png"
        px = load_png(src)
        img = Img(16, 16)
        count = 0
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[y][x]
                chroma = max(r, g, b) - min(r, g, b)
                if a == 255 and (chroma >= CHROMA_MIN or min(r, g, b) >= BRIGHT_MIN):
                    img.set(x, y, (r, g, b))
                    count += 1
        img.save(ASSETS / f"block/{name}_glow.png")
        print(f"    {count} glow pixels")


if __name__ == "__main__":
    main()
