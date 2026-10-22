package de.chronicle.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
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
 * Burg halten: Gegner laufen einen festen Pfad vom Tor zum Kristall. Die Gruppe baut mit gemeinsamen Punkten Tuerme
 * auf zwoelf Bauplaetzen und kaempft selbst mit. Wer durchkommt, schadet dem Kristall; ist er zerstoert, ist der Kampf verloren.
 */
final class ArenaSiege extends ModeLogic {
    static final BlockPos CENTER = new BlockPos(0, 64, 1100);
    /** Wegpunkte (x, z relativ zur Mitte): Tor -> Schlangenlinie -> vor dem Kristall. */
    static final int[][] PATH = {{-24, -28}, {-24, -16}, {24, -16}, {24, 0}, {-24, 0}, {-24, 16}, {0, 16}, {0, 23}};
    static final int[] CORE = {0, 27};
    static final int[][] PADS = {{-16, -8}, {-4, -8}, {8, -8}, {20, -8}, {-16, 8}, {-4, 8}, {8, 8}, {20, 8}, {-17, -24}, {16, -24}, {-12, 24}, {12, 24}};
    static final int HALF = 32;
    /** Grundwerte; kommen spaeter ins Admin-Panel. Prozentwerte wirken als Faktor. */
    static final int CORE_HP = 100, CORE_HP_PLAYER = 25, START_POINTS = 300, BUILD_SECONDS = 25;
    static final int COST_PCT = 100, KILL_PCT = 100, POWER_PCT = 100, LEAK_PCT = 100;

