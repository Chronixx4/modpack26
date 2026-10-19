package de.chronicle.arena;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A fixed, consenting lobby group; newcomers never get pulled into a run. */
public final class ArenaParty {
    private static Pending pending;
    private static final class Pending {
        final UUID leader;
        final ArenaDefs.Encounter encounter;
        final ArenaDefs.Tier tier;
        final int mask, variant;
        final boolean goals;
        final Set<UUID> members = new LinkedHashSet<>(), ready = new HashSet<>();
        int age, countdown = -1;
        Pending(ServerPlayer p, ArenaDefs.Encounter enc, ArenaDefs.Tier tier, List<ServerPlayer> group, int mask, int variant, boolean goals) {
            leader = p.getUUID(); encounter = enc; this.tier = tier; this.mask = mask; this.variant = variant; this.goals = goals;
            group.forEach(q -> members.add(q.getUUID()));
        }
    }
    public static boolean exists() { return pending != null; }
    public static void propose(ServerPlayer p, ArenaDefs.Encounter enc, ArenaDefs.Tier tier, List<ServerPlayer> group, int mask, int variant, boolean goals) {
        if (pending != null) { p.sendSystemMessage(Component.literal("Es gibt bereits eine Bereitschaftsrunde. /arena bereit")); return; }
        pending = new Pending(p, enc, tier, group, mask, variant, goals);
        for (ServerPlayer q : group) { q.sendSystemMessage(Component.literal("Arena-Einladung: " + enc.name() + " / " + tier.name() + ". Bestaetigen mit /arena bereit.")); }
        broadcast(p.server, true);
    }
    private static boolean eligible(ServerPlayer p) {
        return p != null && !p.isSpectator() && p.level().dimension() == ArenaManager.DIM && ArenaManager.inLobby(p);
    }
    public static void toggle(ServerPlayer p) {
        if (pending == null || !pending.members.contains(p.getUUID()) || !eligible(p)) return;
        if (!pending.ready.remove(p.getUUID())) pending.ready.add(p.getUUID());
        pending.countdown = -1;
        broadcast(p.server, false);
    }
    public static void show(ServerPlayer p) {
        if (pending != null && pending.members.contains(p.getUUID())) send(p, true);
    }
    public static void cancel(ServerPlayer p) {
        if (pending != null && (pending.leader.equals(p.getUUID()) || p.hasPermissions(2))) clear(p.server, "Bereitschaftsrunde abgebrochen.");
    }
    public static void decline(ServerPlayer p) {
        if (pending == null || !pending.members.contains(p.getUUID())) return;
        if (pending.leader.equals(p.getUUID())) { cancel(p); return; }
        pending.members.remove(p.getUUID()); pending.ready.remove(p.getUUID()); pending.countdown = -1;
        PacketDistributor.sendToPlayer(p, new Net.OpenParty("Einladung abgelehnt", List.of(), false, false, -1, false));
        broadcast(p.server, false);
    }
    public static void clear(MinecraftServer server, String reason) {
        if (pending != null) for (UUID id : pending.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) { p.sendSystemMessage(Component.literal(reason)); PacketDistributor.sendToPlayer(p, new Net.OpenParty(reason, List.of(), false, false, -1, false)); }
        }
        pending = null;
    }
    private static void send(ServerPlayer p, boolean popup) {
        List<String> list = new ArrayList<>();
        for (UUID id : pending.members) {
            ServerPlayer member = p.server.getPlayerList().getPlayer(id);
            list.add((pending.ready.contains(id) ? "[Bereit] " : "[Wartet] ") + (member == null ? "Offline" : member.getGameProfile().getName()));
        }
        String summary = pending.encounter.name() + " / " + pending.tier.name() + " | " + ArenaManager.modNames(pending.mask) + (ArenaChallenges.kindOf(pending.mask) >= 0 ? " | " + ArenaChallenges.LABEL[ArenaChallenges.kindOf(pending.mask)] : "")
                + " | Arena: " + new String[]{"Klassisch", "Glut", "Eis"}[pending.variant]
                + (pending.goals ? "" : " | Ohne Missionen");
        PacketDistributor.sendToPlayer(p, new Net.OpenParty(summary, list, pending.ready.contains(p.getUUID()), pending.leader.equals(p.getUUID()), pending.countdown < 0 ? -1 : (pending.countdown + 19) / 20, popup));
    }
    private static void broadcast(MinecraftServer server, boolean popup) {
        if (pending == null) return;
        for (UUID id : pending.members) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) send(p, popup); }
    }
    public static void tick(MinecraftServer server) {
        if (pending == null) return;
        Pending r = pending;
        if (++r.age > 2400 || !eligible(server.getPlayerList().getPlayer(r.leader))) { clear(server, "Einladung abgelaufen oder Gruppenleiter hat die Lobby verlassen."); return; }
        if (r.members.stream().anyMatch(id -> !eligible(server.getPlayerList().getPlayer(id)))) {
            clear(server, "Ein Gruppenmitglied hat die Lobby verlassen. Bitte eine neue Gruppe vorbereiten."); return;
        }
        if (r.ready.containsAll(r.members)) {
            if (r.countdown < 0) r.countdown = ArenaSettings.get(server).prepSeconds * 20;
            if (--r.countdown <= 0) {
                List<ServerPlayer> players = r.members.stream().map(id -> server.getPlayerList().getPlayer(id)).toList();
                clear(server, "Alle bereit - der Kampf beginnt!");
                ArenaManager.startPrepared(server, r.encounter, r.tier, players, r.mask, r.variant, r.goals);
                return;
            }
        }
        if (r.age % 20 == 0) broadcast(server, false);
    }
}
