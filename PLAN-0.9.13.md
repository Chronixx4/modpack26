# Plan 0.9.13: neue Mods + Quests

Stand 2026-10-07. Kandidaten aus dem Vergleich mit ATM10: alle für 1.21.1 NeoForge auf Modrinth, nicht in Chronicle, nicht in ATM10.
Alle Angaben unten sind aus den Jars geprüft (Rezepte, Loot, Advancements, Handbücher, Configs).
Noch **nichts eingebaut**: Marvin sucht aus, dann Einbau auf dem Arbeitsbranch.

## 1. Die Mods (Modrinth-Slug, Version)

| # | Mod | Slug | Version | Zusätzliche Abhängigkeiten (fehlen im Pack) |
|---|---|---|---|---|
| 1 | Create: Numismatics | `numismatics` | 1.1.0 | – |
| 2 | Create: The Factory Must Grow | `create-tfmg` | 1.2.0 | – |
| 3 | Create: Mobile Packages | `create-mobile-packages` | 0.7.8 | – |
| 4 | Create: Pattern Schematics | `create-pattern-schematics` | 2.0.10 | – |
| 5 | Create: Ironworks | `create-ironworks` | 4.0.3 | – |
| 6 | Create Mechanical Extruder | `create-mechanical-extruder` | 2.2.2 | – (Mechanicals Lib ist drin) |
| 7 | Create: Ultimate Factory | `create-ultimate-factory` | 2.2.4 | – |
| 8 | Create: Factory | `create-factory` | 0.7b | – |
| 9 | Create: Train Perspective | `create-train-perspective` | 1.1.1 | – |
| 10 | Goety – The Dark Arts | `goety` | 3.2.0 | – (Curios, Patchouli drin) |
| 11 | End Remastered | `endrem` | 6.3.0 | – |
| 12 | Enderscape | `enderscape` | 1.0.9 | **Trimmed**, **YACL** (nur Client) |
| 13 | L2 Hostility | `l2hostility` | 3.0.18 | **L2 Library**, **L2 Complements** |
| 14 | Kobolds | `kobolds` | 4.0.1 | – |
| 15 | Paladins & Priests | `paladins-and-priests` | 3.1.3 | **Runes** (Shield API steckt in der Jar) |
| 16 | Shield Expansion | `shield-expansion` | 1.4.3 | – |
| 17 | Grappling Hook Mod | `grappling-hook-mod` | v13 | – |
| 18 | Naturalist | `naturalist` | 2.0.5 | – |
| 19 | Tide 2 | `tide` | 2.1.1 | – (Cloth Config drin) |
| 20 | Serene Seasons | `serene-seasons` | 10.1.0.9 | – (GlitchCore drin) |

## 2. Entscheidungen für Marvin (vor dem Einbau)

1. **End Remastered (11):** Normale Enderaugen sind dann weder werfbar noch einsetzbar, man braucht 12 von 16 verschiedenen Augen.
   - Option A, empfohlen: so lassen. Die Quest `dimensionen/enderauge` wird umgebaut (siehe 4.3).
   - Option B: In `config/EndRemastered-NeoForge/endrem.json` die Werte `USE_EYE_OF_ENDER` und `THROW_EYE_OF_ENDER` auf `true` setzen. Dann sind die neuen Augen nur ein Bonus.
   - Problem: YUNG schaltet Vanilla-Pyramiden, -Dschungeltempel und -Netherfestungen ab. Old, Rogue und Nether Eye sind deshalb selten. 12 Augen gehen trotzdem ohne diese drei.
2. **L2 Hostility (13):** Mit den Standardwerten wird das Pack sehr viel schwerer.
   - Jede besuchte Dimension gibt +10 Level, und das Pack hat sehr viele Dimensionen. Im End sind schnell Level 100+ erreicht, also +300 % Leben.
   - Wenn wir sie nehmen: `dimensionFactor` auf 0–2 senken und eventuell `allowLegendary=false` setzen.
