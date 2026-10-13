package de.chronicle.arena;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaPresentationTest {
    private <T> void roundTrip(StreamCodec<FriendlyByteBuf,T> codec,T value){
        var b=new FriendlyByteBuf(Unpooled.buffer());try{codec.encode(b,value);assertEquals(value,codec.decode(b));assertFalse(b.isReadable());}finally{b.release();}
    }
    @Test void combatStatusAndClearPreserveProgressAndDeadline(){
        roundTrip(ArenaNet.Battle.CODEC,new ArenaNet.Battle(true,"Wellenkampf",9,12,7,2,"Kristalle",2,3,42,75,100,"Echo: 3"));
        roundTrip(ArenaNet.Battle.CODEC,ArenaNet.Battle.EMPTY);
    }
    @Test void blessingCardsAndSelectionPreserveRunAndWaveIdentity(){
        UUID run=UUID.randomUUID();var info=new ArenaNet.BlessInfo("Glut","Entzuendet Gegner","minecraft:blaze_powder",2,"Passt zu Asche");
        roundTrip(ArenaNet.Blessings.CODEC,new ArenaNet.Blessings(run,5,List.of(info),List.of(info),93,true));
        roundTrip(ArenaNet.Pick.CODEC,new ArenaNet.Pick(run,5,2));
    }
    @Test void routeBallotAndVotePreserveIdentityCountsAndRestrictions(){
        UUID run=UUID.randomUUID();
        roundTrip(ArenaNet.Routes.CODEC,new ArenaNet.Routes(run,5,true,27,3,4,2,1,0,1,0,false,true,true));
        roundTrip(ArenaNet.Routes.CODEC,new ArenaNet.Routes(run,5,false,0,0,0,0,0,0,0,-1,false,false,false));
        roundTrip(ArenaNet.RouteVote.CODEC,new ArenaNet.RouteVote(run,5,2));
    }
    @Test void resultsAndRetryPreserveRewardSnapshot(){
        UUID run=UUID.randomUUID();
        roundTrip(ArenaNet.Result.CODEC,new ArenaNet.Result(run,true,"Zombies","Gold",12,"2:34",150,70,List.of("2 x Diamant"),List.of("Alice | Schaden 100 | Kills 5 | Wiederbelebt 1")));
        roundTrip(ArenaNet.Retry.CODEC,new ArenaNet.Retry(run));
    }
    @Test void excessiveBlessingOffersAreRejectedBeforeAllocation(){
        var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeUUID(UUID.randomUUID());b.writeVarInt(5);b.writeVarInt(4);assertThrows(IllegalArgumentException.class,()->ArenaNet.Blessings.CODEC.decode(b));}finally{b.release();}
    }
    @Test void randomSelectionIncludesAllOtherGoalsWithoutImmediateRepeats(){
        for(int previous=0;previous<5;previous++){Set<Integer> chosen=new HashSet<>();for(int roll=0;roll<4;roll++)chosen.add(ArenaObjectives.chooseKind(roll,previous));assertEquals(4,chosen.size());assertFalse(chosen.contains(previous));assertTrue(chosen.stream().allMatch(n->n>=0&&n<5));}
    }
    @Test void controlPointsRequireThreeFullCapturesAndFreezeWhenContested(){
        var goal=new ArenaObjectives.Objective(4,BlockPos.ZERO);
        for(int i=0;i<199;i++)ArenaObjectives.captureTick(goal,true);
        assertEquals(0,goal.checkpoint);assertEquals(199,goal.held);
        ArenaObjectives.captureTick(goal,false);assertEquals(199,goal.held);
        ArenaObjectives.captureTick(goal,true);assertEquals(1,goal.checkpoint);assertEquals(0,goal.held);assertFalse(goal.complete());
        for(int i=0;i<400;i++)ArenaObjectives.captureTick(goal,true);
        assertTrue(goal.complete());assertEquals(30,ArenaObjectives.progress(goal));
    }
    @Test void queuedMarkedTargetsCannotFinishBeforeTheyHaveSpawned(){
        var goal=new ArenaObjectives.Objective(2,BlockPos.ZERO);goal.expectedTargets=3;goal.markedSpawned=1;
        assertFalse(goal.complete());assertEquals(3,ArenaObjectives.target(goal));
        goal.markedSpawned=3;assertTrue(goal.complete());
    }
    @Test void allGoalMarkersAvoidRedCarpetsInEveryOrientation(){
        for(int turn=0;turn<4;turn++){
            var points=ArenaObjectives.routePoints(BlockPos.ZERO,turn);assertEquals(3,new HashSet<>(points).size());
            for(var point:points){
                boolean carpet=(Math.abs(point.getX())<=1&&Math.abs(point.getZ())>=8&&Math.abs(point.getZ())<=20)
                        ||(Math.abs(point.getZ())<=1&&Math.abs(point.getX())>=8&&Math.abs(point.getX())<=20);
                assertFalse(carpet);assertTrue(point.distSqr(BlockPos.ZERO)<400);
            }
        }
    }
    @Test void guideCanStepOnCarpetsAndPressurePlatesWithoutWalkingIntoWallsOrVoid(){
        assertTrue(ArenaObjectives.guideFloor(true,true,0));assertTrue(ArenaObjectives.guideFloor(true,true,1.0/16));
        assertTrue(ArenaObjectives.guideFloor(true,true,0.125));assertFalse(ArenaObjectives.guideFloor(true,true,1));
        assertFalse(ArenaObjectives.guideFloor(false,true,0));assertFalse(ArenaObjectives.guideFloor(true,false,0));assertFalse(ArenaObjectives.guideFloor(true,true,Double.NaN));
    }
    @Test void escortNeedsAllCheckpointsAndEnemiesBlockAdvancement(){
        var goal=new ArenaObjectives.Objective(3,BlockPos.ZERO);goal.checkpoint=2;assertFalse(goal.complete());goal.checkpoint=3;assertTrue(goal.complete());
        assertFalse(ArenaObjectives.canAdvance(false,false));assertFalse(ArenaObjectives.canAdvance(true,true));assertTrue(ArenaObjectives.canAdvance(true,false));
    }
}
