package de.chronicle.arena;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Snapshots only; opening an old result never grants rewards again. */
public final class ArenaPresentation {
    private record Last(ArenaNet.Result result,Net.Action action,int expires) {}
    private static final Map<UUID,Last> last=new HashMap<>();
    private ArenaPresentation() {}
    public static void clear(){last.clear();}
    public static void forget(UUID id){last.remove(id);}
    public static void loot(ArenaManager.Run r,ServerPlayer p,ItemStack stack){
        var items=r.loot.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>());
        String raw=stack.getHoverName().getString();String label=raw.substring(0,Math.min(raw.length(),200));
        if(items.size()<23||items.containsKey(label))items.merge(label,stack.getCount(),Integer::sum);
        else items.merge("Weitere Gegenstaende",stack.getCount(),Integer::sum);
    }
    public static void snapshot(MinecraftServer server,ArenaManager.Run r){
        List<UUID> ids=new ArrayList<>(r.participants);
        ids.sort((a,b)->Long.compare(r.stat(b)[0],r.stat(a)[0]));
        List<String> team=new ArrayList<>();
        for(UUID id:ids){var s=r.stat(id);team.add(r.names.getOrDefault(id,"Spieler")+" | Schaden "+s[0]+" | Kills "+s[1]+" | Wiederbelebt "+s[2]);}
        for(UUID id:ids){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null)continue;
            List<String> loot=new ArrayList<>();r.loot.getOrDefault(id,Map.of()).forEach((name,n)->loot.add(n+" x "+name));
            ArenaNet.Result result=new ArenaNet.Result(r.id,r.won,r.encounter.name(),r.tier.name(),r.endless?Math.max(0,r.wave-1):r.wave,ArenaManager.formatTime(r.fightTicks),Math.max(0,ArenaStats.earned(p)-r.earnedStart.getOrDefault(id,ArenaStats.earned(p))),r.rewardXp.getOrDefault(id,0),List.copyOf(loot),List.copyOf(team.subList(0,Math.min(1024,team.size()))));
            last.put(id,new Last(result,new Net.Action(false,r.encounter.id().toString(),r.tier.id().toString(),r.mask|((r.variant+1)<<4)),server.getTickCount()+12000));
        }
    }
    public static void show(ServerPlayer p){Last value=last.get(p.getUUID());if(value!=null&&p.server.getTickCount()<value.expires&&ArenaManager.inLobby(p))PacketDistributor.sendToPlayer(p,value.result);else p.sendSystemMessage(Component.literal("Kein aktuelles Arena-Ergebnis vorhanden. Ergebnisse sind 10 Minuten in der Lobby abrufbar."));}
    public static void retry(ServerPlayer p,UUID run){Last value=last.get(p.getUUID());if(value==null||!value.result.run().equals(run)||p.server.getTickCount()>=value.expires||!ArenaManager.inLobby(p))return;ArenaManager.handleAction(p,value.action);}
    public static void sync(MinecraftServer server,ArenaManager.Run r){
        var o=r.objective;
        String phase=switch(r.state){case PREP->r.offer.isEmpty()?"Vorbereitung":"Segen waehlen";case WAVE->"Wellenkampf";case BOSS->"Bosskampf";case ENDING->r.won?"Sieg":"Niederlage";};
        ArenaNet.Battle payload=new ArenaNet.Battle(true,phase,r.wave,r.encounter.boss()?1:r.endless?0:r.totalWaves,r.mobs.size()+r.queue.size(),r.revives,o==null?"":ArenaObjectives.label(o),o==null?0:ArenaObjectives.progress(o),o==null?0:ArenaObjectives.target(o),o==null?0:Math.max(0,(ArenaObjectives.limit(o)-o.ticks+19)/20));
        Set<UUID> all=new HashSet<>(r.alive);all.addAll(r.spectators);
        for(UUID id:all){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null&&p.level().dimension()==ArenaManager.DIM)PacketDistributor.sendToPlayer(p,payload);}
    }
}
