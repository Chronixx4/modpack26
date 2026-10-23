package de.chronicle.arena;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Burg halten: Karte, Modus-Wahl, Kasse und Kristall ohne laufenden Server. */
class ArenaSiegeTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();try(var flags=mockStatic(net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class)){Bootstrap.bootStrap();}}

    private static ArenaDefs.Encounter enc(boolean boss){
        return new ArenaDefs.Encounter(ChronicleArena.id(boss?"boss":"waves"),0,boss,"Test","",null,List.of(),null,5,null,null,List.of(),"default");
    }
    private static ArenaDefs.Tier tier(int waves){return new ArenaDefs.Tier(ChronicleArena.id("t"),0,"Test","",1,1,1,waves,null,10,2,null,10,1,1,1,1);}

    @Test void pathIsAxisAlignedInsideTheCourtyardAndLong(){
        for(int i=1;i<ArenaSiege.PATH.length;i++){
            int[] a=ArenaSiege.PATH[i-1],b=ArenaSiege.PATH[i];
            assertTrue(a[0]==b[0]||a[1]==b[1],"Pfadstueck "+i+" ist schraeg");
        }
        for(int[] p:ArenaSiege.PATH){assertTrue(Math.abs(p[0])<=ArenaSiege.HALF-3);assertTrue(Math.abs(p[1])<=ArenaSiege.HALF-3);}
        assertTrue(ArenaSiege.pathLength()>=150,"Pfad zu kurz: "+ArenaSiege.pathLength());
        assertTrue(ArenaSiege.onPath(ArenaSiege.PATH[0][0],ArenaSiege.PATH[0][1]));
        assertFalse(ArenaSiege.onPath(ArenaSiege.CORE[0]+5,ArenaSiege.CORE[1]+4));
    }

    @Test void padsSitBesideThePathAndDoNotOverlap(){
        Set<String> used=new HashSet<>();
        for(int[] pad:ArenaSiege.PADS){
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                assertFalse(ArenaSiege.onPath(pad[0]+dx,pad[1]+dz),"Bauplatz auf dem Pfad: "+Arrays.toString(pad));
                assertTrue(used.add((pad[0]+dx)+","+(pad[1]+dz)),"Bauplaetze ueberlappen: "+Arrays.toString(pad));
            }
            assertTrue(Math.abs(pad[0])<ArenaSiege.HALF-1&&Math.abs(pad[1])<ArenaSiege.HALF-1);
            assertTrue(Math.abs(pad[0]-ArenaSiege.CORE[0])>2||Math.abs(pad[1]-ArenaSiege.CORE[1])>2,"Bauplatz auf dem Kristall");
            // Jeder Bauplatz erreicht mit dem Pfeilturm (Stufe 1) den Pfad.
            boolean reach=false;
            for(int x=-ArenaSiege.HALF;x<=ArenaSiege.HALF&&!reach;x++)for(int z=-ArenaSiege.HALF;z<=ArenaSiege.HALF;z++)
                if(ArenaSiege.onPath(x,z)&&Math.hypot(x-pad[0],z-pad[1])<=ArenaSiege.Tower.ARROW.range(1)){reach=true;break;}
            assertTrue(reach,"Bauplatz ohne Pfad in Reichweite: "+Arrays.toString(pad));
            // Auch der Feuerturm (Stufe 1) erreicht die Pfadmitte.
            boolean mid=false;
            for(int[][] path:ArenaSiege.PATHS)for(int i=1;i<path.length&&!mid;i++){
                int[] a=path[i-1],b=path[i];
                for(int x=Math.min(a[0],b[0]);x<=Math.max(a[0],b[0])&&!mid;x++)for(int z=Math.min(a[1],b[1]);z<=Math.max(a[1],b[1]);z++)
                    if(Math.hypot(x-pad[0],z-pad[1])<=ArenaSiege.Tower.FLAME.range(1)){mid=true;break;}
            }
            assertTrue(mid,"Feuerturm erreicht den Pfad nicht: "+Arrays.toString(pad));
            // Golems stehen auf dem Weg vor ihrer Kaserne.
            var f=ArenaSiege.facing(i0(pad));
            assertTrue(ArenaSiege.onPath(pad[0]+f.getStepX()*3,pad[1]+f.getStepZ()*3),"Kaserne zeigt nicht zum Weg: "+Arrays.toString(pad));
        }
        assertTrue(ArenaSiege.PADS.length>=20,"Zu wenige Bauplaetze");
    }

    @Test void terminalFlagSelectsSiegeOnlyForWaveEncounters(){
        var siege=new ArenaManager.Run(enc(false),tier(10),1,ArenaManager.MODE_SIEGE,0);
        assertEquals(ArenaMode.SIEGE,siege.mode);assertInstanceOf(ArenaSiege.class,siege.logic);
        assertEquals(10,siege.totalWaves);assertFalse(siege.endless);
        var endless=new ArenaManager.Run(enc(false),tier(0),1,ArenaManager.MODE_SIEGE,0);
        assertTrue(endless.endless);
        var boss=new ArenaManager.Run(enc(true),tier(10),1,ArenaManager.MODE_SIEGE,0);
        assertEquals(ArenaMode.BOSS,boss.mode);assertNull(boss.logic);
        var waves=new ArenaManager.Run(enc(false),tier(10),1,0,0);
        assertEquals(ArenaMode.WAVES,waves.mode);assertNull(waves.logic);
        // Das Flag zaehlt nicht als Coin-Modifikator.
        assertEquals(1.0,ArenaManager.coinMult(ArenaManager.MODE_SIEGE),1e-9);
        assertEquals("keine",ArenaManager.modNames(ArenaManager.MODE_SIEGE));
    }

    @Test void killsFillTheTreasuryButLeakedEnemiesDoNot(){
        var r=new ArenaManager.Run(enc(false),tier(10),1,ArenaManager.MODE_SIEGE,0);
        var s=(ArenaSiege)r.logic;s.points=0;
        LivingEntity normal=mob(),elite=mob(),leaked=mob();
        var eliteSpawn=new ArenaManager.Spawn(ChronicleArena.id("x"),true,false);
        s.spawned(normal,new ArenaManager.Spawn(ChronicleArena.id("x"),false,false));s.spawned(elite,eliteSpawn);
        s.mobDied(normal,null);assertEquals(10,s.points);
        s.mobDied(elite,null);assertEquals(40,s.points);
        assertEquals(1,s.waypoint.getOrDefault(leaked.getUUID(),1));
        assertEquals(5,ArenaSiege.leakDamage(1));assertEquals(15,ArenaSiege.leakDamage(3));assertEquals(30,ArenaSiege.leakDamage(10));
    }

    @Test void crystalLossEndsTheRunAndStateSurvivesSave(){
        var r=new ArenaManager.Run(enc(false),tier(10),2,ArenaManager.MODE_SIEGE,0);
        var s=(ArenaSiege)r.logic;s.coreMax=125;s.core=40;s.points=275;s.type[3]=ArenaSiege.Tower.FROST.ordinal();s.level[3]=2;s.spent[3]=350;
        assertFalse(s.lost());
        var tag=s.save();
        assertEquals(275,tag.getInt("points"));assertEquals(40,tag.getInt("core"));assertEquals(125,tag.getInt("core_max"));
        assertEquals(ArenaSiege.Tower.FROST.ordinal(),tag.getIntArray("type")[3]);assertEquals(2,tag.getIntArray("level")[3]);assertEquals(350,tag.getIntArray("spent")[3]);
        s.core=0;assertTrue(s.lost());
        assertEquals(75,s.price(ArenaSiege.Tower.ARROW,0));assertEquals(200,s.price(ArenaSiege.Tower.ARROW,2));
        assertTrue(ArenaSiege.progress(3,1.0)>ArenaSiege.progress(2,0.0),"Wer weiter ist, wird zuerst beschossen");
    }

    @Test void modeMenuRoundTrips(){
        var menu=new ArenaNet.Menu("siege_pad_4","Bauplatz 5","Gemeinsame Kasse",List.of(
                new ArenaNet.MenuOption(0,"Pfeilturm","Schiesst","minecraft:arrow",100,0,-1),
                new ArenaNet.MenuOption(11,"Abreissen","Haelfte zurueck","minecraft:barrier",-1,1,-1)),275,0,true);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try{ArenaNet.Menu.CODEC.encode(b,menu);assertEquals(menu,ArenaNet.Menu.CODEC.decode(b));assertFalse(b.isReadable());}finally{b.release();}
        var c=new FriendlyByteBuf(Unpooled.buffer());
        try{var pick=new ArenaNet.MenuPick("siege_core",1);ArenaNet.MenuPick.CODEC.encode(c,pick);assertEquals(pick,ArenaNet.MenuPick.CODEC.decode(c));}finally{c.release();}
        var close=ArenaNet.Menu.close();assertTrue(close.id().isEmpty());
    }

    @Test void secondGatePathTrapsAndSaboteurs(){
        var p2=ArenaSiege.PATH2;
        for(int i=1;i<p2.length;i++)assertTrue(p2[i-1][0]==p2[i][0]||p2[i-1][1]==p2[i][1],"Osttor-Pfad schraeg");
        assertEquals(ArenaSiege.HALF-2,p2[0][0],"Osttor-Pfad beginnt nicht an der Ostmauer");
        assertEquals(ArenaSiege.GATE2_Z,p2[0][1]);
        // Die Wege kreuzen sich nicht.
        for(int x=-ArenaSiege.HALF;x<=ArenaSiege.HALF;x++)for(int z=-ArenaSiege.HALF;z<=ArenaSiege.HALF;z++)
            assertFalse(ArenaSiege.onPath(ArenaSiege.PATH,x,z)&&ArenaSiege.onPath(p2,x,z),"Wege ueberschneiden sich bei "+x+","+z);
        var r=new ArenaManager.Run(enc(false),tier(20),1,ArenaManager.MODE_SIEGE,0);
        var s=(ArenaSiege)r.logic;
        assertTrue(s.trapFits(0,-16));
        assertFalse(s.trapFits(0,-13),"Falle ragt vom Weg");
        assertFalse(s.trapFits(-24,-28),"Falle am Tor");
        s.traps.add(new ArenaSiege.Trap(0,-16,ArenaSiege.TrapType.SPIKES,30));
        assertFalse(s.trapFits(2,-16),"Fallen ueberlappen");
        assertTrue(s.trapFits(3,-16));
        assertSame(s.traps.get(0),s.trapAt(1,-15));
        var tag=s.save();
        assertArrayEquals(new int[]{0,-16,0,30},tag.getIntArray("traps"));
        assertEquals(0,ArenaSiege.saboteurQuota(3));assertEquals(1,ArenaSiege.saboteurQuota(4));assertEquals(3,ArenaSiege.saboteurQuota(16));
        for(var t:ArenaSiege.Tower.values())assertEquals(0.0,t.vs(ArenaSiege.SABOTEUR),1e-9,"Tuerme duerfen Saboteure nicht treffen");
        assertTrue(ArenaSiege.Tower.ARROW.vs(ArenaSiege.SHADE)<0.5);
        s.jam.put(UUID.randomUUID(),3);
        assertTrue(s.jammed(null,3));assertFalse(s.jammed(null,4));
    }

    private static int i0(int[] pad){for(int i=0;i<ArenaSiege.PADS.length;i++)if(ArenaSiege.PADS[i]==pad)return i;return -1;}

    @Test void mobsAreHeldOnThePathAndRolesMatchTowers(){
        for(int wp=1;wp<ArenaSiege.PATH.length;wp++){
            var far=new net.minecraft.world.phys.Vec3(ArenaSiege.CENTER.getX()+40,ArenaSiege.CENTER.getY()+5,ArenaSiege.CENTER.getZ()-40);
            assertTrue(ArenaSiege.onPath(ArenaSiege.clampToSegment(far,wp)),"Zuruecksetzen landet neben dem Weg (Stueck "+wp+")");
        }
        assertEquals(ArenaSiege.NORMAL,ArenaSiege.rollRole(1,false,0.0));
        assertEquals(ArenaSiege.RUNNER,ArenaSiege.rollRole(3,false,0.1));
        assertEquals(ArenaSiege.ARMORED,ArenaSiege.rollRole(5,false,0.3));
        assertEquals(ArenaSiege.LEADER,ArenaSiege.rollRole(1,true,0.9));
        assertTrue(ArenaSiege.Tower.CANNON.vs(ArenaSiege.ARMORED)>1&&ArenaSiege.Tower.ARROW.vs(ArenaSiege.ARMORED)<1);
        assertTrue(ArenaSiege.Tower.FLAME.vs(ArenaSiege.RUNNER)>1);
        var r=new ArenaManager.Run(enc(false),tier(10),1,ArenaManager.MODE_SIEGE,0);
        var s=(ArenaSiege)r.logic;
        assertEquals(500,s.points);
        s.type[0]=ArenaSiege.Tower.MINE.ordinal();s.level[0]=2;s.type[1]=ArenaSiege.Tower.MINE.ordinal();s.level[1]=1;
        assertEquals(80,s.mineIncome());
    }

    private static LivingEntity mob(){LivingEntity e=mock(LivingEntity.class);UUID id=UUID.randomUUID();when(e.getUUID()).thenReturn(id);when(e.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(1));return e;}

    @Test void crewRolesVotesAndScore(){
        assertEquals(4,SiegeCrew.ROLES.length);
        assertEquals(SiegeCrew.ROLES.length,SiegeCrew.ROLE_DESC.length);
        assertEquals(SiegeCrew.ROLES.length,SiegeCrew.ABILITY.length);
        assertEquals(85,SiegeCrew.cost(100,SiegeCrew.BUILDER));
        assertEquals(100,SiegeCrew.cost(100,SiegeCrew.GUARD));
        assertEquals(100,SiegeCrew.cost(100,-1));
        assertEquals(2*SiegeCrew.MAGE_COOLDOWN,SiegeCrew.cooldown(SiegeCrew.BUILDER));
        assertEquals(SiegeCrew.CALM,SiegeCrew.winner(new int[]{0,0,0,0}),"Ohne Stimmen bleibt es ruhig");
        assertEquals(SiegeCrew.HORDE,SiegeCrew.winner(new int[]{1,2,1,0}));
        assertEquals(SiegeCrew.HORDE,SiegeCrew.winner(new int[]{0,1,1,1}),"Gleichstand: die erste in der Liste");
        assertEquals(SiegeCrew.CALM,SiegeCrew.winner(new int[]{2,2,0,0}));
        assertEquals(100,SiegeCrew.withBonus(100,SiegeCrew.CALM));
        assertEquals(140,SiegeCrew.withBonus(100,SiegeCrew.HORDE));
        for(int v=1;v<SiegeCrew.VOTES.length;v++)assertTrue(SiegeCrew.VOTE_BONUS[v]>0,"Risiko ohne Bonus: "+SiegeCrew.VOTES[v]);
        int[] st=new int[SiegeCrew.STATS];st[SiegeCrew.KILLS]=10;st[SiegeCrew.BUILT]=2;st[SiegeCrew.SHARDS]=1;st[SiegeCrew.SABOTEURS]=1;st[SiegeCrew.ABILITIES]=3;
        assertEquals(10+6+5+4+6,SiegeCrew.score(st));
        assertTrue(SiegeCrew.line("Alex",st).startsWith("Alex: 10 Kills"));
    }

    @Test void abilityItemsCarryTheirRole(){
        for(int role=0;role<SiegeCrew.ROLES.length;role++){
            var st=SiegeCrew.abilityItem(role);
            assertEquals(role,SiegeCrew.abilityRole(st));
            assertTrue(st.getHoverName().getString().startsWith(SiegeCrew.ABILITY[role]));
        }
        assertEquals(-1,SiegeCrew.abilityRole(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRICK)));
        assertEquals(-1,SiegeCrew.abilityRole(SiegeCrew.shard()));
    }
}
