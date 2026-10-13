package de.chronicle.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Balance-Log (eine JSON-Zeile pro Run), Bestzeiten-Tafel und Trainingsdummy.
 * Das Log liegt in der Welt unter chronicle_arena/runs.jsonl und wird nur angehaengt.
 */
final class ArenaLog {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String DUMMY = "chronicle_dummy";
    private static final String RECORDS = "chronicle_records";

    private ArenaLog() {}

    // ------------------------------------------------------------------ Balance-Log

    /** Eine Zeile pro beendetem Run. Fehler beim Schreiben duerfen den Run nie stoeren. */
    static void logRun(MinecraftServer server, ArenaManager.Run r, boolean won) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("zeit", java.time.Instant.now().toString());
            o.addProperty("run", r.id.toString());
            o.addProperty("begegnung", r.encounter.id().toString());
            o.addProperty("modus", r.encounter.boss() ? "boss" : r.endless ? "abgrund" : "wellen");
            o.addProperty("stufe", r.tier.id().toString());
            o.addProperty("sieg", won);
            o.addProperty("welle", r.wave);
            o.addProperty("wellen_fertig", r.completedWaves);
            o.addProperty("sekunden", r.fightTicks / 20);
            o.addProperty("spieler", r.participants.size());
            o.addProperty("modifikatoren", r.mask);
            o.addProperty("arena", r.variant);
            JsonArray ps = new JsonArray();
            for (UUID id : r.participants) {
                long[] st = r.stat(id);
                JsonObject p = new JsonObject();
                p.addProperty("name", r.names.getOrDefault(id, "?"));
                p.addProperty("schaden", st[0]);
                p.addProperty("kills", st[1]);
                p.addProperty("wiederbelebt", st[2]);
                p.addProperty("ueberlebt", r.alive.contains(id));
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) p.addProperty("coins_verdient", ArenaStats.earned(sp) - r.earnedStart.getOrDefault(id, 0));
                int[] bl = r.bless.get(id);
                if (bl != null) {
                    int sum = 0;
                    for (int b : bl) sum += b;
                    p.addProperty("segen", sum);
                }
                ps.add(p);
            }
            o.add("teilnehmer", ps);
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("chronicle_arena");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("runs.jsonl"), o + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Arena-Log konnte nicht geschrieben werden: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ Bestzeiten

    record Rec(int value, String encounter, String names) {}

    static final class Records extends SavedData {
        final Map<String, Rec> map = new TreeMap<>();

        static Records load(CompoundTag in, HolderLookup.Provider reg) {
            Records rec = new Records();
            for (Tag e : in.getList("recs", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                rec.map.put(c.getString("key"), new Rec(c.getInt("value"), c.getString("enc"), c.getString("names")));
            }
            return rec;
        }

        @Override
        public CompoundTag save(CompoundTag out, HolderLookup.Provider reg) {
            ListTag list = new ListTag();
            for (Map.Entry<String, Rec> en : map.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("key", en.getKey());
                c.putInt("value", en.getValue().value());
                c.putString("enc", en.getValue().encounter());
                c.putString("names", en.getValue().names());
                list.add(c);
            }
            out.put("recs", list);
            return out;
        }
    }

    private static Records records(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Records::new, Records::load, null),
                "chronicle_arena_records");
    }

    private static String key(ArenaManager.Run r) {
        return r.tier.id() + "|" + (r.participants.size() > 1 ? "koop" : "solo");
    }

    /** Neuer Rekord? Siege zaehlen die Kampfzeit (kleiner ist besser), der Abgrund die Wellen (groesser ist besser). */
    static boolean submit(MinecraftServer server, ArenaManager.Run r, boolean won) {
        try {
            return submitUnsafe(server, r, won);
        } catch (RuntimeException e) {
            LOGGER.warn("Arena-Rekord konnte nicht gespeichert werden: {}", e.toString());
            return false;
        }
    }

    private static boolean submitUnsafe(MinecraftServer server, ArenaManager.Run r, boolean won) {
        boolean abyss = r.endless;
        if (abyss ? r.completedWaves <= 0 : !won || r.fightTicks <= 0) return false;
        int value = abyss ? r.completedWaves : r.fightTicks;
        Records rec = records(server);
        String k = key(r);
        Rec old = rec.map.get(k);
        boolean better = old == null || (abyss ? value > old.value() : value < old.value());
        if (!better) return false;
        List<String> names = new ArrayList<>();
        for (java.util.UUID id : r.participants) names.add(r.names.getOrDefault(id, "?"));
        rec.map.put(k, new Rec(value, r.encounter.name(), String.join(", ", names)));
        rec.setDirty();
        refreshBoard(server);
        return true;
    }

    static String text(MinecraftServer server) {
        Records rec = records(server);
        StringBuilder sb = new StringBuilder("BESTZEITEN\n");
        List<ArenaDefs.Tier> tiers = ArenaDefs.tiers();
        tiers.sort(Comparator.comparingInt(ArenaDefs.Tier::order));
        for (ArenaDefs.Tier t : tiers) {
            for (String mode : new String[] {"solo", "koop"}) {
                Rec r = rec.map.get(t.id() + "|" + mode);
                if (r == null) continue;
                sb.append(t.name()).append(' ').append(mode.equals("solo") ? "Solo" : "Koop").append(": ");
                sb.append(t.waves() <= 0 ? "Welle " + r.value() : ArenaManager.formatTime(r.value()));
                sb.append(" - ").append(r.names()).append("\n");
            }
        }
        if (sb.length() == "BESTZEITEN\n".length()) sb.append("Noch keine Rekorde.");
        return sb.toString().stripTrailing();
    }

    static void refreshBoard(MinecraftServer server) {
        ServerLevel level = server.getLevel(ArenaManager.DIM);
        if (level == null) return;
        List<Entity> old = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e.getTags().contains(RECORDS)) old.add(e);
        old.forEach(Entity::discard);
        var board = EntityType.TEXT_DISPLAY.create(level);
        if (board == null) return;
        board.load(ArenaExtras.boardTag(text(server)));
        board.addTag(RECORDS);
        board.moveTo(ArenaManager.LOBBY.getX() + 0.5, ArenaManager.LOBBY.getY() + 4.5, ArenaManager.LOBBY.getZ() + 10.5, 180, 0);
        level.addFreshEntity(board);
    }

    static void dedupe(ServerLevel level) {
        List<Entity> boards = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e.getTags().contains(RECORDS)) boards.add(e);
        boards.sort(Comparator.comparingInt(Entity::getId).reversed());
        for (int i = 1; i < boards.size(); i++) boards.get(i).discard();
    }

    static String recordsChat(MinecraftServer server) {
        return text(server);
    }

    // ------------------------------------------------------------------ Trainingsdummy

    private record Hit(long tick, float dmg) {}

    private static final Map<UUID, Deque<Hit>> HITS = new HashMap<>();
    private static final Map<UUID, Float> TOTAL = new HashMap<>();

    /** Dummy vor dem Spieler aufstellen (ersetzt einen vorhandenen Dummy desselben Spielers). */
    static void spawnDummy(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        String tag = DUMMY + "_" + p.getUUID();
        for (Entity e : new ArrayList<Entity>(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false).toList())) if (e.getTags().contains(tag)) e.discard();
        Mob dummy = EntityType.IRON_GOLEM.create(level);
        if (dummy == null) return;
        var look = p.getLookAngle();
        double x = p.getX() + look.x * 3.0, z = p.getZ() + look.z * 3.0;
        dummy.moveTo(x, p.getY(), z, p.getYRot() + 180.0F, 0.0F);
        dummy.setNoAi(true);
        dummy.setPersistenceRequired();
        dummy.setSilent(true);
        dummy.setCustomName(Component.literal("Trainingsdummy"));
        dummy.setCustomNameVisible(true);
        dummy.addTag(DUMMY);
        dummy.addTag(tag);
        level.addFreshEntity(dummy);
        HITS.remove(p.getUUID());
        TOTAL.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("Trainingsdummy steht. Schlag zu, DPS erscheint in der Leiste. Nochmal /arena training stellt ihn neu hin."));
    }

    static void removeDummies(ServerLevel level) {
        for (Entity e : new ArrayList<Entity>(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false).toList())) if (e.getTags().contains(DUMMY)) e.discard();
    }

    /** true, wenn der Treffer den Dummy betraf (Schaden wird dann gezaehlt und abgebrochen). */
    static boolean dummyHit(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getTags().contains(DUMMY)) return false;
        event.setCanceled(true);
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return true;
        long now = p.serverLevel().getGameTime();
        Deque<Hit> q = HITS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        if (!q.isEmpty() && now - q.peekLast().tick() > 200) {
            q.clear();
            TOTAL.remove(p.getUUID());
        }
        q.addLast(new Hit(now, event.getAmount()));
        while (!q.isEmpty() && now - q.peekFirst().tick() > 100) q.pollFirst();
        float window = 0;
        for (Hit h : q) window += h.dmg();
        float total = TOTAL.merge(p.getUUID(), event.getAmount(), Float::sum);
        p.displayClientMessage(Component.literal(String.format("Treffer %.1f | DPS (5 s) %.1f | Gesamt %.0f",
                event.getAmount(), window / 5.0F, total)), true);
        return true;
    }

    static void forget(UUID id) {
        HITS.remove(id);
        TOTAL.remove(id);
    }
}
