# Unser Modpack – Projektstand (Übergabe an Claude Code)

Eigenes Minecraft-Modpack für Chronixx und Freunde, als Nachfolger für ATM10.
Schwerpunkte: Ars Nouveau (Magie), Create mit vielen Addons, AE2 mit Addons, neue Dimensionen, Bosse, Waffen.
Kommunikation mit dem Nutzer: Deutsch, locker, kurz.

## Eckdaten
- Minecraft **1.21.1**, Loader **NeoForge 21.1.x** (zuletzt 21.1.255), Java 21
- Launcher: **Modrinth App**; Profile liegen unter `C:\Users\Marvin\AppData\Roaming\ModrinthApp\profiles\Unser-Modpack-<version>\`
- Logs: `...\profiles\Unser-Modpack-<version>\logs\latest.log`, Crashs unter `crash-reports\`
- Kampfsystem: **Vanilla**, also bewusst ohne Better Combat und ohne Epic Fight
- Ziel: **eine einzige .mrpack-Datei**, die die Freunde in die Modrinth App importieren
- PC: Windows 11, RTX 5070, Azul Java 21

## Dieser Ordner (Modpack-Builder)
- `START.bat` startet `build-und-test.ps1` (PowerShell 5.1, Datei ist UTF-8 mit BOM und nur ASCII-Text)
- Ablauf des Skripts:
  1. Jede Mod aus `$ModList` (Format `slug|Name|optional-Versionsregex`) über die Modrinth-API v2 auflösen. Zuerst wird der Slug versucht, danach eine Namenssuche mit **nur exakten** Treffern.
  2. Required-Abhängigkeiten rekursiv auflösen.
  3. `modrinth.index.json` schreiben. CurseForge-Jars aus `curseforge-mods\` kommen nach `overrides/mods`. Daraus entsteht `Unser-Modpack-<PACKVER>.mrpack`. Danach wird sofort `ERGEBNIS.txt` geschrieben.
  4. Einen NeoForge-Testserver aufsetzen und starten, bis `Done (` im Log steht. Danach wird `ERGEBNIS.txt` aktualisiert.
- Das Skript schreibt ausserdem `MODLISTE.txt` (alle Mods nach Kategorie; Kategorien = `# `-Zeilen in `$ModList`, CF-Jars ueber `$cfCat`) und `pack/modrinth.index.json`. Beide nach jedem Build mit committen.
- **Release:** `.github/workflows/release.yml` baut aus `pack/modrinth.index.json` + `curseforge-mods/*.jar` das .mrpack und legt ein GitHub-Release an automatisch, sobald `pack/modrinth.index.json` mit neuer `versionId` auf `main` landet (alternativ Actions -> Release -> Run workflow). Gibt es das Release schon, passiert nichts. Tags kann Claude aus der Cloud-Umgebung nicht pushen.
- CurseForge-Jars holt das Skript automatisch aus `%USERPROFILE%\Downloads` (Dateinamen-Muster in `$cfPatterns`).
- Aktuelle Version: **PACKVER 0.9.4**. Bei jeder Änderung `$PACKVER` hochzählen.

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

CurseForge blockt Downloads per Skript, auch über die interne `/api/v1`. Diese Dateien gehen nur über den Browser oder per Hand. Das Pack darf deshalb nur privat geteilt werden und nicht öffentlich hochgeladen werden, wegen der Lizenzen der Mods.

## Was schon schiefging (nicht wiederholen)
- **Unscharfe Suche** hat falsche Mods eingebaut (Tensura, ExtendedAE Plus, Ars Elemancy, Antimagick, FTB Quests Optimizer). Deshalb gilt jetzt: nur exakte Treffer.
- **Rate-Limit**: Modrinth erlaubt etwa 300 Anfragen pro Minute. Das Skript wartet 350 ms zwischen Anfragen und bei 429 jeweils 20 s, mit bis zu 10 Versuchen.
- **Bibliotheken**, die Modrinth nicht als Abhängigkeit einträgt, stehen explizit in der Liste: Iron's Lib (`^1\.21\.1-2`), Bundle API, SmartBrainLib, Lithostitched, Structure Pool API und PolyLib.
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

## Offene Aufgaben
1. v9 bauen, `Unser-Modpack-0.9.0.mrpack` importieren, eine Testwelt anlegen, ins Nether und End gehen, `latest.log` prüfen.
2. ~~Integrated Mowzie's Mobs~~: wird auf Modrinth gefunden, erledigt.
3. **Server auf dem Heimserver** einrichten: selbstgebaut, ZimaOS, dort läuft schon ein Satisfactory-Server. Am besten per Docker, z. B. `itzg/minecraft-server` mit `TYPE=NEOFORGE` und dem .mrpack bzw. dem Mod-Ordner. Der Server bekommt keine Client-Mods (Sodium, Xaero, EMI-Client und Ähnliches über env `server: unsupported`).
4. Optional: FTB-Quests-Kapitel, die Magie, Create und AE2 verbinden.
5. Optional: Shader wieder einbauen, sobald es eine Iris-Version gibt, die zu Sodium 0.8 passt.
6. Erst danach die **richtige Welt** starten, damit Strukturen und Biome überall generiert werden.

## Mod-Bauplan (Übersicht)
Artifact „Modpack-Bauplan 1.21.1“ auf claude.ai (Checkliste mit 143 Mods nach Kategorien). Die maßgebliche Liste ist aber `$ModList` in `build-und-test.ps1`.
