# Questbuch 2.0: Plan

> **Umgesetzt in 0.9.8, alles auf einmal:** 6 Gruppen, 33 Kapitel, 1178 Quests. Entscheidungen: gesperrt (linear), Team teilt Fortschritt, Abgabe-Quests ja, Umfang wie geplant. Regeln und Anker: `AUTOREN.md`.

Ziel: ein richtiges, durchgehendes Questbuch im ATM-Stil. Rund **950 Quests in 33 Kapiteln, aufgeteilt in 6 Gruppen.** Jede Kette folgt den echten Rezepten, und keine Quest bleibt ohne Namen.

## 0. Was in 0.9.7 kaputt war (Ursache gefunden)

- **"Unnamed" und falsche Reihenfolge:** FTB Quests liest IDs als *vorzeichenbehaftete* 64-Bit-Zahl. Etwa die Hälfte unserer IDs fing mit 8–F an und war deshalb zu groß.
  - Folge: Kapitel und Quests mit so einer ID bekamen beim Laden eine neue Zufalls-ID.
  - Dadurch passten Titel/Texte (Unnamed) und Abhängigkeiten (Pfeile/Reihenfolge) nicht mehr.
  - Betroffen waren 8 von 12 Kapiteln, unter anderem Create, Ars, Mekanism, Draconic und Bosse.
  - **Fix:** IDs auf 63 Bit begrenzen.
- **Zu wenig Inhalt:** Bisher gab es pro Mod ~15–25 Quests, die sprunghaft aufeinander folgten. Ganze Mods fehlten, etwa Occultism, Forbidden & Arcanus, Ender IO, PneumaticCraft und Ad Astra.
- **Anordnung:** Bisher hat ein Automatik-Layout die Quests verteilt. Ab jetzt bekommt jedes Kapitel ein festes Raster mit einer klaren Hauptlinie und Seitenästen.

## 1. Grundregeln für jedes Kapitel

1. **Hauptlinie von links nach rechts** = Fortschritt der Mod, eingeteilt in Stufen (Tier 1, 2, 3 ...).
   - Seitenäste gehen nach oben/unten ab, z. B. Deko, Extras oder optionale Sachen.
2. **Reihenfolge = echte Rezepte.** Ich ziehe alle Rezepte aus dem Testserver.
   - Ein Skript prüft dann: Wenn Quest B ein Item braucht, das in A hergestellt wird, muss B nach A kommen.
   - Fehler bricht den Build ab.
3. **Jede Quest erklärt kurz**, was das Ding macht und wofür man es braucht (1–3 Sätze, Deutsch). Wo nötig steht dabei, welches Handbuch weiterhilft.
4. **Formen:**

   | Form | Wofür |
   |---|---|
   | Kreis | normal |
   | Sechseck | Meilenstein / Stufe geschafft |
   | Zahnrad, groß | Kapitelstart |
   | Herz | optional |
   | Stern, groß | Kapitelende |

5. **Kapitel-Freischaltung:** Jedes Kapitel startet mit einer Quest, die auf einen passenden Punkt in "Erste Schritte" verweist (Quest-Link, im anderen Kapitel sichtbar).
   - Beispiel: AE2 startet nach "Eisenzeit", Draconic erst nach "Mekanism Stufe 3" und "Drache besiegt".
6. **Items werden nur erkannt, nie weggenommen** (bleibt so). Ausnahme: ein paar Abgabe-Quests ("Gib 64 X ab") für Belohnungen. Siehe Entscheidung C.

## 2. Gruppen und Kapitel

Die Gruppen erscheinen in der linken Leiste als einklappbare Überschriften.

### Gruppe 1: Start
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 1 | Willkommen | 15 | Bedienung (EMI, Karte, Claims, Teams, Voice, Backups), Questbuch-Erklärung |
| 2 | Erste Schritte | 30 | Holz → Stein → Eisen → Diamant → Netherit, Bett, Waystones, Kompasse, Comforts, Carry On, Corpse, Torchmaster |
| 3 | Lager und Rucksäcke | 25 | Storage Drawers (Stufen, Controller), Sophisticated Storage (Holz→Netherit, Upgrades), Sophisticated Backpacks + Upgrades, Traveler's Backpack |
| 4 | Essen und Farmen | 30 | Farmer's Delight (Kochtopf, Gerichte), Aquaculture (Angeln, Fischfilet), Dungeon's Delight, Create Central Kitchen, Garnished |

