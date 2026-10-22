package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.Minecraft;

/** Nur auf dem Client geladen. */
@net.neoforged.fml.common.EventBusSubscriber(modid=de.chronicle.arena.ChronicleArena.MOD_ID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){
        ClientState.stats=null;ClientState.battle=null;ClientState.battleReceived=0;TerminalScreen.clearLast();
    }

    public static void battle(de.chronicle.arena.ArenaNet.Battle payload){ClientState.battle=payload;ClientState.battleReceived=System.nanoTime();}
    public static void blessings(de.chronicle.arena.ArenaNet.Blessings payload){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof BlessingScreen screen){
            if(screen.choosing()&&payload.offers().isEmpty()&&!payload.popup())mc.setScreen(null);
            else screen.update(payload);
        }else if(payload.popup())mc.setScreen(new BlessingScreen(payload));
    }
    public static void routes(de.chronicle.arena.ArenaNet.Routes payload){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof RouteScreen screen){
            if(!payload.open())mc.setScreen(null);
            else screen.update(payload);
        }else if(payload.open()&&payload.popup())mc.setScreen(new RouteScreen(payload));
    }
    public static void menu(de.chronicle.arena.ArenaNet.Menu payload){
        var mc=Minecraft.getInstance();
        if(payload.id().isEmpty()){if(mc.screen instanceof ModeMenuScreen)mc.setScreen(null);return;}
        if(mc.screen instanceof ModeMenuScreen screen&&screen.id().equals(payload.id()))screen.update(payload);
        else if(payload.popup())mc.setScreen(new ModeMenuScreen(payload));
    }
    public static void result(de.chronicle.arena.ArenaNet.Result payload){Minecraft.getInstance().setScreen(new ResultScreen(payload));}
    public static void openParty(Net.OpenParty payload) {
        var mc = Minecraft.getInstance();
        if (payload.members().isEmpty()) { if (mc.screen instanceof PartyScreen) mc.setScreen(null); return; }
        if (mc.screen instanceof PartyScreen screen) screen.update(payload);
        else if (payload.popup()) mc.setScreen(new PartyScreen(payload));
    }
    public static void openAdmin(Net.OpenAdmin payload) { Minecraft.getInstance().setScreen(new AdminScreen(payload)); }
    public static void openTerminal(Net.OpenTerminal payload) {
        Minecraft.getInstance().setScreen(new TerminalScreen(payload));
    }

    public static void openShop(Net.OpenShop payload) {
        Minecraft.getInstance().setScreen(new ShopScreen(payload));
    }

    public static void openSmith(Net.OpenSmith payload) {
        Minecraft.getInstance().setScreen(new SmithScreen(payload));
    }

    public static void updateStats(Net.Stats stats) {
        ClientState.stats = stats;
        if (Minecraft.getInstance().screen instanceof ShopScreen shop) shop.statsChanged();
    }
}
