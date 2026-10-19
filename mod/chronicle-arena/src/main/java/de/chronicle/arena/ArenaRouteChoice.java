package de.chronicle.arena;

import java.util.*;

/** Server-owned ballot and the lifetime of its five-wave reward/difficulty block. */
public final class ArenaRouteChoice {
    public static final int VOTE_TICKS = 30 * 20;
    public enum Route {
        RISK("Risiko"), RECOVERY("Erholung"), MISSION("Spezialmission"), MERCHANT("Haendler");
        public final String title;
        Route(String title) { this.title = title; }
        public String description(boolean noHeal, boolean goalsEnabled) {
            return switch (this) {
                case RISK -> "Naechste 5 Wellen: +25 % Gegnerleben, +15 % Angriffsschaden, +50 % Wellen-Coins. Mini-Boss-Lebenslimit bleibt bestehen.";
                case RECOVERY -> noHeal ? "Nahrung und Saettigung auffuellen. Kein Heilen ist aktiv: keine Heilung. Keine Extra-Belohnung."
                        : "Jetzt Leben, Nahrung und Saettigung auffuellen. Normale Gegner und Belohnungen.";
                case MERCHANT -> "Ein fahrender Haendler: jeder lebende Spieler bekommt 2 Heiltraenke, 1 Staerketrank und 2 Goldaepfel. Keine Heilung, normale Gegner und Belohnungen.";
                case MISSION -> goalsEnabled ? "Naechste Welle mit Zufallsziel. Nach Ziel UND Welle: eine zufaellige Rune je lebendem Spieler. Danach normale Wellen."
                        : "Nicht verfuegbar: Zielwellen wurden vom Admin deaktiviert.";
            };
        }
    }

    private final UUID run;
    private int checkpoint;
    private int elapsed;
    private boolean voting;
    private boolean missionClaimed;
    private boolean goalsEnabled;
    private Route selected;
    private Set<UUID> eligible = Set.of();
    private final Map<UUID, Route> votes = new HashMap<>();

    public ArenaRouteChoice(UUID run) { this.run = run; }
    public static boolean isCheckpoint(int wave, int total, boolean boss) {
        return !boss && wave > 0 && wave % 5 == 0 && wave < total;
    }
    public boolean begin(int wave, int total, boolean boss, boolean goals, Set<UUID> alive) {
        if (voting || wave <= checkpoint || !isCheckpoint(wave, total, boss)) return false;
        checkpoint = wave;
        elapsed = 0;
        voting = true;
        selected = null;
        missionClaimed = false;
        goalsEnabled = goals;
        votes.clear();
        refresh(alive);
        return true;
    }
    public void refresh(Set<UUID> alive) {
        eligible = Set.copyOf(alive);
        votes.keySet().retainAll(eligible);
    }
    public boolean cast(UUID player, UUID run, int wave, int slot) {
        if (!voting || !this.run.equals(run) || checkpoint != wave || !eligible.contains(player)
                || slot < 0 || slot >= Route.values().length || (slot == Route.MISSION.ordinal() && !goalsEnabled)) return false;
        votes.put(player, Route.values()[slot]);
        return true;
    }
    /** Returns a result exactly once, when all living players voted or the deadline elapsed. */
    public Route tick(Set<UUID> alive) {
        if (!voting) return null;
        refresh(alive);
        elapsed++;
        if (elapsed < VOTE_TICKS && votes.size() < eligible.size()) return null;
        int best = 0;
        Route winner = Route.RECOVERY;
        boolean tied = false;
        for (Route route : Route.values()) {
            int n = count(route);
            if (n > best) { best = n; winner = route; tied = false; }
            else if (n == best) tied = true;
        }
        selected = tied ? Route.RECOVERY : winner;
        voting = false;
        return selected;
    }
    public void cancel() { voting = false; votes.clear(); }
    public boolean voting() { return voting; }
    public int seconds() { return Math.max(0, (VOTE_TICKS - elapsed + 19) / 20); }
    public int checkpoint() { return checkpoint; }
    public int voters() { return eligible.size(); }
    public int voted() { return votes.size(); }
    public int choice(UUID player) { return votes.containsKey(player) ? votes.get(player).ordinal() : -1; }
    public int count(Route route) { return (int) votes.values().stream().filter(v -> v == route).count(); }
    public Route active(int wave) { return wave > checkpoint && wave - checkpoint <= 5 ? selected : null; }
    public boolean heals(boolean noHeal) { return selected == Route.RECOVERY && !noHeal; }
    public double health(int wave) { return active(wave) == Route.RISK ? 1.25 : 1; }
    public double damage(int wave) { return active(wave) == Route.RISK ? 1.15 : 1; }
    public double coins(int wave) { return active(wave) == Route.RISK ? 1.5 : 1; }
    public boolean mission(int wave) { return selected == Route.MISSION && wave == checkpoint + 1; }
    /** Fuer den Neustart-Schutz: eine offene Abstimmung wird nicht gespeichert, sie startet danach neu. */
    public net.minecraft.nbt.CompoundTag save() {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (voting) return tag;
        tag.putInt("checkpoint", checkpoint);
        tag.putInt("selected", selected == null ? -1 : selected.ordinal());
        tag.putBoolean("mission_claimed", missionClaimed);
        tag.putBoolean("goals", goalsEnabled);
        return tag;
    }
    public void load(net.minecraft.nbt.CompoundTag tag) {
        voting = false;
        votes.clear();
        checkpoint = tag.getInt("checkpoint");
        int sel = tag.contains("selected") ? tag.getInt("selected") : -1;
        selected = sel >= 0 && sel < Route.values().length ? Route.values()[sel] : null;
        missionClaimed = tag.getBoolean("mission_claimed");
        goalsEnabled = tag.getBoolean("goals");
    }
    public boolean claimMission(int wave, boolean complete) {
        if (!mission(wave) || !complete || missionClaimed) return false;
        missionClaimed = true;
        return true;
    }
}