### Gruppe 2: Technik
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 5 | Create 1: Grundlagen | 40 | Andesit-Legierung, Welle, Zahnrad, Wasserrad, Mühlstein, Presse, Mixer, Becken, Riemen, Trichter, Deployer, Messing, Mechanical Crafter |
| 6 | Create 2: Fortgeschritten | 40 | Dampfmaschine, Sequenzielles Bauen, Präzisionsmechanismus, Contraptions, Züge (+ Steam 'n' Rails), Logistik/Factory Logistics, Enchantment Industry |
| 7 | Create 3: Addons | 45 | Crafts & Additions (Strom), New Age, Diesel, Big Cannons, Jetpack, Ore Excavation, Mechanical Spawner, Metallurgy, Teleporters, Power Loader, Aeronautics, Slice & Dice |
| 8 | Applied Energistics 2 | 40 | Meteorit, Certus, Inscriber, Prozessoren, ME-System, Kanäle, Zellen, Autocrafting, P2P, Spatial |
| 9 | AE2 Addons | 25 | ExtendedAE, AdvancedAE (Quantum), MEGA Cells, Applied Flux, Wireless Terminals, Applied Mekanistics, ME Requester, Ars Énergistique |
| 10 | Mekanism | 50 | Metallurgic Infuser, Stufen Basic→Ultimate, Erzverarbeitung x2/x3/x4/x5, Generatoren, Fission, Fusion, SPS, Antimaterie, MekaSuit, Mekanism Tools |
| 11 | Immersive Engineering | 25 | Hammer, Kokerei, Hochofen, Draht/Strom, Multiblöcke (Crusher, Arc Furnace, Excavator), Revolver |
| 12 | Powah und Extreme Reactors | 25 | Powah Stufen (Starter→Nitro), Energizing Orb, Reaktor; Extreme Reactors Reaktor + Turbine |
| 13 | Industrial Foregoing | 20 | Latex, Plastik, Laser Drill, Mob-Farmen, Infinity-Werkzeuge |
| 14 | Ender IO | 20 | Legierungen, Kondensatoren, SAG Mill, Alloy Smelter, Leitungen, Soul Binder, Enderios |
| 15 | PneumaticCraft | 20 | Kompressor, Druckkammer, PCBs, Drohnen, Programmer |
| 16 | Oritech | 20 | Maschinen, Laser, Augmente, Partikelbeschleuniger |
| 17 | Kleine Technik | 30 | Actually Additions, Integrated Dynamics, Iron Jetpacks |
| 18 | Draconic Evolution | 35 | Draconium, Fusion Crafting, Wyvern → Draconic → Chaotic, Energiekern, Reaktor, Werkzeuge/Rüstung |

### Gruppe 3: Magie
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 19 | Ars Nouveau | 45 | Zauberbuch, Glyphen nach Stufen, Source, Apparatus, Rituale, Vertraute, Golems, Rüstungen |
| 20 | Ars Addons | 25 | Ars Elemental, Technica, Additions, Creo, Ocultas, Not Enough Glyphs |
| 21 | Iron's Spells | 40 | Schriftrollen, Inscription Table, Schulen (Feuer, Eis, Blitz, ...), Bücher Stufen, Rüstungen, Cataclysm Spellbooks, Gems 'n Jewelry |
| 22 | Occultism | 25 | Spirit Fire, Otherworld, Dämonen beschwören, Dimensional Storage, Miner-Geister |
| 23 | Forbidden & Arcanus + Hexerei | 30 | Hephaestus Forge, Rituale, Mysterium; Hexerei Kessel, Hexenbesen |
| 24 | Apotheosis | 30 | Verzauberungstisch-Stufen, Bücherregale, Edelsteine, Affixe, Gem Cutting, Spawner |

### Gruppe 4: Ressourcen
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 25 | Mystical Agriculture | 40 | Inferium → Prudentium → Tertium → Imperium → Supremium → Insanium (Agradditions), Seeds nach Stufen, Wachstumsbeschleuniger |
| 26 | Bienen | 25 | Productive Bees (Brutkasten, Zentrifuge, Genetik, Upgrades), Bumblezone |

