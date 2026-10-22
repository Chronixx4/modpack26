package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Modus-Schicht: Wellen und Boss laufen im ArenaManager selbst, weitere Modi (bisher Burg halten) haengen sich hier ein.
 * Jede Methode hat ein neutrales Standardverhalten; der Manager fragt sie an festen Stellen ab.
 * Gemeinsam bleiben Crash-Schutz, Wiederbeleben, Killcam, Segen, Bestzeiten und HUD.
 */
abstract class ModeLogic {
    final ArenaManager.Run r;

    ModeLogic(ArenaManager.Run r) { this.r = r; }

    static ModeLogic create(ArenaManager.Run r) {
        return switch (r.mode) {
            case SIEGE -> new ArenaSiege(r);
            default -> null;
        };
    }

    /** Karte bauen bzw. zuruecksetzen, Startwerte setzen. Wird vor dem Teleport aufgerufen. */
    void start(ServerLevel level) {}

    /** Nach dem Teleport der Gruppe. */
    void started(MinecraftServer server, ServerLevel level) {}

    /** Startpunkt des i-ten von n Spielern; null = Kreis um die Mitte. */
    Vec3 startPos(int i, int n) { return null; }

    /** Nach dem Zusammenstellen einer Welle. */
    void afterBuildWave(ServerLevel level) {}

    /** Ob in dieser Welle eine Mission kommt; null = Standard (Gruppenweg oder jede N-te Welle). */
    Boolean objectiveNow() { return null; }

    /** Spawnpunkt; null = Standard rund um die Arenamitte. */
    Vec3 spawnPos(ServerLevel level, ArenaManager.Spawn s) { return null; }

    /** Nach dem Spawnen eines Gegners. */
    void spawned(LivingEntity le, ArenaManager.Spawn s) {}

    /** true = der Modus steuert Laufwege und Ziele der Gegner selbst (kein Standard-Aggro). */
    boolean controlsAi() { return false; }

    /** Darf ein Gegner dieses Ziel angreifen? */
    boolean allowTarget(LivingEntity mob, LivingEntity target) { return true; }

    /** Schaden eines Spielers an einem Gegner des Kampfes; 0 oder weniger = Treffer zaehlt nicht. */
    float playerHit(ServerPlayer attacker, LivingEntity mob, float amount) { return amount; }

    /** Jeder Kampf-Tick. */
    void tickFight(MinecraftServer server, ServerLevel level, int tick) {}

    /** Jeder Tick in der Pause; true = Pause festhalten. */
    boolean holdPrep(MinecraftServer server, ServerLevel level, int tick) { return false; }

    /** Niederlage aus Modus-Gruenden (z. B. Kristall zerstoert). */
    boolean lost() { return false; }

    /** Nach einer geschafften Welle; true = der Modus hat das Wellenende selbst behandelt. */
    boolean waveCleared(MinecraftServer server, ServerLevel level) { return false; }

    /** Pause nach einer Welle in Ticks; -1 = Standard. */
    int pauseTicks(boolean offer) { return -1; }

    /** Rechtsklick auf einen Block; true = verbraucht. */
    boolean interact(ServerPlayer p, BlockPos pos) { return false; }

    /** Auswahl in einem Modus-Menue. */
    void menuPick(ServerPlayer p, String menu, int option) {}

    /** Ein Gegner des Kampfes ist gestorben oder verschwunden; killer kann null sein. */
    void mobDied(LivingEntity dead, ServerPlayer killer) {}

    /** Alle Weltaenderungen des Modus entfernen (Kampfende, Abbruch, Neustart). */
    void clear(ServerLevel level) {}

    /** Phase fuer das HUD; null = Standard. */
    String phase() { return null; }

    /** Zusatzzeile im HUD fuer diesen Spieler. */
    String info(UUID id) { return ""; }

    /** Leben des Schutzziels fuer das HUD; max 0 = keins. */
    int coreHealth() { return 0; }

    int coreMax() { return 0; }

    String coreLabel() { return "Kristall"; }

    boolean routes() { return true; }

    boolean shooters() { return true; }

    CompoundTag save() { return new CompoundTag(); }

    /** Nach einem Neustart: Stand laden und Karte passend herrichten. */
    void load(CompoundTag tag, ServerLevel level) {}
}
