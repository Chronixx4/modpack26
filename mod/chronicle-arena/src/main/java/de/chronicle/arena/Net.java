package de.chronicle.arena;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class Net {
    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("5");
        reg.playToClient(OpenTerminal.TYPE, OpenTerminal.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.openTerminal(payload)));
        reg.playToClient(OpenShop.TYPE, OpenShop.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.openShop(payload)));
        reg.playToClient(Stats.TYPE, Stats.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.updateStats(payload)));
        reg.playToServer(Action.TYPE, Action.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ArenaManager.handleAction(sp, payload);
                }));
        reg.playToClient(OpenSmith.TYPE, OpenSmith.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> de.chronicle.arena.client.ClientHooks.openSmith(payload)));
        reg.playToServer(SmithBuy.TYPE, SmithBuy.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ArenaSmith.buy(sp, payload.id());
                }));
        reg.playToServer(Buy.TYPE, Buy.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) ArenaManager.handleBuy(sp, payload);
                }));
    }

    public record TierInfo(String id, String name, String desc, boolean locked, String info) {}

    public record EncInfo(String id, boolean boss, String name, String desc, String icon) {}

    public record ShopInfo(String id, String category, String name, int price, ItemStack stack, String cosmetic) {}

    public record ModInfo(int bit, String name, String desc, int bonus) {}

    public record ChallengeInfo(String encounter, String tier, int mask, boolean claimed, int bonusCoins, String text) {}

    public record BoardLine(String name, String title, int wins, int bosses, int kills, int endless) {}

    /** Server -> Client: Inhalt des Kampf-Menues. */
    public record OpenTerminal(boolean running, String status, List<TierInfo> tiers, List<EncInfo> encounters,
                               List<ModInfo> mods, ChallengeInfo challenge)
            implements CustomPacketPayload {
        public static final Type<OpenTerminal> TYPE = new Type<>(ChronicleArena.id("open_terminal"));
        public static final StreamCodec<FriendlyByteBuf, OpenTerminal> CODEC = StreamCodec.of(OpenTerminal::write, OpenTerminal::read);

        private static void write(FriendlyByteBuf buf, OpenTerminal p) {
            buf.writeBoolean(p.running);
            buf.writeUtf(p.status);
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
            buf.writeVarInt(p.mods.size());
            for (ModInfo m : p.mods) {
                buf.writeVarInt(m.bit);
                buf.writeUtf(m.name);
                buf.writeUtf(m.desc, 256);
                buf.writeVarInt(m.bonus);
            }
            buf.writeUtf(p.challenge.encounter);
            buf.writeUtf(p.challenge.tier);
            buf.writeVarInt(p.challenge.mask);
            buf.writeBoolean(p.challenge.claimed);
            buf.writeVarInt(p.challenge.bonusCoins);
            buf.writeUtf(p.challenge.text, 256);
        }

        private static OpenTerminal read(FriendlyByteBuf buf) {
            boolean running = buf.readBoolean();
            String status = buf.readUtf();
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
            int nm = buf.readVarInt();
            List<ModInfo> mods = new ArrayList<>();
            for (int i = 0; i < nm; i++) mods.add(new ModInfo(buf.readVarInt(), buf.readUtf(), buf.readUtf(256), buf.readVarInt()));
            ChallengeInfo ch = new ChallengeInfo(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readUtf(256));
            return new OpenTerminal(running, status, tiers, encs, mods, ch);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> Client: Shop mit allen Angeboten (mit echten Item-Stacks fuer Tooltips). */
    public record OpenShop(int coins, List<ShopInfo> offers) implements CustomPacketPayload {
        public static final Type<OpenShop> TYPE = new Type<>(ChronicleArena.id("open_shop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC = StreamCodec.of(OpenShop::write, OpenShop::read);

        private static void write(RegistryFriendlyByteBuf buf, OpenShop p) {
            buf.writeVarInt(p.coins);
            buf.writeVarInt(p.offers.size());
            for (ShopInfo s : p.offers) {
                buf.writeUtf(s.id);
                buf.writeUtf(s.category);
                buf.writeUtf(s.name);
                buf.writeVarInt(s.price);
                ItemStack.STREAM_CODEC.encode(buf, s.stack);
                buf.writeUtf(s.cosmetic);
            }
        }

        private static OpenShop read(RegistryFriendlyByteBuf buf) {
            int coins = buf.readVarInt();
            int n = buf.readVarInt();
            List<ShopInfo> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new ShopInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), ItemStack.STREAM_CODEC.decode(buf), buf.readUtf()));
            }
            return new OpenShop(coins, list);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> Client: Konto, Kosmetik und Rangliste (fuer die Anzeige in der Arena und den Shop). */
    public record Stats(int coins, int kills, int bosses, int wins, String title, String titleId, String trailId,
                        List<String> owned, List<BoardLine> board, boolean showHud, String rank, int earned, int nextRankAt) implements CustomPacketPayload {
        public static final Type<Stats> TYPE = new Type<>(ChronicleArena.id("stats"));
        public static final StreamCodec<FriendlyByteBuf, Stats> CODEC = StreamCodec.of(Stats::write, Stats::read);

        private static void write(FriendlyByteBuf buf, Stats p) {
            buf.writeVarInt(p.coins);
            buf.writeVarInt(p.kills);
            buf.writeVarInt(p.bosses);
            buf.writeVarInt(p.wins);
            buf.writeUtf(p.title);
            buf.writeUtf(p.titleId);
            buf.writeUtf(p.trailId);
            buf.writeVarInt(p.owned.size());
            for (String o : p.owned) buf.writeUtf(o);
            buf.writeVarInt(p.board.size());
            for (BoardLine l : p.board) {
                buf.writeUtf(l.name);
                buf.writeUtf(l.title);
                buf.writeVarInt(l.wins);
                buf.writeVarInt(l.bosses);
                buf.writeVarInt(l.kills);
                buf.writeVarInt(l.endless);
            }
            buf.writeBoolean(p.showHud);
            buf.writeUtf(p.rank);
            buf.writeVarInt(p.earned);
            buf.writeVarInt(p.nextRankAt);
        }

        private static Stats read(FriendlyByteBuf buf) {
            int coins = buf.readVarInt();
            int kills = buf.readVarInt();
            int bosses = buf.readVarInt();
            int wins = buf.readVarInt();
            String title = buf.readUtf();
            String titleId = buf.readUtf();
            String trailId = buf.readUtf();
            int no = buf.readVarInt();
            List<String> owned = new ArrayList<>();
            for (int i = 0; i < no; i++) owned.add(buf.readUtf());
            int nb = buf.readVarInt();
            List<BoardLine> board = new ArrayList<>();
            for (int i = 0; i < nb; i++) board.add(new BoardLine(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            boolean hud = buf.readBoolean();
            return new Stats(coins, kills, bosses, wins, title, titleId, trailId, owned, board, hud, buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> Server: Start oder Zuschauen. */
    public record Action(boolean spectate, String encounter, String tier, int mask) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ChronicleArena.id("action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.spectate);
                    buf.writeUtf(p.encounter);
                    buf.writeUtf(p.tier);
                    buf.writeVarInt(p.mask);
                },
                buf -> new Action(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt()));

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

    public record SmithInfo(String id, String name, String desc, int level, int max, int price) {}

    /** Server -> Client: Schmied mit allen Verbesserungen. */
    public record OpenSmith(int coins, List<SmithInfo> ups) implements CustomPacketPayload {
        public static final Type<OpenSmith> TYPE = new Type<>(ChronicleArena.id("open_smith"));
        public static final StreamCodec<FriendlyByteBuf, OpenSmith> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.coins);
                    buf.writeVarInt(p.ups.size());
                    for (SmithInfo u : p.ups) {
                        buf.writeUtf(u.id);
                        buf.writeUtf(u.name);
                        buf.writeUtf(u.desc);
                        buf.writeVarInt(u.level);
                        buf.writeVarInt(u.max);
                        buf.writeVarInt(u.price);
                    }
                },
                buf -> {
                    int coins = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<SmithInfo> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(new SmithInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                    }
                    return new OpenSmith(coins, list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> Server: Verbesserung beim Schmied kaufen. */
    public record SmithBuy(String id) implements CustomPacketPayload {
        public static final Type<SmithBuy> TYPE = new Type<>(ChronicleArena.id("smith_buy"));
        public static final StreamCodec<FriendlyByteBuf, SmithBuy> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id), buf -> new SmithBuy(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