### Gruppe 5: Abenteuer
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 27 | Dimensionen | 40 | Nether (BetterNether), End (BetterEnd), Aether, Undergarden, Deeper and Darker, Bumblezone, Alex's Caves (5 Biome), Ad Astra (Rakete, Planeten), Allthemodium-Welten |
| 28 | Twilight Forest | 20 | komplette Boss-Reihenfolge: Naga → Lich → Minotaurus → Hydra → Knight Phantoms → Ur-Ghast → Yeti → Alpha Yeti → Snow Queen |
| 29 | Strukturen | 30 | YUNG's, When Dungeons Arise, Dungeons and Taverns, Towns and Towers, IDAS, Repurposed Structures (Struktur-Aufgaben: hingehen reicht) |
| 30 | Bosse | 50 | nach Schwierigkeit sortiert: Vanilla → Mowzie's → Born in Chaos → Mutant Monsters → Aquamirae → Ice & Fire (Drachen) → Bosses'Rise → Cataclysm (komplett) |
| 31 | Waffen und Rüstung | 25 | Simply Swords, Archers, Rogues & Warriors, Immersive Armors, Artifacts (Funde) |

### Gruppe 6: Endgame
| # | Kapitel | Quests | Inhalt |
|---|---|---|---|
| 32 | Allthemodium | 15 | Allthemodium → Vibranium → Unobtainium, Werkzeuge, Rüstung |
| 33 | Das große Finale | 15 | je ein Endziel pro großer Mod (Chaos-Rüstung, MekaSuit komplett, Insanium-Farm, Antimaterie, Cataclysm alle Bosse, ...). Am Ende steht eine "Pack geschafft"-Quest. |

**Summe: etwa 33 Kapitel und rund 950 Quests.** Die Zahlen sind Richtwerte. Wo eine Mod mehr hergibt, wird es mehr.

## 3. Belohnungen
- **Loot-Kisten in 4 Stufen** (Gewöhnlich, Selten, Episch, Legendär) mit gemischtem Inhalt: Erze, Nahrung, nützliche Items aus der jeweiligen Stufe.
- Dazu gibt es XP-Level.
- Bei Meilensteinen gibt es gezielte Items, z. B. Upgrade-Module, Seeds oder Waystone-Staub.
- Keine "kaputten" Belohnungen, die eine Mod überspringen lassen.

## 4. Technik dahinter
- `generator.py` wird erweitert um:
  - Kapitel-Gruppen
  - Quest-Links zwischen Kapiteln
  - Loot-Tabellen
  - festes Raster-Layout (Spalte = Stufe, Zeile = Ast)
- Für jedes Kapitel wird ein **Vorschaubild** (PNG) erzeugt. Darauf prüfe ich Reihenfolge und Anordnung, bevor du es im Spiel ansiehst.
- Prüfungen beim Bauen:
  - Jede ID existiert (wie bisher).
  - Jede Quest hat Titel und Text.
  - Keine Abhängigkeit zeigt rückwärts.
  - Rezept-Reihenfolge passt.
  - Keine Quests überlappen.
- Servertest: muss `Loaded 6 chapter groups, 33 chapters, ~950 quests` zeigen. Im Log darf keine FTB-Quests-Warnung stehen.

## 5. Umsetzung in Etappen
Jede Etappe ist ein eigener PR mit neuer Version, damit du zwischendurch im Spiel draufschauen kannst:
1. **0.9.8:** ID-Fix, Gruppen, neues Layout-System, Belohnungen, Kapitel 1–4 (Start).
2. **0.9.9:** Technik (5–18).
3. **0.9.10:** Magie und Ressourcen (19–26).
4. **0.9.11:** Abenteuer und Endgame (27–33).

## 6. Was du entscheiden musst
- **A. Freischaltung:**
  - *Gesperrt* (Option 1): Quests erst machbar, wenn die vorherige erledigt ist. Ordentlicher, empfohlen.
  - *Flexibel* (Option 2): alles jederzeit abhakbar, so wie bisher.
- **B. Team-Fortschritt:** Teilt ein FTB-Team den Fortschritt (Standard) oder macht jeder alles selbst?
- **C. Abgabe-Quests:** Ein paar "Gib X ab"-Quests mit besseren Belohnungen: ja oder nein?
- **D. Umfang:** ~950 Quests in 4 Etappen. Passt das, oder soll irgendwas raus bzw. dazu?
