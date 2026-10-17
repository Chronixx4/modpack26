# Questbuch: Regeln für Autoren

Der Inhalt liegt in `pack/quests/inhalt/<teil>.py`, das Format steht in `dsl.py`. Die Reihenfolge der Teile im Buch steht in `generator.py` unter `TEILE`.

## Werkzeuge
```
export QUEST_DATEN=<ordner mit ids.json und rezepte.json>
python3 pack/quests/such.py name messing gehäuse     # IDs suchen (deutsche Namen + IDs)
python3 pack/quests/such.py mod mekanism              # alle Items einer Mod
python3 pack/quests/such.py rezept create:brass_casing
python3 pack/quests/such.py wofuer create:brass_ingot
python3 pack/quests/such.py art structures twilightforest
python3 pack/quests/generator.py --nur <teil> --locker --ids $QUEST_DATEN/ids.json \
    --rezepte $QUEST_DATEN/rezepte.json --bilder <ordner> --aus <ordner>
```
- Der Generator bricht ab bei: unbekannter ID, fehlender Abhängigkeit, doppeltem Schlüssel, Kreis, zwei Quests auf demselben Platz.
- Er warnt bei:
  - Quest ohne Text
  - Rezept-Reihenfolge ("braucht X aus Quest Y, die nicht davor liegt")
- Rezept-Warnungen werden behoben, entweder durch eine passende Abhängigkeit oder durch eine andere Reihenfolge. Übrig bleiben dürfen nur Warnungen, die nachweislich falsch sind, z. B. weil es einen alternativen Weg gibt.

## Aufbau eines Kapitels
- **Erste Quest** heißt `start`, hat `form='gear', gross=True, haupt=True`.
  - Sie hängt per `nach=['kapitel/schluessel']` an einem Anker aus einem anderen Kapitel (siehe unten).
  - Im ersten Kapitel des Buchs gibt es keinen solchen Anker.
- **Hauptlinie** (`haupt=True`): der rote Faden der Mod, von links nach rechts, Stufe für Stufe.
  - Wichtige Stufen bekommen `form='hexagon'` und eine Loot-Kiste.
- **Seitenäste** hängen an der Hauptlinie.
  - Lieber Ketten (A → B → C) als 15 Geschwister an einer Quest. Mehr als ~6 direkte Kinder pro Quest machen das Bild unübersichtlich.
  - Gleichartiges (z. B. 8 Samen einer Stufe) kann man in 2–3 Quests mit mehreren Aufgaben bündeln.
- **Letzte Quest** der Hauptlinie heißt `ende`: `form='star', gross=True`, Belohnung `'loot3'` oder `'loot4'`.
- **Optionale Sachen** (Deko, Spielerei): `optional=True, form='heart'`.
- **Abgabe-Quests:** 1–3 pro Kapitel, wo es passt.
  - `abgabe=True`, `optional=True`, Aufgabe z. B. `('item', 'minecraft:iron_ingot', 64)`.
  - Titel beginnt mit "Abgabe:". Belohnung: Loot-Kiste eine Stufe höher als normal.
- **Abhängigkeiten = echte Voraussetzungen:** Was man für das Rezept braucht, kommt davor.
  - Wenn eine Quest mehrere Wege hat: `eine=True` (eine der Abhängigkeiten reicht).

## Texte
- Deutsch, locker, du-Form.
- Länge: 1–3 kurze Sätze pro Absatz, höchstens 2 Absätze.
  - Inhalt: was das Ding macht, wofür man es braucht, und gern ein Tipp.
- Farbcodes sind sparsam erlaubt: `&e` (gelb, Hervorhebung), `&c` (Warnung), `&a` (positiv), `&r` (zurück).
- **Nichts erfinden.** Wenn du dir bei einer Mechanik nicht sicher bist, bleibt der Text allgemein, z. B. "Schau ins Handbuch / in EMI".
- Titel: kurz (max. ~25 Zeichen), deutsch, kein Item-Namen-Spam.

## Belohnungen
**Automatisch:** `inhalt/belohnungen.py` rechnet beim Generieren die Werte um: XP-Level 1/2/3 werden zu 3/5/8, normale Quests bekommen zusaetzlich ein Item aus dem Pool ihres Kapitels (frueh im Kapitel = frueh im Pool), `loot1`-`loot4` werden zu Kisten des Kapitels (`<kapitel>_loot2` usw.) plus XP. Im Inhalt weiter nur `1`-`3`/`'lootN'` schreiben; fuer ein neues Kapitel einen `pool(...)` ergaenzen (nur IDs, die schon in Quests vorkommen, und Vanilla). Quests mit eigener `('item', ...)`-Belohnung bleiben unberuehrt.

| Wofür | Belohnung |
|---|---|
| Normale Quest | `1`–`3` XP-Level |
| Meilenstein | `'loot1'` (frühes Spiel), `'loot2'` (Mittelspiel), `'loot3'` (spät), `'loot4'` (Endgame) |
| Gezielte Items | `('item', id, n)` |

- Gezielte Items sollen helfen, aber keine Stufe überspringen. Also keine fertigen Maschinen der nächsten Stufe.

## Anker (kapitelübergreifende Abhängigkeiten)
Diese Schlüssel **muss** das jeweilige Kapitel enthalten. Andere Kapitel dürfen darauf verweisen.

| Anker | Bedeutung |
|---|---|
| `willkommen/start` | Buch-Start |
| `erste_schritte/eisen` | Eisenbarren |
| `erste_schritte/kupfer` | Kupferbarren |
| `erste_schritte/gold` | Goldbarren |
| `erste_schritte/redstone` | Redstone |
| `erste_schritte/diamant` | Diamant |
| `erste_schritte/netherit` | Netheritbarren |
| `erste_schritte/verzaubern` | Zaubertisch |
| `create_1/start` | Andesitlegierung |
| `create_1/messing` | Messingbarren |
| `create_2/praezision` | Präzisionsmechanismus |
| `ae2/start` | Certus-Quarz |
| `ae2/prozessoren` | alle drei Prozessoren |
| `mekanism/start` | Osmium |
| `mekanism/stahl` | Stahlbarren |
| `mekanism/fortgeschritten` | Advanced-Stufe (Advanced Control Circuit) |
| `mekanism/elite` | Elite Control Circuit |
| `mekanism/ultimate` | Ultimate Control Circuit |
| `dimensionen/nether` | Nether betreten |
| `dimensionen/end` | End betreten |
| `bosse/drache` | Enderdrache besiegt |
| `bosse/wither` | Wither besiegt |
| `ars_nouveau/start` | Ars-Nouveau-Einstieg |
| `irons_spells/start` | Iron's-Spells-Einstieg |

- Jedes Kapitel hat außerdem `start` und `ende`.
- Das Finale verweist auf `<kapitel>/ende`.
