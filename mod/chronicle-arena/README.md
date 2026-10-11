# Chronicle Arena

## Update 0.3.1

Siegertafel korrigiert, Beacon-Verteidigung mit Leben und Verstaerkung, Regeneration abgeschwaecht, Recovery mit Hunger/Timer und drei neue Runen auf der Karte und im Shop. Details: [RELEASE-0.3.1.md](RELEASE-0.3.1.md).

## Update 0.3.0

Fuenf wechselnde Zielarten, Kampf-HUD, Segen-Karten und Ergebnisfenster. Details und Installation: [RELEASE-0.3.0.md](RELEASE-0.3.0.md).
Segen-Auswahl/Uebersicht: `/arena segen`; letztes Ergebnis in der Lobby: `/arena ergebnis`.

## Update 0.2.0

Gruppenbereitschaft, durchsuchbare Begegnungen/Stufen, Zielwellen, Segen-Synergien und OP-Admin-Panel.
Installation, neue Bedienung und Teststatus: [RELEASE-0.2.0.md](RELEASE-0.2.0.md).
OP-Einstellungen: `/arena admin`; Gruppenmenue: `/arena gruppe`; Bereitschaft: `/arena bereit`.
Server und Clients muessen dieselbe Arena-Version verwenden.

Eigene NeoForge-Mod (MC 1.21.1): Arena-Dimension mit Portal, Bosskaempfen und Monsterwellen.

- **Portal** (`chronicle_arena:arena_portal`, Rezept: 4 Obsidian, 4 Diamant, Enderauge) per Rechtsklick in die Lobby, dort zurueck.
- **Terminal** in der Lobby: Modus (Wellen/Boss), Begegnung, Stufe, Starten oder Zuschauen.
- Es gibt genau eine Arena. Die Lobby-Gruppe bestaetigt ihre Teilnahme vor dem Start; wer spaeter kommt, schaut zu. Gefallene werden zu Zuschauern. Befehl `/arena leave`, Op: `/arena stop`.
- Skalierung mit Spielerzahl: Leben +50 %, Schaden +10 %, Mobanzahl +40 % pro zusaetzlichem Spieler, dazu die Faktoren der Stufe.
- Inhalte sind Datapack-JSON (auch per KubeJS-Datapack ergaenzbar): `data/<ns>/arena_tiers/*.json`, `data/<ns>/arena_encounters/*.json`, Loot unter `loot_table/rewards/`. Begegnungen mit Mobs, die im Pack fehlen, werden automatisch ausgeblendet.
- Bauen: `gradle build` im Ordner (Java 21). Texturen: `python3 tools/texturen.py`. Die Release-Action baut die Jar mit und packt sie in .mrpack und beide ZIPs.

## Marken, Shop, Fortschritt
- Jeder Sieg gibt Arena-Marken (`chronicle_arena:arena_token`, Stufe x1, Boss x2). Im Terminal gibt es den **Shop**; Angebote sind JSON unter `data/<ns>/arena_shop/*.json` (`item`, `count`, `price`, optional `components`, z. B. Verzauberungen).
- Stufen werden nacheinander freigeschaltet (`requires` in `arena_tiers`). Siege und Bestzeit pro Stufe stehen im Menue und mit `/arena stats`.
- Wellen sind ueber `wave_health`, `wave_damage`, `wave_count` der Stufe abgeschwaecht (Bosse nicht).

## Arena gestalten
- Es gibt genau eine Prunk-Arena (Tribuene, Tuerme, Fenster, Torbogen, schwebende Inseln, Monde), dazu Lobby, Bruecke und eine Shop-Plattform mit Haendler und sechs Staenden.
- Eigene Arena: Bau im Spiel mit einem Struktur-Block speichern als `chronicle_arena:arena/arena` (bzw. `shop`) und als `data/chronicle_arena/structure/arena/arena.nbt` ins Datapack legen. Sie ersetzt dann den generierten Teil.
- Selbsttest: `gradle runServer -Dchronicle_arena.selftest=true`.

## Coins, Anzeige und Extras
- Coins liegen auf dem Konto (nicht im Inventar). Links im Bild steht in der Arena das Konto (Coins, Kills, Bosse, Siege, Titel), rechts die Rangliste (Top 10, serverweit gespeichert).
- Modifikatoren (Elite-Horde, Kein Heilen, Glaskanone, Hetzjagd) geben Coins-Bonus; die Tages-Challenge (feste Begegnung + Stufe + Modifikatoren pro Tag) gibt einmal taeglich Bonus-Coins.
- Shop-Kategorie Kosmetik: Titel (in Anzeige und Rangliste) und Partikelspuren. Siege loesen Feuerwerk und Fanfare aus.

## Zweite Ebene und Wiederbeleben
- Die Arena hat Radius 40. Rundherum liegt ein Balkon (zweite Ebene, 8 Bloecke ueber dem Boden) mit flacher Innenkante: von dort kann man nach unten springen oder schiessen. Vier Treppen fuehren hinauf.
- Gefallene bleiben als Zuschauer am Fleck liegen (Seelenfeuer-Saeule). Wer lebt, schleicht ca. 4 Sekunden daneben (Umschalt-Taste) und holt sie mit halber Gesundheit zurueck.

## L2 Hostility
In der Arena-Dimension gibt es keine Hostility-Levels (Datapack `l2hostility_config/difficulty/chronicle_arena.json`, apply_chance 0). Die Schwierigkeit kommt nur aus den Arena-Stufen.

