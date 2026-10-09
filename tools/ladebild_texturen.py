# Erzeugt die Ladebildschirm-Texturen (Chronicle-Logo, dunkler Menue-Hintergrund) nach pack/kubejs/assets/minecraft/textures/gui/
# Aufruf: python3 tools/ladebild_texturen.py   (braucht Pillow)
import os, random
from PIL import Image, ImageDraw
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = ROOT + '/pack/kubejs/assets/minecraft/textures/gui'
GOLD = (247, 201, 72, 255); WHITE = (236, 232, 220, 255); LO = (14, 15, 18, 255)

# 5x7-Pixelschrift, nur die Buchstaben von CHRONICLE
F = {
 'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
 'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
 'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
 'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
 'N': ['#...#', '##..#', '#.#.#', '#.#.#', '#..##', '#...#', '#...#'],
 'I': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
 'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
 'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
}
WORD = 'CHRONICLE'

def wortmarke(h=256):
    # 1024x256: Logo 4:1, Text mittig; Pixelgroesse so, dass der Text hineinpasst
    W = 1024
    cols = len(WORD) * 5 + (len(WORD) - 1)          # 9 Buchstaben + Luecken = 53 Spalten
    px = (W - 64) // cols                           # Pixelgroesse
    tw, th = cols * px, 7 * px
    x0, y0 = (W - tw) // 2, (h - th) // 2
    im = Image.new('RGBA', (W, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for pas, farbe, dx, dy in ((0, LO, px // 2, px // 2), (1, None, 0, 0)):   # erst Schatten, dann Schrift
        x = x0
        for i, ch in enumerate(WORD):
            for r, zeile in enumerate(F[ch]):
                for c, v in enumerate(zeile):
                    if v == '#':
                        fc = farbe or (GOLD if ch == 'N' else WHITE)
                        d.rectangle([x + c * px + dx, y0 + r * px + dy, x + (c + 1) * px - 1 + dx, y0 + (r + 1) * px - 1 + dy], fill=fc)
            x += 6 * px
    return im

def logo():
    # Minecraft zeichnet mojangstudios.png in zwei Haelften: oben die linke, unten die rechte Haelfte
    w = wortmarke()
    img = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    img.paste(w.crop((0, 0, 512, 256)), (0, 0))
    img.paste(w.crop((512, 0, 1024, 256)), (0, 256))
    return img

def menue_hintergrund():
    rnd = random.Random(26)
    img = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            v = rnd.randint(-5, 5)
            img.putpixel((x, y), (30 + v, 32 + v, 38 + v, 255))
    return img

os.makedirs(OUT + '/title', exist_ok=True)
logo().save(OUT + '/title/mojangstudios.png')
menue_hintergrund().save(OUT + '/menu_background.png')
print('ok')
