package de.chronicle.arena;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class Net {
    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("2");
        reg.playToClient(OpenTerminal.TYPE, OpenTerminal.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.openTerminal(payload)));
        reg.playToClient(Tokens.TYPE, Tokens.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.updateTokens(payload.count())));
        reg.playToServer(Action.TYPE, Action.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ArenaManager.handleAction(sp, payload);
                }));
        reg.playToServer(Buy.TYPE, Buy.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ArenaManager.handleBuy(sp, payload);
                }));
    }

    public record TierInfo(String id, String name, String desc, boolean locked, String info) {}

    public record EncInfo(String id, boolean boss, String name, String desc, String icon) {}

    public record ShopInfo(String id, String name, String item, int count, int price) {}

    /** Server -> Client: Inhalt des Menues. */
    public record OpenTerminal(boolean running, String status, int tokens, List<TierInfo> tiers,
                               List<EncInfo> encounters, List<ShopInfo> shop) implements CustomPacketPayload {
        public static final Type<OpenTerminal> TYPE = new Type<>(ChronicleArena.id("open_terminal"));
        public static final StreamCodec<FriendlyByteBuf, OpenTerminal> CODEC = StreamCodec.of(OpenTerminal::write, OpenTerminal::read);

        private static void write(FriendlyByteBuf buf, OpenTerminal p) {
            buf.writeBoolean(p.running);
            buf.writeUtf(p.status);
            buf.writeVarInt(p.tokens);
            buf.writeVarInt(p.tiers.size());
            for (TierInfo t : p.tiers) {
                buf.writeUtf(t.id);
                buf.writeUtf(t.name);
                buf.writeUtf(t.desc, 1024);
                buf.writeBoolean(t.locked);
                buf.writeUtf(t.info, 256);
            }
            buf.writeVarInt(p.encounters.size());
            for (EncInfo e : p.encounters) {
                buf.writeUtf(e.id);
                buf.writeBoolean(e.boss);
                buf.writeUtf(e.name);
                buf.writeUtf(e.desc, 1024);
                buf.writeUtf(e.icon);
            }
            buf.writeVarInt(p.shop.size());
            for (ShopInfo s : p.shop) {
                buf.writeUtf(s.id);
                buf.writeUtf(s.name);
                buf.writeUtf(s.item);
                buf.writeVarInt(s.count);
                buf.writeVarInt(s.price);
            }
        }

        private static OpenTerminal read(FriendlyByteBuf buf) {
            boolean running = buf.readBoolean();
            String status = buf.readUtf();
            int tokens = buf.readVarInt();
            int nt = buf.readVarInt();
            List<TierInfo> tiers = new ArrayList<>();
            for (int i = 0; i < nt; i++) {
                tiers.add(new TierInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(1024), buf.readBoolean(), buf.readUtf(256)));
            }
            int ne = buf.readVarInt();
            List<EncInfo> encs = new ArrayList<>();
            for (int i = 0; i < ne; i++) {
                encs.add(new EncInfo(buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readUtf(1024), buf.readUtf()));
            }
            int ns = buf.readVarInt();
            List<ShopInfo> shop = new ArrayList<>();
            for (int i = 0; i < ns; i++) {
                shop.add(new ShopInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
            }
            return new OpenTerminal(running, status, tokens, tiers, encs, shop);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> Client: neue Markenzahl nach einem Kauf. */
    public record Tokens(int count) implements CustomPacketPayload {
        public static final Type<Tokens> TYPE = new Type<>(ChronicleArena.id("tokens"));
        public static final StreamCodec<FriendlyByteBuf, Tokens> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.count), buf -> new Tokens(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> Server: Start oder Zuschauen. */
    public record Action(boolean spectate, String encounter, String tier) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ChronicleArena.id("action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.spectate);
                    buf.writeUtf(p.encounter);
                    buf.writeUtf(p.tier);
                },
                buf -> new Action(buf.readBoolean(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> Server: Shop-Angebot kaufen. */
    public record Buy(String offer) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(ChronicleArena.id("buy"));
        public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.offer), buf -> new Buy(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
