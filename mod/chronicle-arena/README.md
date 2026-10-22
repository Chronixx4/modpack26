# Chronicle Arena

## Update 0.5.1 (in Arbeit): Burg halten ueberarbeitet

- **Echte Tuerme:** Jeder Turm ist ein 3x3-Bauwerk, das zeigt, was er ist (Holzturm mit Zielscheibe, Eisspitze, Feuerbecken, Moerser, Golem-Kaserne mit Gitter, Goldmine). Jede Stufe macht ihn hoeher und prachtvoller. Schuesse sind als Pfeil-, Flammen- oder Rauchspur zu sehen.
- **Zwei neue Tuerme:** Moerser (175/225/325, Flaechenschaden, stark gegen Gepanzerte) und Goldmine (150/200/300, kaempft nicht, +30/50/80 Punkte nach jeder Welle).
- **Gegner bleiben auf dem Weg:** Kein Rueckstoss mehr, wer trotzdem vom Weg kommt (Schubsen, Fliegen), wird sofort zurueckgesetzt. Festhaengen nach 5 s statt 8 s.
- **Selbst kaempfen nur auf dem Weg:** Spieler treffen Gegner nur, wenn sie selbst auf dem Pfad stehen (auch mit Pfeilen). Dort greifen die Gegner sie dann auch an. Neben dem Weg kommt der Hinweis "Nur vom Weg aus!".
- **Mehr Gegner, gemischt:** 60 % mehr Gegner pro Welle, dafuer 25 % weniger Leben und 30 % schneller. Ab Welle 3 Laeufer (schnell, wenig Leben, Feuer +50 %), ab Welle 5 Gepanzerte (langsam, mehr Leben, Pfeile nur 40 %, Moerser +60 %).
- **Mehr Tempo und Geld:** Erste Bauphase 15 s, danach 8 s Pause (20 s bei Segen). "Welle jetzt starten" in jedem Bauplatz-Menue. Start 500 Punkte, Pfeilturm 75, Feuerturm 100.
- **Kristall-Schockwelle:** Am Kristall, kostenlos alle 60 s, trifft alle Gegner bis 12 Bloecke um den Kristall und friert sie kurz ein. Abklingzeit im HUD.
- **Neue Karte:** 23 Bauplaetze, alle 5 Bloecke neben der Pfadmitte (auch der Feuerturm trifft jetzt). Mauern mit Zinnen und Ecktuermen, Torhaus mit Fallgitter und Seelenfeuer, Wiese mit Blumen und Buesche, Laternenpfaehle am Weg, Banner, Kristall als Leuchtfeuer mit violettem Strahl. Wird beim naechsten Kampf neu gebaut (neuer Markerblock).
- Ein gesicherter Stand aus 0.5.0 (alte Bauplaetze) wird beim Laden gutgeschrieben statt aufgestellt.
- Im Spiel ungetestet, nur gebaut und Unit-Tests (72 gruen).

## Update 0.5.0: Burg halten

- **Neuer Modus "Burg halten" (Tower Defense):** Im Terminal den Modus-Knopf auf "Burg halten" stellen (Wellen -> Boss -> Burg halten), Begegnung und Stufe wie bei Wellen waehlen. Gekaempft wird im neuen Burghof (bei z 1100, wird beim ersten Kampf gebaut).
- Gegner kommen am Tor und laufen einen festen, breiten Schlangenpfad zum Kristall. Sie greifen Spieler nur an, wenn man ihnen sehr nahe kommt (4,5 Bloecke); Golems greifen sie an. Wer haengen bleibt, wird nach 8 s zum naechsten Wegpunkt gesetzt.
- **Kristall:** 100 Leben plus 25 je weiterem Spieler. Kommt ein Gegner durch, verliert er 5 (normal), 15 (Elite) oder 30 (Mini-Boss/Finale) Leben. Bei 0 ist der Kampf verloren. Rechtsklick auf den Kristall: reparieren (+25 fuer 150 Punkte) oder die naechste Welle sofort starten.
- **Gemeinsame Kasse:** Start 300 Punkte, 10 je Kill (Elite 30, Mini-Boss 100, auch Turm-Kills), Bonus 50 + 10 je Welle fuer jede gehaltene Welle. Durchgekommene Gegner bringen nichts.
- **12 Bauplaetze** (Rechtsklick): Pfeilturm (100/150/250, trifft den Gegner, der am weitesten ist), Frostturm (150/200/300, verlangsamt), Flammenwerfer (150/200/300, Feuer im Umkreis), Golem-Wache (250/300/400, Stufe 3 = zwei Golems). Drei Stufen, Abreissen gibt die Haelfte zurueck. Bauen geht auch waehrend der Welle. Turmschaden waechst mit Stufe und Welle wie die Gegner.
- Bauphase 25 s vor jeder Welle. Segen alle 5 Wellen wie gewohnt, kein Gruppenweg, keine Zufallsmissionen, keine Balkon-Schuetzen. Abgrund-Stufe = endlose Burg mit eigenem Rekord (`siege_best`).
- Eigene Bestzeiten auf der Tafel ("Burg ..."), Crash-/Neustart-Schutz wie bei Wellen (Tuerme, Kasse und Kristall werden gespeichert).
- HUD zeigt Kristall-Leben und Kasse. Netzwerkversion 12 (Kampf-HUD und neues Modus-Menue), Server und Clients brauchen dieselbe Version.
- Werte sind vorerst fest im Code; ins Admin-Panel kommen sie mit dem Admin-Umbau.
- Im Spiel ungetestet, nur gebaut und Unit-Tests (71 gruen).

## Update 0.4.5

