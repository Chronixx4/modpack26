package de.chronicle.arena;

/** Spielmodi der Arena. Der Index steht im Kampfstand, neue Modi daher nur hinten anhaengen. */
public enum ArenaMode {
    WAVES("Wellen"),
    BOSS("Boss"),
    SIEGE("Burg halten");

    public final String title;

    ArenaMode(String title) { this.title = title; }

    public static ArenaMode byId(int id) {
        ArenaMode[] all = values();
        return id >= 0 && id < all.length ? all[id] : WAVES;
    }

    /** Modi auf der normalen Arena (mit Brunnen, Fallen, Balkon und Arena-Wahl). */
    public boolean arenaMap() { return this == WAVES || this == BOSS; }
}
