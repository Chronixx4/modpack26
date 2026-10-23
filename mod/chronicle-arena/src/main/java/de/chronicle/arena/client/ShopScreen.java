package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Arena-Shop im Stil des Terminals: Kategorien als Reiter links, Raster mit Angeboten in der Mitte,
 * Details und Kaufen rechts. Suche geht ueber alle Kategorien.
 */
public class ShopScreen extends ArenaScreen {
    private static final String[] CATEGORIES = {"verbrauch", "waffen", "ruestung", "werkzeug", "material", "magie", "kosmetik", "runen"};
    private static final String[] ICONS = {"minecraft:golden_apple", "minecraft:diamond_sword", "minecraft:diamond_chestplate", "minecraft:diamond_pickaxe",
            "minecraft:iron_ingot", "minecraft:enchanted_book", "minecraft:name_tag", "minecraft:amethyst_shard"};
    private static final String[] SORTS = {"Standard", "Preis aufsteigend", "Preis absteigend"};
    private static final int SLOT = 22, DETAIL = 134;

    private static int lastCategory, lastSort;
    private final Net.OpenShop data;
    private int tokens;
    private int category = lastCategory, sort = lastSort, page;
    private Net.ShopInfo selected;
    private EditBox search;

    public ShopScreen(Net.OpenShop data) {
        super(Component.translatable("gui.chronicle_arena.shop_title"));
        this.data = data;
        this.tokens = data.coins();
    }

    public void statsChanged() {
        Net.Stats st = ClientState.stats;
        if (st != null) tokens = st.coins();
    }

    private boolean owned(Net.ShopInfo s) {
        Net.Stats st = ClientState.stats;
        return st != null && st.owned().contains(s.id());
    }

    private boolean equipped(Net.ShopInfo s) {
        Net.Stats st = ClientState.stats;
        return st != null && (st.titleId().equals(s.id()) || st.trailId().equals(s.id()));
    }

    private boolean cosmeticOwned(Net.ShopInfo s) { return !s.cosmetic().isEmpty() && owned(s); }

