package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Zusatzschmuck fuer Lobby, Bruecke, Shop und Arena: Details, Tuerme, Fenster, schwebende Inseln, Monde.
 * Alles bleibt ausserhalb der Kampffelder bzw. bodennah, damit es Kaempfe nicht behindert.
 */
final class ArenaDecor {
    private ArenaDecor() {}

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static BlockState stairs(Block block, Direction facing, boolean top) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, top ? net.minecraft.world.level.block.state.properties.Half.TOP
                        : net.minecraft.world.level.block.state.properties.Half.BOTTOM);
    }

    private static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private static BlockState litCandle(Block candle) {
        return candle.defaultBlockState().setValue(CandleBlock.LIT, true);
    }

    private static void pillar(ServerLevel level, int x, int y, int z, int h, Block body, Block top) {
        for (int i = 0; i < h; i++) set(level, x, y + i, z, body);
        set(level, x, y + h, z, top);
    }

    private static void blob(ServerLevel level, int cx, int cy, int cz, double rx, double ry, double rz, RandomSource rnd,
                             Block[] skin, Block core) {
        int ir = (int) Math.ceil(Math.max(rx, Math.max(ry, rz)));
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double v = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                    double noise = 0.9 + rnd.nextDouble() * 0.2;
                    if (v * noise > 1.0) continue;
                    Block b = v > 0.55 ? skin[rnd.nextInt(skin.length)] : core;
                    set(level, cx + dx, cy + dy, cz + dz, b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Himmel

    /** Schwebende Inseln mit Kristallen, Ketten und Lichtern rund um einen Punkt. */
    static void floatingIslands(ServerLevel level, int cx, int cy, int cz, int count, double inner, double outer, long seed) {
        RandomSource rnd = RandomSource.create(seed);
        Block[] skin = {Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE, Blocks.CRYING_OBSIDIAN, Blocks.TUFF};
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * (i + rnd.nextDouble() * 0.6) / count;
            double d = inner + rnd.nextDouble() * (outer - inner);
            int x = cx + (int) Math.round(Math.cos(a) * d);
            int z = cz + (int) Math.round(Math.sin(a) * d);
            int y = cy + 18 + rnd.nextInt(20);
            double rx = 3 + rnd.nextInt(4);
            double rz = 3 + rnd.nextInt(4);
            double ry = 2 + rnd.nextInt(3);
            blob(level, x, y, z, rx, ry, rz, rnd, skin, Blocks.OBSIDIAN);
            // Unterseite: Spitze nach unten.
            for (int s = 1; s <= 3 + rnd.nextInt(4); s++) {
                int w = Math.max(0, (int) (rx * 0.5) - s / 2);
                for (int dx = -w; dx <= w; dx++) {
                    for (int dz = -w; dz <= w; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > w + 1) continue;
                        set(level, x + dx, y - (int) ry - s + 1, z + dz, Blocks.DEEPSLATE);
                    }
                }
            }
            int top = y + (int) ry;
            // Oberseite: Gras/Moos, Kristalle, Licht.
            for (int dx = -(int) rx + 1; dx < (int) rx; dx++) {
                for (int dz = -(int) rz + 1; dz < (int) rz; dz++) {
                    if ((dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) > 0.55) continue;
                    BlockPos p = new BlockPos(x + dx, top, z + dz);
                    while (level.getBlockState(p).isAir() && p.getY() > y - 2) p = p.below();
                    set(level, p.getX(), p.getY(), p.getZ(), rnd.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.DEEPSLATE_TILES);
                    if (rnd.nextInt(9) == 0) set(level, p.getX(), p.getY() + 1, p.getZ(), Blocks.AMETHYST_CLUSTER);
                    else if (rnd.nextInt(14) == 0) set(level, p.getX(), p.getY() + 1, p.getZ(), Blocks.MOSS_CARPET);
                }
            }
            int sy = top + 1;
            // Kleiner Obelisk mit Licht.
            pillar(level, x, sy, z, 3 + rnd.nextInt(3), Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN);
            set(level, x, sy + 8, z, Blocks.AIR);
            // Ketten mit Laternen unter der Insel.
            for (int c = 0; c < 2; c++) {
                int lx = x + rnd.nextInt(5) - 2;
                int lz = z + rnd.nextInt(5) - 2;
                int ly = y - (int) ry - 1;
                int len = 2 + rnd.nextInt(4);
                for (int k = 0; k < len; k++) set(level, lx, ly - k, lz, Blocks.CHAIN);
                set(level, lx, ly - len, lz, hanging(rnd.nextBoolean() ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
            }
        }
    }

    /** Grosser leuchtender Mond mit Kratern. */
    static void moon(ServerLevel level, int cx, int cy, int cz, int r) {
        RandomSource rnd = RandomSource.create(cx * 31L + cz);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r + 0.3) continue;
                    Block b = d > r - 1 && rnd.nextInt(7) == 0 ? Blocks.GLOWSTONE
                            : d > r - 2 ? Blocks.SMOOTH_QUARTZ : Blocks.WHITE_CONCRETE;
                    set(level, cx + dx, cy + dy, cz + dz, b);
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double e = (rnd.nextDouble() - 0.5) * 2.0;
            int kx = cx + (int) Math.round(Math.cos(a) * Math.sqrt(1 - e * e) * r);
            int kz = cz + (int) Math.round(Math.sin(a) * Math.sqrt(1 - e * e) * r);
            int ky = cy + (int) Math.round(e * r);
            set(level, kx, ky, kz, Blocks.LIGHT_GRAY_CONCRETE);
            set(level, kx + 1, ky, kz, Blocks.LIGHT_GRAY_CONCRETE);
            set(level, kx, ky + 1, kz, Blocks.LIGHT_GRAY_CONCRETE);
        }
    }

    /** Ein Ring aus Licht hoch ueber einem Platz. */
    static void halo(ServerLevel level, int cx, int cy, int cz, int r) {
        int n = r * 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int z = cz + (int) Math.round(Math.sin(a) * r);
            set(level, x, cy, z, i % 12 == 0 ? Blocks.SEA_LANTERN : Blocks.PURPUR_PILLAR);
            if (i % 12 == 0) {
                set(level, x, cy + 1, z, Blocks.END_ROD);
                for (int k = 1; k <= 3; k++) set(level, x, cy - k, z, Blocks.CHAIN);
                set(level, x, cy - 4, z, hanging(Blocks.SOUL_LANTERN));
            }
        }
    }

    // ------------------------------------------------------------------ Lobby

    static void lobby(ServerLevel level) {
        int cx = ArenaManager.LOBBY.getX();
        int cz = ArenaManager.LOBBY.getZ();
        int fy = ArenaManager.LOBBY.getY() - 1;
        int r = ArenaManager.LOBBY_RADIUS;
        // Teppiche: lila Ring um das Terminal, rote Wege zu Portal und Bruecke.
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= 4.0 && d <= 5.2) set(level, cx + dx, fy + 1, cz + dz, Blocks.PURPLE_CARPET);
            }
        }
        for (int x = 3; x <= r - 2; x++) for (int w = -1; w <= 1; w++) {
            if (Math.sqrt(x * x + w * w) > 5.3 || Math.sqrt(x * x + w * w) < 3.8) set(level, cx + x, fy + 1, cz + w, Blocks.RED_CARPET);
        }
        for (int z = 3; z <= 7; z++) set(level, cx, fy + 1, cz + z, Blocks.RED_CARPET);
        // Balustrade am Rand (ausser am Durchgang), alle vier Felder eine Laterne.
        for (int i = 0; i < 160; i++) {
            double a = Math.PI * 2.0 * i / 160.0;
            int x = (int) Math.round(Math.cos(a) * (r - 1.0));
            int z = (int) Math.round(Math.sin(a) * (r - 1.0));
            if (x > 4 && Math.abs(z) <= 3) continue;
            set(level, cx + x, fy + 1, cz + z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            if (i % 8 == 0) {
                set(level, cx + x, fy + 2, cz + z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                set(level, cx + x, fy + 3, cz + z, Blocks.SOUL_LANTERN);
            }
        }
        // Vier Zauberecken mit Zaubertisch, Buecherregalen und Kerzen.
        for (int[] c : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            int sx = Integer.signum(c[0]);
            int sz = Integer.signum(c[1]);
            set(level, cx + c[0], fy + 1, cz + c[1], Blocks.ENCHANTING_TABLE);
            for (int h = 1; h <= 3; h++) {
                set(level, cx + c[0] + sx, fy + h, cz + c[1], Blocks.BOOKSHELF);
                set(level, cx + c[0], fy + h, cz + c[1] + sz, Blocks.BOOKSHELF);
            }
            set(level, cx + c[0] + sx, fy + 4, cz + c[1], Blocks.SEA_LANTERN);
            set(level, cx + c[0], fy + 4, cz + c[1] + sz, Blocks.SEA_LANTERN);
            set(level, cx + c[0] - sx, fy + 1, cz + c[1], litCandle(Blocks.PURPLE_CANDLE));
            set(level, cx + c[0], fy + 1, cz + c[1] - sz, litCandle(Blocks.PURPLE_CANDLE));
        }
        // Kronleuchter ueber dem Terminal.
        for (int h = 0; h < 3; h++) set(level, cx, fy + 10 - h, cz, Blocks.CHAIN);
        set(level, cx, fy + 7, cz, Blocks.SEA_LANTERN);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            set(level, cx + dir.getStepX() * 2, fy + 8, cz + dir.getStepZ() * 2, Blocks.CHAIN);
            set(level, cx + dir.getStepX() * 2, fy + 7, cz + dir.getStepZ() * 2, hanging(Blocks.SOUL_LANTERN));
            set(level, cx + dir.getStepX(), fy + 8, cz + dir.getStepZ(), Blocks.END_ROD);
        }
        // Sockel-Treppe um das Terminal.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            set(level, cx + dir.getStepX() * 3, fy + 1, cz + dir.getStepZ() * 3,
                    stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, dir.getOpposite(), false));
        }
        // Amethyst an den Ecken des Sockels.
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) set(level, cx + c[0], fy + 1, cz + c[1], Blocks.AMETHYST_CLUSTER);
        // Himmel.
        floatingIslands(level, cx, fy, cz, 9, 22, 40, 11L);
        halo(level, cx, fy + 16, cz, 18);
    }

    // ------------------------------------------------------------------ Bruecke

    static void bridge(ServerLevel level) {
        int cx = ArenaManager.LOBBY.getX();
        int cz = ArenaManager.LOBBY.getZ();
        int fy = ArenaManager.LOBBY.getY() - 1;
        int from = cx + ArenaManager.LOBBY_RADIUS - 1;
        int to = ArenaManager.SHOP.getX() - ArenaManager.SHOP_RADIUS + 1;
        for (int x = from + 1; x < to; x++) {
            set(level, x, fy + 1, cz, Blocks.PURPLE_CARPET);
            if ((x - from) % 6 == 0) {
                for (int s : new int[]{-2, 2}) {
                    for (int y = fy + 2; y <= fy + 5; y++) set(level, x, y, cz + s, y == fy + 5 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
                for (int w = -2; w <= 2; w++) set(level, x, fy + 6, cz + w, Blocks.POLISHED_BLACKSTONE_BRICKS);
                set(level, x, fy + 7, cz, Blocks.SEA_LANTERN);
                set(level, x, fy + 5, cz, hanging(Blocks.SOUL_LANTERN));
                set(level, x, fy + 7, cz - 2, Blocks.AMETHYST_CLUSTER);
                set(level, x, fy + 7, cz + 2, Blocks.AMETHYST_CLUSTER);
            }
            if ((x - from) % 3 == 0) {
                // Lichtstreifen unter der Bruecke.
                set(level, x, fy - 1, cz, Blocks.SEA_LANTERN);
            }
        }
        // Schwebende Inseln entlang der Bruecke.
        floatingIslands(level, (from + to) / 2, fy, cz, 6, 14, 28, 23L);
    }

    // ------------------------------------------------------------------ Shop

    static void shop(ServerLevel level) {
        int cx = ArenaManager.SHOP.getX();
        int cz = ArenaManager.SHOP.getZ();
        int fy = ArenaManager.SHOP.getY() - 1;
        int r = ArenaManager.SHOP_RADIUS;
        // Mosaik: blauer Ring um die goldene Mitte, Lapis-Sterne.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.6 && d <= 5.0) {
                    set(level, cx + dx, fy, cz + dz, ((int) Math.floor(Math.atan2(dz, dx) / (Math.PI / 6.0)) & 1) == 0
                            ? Blocks.LAPIS_BLOCK : Blocks.BLUE_TERRACOTTA);
                } else if (d > 5.0 && d <= 5.8) {
                    set(level, cx + dx, fy, cz + dz, Blocks.GOLD_BLOCK);
                }
            }
        }
        // Wege zu den Staenden (Teppich in der Farbe des Dachs).
        Block[] carpets = {Blocks.RED_CARPET, Blocks.ORANGE_CARPET, Blocks.YELLOW_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET, Blocks.GREEN_CARPET};
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2.0 * i / 6.0 + Math.PI / 6.0;
            for (int s = 6; s <= r - 5; s++) {
                set(level, cx + (int) Math.round(Math.cos(a) * s), fy + 1, cz + (int) Math.round(Math.sin(a) * s), carpets[i]);
            }
            // Dekoration am Stand: Dekotopf und Blumen.
            int sx = cx + (int) Math.round(Math.cos(a) * (r - 4));
            int sz = cz + (int) Math.round(Math.sin(a) * (r - 4));
            int ox = (int) -Math.signum(Math.cos(a));
            int oz = (int) -Math.signum(Math.sin(a));
            set(level, sx + ox, fy + 1, sz, Blocks.DECORATED_POT);
            set(level, sx, fy + 1, sz + oz, Blocks.POTTED_FLOWERING_AZALEA);
            // Ketten mit Laternen unter dem Dach.
            set(level, sx, fy + 4, sz, Blocks.CHAIN);
            set(level, sx, fy + 3, sz, hanging(Blocks.LANTERN));
        }
        // Pergola ueber dem Haendler: vier Saeulen, Balken, Dach aus Treppen und Spitze.
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            pillar(level, cx + c[0], fy + 4, cz + c[1], 1, Blocks.CUT_SANDSTONE_SLAB, Blocks.LANTERN);
            set(level, cx + c[0], fy + 4, cz + c[1], Blocks.SANDSTONE_WALL);
            set(level, cx + c[0], fy + 5, cz + c[1], Blocks.CHISELED_SANDSTONE);
        }
        for (int d = -2; d <= 2; d++) {
            set(level, cx + d, fy + 5, cz - 2, Blocks.CUT_SANDSTONE);
            set(level, cx + d, fy + 5, cz + 2, Blocks.CUT_SANDSTONE);
            set(level, cx - 2, fy + 5, cz + d, Blocks.CUT_SANDSTONE);
            set(level, cx + 2, fy + 5, cz + d, Blocks.CUT_SANDSTONE);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, cx + dx, fy + 6, cz + dz, Blocks.GOLD_BLOCK);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int k = -2; k <= 2; k++) {
                int px = cx + dir.getStepX() * 2 + (dir.getStepZ() != 0 ? k : 0);
                int pz = cz + dir.getStepZ() * 2 + (dir.getStepX() != 0 ? k : 0);
                set(level, px, fy + 6, pz, stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, dir.getOpposite(), false));
            }
        }
        pillar(level, cx, fy + 7, cz, 1, Blocks.SANDSTONE_WALL, Blocks.END_ROD);
        set(level, cx, fy + 9, cz, Blocks.SEA_LANTERN);
        // Haengende Laternen im Kreis ueber der Pergola.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            set(level, cx + dir.getStepX() * 2, fy + 4, cz + dir.getStepZ() * 2, Blocks.AIR);
        }
        // Blumenbeete am Rand.
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 16.0;
            int x = cx + (int) Math.round(Math.cos(a) * (r - 2.0));
            int z = cz + (int) Math.round(Math.sin(a) * (r - 2.0));
            if (x < cx - 6 && Math.abs(z - cz) <= 4) continue;
            set(level, x, fy + 1, z, Blocks.FLOWERING_AZALEA);
        }
        // Schilder-Saeulen mit Sternen am Eingang (Bruecke, Westseite).
        for (int s : new int[]{-4, 4}) {
            pillar(level, cx - r + 2, fy + 1, cz + s, 4, Blocks.CHISELED_SANDSTONE, Blocks.SEA_LANTERN);
            set(level, cx - r + 2, fy + 6, cz + s, Blocks.END_ROD);
        }
        floatingIslands(level, cx, fy, cz, 9, 22, 40, 37L);
        halo(level, cx, fy + 15, cz, 17);
    }

    // ------------------------------------------------------------------ Arena

    static void arena(ServerLevel level) {
        int cx = ArenaManager.ARENA.getX();
        int cz = ArenaManager.ARENA.getZ();
        int fy = ArenaManager.ARENA.getY() - 1;
        int r = ArenaManager.ARENA_RADIUS;
        if (level.getStructureManager().get(ChronicleArena.id("arena/arena")).isPresent()) return;

        // Speichen im Boden aus Gold (8 Richtungen) und ein Amethystring.
        for (int dx = -33; dx <= 33; dx++) {
            for (int dz = -33; dz <= 33; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 7.5 || d > 33.0) continue;
                double a = Math.atan2(dz, dx);
                double k = Math.abs(((a / (Math.PI / 4.0)) % 1.0 + 1.0) % 1.0);
                boolean spoke = k < 0.025 || k > 0.975 || Math.abs(k - 0.5) < 0.012 && d > 15;
                if (Math.abs(d - 11.0) < 0.55) set(level, cx + dx, fy, cz + dz, Blocks.AMETHYST_BLOCK);
                else if (Math.abs(d - 19.5) < 0.5) set(level, cx + dx, fy, cz + dz, ((dx + dz) & 1) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.GILDED_BLACKSTONE);
                else if (Math.abs(d - 31.5) < 0.5) set(level, cx + dx, fy, cz + dz, ((dx + dz) & 1) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.GILDED_BLACKSTONE);
                else if (spoke && Math.abs(d - 14.0) > 0.6) set(level, cx + dx, fy, cz + dz, Blocks.GILDED_BLACKSTONE);
            }
        }
        // Kerzenkreis auf dem Boden.
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 24.0;
            int x = cx + (int) Math.round(Math.cos(a) * 12.5);
            int z = cz + (int) Math.round(Math.sin(a) * 12.5);
            set(level, x, fy + 1, z, litCandle(i % 2 == 0 ? Blocks.PURPLE_CANDLE : Blocks.RED_CANDLE));
        }
        // Kristallkranz: Amethyst und Kerzen im Wechsel, dazu 8 niedrige Obelisken.
        for (int i = 0; i < 32; i++) {
            double ang = Math.PI * 2.0 * (i + 0.5) / 32.0;
            int x = cx + (int) Math.round(Math.cos(ang) * 27.5);
            int z = cz + (int) Math.round(Math.sin(ang) * 27.5);
            set(level, x, fy + 1, z, i % 2 == 0 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : litCandle(Blocks.PURPLE_CANDLE));
        }
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            int x = cx + (int) Math.round(Math.cos(ang) * 22.0);
            int z = cz + (int) Math.round(Math.sin(ang) * 22.0);
            set(level, x, fy + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            set(level, x, fy + 2, z, Blocks.CHISELED_POLISHED_BLACKSTONE);
            set(level, x, fy + 3, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            set(level, x, fy + 4, z, Blocks.AMETHYST_BLOCK);
            set(level, x, fy + 5, z, Blocks.END_ROD);
        }
        // Amethyst um die goldene Mitte.
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) set(level, cx + c[0], fy + 1, cz + c[1], Blocks.AMETHYST_CLUSTER);

        // Fenster in der Tribuenenmauer: Glas mit Licht dahinter, Bogen darueber.
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 40.0;
            for (int h = 5; h <= 8; h++) {
                int gx = cx + (int) Math.round(Math.cos(a) * 38.6);
                int gz = cz + (int) Math.round(Math.sin(a) * 38.6);
                set(level, gx, fy + h + 5, gz, h == 8 ? Blocks.AMETHYST_BLOCK : (i & 1) == 0 ? Blocks.PURPLE_STAINED_GLASS : Blocks.MAGENTA_STAINED_GLASS);
                int lx = cx + (int) Math.round(Math.cos(a) * 39.6);
                int lz = cz + (int) Math.round(Math.sin(a) * 39.6);
                set(level, lx, fy + h + 5, lz, Blocks.SEA_LANTERN);
            }
        }
        // Acht Tuerme auf der Mauer mit Flammenschalen.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            int tx = cx + (int) Math.round(Math.cos(a) * 41.5);
            int tz = cz + (int) Math.round(Math.sin(a) * 41.5);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int h = 1; h <= 20; h++) {
                        boolean edge = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                        set(level, tx + dx, fy + h, tz + dz, h == 20 ? Blocks.POLISHED_BLACKSTONE_BRICKS
                                : h >= 18 && !edge ? Blocks.CRYING_OBSIDIAN : Blocks.DEEPSLATE_BRICKS);
                    }
                    set(level, tx + dx, fy + 21, tz + dz, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Blocks.POLISHED_BLACKSTONE_BRICK_WALL : Blocks.AIR);
                }
            }
            set(level, tx, fy + 20, tz, Blocks.GOLD_BLOCK);
            set(level, tx, fy + 21, tz, Blocks.SOUL_CAMPFIRE);
            for (int h = 22; h <= 25; h++) set(level, tx, fy + h, tz, Blocks.END_ROD);
            // Fahne an der Innenseite.
            int bx = tx - (int) Math.signum(Math.cos(a)) * 2;
            int bz = tz - (int) Math.signum(Math.sin(a)) * 2;
            if (Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a))) bz = tz; else bx = tx;
            
        }
        // Torbogen auf den Achsen: Saeulen, Balken, Stufen, haengende Laternen.
        for (int axis = 0; axis < 4; axis++) {
            int ax = (int) Math.round(Math.cos(axis * Math.PI / 2.0));
            int az = (int) Math.round(Math.sin(axis * Math.PI / 2.0));
            for (int s : new int[]{-3, 3}) {
                int x = cx + ax * 35 + (az != 0 ? s : 0);
                int z = cz + az * 35 + (ax != 0 ? s : 0);
                pillar(level, x, fy + 1, z, 6, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE);
                set(level, x, fy + 8, z, Blocks.SEA_LANTERN);
            }
            for (int w = -3; w <= 3; w++) {
                int x = cx + ax * 35 + (az != 0 ? w : 0);
                int z = cz + az * 35 + (ax != 0 ? w : 0);
                set(level, x, fy + 7, z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
                if (w == 0) set(level, x, fy + 5, z, hanging(Blocks.SOUL_LANTERN));
            }
        }
        // Kronleuchter an Ketten, hoch ueber dem Kampffeld (unter der Decke).
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2.0 * i / 6.0;
            int x = cx + (int) Math.round(Math.cos(a) * 15);
            int z = cz + (int) Math.round(Math.sin(a) * 15);
            for (int h = 0; h < 8; h++) set(level, x, fy + 40 - h, z, Blocks.CHAIN);
            set(level, x, fy + 31, z, hanging(Blocks.SOUL_LANTERN));
        }
        for (int h = 0; h < 8; h++) set(level, cx, fy + 40 - h, cz, Blocks.CHAIN);
        set(level, cx, fy + 31, cz, Blocks.SEA_LANTERN);
        // Himmel: Inseln, Halos, Mond.
        floatingIslands(level, cx, fy, cz, 18, 50, 80, 53L);
        halo(level, cx, fy + 48, cz, 42);
        halo(level, cx, fy + 54, cz, 52);
        moon(level, cx + 70, fy + 62, cz - 70, 9);
        moon(level, cx - 85, fy + 78, cz - 20, 5);
    }
}
