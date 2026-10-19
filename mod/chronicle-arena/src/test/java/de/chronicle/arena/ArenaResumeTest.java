package de.chronicle.arena;

import java.lang.reflect.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Crash-/Disconnect-Schutz, Kampfstand ueber Neustart und die festen Challenge-Wellen. */
class ArenaResumeTest {
    private MinecraftServer server;
    private ServerLevel level;
    private PlayerList players;
    private ArenaManager.Run run;
    private ArenaDefs.Encounter enc;
    private ArenaDefs.Tier tier;
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();try(var flags=mockStatic(net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class)){Bootstrap.bootStrap();}}
    private static void setRun(ArenaManager.Run value) throws Exception {Field f=ArenaManager.class.getDeclaredField("run");f.setAccessible(true);f.set(null,value);}
    private static Object invoke(String name,Class<?>[] types,Object... args) throws Exception {
        Method m=ArenaManager.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(new ArenaManager(),args);}catch(InvocationTargetException e){throw new AssertionError(e.getCause());}
    }
    private static int ticks() throws Exception {Field f=ArenaManager.class.getDeclaredField("tickCounter");f.setAccessible(true);return f.getInt(null);}
    @SuppressWarnings("unchecked")
    private static void register(ArenaDefs.Encounter e,ArenaDefs.Tier t) throws Exception {
        Field fe=ArenaDefs.class.getDeclaredField("encounters");fe.setAccessible(true);((Map<ResourceLocation,ArenaDefs.Encounter>)fe.get(null)).put(e.id(),e);
        Field ft=ArenaDefs.class.getDeclaredField("tiers");ft.setAccessible(true);((Map<ResourceLocation,ArenaDefs.Tier>)ft.get(null)).put(t.id(),t);
    }
    @BeforeEach void setup() throws Exception {
        server=mock(MinecraftServer.class);level=mock(ServerLevel.class);players=mock(PlayerList.class);
        when(server.getLevel(ArenaManager.DIM)).thenReturn(level);when(server.getPlayerList()).thenReturn(players);when(level.dimension()).thenReturn(ArenaManager.DIM);
        var pools=List.of(new ArenaDefs.Pool(ResourceLocation.withDefaultNamespace("zombie"),1,4,1),new ArenaDefs.Pool(ResourceLocation.withDefaultNamespace("skeleton"),2,2,0.5));
        enc=new ArenaDefs.Encounter(ChronicleArena.id("resume_test"),0,false,"Test","",null,pools,ResourceLocation.withDefaultNamespace("husk"),3,null,null,List.of(),"default");
        tier=new ArenaDefs.Tier(ChronicleArena.id("resume_test"),0,"Test","",1,1,1,10,null,10,2,null,10,1,1,1,1);
        register(enc,tier);
        run=new ArenaManager.Run(enc,tier,2,0,0);run.state=ArenaManager.State.WAVE;run.wave=6;run.completedWaves=5;run.fightTicks=200;run.started=true;setRun(run);
    }
    @AfterEach void cleanup() throws Exception {setRun(null);ArenaPresentation.clear();}
    private ServerPlayer player(String name) throws Exception {
        ServerPlayer p=mock(ServerPlayer.class);UUID id=UUID.randomUUID();
        when(p.getUUID()).thenReturn(id);when(p.level()).thenReturn(level);when(p.serverLevel()).thenReturn(level);
        when(p.position()).thenReturn(Vec3.ZERO);when(p.getY()).thenReturn(64.0);when(p.getHealth()).thenReturn(20F);when(p.getMaxHealth()).thenReturn(20F);
        when(p.getPersistentData()).thenReturn(new CompoundTag());when(p.getDisplayName()).thenReturn(Component.literal(name));when(p.getGameProfile()).thenReturn(new GameProfile(id,name));
        Field serverField=ServerPlayer.class.getField("server");serverField.setAccessible(true);serverField.set(p,server);
        Field connectionField=ServerPlayer.class.getField("connection");connectionField.setAccessible(true);connectionField.set(p,mock(ServerGamePacketListenerImpl.class));
        when(players.getPlayer(id)).thenReturn(p);run.participants.add(id);run.names.put(id,name);
        p.getPersistentData().putInt("chronicle_arena_mode",0);when(p.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(1));return p;
    }
    private void tick() throws Exception {invoke("tickRun",new Class<?>[]{MinecraftServer.class,ArenaManager.Run.class},server,run);}

    @Test void lastPlayerDisconnectPausesInsteadOfLosing() throws Exception {
        var alice=player("Alice");run.alive.add(alice.getUUID());
        invoke("disconnect",new Class<?>[]{ServerPlayer.class,ArenaManager.Run.class},alice,run);
        when(players.getPlayer(alice.getUUID())).thenReturn(null);
        int before=run.fightTicks;
        tick();tick();
        assertEquals(ArenaManager.State.WAVE,run.state);assertTrue(run.paused);assertFalse(run.won);
        assertEquals(before,run.fightTicks,"Pause zaehlt nicht zur Kampfzeit");
        assertTrue(run.away.get(alice.getUUID()).alive());assertTrue(run.dropped.contains(alice.getUUID()));
    }
    @Test void returningPlayerContinuesAliveWithBlessings() throws Exception {
        var alice=player("Alice");run.alive.add(alice.getUUID());run.bless.put(alice.getUUID(),new int[ArenaShow.Blessing.values().length]);
        invoke("disconnect",new Class<?>[]{ServerPlayer.class,ArenaManager.Run.class},alice,run);
        tick();assertTrue(run.paused);
        try(var stats=mockStatic(ArenaStats.class,CALLS_REAL_METHODS)){
            stats.when(()->ArenaStats.top(any(),anyInt())).thenReturn(List.of());
            invoke("rejoin",new Class<?>[]{ServerPlayer.class,ArenaManager.Run.class},alice,run);
        }
        assertTrue(run.alive.contains(alice.getUUID()));assertFalse(run.away.containsKey(alice.getUUID()));assertFalse(run.dropped.contains(alice.getUUID()));
        verify(alice).setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try(var extras=mockStatic(ArenaExtras.class)){tick();}
        assertFalse(run.paused);
    }
    @Test void fallenPlayerComesBackAsRevivableSpectator() throws Exception {
        var alice=player("Alice");var bob=player("Bob");run.alive.add(alice.getUUID());run.spectators.add(bob.getUUID());run.fallen.put(bob.getUUID(),new Vec3(3,64,200));
        invoke("disconnect",new Class<?>[]{ServerPlayer.class,ArenaManager.Run.class},bob,run);
        assertFalse(run.away.get(bob.getUUID()).alive());assertFalse(run.dropped.contains(bob.getUUID()));
        try(var stats=mockStatic(ArenaStats.class,CALLS_REAL_METHODS)){
            stats.when(()->ArenaStats.top(any(),anyInt())).thenReturn(List.of());
            invoke("rejoin",new Class<?>[]{ServerPlayer.class,ArenaManager.Run.class},bob,run);
        }
        assertTrue(run.spectators.contains(bob.getUUID()));assertEquals(new Vec3(3,64,200),run.fallen.get(bob.getUUID()));
    }
    @Test void expiredPlaceEndsTheRunAsLoss() throws Exception {
        var alice=player("Alice");
        run.away.put(alice.getUUID(),new ArenaManager.Away(ticks()-1,true,null));run.dropped.add(alice.getUUID());
        try(var extras=mockStatic(ArenaExtras.class)){tick();}
        assertEquals(ArenaManager.State.ENDING,run.state);assertFalse(run.won);assertTrue(run.away.isEmpty());
    }
    @Test void snapshotRestoresAtLastFinishedWaveWithEveryoneAway() throws Exception {
        var alice=player("Alice");var bob=player("Bob");run.alive.add(alice.getUUID());run.spectators.add(bob.getUUID());
        int[] bl=new int[ArenaShow.Blessing.values().length];bl[ArenaShow.Blessing.POWER.ordinal()]=2;run.bless.put(alice.getUUID(),bl);
        run.stat(alice.getUUID())[1]=17;run.revives=3;run.seed=42L;
        CompoundTag tag=ArenaResume.write(run);
        ArenaManager.Run r=ArenaResume.read(tag,null,5000);
        assertNotNull(r);
        assertEquals(ArenaManager.State.PREP,r.state);assertEquals(5,r.wave);assertEquals(5,r.completedWaves);assertEquals(200,r.fightTicks);
        assertEquals(3,r.revives);assertEquals(42L,r.seed);assertEquals(2,r.extra+1);
        assertTrue(r.away.get(alice.getUUID()).alive());assertFalse(r.away.get(bob.getUUID()).alive());
        assertTrue(r.dropped.contains(alice.getUUID()));assertFalse(r.dropped.contains(bob.getUUID()));
        assertEquals(2,r.bless.get(alice.getUUID())[ArenaShow.Blessing.POWER.ordinal()]);assertEquals(17,r.stat(alice.getUUID())[1]);
        assertEquals("Alice",r.names.get(alice.getUUID()));assertTrue(r.alive.isEmpty());
    }
    @Test void snapshotOfUnknownEncounterIsDropped() throws Exception {
        CompoundTag tag=ArenaResume.write(run);tag.putString("enc","chronicle_arena:gibt_es_nicht");
        assertNull(ArenaResume.read(tag,null,0));
    }
    @Test void sameSeedGivesSameWaves() throws Exception {
        List<String> a=wave(1234L),b=wave(1234L),c=wave(99L);
        assertEquals(a,b);assertNotEquals(a,c);
    }
    private List<String> wave(long seed) throws Exception {
        ArenaManager.Run r=new ArenaManager.Run(enc,tier,1,ArenaManager.MOD_ELITE,0);r.seed=seed;r.wave=9;r.rng.setSeed(ArenaManager.waveSeed(seed,9));
        Method m=ArenaManager.class.getDeclaredMethod("buildWave",ServerLevel.class,ArenaManager.Run.class);m.setAccessible(true);m.invoke(new ArenaManager(),level,r);
        List<String> out=new ArrayList<>();for(var s:r.queue)out.add(s.entity+"/"+s.elite+"/"+s.cls);return out;
    }
    @Test void challengeIsFixedPerPeriodAndWeekIsHarder(){
        var tiers=new ArrayList<ArenaDefs.Tier>();
        for(int i=0;i<4;i++)tiers.add(new ArenaDefs.Tier(ChronicleArena.id("t"+i),i,"T"+i,"",1,1,1,8+4*i,null,10,2,null,10,1,1,1,1));
        tiers.add(new ArenaDefs.Tier(ChronicleArena.id("abgrund"),4,"Abgrund","",1,1,1,0,null,10,2,null,10,1,1,1,1));
        var encs=List.of(enc);
        for(int p=20000;p<20060;p++){
            var d=ArenaChallenges.pick(ArenaChallenges.DAILY,p,encs,tiers);
            assertEquals(d,ArenaChallenges.pick(ArenaChallenges.DAILY,p,encs,tiers));
            assertTrue(d.tier().order()<=2);assertTrue(d.tier().waves()>0);assertTrue(d.mask()>0&&d.mask()<16);
            var w=ArenaChallenges.pick(ArenaChallenges.WEEKLY,p,encs,tiers);
            assertTrue(w.tier().order()>=1&&w.tier().order()<=3);assertTrue(Integer.bitCount(w.mask())>=2);
            assertTrue(w.bonus()>d.bonus()||w.tier().order()<d.tier().order());
        }
        assertEquals(0,ArenaChallenges.week(0));assertEquals(0,ArenaChallenges.week(3));assertEquals(1,ArenaChallenges.week(4),"Montag beginnt eine neue Woche");
        assertEquals(ArenaChallenges.WEEKLY,ArenaChallenges.kindOf(256|3));assertEquals(ArenaChallenges.DAILY,ArenaChallenges.kindOf(128));assertEquals(-1,ArenaChallenges.kindOf(15|64));
    }
    @Test void challengeBoardKeepsBestPerTeamAndResetsEachPeriod(){
        var d=new ArenaChallenges.Data();
        assertEquals(1,d.add(0,10,new ArenaChallenges.Entry("Alice",false,900,4,1)));
        assertEquals(1,d.add(0,10,new ArenaChallenges.Entry("Bob",true,2400,8,1)),"Sieg schlaegt Niederlage");
        assertEquals(2,d.add(0,10,new ArenaChallenges.Entry("Alice",true,3000,8,1)));
        assertEquals(0,d.add(0,10,new ArenaChallenges.Entry("Alice",true,3600,8,1)),"schlechter als eigener Eintrag");
        assertEquals(1,d.add(0,10,new ArenaChallenges.Entry("Alice",true,2000,8,1)));
        assertEquals(2,d.current(0,10).size());
        for(int i=0;i<6;i++)d.add(0,10,new ArenaChallenges.Entry("Team"+i,true,1000+i,8,2));
        assertEquals(5,d.current(0,10).size());
        assertEquals(0,d.add(0,9,new ArenaChallenges.Entry("Alt",true,1,8,1)),"alter Zeitraum zaehlt nicht");
        assertEquals(1,d.add(0,11,new ArenaChallenges.Entry("Neu",false,10,1,1)));
        assertEquals(1,d.current(0,11).size());assertTrue(d.current(0,10).isEmpty());
        assertTrue(d.current(1,11).isEmpty());
    }
    @Test void routeChoiceSurvivesSnapshotButOpenBallotRestarts(){
        var c=new ArenaRouteChoice(UUID.randomUUID());var id=UUID.randomUUID();
        assertTrue(c.begin(5,10,false,true,Set.of(id)));c.cast(id,UUID.randomUUID(),5,0);
        var open=new ArenaRouteChoice(UUID.randomUUID());open.load(c.save());assertEquals(0,open.checkpoint());assertFalse(open.voting());
        for(int i=0;i<ArenaRouteChoice.VOTE_TICKS;i++)c.tick(Set.of(id));
        var loaded=new ArenaRouteChoice(UUID.randomUUID());loaded.load(c.save());
        assertEquals(5,loaded.checkpoint());assertEquals(c.active(6),loaded.active(6));assertEquals(c.health(7),loaded.health(7));
    }
}
