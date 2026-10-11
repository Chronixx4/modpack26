# Chronicle Arena 0.3.2 - Schutzmissionen fuer Solo und Koop

Beacon-, Villager-Eskort- und Kontrollpunktmissionen erhalten eine gemeinsame Grenze fuer Anfangswelle und laufende Gegner:

| Aktive Spieler | Maximal gleichzeitig | Nachschub je 6 Sekunden |
|---|---|---|
| 1 | 4 | 1 |
| 2 | 6 | 2 |
| 3 | 8 | 3 |
| 4 | 10 | 3 |
| 5 oder mehr | 12 | 3 |

- Die Anfangswelle wird auf diese Grenze verkleinert. Besondere Gegner bleiben bevorzugt erhalten.
- Nachschub reserviert Platz fuer bereits wartende Gegner, sodass die Warteschlange die Grenze nicht umgeht.
- Alle tatsaechlichen Spawns verwenden dieselbe Grenze, auch nach dem Abschluss des Missionsziels. Dann endet der Nachschub und die verbleibenden Gegner muessen besiegt werden.
- Die Grenze richtet sich nach den noch aktiven Spielern. Nach Ausscheiden/Verlassen werden weniger Gegner nachgespawnt; vorhandene Gegner werden nicht automatisch entfernt.
- Niedrigere Admin-Gegnergrenzen werden weiterhin eingehalten. Normale Wellen und Bosskaempfe behalten ihre bisherige Skalierung. Gegnerstaerke bleibt abhaengig von Stufe und Welle.

Java 21, Minecraft 1.21.1, NeoForge 21.1.x. Alte Arena-JAR auf Server und Clients durch `chronicle-arena-0.3.2.jar` ersetzen und neu starten.

Pruefung: Java-21-Build und 24 automatisierte Tests. Kein Minecraft-Server oder Client gestartet. Im Spiel besonders Solo mit frueher Ausruestung, Duo, Ausscheiden eines Spielers und den Uebergang nach Missionsabschluss ausprobieren.
