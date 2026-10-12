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
    private static final int[] TIER_COLORS = {0xCD7F32, 0xC0C0C0, 0xFFD700, 0xB366FF};

    private static String lastEncounter = "", lastTier = "";
    private static int lastMask, lastArena;
    private final Net.OpenTerminal data;
    private boolean bossMode;
    private int encIndex;
    private int tierIndex;
    private int mask;
    private int arenaChoice;
    private Button arenaButton;
    private Button modeButton;
    private Button encButton;
    private Button tierButton;
    private Button startButton;
    private Button challengeButton;
    private final List<Button> modButtons = new ArrayList<>();

    public TerminalScreen(Net.OpenTerminal data) {
        super(Component.translatable("gui.chronicle_arena.title"));
        this.data = data;
    }

    private List<Net.EncInfo> current() {
        List<Net.EncInfo> out = new ArrayList<>();
        for (Net.EncInfo e : data.encounters()) if (e.boss() == bossMode) out.add(e);
        return out;
    }

    private Net.TierInfo tier() {
        return data.tiers().isEmpty() ? null : data.tiers().get(tierIndex);
    }

    private int bonusPercent() {
        int b = 0;
        for (Net.ModInfo m : data.mods()) if ((mask & m.bit()) != 0) b += m.bonus();
        return b;
    }

    @Override
    protected void init() {
        modButtons.clear();
        int cx = this.width / 2;
        int lx = cx - 160;
        int rx = cx + 10;
        int w = 150;
        int y = this.height / 2 - 82;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            bossMode = !bossMode;
            encIndex = 0;
            refresh();
        }).bounds(lx, y, w, 20).build());
        encButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            List<Net.EncInfo> list = current();
            minecraft.setScreen(new EncounterScreen(this, list, id -> {
                for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) encIndex = i;
            }));
        }).bounds(lx, y + 24, w, 20).build());
        tierButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            List<Net.EncInfo> options = new ArrayList<>();
            for (Net.TierInfo t : data.tiers()) options.add(new Net.EncInfo(t.id(), false,
                    t.name() + (t.locked() ? " [Gesperrt]" : ""), t.desc() + " | " + t.info(), "minecraft:gold_ingot"));
            minecraft.setScreen(new EncounterScreen(this, options, id -> {
                for (int i = 0; i < data.tiers().size(); i++) if (data.tiers().get(i).id().equals(id)) tierIndex = i;
            }));
        }).bounds(lx, y + 48, w, 20).build());
        arenaButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            arenaChoice = (arenaChoice + 1) % 4;
            refresh();
        }).bounds(lx, y + 72, w, 20).build());
        for (int i = 0; i < data.mods().size(); i++) {
            final Net.ModInfo m = data.mods().get(i);
            Button b = Button.builder(Component.empty(), btn -> {
                mask ^= m.bit();
                refresh();
            }).bounds(rx, y + i * 24, w, 20).build();
            modButtons.add(addRenderableWidget(b));
        }
        challengeButton = addRenderableWidget(Button.builder(Component.literal("Tages-Challenge waehlen"), b -> applyChallenge())
                .bounds(rx, y + 100, w, 20).build());
        startButton = addRenderableWidget(Button.builder(Component.literal("Vorbereiten"), b -> send(false))
                .bounds(lx, y + 158, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.spectate"), b -> send(true))
                .bounds(lx + 111, y + 158, 106, 20).build()).active = data.running();
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.close"), b -> onClose())
                .bounds(lx + 222, y + 158, 98, 20).build());
        if (data.admin()) addRenderableWidget(Button.builder(Component.literal("Admin-Panel"), b ->
                PacketDistributor.sendToServer(new Net.Control("admin"))).bounds(lx, y - 42, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Letzte Auswahl"), b -> restoreLast()).bounds(rx + 46, y - 42, 104, 20).build()).active = !lastEncounter.isEmpty();
        refresh();
    }

    private void restoreLast() {
        for (Net.EncInfo e : data.encounters()) if (e.id().equals(lastEncounter)) bossMode = e.boss();
        List<Net.EncInfo> list = current();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(lastEncounter)) encIndex = i;
        for (int i = 0; i < data.tiers().size(); i++) if (data.tiers().get(i).id().equals(lastTier)) tierIndex = i;
        mask = lastMask; arenaChoice = lastArena; refresh();
    }

    private void applyChallenge() {
        Net.ChallengeInfo ch = data.challenge();
        if (ch.encounter().isEmpty()) return;
        for (int i = 0; i < data.tiers().size(); i++) if (data.tiers().get(i).id().equals(ch.tier())) tierIndex = i;
        for (Net.EncInfo e : data.encounters()) {
            if (e.id().equals(ch.encounter())) {
                bossMode = e.boss();
                List<Net.EncInfo> list = current();
                for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(ch.encounter())) encIndex = i;
            }
        }
        mask = ch.mask();
        refresh();
    }

    private void refresh() {
        modeButton.setMessage(Component.translatable(bossMode ? "gui.chronicle_arena.mode_boss" : "gui.chronicle_arena.mode_waves"));
        List<Net.EncInfo> list = current();
        encButton.setMessage(list.isEmpty() ? Component.translatable("gui.chronicle_arena.none")
                : Component.literal(list.get(Math.min(encIndex, list.size() - 1)).name()));
        encButton.active = list.size() > 0;
        Net.TierInfo t = tier();
        if (t == null) {
            tierButton.setMessage(Component.translatable("gui.chronicle_arena.none"));
        } else {
            Component name = Component.translatable("gui.chronicle_arena.tier", t.name());
            if (t.locked()) name = name.copy().append(" ").append(Component.translatable("gui.chronicle_arena.tier_locked_short"));
            tierButton.setMessage(name);
        }
        arenaButton.setMessage(Component.literal("Arena: " + new String[]{"Auto (nach Gegner)", "Klassisch", "Glut", "Eis"}[arenaChoice]));
        for (int i = 0; i < modButtons.size(); i++) {
            Net.ModInfo m = data.mods().get(i);
            boolean on = (mask & m.bit()) != 0;
            modButtons.get(i).setMessage(Component.literal((on ? "§a[AN] §r" : "§7[aus] §r") + m.name() + " +" + m.bonus() + "%"));
        }
        Net.ChallengeInfo ch = data.challenge();
        challengeButton.active = !ch.encounter().isEmpty();
        challengeButton.setMessage(Component.literal(ch.claimed() ? "Tages-Challenge (heute erledigt)" : "Tages-Challenge waehlen"));
        startButton.active = !data.running() && !list.isEmpty() && t != null && !t.locked() && !(bossMode && t.id().endsWith(":abgrund"));
    }

    private void send(boolean spectate) {
        List<Net.EncInfo> list = current();
        String enc = list.isEmpty() ? "" : list.get(Math.min(encIndex, list.size() - 1)).id();
        Net.TierInfo t = tier();
        if (!spectate) { lastEncounter = enc; lastTier = t == null ? "" : t.id(); lastMask = mask; lastArena = arenaChoice; }
        PacketDistributor.sendToServer(new Net.Action(spectate, enc, t == null ? "" : t.id(), mask | (arenaChoice << 4)));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 82;
        Panel.draw(gfx, cx - 170, top - 44, cx + 170, top + 186, 26, TIER_COLORS[Math.min(tierIndex, TIER_COLORS.length - 1)]);
        gfx.fill(cx - 3, top - 14, cx - 1, top + 152, 0xFF2A2440);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 82;
        int lx = cx - 160;
        int rx = cx + 10;
        gfx.drawCenteredString(this.font, this.title, cx, top - 38, 0xE0B8FF);
        gfx.drawCenteredString(this.font, Component.literal(data.status()), cx, top - 12, data.running() ? 0xFFAA00 : 0x55FF55);

        List<Net.EncInfo> list = current();
        int y = top + 98;
        if (!list.isEmpty()) {
            Net.EncInfo e = list.get(Math.min(encIndex, list.size() - 1));
            ResourceLocation icon = ResourceLocation.tryParse(e.icon());
            int textX = lx;
            if (icon != null && BuiltInRegistries.ITEM.containsKey(icon)) {
                gfx.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(icon)), lx, y);
                textX = lx + 20;
            }
            int ly = y;
            int n = 0;
            for (FormattedCharSequence line : this.font.split(FormattedText.of(e.desc()), 130)) {
                if (n++ >= 3) break;
                gfx.drawString(this.font, line, textX, ly, 0xCCCCCC);
                ly += 10;
            }
        }
        Net.TierInfo t = tier();
        if (t != null) {
            int color = TIER_COLORS[Math.min(tierIndex, TIER_COLORS.length - 1)];
            int ly = y + 30;
            int n = 0;
            for (FormattedCharSequence line : this.font.split(FormattedText.of(t.desc()), 150)) {
                if (n++ >= 2) break;
                gfx.drawString(this.font, line, lx, ly, color);
                ly += 10;
            }
            if (!t.info().isEmpty()) {
                gfx.drawString(this.font, Component.literal(t.info()), lx, y + 50, t.locked() ? 0xFF7777 : 0x88DD88);
            }
        }

        // Rechts: Hinweise zu Modifikatoren und Tages-Challenge.
        int bonus = bonusPercent();
        gfx.drawString(this.font, Component.literal(bonus == 0 ? "Coins: " + data.coinPercent() + "%" : "Coins: " + data.coinPercent() + "% / Bonus +" + bonus + "%"), rx, top + 124,
                bonus == 0 ? 0x999999 : 0xFFD700);
        Net.ChallengeInfo ch = data.challenge();
        if (!ch.encounter().isEmpty()) {
            String txt = (ch.claimed() ? "Heute erledigt" : "Heute: +" + ch.bonusCoins() + " Coins") + " - " + ch.text();
            int ly = top + 136;
            int n = 0;
            for (FormattedCharSequence line : this.font.split(FormattedText.of(txt), 150)) {
                if (n++ >= 2) break;
                gfx.drawString(this.font, line, rx, ly, ch.claimed() ? 0x777777 : 0x88CCFF);
                ly += 10;
            }
        }
        if (encButton.isHovered() && !list.isEmpty()) gfx.renderTooltip(this.font, this.font.split(Component.literal(list.get(Math.min(encIndex, list.size()-1)).desc()), 240), mouseX, mouseY);
        if (tierButton.isHovered() && t != null) gfx.renderTooltip(this.font, this.font.split(Component.literal(t.desc() + " | " + t.info()), 240), mouseX, mouseY);
        // Tooltip fuer Modifikatoren.
        for (int i = 0; i < modButtons.size(); i++) {
            if (modButtons.get(i).isHovered()) {
                gfx.renderTooltip(this.font, Component.literal(data.mods().get(i).desc()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