    private String query() { return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT); }

    private List<Net.ShopInfo> items() {
        String q = query();
        List<Net.ShopInfo> out = new ArrayList<>();
        for (Net.ShopInfo s : data.offers()) {
            if (q.isEmpty() ? s.category().equals(CATEGORIES[category]) : s.name().toLowerCase(Locale.ROOT).contains(q)) out.add(s);
        }
        if (sort == 1) out.sort(Comparator.comparingInt(Net.ShopInfo::price));
        if (sort == 2) out.sort(Comparator.comparingInt(Net.ShopInfo::price).reversed());
        return out;
    }

    private int count(String cat) {
        int n = 0;
        for (Net.ShopInfo s : data.offers()) if (s.category().equals(cat)) n++;
        return n;
    }

    // ------------------------------------------------------------------ Raster

    private int gridX() { return contentX0() + 8; }

    private int gridY() { return contentY0() + 24; }

    private int cols() { return Math.max(4, (contentX1() - DETAIL - 12 - gridX()) / SLOT); }

    private int rows() { return Math.max(3, (contentY1() - 6 - gridY()) / SLOT); }

    private int perPage() { return cols() * rows(); }

    private int pages() { return Math.max(1, (items().size() + perPage() - 1) / perPage()); }

    private void pick(int c) {
        category = c;
        page = 0;
        if (search != null) search.setValue("");
    }

    // ------------------------------------------------------------------ Aufbau

    @Override protected void init() {
        layout(460, 286);
        String old = search == null ? "" : search.getValue();
        // Suchfeld unten in der Seitenleiste, unter den Kategorien.
        search = new EditBox(font, x0 + 15, y1 - 47, 84, 10, Component.literal("Suchen"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setHint(Component.literal("Suchen ...").withColor(0x6A6080));
        search.setValue(old);
        search.setResponder(v -> page = 0);
        addRenderableWidget(search);
    }

    @Override public void onClose() {
        lastCategory = category;
        lastSort = sort;
        super.onClose();
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (pages() > 1) {
            page = Math.floorMod(page - (int) Math.signum(dy), pages());
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        // Tab wechselt die Kategorie, solange das Suchfeld leer ist.
        if (key == 258 && query().isEmpty()) {
            pick((category + (hasShiftDown() ? CATEGORIES.length - 1 : 1)) % CATEGORIES.length);
            return true;
        }
        // Buchstaben gehen ins Suchfeld, auch ohne vorher hineinzuklicken.
        if (search.isFocused() && key != 256) {
            search.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean charTyped(char c, int mods) {
        if (!search.isFocused() && !Character.isISOControl(c)) {
            setFocused(search);
            search.setFocused(true);
        }
        return super.charTyped(c, mods);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        super.renderBackground(g, mx, my, tick);
        // Rahmen fuers Suchfeld (das Feld selbst ist randlos und kommt danach als Widget).
        int sx = search.getX() - 5, sy = search.getY() - 3;
        g.fill(sx, sy, sx + 92, sy + 14, search.isFocused() ? 0xFFB98CFF : 0xFF4A3480);
        g.fill(sx + 1, sy + 1, sx + 91, sy + 13, 0xF0100A1A);
    }

    @Override void draw(GuiGraphics g, int mx, int my) {
        g.drawCenteredString(font, "ARENA-SHOP", (x0 + x1) / 2, y0 + 7, GOLD);
        g.drawCenteredString(font, "Ausruestung fuer deine Kaempfe", (x0 + x1) / 2, y0 + 18, MUTED);
        g.drawString(font, "Guthaben", x1 - 22 - font.width("Guthaben"), y0 + 7, MUTED);
        coins(g, tokens, x1 - 22, y0 + 18, GOLD);

        String[] names = new String[CATEGORIES.length], badges = new String[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            names[i] = Component.translatable("gui.chronicle_arena.cat_" + CATEGORIES[i]).getString();
            badges[i] = String.valueOf(count(CATEGORIES[i]));
        }
        tabs(g, names, ICONS, badges, query().isEmpty() ? category : -1, 24, mx, my, this::pick);

        grid(g, mx, my);
        detail(g, mx, my);
        footer(g, mx, my);
    }

    private void grid(GuiGraphics g, int mx, int my) {
        List<Net.ShopInfo> list = items();
        page = Math.min(page, pages() - 1);
        int gx = gridX(), gy = gridY(), cols = cols(), rows = rows();
        String head = query().isEmpty() ? Component.translatable("gui.chronicle_arena.cat_" + CATEGORIES[category]).getString() : "Suche";
        g.drawString(font, head, gx, contentY0() + 8, GOLD);
        g.drawString(font, list.size() + (list.size() == 1 ? " Angebot" : " Angebote"), gx + font.width(head) + 6, contentY0() + 8, MUTED);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = page * cols * rows + r * cols + c, x = gx + c * SLOT, y = gy + r * SLOT;
                if (idx >= list.size()) {
                    g.fill(x, y, x + SLOT - 2, y + SLOT - 2, 0x80100A1A);
                    continue;
                }
                Net.ShopInfo s = list.get(idx);
                boolean sel = s == selected, hover = over(mx, my, x, y, SLOT - 2, SLOT - 2);
                boolean afford = tokens >= s.price() || cosmeticOwned(s);
                g.fill(x, y, x + SLOT - 2, y + SLOT - 2, sel ? 0xFFFFD36B : hover ? 0xFFB98CFF : afford ? 0xFF4A3480 : 0xFF5A2A3A);
                g.fillGradient(x + 1, y + 1, x + SLOT - 3, y + SLOT - 3, sel ? 0xF0402A70 : afford ? 0xF02A1D4A : 0xF0301424, 0xF0120B20);
                g.renderItem(s.stack(), x + 2, y + 2);
                g.renderItemDecorations(font, s.stack(), x + 2, y + 2);
                if (equipped(s)) g.fill(x + 1, y + SLOT - 5, x + SLOT - 3, y + SLOT - 3, 0xFF6BE08A);
                else if (cosmeticOwned(s)) g.fill(x + SLOT - 7, y + 2, x + SLOT - 4, y + 5, 0xFF6BE08A);
                hit(x, y, SLOT - 2, SLOT - 2, () -> selected = s, null);
                if (hover) itemTooltip(s.stack(), cosmeticOwned(s) ? "Gekauft" : "Preis: " + s.price() + " Coins");
            }
        }
        if (list.isEmpty()) g.drawCenteredString(font, "Nichts gefunden", gx + cols * SLOT / 2, gy + rows * SLOT / 2 - 4, MUTED);
    }

    private void detail(GuiGraphics g, int mx, int my) {
        int dx0 = contentX1() - DETAIL - 4, dx1 = contentX1() - 6, dy0 = contentY0() + 6, dy1 = contentY1() - 6;
        g.fill(dx0, dy0, dx1, dy1, 0xFF4A3480);
        g.fillGradient(dx0 + 1, dy0 + 1, dx1 - 1, dy1 - 1, 0xF02A1D4A, 0xF0120B20);
        int x = dx0 + 6, w = dx1 - dx0 - 12, y = dy0 + 6;
        Net.ShopInfo s = selected;
        if (s == null) {
            item(g, "minecraft:chest", x + w / 2 - 16, y + 30, 2.0F);
            wrap(g, "Waehle links ein Angebot. Mit der Maus darueber siehst du alle Werte.", x, y + 72, w, 5, MUTED);
            return;
        }
        ItemStack st = s.stack();
        item(g, st, x, y, 2.0F);
        if (over(mx, my, x, y, 32, 32)) itemTooltip(st, null);
        int n = 0;
        for (FormattedCharSequence line : font.split(Component.literal(s.name()), w - 36)) {
            if (n >= 3) break;
            g.drawString(font, line, x + 36, y + 2 + n * 10, GOLD);
            n++;
        }
        y += 38;
        g.fill(x, y, x + w, y + 1, 0xFF3A2370);
        y += 5;
        if (cosmeticOwned(s)) {
            g.drawString(font, equipped(s) ? "Gekauft, angelegt" : "Gekauft", x, y, GREEN);
        } else {
            g.drawString(font, "Preis", x, y, MUTED);
            coins(g, s.price(), x + w, y, tokens >= s.price() ? GOLD : RED);
        }
        y += 14;
        // Werte aus dem Item-Tooltip (ohne Namen), so viele wie Platz ist.
        List<Component> tip = getTooltipFromItem(minecraft, st);
        int maxY = dy1 - 30;
        for (int i = 1; i < tip.size(); i++) {
            for (FormattedCharSequence line : font.split(tip.get(i), w)) {
                if (y + 9 > maxY) break;
                g.drawString(font, line, x, y, 0xBBB0D0);
                y += 10;
            }
        }
        String label;
        boolean active;
        if (cosmeticOwned(s)) {
            label = equipped(s) ? "Ablegen" : "Ausruesten";
            active = true;
        } else {
            label = "Kaufen";
            active = tokens >= s.price();
        }
        String why = active ? null : "Dir fehlen " + (s.price() - tokens) + " Coins.";
        button(g, x, dy1 - 26, w, 20, label, active, 1, mx, my, () -> PacketDistributor.sendToServer(new Net.Buy(s.id())), why);
    }

    private void footer(GuiGraphics g, int mx, int my) {
        int y = y1 - 26;
        button(g, x0 + 8, y, 98, 20, "Schliessen", true, 0, mx, my, this::onClose, null);
        int gx = gridX(), gw = cols() * SLOT - 2;
        boolean more = pages() > 1;
        button(g, gx, y, 22, 20, "<", more, 0, mx, my, () -> page = Math.floorMod(page - 1, pages()), null);
        button(g, gx + gw - 22, y, 22, 20, ">", more, 0, mx, my, () -> page = Math.floorMod(page + 1, pages()), null);
        g.drawCenteredString(font, "Seite " + (page + 1) + " / " + pages(), gx + gw / 2, y + 6, more ? TEXT : MUTED);
        int sx = contentX1() - DETAIL - 4;
        button(g, sx, y, contentX1() - 6 - sx, 20, SORTS[sort], true, 0, mx, my, () -> sort = (sort + 1) % SORTS.length, "Sortierung wechseln");
    }
}
