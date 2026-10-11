package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyScreen extends Screen {
    private Net.OpenParty data;
    private int scroll;
    private Button ready;
    public PartyScreen(Net.OpenParty data) { super(Component.literal("Arena - Gemeinsam vorbereiten"));this.data=data; }
    public void update(Net.OpenParty value) { data=value;if(ready!=null)ready.setMessage(Component.literal(data.ready()?"Bereitschaft zuruecknehmen":"Ich bin bereit")); }
    @Override protected void init() {
        int x=width/2-145,y=height/2+78;
        ready=addRenderableWidget(Button.builder(Component.literal(data.ready()?"Bereitschaft zuruecknehmen":"Ich bin bereit"),b->send("ready")).bounds(x,y,190,20).build());
        if(data.leader())addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->send("cancel")).bounds(x+195,y,95,20).build());
        else addRenderableWidget(Button.builder(Component.literal("Ablehnen"),b->send("decline")).bounds(x+195,y,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("Schliessen"),b->onClose()).bounds(x,y+24,290,20).build());
    }
    private void send(String action) { PacketDistributor.sendToServer(new Net.Control(action)); }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {scroll=Math.max(0,Math.min(Math.max(0,data.members().size()-5),scroll-(int)Math.signum(dy)));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        renderBackground(g,mx,my,tick);
        int x=width/2-155,y=height/2-110;
        Panel.draw(g,x,y,x+310,y+242,24,0xFFD700);
        g.drawCenteredString(font,title,width/2,y+7,0xFFFFFF);
        g.drawWordWrap(font,Component.literal(data.summary()),x+10,y+34,290,0xCCCCFF);
        for(int i=scroll;i<Math.min(data.members().size(),scroll+5);i++)g.drawString(font,data.members().get(i),x+10,y+82+(i-scroll)*15,0xFFFFFF);
        g.drawString(font,data.countdown()<0?"Start erst, wenn alle zugestimmt haben.":"Start in "+data.countdown()+" Sekunden",x+10,y+166,0xFFD700);
        super.render(g,mx,my,tick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
