package de.chronicle.arena;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;

import java.util.List;

/** Effekte: Sieges-Feuerwerk, Fanfare und Partikelspuren aus dem Shop. */
final class ArenaFx {
    private ArenaFx() {}

    private static final int[][] COLORS = {
            {0xFFD700, 0xFFFFFF}, {0x9B5CFF, 0xFF66CC}, {0xFF3030, 0xFFAA00}, {0x40E0FF, 0xFFFFFF}, {0x55FF55, 0xFFFF55}};

    static void rocket(ServerLevel level, double x, double y, double z) {
        RandomSource rnd = level.getRandom();
        int[] c = COLORS[rnd.nextInt(COLORS.length)];
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[rnd.nextInt(FireworkExplosion.Shape.values().length)];
        FireworkExplosion ex = new FireworkExplosion(shape, IntArrayList.of(c), IntArrayList.of(0xFFFFFF), rnd.nextBoolean(), true);
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponents.FIREWORKS, new Fireworks(1 + rnd.nextInt(2), List.of(ex)));
        level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, stack));
    }

    /** Ein Feuerwerk um einen Punkt herum. */
    static void burst(ServerLevel level, double cx, double cy, double cz, int n, double radius) {
        RandomSource rnd = level.getRandom();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double d = rnd.nextDouble() * radius;
            rocket(level, cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d);
        }
    }

    static void fanfare(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    static ParticleOptions trail(String kind) {
        return switch (kind) {
            case "flame" -> ParticleTypes.FLAME;
            case "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "heart" -> ParticleTypes.HEART;
            case "sparks" -> ParticleTypes.END_ROD;
            case "magic" -> ParticleTypes.ENCHANT;
            case "electric" -> ParticleTypes.ELECTRIC_SPARK;
            case "cloud" -> ParticleTypes.CLOUD;
            case "note" -> ParticleTypes.NOTE;
            case "glitter" -> ParticleTypes.HAPPY_VILLAGER;
            case "witch" -> ParticleTypes.WITCH;
            case "snow" -> ParticleTypes.SNOWFLAKE;
            case "dragon" -> ParticleTypes.DRAGON_BREATH;
            default -> null;
        };
    }

    static void spawnTrail(ServerLevel level, ServerPlayer p, String kind) {
        ParticleOptions o = trail(kind);
        if (o == null) return;
        level.sendParticles(o, p.getX(), p.getY() + 0.15, p.getZ(), 2, 0.25, 0.05, 0.25, 0.01);
    }
}
