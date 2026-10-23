package de.chronicle.arena.client;

import de.chronicle.arena.ChronicleArena;
import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal der Arena: grosses Fenster mit Reitern (Modus, Begegnung, Optionen, Challenges) links,
 * Inhalt rechts und Fussleiste mit Start. Alles selbst gezeichnet (eigene Hintergrund- und Schalter-Texturen).
 */
public class TerminalScreen extends Screen {
    private static final int[] TIER_COLORS = {0xCD7F32, 0xC0C0C0, 0xFFD700, 0xB366FF, 0xFF5555};
    private static final int GOLD = 0xFFD36B, TEXT = 0xE8E0F8, MUTED = 0x9A90B0, GREEN = 0x6BE08A, RED = 0xFF7777;
    private static final ResourceLocation BG = ChronicleArena.id("textures/gui/terminal/background.png");
    private static final ResourceLocation SWITCH_ON = ChronicleArena.id("textures/gui/terminal/switch_on.png");
    private static final ResourceLocation SWITCH_OFF = ChronicleArena.id("textures/gui/terminal/switch_off.png");
    /** Terminal-Flag fuer Burg halten (wie ArenaManager.MODE_SIEGE). */
    private static final int MODE_SIEGE = 512;

    static final String[] TABS = {"Modus", "Begegnung", "Optionen", "Challenges"};
    private static final String[] TAB_ICONS = {"minecraft:netherite_sword", "minecraft:zombie_head", "minecraft:comparator", "minecraft:clock"};
    static final String[] MODE_NAMES = {"Wellen", "Bosskampf", "Burg halten"};
    private static final String[] MODE_ICONS = {"minecraft:iron_sword", "minecraft:wither_skeleton_skull", "minecraft:amethyst_cluster"};
    static final String[] MODE_DESC = {
            "Welle um Welle. Missionen, Gruppenweg und Segen. Abgrund = endlos.",
            "Ein grosser Boss mit drei Phasen. Bei halbem Leben kommen Helfer.",
            "Tower Defense: Tuerme bauen, Fallen legen, den Kristall halten."};
    private static final String[] ARENAS = {"Auto (nach Gegner)", "Klassisch", "Glut", "Eis"};

    private static String lastEncounter = "", lastTier = "";
    private static int lastMask, lastArena, lastMode, lastTab;
    private static boolean goals = true;
    private final Net.OpenTerminal data;
    private int tab = lastTab;
    /** 0 = Wellen, 1 = Boss, 2 = Burg halten (nutzt Wellen-Begegnungen). */
    private int mode;
    private int encIndex, tierIndex, mask, arenaChoice, listScroll;
    private int x0, y0, x1, y1;

    /** Klickflaechen des letzten Bildes (das Fenster wird jedes Bild neu aufgebaut). */
    private record Hit(int x, int y, int w, int h, Runnable action, String tip) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> next = new ArrayList<>();

    public TerminalScreen(Net.OpenTerminal data) {
        super(Component.translatable("gui.chronicle_arena.title"));
        this.data = data;
    }

    static void clearLast() { lastEncounter = ""; lastTier = ""; lastMask = 0; lastArena = 0; lastMode = 0; }

    private boolean bossMode() { return mode == 1; }

    private List<Net.EncInfo> current() {
        List<Net.EncInfo> out = new ArrayList<>();
        for (Net.EncInfo e : data.encounters()) if (e.boss() == bossMode()) out.add(e);
        return out;
    }

    private Net.EncInfo encounter() {
        List<Net.EncInfo> list = current();
        return list.isEmpty() ? null : list.get(Math.min(encIndex, list.size() - 1));
    }

    private Net.TierInfo tier() { return data.tiers().isEmpty() ? null : data.tiers().get(Math.min(tierIndex, data.tiers().size() - 1)); }

    private int tierColor(int i) { return TIER_COLORS[Math.min(i, TIER_COLORS.length - 1)]; }

