# Chronicle Arena 0.2.0 - Spieltest

Separate Arena-Mod fuer Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

- Gruppenbereitschaft: Vorbereiten, zustimmen oder ablehnen; Countdown erst nach Zustimmung aller verbleibenden Mitglieder. Neue Lobby-Spieler werden nicht mitgezogen.
- Begegnungsauswahl mit Suche, scrollbarer Liste, Icons und vollstaendigen Beschreibungen. Stufen mit Belohnungs-/Fortschrittsvorschau und letzter Auswahl.
- Zielwellen standardmaessig jede dritte Welle: drei Amethyst-Kristalle rechtsklicken, einen Obelisken 30 Sekunden halten oder bis zu drei markierte Gegner innerhalb von 90 Sekunden besiegen. Bei Kristallen/Obelisk 180 Sekunden Zeit. Anschliessend alle verbliebenen Gegner besiegen. Erfolgreiche Zielwellen geben +25 % Wellen-Coins.
- Segen: Glut entzuendet Gegner, Flammenjaeger erhoeht Schaden gegen brennende Gegner, Asche heilt bei deren Tod. Scharfschuetze verstaerkt Projektile und laesst sich mit Vampir kombinieren; Panzerung plus Regeneration gibt zusaetzliche Heilung. Heilsegen werden bei "Kein Heilen" nicht angeboten.
- OP-Admin-Panel im Terminal oder `/arena admin` (Berechtigungsstufe 2): Gegner-Leben/Schaden, Coins, Vorbereitung, Zielwellen ein/aus und Abstand. Einstellungen werden pro Welt gespeichert und gelten fuer neu gestartete Kaempfe. Kampf-Abbruch mit Bestaetigung, ohne Belohnung.

## Installation

Server stoppen. Alte `chronicle-arena-*.jar` aus dem Mods-Ordner entfernen und durch `chronicle-arena-0.2.0.jar` ersetzen. Dieselbe neue JAR auf **Server und allen Clients** installieren; das Netzwerkprotokoll wurde aktualisiert. Keine zweite Arena-JAR daneben liegen lassen. Weltbackup vor dem Spieltest erstellen.

Bestehende Arena-Gebaeude, Coins und Fortschritte werden weiterverwendet. Es wird kein neues Modpack-Release benoetigt.

## Pruefstatus

Java-21-Build und automatisierte Tests fuer persistente Einstellungen, Grenzwerte, Zielbedingungen und Netzwerk-Nachrichten. Noch kein Minecraft-Spieltest und kein kompletter Modpack-Serverlauf. Deshalb als Vorabversion fuer Marvins Spieltest veroeffentlicht.

Im Spiel pruefen: Solo/Koop-Bereitschaft und Ablehnen, Countdown zuruecknehmen, Suche und gesperrte Stufen, Zielwellen 3/6/9, Segen ab Welle 5, OP-Panel und Weltneustart. Nicht-OP-Spieler duerfen das Admin-Panel weder oeffnen noch Einstellungen aendern.
