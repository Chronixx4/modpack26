package de.chronicle.arena.client;

import de.chronicle.arena.Net;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Searchable encounters; selection details remain available as full tooltips. */
public final class EncounterScreen extends Screen {
    private final Screen parent;
    private final List<Net.EncInfo> all;
    private final Consumer<String> choose;
    private final List<Button> buttons=new ArrayList<>();
    private String query="";
    private int offset,rows,x,y,w,h;
    public EncounterScreen(Screen parent,List<Net.EncInfo> all,Consumer<String> choose){super(Component.literal("Begegnung auswaehlen"));this.parent=parent;this.all=all;this.choose=choose;}
    private List<Net.EncInfo> filtered(){return all.stream().filter(e->(e.name()+" "+e.desc()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();}
    @Override protected void init(){
        buttons.clear();w=Math.min(360,width-20);h=Math.min(330,height-20);x=(width-w)/2;y=(height-h)/2;rows=Math.max(1,(h-88)/24);
        EditBox search=new EditBox(font,x+10,y+32,w-20,20,Component.literal("Suchen"));search.setMaxLength(80);search.setValue(query);search.setHint(Component.literal("Name oder Beschreibung suchen ..."));search.setResponder(q->{query=q;offset=0;refresh();});addRenderableWidget(search);
        for(int i=0;i<rows;i++){final int row=i;buttons.add(addRenderableWidget(Button.builder(Component.empty(),b->{var list=filtered();if(offset+row<list.size()){choose.accept(list.get(offset+row).id());onClose();}}).bounds(x+32,y+60+i*24,w-42,20).build()));}
        addRenderableWidget(Button.builder(Component.literal("Zurueck"),b->onClose()).bounds(x+10,y+h-25,w-20,20).build());refresh();
    }
    private void refresh(){var list=filtered();offset=Math.max(0,Math.min(offset,Math.max(0,list.size()-rows)));for(int i=0;i<buttons.size();i++){var b=buttons.get(i);b.visible=offset+i<list.size();if(b.visible)b.setMessage(Component.literal(list.get(offset+i).name()));}}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){offset-=(int)Math.signum(dy);refresh();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        renderBackground(g,mx,my,tick);Panel.draw(g,x,y,x+w,y+h,24,0xFFD700);g.drawCenteredString(font,title,width/2,y+8,0xFFFFFF);super.render(g,mx,my,tick);
        var list=filtered();for(int i=0;i<buttons.size();i++)if(buttons.get(i).visible){var e=list.get(offset+i);var id=ResourceLocation.tryParse(e.icon());if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),x+10,y+62+i*24);if(buttons.get(i).isHovered())g.renderTooltip(font,font.split(Component.literal(e.desc()),250),mx,my);}
        if(list.isEmpty())g.drawString(font,"Keine Treffer",x+10,y+65,0xAAAAAA);
    }
    @Override public boolean isPauseScreen(){return false;}
}
