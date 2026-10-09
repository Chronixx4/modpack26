# Chronicle Arena

Eigene NeoForge-Mod (MC 1.21.1): Arena-Dimension mit Portal, Bosskaempfen und Monsterwellen.

- **Portal** (`chronicle_arena:arena_portal`, Rezept: 4 Obsidian, 4 Diamant, Enderauge) per Rechtsklick in die Lobby, dort zurueck.
- **Terminal** in der Lobby: Modus (Wellen/Boss), Begegnung, Stufe, Starten oder Zuschauen.
- Es gibt genau eine Arena. Beim Start kaempfen alle mit, die in der Lobby stehen; wer spaeter kommt, schaut zu. Gefallene werden zu Zuschauern. Befehl `/arena leave`, Op: `/arena stop`.
- Skalierung mit Spielerzahl: Leben +50 %, Schaden +10 %, Mobanzahl +40 % pro zusaetzlichem Spieler, dazu die Faktoren der Stufe.
- Inhalte sind Datapack-JSON (auch per KubeJS-Datapack ergaenzbar): `data/<ns>/arena_tiers/*.json`, `data/<ns>/arena_encounters/*.json`, Loot unter `loot_table/rewards/`. Begegnungen mit Mobs, die im Pack fehlen, werden automatisch ausgeblendet.
- Bauen: `gradle build` im Ordner (Java 21). Texturen: `python3 tools/texturen.py`. Die Release-Action baut die Jar mit und packt sie in .mrpack und beide ZIPs.
