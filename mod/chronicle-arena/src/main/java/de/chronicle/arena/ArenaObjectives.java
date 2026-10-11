package de.chronicle.arena;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

/** Additional wave goals, isolated from the arena's combat state machine. */
public final class ArenaObjectives {
    public static final class Objective {
        final int kind;
        final BlockPos center;
        final Map<BlockPos,BlockState> originals = new LinkedHashMap<>();
        final Set<BlockPos> crystals = new HashSet<>();
        final Set<UUID> marked = new HashSet<>();
        int ticks, held, markedSpawned, markedKilled;
        boolean failed;
        Objective(int kind, BlockPos center) { this.kind = kind; this.center = center; }
        boolean complete() {
            return switch(kind) { case 0 -> crystals.isEmpty(); case 1 -> held >= 600; default -> markedSpawned > 0 && marked.isEmpty(); };
        }
    }
    public static Objective begin(ServerLevel level, BlockPos center, int round) {
        Objective o = new Objective((round - 1) % 3, center);
        if (o.kind == 0) {
            for (int[] offset : new int[][]{{12,0},{-6,10},{-6,-10}}) {
                BlockPos pos = center.offset(offset[0],0,offset[1]);
                o.originals.put(pos,level.getBlockState(pos)); o.crystals.add(pos);
                level.setBlockAndUpdate(pos,Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        } else if(o.kind == 1) {
            o.originals.put(center,level.getBlockState(center));
            level.setBlockAndUpdate(center,Blocks.BEACON.defaultBlockState());
        }
        return o;
    }
    public static boolean interact(ServerPlayer player, BlockPos pos, Objective o) {
        if(o==null||o.kind!=0||!o.crystals.contains(pos)||player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>36) return false;
        o.crystals.remove(pos);
        player.serverLevel().setBlockAndUpdate(pos,o.originals.get(pos));
        player.sendSystemMessage(Component.literal("Kristall zerstoert! Noch " + o.crystals.size()));
        return true;
    }
    public static void spawned(Objective o, LivingEntity entity) {
        if(o!=null&&o.kind==2&&o.markedSpawned<3) { o.marked.add(entity.getUUID());o.markedSpawned++;entity.setGlowingTag(true);entity.setCustomName(Component.literal("ZIELGEGNER"));entity.setCustomNameVisible(true); }
    }
    public static void killed(Objective o, UUID id) { if(o!=null&&o.marked.remove(id))o.markedKilled++; }
    public static void tick(MinecraftServer server, ServerLevel level, Objective o, Set<UUID> alive, List<UUID> mobs) {
        if(o.complete()) return;
        o.ticks++;
        if(o.kind==1) {
            boolean defender=alive.stream().map(id->server.getPlayerList().getPlayer(id)).anyMatch(p->p!=null&&p.distanceToSqr(o.center.getX()+0.5,o.center.getY(),o.center.getZ()+0.5)<36);
            boolean contested=mobs.stream().map(level::getEntity).anyMatch(e->e!=null&&e.isAlive()&&e.distanceToSqr(o.center.getX()+0.5,o.center.getY(),o.center.getZ()+0.5)<16);
            if(defender&&!contested)o.held++;
            if(o.ticks%20==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,o.center.getX()+0.5,o.center.getY()+2,o.center.getZ()+0.5,12,2,0.3,2,0);
        }
        // Dead/despawned targets cannot make a wave unwinnable.
        if(o.kind==2)o.marked.removeIf(id->{ Entity e=level.getEntity(id);return e==null||!e.isAlive(); });
        int limit=o.kind==2?1800:3600;
        if(o.ticks>limit&&!o.complete())o.failed=true;
    }
    public static String status(Objective o) {
        return switch(o.kind) {
            case 0 -> "Ziel: 3 Kristalle rechtsklicken | Noch " + o.crystals.size() + " | " + Math.max(0,(3600-o.ticks)/20) + " s";
            case 1 -> "Ziel: Obelisk halten (Radius 6, Gegner blockieren) | " + Math.min(30,o.held/20) + "/30 s | " + Math.max(0,(3600-o.ticks)/20) + " s";
            default -> "Ziel: Leuchtende Gegner besiegen | " + (o.markedSpawned-o.marked.size()) + "/" + o.markedSpawned + " | " + Math.max(0,(1800-o.ticks)/20) + " s";
        };
    }
    public static void clear(ServerLevel level, Objective o) { if(level!=null&&o!=null)o.originals.forEach(level::setBlockAndUpdate); }
}
