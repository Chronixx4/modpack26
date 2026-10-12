# Strukturdichte ab Chronicle 0.9.27

Mehr freie Landschaft in der Oberwelt, ohne Mods oder Questziele zu entfernen.
Die Overrides liegen in `kubejs/data/<mod>/worldgen/structure_set/` und werden
von beiden Buildern in die Modrinth-Datei, Client-ZIP und Server-ZIP kopiert.

| Gruppe | Anpassung | Ungefaehre Verringerung der Platzierungsdichte |
|---|---|---|
| When Dungeons Arise und Seven Seas | Abstand und Mindestabstand x2 | 75 % |
| Dungeons and Taverns, oberirdische/Meeres-Sets von IDAS und Repurposed Structures | Abstand und Mindestabstand x1,5 | 55-60 % |
| Zusaetzliche Magierbauten, Lager, Ruinen, Graeber, Galeonen und Aussenposten | Abstand und Mindestabstand x Wurzel(2) | etwa 50 % |
| Cataclysm, Ice and Fire, Mowzie's Mobs, Bosses'Rise und Wilden-Bauten | Abstand und Mindestabstand x1,35 | etwa 45 % |
| Aquamirae-Piratenschiffe, -verstecke und -aussenposten | Bestehende Placement-Frequenz halbiert | 50 % |

Die normalen Zusatzbauten umfassen Born in Chaos, Goety, Iron's Spellbooks,
Hexerei, Ars Additions/Elemental, Illager Invasion, Friends&Foes, Apotheosis,
EvilCraft, Alshanex's Familiars, Supplementaries, Terralith und Towns and Towers.
Es sind insgesamt **159 Structure-Sets aus 25 Namensraeumen** angepasst.

Die Werte beschreiben Platzierungsversuche je Flaeche, keine garantierte
Anzahl fertiger Gebaeude. Biome, Hoehenbedingungen, Ausschlusszonen und die
unabhaengigen Raster der einzelnen Mods beeinflussen die tatsaechliche Welt.
Bei ganzzahligen Abstaenden wird aufgerundet. Die Dichte ist naeherungsweise
umgekehrt proportional zum Quadrat des Abstands.

## Bewusst erhalten

- Alle Mods, Struktur-IDs, Beute und Quests; keine Struktur wird abgeschaltet.
- Waystones und Doerfer inklusive deren zusaetzlicher Gebaeude.
- Vanilla-Strukturen und die YUNG's-Varianten.
- Biome, Gelaende, Erze, Pflanzen, Arena und die anderen Dimensionen.
- Kleine unterirdische Camps, Artifacts-Lager und Koboldbaue.
- Terraliths unterirdische Sets, Supplementaries-Wegweiser und Feendoerfer.
- Ars-Additions-Nexus-Tuerme: dasselbe Set erzeugt sie auch im End; es bleibt
  erhalten, waehrend die Oberwelt-Bibliotheken und Portale ausgeduennt werden.
- Alshanex' seltene Origin Islands mit ihrer konzentrischen Platzierung.

## Sonderregeln

Die Originaldefinitionen kommen aus den im Modrinth-Index gepinnten JARs.
Integrated Cataclysm und Integrated Mowzie's Mobs ersetzen einige gleichnamige
Basisdefinitionen; fuer diese Sets werden die Addon-Definitionen verwendet.
Platzierungstyp, Salt, Strukturgewichte, Frequenzen und Ausschlussregeln bleiben
erhalten, ausser der ausdruecklichen Aquamirae-Frequenzanpassung.

Aquamirae verwendet Config-Referenzen fuer die Abstaende. Diese werden nicht
durch ungueltige Zahlen ersetzt; sein Codec unterstuetzt die gemeinsame
Placement-Frequenz. Natuerliche Eisformationen bleiben unveraendert.

Goetys Windschrein hat acht gekoppelte Erweiterungs-Sets. Alle neun erhalten
dasselbe vergroesserte Raster und behalten denselben Salt. Auch die gekoppelte
Badlands-Mine von Dungeons and Taverns folgt dem Raster ihres Aussenpostens.

## Pflegen und pruefen

Das Profil steht in `tools/strukturdichte.py`. Nach einem Mod-Update die
aktuellen Platzierungen und Addon-Overrides erneut beurteilen, dann mit einem
vollstaendigen Mod-Cache erzeugen beziehungsweise pruefen:

```sh
python3 tools/strukturdichte.py --mods dist/cache --write
python3 tools/strukturdichte.py --mods dist/cache --check
```

Das Werkzeug prueft die Modrinth-JARs per SHA-512, alle explizit ausgewaehlten
Sets, unveraenderte Strukturinhalte und Sonderregeln sowie Goetys gekoppeltes
Raster. Die Release-Action fuehrt die Pruefung nach dem Paket-Build aus.

**Bestehende Chunks behalten ihre Gebaeude.** Die Anpassung wirkt in neu
generierten Chunks. Fuer den Vergleich eignet sich derselbe Seed in einer
neuen Welt; die Platzierungen verschieben sich durch die geaenderten Raster.
Ein Ingame-Vergleich der tatsaechlichen Dichte ist noch erforderlich.
