package de.chronicle.arena;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stufen und Begegnungen kommen aus Datapack-JSON:
 * data/&lt;ns&gt;/arena_tiers/*.json und data/&lt;ns&gt;/arena_encounters/*.json.
 * Neue Bosse oder Wellen lassen sich also (auch per KubeJS-Datapack) ohne Java ergaenzen.
 */
public final class ArenaDefs {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Tier(ResourceLocation id, int order, String name, String desc,
                       double health, double damage, double count, int waves,
                       ResourceLocation loot, int xp, int bossRolls,
                       ResourceLocation requires, int tokens, int tokensPerWave,
                       double waveHealth, double waveDamage, double waveCount) {}

    /** Mob-Gruppe einer Welle: ab fromWave, Anzahl = base + (welle - fromWave) * perWave. */
    public record Pool(ResourceLocation entity, int fromWave, double base, double perWave) {}

    public record Spawn(ResourceLocation entity, int count) {}

    public record Encounter(ResourceLocation id, int order, boolean boss, String name, String desc,
                            ResourceLocation icon, List<Pool> pools, ResourceLocation elite, int eliteEvery,
                            ResourceLocation finale, ResourceLocation bossEntity, List<Spawn> adds, String theme) {

        /** Alle Entity-IDs muessen in diesem Pack existieren, sonst wird die Begegnung ausgeblendet. */
        public boolean available() {
            if (boss) {
                if (!exists(bossEntity)) return false;
                for (Spawn s : adds) if (!exists(s.entity())) return false;
                return true;
            }
            if (pools.isEmpty()) return false;
            for (Pool p : pools) if (!exists(p.entity())) return false;
            if (elite != null && !exists(elite)) return false;
            return finale == null || exists(finale);
        }
    }

