package de.chronicle.arena;

/** Combat recovery has a damage delay and never upgrades a regeneration potion. */
final class ArenaBalance {
    static double mobSpeed(boolean haste,boolean bomber,boolean bloodMoon){return (haste?1.3:1)*(bomber?1.2:1)*(bloodMoon?1.25:1);}
    static int regenInterval(int stacks){return 120-20*Math.clamp(stacks-1,0,2);}
    static float regenAmount(boolean armor){return armor?0.75F:0.5F;}
    static boolean canRegen(int stacks,int now,int lastHit,int lastPulse,boolean noHeal){
        return stacks>0&&!noHeal&&now-lastHit>=80&&now-lastPulse>=regenInterval(stacks);
    }
    static boolean needsRecovery(float health,float max,int food,boolean fire,boolean noHeal){return (!noHeal&&health<max)||food<20||fire;}
    static int reinforcements(int alive,int queued,int living,int max){
        return Math.max(0,Math.min(2+Math.min(4,Math.max(1,alive)),max-queued-living));
    }
}
