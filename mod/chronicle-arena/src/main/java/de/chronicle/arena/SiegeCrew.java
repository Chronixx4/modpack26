package de.chronicle.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Burg halten im Koop: Rollen mit eigener Faehigkeit, Kristall-Splitter, Abstimmung vor jeder Welle und Bilanz am Ende.
 * Hier stehen die festen Werte und reinen Rechnungen; der Ablauf steckt in ArenaSiege.
 */
final class SiegeCrew {
    private SiegeCrew() {}

    static final int BUILDER = 0, GUARD = 1, MAGE = 2, SUPPLY = 3;
    static final String[] ROLES = {"Baumeister", "Waechter", "Magier", "Versorger"};
    static final String[] ROLE_DESC = {
            "Tuerme, Ausbauten und Fallen kosten fuer dich 15 % weniger. Faehigkeit Ueberladung: Tuerme bis 12 Bloecke feuern 10 s doppelt so schnell.",
            "+4 Herzen und auf dem Weg 30 % mehr Schaden. Faehigkeit Kampfschrei: Gegner bis 6 Bloecke stehen 3 s still, du haeltst 8 s mehr aus.",
            "Deine Faehigkeit laedt doppelt so schnell (30 s). Arkanregen: drei Einschlaege auf die Stelle, auf die du schaust.",
            "Reparierst du den Kristall, gibt es +40 statt +25. Golems in deiner Naehe heilen. Faehigkeit Heilwelle: Kristall +15, Golems voll, Mitspieler +3 Herzen."};
    static final String[] ROLE_ICONS = {"minecraft:brick", "minecraft:shield", "minecraft:blaze_rod", "minecraft:glistering_melon_slice"};
    static final String[] ABILITY = {"Ueberladung", "Kampfschrei", "Arkanregen", "Heilwelle"};
    private static final Item[] ABILITY_ITEMS = {Items.BRICK, Items.GOAT_HORN, Items.BLAZE_ROD, Items.GLISTERING_MELON_SLICE};
    static final String ABILITY_TAG = "chronicle_siege_ability";
    static final int ABILITY_COOLDOWN = 60 * 20, MAGE_COOLDOWN = 30 * 20;
    static final int BUILDER_DISCOUNT = 15;
    static final double GUARD_PATH_BONUS = 1.3, GUARD_HEALTH = 8.0;
    static final int REPAIR = 25, SUPPLY_REPAIR = 40;

    static final String[] VOTES = {"Ruhig", "Horde", "Sturm", "Eisenhaut"};
    static final String[] VOTE_DESC = {
            "Normale Welle, normale Punkte.",
            "50 % mehr Gegner. +40 % Punkte in dieser Welle.",
            "Gegner laufen 30 % schneller. +30 % Punkte in dieser Welle.",
            "Gegner haben 40 % mehr Leben. +35 % Punkte in dieser Welle."};
    static final String[] VOTE_ICONS = {"minecraft:white_banner", "minecraft:zombie_head", "minecraft:feather", "minecraft:iron_chestplate"};
    static final int[] VOTE_BONUS = {0, 40, 30, 35};
    static final int CALM = 0, HORDE = 1, STORM = 2, IRON = 3;

    /** Splitter: so viel Kristall-Leben pro Stueck, bei vollem Kristall stattdessen Punkte. Liegen 30 s. */
    static final int SHARD_HEAL = 5, SHARD_POINTS = 20, SHARD_TICKS = 30 * 20;

    /** Statistik pro Spieler fuer die Bilanz. */
    static final int KILLS = 0, BUILT = 1, SHARDS = 2, SABOTEURS = 3, ABILITIES = 4, STATS = 5;

    static int cooldown(int role) { return role == MAGE ? MAGE_COOLDOWN : ABILITY_COOLDOWN; }

    /** Preis fuer diesen Spieler (Baumeister zahlt weniger). */
    static int cost(int price, int role) { return role == BUILDER ? (int) Math.round(price * (100 - BUILDER_DISCOUNT) / 100.0) : price; }

    /** Ergebnis der Abstimmung: meiste Stimmen; bei Gleichstand gewinnt die ruhigere Wahl (kleinerer Index). */
    static int winner(int[] counts) {
        int best = CALM;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
        return best;
    }

    /** Punkte mit dem Bonus der laufenden Abstimmung. */
    static int withBonus(int points, int vote) { return (int) Math.round(points * (100 + VOTE_BONUS[vote]) / 100.0); }

    /** Wertung fuer die Bilanz: Kills, Bauen, Splitter, Saboteure und Faehigkeiten zaehlen. */
    static int score(int[] s) { return s[KILLS] + 3 * s[BUILT] + 5 * s[SHARDS] + 4 * s[SABOTEURS] + 2 * s[ABILITIES]; }

    static String line(String name, int[] s) {
        StringBuilder b = new StringBuilder(name).append(": ").append(s[KILLS]).append(" Kills");
        if (s[BUILT] > 0) b.append(", ").append(s[BUILT]).append(" gebaut");
        if (s[SHARDS] > 0) b.append(", ").append(s[SHARDS]).append(" Splitter");
        if (s[SABOTEURS] > 0) b.append(", ").append(s[SABOTEURS]).append(s[SABOTEURS] == 1 ? " Saboteur" : " Saboteure");
        if (s[ABILITIES] > 0) b.append(", ").append(s[ABILITIES]).append("x ").append("Faehigkeit");
        return b.append(" (").append(score(s)).append(")").toString();
    }

    // ------------------------------------------------------------------ Faehigkeits-Gegenstand

    static ItemStack abilityItem(int role) {
        ItemStack st = new ItemStack(ABILITY_ITEMS[role]);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(ABILITY[role] + " (" + ROLES[role] + ")").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        st.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Rechtsklick: Faehigkeit einsetzen").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("Nur in Burg halten, verschwindet danach").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))));
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putInt(ABILITY_TAG, role);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return st;
    }

    /** Rolle des Faehigkeits-Gegenstands, -1 = kein solcher Gegenstand. */
    static int abilityRole(ItemStack st) {
        CustomData data = st.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        CompoundTag tag = data.copyTag();
        return tag.contains(ABILITY_TAG) ? tag.getInt(ABILITY_TAG) : -1;
    }

    static boolean has(ServerPlayer p, int role) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (abilityRole(inv.getItem(i)) == role) return true;
        return false;
    }

    /** Alle Faehigkeits-Gegenstaende aus dem Inventar nehmen (ausser der Rolle keep, -1 = alle). */
    static void strip(ServerPlayer p, int keep) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            int r = abilityRole(inv.getItem(i));
            if (r >= 0 && r != keep) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    static ItemStack shard() {
        ItemStack st = new ItemStack(Items.AMETHYST_SHARD);
        st.set(DataComponents.CUSTOM_NAME, Component.literal("Kristall-Splitter").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
        return st;
    }
}
