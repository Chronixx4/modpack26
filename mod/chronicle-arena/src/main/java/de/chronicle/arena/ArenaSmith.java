package de.chronicle.arena;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Schmied: dauerhafte Verbesserungen gegen Coins. Wirken nur in der Arena-Dimension
 * (Attribute werden dort aufgelegt und beim Verlassen wieder entfernt).
 */
public final class ArenaSmith {
    private ArenaSmith() {}

    public record Up(String id, String name, String desc, int max, int price, Holder<Attribute> attr,
                     AttributeModifier.Operation op, double per) {
        ResourceLocation modId() {
            return ChronicleArena.id("smith_" + id);
        }
    }

    public static final String LEBENSFUNKE = "lebensfunke";
    public static final String GLUT = "glut";

    public static final List<Up> UPS = List.of(
            new Up("schaerfe", "Schaerfe", "+6 % Schaden pro Stufe", 5, 25, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.06),
            new Up("haertung", "Haertung", "+1 Ruestung pro Stufe", 5, 25, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1.0),
            new Up("lebenskraft", "Lebenskraft", "+1 Herz pro Stufe", 5, 30, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2.0),
            new Up("windlaeufer", "Windlaeufer", "+4 % Tempo pro Stufe", 3, 25, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.04),
            new Up("schlagtempo", "Schlagtempo", "+5 % Angriffstempo pro Stufe", 3, 35, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.05),
            new Up("standfest", "Standfest", "+10 % Rueckstossresistenz pro Stufe", 3, 25, Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.1),
            new Up("reichweite", "Reichweite", "+0,4 Bloecke Reichweite pro Stufe", 3, 40, Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, 0.4),
            new Up(LEBENSFUNKE, "Lebensfunke", "Kills heilen 0,5 Herz pro Stufe", 3, 45, null, null, 0),
            new Up(GLUT, "Schmiedeglut", "Treffer setzen Gegner 2 s pro Stufe in Brand", 3, 45, null, null, 0));

    public static int level(ServerPlayer p, String id) {
        return ArenaStats.smithLevel(p, id);
    }

    public static int price(Up u, int level) {
        return u.price() * (level + 1);
    }

    public static void open(ServerPlayer p) {
        if (p.level().dimension() != ArenaManager.DIM) return;
        List<Net.SmithInfo> list = new ArrayList<>();
        for (Up u : UPS) {
            int lv = level(p, u.id());
            list.add(new Net.SmithInfo(u.id(), u.name(), u.desc(), lv, u.max(), lv >= u.max() ? 0 : price(u, lv)));
        }
        PacketDistributor.sendToPlayer(p, new Net.OpenSmith(ArenaStats.coins(p), list));
    }

    public static void buy(ServerPlayer p, String id) {
        if (p.level().dimension() != ArenaManager.DIM) return;
        for (Up u : UPS) {
            if (!u.id().equals(id)) continue;
            int lv = level(p, id);
            if (lv >= u.max()) return;
            int cost = price(u, lv);
            if (!ArenaStats.spend(p, cost)) {
                p.sendSystemMessage(Component.translatable("msg.chronicle_arena.not_enough", cost));
            } else {
                ArenaStats.setSmithLevel(p, id, lv + 1);
                p.sendSystemMessage(Component.literal("Der Schmied verbessert: " + u.name() + " Stufe " + (lv + 1) + " (" + cost + " Coins)")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                apply(p);
                ArenaManager.sendStats(p);
            }
            open(p);
            return;
        }
    }

    /** Attribute auflegen (in der Arena) bzw. entfernen (ausserhalb). Aenderungen nur bei Bedarf. */
    public static void apply(ServerPlayer p) {
        boolean in = p.level().dimension() == ArenaManager.DIM;
        for (Up u : UPS) {
            if (u.attr() == null) continue;
            AttributeInstance inst = p.getAttribute(u.attr());
            if (inst == null) continue;
            int lv = in ? level(p, u.id()) : 0;
            AttributeModifier cur = inst.getModifier(u.modId());
            double want = u.per() * lv;
            if (lv == 0) {
                if (cur != null) inst.removeModifier(u.modId());
            } else if (cur == null || cur.amount() != want) {
                inst.removeModifier(u.modId());
                inst.addTransientModifier(new AttributeModifier(u.modId(), want, u.op()));
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }
}
