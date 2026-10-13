package de.chronicle.arena;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static de.chronicle.arena.ArenaRouteChoice.Route.*;

class ArenaRouteChoiceTest {
    private final UUID run=UUID.randomUUID(), alice=UUID.randomUUID(), bob=UUID.randomUUID(), cara=UUID.randomUUID();
    private ArenaRouteChoice ballot(Set<UUID> alive, boolean goals){
        var b=new ArenaRouteChoice(run);assertTrue(b.begin(5,20,false,goals,alive));return b;
    }
    @Test void onlyUnfinishedFiveWaveCheckpointsOpenOnce(){
        var b=new ArenaRouteChoice(run);
        for(int wave:new int[]{0,1,4,6,9,20})assertFalse(b.begin(wave,20,false,true,Set.of(alice)));
        assertFalse(b.begin(5,20,true,true,Set.of(alice)));
        assertTrue(b.begin(5,20,false,true,Set.of(alice)));
        assertFalse(b.begin(5,20,false,true,Set.of(alice)));
        b.cast(alice,run,5,0);assertEquals(RISK,b.tick(Set.of(alice)));
        assertFalse(b.begin(5,20,false,true,Set.of(alice)));
        assertTrue(b.begin(10,20,false,true,Set.of(alice)));
        assertTrue(ArenaRouteChoice.isCheckpoint(25,Integer.MAX_VALUE,false));
    }
    @Test void majorityWaitsForAllAndResolvesExactlyOnce(){
        var alive=Set.of(alice,bob,cara);var b=ballot(alive,true);
        assertTrue(b.cast(alice,run,5,0));assertTrue(b.cast(bob,run,5,0));
        assertNull(b.tick(alive));assertTrue(b.cast(cara,run,5,2));
        assertEquals(RISK,b.tick(alive));assertNull(b.tick(alive));assertFalse(b.cast(alice,run,5,1));
    }
    @Test void tieBetweenRiskAndMissionChoosesRecovery(){
        var alive=Set.of(alice,bob);var b=ballot(alive,true);
        b.cast(alice,run,5,0);b.cast(bob,run,5,2);
        assertEquals(RECOVERY,b.tick(alive));
    }
    @Test void noVotesFallsBackAtDeadlineWithoutResettingTimer(){
        var alive=Set.of(alice);var b=ballot(alive,true);
        for(int i=1;i<ArenaRouteChoice.VOTE_TICKS;i++){
            assertFalse(b.begin(5,20,false,true,alive));assertNull(b.tick(alive));
        }
        assertEquals(1,b.seconds());assertEquals(RECOVERY,b.tick(alive));assertEquals(0,b.seconds());
    }
    @Test void timeoutUsesCastVotesAndIgnoresAbstentions(){
        var alive=Set.of(alice,bob,cara);var b=ballot(alive,true);b.cast(alice,run,5,2);
        for(int i=1;i<ArenaRouteChoice.VOTE_TICKS;i++)assertNull(b.tick(alive));
        assertEquals(MISSION,b.tick(alive));
    }
    @Test void leavingOrDyingRemovesVoteAndNoLongerBlocksRound(){
        var b=ballot(Set.of(alice,bob,cara),true);b.cast(alice,run,5,0);b.cast(bob,run,5,2);
        assertEquals(MISSION,b.tick(Set.of(bob)));assertEquals(0,b.count(RISK));assertEquals(1,b.voters());
    }
    @Test void staleOutsiderInvalidAndDisabledVotesCannotChangeBallot(){
        var b=ballot(Set.of(alice),false);
        assertFalse(b.cast(bob,run,5,0));assertFalse(b.cast(alice,UUID.randomUUID(),5,0));
        assertFalse(b.cast(alice,run,10,0));assertFalse(b.cast(alice,run,5,-1));
        assertFalse(b.cast(alice,run,5,4));assertFalse(b.cast(alice,run,5,2));assertEquals(0,b.voted());
        assertTrue(b.cast(alice,run,5,1));assertEquals(RECOVERY,b.tick(Set.of(alice)));
    }
    @Test void changingVoteDoesNotCreateExtraVotesAndEligibilityRefreshes(){
        var b=ballot(Set.of(alice,bob),true);
        b.cast(alice,run,5,0);b.cast(alice,run,5,2);
        assertEquals(1,b.voted());assertEquals(0,b.count(RISK));assertEquals(2,b.choice(alice));
        b.refresh(Set.of(alice,bob,cara));assertTrue(b.cast(cara,run,5,2));
        assertNull(b.tick(Set.of(alice,bob,cara)));b.cast(bob,run,5,0);
        assertEquals(MISSION,b.tick(Set.of(alice,bob,cara)));
    }
    @Test void riskAffectsExactlyTheNextFiveWavesWithoutStacking(){
        var b=ballot(Set.of(alice),true);b.cast(alice,run,5,0);b.tick(Set.of(alice));
        assertEquals(1,b.health(5));
        for(int wave=6;wave<=10;wave++){assertEquals(1.25,b.health(wave));assertEquals(1.15,b.damage(wave));assertEquals(1.5,b.coins(wave));}
        assertEquals(1,b.health(11));assertEquals(1,b.damage(11));assertEquals(1,b.coins(11));
        b.begin(10,20,false,true,Set.of(alice));b.cast(alice,run,10,0);b.tick(Set.of(alice));
        assertEquals(1.25,b.health(11));assertEquals(1.5,b.coins(15));assertEquals(1,b.coins(16));
    }
    @Test void missionRequiresSuccessOnFirstWaveAndCanOnlyRewardOnce(){
        var b=ballot(Set.of(alice),true);b.cast(alice,run,5,2);b.tick(Set.of(alice));
        assertFalse(b.mission(5));assertTrue(b.mission(6));assertFalse(b.mission(7));
        assertFalse(b.claimMission(6,false));assertFalse(b.claimMission(5,true));
        assertTrue(b.claimMission(6,true));assertFalse(b.claimMission(6,true));assertFalse(b.claimMission(7,true));
        b.begin(10,20,false,true,Set.of(alice));b.cast(alice,run,10,2);b.tick(Set.of(alice));
        assertTrue(b.claimMission(11,true));
    }
    @Test void recoveryHealingHonoursNoHealModifierAndOtherRoutesNeverHeal(){
        for(var route:ArenaRouteChoice.Route.values()){
            var b=ballot(Set.of(alice),true);b.cast(alice,run,5,route.ordinal());b.tick(Set.of(alice));
            assertFalse(b.heals(true));assertEquals(route==RECOVERY,b.heals(false));
            if(route!=RISK){assertEquals(1,b.health(6));assertEquals(1,b.damage(6));assertEquals(1,b.coins(6));}
        }
    }
    @Test void cancelledBallotsCannotAcceptVotesOrResolve(){
        var b=ballot(Set.of(alice),true);b.cancel();
        assertFalse(b.cast(alice,run,5,0));assertNull(b.tick(Set.of(alice)));
    }
    @Test void merchantCanWinAndTiesStillRecover(){
        var alive=Set.of(alice,bob);var b=ballot(alive,true);
        assertTrue(b.cast(alice,run,5,3));assertTrue(b.cast(bob,run,5,3));
        assertEquals(MERCHANT,b.tick(alive));
        var c=ballot(alive,true);
        c.cast(alice,run,5,3);c.cast(bob,run,5,1);
        assertEquals(RECOVERY,c.tick(alive));
    }
}
