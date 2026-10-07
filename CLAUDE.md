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
- CurseForge-Jars holt das Skript automatisch aus `%USERPROFILE%\Downloads` (Dateinamen-Muster in `$cfPatterns`).
- Aktuelle Version: **PACKVER 0.9.11**. Bei jeder Änderung `$PACKVER` hochzählen.

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
- **Iris entfernt**: Iris 1.8.12 passt nicht zu Sodium 0.8.13 (Fehler `SodiumGameOptions$PerformanceSettings` ClassNotFound) und hat beim Betreten der Welt den Absturz ausgelöst.
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

## Offene Aufgaben
1. **0.9.10 im Client testen:** kurz in die Welt, auf Grafikfehler achten (More Culling, BadOptimizations seit 0.9.9).
2. **Questbuch im Spiel ansehen** (seit 0.9.8): Texte, Reihenfolge, Bilder. Aenderungen in `pack/quests/inhalt/*.py` machen (nicht im Editiermodus, sonst ueberschreibt der naechste Generatorlauf sie).
3. **Server auf dem Heimserver** einrichten: ZimaOS, Marvin hat dort schon ein eigenes Docker-Panel fuer mehrere Minecraft-Server (keine docker-compose noetig). Dafuer das `-Server.zip` aus dem Release nehmen (12 GB RAM fuer Minecraft, Container mind. 14 GB, Java 21, Ports 25565/TCP und 24454/UDP fuer Voice Chat), danach mit Chunky vorgenerieren.
4. Erst danach die **richtige Welt** starten, damit Strukturen und Biome ueberall generiert werden.
5. **Beim naechsten Update:** EvilCraft-Questtexte nachbessern (viele Quests verweisen nur aufs Handbuch). Dafuer das Handbuch "Origins of Darkness" aus der EvilCraft-Jar auslesen und die Mechaniken daraus uebernehmen.
6. Optional: Shader wieder einbauen, sobald es eine Iris-Version gibt, die zu Sodium 0.8 passt.

## Mod-Bauplan (Übersicht)
Artifact „Modpack-Bauplan 1.21.1“ auf claude.ai (Checkliste mit 143 Mods nach Kategorien). Die maßgebliche Liste ist aber `$ModList` in `build-und-test.ps1`.
