package de.chronicle.arena;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Wave goals own their markers and clean up on every normal/aborted run exit. */
public final class ArenaObjectives {
    public static final class Objective {
        final int kind;
        final BlockPos center;
        final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final Set<BlockPos> crystals=new HashSet<>();
        final Set<UUID> marked=new HashSet<>();
        final List<BlockPos> points=new ArrayList<>();
        int ticks,held,markedSpawned,markedKilled,checkpoint,expectedTargets=1;
        UUID escort,defender;
        float health=100;
        final Map<UUID,Integer> lastStrike=new HashMap<>();
        boolean failed;
        Objective(int kind,BlockPos center){this.kind=kind;this.center=center;}
        boolean complete(){return switch(kind){case 0->crystals.isEmpty();case 1->health>0&&held>=600;case 2->markedSpawned>=expectedTargets&&marked.isEmpty();case 3,4->checkpoint>=3;default->false;};}
    }
    /** Maps a uniform roll to all goal types except the preceding one. */
    static int chooseKind(int roll,int previous){return previous>=0&&previous<5?(roll>=previous?roll+1:roll):roll;}
    public static Objective begin(ServerLevel level,BlockPos center,int previous){
        int kind=chooseKind(level.getRandom().nextInt(previous>=0?4:5),previous);
        Objective o=new Objective(kind,center);
        // Rotate the same safe, short route through four different orientations.
        int turn=level.getRandom().nextInt(4);
        for(int[] v:new int[][]{{-12,-8},{0,12},{12,-8}}){int x=v[0],z=v[1];for(int i=0;i<turn;i++){int old=x;x=-z;z=old;}o.points.add(center.offset(x,0,z));}
        if(kind==0||kind==1||kind==4){
            List<BlockPos> needed=kind==1?List.of(o.points.get(0)):o.points;
            // Never overwrite containers, existing builds or their block entity data.
            if(needed.stream().anyMatch(p->!level.getBlockState(p).isAir()||!level.getBlockState(p.above()).isAir()||!level.getBlockState(p.below()).isSolid()))return new Objective(2,center);
            for(BlockPos p:needed){o.originals.put(p,level.getBlockState(p));level.setBlockAndUpdate(p,(kind==0?Blocks.AMETHYST_BLOCK:Blocks.BEACON).defaultBlockState());if(kind==0)o.crystals.add(p);}
            if(kind==1){
                Villager proxy=EntityType.VILLAGER.create(level);
                if(proxy==null){clear(level,o);return new Objective(2,center);}
                proxy.setNoAi(true);proxy.setNoGravity(true);proxy.setSilent(true);proxy.setPersistenceRequired();proxy.addTag("chronicle_arena_beacon");
                BlockPos at=o.points.get(0);proxy.moveTo(at.getX()+0.5,at.getY()+1,at.getZ()+0.5,0,0);
                if(!level.addFreshEntity(proxy)){clear(level,o);return new Objective(2,center);}proxy.setInvisible(true);o.defender=proxy.getUUID();
            }
        }else if(kind==3){
            Villager guide=EntityType.VILLAGER.create(level);
            if(guide==null)return new Objective(2,center);
            guide.setNoAi(true);guide.setInvulnerable(true);guide.setSilent(true);guide.setPersistenceRequired();guide.setGlowingTag(true);
            guide.setCustomName(Component.literal("Arena-Fuehrer - Bleibt in meiner Naehe!"));guide.setCustomNameVisible(true);
            guide.moveTo(center.getX()+0.5,center.getY(),center.getZ()+0.5,0,0);
            if(!level.addFreshEntity(guide))return new Objective(2,center);
            o.escort=guide.getUUID();
        }
        return o;
    }
    public static boolean interact(ServerPlayer p,BlockPos pos,Objective o){
        if(o==null||o.kind!=0||!o.crystals.contains(pos)||p.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>36)return false;
        o.crystals.remove(pos);p.serverLevel().setBlockAndUpdate(pos,o.originals.get(pos));return true;
    }
    public static void spawned(Objective o,LivingEntity e){if(o!=null&&o.kind==2&&o.markedSpawned<3){o.marked.add(e.getUUID());o.markedSpawned++;e.setGlowingTag(true);e.setCustomName(Component.literal("ZIELGEGNER"));e.setCustomNameVisible(true);}}
    public static void killed(Objective o,UUID id){if(o!=null&&o.marked.remove(id))o.markedKilled++;}
    static boolean canAdvance(boolean defender,boolean contested){return defender&&!contested;}
    static void captureTick(Objective o,boolean defended){
        if(!defended||o.complete())return;
        o.held++;
        if(o.kind==4&&o.held>=200){o.checkpoint++;o.held=0;}
    }
    private static boolean defend(MinecraftServer server,ServerLevel level,Vec3 at,Set<UUID> alive,List<UUID> mobs){
        boolean defender=alive.stream().map(id->server.getPlayerList().getPlayer(id)).anyMatch(p->p!=null&&p.level()==level&&p.distanceToSqr(at)<36);
        boolean contested=mobs.stream().map(level::getEntity).anyMatch(e->e!=null&&e.isAlive()&&e.distanceToSqr(at)<16);
        return canAdvance(defender,contested);
    }
    public static void tick(MinecraftServer server,ServerLevel level,Objective o,Set<UUID> alive,List<UUID> mobs){
        if(o.complete())return;o.ticks++;
        if(o.kind==1){
            LivingEntity proxy=defender(level,o);
            if(proxy==null){o.failed=true;return;}
            BlockPos at=o.points.get(0);proxy.teleportTo(at.getX()+0.5,at.getY()+1,at.getZ()+0.5);proxy.setInvisible(true);proxy.setDeltaMovement(Vec3.ZERO);proxy.clearFire();
            if(o.health<=0){o.failed=true;return;}o.held++;if(o.complete())proxy.discard();
            if(o.ticks%20==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,at.getX()+0.5,at.getY()+2,at.getZ()+0.5,15,2,0.3,2,0);
        }else if(o.kind==4){
            BlockPos point=o.points.get(o.checkpoint);Vec3 at=Vec3.atCenterOf(point);
            captureTick(o,defend(server,level,at,alive,mobs));
            if(o.ticks%20==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,at.x,at.y+2,at.z,15,2,0.3,2,0);
        }else if(o.kind==3){
            Entity guide=o.escort==null?null:level.getEntity(o.escort);
            if(guide==null||!guide.isAlive()){o.failed=true;return;}
            Vec3 to=Vec3.atBottomCenterOf(o.points.get(o.checkpoint));Vec3 delta=to.subtract(guide.position());
            if(delta.lengthSqr()<0.25)o.checkpoint++;
            else if(defend(server,level,guide.position(),alive,mobs)){
                Vec3 next=guide.position().add(delta.normalize().scale(0.05));BlockPos at=BlockPos.containing(next);
                if(level.getBlockState(at).isAir()&&level.getBlockState(at.above()).isAir()&&level.getBlockState(at.below()).isSolid())guide.teleportTo(next.x,next.y,next.z);
            }
            if(o.ticks%10==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,to.x,to.y+1,to.z,6,0.4,0.2,0.4,0);
        }
        if(o.kind==2)o.marked.removeIf(id->{Entity e=level.getEntity(id);return e==null||!e.isAlive();});
        if(o.ticks>limit(o)&&!o.complete())o.failed=true;
    }
    public static LivingEntity defender(ServerLevel level,Objective o){
        if(o==null||o.kind!=1||o.complete()||o.failed||o.defender==null)return null;
        return level.getEntity(o.defender) instanceof LivingEntity le&&le.isAlive()?le:null;
    }
    static void strike(Objective o,UUID attacker,float damage){
        if(o==null||o.kind!=1||o.complete()||o.failed||!Float.isFinite(damage)||damage<=0)return;
        if(o.ticks-o.lastStrike.getOrDefault(attacker,-100)>=20){o.lastStrike.put(attacker,o.ticks);o.health=Math.max(0,o.health-Math.clamp(damage,1,8));if(o.health<=0)o.failed=true;}
    }
    static boolean pressureMission(Objective o){return o!=null&&(o.kind==1||o.kind==3||o.kind==4);}
    static boolean needsPressure(Objective o){return pressureMission(o)&&!o.complete()&&!o.failed;}
    public static int limit(Objective o){return o.kind==2?1800:3600;}
    public static int progress(Objective o){return switch(o.kind){case 0->3-o.crystals.size();case 1->Math.min(30,o.held/20);case 2->o.markedSpawned-o.marked.size();case 3->o.checkpoint;case 4->o.checkpoint*10+o.held/20;default->0;};}
    public static int target(Objective o){return switch(o.kind){case 1,4->30;case 2->o.expectedTargets;default->3;};}
    public static String label(Objective o){return switch(o.kind){case 0->"Kristalle rechtsklicken";case 1->"Beacon verteidigen: 30 Sekunden";case 2->"Leuchtende Gegner besiegen";case 3->"NPC folgen (Radius 6); Gegner fernhalten";case 4->"Kontrollpunkte halten: "+Math.min(3,o.checkpoint+1)+"/3";default->"";};}
    public static String status(Objective o){return label(o)+" | "+progress(o)+"/"+target(o)+" | "+Math.max(0,(limit(o)-o.ticks)/20)+" s";}
    public static void clear(ServerLevel level,Objective o){if(level==null||o==null)return;o.originals.forEach(level::setBlockAndUpdate);if(o.defender!=null){Entity e=level.getEntity(o.defender);if(e!=null)e.discard();}if(o.escort!=null){Entity e=level.getEntity(o.escort);if(e!=null)e.discard();}}
}
