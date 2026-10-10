# Chronicle Arena

Eigene NeoForge-Mod (MC 1.21.1): Arena-Dimension mit Portal, Bosskaempfen und Monsterwellen.

- **Portal** (`chronicle_arena:arena_portal`, Rezept: 4 Obsidian, 4 Diamant, Enderauge) per Rechtsklick in die Lobby, dort zurueck.
- **Terminal** in der Lobby: Modus (Wellen/Boss), Begegnung, Stufe, Starten oder Zuschauen.
- Es gibt genau eine Arena. Beim Start kaempfen alle mit, die in der Lobby stehen; wer spaeter kommt, schaut zu. Gefallene werden zu Zuschauern. Befehl `/arena leave`, Op: `/arena stop`.
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
- Musik: Pigstep bei Wellen, Precipice beim Boss (Vanilla-Platten, Kategorie Musik, Schleife).
