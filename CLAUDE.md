# Chronicle (Modpack) – Projektstand (Übergabe an Claude Code)

Eigenes Minecraft-Modpack **Chronicle** (bis 0.9.10 "Unser Modpack") für Chronixx und Freunde, als Nachfolger für ATM10.
Schwerpunkte: Ars Nouveau (Magie), Create mit vielen Addons, AE2 mit Addons, neue Dimensionen, Bosse, Waffen.
Kommunikation mit dem Nutzer: Deutsch, locker, kurz.
**Tests nur nach Rueckfrage:** Servertests (lokal in der Cloud oder per Workflow-Start) nie von selbst starten, immer erst fragen und auf Marvins OK warten. Der automatische Test von GitHub bei PRs ist davon ausgenommen.
**PR/Release nur auf Ansage:** Aenderungen nur committen und auf den Arbeitsbranch pushen. Einen PR (und damit ein Release) erst erstellen, wenn Marvin es sagt, er will vorher oft noch mehr aendern.

## Eckdaten
- Minecraft **1.21.1**, Loader **NeoForge 21.1.x** (zuletzt 21.1.255), Java 21
- Launcher: **Modrinth App**; Profile liegen unter `C:\Users\Marvin\AppData\Roaming\ModrinthApp\profiles\Chronicle-<version>\`
- Logs: `...\profiles\Chronicle-<version>\logs\latest.log`, Crashs unter `crash-reports\`
- Kampfsystem: **Vanilla**, also bewusst ohne Better Combat und ohne Epic Fight
- Ziel: **eine einzige .mrpack-Datei**, die die Freunde in die Modrinth App importieren
- PC: Windows 11, RTX 5070, Azul Java 21

## Dieser Ordner (Modpack-Builder)
- `START.bat` startet `build-und-test.ps1` (PowerShell 5.1, Datei ist UTF-8 mit BOM und nur ASCII-Text)
- Ablauf des Skripts:
  1. Jede Mod aus `$ModList` (Format `slug|Name|optional-Versionsregex`) über die Modrinth-API v2 auflösen. Zuerst wird der Slug versucht, danach eine Namenssuche mit **nur exakten** Treffern.
  2. Required-Abhängigkeiten rekursiv auflösen.
  3. `modrinth.index.json` schreiben. CurseForge-Jars aus `curseforge-mods\` kommen nach `overrides/mods`. Daraus entsteht `Chronicle-<PACKVER>.mrpack`. Danach wird sofort `ERGEBNIS.txt` geschrieben.
  4. Einen NeoForge-Testserver aufsetzen und starten, bis `Done (` im Log steht. Danach wird `ERGEBNIS.txt` aktualisiert.
- Das Skript schreibt ausserdem `MODLISTE.txt` (alle Mods nach Kategorie; Kategorien = `# `-Zeilen in `$ModList`, CF-Jars ueber `$cfCat`) und `pack/modrinth.index.json`. Beide nach jedem Build mit committen.
- **Ausschlusslisten:** `pack/server-ausschluss.txt` und `pack/client-ausschluss.txt` (Dateinamen-Muster). Das Skript setzt dafuer `env.server`/`env.client` auf `unsupported` im .mrpack. Aktuell: Chunky nur Server, Distant Horizons/Xaero/Sound Physics nur Client.
- **Release:** `.github/workflows/release.yml` ruft `.github/scripts/pakete-bauen.sh` (baut `.mrpack`, `-Client.zip` mit mods-Ordner, `-Server.zip` mit Server-Mods, NeoForge-Installer, `start.sh`/`start.bat`, `user_jvm_args.txt` (12 GB), `server.properties`, `eula.txt`, LIES-MICH) und `.github/scripts/servertest.sh` (Server-ZIP entpacken, starten bis `Done (`). Vorlagen fuer die Server-/Client-Dateien liegen in `pack/server/` und `pack/client/` (Platzhalter `@VERSION@`, `@MC@`, `@NEOFORGE@`).
  - Pull Request: bauen + Servertest. Push auf `main` mit neuer `versionId` in `pack/modrinth.index.json`: zusaetzlich Release `v<Version>` mit allen Dateien. Gibt es das Release schon, passiert nichts. Tags kann Claude aus der Cloud-Umgebung nicht pushen.
  - Lokal testbar: `bash .github/scripts/pakete-bauen.sh dist && bash .github/scripts/servertest.sh dist/Chronicle-<ver>-Server.zip`
- **Questbuch (FTB Quests):** Inhalt in `pack/quests/inhalt/*.py` (ein Teil pro Datei, Reihenfolge in `generator.py` `TEILE`), Format in `pack/quests/dsl.py`, Regeln + Anker (kapiteluebergreifende Schluessel) in `pack/quests/AUTOREN.md`. `pack/quests/generator.py` schreibt `pack/config/ftbquests/quests/` (data.snbt, chapter_groups.snbt, chapters/*.snbt, reward_tables/*.snbt, lang/en_us.snbt; Texte stehen in en_us, damit sie in jeder Client-Sprache erscheinen). Aufruf: `python3 pack/quests/generator.py --ids ids.json --rezepte rezepte.json [--bilder ordner]`, Ergebnis mit committen.
  - Quest-IDs = Hash aus Kapitel + Schluessel, **auf 63 Bit begrenzt** (FTB Quests liest IDs als vorzeichenbehaftete Zahl; groessere IDs werden stillschweigend zu 0 und neu vergeben -> "Unnamed" und kaputte Abhaengigkeiten, so passiert in 0.9.7). Schluessel nicht umbenennen, sonst geht Fortschritt verloren.
  - `ids.json` = Registry-Dump (KubeJS auf dem Testserver) plus Dimensionen/Strukturen/Struktur-Tags/Biome aus den Jars; `rezepte.json` baut `pack/quests/rezepte.py <mods-ordner> <aus.json>`. Nachschlagen: `pack/quests/such.py`. Beide JSONs sind nicht im Repo (je ~5 MB).
  - Modus `linear` (Quests gesperrt bis Vorgaenger fertig); Quests mit Kill/Dimension/Struktur/Biom setzt der Generator auf `flexible`, sonst zaehlen sie erst nach dem Freischalten. Abgabe-Quests (`abgabe=True`) verbrauchen Items. Belohnungen: XP, Items, Loot-Kisten `loot1`-`loot4` (`inhalt/loot.py`).
  - `pack/config/` landet in `overrides/config` (.mrpack) bzw. `config/` (beide ZIPs) und im lokalen Testserver. Der Servertest gibt die Zeile `Questbuch: Loaded ... quests` aus.
- **Webseite:** `site/index.html` (statisch, Netlify-Projekt `chronicle-modpack`, Tabs Uebersicht/Guide/Skilltree/Modliste/Changelog; Item-Icons in `site/img/items/`). `python3 tools/seite_aktualisieren.py` schreibt `site/modliste.json` aus `MODLISTE.txt` und setzt die Mod-Anzahl in `index.html`, nach jedem Build mitlaufen lassen und committen. Von Hand bleiben Changelog, Questzahlen und Guide-Rezepte in `index.html`. `netlify.toml` setzt `site/` als Veroeffentlichungsordner.
- CurseForge-Jars holt das Skript automatisch aus `%USERPROFILE%\Downloads` (Dateinamen-Muster in `$cfPatterns`).
- Aktuelle Version: **PACKVER 0.9.30**. Bei jeder Änderung `$PACKVER` hochzählen.

## Mods, die nur auf CurseForge sind (liegen in curseforge-mods\ bzw. Downloads)
Alle für 1.21.1 NeoForge. Download-URL-Schema: `https://www.curseforge.com/minecraft/mc-mods/<slug>/download/<fileId>`

| Mod | CF-Slug | File-ID |
|---|---|---|
| The Twilight Forest 4.8.3345 | the-twilight-forest | 7797302 |
| ExtendedAE 2.2.39 | ex-pattern-provider | 9044672 |
| Ars Elemental 0.7.10.4 | ars-elemental | 9057244 |
| Ars Technica 2.7.6 | ars-technica | 7642730 |
| FTB Quests 2101.1.36 | ftb-quests-forge | 8885017 |
| FTB Library 2101.1.37 | ftb-library-forge | 9008089 |
| FTB Teams 2101.1.11 | ftb-teams-forge | 8724782 |
| FTB Backups 2 1.0.28 | ftb-backups-2 | 5501351 |
| AE2 Network Analyser 2.1.5 | ae2-network-analyser | 7622554 |
| AllTheModium 3.1.1 | allthemodium | ? |
| AllTheLeaks 1.1.13 | alltheleaks | ? |

CurseForge blockt Downloads per Skript, auch über die interne `/api/v1`. Diese Dateien gehen nur über den Browser oder per Hand. Das Pack darf deshalb nur privat geteilt werden und nicht öffentlich hochgeladen werden, wegen der Lizenzen der Mods.

## Was schon schiefging (nicht wiederholen)
- **Unscharfe Suche** hat falsche Mods eingebaut (Tensura, ExtendedAE Plus, Ars Elemancy, Antimagick, FTB Quests Optimizer). Deshalb gilt jetzt: nur exakte Treffer.
- **Rate-Limit**: Modrinth erlaubt etwa 300 Anfragen pro Minute. Das Skript wartet 350 ms zwischen Anfragen und bei 429 jeweils 20 s, mit bis zu 10 Versuchen.
- **Bibliotheken**, die Modrinth nicht als Abhängigkeit einträgt, stehen explizit in der Liste: Iron's Lib (`^1\.21\.1-2`), Bundle API, SmartBrainLib, Lithostitched, Structure Pool API, PolyLib, Atlas Api (fuer Iron's Gems 'n Jewelry) und RunicLib (fuer Dungeon's Delight).
- **Chunky + Distant Horizons** zusammen crashen (`IllegalStateException: Chunky is not loaded` aus DHs ChunkyAccessor beim ersten Chunk), auch im Einzelspieler. Deshalb Chunky nur Server, DH nur Client.
- **env client**: Viele Bibliotheken sind auf Modrinth als „nur Server“ markiert, werden im Client aber trotzdem gebraucht. Deshalb steht `client = 'required'` immer.
- **Iris** (0.9.19 wieder drin als 1.8.14-beta.1): Iris 1.8.12 passte nicht zu Sodium 0.8.13 (Fehler `SodiumGameOptions$PerformanceSettings` ClassNotFound) und hat beim Betreten der Welt den Absturz ausgelöst.
- **Relics entfernt**: braucht OctoLib, das es nicht auf Modrinth gibt.
- **Andere Slugs** als vermutet: `repurposed-structures-forge`, `betternether-neoforge`, `betterend-neoforge`.
- Nicht für 1.21.1 verfügbar und deshalb gestrichen: Blue Skies, Lost Aether Content, T.O Magic 'n Extras.

## Bekannte harmlose Log-Meldungen
- Sable: `Failed to apply tag physics properties. Unknown block: copycats:copycat_catwalk`
- Bumblezone: `Trade result entry is set to required ...`
- `Couldn't parse element ... loot_table]:railways:blocks/track_*` (ca. 200x), dazu `create_connected:` und `dndecor:`: Loot-Tables fuer Kompat-Bloecke von Mods, die nicht im Pack sind (BYG, TFC, Nature's Spirit ...)
- `AbstractVillager: Failed to load offers: Unknown registry key ... croptopia:` bzw. `cyclic:`: vorgebaute Villager in Struktur-Dateien mit Handel fuer fehlende Mods
- `Parsing error loading recipe` (ca. 55x, v. a. cataclysm_spellbooks, garnished, more_rpg_classes): optionale Kompat-Rezepte

## Stand
- Profil 0.6.0 plus händisch PolyLib und die CF-Mods: **Spiel startet, die Welt lädt (unter 8 s), kein Crash.** Es sind 185 Mods inklusive Bibliotheken.
- v9 (0.9.0) per Servertest in der Cloud geprueft (2026-10-06): 180 Mods auf dem Server, Start ohne Fehler; Chunks in Nether, End, Overworld, Twilight, Aether, Otherside, Undergarden, Bumblezone generiert, kein Crash. Better Nether/End- und Repurposed-Structures-Biome/Strukturen werden per `locate` gefunden. Rund 60 Rezept-Parsingfehler (optionale Kompat-Rezepte, v. a. Cataclysm Spellbooks, Garnished) sind harmlos. **Client noch nicht getestet.**
- Bugfix im Skript: Dateinamen mit `[ ]` (z. B. Born in Chaos) wurden nicht in den Testserver kopiert (PowerShell-Platzhalter), jetzt `-LiteralPath`.
- 0.9.1: Slug von AE2 Wireless Terminals korrigiert (`applied-energistics-2-wireless-terminals`, vorher nicht gefunden).
- 0.9.2: Create: Steam 'n' Rails eingebaut. Offiziell gibt es die Mod nur bis 1.20.1, deshalb der inoffizielle Port `create-steam-n-rails-1.21.1` (das Skript nimmt die Release-Version 0.2.1, Create 6.0.10 passt). Servertest 0.9.2: Start OK, 173 Modrinth- plus 9 CF-Mods, AE2 Wireless Terminals wird jetzt gefunden.
- 0.9.3: 15 neue Mods (Create: Bits 'n' Bobs, Diesel Generators, Interiors, Train Utilities, Railways Navigator, Metallurgy, Mechanical Spawner, Mekanism, Applied Mekanistics, Productive Bees, Ars Ocultas, Mystical Agriculture, Carry On, Traveler's Backpack, Alex's Caves (Unofficial Port, per Projekt-ID `kSsLSjEt`)). Servertest: 206 Dateien im Pack, 194 Modrinth- plus 9 CF-Mods auf dem Server, Start OK, Chunks in Overworld (inkl. Magnetic Caves von Alex's Caves), Nether, End, Twilight generiert, kein Crash. Client noch nicht getestet.
- Fehlende Abhaengigkeit `cC0JkO8i` (Ars Creo, Not Enough Glyphs) in ERGEBNIS.txt: Projekt ist auf Modrinth geloescht, die Mods laden trotzdem, ignorieren.
- 0.9.4: AllTheModium 3.1.1 (offizielle CF-Version, thevortex) in curseforge-mods. Das Modrinth-Projekt `allthemodium` ist ein fremder Reupload (jpaulmasonry, ARR, ohne Quellcode) und wird bewusst NICHT benutzt. Servertest 0.9.4: Start OK, Chunks in Mining Dimension, The Other, The Beyond, Nether, End, Overworld generiert, keine neuen Fehler.

- 0.9.5: Client-Test durch Marvin: einwandfrei.
- 0.9.6: Technik-Paket (Draconic Evolution (Beta), Industrial Foregoing, PneumaticCraft, Immersive Engineering, Extreme Reactors, Oritech, Ender IO (Beta), Iron Jetpacks, Actually Additions, Integrated Dynamics). Camera Overhaul und AmbientSounds entfernt (soll jeder selbst entscheiden).
- 0.9.7: Questbuch mit 12 Kapiteln und 233 Quests (Willkommen, Create, Ars Nouveau, Iron's Spells, AE2, Mekanism, Samen und Bienen, Draconic, Dimensionen, Bosse, Apotheosis, Energie und Technik). Alle IDs gegen die echte Registry geprueft, Servertest OK (`Loaded 12 chapters, 233 quests`). Aussehen im Spiel noch nicht geprueft.
- 0.9.8: Questbuch 2.0: 6 Gruppen, 33 Kapitel, 1178 Quests, 4 Loot-Kisten-Stufen. Fix fuer "Unnamed"/falsche Reihenfolge (63-Bit-IDs). Reihenfolge gegen Rezepte geprueft, Vorschaubilder pro Kapitel.
- 0.9.9: Performance: FastSuite, FastFurnace, FastWorkbench, Packet Fixer, dazu nur fuer den Client BadOptimizations, Dynamic FPS und More Culling. Server-`user_jvm_args.txt` mit Xms=Xmx=12G und Aikar-G1-Flags (der Servertest ueberschreibt die Datei mit wenig RAM, die Flags testet er also nicht). `simulation-distance` 6. Im Index nachgetragen, ohne kompletten Neu-Build (in der Cloud gibt's kein pwsh), andere Mods also unveraendert. Bewusst nicht aufgenommen: C2ME und ScalableLux (Alpha), Noisium (veraltet), AllTheLeaks (nur CF), ModernFix `dynamic_resources` (ungetestet).
- 0.9.10: Lootr, EMI Loot, Easy Villagers, Trade Cycling, Elevator Mod, Cooking for Blockheads, Pipez, Refined Storage, EvilCraft, dazu AllTheLeaks (CF). Questbuch: neue Kapitel Refined Storage und EvilCraft, Ergaenzungen in Willkommen (EMI Loot, Lootr), Erste Schritte (Aufzug), Lager (Pipez), Essen (Cooking for Blockheads), Strukturen (Dorfbewohner); 35 Kapitel, 1231 Quests. `ids.json`/`rezepte.json` dafuer aus den Jars ergaenzt (kein Servertest).
- 0.9.11: Pack heisst jetzt **Chronicle** (Name im .mrpack, Dateinamen `Chronicle-<ver>.*`, Release-Titel, MOTD, Questbuch-Titel, LIES-MICH).
- 0.9.12 (Release v0.9.12, 2026-10-07): **Almost Unified** (Standard-Config: Prioritaet minecraft > create > immersiveengineering > mekanism; z. B. Stahl/Silber/Elektrum/Nickel/Uran werden zu IE). Quests, die Varianten anderer Mods verlangten (Mekanism-/Oritech-/Ad-Astra-Stahl, Occultism-Silber usw.), auf die IE-Items umgestellt. **Chronicle-Kern** (eigenes Endgame): KubeJS-Items `kubejs:technik_fragment`, `magie_fragment`, `abenteuer_fragment`, `chronicle_core` in `pack/kubejs/` (startup_scripts, Rezepte als JSON in data/kubejs/recipe, Texturen, Namen); der Kern im mechanischen Crafter (5x5). Questbuch-Finale: Fragmente + Kern vor `ende`. `pack/kubejs` landet in overrides/kubejs bzw. kubejs/ beider ZIPs. GitHub-Servertest OK (258 Mods, 374 s, `Loaded 35 chapters, 1235 quests`, keine ungueltigen Items, KubeJS-Items registriert). Ob die KubeJS-Rezepte laden, zeigt der Servertest nicht (Rezeptfehler werden gefiltert), also im Spiel per EMI pruefen.

- 0.9.13 (Release v0.9.13, 2026-10-07; GitHub-Servertest OK nach 334 s, 282 Server-Mods, `Loaded 38 chapters, 1419 quests`, keine ungueltigen Items): Umsetzung von `PLAN-0.9.13.md`.
  - EMI ersetzt durch **JEI**, EMI Loot durch **Just Enough Resources** (nur Client). Questtexte auf JEI umgestellt, die Schluessel `emi`/`emi_loot` bleiben.
  - **20 neue Mods:** Numismatics, TFMG, Mobile Packages, Pattern Schematics, Ironworks, Mechanical Extruder, Ultimate Factory, Create Factory, Train Perspective, Goety, End Remastered, Enderscape, L2 Hostility, Kobolds, Paladins & Priests, Shield Expansion, Grappling Hook, Naturalist, Tide, Serene Seasons. Dazu 6 Bibliotheken; insgesamt 303 Mods.
  - **Neuer Ordner `pack/defaultconfigs`** (landet in overrides/defaultconfigs und in beiden ZIPs). Darin ist L2 Hostility entschaerft: `dimensionFactor` 2, `allowLegendary` false. Die Datei gilt als Server-Config pro Welt und greift nur bei neuen Welten.
  - **KubeJS:** `pack/kubejs/data/create/recipe/mixing/brass_ingot.json` ueberschreibt Ironworks, damit Messing wieder 2 Barren gibt.
  - **End Remastered streng:** Normale Enderaugen gehen nicht, man braucht 12 von 16 Augen.
  - **Questbuch:** 38 Kapitel, 1419 Quests.
    - Neue Kapitel: `tfmg` (Create 4), `goety`, `hostility` (Gefahrenstufen).
    - Neue Aeste in create_2, create_3, dimensionen (Augen, Enderscape, Endstadt alt/neu), strukturen (Kobolds), waffen (Paladins, Schilde), erste_schritte (Enterhaken), essen (Tide, Naturalist, Jahreszeiten, Create Factory) und bosse (Goety-Bosse).
    - EvilCraft-Texte aus dem Handbuch. Die Quest `evilcraft/akkumulator` verlangt jetzt den Akkumulationskern, weil der Akkumulator nicht craftbar ist.
    - Uebrig bleibt eine Warnung: `refined_storage/silizium`. Sie ist falsch, weil `refined_storage/start` den Nether schon voraussetzt.
  - **TFMG = Community Edition** (`tfmg-community-edition` 1.3.2a, gleiche modId `tfmg`). Das Original TFMG 1.2.0 laesst sich nicht mit Bits 'n' Bobs starten (Absturz im PR-Servertest: "bits_n_bobs is incompatible with tfmg 1.2.2 or below"). In CE gibt es keine Motorsteuerung und keinen FE-Converter mehr. Die Quests sind darauf angepasst (`tfmg/ende` = Grossmotor, `tfmg/akku` statt Wandler).
  - `ids.json`/`rezepte.json` aus den neuen Jars ergaenzt.
  - Im Spiel pruefen:
    - Ist das Enderscape-Datapack `new_end_cities` aktiv? Laufen Enderscape und Better End zusammen?
    - Gehen die TFMG-Rezepte (Community Edition) mit Almost Unified noch? Betroffen sind `heavy_plate` und `rebar`.
    - Kommt das Messing-Rezept wieder mit 2 Barren?
    - Greift die L2-Config in einer neuen Welt?

- 0.9.14: Chronicle-Kern, Fragmente: Items per `StartupEvents.modifyCreativeTab` in den Tab "Zutaten" (ohne Tab fehlten sie in der JEI-Liste; `.group()` gibt es in KubeJS 7 nicht mehr und bricht den Start ab). Rezepte laden serverseitig korrekt (Pruefskript). **Startkiste** (`pack/kubejs/server_scripts/startkiste.js`): beim ersten Login Questbuch, Fleisch, Fackeln, Steinspitzhacke/-axt. **ModernFix `dynamic_resources`** an (`pack/config/modernfix-mixins.properties`), bei Grafikfehlern auf false. C2ME bewusst nicht (nur Alpha). Im Spiel pruefen: JEI zeigt Fragmente/Kern, Startkiste kommt genau einmal, keine fehlenden Modelle.

- 0.9.15: Questbuch-Layout entwirrt (`generator.py`: ueberfluessige Abhaengigkeiten entfernt, `zeile` ist nur noch Startwert, lokale Suche gegen Linienkreuzungen, 199 -> 77 im ganzen Buch, `haupt` bleibt auf Zeile 0). Rezepte von Fragmenten und Kern stehen jetzt zusaetzlich im Questtext. **Offen:** JEI zeigt auf dem Client keine Rezepte fuer Fragmente/Kern, obwohl sie serverseitig (nach Almost Unified) vorhanden sind; Ursache unbekannt. Test: `/recipe give @s kubejs:technik_fragment`, Rezeptbuch in der Werkbank.
- **Bossfuehrer** (0.9.15 als Patchouli-Buch, ging im Spiel NICHT: Patchouli liest Buecher nur aus Mod-Jars, nicht aus KubeJS-Daten). Ab 0.9.17 als **Modonomicon**-Buch (Datapack-faehig): `python3 pack/quests/bossbuch.py` erzeugt aus den kill-Quests 72 Eintraege in 16 Kategorien (je Mod) nach `pack/kubejs/data/chronicle/modonomicon/books/bossfuehrer` plus Texte in `pack/kubejs/assets/chronicle/lang/en_us.json`. Texte kommen aus den Quests, also dort aendern und neu erzeugen. Item `modonomicon:modonomicon[modonomicon:book_id="chronicle:bossfuehrer"]` in der Startkiste und per Werkbank-Rezept `chronicle:bossfuehrer` (Buch + Eisenschwert). Im Spiel ungeprueft.

- 0.9.16 (in Arbeit, Plan: `PLAN-0.9.16-skilltree.md`): **Skilltree** mit Pufferfish's Skills (`skills`, 0.19.1). Spec in `pack/skilltree/spec.py`, `python3 pack/skilltree/generator.py` schreibt `pack/kubejs/data/puffish_skills/puffish_skills/` (111 Knoten, 5 Aeste, Wirkung auf alle passenden Mods, alles nur echte Attribute). Lokaler Servertest 2026-10-08: Datapack laedt, alle 38 Attribut-IDs existieren. Punkte kommen aus Kills (Bosse viel) und Erzen (`experience.json`). **Gestrichen:** Quest-Belohnungen mit Punkten, Skript-Werte (Bossschaden/Nahrung/Haltbarkeit), Respec, Startkisten-Hinweis. Im Client ungetestet: Aussehen, Oeffnen (Taste K), Staerke der Boni, Tempo des Levelns.

- 0.9.17 (Release geplant): Bossfuehrer als Modonomicon-Buch (siehe oben) und **6 Cataclysm-Addons** (310 Mods): Integrated Cataclysm (`integrated-catalcysm`, ueberarbeitet alle Cataclysm-Strukturen und einige Rezepte mit Create), Goety Cataclysm, Mowzie's Cataclysm, L_Ender's Cataclysm Delight, GTBC's Cataclysmic Boss UI, Cataclysm x Create Integration (`cataclysm_create`). Bewusst nicht: Cataclysm Dimension (jede Struktur in eigener Dimension, beta, aendert den Fortschritt ueber die Augen; ggf. spaeter mit Config "keep structures"), Cinematic Cataclysm, Waffen-/Werkzeug-Addons, Expeditions. Im Spiel pruefen: Cataclysm-Rezepte in Quests (Integrated Cataclysm und Create-Addon aendern Rezepte), Struktur-Aussehen.

- 0.9.18 (Release geplant): **314 Mods**. Neu: Deep Aether (`deep-aether`, bringt AeroBlender per jar-in-jar mit), Alshanex's Familiars (`alshanexs-familiars`) plus Bibliothek Familiarslib, Create: Wizardry (`create-wizardry`). Questbuch: 11 neue Quests (Deep Aether im Kapitel dimensionen: Biom, Skyjade, Boss `deep_aether:eots_controller`; Create: Wizardry (5) und Familiars (3) im Kapitel irons_spells), 1430 Quests; Bossfuehrer um Deep Aether erweitert (73 Bosse, 17 Kategorien). **Questbuch-Layout** (`generator.py` `_entwirren`): Kosten jetzt mit echten Koordinaten, Linien durch fremde Quests werden bestraft (`NAH`), Linien-Laenge staerker gewichtet, danach deterministisches Simulated Annealing (Seed 7). Der Generator braucht dadurch ca. 2 Minuten. Mit `--bilder <ordner>` lassen sich die Kapitel als PNG ansehen. Im Spiel pruefen: Layout der grossen Kapitel (Create, Dimensionen, Draconic), neue Quests, Deep Aether/Familiars starten ohne Fehler.

- 0.9.19 (Release auf Ansage): **Iris 1.8.14-beta.1** (`iris`, nur Client, 315 Mods) fuer Shader. Index von Hand ergaenzt (Cloud hat keinen Modrinth-Zugriff). Bekannte Iris-Issues: Startabsturz Sodium 0.8.13 (#3402, offen), Absturz nach Serverbeitritt (#3187), Aufloesung im Spiel aendern macht Bloecke unsichtbar (#3214), leeres Shader-Menue -> Hintergrund-Blur aus. **Im Client pruefen:** Start, Welt, Serverbeitritt, Shader an/aus. Wenn es crasht: Iris wieder raus (Zeile in `$ModList`, Index, MODLISTE).

- 0.9.20 (in Arbeit): **Eigener Ladebildschirm**. Mods (nur Client, 319 Mods): FancyMenu 3.9.14, Drippy Loading Screen 3.1.5, dazu Konkrete und Melody. Entwurf (Funken, Logo CHRONICLE, gruener Pixel-Balken, Tipps, ohne Untertitel und Versionsnummer) wurde von Marvin abgenommen; er will auch die Beitritts-Bildschirme (Verbinde/Lade Gelaende) im gleichen Stil. **Noch offen:** die Layouts selbst (`config/fancymenu`, `config/drippyloadingscreen`) entstehen im FancyMenu-Editor im Spiel und wurden noch nicht erstellt; Dateien dann nach `pack/config/` legen. **Texturen (fest im Pack):** `python3 tools/ladebild_texturen.py` erzeugt `pack/kubejs/assets/minecraft/textures/gui/title/mojangstudios.png` (CHRONICLE-Logo statt Mojang) und `menu_background.png` (dunkle Kachel fuer Beitritts-/Menuebildschirme). Ob KubeJS-Assets schon beim ersten Startbildschirm greifen und ob das Logo-Layout (zwei Haelften uebereinander) stimmt, ist ungeprueft; sonst als normales Ressourcenpaket in `resourcepacks/`. Nicht im Client getestet.

- 0.9.21 (Release auf Ansage): **NoTimeOut** (`notimeout`, 1.0.0) verlaengert Login-, Keep-Alive- und Read-Timeout des Servers. Grund: Nach einem Server-Neustart flog man kurz nach dem Joinen mit "Timed out" raus (Server-Log: 40 s nach dem Login, Server dabei 9 s im Rueckstand). Index von Hand ergaenzt. Ungetestet; wenn es nicht hilft, Client-`latest.log` anschauen, ggf. `view-distance` 8.

- 0.9.22 (Release auf Ansage, erst wenn die Arena-Fixes fertig sind): **Minebohrer** 0.5.2 (eigene Mod von Chronixx, Jar liegt in `curseforge-mods/`, Muster in `$cfPatterns`, Kategorie in `$cfCat`) und die **Chronicle Arena** (`mod/chronicle-arena`, wird von `pakete-bauen.sh` gebaut). **FancyMenu, Drippy Loading Screen, Konkrete und Melody entfernt** (der eigene Ladebildschirm aus 0.9.20 ist damit gestrichen; die Texturen `mojangstudios.png`/`menu_background.png` in `pack/kubejs/assets` bleiben vorerst). 317 Mods.

- 0.9.23 (Release): **Arena-Update** (groessere Arena, Balkon, Wiederbeleben, Segen, Mini-Bosse, Abgrund-Modus, Glut-/Eis-Arenen, Schmied, Kill-Cam, PvP aus, Hostility-Datapack). Mod-Stand siehe Branch `claude/project-thread-vy4xq5`. Keine Aenderung an der Modliste (317 Mods).

- 0.9.24 (Release): **7 Mods, jetzt 324**: Create: Automotives 1.1 (braucht Immersive Vehicles 24.0.0, ebenfalls neu), Small Ships 2.0.0-b2.1 (Beta, einzige 1.21.1-Version), Effortless Building 4.2, Another Furniture 4.0.3, Decocraft 3.0.11 (93 MB), Illarilover17's Overwatch Plushies 0.3.1. Index von Hand ergaenzt. **Croptopia Delight nicht eingebaut:** braucht Croptopia, das es nur auf CurseForge gibt (Jar liegt nicht in `curseforge-mods`). Wenn Marvin die Croptopia-Jar bringt, beide nachziehen.

- 0.9.25 (Release): **Small Ships und Overwatch Plushies raus, Immersive Aircraft 1.6.0 rein (323 Mods).** Bits And Chisels gibt es nur fuer Fabric/1.20.1, also nicht eingebaut (Alternative waere Chisels & Bits fuer NeoForge, nur auf Wunsch).

## Offene Aufgaben
1. **0.9.15 im Client testen:** kurz in eine neue Welt, auf Grafikfehler achten. Pruefen: Rezepte der drei Fragmente und des Chronicle-Kerns in JEI, Almost Unified (Mekanism-Stahl wird zu IE-Stahl), keine doppelten Erze, dazu die Punkte unter "Im Spiel pruefen" bei 0.9.13.
2. **Questbuch im Spiel ansehen** (seit 0.9.8): Texte, Reihenfolge, Bilder. Aenderungen in `pack/quests/inhalt/*.py` machen (nicht im Editiermodus, sonst ueberschreibt der naechste Generatorlauf sie).
3. **Server auf dem Heimserver** einrichten: ZimaOS, Marvin hat dort schon ein eigenes Docker-Panel fuer mehrere Minecraft-Server (keine docker-compose noetig). Dafuer das `-Server.zip` aus dem Release nehmen (12 GB RAM fuer Minecraft, Container mind. 14 GB, Java 21, Ports 25565/TCP und 24454/UDP fuer Voice Chat), danach mit Chunky vorgenerieren.
4. Erst danach die **richtige Welt** starten, damit Strukturen und Biome ueberall generiert werden.
5. (erledigt in 0.9.13: EvilCraft-Questtexte aus dem Handbuch)
6. (erledigt: 0.9.13 released)
7. Optional: Shader wieder einbauen, sobald es eine Iris-Version gibt, die zu Sodium 0.8 passt.

## Mod-Bauplan (Übersicht)
Artifact „Modpack-Bauplan 1.21.1“ auf claude.ai (Checkliste mit 143 Mods nach Kategorien). Die maßgebliche Liste ist aber `$ModList` in `build-und-test.ps1`.

- 0.9.26: Eigene Keybind Overview 1.0.0 (324 Mods). Aktueller Build aus Marvins KeybindOverview-Projekt mit Suche, Filtern und kompakter Ansicht. Lokale reine Client-JARs liegen in `pack/client-mods`; beide Builder kopieren sie nach `client-overrides/mods` der .mrpack, der Release-Builder auch in die Client-ZIP. Kein Einbau in Server-ZIP/Testserver. Aufruf ueber die Tastenbelegungsseite; K ist auch vom Skilltree belegt und kann im Spiel neu zugewiesen werden.

- 0.9.27: Strukturdichte in der Oberwelt reduziert (159 Structure-Sets, 25 Namensraeume). WDA/Seven Seas etwa -75 %, ausgewaehlte Strukturpakete -55 bis -60 %, normale Zusatzbauten etwa -50 %, Boss-Orte etwa -45 %. Keine Mods entfernt; Waystones, Doerfer und andere Dimensionen bleiben erhalten. Overrides unter pack/kubejs/data; Profil, Ausnahmen und Pflege in pack/strukturdichte.md und tools/strukturdichte.py. Die Release-Action prueft sie gegen die gepinnten JARs. Wirkt nur in neuen Chunks.

- 0.9.28: Tom's Storage 2.4.2, Mekanism: Ad Astra Ores 1.1.0, Mahou Tsukai 1.36.27, Youkais' Feasts 1.1.1, Building Gadgets 2 1.3.9 sowie XyCraft Core/World/Machines 0.7.53. Planets+ und Ad Astra: Rocketed auf Nutzerwunsch ausgelassen (nur 1.20.x). Arena auf den freigegebenen Quellstand arena-v0.3.1 aktualisiert; Windows nimmt jetzt die SHA-256-gepruefte eigene JAR aus pack/local-mods auf. Linux/GitHub baut sauber und fuehrt die 21 Arena-Tests aus. Alle Pakete werden auf genau eine passende Arena und die acht neuen Mods geprueft. Modliste zaehlt die Arena jetzt mit: 333 Mods insgesamt. Die Strukturdichte aus 0.9.27 bleibt erhalten.

- 0.9.29: Buddy Drones 1.1.0, ByteBuddies 0.9.2 (offizielle CurseForge-NeoForge-JAR, Datei 7174647), Laser Bridges & Doors 6.1 und Superb Warfare 0.8.9.2. Deimos 2.7 kommt als benoetigte Bibliothek dazu: 338 Mods insgesamt. Architectury und Kotlin for Forge bereits vorhanden; SimpleBedrockModel wird von Superb Warfare eingebettet mitgeliefert. ByteBuddies fuer NeoForge braucht keine Turtleboi Core Library (diese ist nur fuer Forge 1.20.1). Vista, Drifter und Cyborg Robot Chicken nicht enthalten. Superb-Warfare-Blockzerstoerung bleibt beim Mod-Standard (aktiviert). Paketcheck prueft auch die fuenf neuen JARs in allen Paketen; Client/Ingame noch ungeprueft.

- 0.9.30 (Branch `claude/project-thread-ekqpz0`, Release auf Ansage): **Strukturdichte deutlich staerker reduziert**: WDA/Seven Seas/Alex's Caves x3, Boss- und Zusatzbauten x2,5, Doerfer x1,5 (170 Sets, 31 Namensraeume, Profil `tools/strukturdichte.py`, Doku `pack/strukturdichte.md`). Neu angepasst u. a. Alex's Caves, YUNG's Dungeons/Tempel/Hexenhuetten/Monumente, Kobolde, Repurposed Ancient Cities/Bastions/Doerfer. **Dungeons and Taverns entfernt**, die 4 `dnt_`-Quests sind raus. Neu: **Oracle Index** 1.4.0 (Modrinth, nur Client) **FTB Chunks** 2101.1.22 und **FTB Ultimine** 2101.1.15 (CF-Jars in `curseforge-mods`, Muster in `$cfPatterns`), **Arena 0.4.1** (Quellen, JAR und SHA aus Branch `codex/arena-route-choice` uebernommen; Wege-Abstimmung, Korrekturen, Details `mod/chronicle-arena/PRUEFBERICHT-0.4.1.md`, Laufzeit-/Client-Test offen). 340 Mods. Ob KubeJS die Worldgen-Overrides im Spiel wirklich laedt, ist weiter ungetestet (kein Servertest gemacht). Ein "Spawn frei halten" ist per Datapack nicht sauber moeglich (Exclusion-Zonen sind nur max. 16 Chunks gross und an andere Sets gebunden), das grosse Raster ist der Ersatz.