3. **Tide (19) und Aquaculture:** Tide macht die Vanilla-Angel zur Tide-Angel. Dann laufen zwei Angelsysteme nebeneinander und es gibt zwei "Iron Fishing Rod". Entweder beides behalten und im Kapitel Essen sauber trennen, oder eine der beiden Mods weglassen.
4. **Serene Seasons (20):** Vanilla-Pflanzen wachsen außerhalb ihrer Saison langsam, und der Winter bringt Schnee.
   - Abhilfe: Glas über dem Feld oder Felder unter Y=48. Farmen unter einem Glasdach sind also nicht betroffen.
   - Das verändert das frühe Spiel spürbar.
5. **Enderscape (12):**
   - Mögliche Reibung mit Better End (Chorus, Oberflächenregeln, Himmel). Ein Absturz-Konflikt ist im Code nicht zu sehen. **Ein Servertest mit End-Chunks ist Pflicht** (nur nach Rückfrage).
   - Das mitgelieferte Datapack `new_end_cities` ersetzt die End-Städte. Dann muss die Quest `dimensionen/endstadt` auf `enderscape:end_city` umgestellt werden. Mit `/datapack list` prüfen, ob es aktiv ist.
6. **Ironworks (5):** Überschreibt Creates Messing-Rezept. Der Mixer gibt dann 1 statt 2 Messingbarren, Messing wird doppelt so teuer. Wenn das stört, das Rezept per KubeJS zurücksetzen.
7. **Paladins (15) und Shield Expansion (16):** Die Schilde der Paladins fallen bei Shield Expansion auf Standardwerte zurück. Falls es Probleme gibt, in Shield Expansion den "Item Only Mode" einschalten.

## 3. Technische Hinweise

- **TFMG + Almost Unified:**
  - TFMG-Stahl, Aluminium, Blei, Nickel und Konstantan werden voraussichtlich zu den IE-Items, die Platten zu IE-Platten.
  - Viele TFMG-Rezepte verlangen `tfmg:heavy_plate` und `tfmg:rebar` aber als feste ID. Nach dem Einbau per EMI prüfen, ob die Rezepte noch gehen. In Quests Metalle als IE-IDs nehmen und `heavy_plate`/`rebar` nicht als Aufgabe stellen.
- **TFMG, erster Magnet:** Der Polarizer braucht schon einen Magneten. Den ersten Magneten bekommt man nur, indem man einen `tfmg:magnetic_alloy_ingot` fallen lässt und ein Blitz einschlägt (Gewitter oder Dreizack mit Kanalisierung). Muss in den Questtext.
- **TFMG, erster Stahl:** Die Hochofen-Verstärkung braucht schon Stahl. Er kommt aus dem Ironworks-Mixer (überhitzt, 2 Eisen + 7 Kohlestaub), von IE oder von Mekanism.
- **TFMG und Ultimate Factory** haben beide ein Zerkleinern-Rezept für Kalkstein. Welches gewinnt, ist offen.
- **Numismatics:** Münzen haben kein Rezept und keinen Loot, also nicht als Quest-Aufgabe nehmen.
- **Grappling Hook:** Haken mit Upgrade bleibt die ID `grapplemod:grapplinghook`. Quests deshalb auf die Upgrade-Items legen, nicht auf `motorhook` usw. Die Stiefel haben kein Rezept.
- **Shield Expansion:** Paragon und Griefer Shield sind im Pack nicht erhältlich, weil Fremd-Mods fehlen. Das Griefer-Rezept gibt einen harmlosen Rezeptfehler.
- **Naturalist:** Entfernt Nutztiere in Savannen und Sümpfen sowie Schweine in Wäldern.
- **Goety:**
  - `goety:cryptic_eye` ist nicht dasselbe wie `endrem:cryptic_eye`.
  - Die Schriftrollen aus Pyramide und Dschungeltempel (front, buried, floral) sind wegen YUNG kaum zu bekommen. Keine Quest daran hängen.
