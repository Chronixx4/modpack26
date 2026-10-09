package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreen extends Screen {
    private final Net.OpenTerminal data;
    private boolean bossMode;
    private int encIndex;
    private int tierIndex;
    private Button modeButton;
    private Button encButton;
    private Button tierButton;
    private Button startButton;

    public TerminalScreen(Net.OpenTerminal data) {
        super(Component.translatable("gui.chronicle_arena.title"));
        this.data = data;
        this.tierIndex = 0;
    }

    private List<Net.EncInfo> current() {
        List<Net.EncInfo> out = new ArrayList<>();
        for (Net.EncInfo e : data.encounters()) if (e.boss() == bossMode) out.add(e);
        return out;
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 70;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            bossMode = !bossMode;
            encIndex = 0;
            refresh();
        }).bounds(x, y, w, 20).build());
        encButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            List<Net.EncInfo> list = current();
            if (!list.isEmpty()) encIndex = (encIndex + 1) % list.size();
            refresh();
        }).bounds(x, y + 24, w, 20).build());
        tierButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (!data.tiers().isEmpty()) tierIndex = (tierIndex + 1) % data.tiers().size();
            refresh();
        }).bounds(x, y + 48, w, 20).build());
        startButton = addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.start"), b -> send(false))
                .bounds(x, y + 120, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.spectate"), b -> send(true))
                .bounds(x + 102, y + 120, 98, 20).build()).active = data.running();
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.close"), b -> onClose())
                .bounds(x, y + 144, w, 20).build());
        refresh();
    }

    private void refresh() {
        modeButton.setMessage(Component.translatable(bossMode ? "gui.chronicle_arena.mode_boss" : "gui.chronicle_arena.mode_waves"));
        List<Net.EncInfo> list = current();
        encButton.setMessage(list.isEmpty() ? Component.translatable("gui.chronicle_arena.none")
                : Component.literal(list.get(Math.min(encIndex, list.size() - 1)).name()));
        encButton.active = list.size() > 0;
        tierButton.setMessage(data.tiers().isEmpty() ? Component.translatable("gui.chronicle_arena.none")
                : Component.translatable("gui.chronicle_arena.tier", data.tiers().get(tierIndex).name()));
        startButton.active = !data.running() && !list.isEmpty() && !data.tiers().isEmpty();
    }

    private void send(boolean spectate) {
        List<Net.EncInfo> list = current();
        String enc = list.isEmpty() ? "" : list.get(Math.min(encIndex, list.size() - 1)).id();
        String tier = data.tiers().isEmpty() ? "" : data.tiers().get(tierIndex).id();
        PacketDistributor.sendToServer(new Net.Action(spectate, enc, tier));
        onClose();
    }

    private static final int[] TIER_COLORS = {0xCD7F32, 0xC0C0C0, 0xFFD700, 0xB366FF};

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 70;
        int x0 = cx - 118;
        int x1 = cx + 118;
        int y0 = top - 44;
        int y1 = top + 172;
        gfx.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF7A3FD0);
        gfx.fill(x0, y0, x1, y1, 0xEE12101C);
        gfx.fill(x0, y0 + 26, x1, y0 + 27, 0xFF7A3FD0);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 70;
        gfx.drawCenteredString(this.font, this.title, cx, top - 38, 0xE0B8FF);
        gfx.drawCenteredString(this.font, Component.literal(data.status()), cx, top - 12, data.running() ? 0xFFAA00 : 0x55FF55);

        List<Net.EncInfo> list = current();
        int y = top + 74;
        if (!list.isEmpty()) {
            Net.EncInfo e = list.get(Math.min(encIndex, list.size() - 1));
            ResourceLocation icon = ResourceLocation.tryParse(e.icon());
            if (icon != null && BuiltInRegistries.ITEM.containsKey(icon)) {
                gfx.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(icon)), cx - 100, y);
            }
            int ly = y;
            int n = 0;
            for (FormattedCharSequence line : this.font.split(FormattedText.of(e.desc()), 176)) {
                if (n++ >= 2) break;
                gfx.drawString(this.font, line, cx - 78, ly, 0xCCCCCC);
                ly += 10;
            }
        }
        if (!data.tiers().isEmpty()) {
            Net.TierInfo t = data.tiers().get(tierIndex);
            int ly = y + 24;
            int n = 0;
            for (FormattedCharSequence line : this.font.split(FormattedText.of(t.desc()), 200)) {
                if (n++ >= 2) break;
                gfx.drawString(this.font, line, cx - 100, ly, TIER_COLORS[Math.min(tierIndex, TIER_COLORS.length - 1)]);
                ly += 10;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
