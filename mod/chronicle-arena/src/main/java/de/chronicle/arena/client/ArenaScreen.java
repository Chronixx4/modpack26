package de.chronicle.arena.client;

import de.chronicle.arena.ChronicleArena;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Gemeinsamer Aufbau der grossen Arena-Fenster (Terminal, Shop, Schmied): Rahmen, Seitenleiste mit Reitern,
 * Inhaltsflaeche mit Steinkachel und selbst gezeichnete Knoepfe. Das Fenster wird jedes Bild neu aufgebaut;
 * Klickflaechen und Tooltips des letzten Bildes gelten fuer die Maus.
 */
abstract class ArenaScreen extends Screen {
    static final int GOLD = 0xFFD36B, TEXT = 0xE8E0F8, MUTED = 0x9A90B0, GREEN = 0x6BE08A, RED = 0xFF7777;
    static final ResourceLocation BG = ChronicleArena.id("textures/gui/terminal/background.png");
    static final ResourceLocation SWITCH_ON = ChronicleArena.id("textures/gui/terminal/switch_on.png");
    static final ResourceLocation SWITCH_OFF = ChronicleArena.id("textures/gui/terminal/switch_off.png");

    int x0, y0, x1, y1;

    record Hit(int x, int y, int w, int h, Runnable action, String tip) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> next = new ArrayList<>();
    /** Item-Tooltip, der nach dem Bild gezeichnet wird (Vorrang vor Text-Tooltips). */
    private ItemStack itemTip = ItemStack.EMPTY;
    private String itemTipExtra = "";

    ArenaScreen(Component title) { super(title); }

    /** Fenstergroesse festlegen (bis maxW x maxH, kleiner bei kleinen Fenstern). */
    void layout(int maxW, int maxH) {
        int w = Math.min(maxW, width - 16), h = Math.min(maxH, height - 16);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        x1 = x0 + w;
        y1 = y0 + h;
    }

    int contentX0() { return x0 + 112; }

    int contentY0() { return y0 + 36; }

    int contentX1() { return x1 - 8; }

    int contentY1() { return y1 - 34; }

    /** Akzentfarbe des unteren Rahmenstreifens. */
    int accent() { return GOLD; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        super.renderBackground(g, mx, my, tick);
        Panel.draw(g, x0, y0, x1, y1, 30, accent());
        int cx0 = contentX0(), cy0 = contentY0(), cx1 = contentX1(), cy1 = contentY1();
        g.enableScissor(cx0, cy0, cx1, cy1);
        for (int x = cx0; x < cx1; x += 32) for (int y = cy0; y < cy1; y += 32) g.blit(BG, x, y, 0, 0, 32, 32, 32, 32);
        g.disableScissor();
        g.fill(cx0, cy0, cx1, cy1, 0x66000000);
        frame(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, 0xFF5A2DA0);
        g.fillGradient(x0 + 6, y0 + 36, x0 + 106, y1 - 34, 0xCC24173F, 0xCC120B20);
        frame(g, x0 + 5, y0 + 35, x0 + 107, y1 - 33, 0xFF3A2370);
    }

