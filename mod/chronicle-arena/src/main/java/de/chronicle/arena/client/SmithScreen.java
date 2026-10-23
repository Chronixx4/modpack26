package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/**
 * Schmied im Stil des Terminals: links Amboss, Guthaben und Gesamtstufe, rechts eine Zeile pro Verbesserung
 * mit Stufenanzeige und Kaufknopf. Der Server oeffnet das Fenster nach jedem Kauf neu, deshalb merkt sich
 * die Liste ihre Scrollposition.
 */
public class SmithScreen extends ArenaScreen {
    private static final int ROW = 28;
    private static final Map<String, String> ICONS = Map.of(
            "schaerfe", "minecraft:iron_sword",
            "haertung", "minecraft:iron_chestplate",
            "lebenskraft", "minecraft:glistering_melon_slice",
            "windlaeufer", "minecraft:feather",
            "schlagtempo", "minecraft:golden_sword",
            "standfest", "minecraft:shield",
            "reichweite", "minecraft:spyglass",
            "lebensfunke", "minecraft:totem_of_undying",
            "glut", "minecraft:blaze_powder");

    private static int scroll;
    private final Net.OpenSmith data;

    public SmithScreen(Net.OpenSmith data) {
        super(Component.literal("Schmied"));
        this.data = data;
    }

    @Override protected void init() {
        layout(440, 300);
        scroll = Math.min(scroll, maxScroll());
    }

    private int rows() { return Math.max(2, (contentY1() - contentY0() - 30) / ROW); }

    private int maxScroll() { return Math.max(0, data.ups().size() - rows()); }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override void draw(GuiGraphics g, int mx, int my) {
        g.drawCenteredString(font, "SCHMIED", (x0 + x1) / 2, y0 + 7, GOLD);
        g.drawCenteredString(font, "Dauerhafte Verbesserungen fuer die Arena", (x0 + x1) / 2, y0 + 18, MUTED);
        sidebar(g);
        list(g, mx, my);
        button(g, x0 + 8, y1 - 26, 98, 20, "Schliessen", true, 0, mx, my, this::onClose, null);
    }

    private void sidebar(GuiGraphics g) {
        int cx = x0 + 56, y = y0 + 46;
        item(g, "minecraft:anvil", cx - 24, y, 3.0F);
        y += 56;
        g.drawCenteredString(font, "Guthaben", cx, y, MUTED);
        y += 11;
        int w = font.width(String.valueOf(data.coins())) + 13;
        coins(g, data.coins(), cx + w / 2, y, GOLD);
        y += 18;
        int level = 0, max = 0;
        for (Net.SmithInfo u : data.ups()) {
            level += u.level();
            max += u.max();
        }
        g.drawCenteredString(font, "Stufen gesamt", cx, y, MUTED);
        g.drawCenteredString(font, level + " / " + max, cx, y + 11, level >= max ? GREEN : TEXT);
        int bx = x0 + 14, bw = 84;
        g.fill(bx, y + 24, bx + bw, y + 28, 0xFF2A1F45);
        if (max > 0) g.fill(bx, y + 24, bx + bw * level / max, y + 28, 0xFFFFD36B);
        y += 40;
        wrap(g, "Wirkt nur in der Arena und bleibt fuer immer.", x0 + 12, y, 90, 4, 0x8A80A0);
    }

    private void list(GuiGraphics g, int mx, int my) {
        int cx0 = contentX0() + 8, cx1 = contentX1() - 8, y = contentY0() + 8;
        g.drawString(font, "Verbesserungen", cx0, y, GOLD);
        g.drawString(font, "Preis steigt mit jeder Stufe", cx0 + font.width("Verbesserungen") + 8, y, MUTED);
        y += 16;
        int rows = rows(), rw = cx1 - cx0 - (maxScroll() > 0 ? 6 : 0);
        for (int r = 0; r < rows && scroll + r < data.ups().size(); r++) {
            Net.SmithInfo u = data.ups().get(scroll + r);
            int ry = y + r * ROW, rh = ROW - 4;
            boolean done = u.level() >= u.max(), afford = data.coins() >= u.price();
            boolean hover = over(mx, my, cx0, ry, rw, rh);
            g.fill(cx0, ry, cx0 + rw, ry + rh, done ? 0xFF3E7A4E : hover ? 0xFF6B4BB0 : 0xFF2A1F45);
            g.fillGradient(cx0 + 1, ry + 1, cx0 + rw - 1, ry + rh - 1, done ? 0xF0203A2A : 0xF02A1D4A, 0xF0120B20);
            item(g, ICONS.getOrDefault(u.id(), "minecraft:anvil"), cx0 + 5, ry + 5, 1.0F);
            g.drawString(font, u.name(), cx0 + 26, ry + 4, done ? GREEN : GOLD);
            // Stufen als kleine Kaestchen hinter dem Namen.
            int px = cx0 + 26 + font.width(u.name()) + 6;
            for (int i = 0; i < u.max(); i++) {
                int c = i < u.level() ? 0xFFFFD36B : 0xFF3A2D5A;
                g.fill(px + i * 7, ry + 5, px + i * 7 + 5, ry + 10, c);
            }
            int bw = 62;
            g.drawString(font, font.plainSubstrByWidth(u.desc(), rw - 26 - bw - 10), cx0 + 26, ry + 15, 0xBBB0D0);
            hit(cx0, ry, rw - bw - 6, rh, null, u.name() + " " + u.level() + "/" + u.max() + "\n" + u.desc());
            String label = done ? "Maximal" : u.price() + " Coins";
            String tip = done ? null : afford ? "Stufe " + (u.level() + 1) + " kaufen" : "Dir fehlen " + (u.price() - data.coins()) + " Coins.";
            button(g, cx0 + rw - bw - 4, ry + 3, bw, rh - 6, label, !done && afford, done ? 2 : 1, mx, my,
                    () -> PacketDistributor.sendToServer(new Net.SmithBuy(u.id())), tip);
        }
        if (maxScroll() > 0) {
            int track = rows * ROW - 4, n = data.ups().size(), bar = Math.max(10, track * rows / n);
            int pos = (track - bar) * scroll / maxScroll();
            g.fill(cx1 - 3, y, cx1 - 1, y + track, 0xFF2A1F45);
            g.fill(cx1 - 3, y + pos, cx1 - 1, y + pos + bar, 0xFF9B5CFF);
        }
    }
}
