#!/usr/bin/env python3
"""Erzeugt die 16x16-Texturen der Arena-Bloecke (reines Python, ohne PIL)."""
import math, os, struct, zlib

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'chronicle_arena', 'textures', 'block')


def png(path, px):
    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in px)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)


def portal():
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            v = 0.5 + 0.5 * math.sin(a * 3 + d * 1.1)
            edge = max(abs(dx), abs(dy)) > 6.5
            if edge:
                row.append((30, 20, 50, 255))
            else:
                row.append((int(70 + 120 * v), int(20 + 40 * v), int(140 + 110 * v), 255))
        px.append(row)
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


os.makedirs(OUT, exist_ok=True)
png(os.path.join(OUT, 'arena_portal.png'), portal())
png(os.path.join(OUT, 'arena_terminal_top.png'), terminal(True))
png(os.path.join(OUT, 'arena_terminal_side.png'), terminal(False))
