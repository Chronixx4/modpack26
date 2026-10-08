#!/usr/bin/env python3
"""Chronicle-Trailer: 20 s, 1920x1080, 60 fps, komplett prozedural (PIL + numpy + ffmpeg).

  python3 tools/trailer/render.py out.mp4          # ganzes Video
  python3 tools/trailer/render.py --still 30 300 900 --dir ordner   # Einzelbilder (Frame-Nummern)
"""
import math, os, subprocess, sys
from multiprocessing import Pool
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS, DUR = 1920, 1080, 60, 20.0
NF = int(FPS * DUR)
ROOT = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(ROOT, '..', '..', 'pack', 'kubejs', 'assets', 'kubejs', 'textures', 'item')
FD = '/usr/share/fonts/opentype/inter/'

C_TECH, C_MAGIE, C_ABEN = (70, 150, 255), (175, 95, 240), (240, 90, 70)
C_GOLD, C_WHITE = (255, 205, 120), (255, 255, 255)

# ---------- Helfer ----------
def cl(x, a=0.0, b=1.0): return max(a, min(b, x))
def sm(x): x = cl(x); return x * x * (3 - 2 * x)
def eo(x): x = cl(x); return 1 - (1 - x) ** 3
def ei(x): x = cl(x); return x ** 3
def eio(x): x = cl(x); return 4 * x ** 3 if x < .5 else 1 - (-2 * x + 2) ** 3 / 2
def eb(x):
    x = cl(x); c1, c3 = 1.70158, 2.70158
    return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2
def lerp(a, b, t): return a + (b - a) * t
def mix(c1, c2, t): return tuple(lerp(c1[i], c2[i], t) for i in range(3))
def sc(c, k): return tuple(int(cl(v * k, 0, 255)) for v in c)
def seg(t, a, b): return cl((t - a) / (b - a))

_fonts = {}
def font(name, size):
    k = (name, size)
    if k not in _fonts: _fonts[k] = ImageFont.truetype(FD + name, size)
    return _fonts[k]

_sprites = {}
def sprite(name, px):
    k = (name, px)
    if k not in _sprites:
        im = Image.open(os.path.join(TEX, name + '.png')).convert('RGBA')
        im = im.resize((256, 256), Image.NEAREST)
        # Pixel-Art etwas heller/satter, damit sie auf dunklem Grund steht
        r, g, b, a = im.split()
        a = a.point(lambda v: 255 if v > 20 else 0)
        im = Image.merge('RGBA', (r, g, b, a))
        _sprites[k] = im
    return _sprites[k]

def put_sprite(art, name, cx, cy, size, ang=0.0, alpha=1.0, boost=1.25):
    if size < 4 or alpha <= 0: return
    im = sprite(name, 0)
    big = im.rotate(ang, Image.BICUBIC, expand=True)
    s = size / 256.0
    big = big.resize((max(1, int(big.width * s)), max(1, int(big.height * s))), Image.BICUBIC)
    r, g, b, a = big.split()
    rgb = Image.merge('RGB', (r, g, b)).point(lambda v: min(255, int(v * boost)))
    a = a.point([int(v * alpha) for v in range(256)])
    art.paste(rgb, (int(cx - big.width / 2), int(cy - big.height / 2)), a)

def text(art, s, cx, cy, fnt, col, alpha=1.0, spacing=0, anchor='m', blur=0):
    """Text mit optionalem Letter-Spacing; anchor m=Mitte, l=links."""
    if alpha <= 0.003 or not s: return
    widths = [fnt.getlength(ch) for ch in s]
    total = sum(widths) + spacing * (len(s) - 1)
    x = cx - total / 2 if anchor == 'm' else cx
    asc, desc = fnt.getmetrics()
    pad = 30
    mw, mh = int(total + 2 * pad), asc + desc + 2 * pad
    mask = Image.new('L', (mw, mh), 0)
    d = ImageDraw.Draw(mask)
    px = pad
    for ch, w in zip(s, widths):
        d.text((px, pad), ch, font=fnt, fill=255)
        px += w + spacing
    if blur: mask = mask.filter(ImageFilter.GaussianBlur(blur))
    mask = mask.point([int(v * alpha) for v in range(256)])
    art.paste(tuple(int(v) for v in col), (int(x - pad), int(cy - mh / 2)), mask)

