#!/usr/bin/env python3
"""Erzeugt die 16x16-Texturen der Arena-Bloecke (reines Python, ohne PIL)."""
import math, os, struct, zlib

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'chronicle_arena', 'textures', 'block')


def png(path, px):
    h = len(px)
    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in px)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)


FRAMES = 8


def portal_frame(t):
    """Runenstein-Rahmen mit wirbelnder Mitte; t = Bild 0..FRAMES-1."""
    px = []
    phase = t * math.pi * 2 / FRAMES
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            edge = max(abs(dx), abs(dy))
            if edge > 6.5:
                v = 22 + ((x * 3 + y * 5) % 3) * 4
                row.append((v, v - 4, v + 18, 255))
            elif edge > 5.5:
                rune = (x + y) % 4 == 0
                row.append((130, 70, 210, 255) if rune else (48, 30, 80, 255))
            else:
                v = 0.5 + 0.5 * math.sin(a * 2 - d * 1.3 + phase)
                w = 0.5 + 0.5 * math.sin(d * 0.9 - phase * 2)
                row.append((int(60 + 130 * v), int(15 + 60 * w), int(150 + 100 * v), 255))
        px.append(row)
    return px


def portal():
    px = []
    for t in range(FRAMES):
        px += portal_frame(t)
    return px


def terminal(top):
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            border = x in (0, 15) or y in (0, 15)
            if border:
                row.append((25, 25, 35, 255))
            elif top:
                v = 40 + ((x * 7 + y * 13) % 5) * 4
                row.append((v, v, v + 20, 255))
            else:
                glow = 3 <= x <= 12 and 3 <= y <= 9
                if glow:
                    row.append((60, 220, 120, 255) if (x + y) % 3 else (30, 150, 80, 255))
                else:
                    v = 50 + ((x * 5 + y * 11) % 4) * 5
                    row.append((v, v, v + 14, 255))
        px.append(row)
    return px


def shop(top):
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            border = x in (0, 15) or y in (0, 15)
            if border:
                row.append((90, 60, 20, 255))
            elif top:
                v = 190 + ((x * 7 + y * 13) % 4) * 8
                row.append((v, int(v * 0.8), 40, 255))
            else:
                dx, dy = x - 7.5, y - 7.5
                coin = math.hypot(dx, dy) < 4.8
                core = math.hypot(dx, dy) < 2.6
                if core:
                    row.append((120, 60, 190, 255))
                elif coin:
                    row.append((250, 215, 80, 255))
                else:
                    v = 120 + ((x * 5 + y * 9) % 3) * 6
                    row.append((v, int(v * 0.7), 40, 255))
        px.append(row)
    return px


os.makedirs(OUT, exist_ok=True)
png(os.path.join(OUT, 'arena_shop_top.png'), shop(True))
png(os.path.join(OUT, 'arena_shop_side.png'), shop(False))
png(os.path.join(OUT, 'arena_portal.png'), portal())
open(os.path.join(OUT, 'arena_portal.png.mcmeta'), 'w').write('{"animation":{"frametime":3,"interpolate":true}}\n')
png(os.path.join(OUT, 'arena_terminal_top.png'), terminal(True))
png(os.path.join(OUT, 'arena_terminal_side.png'), terminal(False))


def token():
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 7.2:
                row.append((0, 0, 0, 0))
            elif d > 6.0:
                row.append((196, 150, 40, 255))
            elif d > 4.6:
                row.append((240, 200, 70, 255))
            else:
                rune = abs(dx) < 1.2 or abs(dy) < 1.2 or abs(abs(dx) - abs(dy)) < 1.0
                row.append((140, 70, 210, 255) if rune else (70, 30, 120, 255))
        px.append(row)
    return px


ITEM_OUT = os.path.join(OUT, '..', 'item')
os.makedirs(ITEM_OUT, exist_ok=True)
png(os.path.join(ITEM_OUT, 'arena_token.png'), token())
