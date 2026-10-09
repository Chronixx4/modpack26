package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Rechtsklick: Hin zur Arena-Lobby bzw. aus der Lobby zurueck. */
public class ArenaPortalBlock extends Block {
    public ArenaPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ArenaManager.usePortal(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
