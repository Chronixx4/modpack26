package de.chronicle.arena;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Karten der neuen Modi, bisher der Burghof (Burg halten). Er wird beim ersten Kampf gebaut;
 * ein Markerblock unter der Karte merkt sich die Bauversion. Neue Version = anderer Markerblock.
 */
final class ArenaMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Block SIEGE_MARKER = Blocks.CHISELED_QUARTZ_BLOCK;

    private ArenaMaps() {}

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    // ------------------------------------------------------------------ Burghof

    static void ensureSiege(ServerLevel level) {
        BlockPos c = ArenaSiege.CENTER;
        BlockPos marker = new BlockPos(c.getX(), 60, c.getZ());
        if (level.getBlockState(marker).is(SIEGE_MARKER)) return;
        int h = ArenaSiege.HALF;
        for (int x = -h - 2; x <= h + 2; x++) {
            for (int z = -h - 2; z <= h + 2; z++) {
                for (int y = -2; y <= 14; y++) {
                    BlockPos p = c.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) set(level, p, Blocks.AIR);
                }
                boolean wall = Math.abs(x) > h || Math.abs(z) > h;
                BlockPos floor = c.offset(x, -1, z);
                if (wall) {
                    set(level, c.offset(x, -2, z), Blocks.STONE_BRICKS);
                    set(level, floor, Blocks.STONE_BRICKS);
                    for (int y = 0; y <= 4; y++) set(level, c.offset(x, y, z), (y == 4 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS));
                    for (int y = 5; y <= 13; y++) set(level, c.offset(x, y, z), Blocks.BARRIER);
                    continue;
                }
                set(level, c.offset(x, -2, z), Blocks.STONE);
                Block ground;
                if (ArenaSiege.onPath(x, z)) ground = Blocks.DIRT_PATH;
                else if ((x + z) % 2 == 0 && Math.floorMod(x * 7 + z * 13, 5) == 0) ground = Blocks.MOSSY_STONE_BRICKS;
                else ground = Blocks.GRASS_BLOCK;
                set(level, floor, ground);
                // Licht: Seelaternen im Boden abseits des Pfades.
                if (!ArenaSiege.onPath(x, z) && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) set(level, floor, Blocks.SEA_LANTERN);
                set(level, c.offset(x, 13, z), Blocks.BARRIER);
            }
        }
        // Pfad-Raender aus Steinziegeln, damit der Weg gut zu sehen ist.
        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                if (ArenaSiege.onPath(x, z)) continue;
                boolean edge = ArenaSiege.onPath(x + 1, z) || ArenaSiege.onPath(x - 1, z) || ArenaSiege.onPath(x, z + 1) || ArenaSiege.onPath(x, z - 1);
                if (edge) set(level, c.offset(x, -1, z), Blocks.POLISHED_ANDESITE);
            }
        }
        // Bauplaetze.
        for (int[] pad : ArenaSiege.PADS) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                set(level, c.offset(pad[0] + dx, -1, pad[1] + dz), dx == 0 && dz == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE);
            }
        }
        // Kristall auf einem Sockel.
        int cx = ArenaSiege.CORE[0], cz = ArenaSiege.CORE[1];
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(level, c.offset(cx + dx, -1, cz + dz), Blocks.QUARTZ_BRICKS);
        set(level, c.offset(cx, 0, cz), Blocks.AMETHYST_BLOCK);
        set(level, c.offset(cx, 1, cz), Blocks.AMETHYST_BLOCK);
        set(level, c.offset(cx, 2, cz), Blocks.BUDDING_AMETHYST);
        set(level, c.offset(cx, 3, cz), Blocks.END_ROD);
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            set(level, c.offset(cx + d[0], 0, cz + d[1]), Blocks.QUARTZ_PILLAR);
            set(level, c.offset(cx + d[0], 1, cz + d[1]), Blocks.SOUL_LANTERN);
        }
        // Tor am Pfadanfang.
        int gx = ArenaSiege.PATH[0][0];
        for (int dx = -3; dx <= 3; dx++) {
            for (int y = 0; y <= 5; y++) {
                boolean post = Math.abs(dx) == 3;
                boolean top = y >= 4;
                if (post || top) set(level, c.offset(gx + dx, y, -h), top && !post ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE);
                else set(level, c.offset(gx + dx, y, -h - 1), Blocks.BLACK_STAINED_GLASS);
            }
        }
        set(level, c.offset(gx - 3, 6, -h), Blocks.LANTERN.defaultBlockState());
        set(level, c.offset(gx + 3, 6, -h), Blocks.LANTERN.defaultBlockState());
        // Fackeln auf der Mauer rundum.
        for (int i = -h; i <= h; i += 4) {
            set(level, c.offset(i, 5, -h - 1), Blocks.GLOWSTONE);
            set(level, c.offset(i, 5, h + 1), Blocks.GLOWSTONE);
            set(level, c.offset(-h - 1, 5, i), Blocks.GLOWSTONE);
            set(level, c.offset(h + 1, 5, i), Blocks.GLOWSTONE);
        }
        level.setBlock(marker, SIEGE_MARKER.defaultBlockState(), 2);
        LOGGER.info("Arena: Burghof bei {} gebaut", c);
    }
}