- **Create: Factory:** Die Karamell-Rezepte geben harmlose Parsing-Fehler im Log.
- `ids.json` und `rezepte.json` wie bei 0.9.10 aus den Jars ergänzen.

## 4. Quest-Plan

Format: Schlüssel – Titel – Aufgabe – nach. **H** heißt Hauptlinie, ohne **H** ist es ein Seitenast.

### 4.1 Neues Kapitel `tfmg` "Create: TFMG" (Gruppe Create, nach `create_3`, ca. 35 Quests)
**Grundstoffe:**
- `start` **H** – Gusseisen – `tfmg:cast_iron_ingot` 8 – `create_1/messing`
- `koksofen` **H** – Koksofen – `tfmg:coke_oven` – start
- `koks` **H** – Koks – `tfmg:coal_coke` 32 – koksofen
- `kreosot` – Gehärtete Bretter – `tfmg:hardened_planks` 16 – koksofen
- `schamotte` **H** – Schamotte – `tfmg:fireclay_ball` 16 – start
- `ziegel` **H** – Feuerfeste Ziegel – `tfmg:fireproof_bricks` 24 – schamotte
- `erststahl` **H** – Erster Stahl – `immersiveengineering:ingot_steel` 16 – start
- `gehaeuse` **H** – Stahlgehäuse – `tfmg:steel_casing` 8 – kreosot, erststahl
- `schwergehaeuse` **H** – Schwermaschinen – `tfmg:heavy_machinery_casing` 8 – gehaeuse
- `blei_nickel` – Blei und Nickel – IE-Blei 16 + IE-Nickel 16 – start
- `kalk` **H** – Flussmittel – `tfmg:limesand` 32 – start

**Hochofen und Stahl:**
- `hochofen` **H** – Hochofen – `tfmg:blast_furnace_output`, `tfmg:blast_furnace_hatch`, `tfmg:blast_furnace_reinforcement` 4 – ziegel, erststahl
- `winderhitzer` **H** – Heißluft – `tfmg:blast_stove`, `tfmg:air_intake` – hochofen
- `guss` **H** – Gießbecken – `tfmg:casting_basin` – ziegel
- `stahl` **H** – Hochofenstahl – `tfmg:molten_steel_bucket` – hochofen, winderhitzer, kalk, guss, koks; Belohnung loot2
- `mechanismus` **H** – Stahlmechanismus – `tfmg:steel_mechanism` 4 – stahl, blei_nickel

**Öl:**
- `scanner` **H** – Ölsuche – `tfmg:surface_scanner` – mechanismus, schwergehaeuse
- `pumpenbock` **H** – Pumpenbock – `tfmg:pumpjack_base`, `tfmg:pumpjack_crank`, `tfmg:pumpjack_hammer`, `tfmg:machine_input` – scanner
- `rohoel` **H** – Rohöl – `tfmg:crude_oil_bucket` – pumpenbock
- `destille` **H** – Destillationsturm – `tfmg:steel_distillation_controller` + `tfmg:steel_distillation_output` 3 – rohoel
- `treibstoff` **H** – Treibstoffe – `tfmg:diesel_bucket`, `tfmg:gasoline_bucket` – destille; Belohnung loot2
- `asphalt` (Herz) – Straßenbau – `tfmg:asphalt` 64 – destille
- `beton` (Herz) – Beton – `tfmg:liquid_concrete_bucket` – kalk

**Motoren:**
- `motor` **H** – Verbrennungsmotor – `tfmg:regular_engine` 2 + `tfmg:engine_cylinder` 2 – treibstoff, mechanismus
- `grossmotor` – Einfacher Großmotor – `tfmg:simple_large_engine` – `create_2/praezision`, stahl

**Chemie:**
- `bottich` **H** – Chemiebottich – `tfmg:steel_chemical_vat` + `tfmg:industrial_mixer` + `tfmg:mixer_blade` – mechanismus
- `kunststoff` **H** – Kunststoff – `tfmg:plastic_sheet` 16 – bottich, treibstoff, guss
- `gummi` – Gummi – `tfmg:rubber_sheet` 8 – bottich
- `aluminium` – Aluminium – IE-Aluminium 16 – bottich
- `silizium` **H** – Silizium – `tfmg:silicon_ingot` 8 – stahl

