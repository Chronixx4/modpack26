package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Der Schmied: Liste der Verbesserungen mit Stufe, Preis und Kaufknopf. */
public class SmithScreen extends Screen {
    private static final int ROW = 24;
    private final Net.OpenSmith data;

    public SmithScreen(Net.OpenSmith data) {
        super(Component.literal("Schmied"));
        this.data = data;
    }

    private int top() {
        return this.height / 2 - (data.ups().size() * ROW) / 2 - 10;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 170;
        for (int i = 0; i < data.ups().size(); i++) {
            Net.SmithInfo u = data.ups().get(i);
            boolean maxed = u.level() >= u.max();
            Button b = Button.builder(Component.literal(maxed ? "Max" : u.price() + " Coins"),
                    btn -> PacketDistributor.sendToServer(new Net.SmithBuy(u.id())))
                    .bounds(left + 250, top() + i * ROW, 90, 20).build();
            b.active = !maxed && data.coins() >= u.price();
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.close"), b -> onClose())
                .bounds(this.width / 2 - 50, top() + data.ups().size() * ROW + 6, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int left = this.width / 2 - 170;
        gfx.drawCenteredString(this.font, "§6§lSchmied §r§7- wirkt nur in der Arena   §eCoins: " + data.coins(), this.width / 2, top() - 18, 0xFFFFFF);
        for (int i = 0; i < data.ups().size(); i++) {
            Net.SmithInfo u = data.ups().get(i);
            int y = top() + i * ROW;
            StringBuilder pips = new StringBuilder();
            for (int k = 0; k < u.max(); k++) pips.append(k < u.level() ? "§a|" : "§8|");
            gfx.drawString(this.font, "§f" + u.name() + " " + pips, left, y, 0xFFFFFF);
            gfx.drawString(this.font, "§7" + u.desc(), left, y + 10, 0xFFFFFF);
        }
    }
}
