# Chronicle Arena 0.4.2 (vorbereitet)

Server und alle Clients brauchen dieselbe `chronicle-arena-0.4.2.jar` (Netzwerkversion 10). Alte Arena-JAR ersetzen, nicht beide behalten. Welt und Fortschritt bleiben erhalten.

## Neu
- **Fluch-Karten** (Segen mit Preis, hoechstens eine pro Angebot): Glaskanone (+40 % Schaden, -4 Herzen), Leichtfuss (+20 % Tempo, -4 Ruestung), Blutpakt (+50 % Coins pro Welle, -2 Herzen).
- **Haendler-Weg** als vierte Option der Wege-Abstimmung: jeder lebende Spieler bekommt 2 Heiltraenke, 1 Staerketrank und 2 Goldaepfel, aber keine Heilung. Gleichstand bleibt Erholung.
- **Boss-Phasen:** 75 % Druckwelle (Rueckstoss, kurze Verlangsamung), 50 % Helfer (wie bisher), 25 % Raserei (Tempo und Staerke fuer den Boss, halbe Helfer-Gruppe).
- **Bestzeiten-Tafel** in der Lobby, pro Stufe und Solo/Koop (Abgrund: hoechste Welle). Chat: `/arena rekorde`.
- **Trainingsdummy:** `/arena training` in der Lobby, DPS-Anzeige in der Aktionsleiste.
- **Balance-Log:** pro Run eine JSON-Zeile in `<Welt>/chronicle_arena/runs.jsonl`.
- **14 neue Bosse:** Naga, Minoshroom, Geisterritter, Alpha-Yeti, Slider, Zyklop, Gorgone, Blitzdrache, Wesir, Schaedelfuerst, Hexe der Sumpfe, Kuerbiskoenig, Beschwoerer, Wildfeuer. **4 neue Wellen-Themen:** Meer, Frost, Ende, Hexen. Begegnungen mit Mobs, die im Pack fehlen, blendet die Mod selbst aus.

## Pruefung
Java-21-Build, 50 automatisierte Tests gruen. Im Spiel nicht getestet (Client, Koop, Phasen, Dummy, Haendler, Fluch-Karten, Tafel-Position, neue Bosse).
Offen: Boss ueben ohne Belohnung, Balance der neuen Bosse und Fluch-Karten.
