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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
    static final BlockPos SHOP = new BlockPos(60, 64, 0);
    static final int LOBBY_RADIUS = 12;
    static final int SHOP_RADIUS = 12;
    static final int ARENA_RADIUS = 26;
    static final int MAX_ALIVE_MOBS = 30;
    static final int PREP_TICKS = 100;
    static final int END_TICKS = 200;

    private static final String TAG_ORIGIN = "chronicle_arena_origin";
    private static final String TAG_MODE = "chronicle_arena_mode";
    private static final String TAG_PROGRESS = "chronicle_arena_progress";

    private static Run run;
    private static int tickCounter;

    // ------------------------------------------------------------------ Run

    private enum State { PREP, WAVE, BOSS, ENDING }

    private static final class Spawn {
        final ResourceLocation entity;
        final boolean elite;
        final boolean boss;

        Spawn(ResourceLocation entity, boolean elite, boolean boss) {
            this.entity = entity;
            this.elite = elite;
            this.boss = boss;
        }
    }

    private static final class Run {
        final ArenaDefs.Encounter encounter;
        final ArenaDefs.Tier tier;
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Set<UUID> participants = new HashSet<>();
        final List<UUID> mobs = new ArrayList<>();
        final List<Spawn> queue = new ArrayList<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        final double healthScale;
        final double damageScale;
        final double countScale;
        final int totalWaves;
        State state = State.PREP;
        int wave;
        int timer = PREP_TICKS;
        int waveTotal;
        UUID bossId;
        boolean addsSpawned;
        boolean won;
        boolean started;
        int fightTicks;

        Run(ArenaDefs.Encounter encounter, ArenaDefs.Tier tier, int players) {
            this.encounter = encounter;
            this.tier = tier;
            int extra = Math.max(0, players - 1);
            boolean waves = !encounter.boss();
            this.healthScale = tier.health() * (waves ? tier.waveHealth() : 1.0) * (1.0 + 0.5 * extra);
            this.damageScale = tier.damage() * (waves ? tier.waveDamage() : 1.0) * (1.0 + 0.1 * extra);
            this.countScale = tier.count() * (waves ? tier.waveCount() : 1.0) * (1.0 + 0.4 * extra);
            this.totalWaves = encounter.boss() ? 1 : tier.waves();
            bar.setProgress(1.0F);
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
    }

    public static void openTerminal(ServerPlayer sp) {
        if (sp.level().dimension() != DIM) return;
        List<Net.TierInfo> tiers = new ArrayList<>();
        for (ArenaDefs.Tier t : ArenaDefs.tiers()) {
            int wins = wins(sp, t.id());
            int best = best(sp, t.id());
            String info = wins == 0 ? "" : Component.translatable("gui.chronicle_arena.tier_info", wins, formatTime(best)).getString();
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
        PacketDistributor.sendToPlayer(sp, new Net.OpenTerminal(run != null, status, tiers, encs));
    }

    public static void openShop(ServerPlayer sp) {
        if (sp.level().dimension() != DIM) return;
        List<Net.ShopInfo> shop = new ArrayList<>();
        for (ArenaDefs.ShopOffer o : ArenaDefs.shopOffers()) {
            ItemStack stack = o.stack(sp.server.registryAccess());
            if (stack.isEmpty()) continue;
            shop.add(new Net.ShopInfo(o.id().toString(), o.category(), o.name(), o.price(), stack));
        }
        PacketDistributor.sendToPlayer(sp, new Net.OpenShop(countTokens(sp), shop));
    }

    public static void handleBuy(ServerPlayer sp, Net.Buy buy) {
        if (sp.level().dimension() != DIM) return;
        ArenaDefs.ShopOffer offer = ArenaDefs.shopOffer(ResourceLocation.tryParse(buy.offer()));
        if (offer == null) return;
        if (countTokens(sp) < offer.price()) {
            sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.not_enough", offer.price()));
            return;
        }
        removeTokens(sp, offer.price());
        ItemHandlerHelper.giveItemToPlayer(sp, offer.stack(sp.server.registryAccess()));
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.bought", offer.name(), offer.price()));
        PacketDistributor.sendToPlayer(sp, new Net.Tokens(countTokens(sp)));
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
        startRun(sp.server, enc, tier, group);
    }

    // ------------------------------------------------------------------ Start und Ende

    private static void startRun(MinecraftServer server, ArenaDefs.Encounter enc, ArenaDefs.Tier tier, List<ServerPlayer> group) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) return;
        ArenaBuilder.ensureBuilt(level);
        Run r = new Run(enc, tier, group.size());
        run = r;
        int i = 0;
        for (ServerPlayer p : group) {
            rememberMode(p);
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.removeAllEffects();
            double angle = (Math.PI * 2.0 * i) / group.size();
            teleport(p, level, ARENA.getX() + 0.5 + Math.cos(angle) * 4.0, ARENA.getY(),
                    ARENA.getZ() + 0.5 + Math.sin(angle) * 4.0, 0.0F);
            r.alive.add(p.getUUID());
            r.participants.add(p.getUUID());
            r.bar.addPlayer(p);
            p.sendSystemMessage(Component.translatable("msg.chronicle_arena.start", enc.name(), tier.name(), group.size()));
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
        teleport(sp, level, ARENA.getX() + 0.5, 80.0, ARENA.getZ() + 0.5, 0.0F);
        sp.sendSystemMessage(Component.translatable("msg.chronicle_arena.spectating"));
    }

    private static void makeSpectator(ServerPlayer sp) {
        Run r = run;
        if (r == null) return;
        r.alive.remove(sp.getUUID());
        r.spectators.add(sp.getUUID());
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
        r.state = State.ENDING;
        r.timer = END_TICKS;
        r.won = won;
        discardMobs(server, r);
        r.queue.clear();
        r.bar.setProgress(won ? 1.0F : 0.0F);
        r.bar.setName(Component.translatable(won ? "bar.chronicle_arena.won" : "bar.chronicle_arena.lost"));
        ServerLevel level = server.getLevel(DIM);
        for (UUID id : r.participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || level == null) continue;
            if (won) {
                recordWin(p, r);
                reward(p, level, r);
            } else {
                p.sendSystemMessage(Component.translatable("msg.chronicle_arena.lost"));
            }
        }
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
                    ItemHandlerHelper.giveItemToPlayer(p, stack);
                }
            }
        }
        if (tier.xp() > 0) p.giveExperiencePoints(tier.xp());
        int tokens = tier.tokens() * (r.encounter.boss() ? 2 : 1);
        if (tokens > 0) {
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModRegistry.TOKEN.get(), tokens));
            p.sendSystemMessage(Component.translatable("msg.chronicle_arena.tokens", tokens));
        }
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
        discardMobs(server, r);
        ServerLevel level = server.getLevel(DIM);
        Set<UUID> all = new HashSet<>(r.alive);
        all.addAll(r.spectators);
        for (UUID id : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || level == null || p.level().dimension() != DIM) continue;
            restoreMode(p);
            p.setHealth(p.getMaxHealth());
            teleport(p, level, LOBBY.getX() + 0.5, LOBBY.getY(), LOBBY.getZ() + 5.5, 180.0F);
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
        boolean wasAlive = r.alive.remove(sp.getUUID());
        r.spectators.remove(sp.getUUID());
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
        Run r = run;
        if (r != null) tickRun(server, r);
        if (tickCounter % 20 == 0) safetyCheck(server);
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
                if (p != null) r.bar.removePlayer(p);
            }
        }
        if (r.alive.isEmpty() && r.state != State.ENDING) {
            finish(server, false);
        }

        if (r.started && r.state != State.ENDING) r.fightTicks++;
        switch (r.state) {
            case PREP -> {
                if (r.timer == PREP_TICKS || r.timer % 20 == 0) {
                    int sec = Math.max(1, (r.timer + 19) / 20);
                    actionbar(server, r, Component.translatable("msg.chronicle_arena.countdown", sec));
                }
                if (--r.timer <= 0) beginNext(server, level, r);
            }
            case WAVE, BOSS -> tickFight(server, level, r);
            case ENDING -> {
                if (--r.timer <= 0) endRun(server);
            }
        }
    }

    private void beginNext(MinecraftServer server, ServerLevel level, Run r) {
        if (r.encounter.boss()) {
            r.started = true;
            r.state = State.BOSS;
            r.wave = 1;
            r.queue.add(new Spawn(r.encounter.bossEntity(), false, true));
            r.bar.setName(Component.translatable("bar.chronicle_arena.boss", r.encounter.name()));
            r.bar.setColor(BossEvent.BossBarColor.RED);
            return;
        }
        r.started = true;
        r.state = State.WAVE;
        r.wave++;
        buildWave(level, r);
        r.waveTotal = Math.max(1, r.queue.size());
        r.bar.setColor(BossEvent.BossBarColor.PURPLE);
        r.bar.setName(Component.translatable("bar.chronicle_arena.wave", r.wave, r.totalWaves));
        actionbar(server, r, Component.translatable("msg.chronicle_arena.wave", r.wave, r.totalWaves));
    }

    private void buildWave(ServerLevel level, Run r) {
        ArenaDefs.Encounter e = r.encounter;
        int w = r.wave;
        for (ArenaDefs.Pool p : e.pools()) {
            if (w < p.fromWave()) continue;
            double base = p.base() + (w - p.fromWave()) * p.perWave();
            int count = Math.max(1, (int) Math.round(base * r.countScale));
            for (int i = 0; i < count; i++) r.queue.add(new Spawn(p.entity(), false, false));
        }
        if (e.elite() != null && w % e.eliteEvery() == 0 && w < r.totalWaves) {
            r.queue.add(new Spawn(e.elite(), true, false));
        }
        if (w == r.totalWaves && e.finale() != null) {
            r.queue.add(new Spawn(e.finale(), true, false));
        }
        // Mischen, damit nicht alle gleichen Mobs hintereinander kommen.
        java.util.Collections.shuffle(r.queue, new java.util.Random(level.getRandom().nextLong()));
    }

    private void tickFight(MinecraftServer server, ServerLevel level, Run r) {
        // Lebende Mobs zaehlen.
        r.mobs.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });

        // Nachspawnen.
        if (!r.queue.isEmpty() && tickCounter % 10 == 0) {
            int spawned = 0;
            while (!r.queue.isEmpty() && r.mobs.size() < MAX_ALIVE_MOBS && spawned < 4) {
                Spawn s = r.queue.remove(0);
                LivingEntity le = spawnMob(level, r, s);
                if (le != null) {
                    r.mobs.add(le.getUUID());
                    if (s.boss) r.bossId = le.getUUID();
                }
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
                    for (int i = 0; i < n; i++) r.queue.add(new Spawn(a.entity(), false, false));
                }
                if (!r.encounter.adds().isEmpty()) {
                    actionbar(server, r, Component.translatable("msg.chronicle_arena.adds"));
                }
            }
        }

        // Aggro halten.
        if (tickCounter % 40 == 0) {
            for (UUID id : r.mobs) {
                if (level.getEntity(id) instanceof Mob m && (m.getTarget() == null || !m.getTarget().isAlive())) {
                    ServerPlayer target = nearestAlive(server, r, m.position());
                    if (target != null) m.setTarget(target);
                }
            }
        }

        // Fortschrittsbalken.
        if (r.state == State.WAVE) {
            int left = r.queue.size() + r.mobs.size();
            r.bar.setProgress(Math.max(0.0F, Math.min(1.0F, left / (float) r.waveTotal)));
        } else if (r.bossId != null && level.getEntity(r.bossId) instanceof LivingEntity b) {
            r.bar.setProgress(Math.max(0.0F, Math.min(1.0F, b.getHealth() / b.getMaxHealth())));
        }

        boolean cleared = r.queue.isEmpty() && r.mobs.isEmpty();
        if (!cleared) return;

        if (r.state == State.WAVE && r.tier.tokensPerWave() > 0) {
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModRegistry.TOKEN.get(), r.tier.tokensPerWave()));
                p.displayClientMessage(Component.translatable("msg.chronicle_arena.wave_tokens", r.wave, r.tier.tokensPerWave()), true);
            }
        }

        if (r.state == State.BOSS) {
            finish(server, true);
        } else if (r.wave >= r.totalWaves) {
            finish(server, true);
        } else {
            // Kleine Pause, Spieler werden etwas geheilt.
            r.state = State.PREP;
            r.timer = 100;
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) p.heal(6.0F);
            }
            r.bar.setProgress(1.0F);
            r.bar.setName(Component.translatable("bar.chronicle_arena.pause", r.wave, r.totalWaves));
        }
    }

    private LivingEntity spawnMob(ServerLevel level, Run r, Spawn s) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.entity).orElse(null);
        if (type == null) return null;
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity le)) {
            if (entity != null) entity.discard();
            return null;
        }
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = s.boss ? 8.0 : 13.0 + level.getRandom().nextDouble() * 6.5;
        double x = ARENA.getX() + 0.5 + Math.cos(angle) * dist;
        double z = ARENA.getZ() + 0.5 + Math.sin(angle) * dist;
        le.moveTo(x, ARENA.getY(), z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (le instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(le.blockPosition()), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
        }
        double h = r.healthScale * (s.elite ? 2.0 : 1.0);
        scale(le, Attributes.MAX_HEALTH, h);
        scale(le, Attributes.ATTACK_DAMAGE, r.damageScale);
        le.setHealth(le.getMaxHealth());
        if (s.elite || s.boss) le.setGlowingTag(true);
        level.addFreshEntity(le);
        if (le instanceof Mob mob) {
            ServerPlayer target = nearestAlive(level.getServer(), r, le.position());
            if (target != null) mob.setTarget(target);
        }
        return le;
    }

    private static void scale(LivingEntity le, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double factor) {
        if (factor <= 1.0001) return;
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
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() && !p.isSpectator()) continue;
            boolean inRun = run != null && run.involves(p.getUUID());
            if (p.getY() < 40.0) {
                if (run != null && run.alive.contains(p.getUUID())) {
                    teleport(p, level, ARENA.getX() + 0.5, ARENA.getY(), ARENA.getZ() + 0.5, 0.0F);
                } else {
                    teleport(p, level, LOBBY.getX() + 0.5, LOBBY.getY(), LOBBY.getZ() + 5.5, 180.0F);
                }
                continue;
            }
            if (p.isSpectator() && run != null && run.spectators.contains(p.getUUID())) {
                double dx = p.getX() - (ARENA.getX() + 0.5);
                double dz = p.getZ() - (ARENA.getZ() + 0.5);
                if (dx * dx + dz * dz > 30.0 * 30.0 || p.getY() > 95.0 || p.getY() < 55.0) {
                    teleport(p, level, ARENA.getX() + 0.5, 80.0, ARENA.getZ() + 0.5, 0.0F);
                }
            } else if (!inRun && p.isSpectator() && !p.isCreative()) {
                restoreMode(p);
            }
        }
    }

    // ------------------------------------------------------------------ Ereignisse

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().dimension() != DIM) return;
        Run r = run;
        boolean fighting = r != null && r.alive.contains(sp.getUUID());
        if (!fighting) {
            // Lobby und Zuschauer sind unverwundbar (ausser /kill und Abgrund).
            if (!event.getSource().is(DamageTypes.GENERIC_KILL) && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAmount() >= sp.getHealth() + sp.getAbsorptionAmount()
                && !event.getSource().is(DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
            makeSpectator(sp);
            if (r.alive.isEmpty()) finish(sp.server, false);
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
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level().dimension() == DIM) {
            removeFromRun(sp);
        }
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
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (p.level().dimension() != DIM) {
                        ctx.getSource().sendFailure(Component.translatable("msg.chronicle_arena.not_in_arena"));
                        return 0;
                    }
                    leave(p);
                    return 1;
                }))
                .then(Commands.literal("stats").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    for (ArenaDefs.Tier t : ArenaDefs.tiers()) {
                        int w = wins(p, t.id());
                        p.sendSystemMessage(Component.translatable("msg.chronicle_arena.stats", t.name(), w,
                                w == 0 ? "-" : formatTime(best(p, t.id()))));
                    }
                    p.sendSystemMessage(Component.translatable("msg.chronicle_arena.stats_tokens", countTokens(p)));
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

    /** Selbsttest: baut die Arena und spawnt jeden Mob jeder Begegnung einmal (dann wieder weg). */
    private static void debug(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIM);
        if (level == null) {
            LOGGER.error("ARENA-DEBUG: Dimension {} nicht gefunden", DIM.location());
            return;
        }
        ArenaBuilder.ensureBuilt(level);
        LOGGER.info("ARENA-DEBUG: Arena gebaut, Marker={}, Terminal={}, Portal={}",
                level.getBlockState(new BlockPos(0, 60, 200)).getBlock(),
                level.getBlockState(LOBBY).getBlock(),
                level.getBlockState(new BlockPos(0, 64, 8)).getBlock());
        List<ArenaDefs.Tier> tiers = ArenaDefs.tiers();
        LOGGER.info("ARENA-DEBUG: {} Stufen, {} Begegnungen verfuegbar", tiers.size(), ArenaDefs.encounters().size());
        Run dummy = new Run(ArenaDefs.encounters().get(0), tiers.get(tiers.size() - 1), 3);
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
        int shopOk = 0;
        for (ArenaDefs.ShopOffer o : ArenaDefs.shopOffers()) {
            ItemStack st = o.stack(server.registryAccess());
            if (st.isEmpty()) LOGGER.error("ARENA-DEBUG: Shop {} leer", o.id());
            else shopOk++;
            if (o.components() != null && st.getEnchantments().isEmpty()) LOGGER.error("ARENA-DEBUG: Shop {} ohne Verzauberung", o.id());
        }
        LOGGER.info("ARENA-DEBUG: Shop {} von {} Angeboten ok", shopOk, ArenaDefs.shopOffers().size());
        LOGGER.info("ARENA-DEBUG: Shop-Block={}", level.getBlockState(SHOP).getBlock());
    }

    // ------------------------------------------------------------------ Hilfen

    private static boolean inLobby(ServerPlayer sp) {
        double dx = sp.getX() - (LOBBY.getX() + 0.5);
        double dz = sp.getZ() - (LOBBY.getZ() + 0.5);
        return dx * dx + dz * dz <= (LOBBY_RADIUS + 2) * (LOBBY_RADIUS + 2) && sp.getY() > 40;
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
    }

    static String formatTime(int ticks) {
        int sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    static int countTokens(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(ModRegistry.TOKEN.get())) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(ModRegistry.TOKEN.get())) n += st.getCount();
        return n;
    }

    static void removeTokens(ServerPlayer p, int amount) {
        int left = amount;
        for (ItemStack st : p.getInventory().items) {
            if (left <= 0) break;
            if (st.is(ModRegistry.TOKEN.get())) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        for (ItemStack st : p.getInventory().offhand) {
            if (left <= 0) break;
            if (st.is(ModRegistry.TOKEN.get())) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        p.getInventory().setChanged();
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
}