**Strom:**
- `wickler` **H** – Wickelmaschine – `tfmg:winding_machine` + `tfmg:copper_spool` – mechanismus
- `magnet` **H** – Blitzmagnet – `tfmg:magnet` – silizium, blei_nickel. Text: Barren fallen lassen, Blitz!
- `platine` **H** – Leiterplatte – `tfmg:circuit_board` 4 – kunststoff, wickler, gummi
- `generator` **H** – Generator – `tfmg:generator` – magnet, wickler; Belohnung loot3
- `stromnetz` – Stromnetz – `tfmg:electric_post` 4, `tfmg:cable_connector` 4, `tfmg:light_bulb` – generator
- `wandler` – FE-Wandler – `tfmg:converter` – stromnetz
- `e_motor` – Elektromotor – `tfmg:electric_motor` – generator

**Abschluss:**
- `ende` (Stern) – Motorsteuerung – `tfmg:engine_controller` + `tfmg:large_engine` – motor, platine; Belohnung loot3
- Abgabe-Quests: 64 Koks, 16 Kunststoff

### 4.2 Weitere Create-Addons (in bestehende Kapitel)
**`create_2` (Fortgeschritten):**
- Numismatics:
  - `nm_laden` – Eigener Laden – `numismatics:vendor` – `create_1/messing`
  - `nm_bank` – Bankterminal – `numismatics:bank_terminal` – praezision
  - `nm_karte` – Bankkarte – `numismatics:white_card` – nm_bank
  - `nm_kasse` – Kassenautomat – `numismatics:brass_depositor` – nm_laden
- Mobile Packages:
  - `mp_biene` – Robo-Biene – `create_mobile_packages:robo_bee` – Quest zum Verpacker
  - `mp_port` – Bienenhafen – `create_mobile_packages:bee_port` – mp_biene
  - `mp_ticker` – Lager in der Tasche – `create_mobile_packages:portable_stock_ticker` – Quest zum Lagerticker
- Pattern Schematics:
  - `muster` (Herz) – Musterbauplan – `create_pattern_schematics:empty_pattern_schematic` – Bauplan-Quest

**`create_3` (Addons):**
- Ironworks:
  - `iw_zinn` – Zinn – `mekanism:ingot_tin` 8 (wird von Almost Unified vereinheitlicht) – `create_1/start`
  - `iw_bronze` – Bronze – `mekanism:ingot_bronze` 8 – iw_zinn
  - `iw_stahl` – Mixer-Stahl – IE-Stahl 8 – `create_1/messing`
  - `iw_hammer` – Stahlhammer – `create_ironworks:steel_hammer` – iw_stahl
- Extruder:
  - `extruder` – Extruder – `create_mechanical_extruder:mechanical_extruder` – `create_1/start`
  - `extruder_messing` – Messing-Extruder – `create_mechanical_extruder:mechanical_brass_extruder` – `create_1/messing`
- Ultimate Factory:
  - `uf_diamant` (Herz) – Diamantpresse – `minecraft:diamond` – `create_1/messing`. Text: 9 Kohleblöcke + Lava im beheizten Kompaktieren.

**`essen`:**
- `waffel` – Waffeln – `create_factory:waffle` 4 – Create-Anker
- `marmelade` – Marmeladenbrötchen – `create_factory:sweet_berries_jam_roll`

**Train Perspective:** keine Quest.

### 4.3 End Remastered (im Kapitel `dimensionen`)
- **Hauptlinie:** Der Schlüssel `enderauge` bleibt, damit der Fortschritt erhalten bleibt. Titel wird "Uralte Augen", Aufgabe `check`.
  - Text: Normale Enderaugen gehen nicht mehr. Man braucht 12 verschiedene Augen, die man werfen kann und die zur Festung zeigen.
  - Der Text von `end` wird angepasst.
