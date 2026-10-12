package de.chronicle.arena.client;

import de.chronicle.arena.ArenaNet;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vertical cards adapt to GUI scale; long descriptions and owned blessings scroll. */
public final class BlessingScreen extends Screen {
    private ArenaNet.Blessings data;
    private int x,y,w,h,scroll,contentHeight;
    private final List<Button> choices=new ArrayList<>();
    private final List<Integer> rowY=new ArrayList<>();
    private boolean sent;
    public BlessingScreen(ArenaNet.Blessings data){super(Component.literal("Arena - Segen"));this.data=data;}
    public boolean choosing(){return !data.offers().isEmpty();}
    public void update(ArenaNet.Blessings p){boolean rebuild=!data.offers().equals(p.offers())||!data.active().equals(p.active());data=p;if(rebuild)rebuildWidgets();}
    @Override protected void init(){
        w=Math.min(370,width-24);h=Math.min(350,height-24);x=(width-w)/2;y=(height-h)/2;
        choices.clear();rowY.clear();int offset=0;
        for(int i=0;i<data.offers().size();i++){
            final int slot=i+1;var info=data.offers().get(i);int dy=offset;rowY.add(dy);
            Button b=addRenderableWidget(Button.builder(Component.literal(info.name()+" - Stapel "+info.stacks()+" -> "+(info.stacks()+1)),v->{if(sent)return;sent=true;choices.forEach(c->c.active=false);PacketDistributor.sendToServer(new ArenaNet.Pick(data.run(),data.wave(),slot));}).bounds(x+30,y+48+dy,w-42,20).build());choices.add(b);
            offset+=36+font.split(Component.literal(info.description()),w-24).size()*10+(info.synergy().isEmpty()?0:font.split(Component.literal(info.synergy()),w-24).size()*10);
        }
        offset+=18;
        for(var info:data.active())offset+=26+font.split(Component.literal(info.description()),w-24).size()*10+(info.synergy().isEmpty()?0:font.split(Component.literal(info.synergy()),w-24).size()*10);
        contentHeight=offset;scroll=Math.min(scroll,maxScroll());
        addRenderableWidget(Button.builder(Component.literal(choosing()?"Spaeter waehlen (/arena segen)":"Schliessen"),b->onClose()).bounds(x+10,y+h-27,w-20,20).build());position();
    }
    private int maxScroll(){return Math.max(0,contentHeight-(h-87));}
    private void position(){for(int i=0;i<choices.size();i++){var b=choices.get(i);b.setY(y+48+rowY.get(i)-scroll);b.visible=b.getY()>=y+46&&b.getY()+20<=y+h-35;b.active=!sent;}}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(dy)*20));position();return true;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){
        super.renderBackground(g,mx,my,tick);Panel.draw(g,x,y,x+w,y+h,24,0xFFD700);
        g.enableScissor(x+5,y+46,x+w-5,y+h-35);
        for(int i=0;i<rowY.size();i++){
            int top=y+48+rowY.get(i)-scroll;
            var info=data.offers().get(i);
            int height=36+font.split(Component.literal(info.description()),w-24).size()*10+(info.synergy().isEmpty()?0:font.split(Component.literal(info.synergy()),w-24).size()*10)-4;
            g.fill(x+6,top-2,x+w-6,top+height,0xFF6B4BB0);g.fillGradient(x+7,top-1,x+w-7,top+height-1,0xFF30234A,0xFF171022);
        }
        g.disableScissor();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        super.render(g,mx,my,tick);g.drawCenteredString(font,title,width/2,y+8,0xFFFFFF);
        g.drawString(font,choosing()?"Eine Auswahl | Zufall in "+data.seconds()+" s":"Aktive Segen dieses Durchgangs",x+10,y+31,0xCCCCFF);
        g.enableScissor(x+5,y+46,x+w-5,y+h-35);int cy=y+48-scroll;
        for(var info:data.offers()){
            icon(g,info,cy+2);cy+=24;g.drawWordWrap(font,Component.literal(info.description()),x+10,cy,w-24,0xFFFFFF);cy+=font.split(Component.literal(info.description()),w-24).size()*10;
            if(!info.synergy().isEmpty()){g.drawWordWrap(font,Component.literal(info.synergy()),x+10,cy,w-24,0x7FE0FF);cy+=font.split(Component.literal(info.synergy()),w-24).size()*10;}cy+=12;
        }
        g.drawString(font,"Deine aktiven Segen",x+10,cy,0xFFD700);cy+=18;
        if(data.active().isEmpty())g.drawString(font,"Noch keine Segen gewaehlt.",x+10,cy,0xAAAAAA);
        for(var info:data.active()){
            icon(g,info,cy);g.drawString(font,info.name()+" x"+info.stacks(),x+30,cy+3,0xFFD700);cy+=20;
            g.drawWordWrap(font,Component.literal(info.description()),x+10,cy,w-24,0xCCCCCC);cy+=font.split(Component.literal(info.description()),w-24).size()*10;
            if(!info.synergy().isEmpty()){g.drawWordWrap(font,Component.literal(info.synergy()),x+10,cy,w-24,0x7FE0FF);cy+=font.split(Component.literal(info.synergy()),w-24).size()*10;}cy+=6;
        }
        g.disableScissor();if(maxScroll()>0)g.drawString(font,"Scrollen",x+w-55,y+31,0xAAAAAA);
    }
    private void icon(GuiGraphics g,ArenaNet.BlessInfo info,int cy){var id=ResourceLocation.tryParse(info.icon());if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),x+10,cy);}
    @Override public boolean isPauseScreen(){return false;}
}
