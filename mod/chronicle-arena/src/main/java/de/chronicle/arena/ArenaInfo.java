package de.chronicle.arena;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Zwei Infotafeln in der Lobby (ueber den oestlichen Zauberecken): was es in der Arena gibt, und eine Vorschau auf den Bunker.
 * Fehlen sie (z. B. nach einem Neustart), werden sie neu geschrieben, sobald jemand in der Lobby ist.
 */
final class ArenaInfo {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String TAG = "chronicle_info";

    private ArenaInfo() {}

    static final String OVERVIEW = String.join("\n",
            "CHRONICLE ARENA - WAS ES GIBT",
            "",
            "Wellen: Begegnung und Stufe am Terminal waehlen",
            "Abgrund: endlose Wellen mit eigenem Rekord",
            "Boss-Kaempfe mit Phasen bei 75/50/25 %",
            "NEU: Burg halten - Tower Defense im Burghof",
            "(Modus-Knopf im Terminal)",
            "",
            "Missionen, Gruppenweg, Haendler-Weg",
            "Segen alle 5 Wellen, Fluch-Karten",
            "Tages- und Wochen-Challenge (Tafel im Westen)",
            "Bestzeiten (Sueden), letzter Run (Norden)",
            "Shop und Schmied ueber die Bruecke",
            "",
            "/arena training  /arena rekorde",
            "/arena challenge  /arena stats");

    static final String TEASER = String.join("\n",
            "DEMNAECHST: BUNKER",
            "Zombie-Modus im Stil von Call of Duty",
            "",
            "Endlose Runden, Gegner brechen durch",
            "Fenster und Gaenge",
            "Punkte fuer jeden Treffer und Kill",
            "Tueren freikaufen, neue Raeume entdecken",
            "Perk-Automaten: Zaehigkeit, Schnellhand,",
            "Sprinter, Wiederbeleber",
            "Waffen und Traenke an der Wand kaufen",
            "Strom-Schalter fuer Fallen und Automaten",
            "Versteckte Geheimnisse ...",
            "",
            "Haltet durch.");

    /** Einmal pro Sekunde: steht jemand in der Lobby und fehlen Tafeln (oder sind doppelt), neu schreiben. */
    static void tick(ServerLevel level) {
        boolean near = false;
        for (var p : level.players()) {
            if (p.distanceToSqr(ArenaManager.LOBBY.getX() + 0.5, p.getY(), ArenaManager.LOBBY.getZ() + 0.5) < 40 * 40) { near = true; break; }
        }
        if (!near) return;
        int count = 0;
        for (Entity e : level.getAllEntities()) if (e.getTags().contains(TAG)) count++;
        if (count != 2) refresh(level);
    }

    static void refresh(ServerLevel level) {
        try {
            List<Entity> old = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
            old.forEach(Entity::discard);
            double y = ArenaManager.LOBBY.getY() + 6.0;
            board(level, ArenaManager.LOBBY.getX() + 6.5, y, ArenaManager.LOBBY.getZ() - 6.5, OVERVIEW, "gold");
            board(level, ArenaManager.LOBBY.getX() + 6.5, y, ArenaManager.LOBBY.getZ() + 7.5, TEASER, "red");
        } catch (RuntimeException e) {
            LOGGER.warn("Infotafeln konnten nicht geschrieben werden: {}", e.toString());
        }
    }

    private static void board(ServerLevel level, double x, double y, double z, String text, String color) {
        CompoundTag data = ArenaExtras.boardTag(text);
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        json.addProperty("color", color);
        data.putString("text", json.toString());
        data.putInt("line_width", 260);
        Entity e = EntityType.TEXT_DISPLAY.create(level);
        if (e == null) return;
        e.load(data);
        e.addTag(TAG);
        e.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(e);
    }
}