- **Seitenast, 16 optionale Augen-Quests:**

| Quest | Woher | nach |
|---|---|---|
| `er_lost` | Minenschacht | – |
| `er_cold` | Iglu-Keller | – |
| `er_black` | Vergrabener Schatz | – |
| `er_corrupted` | Außenposten | – |
| `er_magical` | Waldanwesen/Evoker | – |
| `er_witch` | Hexenpupille + Enderauge | – |
| `er_undead` | Seele vom Skelettpferd | – |
| `er_evil` | Meister-Kleriker | – |
| `er_cryptic` | 1/120 beim Verzaubern | `erste_schritte/verzaubern` |
| `er_cursed` | Bastion-Schatz | Nether |
| `er_nether` | Festung, selten | Nether |
| `er_guardian` | Großer Wächter | – |
| `er_wither` | Wither | `bosse/wither` |
| `er_exotic` | Korallen + 2 Aquisitoren | – |
| `er_old` / `er_rogue` | selten, Dungeons Arise | – |

### 4.4 Enderscape (Kapitel `dimensionen`, Ast nach `end`)
- `es_biome` – Neue End-Biome – 4 Biome – end
- `es_nebulit` – Nebulit – `enderscape:nebulite` 3 – es_biome
- `es_shadolin` – Shadolin – `enderscape:shadoline_ingot` 4 – es_biome
- `es_drifter` – Driftgelee – `enderscape:drift_jelly_bottle` 4 – es_biome
- `es_hose` – Drift-Hose – `enderscape:drift_leggings` – es_nebulit, es_drifter
- `es_magnet` – Magnia-Anzieher – `enderscape:magnia_attractor` – es_shadolin
- `es_schild` – Geröllschild – `enderscape:end_stone_rubble_shield` – es_shadolin
- `es_ruinen` – Morast-Ruinen – Struktur `enderscape:mirestone_ruins` – es_biome
- `es_schluessel` – Endstadt-Schlüssel – `enderscape:end_city_key` – endstadt
- `es_spiegel` – Der Spiegel – `enderscape:mirror` – es_schluessel
- `es_rustle` (Herz) – Rustle im Eimer – `enderscape:rustle_bucket` – es_biome

### 4.5 Neues Kapitel `goety` "Goety" (Gruppe Magie, nach `evilcraft`, ca. 35 Quests)
**Hauptlinie:**
1. `start` – Ektoplasma (`goety:ectoplasm`) – `erste_schritte/eisen`
2. `wurzeltotem` – Wurzeltotem (`goety:totem_of_roots`)
3. `fluchmetall` – Verfluchtes Metall (`goety:cursed_ingot` 8)
4. `zaubersmaragd` – Magischer Smaragd (`goety:magic_emerald` 4)
5. `seelentotem` – Seelentotem (`goety:totem_of_souls`)
6. `stoffe` – Magische Stoffe (`goety:dark_fabric`, `goety:magic_fabric`)
7. `fokus` – Leerer Fokus (`goety:empty_focus`)
8. `zauberstab` – Dunkler Stab (`goety:dark_wand`). Text: Fokus mit Taste Z einsetzen.
9. `kaefig` – Seelenkäfig (`goety:cursed_cage`)
10. `altar` – Dunkler Altar (`goety:dark_altar` + `goety:pedestal` 4). Belohnung loot2.
11. `kern` – Animationskern (`goety:animation_core`). Text: Ritual mit 15 Leitern, 15 Schienen und einem Kürbis.
12. `faulfokus` – Fäulnis-Fokus (`goety:rotting_focus`). Text: Necroturgy, nur nachts.
13. `nekrostab` – Nekromantenstab (`goety:necro_staff`)
14. `seelensmaragd` – Seelensmaragd (`goety:soul_emerald`)
15. `kohlebecken` – Nekro-Kohlebecken (`goety:necro_brazier`). Text: Forge-Ritual.
16. `dunkelbarren` – Dunkles Metall (`goety:dark_ingot` 4)
17. `krypta` – Struktur `goety:crypt`
18. `schaedelfuerst` – Gegner `goety:skull_lord` töten
19. `verboten` – Verbotenes Wissen (`goety:forbidden_scroll`). Text: 81 Stücke, Antike Stadt.
20. `arca` – Die Arca (`goety:arca`)
21. `apostel` – Gegner `goety:apostle` töten. Text: Sabbath-Ritual, Netheritbarren, nicht im Nether kämpfen, Bann-Schaden hilft. Abhängigkeit zusätzlich `dimensionen/nether`.
22. `blut` – Unheiliges Blut (`goety:unholy_blood`)
23. `lich` – Trank der Wandlung (`goety:undeath_potion`). Text: 1000 Seelen, ein Dorfbewohner als Opfer.
24. `ende` (Stern) – Meister der Dunkelheit. Belohnung loot4.

