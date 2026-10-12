package de.chronicle.arena.client;

import de.chronicle.arena.Net;

/** Letzter Stand von Konto und Rangliste auf dem Client. */
public final class ClientState {
    private ClientState() {}

    static Net.Stats stats;
    static de.chronicle.arena.ArenaNet.Battle battle;
    static long battleReceived;

}
