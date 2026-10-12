package de.chronicle.arena.client;

import de.chronicle.arena.ArenaNet;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ResultScreen extends Screen {
    private final ArenaNet.Result data;
    private final List<FormattedCharSequence> lines=new ArrayList<>();
    private int x,y,w,h,scroll;
    public ResultScreen(ArenaNet.Result data){super(Component.literal(data.won()?"Arena - Sieg!":"Arena - Ergebnis"));this.data=data;}
    private void line(String text){lines.addAll(font.split(Component.literal(text),w-24));}
    @Override protected void init(){
        w=Math.min(400,width-24);h=Math.min(340,height-24);x=(width-w)/2;y=(height-h)/2;lines.clear();
        line(data.encounter()+" / "+data.tier());line("Welle "+data.wave()+" | Zeit "+data.time());line("");
        line("Deine Belohnungen: "+data.coins()+" Coins | "+data.xp()+" XP");
        if(data.loot().isEmpty())line("Keine Gegenstandsbelohnung.");else for(String item:data.loot())line(item);
        line("");line("Teamleistung");for(String member:data.team()){line(member);line("");}
        int half=(w-24)/2;
        addRenderableWidget(Button.builder(Component.literal("Erneut vorbereiten"),b->{PacketDistributor.sendToServer(new ArenaNet.Retry(data.run()));onClose();}).bounds(x+10,y+h-27,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zur Lobby"),b->onClose()).bounds(x+14+half,y+h-27,half,20).build());
        scroll=Math.min(scroll,maxScroll());
    }
    private int maxScroll(){return Math.max(0,lines.size()-Math.max(1,(h-77)/11));}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(dy)*3));return true;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){super.renderBackground(g,mx,my,tick);Panel.draw(g,x,y,x+w,y+h,24,data.won()?0x55FF55:0xFF7777);}
    @Override public void render(GuiGraphics g,int mx,int my,float tick){super.render(g,mx,my,tick);g.drawCenteredString(font,title,width/2,y+8,data.won()?0xFFD700:0xFFFFFF);g.drawString(font,"Erneut anzeigen: /arena ergebnis",x+10,y+31,0xAAAAAA);g.enableScissor(x+5,y+46,x+w-5,y+h-35);int cy=y+48;for(int i=scroll;i<lines.size()&&cy+9<=y+h-35;i++,cy+=11)g.drawString(font,lines.get(i),x+10,cy,0xFFFFFF);g.disableScissor();if(maxScroll()>0)g.drawString(font,"Scrollen",x+w-55,y+31,0xAAAAAA);}
    @Override public boolean isPauseScreen(){return false;}
}
