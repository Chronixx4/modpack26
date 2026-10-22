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
    private static final Block SIEGE_MARKER = Blocks.CHISELED_COPPER;

    private ArenaMaps() {}

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    // ------------------------------------------------------------------ Burghof

    /** Feste Streuung fuer Deko (gleiche Karte bei jedem Bau). */
    private static int hash(int x, int z) {
        int v = x * 73856093 ^ z * 19349663;
        v ^= v >>> 13;
        v *= 0x5bd1e995;
        return Math.floorMod(v ^ (v >>> 15), 100);
    }

    /** Freie Wiese: kein Pfad, kein Bauplatz (mit Rand), nicht am Kristall, nicht am Startplatz, nicht an der Mauer. */
    static boolean meadow(int x, int z) {
        int h = ArenaSiege.HALF;
        if (Math.abs(x) >= h - 1 || Math.abs(z) >= h - 1) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (ArenaSiege.onPath(x + dx, z + dz)) return false;
        for (int[] pad : ArenaSiege.PADS) if (Math.abs(x - pad[0]) <= 3 && Math.abs(z - pad[1]) <= 3) return false;
        if (Math.abs(x - ArenaSiege.CORE[0]) <= 4 && Math.abs(z - ArenaSiege.CORE[1]) <= 4) return false;
        if (x >= 5 && x <= 12 && z >= 18 && z <= 21) return false;
        if (x > ArenaSiege.HALF - 5 && Math.abs(z - ArenaSiege.GATE2_Z) <= 4) return false;
        return Math.abs(x - ArenaSiege.PATH[0][0]) > 4 || z > ArenaSiege.PATH[0][1] + 2;
    }

    static void ensureSiege(ServerLevel level) {
        BlockPos c = ArenaSiege.CENTER;
        BlockPos marker = new BlockPos(c.getX(), 60, c.getZ());
        if (level.getBlockState(marker).is(SIEGE_MARKER)) return;
        int h = ArenaSiege.HALF;
        int gx = ArenaSiege.PATH[0][0], gz = ArenaSiege.PATH[0][1];
        for (int x = -h - 3; x <= h + 3; x++) {
            for (int z = -h - 3; z <= h + 3; z++) {
                for (int y = -2; y <= 16; y++) {
                    BlockPos p = c.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) set(level, p, Blocks.AIR);
                }
                boolean wall = Math.abs(x) > h || Math.abs(z) > h;
                BlockPos floor = c.offset(x, -1, z);
                int n = hash(x, z);
                if (wall) {
                    boolean outer = Math.abs(x) > h + 2 || Math.abs(z) > h + 2;
                    boolean cornerTower = Math.abs(x) > h - 3 && Math.abs(z) > h - 3;
                    set(level, c.offset(x, -2, z), Blocks.STONE_BRICKS);
                    set(level, floor, Blocks.STONE_BRICKS);
                    int top = cornerTower ? 8 : 5;
                    for (int y = 0; y < top; y++) {
                        Block b = n < 12 ? Blocks.MOSSY_STONE_BRICKS : n < 20 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
                        if (y == top - 1) b = Blocks.CHISELED_STONE_BRICKS;
                        set(level, c.offset(x, y, z), b);
                    }
                    // Zinnen auf der Aussenkante, dahinter Barrieren bis unter die Decke.
                    boolean merlon = outer && Math.floorMod(x + z, 2) == 0;
                    if (merlon) set(level, c.offset(x, top, z), Blocks.STONE_BRICKS);
                    for (int y = top + (merlon ? 1 : 0); y <= 15; y++) set(level, c.offset(x, y, z), Blocks.BARRIER);
                    if (cornerTower && Math.abs(x) == h + 2 && Math.abs(z) == h + 2) set(level, c.offset(x, top + 1, z), Blocks.LANTERN);
                    continue;
                }
                set(level, c.offset(x, -2, z), Blocks.STONE);
                boolean gateLane = Math.abs(x - gx) <= 2 && z < gz;
                Block ground;
                if (ArenaSiege.onPath(x, z) || gateLane) {
                    ground = n < 14 ? Blocks.COARSE_DIRT : n < 22 ? Blocks.PACKED_MUD : Blocks.DIRT_PATH;
                } else {
                    ground = n < 6 ? Blocks.MOSS_BLOCK : n < 9 ? Blocks.PODZOL : n < 11 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
                }
                set(level, floor, ground);
                set(level, c.offset(x, 15, z), Blocks.BARRIER);
            }
        }
        // Pfad-Raender aus Stein, damit der Weg klar zu sehen ist; ab und zu ein Laternenpfahl.
        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                if (ArenaSiege.onPath(x, z)) continue;
                boolean edge = ArenaSiege.onPath(x + 1, z) || ArenaSiege.onPath(x - 1, z) || ArenaSiege.onPath(x, z + 1) || ArenaSiege.onPath(x, z - 1);
                if (!edge) continue;
                int n = hash(x, z);
                set(level, c.offset(x, -1, z), n < 30 ? Blocks.MOSSY_COBBLESTONE : n < 60 ? Blocks.COBBLESTONE : Blocks.POLISHED_ANDESITE);
                boolean nearPad = false;
                for (int[] pad : ArenaSiege.PADS) if (Math.abs(x - pad[0]) <= 2 && Math.abs(z - pad[1]) <= 2) nearPad = true;
                if (!nearPad && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0 || !nearPad && n == 3) {
                    set(level, c.offset(x, 0, z), Blocks.SPRUCE_FENCE);
                    set(level, c.offset(x, 1, z), Blocks.LANTERN);
                }
            }
        }
        // Wiese: Gras, Blumen, Buesche, Licht im Boden.
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM};
        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                if (!meadow(x, z)) continue;
                int n = hash(x, z), m = hash(z * 3 + 1, x * 5 - 2);
                BlockPos floor = c.offset(x, -1, z), above = c.offset(x, 0, z);
                if (Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) { set(level, floor, Blocks.SEA_LANTERN); continue; }
                if (n >= 96) {
                    set(level, floor, Blocks.GRASS_BLOCK);
                    set(level, above, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    if (m < 40) set(level, c.offset(x, 1, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                } else if (n < 30 && level.getBlockState(floor).is(Blocks.GRASS_BLOCK)) {
                    set(level, above, m < 12 ? flowers[m % flowers.length] : Blocks.SHORT_GRASS);
                }
            }
        }
        // Wandbehaenge an den Innenseiten.
        for (int i = -h + 4; i <= h - 4; i += 8) {
            if (Math.abs(i - ArenaSiege.GATE2_Z) > 5) set(level, c.offset(h, 3, i), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, net.minecraft.core.Direction.WEST));
            set(level, c.offset(-h, 3, i), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, net.minecraft.core.Direction.EAST));
            set(level, c.offset(i, 3, h), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, net.minecraft.core.Direction.NORTH));
            if (Math.abs(i - gx) > 5) set(level, c.offset(i, 3, -h), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, net.minecraft.core.Direction.SOUTH));
        }
        // Bauplaetze.
        for (int[] pad : ArenaSiege.PADS) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                set(level, c.offset(pad[0] + dx, -1, pad[1] + dz), dx == 0 && dz == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE);
            }
        }
        // Kristall: Leuchtfeuer mit violettem Strahl, umringt von Amethyst.
        int cx = ArenaSiege.CORE[0], cz = ArenaSiege.CORE[1];
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean ring = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            set(level, c.offset(cx + dx, -1, cz + dz), Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Blocks.GOLD_BLOCK : ring ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_BRICKS);
        }
        set(level, c.offset(cx, 0, cz), Blocks.BEACON);
        for (int y = 1; y <= 3; y++) set(level, c.offset(cx, y, cz), Blocks.PURPLE_STAINED_GLASS);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int y = 0; y <= 2; y++) set(level, c.offset(cx + d[0], y, cz + d[1]), y == 2 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK);
            set(level, c.offset(cx + d[0], 3, cz + d[1]), Blocks.AMETHYST_CLUSTER);
        }
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            set(level, c.offset(cx + d[0], 0, cz + d[1]), Blocks.QUARTZ_PILLAR);
            set(level, c.offset(cx + d[0], 1, cz + d[1]), Blocks.QUARTZ_PILLAR);
            set(level, c.offset(cx + d[0], 2, cz + d[1]), Blocks.SOUL_LANTERN);
        }
        // Torhaus am Pfadanfang: dunkler Durchgang, halb hochgezogenes Fallgitter, Seelenfeuer.
        for (int dx = -4; dx <= 4; dx++) {
            for (int y = 0; y <= 8; y++) {
                boolean post = Math.abs(dx) >= 3;
                boolean top = y >= 5;
                for (int zz = -h - 2; zz <= -h - 1; zz++) {
                    if (post || top) set(level, c.offset(gx + dx, y, zz), top && !post ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE);
                    else set(level, c.offset(gx + dx, y, zz), zz == -h - 2 ? Blocks.BLACK_CONCRETE : Blocks.AIR);
                }
                if (!post && !top && y >= 3) set(level, c.offset(gx + dx, y, -h - 1), Blocks.IRON_BARS);
            }
            if (Math.floorMod(dx, 2) == 0) set(level, c.offset(gx + dx, 9, -h - 1), Blocks.DEEPSLATE_BRICKS);
        }
        for (int s : new int[]{-3, 3}) {
            set(level, c.offset(gx + s, 0, -h), Blocks.POLISHED_DEEPSLATE);
            set(level, c.offset(gx + s, 1, -h), Blocks.SOUL_CAMPFIRE);
        }
        // Osttor (zweiter Weg): gleiches Torhaus in der Ostmauer, Fallgitter setzt der Modus.
        int g2 = ArenaSiege.GATE2_Z;
        for (int dz = -4; dz <= 4; dz++) {
            for (int y = 0; y <= 8; y++) {
                boolean post = Math.abs(dz) >= 3;
                boolean top = y >= 5;
                for (int xx = h + 1; xx <= h + 2; xx++) {
                    if (post || top) set(level, c.offset(xx, y, g2 + dz), top && !post ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE);
                    else set(level, c.offset(xx, y, g2 + dz), xx == h + 2 ? Blocks.BLACK_CONCRETE : Blocks.IRON_BARS);
                }
            }
            if (Math.floorMod(dz, 2) == 0) set(level, c.offset(h + 1, 9, g2 + dz), Blocks.DEEPSLATE_BRICKS);
        }
        for (int sd : new int[]{-3, 3}) {
            set(level, c.offset(h, 0, g2 + sd), Blocks.POLISHED_DEEPSLATE);
            set(level, c.offset(h, 1, g2 + sd), Blocks.SOUL_CAMPFIRE);
        }
        level.setBlock(marker, SIEGE_MARKER.defaultBlockState(), 2);
        LOGGER.info("Arena: Burghof bei {} gebaut", c);
    }
}