    /** Inhalt des Fensters; Klickflaechen ueber hit(), button() usw. anmelden. */
    abstract void draw(GuiGraphics g, int mx, int my);

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        next.clear();
        itemTip = ItemStack.EMPTY;
        itemTipExtra = "";
        draw(g, mx, my);
        hits.clear();
        hits.addAll(next);
        if (!itemTip.isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, itemTip));
            if (!itemTipExtra.isEmpty()) lines.add(Component.literal(itemTipExtra).withColor(GOLD));
            g.renderTooltip(font, lines, itemTip.getTooltipImage(), mx, my);
            return;
        }
        for (Hit h : hits) {
            if (h.tip != null && !h.tip.isEmpty() && h.in(mx, my)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String part : h.tip.split("\n")) lines.addAll(font.split(Component.literal(part), 240));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

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

    void hit(int x, int y, int w, int h, Runnable action, String tip) { next.add(new Hit(x, y, w, h, action, tip)); }

    void itemTooltip(ItemStack stack, String extra) {
        itemTip = stack;
        itemTipExtra = extra == null ? "" : extra;
    }

    static boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    /** Knopf im Arena-Stil; style 0 = normal, 1 = Hauptknopf (gold), 2 = gewaehlt. */
    void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, int style, int mx, int my, Runnable action, String tip) {
        boolean hover = active && over(mx, my, x, y, w, h);
        int border = !active ? 0xFF2A2440 : style == 1 || style == 2 ? 0xFFFFD36B : hover ? 0xFFB98CFF : 0xFF6B4BB0;
        int top = !active ? 0xFF1E1830 : style == 1 ? (hover ? 0xFF8A6A1E : 0xFF6E521A) : style == 2 ? 0xFF4A3480 : hover ? 0xFF3E2C6E : 0xFF2C1F50;
        int bottom = !active ? 0xFF151022 : style == 1 ? 0xFF3A2A0C : 0xFF171026;
        g.fill(x, y, x + w, y + h, border);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFFFFF);
        int color = !active ? 0x6A6080 : style == 1 ? 0xFFF2C8 : TEXT;
        g.drawCenteredString(font, font.plainSubstrByWidth(label, w - 6), x + w / 2, y + (h - 8) / 2, color);
        hit(x, y, w, h, active ? action : null, tip);
    }

    void item(GuiGraphics g, String id, int x, int y, float scale) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return;
        item(g, new ItemStack(BuiltInRegistries.ITEM.get(rl)), x, y, scale);
    }

    void item(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    int wrap(GuiGraphics g, String text, int x, int y, int w, int maxLines, int color) {
        int n = 0;
        for (FormattedCharSequence line : font.split(FormattedText.of(text), w)) {
            if (n >= maxLines) break;
            g.drawString(font, line, x, y + n * 10, color);
            n++;
        }
        return n;
    }

    /** Reiter in der Seitenleiste; gibt die y-Position unter dem letzten Reiter zurueck. */
    int tabs(GuiGraphics g, String[] names, String[] icons, String[] badges, int selected, int step, int mx, int my, IntConsumer pick) {
        int x = x0 + 10, y = y0 + 42, h = step - 4;
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            boolean sel = selected == i, hover = over(mx, my, x, y, 92, h);
            g.fill(x, y, x + 92, y + h, sel ? 0xFFFFD36B : hover ? 0xFF6B4BB0 : 0xFF2A1F45);
            g.fillGradient(x + 1, y + 1, x + 92 - (sel ? 0 : 1), y + h - 1, sel ? 0xFF4A3480 : 0xFF2C1F50, sel ? 0xFF2A1D4A : 0xFF171026);
            if (sel) g.fill(x, y + 1, x + 3, y + h - 1, 0xFFFFD36B);
            item(g, icons[i], x + 4, y + (h - 16) / 2, 1.0F);
            int nameW = badges == null || badges[i] == null ? 66 : 68 - font.width(badges[i]) - 2;
            g.drawString(font, font.plainSubstrByWidth(names[i], nameW), x + 22, y + (h - 8) / 2, sel ? GOLD : TEXT);
            if (badges != null && badges[i] != null) g.drawString(font, badges[i], x + 90 - font.width(badges[i]), y + (h - 8) / 2, MUTED);
            hit(x, y, 92, h, () -> pick.accept(t), null);
            y += step;
        }
        return y;
    }

    /** Muenze und Betrag (rechtsbuendig an x). */
    void coins(GuiGraphics g, int coins, int rightX, int y, int color) {
        String s = String.valueOf(coins);
        int w = font.width(s);
        g.drawString(font, s, rightX - w, y, color);
        item(g, ChronicleArena.MOD_ID + ":arena_token", rightX - w - 13, y - 2, 0.75F);
    }

    @Override public boolean isPauseScreen() { return false; }
}
