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
        }
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
        assertEquals(100,s.price(ArenaSiege.Tower.ARROW,0));assertEquals(250,s.price(ArenaSiege.Tower.ARROW,2));
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

    private static LivingEntity mob(){LivingEntity e=mock(LivingEntity.class);UUID id=UUID.randomUUID();when(e.getUUID()).thenReturn(id);return e;}
}
