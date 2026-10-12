# Chronicle Arena 0.3.1 – Verteidigung, Recovery und Runen

## Korrekturen und Balance

- Siegertafel: Alte Textanzeigen werden direkt entfernt; genau eine Tafel bleibt erhalten. Echte Zeilenumbrueche und eine breitere, kompakte Darstellung verhindern Textueberlagerung. Bereits vorhandene doppelte Tafeln werden in der geladenen Arena bereinigt.
- Beacon-Mission: 30 Sekunden verteidigen, 100 Lebenspunkte. Wellengegner greifen jetzt gezielt ein unsichtbares Angriffsziel auf dem Beacon an. Ihre Treffer ziehen Beacon-Leben ab (maximal 8 je Treffer, je Gegner maximal einmal pro Sekunde). Ist der Beacon zerstoert, ist der Run verloren. Nach der Verteidigungszeit greifen die verbliebenen Gegner wieder Spieler an; sie muessen fuer das Wellenende besiegt werden.
- Verstaerkung alle vier Sekunden, solange Beacon-, Eskort- oder Kontrollpunktziele offen sind. Gegnerzahl und Warteschlange sind begrenzt, damit die Arena nicht ueberfuellt wird.
- Regeneration-Segen: kleine Heilimpulse erst nach vier Sekunden ohne Treffer. Ein Stapel heilt 1/4 Herz alle sechs Sekunden; weitere Stapel verkuerzen das Intervall bis auf vier Sekunden. Panzerung erhoeht den Impuls um 1/8 Herz. Keine automatische Regeneration-II-Wirkung und kein alter 1-Herz-Bonus alle zwei Sekunden.

## Recovery

Heilbrunnen geben weiterhin ihre Heilung, fuellen zusaetzlich zwei Hungerkeulen und etwas Saettigung auf und loeschen Feuer. Voll gesunde und satte Spieler loesen keinen unnoetigen Cooldown aus. Schwebender Text zeigt `Bereit` oder die verbleibenden Sekunden; die Anzeige wird nun verlaesslich erstellt und jede Sekunde aktualisiert. Cooldown weiterhin 45 Sekunden. Bei `Kein Heilen` gibt es Nahrung und Feuerloeschen, aber keine Lebenspunkte.

## Drei neue Runen

- Stasis-Rune: Gegner im Radius 8 werden fuer 5 Sekunden stark verlangsamt.
- Echo-Rune: Die naechsten drei Treffer koennen je einen Zusatztreffer mit 4 Schaden auf einen anderen Gegner innerhalb von 7 Bloecken ausloesen.
- Runenschild: Absorbiert bis zu 4 Schaden und verfaellt nach 8 Sekunden.

Leuchtende Runen erscheinen auf der Karte, maximal drei gleichzeitig. Beruehren aktiviert sie sofort, auch bei vollem Inventar. Sie verschwinden nach 12 Sekunden oder am Wellen-/Run-Ende. Die Wirkung bleibt auf den aktuellen Run beschraenkt.

Im Shop gibt es die Kategorie `Runen`: Stasis und Schild je 8 Coins, Echo 10 Coins. Gekaufte Runen sind stapelbare Verbrauchsitems und werden per Rechtsklick im laufenden Arena-Kampf eingesetzt. Gemeinsamer Einsatz-Cooldown vier Sekunden. Außerhalb eines laufenden Kampfes werden sie nicht verbraucht. Effekte stapeln sich nicht unbegrenzt; eine weitere Rune erneuert denselben Effekt.

## Installation und Pruefung

Java 21, Minecraft 1.21.1, NeoForge 21.1.x. Die alte Arena-JAR auf Server und **allen Clients** durch `chronicle-arena-0.3.1.jar` ersetzen und neu starten. Netzwerkversion geaendert. Welt, Fortschritt und Einstellungen bleiben erhalten.

Java-21-Build und 21 automatisierte Tests erfolgreich. Geprueft: Beacon-Schaden/Cooldown/Versagen, begrenzte Verstaerkung, abgeschwaechte Heilung, Recovery-Bedarf, Text-NBT und Shop-/Modellressourcen sowie die bisherigen Nachrichten- und Zieltests. Kein Minecraft-Server oder Client gestartet.

Im Spiel testen: Beacon-Zielverhalten auch mit Mod-Gegnern, Verstaerkung und Verlust bei Zerstoerung, doppelte Tafel nach Update, Recovery mit Hunger und Cooldown-Text, Karten-Pickups bei vollem Inventar, Kaufen/Verbrauchen aller Runen und das Regenerations-Balancing. Mod-Gegner mit eigener KI koennen sich trotz gesetztem Angriffsziel unterschiedlich verhalten.
