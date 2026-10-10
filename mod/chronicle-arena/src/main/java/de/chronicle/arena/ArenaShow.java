package de.chronicle.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;

/** Titel, Musik und Segen der Arena (alles serverseitig, der Client braucht nichts dafuer). */
public final class ArenaShow {
    private ArenaShow() {}

    // ------------------------------------------------------------------ Titel

    public static void title(Collection<ServerPlayer> players, Component main, Component sub, int in, int stay, int out) {
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(main));
        }
    }

    public static MutableComponent big(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color, ChatFormatting.BOLD);
    }

    // ------------------------------------------------------------------ Musik

    public static final ResourceLocation WAVE_MUSIC = ResourceLocation.withDefaultNamespace("music_disc.pigstep");
    public static final int WAVE_MUSIC_TICKS = 149 * 20;
    public static final ResourceLocation BOSS_MUSIC = ResourceLocation.withDefaultNamespace("music_disc.precipice");
    public static final int BOSS_MUSIC_TICKS = 299 * 20;

    public static void playMusic(Collection<ServerPlayer> players, ResourceLocation id) {
        Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.getHolder(id).map(h -> (Holder<SoundEvent>) h).orElse(null);
        if (sound == null) return;
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundSoundEntityPacket(sound, SoundSource.MUSIC, p, 0.7F, 1.0F, p.getRandom().nextLong()));
        }
    }

    public static void stopMusic(Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundStopSoundPacket(WAVE_MUSIC, SoundSource.MUSIC));
            p.connection.send(new ClientboundStopSoundPacket(BOSS_MUSIC, SoundSource.MUSIC));
        }
    }

    // ------------------------------------------------------------------ Segen

    public enum Blessing {
        POWER("Kraft", "+15 % Schaden", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.15),
        VITALITY("Vitalitaet", "+2 Herzen", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 4.0),
        SWIFT("Eile", "+8 % Tempo", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.08),
        ARMOR("Panzerung", "+3 Ruestung", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 3.0),
        REGEN("Regeneration", "Du heilst dich langsam", null, null, 0.0),
        GOLD("Goldrausch", "+25 % Coins pro Welle", null, null, 0.0);

        public final String label;
        public final String desc;
        final Holder<Attribute> attr;
        final AttributeModifier.Operation op;
        final double amount;

        Blessing(String label, String desc, Holder<Attribute> attr, AttributeModifier.Operation op, double amount) {
            this.label = label;
            this.desc = desc;
            this.attr = attr;
            this.op = op;
            this.amount = amount;
        }

        ResourceLocation modId() {
            return ChronicleArena.id("blessing_" + name().toLowerCase());
        }
    }

    /** Segen mit der Stapelzahl neu anwenden (nicht gespeichert, geht beim Neustart von selbst weg). */
    public static void apply(ServerPlayer p, int[] stacks) {
        for (Blessing b : Blessing.values()) {
            if (b.attr == null) continue;
            AttributeInstance inst = p.getAttribute(b.attr);
            if (inst == null) continue;
            inst.removeModifier(b.modId());
            int n = b.ordinal() < stacks.length ? stacks[b.ordinal()] : 0;
            if (n > 0) inst.addTransientModifier(new AttributeModifier(b.modId(), b.amount * n, b.op));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static void clear(ServerPlayer p) {
        for (Blessing b : Blessing.values()) {
            if (b.attr == null) continue;
            AttributeInstance inst = p.getAttribute(b.attr);
            if (inst != null) inst.removeModifier(b.modId());
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static Component offerLine(int slot, Blessing b) {
        return Component.literal(" [" + (slot + 1) + "] ").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arena segen " + (slot + 1)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Klicken zum Waehlen"))))
                .append(Component.literal(b.label).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" - " + b.desc).withStyle(ChatFormatting.GRAY));
    }
}
