package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;

/** Nur auf dem Client geladen. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTerminal(Net.OpenTerminal payload) {
        Minecraft.getInstance().setScreen(new TerminalScreen(payload));
    }
}
