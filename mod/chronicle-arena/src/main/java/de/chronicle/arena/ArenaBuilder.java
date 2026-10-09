package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Baut Lobby und Kampfplatz beim ersten Betreten selbst, damit keine Strukturdatei noetig ist. */
final class ArenaBuilder {
    private static final BlockPos MARKER = new BlockPos(0, 60, 200);

    private ArenaBuilder() {}

    static void ensureBuilt(ServerLevel level) {
        if (level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK)) {
            // Fixtures koennten fehlen, wenn eine Mod-Version sie neu eingefuehrt hat.
            return;
        }
        buildLobby(level);
        buildArena(level);
        level.setBlock(MARKER, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void buildLobby(ServerLevel level) {
        int cx = ArenaManager.LOBBY.getX();
        int cz = ArenaManager.LOBBY.getZ();
        int fy = ArenaManager.LOBBY.getY() - 1;
        int r = ArenaManager.LOBBY_RADIUS;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d <= r - 1.5) {
                    set(level, x, fy, z, ((dx + dz) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
                } else if (d <= r - 0.5) {
                    set(level, x, fy, z, Blocks.CRYING_OBSIDIAN);
                }
                if (d >= r - 0.5 && d < r + 0.5) {
                    for (int y = fy + 1; y <= fy + 10; y++) set(level, x, y, z, Blocks.BARRIER);
                }
                if (d <= r + 0.5) set(level, x, fy + 11, z, Blocks.BARRIER);
            }
        }
        // Terminal auf Sockel.
        set(level, cx, fy, cz, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(ArenaManager.LOBBY, ModRegistry.TERMINAL.get().defaultBlockState(), 2);
        // Ausgangsportal.
        set(level, cx, fy, cz + 8, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(new BlockPos(cx, fy + 1, cz + 8), ModRegistry.PORTAL.get().defaultBlockState(), 2);
        // Lichtsaeulen.
        int[][] corners = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
        for (int[] c : corners) pillar(level, cx + c[0], fy + 1, cz + c[1], 4);
    }

    private static void buildArena(ServerLevel level) {
        int cx = ArenaManager.ARENA.getX();
        int cz = ArenaManager.ARENA.getZ();
        int fy = ArenaManager.ARENA.getY() - 1;
        int r = ArenaManager.ARENA_RADIUS;
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d <= r - 1.5) {
                    boolean inner = d <= 6;
                    set(level, x, fy, z, inner ? Blocks.POLISHED_BLACKSTONE
                            : ((dx + dz) & 1) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
                } else if (d <= r - 0.5) {
                    set(level, x, fy, z, Blocks.CRYING_OBSIDIAN);
                }
                // Unsichtbare Wand und Decke.
                if (d >= r + 0.5 && d < r + 2.0) {
                    for (int y = fy + 1; y <= fy + 40; y++) set(level, x, y, z, Blocks.BARRIER);
                }
                if (d < r + 2.0) set(level, x, fy + 41, z, Blocks.BARRIER);
            }
        }
        // Saeulen am Rand.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            int x = cx + (int) Math.round(Math.cos(a) * (r - 3));
            int z = cz + (int) Math.round(Math.sin(a) * (r - 3));
            pillar(level, x, fy + 1, z, 6);
        }
    }

    private static void pillar(ServerLevel level, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) set(level, x, y + i, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        set(level, x, y + h, z, Blocks.SEA_LANTERN);
    }
}
