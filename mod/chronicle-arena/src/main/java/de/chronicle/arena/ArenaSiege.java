package de.chronicle.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Burg halten: Gegner laufen fest auf einem Pfad vom Tor zum Kristall. Die Gruppe baut mit gemeinsamen Punkten Tuerme
 * auf den Bauplaetzen. Selbst kaempfen geht nur auf dem Pfad, dort greifen die Gegner aber auch an.
 * Wer durchkommt, schadet dem Kristall; ist er zerstoert, ist der Kampf verloren.
 */
final class ArenaSiege extends ModeLogic {
    static final BlockPos CENTER = new BlockPos(0, 64, 1100);
    /** Wegpunkte (x, z relativ zur Mitte): Tor -> Schlangenlinie -> vor dem Kristall. */
    static final int[][] PATH = {{-24, -28}, {-24, -16}, {24, -16}, {24, 0}, {-24, 0}, {-24, 16}, {0, 16}, {0, 23}};
    /** Zweiter Weg vom Osttor, offen ab Welle SECOND_GATE_WAVE. */
    static final int[][] PATH2 = {{30, 14}, {20, 14}, {20, 28}, {5, 28}};
    static final int[][][] PATHS = {PATH, PATH2};
    static final int SECOND_GATE_WAVE = 10;
    /** Osttor: Mitte der Oeffnung (z) in der Ostmauer. */
    static final int GATE2_Z = 14;
    static final int[] CORE = {0, 27};
    /** Bauplaetze (3x3), alle 5 Bloecke neben der Pfadmitte, damit auch kurze Reichweiten treffen. */
    static final int[][] PADS = {
            {-19, -25}, {-12, -21}, {0, -21}, {12, -21},
            {-16, -11}, {-6, -11}, {4, -11}, {14, -11},
            {-16, -5}, {-6, -5}, {4, -5}, {14, -5}, {29, -8},
            {-16, 5}, {-6, 5}, {4, 5}, {14, 5}, {-29, 8},
            {-16, 11}, {-6, 11}, {5, 16},
            {-5, 22}, {5, 22},
            {26, 9}, {25, 19}, {15, 19}, {11, 23}};
    static final int HALF = 32;
    /** Grundwerte; kommen spaeter ins Admin-Panel. Prozentwerte wirken als Faktor. */
    static final int CORE_HP = 100, CORE_HP_PLAYER = 25, START_POINTS = 500, FIRST_BUILD_SECONDS = 15, PAUSE_SECONDS = 8;
    static final int COST_PCT = 100, KILL_PCT = 100, POWER_PCT = 100, LEAK_PCT = 100;
    /** Gegner pro Welle im Vergleich zum Wellen-Modus; dafuer hat jeder weniger Leben. */
    static final double COUNT_FACTOR = 1.6, HEALTH_FACTOR = 0.75, SPEED_BONUS = 0.3;
    static final int SHOCK_COOLDOWN = 60 * 20;
    /** Besetzter Turm: Schadensfaktor; eigener Fernkampf vom Pfeilturm aus. */
    static final double MANNED_BONUS = 1.5, ARCHER_BONUS = 1.25;
    static final int MAX_TRAPS = 6;

    /** Gegnerrollen: bestimmen Tempo, Leben und welche Tuerme gut wirken. */
    static final int NORMAL = 0, RUNNER = 1, ARMORED = 2, LEADER = 3, SABOTEUR = 4, SHADE = 5;

    enum Tower {
        ARROW("Pfeilturm", "Holzturm mit Schuetzen. Trifft den Gegner, der am weitesten gekommen ist. Schwach gegen Gepanzerte.", "minecraft:bow", 75, 125, 200),
        FROST("Frostturm", "Eisspitze. Verlangsamt alle Gegner in Reichweite stark, gut gegen Laeufer.", "minecraft:blue_ice", 120, 175, 250),
        FLAME("Feuerturm", "Feuerbecken. Flammenstoss auf alle Gegner nahe am Turm, setzt sie in Brand.", "minecraft:blaze_powder", 100, 150, 250),
        CANNON("Moerser", "Langsam, aber Flaechenschaden. Stark gegen Gepanzerte, schwach gegen Laeufer.", "minecraft:fire_charge", 175, 225, 325),
        GOLEM("Golem-Kaserne", "Ein Eisengolem haelt den Weg. Stufe 2: staerker, Stufe 3: zwei Golems.", "minecraft:iron_block", 200, 250, 350),
        MINE("Goldmine", "Kaempft nicht, bringt aber nach jeder Welle Punkte in die Kasse.", "minecraft:gold_ingot", 150, 200, 300);

        final String title, desc, icon;
        final int[] price;

        Tower(String title, String desc, String icon, int... price) {
            this.title = title;
            this.desc = desc;
            this.icon = icon;
            this.price = price;
        }

        double range(int level) {
            return switch (this) {
                case ARROW -> 9 + level;
                case FROST -> 6 + level;
                case FLAME -> 5.5 + level;
                case CANNON -> 8 + level;
                case GOLEM -> 10;
                case MINE -> 0;
            };
        }

        int interval(int level) {
            return switch (this) {
                case ARROW -> 24 - 4 * level;
                case FROST -> 10;
                case FLAME -> 30 - 4 * level;
                case CANNON -> 56 - 6 * level;
                default -> 40;
            };
        }

        double damage(int level) {
            return switch (this) {
                case ARROW -> new double[]{5, 7, 10}[level - 1];
                case FROST -> level;
                case FLAME -> new double[]{6, 9, 13}[level - 1];
                case CANNON -> new double[]{7, 10, 15}[level - 1];
                default -> 0;
            };
        }

        /** Punkte, die eine Goldmine nach jeder Welle bringt. */
        static int income(int level) { return new int[]{0, 30, 50, 80}[level]; }

        /** Schadensfaktor gegen eine Gegnerrolle. */
        double vs(int role) {
            return switch (role) {
                case SABOTEUR -> 0.0;
                case SHADE -> 0.2;
                case RUNNER -> this == FLAME ? 1.5 : this == CANNON ? 0.6 : 1.0;
                case ARMORED -> this == ARROW ? 0.4 : this == FROST ? 0.5 : this == CANNON ? 1.6 : 1.0;
                default -> 1.0;
            };
        }

        /** Hoehe des Turmkoerpers; die Spitze sitzt darueber. */
        int height(int level) { return this == MINE ? 1 + level : 3 + level; }

        /** Kann ein Spieler oben stehen und den Turm verstaerken? */
        boolean mannable() { return this == ARROW || this == FROST || this == FLAME || this == CANNON; }
    }

    /** Fallen, die Spieler (schleichend, Rechtsklick auf den Weg) direkt auf den Pfad legen. */
    enum TrapType {
        SPIKES("Stachelfalle", "Sticht alle Gegner darauf, 30 Treffer lang.", "minecraft:pointed_dripstone", 60, 30),
        TAR("Teergrube", "Macht Gegner darauf sehr langsam. Haelt 3 Wellen.", "minecraft:black_dye", 50, 3),
        BOMB("Sprengfalle", "Explodiert beim ersten Gegner und trifft alles im Umkreis von 3,5 Bloecken hart. Einmalig.", "minecraft:tnt", 90, 1);

        final String title, desc, icon;
        final int price, charges;

        TrapType(String title, String desc, String icon, int price, int charges) {
            this.title = title;
            this.desc = desc;
            this.icon = icon;
            this.price = price;
            this.charges = charges;
        }

        Block carpet() { return this == SPIKES ? Blocks.LIGHT_GRAY_CARPET : this == TAR ? Blocks.BLACK_CARPET : Blocks.RED_CARPET; }
    }

    static final class Trap {
        final int x, z;
        final TrapType type;
        int charges;

        Trap(int x, int z, TrapType type, int charges) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.charges = charges;
        }