    private int bonusPercent() {
        int b = 0;
        for (Net.ModInfo m : data.mods()) if ((mask & m.bit()) != 0) b += m.bonus();
        return b;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override protected void init() {
        int w = Math.min(460, width - 16), h = Math.min(286, height - 16);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        x1 = x0 + w;
        y1 = y0 + h;
    }

    private void setMode(int m) {
        if (m != mode) { encIndex = 0; listScroll = 0; }
        mode = m;
    }

    private void restoreLast() {
        setMode(lastMode);
        for (Net.EncInfo e : data.encounters()) if (e.id().equals(lastEncounter) && e.boss() != bossMode()) setMode(e.boss() ? 1 : 0);
        List<Net.EncInfo> list = current();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(lastEncounter)) encIndex = i;
        for (int i = 0; i < data.tiers().size(); i++) if (data.tiers().get(i).id().equals(lastTier)) tierIndex = i;
        mask = lastMask;
        arenaChoice = lastArena;
    }

    private void applyChallenge(Net.ChallengeInfo ch) {
        if (ch.encounter().isEmpty()) return;
        for (int i = 0; i < data.tiers().size(); i++) if (data.tiers().get(i).id().equals(ch.tier())) tierIndex = i;
        for (Net.EncInfo e : data.encounters()) {
            if (!e.id().equals(ch.encounter())) continue;
            setMode(e.boss() ? 1 : 0);
            List<Net.EncInfo> list = current();
            for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(ch.encounter())) encIndex = i;
        }
        mask = ch.mask();
    }

    /** Stimmt die aktuelle Auswahl mit der Challenge ueberein? Dann startet der Kampf mit ihrem festen Seed. */
    private boolean selected(Net.ChallengeInfo ch) {
        if (ch.encounter().isEmpty() || mode == 2) return false;
        Net.EncInfo e = encounter();
        Net.TierInfo t = tier();
        return e != null && t != null && e.id().equals(ch.encounter()) && t.id().equals(ch.tier()) && mask == ch.mask();
    }

    private boolean canStart() {
        Net.TierInfo t = tier();
        return !data.running() && encounter() != null && t != null && !t.locked() && !(bossMode() && t.id().endsWith(":abgrund"));
    }

    private void send(boolean spectate) {
        Net.EncInfo e = encounter();
        String enc = e == null ? "" : e.id();
        Net.TierInfo t = tier();
        if (!spectate) { lastEncounter = enc; lastTier = t == null ? "" : t.id(); lastMask = mask; lastArena = arenaChoice; lastMode = mode; }
        // 128 = Tages-, 256 = Wochen-Challenge (Server erzwingt dann Begegnung, Stufe und Modifikatoren).
        int challenge = selected(data.challenge()) ? 128 : selected(data.weekly()) ? 256 : 0;
        PacketDistributor.sendToServer(new Net.Action(spectate, enc, t == null ? "" : t.id(), mask | (arenaChoice << 4) | (goals ? 0 : 64) | challenge | (mode == 2 ? MODE_SIEGE : 0)));
        onClose();
    }

    @Override public void onClose() {
        lastTab = tab;
        super.onClose();
    }

    // ------------------------------------------------------------------ Eingabe

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (h.action != null && h.in(mx, my)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    h.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (tab == 1) {
            listScroll = Math.max(0, Math.min(Math.max(0, current().size() - listRows()), listScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        // Tab / Strg+Tab wechselt die Reiter.
        if (key == 258) {
            tab = (tab + (hasShiftDown() ? TABS.length - 1 : 1)) % TABS.length;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        super.renderBackground(g, mx, my, tick);
        Panel.draw(g, x0, y0, x1, y1, 30, tierColor(tierIndex));
        // Inhaltsflaeche mit Steinkachel.
        int cx0 = x0 + 112, cy0 = y0 + 36, cx1 = x1 - 8, cy1 = y1 - 34;
        g.enableScissor(cx0, cy0, cx1, cy1);
        for (int x = cx0; x < cx1; x += 32) for (int y = cy0; y < cy1; y += 32) g.blit(BG, x, y, 0, 0, 32, 32, 32, 32);
        g.disableScissor();
        g.fill(cx0, cy0, cx1, cy1, 0x66000000);
        frame(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, 0xFF5A2DA0);
        // Seitenleiste.
        g.fillGradient(x0 + 6, y0 + 36, x0 + 106, y1 - 34, 0xCC24173F, 0xCC120B20);
        frame(g, x0 + 5, y0 + 35, x0 + 107, y1 - 33, 0xFF3A2370);
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        next.clear();
        // Kopfzeile.
        g.drawCenteredString(font, "CHRONICLE ARENA", (x0 + x1) / 2, y0 + 7, GOLD);
        g.drawCenteredString(font, data.status(), (x0 + x1) / 2, y0 + 18, data.running() ? 0xFFAA00 : GREEN);
        tabs(g, mx, my);
        summary(g);
        switch (tab) {
            case 0 -> modeTab(g, mx, my);
            case 1 -> encounterTab(g, mx, my);
            case 2 -> optionsTab(g, mx, my);
            default -> challengeTab(g, mx, my);
        }
        footer(g, mx, my);
        hits.clear();
        hits.addAll(next);
        for (Hit h : hits) {
            if (h.tip != null && !h.tip.isEmpty() && h.in(mx, my)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String part : h.tip.split("\n")) lines.addAll(font.split(Component.literal(part), 240));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

    private void hit(int x, int y, int w, int h, Runnable action, String tip) { next.add(new Hit(x, y, w, h, action, tip)); }

    /** Knopf im Arena-Stil; style 0 = normal, 1 = Hauptknopf (gold), 2 = gewaehlt. */
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, int style, int mx, int my, Runnable action, String tip) {
        boolean hover = active && mx >= x && mx < x + w && my >= y && my < y + h;
        int border = !active ? 0xFF2A2440 : style == 1 ? 0xFFFFD36B : style == 2 ? 0xFFFFD36B : hover ? 0xFFB98CFF : 0xFF6B4BB0;
        int top = !active ? 0xFF1E1830 : style == 1 ? (hover ? 0xFF8A6A1E : 0xFF6E521A) : style == 2 ? 0xFF4A3480 : hover ? 0xFF3E2C6E : 0xFF2C1F50;
        int bottom = !active ? 0xFF151022 : style == 1 ? 0xFF3A2A0C : 0xFF171026;
        g.fill(x, y, x + w, y + h, border);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFFFFF);
        int color = !active ? 0x6A6080 : style == 1 ? 0xFFF2C8 : TEXT;
        g.drawCenteredString(font, font.plainSubstrByWidth(label, w - 6), x + w / 2, y + (h - 8) / 2, color);
        hit(x, y, w, h, active ? action : null, tip);
    }

    private void item(GuiGraphics g, String id, int x, int y, float scale) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)), 0, 0);
        g.pose().popPose();
    }

    private int wrap(GuiGraphics g, String text, int x, int y, int w, int maxLines, int color) {
        int n = 0;
        for (FormattedCharSequence line : font.split(FormattedText.of(text), w)) {
            if (n >= maxLines) break;
            g.drawString(font, line, x, y + n * 10, color);
            n++;
        }
        return n;
    }

    private void tabs(GuiGraphics g, int mx, int my) {
        int x = x0 + 10, y = y0 + 42;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            boolean sel = tab == i, hover = mx >= x && mx < x + 92 && my >= y && my < y + 22;
            g.fill(x, y, x + 92, y + 22, sel ? 0xFFFFD36B : hover ? 0xFF6B4BB0 : 0xFF2A1F45);
            g.fillGradient(x + 1, y + 1, x + 92 - (sel ? 0 : 1), y + 21, sel ? 0xFF4A3480 : 0xFF2C1F50, sel ? 0xFF2A1D4A : 0xFF171026);
            if (sel) g.fill(x, y + 1, x + 3, y + 21, 0xFFFFD36B);
            item(g, TAB_ICONS[i], x + 5, y + 3, 1.0F);
            g.drawString(font, TABS[i], x + 25, y + 7, sel ? GOLD : TEXT);
            hit(x, y, 92, 22, () -> {
                tab = t;
                if (t == 1) listScroll = Math.max(0, encIndex - listRows() / 2);
            }, null);
            y += 26;
        }
    }

    /** Kurze Zusammenfassung der Auswahl unter den Reitern. */
    private void summary(GuiGraphics g) {
        int x = x0 + 12, y = y0 + 42 + TABS.length * 26 + 6;
        g.fill(x - 2, y - 4, x + 90, y - 3, 0xFF3A2370);
        g.drawString(font, "Auswahl", x, y, MUTED);
        g.drawString(font, font.plainSubstrByWidth(MODE_NAMES[mode], 88), x, y + 12, GOLD);
        Net.EncInfo e = encounter();
        g.drawString(font, font.plainSubstrByWidth(e == null ? "-" : e.name(), 88), x, y + 23, TEXT);
        Net.TierInfo t = tier();
        g.drawString(font, font.plainSubstrByWidth(t == null ? "-" : t.name(), 88), x, y + 34, tierColor(tierIndex));
        int bonus = bonusPercent();
        if (bonus > 0) g.drawString(font, "Bonus +" + bonus + "%", x, y + 45, 0xFFD700);
        if (selected(data.challenge()) || selected(data.weekly())) g.drawString(font, selected(data.challenge()) ? "Tages-Challenge" : "Wochen-Challenge", x, y + 56, GREEN);
    }

    // ---- Reiter Modus

    private void modeTab(GuiGraphics g, int mx, int my) {
        int cx0 = x0 + 120, cx1 = x1 - 16, y = y0 + 44;
        g.drawString(font, "Modus waehlen", cx0, y, GOLD);
        y += 14;
        int cw = (cx1 - cx0 - 16) / 3, ch = Math.min(124, y1 - 34 - y - 46);
        for (int i = 0; i < 3; i++) {
            final int m = i;
            int x = cx0 + i * (cw + 8);
            boolean sel = mode == i, hover = mx >= x && mx < x + cw && my >= y && my < y + ch;
            g.fill(x, y, x + cw, y + ch, sel ? 0xFFFFD36B : hover ? 0xFF9B5CFF : 0xFF4A3480);
            g.fillGradient(x + 1, y + 1, x + cw - 1, y + ch - 1, sel ? 0xF0402A70 : 0xF02A1D4A, 0xF0120B20);
            if (sel) g.fill(x + 1, y + ch - 4, x + cw - 1, y + ch - 1, 0xFFFFD36B);
            item(g, MODE_ICONS[i], x + cw / 2 - 16, y + 8, 2.0F);
            g.drawCenteredString(font, MODE_NAMES[i], x + cw / 2, y + 44, sel ? GOLD : TEXT);
            if (i == 2) g.drawCenteredString(font, "NEU", x + cw - 14, y + 5, 0xFF5555);
            wrap(g, MODE_DESC[i], x + 6, y + 58, cw - 12, (ch - 62) / 10, sel ? 0xD8D0E8 : MUTED);
            hit(x, y, cw, ch, () -> setMode(m), null);
        }
        y += ch + 8;
        g.drawString(font, "Demnaechst", cx0, y, MUTED);
        y += 12;
        int sw = (cx1 - cx0 - 8) / 2;
        String[][] soon = {{"minecraft:compass", "Expedition", "Raumkette mit Weg-Karte"}, {"minecraft:iron_door", "Bunker", "Ueberleben in Raeumen"}};
        for (int i = 0; i < 2; i++) {
            int x = cx0 + i * (sw + 8);
            g.fill(x, y, x + sw, y + 26, 0xFF2A2440);
            g.fill(x + 1, y + 1, x + sw - 1, y + 25, 0xF0161020);
            item(g, soon[i][0], x + 5, y + 5, 1.0F);
            g.drawString(font, soon[i][1], x + 25, y + 4, 0x8A80A0);
            g.drawString(font, font.plainSubstrByWidth(soon[i][2], sw - 30), x + 25, y + 15, 0x6A6080);
        }
    }

    // ---- Reiter Begegnung

    private int listRows() { return Math.max(3, (y1 - 34 - (y0 + 58) - 62) / 20); }

    private void encounterTab(GuiGraphics g, int mx, int my) {
        int cx0 = x0 + 120, cx1 = x1 - 16, y = y0 + 44;
        List<Net.EncInfo> list = current();
        g.drawString(font, (mode == 1 ? "Boss" : "Begegnung") + " (" + list.size() + ")", cx0, y, GOLD);
        button(g, cx0 + 120, y - 3, 56, 14, "Suchen", !list.isEmpty(), 0, mx, my, () ->
                minecraft.setScreen(new EncounterScreen(this, list, id -> {
                    for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) encIndex = i;
                })), "Liste mit Suchfeld oeffnen");
        y += 14;
        int lw = 176, rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - rows)));
        for (int r = 0; r < rows && listScroll + r < list.size(); r++) {
            final int idx = listScroll + r;
            Net.EncInfo e = list.get(idx);
            int ry = y + r * 20;
            boolean sel = idx == Math.min(encIndex, list.size() - 1), hover = mx >= cx0 && mx < cx0 + lw && my >= ry && my < ry + 19;
            g.fill(cx0, ry, cx0 + lw, ry + 19, sel ? 0xFFFFD36B : hover ? 0xFF6B4BB0 : 0xFF2A1F45);
            g.fill(cx0 + 1, ry + 1, cx0 + lw - 1, ry + 18, sel ? 0xF0402A70 : 0xF01A1230);
            item(g, e.icon(), cx0 + 2, ry + 2, 0.94F);
            g.drawString(font, font.plainSubstrByWidth(e.name(), lw - 26), cx0 + 22, ry + 6, sel ? GOLD : TEXT);
            hit(cx0, ry, lw, 19, () -> encIndex = idx, e.desc());
        }
        if (list.size() > rows) {
            int track = rows * 20 - 2, bar = Math.max(10, track * rows / list.size());
            int pos = (track - bar) * listScroll / Math.max(1, list.size() - rows);
            g.fill(cx0 + lw + 2, y, cx0 + lw + 4, y + track, 0xFF2A1F45);
            g.fill(cx0 + lw + 2, y + pos, cx0 + lw + 4, y + pos + bar, 0xFF9B5CFF);
        }
        // Rechts: Details zur Begegnung.
        int dx = cx0 + lw + 10, dw = cx1 - dx;
        Net.EncInfo e = encounter();
        if (e != null) {
            item(g, e.icon(), dx, y, 2.0F);
            int n = 0;
            for (FormattedCharSequence line : font.split(FormattedText.of(e.name()), dw - 38)) {
                if (n >= 2) break;
                g.drawString(font, line, dx + 38, y + 6 + n * 10, GOLD);
                n++;
            }
            wrap(g, e.desc(), dx, y + 38, dw, (rows * 20 - 40) / 10, 0xCCCCCC);
        } else {
            g.drawString(font, "Keine verfuegbar", dx, y, MUTED);
        }
        // Unten: Stufen und Arena.
        int ty = y + rows * 20 + 6;
        g.drawString(font, "Stufe", cx0, ty, MUTED);
        int tx = cx0 + 34, tw = Math.max(40, (cx1 - tx - 4 * 4) / Math.max(1, data.tiers().size()));
        for (int i = 0; i < data.tiers().size(); i++) {
            final int ti = i;
            Net.TierInfo t = data.tiers().get(i);
            int x = tx + i * (tw + 4);
            boolean sel = i == tierIndex, hover = mx >= x && mx < x + tw && my >= ty - 4 && my < ty + 14;
            int col = tierColor(i);
            g.fill(x, ty - 4, x + tw, ty + 14, sel ? 0xFF000000 | col : hover ? 0xFF6B4BB0 : 0xFF2A1F45);
            g.fill(x + 1, ty - 3, x + tw - 1, ty + 13, sel ? 0xF0302040 : 0xF01A1230);
            g.drawCenteredString(font, font.plainSubstrByWidth(t.name(), tw - 4), x + tw / 2, ty + 1, t.locked() ? 0x7A6A7A : col);
            hit(x, ty - 4, tw, 18, () -> tierIndex = ti, t.name() + (t.locked() ? " (gesperrt)" : "") + "\n" + t.desc() + (t.info().isEmpty() ? "" : "\n" + t.info()));
        }
        Net.TierInfo t = tier();
        if (t != null) g.drawString(font, font.plainSubstrByWidth(t.info().isEmpty() ? t.desc() : t.info(), cx1 - cx0), cx0, ty + 20, t.locked() ? RED : 0x88DD88);
        int ay = ty + 34;
        g.drawString(font, mode == 2 ? "Karte" : "Arena", cx0, ay + 3, MUTED);
        if (mode == 2) {
            g.drawString(font, "Burghof (fest)", cx0 + 34, ay + 3, TEXT);
        } else {
            for (int i = 0; i < ARENAS.length; i++) {
                final int a = i;
                int aw = (cx1 - cx0 - 34 - 12) / 4;
                button(g, cx0 + 34 + i * (aw + 4), ay, aw, 14, ARENAS[i].replace(" (nach Gegner)", ""), true, arenaChoice == i ? 2 : 0, mx, my,
                        () -> arenaChoice = a, i == 0 ? "Arena passend zu den Gegnern" : null);
            }
        }
    }

    // ---- Reiter Optionen

    private void toggle(GuiGraphics g, int x, int y, int w, boolean on, boolean active, String name, String right, String desc, int mx, int my, Runnable action) {
        boolean hover = active && mx >= x && mx < x + w && my >= y && my < y + 22;
        g.fill(x, y, x + w, y + 22, hover ? 0xFF6B4BB0 : on ? 0xFF4A3480 : 0xFF2A1F45);
        g.fill(x + 1, y + 1, x + w - 1, y + 21, on ? 0xF0302048 : 0xF01A1230);
        g.blit(on ? SWITCH_ON : SWITCH_OFF, x + 4, y + 5, 0, 0, 24, 12, 24, 12);
        g.drawString(font, font.plainSubstrByWidth(name, w - 40 - (right.isEmpty() ? 0 : font.width(right) + 8)), x + 34, y + 7, !active ? 0x6A6080 : on ? 0xFFFFFF : 0xBBB0D0);
        if (!right.isEmpty()) g.drawString(font, right, x + w - 6 - font.width(right), y + 7, on ? 0xFFD700 : MUTED);
        hit(x, y, w, 22, active ? action : null, desc);
    }

    private void optionsTab(GuiGraphics g, int mx, int my) {
        int cx0 = x0 + 120, cx1 = x1 - 16, y = y0 + 44;
        g.drawString(font, "Modifikatoren", cx0, y, GOLD);
        g.drawString(font, "mehr Risiko, mehr Coins", cx0 + 90, y, MUTED);
        y += 14;
        int cols = 2, cw = (cx1 - cx0 - 6) / cols;
        for (int i = 0; i < data.mods().size(); i++) {
            Net.ModInfo m = data.mods().get(i);
            int x = cx0 + (i % cols) * (cw + 6), ry = y + (i / cols) * 26;
            toggle(g, x, ry, cw, (mask & m.bit()) != 0, true, m.name(), "+" + m.bonus() + "%", m.desc(), mx, my, () -> mask ^= m.bit());
        }
        y += ((data.mods().size() + cols - 1) / cols) * 26 + 8;
        g.drawString(font, "Ablauf", cx0, y, GOLD);
        y += 14;
        toggle(g, cx0, y, cw, goals && mode != 2, mode != 2, "Zufallsmissionen", mode == 2 ? "nicht bei Burg" : "", goals
                ? "Ab und zu kommt eine Welle mit Zusatzziel (Kristalle, Beacon, Zielgegner, Kontrollpunkte, Lichtkreis)."
                : "Keine Zufallsmissionen in diesem Kampf. Der Missions-Weg bei der Wegwahl ist dann gesperrt.", mx, my, () -> goals = !goals);
        y += 34;
        int bonus = bonusPercent();
        g.drawString(font, "Coins: " + data.coinPercent() + "%" + (bonus > 0 ? "  |  Bonus +" + bonus + "%" : ""), cx0, y, bonus > 0 ? 0xFFD700 : MUTED);
        if (mask != 0) button(g, cx1 - 70, y - 4, 70, 16, "Alle aus", true, 0, mx, my, () -> mask = 0, null);
    }

    // ---- Reiter Challenges

    private void challengeTab(GuiGraphics g, int mx, int my) {
        int cx0 = x0 + 120, cx1 = x1 - 16, y = y0 + 44;
        g.drawString(font, "Challenges", cx0, y, GOLD);
        g.drawString(font, "fester Seed, alle bekommen dieselben Wellen", cx0 + 70, y, MUTED);
        y += 14;
        int ch = (y1 - 34 - y - 14) / 2;
        for (int k = 0; k < 2; k++) {
            Net.ChallengeInfo c = k == 0 ? data.challenge() : data.weekly();
            int top = y + k * (ch + 6);
            boolean sel = selected(c);
            g.fill(cx0, top, cx1, top + ch, sel ? 0xFF6BE08A : 0xFF4A3480);
            g.fillGradient(cx0 + 1, top + 1, cx1 - 1, top + ch - 1, 0xF02A1D4A, 0xF0120B20);
            item(g, k == 0 ? "minecraft:sunflower" : "minecraft:nether_star", cx0 + 6, top + 6, 1.5F);
            g.drawString(font, k == 0 ? "Tages-Challenge" : "Wochen-Challenge", cx0 + 34, top + 6, GOLD);
            if (c.encounter().isEmpty()) {
                g.drawString(font, "Heute keine verfuegbar", cx0 + 34, top + 18, MUTED);
                continue;
            }
            String status = c.claimed() ? "Bonus abgeholt" : "Bonus beim Sieg: +" + c.bonusCoins() + " Coins";
            g.drawString(font, status, cx1 - 8 - font.width(status), top + 6, c.claimed() ? MUTED : 0xFFD700);
            wrap(g, c.text(), cx0 + 34, top + 19, cx1 - cx0 - 44, Math.max(1, (ch - 44) / 10), 0xCCCCCC);
            button(g, cx1 - 96, top + ch - 22, 88, 16, sel ? "Ausgewaehlt" : "Auswaehlen", true, sel ? 2 : 1, mx, my, () -> applyChallenge(c),
                    "Waehlt Begegnung, Stufe und Modifikatoren aus. Danach Vorbereiten.");
        }
    }

    // ---- Fussleiste

    private void footer(GuiGraphics g, int mx, int my) {
        int y = y1 - 26, x = x0 + 8;
        boolean start = canStart();
        String why = data.running() ? "Gerade laeuft ein Kampf. Du kannst zuschauen."
                : encounter() == null ? "Keine Begegnung gewaehlt."
                : tier() != null && tier().locked() ? "Diese Stufe ist noch gesperrt."
                : bossMode() && tier() != null && tier().id().endsWith(":abgrund") ? "Den Abgrund gibt es nicht als Bosskampf." : "Gruppe zusammenstellen und starten.";
        button(g, x, y, 120, 20, "Vorbereiten", start, 1, mx, my, () -> send(false), why);
        button(g, x + 124, y, 80, 20, "Zuschauen", data.running(), 0, mx, my, () -> send(true), null);
        button(g, x + 208, y, 90, 20, "Letzte Auswahl", !lastEncounter.isEmpty(), 0, mx, my, this::restoreLast, null);
        int right = x1 - 8;
        button(g, right - 72, y, 72, 20, "Schliessen", true, 0, mx, my, this::onClose, null);
        if (data.admin()) button(g, right - 72 - 4 - 64, y, 64, 20, "Admin", true, 0, mx, my, () -> PacketDistributor.sendToServer(new Net.Control("admin")), "Admin-Panel");
    }

    @Override public boolean isPauseScreen() { return false; }
}
