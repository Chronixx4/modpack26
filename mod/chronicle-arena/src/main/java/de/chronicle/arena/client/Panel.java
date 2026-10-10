package de.chronicle.arena.client;

import net.minecraft.client.gui.GuiGraphics;

/** Gemeinsamer Rahmen fuer die Arena-Menues: Verlauf, Doppelrahmen, goldene Ecken, Kopfleiste. */
final class Panel {
    private Panel() {}

    static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, int headerH, int accent) {
        g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, 0xFF1A0F2E);
        g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, 0xFF5A2DA0);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF9B5CFF);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF2A1850);
        g.fillGradient(x0, y0, x1, y1, 0xF5201638, 0xF50B0914);
        g.fillGradient(x0, y0, x1, y0 + headerH, 0xFF3A2370, 0xFF1C1236);
        g.fill(x0, y0 + headerH, x1, y0 + headerH + 1, 0xFFFFD36B);
        g.fill(x0, y0 + headerH + 1, x1, y0 + headerH + 2, 0xFF5A2DA0);
        // Akzentstreifen unten (Farbe der Stufe).
        g.fill(x0, y1 - 3, x1, y1, 0xFF000000 | accent);
        // Goldene Ecken.
        int c = 0xFFFFD36B;
        for (int[] p : new int[][]{{x0 - 4, y0 - 4}, {x1 - 3, y0 - 4}, {x0 - 4, y1 - 3}, {x1 - 3, y1 - 3}}) {
            g.fill(p[0], p[1], p[0] + 7, p[1] + 7, c);
            g.fill(p[0] + 2, p[1] + 2, p[0] + 5, p[1] + 5, 0xFF5A2DA0);
        }
        // Kleine Rauten links und rechts in der Kopfleiste.
        diamond(g, x0 + 10, y0 + headerH / 2, 0xFFFFD36B);
        diamond(g, x1 - 10, y0 + headerH / 2, 0xFFFFD36B);
    }

    static void diamond(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx, cy - 3, cx + 1, cy + 4, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
        g.fill(cx - 3, cy, cx + 4, cy + 1, color);
    }
}
