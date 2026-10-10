package de.chronicle.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Konto eines Spielers (Coins, Kills, Bosse, Kosmetik) und die serverweite Rangliste. */
public final class ArenaStats {
    private static final String TAG = "chronicle_arena_stats";

    private ArenaStats() {}

    private static CompoundTag tag(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    public static int smithLevel(ServerPlayer p, String id) {
        return tag(p).getCompound("smith").getInt(id);
    }

    public static void setSmithLevel(ServerPlayer p, String id, int lv) {
        CompoundTag t = tag(p);
        CompoundTag sm = t.getCompound("smith");
        sm.putInt(id, lv);
        t.put("smith", sm);
    }

    public static int coins(ServerPlayer p) { return tag(p).getInt("coins"); }
    public static int kills(ServerPlayer p) { return tag(p).getInt("kills"); }
    public static int bosses(ServerPlayer p) { return tag(p).getInt("bosses"); }
    public static int wins(ServerPlayer p) { return tag(p).getInt("wins"); }
    public static String title(ServerPlayer p) { return tag(p).getString("title"); }
    public static String trail(ServerPlayer p) { return tag(p).getString("trail"); }
    public static int challengeDay(ServerPlayer p) { return tag(p).getInt("challenge_day"); }

    public static int earned(ServerPlayer p) { return tag(p).getInt("earned"); }

    public static int rankIdx(ServerPlayer p) { return tag(p).getInt("rank_idx"); }

    public static void setRankIdx(ServerPlayer p, int i) { tag(p).putInt("rank_idx", i); }

    public static final String[] RANKS = {"Rekrut", "Kaempfer", "Veteran", "Elite", "Champion", "Legende"};
    public static final int[] RANK_AT = {0, 300, 1200, 3500, 8000, 18000};
    public static final int[] RANK_BONUS = {0, 100, 200, 400, 800, 1500};

    public static int rankFor(int earned) {
        int idx = 0;
        for (int i = 0; i < RANK_AT.length; i++) if (earned >= RANK_AT[i]) idx = i;
        return idx;
    }

    public static int endlessBest(ServerPlayer p) { return tag(p).getInt("endless_best"); }

    public static void setEndlessBest(ServerPlayer p, int w) { tag(p).putInt("endless_best", w); }

    public static void setChallengeDay(ServerPlayer p, int day) { tag(p).putInt("challenge_day", day); }

    public static void addCoins(ServerPlayer p, int n) {
        CompoundTag t = tag(p);
        t.putInt("coins", Math.max(0, t.getInt("coins") + n));
        if (n > 0) t.putInt("earned", t.getInt("earned") + n);
    }

    public static boolean spend(ServerPlayer p, int n) {
        CompoundTag t = tag(p);
        if (t.getInt("coins") < n) return false;
        t.putInt("coins", t.getInt("coins") - n);
        return true;
    }

    public static void addKill(ServerPlayer p) {
        CompoundTag t = tag(p);
        t.putInt("kills", t.getInt("kills") + 1);
    }

    public static void addWin(ServerPlayer p, boolean boss) {
        CompoundTag t = tag(p);
        t.putInt("wins", t.getInt("wins") + 1);
        if (boss) t.putInt("bosses", t.getInt("bosses") + 1);
    }

    public static boolean owns(ServerPlayer p, String id) {
        for (Tag e : tag(p).getList("owned", Tag.TAG_STRING)) if (e.getAsString().equals(id)) return true;
        return false;
    }

    public static List<String> owned(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (Tag e : tag(p).getList("owned", Tag.TAG_STRING)) out.add(e.getAsString());
        return out;
    }

    public static void give(ServerPlayer p, String id) {
        if (owns(p, id)) return;
        CompoundTag t = tag(p);
        ListTag list = t.getList("owned", Tag.TAG_STRING);
        list.add(StringTag.valueOf(id));
        t.put("owned", list);
    }

    public static void equip(ServerPlayer p, String type, String id) {
        tag(p).putString(type, id);
    }

    // ------------------------------------------------------------------ Rangliste

    public record Line(String name, String title, int wins, int bosses, int kills, int endless) {}

    public static final class Board extends SavedData {
        final Map<UUID, Line> lines = new LinkedHashMap<>();

        static Board load(CompoundTag in, HolderLookup.Provider reg) {
            Board b = new Board();
            for (Tag e : in.getList("lines", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                b.lines.put(c.getUUID("id"), new Line(c.getString("name"), c.getString("title"), c.getInt("wins"),
                        c.getInt("bosses"), c.getInt("kills"), c.getInt("endless")));
            }
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag out, HolderLookup.Provider reg) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Line> en : lines.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", en.getKey());
                c.putString("name", en.getValue().name());
                c.putString("title", en.getValue().title());
                c.putInt("wins", en.getValue().wins());
                c.putInt("bosses", en.getValue().bosses());
                c.putInt("kills", en.getValue().kills());
                c.putInt("endless", en.getValue().endless());
                list.add(c);
            }
            out.put("lines", list);
            return out;
        }
    }

    private static Board board(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Board::new, Board::load, null),
                "chronicle_arena_board");
    }

    /** Eintrag des Spielers in der Rangliste aktualisieren. */
    public static void publish(ServerPlayer p) {
        Board b = board(p.server);
        String titleId = title(p);
        String titleName = titleId.isEmpty() ? "" : ArenaDefs.cosmeticName(titleId);
        b.lines.put(p.getUUID(), new Line(p.getGameProfile().getName(), titleName, wins(p), bosses(p), kills(p), endlessBest(p)));
        b.setDirty();
    }

    public static List<Line> top(MinecraftServer server, int n) {
        List<Line> all = new ArrayList<>(board(server).lines.values());
        all.sort(Comparator.comparingInt(Line::wins).reversed().thenComparing(Comparator.comparingInt(Line::bosses).reversed())
                .thenComparing(Comparator.comparingInt(Line::kills).reversed()));
        return all.size() > n ? all.subList(0, n) : all;
    }
}
