package de.chronicle.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific settings. Changes only affect newly prepared runs. */
public final class ArenaSettings extends SavedData {
    public int health = 100, damage = 100, coins = 100, prepSeconds = 10, goalInterval = 3;
    public boolean goals = true;

    public static ArenaSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ArenaSettings::new, ArenaSettings::load, null), "chronicle_arena_settings");
    }

    static ArenaSettings load(CompoundTag tag, HolderLookup.Provider registry) {
        ArenaSettings s = new ArenaSettings();
        if (tag.contains("health")) s.health = clamp(tag.getInt("health"), 25, 300);
        if (tag.contains("damage")) s.damage = clamp(tag.getInt("damage"), 25, 300);
        if (tag.contains("coins")) s.coins = clamp(tag.getInt("coins"), 0, 300);
        if (tag.contains("prep")) s.prepSeconds = clamp(tag.getInt("prep"), 3, 60);
        if (tag.contains("interval")) s.goalInterval = clamp(tag.getInt("interval"), 2, 10);
        if (tag.contains("goals")) s.goals = tag.getBoolean("goals");
        return s;
    }

    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        tag.putInt("health", health); tag.putInt("damage", damage); tag.putInt("coins", coins);
        tag.putInt("prep", prepSeconds); tag.putInt("interval", goalInterval); tag.putBoolean("goals", goals);
        return tag;
    }
}
