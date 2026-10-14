package de.chronicle.arena;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

final class ArenaRoutes {
    private ArenaRoutes() {}
    /** Called after blessings; pauses the wave countdown while the ballot is open. */
    static boolean tick(MinecraftServer server, ArenaManager.Run r) {
        if (r.alive.isEmpty()) return true;
        boolean opened = r.routes.begin(r.wave, r.totalWaves, r.encounter.boss(), r.goalsEnabled, r.alive);
        if (!r.routes.voting()) return false;
        var chosen = r.routes.tick(r.alive);
        if (chosen == null) {
            if (opened || server.getTickCount() % 20 == 0) {
                for (UUID id : r.alive) {
                    var p = server.getPlayerList().getPlayer(id);
                    if (p != null) {
                        if (opened) p.sendSystemMessage(Component.literal("Gruppenweg waehlen: Risiko, Erholung oder Spezialmission. 30 Sekunden; Menue: /arena weg"));
                        send(p, r, opened);
                    }
                }
            }
        } else {
            r.timer = 60;
            for (UUID id : r.alive) {
                var p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                close(p, r);
                if (chosen == ArenaRouteChoice.Route.RECOVERY) {
                    if (r.routes.heals((r.mask & ArenaManager.MOD_NOHEAL) != 0)) p.heal(p.getMaxHealth());
                    p.getFoodData().setFoodLevel(20);
                    p.getFoodData().setSaturation(20);
                }
                if (chosen == ArenaRouteChoice.Route.MERCHANT) merchant(p);
                p.sendSystemMessage(Component.literal("Gruppenweg: " + chosen.title + " | Wellen "
                        + (r.wave + 1) + "-" + Math.min(r.wave + 5, r.totalWaves)));
            }
        }
        return true;
    }
    private static void merchant(ServerPlayer p) {
        ItemStack heal = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.STRONG_HEALING);
        heal.setCount(2);
        ItemStack power = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.STRONG_STRENGTH);
        ItemHandlerHelper.giveItemToPlayer(p, heal);
        ItemHandlerHelper.giveItemToPlayer(p, power);
        ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2));
        p.sendSystemMessage(Component.literal("Der Haendler liefert: 2 Heiltraenke, 1 Staerketrank, 2 Goldaepfel."));
    }
    static void send(ServerPlayer p, ArenaManager.Run r, boolean popup) {
        var c = r.routes;
        PacketDistributor.sendToPlayer(p, new ArenaNet.Routes(r.id, r.wave, c.voting(), c.seconds(),
                c.voted(), c.voters(), c.count(ArenaRouteChoice.Route.RISK), c.count(ArenaRouteChoice.Route.RECOVERY),
                c.count(ArenaRouteChoice.Route.MISSION), c.count(ArenaRouteChoice.Route.MERCHANT), c.choice(p.getUUID()), r.goalsEnabled,
                (r.mask & ArenaManager.MOD_NOHEAL) != 0, popup));
    }
    static void close(ServerPlayer p, ArenaManager.Run r) {
        PacketDistributor.sendToPlayer(p, new ArenaNet.Routes(r.id, r.wave, false, 0, 0, 0, 0, 0, 0, 0, -1, false, false, false));
    }
    static void reward(MinecraftServer server, ArenaManager.Run r) {
        if (!r.routes.claimMission(r.wave, r.objective != null && r.objective.complete())) return;
        for (UUID id : r.alive) {
            var p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            var item = switch (p.getRandom().nextInt(3)) {
                case 0 -> ModRegistry.STASIS.get();
                case 1 -> ModRegistry.ECHO.get();
                default -> ModRegistry.WARD.get();
            };
            var stack = new ItemStack(item);
            ArenaPresentation.loot(r, p, stack);
            ItemHandlerHelper.giveItemToPlayer(p, stack);
            p.sendSystemMessage(Component.literal("Spezialmission geschafft! Belohnung: ").append(item.getDescription()));
        }
    }
}
