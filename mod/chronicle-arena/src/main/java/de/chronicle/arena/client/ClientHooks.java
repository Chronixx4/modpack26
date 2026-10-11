package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;

/** Nur auf dem Client geladen. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openParty(Net.OpenParty payload) {
        var mc = Minecraft.getInstance();
        if (payload.members().isEmpty()) { if (mc.screen instanceof PartyScreen) mc.setScreen(null); return; }
        if (mc.screen instanceof PartyScreen screen) screen.update(payload);
        else if (payload.popup()) mc.setScreen(new PartyScreen(payload));
    }
    public static void openAdmin(Net.OpenAdmin payload) { Minecraft.getInstance().setScreen(new AdminScreen(payload)); }
    public static void openTerminal(Net.OpenTerminal payload) {
        Minecraft.getInstance().setScreen(new TerminalScreen(payload));
    }

    public static void openShop(Net.OpenShop payload) {
        Minecraft.getInstance().setScreen(new ShopScreen(payload));
    }

    public static void openSmith(Net.OpenSmith payload) {
        Minecraft.getInstance().setScreen(new SmithScreen(payload));
    }

    public static void updateStats(Net.Stats stats) {
        ClientState.stats = stats;
        if (Minecraft.getInstance().screen instanceof ShopScreen shop) shop.statsChanged();
    }
}
