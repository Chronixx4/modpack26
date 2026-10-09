package de.chronicle.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * Baut Lobby und Kampfplatz selbst, damit keine Strukturdatei noetig ist.
 * Der Boden der Arena richtet sich nach dem Thema der Begegnung. Wer eine eigene Arena bauen will, legt eine
 * Strukturdatei unter data/chronicle_arena/structure/arena/&lt;thema&gt;.nbt ab; sie ersetzt dann das generierte Thema.
 */
final class ArenaBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos MARKER = new BlockPos(0, 60, 200);
    private static final int CLEAR_HEIGHT = 16;

    /** Bodenmuster, Mitte, Ring, Saeule, Licht, Zierblock. */
    private record Palette(Block a, Block b, Block center, Block ring, Block pillar, Block light, Block accent) {}

    private static final Map<String, Palette> THEMES = Map.of(
            "default", new Palette(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE,
                    Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN, Blocks.CHISELED_POLISHED_BLACKSTONE),
            "nether", new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS,
                    Blocks.GILDED_BLACKSTONE, Blocks.CHISELED_NETHER_BRICKS, Blocks.SHROOMLIGHT, Blocks.BLACKSTONE),
            "end", new Palette(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.OBSIDIAN,
                    Blocks.CRYING_OBSIDIAN, Blocks.PURPUR_PILLAR, Blocks.END_ROD, Blocks.PURPUR_BLOCK),
            "ice", new Palette(Blocks.CALCITE, Blocks.SNOW_BLOCK, Blocks.LIGHT_BLUE_CONCRETE,
                    Blocks.PRISMARINE_BRICKS, Blocks.PACKED_ICE, Blocks.SEA_LANTERN, Blocks.BLUE_ICE),
            "desert", new Palette(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
                    Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE, Blocks.GLOWSTONE, Blocks.GOLD_BLOCK),
            "forest", new Palette(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE,
                    Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.SHROOMLIGHT, Blocks.MOSS_BLOCK),
            "sea", new Palette(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE,
                    Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE),
            "deepdark", new Palette(Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.SCULK,
                    Blocks.SCULK_CATALYST, Blocks.POLISHED_DEEPSLATE, Blocks.SOUL_LANTERN, Blocks.REINFORCED_DEEPSLATE),
            "sky", new Palette(Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.GOLD_BLOCK,
                    Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.WHITE_CONCRETE));

    private static String currentTheme;

    private ArenaBuilder() {}

    static boolean hasTheme(String id) {
        return THEMES.containsKey(id);
    }

    static java.util.Set<String> themes() {
        return THEMES.keySet();
    }

    static void ensureBuilt(ServerLevel level) {
        if (level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK)) return;
        buildLobby(level);
        buildShell(level);
        currentTheme = null;
        level.setBlock(MARKER, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    // ------------------------------------------------------------------ Lobby

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
        set(level, cx, fy, cz, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(ArenaManager.LOBBY, ModRegistry.TERMINAL.get().defaultBlockState(), 2);
        set(level, cx, fy, cz + 8, Blocks.CHISELED_POLISHED_BLACKSTONE);
        level.setBlock(new BlockPos(cx, fy + 1, cz + 8), ModRegistry.PORTAL.get().defaultBlockState(), 2);
        int[][] corners = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
        for (int[] c : corners) pillar(level, cx + c[0], fy + 1, cz + c[1], 4, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SEA_LANTERN);
    }

    // ------------------------------------------------------------------ Arena

    /** Unsichtbare Wand und Decke, die gleich bleiben. */
    private static void buildShell(ServerLevel level) {
        int cx = ArenaManager.ARENA.getX();
        int cz = ArenaManager.ARENA.getZ();
        int fy = ArenaManager.ARENA.getY() - 1;
        int r = ArenaManager.ARENA_RADIUS;
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
    }

    /** Baut den Kampfplatz im Stil des Themas neu auf, wenn es sich geaendert hat. */
    static void applyTheme(ServerLevel level, String theme) {
        String id = THEMES.containsKey(theme) ? theme : "default";
        if (id.equals(currentTheme)) return;
        int cx = ArenaManager.ARENA.getX();
        int cz = ArenaManager.ARENA.getZ();
        int fy = ArenaManager.ARENA.getY() - 1;
        int r = ArenaManager.ARENA_RADIUS;

        // Alles innerhalb der Wand leeren.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > r + 0.4) continue;
                for (int y = fy; y <= fy + CLEAR_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // Eigene Struktur (Boden = unterste Ebene, in der Mitte der Arena).
        Optional<StructureTemplate> custom = level.getStructureManager().get(ChronicleArena.id("arena/" + id));
        if (custom.isPresent()) {
            StructureTemplate t = custom.get();
            BlockPos origin = new BlockPos(cx - t.getSize().getX() / 2, fy, cz - t.getSize().getZ() / 2);
            t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
            LOGGER.info("Arena: eigene Struktur arena/{} eingesetzt ({} Bloecke gross)", id, t.getSize());
            currentTheme = id;
            return;
        }

        Palette p = THEMES.get(id);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (d <= 6.0) {
                    set(level, x, fy, z, p.center());
                } else if (d <= 7.0) {
                    set(level, x, fy, z, p.ring());
                } else if (d <= r - 1.5) {
                    set(level, x, fy, z, ((dx + dz) & 1) == 0 ? p.a() : p.b());
                } else if (d <= r - 0.5) {
                    set(level, x, fy, z, p.ring());
                }
            }
        }
        // Stufenring in der Mitte.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= 3.5) set(level, cx + dx, fy, cz + dz, p.accent());
            }
        }
        // Acht hohe Saeulen am Rand, vier niedrige im Inneren, dazu Lichtstelen.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            pillar(level, cx + (int) Math.round(Math.cos(a) * (r - 3)), fy + 1,
                    cz + (int) Math.round(Math.sin(a) * (r - 3)), 6 + (i % 2) * 3, p.pillar(), p.light());
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2.0 * i / 4.0 + Math.PI / 4.0;
            pillar(level, cx + (int) Math.round(Math.cos(a) * 12), fy + 1,
                    cz + (int) Math.round(Math.sin(a) * 12), 2, p.accent(), p.light());
        }
        currentTheme = id;
        LOGGER.info("Arena: Thema {} aufgebaut", id);
    }

    private static void pillar(ServerLevel level, int x, int y, int z, int h, Block body, Block top) {
        for (int i = 0; i < h; i++) set(level, x, y + i, z, body);
        set(level, x, y + h, z, top);
    }
}
