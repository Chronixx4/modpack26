package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Unsichtbarer, anklickbarer Block unter dem Holo-Fenster: Rechtsklick auf das Fenster oeffnet das Menue. */
public class ArenaHoloBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private final boolean shop;

    public ArenaHoloBlock(Properties properties, boolean shop) {
        super(properties);
        this.shop = shop;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (shop) ArenaManager.openShop(sp); else ArenaManager.openTerminal(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
