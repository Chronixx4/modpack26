package de.chronicle.arena.client;

import de.chronicle.arena.ArenaNet;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Menue eines Modus (z. B. Bauplatz und Kristall bei Burg halten). Der Server entscheidet, das Fenster zeigt nur an. */
public final class ModeMenuScreen extends Screen {
    private ArenaNet.Menu data;
    private int x, y, w, h, scroll, contentHeight;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Integer> rowY = new ArrayList<>();
    private final List<Integer> rowH = new ArrayList<>();

    public ModeMenuScreen(ArenaNet.Menu data) {
        super(Component.literal(data.title()));
        this.data = data;
    }

    public String id() { return data.id(); }

    public void update(ArenaNet.Menu p) {
        boolean rebuild = !data.options().equals(p.options()) || !data.title().equals(p.title());
        data = p;
        if (rebuild) rebuildWidgets();
    }

    private int descLines(ArenaNet.MenuOption o) { return font.split(Component.literal(o.desc()), w - 40).size(); }

    @Override protected void init() {
        w = Math.min(340, width - 24);
        h = Math.min(300, height - 24);
        x = (width - w) / 2;
        y = (height - h) / 2;
        buttons.clear();
        rowY.clear();
        rowH.clear();
        int offset = 0;
        for (ArenaNet.MenuOption o : data.options()) {
            int rh = 26 + descLines(o) * 10;
            rowY.add(offset);
            rowH.add(rh);
            String label = o.state() == 2 ? "Gewaehlt" : o.price() >= 0 ? o.price() + " Punkte" : "Waehlen";
            Button b = addRenderableWidget(Button.builder(Component.literal(label),
                    v -> PacketDistributor.sendToServer(new ArenaNet.MenuPick(data.id(), o.id()))).bounds(x + w - 92, y + 50 + offset, 80, 18).build());
            b.active = o.state() == 0;
            buttons.add(b);
            offset += rh + 6;
        }
        contentHeight = offset;
        scroll = Math.min(scroll, maxScroll());
        addRenderableWidget(Button.builder(Component.literal("Schliessen"), b -> onClose()).bounds(x + 10, y + h - 27, w - 20, 20).build());
        position();
    }

    private int maxScroll() { return Math.max(0, contentHeight - (h - 90)); }

    private void position() {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            b.setY(y + 50 + rowY.get(i) - scroll);
            b.visible = b.getY() >= y + 46 && b.getY() + 18 <= y + h - 35;
        }
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy) * 20));
        position();
        return true;
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        super.renderBackground(g, mx, my, tick);
        Panel.draw(g, x, y, x + w, y + h, 24, 0x55CCFF);
        g.enableScissor(x + 5, y + 46, x + w - 5, y + h - 35);
        for (int i = 0; i < rowY.size(); i++) {
            int top = y + 48 + rowY.get(i) - scroll;
            ArenaNet.MenuOption o = data.options().get(i);
            int border = o.state() == 2 ? 0xFFFFD36B : o.state() == 1 ? 0xFF3A3050 : 0xFF6B4BB0;
            g.fill(x + 6, top - 2, x + w - 6, top + rowH.get(i), border);
            g.fillGradient(x + 7, top - 1, x + w - 7, top + rowH.get(i) - 1, 0xFF30234A, 0xFF171022);
        }
        g.disableScissor();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        g.drawCenteredString(font, data.title(), width / 2, y + 8, 0xFFFFFF);
        String head = data.subtitle();
        if (data.points() >= 0) head = "Kasse: " + data.points() + " Punkte" + (head.isEmpty() ? "" : " | " + head);
        if (data.seconds() > 0) head += " | noch " + data.seconds() + " s";
        g.drawString(font, font.plainSubstrByWidth(head, w - 20), x + 10, y + 31, 0xCCCCFF);
        g.enableScissor(x + 5, y + 46, x + w - 5, y + h - 35);
        for (int i = 0; i < rowY.size(); i++) {
            ArenaNet.MenuOption o = data.options().get(i);
            int top = y + 50 + rowY.get(i) - scroll;
            ResourceLocation id = ResourceLocation.tryParse(o.icon());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), x + 10, top);
            int color = o.state() == 1 ? 0x888888 : o.state() == 2 ? 0xFFD36B : 0xFFFFFF;
            String label = o.label() + (o.votes() >= 0 ? "  (" + o.votes() + " Stimmen)" : "");
            g.drawString(font, font.plainSubstrByWidth(label, w - 140), x + 30, top + 5, color);
            g.drawWordWrap(font, Component.literal(o.desc()), x + 12, top + 22, w - 40, o.state() == 1 ? 0x888888 : 0xCCCCCC);
        }
        g.disableScissor();
        if (maxScroll() > 0) g.drawString(font, "Scrollen", x + w - 55, y + h - 40, 0xAAAAAA);
    }

    @Override public boolean isPauseScreen() { return false; }
}