def letters(art, s, cx, cy, fnt, col, t, t0, stagger, dur, spacing0, spacing1, alpha=1.0):
    """Buchstabe fuer Buchstabe einblenden (Alpha + Verschiebung nach oben)."""
    widths = [fnt.getlength(ch) for ch in s]
    sp = spacing1 + (spacing0 - spacing1) * (1 - eo(seg(t, t0, t0 + stagger * len(s) + dur)))
    total = sum(widths) + sp * (len(s) - 1)
    x = cx - total / 2
    for i, (ch, w) in enumerate(zip(s, widths)):
        p = eo(seg(t, t0 + i * stagger, t0 + i * stagger + dur))
        text(art, ch, x + w / 2, cy + (1 - p) * 40, fnt, col, alpha * p, 0)
        x += w + sp

# ---------- Hintergrund ----------
def make_bg():
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - W / 2) / (W * .62)) ** 2 + ((yy - H / 2) / (H * .75)) ** 2)
    v = np.clip(1 - d, 0, 1) ** 1.6
    base = np.zeros((H, W, 3), np.float32)
    base[..., 0] = 6 + 10 * v
    base[..., 1] = 7 + 12 * v
    base[..., 2] = 14 + 24 * v
    rng = np.random.default_rng(7)
    base += rng.uniform(-1.2, 1.2, (H, W, 1)).astype(np.float32)   # Dithering gegen Banding
    return base

BG = None
rng0 = np.random.default_rng(42)
NP = 170
P = dict(x=rng0.uniform(0, W, NP), y=rng0.uniform(0, H, NP), v=rng0.uniform(14, 60, NP),
         s=rng0.uniform(1.0, 3.6, NP), ph=rng0.uniform(0, 6.28, NP), sw=rng0.uniform(10, 60, NP),
         z=rng0.uniform(0.3, 1, NP))

# ---------- Szenen-Parameter ----------
def tint_color(t):
    keys = [(0, (150, 120, 255)), (3.8, (150, 120, 255)), (4.6, C_TECH), (6.4, C_TECH), (7.2, C_MAGIE),
            (9.0, C_MAGIE), (9.8, C_ABEN), (11.6, C_ABEN), (12.6, (210, 170, 255)), (14.0, C_GOLD),
            (20, C_GOLD)]
    for (ta, ca), (tb, cb) in zip(keys, keys[1:]):
        if ta <= t <= tb: return mix(ca, cb, sm((t - ta) / (tb - ta)))
    return keys[-1][1]

def core_state(t):
    """(x, y, size, alpha) des Chronicle-Kerns."""
    if t < 14.0: return None
    a = eb(seg(t, 14.0, 14.7))
    size = 360 * a
    k = eio(seg(t, 15.3, 16.2))
    x, y = 960, lerp(540, 285, k)
    size = lerp(size, 210, k)
    y += math.sin(t * 2.2) * 8 * k
    return x, y, size

# ---------- Zeichnen ----------
def ring(d, cx, cy, r, ang, col, n, gap, width, sweep=1.0):
    step = 360.0 / n
    for i in range(n):
        s = ang + i * step
        e = s + step * (1 - gap)
        if (i + 1) / n > sweep: e = s + step * (1 - gap) * cl(sweep * n - i)
        if e > s: d.arc((cx - r, cy - r, cx + r, cy + r), s, e, fill=col, width=width)

def ticks(d, cx, cy, r, ang, col, n, ln, width):
    for i in range(n):
        a = math.radians(ang + 360.0 * i / n)
        d.line((cx + math.cos(a) * r, cy + math.sin(a) * r, cx + math.cos(a) * (r + ln), cy + math.sin(a) * (r + ln)), fill=col, width=width)

