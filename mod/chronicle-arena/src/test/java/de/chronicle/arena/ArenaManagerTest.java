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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise real event handlers and run transitions with mocked players/levels, without launching a server. */
class ArenaManagerTest {
    private MinecraftServer server;
    private ServerLevel level;
    private PlayerList players;
    private ArenaManager.Run run;
    private final ArenaManager manager=new ArenaManager();
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();try(var flags=mockStatic(net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class)){Bootstrap.bootStrap();}}
    private static void setRun(ArenaManager.Run value) throws Exception {Field f=ArenaManager.class.getDeclaredField("run");f.setAccessible(true);f.set(null,value);}
    private static void invoke(String name,Class<?>[] types,Object... args) throws Exception {
        Method m=ArenaManager.class.getDeclaredMethod(name,types);m.setAccessible(true);
        try{m.invoke(new ArenaManager(),args);}catch(InvocationTargetException e){throw new AssertionError(e.getCause());}
    }
    @BeforeEach void setup() throws Exception {
        server=mock(MinecraftServer.class);level=mock(ServerLevel.class);players=mock(PlayerList.class);
        when(server.getLevel(ArenaManager.DIM)).thenReturn(level);when(server.getPlayerList()).thenReturn(players);when(level.dimension()).thenReturn(ArenaManager.DIM);
        var enc=new ArenaDefs.Encounter(ChronicleArena.id("test"),0,false,"Test","",null,List.of(),null,5,null,null,List.of(),"default");
        var tier=new ArenaDefs.Tier(ChronicleArena.id("test"),0,"Test","",1,1,1,0,null,10,2,null,10,1,1,1,1);
        run=new ArenaManager.Run(enc,tier,2,0,0);run.state=ArenaManager.State.WAVE;run.wave=6;run.completedWaves=5;run.fightTicks=1;setRun(run);
    }
    @AfterEach void cleanup() throws Exception {setRun(null);ArenaPresentation.clear();}
    private ServerPlayer player(String name) throws Exception {
        ServerPlayer p=mock(ServerPlayer.class);UUID id=UUID.randomUUID();
        when(p.getUUID()).thenReturn(id);when(p.level()).thenReturn(level);when(p.serverLevel()).thenReturn(level);
        when(p.position()).thenReturn(Vec3.ZERO);when(p.getY()).thenReturn(64.0);when(p.getHealth()).thenReturn(20F);when(p.getMaxHealth()).thenReturn(20F);
        when(p.getPersistentData()).thenReturn(new CompoundTag());when(p.getDisplayName()).thenReturn(Component.literal(name));when(p.getGameProfile()).thenReturn(new GameProfile(id,name));
        Field serverField=ServerPlayer.class.getField("server");serverField.setAccessible(true);serverField.set(p,server);
        Field connectionField=ServerPlayer.class.getField("connection");connectionField.setAccessible(true);connectionField.set(p,mock(ServerGamePacketListenerImpl.class));
        when(players.getPlayer(id)).thenReturn(p);run.participants.add(id);run.names.put(id,name);return p;
    }
    private DamageSource attack(Mob enemy){return new DamageSource(Holder.direct(new DamageType("test",DamageScaling.NEVER,0)),enemy);}
    @Test void rawDamageDoesNotEliminateBeforeArmorAndActualDeathBecomesSpectator() throws Exception {
        var alice=player("Alice");var bob=player("Bob");run.alive.add(alice.getUUID());run.alive.add(bob.getUUID());
        Mob enemy=mock(Mob.class);when(enemy.getUUID()).thenReturn(UUID.randomUUID());when(enemy.isAlive()).thenReturn(true);when(enemy.getDisplayName()).thenReturn(Component.literal("Zombie"));
        var incoming=new LivingIncomingDamageEvent(alice,new net.neoforged.neoforge.common.damagesource.DamageContainer(attack(enemy),40));
        manager.onIncomingDamage(incoming);
        assertFalse(incoming.isCanceled());assertTrue(run.alive.contains(alice.getUUID()));assertFalse(run.spectators.contains(alice.getUUID()));
        var death=new LivingDeathEvent(alice,attack(enemy));manager.onDeath(death);
        assertTrue(death.isCanceled());assertFalse(run.alive.contains(alice.getUUID()));assertTrue(run.spectators.contains(alice.getUUID()));
        verify(alice).setHealth(20F);assertEquals(100,run.killcamTicks.get(alice.getUUID()));
    }
    @Test void lastPlayersKillcamCannotAdvanceAnEmptyWaveIntoVictory() throws Exception {
        run.lossDelay=2;run.queue.clear();run.mobs.clear();
        invoke("tickRun",new Class<?>[]{MinecraftServer.class,ArenaManager.Run.class},server,run);
        assertEquals(1,run.lossDelay);assertEquals(ArenaManager.State.WAVE,run.state);assertFalse(run.won);assertEquals(5,run.completedWaves);
    }
    @Test void revivalRestoresOwnCameraAndClearsKillcamState() throws Exception {
        var alice=player("Alice");var bob=player("Bob");run.alive.add(alice.getUUID());run.spectators.add(bob.getUUID());
        run.fallen.put(bob.getUUID(),Vec3.ZERO);run.reviving.put(bob.getUUID(),79);run.killcamTicks.put(bob.getUUID(),99);run.killcamTarget.put(bob.getUUID(),UUID.randomUUID());when(alice.isShiftKeyDown()).thenReturn(true);
        try(var fx=mockStatic(ArenaFx.class)){
            invoke("tickRevive",new Class<?>[]{MinecraftServer.class,ServerLevel.class,ArenaManager.Run.class},server,level,run);
        }
        assertTrue(run.alive.contains(bob.getUUID()));assertFalse(run.spectators.contains(bob.getUUID()));
        assertFalse(run.killcamTicks.containsKey(bob.getUUID()));assertFalse(run.killcamTarget.containsKey(bob.getUUID()));verify(bob).setCamera(bob);assertEquals(1,run.revives);
    }
    @Test void resultsUseCompletedWavesDuringBothPauseAndNextFight() throws Exception {
        var alice=player("Alice");run.alive.add(alice.getUUID());run.state=ArenaManager.State.PREP;run.wave=5;
        ArenaPresentation.snapshot(server,run);ArenaPresentation.show(alice);
        run.state=ArenaManager.State.WAVE;run.wave=6;ArenaPresentation.snapshot(server,run);ArenaPresentation.show(alice);
        var packets=ArgumentCaptor.forClass(Packet.class);verify(alice.connection,times(2)).send(packets.capture());
        for(var packet:packets.getAllValues()){var result=(ArenaNet.Result)((ClientboundCustomPayloadPacket)packet).payload();assertEquals(5,result.wave());}
    }
    @Test void leavingCameraAndRevivalStateIsCleanedImmediately() throws Exception {
        var alice=player("Alice");var bob=player("Bob");run.alive.add(bob.getUUID());run.spectators.add(alice.getUUID());
        run.fallen.put(alice.getUUID(),Vec3.ZERO);run.reviving.put(alice.getUUID(),20);run.killcamTicks.put(alice.getUUID(),60);run.killcamTarget.put(alice.getUUID(),UUID.randomUUID());
        invoke("removeFromRun",new Class<?>[]{ServerPlayer.class},alice);
        assertFalse(run.involves(alice.getUUID()));assertFalse(run.fallen.containsKey(alice.getUUID()));assertFalse(run.reviving.containsKey(alice.getUUID()));assertFalse(run.killcamTicks.containsKey(alice.getUUID()));verify(alice).setCamera(alice);
    }
    @Test void desertersReceiveNoWinXpOrCoins() throws Exception {
        var alice=player("Alice");
        try(var extras=mockStatic(ArenaExtras.class)){
            invoke("finish",new Class<?>[]{MinecraftServer.class,boolean.class},server,true);
        }
        verify(alice,never()).giveExperiencePoints(anyInt());assertEquals(0,ArenaStats.coins(alice));assertEquals(0,ArenaStats.wins(alice));
    }
    @Test void playerClonePreservesCoinsSmithCosmeticsAndProgressAsIndependentCopy() throws Exception {
        var old=player("Old");var current=player("Current");
        ArenaStats.addCoins(old,500);ArenaStats.setSmithLevel(old,"lebenskraft",3);ArenaStats.give(old,"chronicle_arena:title_test");
        CompoundTag progress=new CompoundTag(),tier=new CompoundTag();tier.putInt("wins",2);progress.put("chronicle_arena:test",tier);old.getPersistentData().put("chronicle_arena_progress",progress);
        manager.onClone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(current,old,true));
        assertEquals(500,ArenaStats.coins(current));assertEquals(500,ArenaStats.earned(current));assertEquals(3,ArenaStats.smithLevel(current,"lebenskraft"));assertTrue(ArenaStats.owns(current,"chronicle_arena:title_test"));
        assertEquals(2,ArenaManager.wins(current,ChronicleArena.id("test")));
        ArenaStats.addCoins(old,10);assertEquals(500,ArenaStats.coins(current));
    }
    @Test void customStructuresAreNotOverwrittenByDecorationOrUpgradeHooks(){
        var structures=mock(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.class);
        when(level.getStructureManager()).thenReturn(structures);
        when(structures.get(any())).thenReturn(Optional.of(mock(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.class)));
        ArenaDecor.lobby(level);ArenaDecor.shop(level);ArenaDecor.ensureSmith(level);ArenaDecor.ensureGadgets(level);
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void failedRequiredSpawnAbortsInsteadOfGrantingAnEmptyVictory() throws Exception {
        run.queue.add(new ArenaManager.Spawn(ResourceLocation.parse("minecraft:missing_test_entity"),false,true));
        try(var extras=mockStatic(ArenaExtras.class)){
            invoke("tickFight",new Class<?>[]{MinecraftServer.class,ServerLevel.class,ArenaManager.Run.class},server,level,run);
        }
        assertEquals(ArenaManager.State.ENDING,run.state);assertFalse(run.won);assertEquals(5,run.completedWaves);
    }
}
