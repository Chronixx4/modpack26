package de.chronicle.arena;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Schwebende Schilder fuer die Modus-Karten. Alle tragen ein Tag und werden darueber aufgeraeumt. */
final class ArenaLabels {
    private ArenaLabels() {}

    static final String TAG = "chronicle_mode_label";

    /** Schild anlegen oder aktualisieren; gibt die UUID zurueck (zum Wiederfinden). */
    static UUID text(ServerLevel level, UUID existing, String tag, double x, double y, double z, String text, String color, int width) {
        CompoundTag data = ArenaExtras.boardTag(text);
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        json.addProperty("color", color);
        data.putString("text", json.toString());
        data.putString("billboard", "center");
        data.putInt("line_width", width);
        Entity e = existing == null ? null : level.getEntity(existing);
        boolean fresh = e == null;
        if (fresh) e = EntityType.TEXT_DISPLAY.create(level);
        if (e == null) return null;
        e.load(data);
        e.addTag(TAG);
        e.addTag(tag);
        e.moveTo(x, y, z, 0, 0);
        if (fresh && !level.addFreshEntity(e)) return null;
        return e.getUUID();
    }

    static void remove(ServerLevel level, String tag) {
        if (level == null) return;
        List<Entity> old = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e.getTags().contains(tag)) old.add(e);
        old.forEach(Entity::discard);
    }
}