    enum Tower {
        ARROW("Pfeilturm", "Schiesst auf den Gegner, der am weitesten gekommen ist.", "minecraft:arrow", 100, 150, 250),
        FROST("Frostturm", "Verlangsamt alle Gegner in Reichweite stark und macht etwas Schaden.", "minecraft:blue_ice", 150, 200, 300),
        FLAME("Flammenwerfer", "Flammenstoss auf alle Gegner in kurzer Reichweite, setzt sie in Brand.", "minecraft:blaze_powder", 150, 200, 300),
        GOLEM("Golem-Wache", "Ein Eisengolem bewacht den Platz. Stufe 2: staerker, Stufe 3: zwei Golems.", "minecraft:iron_block", 250, 300, 400);

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
                case FLAME -> 4.5 + level;
                case GOLEM -> 10;
            };
        }

        int interval(int level) {
            return switch (this) {
                case ARROW -> 24 - 4 * level;
                case FROST -> 10;
                case FLAME -> 34 - 4 * level;
                case GOLEM -> 40;
            };
        }

        double damage(int level) {
            return switch (this) {
                case ARROW -> new double[]{4, 6, 9}[level - 1];
                case FROST -> level;
                case FLAME -> new double[]{5, 7, 10}[level - 1];
                case GOLEM -> 0;
            };
        }
    }

    static final String[] ROMAN = {"", "I", "II", "III"};

    final int[] type = new int[PADS.length];
    final int[] level = new int[PADS.length];
    final int[] spent = new int[PADS.length];
    private final int[] cooldown = new int[PADS.length];
    private final Map<Integer, List<UUID>> golems = new HashMap<>();
    private final UUID[] padLabels = new UUID[PADS.length];
    private UUID coreLabel;
    int points;
    int core, coreMax;
    private boolean startNow;
    final Map<UUID, Integer> waypoint = new HashMap<>();
    private final Map<UUID, Integer> worth = new HashMap<>();
    private final Map<UUID, double[]> stuck = new HashMap<>();
    private final Set<UUID> leaked = new HashSet<>();

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

    int price(Tower t, int lvl) { return (int) Math.round(t.price[lvl] * COST_PCT / 100.0); }

    static int leakDamage(int worth) { return worth >= 10 ? 30 : worth >= 3 ? 15 : 5; }

    // ------------------------------------------------------------------ Start und Karte

    @Override void start(ServerLevel lvl) {
        ArenaMaps.ensureSiege(lvl);
        r.center = CENTER;
        r.variant = 0;
        coreMax = core = CORE_HP + CORE_HP_PLAYER * r.extra;
        points = START_POINTS;
        resetPads(lvl);
        labels(lvl);
    }

    @Override void started(MinecraftServer server, ServerLevel lvl) {
        r.timer = Math.max(r.timer, BUILD_SECONDS * 20);
        for (UUID id : r.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("Burg halten: Rechtsklick auf einen Bauplatz baut Tuerme, Rechtsklick auf den Kristall repariert ihn oder startet die Welle sofort.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override Vec3 startPos(int i, int n) {
        double off = (i - (n - 1) / 2.0) * 1.6;
        return new Vec3(CENTER.getX() + 8.5 + off, CENTER.getY(), CENTER.getZ() + 22.5);
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
        for (int y = 0; y <= 6; y++) lvl.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        for (UUID g : golems.getOrDefault(i, List.of())) {
            Entity e = lvl.getEntity(g);
            if (e != null) e.discard();
        }
        golems.remove(i);
    }

    private void placeTower(ServerLevel lvl, int i) {
        clearTower(lvl, i);
        if (type[i] < 0) return;
        Tower t = Tower.values()[type[i]];
        int x = PADS[i][0], z = PADS[i][1], lv = level[i];
        Block body = switch (t) {
            case ARROW -> Blocks.POLISHED_ANDESITE;
            case FROST -> Blocks.PACKED_ICE;
            case FLAME -> Blocks.NETHER_BRICKS;
            case GOLEM -> Blocks.IRON_BLOCK;
        };
        for (int y = 0; y < lv; y++) lvl.setBlock(at(x, y, z), body.defaultBlockState(), 2);
        BlockState top = switch (t) {
            case ARROW -> Blocks.TARGET.defaultBlockState();
            case FROST -> Blocks.BLUE_ICE.defaultBlockState();
            case FLAME -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            case GOLEM -> Blocks.CARVED_PUMPKIN.defaultBlockState();
        };
        lvl.setBlock(at(x, lv, z), top, 2);
        if (t == Tower.GOLEM) spawnGolems(lvl, i);
    }

    private void labels(ServerLevel lvl) {
        for (int i = 0; i < PADS.length; i++) padLabel(lvl, i);
        coreLabel(lvl);
    }

    private void padLabel(ServerLevel lvl, int i) {
        String text = type[i] < 0 ? "BAUPLATZ\nRechtsklick" : Tower.values()[type[i]].title + " " + ROMAN[level[i]];
        padLabels[i] = ArenaLabels.text(lvl, padLabels[i], "chronicle_siege", CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + 4.6,
                CENTER.getZ() + PADS[i][1] + 0.5, text, type[i] < 0 ? "gray" : "gold", 120);
    }

    private void coreLabel(ServerLevel lvl) {
        coreLabel = ArenaLabels.text(lvl, coreLabel, "chronicle_siege", CENTER.getX() + CORE[0] + 0.5, CENTER.getY() + 5.2, CENTER.getZ() + CORE[1] + 0.5,
                "KRISTALL\n" + Math.max(0, core) + " / " + coreMax, core * 3 <= coreMax ? "red" : "aqua", 120);
    }

    // ------------------------------------------------------------------ Gegner

    @Override Boolean objectiveNow() { return Boolean.FALSE; }

    @Override boolean controlsAi() { return true; }

    @Override boolean routes() { return false; }

    @Override boolean shooters() { return false; }

    @Override int pauseTicks(boolean offer) { return Math.max(BUILD_SECONDS * 20, offer ? 20 * 20 : 0); }

    @Override Vec3 spawnPos(ServerLevel lvl, ArenaManager.Spawn s) {
        Vec3 gate = point(0);
        return gate.add((lvl.getRandom().nextDouble() - 0.5) * 3.0, 0, (lvl.getRandom().nextDouble() - 0.5) * 2.0);
    }

    @Override void spawned(LivingEntity le, ArenaManager.Spawn s) {
        waypoint.put(le.getUUID(), 1);
        worth.put(le.getUUID(), s.mini || s.boss ? 10 : s.elite ? 3 : 1);
    }

    @Override boolean allowTarget(LivingEntity mob, LivingEntity target) {
        if (target == null || target instanceof IronGolem) return true;
        return target instanceof ServerPlayer && mob.distanceToSqr(target) < 6.0 * 6.0;
    }

    @Override void afterBuildWave(ServerLevel lvl) {
        // Gefallene Golems kommen zu jeder Welle zurueck.
        for (int i = 0; i < PADS.length; i++) if (type[i] == Tower.GOLEM.ordinal()) spawnGolems(lvl, i);
    }

    @Override void tickFight(MinecraftServer server, ServerLevel lvl, int tick) {
        if (tick % 10 == 0) steer(server, lvl);
        if (tick % 5 == 0) leaks(server, lvl);
        towers(lvl, tick);
        if (tick % 40 == 0) leash(lvl);
        if (tick % 20 == 0) {
            coreLabel(lvl);
            double cx = CENTER.getX() + CORE[0] + 0.5, cz = CENTER.getZ() + CORE[1] + 0.5;
            lvl.sendParticles(ParticleTypes.END_ROD, cx, CENTER.getY() + 4.0, cz, 6, 0.3, 0.6, 0.3, 0.01);
        }
    }

    @Override boolean holdPrep(MinecraftServer server, ServerLevel lvl, int tick) {
        if (startNow) {
            startNow = false;
            r.timer = Math.min(r.timer, 60);
        }
        if (tick % 20 == 0) coreLabel(lvl);
        return false;
    }

    /** Laufwege: Gegner folgen dem Pfad; nur wer einem Spieler sehr nahe kommt, greift ihn an. */
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
                if (p != null && p.level() == lvl && !p.isSpectator() && m.distanceToSqr(p) < 4.5 * 4.5) { near = p; break; }
            }
            if (near != null) {
                if (cur != near) m.setTarget(near);
                continue;
            }
            if (cur != null) m.setTarget(null);
            m.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            // Haengt ein Gegner 8 Sekunden fest, wird er auf den naechsten Wegpunkt gesetzt.
            double[] st = stuck.computeIfAbsent(id, k -> new double[]{m.getX(), m.getZ(), 0});
            if (Math.abs(m.getX() - st[0]) + Math.abs(m.getZ() - st[1]) > 1.0) {
                st[0] = m.getX();
                st[1] = m.getZ();
                st[2] = 0;
            } else if (++st[2] >= 16) {
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

    private void towers(ServerLevel lvl, int tick) {
        for (int i = 0; i < PADS.length; i++) {
            if (type[i] < 0 || type[i] == Tower.GOLEM.ordinal()) continue;
            if (cooldown[i] > 0) { cooldown[i]--; continue; }
            Tower t = Tower.values()[type[i]];
            int lv = level[i];
            Vec3 pos = new Vec3(CENTER.getX() + PADS[i][0] + 0.5, CENTER.getY() + lv + 0.5, CENTER.getZ() + PADS[i][1] + 0.5);
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
                    LivingEntity best = null;
                    double bestP = -1e9;
                    for (LivingEntity m : inRange) {
                        int wp = waypoint.getOrDefault(m.getUUID(), 1);
                        double pr = progress(wp, Math.sqrt(m.position().subtract(point(Math.min(wp, PATH.length - 1))).horizontalDistanceSqr()));
                        if (pr > bestP) { bestP = pr; best = m; }
                    }
                    if (best == null) break;
                    beam(lvl, pos, best.position().add(0, best.getBbHeight() * 0.6, 0));
                    best.hurt(lvl.damageSources().magic(), dmg);
                    lvl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.8F, 1.2F);
                }
                case FROST -> {
                    for (LivingEntity m : inRange) {
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, lv, false, true));
                        if (tick % 20 == 0) m.hurt(lvl.damageSources().freeze(), dmg);
                    }
                    lvl.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5, pos.z, 10, range / 3, 0.3, range / 3, 0.01);
                }
                case FLAME -> {
                    for (LivingEntity m : inRange) {
                        m.hurt(lvl.damageSources().inFire(), dmg);
                        m.igniteForSeconds(3);
                    }
                    lvl.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 40, range / 2.5, 0.4, range / 2.5, 0.03);
                    lvl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.8F);
                }
                default -> {}
            }
        }
    }

    private static void beam(ServerLevel lvl, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 2));
        for (int k = 0; k <= steps; k++) {
            Vec3 p = from.add(d.scale(k / (double) steps));
            lvl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void spawnGolems(ServerLevel lvl, int i) {
        List<UUID> list = golems.computeIfAbsent(i, k -> new ArrayList<>());
        list.removeIf(id -> !(lvl.getEntity(id) instanceof IronGolem g) || !g.isAlive());
        int want = level[i] >= 3 ? 2 : 1;
        while (list.size() < want) {
            IronGolem g = EntityType.IRON_GOLEM.create(lvl);
            if (g == null) return;
            g.setPlayerCreated(true);
            g.setPersistenceRequired();
            g.addTag(ArenaManager.GOLEM_TAG);
            double side = list.isEmpty() ? 2.0 : -2.0;
            g.moveTo(CENTER.getX() + PADS[i][0] + 0.5 + side, CENTER.getY(), CENTER.getZ() + PADS[i][1] + 0.5, 0, 0);
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

    /** Golems bleiben in der Naehe ihres Bauplatzes. */
    private void leash(ServerLevel lvl) {
        for (var en : golems.entrySet()) {
            Vec3 home = new Vec3(CENTER.getX() + PADS[en.getKey()][0] + 0.5, CENTER.getY(), CENTER.getZ() + PADS[en.getKey()][1] + 2.5);
            for (UUID id : en.getValue()) {
                if (lvl.getEntity(id) instanceof IronGolem g && g.isAlive() && g.position().distanceToSqr(home) > 10 * 10) {
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
        int w = worth.getOrDefault(id, 1);
        worth.remove(id);
        if (leaked.remove(id)) return;
        points += (int) Math.round(10 * w * KILL_PCT / 100.0);
    }

    @Override boolean lost() { return core <= 0; }

    @Override boolean waveCleared(MinecraftServer server, ServerLevel lvl) {
        int bonus = (int) Math.round((50 + 10 * r.wave) * KILL_PCT / 100.0);
        points += bonus;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal("Welle gehalten: +" + bonus + " Punkte (Kasse " + points + "). Bauphase!").withStyle(ChatFormatting.GREEN));
        }
        waypoint.clear();
        stuck.clear();
        worth.clear();
        leaked.clear();
        return false;
    }

    // ------------------------------------------------------------------ Bauen

    int padAt(BlockPos pos) {
        for (int i = 0; i < PADS.length; i++) {
            BlockPos c = at(PADS[i][0], 0, PADS[i][1]);
            if (Math.abs(pos.getX() - c.getX()) <= 1 && Math.abs(pos.getZ() - c.getZ()) <= 1 && pos.getY() >= c.getY() - 1 && pos.getY() <= c.getY() + 6) return i;
        }
        return -1;
    }

    static boolean isCore(BlockPos pos) {
        BlockPos c = at(CORE[0], 0, CORE[1]);
        return Math.abs(pos.getX() - c.getX()) <= 1 && Math.abs(pos.getZ() - c.getZ()) <= 1 && pos.getY() >= c.getY() - 1 && pos.getY() <= c.getY() + 4;
    }

    @Override boolean interact(ServerPlayer p, BlockPos pos) {
        int pad = padAt(pos);
        if (pad >= 0) { padMenu(p, pad, true); return true; }
        if (isCore(pos)) { coreMenu(p, true); return true; }
        return false;
    }

    private void padMenu(ServerPlayer p, int i, boolean popup) {
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        if (type[i] < 0) {
            for (Tower t : Tower.values()) {
                int price = price(t, 0);
                opts.add(new ArenaNet.MenuOption(t.ordinal(), t.title, t.desc + " Reichweite " + (int) t.range(1) + ".", t.icon, price, points >= price ? 0 : 1, -1));
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
        String title = type[i] < 0 ? "Bauplatz " + (i + 1) : Tower.values()[type[i]].title + " " + ROMAN[level[i]];
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_pad_" + i, title, "Gemeinsame Kasse der Gruppe. Bauen geht jederzeit, auch waehrend der Welle.", opts, points, 0, popup));
    }

    private static String upgradeText(Tower t, int lv) {
        return switch (t) {
            case ARROW -> "Mehr Schaden, schnellere Schuesse, Reichweite " + (int) t.range(lv) + ".";
            case FROST -> "Staerkere Verlangsamung, mehr Schaden, Reichweite " + (int) t.range(lv) + ".";
            case FLAME -> "Mehr Schaden, oefter, Reichweite " + (int) t.range(lv) + ".";
            case GOLEM -> lv >= 3 ? "Ein zweiter Golem kommt dazu." : "Golem mit 50 % mehr Leben und Schaden.";
        };
    }

    private void coreMenu(ServerPlayer p, boolean popup) {
        int price = (int) Math.round(150 * COST_PCT / 100.0);
        List<ArenaNet.MenuOption> opts = new ArrayList<>();
        opts.add(new ArenaNet.MenuOption(0, "Kristall reparieren", "+25 Leben (hoechstens bis zum Maximum).", "minecraft:amethyst_shard", price,
                points >= price && core < coreMax ? 0 : 1, -1));
        boolean prep = r.state == ArenaManager.State.PREP && r.offer.isEmpty();
        opts.add(new ArenaNet.MenuOption(1, "Welle sofort starten", "Bauphase beenden, die naechste Welle kommt in 3 Sekunden.", "minecraft:bell", -1, prep ? 0 : 1, -1));
        PacketDistributor.sendToPlayer(p, new ArenaNet.Menu("siege_core", "Kristall " + Math.max(0, core) + " / " + coreMax, "Faellt der Kristall, ist der Kampf verloren.", opts, points, 0, popup));
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
            } else if (option == 1 && r.state == ArenaManager.State.PREP && r.offer.isEmpty()) {
                startNow = true;
            }
            coreMenu(p, false);
            return;
        }
        if (!menu.startsWith("siege_pad_")) return;
        int i;
        try { i = Integer.parseInt(menu.substring(10)); } catch (NumberFormatException e) { return; }
        if (i < 0 || i >= PADS.length) return;
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

    @Override String info(UUID id) { return "Kasse: " + points + " Punkte"; }

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
        for (int i = 0; i < PADS.length; i++) {
            type[i] = i < ty.length && ty[i] >= -1 && ty[i] < Tower.values().length ? ty[i] : -1;
            level[i] = type[i] < 0 ? 0 : i < lv.length ? Math.max(1, Math.min(3, lv[i])) : 1;
            spent[i] = i < sp.length ? sp[i] : 0;
            if (type[i] >= 0) placeTower(lvl, i);
        }
        ArenaLabels.remove(lvl, "chronicle_siege");
        labels(lvl);
    }
}
