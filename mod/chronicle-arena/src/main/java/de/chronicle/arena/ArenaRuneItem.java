package de.chronicle.arena;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Purchased charges are real consumables; map pickups activate immediately. */
public final class ArenaRuneItem extends Item {
    private final int kind;
    public ArenaRuneItem(int kind){super(new Item.Properties().stacksTo(16));this.kind=kind;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide())return InteractionResultHolder.success(stack);
        if(player instanceof ServerPlayer sp&&ArenaManager.useRune(sp,kind)){
            if(!player.getAbilities().instabuild)stack.shrink(1);
            player.getCooldowns().addCooldown(this,80);return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> lines,TooltipFlag flags){
        lines.add(Component.literal(kind==0?"5 s Stasis im Radius 8":kind==1?"3 Echo-Schlaege: je 4 Zusatzschaden":"Runenschild: 4 Schutz fuer 8 s"));
        lines.add(Component.literal("Rechtsklick im laufenden Arena-Kampf."));
        lines.add(Component.literal("Einmal verwendbar. Gemeinsamer Cooldown: 4 s."));
    }
}
