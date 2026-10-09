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
- Jede Begegnung hat ein `theme` (default, nether, end, ice, desert, forest, sea, deepdark, sky); die Arena baut sich beim Start im passenden Stil um.
- Eigene Arena: Bau im Spiel mit einem Struktur-Block (Ecke zu Ecke, max. 52 x 52, unterste Ebene = Boden, 15 Hoehe) speichern als `chronicle_arena:arena/<thema>` und die Datei als `data/chronicle_arena/structure/arena/<thema>.nbt` ins Datapack (z. B. `pack/kubejs/data/...`) legen. Sie ersetzt dann das generierte Thema.
- Selbsttest: `gradle runServer -Dchronicle_arena.selftest=true`.