## Show-Elemente (Titel, Zaehler, Segen, Musik)
- Titel bei Wellenstart ("WELLE 3 / 12", Gegnerzahl), Countdown 3-2-1, "WELLE GESCHAFFT", Sieg (mit Zeit und Bestzeit) und Niederlage.
- Bossleiste zeigt die verbleibenden Gegner.
- Mini-Boss: bei Stufen ab 15 Wellen alle 10 Wellen, sonst in der Mitte (5-fache Leben, 3 Wellen-Coins Bonus).
- Segen: nach jeder 5. Welle 3 zur Auswahl (klickbar im Chat, `/arena segen 1-3`), gelten bis zum Run-Ende, stapelbar.
- Wiederbeleben: 2 pro Run, +1 nach jeder 5. Welle (max 5), dazu volle Heilung.
- Musik: Pigstep bei Wellen, Precipice beim Boss (Vanilla-Platten, Kategorie Musik, Schleife).

## Scaling und Abgrund (Endlos)
- Pro Welle: Anzahl +6 %, Leben +4 %, Schaden +2 %. Koop pro Spieler: +35 % Anzahl, +25 % Leben, +8 % Schaden; Mob-Limit 30 + 8 pro Spieler (max. 70).
- Mini-Boss mit Lebens-Cap pro Stufe (200 / 350 / 600 / 1000).
- Stufe "Abgrund" (`arena_tiers/abgrund.json`, `waves: 0`, braucht Gold): Endlos nur im Wellen-Modus, Wachstum +5 % Anzahl / +6 % Leben / +3 % Schaden pro Welle, ab Welle 20 zusaetzlich x1,02 pro Welle. Mini-Boss alle 10 Wellen, alle 25 zwei Finale-Mobs. Run endet bei Niederlage; Beute je 5 Wellen, persoenlicher Rekord (`/arena stats`).

## Anpassung an die Spielerstaerke
Zu Beginn jeder Welle (und beim Boss) wird die Staerke der lebenden Spieler gemessen (Ruestung + Haerte, Angriffswert inkl. Waffe, Skilltree und Segen, maximale Leben). Die Gegner-Leben werden mit `(Staerke / 0,91)^0,7` skaliert (0,8x bis 1,8x); der Wellen-Titel zeigt "Staerke xN". Mini-Boss solo: 1,75x statt 2,5x Leben.

## Ereignisse, Klassen, Karte, Rang, Siegertafel
- Ereignisse ab Welle 3 (30 %): Blutmond (Gegner schneller, +50 % Coins), Gewitter (Blitze treffen Gegner), Mondsprung (Spieler springen hoeher, schweben).
- Gegner-Klassen ab Welle 3: Sanitaeter (heilt Gegner), Schildtraeger (Ruestung, Rueckstoss-Resistenz), Bombenleger (explodiert beim Tod), Schuetze (Skelett auf dem Balkon, ab Welle 4).
- Karte: 4 Heilbrunnen (laden 45 s auf), 8 Flammenfallen (rote Druckplatten, treffen Gegner hart, Spieler leicht).
- Arena-Wechsel: Thema "nether" -> Glutarena (Z 500), "ice" -> Eisarena (Z 800), sonst Standardarena. Die Varianten werden beim ersten Bedarf gebaut und per `ArenaPalette` umgefaerbt.
- Arena-Rang (Rekrut bis Legende) nach insgesamt verdienten Coins, mit Bonus-Coins beim Aufstieg; im HUD mit Fortschrittsbalken. Rangliste um die Abgrund-Spalte (A) ergaenzt.
- Siegertafel: Nach jedem Run zeigt ein Textfeld in der Lobby Schaden, Kills und Wiederbelebungen pro Spieler.

## Kill-Cam und Perk-Pause
- Gefallene sehen 5 Sekunden durch die Augen ihres Bezwingers (Spectator-Kamera) und bekommen den Titel "BESIEGT von ...".
- Segen nach jeder 5. Welle: die Runde pausiert, bis alle Lebenden gewaehlt haben (Anzeige "Warte auf: ..."; nach 2 Minuten waehlt der Zufall). Neu: Vampir (6 % Schaden als Leben) und Zweite Chance (+1 Wiederbelebung).

## Schmied, Arena-Wahl, Killcam (neu)
- **Schmied:** Amboss an der Ostseite des Shops (Rechtsklick). 9 Verbesserungen gegen Coins (Schaerfe, Haertung, Lebenskraft, Windlaeufer, Schlagtempo, Standfest, Reichweite, Lebensfunke, Schmiedeglut). Wirken nur in der Arena-Dimension (Attribute werden beim Verlassen entfernt). Wird auch in bestehenden Welten nachgebaut.
- **Arena-Wahl:** Im Terminal gibt es einen Knopf "Arena: Auto / Klassisch / Glut / Eis". Auto = Glut bei Nether-, Eis bei Eis-Gegnern, sonst klassisch.
- **Killcam:** Solo wird die Niederlage um 5 Sekunden verzoegert, damit die Killcam laeuft.
- Wuermer/Silberfischchen werden in der Dimension nicht gespawnt. Heilbrunnen haben Lichtsaeule, Ring und Timer-Text. Neue 3D-Modelle fuer Terminal, Shop und Portal.
