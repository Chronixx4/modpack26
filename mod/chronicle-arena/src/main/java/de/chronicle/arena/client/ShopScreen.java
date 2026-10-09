package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arena-Shop: Kategorien links, Raster mit Gegenstaenden in der Mitte, Details und Kaufen rechts.
 * Der Mauszeiger zeigt den vollen Item-Tooltip (Verzauberungen, Werte).
 */
public class ShopScreen extends Screen {
    private static final String[] CATEGORIES = {"verbrauch", "waffen", "ruestung", "werkzeug", "material", "magie"};
    private static final int COLS = 6;
    private static final int ROWS = 5;
    private static final int SLOT = 20;

    private final Net.OpenShop data;
    private final List<Button> tabButtons = new ArrayList<>();
    private Button buyButton;
    private Button prevButton;
    private Button nextButton;
    private int tokens;
    private int category;
    private int page;
    private Net.ShopInfo selected;

    private int left;
    private int top;

    public ShopScreen(Net.OpenShop data) {
        super(Component.translatable("gui.chronicle_arena.shop_title"));
        this.data = data;
        this.tokens = data.tokens();
    }

    public void setTokens(int count) {
        this.tokens = count;
        updateButtons();
    }

    private List<Net.ShopInfo> items() {
        List<Net.ShopInfo> out = new ArrayList<>();
        for (Net.ShopInfo s : data.offers()) if (s.category().equals(CATEGORIES[category])) out.add(s);
        return out;
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String c : CATEGORIES) m.put(c, 0);
        for (Net.ShopInfo s : data.offers()) m.merge(s.category(), 1, Integer::sum);
        return m;
    }

    private int pages() {
        return Math.max(1, (items().size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    @Override
    protected void init() {
        tabButtons.clear();
        left = this.width / 2 - 190;
        top = this.height / 2 - 105;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.translatable("gui.chronicle_arena.cat_" + CATEGORIES[i]), btn -> {
                category = idx;
                page = 0;
                selected = null;
                updateButtons();
            }).bounds(left + 6, top + 36 + i * 24, 84, 20).build();
            tabButtons.add(addRenderableWidget(b));
        }
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + pages() - 1) % pages();
            updateButtons();
        }).bounds(left + 98, top + 36 + ROWS * SLOT + 8, 24, 18).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % pages();
            updateButtons();
        }).bounds(left + 98 + COLS * SLOT - 24, top + 36 + ROWS * SLOT + 8, 24, 18).build());
        buyButton = addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.buy"), b -> {
            if (selected != null) PacketDistributor.sendToServer(new Net.Buy(selected.id()));
        }).bounds(left + 98 + COLS * SLOT + 14, top + 196, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.close"), b -> onClose())
                .bounds(left + 6, top + 36 + CATEGORIES.length * 24 + 12, 84, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        Map<String, Integer> counts = counts();
        for (int i = 0; i < tabButtons.size(); i++) {
            Button b = tabButtons.get(i);
            b.active = i != category && counts.getOrDefault(CATEGORIES[i], 0) > 0;
        }
        prevButton.active = nextButton.active = pages() > 1;
        buyButton.active = selected != null && tokens >= selected.price();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<Net.ShopInfo> list = items();
        int gx = left + 98;
        int gy = top + 36;
        if (mx >= gx && my >= gy && mx < gx + COLS * SLOT && my < gy + ROWS * SLOT) {
            int col = (int) ((mx - gx) / SLOT);
            int row = (int) ((my - gy) / SLOT);
            int idx = page * COLS * ROWS + row * COLS + col;
            if (idx < list.size()) {
                selected = list.get(idx);
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
        gfx.fill(left - 2, top - 2, left + 382, top + 226, 0xFF7A3FD0);
        gfx.fill(left, top, left + 380, top + 224, 0xF012101C);
        gfx.fill(left, top + 28, left + 380, top + 29, 0xFF7A3FD0);
        gfx.fill(left + 94, top + 32, left + 96, top + 220, 0xFF2A2440);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawString(this.font, this.title, left + 8, top + 10, 0xE0B8FF);
        Component tk = Component.translatable("gui.chronicle_arena.tokens", tokens);
        gfx.drawString(this.font, tk, left + 372 - this.font.width(tk), top + 10, 0xFFD700);

        List<Net.ShopInfo> list = items();
        int gx = left + 98;
        int gy = top + 36;
        Net.ShopInfo hover = null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = page * COLS * ROWS + row * COLS + col;
                int x = gx + col * SLOT;
                int y = gy + row * SLOT;
                boolean has = idx < list.size();
                Net.ShopInfo s = has ? list.get(idx) : null;
                int bg = s == null ? 0xFF1A1726 : s == selected ? 0xFF6A3FB0 : tokens >= s.price() ? 0xFF2A2440 : 0xFF3A2430;
                gfx.fill(x, y, x + SLOT - 1, y + SLOT - 1, bg);
                if (s != null) {
                    gfx.renderItem(s.stack(), x + 1, y + 1);
                    gfx.renderItemDecorations(this.font, s.stack(), x + 1, y + 1);
                    if (mouseX >= x && mouseX < x + SLOT - 1 && mouseY >= y && mouseY < y + SLOT - 1) hover = s;
                }
            }
        }
        gfx.drawCenteredString(this.font, Component.translatable("gui.chronicle_arena.page", page + 1, pages()),
                gx + COLS * SLOT / 2, gy + ROWS * SLOT + 13, 0xAAAAAA);

        // Detailbereich rechts.
        int dx = left + 98 + COLS * SLOT + 14;
        if (selected != null) {
            ItemStack st = selected.stack();
            gfx.renderItem(st, dx, top + 36);
            gfx.drawString(this.font, selected.name(), dx + 22, top + 36, 0xFFFFFF);
            gfx.drawString(this.font, Component.translatable("gui.chronicle_arena.tokens", selected.price()), dx + 22, top + 47,
                    tokens >= selected.price() ? 0xFFD700 : 0xFF6666);
            List<Component> tip = Screen.getTooltipFromItem(this.minecraft, st);
            int ly = top + 66;
            for (int i = 1; i < tip.size() && i < 11; i++) {
                gfx.drawString(this.font, this.font.substrByWidth(tip.get(i), 106).getString(), dx, ly, 0xBBBBBB);
                ly += 10;
            }
        } else {
            gfx.drawString(this.font, Component.translatable("gui.chronicle_arena.pick"), dx, top + 40, 0x888888);
        }
        if (hover != null) gfx.renderTooltip(this.font, hover.stack(), mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
