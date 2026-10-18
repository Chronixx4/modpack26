package de.chronicle.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArenaRulesTest {
    @Test void persistedSettingsKeepAllValues() {
        ArenaSettings s=new ArenaSettings();s.health=175;s.damage=75;s.coins=150;s.prepSeconds=12;s.goalInterval=4;s.goals=false;
        ArenaSettings restored=ArenaSettings.load(s.save(new CompoundTag(),null),null);
        assertEquals(175,restored.health);assertEquals(75,restored.damage);assertEquals(150,restored.coins);
        assertEquals(12,restored.prepSeconds);assertEquals(4,restored.goalInterval);assertFalse(restored.goals);
    }
    @Test void oldWorldsReceiveSafeDefaultsAndCorruptSettingsAreBounded() {
        ArenaSettings old=ArenaSettings.load(new CompoundTag(),null);assertEquals(100,old.health);assertEquals(100,old.coins);assertTrue(old.goals);
        CompoundTag corrupt=new CompoundTag();corrupt.putInt("health",-50);corrupt.putInt("coins",Integer.MAX_VALUE);corrupt.putInt("prep",0);corrupt.putInt("interval",-1);
        ArenaSettings restored=ArenaSettings.load(corrupt,null);assertEquals(25,restored.health);assertEquals(300,restored.coins);assertEquals(3,restored.prepSeconds);assertEquals(2,restored.goalInterval);
    }
    @Test void allThreeGoalsRequireTheirOwnCompletionCondition() {
        var crystals=new ArenaObjectives.Objective(0,BlockPos.ZERO);crystals.crystals.add(BlockPos.ZERO);assertFalse(crystals.complete());crystals.crystals.clear();assertTrue(crystals.complete());
        var hold=new ArenaObjectives.Objective(1,BlockPos.ZERO);hold.held=599;assertFalse(hold.complete());hold.held=600;assertTrue(hold.complete());
        var marked=new ArenaObjectives.Objective(2,BlockPos.ZERO);assertFalse(marked.complete());marked.markedSpawned=1;UUID target=UUID.randomUUID();marked.marked.add(target);assertFalse(marked.complete());ArenaObjectives.killed(marked,target);assertTrue(marked.complete());
    }
    @Test void onlyIronJetpacksAreBlockedInTheArena(){
        assertTrue(ArenaJetpacks.jetpack("ironjetpacks"));assertFalse(ArenaJetpacks.jetpack("create_jetpack"));assertFalse(ArenaJetpacks.jetpack("minecraft"));
    }
}
