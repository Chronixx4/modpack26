package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Scrollable smith list; all upgrades remain reachable at large GUI scales. */
public class SmithScreen extends Screen {
    private static final int ROW=24;
    private final Net.OpenSmith data;
    private final List<Button> buys=new ArrayList<>();
    private int x,y,w,h,scroll;
    public SmithScreen(Net.OpenSmith data){super(Component.literal("Schmied"));this.data=data;}
    @Override protected void init(){
        w=Math.min(360,width-20);h=Math.min(320,height-20);x=(width-w)/2;y=(height-h)/2;buys.clear();
        for(int i=0;i<data.ups().size();i++){
            var u=data.ups().get(i);
            buys.add(addRenderableWidget(Button.builder(Component.literal(u.level()>=u.max()?"Max":u.price()+" Coins"),b->PacketDistributor.sendToServer(new Net.SmithBuy(u.id()))).bounds(x+w-100,y+48+i*ROW,90,20).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.chronicle_arena.close"),b->onClose()).bounds(x+10,y+h-27,w-20,20).build());
        scroll=Math.min(scroll,maxScroll());position();
    }
    private int maxScroll(){return Math.max(0,data.ups().size()*ROW-(h-88));}
    private void position(){for(int i=0;i<buys.size();i++){var b=buys.get(i);var u=data.ups().get(i);b.setY(y+48+i*ROW-scroll);b.visible=b.getY()>=y+46&&b.getY()+20<=y+h-35;b.active=u.level()<u.max()&&data.coins()>=u.price();}}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(dy)*ROW));position();return true;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){super.renderBackground(g,mx,my,tick);Panel.draw(g,x,y,x+w,y+h,24,0xFFD36B);}
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        super.render(g,mx,my,tick);g.drawCenteredString(font,title,width/2,y+8,0xFFD36B);g.drawString(font,"Coins: "+data.coins()+" | wirkt nur in der Arena",x+10,y+31,0xCCCCCC);
        g.enableScissor(x+5,y+46,x+w-5,y+h-35);
        for(int i=0;i<data.ups().size();i++){
            var u=data.ups().get(i);int top=y+48+i*ROW-scroll;
            g.drawString(font,font.plainSubstrByWidth(u.name()+" "+u.level()+"/"+u.max(),w-120),x+10,top,0xFFFFFF);
            g.drawString(font,font.plainSubstrByWidth(u.desc(),w-120),x+10,top+10,0xBBBBBB);
        }
        g.disableScissor();
        for(int i=0;i<data.ups().size();i++){
            int top=y+48+i*ROW-scroll;
            if(top>=y+46&&top+20<=y+h-35&&mx>=x+10&&mx<x+w-110&&my>=top&&my<top+20)g.renderTooltip(font,Component.literal(data.ups().get(i).desc()),mx,my);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
