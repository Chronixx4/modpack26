# Strukturdichte ab Chronicle 0.9.27

Mehr freie Landschaft in der Oberwelt, ohne Mods oder Questziele zu entfernen.
Die Overrides liegen in `kubejs/data/<mod>/worldgen/structure_set/` und werden
von beiden Buildern in die Modrinth-Datei, Client-ZIP und Server-ZIP kopiert.

| Gruppe | Faktor auf Abstand und Mindestabstand | Dichte gegenueber dem Original |
|---|---|---|
| When Dungeons Arise, Seven Seas, Alex's Caves | x3 | etwa -89 % |
| Cataclysm, Ice and Fire, Mowzie's, Bossbauten, IDAS, Repurposed (ohne Doerfer), Goety, Iron's, Born in Chaos, YUNG's Dungeons/Tempel/Hexenhuetten/Monumente, Kobolde, weitere Zusatzbauten | x2,5 | etwa -84 % |
| Doerfer (Repurposed, Towns and Towers, Terralith rare_village) | x1,5 | etwa -55 % |
| Aquamirae-Piratenschiffe, -verstecke und -aussenposten | Placement-Frequenz geviertelt | -75 % |

(Stand 0.9.30; in 0.9.27 waren es nur x1,35 bis x2.) Dungeons and Taverns
ist seit 0.9.30 nicht mehr im Pack. Es sind **170 Structure-Sets aus 31
Namensraeumen** angepasst. Das Profil steht in `tools/strukturdichte.py`
(`RULES`, `SET_FACTOR`).

Die Werte beschreiben Platzierungsversuche je Flaeche, keine garantierte
Anzahl fertiger Gebaeude. Biome, Hoehenbedingungen, Ausschlusszonen und die
unabhaengigen Raster der einzelnen Mods beeinflussen die tatsaechliche Welt.
Bei ganzzahligen Abstaenden wird aufgerundet. Die Dichte ist naeherungsweise
umgekehrt proportional zum Quadrat des Abstands.

## Bewusst erhalten

- Alle Mods, Struktur-IDs, Beute und Quests; keine Struktur wird abgeschaltet.
- Waystones und Doerfer inklusive deren zusaetzlicher Gebaeude.
- Vanilla-Strukturen, YUNG's Mineshafts, Strongholds und Nether-Festungen.
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

## Platzhalter `nova_structures` (ab 0.9.32)
Dungeons and Taverns ist seit 0.9.30 entfernt, aber Modrinth loescht bei Updates keine alten Override-Dateien. In bestehenden Profilen blieben die alten `nova_structures`-Structure-Sets liegen und verwiesen auf nicht mehr vorhandene Strukturen: Absturz bei "Neue Welt erstellen" (`Unbound values in registry worldgen/structure`). Die leeren Platzhalter unter `pack/kubejs/data/nova_structures/` ueberschreiben die alten Dateien und sind harmlos. Nicht loeschen, solange alte Profile existieren.
