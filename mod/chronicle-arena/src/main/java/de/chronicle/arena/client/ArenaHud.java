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

    private static void drawBattle(GuiGraphics g,Font f,de.chronicle.arena.ArenaNet.Battle b){
        int w=Math.min(220,g.guiWidth()-130);if(w<100)return;
        int x=g.guiWidth()-w-6,y=40;
        int goalLines=b.goal().isEmpty()?0:f.split(net.minecraft.network.chat.Component.literal(b.goal()),w-12).size();
        int pickupLines=b.pickups().isEmpty()?0:f.split(net.minecraft.network.chat.Component.literal(b.pickups()),w-12).size();
        var heading=net.minecraft.network.chat.Component.literal(b.phase()+" | "+(b.total()==0?"Welle "+b.wave():"Welle "+b.wave()+"/"+b.total()));
        int headerExtra=Math.max(0,f.split(heading,w-12).size()-1)*10;
        int h=48+headerExtra+(goalLines==0?0:goalLines*10+30)+(b.objectiveMaxHealth()>0?24:0)+(pickupLines>0?pickupLines*10+5:0);
        g.fill(x-1,y-1,x+w+1,y+h+1,0xFF9B5CFF);g.fillGradient(x,y,x+w,y+h,0xD0201638,0xD00B0914);
        g.drawWordWrap(f,heading,x+6,y+5,w-12,0xFFD700);
        g.drawString(f,"Gegner: "+b.enemies(),x+6,y+18+headerExtra,0xFFFFFF);
        g.drawString(f,"Wiederbelebungen: "+b.revives(),x+6,y+31+headerExtra,0x7FE0FF);
        int bottom=y+48+headerExtra;
        if(goalLines>0){
            int gy=y+47+headerExtra;g.drawWordWrap(f,net.minecraft.network.chat.Component.literal(b.goal()),x+6,gy,w-12,0xFFFFFF);gy+=goalLines*10;
            boolean complete=b.progress()>=b.target();
            g.drawString(f,b.progress()+"/"+b.target()+(complete?" - Erledigt": " | "+b.seconds()+" s"),x+6,gy,complete?0x55FF55:b.seconds()<20?0xFF7777:0xFFD700);
            g.fill(x+6,gy+12,x+w-6,gy+16,0xFF322448);int fill=(int)((w-12)*Math.clamp(b.progress()/(double)Math.max(1,b.target()),0,1));g.fill(x+6,gy+12,x+6+fill,gy+16,complete?0xFF55FF55:0xFF9B5CFF);
            bottom=gy+23;
        }
        if(b.objectiveMaxHealth()>0){
            g.drawString(f,"Beacon: "+b.objectiveHealth()+"/"+b.objectiveMaxHealth()+" LP",x+6,bottom,b.objectiveHealth()<=30?0xFF7777:0x55FF55);
            g.fill(x+6,bottom+12,x+w-6,bottom+16,0xFF322448);int hp=(int)((w-12)*Math.clamp(b.objectiveHealth()/(double)b.objectiveMaxHealth(),0,1));g.fill(x+6,bottom+12,x+6+hp,bottom+16,0xFF55CC77);bottom+=24;
        }
        if(pickupLines>0)g.drawWordWrap(f,net.minecraft.network.chat.Component.literal(b.pickups()),x+6,bottom,w-12,0x7FE0FF);
    }
    private static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Net.Stats s = ClientState.stats;
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (mc.level.dimension() != ArenaManager.DIM) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;
        Font f = mc.font;
        if(s==null){var b=ClientState.battle;if(b!=null&&b.active()&&System.nanoTime()-ClientState.battleReceived<3_000_000_000L)drawBattle(g,f,b);return;}

        // Links: Konto.
        int x = 6;
        int y = 6;
        int w = 112;
        int h = s.title().isEmpty() ? 64 : 74;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF9B5CFF);
        g.fillGradient(x, y, x + w, y + h, 0xD0201638, 0xD00B0914);
        g.fill(x, y, x + w, y + 12, 0xFF3A2370);
        g.drawString(f, "Dein Konto", x + 4, y + 2, 0xFFE0B8FF);
        g.renderItem(new ItemStack(Items.GOLD_NUGGET), x + 2, y + 14);
        g.drawString(f, "Coins: " + s.coins(), x + 22, y + 18, 0xFFFFD700);
        g.drawString(f, "Kills: " + s.kills(), x + 4, y + 32, 0xFFFFFFFF);
        g.drawString(f, "Bosse: " + s.bosses() + "  Siege: " + s.wins(), x + 4, y + 42, 0xFFCCCCCC);
        g.drawString(f, "Rang: " + s.rank(), x + 4, y + 52, 0xFFB388FF);
        if (!s.title().isEmpty()) g.drawString(f, s.title(), x + 4, y + 64, 0xFFFFAA00);
        if (s.nextRankAt() > 0) {
            int bw = w - 8;
            g.fill(x + 4, y + 61, x + 4 + bw, y + 63, 0xFF2A2A2A);
            int fill = (int) Math.min(bw, bw * (long) s.earned() / Math.max(1, s.nextRankAt()));
            g.fill(x + 4, y + 61, x + 4 + fill, y + 63, 0xFFB388FF);
        }

        // Links unten: aktive Verstaerkungen (klein).
        List<String> perks = s.perks();
        if (!perks.isEmpty()) {
            int py = y + h + 6;
            int pw = 112;
            int maxPerks=Math.max(0,Math.min(14,(g.guiHeight()-py-55)/9));
            int ph = 12 + Math.min(perks.size(), maxPerks) * 9 + 3 + (perks.size()>maxPerks?11:0);
            g.fill(x - 1, py - 1, x + pw + 1, py + ph + 1, 0xFF6B4BB0);
            g.fillGradient(x, py, x + pw, py + ph, 0xC0201638, 0xC00B0914);
            g.drawString(f, "Verstaerkungen", x + 3, py + 2, 0xFFE0B8FF);
            int ky = py + 13;
            int shown = 0;
            for (String k : perks) {
                if (shown++ >= maxPerks) break;
                boolean smith = k.startsWith("S ");
                g.drawString(f, f.plainSubstrByWidth(k.substring(2), pw - 8), x + 4, ky, smith ? 0xFFFFD36B : 0xFF7FE0FF);
                ky += 9;
            }
            if(perks.size()>maxPerks)g.drawString(f,"Mehr: /arena segen",x+4,ky,0xFFAAAAAA);
        }

        // During combat, the goal panel takes the leaderboard's place.
        var battle=ClientState.battle;
        if(battle!=null&&battle.active()&&System.nanoTime()-ClientState.battleReceived<3_000_000_000L){drawBattle(g,f,battle);return;}
        // Rechts: Rangliste.
        List<Net.BoardLine> board = s.board();
        if (board.isEmpty()) return;
        int rw = 168;
        int rx = g.guiWidth() - rw - 6;
        int rh = 14 + board.size() * 10 + 4;
        g.fill(rx - 1, y - 1, rx + rw + 1, y + rh + 1, 0xFFFFD36B);
        g.fillGradient(rx, y, rx + rw, y + rh, 0xD0201638, 0xD00B0914);
        g.fill(rx, y, rx + rw, y + 12, 0xFF3A2370);
        g.drawString(f, "Rangliste", rx + 4, y + 2, 0xFFFFD36B);
        g.drawString(f, "S/B/K/A", rx + rw - f.width("S/B/K/A") - 4, y + 2, 0xFFAAAAAA);
        String me = mc.player.getGameProfile().getName();
        int ly = y + 15;
        int rank = 1;
        for (Net.BoardLine l : board) {
            int color = l.name().equals(me) ? 0xFF55FF55 : rank == 1 ? 0xFFFFD700 : rank == 2 ? 0xFFC0C0C0 : rank == 3 ? 0xFFCD7F32 : 0xFFFFFFFF;
            String name = f.plainSubstrByWidth(rank + ". " + l.name(), 76);
            g.drawString(f, name, rx + 4, ly, color);
            String nums = l.wins() + "/" + l.bosses() + "/" + l.kills() + "/" + l.endless();
            g.drawString(f, nums, rx + rw - f.width(nums) - 4, ly, color);
            ly += 10;
            rank++;
        }
    }
}
