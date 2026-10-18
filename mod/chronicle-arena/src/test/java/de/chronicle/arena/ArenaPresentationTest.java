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
        for(int previous:ArenaObjectives.KINDS){Set<Integer> chosen=new HashSet<>();for(int roll=0;roll<ArenaObjectives.choices(previous);roll++)chosen.add(ArenaObjectives.chooseKind(roll,previous));assertEquals(ArenaObjectives.KINDS.length-1,chosen.size());assertFalse(chosen.contains(previous));assertTrue(chosen.stream().allMatch(ArenaObjectives::active));}
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
    @Test void escortIsNeverRolledEvenAfterAnOldEscortOrAtRunStart(){
        assertFalse(ArenaObjectives.active(3));assertTrue(ArenaObjectives.active(5));assertEquals(5,ArenaObjectives.choices(-1));assertEquals(5,ArenaObjectives.choices(3));
        for(int previous:new int[]{-1,3})for(int roll=0;roll<5;roll++)assertNotEquals(3,ArenaObjectives.chooseKind(roll,previous));
        assertFalse(new ArenaObjectives.Objective(3,BlockPos.ZERO).complete());
    }
    @Test void lightCircleMovesStraightAndStopsExactlyOnItsTarget(){
        var from=new net.minecraft.world.phys.Vec3(0,64,0);var to=new net.minecraft.world.phys.Vec3(10,64,0);
        var step=ArenaObjectives.moveCircle(from,to,0.05);assertEquals(0.05,step.x,1e-9);assertEquals(64,step.y,1e-9);assertEquals(0,step.z,1e-9);
        assertEquals(to,ArenaObjectives.moveCircle(new net.minecraft.world.phys.Vec3(9.98,64,0),to,0.05));
    }
    @Test void lightCircleNeedsHalfTheGroupInsideAndThirtySeconds(){
        assertEquals(1,ArenaObjectives.circleNeeded(1));assertEquals(1,ArenaObjectives.circleNeeded(2));assertEquals(2,ArenaObjectives.circleNeeded(3));assertEquals(2,ArenaObjectives.circleNeeded(4));assertEquals(1,ArenaObjectives.circleNeeded(0));
        var goal=new ArenaObjectives.Objective(5,BlockPos.ZERO);
        for(int i=0;i<100;i++)ArenaObjectives.circleTick(goal,1,3);assertEquals(0,goal.held);
        for(int i=0;i<599;i++)ArenaObjectives.circleTick(goal,2,3);assertFalse(goal.complete());
        ArenaObjectives.circleTick(goal,2,3);assertTrue(goal.complete());assertEquals(30,ArenaObjectives.progress(goal));assertFalse(ArenaObjectives.needsPressure(goal));
        ArenaObjectives.circleTick(goal,2,3);assertEquals(600,goal.held);
    }
    @Test void enemiesNearAControlPointBlockAdvancement(){
        assertFalse(ArenaObjectives.canAdvance(false,false));assertFalse(ArenaObjectives.canAdvance(true,true));assertTrue(ArenaObjectives.canAdvance(true,false));
    }
}
