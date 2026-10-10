package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

/** Faerbt eine frisch gebaute Arena um (Variante 1 = Glut, 2 = Eis); Stufen, Platten und Waende behalten ihre Ausrichtung. */
final class ArenaPalette {
    private ArenaPalette() {}

    private static Map<Block, Block> table(int variant) {
        Map<Block, Block> m = new HashMap<>();
        if (variant == 1) {
            m.put(Blocks.DEEPSLATE_BRICKS, Blocks.NETHER_BRICKS);
            m.put(Blocks.POLISHED_DEEPSLATE, Blocks.RED_NETHER_BRICKS);
            m.put(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE);
            m.put(Blocks.DEEPSLATE, Blocks.NETHERRACK);
            m.put(Blocks.COBBLED_DEEPSLATE, Blocks.BASALT);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRIMSON_PLANKS);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.NETHER_BRICK_STAIRS);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.NETHER_BRICK_SLAB);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.NETHER_BRICK_WALL);
            m.put(Blocks.PURPUR_PILLAR, Blocks.CRIMSON_STEM);
            m.put(Blocks.CRYING_OBSIDIAN, Blocks.MAGMA_BLOCK);
            m.put(Blocks.AMETHYST_BLOCK, Blocks.MAGMA_BLOCK);
            m.put(Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT);
            m.put(Blocks.SOUL_LANTERN, Blocks.LANTERN);
            m.put(Blocks.TUFF, Blocks.SMOOTH_BASALT);
            m.put(Blocks.MOSS_BLOCK, Blocks.NETHER_WART_BLOCK);
            m.put(Blocks.PURPLE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS);
            m.put(Blocks.MAGENTA_STAINED_GLASS, Blocks.RED_STAINED_GLASS);
            m.put(Blocks.PURPLE_WOOL, Blocks.ORANGE_WOOL);
            m.put(Blocks.PURPLE_CANDLE, Blocks.ORANGE_CANDLE);
            m.put(Blocks.AMETHYST_CLUSTER, Blocks.FIRE_CORAL_FAN);
        } else if (variant == 2) {
            m.put(Blocks.DEEPSLATE_BRICKS, Blocks.PACKED_ICE);
            m.put(Blocks.POLISHED_DEEPSLATE, Blocks.QUARTZ_BLOCK);
            m.put(Blocks.DEEPSLATE_TILES, Blocks.LIGHT_GRAY_CONCRETE);
            m.put(Blocks.DEEPSLATE, Blocks.SNOW_BLOCK);
            m.put(Blocks.COBBLED_DEEPSLATE, Blocks.CALCITE);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.STONE_BRICKS);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.STONE_BRICK_SLAB);
            m.put(Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.STONE_BRICK_WALL);
            m.put(Blocks.PURPUR_PILLAR, Blocks.QUARTZ_PILLAR);
            m.put(Blocks.CRYING_OBSIDIAN, Blocks.LIGHT_BLUE_CONCRETE);
            m.put(Blocks.AMETHYST_BLOCK, Blocks.PRISMARINE_BRICKS);
            m.put(Blocks.TUFF, Blocks.SNOW_BLOCK);
            m.put(Blocks.MOSS_BLOCK, Blocks.SNOW_BLOCK);
            m.put(Blocks.PURPLE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS);
            m.put(Blocks.MAGENTA_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS);
            m.put(Blocks.PURPLE_WOOL, Blocks.LIGHT_BLUE_WOOL);
            m.put(Blocks.RED_WOOL, Blocks.WHITE_WOOL);
            m.put(Blocks.RED_CARPET, Blocks.LIGHT_BLUE_CARPET);
            m.put(Blocks.PURPLE_CANDLE, Blocks.WHITE_CANDLE);
            m.put(Blocks.RED_CANDLE, Blocks.LIGHT_BLUE_CANDLE);
        }
        return m;
    }

    static void apply(ServerLevel level, BlockPos center, int variant) {
        Map<Block, Block> map = table(variant);
        if (map.isEmpty()) return;
        int r = ArenaManager.ARENA_RADIUS + 45;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = 55; y <= 130; y++) {
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState old = level.getBlockState(pos);
                    if (old.isAir()) continue;
                    Block to = map.get(old.getBlock());
                    if (to == null) continue;
                    level.setBlock(pos, copyProps(old, to.defaultBlockState()), 2);
                }
            }
        }
    }

    private static BlockState copyProps(BlockState from, BlockState to) {
        for (Property<?> p : from.getProperties()) {
            if (to.hasProperty(p)) to = copy(from, to, p);
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.setValue(p, from.getValue(p));
    }
}
