# Chronicle Arena 0.3.0 – Ziele, Segen und Ergebnisse

## Neue Funktionen

- Zielwellen waehlen zufaellig aus fuenf Aufgaben, ohne unmittelbare Wiederholung: Kristalle rechtsklicken, Obelisk halten, markierte Gegner besiegen, einen NPC eskortieren und drei Kontrollpunkte nacheinander halten. Die Positionen wechseln zwischen vier Ausrichtungen.
- Eskort-NPC: In seiner Naehe bleiben (Radius 6) und Gegner fernhalten (Radius 4). Er folgt drei Wegpunkten; leuchtende Partikel zeigen den naechsten. Kontrollpunkte: Jeder Punkt braucht 10 Sekunden, waehrend ein lebender Spieler in der Naehe ist und keine Gegner den Punkt blockieren. Fortschritt bleibt bei Unterbrechungen erhalten.
- Kampf-HUD rechts ersetzt waehrend eines Runs die Rangliste: Phase, Welle, Gegnerzahl, Wiederbelebungen, Ziel, Fortschrittsbalken und verbleibende Zeit. Nach dem Run erscheint die Rangliste wieder.
- Segen-Auswahl als scrollbares Kartenmenue mit Symbolen, Beschreibungen, Stapeln und Synergiehinweisen. Es oeffnet sich in den bisherigen Segen-Pausen nach jeder fuenften Welle. `/arena segen` oeffnet die Auswahl oder die Uebersicht der aktiven Segen erneut; Chat-Auswahl bleibt moeglich. Nach zwei Minuten erfolgt weiterhin die automatische Zufallsauswahl.
- Ergebnisfenster nach Rueckkehr in die Lobby: Schaden, Kills, Wiederbelebungen, erreichte Welle, Dauer und persoenliche Belohnungen (Coins inklusive Wellen-/Rangboni, XP und erhaltene Gegenstaende). `/arena ergebnis` zeigt das Ergebnis zehn Minuten lang erneut an. `Erneut vorbereiten` uebernimmt Begegnung, Stufe, Arena und Modifikatoren und startet eine neue Gruppen-Bereitschaftsrunde.

## Installation

Minecraft 1.21.1, NeoForge 21.1.x und Java 21. Die alte Arena-JAR auf dem Server und bei **allen Clients** durch `chronicle-arena-0.3.0.jar` ersetzen, dann neu starten. Die Netzwerkversion wurde erhoeht; alle muessen aktualisieren. Bestehende Welt, Fortschritt und Admin-Einstellungen werden weiterverwendet.

## Verhalten und Absicherung

Der Server prueft jede Segen-Auswahl anhand von Run, Welle, Teilnahme und bereits erfolgter Auswahl. Ergebnisse sind reine Anzeigen und vergeben keine weiteren Belohnungen. Eine Wiederholung prueft die aktuellen Freischaltungen und verlangt erneut die Zustimmung der Lobby-Gruppe.

Zielmarker und Eskort-NPC werden nach Wellen und beim regulaeren Run-Ende, Admin-Abbruch und kontrollierten Server-Stopp entfernt. Marker ersetzen nur freie Luft ueber festem Boden. Ist eine vorgegebene Markerposition im eigenen Arena-Bau belegt, verwendet die Runde markierte Gegner als Ersatz.

## Validierung und Spieltest

Java-21-Build und 14 Unit-Tests erfolgreich. Getestet sind unter anderem Nachrichten-Rundreisen, Listengrenzen, Zielwechsel ohne Wiederholung, vollstaendige Kontrollpunkt-Einnahme, blockierter Fortschritt und noch nicht gespawnte Zielgegner. Keine Minecraft-Instanz gestartet.

Im Modpack pruefen: alle fuenf Zielarten, Anzeige bei unterschiedlichen GUI-Skalierungen, Segen-Auswahl allein und in einer Gruppe, Zeitablauf der Auswahl, Sieg/Niederlage samt Belohnungen, `Erneut vorbereiten` mit mehreren Spielern und Admin-Abbruch waehrend einer Eskort-Welle.
