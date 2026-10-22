package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crash- und Neustart-Schutz: Stand des laufenden Kampfes (an Wellengrenzen) und Ergebnisse fuer Spieler,
 * die beim Kampfende offline waren. Liegt als SavedData in der Welt und wird mit ihr gespeichert.
 */
final class ArenaResume extends SavedData {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    CompoundTag run;
    final Map<UUID, CompoundTag> pending = new HashMap<>();

    static ArenaResume load(CompoundTag in, HolderLookup.Provider reg) {
        ArenaResume r = new ArenaResume();
        if (in.contains("run")) r.run = in.getCompound("run");
        CompoundTag p = in.getCompound("pending");
        for (String key : p.getAllKeys()) {
            try { r.pending.put(UUID.fromString(key), p.getCompound(key)); } catch (IllegalArgumentException ignored) {}
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag out, HolderLookup.Provider reg) {
        if (run != null) out.put("run", run);
        CompoundTag p = new CompoundTag();
        pending.forEach((id, tag) -> p.put(id.toString(), tag));
        out.put("pending", p);
        return out;
    }

    static ArenaResume get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ArenaResume::new, ArenaResume::load, null),
                "chronicle_arena_resume");
    }

    /** Fehler beim Speichern duerfen den Kampf nie stoeren. */
    static void store(MinecraftServer server, ArenaManager.Run r) {
        try {
            ArenaResume data = get(server);
            data.run = write(r);
            data.setDirty();
        } catch (RuntimeException e) {
            LOGGER.warn("Arena-Stand konnte nicht gesichert werden: {}", e.toString());
        }
    }

    static void clearRun(MinecraftServer server) {
        try {
            ArenaResume data = get(server);
            if (data.run != null) { data.run = null; data.setDirty(); }
        } catch (RuntimeException e) {
            LOGGER.warn("Arena-Stand konnte nicht geloescht werden: {}", e.toString());
        }
    }

    static void addPending(MinecraftServer server, UUID id, CompoundTag result) {
        try {
            ArenaResume data = get(server);
            data.pending.put(id, result);
            data.setDirty();
        } catch (RuntimeException e) {
            LOGGER.warn("Arena-Ergebnis fuer {} konnte nicht gespeichert werden: {}", id, e.toString());
        }
    }

    static CompoundTag takePending(MinecraftServer server, UUID id) {
        try {
            ArenaResume data = get(server);
            CompoundTag tag = data.pending.remove(id);
            if (tag != null) data.setDirty();
            return tag;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Kampfstand

    /** Teilnehmer-Zustand im Stand: lebt (auch offline), gefallen, schon raus. */
    static final int ALIVE = 0, DOWN = 1, GONE = 2;

    static CompoundTag base(ArenaManager.Run r) {
        CompoundTag t = new CompoundTag();
        t.putString("enc", r.encounter.id().toString());
        t.putString("tier", r.tier.id().toString());
        t.putInt("size", r.extra + 1);
        t.putInt("mask", r.mask);
        t.putInt("variant", r.variant);
        t.putInt("coins", r.coinPercent);
        t.putInt("period", r.challengePeriod);
        t.putInt("wave", r.wave);
        t.putInt("waves", r.completedWaves);
        t.putInt("ticks", r.fightTicks);
        return t;
    }

    static CompoundTag write(ArenaManager.Run r) {
        CompoundTag t = base(r);
        t.putBoolean("goals", r.goalsEnabled);
        t.putInt("interval", r.goalInterval);
        t.putDouble("hp", r.adminHealth);
        t.putDouble("dmg", r.adminDamage);
        t.putLong("seed", r.seed);
        t.putInt("revives", r.revives);
        t.putInt("last_goal", r.lastGoal);
        t.put("routes", r.routes.save());
        if (r.logic != null) t.put("mode_data", r.logic.save());
        ListTag ps = new ListTag();
        for (UUID id : r.participants) {
            CompoundTag p = new CompoundTag();
            p.putUUID("id", id);
            p.putString("name", r.names.getOrDefault(id, "?"));
            ArenaManager.Away away = r.away.get(id);
            int state = r.alive.contains(id) || (away != null && away.alive()) ? ALIVE
                    : r.spectators.contains(id) || away != null ? DOWN : GONE;
            p.putInt("state", state);
            p.putBoolean("dropped", r.dropped.contains(id));
            int[] bl = r.bless.get(id);
            if (bl != null) p.putIntArray("bless", bl);
            p.putLongArray("stat", r.stat(id));
            p.putInt("earned", r.earnedStart.getOrDefault(id, 0));
            ps.add(p);
        }
        t.put("players", ps);
        // Offenes Segen-Angebot (nur zwischen den Wellen).
        if (r.state == ArenaManager.State.PREP && !r.offer.isEmpty()) {
            int[] offer = new int[r.offer.size()];
            for (int i = 0; i < offer.length; i++) offer[i] = r.offer.get(i).ordinal();
            t.putIntArray("offer", offer);
            ListTag picked = new ListTag();
            for (UUID id : r.picked) picked.add(NbtUtils.createUUID(id));
            t.put("picked", picked);
        }
        // Bloecke, die ein Wellenziel ersetzt hat, damit sie nach einem Absturz zurueckkommen.
        if (r.objective != null && !r.objective.originals.isEmpty()) {
            ListTag blocks = new ListTag();
            for (Map.Entry<BlockPos, BlockState> en : r.objective.originals.entrySet()) {
                CompoundTag b = new CompoundTag();
                b.putLong("pos", en.getKey().asLong());
                b.put("state", NbtUtils.writeBlockState(en.getValue()));
                blocks.add(b);
            }
            t.put("blocks", blocks);
        }
        return t;
    }

    /** Leerer Run aus den Grunddaten (fuer Ergebnisse und den Neustart); null, wenn Begegnung oder Stufe fehlen. */
    static ArenaManager.Run shell(CompoundTag t) {
        ArenaDefs.Encounter enc = ArenaDefs.encounter(ResourceLocation.tryParse(t.getString("enc")));
        ArenaDefs.Tier tier = ArenaDefs.tier(ResourceLocation.tryParse(t.getString("tier")));
        if (enc == null || tier == null) return null;
        ArenaManager.Run r = new ArenaManager.Run(enc, tier, Math.max(1, t.getInt("size")), t.getInt("mask"), t.getInt("variant"));
        r.coinPercent = t.contains("coins") ? t.getInt("coins") : 100;
        r.challengePeriod = t.contains("period") ? t.getInt("period") : -1;
        r.wave = t.getInt("wave");
        r.completedWaves = t.getInt("waves");
        r.fightTicks = t.getInt("ticks");
        return r;
    }

    /** Kampf aus dem Stand wiederherstellen: zurueck auf die letzte fertige Welle, alle warten auf ihre Spieler. */
    static ArenaManager.Run read(CompoundTag t, ServerLevel level, int deadline) {
        ArenaManager.Run r = shell(t);
        if (r == null) return null;
        r.wave = r.completedWaves;
        r.started = r.completedWaves > 0;
        r.state = ArenaManager.State.PREP;
        r.goalsEnabled = t.getBoolean("goals");
        r.goalInterval = Math.max(2, t.getInt("interval"));
        r.adminHealth = t.contains("hp") ? t.getDouble("hp") : 1;
        r.adminDamage = t.contains("dmg") ? t.getDouble("dmg") : 1;
        r.seed = t.getLong("seed");
        r.revives = t.getInt("revives");
        r.lastGoal = t.contains("last_goal") ? t.getInt("last_goal") : -1;
        r.routes.load(t.getCompound("routes"));
        if (r.logic != null && level != null) {
            // Burg halten: Karte, Tuerme und Kasse wiederherstellen (ohne Stand: frisch aufbauen).
            if (t.contains("mode_data")) r.logic.load(t.getCompound("mode_data"), level);
            else r.logic.start(level);
        }
        for (Tag e : t.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) e;
            if (!p.hasUUID("id")) continue;
            UUID id = p.getUUID("id");
            r.participants.add(id);
            r.names.put(id, p.getString("name"));
            if (p.contains("bless")) {
                int[] stored = p.getIntArray("bless");
                int[] bl = new int[ArenaShow.Blessing.values().length];
                System.arraycopy(stored, 0, bl, 0, Math.min(stored.length, bl.length));
                r.bless.put(id, bl);
            }
            long[] stat = p.getLongArray("stat");
            System.arraycopy(stat, 0, r.stat(id), 0, Math.min(stat.length, 4));
            r.earnedStart.put(id, p.getInt("earned"));
            int state = p.getInt("state");
            if (state != GONE) r.away.put(id, new ArenaManager.Away(deadline, state == ALIVE, null));
            if (state == ALIVE || p.getBoolean("dropped")) r.dropped.add(id);
        }
        if (t.contains("offer")) {
            ArenaShow.Blessing[] all = ArenaShow.Blessing.values();
            for (int o : t.getIntArray("offer")) if (o >= 0 && o < all.length) r.offer.add(all[o]);
            for (Tag e : t.getList("picked", Tag.TAG_INT_ARRAY)) r.picked.add(NbtUtils.loadUUID(e));
        }
        if (level != null && t.contains("blocks")) {
            var blocks = level.holderLookup(Registries.BLOCK);
            for (Tag e : t.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) e;
                level.setBlockAndUpdate(BlockPos.of(b.getLong("pos")), NbtUtils.readBlockState(blocks, b.getCompound("state")));
            }
        }
        return r;
    }

    /** Ergebnis fuer einen Spieler, der beim Kampfende offline war. */
    static CompoundTag result(ArenaManager.Run r, boolean won) {
        CompoundTag t = base(r);
        t.putBoolean("won", won);
        return t;
    }
}
