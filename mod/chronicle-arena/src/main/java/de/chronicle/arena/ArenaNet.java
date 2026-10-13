package de.chronicle.arena;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Bounded presentation packets. The server owns choices, rewards and retries. */
public final class ArenaNet {
    private ArenaNet() {}
    public static void register(PayloadRegistrar r) {
        r.playToClient(Routes.TYPE,Routes.CODEC,(p,c)->c.enqueueWork(()->de.chronicle.arena.client.ClientHooks.routes(p)));
        r.playToServer(RouteVote.TYPE,RouteVote.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp)ArenaManager.voteRoute(sp,p);}));
        r.playToClient(Battle.TYPE,Battle.CODEC,(p,c)->c.enqueueWork(()->de.chronicle.arena.client.ClientHooks.battle(p)));
        r.playToClient(Blessings.TYPE,Blessings.CODEC,(p,c)->c.enqueueWork(()->de.chronicle.arena.client.ClientHooks.blessings(p)));
        r.playToClient(Result.TYPE,Result.CODEC,(p,c)->c.enqueueWork(()->de.chronicle.arena.client.ClientHooks.result(p)));
        r.playToServer(Pick.TYPE,Pick.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp)ArenaManager.pick(sp,p);}));
        r.playToServer(Retry.TYPE,Retry.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp)ArenaPresentation.retry(sp,p.run());}));
    }
    private static int count(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid arena list size");return n;}
    private static void strings(FriendlyByteBuf b,List<String> list,int max){if(list.size()>max)throw new IllegalArgumentException("Arena list too large");b.writeVarInt(list.size());for(String s:list)b.writeUtf(s,512);}
    private static List<String> strings(FriendlyByteBuf b,int max){int n=count(b,max);List<String> out=new ArrayList<>();for(int i=0;i<n;i++)out.add(b.readUtf(512));return List.copyOf(out);}
    public record Battle(boolean active,String phase,int wave,int total,int enemies,int revives,String goal,int progress,int target,int seconds,int objectiveHealth,int objectiveMaxHealth,String pickups) implements CustomPacketPayload {
        public static final Type<Battle> TYPE=new Type<>(ChronicleArena.id("battle"));
        public static final Battle EMPTY=new Battle(false,"",0,0,0,0,"",0,0,0,0,0,"");
        public static final StreamCodec<FriendlyByteBuf,Battle> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.active);b.writeUtf(p.phase,128);b.writeVarInt(p.wave);b.writeVarInt(p.total);b.writeVarInt(p.enemies);b.writeVarInt(p.revives);b.writeUtf(p.goal,256);b.writeVarInt(p.progress);b.writeVarInt(p.target);b.writeVarInt(p.seconds);b.writeVarInt(p.objectiveHealth);b.writeVarInt(p.objectiveMaxHealth);b.writeUtf(p.pickups,128);},b->new Battle(b.readBoolean(),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(128)));
        public Battle withPickups(String value){return new Battle(active,phase,wave,total,enemies,revives,goal,progress,target,seconds,objectiveHealth,objectiveMaxHealth,value);}
        public Type<Battle> type(){return TYPE;}
    }
    public record Routes(UUID run,int wave,boolean open,int seconds,int voted,int voters,int risk,int recovery,int mission,int choice,boolean goalsEnabled,boolean noHeal,boolean popup) implements CustomPacketPayload {
        public static final Type<Routes> TYPE=new Type<>(ChronicleArena.id("routes"));
        public static final StreamCodec<FriendlyByteBuf,Routes> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.run);b.writeVarInt(p.wave);b.writeBoolean(p.open);b.writeVarInt(p.seconds);
            b.writeVarInt(p.voted);b.writeVarInt(p.voters);b.writeVarInt(p.risk);b.writeVarInt(p.recovery);b.writeVarInt(p.mission);
            b.writeVarInt(p.choice);b.writeBoolean(p.goalsEnabled);b.writeBoolean(p.noHeal);b.writeBoolean(p.popup);
        },b->new Routes(b.readUUID(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        public int count(int slot){return switch(slot){case 0->risk;case 1->recovery;default->mission;};}
        public Type<Routes> type(){return TYPE;}
    }
    public record RouteVote(UUID run,int wave,int slot) implements CustomPacketPayload {
        public static final Type<RouteVote> TYPE=new Type<>(ChronicleArena.id("route_vote"));
        public static final StreamCodec<FriendlyByteBuf,RouteVote> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.run);b.writeVarInt(p.wave);b.writeVarInt(p.slot);},b->new RouteVote(b.readUUID(),b.readVarInt(),b.readVarInt()));
        public Type<RouteVote> type(){return TYPE;}
    }
    public record BlessInfo(String name,String description,String icon,int stacks,String synergy) {}
    private static void blessings(FriendlyByteBuf b,List<BlessInfo> list,int max){if(list.size()>max)throw new IllegalArgumentException("Too many blessings");b.writeVarInt(list.size());for(var p:list){b.writeUtf(p.name,64);b.writeUtf(p.description,512);b.writeUtf(p.icon,128);b.writeVarInt(p.stacks);b.writeUtf(p.synergy,256);}}
    private static List<BlessInfo> blessings(FriendlyByteBuf b,int max){int n=count(b,max);List<BlessInfo> out=new ArrayList<>();for(int i=0;i<n;i++)out.add(new BlessInfo(b.readUtf(64),b.readUtf(512),b.readUtf(128),b.readVarInt(),b.readUtf(256)));return List.copyOf(out);}
    public record Blessings(UUID run,int wave,List<BlessInfo> offers,List<BlessInfo> active,int seconds,boolean popup) implements CustomPacketPayload {
        public static final Type<Blessings> TYPE=new Type<>(ChronicleArena.id("blessings"));
        public static final StreamCodec<FriendlyByteBuf,Blessings> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.run);b.writeVarInt(p.wave);blessings(b,p.offers,3);blessings(b,p.active,32);b.writeVarInt(p.seconds);b.writeBoolean(p.popup);},b->new Blessings(b.readUUID(),b.readVarInt(),blessings(b,3),blessings(b,32),b.readVarInt(),b.readBoolean()));
        public Type<Blessings> type(){return TYPE;}
    }
    public record Pick(UUID run,int wave,int slot) implements CustomPacketPayload {
        public static final Type<Pick> TYPE=new Type<>(ChronicleArena.id("pick"));
        public static final StreamCodec<FriendlyByteBuf,Pick> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.run);b.writeVarInt(p.wave);b.writeVarInt(p.slot);},b->new Pick(b.readUUID(),b.readVarInt(),b.readVarInt()));
        public Type<Pick> type(){return TYPE;}
    }
    public record Result(UUID run,boolean won,String encounter,String tier,int wave,String time,int coins,int xp,List<String> loot,List<String> team) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(ChronicleArena.id("result"));
        public static final StreamCodec<FriendlyByteBuf,Result> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.run);b.writeBoolean(p.won);b.writeUtf(p.encounter,128);b.writeUtf(p.tier,128);b.writeVarInt(p.wave);b.writeUtf(p.time,64);b.writeVarInt(p.coins);b.writeVarInt(p.xp);strings(b,p.loot,24);strings(b,p.team,1024);},b->new Result(b.readUUID(),b.readBoolean(),b.readUtf(128),b.readUtf(128),b.readVarInt(),b.readUtf(64),b.readVarInt(),b.readVarInt(),strings(b,24),strings(b,1024)));
        public Type<Result> type(){return TYPE;}
    }
    public record Retry(UUID run) implements CustomPacketPayload {
        public static final Type<Retry> TYPE=new Type<>(ChronicleArena.id("retry"));
        public static final StreamCodec<FriendlyByteBuf,Retry> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.run),b->new Retry(b.readUUID()));
        public Type<Retry> type(){return TYPE;}
    }
}
