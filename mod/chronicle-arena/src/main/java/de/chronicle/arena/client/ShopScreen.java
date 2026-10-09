package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Tauscht Arena-Marken gegen Gegenstaende. */
public class ShopScreen extends Screen {
    private static final int PER_PAGE = 6;

    private final Net.OpenTerminal data;
    private final Screen parent;
    private final List<Button> buyButtons = new ArrayList<>();
    private int tokens;
    private int page;

    public ShopScreen(Net.OpenTerminal data, Screen parent) {
        super(Component.translatable("gui.chronicle_arena.shop_title"));
        this.data = data;
        this.parent = parent;
        this.tokens = data.tokens();
    }

    public void setTokens(int count) {
        this.tokens = count;
        updateButtons();
    }

    private int pages() {
        return Math.max(1, (data.shop().size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        buyButtons.clear();
        int cx = this.width / 2;
        int top = this.height / 2 - 92;
        for (int i = 0; i < PER_PAGE; i++) {
            final int slot = i;
            Button b = Button.builder(Component.translatable("gui.chronicle_arena.buy"), btn -> {
                int idx = page * PER_PAGE + slot;
                if (idx < data.shop().size()) PacketDistributor.sendToServer(new Net.Buy(data.shop().get(idx).id()));
            }).bounds(cx + 56, top + 30 + i * 24, 52, 20).build();
            buyButtons.add(addRenderableWidget(b));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + pages() - 1) % pages();
            updateButtons();
        }).bounds(cx - 108, top + 178, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % pages();
            updateButtons();
        }).bounds(cx - 80, top + 178, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.back"),
                b -> Minecraft.getInstance().setScreen(parent)).bounds(cx + 18, top + 178, 90, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < buyButtons.size(); i++) {
            int idx = page * PER_PAGE + i;
            Button b = buyButtons.get(i);
            b.visible = idx < data.shop().size();
            b.active = b.visible && tokens >= data.shop().get(idx).price();
        }
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 92;
        gfx.fill(cx - 120, top - 6, cx + 120, top + 206, 0xFF7A3FD0);
        gfx.fill(cx - 118, top - 4, cx + 118, top + 204, 0xEE12101C);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 92;
        gfx.drawCenteredString(this.font, this.title, cx, top + 2, 0xE0B8FF);
        gfx.drawCenteredString(this.font, Component.translatable("gui.chronicle_arena.tokens", tokens), cx, top + 16, 0xFFD700);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= data.shop().size()) break;
            Net.ShopInfo s = data.shop().get(idx);
            int y = top + 30 + i * 24;
            ResourceLocation id = ResourceLocation.tryParse(s.item());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                gfx.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id), s.count()), cx - 112, y + 2);
            }
            gfx.drawString(this.font, s.name(), cx - 92, y + 2, 0xFFFFFF);
            gfx.drawString(this.font, Component.translatable("gui.chronicle_arena.tokens", s.price()), cx - 92, y + 12,
                    tokens >= s.price() ? 0xFFD700 : 0xAA5555);
        }
        gfx.drawCenteredString(this.font, Component.translatable("gui.chronicle_arena.page", page + 1, pages()), cx - 60, top + 160, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
