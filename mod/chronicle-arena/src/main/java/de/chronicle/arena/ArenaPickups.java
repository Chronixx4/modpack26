package de.chronicle.arena;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Only registered run-owned entities can activate run-scoped effects. */
final class ArenaPickups {
    record Drop(int kind,int expires) {}
    static String name(int kind){return switch(kind){case 0->"Stasis-Rune";case 1->"Echo-Rune";default->"Runenschild";};}
    static void spawn(ServerLevel level,ArenaManager.Run r){
        if(r.pickups.size()>=3)return;
        for(int i=0;i<16;i++){
            double a=level.getRandom().nextDouble()*Math.PI*2,d=12+level.getRandom().nextDouble()*12;
            BlockPos at=r.center.offset((int)(Math.cos(a)*d),0,(int)(Math.sin(a)*d));
            if(!level.getBlockState(at).isAir()||!level.getBlockState(at.above()).isAir()||!level.getBlockState(at.below()).isSolid())continue;
            int kind=level.getRandom().nextInt(3);
            ItemEntity item=new ItemEntity(level,at.getX()+0.5,at.getY()+0.25,at.getZ()+0.5,new ItemStack(kind==0?ModRegistry.STASIS.get():kind==1?ModRegistry.ECHO.get():ModRegistry.WARD.get()));
            item.setDeltaMovement(0,0,0);item.setInvulnerable(true);item.setPickUpDelay(32767);item.setGlowingTag(true);item.addTag("chronicle_arena_pickup");
            item.setCustomName(Component.literal(name(kind)));item.setCustomNameVisible(true);
            if(level.addFreshEntity(item))r.pickups.put(item.getUUID(),new Drop(kind,r.fightTicks+240));return;
        }
    }
    static void tick(ServerLevel level,ArenaManager.Run r){
        if(r.fightTicks%300==0)spawn(level,r);
        for(var it=r.pickups.entrySet().iterator();it.hasNext();){var en=it.next();Entity item=level.getEntity(en.getKey());
            if(item==null||!item.isAlive()||r.fightTicks>=en.getValue().expires){if(item!=null)item.discard();it.remove();continue;}
            ServerPlayer taker=null;
            for(UUID id:r.alive){ServerPlayer p=level.getServer().getPlayerList().getPlayer(id);if(p!=null&&!p.isSpectator()&&p.level()==level&&p.distanceToSqr(item)<2.25){taker=p;break;}}
            if(taker!=null){activate(r,taker,en.getValue().kind);item.discard();it.remove();}
            else if(r.fightTicks%20==0)level.sendParticles(ParticleTypes.END_ROD,item.getX(),item.getY()+0.8,item.getZ(),3,0.1,0.2,0.1,0);
        }
        if(r.fightTicks%10==0)for(var en:r.stasisUntil.entrySet()){
            if(en.getValue()<=r.fightTicks)continue;ServerPlayer p=level.getServer().getPlayerList().getPlayer(en.getKey());if(p==null||!r.alive.contains(en.getKey()))continue;
            for(UUID id:r.mobs)if(level.getEntity(id) instanceof LivingEntity m&&m.distanceToSqr(p)<64)m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20,4,false,true));
            level.sendParticles(ParticleTypes.SNOWFLAKE,p.getX(),p.getY()+1,p.getZ(),12,3,0.5,3,0);
        }
    }
    static void activate(ArenaManager.Run r,ServerPlayer p,int kind){
        if(kind==0)r.stasisUntil.put(p.getUUID(),r.fightTicks+100);
        else if(kind==1)r.echo.put(p.getUUID(),3);
        else {r.ward.put(p.getUUID(),4F);r.wardUntil.put(p.getUUID(),r.fightTicks+160);}
        p.sendSystemMessage(Component.literal(name(kind)+": "+(kind==0?"Gegner im Radius 8 fuer 5 s stark verlangsamt":kind==1?"Die naechsten 3 Treffer erzeugen einen Echo-Schlag":"Schutz gegen 4 Schaden fuer 8 s")));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,0.7F,1.2F);
    }
    static float shield(ArenaManager.Run r,ServerPlayer p,float damage){
        if(r.wardUntil.getOrDefault(p.getUUID(),0)<=r.fightTicks)return damage;
        float available=r.ward.getOrDefault(p.getUUID(),0F),used=Math.min(Math.max(0,damage),available);r.ward.put(p.getUUID(),available-used);return damage-used;
    }
    static void echo(ServerLevel level,ArenaManager.Run r,ServerPlayer p,LivingEntity hit){
        int charges=r.echo.getOrDefault(p.getUUID(),0);if(charges<=0||r.echoBusy.contains(p.getUUID()))return;
        LivingEntity next=null;double dist=49;
        for(UUID id:r.mobs)if(level.getEntity(id) instanceof LivingEntity m&&m!=hit&&m.isAlive()){double d=m.distanceToSqr(hit);if(d<dist){dist=d;next=m;}}
        if(next==null)return;r.echo.put(p.getUUID(),charges-1);r.echoBusy.add(p.getUUID());
        try{next.hurt(p.damageSources().playerAttack(p),4F);level.sendParticles(ParticleTypes.ELECTRIC_SPARK,next.getX(),next.getY()+1,next.getZ(),18,0.3,0.5,0.3,0);}finally{r.echoBusy.remove(p.getUUID());}
    }
    static String status(ArenaManager.Run r,UUID id){
        List<String> s=new ArrayList<>();int echo=r.echo.getOrDefault(id,0);if(echo>0)s.add("Echo: "+echo);
        if(r.stasisUntil.getOrDefault(id,0)>r.fightTicks)s.add("Stasis: "+((r.stasisUntil.get(id)-r.fightTicks+19)/20)+" s");
        if(r.wardUntil.getOrDefault(id,0)>r.fightTicks&&r.ward.getOrDefault(id,0F)>0)s.add("Schild: "+String.format(java.util.Locale.ROOT,"%.1f",r.ward.get(id)));
        return String.join(" | ",s);
    }
    static void clear(ServerLevel level,ArenaManager.Run r){if(level!=null)for(UUID id:r.pickups.keySet()){Entity e=level.getEntity(id);if(e!=null)e.discard();}r.pickups.clear();}
}