def gear(d, cx, cy, r, teeth, ang, col, width):
    pts = []
    for i in range(teeth):
        a0 = ang + 360.0 * i / teeth
        for da, rr in ((0, r), (.18, r), (.28, r * 1.2), (.58, r * 1.2), (.68, r)):
            a = math.radians(a0 + da * 360.0 / teeth)
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.line(pts + [pts[0]], fill=col, width=width, joint='curve')
    d.ellipse((cx - r * .55, cy - r * .55, cx + r * .55, cy + r * .55), outline=col, width=width)
    for i in range(6):
        a = math.radians(ang + 60 * i)
        d.line((cx + math.cos(a) * r * .55, cy + math.sin(a) * r * .55, cx + math.cos(a) * r * .9, cy + math.sin(a) * r * .9), fill=col, width=width)

def polygon_line(d, pts, col, width):
    d.line(pts + [pts[0]], fill=col, width=width, joint='curve')

def star(d, cx, cy, r, n, step, ang, col, width):
    pts = [(cx + math.cos(math.radians(ang + 360.0 * (i * step) / n)) * r, cy + math.sin(math.radians(ang + 360.0 * (i * step) / n)) * r) for i in range(n)]
    d.line(pts + [pts[0]], fill=col, width=width, joint='curve')

def icon_tech(d, cx, cy, t, col, k):
    gear(d, cx, cy, 190 * k, 14, t * 18, col, 4)
    gear(d, cx + 285 * k, cy + 190 * k, 100 * k, 8, -t * 18 * 14 / 8 + 11, sc(col, .7), 3)
    gear(d, cx - 270 * k, cy + 215 * k, 70 * k, 6, -t * 18 * 14 / 6 + 30, sc(col, .55), 3)

def icon_magie(d, cx, cy, t, col, k):
    ring(d, cx, cy, 250 * k, t * 14, col, 3, .08, 4)
    ring(d, cx, cy, 215 * k, -t * 22, sc(col, .7), 12, .35, 3)
    ticks(d, cx, cy, 258 * k, -t * 8, sc(col, .6), 48, 14, 2)
    star(d, cx, cy, 200 * k, 6, 2, t * 20, col, 4)
    star(d, cx, cy, 200 * k, 6, 1, t * 20, sc(col, .55), 3)
    star(d, cx, cy, 110 * k, 5, 2, -t * 30, sc(col, .8), 3)

def icon_aben(d, cx, cy, t, col, k):
    ring(d, cx, cy, 245 * k, t * 10, col, 4, .12, 4)
    ring(d, cx, cy, 210 * k, -t * 16, sc(col, .6), 24, .5, 3)
    ticks(d, cx, cy, 250 * k, 0, sc(col, .7), 72, 12, 2)
    # Kompassrose
    a0 = math.sin(t * 1.3) * 12
    for i in range(8):
        a = math.radians(i * 45 + a0)
        L = 215 * k if i % 2 == 0 else 120 * k
        w = math.radians(9 if i % 2 == 0 else 14)
        pts = [(cx + math.cos(a) * L, cy + math.sin(a) * L), (cx + math.cos(a + w) * 28 * k, cy + math.sin(a + w) * 28 * k),
               (cx + math.cos(a - w) * 28 * k, cy + math.sin(a - w) * 28 * k)]
        polygon_line(d, pts, col if i % 2 == 0 else sc(col, .6), 3)

SECTIONS = [
    dict(t0=4.0, name='TECHNIK', spr='technik_fragment', col=C_TECH, icon=icon_tech,
         lines=['Create  ·  Mekanism  ·  AE2', 'Draconic Evolution  ·  Oritech', 'Züge, Fabriken und Reaktoren']),
    dict(t0=6.6, name='MAGIE', spr='magie_fragment', col=C_MAGIE, icon=icon_magie,
         lines=['Ars Nouveau  ·  Iron\'s Spells', 'Goety  ·  Ars Elemental', 'Glyphen, Zauber und Rituale']),
    dict(t0=9.2, name='ABENTEUER', spr='abenteuer_fragment', col=C_ABEN, icon=icon_aben,
         lines=['Twilight Forest  ·  Aether', 'Neue Dimensionen  ·  Bosse', 'Waffen, Rüstungen und Beute']),
]
SEC_LEN = 2.6