        boolean covers(int cx, int cz) { return Math.abs(cx - x) <= 1 && Math.abs(cz - z) <= 1; }
    }

    static final String[] ROMAN = {"", "I", "II", "III"};

    final int[] type = new int[PADS.length];
    final int[] level = new int[PADS.length];
    final int[] spent = new int[PADS.length];
    private final int[] cooldown = new int[PADS.length];
    private final Map<Integer, List<UUID>> golems = new HashMap<>();
    private final UUID[] padLabels = new UUID[PADS.length];
    private UUID coreLabel;
    int points = START_POINTS;
    int core, coreMax;
    int shockCooldown;
    private boolean startNow;
    final Map<UUID, Integer> waypoint = new HashMap<>();
    private final Map<UUID, Integer> worth = new HashMap<>();
    final Map<UUID, Integer> role = new HashMap<>();
    private final Map<UUID, double[]> stuck = new HashMap<>();
    private final Set<UUID> leaked = new HashSet<>();
    private final Map<UUID, Long> hint = new HashMap<>();
    final Map<UUID, Integer> pathOf = new HashMap<>();
    private int nextPath;
    final List<Trap> traps = new ArrayList<>();
    /** Saboteur -> Bauplatz, den er lahmlegt. */
    final Map<UUID, Integer> jam = new HashMap<>();
    private int saboteurs;
    private boolean gate2Open;
    /** Koop: Rolle pro Spieler, Bereitschaft der Faehigkeit (Spielzeit), getragene Splitter, Statistik, Stimmen. */
    final Map<UUID, Integer> crew = new HashMap<>();
    private final Map<UUID, Long> ready = new HashMap<>();
    final Map<UUID, Integer> carried = new HashMap<>();
    final Map<UUID, int[]> stats = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    final Map<UUID, Integer> votes = new HashMap<>();
    private final Map<UUID, Long> shards = new HashMap<>();
    private final long[] overload = new long[PADS.length];
    private final List<double[]> rains = new ArrayList<>();
    boolean rolesLocked, voteOpen;
    /** Abstimmungsergebnis fuer die laufende Welle (SiegeCrew.CALM ...). */
    int activeVote;
    /** Spielzeit der Arena-Welt beim letzten Tick (fuer die HUD-Anzeige der Abklingzeit). */
    private long now;
    private static final net.minecraft.resources.ResourceLocation GUARD_HP = ChronicleArena.id("siege_guard_hp");

    ArenaSiege(ArenaManager.Run r) {
        super(r);
        java.util.Arrays.fill(type, -1);
    }

    static BlockPos at(int x, int y, int z) { return CENTER.offset(x, y, z); }

    static Vec3 point(int i) { return point(PATH, i); }

    static Vec3 point(int[][] path, int i) {
        return new Vec3(CENTER.getX() + path[i][0] + 0.5, CENTER.getY(), CENTER.getZ() + path[i][1] + 0.5);
    }

    private int[][] pathFor(UUID id) { return PATHS[pathOf.getOrDefault(id, 0)]; }

    /** Wie weit ein Gegner gekommen ist (fuer die Zielwahl der Tuerme): Wegpunkt zaehlt, dann Restabstand. */
    static double progress(int wp, double distToNext) { return wp * 1000.0 - distToNext; }

    /** Laenge eines Pfades in Bloecken (fuer Tests und Anzeige). */
    static int pathLength() {
        int len = 0;
        for (int i = 1; i < PATH.length; i++) len += Math.abs(PATH[i][0] - PATH[i - 1][0]) + Math.abs(PATH[i][1] - PATH[i - 1][1]);
        return len;
    }

    /** Liegt (x, z) auf einem der Pfade (halbe Breite 2)? */
    static boolean onPath(int x, int z) {
        for (int[][] path : PATHS) if (onPath(path, x, z)) return true;
        return false;
    }

    static boolean onPath(int[][] path, int x, int z) {
        for (int i = 1; i < path.length; i++) {
            int x0 = Math.min(path[i - 1][0], path[i][0]) - 2, x1 = Math.max(path[i - 1][0], path[i][0]) + 2;
            int z0 = Math.min(path[i - 1][1], path[i][1]) - 2, z1 = Math.max(path[i - 1][1], path[i][1]) + 2;
            if (x >= x0 && x <= x1 && z >= z0 && z <= z1) return true;
        }
        return false;
    }

    /** Steht eine Position (Weltkoordinaten) auf dem Pfad? */
    static boolean onPath(Vec3 pos) {
        return onPath((int) Math.floor(pos.x) - CENTER.getX(), (int) Math.floor(pos.z) - CENTER.getZ());
    }

    /** Naechster Punkt auf dem Pfadstueck vor Wegpunkt wp (Weltkoordinaten), mit Abstand zum Rand. */
    static Vec3 clampToSegment(Vec3 pos, int wp) { return clampToSegment(PATH, pos, wp); }

    static Vec3 clampToSegment(int[][] path, Vec3 pos, int wp) {
        int i = Math.max(1, Math.min(path.length - 1, wp));
        double ax = CENTER.getX() + path[i - 1][0] + 0.5, az = CENTER.getZ() + path[i - 1][1] + 0.5;
        double bx = CENTER.getX() + path[i][0] + 0.5, bz = CENTER.getZ() + path[i][1] + 0.5;
        double x = Math.max(Math.min(ax, bx) - 1.0, Math.min(Math.max(ax, bx) + 1.0, pos.x));
        double z = Math.max(Math.min(az, bz) - 1.0, Math.min(Math.max(az, bz) + 1.0, pos.z));
        return new Vec3(x, CENTER.getY(), z);
    }

    int price(Tower t, int lvl) { return (int) Math.round(t.price[lvl] * COST_PCT / 100.0); }

    static int leakDamage(int worth) { return worth >= 10 ? 30 : worth >= 3 ? 15 : 5; }

    /** Rolle eines neuen Gegners aus Welle und Zufallswert (0..1). */
    static int rollRole(int wave, boolean special, double roll) {
        if (special) return LEADER;
        if (wave >= 3 && roll < 0.2) return RUNNER;
        if (wave >= 5 && roll < 0.35) return ARMORED;
        if (wave >= 6 && roll < 0.45) return SHADE;
        return NORMAL;
    }

    /** Wie viele Saboteure eine Welle bringt. */
    static int saboteurQuota(int wave) { return wave < 4 ? 0 : 1 + wave / 8; }

    // ------------------------------------------------------------------ Start und Karte

    @Override void start(ServerLevel lvl) {
        ArenaMaps.ensureSiege(lvl);
        r.center = CENTER;
        r.variant = 0;
        coreMax = core = CORE_HP + CORE_HP_PLAYER * r.extra;
        points = START_POINTS;
        crew.clear();
        ready.clear();
        carried.clear();
        stats.clear();
        votes.clear();
        rolesLocked = voteOpen = false;
        activeVote = SiegeCrew.CALM;
        shockCooldown = 0;
        resetPads(lvl);
        clearTraps(lvl);
        gate(lvl, false);
        labels(lvl);
    }

    @Override void started(MinecraftServer server, ServerLevel lvl) {
        r.timer = Math.max(r.timer, FIRST_BUILD_SECONDS * 20);
        for (UUID id : r.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("Burg halten: Rechtsklick auf einen Bauplatz baut Tuerme, ueber das Menue kannst du sie auch besteigen (+50 % Schaden)."
                    + " Gegner triffst du nur vom Weg aus oder mit Pfeilen vom Pfeilturm. Schleichen + Rechtsklick auf den Weg legt Fallen."
                    + " Saboteure legen Tuerme lahm, die musst du selbst erledigen. Ab Welle " + SECOND_GATE_WAVE + " oeffnet sich das Osttor.").withStyle(ChatFormatting.AQUA));
            p.sendSystemMessage(Component.literal("Waehlt eure Rollen (bis zur ersten Welle, sonst Zufall). Starke Gegner lassen Kristall-Splitter fallen: aufheben und zum Kristall tragen."
                    + " Vor jeder weiteren Welle stimmt ihr ueber die naechste ab.").withStyle(ChatFormatting.LIGHT_PURPLE));
            roleMenu(p, true);
        }
    }

    @Override Vec3 startPos(int i, int n) {
        double off = (i - (n - 1) / 2.0) * 1.6;
        return new Vec3(CENTER.getX() + 8.5 + off, CENTER.getY(), CENTER.getZ() + 19.5);
    }

    private void resetPads(ServerLevel lvl) {
        for (int i = 0; i < PADS.length; i++) {
            clearTower(lvl, i);
            type[i] = -1;
            level[i] = 0;
            spent[i] = 0;
        }
    }

    private void clearTower(ServerLevel lvl, int i) {
        int x = PADS[i][0], z = PADS[i][1];
        for (int y = 0; y <= 10; y++)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) lvl.setBlock(at(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
        for (UUID g : golems.getOrDefault(i, List.of())) {
            Entity e = lvl.getEntity(g);
            if (e != null) e.discard();
        }
        golems.remove(i);
    }

    /** Ein Block des Turms, relativ zur Platzmitte. */
    private static void put(ServerLevel lvl, int i, int dx, int y, int dz, BlockState s) {
        lvl.setBlock(at(PADS[i][0] + dx, y, PADS[i][1] + dz), s, 2);
    }

    private static void put(ServerLevel lvl, int i, int dx, int y, int dz, Block b) { put(lvl, i, dx, y, dz, b.defaultBlockState()); }

    private static boolean corner(int dx, int dz) { return dx != 0 && dz != 0; }

    private static boolean side(int dx, int dz) { return (dx == 0) != (dz == 0); }

    /** Richtung vom Platz zum naechsten Pfadstueck (fuer Kuerbis und Fenster). */
    static Direction facing(int i) {
        int x = PADS[i][0], z = PADS[i][1];
        if (onPath(x + 5, z)) return Direction.EAST;
        if (onPath(x - 5, z)) return Direction.WEST;
        if (onPath(x, z + 5)) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private void placeTower(ServerLevel lvl, int i) {
        clearTower(lvl, i);
        if (type[i] < 0) return;
        Tower t = Tower.values()[type[i]];
        int lv = level[i], h = t.height(lv);
        for (int y = 0; y < h; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean c = corner(dx, dz), s = side(dx, dz);
                Block b = switch (t) {
                    case ARROW -> c ? Blocks.STRIPPED_SPRUCE_LOG : s && y == h - 2 ? Blocks.SPRUCE_FENCE : Blocks.SPRUCE_PLANKS;
                    case FROST -> c ? (y < h - 1 ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK) : s ? (y % 2 == 0 ? Blocks.PACKED_ICE : Blocks.LIGHT_BLUE_STAINED_GLASS) : Blocks.PACKED_ICE;
                    case FLAME -> c ? (y == h - 1 ? Blocks.MAGMA_BLOCK : Blocks.RED_NETHER_BRICKS) : Blocks.NETHER_BRICKS;
                    case CANNON -> c ? Blocks.POLISHED_BLACKSTONE_BRICKS : s && y == h - 1 ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE;
                    case GOLEM -> c ? (y == h - 1 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS) : s ? (y == h - 1 ? Blocks.STONE_BRICKS : Blocks.IRON_BARS) : Blocks.IRON_BLOCK;
                    case MINE -> c ? Blocks.OAK_LOG : s ? (y == 0 ? Blocks.BARREL : Blocks.OAK_PLANKS) : Blocks.RAW_GOLD_BLOCK;
                };
                put(lvl, i, dx, y, dz, b);
            }
        }
        // Spitze: zeigt, was der Turm ist; jede Stufe macht ihn hoeher und prachtvoller.
        switch (t) {
            case ARROW -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(lvl, i, dx, h, dz, corner(dx, dz) ? Blocks.SPRUCE_FENCE : Blocks.AIR);
                put(lvl, i, 0, h, 0, Blocks.TARGET);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) put(lvl, i, dx, h + 1, dz, Blocks.SPRUCE_FENCE);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(lvl, i, dx, h + 2, dz, lv >= 3 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_SLAB);
                if (lv >= 2) put(lvl, i, 0, h + 3, 0, Blocks.LANTERN);
                if (lv >= 3) put(lvl, i, 0, h + 4, 0, Blocks.RED_BANNER);
            }
            case FROST -> {
                put(lvl, i, 0, h, 0, Blocks.BLUE_ICE);
                put(lvl, i, 0, h + 1, 0, Blocks.PACKED_ICE);
                for (int k = 0; k < lv; k++) put(lvl, i, 0, h + 2 + k, 0, k == lv - 1 ? Blocks.END_ROD : Blocks.LIGHT_BLUE_STAINED_GLASS);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (side(dx, dz)) put(lvl, i, dx, h, dz, Blocks.SNOW_BLOCK);
            }
            case FLAME -> {
                put(lvl, i, 0, h, 0, Blocks.NETHERRACK);
                put(lvl, i, 0, h + 1, 0, Blocks.FIRE);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) {
                    put(lvl, i, dx, h, dz, Blocks.NETHER_BRICK_FENCE);
                    if (lv >= 2) put(lvl, i, dx, h + 1, dz, Blocks.NETHER_BRICK_FENCE);
                    if (lv >= 3) put(lvl, i, dx, h + 2, dz, Blocks.SOUL_LANTERN);
                }
            }
            case CANNON -> {
                put(lvl, i, 0, h, 0, Blocks.CAULDRON);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) put(lvl, i, dx, h, dz, Blocks.POLISHED_BLACKSTONE_WALL);
                if (lv >= 2) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) put(lvl, i, dx, h + 1, dz, Blocks.CHAIN);
                if (lv >= 3) put(lvl, i, 0, h + 1, 0, Blocks.COAL_BLOCK);
            }
            case GOLEM -> {
                put(lvl, i, 0, h, 0, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, facing(i)));
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) put(lvl, i, dx, h, dz, Blocks.STONE_BRICK_WALL);
                if (lv >= 2) put(lvl, i, 0, h + 1, 0, Blocks.POPPY);
                if (lv >= 3) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (corner(dx, dz)) put(lvl, i, dx, h + 1, dz, Blocks.LANTERN);
            }
            case MINE -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(lvl, i, dx, h, dz, corner(dx, dz) ? Blocks.LANTERN : lv >= 3 ? Blocks.GOLD_BLOCK : Blocks.AIR);
                put(lvl, i, 0, h, 0, Blocks.GOLD_BLOCK);
                if (lv >= 2) put(lvl, i, 0, h + 1, 0, Blocks.BELL);
            }
        }
        if (t == Tower.GOLEM) spawnGolems(lvl, i);
        lvl.sendParticles(ParticleTypes.CLOUD, CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + 1.0, CENTER.getZ() + PADS[i][1] + 0.5, 20, 1.2, 0.8, 1.2, 0.02);
    }

    /** Hoehe, aus der ein Turm schiesst. */
    private int muzzle(int i) {
        if (type[i] < 0) return 1;
        return Tower.values()[type[i]].height(level[i]) + 1;
    }

    private void labels(ServerLevel lvl) {
        for (int i = 0; i < PADS.length; i++) padLabel(lvl, i);
        coreLabel(lvl);
    }

    private void padLabel(ServerLevel lvl, int i) {
        String text = type[i] < 0 ? "BAUPLATZ\nRechtsklick" : Tower.values()[type[i]].title + " " + ROMAN[level[i]];
        double y = type[i] < 0 ? 2.2 : muzzle(i) + 4.0;
        padLabels[i] = ArenaLabels.text(lvl, padLabels[i], "chronicle_siege", CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + y,
                CENTER.getZ() + PADS[i][1] + 0.5, text, type[i] < 0 ? "gray" : "gold", 120);
    }

    private void coreLabel(ServerLevel lvl) {
        coreLabel = ArenaLabels.text(lvl, coreLabel, "chronicle_siege", CENTER.getX() + CORE[0] + 0.5, CENTER.getY() + 6.2, CENTER.getZ() + CORE[1] + 0.5,
                "KRISTALL\n" + Math.max(0, core) + " / " + coreMax, core * 3 <= coreMax ? "red" : "aqua", 120);
    }

    // ------------------------------------------------------------------ Gegner

    @Override Boolean objectiveNow() { return Boolean.FALSE; }

    @Override boolean controlsAi() { return true; }

    @Override boolean routes() { return false; }

    @Override boolean shooters() { return false; }

    @Override int pauseTicks(boolean offer) { return offer ? 20 * 20 : PAUSE_SECONDS * 20; }

    @Override Vec3 spawnPos(ServerLevel lvl, ArenaManager.Spawn s) {
        nextPath = gate2Open && lvl.getRandom().nextDouble() < 0.4 ? 1 : 0;
        if (nextPath == 1) {
            Vec3 gate = point(PATH2, 0);
            return gate.add((lvl.getRandom().nextDouble() - 0.5) * 2.0, 0, (lvl.getRandom().nextDouble() - 0.5) * 3.0);
        }
        Vec3 gate = point(0);
        return gate.add((lvl.getRandom().nextDouble() - 0.5) * 3.0, 0, (lvl.getRandom().nextDouble() - 0.5) * 2.0);
    }

    @Override void spawned(LivingEntity le, ArenaManager.Spawn s) {
        UUID id = le.getUUID();
        waypoint.put(id, 1);
        pathOf.put(id, nextPath);
        nextPath = 0;
        worth.put(id, s.mini || s.boss ? 10 : s.elite ? 3 : 1);
        int ro = rollRole(r.wave, s.mini || s.boss, le.getRandom().nextDouble());
        int quota = saboteurQuota(r.wave);
        if (ro == NORMAL && saboteurs < quota && (le.getRandom().nextDouble() < 0.1 || r.queue.size() < quota - saboteurs)) {
            ro = SABOTEUR;
            saboteurs++;
        }
        role.put(id, ro);
        double hp = HEALTH_FACTOR * switch (ro) {
            case RUNNER -> 0.6;
            case ARMORED -> 1.3;
            case SABOTEUR -> 1.5;
            default -> 1.0;
        } * (activeVote == SiegeCrew.IRON ? 1.4 : 1.0);
        double speed = SPEED_BONUS + (ro == RUNNER ? 0.5 : ro == ARMORED ? -0.15 : 0) + (activeVote == SiegeCrew.STORM ? 0.3 : 0);
        modify(le, Attributes.MAX_HEALTH, "siege_health", hp - 1.0);
        modify(le, Attributes.MOVEMENT_SPEED, "siege_speed", speed);
        modify(le, Attributes.KNOCKBACK_RESISTANCE, "siege_knockback", 1.0);
        le.setHealth(le.getMaxHealth());
        if (ro == SABOTEUR || !le.hasCustomName() && (ro == RUNNER || ro == ARMORED || ro == SHADE)) {
            String name = switch (ro) {
                case RUNNER -> "Laeufer";
                case ARMORED -> "Gepanzert";
                case SHADE -> "Schemen";
                default -> "Saboteur";
            };
            ChatFormatting color = switch (ro) {
                case RUNNER -> ChatFormatting.YELLOW;
                case ARMORED -> ChatFormatting.GRAY;
                case SHADE -> ChatFormatting.DARK_AQUA;
                default -> ChatFormatting.RED;
            };
            le.setCustomName(Component.literal(name).withStyle(color));
            le.setCustomNameVisible(true);
        }
        if (ro == SABOTEUR) le.setGlowingTag(true);
        if (ro == ARMORED && le instanceof Mob m) {
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setDropChance(EquipmentSlot.CHEST, 0);
            m.setDropChance(EquipmentSlot.HEAD, 0);
        }
    }

    private static void modify(LivingEntity le, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String name, double amount) {
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null || amount == 0) return;
        inst.removeModifier(ChronicleArena.id(name));
        AttributeModifier.Operation op = attr == Attributes.KNOCKBACK_RESISTANCE ? AttributeModifier.Operation.ADD_VALUE : AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        inst.addPermanentModifier(new AttributeModifier(ChronicleArena.id(name), amount, op));
    }

    @Override boolean allowTarget(LivingEntity mob, LivingEntity target) {
        if (target == null || target instanceof IronGolem) return true;
        return target instanceof ServerPlayer && onPath(target.position()) && mob.distanceToSqr(target) < 6.0 * 6.0;
    }

    /** Spieler treffen Gegner nur, wenn sie selbst auf dem Weg stehen, oder mit Fernkampf von einem besetzten Pfeilturm. */
    @Override float playerHit(ServerPlayer attacker, LivingEntity mob, float amount, boolean ranged) {
        if (onPath(attacker.position())) return crew.getOrDefault(attacker.getUUID(), -1) == SiegeCrew.GUARD ? (float) (amount * SiegeCrew.GUARD_PATH_BONUS) : amount;
        int on = mannedBy(attacker);
        if (ranged && on >= 0 && type[on] == Tower.ARROW.ordinal()) return (float) (amount * ARCHER_BONUS);
        long now = attacker.level().getGameTime();
        Long last = hint.get(attacker.getUUID());
        if (last == null || now - last > 60) {
            hint.put(attacker.getUUID(), now);
            attacker.displayClientMessage(Component.literal("Nur vom Weg aus oder mit Pfeilen vom Pfeilturm!").withStyle(ChatFormatting.GOLD), true);
        }
        return 0;
    }

    @Override void afterBuildWave(ServerLevel lvl) {
        // Mehr Gegner als im Wellen-Modus, dafuer schwaecher (siehe spawned).
        lockRoles(lvl.getServer());
        resolveVote(lvl.getServer());
        List<ArenaManager.Spawn> normal = new ArrayList<>();
        for (ArenaManager.Spawn s : r.queue) if (!s.elite && !s.boss && !s.mini) normal.add(s);
        int extra = (int) Math.round(normal.size() * (COUNT_FACTOR * (activeVote == SiegeCrew.HORDE ? 1.5 : 1.0) - 1.0));
        for (int k = 0; k < extra && !normal.isEmpty(); k++) {
            ArenaManager.Spawn copy = new ArenaManager.Spawn(normal.get(k % normal.size()).entity, false, false);
            r.queue.add(lvl.getRandom().nextInt(r.queue.size() + 1), copy);
        }
        // Gefallene Golems kommen zu jeder Welle zurueck.
        for (int i = 0; i < PADS.length; i++) if (type[i] == Tower.GOLEM.ordinal()) spawnGolems(lvl, i);
        saboteurs = 0;
        jam.clear();
        if (r.wave >= SECOND_GATE_WAVE && !gate2Open) {
            gate(lvl, true);
            for (UUID id : r.alive) {
                ServerPlayer p = lvl.getServer().getPlayerList().getPlayer(id);
                if (p == null) continue;
                p.sendSystemMessage(Component.literal("Das Osttor ist offen! Ab jetzt kommen Gegner von zwei Seiten.").withStyle(ChatFormatting.RED));
                ArenaShow.title(List.of(p), ArenaShow.big("OSTTOR OFFEN", ChatFormatting.RED), Component.literal("Gegner von zwei Seiten").withStyle(ChatFormatting.GRAY), 5, 50, 10);
            }
        }
    }

    /** Fallgitter des Osttors: geschlossen bis Welle SECOND_GATE_WAVE. */
    void gate(ServerLevel lvl, boolean open) {
        gate2Open = open;
        for (int dz = -2; dz <= 2; dz++) for (int y = 0; y <= 4; y++) {
            boolean bars = !open || y >= 3;
            lvl.setBlock(at(HALF + 1, y, GATE2_Z + dz), bars ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    @Override void tickFight(MinecraftServer server, ServerLevel lvl, int tick) {
        now = lvl.getGameTime();
        if (tick % 5 == 0) {
            keepOnPath(lvl);
            leaks(server, lvl);
        }
        if (tick % 10 == 0) steer(server, lvl);
        if (tick % 5 == 0) shards(server, lvl);
        if (tick % 10 == 0) rains(lvl);
        if (tick % 40 == 0) ensureCrew(server);
        if (tick % 100 == 0) supplyAura(server, lvl);
        if (shockCooldown > 0) shockCooldown--;
        towers(lvl, tick);
        if (tick % 10 == 0) traps(lvl);
        if (tick % 40 == 0) leash(lvl);
        if (tick % 20 == 0) {
            coreLabel(lvl);
            double cx = CENTER.getX() + CORE[0] + 0.5, cz = CENTER.getZ() + CORE[1] + 0.5;
            lvl.sendParticles(ParticleTypes.END_ROD, cx, CENTER.getY() + 4.0, cz, 6, 0.3, 0.6, 0.3, 0.01);
            Vec3 gate = point(0);
            lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, gate.x, gate.y + 1.0, gate.z - 3.0, 8, 1.5, 1.0, 0.3, 0.01);
            if (gate2Open) {
                Vec3 g2 = point(PATH2, 0);
                lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, g2.x + 2.0, g2.y + 1.0, g2.z, 8, 0.3, 1.0, 1.5, 0.01);
            }
            for (var en : jam.entrySet()) {
                int i = en.getValue();
                lvl.sendParticles(ParticleTypes.LARGE_SMOKE, CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + muzzle(i), CENTER.getZ() + PADS[i][1] + 0.5, 6, 0.6, 0.4, 0.6, 0.01);
            }
            for (int i = 0; i < PADS.length; i++) if (manned(lvl, i) != null)
                lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + muzzle(i) + 1.5, CENTER.getZ() + PADS[i][1] + 0.5, 4, 0.8, 0.3, 0.8, 0);
        }
    }

    @Override boolean holdPrep(MinecraftServer server, ServerLevel lvl, int tick) {
        now = lvl.getGameTime();
        if (shockCooldown > 0) shockCooldown--;
        if (startNow) {
            startNow = false;
            r.timer = Math.min(r.timer, 60);
        }
        if (tick % 20 == 0) coreLabel(lvl);
        if (tick % 5 == 0) shards(lvl.getServer(), lvl);
        if (tick % 40 == 0) ensureCrew(server);
        return false;
    }

    /** Gegner, die vom Weg abkommen (Rueckstoss, Fliegen, Schubsen), werden sofort zurueckgesetzt. */
    private void keepOnPath(ServerLevel lvl) {
        for (UUID id : r.mobs) {
            if (!(lvl.getEntity(id) instanceof LivingEntity m) || !m.isAlive()) continue;
            boolean high = m.getY() > CENTER.getY() + 2.5;
            if (onPath(m.position()) && !high) continue;
            Vec3 back = clampToSegment(pathFor(id), m.position(), waypoint.getOrDefault(id, 1));
            m.teleportTo(back.x, back.y, back.z);
            m.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Laufwege: Gegner folgen dem Pfad; nur wer einem Spieler auf dem Weg sehr nahe kommt, greift ihn an. */
    private void steer(MinecraftServer server, ServerLevel lvl) {
        for (UUID id : r.mobs) {
            if (!(lvl.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            int[][] path = pathFor(id);
            int wp = waypoint.getOrDefault(id, 1);
            Vec3 target = point(path, wp);
            if (m.position().subtract(target).horizontalDistanceSqr() < 1.8 * 1.8 && wp < path.length - 1) {
                wp++;
                waypoint.put(id, wp);
                target = point(path, wp);
            }
            LivingEntity cur = m.getTarget();
            if (cur instanceof IronGolem g && g.isAlive() && m.distanceToSqr(g) < 4.0 * 4.0) continue;
            ServerPlayer near = null;
            for (UUID pid : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null && p.level() == lvl && !p.isSpectator() && onPath(p.position()) && m.distanceToSqr(p) < 4.5 * 4.5) { near = p; break; }
            }
            if (near != null) {
                if (cur != near) m.setTarget(near);
                continue;
            }
            if (cur != null) m.setTarget(null);
            // Saboteur: bleibt am naechsten Turm stehen und legt ihn lahm.
            if (role.getOrDefault(id, NORMAL) == SABOTEUR) {
                Integer j = jam.get(id);
                if (j == null || type[j] < 0) {
                    j = nearestTower(m.position(), 7.0);
                    if (j != null) {
                        jam.put(id, j);
                        Tower t = Tower.values()[type[j]];
                        for (UUID pid : r.alive) {
                            ServerPlayer p = server.getPlayerList().getPlayer(pid);
                            if (p != null) p.displayClientMessage(Component.literal("Ein Saboteur legt " + t.title + " (Platz " + (j + 1) + ") lahm! Nur ihr koennt ihn stoppen.")
                                    .withStyle(ChatFormatting.RED), true);
                        }
                    } else jam.remove(id);
                }
                if (j != null) {
                    m.getNavigation().stop();
                    stuck.remove(id);
                    continue;
                }
            }
            m.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            // Haengt ein Gegner 5 Sekunden fest, wird er auf den naechsten Wegpunkt gesetzt.
            double[] st = stuck.computeIfAbsent(id, k -> new double[]{m.getX(), m.getZ(), 0});
            if (Math.abs(m.getX() - st[0]) + Math.abs(m.getZ() - st[1]) > 1.0) {
                st[0] = m.getX();
                st[1] = m.getZ();
                st[2] = 0;
            } else if (++st[2] >= 10) {
                m.teleportTo(target.x, target.y, target.z);
                st[0] = target.x;
                st[1] = target.z;
                st[2] = 0;
            }
        }
    }

    private void leaks(MinecraftServer server, ServerLevel lvl) {
        for (UUID id : new ArrayList<>(r.mobs)) {
            int[][] path = pathFor(id);
            Vec3 end = point(path, path.length - 1);
            if (waypoint.getOrDefault(id, 0) < path.length - 1) continue;
            if (!(lvl.getEntity(id) instanceof LivingEntity m) || !m.isAlive()) continue;
            if (m.position().subtract(end).horizontalDistanceSqr() > 2.2 * 2.2) continue;
            int dmg = (int) Math.round(leakDamage(worth.getOrDefault(id, 1)) * LEAK_PCT / 100.0);
            core -= dmg;
            leaked.add(id);
            m.discard();
            lvl.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
            lvl.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y + 1, end.z, 2, 0.4, 0.4, 0.4, 0);
            for (UUID pid : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null) p.displayClientMessage(Component.literal("Ein Gegner hat den Kristall erreicht! -" + dmg + " Leben")
                        .withStyle(ChatFormatting.RED), true);
            }
            coreLabel(lvl);
        }
    }

    double towerScale() {
        return r.adminHealth * r.healthScale * (1.0 + 0.25 * r.extra) * r.growth(0.04, 0.06) * POWER_PCT / 100.0;
    }

    private void hit(ServerLevel lvl, LivingEntity m, Tower t, float dmg, net.minecraft.world.damagesource.DamageSource src) {
        double f = t.vs(role.getOrDefault(m.getUUID(), NORMAL));
        if (f <= 0) return;
        m.invulnerableTime = 0;
        m.hurt(src, (float) (dmg * f));
    }

    /** Naechster Turm (nicht Goldmine) innerhalb von max Bloecken, sonst null. */
    private Integer nearestTower(Vec3 pos, double max) {
        Integer best = null;
        double bestD = max * max;
        for (int i = 0; i < PADS.length; i++) {
            if (type[i] < 0 || type[i] == Tower.MINE.ordinal()) continue;
            double dx = pos.x - (CENTER.getX() + PADS[i][0] + 0.5), dz = pos.z - (CENTER.getZ() + PADS[i][1] + 0.5);
            double d = dx * dx + dz * dz;
            if (d <= bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Wird der Turm gerade von einem lebenden Saboteur lahmgelegt? */
    boolean jammed(ServerLevel lvl, int i) {
        for (var en : jam.entrySet()) {
            if (en.getValue() != i) continue;
            if (lvl == null || lvl.getEntity(en.getKey()) instanceof LivingEntity m && m.isAlive()) return true;
        }
        return false;
    }

    /** Hoehe, auf der man oben auf dem Turm steht (Seite zum Weg). */
    private int standY(ServerLevel lvl, int i) {
        Direction f = facing(i);
        int x = PADS[i][0] + f.getStepX(), z = PADS[i][1] + f.getStepZ();
        int y = Tower.values()[type[i]].height(level[i]);
        while (y < 12 && !lvl.getBlockState(at(x, y, z)).isAir()) y++;
        return y;
    }

    /** Spieler, der oben auf dem Turm steht, sonst null. */
    ServerPlayer manned(ServerLevel lvl, int i) {
        if (type[i] < 0 || !Tower.values()[type[i]].mannable()) return null;
        double cx = CENTER.getX() + PADS[i][0] + 0.5, cz = CENTER.getZ() + PADS[i][1] + 0.5;
        double minY = CENTER.getY() + Tower.values()[type[i]].height(level[i]) - 0.5;
        for (UUID id : r.alive) {
            ServerPlayer p = lvl.getServer().getPlayerList().getPlayer(id);
            if (p != null && p.level() == lvl && !p.isSpectator() && Math.abs(p.getX() - cx) <= 1.7 && Math.abs(p.getZ() - cz) <= 1.7 && p.getY() >= minY) return p;
        }
        return null;
    }

    /** Auf welchem Turm steht der Spieler? -1 = keinem. */
    int mannedBy(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        for (int i = 0; i < PADS.length; i++) if (manned(lvl, i) == p) return i;
        return -1;
    }

    // ------------------------------------------------------------------ Fallen

    private void traps(ServerLevel lvl) {
        for (Trap tr : new ArrayList<>(traps)) {
            double cx = CENTER.getX() + tr.x + 0.5, cz = CENTER.getZ() + tr.z + 0.5;
            List<LivingEntity> on = new ArrayList<>();
            for (UUID id : r.mobs) {
                if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && Math.abs(m.getX() - cx) <= 1.6 && Math.abs(m.getZ() - cz) <= 1.6) on.add(m);
            }
            if (on.isEmpty()) continue;
            switch (tr.type) {
                case SPIKES -> {
                    for (LivingEntity m : on) {
                        if (tr.charges <= 0) break;
                        m.invulnerableTime = 0;
                        m.hurt(lvl.damageSources().cactus(), (float) (3.0 * towerScale()));
                        tr.charges--;
                    }
                    lvl.sendParticles(ParticleTypes.CRIT, cx, CENTER.getY() + 0.3, cz, 10, 1.0, 0.1, 1.0, 0.05);
                }
                case TAR -> {
                    for (LivingEntity m : on) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, true));
                    lvl.sendParticles(ParticleTypes.SQUID_INK, cx, CENTER.getY() + 0.2, cz, 4, 1.0, 0.05, 1.0, 0.01);
                }
                case BOMB -> {
                    for (UUID id : r.mobs) {
                        if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && m.position().distanceToSqr(cx, CENTER.getY(), cz) <= 3.5 * 3.5) {
                            m.invulnerableTime = 0;
                            m.hurt(lvl.damageSources().explosion(null, null), (float) (25.0 * towerScale()));
                        }
                    }
                    lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, CENTER.getY() + 0.5, cz, 1, 0, 0, 0, 0);
                    lvl.playSound(null, cx, CENTER.getY(), cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    tr.charges = 0;
                }
            }
            if (tr.charges <= 0) removeTrap(lvl, tr);
        }
    }

    Trap trapAt(int x, int z) {
        for (Trap t : traps) if (t.covers(x, z)) return t;
        return null;
    }

    /** Darf hier eine Falle (3x3) liegen? Ganz auf dem Weg, nicht am Tor, nicht ueber einer anderen Falle. */
    boolean trapFits(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!onPath(x + dx, z + dz)) return false;
        for (Trap t : traps) if (Math.abs(t.x - x) <= 2 && Math.abs(t.z - z) <= 2) return false;
        for (int[][] path : PATHS) if (Math.abs(x - path[0][0]) <= 3 && Math.abs(z - path[0][1]) <= 3) return false;
        return true;
    }

    private void placeTrap(ServerLevel lvl, Trap t) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean show = t.type != TrapType.BOMB || dx == 0 && dz == 0;
            lvl.setBlock(at(t.x + dx, 0, t.z + dz), show ? t.type.carpet().defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void removeTrap(ServerLevel lvl, Trap t) {
        traps.remove(t);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) lvl.setBlock(at(t.x + dx, 0, t.z + dz), Blocks.AIR.defaultBlockState(), 2);
    }

    private void clearTraps(ServerLevel lvl) {
        for (Trap t : new ArrayList<>(traps)) removeTrap(lvl, t);
    }

    private void trapMenu(ServerPlayer p, int x, int z, boolean popup) {
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        Trap here = trapAt(x, z);
        String title;
        if (here != null) {
            title = here.type.title;
            String left = here.type == TrapType.SPIKES ? here.charges + " Treffer uebrig." : here.type == TrapType.TAR ? "Noch " + here.charges + " Wellen." : "Wartet auf den ersten Gegner.";
            opts.add(new ArenaNet.MenuOption(11, "Falle entfernen", left + " Keine Punkte zurueck.", "minecraft:barrier", -1, 0, -1));
        } else {
            title = "Falle legen";
            boolean fits = trapFits(x, z), room = traps.size() < MAX_TRAPS;
            for (TrapType t : TrapType.values()) {
                int price = cost(p, t.price);
                opts.add(new ArenaNet.MenuOption(t.ordinal(), t.title, t.desc, t.icon, price, fits && room && points >= price ? 0 : 1, -1));
            }
            if (!fits) title += " (hier kein Platz)";
            else if (!room) title += " (hoechstens " + MAX_TRAPS + ")";
        }
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_trap_" + x + "_" + z, title, "3x3 Felder auf dem Weg. Fallen liegen auch fuer euch, tun euch aber nichts.", opts, points, 0, popup));
    }

    private LivingEntity furthest(List<LivingEntity> list) {
        LivingEntity best = null;
        double bestP = -1e9;
        for (LivingEntity m : list) {
            int[][] path = pathFor(m.getUUID());
            int wp = waypoint.getOrDefault(m.getUUID(), 1);
            double pr = progress(wp + (PATH.length - path.length), Math.sqrt(m.position().subtract(point(path, Math.min(wp, path.length - 1))).horizontalDistanceSqr()));
            if (pr > bestP) { bestP = pr; best = m; }
        }
        return best;
    }

    private void towers(ServerLevel lvl, int tick) {
        for (int i = 0; i < PADS.length; i++) {
            if (type[i] < 0) continue;
            Tower t = Tower.values()[type[i]];
            if (t == Tower.GOLEM || t == Tower.MINE) continue;
            if (cooldown[i] > 0) { cooldown[i]--; continue; }
            if (jammed(lvl, i)) continue;
            int lv = level[i];
            Vec3 pos = new Vec3(CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + muzzle(i) + 0.5, CENTER.getZ() + PADS[i][1] + 0.5);
            double range = t.range(lv);
            List<LivingEntity> inRange = new ArrayList<>();
            for (UUID id : r.mobs) {
                if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && t.vs(role.getOrDefault(id, NORMAL)) > 0
                        && m.position().subtract(pos).horizontalDistanceSqr() <= range * range) inRange.add(m);
            }
            if (inRange.isEmpty()) continue;
            cooldown[i] = overload[i] > lvl.getGameTime() ? Math.max(1, t.interval(lv) / 2) : t.interval(lv);
            float dmg = (float) (t.damage(lv) * towerScale() * (manned(lvl, i) != null ? MANNED_BONUS : 1.0));
            switch (t) {
                case ARROW -> {
                    LivingEntity best = furthest(inRange);
                    if (best == null) break;
                    beam(lvl, ParticleTypes.CRIT, pos, best.position().add(0, best.getBbHeight() * 0.6, 0));
                    hit(lvl, best, t, dmg, lvl.damageSources().magic());
                    lvl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.8F, 1.2F);
                }
                case FROST -> {
                    for (LivingEntity m : inRange) {
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, lv, false, true));
                        if (tick % 20 == 0) hit(lvl, m, t, dmg, lvl.damageSources().freeze());
                    }
                    lvl.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y - 1.0, pos.z, 16, range / 3, 0.6, range / 3, 0.02);
                }
                case FLAME -> {
                    for (LivingEntity m : inRange) {
                        hit(lvl, m, t, dmg, lvl.damageSources().inFire());
                        m.igniteForSeconds(3);
                        beam(lvl, ParticleTypes.FLAME, pos, m.position().add(0, m.getBbHeight() * 0.5, 0));
                    }
                    lvl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.8F);
                }
                case CANNON -> {
                    LivingEntity best = furthest(inRange);
                    if (best == null) break;
                    Vec3 c = best.position();
                    beam(lvl, ParticleTypes.SMOKE, pos, c.add(0, 1.0, 0));
                    double splash = 2.0 + 0.5 * lv;
                    for (UUID id : r.mobs) {
                        if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && m.position().distanceToSqr(c) <= splash * splash)
                            hit(lvl, m, t, dmg, lvl.damageSources().explosion(null, null));
                    }
                    lvl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, splash / 2, 0.3, splash / 2, 0);
                    lvl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.3F);
                }
                default -> {}
            }
        }
    }

    private static void beam(ServerLevel lvl, net.minecraft.core.particles.SimpleParticleType particle, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 2));
        for (int k = 0; k <= steps; k++) {
            Vec3 p = from.add(d.scale(k / (double) steps));
            lvl.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void spawnGolems(ServerLevel lvl, int i) {
        List<UUID> list = golems.computeIfAbsent(i, k -> new ArrayList<>());
        list.removeIf(id -> !(lvl.getEntity(id) instanceof IronGolem g) || !g.isAlive());
        int want = level[i] >= 3 ? 2 : 1;
        Direction f = facing(i);
        while (list.size() < want) {
            IronGolem g = EntityType.IRON_GOLEM.create(lvl);
            if (g == null) return;
            g.setPlayerCreated(true);
            g.setPersistenceRequired();
            g.addTag(ArenaManager.GOLEM_TAG);
            // Golems stehen zwischen Kaserne und Weg.
            double side = list.isEmpty() ? 1.0 : -1.0;
            double gx = CENTER.getX() + PADS[i][0] + 0.5 + f.getStepX() * 2.5 + (f.getStepX() == 0 ? side : 0);
            double gz = CENTER.getZ() + PADS[i][1] + 0.5 + f.getStepZ() * 2.5 + (f.getStepZ() == 0 ? side : 0);
            g.moveTo(gx, CENTER.getY(), gz, 0, 0);
            double s = Math.min(3.0, r.growth(0.04, 0.06)) * (level[i] >= 2 ? 1.5 : 1.0);
            scale(g, Attributes.MAX_HEALTH, s);
            scale(g, Attributes.ATTACK_DAMAGE, s);
            g.setHealth(g.getMaxHealth());
            if (!lvl.addFreshEntity(g)) return;
            list.add(g.getUUID());
        }
    }

    private static void scale(LivingEntity le, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double f) {
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null || f == 1.0) return;
        inst.removeModifier(ChronicleArena.id("siege_golem"));
        inst.addPermanentModifier(new AttributeModifier(ChronicleArena.id("siege_golem"), f - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Golems bleiben in der Naehe ihrer Kaserne. */
    private void leash(ServerLevel lvl) {
        for (var en : golems.entrySet()) {
            int i = en.getKey();
            Direction f = facing(i);
            Vec3 home = new Vec3(CENTER.getX() + PADS[i][0] + 0.5 + f.getStepX() * 2.5, CENTER.getY(), CENTER.getZ() + PADS[i][1] + 0.5 + f.getStepZ() * 2.5);
            for (UUID id : en.getValue()) {
                if (lvl.getEntity(id) instanceof IronGolem g && g.isAlive() && g.position().distanceToSqr(home) > 9 * 9) {
                    g.setTarget(null);
                    g.teleportTo(home.x, home.y, home.z);
                }
            }
        }
    }

    @Override void mobDied(LivingEntity dead, ServerPlayer killer) {
        UUID id = dead.getUUID();
        waypoint.remove(id);
        stuck.remove(id);
        int ro = role.getOrDefault(id, NORMAL);
        role.remove(id);
        pathOf.remove(id);
        jam.remove(id);
        int w = worth.getOrDefault(id, 1);
        worth.remove(id);
        if (leaked.remove(id)) return;
        points += SiegeCrew.withBonus((int) Math.round(10 * w * KILL_PCT / 100.0), activeVote);
        if (killer != null) {
            stat(killer, SiegeCrew.KILLS);
            if (ro == SABOTEUR) stat(killer, SiegeCrew.SABOTEURS);
        }
        if (dead.level() instanceof ServerLevel lvl && (w >= 3 || ro == LEADER || ro == ARMORED && dead.getRandom().nextDouble() < 0.35)) dropShard(lvl, dead.position());
    }

    @Override boolean lost() { return core <= 0; }

    /** Punkte aller Goldminen nach einer Welle. */
    int mineIncome() {
        int sum = 0;
        for (int i = 0; i < PADS.length; i++) if (type[i] == Tower.MINE.ordinal()) sum += Tower.income(level[i]);
        return sum;
    }

    @Override boolean waveCleared(MinecraftServer server, ServerLevel lvl) {
        int bonus = SiegeCrew.withBonus((int) Math.round((50 + 10 * r.wave) * KILL_PCT / 100.0), activeVote);
        int mines = mineIncome();
        points += bonus + mines;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal("Welle gehalten: +" + bonus + " Punkte" + (mines > 0 ? ", Goldminen +" + mines : "")
                    + " (Kasse " + points + "). Bauphase!").withStyle(ChatFormatting.GREEN));
        }
        waypoint.clear();
        stuck.clear();
        worth.clear();
        role.clear();
        pathOf.clear();
        jam.clear();
        leaked.clear();
        for (Trap t : new ArrayList<>(traps)) if (t.type == TrapType.TAR && --t.charges <= 0) removeTrap(lvl, t);
        activeVote = SiegeCrew.CALM;
        openVote(server);
        return false;
    }

    // ------------------------------------------------------------------ Bauen

    int padAt(BlockPos pos) {
        for (int i = 0; i < PADS.length; i++) {
            BlockPos c = at(PADS[i][0], 0, PADS[i][1]);
            if (Math.abs(pos.getX() - c.getX()) <= 1 && Math.abs(pos.getZ() - c.getZ()) <= 1 && pos.getY() >= c.getY() - 1 && pos.getY() <= c.getY() + 10) return i;
        }
        return -1;
    }

    static boolean isCore(BlockPos pos) {
        BlockPos c = at(CORE[0], 0, CORE[1]);
        return Math.abs(pos.getX() - c.getX()) <= 2 && Math.abs(pos.getZ() - c.getZ()) <= 2 && pos.getY() >= c.getY() - 1 && pos.getY() <= c.getY() + 5;
    }

    @Override boolean interact(ServerPlayer p, BlockPos pos) {
        int pad = padAt(pos);
        if (pad >= 0) { padMenu(p, pad, true); return true; }
        if (isCore(pos)) { coreMenu(p, true); return true; }
        int x = pos.getX() - CENTER.getX(), z = pos.getZ() - CENTER.getZ(), y = pos.getY() - CENTER.getY();
        if (p.isShiftKeyDown() && (y == -1 || y == 0) && onPath(x, z)) {
            Trap t = trapAt(x, z);
            if (t != null) trapMenu(p, t.x, t.z, true);
            else trapMenu(p, x, z, true);
            return true;
        }
        return false;
    }

    private boolean canStart() { return r.state == ArenaManager.State.PREP && r.offer.isEmpty(); }

    private void padMenu(ServerPlayer p, int i, boolean popup) {
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        if (type[i] < 0) {
            for (Tower t : Tower.values()) {
                int price = cost(p, price(t, 0));
                String extra = t == Tower.MINE ? " +" + Tower.income(1) + " Punkte pro Welle." : t == Tower.GOLEM ? "" : " Reichweite " + (int) t.range(1) + ".";
                opts.add(new ArenaNet.MenuOption(t.ordinal(), t.title, t.desc + extra, t.icon, price, points >= price ? 0 : 1, -1));
            }
        } else {
            Tower t = Tower.values()[type[i]];
            if (level[i] < 3) {
                int price = cost(p, price(t, level[i]));
                opts.add(new ArenaNet.MenuOption(10, "Ausbauen auf Stufe " + ROMAN[level[i] + 1], upgradeText(t, level[i] + 1), t.icon, price, points >= price ? 0 : 1, -1));
            } else {
                opts.add(new ArenaNet.MenuOption(10, "Hoechste Stufe", "Dieser Turm ist voll ausgebaut.", t.icon, -1, 1, -1));
            }
            opts.add(new ArenaNet.MenuOption(11, "Abreissen", "Gibt die Haelfte der investierten Punkte zurueck (" + spent[i] / 2 + ").", "minecraft:barrier", -1, 0, -1));
            if (t.mannable()) {
                ServerPlayer on = manned(p.serverLevel(), i);
                if (on == p) opts.add(new ArenaNet.MenuOption(22, "Herunterklettern", "Zurueck auf den Boden hinter dem Turm.", "minecraft:ladder", -1, 0, -1));
                else opts.add(new ArenaNet.MenuOption(21, "Turm besteigen", "Oben macht der Turm 50 % mehr Schaden."
                        + (t == Tower.ARROW ? " Vom Pfeilturm aus treffen auch deine Pfeile (+25 %)." : ""), "minecraft:ladder", -1, on == null ? 0 : 1, -1));
            }
        }
        if (jammed(p.serverLevel(), i)) opts.add(0, new ArenaNet.MenuOption(30, "Lahmgelegt!", "Ein Saboteur steht am Turm. Erledigt ihn, dann feuert der Turm wieder.", "minecraft:redstone", -1, 1, -1));
        if (canStart()) opts.add(new ArenaNet.MenuOption(20, "Welle jetzt starten", "Bauphase beenden, die naechste Welle kommt in 3 Sekunden.", "minecraft:bell", -1, 0, -1));
        String title = type[i] < 0 ? "Bauplatz " + (i + 1) : Tower.values()[type[i]].title + " " + ROMAN[level[i]];
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_pad_" + i, title, "Gemeinsame Kasse der Gruppe. Bauen geht jederzeit, auch waehrend der Welle.", opts, points, 0, popup));
    }

    private static String upgradeText(Tower t, int lv) {
        return switch (t) {
            case ARROW -> "Mehr Schaden, schnellere Schuesse, Reichweite " + (int) t.range(lv) + ".";
            case FROST -> "Staerkere Verlangsamung, mehr Schaden, Reichweite " + (int) t.range(lv) + ".";
            case FLAME -> "Mehr Schaden, oefter, Reichweite " + (int) t.range(lv) + ".";
            case CANNON -> "Mehr Schaden, groessere Explosion, Reichweite " + (int) t.range(lv) + ".";
            case GOLEM -> lv >= 3 ? "Ein zweiter Golem kommt dazu." : "Golem mit 50 % mehr Leben und Schaden.";
            case MINE -> "+" + Tower.income(lv) + " Punkte pro Welle statt " + Tower.income(lv - 1) + ".";
        };
    }

    private void coreMenu(ServerPlayer p, boolean popup) {
        int price = (int) Math.round(150 * COST_PCT / 100.0);
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        opts.add(new ArenaNet.MenuOption(0, "Kristall reparieren", "+" + repair(p) + " Leben (hoechstens bis zum Maximum).", "minecraft:amethyst_shard", price,
                points >= price && core < coreMax ? 0 : 1, -1));
        boolean fight = r.state != ArenaManager.State.PREP;
        String shock = shockCooldown > 0 ? "Laedt noch " + (shockCooldown + 19) / 20 + " s." : "Bereit.";
        opts.add(new ArenaNet.MenuOption(2, "Schockwelle", "Trifft alle Gegner bis 12 Bloecke um den Kristall hart und friert sie kurz ein. Kostenlos, alle 60 s. " + shock,
                "minecraft:echo_shard", -1, fight && shockCooldown <= 0 ? 0 : 1, -1));
        opts.add(new ArenaNet.MenuOption(1, "Welle sofort starten", "Bauphase beenden, die naechste Welle kommt in 3 Sekunden.", "minecraft:bell", -1, canStart() ? 0 : 1, -1));
        if (!rolesLocked) opts.add(new ArenaNet.MenuOption(3, "Rolle waehlen", "Baumeister, Waechter, Magier oder Versorger. Geht bis zur ersten Welle.", "minecraft:armor_stand", -1, 0, -1));
        if (voteOpen) opts.add(new ArenaNet.MenuOption(4, "Naechste Welle abstimmen", "Mehr Risiko, mehr Punkte. Die Mehrheit entscheidet.", "minecraft:paper", -1, 0, -1));
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_core", "Kristall " + Math.max(0, core) + " / " + coreMax, "Faellt der Kristall, ist der Kampf verloren.", opts, points, 0, popup));
    }

    /** Schockwelle am Kristall: Gruppenfaehigkeit mit Abklingzeit. */
    private void shock(ServerPlayer p, ServerLevel lvl) {
        if (shockCooldown > 0 || r.state == ArenaManager.State.PREP) return;
        shockCooldown = SHOCK_COOLDOWN;
        Vec3 c = new Vec3(CENTER.getX() + CORE[0] + 0.5, CENTER.getY() + 1, CENTER.getZ() + CORE[1] + 0.5);
        float dmg = (float) (12 * towerScale());
        for (UUID id : r.mobs) {
            if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && m.position().distanceToSqr(c) <= 12 * 12) {
                m.invulnerableTime = 0;
                m.hurt(lvl.damageSources().magic(), dmg);
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6, false, true));
            }
        }
        lvl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        lvl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 6, 0.4, 6, 0.05);
        lvl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.0F, 1.2F);
        say(p, p.getGameProfile().getName() + " loest die Schockwelle aus.");
    }

    @Override void menuPick(ServerPlayer p, String menu, int option) {
        ServerLevel lvl = p.serverLevel();
        if (menu.equals("siege_core")) {
            if (option == 0) {
                int price = (int) Math.round(150 * COST_PCT / 100.0);
                if (points < price || core >= coreMax) return;
                points -= price;
                core = Math.min(coreMax, core + repair(p));
                coreLabel(lvl);
                lvl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
            } else if (option == 1 && canStart()) {
                startNow = true;
            } else if (option == 2) {
                shock(p, lvl);
            } else if (option == 3 && !rolesLocked) {
                roleMenu(p, false);
                return;
            } else if (option == 4 && voteOpen) {
                voteMenu(p, false);
                return;
            }
            coreMenu(p, false);
            return;
        }
        if (menu.equals("siege_role")) {
            if (!rolesLocked && option >= 0 && option < SiegeCrew.ROLES.length) setRole(p, option, true);
            for (UUID id : r.alive) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(id);
                if (o != null) roleMenu(o, false);
            }
            return;
        }
        if (menu.equals("siege_vote")) {
            if (voteOpen && option >= 0 && option < SiegeCrew.VOTES.length) votes.put(p.getUUID(), option);
            for (UUID id : r.alive) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(id);
                if (o != null) voteMenu(o, false);
            }
            return;
        }
        if (menu.startsWith("siege_trap_")) {
            String[] parts = menu.substring(11).split("_");
            int x, z;
            try { x = Integer.parseInt(parts[0]); z = Integer.parseInt(parts[1]); } catch (RuntimeException e) { return; }
            Trap here = trapAt(x, z);
            if (here != null && option == 11) {
                removeTrap(lvl, here);
                trapMenu(p, x, z, false);
                return;
            }
            if (here == null && option >= 0 && option < TrapType.values().length && trapFits(x, z) && traps.size() < MAX_TRAPS) {
                TrapType t = TrapType.values()[option];
                int price = cost(p, t.price);
                if (points < price) return;
                points -= price;
                stat(p, SiegeCrew.BUILT);
                Trap tr = new Trap(x, z, t, t.charges);
                traps.add(tr);
                placeTrap(lvl, tr);
                lvl.playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
                say(p, p.getGameProfile().getName() + " legt eine " + t.title + ".");
                PacketDistributor.sendToPlayer(p, ArenaNet.Menu.close());
            }
            return;
        }
        if (!menu.startsWith("siege_pad_")) return;
        int i;
        try { i = Integer.parseInt(menu.substring(10)); } catch (NumberFormatException e) { return; }
        if (i < 0 || i >= PADS.length) return;
        if (option == 20) {
            if (canStart()) startNow = true;
            PacketDistributor.sendToPlayer(p, ArenaNet.Menu.close());
            return;
        }
        if (option == 21 && type[i] >= 0 && Tower.values()[type[i]].mannable() && manned(lvl, i) == null) {
            Direction f = facing(i);
            p.teleportTo(CENTER.getX() + PADS[i][0] + 0.5 + f.getStepX(), CENTER.getY() + standY(lvl, i), CENTER.getZ() + PADS[i][1] + 0.5 + f.getStepZ());
            PacketDistributor.sendToPlayer(p, ArenaNet.Menu.close());
            return;
        }
        if (option == 22 && manned(lvl, i) == p) {
            Direction f = facing(i);
            p.teleportTo(CENTER.getX() + PADS[i][0] + 0.5 - f.getStepX() * 2.5, CENTER.getY(), CENTER.getZ() + PADS[i][1] + 0.5 - f.getStepZ() * 2.5);
            p.fallDistance = 0;
            PacketDistributor.sendToPlayer(p, ArenaNet.Menu.close());
            return;
        }
        if (type[i] < 0 && option >= 0 && option < Tower.values().length) {
            Tower t = Tower.values()[option];
            int price = cost(p, price(t, 0));
            if (points < price) return;
            points -= price;
            spent[i] += price;
            stat(p, SiegeCrew.BUILT);
            type[i] = option;
            level[i] = 1;
            placeTower(lvl, i);
            say(p, p.getGameProfile().getName() + " baut " + t.title + " auf Platz " + (i + 1) + ".");
        } else if (type[i] >= 0 && option == 10 && level[i] < 3) {
            Tower t = Tower.values()[type[i]];
            int price = cost(p, price(t, level[i]));
            if (points < price) return;
            points -= price;
            spent[i] += price;
            stat(p, SiegeCrew.BUILT);
            level[i]++;
            placeTower(lvl, i);
            say(p, p.getGameProfile().getName() + " baut " + t.title + " auf Stufe " + ROMAN[level[i]] + " aus.");
        } else if (type[i] >= 0 && option == 11) {
            points += spent[i] / 2;
            clearTower(lvl, i);
            type[i] = -1;
            level[i] = 0;
            spent[i] = 0;
        } else return;
        lvl.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        padLabel(lvl, i);
        padMenu(p, i, false);
    }

    private void say(ServerPlayer from, String text) {
        for (UUID id : r.alive) {
            ServerPlayer p = from.server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ Koop: Rollen, Faehigkeiten, Splitter, Abstimmung

    private int roleOf(ServerPlayer p) { return crew.getOrDefault(p.getUUID(), -1); }

    private int cost(ServerPlayer p, int price) { return SiegeCrew.cost(price, roleOf(p)); }

    private int repair(ServerPlayer p) { return roleOf(p) == SiegeCrew.SUPPLY ? SiegeCrew.SUPPLY_REPAIR : SiegeCrew.REPAIR; }

    private void stat(ServerPlayer p, int kind) {
        names.put(p.getUUID(), p.getGameProfile().getName());
        stats.computeIfAbsent(p.getUUID(), k -> new int[SiegeCrew.STATS])[kind]++;
    }

    private void roleMenu(ServerPlayer p, boolean popup) {
        int mine = roleOf(p);
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        for (int i = 0; i < SiegeCrew.ROLES.length; i++) {
            int count = 0;
            for (int v : crew.values()) if (v == i) count++;
            opts.add(new ArenaNet.MenuOption(i, SiegeCrew.ROLES[i], SiegeCrew.ROLE_DESC[i], SiegeCrew.ROLE_ICONS[i], -1,
                    mine == i ? 2 : rolesLocked ? 1 : 0, count));
        }
        String sub = rolesLocked ? "Die Rollen stehen fest." : mine >= 0
                ? "Du spielst " + SiegeCrew.ROLES[mine] + ". Wechseln geht bis zur ersten Welle."
                : "Gilt fuer den ganzen Kampf. Wer bis zur ersten Welle nichts waehlt, bekommt eine Zufallsrolle. Mehrfach erlaubt.";
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_role", "Rolle waehlen", sub, opts, -1, 0, popup));
    }

    void setRole(ServerPlayer p, int role, boolean announce) {
        crew.put(p.getUUID(), role);
        names.put(p.getUUID(), p.getGameProfile().getName());
        stats.computeIfAbsent(p.getUUID(), k -> new int[SiegeCrew.STATS]);
        applyRole(p);
        if (announce) say(p, p.getGameProfile().getName() + " spielt " + SiegeCrew.ROLES[role] + ".");
    }

    /** Lebens-Bonus und Faehigkeits-Gegenstand passend zur Rolle (auch nach Neustart oder Wiederbeitritt). */
    private void applyRole(ServerPlayer p) {
        int role = roleOf(p);
        SiegeCrew.strip(p, role);
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            boolean has = hp.getModifier(GUARD_HP) != null;
            if (role == SiegeCrew.GUARD && !has) {
                hp.addTransientModifier(new AttributeModifier(GUARD_HP, SiegeCrew.GUARD_HEALTH, AttributeModifier.Operation.ADD_VALUE));
                p.heal((float) SiegeCrew.GUARD_HEALTH);
            } else if (role != SiegeCrew.GUARD && has) {
                hp.removeModifier(GUARD_HP);
                if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }
        }
        if (role >= 0 && !SiegeCrew.has(p, role) && !p.getInventory().add(SiegeCrew.abilityItem(role)))
            p.displayClientMessage(Component.literal("Inventar voll: kein Platz fuer deine Faehigkeit!").withStyle(ChatFormatting.RED), true);
    }

    private void ensureCrew(MinecraftServer server) {
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && crew.containsKey(id)) applyRole(p);
        }
    }

    /** Erste Welle: wer noch keine Rolle hat, bekommt eine zufaellige; danach steht alles fest. */
    private void lockRoles(MinecraftServer server) {
        if (rolesLocked) return;
        rolesLocked = true;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (!crew.containsKey(id)) setRole(p, p.getRandom().nextInt(SiegeCrew.ROLES.length), true);
            roleMenu(p, false);
            p.sendSystemMessage(Component.literal("Deine Rolle: " + SiegeCrew.ROLES[roleOf(p)] + ". Faehigkeit " + SiegeCrew.ABILITY[roleOf(p)]
                    + " per Rechtsklick mit dem goldenen Gegenstand.").withStyle(ChatFormatting.GOLD));
        }
    }

    // ---- Faehigkeiten

    @Override boolean useItem(ServerPlayer p, ItemStack stack) {
        int role = SiegeCrew.abilityRole(stack);
        if (role < 0) return false;
        if (role != roleOf(p)) {
            stack.setCount(0);
            return true;
        }
        if (r.state == ArenaManager.State.PREP) {
            p.displayClientMessage(Component.literal(SiegeCrew.ABILITY[role] + " geht nur waehrend einer Welle.").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        ServerLevel lvl = p.serverLevel();
        long now = lvl.getGameTime();
        long at = ready.getOrDefault(p.getUUID(), 0L);
        if (now < at) {
            p.displayClientMessage(Component.literal(SiegeCrew.ABILITY[role] + " laedt noch " + (at - now + 19) / 20 + " s.").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        boolean used = switch (role) {
            case SiegeCrew.BUILDER -> overload(p, lvl, now);
            case SiegeCrew.GUARD -> warcry(p, lvl);
            case SiegeCrew.MAGE -> arcaneRain(p, lvl);
            default -> healWave(p, lvl);
        };
        if (!used) return true;
        int cd = SiegeCrew.cooldown(role);
        ready.put(p.getUUID(), now + cd);
        p.getCooldowns().addCooldown(stack.getItem(), cd);
        stat(p, SiegeCrew.ABILITIES);
        say(p, p.getGameProfile().getName() + " setzt " + SiegeCrew.ABILITY[role] + " ein.");
        return true;
    }

    private boolean overload(ServerPlayer p, ServerLevel lvl, long now) {
        int n = 0;
        for (int i = 0; i < PADS.length; i++) {
            if (type[i] < 0 || type[i] == Tower.GOLEM.ordinal() || type[i] == Tower.MINE.ordinal()) continue;
            double dx = p.getX() - (CENTER.getX() + PADS[i][0] + 0.5), dz = p.getZ() - (CENTER.getZ() + PADS[i][1] + 0.5);
            if (dx * dx + dz * dz > 12 * 12) continue;
            overload[i] = now + 200;
            n++;
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + muzzle(i) + 0.5, CENTER.getZ() + PADS[i][1] + 0.5, 30, 0.8, 0.8, 0.8, 0.1);
        }
        if (n == 0) {
            p.displayClientMessage(Component.literal("Kein Turm in 12 Bloecken.").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        lvl.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
        return true;
    }

    private boolean warcry(ServerPlayer p, ServerLevel lvl) {
        for (UUID id : r.mobs) {
            if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && m.distanceToSqr(p) <= 6 * 6) {
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 10, false, true));
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 1, false, true));
        lvl.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
        lvl.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    private boolean arcaneRain(ServerPlayer p, ServerLevel lvl) {
        net.minecraft.world.phys.HitResult hit = p.pick(48.0, 0, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            p.displayClientMessage(Component.literal("Schau auf die Stelle, die getroffen werden soll.").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        Vec3 at = hit.getLocation();
        rains.add(new double[]{at.x, CENTER.getY(), at.z, 3});
        lvl.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 0.8F);
        return true;
    }

    /** Arkanregen: drei Einschlaege im Abstand einer halben Sekunde. */
    private void rains(ServerLevel lvl) {
        for (var it = rains.iterator(); it.hasNext(); ) {
            double[] rn = it.next();
            Vec3 c = new Vec3(rn[0], rn[1], rn[2]);
            float dmg = (float) (8 * towerScale());
            for (UUID id : r.mobs) {
                if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive() && m.position().distanceToSqr(c) <= 4 * 4) {
                    m.invulnerableTime = 0;
                    m.hurt(lvl.damageSources().magic(), dmg);
                }
            }
            lvl.sendParticles(ParticleTypes.WITCH, c.x, c.y + 4.0, c.z, 40, 2.0, 2.0, 2.0, 0.05);
            lvl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 2.0, 0.2, 2.0, 0);
            lvl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
            if (--rn[3] <= 0) it.remove();
        }
    }

    private boolean healWave(ServerPlayer p, ServerLevel lvl) {
        core = Math.min(coreMax, core + 15);
        coreLabel(lvl);
        for (List<UUID> list : golems.values()) {
            for (UUID id : list) if (lvl.getEntity(id) instanceof IronGolem g && g.isAlive()) g.setHealth(g.getMaxHealth());
        }
        if ((r.mask & ArenaManager.MOD_NOHEAL) == 0) {
            for (UUID id : r.alive) {
                ServerPlayer o = lvl.getServer().getPlayerList().getPlayer(id);
                if (o != null && o.level() == lvl && o.distanceToSqr(p) <= 10 * 10) o.heal(6.0F);
            }
        }
        lvl.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.0, p.getZ(), 20, 3.0, 1.0, 3.0, 0);
        lvl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.2F);
        return true;
    }

    /** Versorger: Golems in 8 Bloecken heilen alle 5 Sekunden. */
    private void supplyAura(MinecraftServer server, ServerLevel lvl) {
        for (UUID id : r.alive) {
            if (crew.getOrDefault(id, -1) != SiegeCrew.SUPPLY) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.level() != lvl) continue;
            for (List<UUID> list : golems.values()) {
                for (UUID gid : list) {
                    if (lvl.getEntity(gid) instanceof IronGolem g && g.isAlive() && g.distanceToSqr(p) <= 8 * 8 && g.getHealth() < g.getMaxHealth()) {
                        g.heal(8.0F);
                        lvl.sendParticles(ParticleTypes.HEART, g.getX(), g.getY() + 2.5, g.getZ(), 2, 0.4, 0.2, 0.4, 0);
                    }
                }
            }
        }
    }

    // ---- Splitter

    private void dropShard(ServerLevel lvl, Vec3 pos) {
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(lvl, pos.x, CENTER.getY() + 0.2, pos.z, SiegeCrew.shard(), 0, 0.2, 0);
        item.addTag("chronicle_arena_pickup");
        item.setGlowingTag(true);
        item.setUnlimitedLifetime();
        if (lvl.addFreshEntity(item)) shards.put(item.getUUID(), lvl.getGameTime() + SiegeCrew.SHARD_TICKS);
    }

    /** Splitter aufheben (darueber laufen) und am Kristall abgeben. */
    private void shards(MinecraftServer server, ServerLevel lvl) {
        long now = lvl.getGameTime();
        for (var it = shards.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (!(lvl.getEntity(en.getKey()) instanceof net.minecraft.world.entity.item.ItemEntity item) || !item.isAlive()) { it.remove(); continue; }
            if (now > en.getValue()) {
                lvl.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.3, item.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                item.discard();
                it.remove();
                continue;
            }
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || p.level() != lvl || p.isSpectator() || p.distanceToSqr(item) > 1.8 * 1.8) continue;
                carried.merge(id, 1, Integer::sum);
                item.discard();
                it.remove();
                lvl.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);
                p.displayClientMessage(Component.literal("Kristall-Splitter (" + carried.get(id) + ") - bring ihn zum Kristall!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                break;
            }
        }
        double cx = CENTER.getX() + CORE[0] + 0.5, cz = CENTER.getZ() + CORE[1] + 0.5;
        for (UUID id : r.alive) {
            int n = carried.getOrDefault(id, 0);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (n <= 0 || p == null || p.level() != lvl) continue;
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            if (dx * dx + dz * dz > 4.5 * 4.5) continue;
            carried.remove(id);
            int heal = 0, bonus = 0;
            for (int k = 0; k < n; k++) {
                if (core < coreMax) {
                    int add = Math.min(SiegeCrew.SHARD_HEAL, coreMax - core);
                    core += add;
                    heal += add;
                } else {
                    points += SiegeCrew.SHARD_POINTS;
                    bonus += SiegeCrew.SHARD_POINTS;
                }
                stat(p, SiegeCrew.SHARDS);
            }
            coreLabel(lvl);
            lvl.sendParticles(ParticleTypes.END_ROD, cx, CENTER.getY() + 3.0, cz, 30, 0.6, 1.2, 0.6, 0.05);
            lvl.playSound(null, cx, CENTER.getY() + 2, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 1.0F);
            say(p, p.getGameProfile().getName() + " bringt " + n + (n == 1 ? " Splitter" : " Splitter") + " zum Kristall"
                    + (heal > 0 ? ": +" + heal + " Leben" : "") + (bonus > 0 ? (heal > 0 ? ", " : ": ") + "+" + bonus + " Punkte" : "") + ".");
        }
    }

    // ---- Abstimmung

    private void voteMenu(ServerPlayer p, boolean popup) {
        int mine = votes.getOrDefault(p.getUUID(), -1);
        int[] counts = voteCounts();
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        for (int i = 0; i < SiegeCrew.VOTES.length; i++) {
            opts.add(new ArenaNet.MenuOption(i, SiegeCrew.VOTES[i], SiegeCrew.VOTE_DESC[i], SiegeCrew.VOTE_ICONS[i], -1,
                    mine == i ? 2 : voteOpen ? 0 : 1, counts[i]));
        }
        String sub = voteOpen ? "Gilt fuer Welle " + (r.wave + 1) + ". Mehrheit entscheidet, bei Gleichstand die ruhigere Wahl." : "Die Abstimmung ist vorbei.";
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_vote", "Naechste Welle", sub, opts, -1, 0, popup));
    }

    private int[] voteCounts() {
        int[] counts = new int[SiegeCrew.VOTES.length];
        for (int v : votes.values()) if (v >= 0 && v < counts.length) counts[v]++;
        return counts;
    }

    /** Nach einer Welle (nicht nach der letzten): Abstimmung oeffnen. Bei Segen-Wellen ohne Popup, das Menue gibt es am Kristall. */
    private void openVote(MinecraftServer server) {
        votes.clear();
        if (r.wave >= r.totalWaves) return;
        voteOpen = true;
        boolean popup = r.wave % 5 != 0;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (popup) voteMenu(p, true);
            else p.sendSystemMessage(Component.literal("Abstimmung fuer die naechste Welle: am Kristall (Rechtsklick).").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private void resolveVote(MinecraftServer server) {
        if (!voteOpen) {
            activeVote = SiegeCrew.CALM;
            return;
        }
        voteOpen = false;
        activeVote = SiegeCrew.winner(voteCounts());
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            voteMenu(p, false);
            if (activeVote != SiegeCrew.CALM)
                p.sendSystemMessage(Component.literal("Abgestimmt: " + SiegeCrew.VOTES[activeVote] + "! " + SiegeCrew.VOTE_DESC[activeVote]).withStyle(ChatFormatting.GOLD));
        }
        votes.clear();
    }

    // ---- Bilanz

    @Override void finished(MinecraftServer server, boolean won) {
        List<UUID> ids = new ArrayList<>(stats.keySet());
        if (ids.isEmpty()) return;
        ids.sort((a, b) -> Integer.compare(SiegeCrew.score(stats.get(b)), SiegeCrew.score(stats.get(a))));
        List<String> lines = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            String name = p != null ? p.getGameProfile().getName() : names.getOrDefault(id, "?");
            int role = crew.getOrDefault(id, -1);
            lines.add(SiegeCrew.line(name + (role >= 0 ? " [" + SiegeCrew.ROLES[role] + "]" : ""), stats.get(id)));
        }
        UUID best = ids.get(0);
        ServerPlayer bp = server.getPlayerList().getPlayer(best);
        String mvp = bp != null ? bp.getGameProfile().getName() : names.getOrDefault(best, "?");
        Set<UUID> all = new HashSet<>(r.participants);
        all.addAll(r.alive);
        for (UUID id : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("--- Burg-Bilanz ---").withStyle(ChatFormatting.GOLD));
            if (ids.size() > 1) p.sendSystemMessage(Component.literal("MVP: " + mvp).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            for (String l : lines) p.sendSystemMessage(Component.literal(l).withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ Anzeige, Ende, Neustart

    @Override String phase() {
        if (r.state == ArenaManager.State.PREP) return voteOpen ? "Burg halten | Bauphase, Abstimmung" : "Burg halten | Bauphase";
        return activeVote != SiegeCrew.CALM ? "Burg halten | " + SiegeCrew.VOTES[activeVote] : "Burg halten";
    }

    @Override String info(UUID id) {
        StringBuilder b = new StringBuilder("Kasse: ").append(points).append(" Punkte | Schockwelle ")
                .append(shockCooldown > 0 ? (shockCooldown + 19) / 20 + " s" : "bereit");
        int role = crew.getOrDefault(id, -1);
        if (role >= 0) {
            long left = ready.getOrDefault(id, 0L) - now;
            b.append(" | ").append(SiegeCrew.ABILITY[role]).append(left > 0 ? " " + (left + 19) / 20 + " s" : " bereit");
        }
        int n = carried.getOrDefault(id, 0);
        if (n > 0) b.append(" | Splitter: ").append(n);
        return b.toString();
    }

    @Override int coreHealth() { return Math.max(0, core); }

    @Override int coreMax() { return coreMax; }

    @Override void clear(ServerLevel lvl) {
        if (lvl == null) return;
        resetPads(lvl);
        clearTraps(lvl);
        gate(lvl, false);
        ArenaLabels.remove(lvl, "chronicle_siege");
        java.util.Arrays.fill(padLabels, null);
        coreLabel = null;
        for (UUID id : shards.keySet()) if (lvl.getEntity(id) instanceof Entity e) e.discard();
        shards.clear();
        rains.clear();
        Set<UUID> all = new HashSet<>(r.participants);
        all.addAll(r.alive);
        for (UUID id : all) {
            ServerPlayer p = lvl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            SiegeCrew.strip(p, -1);
            AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && hp.getModifier(GUARD_HP) != null) {
                hp.removeModifier(GUARD_HP);
                if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }
        }
    }

    @Override CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("points", points);
        t.putInt("core", core);
        t.putInt("core_max", coreMax);
        t.putInt("pads", PADS.length);
        t.putIntArray("type", type);
        t.putIntArray("level", level);
        t.putIntArray("spent", spent);
        int[] tr = new int[traps.size() * 4];
        for (int k = 0; k < traps.size(); k++) {
            Trap x = traps.get(k);
            tr[k * 4] = x.x;
            tr[k * 4 + 1] = x.z;
            tr[k * 4 + 2] = x.type.ordinal();
            tr[k * 4 + 3] = x.charges;
        }
        t.putIntArray("traps", tr);
        CompoundTag c = new CompoundTag();
        Set<UUID> ids = new HashSet<>(crew.keySet());
        ids.addAll(stats.keySet());
        ids.addAll(carried.keySet());
        for (UUID id : ids) {
            int[] st = stats.getOrDefault(id, new int[SiegeCrew.STATS]);
            int[] row = new int[SiegeCrew.STATS + 2];
            row[0] = crew.getOrDefault(id, -1);
            row[1] = carried.getOrDefault(id, 0);
            System.arraycopy(st, 0, row, 2, SiegeCrew.STATS);
            c.putIntArray(id.toString(), row);
            if (names.containsKey(id)) c.putString(id + "_name", names.get(id));
        }
        t.put("crew", c);
        t.putBoolean("roles_locked", rolesLocked);
        t.putInt("vote", activeVote);
        return t;
    }

    @Override void load(CompoundTag t, ServerLevel lvl) {
        ArenaMaps.ensureSiege(lvl);
        r.center = CENTER;
        points = t.getInt("points");
        core = t.getInt("core");
        coreMax = Math.max(1, t.getInt("core_max"));
        int[] ty = t.getIntArray("type"), lv = t.getIntArray("level"), sp = t.getIntArray("spent");
        resetPads(lvl);
        // Stand aus einer aelteren Karte (andere Bauplaetze): Tuerme werden gutgeschrieben statt falsch aufgestellt.
        boolean sameMap = t.getInt("pads") == PADS.length;
        for (int i = 0; i < Math.max(PADS.length, ty.length); i++) {
            int typ = i < ty.length && ty[i] >= -1 && ty[i] < Tower.values().length ? ty[i] : -1;
            int spend = i < sp.length ? sp[i] : 0;
            if (!sameMap || i >= PADS.length) { if (typ >= 0) points += spend; continue; }
            type[i] = typ;
            level[i] = typ < 0 ? 0 : i < lv.length ? Math.max(1, Math.min(3, lv[i])) : 1;
            spent[i] = spend;
            if (typ >= 0) placeTower(lvl, i);
        }
        clearTraps(lvl);
        int[] tr = t.getIntArray("traps");
        for (int k = 0; k + 3 < tr.length; k += 4) {
            if (tr[k + 2] < 0 || tr[k + 2] >= TrapType.values().length || !trapFits(tr[k], tr[k + 1])) continue;
            Trap x = new Trap(tr[k], tr[k + 1], TrapType.values()[tr[k + 2]], tr[k + 3]);
            traps.add(x);
            placeTrap(lvl, x);
        }
        gate(lvl, r.wave >= SECOND_GATE_WAVE);
        ArenaLabels.remove(lvl, "chronicle_siege");
        labels(lvl);
        crew.clear();
        stats.clear();
        carried.clear();
        CompoundTag c = t.getCompound("crew");
        for (String key : c.getAllKeys()) {
            if (key.endsWith("_name")) continue;
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            int[] row = c.getIntArray(key);
            if (row.length < SiegeCrew.STATS + 2) continue;
            if (row[0] >= 0 && row[0] < SiegeCrew.ROLES.length) crew.put(id, row[0]);
            if (row[1] > 0) carried.put(id, row[1]);
            stats.put(id, java.util.Arrays.copyOfRange(row, 2, 2 + SiegeCrew.STATS));
            if (c.contains(key + "_name")) names.put(id, c.getString(key + "_name"));
        }
        rolesLocked = t.getBoolean("roles_locked") || r.wave >= 1;
        activeVote = Math.max(0, Math.min(SiegeCrew.VOTES.length - 1, t.getInt("vote")));
        voteOpen = false;
    }
}
