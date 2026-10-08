# Plan 0.9.16: Skilltree

Stand 2026-10-08. Noch **nichts eingebaut**. Vorschau (klickbar, mit allen Knoten): https://claude.ai/artifact/NAYYXzqocNsYPRjqcY4QMT

## 1. Ziel

Ein eigener Skilltree "Chronicle" fuer alle Spieler. Fuenf Aeste (Abenteuer, Magie, Handwerk, Ueberleben, Technik) um eine Mitte, je Ast eine Wurzel, vier Linien zu fuenf Stufen und ein Gipfel (111 Knoten). Punkte bekommt man durch Spielen (Kills, Bosse, Erz abbauen, Quests). **Jede Faehigkeit wirkt gleichzeitig auf alle passenden Mods** (z. B. Mana bei Iron's Spells und Ars Nouveau), nicht nur auf Vanilla.

## 2. Die Mod

- **Pufferfish's Skills** (Modrinth-Slug `skills`), Version 0.19.1 (beta, NeoForge 1.21.1, ohne weitere Abhaengigkeiten). Die Mod bringt das Fenster, die Punkte und die Speicherung, aber **keinen** Baum.
- **Nicht** nehmen: "Default Skill Trees" (nur Beispielbaeume Kampf/Mining), Drittanbieter-Baeume. Unser Baum kommt als eigene Daten.
- Zusatz (optional, spaeter): Pufferfish's Skills Inventory Button (Button im Inventar). Taste zum Oeffnen ist sonst K.
- Einbau in `build-und-test.ps1` (`$ModList`, Kategorie "Multiplayer, Quests und Verwaltung" oder "Komfort") plus `MODLISTE.txt`/`pack/modrinth.index.json` wie bisher. Client **und** Server.

## 3. Datenformat (aus der Jar und dem Default-Datenpaket geprueft)

Ort: `pack/kubejs/data/puffish_skills/puffish_skills/` (wird als Datenpaket geladen, wie unsere Rezepte).

```
config.json                         {"version": 3, "categories": ["chronicle"]}
categories/chronicle/
  category.json                     Titel, Symbol, Hintergrund, unlocked_by_default
  definitions.json                  Fertigkeiten-Typen: Titel, Symbol, Rahmen, rewards[]
  skills.json                       Knoten: id -> x, y, definition (+ optional root)
  connections.json                  {"normal": {"bidirectional": [[a,b], ...]}}
  experience.json                   Punkte-Quellen (siehe 5)
```

- **Rewards** (eine Definition kann mehrere haben): `puffish_skills:attribute` (Attribut-ID, Wert, Operation `addition`/`multiply_base`/`multiply_total`), `puffish_skills:command` (beim Freischalten/Zuruecksetzen), `puffish_skills:tag`, `puffish_skills:points`, `puffish_skills:scoreboard`.
- Mehrere Mods in einem Knoten = **mehrere attribute-Rewards in einer Definition**. Das ist der Kern des Designs.
- Eigene Attribute der Mod: u. a. `puffish_skills:player.melee_damage`, `.mining_speed`, `.fortune`, `.resistance`, `.health_regen` usw. (Liste beim Bauen aus der Jar lesen).

## 4. Aufbau und Generator

Wie beim Questbuch: **Generator statt Handarbeit.** `pack/skilltree/spec.py` (die Daten) + `pack/skilltree/generator.py` (schreibt die JSONs). Die Vorschau-Daten (`AESTE` im Artifact) sind die Vorlage.

- Positionen: radial wie in der Vorschau (Wurzel 120 vom Mittelpunkt, Schritt 56, Faecher -30/-10/10/30 Grad). Der Mittelpunkt ist der Startknoten (`root`), Pixelmasse in `skills.json` x/y.
- Eine Definition pro (Linie, Stufe) mit fester ID, z. B. `magie_zauberkraft_3`. Knoten-IDs = `<ast>_<linie>_<stufe>`. IDs nie umbenennen (sonst geht Fortschritt verloren).
- Rahmen: normal = Kreis, Stufe V = grosser Rahmen, Gipfel = `challenge`-Rahmen. Symbole aus Items (z. B. Magie: `irons_spellbooks:arcane_essence`).
- Texte als Titel in der Definition (Deutsch, ein Satz). Pufferfish zeigt Titel + Beschreibung; Beschreibungen mit "Wirkt auf" aus der Spec generieren.
- Kosten: Stufe I-III 1 Punkt, IV-V 2 Punkte, Gipfel 3 Punkte (`cost`/`required_points` laut Format beim Bauen pruefen).

## 5. Punkte (Erfahrung -> Level -> Skillpunkte)

Eine einzige Kategorie "Chronicle" mit **einem gemeinsamen Punktetopf** (wie in der Vorschau). Quellen in `experience.json`:

| Quelle | Typ | Formel |
|---|---|---|
| Gegner toeten | `kill_entity` | `max_health / 4`, Bosse bringen von selbst viel (hohe Lebenspunkte), Anti-Farming je Chunk |
| Erze abbauen | `mine_block` | feste Werte pro Erz-Tag (`#c:ores`), Deepslate/Raritaeten mehr |
| Herstellen/Schmelzen | `craft_item`/`smelt_item` | klein, nur seltene Ergebnisse |
| Fortschritt | `criterion` | Advancements (z. B. Boss-Advancements) -> grosse Einmal-Boni |
| Quests | FTB-Quests-Belohnung | Befehl `/puffish_skills points add @p chronicle 1` bei Boss-/Kapitelende |

- Level-Kurve: `experience_per_level` als Ausdruck, z. B. `min(level ^ 1.4 + 20, 300)`; 1 Punkt pro Level, max. Level ~110 (= alle Knoten).
- Ziel: ca. 20-30 Punkte in den ersten 5 Spielstunden, alles ausbauen ist Endgame.

## 6. Welche Attribute (zu pruefen)

Alle Ziele aus den Mod-Jars gelesen (`lang/en_us.json`). **Genaue Attribut-IDs und Skalierung beim Bauen im Spiel mit `/attribute @s <id> get` pruefen.**

| Linie | Ziele (Attribut) |
|---|---|
| Magie: Manavorrat | `irons_spellbooks:max_mana`, Ars Nouveau `max_mana` (PerkAttributes, ID pruefen), Goety `goety:soul_discount` |
| Magie: Energie | `irons_spellbooks:mana_regen`, Ars `mana_regen`, `goety:casting_speed` |
| Magie: Zauberkraft | `irons_spellbooks:spell_power`, `spell_power:generic` (Spell Power: Paladine/Schurken), `goety:spell_potency`, Ars `spell_damage` |
| Magie: Konzentration | `irons_spellbooks:cooldown_reduction`, `goety:cooldown_discount`, `spell_power:haste` |
| Abenteuer | `generic.attack_damage`, `ranged_weapon:damage`, `generic.max_health`, `generic.movement_speed`, `apothic_attributes:armor_pierce`, `apothic_attributes:crit_damage`, `irons_spellbooks:casting_movespeed` |
| Handwerk | `apothic_attributes:experience_gained`, `generic.luck`, `apothic_attributes:healing_received`, `apothic_attributes:life_steal` |
| Ueberleben | `generic.armor`, `generic.armor_toughness`, `generic.knockback_resistance`, `apothic_attributes:dodge_chance`, Fallschaden, `irons_spellbooks:spell_resist`, `spell_power:resistance.generic`, `aces_spell_utils` Resist, `spell_engine:damage_taken` |
| Technik | `player.mining_efficiency` + `apothic_attributes:mining_speed`, `player.block_interaction_range`, `player.entity_interaction_range`, Unterwasser-Abbau/-Bewegung |

- Skalierung je Mod verschieden (Ars/Iron's haben andere Mana-Groessen): Faktor je Ziel in der Spec.
- Drei Faehigkeiten ohne Mod-Wert brauchen ein **KubeJS-Skript** (`server_scripts/skills.js`): Schaden gegen Bosse (LivingHurt, Tag `c:bosses`), Nahrungswert, Haltbarkeit. Anbindung: Reward `puffish_skills:tag`/Scoreboard auslesen oder Attribut `puffish_skills:*`-Dummy, im Skript abfragen. Falls das hakt: diese drei zuerst weglassen.

## 7. Schritte

1. **Mod einbauen** (`$ModList`, Modliste, Index) und Servertest "Start OK" (PR-Test reicht).
2. **Generator + Spec** schreiben (`pack/skilltree/`), erzeugt die Datenpaket-Dateien. Kopie in `pack/kubejs/data/puffish_skills/...`.
3. **Attribut-IDs pruefen**: lokaler Servertest mit KubeJS-Pruefskript (`Attribute`-Registry auslesen), danach Spec korrigieren. Mit Marvins OK.
4. **XP-Quellen und Punkte-Kurve** festlegen; FTB-Quest-Belohnungen (Boss-Kapitelenden) mit `points add`.
5. **KubeJS-Skripte** fuer die drei Sonderwerte (oder streichen).
6. **Respec-Moeglichkeit**: Befehl/Item (z. B. Werkbank-Rezept "Vergessenstrank", `puffish_skills:skills reset`); Details beim Bauen.
7. **Startkiste/Questbuch/Bossfuehrer**: kurzer Hinweis (Taste K), Willkommen-Quest "Skilltree".
8. **Servertest** (PR) und danach **Marvin im Client**: Baum sichtbar? Boni wirken? Nach Release 0.9.16.

## 8. Offene Punkte / Risiken

- Pufferfish's Skills ist **Beta**; das Datenformat (`version: 3`) ist stabil, aber die Mod laeuft bei Marvin noch nicht, im Client ungetestet.
- Balance: L2 Hostility und harte Bosse sind schon fordernd. Starke Gipfel (z. B. +20 % Zauberschaden in vier Systemen) ggf. senken.
- Ars Nouveau: Attribut-Namen noch nicht sicher (siehe 6).
- Alle Aenderungen am Baum nur ueber den Generator, nie von Hand in den JSONs.
- Entscheidungen von Marvin (Standard in Klammern): Anzahl Aeste (5), Punkte nur ueber Level oder auch Quests/Bosse (beides), Respec (ja).
