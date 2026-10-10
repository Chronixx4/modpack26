package de.chronicle.arena;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Baut Lobby, Kampfarena und Shop-Plattform selbst, damit keine Strukturdatei noetig ist.
 * Wer die Arena selbst gestalten will, legt eine Strukturdatei unter
 * data/chronicle_arena/structure/arena/arena.nbt (bzw. lobby.nbt, shop.nbt) ab; sie ersetzt dann den generierten Teil.
 */
final class ArenaBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Versionsmarker; ein anderer Block baut alles neu. */
    private static final BlockPos MARKER = new BlockPos(0, 60, 200);
    private static final Block MARKER_BLOCK = Blocks.PURPUR_PILLAR;
    private static final int ARENA_HEIGHT = 16;

    private ArenaBuilder() {}

    static void ensureBuilt(ServerLevel level) {
        if (level.getBlockState(MARKER).is(MARKER_BLOCK)) return;
        buildLobby(level);
        buildShop(level);
        buildBridge(level);   // nach dem Shop, sonst raeumt dessen Platz das Brueckenende wieder weg
        buildArena(level, ArenaManager.ARENA);
        ArenaDecor.lobby(level);
        ArenaDecor.shop(level);
        ArenaDecor.bridge(level);
        ArenaDecor.arena(level, ArenaManager.ARENA);
        level.setBlock(MARKER, MARKER_BLOCK.defaultBlockState(), 2);
        LOGGER.info("Arena: Lobby, Shop und Kampfplatz gebaut");
    }

    /** Zusaetzliche Kampfarenen (Glut- und Eisarena) werden beim ersten Bedarf gebaut und umgefaerbt. */
    static void ensureArena(ServerLevel level, BlockPos center, int variant) {
        if (variant == 0) return;
        BlockPos marker = new BlockPos(center.getX(), 60, center.getZ());
        if (level.getBlockState(marker).is(MARKER_BLOCK)) return;
        buildArena(level, center);
        ArenaDecor.arena(level, center);
        ArenaPalette.apply(level, center, variant);
        level.setBlock(marker, MARKER_BLOCK.defaultBlockState(), 2);
        LOGGER.info("Arena: Variante {} bei {} gebaut", variant, center);
    }

    // ------------------------------------------------------------------ Helfer

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void clear(ServerLevel level, int cx, int cz, int fy, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > radius + 0.4) continue;
                for (int y = fy; y <= fy + height; y++) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void pillar(ServerLevel level, int x, int y, int z, int h, Block body, Block top) {
        for (int i = 0; i < h; i++) set(level, x, y + i, z, body);
        set(level, x, y + h, z, top);
    }

    private static BlockState hangingLantern(Block block) {
        return block.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private static BlockState stairs(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** Ob an dieser Stelle eine eigene Struktur steht; platziert sie dann (Boden = unterste Ebene, mittig). */
    private static boolean placeCustom(ServerLevel level, String name, int cx, int cz, int fy) {
        Optional<StructureTemplate> custom = level.getStructureManager().get(ChronicleArena.id("arena/" + name));
        if (custom.isEmpty()) return false;
        StructureTemplate t = custom.get();
        BlockPos origin = new BlockPos(cx - t.getSize().getX() / 2, fy, cz - t.getSize().getZ() / 2);
        t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        LOGGER.info("Arena: eigene Struktur arena/{} eingesetzt ({})", name, t.getSize());
        return true;
    }

    // ------------------------------------------------------------------ Lobby

    private static void buildLobby(ServerLevel level) {
        int cx = ArenaManager.LOBBY.getX();
        int cz = ArenaManager.LOBBY.getZ();
        int fy = ArenaManager.LOBBY.getY() - 1;
        int r = ArenaManager.LOBBY_RADIUS;
        clear(level, cx, cz, fy, r + 2, 14);
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
                boolean gap = dx > 0 && Math.abs(dz) <= 3;   // Durchgang zur Bruecke (Osten)
                if (d >= r - 0.5 && d < r + 0.5 && !gap) {
                    for (int y = fy + 1; y <= fy + 10; y++) set(level, x, y, z, Blocks.BARRIER);
                }
                if (d <= r + 0.5) set(level, x, fy + 11, z, Blocks.BARRIER);
            }
        }
        // Sockel mit Terminal, Muster in Gold.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) set(level, cx + dx, fy, cz + dz,
                        (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Blocks.GOLD_BLOCK : Blocks.CHISELED_POLISHED_BLACKSTONE);
            }
        }
        set(level, cx, fy, cz, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(ArenaManager.LOBBY, ModRegistry.TERMINAL.get().defaultBlockState(), 2);
        level.setBlock(ArenaManager.LOBBY.above(1), ModRegistry.HOLO_TERMINAL.get().defaultBlockState(), 2);
        level.setBlock(ArenaManager.LOBBY.above(2), ModRegistry.HOLO_TERMINAL.get().defaultBlockState(), 2);
        // Ausgangsportal.
        set(level, cx, fy, cz + 8, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(new BlockPos(cx, fy + 1, cz + 8), ModRegistry.PORTAL.get().defaultBlockState(), 2);
        int[][] corners = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
        for (int[] c : corners) pillar(level, cx + c[0], fy + 1, cz + c[1], 4, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN);
        // Wegweiser zum Shop: Laternen am Durchgang.
        pillar(level, cx + r - 2, fy + 1, cz - 4, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN);
        pillar(level, cx + r - 2, fy + 1, cz + 4, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN);
    }

    // ------------------------------------------------------------------ Bruecke und Shop

    private static void buildBridge(ServerLevel level) {
        int cx = ArenaManager.LOBBY.getX();
        int cz = ArenaManager.LOBBY.getZ();
        int fy = ArenaManager.LOBBY.getY() - 1;
        int from = cx + ArenaManager.LOBBY_RADIUS - 1;
        int to = ArenaManager.SHOP.getX() - ArenaManager.SHOP_RADIUS + 1;
        for (int x = from; x <= to; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dz) == 2;
                set(level, x, fy, cz + dz, edge ? Blocks.POLISHED_BLACKSTONE_BRICKS
                        : ((x + dz) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
                if (edge) {
                    set(level, x, fy + 1, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                    for (int y = fy + 2; y <= fy + 4; y++) set(level, x, y, cz + dz, Blocks.BARRIER);
                }
                set(level, x, fy + 5, cz + dz, Blocks.BARRIER);
            }
            if ((x - from) % 6 == 3) {
                for (int s : new int[]{-2, 2}) {
                    set(level, x, fy + 2, cz + s, Blocks.SEA_LANTERN);
                }
            }
        }
    }

    private static void buildShop(ServerLevel level) {
        int cx = ArenaManager.SHOP.getX();
        int cz = ArenaManager.SHOP.getZ();
        int fy = ArenaManager.SHOP.getY() - 1;
        int r = ArenaManager.SHOP_RADIUS;
        clear(level, cx, cz, fy, r + 2, 14);
        if (placeCustom(level, "shop", cx, cz, fy)) return;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d <= 3.5) {
                    set(level, x, fy, z, ((dx + dz) & 1) == 0 ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE);
                } else if (d <= r - 1.5) {
                    set(level, x, fy, z, ((dx + dz) & 1) == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.CUT_SANDSTONE);
                } else if (d <= r - 0.5) {
                    set(level, x, fy, z, Blocks.CHISELED_SANDSTONE);
                }
                boolean gap = dx < 0 && Math.abs(dz) <= 3;   // Brueckenseite
                if (d >= r - 0.5 && d < r + 0.5 && !gap) {
                    for (int y = fy + 1; y <= fy + 10; y++) set(level, x, y, z, Blocks.BARRIER);
                }
                if (d <= r + 0.5) set(level, x, fy + 11, z, Blocks.BARRIER);
            }
        }
        // Haendler in der Mitte.
        level.setBlock(ArenaManager.SHOP, ModRegistry.SHOP.get().defaultBlockState(), 2);
        level.setBlock(ArenaManager.SHOP.above(1), ModRegistry.HOLO_SHOP.get().defaultBlockState(), 2);
        level.setBlock(ArenaManager.SHOP.above(2), ModRegistry.HOLO_SHOP.get().defaultBlockState(), 2);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            pillar(level, cx + c[0], fy + 1, cz + c[1], 3, Blocks.CUT_SANDSTONE_SLAB, Blocks.LANTERN);
        }
        // Marktstaende: sechs Buden im Kreis, mit Dach aus buntem Stoff und Laternen.
        Block[] roofs = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL};
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2.0 * i / 6.0 + Math.PI / 6.0;
            int sx = cx + (int) Math.round(Math.cos(a) * (r - 4));
            int sz = cz + (int) Math.round(Math.sin(a) * (r - 4));
            stall(level, sx, fy + 1, sz, roofs[i]);
        }
        // Lichtsaeulen am Rand.
        for (int i = 0; i < 8; i++) {
            if (i == 4) continue; // Westseite: dort ist der Eingang von der Bruecke
            double a = Math.PI * 2.0 * i / 8.0;
            pillar(level, cx + (int) Math.round(Math.cos(a) * (r - 1)), fy + 1,
                    cz + (int) Math.round(Math.sin(a) * (r - 1)), 4, Blocks.SANDSTONE_WALL, Blocks.GLOWSTONE);
        }
    }

    private static void stall(ServerLevel level, int x, int y, int z, Block roof) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, x + dx, y + 3, z + dz, roof);
                set(level, x + dx, y + 4, z + dz, Blocks.AIR);
            }
        }
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            for (int h = 0; h < 3; h++) set(level, x + c[0], y + h, z + c[1], Blocks.OAK_FENCE);
        }
        set(level, x, y, z, Blocks.BARREL);
        set(level, x, y + 1, z, Blocks.LANTERN);
        set(level, x, y + 2, z, Blocks.AIR);
        set(level, x, y + 3, z, roof);
    }

    // ------------------------------------------------------------------ Kampfarena

    private static void buildArena(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cz = center.getZ();
        int fy = center.getY() - 1;
        int r = ArenaManager.ARENA_RADIUS;
        clear(level, cx, cz, fy, r + 3, 44);

        // Unsichtbare Wand und Decke.
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d >= r + 0.5 && d < r + 2.0) {
                    for (int y = fy + 1; y <= fy + 40; y++) set(level, x, y, z, Blocks.BARRIER);
                }
                if (d < r + 2.0) set(level, x, fy + 41, z, Blocks.BARRIER);
            }
        }
        if (placeCustom(level, "arena", cx, cz, fy)) return;

        // Boden: Schachbrett, goldene Mitte, Ringe, Laeufer.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d > r + 0.4) continue;
                Block floor;
                if (d <= 2.5) {
                    floor = Blocks.GOLD_BLOCK;
                } else if (d <= 5.0) {
                    floor = ((int) Math.floor(Math.atan2(dz, dx) / (Math.PI / 4.0)) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.CHISELED_POLISHED_BLACKSTONE;
                } else if (d <= 6.0) {
                    floor = Blocks.GILDED_BLACKSTONE;
                } else if (d <= 7.0) {
                    floor = Blocks.CRYING_OBSIDIAN;
                } else if (d <= 33.0) {
                    boolean ring = Math.abs(d - 14.0) < 0.6 || Math.abs(d - 26.0) < 0.6;
                    floor = ring ? Blocks.POLISHED_BLACKSTONE_BRICKS
                            : ((dx + dz) & 1) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE;
                } else if (d <= 34.0) {
                    floor = Blocks.CRYING_OBSIDIAN;
                } else {
                    floor = Blocks.DEEPSLATE_BRICKS;
                }
                set(level, x, fy, z, floor);
            }
        }
        // Vier rote Laeufer (Teppich) auf den Achsen.
        for (int i = 8; i <= 20; i++) {
            for (int w = -1; w <= 1; w++) {
                set(level, cx + i, fy + 1, cz + w, Blocks.RED_CARPET);
                set(level, cx - i, fy + 1, cz + w, Blocks.RED_CARPET);
                set(level, cx + w, fy + 1, cz + i, Blocks.RED_CARPET);
                set(level, cx + w, fy + 1, cz - i, Blocks.RED_CARPET);
            }
        }
        // Lichter im Boden.
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 16.0;
            set(level, cx + (int) Math.round(Math.cos(a) * 17.5), fy, cz + (int) Math.round(Math.sin(a) * 17.5), Blocks.SEA_LANTERN);
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 24.0;
            set(level, cx + (int) Math.round(Math.cos(a) * 29.5), fy, cz + (int) Math.round(Math.sin(a) * 29.5), Blocks.SEA_LANTERN);
        }
        // Tribuene: vier Stufen aussen (Zuschauer, Gaeste), dahinter eine hohe Mauer.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 34.0 || d > r + 0.4) continue;
                int steps = d < 35.0 ? 0 : d < 36.0 ? 1 : d < 37.0 ? 2 : d < 38.0 ? 3 : 4;
                int x = cx + dx;
                int z = cz + dz;
                for (int h = 1; h <= steps; h++) {
                    set(level, x, fy + h, z, h == steps && ((dx + dz) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS);
                }
                if (d >= 38.0) {
                    for (int h = steps + 1; h <= 14; h++) {
                        set(level, x, fy + h, z, h == 14 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                    }
                } else {
                    // Zweite Ebene: Balkon rundherum, innen nur eine flache Kante (Springen oder Schiessen nach unten).
                    set(level, x, fy + 8, z, ((dx + dz) & 1) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
                    if (d < 34.6) set(level, x, fy + 9, z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
                    if (d >= 36.0 && d < 37.0 && ((int) Math.round(Math.atan2(dz, dx) * 40.0) & 3) == 0) set(level, x, fy + 7, z, Blocks.SEA_LANTERN);
                }
            }
        }
        // Zinnen auf der Mauer.
        for (int i = 0; i < 160; i++) {
            if ((i & 1) == 1) continue;
            double a = Math.PI * 2.0 * i / 160.0;
            int x = cx + (int) Math.round(Math.cos(a) * 39.5);
            int z = cz + (int) Math.round(Math.sin(a) * 39.5);
            set(level, x, fy + 15, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        // Sechzehn Saeulen vor der Tribuene mit Fahnen (Wolle), Ketten und Laternen.
        Block[] banner = {Blocks.RED_WOOL, Blocks.PURPLE_WOOL};
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * i / 24.0;
            int px = cx + (int) Math.round(Math.cos(a) * 33.0);
            int pz = cz + (int) Math.round(Math.sin(a) * 33.0);
            pillar(level, px, fy + 1, pz, 8, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE);
            set(level, px, fy + 10, pz, Blocks.SEA_LANTERN);
            // Fahne an der Innenseite der Saeule.
            int fx = px - (int) Math.signum(Math.cos(a));
            int fz = pz - (int) Math.signum(Math.sin(a));
            if (Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a))) fz = pz; else fx = px;
            for (int h = 3; h <= 7; h++) set(level, fx, fy + h, fz, banner[i & 1]);
            set(level, fx, fy + 8, fz, Blocks.GOLD_BLOCK);
        }
        // Feuerschalen auf niedrigen Sockeln.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            int bx = cx + (int) Math.round(Math.cos(a) * 9.0);
            int bz = cz + (int) Math.round(Math.sin(a) * 9.0);
            set(level, bx, fy + 1, bz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            set(level, bx, fy + 2, bz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            set(level, bx, fy + 3, bz, Blocks.CAMPFIRE);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12.0 + Math.PI / 12.0;
            int bx = cx + (int) Math.round(Math.cos(a) * 24.0);
            int bz = cz + (int) Math.round(Math.sin(a) * 24.0);
            set(level, bx, fy + 1, bz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            set(level, bx, fy + 2, bz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            set(level, bx, fy + 3, bz, Blocks.CAMPFIRE);
        }
        // Vier Treppen (je zwei Breite 3) hinauf zum Balkon.
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                Direction up = sx > 0 ? Direction.EAST : Direction.WEST;
                for (int i = 0; i < 8; i++) {
                    for (int w = -1; w <= 1; w++) {
                        int x = cx + sx * (25 + i);
                        int z = cz + sz * 12 + w;
                        for (int y = fy + 1; y < fy + 1 + i; y++) set(level, x, y, z, Blocks.DEEPSLATE_BRICKS);
                        set(level, x, fy + 1 + i, z, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, up));
                        if (i == 7) {
                            for (int k = 0; k <= 3; k++) set(level, x + sx * k, fy + 9, z, Blocks.AIR);
                        }
                    }
                }
            }
        }
        // Vier Torbogen auf den Achsen (Eingaenge der Tribuene).
        for (int axis = 0; axis < 4; axis++) {
            int ax = (int) Math.round(Math.cos(axis * Math.PI / 2.0));
            int az = (int) Math.round(Math.sin(axis * Math.PI / 2.0));
            for (int w = -2; w <= 2; w++) {
                int x = cx + ax * 35 + (az != 0 ? w : 0);
                int z = cz + az * 35 + (ax != 0 ? w : 0);
                set(level, x, fy + 6, z, Blocks.GOLD_BLOCK);
            }
        }
    }
}