- **Crash- und Disconnect-Schutz:** Wer im Kampf rausfliegt, behaelt seinen Platz 3 Minuten. Die anderen kaempfen weiter; war er der letzte Lebende, pausiert der Kampf (Gegner eingefroren, Kampfzeit steht). Beim Wiederkommen geht es lebend mit allen Segen weiter (3 s Schutz), Gefallene kommen als wiederbelebbare Zuschauer zurueck. Laeuft die Frist ab, ist der Platz weg; das Ergebnis (Sieg-Belohnung oder Abgrund-Beute) kommt trotzdem beim naechsten Login.
- **Kampf uebersteht einen Neustart:** Der Stand wird alle 5 s und beim Stoppen in der Welt gesichert (`chronicle_arena_resume`). Nach dem Start geht es an der letzten fertigen Welle weiter (Segen, Wiederbelebungen, Gruppenweg, Statistik bleiben), alle haben 5 Minuten zum Zurueckkommen. Gegner, Runen und Beacon aus einem abgebrochenen Kampf werden beim Laden entfernt, Bloecke eines Wellenziels zurueckgesetzt. Nach einem echten Absturz gilt der Stand des letzten Autosaves.
- **Tages- und Wochen-Challenge:** Feste Begegnung, Stufe, Modifikatoren und ein fester Seed, damit alle dieselben Wellen bekommen (Zusammensetzung, Elite, Klassen, Ereignisse, Zielwellen, Segen-Angebote). Woche: Silber bis Chronicle, mindestens zwei Modifikatoren, 5-facher Bonus statt 2-fach, Wechsel montags (UTC). Bonus einmal pro Zeitraum. Im Terminal Knoepfe "Tag" und "Woche"; bei einer Challenge sind Arena (nach Gegner) und Zielwellen fest. Eigene Tafel in der Lobby (Westseite, beste 5 je Zeitraum, Sieg nach Zeit, sonst Wellen), auch per `/arena challenge`.
- **Zwei neue Bosse:** Captain Cornelia (Aquamirae, Eisarena, mit Ertrunkenen) und Uraltes Relikt (Cataclysm, mit Wuestenkriegern).
- Netzwerkversion 11 (Terminal-Paket hat jetzt beide Challenges), Server und Clients brauchen dieselbe Version.
- Im Spiel ungetestet, nur gebaut und Unit-Tests (65 gruen).

## Update 0.4.4 (vorbereitet)

- Gegner in der Arena lassen wieder XP fallen (Item-Loot bleibt aus).
- Musik-Discs droppen in der Arena nicht mehr (z. B. Creeper durch Skelett-Pfeil).

## Update 0.4.3 (vorbereitet)

- Zufallsmission "NPC begleiten" entfernt (Fuehrer blieb haengen).
- Neue Mission "Wandernder Lichtkreis": Ein Partikelkreis (Radius 4,5) wandert mit 1 Block/s zwischen drei Punkten durch die Arena. Mindestens die Haelfte der lebenden Gruppe (aufgerundet) muss darin stehen, insgesamt 30 s, Zeitlimit 120 s. Ausserhalb laeuft der Fortschritt nicht weiter. Normaler Nachschub wie bei den Kontrollpunkten.
- Missionen jetzt: Kristalle, Beacon, Zielgegner, Kontrollpunkte, Lichtkreis.
- Beacon verteidigen: 200 LP plus 75 je weiterem Spieler (vorher 100), hoechstens 5 Schaden pro Treffer (vorher 8). Waehrend der Mission nur 60 % der normalen Gegner, gleichzeitig hoechstens 6 plus 4 je weiterem Spieler, Nachschub nur alle 8 s und 1 bis 3 Gegner.
- Terminal: Schalter "Zufallsmissionen" (AN/aus) pro Kampf. Aus heisst keine Zielwellen und kein Missions-Weg. Der Admin-Schalter "Zielwellen" gilt weiter fuer die ganze Welt.
- Iron Jetpacks gehen in der Arena-Dimension nicht: Ein angezogenes Jetpack (Brustplatz oder Curios-Slot) kommt alle 0,5 s zurueck ins Inventar, bei vollem Inventar faellt es auf den Boden. Kreativ und Zuschauer sind ausgenommen. Create Jetpack bleibt erlaubt.
- Paketformat unveraendert (Schalter steckt im Aktions-Bitfeld), Netzwerkversion bleibt 10.

## Update 0.4.2 (vorbereitet)

Fluch-Karten, Haendler-Weg, Boss-Phasen, Bestzeiten-Tafel, Trainingsdummy, Balance-Log, 14 neue Bosse und 4 Wellen-Themen. Details: [RELEASE-0.4.2.md](RELEASE-0.4.2.md).

## Update 0.4.1

Fehlerkorrekturen nach Gesamtpruefung: Tod erst nach Ruestung/Totem, Fortschritt nach Respawn erhalten, Abgrund-Zaehler, Killcam/Wiederbeleben, Zielpunkte/NPC-Wege, Belohnungen und Menuegroessen. Details und Teststand: [PRUEFBERICHT-0.4.1.md](PRUEFBERICHT-0.4.1.md).

## Update 0.4.0 (vorbereitet)

Gruppenwege nach jeder fuenften Welle: Risiko, Erholung oder Spezialmission. Menue: `/arena weg`. Volle Pausenheilung gehoert jetzt zum Erholungsweg. Regeln und Teststatus: [RELEASE-0.4.0.md](RELEASE-0.4.0.md).

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
- Wiederbeleben: 2 pro Run, +1 nach jeder 5. Welle (max 5), volle Heilung durch den Erholungsweg.
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