**Seitenäste:**
- `infuser`: Fluch-Infuser, kommt auf einen Spawner.
- `knochenfokus`, `plagefokus`, `feuerfokus`
- `nekroset`: Krone und Umhang
- `schwarzeisen`, `dunkelruestung`
- `kristallkugel`: Struktur Verfallene Hütte, danach `crone` (Gegner töten)
- `wesir`: Dunkle Schriftrolle im Waldanwesen, danach `rubin` (`goety:soul_ruby`)
- `terminal`: Struktur `goety:final_terminal` im End, danach `endwaechter` (`goety:ender_keeper`)
- `hurrikan`: Trial Chambers
- `todessense`: `goety:death_scythe`

**Außerdem:**
- Im Kapitel `bosse` je eine Verweis-Quest auf Apostel und Wesir.
- Im Finale (Chronicle-Kern) den Lich optional als Alternative im Magie-Fragment aufnehmen. Zuerst klären, ob der Lich so weit gehen soll.

### 4.6 Kampf (Kapitel `waffen`)
**Paladins & Priests:**
- `moench` – Mönchswerkbank – `paladins:monk_workbench`
- `heiligstab` – Heiliger Stab – `paladins:holy_staff` – moench
- `prior` – Prior-Gewand – `paladins:prior_robe_chest` – heiligstab
- `kriegshammer` – Großhammer – `paladins:iron_great_hammer` – moench
- `kite_schild` – Drachenschild – `paladins:iron_kite_shield` – kriegshammer
- `paladin_ruestung` – Paladin-Rüstung – `paladins:paladin_armor_chest` – kriegshammer
- `kreuzritter` – Kreuzritter – `paladins:crusader_armor_chest` – paladin_ruestung, `dimensionen/nether`
- `netherit_paladin` – Netherit-Kreuzritter – `paladins:netherite_crusader_armor_chest` – kreuzritter, `erste_schritte/netherit`

**Shield Expansion:**
- `schild_eisen` – Eisenschild – `shieldexp:iron_shield`. Text: Parieren erklären.
- `schild_diamant` – Diamantschild – `shieldexp:diamond_shield` – schild_eisen
- `schild_netherit` – Netheritschild – `shieldexp:netherite_shield` – schild_diamant, `erste_schritte/netherit`

### 4.7 Fortbewegung (Kapitel `erste_schritte`, Seitenast)
- `enterhaken` – Enterhaken – `grapplemod:grapplinghook` – `erste_schritte/eisen`
- `haken_upgrade` – Haken-Upgrade – `grapplemod:baseupgradeitem` – enterhaken
- `haken_modifier` – Haken-Werkbank – `grapplemod:block_grapple_modifier` – haken_upgrade
- `haken_motor` – Motor-Upgrade – `grapplemod:motorupgradeitem` – haken_upgrade
- `enderstab` – Enderstab – `grapplemod:launcheritem` – enterhaken
- `haken_limits` – Grenzenlos – `grapplemod:limitsupgradeitem` – haken_modifier, `bosse/wither`

