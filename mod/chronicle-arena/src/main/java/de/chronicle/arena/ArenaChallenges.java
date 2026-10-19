package de.chronicle.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Tages- und Wochen-Challenge: feste Begegnung, Stufe, Modifikatoren und ein fester Seed, damit alle dieselben Wellen bekommen.
 * Eigene Tafel in der Lobby (beste 5 pro Zeitraum, Sieg vor Niederlage, dann Zeit bzw. Wellen).
 */
final class ArenaChallenges {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    static final int DAILY = 0, WEEKLY = 1;
    /** Bits im Aktions-Bitfeld (0-3 Modifikatoren, 4-5 Arena, 6 keine Zielwellen). */
    static final int FLAG_DAILY = 128, FLAG_WEEKLY = 256;
    static final String[] LABEL = {"Tages-Challenge", "Wochen-Challenge"};
    private static final String BOARD = "chronicle_challenges";
    private static final int TOP = 5;

    private ArenaChallenges() {}

    record Challenge(int kind, int period, ArenaDefs.Encounter encounter, ArenaDefs.Tier tier, int mask, int bonus, long seed) {
        int flag() { return kind == WEEKLY ? FLAG_WEEKLY : FLAG_DAILY; }
        String text() { return encounter.name() + " / " + tier.name() + " / " + ArenaManager.modNames(mask); }
    }

    static int kindOf(int mask) {
        return (mask & FLAG_WEEKLY) != 0 ? WEEKLY : (mask & FLAG_DAILY) != 0 ? DAILY : -1;
    }

