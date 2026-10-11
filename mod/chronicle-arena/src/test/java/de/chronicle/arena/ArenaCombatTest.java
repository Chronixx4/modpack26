package de.chronicle.arena;

import java.util.UUID;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaCombatTest {
    @Test void beaconHasFiniteHealthAndOneStrikePerMobPerSecond(){
        var o=new ArenaObjectives.Objective(1,BlockPos.ZERO);UUID enemy=UUID.randomUUID();
        ArenaObjectives.strike(o,enemy,1000);assertEquals(92,o.health);
        ArenaObjectives.strike(o,enemy,8);assertEquals(92,o.health);
        o.ticks=20;ArenaObjectives.strike(o,enemy,8);assertEquals(84,o.health);
        for(int i=0;i<11;i++){o.ticks+=20;ArenaObjectives.strike(o,enemy,8);}
        assertEquals(0,o.health);assertTrue(o.failed);o.held=600;assertFalse(o.complete());
    }
    @Test void completeBeaconIsSafeAndInvalidDamageCannotCorruptHealth(){
        var o=new ArenaObjectives.Objective(1,BlockPos.ZERO);UUID id=UUID.randomUUID();
        ArenaObjectives.strike(o,id,Float.NaN);ArenaObjectives.strike(o,id,-2);assertEquals(100,o.health);
        o.held=600;assertTrue(o.complete());ArenaObjectives.strike(o,id,8);assertEquals(100,o.health);assertFalse(ArenaObjectives.needsPressure(o));
    }
    @Test void reinforcementsNeverOverflowTheCombatCap(){
        assertEquals(3,ArenaBalance.reinforcements(1,0,0,16));
        assertEquals(1,ArenaBalance.reinforcements(4,3,12,16));
        assertEquals(0,ArenaBalance.reinforcements(4,3,20,16));
        var hold=new ArenaObjectives.Objective(4,BlockPos.ZERO);assertTrue(ArenaObjectives.needsPressure(hold));hold.checkpoint=3;assertFalse(ArenaObjectives.needsPressure(hold));
    }
    @Test void regenerationWaitsForDamageAndNeverExceedsItsStackCap(){
        assertFalse(ArenaBalance.canRegen(1,120,50,0,false));
        assertTrue(ArenaBalance.canRegen(1,130,50,0,false));
        assertFalse(ArenaBalance.canRegen(1,130,50,30,false));
        assertFalse(ArenaBalance.canRegen(3,200,0,0,true));
        assertEquals(120,ArenaBalance.regenInterval(1));assertEquals(80,ArenaBalance.regenInterval(99));
        assertEquals(0.5F,ArenaBalance.regenAmount(false));assertEquals(0.75F,ArenaBalance.regenAmount(true));
    }
    @Test void healthyFedPlayersDoNotWasteRecoveryAndNoHealStillAllowsFood(){
        assertFalse(ArenaBalance.needsRecovery(20,20,20,false,false));
        assertTrue(ArenaBalance.needsRecovery(20,20,16,false,true));
        assertFalse(ArenaBalance.needsRecovery(10,20,20,false,true));
        assertTrue(ArenaBalance.needsRecovery(10,20,20,false,false));
        assertTrue(ArenaBalance.needsRecovery(20,20,20,true,false));
    }
    @Test void boardTextPreservesRealNewlinesQuotesAndWrapping(){
        String text="LETZTER RUN\nAlice \"Gold\"\nSchaden 12 | Kills 3";
        var nbt=ArenaExtras.boardTag(text);
        assertEquals(text,JsonParser.parseString(nbt.getString("text")).getAsJsonObject().get("text").getAsString());
        assertEquals(320,nbt.getInt("line_width"));assertEquals("vertical",nbt.getString("billboard"));
    }
    @Test void allThreeConsumablesHaveShopEntriesAndIcons() throws Exception {
        for(String name:new String[]{"rune_stasis","rune_echo","rune_ward"}){
            try(var in=getClass().getResourceAsStream("/data/chronicle_arena/arena_shop/"+name+".json")){
                assertNotNull(in);var json=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("runen",json.get("category").getAsString());assertTrue(json.get("price").getAsInt()>0);assertEquals("chronicle_arena:"+name,json.get("item").getAsString());
            }
            assertNotNull(getClass().getResource("/assets/chronicle_arena/models/item/"+name+".json"));
        }
    }
}
