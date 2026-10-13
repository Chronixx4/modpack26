# Chronicle Arena 0.4.0 – Wege-Auswahl (vorbereitet)

Nach jeder fuenften abgeschlossenen Welle waehlen alle lebenden Spieler erst ihren Segen und danach gemeinsam den Weg fuer den naechsten Block. Nach der letzten Welle und in Bosskaempfen gibt es keine Wege-Abstimmung. Im Abgrund wiederholt sie sich alle fuenf Wellen.

| Weg | Wirkung |
|---|---|
| Risiko | Fuer die naechsten bis zu fuenf Wellen: +25 % Gegnerleben, +15 % Angriffsschaden und +50 % Wellen-Coins. Mini-Boss-Lebenslimits bleiben erhalten. Der Bonus gilt auch fuer deren Wellen-Coins, nicht fuer die Abschlussbelohnung. |
| Erholung | Sofort volle Leben, Nahrung und Saettigung. Normale Gegner und Belohnungen. Mit **Kein Heilen** werden nur Nahrung und Saettigung aufgefuellt. |
| Spezialmission | In der naechsten Welle ein zufaelliges Ziel aus den bestehenden fuenf Zielarten. Wer nach erfolgreichem Ziel und abgeschlossener Welle lebt, erhaelt einmal eine zufaellige Stasis-, Echo- oder Schutzrune. Die weiteren Wellen des Blocks bleiben normal. Bei deaktivierten Zielwellen ist dieser Weg gesperrt. |

Die kleine Pausenheilung von 6 Lebenspunkten bleibt bestehen. **Volle Heilung nach jeder fuenften Welle gibt es jetzt nur durch Erholung.** Segen und die zusaetzliche Wiederbelebung bleiben erhalten.

## Abstimmung

- Eigenes Menue mit Beschreibungen, Stimmen und 30-Sekunden-Timer. `/arena weg` oeffnet eine laufende Abstimmung wieder; Schliessen behaelt die Stimme.
- Jeder lebende Teilnehmer hat eine Stimme und kann sie bis zum Ende aendern. Sobald alle abgestimmt haben oder 30 Sekunden vergangen sind, wird entschieden.
- Die meisten Stimmen gewinnen. Bei Gleichstand oder ohne Stimmen gewinnt Erholung; fehlende Stimmen werden nicht als Erholung gezaehlt.
- Stimmen ausgeschiedener Spieler werden entfernt. Zuschauer koennen nicht abstimmen.
- Anschliessend 3 Sekunden Countdown. Der Weg steht im Kampf-HUD. Risiko stapelt sich nicht zwischen Bloecken.
- Veraltete Pakete mit falscher Durchgangs-ID/Welle oder ungueltiger Auswahl werden serverseitig verworfen. Spezialmissionsbelohnungen werden in der Ergebnistafel erfasst.

## Installation und Pruefung

Server und alle Clients brauchen dieselbe neue `chronicle-arena-0.4.0.jar` (Netzwerkversion 9). Die alte Arena-JAR ersetzen, nicht beide behalten. Welt und gespeicherter Fortschritt bleiben erhalten.

Automatisierte Tests decken Abstimmung, Zeitlimit, Gleichstand, Ausscheiden, ungueltige Stimmen, Wirkungsdauer, einmalige Missionsbelohnung und Netzwerkpakete ab. Java-21-Build erfolgreich; alle 34 automatisierten Tests bestanden (13 neue Tests). Kein Client-/Servertest gestartet.

Vor einem Release im Spiel pruefen: solo und zu zweit bis Welle 6/11, alle drei Wege, Zielwellen deaktiviert, Kein Heilen, Abstimmung schliessen/oeffnen, Abbruch/Ausscheiden waehrend der Abstimmung, Rune bei vollem Inventar und UI bei grosser GUI-Skalierung.

Trainingsraum und eigener Boss sind nicht Bestandteil dieses Updates.
