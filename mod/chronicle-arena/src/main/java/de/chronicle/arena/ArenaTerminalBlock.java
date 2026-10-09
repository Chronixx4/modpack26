package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Rechtsklick oeffnet das Arena-Menue (Modus, Stufe, Start/Zuschauen). */
public class ArenaTerminalBlock extends Block {
    public ArenaTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ArenaManager.openTerminal(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
