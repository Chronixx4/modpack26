package de.chronicle.arena.client;

import de.chronicle.arena.ArenaNet;
import de.chronicle.arena.ArenaRouteChoice;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Live group vote; cards scroll at high GUI scales. Escape does not discard a vote. */
public final class RouteScreen extends Screen {
    private ArenaNet.Routes data;
    private int x,y,w,h,scroll,contentHeight;
    private final List<Button> choices=new ArrayList<>();
    private final List<Integer> rowY=new ArrayList<>();
    public RouteScreen(ArenaNet.Routes data){super(Component.literal("Arena - Gruppenweg"));this.data=data;}
    public void update(ArenaNet.Routes value){data=value;position();}
    private Component description(int slot){return Component.literal(ArenaRouteChoice.Route.values()[slot].description(data.noHeal(),data.goalsEnabled()));}
    private Component label(int slot){return Component.literal((data.choice()==slot?"> ":"")+ArenaRouteChoice.Route.values()[slot].title+" | Stimmen: "+data.count(slot));}
    @Override protected void init(){
        w=Math.min(400,width-24);h=Math.min(345,height-24);x=(width-w)/2;y=(height-h)/2;
        choices.clear();rowY.clear();int offset=0;
        for(int i=0;i<3;i++){
            final int slot=i;rowY.add(offset);
            choices.add(addRenderableWidget(Button.builder(label(slot),b->PacketDistributor.sendToServer(new ArenaNet.RouteVote(data.run(),data.wave(),slot))).bounds(x+10,y+58+offset,w-20,20).build()));
            offset+=34+font.split(description(slot),w-24).size()*10;
        }
        contentHeight=offset;scroll=Math.min(scroll,maxScroll());
        addRenderableWidget(Button.builder(Component.literal("Schliessen | wieder oeffnen: /arena weg"),b->onClose()).bounds(x+10,y+h-27,w-20,20).build());
        position();
    }
    private int maxScroll(){return Math.max(0,contentHeight-(h-96));}
    private void position(){
        for(int i=0;i<choices.size();i++){
            var b=choices.get(i);b.setY(y+58+rowY.get(i)-scroll);b.setMessage(label(i));
            b.visible=b.getY()>=y+56&&b.getY()+20<=y+h-35;
            b.active=data.open()&&(i!=2||data.goalsEnabled());
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(dy)*20));position();return true;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){super.renderBackground(g,mx,my,tick);Panel.draw(g,x,y,x+w,y+h,24,0xFFD700);}
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        super.render(g,mx,my,tick);
        g.drawCenteredString(font,title,width/2,y+8,0xFFFFFF);
        g.drawString(font,data.voted()+"/"+data.voters()+" gewaehlt | noch "+data.seconds()+" s",x+10,y+30,0xCCCCFF);
        g.drawString(font,"Meiste Stimmen; Gleichstand: Erholung",x+10,y+42,0xFFD700);
        g.enableScissor(x+5,y+56,x+w-5,y+h-35);
        for(int i=0;i<3;i++)g.drawWordWrap(font,description(i),x+10,y+82+rowY.get(i)-scroll,w-24,0xCCCCCC);
        g.disableScissor();
    }
    @Override public boolean isPauseScreen(){return false;}
}
