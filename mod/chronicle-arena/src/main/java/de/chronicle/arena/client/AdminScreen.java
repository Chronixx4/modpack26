package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Visibility is convenience only; every request is independently OP-checked by the server. */
public final class AdminScreen extends Screen {
    private final Net.OpenAdmin data;
    private final int[] values;
    private final String[] labels={"Gegner-Leben (%)","Gegner-Schaden (%)","Coins (%)","Vorbereitung (Sek.)","Zielwelle alle N Wellen"};
    private final int[] min={25,25,0,3,2},max={300,300,300,60,10},step={25,25,25,1,1};
    private boolean goals,confirmStop;
    public AdminScreen(Net.OpenAdmin data){super(Component.literal("Arena - Admin Control Panel"));this.data=data;values=new int[]{data.health(),data.damage(),data.coins(),data.prep(),data.interval()};goals=data.goals();}
    @Override protected void init(){
        int x=width/2-165,y=height/2-118;
        for(int i=0;i<5;i++){
            final int j=i;
            addRenderableWidget(Button.builder(Component.literal("-"),b->values[j]=Math.max(min[j],values[j]-step[j])).bounds(x+236,y+43+i*23,25,20).build());
            addRenderableWidget(Button.builder(Component.literal("+"),b->values[j]=Math.min(max[j],values[j]+step[j])).bounds(x+275,y+43+i*23,25,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Zielwellen: "+(goals?"AN":"AUS")),b->{goals=!goals;b.setMessage(Component.literal("Zielwellen: "+(goals?"AN":"AUS")));}).bounds(x+10,y+161,290,20).build());
        addRenderableWidget(Button.builder(Component.literal("Speichern"),b->send(false)).bounds(x+10,y+201,94,20).build());
        addRenderableWidget(Button.builder(Component.literal("Kampf abbrechen"),b->{if(confirmStop)send(true);else{confirmStop=true;b.setMessage(Component.literal("Bestaetigen?"));}}).bounds(x+108,y+201,115,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurueck"),b->onClose()).bounds(x+227,y+201,73,20).build());
    }
    private void send(boolean stop){PacketDistributor.sendToServer(new Net.AdminChange(values[0],values[1],values[2],values[3],values[4],goals,stop));}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){
        super.renderBackground(g,mx,my,tick);
        int x=width/2-165,y=height/2-118;
        Panel.draw(g,x,y,x+310,y+232,24,0xFF7777);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        super.render(g,mx,my,tick);
        int x=width/2-165,y=height/2-118;
        g.drawCenteredString(font,title,x+155,y+8,0xFFFFFF);
        g.drawString(font,font.plainSubstrByWidth(data.status(),290),x+10,y+29,0xBBBBBB);
        for(int i=0;i<5;i++)g.drawString(font,labels[i]+": "+values[i],x+10,y+49+i*23,0xFFFFFF);
        g.drawString(font,"Aenderungen gelten fuer neue Kaempfe.",x+10,y+185,0xFFD700);
        if(mx>x&&mx<x+310&&my>y+24&&my<y+40)g.renderTooltip(font,Component.literal(data.status()),mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
}
