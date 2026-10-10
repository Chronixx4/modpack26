package de.chronicle.arena.client;

import de.chronicle.arena.ArenaManager;
import de.chronicle.arena.ChronicleArena;
import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

/** Anzeige links (Coins, Kills, Bosse, Siege) und Rangliste rechts; nur in der Arena-Dimension. */
@EventBusSubscriber(modid = ChronicleArena.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArenaHud {
    private ArenaHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ChronicleArena.id("hud"), (gfx, delta) -> draw(gfx));
    }

    private static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Net.Stats s = ClientState.stats;
        if (s == null || mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (mc.level.dimension() != ArenaManager.DIM) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;
        Font f = mc.font;

        // Links: Konto.
        int x = 6;
        int y = 6;
        int w = 112;
        int h = s.title().isEmpty() ? 52 : 62;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF9B5CFF);
        g.fillGradient(x, y, x + w, y + h, 0xD0201638, 0xD00B0914);
        g.fill(x, y, x + w, y + 12, 0xFF3A2370);
        g.drawString(f, "Dein Konto", x + 4, y + 2, 0xFFE0B8FF);
        g.renderItem(new ItemStack(Items.GOLD_NUGGET), x + 2, y + 14);
        g.drawString(f, "Coins: " + s.coins(), x + 22, y + 18, 0xFFFFD700);
        g.drawString(f, "Kills: " + s.kills(), x + 4, y + 32, 0xFFFFFFFF);
        g.drawString(f, "Bosse: " + s.bosses() + "  Siege: " + s.wins(), x + 4, y + 42, 0xFFCCCCCC);
        if (!s.title().isEmpty()) g.drawString(f, s.title(), x + 4, y + 52, 0xFFFFAA00);

        // Rechts: Rangliste.
        List<Net.BoardLine> board = s.board();
        if (board.isEmpty()) return;
        int rw = 150;
        int rx = g.guiWidth() - rw - 6;
        int rh = 14 + board.size() * 10 + 4;
        g.fill(rx - 1, y - 1, rx + rw + 1, y + rh + 1, 0xFFFFD36B);
        g.fillGradient(rx, y, rx + rw, y + rh, 0xD0201638, 0xD00B0914);
        g.fill(rx, y, rx + rw, y + 12, 0xFF3A2370);
        g.drawString(f, "Rangliste", rx + 4, y + 2, 0xFFFFD36B);
        g.drawString(f, "S/B/K", rx + rw - f.width("S/B/K") - 4, y + 2, 0xFFAAAAAA);
        String me = mc.player.getGameProfile().getName();
        int ly = y + 15;
        int rank = 1;
        for (Net.BoardLine l : board) {
            int color = l.name().equals(me) ? 0xFF55FF55 : rank == 1 ? 0xFFFFD700 : rank == 2 ? 0xFFC0C0C0 : rank == 3 ? 0xFFCD7F32 : 0xFFFFFFFF;
            String name = f.plainSubstrByWidth(rank + ". " + l.name(), 84);
            g.drawString(f, name, rx + 4, ly, color);
            String nums = l.wins() + "/" + l.bosses() + "/" + l.kills();
            g.drawString(f, nums, rx + rw - f.width(nums) - 4, ly, color);
            ly += 10;
            rank++;
        }
    }
}