### 4.8 Welt und Alltag
**Kobolds (Kapitel `strukturen`):**
- `koboldbau` – Koboldbau – Struktur `kobolds:kobold_den`. Text: Handel gegen Smaragde.
- `kobold_vorlage` – Kobold-Vorlage – `kobolds:kobold_template` – koboldbau
- `kobold_schwert` – Kobold-Klinge – `kobolds:kobold_iron_sword` – kobold_vorlage

**Naturalist (Kapitel `essen` oder `willkommen`):**
- `fangnetz` – Fangnetz – `naturalist:capture_net`
- `schmetterling` – Schmetterling – `naturalist:butterfly` – fangnetz
- `glibber` – Leuchtschleim – `naturalist:glow_goop` – fangnetz
- `geweih` – Geweih – `naturalist:antler`
- `tornister` – Tornister – `naturalist:knapsack` – geweih

**Tide (Kapitel `essen`, eigener Ast, getrennt von Aquaculture):**
- `tide_angel` – Steinangel – `tide:stone_fishing_rod`
- `tide_buch` – Angeltagebuch – `tide:fishing_journal` – tide_angel
- `tide_koeder` – Köder – `tide:bait` – tide_angel
- `tide_tisch` – Angeltisch – `tide:angling_table` – tide_angel
- `tide_eisen` – Eiserne Tide-Angel – `tide:iron_fishing_rod` – tide_tisch
- `tide_kiste` – Holzkiste – `tide:wooden_crate` – tide_angel
- `tide_lava` – Lavafest – `tide:lavaproof_hook` – tide_tisch, `dimensionen/nether`
- `tide_void` – Leerehaken – `tide:void_hook` – tide_lava, `dimensionen/end`
- `tide_finder` – Fischfinder – `tide:fish_finder` – tide_buch
- `tide_diamant` – Diamant-Tide-Angel – `tide:diamond_fishing_rod` – tide_eisen, `erste_schritte/diamant`

**Serene Seasons (Kapitel `essen`):**
- `kalender` – Kalender – `sereneseasons:calendar`
- `gewaechshaus` – Tipp: Gewächshaus – `check`, nach kalender
- `jahreszeitsensor` – Jahreszeitsensor – `sereneseasons:season_sensor`, nach kalender

**L2 Hostility (kleines Kapitel `hostility` oder Ast in `bosse`):**
- `hostility_start` – Gefahrenkompass – `l2hostility:hostility_detector`
- `hostility_brille` – Unsichtbares sehen – `l2hostility:detector_glasses` – hostility_start
- `hostility_charm` – Beute-Talisman – `l2hostility:charm_of_looting_1` – hostility_start
- `hostility_orb` – Hostility-Kugel – `l2hostility:hostility_orb` – hostility_start
- `hostility_sanity` – Flasche der Vernunft – `l2hostility:bottle_of_sanity` – hostility_orb
- `hostility_chaos` – Chaos-Barren – `l2hostility:chaos_ingot` – hostility_orb
- `hostility_wunder` – Wunder-Barren – `l2hostility:miracle_ingot` – hostility_chaos

## 5. Ablauf beim Einbau

1. Marvin entscheidet die Punkte aus Abschnitt 2 und sucht die Mods aus.
2. Die Mods kommen in `$ModList`, zusätzlich die fehlenden Abhängigkeiten aus Abschnitt 1. Index und MODLISTE werden per Python nachgetragen, `PACKVER` 0.9.13.
3. Configs anpassen: endrem, l2hostility, eventuell Tide oder Shield Expansion. Wenn gewünscht, das Messing-Rezept per KubeJS zurücksetzen.
4. `ids.json`/`rezepte.json` aus den Jars ergänzen und die Quests schreiben. Der Generator muss ohne Fehler durchlaufen.
5. Dazu die EvilCraft-Texte aus dem Handbuch verbessern (offene Aufgabe 5 in CLAUDE.md).
6. Commit und Push auf den Arbeitsbranch. Den PR und das Release gibt es erst, wenn Marvin "mach den Release" sagt. Vorher einen Servertest mit End-Chunks nur nach Rückfrage (wegen Enderscape und Better End).