def frame(i):
    global BG
    if BG is None: BG = make_bg()
    t = i / FPS
    art = Image.new('RGB', (W, H), (0, 0, 0))
    d = ImageDraw.Draw(art)
    tint = tint_color(t)
    flash = 0.0
    cx, cy = W // 2, H // 2

    # --- Partikel ---
    pm = 1.0 if t > 0.4 else t / 0.4
    for k in range(NP):
        spd = P['v'][k] * (1 + 4 * ei(seg(t, 11.9, 14.0)) * 0.0)
        y = (P['y'][k] - spd * t) % (H + 20) - 10
        x = P['x'][k] + math.sin(t * .8 + P['ph'][k]) * P['sw'][k]
        tw = .55 + .45 * math.sin(t * 3 + P['ph'][k])
        r = P['s'][k] * (.7 + .5 * P['z'][k])
        col = sc(mix(tint, C_WHITE, .45), tw * P['z'][k] * pm)
        d.ellipse((x - r, y - r, x + r, y + r), fill=col)

    # --- Szene A: Intro (0 - 4.2) ---
    if t < 4.6:
        fadeA = 1 - sm(seg(t, 3.5, 4.2))
        sw = eo(seg(t, 0.15, 1.9))
        col = sc(mix(tint, C_WHITE, .25), fadeA)
        if sw > 0:
            ring(d, cx, cy, 430, t * 12, col, 6, .06, 4, sw)
            ring(d, cx, cy, 395, -t * 20, sc(col, .6), 40, .45, 3, sw)
            ticks(d, cx, cy, 440, t * 5, sc(col, .55), 96, 14, 2) if sw > .95 else None
            ring(d, cx, cy, 300, t * 30, sc(col, .45), 90, .5, 2, sw)
        f = font('Inter-ExtraBold.otf', 200)
        letters(art, 'CHRONICLE', cx, cy - 20, f, sc(C_WHITE, 1), t, 1.0, .09, .55, 70, 26, fadeA)
        text(art, 'EIN MODPACK FÜR CHRONIXX UND FREUNDE', cx, cy + 150, font('Inter-Medium.otf', 36), sc(mix(tint, C_WHITE, .5), 1),
             eo(seg(t, 2.5, 3.2)) * fadeA, 12)

    # --- Szene B: drei Saeulen (4.0 - 11.8) ---
    for si, sec in enumerate(SECTIONS):
        ts = t - sec['t0']
        if ts < -0.1 or ts > SEC_LEN + 0.1: continue
        pin = eo(seg(ts, 0.0, 0.7))
        pout = ei(seg(ts, SEC_LEN - 0.45, SEC_LEN))
        alpha = pin * (1 - pout)
        if alpha <= 0: continue
        col = sec['col']
        ix = lerp(430, 560, pin) + pout * -120
        iy = cy
        k = lerp(.75, 1.0, pin)
        sec['icon'](d, ix, iy, t + si * 3, sc(col, alpha), k)
        bob = math.sin(t * 2.6 + si) * 14
        put_sprite(art, sec['spr'], ix, iy + bob, 300 * eb(seg(ts, 0.05, .75)) * (1 - .3 * pout), math.sin(t * 1.7 + si) * 10, alpha)
        # Text rechts
        tx = lerp(1060, 980, pin) + pout * 140
        text(art, sec['name'], tx, cy - 110, font('Inter-Black.otf', 118), mix(col, C_WHITE, .55), alpha, 8, 'l')
        wl = 700 * eo(seg(ts, .2, .9))
        d.rectangle((tx, cy - 10, tx + wl, cy - 6), fill=sc(col, alpha))
        for li, ln in enumerate(sec['lines']):
            a2 = eo(seg(ts, .45 + li * .16, 1.0 + li * .16)) * (1 - pout)
            text(art, ln, tx + (1 - a2) * 40, cy + 55 + li * 62, font('Inter-Medium.otf' if li < 2 else 'Inter-Regular.otf', 40 if li < 2 else 34),
                 sc(C_WHITE, .9 if li < 2 else .65), a2, 2, 'l')

    # --- Szene C: Verschmelzung (11.6 - 14.0) ---
    if 11.6 <= t < 14.6:
        p = seg(t, 11.9, 14.0)
        form = eo(seg(t, 11.6, 12.4))
        for k, (nm, c) in enumerate((('technik_fragment', C_TECH), ('magie_fragment', C_MAGIE), ('abenteuer_fragment', C_ABEN))):
            for j in range(14, -1, -1):
                tt = t - j * 0.018
                pp = cl((tt - 11.9) / 2.1)
                r = lerp(430, 0, ei(pp) ** .85) * (1 if tt > 11.5 else 0)
                a = 2 * math.pi * k / 3 + lerp(0, 9 * math.pi, pp ** 1.6) - math.pi / 2
                x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * .92
                if j == 0:
                    sz = 190 * form * (1 - .35 * ei(pp))
                    put_sprite(art, nm, x, y, sz, t * 90 * (1 + pp), 1.0)
                else:
                    rr = 12 * (1 - j / 16) * form
                    d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=sc(c, (1 - j / 15) * .9 * form))
        # Sog-Ringe
        for q in range(4):
            u = (p * 2.0 + q / 4.0) % 1.0
            rr = lerp(520, 40, u)
            d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=sc(mix(C_WHITE, tint, .4), (1 - u) * .5 * p * (1 - seg(t, 13.8, 14.0))), width=2)
        ring(d, cx, cy, 460 * (1 - .4 * ei(p)), t * 40, sc(tint, .8 * form), 5, .1, 3)

    # Blitz + Schockwellen
    if t >= 13.85:
        flash = max(eo(1 - seg(t, 14.0, 14.55)) * 1.0, ei(seg(t, 13.85, 14.0)) * (t < 14.0))
        for q in range(3):
            u = seg(t, 14.0 + q * .13, 15.6 + q * .13)
            if 0 < u < 1:
                rr = 1100 * eo(u)
                d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=sc(mix(C_WHITE, C_GOLD, q / 2.5), (1 - u) ** 1.5), width=int(lerp(10, 2, u)))
        # Funken
        if t < 16.5:
            u = t - 14.0
            for s in range(70):
                ang = s * 2.399
                spd = 260 + (s * 37 % 520)
                rr = spd * (1 - math.exp(-u * 2.2)) * 1.2
                x, y = cx + math.cos(ang) * rr, cy + math.sin(ang) * rr + 80 * u * u
                a = cl(1 - u / 2.4)
                r = 2 + (s % 3)
                d.ellipse((x - r, y - r, x + r, y + r), fill=sc(mix(C_GOLD, C_WHITE, (s % 4) / 5), a))

    # --- Kern (ab 14.0) ---
    cs = core_state(t)
    if cs:
        x, y, size = cs
        pulse = 1 + .04 * math.sin(t * 5)
        rr = size * .62 * pulse
        # Runen um den Kern
        base_a = eo(seg(t, 14.4, 15.4))
        ring(d, x, y, size * .78, t * 25, sc(C_GOLD, base_a * .9), 4, .12, 3)
        ring(d, x, y, size * .9, -t * 35, sc(C_GOLD, base_a * .5), 28, .5, 2)
        put_sprite(art, 'chronicle_core', x, y, size * pulse, math.sin(t * 1.5) * 6, 1.0, 1.0)
        # innerer Schimmer
        d.ellipse((x - rr * .35, y - rr * .35, x + rr * .35, y + rr * .35), fill=sc(C_GOLD, .25 + .1 * math.sin(t * 5)))

    if 14.7 <= t < 16.0:
        a = eo(seg(t, 14.8, 15.4)) * (1 - sm(seg(t, 15.5, 15.9)))
        text(art, 'CHRONICLE-KERN', cx, cy + 270, font('Inter-ExtraBold.otf', 84), mix(C_GOLD, C_WHITE, .5), a, 22)
        text(art, 'Technik  +  Magie  +  Abenteuer', cx, cy + 345, font('Inter-Medium.otf', 38), sc(C_WHITE, .8), a * .9, 6)

    # --- Szene D: Zahlen (15.6 - 18.2) ---
    if 15.6 <= t < 18.3:
        stats = [(303, 'MODS', '{}+', 300), (38, 'KAPITEL', '{}', 38), (1419, 'QUESTS', '{}', 1419)]
        out = 1 - sm(seg(t, 17.6, 18.2))
        for k, (n, lab, fmt, _) in enumerate(stats):
            ts = t - (15.7 + k * .22)
            a = eo(seg(ts, 0, .6)) * out
            val = int(n * eo(seg(ts, 0, 1.3)))
            s = f'{val:,}'.replace(',', '.')
            if k == 0: s = f'{val}'
            x = cx + (k - 1) * 520
            text(art, s + ('+' if k == 0 and val >= n else ''), x, 650 + (1 - a) * 30, font('Inter-Black.otf', 150), mix(C_GOLD, C_WHITE, .55 - k * .1), a, 2)
            text(art, lab, x, 760, font('Inter-SemiBold.otf', 44), sc(C_WHITE, .75), a, 16)
            d.rectangle((x - 70 * a, 802, x + 70 * a, 805), fill=sc(C_GOLD, a))

    # --- Szene E: Finale (18.0 - 20) ---
    if t >= 18.3:
        a = eo(seg(t, 18.6, 19.3))
        letters(art, 'CHRONICLE', cx, 650, font('Inter-ExtraBold.otf', 170), C_WHITE, t, 18.4, .045, .5, 40, 24)
        text(art, 'MINECRAFT 1.21.1   ·   NEOFORGE', cx, 775, font('Inter-Medium.otf', 38), sc(mix(C_GOLD, C_WHITE, .4), 1), a, 10)
        wl = 520 * eo(seg(t, 18.8, 19.4))
        d.rectangle((cx - wl, 820, cx + wl, 823), fill=sc(C_GOLD, .9))

    # Fortschrittsbalken unten
    d.rectangle((0, H - 6, int(W * t / DUR), H - 1), fill=sc(mix(tint, C_WHITE, .2), .75))

    # ---------- Komposition ----------
    g1 = art.reduce(2).filter(ImageFilter.GaussianBlur(5)).resize((W, H), Image.BILINEAR)
    far = art.reduce(16)
    fd = ImageDraw.Draw(far)
    # Hintergrund-Tint als weiches Licht
    tr = 46 + 4 * math.sin(t * 1.2)
    tcx, tcy = 60, 34
    amp = 0.5 if t < 11.6 else 0.62
    fd.ellipse((tcx - tr, tcy - tr * .75, tcx + tr, tcy + tr * .75), fill=sc(tint, .16 * amp * 2))
    g2 = far.filter(ImageFilter.GaussianBlur(9)).resize((W, H), Image.BICUBIC)
    out = BG + np.asarray(art, np.float32) + np.asarray(g1, np.float32) * .85 + np.asarray(g2, np.float32) * 1.9
    out = np.minimum(out, 255)
    fade = sm(seg(t, 0, .5)) * (1 - sm(seg(t, 19.55, 20.0)))
    out *= fade
    if flash > 0: out = out + (255 - out) * (flash * .85)
    return np.clip(out, 0, 255).astype(np.uint8).tobytes()

def main():
    a = sys.argv[1:]
    if a and a[0] == '--still':
        i = a.index('--dir') if '--dir' in a else None
        outdir = a[i + 1] if i else '.'
        nums = [int(x) for x in a[1:(i if i else len(a))]]
        os.makedirs(outdir, exist_ok=True)
        for n in nums:
            Image.frombytes('RGB', (W, H), frame(n)).save(os.path.join(outdir, f'f{n:04d}.png'))
        return
    out = a[0] if a else 'Chronicle-Trailer.mp4'
    ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS),
                           '-i', '-', '-c:v', 'libx264', '-preset', 'slow', '-crf', '15', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
                           '-movflags', '+faststart', out], stdin=subprocess.PIPE)
    with Pool(os.cpu_count()) as pool:
        for n, fr in enumerate(pool.imap(frame, range(NF), chunksize=4)):
            ff.stdin.write(fr)
            if n % 120 == 0: print(f'{n}/{NF}', flush=True)
    ff.stdin.close(); ff.wait()
    print('fertig', out)

if __name__ == '__main__':
    main()