    /** Shop-Angebot: Gegenstand gegen Arena-Marken. components ist ein optionales Komponenten-JSON. */
    public record ShopOffer(ResourceLocation id, int order, String category, String name, ResourceLocation item,
                            int count, int price, JsonObject components) {
        public boolean available() {
            return BuiltInRegistries.ITEM.containsKey(item);
        }

        public ItemStack stack(HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item), count);
            if (components != null) {
                DataComponentPatch.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), components)
                        .resultOrPartial(err -> LOGGER.error("Arena-Shop {}: Komponenten ungueltig: {}", id, err))
                        .ifPresent(stack::applyComponents);
            }
            return stack;
        }
    }

    private static Map<ResourceLocation, Tier> tiers = new LinkedHashMap<>();
    private static Map<ResourceLocation, ShopOffer> shop = new LinkedHashMap<>();
    private static Map<ResourceLocation, Encounter> encounters = new LinkedHashMap<>();

    private ArenaDefs() {}

    public static List<Tier> tiers() {
        return new ArrayList<>(tiers.values());
    }

    public static List<Encounter> encounters() {
        List<Encounter> out = new ArrayList<>();
        for (Encounter e : encounters.values()) if (e.available()) out.add(e);
        return out;
    }

    public static List<ShopOffer> shopOffers() {
        List<ShopOffer> out = new ArrayList<>();
        for (ShopOffer o : shop.values()) if (o.available()) out.add(o);
        return out;
    }

    public static ShopOffer shopOffer(ResourceLocation id) {
        ShopOffer o = id == null ? null : shop.get(id);
        return o != null && o.available() ? o : null;
    }

    public static Tier tier(ResourceLocation id) {
        return tiers.get(id);
    }

    public static Encounter encounter(ResourceLocation id) {
        Encounter e = encounters.get(id);
        return e != null && e.available() ? e : null;
    }

    static boolean exists(ResourceLocation id) {
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TierLoader());
        event.addListener(new EncounterLoader());
        event.addListener(new ShopLoader());
    }

    private static ResourceLocation rl(JsonObject o, String key, ResourceLocation fallback) {
        return o.has(key) ? ResourceLocation.parse(GsonHelper.getAsString(o, key)) : fallback;
    }

    private static final class TierLoader extends SimpleJsonResourceReloadListener {
        TierLoader() {
            super(new Gson(), "arena_tiers");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            List<Tier> list = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = GsonHelper.convertToJsonObject(e.getValue(), "tier");
                    list.add(new Tier(e.getKey(),
                            GsonHelper.getAsInt(o, "order", 0),
                            GsonHelper.getAsString(o, "name", e.getKey().getPath()),
                            GsonHelper.getAsString(o, "description", ""),
                            GsonHelper.getAsDouble(o, "health", 1.0),
                            GsonHelper.getAsDouble(o, "damage", 1.0),
                            GsonHelper.getAsDouble(o, "count", 1.0),
                            GsonHelper.getAsInt(o, "waves", 10),
                            rl(o, "loot", null),
                            GsonHelper.getAsInt(o, "xp", 0),
                            GsonHelper.getAsInt(o, "boss_rolls", 2),
                            rl(o, "requires", null),
                            GsonHelper.getAsInt(o, "tokens", 3),
                            GsonHelper.getAsInt(o, "tokens_per_wave", 1),
                            GsonHelper.getAsDouble(o, "wave_health", 1.0),
                            GsonHelper.getAsDouble(o, "wave_damage", 1.0),
                            GsonHelper.getAsDouble(o, "wave_count", 1.0)));
                } catch (Exception ex) {
                    LOGGER.error("Arena-Stufe {} ungueltig: {}", e.getKey(), ex.toString());
                }
            }
            list.sort(Comparator.comparingInt(Tier::order).thenComparing(t -> t.id().toString()));
            Map<ResourceLocation, Tier> out = new LinkedHashMap<>();
            for (Tier t : list) out.put(t.id(), t);
            tiers = out;
            LOGGER.info("Arena: {} Stufen geladen", out.size());
        }
    }

    private static final class ShopLoader extends SimpleJsonResourceReloadListener {
        ShopLoader() {
            super(new Gson(), "arena_shop");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            List<ShopOffer> list = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = GsonHelper.convertToJsonObject(e.getValue(), "offer");
                    list.add(new ShopOffer(e.getKey(), GsonHelper.getAsInt(o, "order", 0),
                            GsonHelper.getAsString(o, "category", "material"),
                            GsonHelper.getAsString(o, "name", e.getKey().getPath()),
                            ResourceLocation.parse(GsonHelper.getAsString(o, "item")),
                            Math.max(1, GsonHelper.getAsInt(o, "count", 1)),
                            Math.max(1, GsonHelper.getAsInt(o, "price", 1)),
                            o.has("components") ? GsonHelper.getAsJsonObject(o, "components") : null));
                } catch (Exception ex) {
                    LOGGER.error("Arena-Shop {} ungueltig: {}", e.getKey(), ex.toString());
                }
            }
            list.sort(Comparator.comparingInt(ShopOffer::order).thenComparing(t -> t.id().toString()));
            Map<ResourceLocation, ShopOffer> out = new LinkedHashMap<>();
            for (ShopOffer o : list) out.put(o.id(), o);
            shop = out;
            int missing = 0;
            for (ShopOffer o : out.values()) {
                if (!o.available()) {
                    missing++;
                    LOGGER.info("Arena-Shop: {} ausgeblendet (Item {} fehlt im Pack)", o.id(), o.item());
                }
            }
            LOGGER.info("Arena: {} Shop-Angebote geladen, {} ausgeblendet", out.size(), missing);
        }
    }

    private static final class EncounterLoader extends SimpleJsonResourceReloadListener {
        EncounterLoader() {
            super(new Gson(), "arena_encounters");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            List<Encounter> list = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    list.add(parse(e.getKey(), GsonHelper.convertToJsonObject(e.getValue(), "encounter")));
                } catch (Exception ex) {
                    LOGGER.error("Arena-Begegnung {} ungueltig: {}", e.getKey(), ex.toString());
                }
            }
            list.sort(Comparator.comparingInt(Encounter::order).thenComparing(t -> t.id().toString()));
            Map<ResourceLocation, Encounter> out = new LinkedHashMap<>();
            int hidden = 0;
            for (Encounter en : list) {
                out.put(en.id(), en);
                if (!en.available()) {
                    hidden++;
                    LOGGER.info("Arena: Begegnung {} ausgeblendet (Mob fehlt im Pack)", en.id());
                }
            }
            encounters = out;
            LOGGER.info("Arena: {} Begegnungen geladen, {} ausgeblendet", out.size(), hidden);
        }

        private static Encounter parse(ResourceLocation id, JsonObject o) {
            boolean boss = "boss".equals(GsonHelper.getAsString(o, "type", "waves"));
            List<Pool> pools = new ArrayList<>();
            if (o.has("pools")) {
                for (JsonElement el : GsonHelper.getAsJsonArray(o, "pools")) {
                    JsonObject p = el.getAsJsonObject();
                    pools.add(new Pool(ResourceLocation.parse(GsonHelper.getAsString(p, "entity")),
                            GsonHelper.getAsInt(p, "from_wave", 1),
                            GsonHelper.getAsDouble(p, "base", 2),
                            GsonHelper.getAsDouble(p, "per_wave", 0.5)));
                }
            }
            ResourceLocation elite = null;
            int eliteEvery = 5;
            if (o.has("elite")) {
                JsonObject el = GsonHelper.getAsJsonObject(o, "elite");
                elite = ResourceLocation.parse(GsonHelper.getAsString(el, "entity"));
                eliteEvery = Math.max(1, GsonHelper.getAsInt(el, "every", 5));
            }
            ResourceLocation finale = null;
            if (o.has("finale")) {
                finale = ResourceLocation.parse(GsonHelper.getAsString(GsonHelper.getAsJsonObject(o, "finale"), "entity"));
            }
            ResourceLocation bossEntity = null;
            List<Spawn> adds = new ArrayList<>();
            if (boss) {
                bossEntity = ResourceLocation.parse(GsonHelper.getAsString(GsonHelper.getAsJsonObject(o, "boss"), "entity"));
                if (o.has("adds")) {
                    JsonArray arr = GsonHelper.getAsJsonArray(o, "adds");
                    for (JsonElement el : arr) {
                        JsonObject a = el.getAsJsonObject();
                        adds.add(new Spawn(ResourceLocation.parse(GsonHelper.getAsString(a, "entity")),
                                GsonHelper.getAsInt(a, "count", 2)));
                    }
                }
            }
            return new Encounter(id, GsonHelper.getAsInt(o, "order", 0), boss,
                    GsonHelper.getAsString(o, "name", id.getPath()),
                    GsonHelper.getAsString(o, "description", ""),
                    rl(o, "icon", ResourceLocation.withDefaultNamespace("barrier")),
                    pools, elite, eliteEvery, finale, bossEntity, adds, GsonHelper.getAsString(o, "theme", "default"));
        }
    }
}
