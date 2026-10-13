package de.chronicle.arena;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Vex;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Es gibt genau eine Arena. Ein Durchgang ("Run") laeuft zur Zeit; alle, die beim Start in der Lobby stehen,
 * kaempfen mit, alle anderen koennen waehrenddessen zuschauen.
 */
public final class ArenaManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, ChronicleArena.id("arena"));

    static final BlockPos LOBBY = new BlockPos(0, 64, 0);
    static final BlockPos ARENA = new BlockPos(0, 64, 200);
    /** Glutarena (Nether-Thema) und Eisarena (Eis-Thema): gleicher Grundriss, andere Bausteine. */
    static final BlockPos ARENA_B = new BlockPos(0, 64, 500);
    static final BlockPos ARENA_C = new BlockPos(0, 64, 800);

    static int variantFor(String theme) {
        return "nether".equals(theme) ? 1 : "ice".equals(theme) ? 2 : 0;
    }

    static BlockPos centerFor(int variant) {
        return variant == 1 ? ARENA_B : variant == 2 ? ARENA_C : ARENA;
    }
    static final BlockPos SHOP = new BlockPos(60, 64, 0);
    static final int LOBBY_RADIUS = 12;
    static final int SHOP_RADIUS = 12;
    static final int ARENA_RADIUS = 40;
    static final int MAX_ALIVE_MOBS = 30;
    static final int PREP_TICKS = 100;
    static final int END_TICKS = 200;

    private static final String TAG_ORIGIN = "chronicle_arena_origin";
    private static final String TAG_MODE = "chronicle_arena_mode";
    private static final String TAG_PROGRESS = "chronicle_arena_progress";

    private static Run run;
    private static int tickCounter;
    private static final Set<UUID> statsDirty = new HashSet<>();

    // ------------------------------------------------------------------ Modifikatoren

    static final int MOD_ELITE = 1;
    static final int MOD_NOHEAL = 2;
    static final int MOD_GLASS = 4;
    static final int MOD_SPEED = 8;
    private static final int[] MOD_BITS = {MOD_ELITE, MOD_NOHEAL, MOD_GLASS, MOD_SPEED};
    private static final String[] MOD_NAMES = {"Elite-Horde", "Kein Heilen", "Glaskanone", "Hetzjagd"};
    private static final String[] MOD_DESCS = {
            "30% der Gegner sind Elite (doppelte Gesundheit).",
            "Keine Regeneration, auch nicht zwischen den Wellen.",
            "Du erleidest doppelten Schaden.",
            "Alle Gegner sind 30% schneller."};
    private static final int[] MOD_BONUS = {25, 35, 30, 20};

    static double coinMult(int mask) {
        int bonus = 0;
        for (int i = 0; i < MOD_BITS.length; i++) if ((mask & MOD_BITS[i]) != 0) bonus += MOD_BONUS[i];
        return 1.0 + bonus / 100.0;
    }

    static String modNames(int mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MOD_BITS.length; i++) {
            if ((mask & MOD_BITS[i]) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(MOD_NAMES[i]);
            }
        }
        return sb.length() == 0 ? "keine" : sb.toString();
    }

    // ------------------------------------------------------------------ Tages-Challenge

    record Challenge(int day, ArenaDefs.Encounter encounter, ArenaDefs.Tier tier, int mask, int bonus) {}

    static Challenge dailyChallenge() {
        List<ArenaDefs.Encounter> encs = ArenaDefs.encounters();
        List<ArenaDefs.Tier> tiers = ArenaDefs.tiers();
        if (encs.isEmpty() || tiers.isEmpty()) return null;
        int day = (int) LocalDate.now(ZoneOffset.UTC).toEpochDay();
        java.util.Random rnd = new java.util.Random(day * 7919L + 13L);
        ArenaDefs.Encounter enc = encs.get(rnd.nextInt(encs.size()));
        ArenaDefs.Tier tier = tiers.get(rnd.nextInt(Math.min(3, tiers.size())));
        int mask = 1 + rnd.nextInt(15);
        int bonus = (int) Math.round(tier.tokens() * (enc.boss() ? 2 : 1) * coinMult(mask) * 2.0);
        return new Challenge(day, enc, tier, mask, bonus);
    }

    // ------------------------------------------------------------------ Run

    enum State { PREP, WAVE, BOSS, ENDING }

    static final class Spawn {
        final ResourceLocation entity;
        final boolean elite;
        final boolean boss;
        boolean mini;
        int cls;

        Spawn(ResourceLocation entity, boolean elite, boolean boss) {
            this.entity = entity;
            this.elite = elite;
            this.boss = boss;
        }
    }

    static final class Run {
        final ArenaDefs.Encounter encounter;
        final ArenaDefs.Tier tier;
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Set<UUID> participants = new HashSet<>();
        final Map<UUID, net.minecraft.world.phys.Vec3> fallen = new HashMap<>();
        final Map<UUID, Integer> reviving = new HashMap<>();
        final List<UUID> mobs = new ArrayList<>();
        final List<Spawn> queue = new ArrayList<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        final double healthScale;
        final double damageScale;
        final double countScale;
        final int totalWaves;
        final BlockPos center;
        final int variant;
        final boolean endless;
        final int extra;
        final int mask;
        State state = State.PREP;
        int wave;
        int completedWaves;
        int timer = PREP_TICKS;
        int waveTotal;
        UUID bossId;
        boolean addsSpawned;
        boolean won;
        int lossDelay;
        boolean started;
        int fightTicks;
        final UUID id = UUID.randomUUID();
        final ArenaRouteChoice routes = new ArenaRouteChoice(id);
        int lastGoal = -1;
        final Map<UUID,String> names = new HashMap<>();
        final Map<UUID,Integer> lastDamage = new HashMap<>(),lastRegen = new HashMap<>();
        final Map<UUID,ArenaPickups.Drop> pickups = new HashMap<>();
        final Map<UUID,Integer> stasisUntil = new HashMap<>(),echo = new HashMap<>(),wardUntil = new HashMap<>(),runeReady = new HashMap<>();
        final Map<UUID,Float> ward = new HashMap<>();
        final Set<UUID> echoBusy = new HashSet<>();
        final Map<UUID,Integer> earnedStart = new HashMap<>(), rewardXp = new HashMap<>();
        final Map<UUID,Map<String,Integer>> loot = new HashMap<>();
        final Map<UUID, int[]> bless = new HashMap<>();
        List<ArenaShow.Blessing> offer = new ArrayList<>();
        final Set<UUID> picked = new HashSet<>();
        int musicTimer;
        boolean musicBoss;
        boolean miniThisWave;
        int revives = 2;
        double powerMul = 1.0;
        int offerWait;
        final Map<UUID, UUID> killcamTarget = new HashMap<>();
        final Map<UUID, Integer> killcamTicks = new HashMap<>();
        int event;
        int coinPercent = 100, goalInterval = 3;
        boolean goalsEnabled = true;
        double adminHealth = 1, adminDamage = 1;
        ArenaObjectives.Objective objective;
        final Map<UUID, Integer> classes = new HashMap<>();
        final long[] fountainReady = new long[4];
        final long[] trapReady = new long[8];
        final Map<UUID, long[]> stat = new HashMap<>();

        long[] stat(UUID id) {
            return stat.computeIfAbsent(id, k -> new long[4]);
        }

        Run(ArenaDefs.Encounter encounter, ArenaDefs.Tier tier, int players, int mask, int variant) {
            this.variant = variant;
            this.center = centerFor(variant);
            this.mask = mask;
            this.encounter = encounter;
            this.tier = tier;
            int extra = Math.max(0, players - 1);
            this.extra = extra;
            this.endless = tier.waves() <= 0 && !encounter.boss();
            boolean waves = !encounter.boss();
            this.healthScale = tier.health() * (waves ? tier.waveHealth() : 1.0);
            this.damageScale = tier.damage() * (waves ? tier.waveDamage() : 1.0);
            this.countScale = tier.count() * (waves ? tier.waveCount() : 1.0) * (1.0 + 0.35 * extra);
            this.totalWaves = encounter.boss() ? 1 : tier.waves() <= 0 ? Integer.MAX_VALUE : tier.waves();
            bar.setProgress(1.0F);
        }

        String totalStr() {
            return endless ? "\u221e" : String.valueOf(totalWaves);
        }

        /** Wachstum pro Welle; im Endlos-Modus staerker und ab Welle 20 zusaetzlich exponentiell. */
        double growth(double perWave, double endlessPerWave) {
            int w = Math.max(1, wave);
            double g = 1.0 + (endless ? endlessPerWave : perWave) * (w - 1);
            if (endless && w > 20) g *= Math.pow(1.02, w - 20);
            return g;
        }

        int maxAlive() {
            return Math.min(70, MAX_ALIVE_MOBS + 8 * extra);
        }

        boolean involves(UUID id) {
            return alive.contains(id) || spectators.contains(id);
        }
    }

    // ------------------------------------------------------------------ Portal und Menue

    public static void usePortal(ServerPlayer sp, BlockPos portalPos) {
        MinecraftServer server = sp.server;
        ServerLevel arena = server.getLevel(DIM);
        if (arena == null) {
            sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.no_dimension"));
            return;
        }
        if (sp.level().dimension() == DIM) {
            leave(sp);
            return;
        }
        CompoundTag origin = new CompoundTag();
        origin.putString("dim", sp.level().dimension().location().toString());
        origin.putDouble("x", sp.getX());
        origin.putDouble("y", sp.getY());
        origin.putDouble("z", sp.getZ());
        sp.getPersistentData().put(TAG_ORIGIN, origin);
        ArenaBuilder.ensureBuilt(arena);
        teleport(sp, arena, LOBBY.getX() + 0.5, LOBBY.getY(), LOBBY.getZ() + 5.5, 180.0F);
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.welcome"));
        convertTokens(sp);
        sendStats(sp);
    }

    public static void openTerminal(ServerPlayer sp) {
        if (sp.level().dimension() != DIM) return;
        if (ArenaParty.exists()) { ArenaParty.show(sp); return; }
        List<Net.TierInfo> tiers = new ArrayList<>();
        for (ArenaDefs.Tier t : ArenaDefs.tiers()) {
            int wins = wins(sp, t.id());
            int best = best(sp, t.id());
            String info = wins == 0 ? "" : Component.translatable("gui.chronicle_arena.tier_info", wins, formatTime(best)).getString();
            info += " | " + (int)Math.round(t.tokens() * ArenaSettings.get(sp.server).coins / 100.0) + " Coins (Boss x2), " + t.xp() + " XP, Stufenbeute";
            boolean locked = !unlocked(sp, t);
            if (locked) {
                ArenaDefs.Tier req = ArenaDefs.tier(t.requires());
                info = Component.translatable("gui.chronicle_arena.tier_locked", req == null ? "?" : req.name()).getString();
            }
            tiers.add(new Net.TierInfo(t.id().toString(), t.name(), t.desc(), locked, info));
        }
        List<Net.EncInfo> encs = new ArrayList<>();
        for (ArenaDefs.Encounter e : ArenaDefs.encounters()) {
            encs.add(new Net.EncInfo(e.id().toString(), e.boss(), e.name(), e.desc(), e.icon().toString()));
        }
        String status;
        if (run == null) {
            status = Component.translatable("gui.chronicle_arena.status_free", playersInLobby(sp.server).size()).getString();
        } else {
            status = Component.translatable("gui.chronicle_arena.status_running", run.encounter.name(), run.tier.name(),
                    run.alive.size()).getString();
        }
        List<Net.ModInfo> mods = new ArrayList<>();
        for (int i = 0; i < MOD_BITS.length; i++) mods.add(new Net.ModInfo(MOD_BITS[i], MOD_NAMES[i], MOD_DESCS[i], MOD_BONUS[i]));
        Challenge ch = dailyChallenge();
        Net.ChallengeInfo chi = ch == null ? new Net.ChallengeInfo("", "", 0, true, 0, "")
                : new Net.ChallengeInfo(ch.encounter().id().toString(), ch.tier().id().toString(), ch.mask(),
                ArenaStats.challengeDay(sp) == ch.day(), (int)Math.round(ch.bonus() * ArenaSettings.get(sp.server).coins / 100.0),
                ch.encounter().name() + " / " + ch.tier().name() + " / " + modNames(ch.mask()));
        PacketDistributor.sendToPlayer(sp, new Net.OpenTerminal(run != null || ArenaParty.exists(), status, tiers, encs, mods, chi, sp.hasPermissions(2), ArenaSettings.get(sp.server).coins));
        sendStats(sp);
    }

    public static void openShop(ServerPlayer sp) {
        if (sp.level().dimension() != DIM) return;
        List<Net.ShopInfo> shop = new ArrayList<>();
        for (ArenaDefs.ShopOffer o : ArenaDefs.shopOffers()) {
            ItemStack stack = o.stack(sp.server.registryAccess());
            if (stack.isEmpty()) continue;
            shop.add(new Net.ShopInfo(o.id().toString(), o.category(), o.name(), o.price(), stack, o.cosmetic()));
        }
        convertTokens(sp);
        PacketDistributor.sendToPlayer(sp, new Net.OpenShop(ArenaStats.coins(sp), shop));
        sendStats(sp);
    }

    public static void handleBuy(ServerPlayer sp, Net.Buy buy) {
        if (sp.level().dimension() != DIM) return;
        ArenaDefs.ShopOffer offer = ArenaDefs.shopOffer(ResourceLocation.tryParse(buy.offer()));
        if (offer == null) return;
        String oid = offer.id().toString();
        if (offer.isCosmetic()) {
            boolean had = ArenaStats.owns(sp, oid);
            if (!had) {
                if (!ArenaStats.spend(sp, offer.price())) {
                    sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.not_enough", offer.price()));
                    return;
                }
                ArenaStats.give(sp, oid);
                sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.bought", offer.name(), offer.price()));
            }
            String cur = offer.cosmetic().equals("title") ? ArenaStats.title(sp) : ArenaStats.trail(sp);
            ArenaStats.equip(sp, offer.cosmetic(), had && cur.equals(oid) ? "" : oid);
            ArenaStats.publish(sp);
            sendStats(sp);
            return;
        }
        if (!ArenaStats.spend(sp, offer.price())) {
            sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.not_enough", offer.price()));
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(sp, offer.stack(sp.server.registryAccess()));
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.bought", offer.name(), offer.price()));
        sendStats(sp);
    }

    public static void handleAction(ServerPlayer sp, Net.Action action) {
        if (sp.level().dimension() != DIM || !inLobby(sp)) return;
        if (action.spectate()) {
            if (run == null) {
                sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.nothing_running"));
            } else {
                addSpectator(sp);
            }
            return;
        }
        if (run != null) {
            sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.already_running"));
            return;
        }
        ArenaDefs.Encounter enc = ArenaDefs.encounter(ResourceLocation.tryParse(action.encounter()));
        ArenaDefs.Tier tier = ArenaDefs.tier(ResourceLocation.tryParse(action.tier()));
        if (enc == null || tier == null) {
            sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.bad_choice"));
            return;
        }
        if (tier.waves() <= 0 && enc.boss()) {
            sp.sendSystemMessage(Component.literal("Der Abgrund (Endlos) geht nur im Wellen-Modus."));
            return;
        }
        List<ServerPlayer> group = playersInLobby(sp.server);
        if (!group.contains(sp)) group.add(sp);
        for (ServerPlayer p : group) {
            if (!unlocked(p, tier)) {
                ArenaDefs.Tier req = ArenaDefs.tier(tier.requires());
                for (ServerPlayer q : group) {
                    q.sendSystemMessage(Component.translatable("msg.chronicle_arena.locked", p.getDisplayName(), tier.name(),
                            req == null ? "?" : req.name()));
                }
                return;
            }
        }
        int choice = (action.mask() >> 4) & 3;
        ArenaParty.propose(sp, enc, tier, group, action.mask() & 15, choice == 0 ? variantFor(enc.theme()) : choice - 1);
    }

    // ------------------------------------------------------------------ Start und Ende

    static void startPrepared(MinecraftServer server, ArenaDefs.Encounter enc, ArenaDefs.Tier tier, List<ServerPlayer> group, int mask, int variant) {
        if (run == null && !group.isEmpty() && group.stream().allMatch(p -> inLobby(p) && unlocked(p, tier))) startRun(server, enc, tier, group, mask, variant);
    }

    private static void startRun(MinecraftServer server, ArenaDefs.Encounter enc, ArenaDefs.Tier tier, List<ServerPlayer> group, int mask, int variant) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        ArenaBuilder.ensureBuilt(level);
        ArenaBuilder.ensureArena(level, centerFor(variant), variant);
        Run r = new Run(enc, tier, group.size(), mask, variant);
        ArenaSettings settings = ArenaSettings.get(server);
        r.coinPercent = settings.coins; r.goalsEnabled = settings.goals; r.goalInterval = settings.goalInterval;
        r.timer = settings.prepSeconds * 20;
        r.adminHealth = settings.health / 100.0; r.adminDamage = settings.damage / 100.0;
        run = r;
        int i = 0;
        for (ServerPlayer p : group) {
            r.names.put(p.getUUID(),p.getGameProfile().getName());
            r.earnedStart.put(p.getUUID(),ArenaStats.earned(p));
            rememberMode(p);
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.removeAllEffects();
            double angle = (Math.PI * 2.0 * i) / group.size();
            teleport(p, level, r.center.getX() + 0.5 + Math.cos(angle) * 4.0, r.center.getY(),
                    r.center.getZ() + 0.5 + Math.sin(angle) * 4.0, 0.0F);
            r.alive.add(p.getUUID());
            r.participants.add(p.getUUID());
            r.bar.addPlayer(p);
            p.sendSystemMessage(Component.translatable("msg.chronicle_arena.start", enc.name(), tier.name(), group.size()));
            if (mask != 0) p.sendSystemMessage(Component.literal("Modifikatoren: " + modNames(mask) + " (Coins x" + coinMult(mask) + ")"));
            i++;
        }
        r.bar.setName(Component.translatable("bar.chronicle_arena.prepare", enc.name()));
        LOGGER.info("Arena-Run gestartet: {} / {} mit {} Spielern", enc.id(), tier.id(), group.size());
    }

    private static void addSpectator(ServerPlayer sp) {
        Run r = run;
        if (r == null) return;
        ServerLevel level = sp.serverLevel();
        rememberMode(sp);
        r.alive.remove(sp.getUUID());
        r.spectators.add(sp.getUUID());
        r.bar.addPlayer(sp);
        sp.setGameMode(GameType.SPECTATOR);
        teleport(sp, level, r.center.getX() + 0.5, 80.0, r.center.getZ() + 0.5, 0.0F);
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.spectating"));
    }

    private static void makeSpectator(ServerPlayer sp) {
        Run r = run;
        if (r == null) return;
        r.alive.remove(sp.getUUID());
        r.spectators.add(sp.getUUID());
        r.fallen.put(sp.getUUID(), sp.position());
        ArenaRoutes.close(sp, r);
        PacketDistributor.sendToPlayer(sp,new ArenaNet.Blessings(r.id,r.wave,List.of(),List.of(),0,false));
        sp.setGameMode(GameType.SPECTATOR);
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.fallen"));
        for (UUID id : r.alive) {
            ServerPlayer other = sp.server.getPlayerList().getPlayer(id);
            if (other != null) other.sendSystemMessage(Component.translatable("msg.chronicle_arena.fallen_other", sp.getDisplayName()));
        }
    }

    private static void finish(MinecraftServer server, boolean won) {
        Run r = run;
        if (r == null || r.state == State.ENDING) return;
        r.routes.cancel();
        r.state = State.ENDING;
        r.timer = END_TICKS;
        r.won = won;
        ArenaObjectives.clear(server.getLevel(DIM), r.objective);
        ArenaPickups.clear(server.getLevel(DIM),r);
        discardMobs(server, r);
        r.queue.clear();
        for (UUID kc : r.killcamTicks.keySet()) {
            ServerPlayer kp = server.getPlayerList().getPlayer(kc);
            if (kp != null) kp.setCamera(kp);
        }
        r.killcamTicks.clear();
        ArenaShow.stopMusic(runPlayers(server, r));
        r.bar.setProgress(won ? 1.0F : 0.0F);
        r.bar.setName(Component.translatable(won ? "bar.chronicle_arena.won" : "bar.chronicle_arena.lost"));
        ServerLevel level = server.getLevel(DIM);
        ArenaExtras.updateBoard(server, r, won);
        for (UUID id : r.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || level == null || !r.involves(id)) continue;
            ArenaShow.clear(p);
            ArenaRoutes.close(p, r);
            PacketDistributor.sendToPlayer(p,new ArenaNet.Blessings(r.id,r.wave,List.of(),List.of(),0,false));
            if (won) {
                recordWin(p, r);
                ArenaShow.title(List.of(p), ArenaShow.big("SIEG!", net.minecraft.ChatFormatting.GOLD),
                        Component.literal("Zeit " + formatTime(r.fightTicks) + "  |  Bestzeit " + formatTime(best(p, r.tier.id()))).withStyle(net.minecraft.ChatFormatting.YELLOW), 10, 80, 20);
                reward(p, level, r);
                ArenaFx.fanfare(p);
                ArenaFx.burst(level, p.getX(), p.getY() + 1.0, p.getZ(), 3, 5.0);
            } else if (r.endless) {
                endlessResult(p, level, r);
            } else {
                ArenaShow.title(List.of(p), ArenaShow.big("NIEDERLAGE", net.minecraft.ChatFormatting.DARK_RED),
                        Component.literal("Welle " + r.wave + " erreicht").withStyle(net.minecraft.ChatFormatting.GRAY), 10, 60, 20);
                p.sendSystemMessage(Component.translatable("msg.chronicle_arena.lost"));
                sendStats(p);
            }
        }
        ArenaPresentation.snapshot(server,r);
    }

    private static void endlessResult(ServerPlayer p, ServerLevel level, Run r) {
        int reached = r.completedWaves;
        int old = ArenaStats.endlessBest(p);
        boolean record = reached > old;
        if (record) ArenaStats.setEndlessBest(p, reached);
        ArenaShow.title(List.of(p), ArenaShow.big("ABGRUND: WELLE " + reached, net.minecraft.ChatFormatting.DARK_PURPLE),
                Component.literal(record ? "Neuer Rekord!" : "Rekord: Welle " + old).withStyle(net.minecraft.ChatFormatting.YELLOW), 10, 80, 20);
        if (r.tier.loot() != null) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, r.tier.loot());
            LootTable table = p.server.reloadableRegistries().getLootTable(key);
            int rolls = reached / 5;
            for (int i = 0; i < rolls; i++) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, p.position())
                        .create(LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params)) { ArenaPresentation.loot(r,p,stack); ItemHandlerHelper.giveItemToPlayer(p, stack); }
            }
            p.sendSystemMessage(Component.literal("Abgrund-Beute: " + rolls + " Kisten fuer " + reached + " Wellen."));
        }
        if (r.tier.xp() > 0 && reached > 0) { int xp=r.tier.xp()*reached/10; p.giveExperiencePoints(xp); r.rewardXp.put(p.getUUID(),xp); }
        ArenaStats.publish(p);
        sendStats(p);
    }

    private static void reward(ServerPlayer p, ServerLevel level, Run r) {
        ArenaDefs.Tier tier = r.tier;
        p.sendSystemMessage(Component.translatable("msg.chronicle_arena.won", r.encounter.name(), tier.name()));
        if (tier.loot() != null) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tier.loot());
            LootTable table = p.server.reloadableRegistries().getLootTable(key);
            int rolls = r.encounter.boss() ? Math.max(1, tier.bossRolls()) : 1;
            if (r.alive.contains(p.getUUID())) rolls++;
            for (int i = 0; i < rolls; i++) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, p.position())
                        .create(LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params)) {
                    ArenaPresentation.loot(r,p,stack);
                    ItemHandlerHelper.giveItemToPlayer(p, stack);
                }
            }
        }
        if (tier.xp() > 0) { p.giveExperiencePoints(tier.xp()); r.rewardXp.put(p.getUUID(),tier.xp()); }
        int coins = (int) Math.round(tier.tokens() * (r.encounter.boss() ? 2 : 1) * coinMult(r.mask) * r.coinPercent / 100.0);
        Challenge ch = dailyChallenge();
        if (ch != null && ch.encounter() == r.encounter && ch.tier() == r.tier && ch.mask() == r.mask
                && ArenaStats.challengeDay(p) != ch.day()) {
            ArenaStats.setChallengeDay(p, ch.day());
            int bonus = (int) Math.round(ch.bonus() * r.coinPercent / 100.0);
            coins += bonus;
            p.sendSystemMessage(Component.literal("Tages-Challenge geschafft: +" + bonus + " Bonus-Coins!"));
        }
        if (coins > 0) {
            ArenaStats.addCoins(p, coins);
            p.sendSystemMessage(Component.translatable("msg.chronicle_arena.tokens", coins));
        }
        ArenaStats.publish(p);
        sendStats(p);
        if (wins(p, tier.id()) == 1) {
            for (ArenaDefs.Tier other : ArenaDefs.tiers()) {
                if (tier.id().equals(other.requires())) {
                    p.sendSystemMessage(Component.translatable("msg.chronicle_arena.unlocked", other.name()));
                }
            }
        }
    }

    private static void endRun(MinecraftServer server) {
        Run r = run;
        run = null;
        if (r == null) return;
        r.bar.removeAllPlayers();
        ArenaObjectives.clear(server.getLevel(DIM), r.objective);
        ArenaPickups.clear(server.getLevel(DIM),r);
        discardMobs(server, r);
        ServerLevel level = server.getLevel(DIM);
        if (level != null) ArenaExtras.clearFountainLabels(server, level);
        Set<UUID> all = new HashSet<>(r.alive);
        all.addAll(r.spectators);
        for (UUID id : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || level == null || p.level().dimension() != DIM) continue;
            p.setCamera(p);
            ArenaRoutes.close(p, r);
            ArenaShow.clear(p);
            restoreMode(p);
            p.setHealth(p.getMaxHealth());
            teleport(p, level, LOBBY.getX() + 0.5, LOBBY.getY(), LOBBY.getZ() + 5.5, 180.0F);
            PacketDistributor.sendToPlayer(p,ArenaNet.Battle.EMPTY);
            sendStats(p);
            if(r.state==State.ENDING && r.participants.contains(id)) ArenaPresentation.show(p);
            else PacketDistributor.sendToPlayer(p,new ArenaNet.Blessings(r.id,r.wave,List.of(),List.of(),0,false));
        }
        LOGGER.info("Arena-Run beendet ({})", r.won ? "Sieg" : "Niederlage");
    }

    private static void discardMobs(MinecraftServer server, Run r) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        for (UUID id : r.mobs) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        r.mobs.clear();
    }

    /** Spieler verlaesst die Arena ueber Portal oder Befehl. */
    private static void leave(ServerPlayer sp) {
        removeFromRun(sp);
        restoreMode(sp);
        CompoundTag origin = sp.getPersistentData().getCompound(TAG_ORIGIN);
        ServerLevel target = null;
        double x;
        double y;
        double z;
        if (sp.getPersistentData().contains(TAG_ORIGIN) && origin.contains("dim")) {
            ResourceLocation dimId = ResourceLocation.tryParse(origin.getString("dim"));
            if (dimId != null) target = sp.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        }
        if (target != null) {
            x = origin.getDouble("x");
            y = origin.getDouble("y");
            z = origin.getDouble("z");
        } else {
            target = sp.server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        }
        sp.getPersistentData().remove(TAG_ORIGIN);
        teleport(sp, target, x, y, z, sp.getYRot());
    }

    private static void removeFromRun(ServerPlayer sp) {
        Run r = run;
        if (r == null) return;
        ArenaShow.clear(sp);
        PacketDistributor.sendToPlayer(sp,ArenaNet.Battle.EMPTY);
        ArenaRoutes.close(sp, r);
        PacketDistributor.sendToPlayer(sp,new ArenaNet.Blessings(r.id,r.wave,List.of(),List.of(),0,false));
        if (r.state != State.ENDING) ArenaShow.stopMusic(List.of(sp));
        boolean wasAlive = r.alive.remove(sp.getUUID());
        r.spectators.remove(sp.getUUID());
        r.fallen.remove(sp.getUUID());
        r.reviving.remove(sp.getUUID());
        r.killcamTarget.remove(sp.getUUID());
        r.killcamTicks.remove(sp.getUUID());
        sp.setCamera(sp);
        r.bar.removePlayer(sp);
        if (wasAlive && r.alive.isEmpty() && r.state != State.ENDING) {
            finish(sp.server, false);
        }
    }

    // ------------------------------------------------------------------ Tick

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;
        ArenaParty.tick(server);
        Run r = run;
        if (r != null) tickRun(server, r);
        if (run != null && tickCounter % 10 == 0) ArenaPresentation.sync(server,run);
        if (tickCounter % 20 == 0) safetyCheck(server);
        if (tickCounter % 20 == 7) for (ServerPlayer sm : server.getPlayerList().getPlayers()) ArenaSmith.apply(sm);
        if (tickCounter % 4 == 0) trails(server);
        if (tickCounter % 20 == 0 || tickCounter % 100 == 0) syncStats(server);
    }

    private void trails(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            String id = ArenaStats.trail(p);
            if (!id.isEmpty()) ArenaFx.spawnTrail(level, p, ArenaDefs.cosmeticValue(id));
        }
    }

    private void syncStats(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        boolean all = tickCounter % 100 == 0;
        for (ServerPlayer p : level.players()) {
            if (all || statsDirty.contains(p.getUUID())) {
                sendStats(p);
                if (statsDirty.contains(p.getUUID())) ArenaStats.publish(p);
            }
        }
        statsDirty.clear();
    }

    private void tickRun(MinecraftServer server, Run r) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) {
            run = null;
            return;
        }
        // Spieler, die offline gegangen sind oder die Dimension verlassen haben, rausnehmen.
        for (Iterator<UUID> it = r.alive.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.level().dimension() != DIM) {
                it.remove();
                if (p != null) { r.bar.removePlayer(p); ArenaRoutes.close(p, r); }
            }
        }
        tickKillcam(server, level, r);
        if (r.lossDelay > 0) {
            if (--r.lossDelay == 0) finish(server, false);
        } else if (r.alive.isEmpty() && r.state != State.ENDING) {
            finish(server, false);
        }

        // Never complete a wave or award a victory during the last player's killcam.
        if (r.alive.isEmpty() && r.state != State.ENDING) return;
        if (r.started && r.state != State.ENDING) r.fightTicks++;
        if (r.started && r.state != State.ENDING && tickCounter % 20 == 0) {
            boolean boss = r.state == State.BOSS;
            r.musicTimer -= 20;
            if (r.musicTimer <= 0 || boss != r.musicBoss) {
                List<ServerPlayer> pl = runPlayers(server, r);
                ArenaShow.stopMusic(pl);
                ArenaShow.playMusic(pl, boss ? ArenaShow.BOSS_MUSIC : ArenaShow.WAVE_MUSIC);
                r.musicBoss = boss;
                r.musicTimer = boss ? ArenaShow.BOSS_MUSIC_TICKS : ArenaShow.WAVE_MUSIC_TICKS;
            }
        }
        switch (r.state) {
            case PREP -> {
                if (!r.offer.isEmpty()) {
                    if (r.picked.containsAll(r.alive)) {
                        r.offer.clear();
                        r.timer = Math.min(r.timer, 60);
                    } else {
                        // Die Runde wartet, bis alle Lebenden gewaehlt haben (nach 2 Minuten waehlt der Zufall).
                        r.offerWait++;
                        if (r.offerWait > 2400) {
                            for (UUID id : new ArrayList<>(r.alive)) {
                                ServerPlayer lazy = server.getPlayerList().getPlayer(id);
                                if (lazy != null && !r.picked.contains(id)) chooseBlessing(lazy, 1 + level.getRandom().nextInt(r.offer.size()));
                            }
                        } else if (r.offerWait % 20 == 0) {
                            for(UUID id:r.alive){ServerPlayer bp=server.getPlayerList().getPlayer(id);if(bp!=null)openBlessings(bp,false);}
                            StringBuilder waiting = new StringBuilder();
                            for (UUID id : r.alive) {
                                ServerPlayer wp = server.getPlayerList().getPlayer(id);
                                if (wp != null && !r.picked.contains(id)) waiting.append(waiting.length() > 0 ? ", " : "").append(wp.getGameProfile().getName());
                            }
                            actionbar(server, r, Component.literal("Segen waehlen (Menue: /arena segen, alternativ /arena segen 1-" + r.offer.size() + ")  |  Warte auf: " + waiting)
                                    .withStyle(net.minecraft.ChatFormatting.AQUA));
                        }
                        break;
                    }
                }
                if (ArenaRoutes.tick(server, r)) break;
                if (r.started && r.timer > 0 && r.timer <= 60 && r.timer % 20 == 0) {
                    ArenaShow.title(runPlayers(server, r), ArenaShow.big(String.valueOf(r.timer / 20), net.minecraft.ChatFormatting.GOLD),
                            Component.literal("Naechste Welle"), 0, 18, 2);
                }
                if (r.timer == PREP_TICKS || r.timer % 20 == 0) {
                    int sec = Math.max(1, (r.timer + 19) / 20);
                    actionbar(server, r, Component.translatable("msg.chronicle_arena.countdown", sec));
                }
                if (--r.timer <= 0) beginNext(server, level, r);
            }
            case WAVE, BOSS -> tickFight(server, level, r);
            case ENDING -> {
                if (r.won && r.timer % 25 == 0 && r.timer > 40) {
                    for (UUID id : r.alive) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null) ArenaFx.burst(level, p.getX(), p.getY() + 1.0, p.getZ(), 2, 6.0);
                    }
                    ArenaFx.burst(level, r.center.getX() + 0.5, r.center.getY() + 4.0, r.center.getZ() + 0.5, 3, 14.0);
                }
                if (--r.timer <= 0) endRun(server);
            }
        }
    }

    private void beginNext(MinecraftServer server, ServerLevel level, Run r) {
        if (r.encounter.boss()) {
            r.started = true;
            r.state = State.BOSS;
            r.wave = 1;
            r.powerMul = computePower(server, r);
            r.queue.add(new Spawn(r.encounter.bossEntity(), false, true));
            ArenaPickups.spawn(level,r);ArenaPickups.spawn(level,r);
            r.bar.setName(Component.translatable("bar.chronicle_arena.boss", r.encounter.name()));
            r.bar.setColor(BossEvent.BossBarColor.RED);
            ArenaShow.title(runPlayers(server, r), ArenaShow.big("BOSSKAMPF", net.minecraft.ChatFormatting.DARK_RED),
                    Component.literal(String.valueOf(r.encounter.name())).withStyle(net.minecraft.ChatFormatting.RED), 10, 60, 20);
            return;
        }
        r.started = true;
        r.state = State.WAVE;
        r.wave++;
        buildWave(level, r);
        r.waveTotal = Math.max(1, r.queue.size());
        r.powerMul = computePower(server, r);
        r.objective = r.goalsEnabled && (r.routes.mission(r.wave) || r.wave % r.goalInterval == 0) ? ArenaObjectives.begin(level, r.center, r.lastGoal) : null;
        if(r.objective!=null){
            r.lastGoal=r.objective.kind;
            if(r.objective.kind==2)r.objective.expectedTargets=Math.min(3,Math.max(1,r.queue.size()));
        }
        r.offer.clear();
        ArenaPickups.spawn(level,r);ArenaPickups.spawn(level,r);
        r.event = ArenaExtras.rollEvent(level, r.wave, false);
        r.bar.setColor(r.event == ArenaExtras.EV_BLUTMOND ? BossEvent.BossBarColor.RED
                : r.event == ArenaExtras.EV_GEWITTER ? BossEvent.BossBarColor.YELLOW
                : r.event == ArenaExtras.EV_MONDSPRUNG ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PURPLE);
        if (r.event != ArenaExtras.EV_NONE) {
            for (ServerPlayer ep : runPlayers(server, r)) {
                ep.sendSystemMessage(Component.literal("EREIGNIS: " + ArenaExtras.eventName(r.event) + " - " + ArenaExtras.eventText(r.event))
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
        }
        ArenaShow.title(runPlayers(server, r), ArenaShow.big("WELLE " + r.wave + " / " + r.totalStr(),
                        r.miniThisWave ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.LIGHT_PURPLE),
                Component.literal(r.waveTotal + " Gegner" + (r.miniThisWave ? " - MINI-BOSS!" : "")
                        + (Math.abs(r.powerMul - 1.0) > 0.05 ? String.format(" | Staerke x%.2f", r.powerMul) : "")
                        + (r.event != ArenaExtras.EV_NONE ? " | " + ArenaExtras.eventName(r.event).toUpperCase() : "")).withStyle(
                        r.miniThisWave ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY), 8, 45, 12);
        r.bar.setName(Component.translatable("bar.chronicle_arena.wave", r.wave, r.totalStr()));
        actionbar(server, r, Component.translatable("msg.chronicle_arena.wave", r.wave, r.totalStr()));
    }

    private void buildWave(ServerLevel level, Run r) {
        ArenaDefs.Encounter e = r.encounter;
        int w = r.wave;
        r.miniThisWave = false;
        for (ArenaDefs.Pool p : e.pools()) {
            if (w < p.fromWave()) continue;
            double base = p.base() + (w - p.fromWave()) * p.perWave();
            int count = Math.max(1, (int) Math.round(base * r.countScale * r.growth(0.06, 0.05)));
            for (int i = 0; i < count; i++) r.queue.add(new Spawn(p.entity(), eliteRoll(level, r), false));
        }
        if (e.elite() != null && w % e.eliteEvery() == 0 && w < r.totalWaves) {
            r.queue.add(new Spawn(e.elite(), true, false));
        }
        // Gegner-Klassen ab Welle 3 und Schuetzen auf dem Balkon ab Welle 4.
        if (w >= 3) {
            float chance = Math.min(0.28F, 0.08F + 0.015F * w);
            for (Spawn sp : r.queue) {
                if (!sp.elite && !sp.boss && level.getRandom().nextFloat() < chance) sp.cls = 1 + level.getRandom().nextInt(3);
            }
        }
        if (w >= 4) {
            int shooters = Math.min(4, 1 + (w - 4) / 6);
            for (int i = 0; i < shooters; i++) {
                Spawn sh = new Spawn(ResourceLocation.withDefaultNamespace("skeleton"), false, false);
                sh.cls = ArenaExtras.CL_SCHUETZE;
                r.queue.add(sh);
            }
        }
        boolean mini = w > 1 && w < r.totalWaves && (r.totalWaves >= 15 ? w % 10 == 0 : w == (r.totalWaves + 1) / 2);
        boolean bigBoss = r.endless && w % 25 == 0 && e.finale() != null;
        if (mini && (e.elite() != null || !e.pools().isEmpty())) {
            Spawn m = new Spawn(e.elite() != null ? e.elite() : e.pools().get(0).entity(), true, false);
            m.mini = true;
            r.queue.add(m);
            r.miniThisWave = true;
        }
        if (bigBoss) {
            for (int i = 0; i < 2; i++) {
                Spawn m = new Spawn(e.finale(), true, false);
                m.mini = true;
                r.queue.add(m);
            }
            r.miniThisWave = true;
        }
        if (w == r.totalWaves && e.finale() != null) {
            r.queue.add(new Spawn(e.finale(), true, false));
        }
        // Mischen, damit nicht alle gleichen Mobs hintereinander kommen.
        java.util.Collections.shuffle(r.queue, new java.util.Random(level.getRandom().nextLong()));
    }

    private static final int REVIVE_TICKS = 80;

    private void tickRevive(MinecraftServer server, ServerLevel level, Run r) {
        if (r.fallen.isEmpty()) return;
        for (var it = r.fallen.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            UUID id = en.getKey();
            ServerPlayer down = server.getPlayerList().getPlayer(id);
            if (down == null || !r.spectators.contains(id)) {
                it.remove();
                r.reviving.remove(id);
                continue;
            }
            net.minecraft.world.phys.Vec3 pos = en.getValue();
            if (tickCounter % 6 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.3, pos.z, 4, 0.25, 0.6, 0.25, 0.01);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.x, pos.y + 1.6, pos.z, 1, 0.1, 0.4, 0.1, 0.0);
            }
            ServerPlayer helper = null;
            for (UUID aid : r.alive) {
                ServerPlayer a = server.getPlayerList().getPlayer(aid);
                if (a != null && a.isShiftKeyDown() && a.level() == level
                        && a.distanceToSqr(pos.x, pos.y, pos.z) <= 3.0 * 3.0) {
                    helper = a;
                    break;
                }
            }
            int prog = r.reviving.getOrDefault(id, 0);
            if (helper == null) {
                if (prog > 0) r.reviving.put(id, Math.max(0, prog - 2));
                continue;
            }
            prog++;
            if (prog >= REVIVE_TICKS && r.revives <= 0) {
                r.reviving.put(id, REVIVE_TICKS - 1);
                helper.displayClientMessage(Component.literal("Keine Wiederbelebungen mehr! Es gibt neue nach Welle 5, 10, ..."), true);
                continue;
            }
            if (prog >= REVIVE_TICKS) {
                r.revives--;
                r.reviving.remove(id);
                it.remove();
                r.stat(helper.getUUID())[2]++;
                r.spectators.remove(id);
                r.alive.add(id);
                down.setCamera(down);
                r.killcamTicks.remove(id);
                r.killcamTarget.remove(id);
                down.setGameMode(GameType.SURVIVAL);
                teleport(down, level, pos.x, pos.y, pos.z, down.getYRot());
                down.setHealth(Math.max(1.0F, down.getMaxHealth() * 0.5F));
                ArenaFx.burst(level, pos.x, pos.y + 1.0, pos.z, 2, 3.0);
                for (UUID pid : r.alive) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p != null) p.sendSystemMessage(Component.literal(down.getDisplayName().getString() + " wurde von " + helper.getDisplayName().getString() + " wiederbelebt!"));
                }
            } else {
                r.reviving.put(id, prog);
                helper.displayClientMessage(Component.literal("Wiederbeleben: " + down.getDisplayName().getString() + " " + (prog * 100 / REVIVE_TICKS) + "%"), true);
            }
        }
    }

    /** Gefallene sehen 5 Sekunden lang durch die Augen ihres Bezwingers. */
    private void tickKillcam(MinecraftServer server, ServerLevel level, Run r) {
        if (r.killcamTicks.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = r.killcamTicks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            Entity target = level.getEntity(r.killcamTarget.get(en.getKey()));
            if (sp == null || !r.spectators.contains(sp.getUUID())) {
                it.remove();
                continue;
            }
            int left = en.getValue() - 1;
            if (left <= 0 || target == null || !target.isAlive()) {
                sp.setCamera(sp);
                it.remove();
                continue;
            }
            if (sp.getCamera() != target) sp.setCamera(target);
            en.setValue(left);
        }
    }

    private void tickFight(MinecraftServer server, ServerLevel level, Run r) {
        // Lebende Mobs zaehlen.
        r.mobs.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });

        // Timed objectives retain pressure even when the original wave is cleared.
        if(ArenaObjectives.needsPressure(r.objective)&&r.fightTicks%80==0){
            int count=ArenaBalance.reinforcements(r.alive.size(),r.queue.size(),r.mobs.size(),Math.min(r.maxAlive(),12+4*r.alive.size()));
            List<ArenaDefs.Pool> pools=r.encounter.pools().stream().filter(p->p.fromWave()<=r.wave).toList();
            if(!pools.isEmpty())for(int i=0;i<count;i++)r.queue.add(new Spawn(pools.get(level.getRandom().nextInt(pools.size())).entity(),false,false));
            r.waveTotal=Math.max(r.waveTotal,r.queue.size()+r.mobs.size());
        }
        ArenaPickups.tick(level,r);
        // Nachspawnen.
        if (!r.queue.isEmpty() && tickCounter % 10 == 0) {
            int spawned = 0;
            while (!r.queue.isEmpty() && r.mobs.size() < r.maxAlive() && spawned < 4) {
                Spawn s = r.queue.remove(0);
                LivingEntity le = spawnMob(level, r, s);
                if (le == null) {
                    LOGGER.error("Arena: Gegner {} konnte nicht gespawnt werden; Kampf abgebrochen", s.entity);
                    actionbar(server, r, Component.literal("Gegner konnte nicht gespawnt werden. Kampf abgebrochen."));
                    finish(server, false);
                    return;
                }
                r.mobs.add(le.getUUID());
                if (s.boss) r.bossId = le.getUUID();
                spawned++;
            }
        }

        // Boss: bei halber Gesundheit kommen die Helfer.
        if (r.state == State.BOSS && r.bossId != null && !r.addsSpawned) {
            Entity boss = level.getEntity(r.bossId);
            if (boss instanceof LivingEntity b && b.getHealth() <= b.getMaxHealth() * 0.5F) {
                r.addsSpawned = true;
                for (ArenaDefs.Spawn a : r.encounter.adds()) {
                    int n = Math.max(1, (int) Math.round(a.count() * r.countScale));
                    for (int i = 0; i < n; i++) r.queue.add(new Spawn(a.entity(), eliteRoll(level, r), false));
                }
                if (!r.encounter.adds().isEmpty()) {
                    actionbar(server, r, Component.translatable("msg.chronicle_arena.adds"));
                }
            }
        }

        // Wiederbeleben: neben einem Gefallenen schleichen (ca. 4 Sekunden).
        tickRevive(server, level, r);
        ArenaExtras.tick(server, level, r, tickCounter);

        // Aggro halten.
        if (tickCounter % 40 == 0) {
            for (UUID id : r.mobs) {
                if (level.getEntity(id) instanceof Mob m) {
                    LivingEntity beacon=ArenaObjectives.defender(level,r.objective);
                    if(beacon!=null){m.setTarget(beacon);m.getNavigation().moveTo(beacon,1.0);continue;}
                    if(m.getTarget()!=null&&m.getTarget().isAlive())continue;
                    ServerPlayer target = nearestAlive(server, r, m.position());
                    if (target != null) m.setTarget(target);
                }
            }
        }

        // Gegner-Zaehler in der Bossleiste, Regeneration-Segen.
        if (tickCounter % 10 == 0) {
            int remaining = r.queue.size() + r.mobs.size();
            if (r.state == State.WAVE) {
                r.bar.setName(Component.literal("Welle " + r.wave + " / " + r.totalStr() + "  |  Gegner: " + remaining
                        + "  |  Wiederbelebungen: " + r.revives));
            } else {
                r.bar.setName(Component.literal("Boss: " + r.encounter.name() + (remaining > 1 ? "  |  Gegner: " + remaining : "")));
            }
        }
        for(UUID id:r.alive){
            int stacks=stack(r,id,ArenaShow.Blessing.REGEN);
            if(ArenaBalance.canRegen(stacks,r.fightTicks,r.lastDamage.getOrDefault(id,-1000),r.lastRegen.getOrDefault(id,0),(r.mask&MOD_NOHEAL)!=0)){
                ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null)p.heal(ArenaBalance.regenAmount(stack(r,id,ArenaShow.Blessing.ARMOR)>0));r.lastRegen.put(id,r.fightTicks);
            }
        }

        // Fortschrittsbalken.
        if (r.state == State.WAVE) {
            int left = r.queue.size() + r.mobs.size();
            r.bar.setProgress(Math.max(0.0F, Math.min(1.0F, left / (float) r.waveTotal)));
        } else if (r.bossId != null && level.getEntity(r.bossId) instanceof LivingEntity b) {
            r.bar.setProgress(Math.max(0.0F, Math.min(1.0F, b.getHealth() / b.getMaxHealth())));
        }

        if (r.objective != null) {
            ArenaObjectives.tick(server, level, r.objective, r.alive, r.mobs);
            if (tickCounter % 20 == 0) actionbar(server, r, Component.literal(ArenaObjectives.status(r.objective)));
            if (r.objective.failed) { finish(server, false); return; }
        }
        boolean cleared = r.queue.isEmpty() && r.mobs.isEmpty() && (r.objective == null || r.objective.complete());
        if (!cleared) return;

        if (r.state == State.WAVE && r.tier.tokensPerWave() > 0) {
            int basePerWave = (int) Math.round(r.tier.tokensPerWave() * coinMult(r.mask) * ArenaExtras.eventCoinMult(r.event) * r.coinPercent / 100.0 * (r.objective == null ? 1 : 1.25) * r.routes.coins(r.wave));
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                int perWave = (int) Math.round(basePerWave * (1.0 + 0.25 * stack(r, id, ArenaShow.Blessing.GOLD)))
                        + (r.miniThisWave ? basePerWave * 3 : 0);
                ArenaStats.addCoins(p, perWave);
                statsDirty.add(id);
                p.displayClientMessage(Component.translatable("msg.chronicle_arena.wave_tokens", r.wave, perWave), true);
            }
        }

        r.completedWaves = r.wave;
        ArenaRoutes.reward(server, r);
        ArenaObjectives.clear(level, r.objective);
        ArenaPickups.clear(level,r);
        r.objective = null;
        r.event = ArenaExtras.EV_NONE;
        r.classes.clear();
        if (r.state == State.BOSS) {
            finish(server, true);
        } else if (r.wave >= r.totalWaves) {
            finish(server, true);
        } else {
            // Kleine Pause, Spieler werden etwas geheilt.
            r.state = State.PREP;
            boolean offer = r.wave % 5 == 0;
            r.timer = offer ? 400 : 100;
            ArenaShow.title(runPlayers(server, r), ArenaShow.big("WELLE " + r.wave + " GESCHAFFT", net.minecraft.ChatFormatting.GREEN),
                    Component.literal(offer ? "Segen, +1 Wiederbelebung, danach Gruppenweg waehlen" : "Kurze Pause").withStyle(net.minecraft.ChatFormatting.GRAY), 5, 40, 10);
            if (offer) {
                offerBlessings(server, r);
                r.revives = Math.min(5, r.revives + 1);
            }
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && (r.mask & MOD_NOHEAL) == 0) p.heal(6.0F);
            }
            r.bar.setProgress(1.0F);
            r.bar.setName(Component.translatable("bar.chronicle_arena.pause", r.wave, r.totalStr()));
        }
    }

    private static boolean eliteRoll(ServerLevel level, Run r) {
        return (r.mask & MOD_ELITE) != 0 && level.getRandom().nextFloat() < 0.3F;
    }

    private static final java.util.regex.Pattern WORMS = java.util.regex.Pattern.compile("silverfish|endermite|worm|maggot|larva|grub|parasite|leech|mite");
    private static boolean spawningMob;

    private LivingEntity spawnMob(ServerLevel level, Run r, Spawn s) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.entity).orElse(null);
        if (type == null) return null;
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity le)) {
            if (entity != null) entity.discard();
            return null;
        }
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = s.boss ? 8.0 : 14.0 + level.getRandom().nextDouble() * 12.0;
        double x = r.center.getX() + 0.5 + Math.cos(angle) * dist;
        double z = r.center.getZ() + 0.5 + Math.sin(angle) * dist;
        double y = r.center.getY();
        if (s.cls == ArenaExtras.CL_SCHUETZE) {
            x = r.center.getX() + 0.5 + Math.cos(angle) * 35.6;
            z = r.center.getZ() + 0.5 + Math.sin(angle) * 35.6;
            y = r.center.getY() + 8.0;
        }
        le.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (le instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(le.blockPosition()), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
        }
        double h = r.adminHealth * r.healthScale * (1.0 + 0.25 * r.extra) * r.growth(0.04, 0.06) * r.powerMul * r.routes.health(r.wave)
                * (s.mini ? (r.extra == 0 ? 1.75 : 2.5) : s.elite ? 2.0 : 1.0);
        if (s.mini) {
            double cap = switch (r.tier.order()) {
                case 0 -> 200.0;
                case 1 -> 350.0;
                case 2 -> 600.0;
                case 3 -> 1000.0;
                default -> 1500.0 + 40.0 * r.wave;
            };
            double base = Math.max(1.0, le.getMaxHealth());
            if (base * h > cap) h = cap / base;
        }
        scale(le, Attributes.MAX_HEALTH, h);
        scale(le, Attributes.ATTACK_DAMAGE, r.adminDamage * r.damageScale * (1.0 + 0.08 * r.extra) * r.growth(0.02, 0.03) * r.routes.damage(r.wave) * (s.mini ? 1.15 : 1.0));
        scale(le, Attributes.MOVEMENT_SPEED, ArenaBalance.mobSpeed((r.mask & MOD_SPEED) != 0,
                s.cls == ArenaExtras.CL_BOMBE, r.event == ArenaExtras.EV_BLUTMOND));
        le.setHealth(le.getMaxHealth());
        if (s.elite || s.boss) le.setGlowingTag(true);
        if (s.cls != ArenaExtras.CL_NONE) {
            le.setCustomName(Component.literal(ArenaExtras.className(s.cls)).withStyle(ArenaExtras.classColor(s.cls)));
            le.setCustomNameVisible(true);
            if (s.cls == ArenaExtras.CL_SCHILD) {
                scaleAdd(le, Attributes.ARMOR, 12.0);
                scaleAdd(le, Attributes.KNOCKBACK_RESISTANCE, 0.9);
                le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
            } else if (s.cls == ArenaExtras.CL_BOMBE) {
                le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.TNT));
            }
            r.classes.put(le.getUUID(), s.cls);
        }
        if (s.mini) {
            le.setCustomName(Component.literal("Mini-Boss").withStyle(net.minecraft.ChatFormatting.RED));
            le.setCustomNameVisible(true);
        }
        spawningMob = true;
        boolean added;
        try { added = level.addFreshEntity(le); } finally { spawningMob = false; }
        if (!added) return null;
        ArenaObjectives.spawned(r.objective, le);
        if (le instanceof Mob mob) {
            LivingEntity target = ArenaObjectives.defender(level,r.objective);
            if(target==null)target=nearestAlive(level.getServer(),r,le.position());
            if (target != null) mob.setTarget(target);
        }
        return le;
    }

    private static void scaleAdd(LivingEntity le, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double add) {
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(ChronicleArena.id("class_" + attr.value().getDescriptionId()));
        inst.addPermanentModifier(new AttributeModifier(ChronicleArena.id("class_" + attr.value().getDescriptionId()), add,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static void scale(LivingEntity le, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double factor) {
        if (Math.abs(factor - 1.0) <= 0.0001) return;
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(ChronicleArena.id("scale"));
        inst.addPermanentModifier(new AttributeModifier(ChronicleArena.id("scale"), factor - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static ServerPlayer nearestAlive(MinecraftServer server, Run r, Vec3 pos) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;
            double d = p.distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Gegner-Anpassung an die Staerke der Lebenden: Ruestung, Angriff (Waffe, Skilltree, Segen) und Leben. */
    private static double computePower(MinecraftServer server, Run r) {
        double sum = 0;
        int n = 0;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            double armor = p.getAttributeValue(Attributes.ARMOR) + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double hp = p.getMaxHealth();
            sum += 0.35 * (1.0 + armor / 10.0) + 0.45 * (dmg / 5.0) + 0.2 * (hp / 20.0);
            n++;
        }
        if (n == 0) return 1.0;
        double power = sum / n;
        // 0,91 = Spieler mit Eisenwaffe ohne Ruestung; staerkere Spieler bekommen etwas mehr Leben auf der Gegnerseite.
        return Math.max(0.8, Math.min(1.8, Math.pow(power / 0.91, 0.7)));
    }

    private static List<ServerPlayer> runPlayers(MinecraftServer server, Run r) {
        List<ServerPlayer> out = new ArrayList<>();
        Set<UUID> all = new HashSet<>(r.alive);
        all.addAll(r.spectators);
        for (UUID id : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static int stack(Run r, UUID id, ArenaShow.Blessing b) {
        int[] st = r.bless.get(id);
        return st == null ? 0 : st[b.ordinal()];
    }

    private static void offerBlessings(MinecraftServer server, Run r) {
        List<ArenaShow.Blessing> all = new ArrayList<>(List.of(ArenaShow.Blessing.values()));
        java.util.Collections.shuffle(all);
        if ((r.mask & MOD_NOHEAL) != 0) all.removeIf(b -> b == ArenaShow.Blessing.REGEN || b == ArenaShow.Blessing.VAMPIR || b == ArenaShow.Blessing.ASH);
        r.offer = new ArrayList<>(all.subList(0, 3));
        r.picked.clear();
        r.offerWait = 0;
        for (UUID id : r.alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("Waehle einen Segen (gilt bis zum Ende des Runs):").withStyle(net.minecraft.ChatFormatting.AQUA));
            for (int i = 0; i < r.offer.size(); i++) p.sendSystemMessage(ArenaShow.offerLine(i, r.offer.get(i)));
            openBlessings(p,true);
        }
    }

    private static int chooseBlessing(ServerPlayer p, int slot) {
        Run r = run;
        if (r == null || r.state != State.PREP || p.level().dimension()!=DIM || !r.alive.contains(p.getUUID()) || r.offer.isEmpty() || r.picked.contains(p.getUUID())
                || slot < 1 || slot > r.offer.size()) {
            p.sendSystemMessage(Component.literal("Gerade gibt es keinen Segen zur Auswahl."));
            return 0;
        }
        ArenaShow.Blessing b = r.offer.get(slot - 1);
        int[] st = r.bless.computeIfAbsent(p.getUUID(), k -> new int[ArenaShow.Blessing.values().length]);
        st[b.ordinal()]++;
        ArenaShow.apply(p, st);
        r.picked.add(p.getUUID());
        statsDirty.add(p.getUUID());
        if (b == ArenaShow.Blessing.LIFE) r.revives = Math.min(9, r.revives + 1);
        openBlessings(p,false);
        p.sendSystemMessage(Component.literal("Segen: " + b.label + " (" + b.desc + ")").withStyle(net.minecraft.ChatFormatting.GOLD));
        return 1;
    }

    private static void actionbar(MinecraftServer server, Run r, Component msg) {
        Set<UUID> all = new HashSet<>(r.alive);
        all.addAll(r.spectators);
        for (UUID id : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.displayClientMessage(msg, true);
        }
    }

    /** Einmal pro Sekunde: Spieler im Abgrund, Zuschauer ausserhalb, Ueberbleibsel nach Neustart. */
    private void safetyCheck(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        ArenaExtras.deduplicateBoard(level);
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() && !p.isSpectator()) continue;
            boolean inRun = run != null && run.involves(p.getUUID());
            if (p.getY() < 40.0) {
                if (run != null && run.alive.contains(p.getUUID())) {
                    teleport(p, level, run.center.getX() + 0.5, run.center.getY(), run.center.getZ() + 0.5, 0.0F);
                } else {
                    teleport(p, level, LOBBY.getX() + 0.5, LOBBY.getY(), LOBBY.getZ() + 5.5, 180.0F);
                }
                continue;
            }
            if (p.isSpectator() && run != null && run.spectators.contains(p.getUUID())) {
                double dx = p.getX() - (run.center.getX() + 0.5);
                double dz = p.getZ() - (run.center.getZ() + 0.5);
                if (dx * dx + dz * dz > 46.0 * 46.0 || p.getY() > 95.0 || p.getY() < 55.0) {
                    teleport(p, level, run.center.getX() + 0.5, 80.0, run.center.getZ() + 0.5, 0.0F);
                }
            } else if (!inRun && p.isSpectator() && !p.isCreative()) {
                restoreMode(p);
            }
        }
    }

    // ------------------------------------------------------------------ Ereignisse

    /** Keine Vexe (schwer zu sehen, fliegen durch Waende) und keine unsichtbaren Gegner in der Arena. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getLevel().dimension() != DIM) return;
        if (event.getEntity() instanceof Vex) {
            event.setCanceled(true);
        } else if (!spawningMob && event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)
                && WORMS.matcher(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath()).find()) {
            // Keine Wuermer/Silberfischchen (z. B. aus Mod-Drops von Zombies) in der Arena.
            event.setCanceled(true);
        } else if (event.getEntity() instanceof LivingEntity le && !(le instanceof Player)) {
            le.removeEffect(MobEffects.INVISIBILITY);
            le.setInvisible(false);
        }
    }

    @SubscribeEvent
    public void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event){
        if(run!=null&&run.mobs.contains(event.getEntity().getUUID())&&event.getEntity().level() instanceof ServerLevel level){LivingEntity beacon=ArenaObjectives.defender(level,run.objective);if(beacon!=null)event.setNewAboutToBeSetTarget(beacon);}
    }
    @SubscribeEvent
    public void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event){
        if(event.getItemEntity().getTags().contains("chronicle_arena_pickup"))event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if(run!=null&&run.objective!=null&&event.getEntity().getUUID().equals(run.objective.defender)){
            if(event.getSource().getEntity()!=null&&run.mobs.contains(event.getSource().getEntity().getUUID()))ArenaObjectives.strike(run.objective,event.getSource().getEntity().getUUID(),event.getAmount());
            event.setCanceled(true);return;
        }
        if (run != null && run.mobs.contains(event.getEntity().getUUID())
                && event.getSource().getEntity() instanceof ServerPlayer attacker && run.alive.contains(attacker.getUUID())) {
            int fire = stack(run, attacker.getUUID(), ArenaShow.Blessing.FLARE);
            int hunter = stack(run, attacker.getUUID(), ArenaShow.Blessing.HUNTER);
            boolean ranged = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
            double bonus = (event.getEntity().isOnFire() ? fire * 0.15 : 0) + (ranged ? hunter * 0.15 : 0);
            event.setAmount((float)(event.getAmount() * (1 + bonus)));
        }
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().dimension() != DIM) return;
        // Kein PvP (auch nicht per Pfeil oder Wurfwaffe).
        if (event.getSource().getEntity() instanceof Player attacker && attacker != sp) {
            event.setCanceled(true);
            return;
        }
        Run r = run;
        boolean fighting = r != null && r.state != State.ENDING && r.alive.contains(sp.getUUID());
        if (!fighting) {
            // Lobby und Zuschauer sind unverwundbar (ausser /kill und Abgrund).
            if (!event.getSource().is(DamageTypes.GENERIC_KILL) && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
                event.setCanceled(true);
            }
            return;
        }
        if(event.getAmount()>0)r.lastDamage.put(sp.getUUID(),r.fightTicks);
        event.setAmount(ArenaPickups.shield(r,sp,event.getAmount()));
        if ((r.mask & MOD_GLASS) != 0) event.setAmount(event.getAmount() * 2.0F);
    }

    /** Called only after vanilla armor, shields, absorption and death-protection were applied. */
    private static void defeat(ServerPlayer sp, net.minecraft.world.damagesource.DamageSource source) {
        Run r = run;
        if (r == null) return;
        Entity slayer = source.getEntity();
        makeSpectator(sp);
        if (slayer instanceof LivingEntity && !(slayer instanceof Player) && slayer.isAlive()) {
            r.killcamTarget.put(sp.getUUID(), slayer.getUUID());
            r.killcamTicks.put(sp.getUUID(), 100);
            ArenaShow.title(List.of(sp), ArenaShow.big("BESIEGT", net.minecraft.ChatFormatting.DARK_RED),
                    Component.literal("von " + slayer.getDisplayName().getString()).withStyle(net.minecraft.ChatFormatting.GRAY), 5, 70, 15);
        }
        if (r.alive.isEmpty()) {
            if (r.killcamTicks.isEmpty()) finish(sp.server, false);
            else r.lossDelay = 105;
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != DIM) return;
        if (run != null && event.getEntity() instanceof ServerPlayer player && run.alive.contains(player.getUUID())
                && ArenaObjectives.interact(player, event.getPos(), run.objective)) {
            event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS); return;
        }
        if (!event.getPos().equals(ArenaDecor.SMITH_ANVIL)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer sp) ArenaSmith.open(sp);
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().dimension() != DIM) return;
        Run r = run;
        if (r != null && (r.mask & MOD_NOHEAL) != 0 && r.alive.contains(sp.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Run r = run;
        if (r == null || dead.level().dimension() != DIM) return;
        if (dead instanceof ServerPlayer sp) {
            if (r.state != State.ENDING && r.alive.contains(sp.getUUID())) {
                if (event.getSource().is(DamageTypes.GENERIC_KILL)) {
                    removeFromRun(sp);
                    restoreMode(sp);
                } else {
                    event.setCanceled(true);
                    defeat(sp, event.getSource());
                }
            }
            return;
        }
        if (dead instanceof Player) return;
        ArenaObjectives.killed(r.objective, dead.getUUID());
        if (event.getSource().getEntity() instanceof ServerPlayer killer && r.alive.contains(killer.getUUID())
                && r.mobs.contains(dead.getUUID())) {
            ArenaStats.addKill(killer);
            if ((r.mask & MOD_NOHEAL) == 0 && dead.isOnFire() && stack(r, killer.getUUID(), ArenaShow.Blessing.ASH) > 0) {
                killer.heal(2.0F * stack(r, killer.getUUID(), ArenaShow.Blessing.ASH));
            }
            statsDirty.add(killer.getUUID());
            r.stat(killer.getUUID())[1]++;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer sk && r.alive.contains(sk.getUUID())) {
            int fun = ArenaSmith.level(sk, ArenaSmith.LEBENSFUNKE);
            if (fun > 0 && (r.mask & MOD_NOHEAL) == 0) sk.heal(1.0F * fun);
        }
        Integer cls = r.classes.remove(dead.getUUID());
        if (cls != null && cls == ArenaExtras.CL_BOMBE && dead.level() instanceof ServerLevel dl) ArenaExtras.bombDeath(dl, r, dead);
    }

    @SubscribeEvent
    public void onDamageDone(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        Run r = run;
        if (r == null || !r.mobs.contains(event.getEntity().getUUID()) || event.getEntity().level().dimension() != DIM) return;
        if (event.getSource().getEntity() instanceof ServerPlayer p && r.alive.contains(p.getUUID())) {
            r.stat(p.getUUID())[0] += Math.round(event.getNewDamage());
            if(event.getNewDamage()>0)ArenaPickups.echo(p.serverLevel(),r,p,event.getEntity());
            int embers = stack(r, p.getUUID(), ArenaShow.Blessing.EMBER);
            if (embers > 0) event.getEntity().igniteForSeconds(2.0F + embers);
            int glut = ArenaSmith.level(p, ArenaSmith.GLUT);
            if (glut > 0) event.getEntity().igniteForSeconds(2.0F * glut);
            int vamp = stack(r, p.getUUID(), ArenaShow.Blessing.VAMPIR);
            if (vamp > 0 && (r.mask & MOD_NOHEAL) == 0) p.heal(event.getNewDamage() * 0.06F * vamp);
        }
    }

    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player) && event.getEntity().level().dimension() == DIM) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMobXp(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Player) && event.getEntity().level().dimension() == DIM) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (p.level().dimension() == DIM && !p.isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && p.level().dimension() == DIM && !p.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == DIM) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public void onGrief(EntityMobGriefingEvent event) {
        if (event.getEntity().level().dimension() == DIM) event.setCanGrief(false);
    }

    @SubscribeEvent
    public void onPearl(EntityTeleportEvent.EnderPearl event) {
        if (event.getPlayer().level().dimension() == DIM && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntity().level().dimension() == DIM && event.getEntity() instanceof Player p && !p.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag current = event.getEntity().getPersistentData();
        // NeoForge automatically retains only PlayerPersisted; these legacy arena keys live at the root.
        for (String key : List.of("chronicle_arena_stats", TAG_PROGRESS, TAG_ORIGIN, TAG_MODE)) {
            if (old.contains(key)) current.put(key, old.get(key).copy());
        }
        if (event.getOriginal() instanceof ServerPlayer sp && sp.level().dimension() == DIM) removeFromRun(sp);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getPersistentData().contains(TAG_MODE)) restoreMode(sp);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level().dimension() == DIM) {
            removeFromRun(sp);
        }
        ArenaPresentation.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level().dimension() == DIM
                && (run == null || !run.involves(sp.getUUID()))) {
            leave(sp);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == DIM && event.getEntity() instanceof ServerPlayer sp) {
            removeFromRun(sp);
            restoreMode(sp);
        }
    }

    /** Nur fuer den Entwicklungs-Selbsttest: gradle runServer -Dchronicle_arena.selftest=true. */
    @SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (Boolean.getBoolean("chronicle_arena.selftest")) {
            debug(event.getServer());
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("arena")
                .then(Commands.literal("bereit").executes(ctx -> { ArenaParty.toggle(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("gruppe").executes(ctx -> { ArenaParty.show(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("admin").requires(src -> src.hasPermission(2)).executes(ctx -> { openAdmin(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (p.level().dimension() != DIM) {
                        ctx.getSource().sendFailure(Component.translatable("msg.chronicle_arena.not_in_arena"));
                        return 0;
                    }
                    leave(p);
                    return 1;
                }))
                .then(Commands.literal("ergebnis").executes(ctx->{ArenaPresentation.show(ctx.getSource().getPlayerOrException());return 1;}))
                .then(Commands.literal("weg").executes(ctx -> { openRoutes(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("segen").executes(ctx->{openBlessings(ctx.getSource().getPlayerOrException(),true);return 1;}).then(Commands.argument("nr", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                        .executes(ctx -> chooseBlessing(ctx.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "nr")))))
                .then(Commands.literal("stats").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    for (ArenaDefs.Tier t : ArenaDefs.tiers()) {
                        int w = wins(p, t.id());
                        p.sendSystemMessage(Component.translatable("msg.chronicle_arena.stats", t.name(), w,
                                w == 0 ? "-" : formatTime(best(p, t.id()))));
                    }
                    p.sendSystemMessage(Component.literal("Abgrund-Rekord: Welle " + ArenaStats.endlessBest(p)));
                    p.sendSystemMessage(Component.literal("Coins: " + ArenaStats.coins(p) + ", Kills: " + ArenaStats.kills(p)
                            + ", Bosse: " + ArenaStats.bosses(p) + ", Siege: " + ArenaStats.wins(p)));
                    return 1;
                }))
                .then(Commands.literal("debug").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    debug(ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    if (run == null) {
                        ctx.getSource().sendFailure(Component.translatable("msg.chronicle_arena.nothing_running"));
                        return 0;
                    }
                    finish(ctx.getSource().getServer(), false);
                    return 1;
                })));
    }

    /** Nur fuer Vorschaubilder: schreibt die gebauten Bloecke in eine Textdatei (-Dchronicle_arena.dump=pfad). */
    private static void dumpBlocks(ServerLevel level) {
        String path = System.getProperty("chronicle_arena.dump");
        if (path == null || path.isEmpty()) return;
        try (java.io.PrintWriter w = new java.io.PrintWriter(path)) {
            int[][] boxes = {{-20, 55, -20, 82, 100, 20}, {-52, 55, 148, 52, 140, 252}, {-6, 0, -6, 6, 54, 6}, {-6, 0, 194, 6, 54, 206}, {-52, 55, 448, 52, 140, 552}, {-52, 55, 748, 52, 140, 852}};
            for (int[] b : boxes) {
                for (int x = b[0]; x <= b[3]; x++) for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) {
                    var st = level.getBlockState(new BlockPos(x, y, z));
                    if (st.isAir() || st.is(net.minecraft.world.level.block.Blocks.BARRIER)) continue;
                    w.println(x + " " + y + " " + z + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Dump fehlgeschlagen", e);
        }
    }

    /** Selbsttest: baut die Arena und spawnt jeden Mob jeder Begegnung einmal (dann wieder weg). */
    private static void debug(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) {
            LOGGER.error("ARENA-DEBUG: Dimension {} nicht gefunden", DIM.location());
            return;
        }
        ArenaBuilder.ensureBuilt(level);
        ArenaBuilder.ensureArena(level, ARENA_B, 1);
        ArenaBuilder.ensureArena(level, ARENA_C, 2);
        dumpBlocks(level);
        LOGGER.info("ARENA-DEBUG: Arena gebaut, Marker={}, Terminal={}, Portal={}",
                level.getBlockState(new BlockPos(0, 60, 200)).getBlock(),
                level.getBlockState(LOBBY).getBlock(),
                level.getBlockState(new BlockPos(0, 64, 8)).getBlock());
        List<ArenaDefs.Tier> tiers = ArenaDefs.tiers();
        LOGGER.info("ARENA-DEBUG: Musik Wellen={} Boss={}", BuiltInRegistries.SOUND_EVENT.containsKey(ArenaShow.WAVE_MUSIC), BuiltInRegistries.SOUND_EVENT.containsKey(ArenaShow.BOSS_MUSIC));
        LOGGER.info("ARENA-DEBUG: {} Stufen, {} Begegnungen verfuegbar", tiers.size(), ArenaDefs.encounters().size());
        if (tiers.isEmpty() || ArenaDefs.encounters().isEmpty()) {
            LOGGER.error("ARENA-DEBUG: Keine Stufen oder verfuegbaren Begegnungen fuer den Selbsttest");
            return;
        }
        Run dummy = new Run(ArenaDefs.encounters().get(0), tiers.get(tiers.size() - 1), 3, 15, 0);
        int ok = 0;
        int fail = 0;
        for (ArenaDefs.Encounter e : ArenaDefs.encounters()) {
            List<ResourceLocation> ids = new ArrayList<>();
            if (e.boss()) {
                ids.add(e.bossEntity());
                for (ArenaDefs.Spawn a : e.adds()) ids.add(a.entity());
            } else {
                for (ArenaDefs.Pool p : e.pools()) ids.add(p.entity());
                if (e.elite() != null) ids.add(e.elite());
                if (e.finale() != null) ids.add(e.finale());
            }
            for (ResourceLocation id : ids) {
                try {
                    LivingEntity le = new ArenaManager().spawnMob(level, dummy, new Spawn(id, true, false));
                    if (le == null) {
                        fail++;
                        LOGGER.error("ARENA-DEBUG: {} ({}) ergab null", id, e.id());
                    } else {
                        ok++;
                        le.discard();
                    }
                } catch (Throwable t) {
                    fail++;
                    LOGGER.error("ARENA-DEBUG: {} ({}) Fehler", id, e.id(), t);
                }
            }
        }
        LOGGER.info("ARENA-DEBUG: Spawn-Test fertig, ok={} fehler={}", ok, fail);
        // Klassen, Ereignisse, Brunnen/Fallen und Siegertafel einmal durchspielen.
        try {
            Run xr = new Run(ArenaDefs.encounters().get(0), tiers.get(0), 1, 0, 0);
            xr.wave = 6;
            new ArenaManager().buildWave(level, xr);
            int withClass = 0;
            for (Spawn sp : xr.queue) if (sp.cls != 0) withClass++;
            LOGGER.info("ARENA-DEBUG: Welle 6: {} Spawns, {} mit Klasse", xr.queue.size(), withClass);
            List<LivingEntity> made = new ArrayList<>();
            for (int cl = 1; cl <= 4; cl++) {
                Spawn sp = new Spawn(ResourceLocation.withDefaultNamespace("zombie"), false, false);
                sp.cls = cl;
                LivingEntity le = new ArenaManager().spawnMob(level, xr, sp);
                if (le == null) LOGGER.error("ARENA-DEBUG: Klasse {} ergab null", cl);
                else { made.add(le); xr.mobs.add(le.getUUID()); }
            }
            for (int ev = 1; ev <= 3; ev++) {
                xr.event = ev;
                for (int t = 0; t < 400; t += 10) ArenaExtras.tick(server, level, xr, t);
            }
            for (LivingEntity le : made) {
                if (xr.classes.get(le.getUUID()) != null && xr.classes.get(le.getUUID()) == ArenaExtras.CL_BOMBE) ArenaExtras.bombDeath(level, xr, le);
                le.discard();
            }
            xr.stat(java.util.UUID.randomUUID())[0] = 1234;
            ArenaExtras.updateBoard(server, xr, true);
            int boards = level.getEntities(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, e -> e.getTags().contains("chronicle_board")).size();
            LOGGER.info("ARENA-DEBUG: Siegertafel-Eintraege: {}", boards);
        } catch (Throwable t) {
            LOGGER.error("ARENA-DEBUG: Extras-Test Fehler", t);
        }

        // Verify objectives and escort navigation against the actual generated geometry in all variants.
        int routeOk = 0, routeFail = 0;
        for (BlockPos center : List.of(ARENA, ARENA_B, ARENA_C)) {
            Set<Integer> kinds = new HashSet<>();
            for (int attempt = 0; attempt < 80 && kinds.size() < 5; attempt++) {
                ArenaObjectives.Objective goal = ArenaObjectives.begin(level, center, -1);
                kinds.add(goal.kind);
                ArenaObjectives.clear(level, goal);
            }
            if (kinds.size() != 5) { routeFail++; LOGGER.error("ARENA-DEBUG: Zielarten bei {} fehlen: {}", center, kinds); }
            for (int turn = 0; turn < 4; turn++) {
                var guide = EntityType.VILLAGER.create(level);
                if (guide == null) { routeFail++; continue; }
                guide.setNoAi(true);guide.setInvulnerable(true);guide.moveTo(center.getX()+0.5,center.getY(),center.getZ()+0.5,0,0);
                if (!level.addFreshEntity(guide)) { routeFail++; continue; }
                boolean passed = true;int steps = 0;
                for (BlockPos point : ArenaObjectives.routePoints(center, turn)) {
                    Vec3 to = Vec3.atBottomCenterOf(point);
                    while (guide.position().distanceToSqr(to) >= 0.25 && ++steps <= 3600) {
                        if (!ArenaObjectives.stepGuide(level, guide, to)) { passed=false;break; }
                    }
                    if (!passed || steps > 3600) { passed=false;break; }
                }
                guide.discard();
                if (passed) routeOk++;
                else { routeFail++; LOGGER.error("ARENA-DEBUG: Begleitroute bei {} Orientierung {} blockiert", center, turn); }
            }
        }
        LOGGER.info("ARENA-DEBUG: Ziel-/Begleit-Test fertig, routen_ok={} fehler={}", routeOk, routeFail);

        int shopOk = 0;
        for (ArenaDefs.ShopOffer o : ArenaDefs.shopOffers()) {
            ItemStack st = o.stack(server.registryAccess());
            if (st.isEmpty()) LOGGER.error("ARENA-DEBUG: Shop {} leer", o.id());
            else shopOk++;
            if (o.components() != null && st.getEnchantments().isEmpty()
                    && !st.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) LOGGER.error("ARENA-DEBUG: Shop {} ohne Verzauberung", o.id());
        }
        LOGGER.info("ARENA-DEBUG: Shop {} von {} Angeboten ok", shopOk, ArenaDefs.shopOffers().size());
        LOGGER.info("ARENA-DEBUG: Shop-Block={}", level.getBlockState(SHOP).getBlock());
        for (ArenaDefs.ShopOffer o : ArenaDefs.shopOffers()) {
            if ("trail".equals(o.cosmetic()) && ArenaFx.trail(o.value()) == null) LOGGER.error("ARENA-DEBUG: Spur {} ohne Partikel", o.id());
        }
        Challenge ch = dailyChallenge();
        LOGGER.info("ARENA-DEBUG: Tages-Challenge {} / {} / {} (Bonus {}), Rangliste {} Eintraege", ch == null ? "-" : ch.encounter().id(),
                ch == null ? "-" : ch.tier().id(), ch == null ? 0 : modNames(ch.mask()), ch == null ? 0 : ch.bonus(), ArenaStats.top(server, 10).size());
    }

    // ------------------------------------------------------------------ Hilfen

    static boolean inLobby(ServerPlayer sp) {
        double dx = sp.getX() - (LOBBY.getX() + 0.5);
        double dz = sp.getZ() - (LOBBY.getZ() + 0.5);
        return sp.level().dimension() == DIM && dx * dx + dz * dz <= (LOBBY_RADIUS + 2) * (LOBBY_RADIUS + 2) && sp.getY() > 40;
    }

    private static List<ServerPlayer> playersInLobby(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return out;
        for (ServerPlayer p : level.players()) {
            if (inLobby(p) && !p.isSpectator()) out.add(p);
        }
        return out;
    }

    private static void teleport(ServerPlayer sp, ServerLevel level, double x, double y, double z, float yaw) {
        sp.teleportTo(level, x, y, z, yaw, 0.0F);
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
    }

    private static CompoundTag progress(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG_PROGRESS)) root.put(TAG_PROGRESS, new CompoundTag());
        return root.getCompound(TAG_PROGRESS);
    }

    static int wins(ServerPlayer p, ResourceLocation tier) {
        return progress(p).getCompound(tier.toString()).getInt("wins");
    }

    static int best(ServerPlayer p, ResourceLocation tier) {
        return progress(p).getCompound(tier.toString()).getInt("best");
    }

    static boolean unlocked(ServerPlayer p, ArenaDefs.Tier tier) {
        return tier.requires() == null || wins(p, tier.requires()) > 0;
    }

    private static void recordWin(ServerPlayer p, Run r) {
        CompoundTag pr = progress(p);
        String key = r.tier.id().toString();
        CompoundTag t = pr.getCompound(key);
        t.putInt("wins", t.getInt("wins") + 1);
        int best = t.getInt("best");
        if (best == 0 || r.fightTicks < best) t.putInt("best", Math.max(1, r.fightTicks));
        pr.put(key, t);
        ArenaStats.addWin(p, r.encounter.boss());
    }

    static String formatTime(int ticks) {
        int sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    /** Alte Arena-Marken aus dem Inventar in Coins umwandeln (1:1). */
    static void convertTokens(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) {
            if (st.is(ModRegistry.TOKEN.get())) {
                n += st.getCount();
                st.setCount(0);
            }
        }
        for (ItemStack st : p.getInventory().offhand) {
            if (st.is(ModRegistry.TOKEN.get())) {
                n += st.getCount();
                st.setCount(0);
            }
        }
        if (n > 0) {
            ArenaStats.addCoins(p, n);
            p.getInventory().setChanged();
            p.sendSystemMessage(Component.literal(n + " Arena-Marken wurden in Coins umgewandelt."));
        }
    }

    /** Arena-Rang aus den insgesamt verdienten Coins; beim Aufstieg gibt es Bonus-Coins und eine Meldung. */
    private static void checkRank(ServerPlayer p) {
        int now = ArenaStats.rankFor(ArenaStats.earned(p));
        int old = ArenaStats.rankIdx(p);
        if (now <= old) return;
        ArenaStats.setRankIdx(p, now);
        int bonus = ArenaStats.RANK_BONUS[now];
        ArenaStats.addCoins(p, bonus);
        ArenaShow.title(List.of(p), ArenaShow.big("RANG: " + ArenaStats.RANKS[now].toUpperCase(), net.minecraft.ChatFormatting.GOLD),
                Component.literal("+" + bonus + " Coins").withStyle(net.minecraft.ChatFormatting.YELLOW), 10, 70, 20);
        p.sendSystemMessage(Component.literal("Neuer Arena-Rang: " + ArenaStats.RANKS[now] + " (+" + bonus + " Coins)")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        if (p.level() instanceof ServerLevel sl) ArenaFx.burst(sl, p.getX(), p.getY() + 1.0, p.getZ(), 3, 4.0);
        checkRank(p);
    }

    /** Kurze Liste der aktiven Verstaerkungen (Schmied und Segen dieses Laufs) fuer die Anzeige. */
    private static List<String> perkLines(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (ArenaSmith.Up u : ArenaSmith.UPS) {
            int lv = ArenaSmith.level(p, u.id());
            if (lv > 0) out.add("S " + u.name() + " " + lv);
        }
        Run r = run;
        int[] st = r == null || !r.involves(p.getUUID()) ? null : r.bless.get(p.getUUID());
        if (st != null) {
            for (ArenaShow.Blessing b : ArenaShow.Blessing.values()) {
                if (b.ordinal() < st.length && st[b.ordinal()] > 0) out.add("+ " + b.label + " x" + st[b.ordinal()]);
            }
        }
        return out;
    }

    static void sendStats(ServerPlayer p) {
        checkRank(p);
        String titleId = ArenaStats.title(p);
        List<Net.BoardLine> board = new ArrayList<>();
        for (ArenaStats.Line l : ArenaStats.top(p.server, 10)) {
            board.add(new Net.BoardLine(l.name(), l.title(), l.wins(), l.bosses(), l.kills(), l.endless()));
        }
        int rk = ArenaStats.rankFor(ArenaStats.earned(p));
        PacketDistributor.sendToPlayer(p, new Net.Stats(ArenaStats.coins(p), ArenaStats.kills(p), ArenaStats.bosses(p),
                ArenaStats.wins(p), titleId.isEmpty() ? "" : ArenaDefs.cosmeticName(titleId), titleId, ArenaStats.trail(p),
                ArenaStats.owned(p), board, true, ArenaStats.RANKS[rk], ArenaStats.earned(p),
                rk + 1 < ArenaStats.RANK_AT.length ? ArenaStats.RANK_AT[rk + 1] : 0, perkLines(p)));
    }

    private static void rememberMode(ServerPlayer sp) {
        CompoundTag tag = sp.getPersistentData();
        if (!tag.contains(TAG_MODE)) tag.putInt(TAG_MODE, sp.gameMode.getGameModeForPlayer().getId());
    }

    private static void restoreMode(ServerPlayer sp) {
        CompoundTag tag = sp.getPersistentData();
        if (tag.contains(TAG_MODE)) {
            sp.setGameMode(GameType.byId(tag.getInt(TAG_MODE)));
            tag.remove(TAG_MODE);
        } else if (sp.isSpectator()) {
            sp.setGameMode(GameType.SURVIVAL);
        }
    }

    public static boolean useRune(ServerPlayer p,int kind){
        Run r=run;
        if(r==null||p.level().dimension()!=DIM||!r.alive.contains(p.getUUID())||(r.state!=State.WAVE&&r.state!=State.BOSS)||kind<0||kind>2){p.sendSystemMessage(Component.literal("Runen sind nur im laufenden Arena-Kampf verwendbar."));return false;}
        if(r.runeReady.getOrDefault(p.getUUID(),0)>r.fightTicks)return false;
        r.runeReady.put(p.getUUID(),r.fightTicks+80);ArenaPickups.activate(r,p,kind);return true;
    }
    public static void pick(ServerPlayer p,ArenaNet.Pick choice){
        Run r=run;
        if(r!=null && r.id.equals(choice.run()) && r.wave==choice.wave()) chooseBlessing(p,choice.slot());
    }
    public static void openRoutes(ServerPlayer p) {
        Run r = run;
        if (r == null || r.state != State.PREP || !r.routes.voting() || !r.alive.contains(p.getUUID()) || p.level().dimension() != DIM) {
            p.sendSystemMessage(Component.literal("Gerade keine Wege-Abstimmung aktiv. Sie startet nach dem Segen jeder fuenften Welle."));
            return;
        }
        r.routes.refresh(r.alive);
        ArenaRoutes.send(p, r, true);
    }

    public static void voteRoute(ServerPlayer p, ArenaNet.RouteVote vote) {
        Run r = run;
        if (r == null || r.state != State.PREP || p.level().dimension() != DIM || !r.alive.contains(p.getUUID())) return;
        r.routes.refresh(r.alive);
        if (r.routes.cast(p.getUUID(), vote.run(), vote.wave(), vote.slot())) ArenaRoutes.send(p, r, false);
    }

    public static void openBlessings(ServerPlayer p,boolean popup){
        Run r=run;
        if(r==null||p.level().dimension()!=DIM||!r.involves(p.getUUID()))return;
        List<ArenaNet.BlessInfo> offers=new ArrayList<>(),active=new ArrayList<>();
        int[] stacks=r.bless.getOrDefault(p.getUUID(),new int[ArenaShow.Blessing.values().length]);
        for(ArenaShow.Blessing b:ArenaShow.Blessing.values())if(stacks[b.ordinal()]>0)active.add(blessInfo(b,stacks));
        if(r.state==State.PREP&&r.alive.contains(p.getUUID())&&!r.picked.contains(p.getUUID()))for(ArenaShow.Blessing b:r.offer)offers.add(blessInfo(b,stacks));
        PacketDistributor.sendToPlayer(p,new ArenaNet.Blessings(r.id,r.wave,offers,active,Math.max(0,(2400-r.offerWait+19)/20),popup));
    }
    private static ArenaNet.BlessInfo blessInfo(ArenaShow.Blessing b,int[] stacks){
        String icon=switch(b){case POWER->"iron_sword";case VITALITY->"golden_apple";case SWIFT->"feather";case ARMOR->"iron_chestplate";case REGEN->"glistering_melon_slice";case GOLD->"gold_ingot";case VAMPIR->"redstone";case LIFE->"totem_of_undying";case EMBER->"blaze_powder";case FLARE->"fire_charge";case ASH->"coal";case HUNTER->"bow";};
        String synergy=switch(b){
            case EMBER->"Passt zu Flammenjaeger / Asche";
            case FLARE,ASH->stacks[ArenaShow.Blessing.EMBER.ordinal()]>0?"Synergie mit deiner Glut aktiv":"Passt zu Glut";
            case ARMOR->stacks[ArenaShow.Blessing.REGEN.ordinal()]>0?"Synergie mit deiner Regeneration aktiv":"Passt zu Regeneration";
            case REGEN->stacks[ArenaShow.Blessing.ARMOR.ordinal()]>0?"Synergie mit deiner Panzerung aktiv":"Passt zu Panzerung";
            case HUNTER->stacks[ArenaShow.Blessing.VAMPIR.ordinal()]>0?"Mehr Schaden verbessert deinen Vampir":"Passt zu Vampir";
            case VAMPIR->"Profitiert von Kraft / Scharfschuetze";
            default->"";
        };
        return new ArenaNet.BlessInfo(b.label,b.desc,"minecraft:"+icon,stacks[b.ordinal()],synergy);
    }
    public static void control(ServerPlayer p, Net.Control control) {
        switch (control.action()) {
            case "ready" -> ArenaParty.toggle(p);
            case "cancel" -> ArenaParty.cancel(p);
            case "decline" -> ArenaParty.decline(p);
            case "admin" -> openAdmin(p);
            case "blessings" -> openBlessings(p,true);
        }
    }
    public static void openAdmin(ServerPlayer p) {
        if (!p.hasPermissions(2)) return;
        ArenaSettings s = ArenaSettings.get(p.server);
        PacketDistributor.sendToPlayer(p, new Net.OpenAdmin(s.health, s.damage, s.coins, s.prepSeconds, s.goalInterval, s.goals,
                run == null ? "Kein Kampf aktiv" : "Kampf: " + run.encounter.name() + " / Welle " + run.wave));
    }
    public static void adminChange(ServerPlayer p, Net.AdminChange change) {
        if (!p.hasPermissions(2)) return;
        if (change.stop()) { ArenaParty.clear(p.server, "Vom Admin abgebrochen."); if (run != null) endRun(p.server); }
        else {
            ArenaSettings s = ArenaSettings.get(p.server);
            s.health = ArenaSettings.clamp(change.health(),25,300); s.damage = ArenaSettings.clamp(change.damage(),25,300);
            s.coins = ArenaSettings.clamp(change.coins(),0,300); s.prepSeconds = ArenaSettings.clamp(change.prep(),3,60);
            s.goalInterval = ArenaSettings.clamp(change.interval(),2,10); s.goals = change.goals(); s.setDirty();
            p.sendSystemMessage(Component.literal("Arena-Einstellungen gespeichert. Gelten fuer neue Kaempfe."));
        }
        LOGGER.info("Arena-Admin {}: {}", p.getGameProfile().getName(), change);
        openAdmin(p);
    }
    @SubscribeEvent public void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ArenaParty.clear(event.getServer(), "Server wird beendet.");
        if (run != null) endRun(event.getServer());
    }
    @SubscribeEvent public void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        run = null; ArenaPresentation.clear(); ArenaParty.clear(event.getServer(), "Server beendet."); statsDirty.clear(); tickCounter = 0;
    }
}
