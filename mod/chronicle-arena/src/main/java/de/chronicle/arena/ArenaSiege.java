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
    static final int[] CORE = {0, 27};
    /** Bauplaetze (3x3), alle 5 Bloecke neben der Pfadmitte, damit auch kurze Reichweiten treffen. */
    static final int[][] PADS = {
            {-19, -25}, {-12, -21}, {0, -21}, {12, -21},
            {-16, -11}, {-6, -11}, {4, -11}, {14, -11},
            {-16, -5}, {-6, -5}, {4, -5}, {14, -5}, {29, -8},
            {-16, 5}, {-6, 5}, {4, 5}, {14, 5}, {-29, 8},
            {-16, 11}, {-6, 11}, {5, 16},
            {-5, 22}, {5, 22}};
    static final int HALF = 32;
    /** Grundwerte; kommen spaeter ins Admin-Panel. Prozentwerte wirken als Faktor. */
    static final int CORE_HP = 100, CORE_HP_PLAYER = 25, START_POINTS = 500, FIRST_BUILD_SECONDS = 15, PAUSE_SECONDS = 8;
    static final int COST_PCT = 100, KILL_PCT = 100, POWER_PCT = 100, LEAK_PCT = 100;
    /** Gegner pro Welle im Vergleich zum Wellen-Modus; dafuer hat jeder weniger Leben. */
    static final double COUNT_FACTOR = 1.6, HEALTH_FACTOR = 0.75, SPEED_BONUS = 0.3;
    static final int SHOCK_COOLDOWN = 60 * 20;

    /** Gegnerrollen: bestimmen Tempo, Leben und welche Tuerme gut wirken. */
    static final int NORMAL = 0, RUNNER = 1, ARMORED = 2, LEADER = 3;

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
                case RUNNER -> this == FLAME ? 1.5 : this == CANNON ? 0.6 : 1.0;
                case ARMORED -> this == ARROW ? 0.4 : this == FROST ? 0.5 : this == CANNON ? 1.6 : 1.0;
                default -> 1.0;
            };
        }

        /** Hoehe des Turmkoerpers; die Spitze sitzt darueber. */
        int height(int level) { return this == MINE ? 1 + level : 3 + level; }
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

    ArenaSiege(ArenaManager.Run r) {
        super(r);
        java.util.Arrays.fill(type, -1);
    }

    static BlockPos at(int x, int y, int z) { return CENTER.offset(x, y, z); }

    static Vec3 point(int i) {
        return new Vec3(CENTER.getX() + PATH[i][0] + 0.5, CENTER.getY(), CENTER.getZ() + PATH[i][1] + 0.5);
    }

    /** Wie weit ein Gegner gekommen ist (fuer die Zielwahl der Tuerme): Wegpunkt zaehlt, dann Restabstand. */
    static double progress(int wp, double distToNext) { return wp * 1000.0 - distToNext; }

    /** Laenge eines Pfades in Bloecken (fuer Tests und Anzeige). */
    static int pathLength() {
        int len = 0;
        for (int i = 1; i < PATH.length; i++) len += Math.abs(PATH[i][0] - PATH[i - 1][0]) + Math.abs(PATH[i][1] - PATH[i - 1][1]);
        return len;
    }

    /** Liegt (x, z) auf dem Pfad (halbe Breite 2)? */
    static boolean onPath(int x, int z) {
        for (int i = 1; i < PATH.length; i++) {
            int x0 = Math.min(PATH[i - 1][0], PATH[i][0]) - 2, x1 = Math.max(PATH[i - 1][0], PATH[i][0]) + 2;
            int z0 = Math.min(PATH[i - 1][1], PATH[i][1]) - 2, z1 = Math.max(PATH[i - 1][1], PATH[i][1]) + 2;
            if (x >= x0 && x <= x1 && z >= z0 && z <= z1) return true;
        }
        return false;
    }

    /** Steht eine Position (Weltkoordinaten) auf dem Pfad? */
    static boolean onPath(Vec3 pos) {
        return onPath((int) Math.floor(pos.x) - CENTER.getX(), (int) Math.floor(pos.z) - CENTER.getZ());
    }

    /** Naechster Punkt auf dem Pfadstueck vor Wegpunkt wp (Weltkoordinaten), mit Abstand zum Rand. */
    static Vec3 clampToSegment(Vec3 pos, int wp) {
        int i = Math.max(1, Math.min(PATH.length - 1, wp));
        double ax = CENTER.getX() + PATH[i - 1][0] + 0.5, az = CENTER.getZ() + PATH[i - 1][1] + 0.5;
        double bx = CENTER.getX() + PATH[i][0] + 0.5, bz = CENTER.getZ() + PATH[i][1] + 0.5;
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
        return NORMAL;
    }

    // ------------------------------------------------------------------ Start und Karte

    @Override void start(ServerLevel lvl) {
        ArenaMaps.ensureSiege(lvl);
        r.center = CENTER;
        r.variant = 0;
        coreMax = core = CORE_HP + CORE_HP_PLAYER * r.extra;
        points = START_POINTS;
        shockCooldown = 0;
        resetPads(lvl);
        labels(lvl);
    }

    @Override void started(MinecraftServer server, ServerLevel lvl) {
        r.timer = Math.max(r.timer, FIRST_BUILD_SECONDS * 20);
        for (UUID id : r.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("Burg halten: Rechtsklick auf einen Bauplatz baut Tuerme. Gegner treffen kannst du nur, wenn du selbst auf dem Weg stehst."
                    + " Am Kristall: reparieren, Schockwelle, Welle sofort starten.").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override Vec3 startPos(int i, int n) {
        double off = (i - (n - 1) / 2.0) * 1.6;
        return new Vec3(CENTER.getX() + 12.5 + off, CENTER.getY(), CENTER.getZ() + 23.5);
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
                    case FLAME -> c ? Blocks.RED_NETHER_BRICKS : s && y == h - 1 ? Blocks.MAGMA_BLOCK : Blocks.NETHER_BRICKS;
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
        Vec3 gate = point(0);
        return gate.add((lvl.getRandom().nextDouble() - 0.5) * 3.0, 0, (lvl.getRandom().nextDouble() - 0.5) * 2.0);
    }

    @Override void spawned(LivingEntity le, ArenaManager.Spawn s) {
        UUID id = le.getUUID();
        waypoint.put(id, 1);
        worth.put(id, s.mini || s.boss ? 10 : s.elite ? 3 : 1);
        int ro = rollRole(r.wave, s.mini || s.boss, le.getRandom().nextDouble());
        role.put(id, ro);
        double hp = HEALTH_FACTOR * switch (ro) {
            case RUNNER -> 0.6;
            case ARMORED -> 1.3;
            default -> 1.0;
        };
        double speed = SPEED_BONUS + (ro == RUNNER ? 0.5 : ro == ARMORED ? -0.15 : 0);
        modify(le, Attributes.MAX_HEALTH, "siege_health", hp - 1.0);
        modify(le, Attributes.MOVEMENT_SPEED, "siege_speed", speed);
        modify(le, Attributes.KNOCKBACK_RESISTANCE, "siege_knockback", 1.0);
        le.setHealth(le.getMaxHealth());
        if (!le.hasCustomName() && (ro == RUNNER || ro == ARMORED)) {
            le.setCustomName(Component.literal(ro == RUNNER ? "Laeufer" : "Gepanzert").withStyle(ro == RUNNER ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            le.setCustomNameVisible(true);
        }
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

    /** Spieler treffen Gegner nur, wenn sie selbst auf dem Weg stehen. */
    @Override float playerHit(ServerPlayer attacker, LivingEntity mob, float amount) {
        if (onPath(attacker.position())) return amount;
        long now = attacker.level().getGameTime();
        Long last = hint.get(attacker.getUUID());
        if (last == null || now - last > 60) {
            hint.put(attacker.getUUID(), now);
            attacker.displayClientMessage(Component.literal("Nur vom Weg aus! Stell dich auf den Pfad, um selbst zu kaempfen.").withStyle(ChatFormatting.GOLD), true);
        }
        return 0;
    }

    @Override void afterBuildWave(ServerLevel lvl) {
        // Mehr Gegner als im Wellen-Modus, dafuer schwaecher (siehe spawned).
        List<ArenaManager.Spawn> normal = new ArrayList<>();
        for (ArenaManager.Spawn s : r.queue) if (!s.elite && !s.boss && !s.mini) normal.add(s);
        int extra = (int) Math.round(normal.size() * (COUNT_FACTOR - 1.0));
        for (int k = 0; k < extra && !normal.isEmpty(); k++) {
            ArenaManager.Spawn copy = new ArenaManager.Spawn(normal.get(k % normal.size()).entity, false, false);
            r.queue.add(lvl.getRandom().nextInt(r.queue.size() + 1), copy);
        }
        // Gefallene Golems kommen zu jeder Welle zurueck.
        for (int i = 0; i < PADS.length; i++) if (type[i] == Tower.GOLEM.ordinal()) spawnGolems(lvl, i);
    }

    @Override void tickFight(MinecraftServer server, ServerLevel lvl, int tick) {
        if (tick % 5 == 0) {
            keepOnPath(lvl);
            leaks(server, lvl);
        }
        if (tick % 10 == 0) steer(server, lvl);
        if (shockCooldown > 0) shockCooldown--;
        towers(lvl, tick);
        if (tick % 40 == 0) leash(lvl);
        if (tick % 20 == 0) {
            coreLabel(lvl);
            double cx = CENTER.getX() + CORE[0] + 0.5, cz = CENTER.getZ() + CORE[1] + 0.5;
            lvl.sendParticles(ParticleTypes.END_ROD, cx, CENTER.getY() + 4.0, cz, 6, 0.3, 0.6, 0.3, 0.01);
            Vec3 gate = point(0);
            lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, gate.x, gate.y + 1.0, gate.z - 3.0, 8, 1.5, 1.0, 0.3, 0.01);
        }
    }

    @Override boolean holdPrep(MinecraftServer server, ServerLevel lvl, int tick) {
        if (shockCooldown > 0) shockCooldown--;
        if (startNow) {
            startNow = false;
            r.timer = Math.min(r.timer, 60);
        }
        if (tick % 20 == 0) coreLabel(lvl);
        return false;
    }

    /** Gegner, die vom Weg abkommen (Rueckstoss, Fliegen, Schubsen), werden sofort zurueckgesetzt. */
    private void keepOnPath(ServerLevel lvl) {
        for (UUID id : r.mobs) {
            if (!(lvl.getEntity(id) instanceof LivingEntity m) || !m.isAlive()) continue;
            boolean high = m.getY() > CENTER.getY() + 2.5;
            if (onPath(m.position()) && !high) continue;
            Vec3 back = clampToSegment(m.position(), waypoint.getOrDefault(id, 1));
            m.teleportTo(back.x, back.y, back.z);
            m.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Laufwege: Gegner folgen dem Pfad; nur wer einem Spieler auf dem Weg sehr nahe kommt, greift ihn an. */
    private void steer(MinecraftServer server, ServerLevel lvl) {
        for (UUID id : r.mobs) {
            if (!(lvl.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            int wp = waypoint.getOrDefault(id, 1);
            Vec3 target = point(wp);
            if (m.position().subtract(target).horizontalDistanceSqr() < 1.8 * 1.8 && wp < PATH.length - 1) {
                wp++;
                waypoint.put(id, wp);
                target = point(wp);
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
        Vec3 end = point(PATH.length - 1);
        for (UUID id : new ArrayList<>(r.mobs)) {
            if (waypoint.getOrDefault(id, 0) < PATH.length - 1) continue;
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
        m.invulnerableTime = 0;
        m.hurt(src, (float) (dmg * t.vs(role.getOrDefault(m.getUUID(), NORMAL))));
    }

    private LivingEntity furthest(List<LivingEntity> list) {
        LivingEntity best = null;
        double bestP = -1e9;
        for (LivingEntity m : list) {
            int wp = waypoint.getOrDefault(m.getUUID(), 1);
            double pr = progress(wp, Math.sqrt(m.position().subtract(point(Math.min(wp, PATH.length - 1))).horizontalDistanceSqr()));
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
            int lv = level[i];
            Vec3 pos = new Vec3(CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + muzzle(i) + 0.5, CENTER.getZ() + PADS[i][1] + 0.5);
            double range = t.range(lv);
            List<LivingEntity> inRange = new ArrayList<>();
            for (UUID id : r.mobs) {
                if (lvl.getEntity(id) instanceof LivingEntity m && m.isAlive()
                        && m.position().subtract(pos).horizontalDistanceSqr() <= range * range) inRange.add(m);
            }
            if (inRange.isEmpty()) continue;
            cooldown[i] = t.interval(lv);
            float dmg = (float) (t.damage(lv) * towerScale());
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
        role.remove(id);
        int w = worth.getOrDefault(id, 1);
        worth.remove(id);
        if (leaked.remove(id)) return;
        points += (int) Math.round(10 * w * KILL_PCT / 100.0);
    }

    @Override boolean lost() { return core <= 0; }

    /** Punkte aller Goldminen nach einer Welle. */
    int mineIncome() {
        int sum = 0;
        for (int i = 0; i < PADS.length; i++) if (type[i] == Tower.MINE.ordinal()) sum += Tower.income(level[i]);
        return sum;
    }

    @Override boolean waveCleared(MinecraftServer server, ServerLevel lvl) {
        int bonus = (int) Math.round((50 + 10 * r.wave) * KILL_PCT / 100.0);
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
        leaked.clear();
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
        return false;
    }

    private boolean canStart() { return r.state == ArenaManager.State.PREP && r.offer.isEmpty(); }

    private void padMenu(ServerPlayer p, int i, boolean popup) {
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        if (type[i] < 0) {
            for (Tower t : Tower.values()) {
                int price = price(t, 0);
                String extra = t == Tower.MINE ? " +" + Tower.income(1) + " Punkte pro Welle." : t == Tower.GOLEM ? "" : " Reichweite " + (int) t.range(1) + ".";
                opts.add(new ArenaNet.MenuOption(t.ordinal(), t.title, t.desc + extra, t.icon, price, points >= price ? 0 : 1, -1));
            }
        } else {
            Tower t = Tower.values()[type[i]];
            if (level[i] < 3) {
                int price = price(t, level[i]);
                opts.add(new ArenaNet.MenuOption(10, "Ausbauen auf Stufe " + ROMAN[level[i] + 1], upgradeText(t, level[i] + 1), t.icon, price, points >= price ? 0 : 1, -1));
            } else {
                opts.add(new ArenaNet.MenuOption(10, "Hoechste Stufe", "Dieser Turm ist voll ausgebaut.", t.icon, -1, 1, -1));
            }
            opts.add(new ArenaNet.MenuOption(11, "Abreissen", "Gibt die Haelfte der investierten Punkte zurueck (" + spent[i] / 2 + ").", "minecraft:barrier", -1, 0, -1));
        }
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
        opts.add(new ArenaNet.MenuOption(0, "Kristall reparieren", "+25 Leben (hoechstens bis zum Maximum).", "minecraft:amethyst_shard", price,
                points >= price && core < coreMax ? 0 : 1, -1));
        boolean fight = r.state != ArenaManager.State.PREP;
        String shock = shockCooldown > 0 ? "Laedt noch " + (shockCooldown + 19) / 20 + " s." : "Bereit.";
        opts.add(new ArenaNet.MenuOption(2, "Schockwelle", "Trifft alle Gegner bis 12 Bloecke um den Kristall hart und friert sie kurz ein. Kostenlos, alle 60 s. " + shock,
                "minecraft:echo_shard", -1, fight && shockCooldown <= 0 ? 0 : 1, -1));
        opts.add(new ArenaNet.MenuOption(1, "Welle sofort starten", "Bauphase beenden, die naechste Welle kommt in 3 Sekunden.", "minecraft:bell", -1, canStart() ? 0 : 1, -1));
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
                core = Math.min(coreMax, core + 25);
                coreLabel(lvl);
                lvl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
            } else if (option == 1 && canStart()) {
                startNow = true;
            } else if (option == 2) {
                shock(p, lvl);
            }
            coreMenu(p, false);
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
        if (type[i] < 0 && option >= 0 && option < Tower.values().length) {
            Tower t = Tower.values()[option];
            int price = price(t, 0);
            if (points < price) return;
            points -= price;
            spent[i] += price;
            type[i] = option;
            level[i] = 1;
            placeTower(lvl, i);
            say(p, p.getGameProfile().getName() + " baut " + t.title + " auf Platz " + (i + 1) + ".");
        } else if (type[i] >= 0 && option == 10 && level[i] < 3) {
            Tower t = Tower.values()[type[i]];
            int price = price(t, level[i]);
            if (points < price) return;
            points -= price;
            spent[i] += price;
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

    // ------------------------------------------------------------------ Anzeige, Ende, Neustart

    @Override String phase() { return r.state == ArenaManager.State.PREP ? "Burg halten | Bauphase" : "Burg halten"; }

    @Override String info(UUID id) {
        return "Kasse: " + points + " Punkte | Schockwelle " + (shockCooldown > 0 ? (shockCooldown + 19) / 20 + " s" : "bereit");
    }

    @Override int coreHealth() { return Math.max(0, core); }

    @Override int coreMax() { return coreMax; }

    @Override void clear(ServerLevel lvl) {
        if (lvl == null) return;
        resetPads(lvl);
        ArenaLabels.remove(lvl, "chronicle_siege");
        java.util.Arrays.fill(padLabels, null);
        coreLabel = null;
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
        ArenaLabels.remove(lvl, "chronicle_siege");
        labels(lvl);
    }
}