    static int today() { return (int) LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    /** Wochen beginnen montags (Tag 0, der 1.1.1970, war ein Donnerstag). */
    static int week(int day) { return Math.floorDiv(day + 3, 7); }

    static int period(int kind) { return kind == WEEKLY ? week(today()) : today(); }

    static Challenge get(int kind) {
        return pick(kind, period(kind), ArenaDefs.encounters(), ArenaDefs.tiers());
    }

    /** Rein rechnerisch, damit derselbe Zeitraum auf jedem Server dieselbe Challenge ergibt. */
    static Challenge pick(int kind, int period, List<ArenaDefs.Encounter> allEncs, List<ArenaDefs.Tier> allTiers) {
        List<ArenaDefs.Encounter> encs = new ArrayList<>(allEncs);
        encs.sort(Comparator.comparing(e -> e.id().toString()));
        List<ArenaDefs.Tier> tiers = new ArrayList<>(allTiers.stream().filter(t -> t.waves() > 0).toList());
        tiers.sort(Comparator.comparingInt(ArenaDefs.Tier::order));
        if (encs.isEmpty() || tiers.isEmpty()) return null;
        Random rnd = new Random(kind == WEEKLY ? period * 104729L + 977L : period * 7919L + 13L);
        ArenaDefs.Encounter enc = encs.get(rnd.nextInt(encs.size()));
        ArenaDefs.Tier tier;
        int mask;
        if (kind == WEEKLY) {
            // Woche: eine Stufe hoeher und mindestens zwei Modifikatoren.
            int from = Math.min(1, tiers.size() - 1);
            tier = tiers.get(from + rnd.nextInt(Math.min(3, tiers.size() - from)));
            do mask = 1 + rnd.nextInt(15); while (Integer.bitCount(mask) < 2);
        } else {
            tier = tiers.get(rnd.nextInt(Math.min(3, tiers.size())));
            mask = 1 + rnd.nextInt(15);
        }
        int bonus = (int) Math.round(tier.tokens() * (enc.boss() ? 2 : 1) * ArenaManager.coinMult(mask) * (kind == WEEKLY ? 5.0 : 2.0));
        return new Challenge(kind, period, enc, tier, mask, bonus, rnd.nextLong());
    }

    static boolean claimed(ServerPlayer p, int kind, int period) {
        return (kind == WEEKLY ? ArenaStats.challengeWeek(p) : ArenaStats.challengeDay(p)) == period;
    }

    static void claim(ServerPlayer p, int kind, int period) {
        if (kind == WEEKLY) ArenaStats.setChallengeWeek(p, period);
        else ArenaStats.setChallengeDay(p, period);
    }

    // ------------------------------------------------------------------ Tafel

    record Entry(String names, boolean won, int ticks, int waves, int players) {}

    /** Sieg vor Niederlage; Siege nach Zeit, Niederlagen nach erreichten Wellen. */
    static final Comparator<Entry> ORDER = (a, b) -> {
        if (a.won() != b.won()) return a.won() ? -1 : 1;
        if (a.won()) return Integer.compare(a.ticks(), b.ticks());
        if (a.waves() != b.waves()) return Integer.compare(b.waves(), a.waves());
        return Integer.compare(a.ticks(), b.ticks());
    };

    static final class Data extends SavedData {
        final int[] period = {-1, -1};
        @SuppressWarnings("unchecked")
        final List<Entry>[] lists = new List[] {new ArrayList<Entry>(), new ArrayList<Entry>()};

        static Data load(CompoundTag in, HolderLookup.Provider reg) {
            Data d = new Data();
            for (int k = 0; k < 2; k++) {
                d.period[k] = in.contains("period" + k) ? in.getInt("period" + k) : -1;
                for (Tag t : in.getList("list" + k, Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) t;
                    d.lists[k].add(new Entry(c.getString("names"), c.getBoolean("won"), c.getInt("ticks"), c.getInt("waves"), c.getInt("players")));
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag out, HolderLookup.Provider reg) {
            for (int k = 0; k < 2; k++) {
                out.putInt("period" + k, period[k]);
                ListTag list = new ListTag();
                for (Entry e : lists[k]) {
                    CompoundTag c = new CompoundTag();
                    c.putString("names", e.names());
                    c.putBoolean("won", e.won());
                    c.putInt("ticks", e.ticks());
                    c.putInt("waves", e.waves());
                    c.putInt("players", e.players());
                    list.add(c);
                }
                out.put("list" + k, list);
            }
            return out;
        }

        /** Neuer Eintrag; ein Team (gleiche Namen) steht nur einmal auf der Tafel. Gibt den Platz (1-5) oder 0 zurueck. */
        int add(int kind, int period, Entry e) {
            if (period < this.period[kind]) return 0;
            if (period > this.period[kind]) {
                this.period[kind] = period;
                lists[kind].clear();
            }
            List<Entry> list = lists[kind];
            for (Entry old : list) {
                if (!old.names().equals(e.names())) continue;
                if (ORDER.compare(e, old) >= 0) return 0;
                list.remove(old);
                break;
            }
            list.add(e);
            list.sort(ORDER);
            while (list.size() > TOP) list.remove(list.size() - 1);
            setDirty();
            int idx = list.indexOf(e);
            return idx < 0 ? 0 : idx + 1;
        }

        List<Entry> current(int kind, int period) {
            return this.period[kind] == period ? lists[kind] : List.of();
        }
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "chronicle_arena_challenges");
    }

    static String names(ArenaManager.Run r) {
        List<String> names = new ArrayList<>();
        for (UUID id : r.participants) names.add(r.names.getOrDefault(id, "?"));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    /** Ergebnis eintragen; gibt den Platz auf der Tafel zurueck (0 = nicht drauf). Fehler stoeren den Run nie. */
    static int submit(MinecraftServer server, ArenaManager.Run r, boolean won) {
        if (r.challenge < 0 || r.challengePeriod < 0) return 0;
        try {
            int place = data(server).add(r.challenge, r.challengePeriod,
                    new Entry(names(r), won, Math.max(1, r.fightTicks), r.completedWaves, r.participants.size()));
            if (place > 0) refreshBoard(server);
            return place;
        } catch (RuntimeException e) {
            LOGGER.warn("Challenge-Ergebnis konnte nicht gespeichert werden: {}", e.toString());
            return 0;
        }
    }

    static String line(Entry e) {
        String value = e.won() ? ArenaManager.formatTime(e.ticks()) : "Welle " + e.waves() + " (verloren)";
        return value + " - " + e.names() + (e.players() > 1 ? " (" + e.players() + " Spieler)" : "");
    }

    static String text(MinecraftServer server) {
        StringBuilder sb = new StringBuilder("HERAUSFORDERUNGEN");
        Data d = data(server);
        for (int kind = DAILY; kind <= WEEKLY; kind++) {
            Challenge ch = get(kind);
            sb.append("\n\n").append(kind == WEEKLY ? "Woche: " : "Heute: ");
            if (ch == null) {
                sb.append("keine");
                continue;
            }
            sb.append(ch.text());
            List<Entry> list = d.current(kind, ch.period());
            if (list.isEmpty()) sb.append("\nNoch keine Eintraege.");
            for (int i = 0; i < list.size(); i++) sb.append('\n').append(i + 1).append(". ").append(line(list.get(i)));
        }
        return sb.toString();
    }

    private static int shownDay = Integer.MIN_VALUE;

    /** Einmal pro Sekunde: beim Tageswechsel (und nach dem Start) die Tafel neu schreiben, sobald jemand in der Arena ist. */
    static void tick(MinecraftServer server, ServerLevel level) {
        if (level.players().isEmpty()) return;
        int day = today();
        if (day == shownDay) return;
        shownDay = day;
        refreshBoard(server);
    }

    static void reset() { shownDay = Integer.MIN_VALUE; }

    static void refreshBoard(MinecraftServer server) {
        try {
            ServerLevel level = server.getLevel(ArenaManager.DIM);
            if (level == null) return;
            List<Entity> old = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e.getTags().contains(BOARD)) old.add(e);
            old.forEach(Entity::discard);
            var board = EntityType.TEXT_DISPLAY.create(level);
            if (board == null) return;
            board.load(ArenaExtras.boardTag(text(server)));
            board.addTag(BOARD);
            board.moveTo(ArenaManager.LOBBY.getX() - 9.5, ArenaManager.LOBBY.getY() + 4.5, ArenaManager.LOBBY.getZ() + 0.5, 90, 0);
            level.addFreshEntity(board);
        } catch (RuntimeException e) {
            LOGGER.warn("Challenge-Tafel konnte nicht geschrieben werden: {}", e.toString());
        }
    }

    static void dedupe(ServerLevel level) {
        List<Entity> boards = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e.getTags().contains(BOARD)) boards.add(e);
        boards.sort(Comparator.comparingInt(Entity::getId).reversed());
        for (int i = 1; i < boards.size(); i++) boards.get(i).discard();
    }
}
