package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Traegt nur den Renderer fuer das schwebende Holo-Fenster ueber Terminal und Shop. */
public class HoloBlockEntity extends BlockEntity {
    public HoloBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.HOLO.get(), pos, state);
    }
}
