# Arena-Gesamtpruefung – 13.10.2026

Gepruefter Ausgangsstand: Arena 0.4.0, Commit `830990a730dc288f43996ab482b271745e019231`.
Korrekturen: Arena 0.4.1 auf `codex/arena-route-choice`; Pack 0.9.31 vorbereitet.

## Umfang

Rundensteuerung, Gruppenbereitschaft, Boss-/Wellen-/Abgrund-Modus, alle fuenf Zielarten, Wege-Abstimmung, Segen, Runen, Brunnen/Fallen, Wiederbeleben/Killcam, Fortschritt/Konto/Kosmetik/Schmied, Loot, Admin-Abbruch, Verlassen/Logout/Respawn/Serverende, Netzwerk und Client-Menues. Dazu Dimension, Strukturbau/Varianten und Ressourcenverweise.

## Bestaetigte Fehler und Korrekturen

| Fehler | Korrektur |
|---|---|
| Ausschalten anhand von Rohschaden vor Ruestung, Absorption und Totem | Erst das tatsaechliche Todesereignis macht den Spieler zum Zuschauer. Vanilla-Schutzmechanismen werden vorher angewendet; Inventar bleibt bei normalen Arena-Niederlagen erhalten. `/kill` entfernt den Spieler sauber aus der Runde. |
| Coins, Schmied/Kosmetik und Stufenfortschritt gingen bei normalem Spieler-Respawn verloren | Arena-NBT wird beim Klonen des Spielers als unabhaengige Kopie uebernommen. Gespeicherter vorheriger Spielmodus wird nach Respawn wiederhergestellt. |
| Letzter Spieler ausgeschaltet, letzte Gegner sterben waehrend der Killcam: Runde konnte trotzdem siegen | Ohne lebende Spieler wird waehrend der Killcam keine Welle weitergefuehrt oder abgeschlossen. |
| Wiederbelebter Spieler blieb mit der Kamera am Gegner | Eigene Kamera und Killcam-Maps werden beim Wiederbeleben und Verlassen sofort zurueckgesetzt. Killcam tickt auch in Pausen. |
| Abgrund-Abbruch in einer Pause unterschlug die gerade abgeschlossene Welle | Abgeschlossene Wellen werden explizit gespeichert; Beute, Rekord, Siegertafel und Ergebnis verwenden diesen Wert. |
| Spieler verliess den Run, erhielt beim spaeteren Teamsieg dennoch Coins/XP/Freischaltungen | Abschlussbelohnungen nur fuer Teilnehmer, die noch zur Runde gehoeren. Gefallene Teilnehmer bleiben berechtigt; spaeter hinzugekommene Zuschauer nicht. |
| Fehlgeschlagener erforderlicher Spawn konnte einen leeren Boss-/Wellenkampf automatisch gewinnen | Spawnfehler bricht den Kampf als Niederlage mit Meldung ab. |
| Hetzjagd, Bombenleger und Blutmond ersetzten gegenseitig den Tempo-Modifikator | Alle drei Faktoren werden gemeinsam berechnet und einmal angewendet. |
| Ein Zielpunkt lag in jeder Orientierung auf einem roten Teppich | Alle Zielpunkte liegen jetzt abseits der Teppichachsen; Kristall-/Kontrollpunkt-Ziele fallen deshalb nicht mehr stets auf markierte Gegner zurueck. |
| Begleit-NPC blieb an Teppichen/Druckplatten haengen | Niedrige Bodenformen werden beruecksichtigt; Schritte pruefen Kopffreiheit, tragenden Boden und den gesamten Kollisionskasten. Kleine Hindernisse werden umgangen. |
| Eigene Lobby-Struktur ignoriert; Dekoration/Schmied/Gadgets ueberschrieben eigene Strukturen | Lobby-Vorlage wird eingesetzt; automatische Dekoration und Nachruestung respektieren eigene Lobby-, Shop- und Arena-Strukturen. |
| Menueelemente bei hoher GUI-Skalierung abgeschnitten | Schmiedliste scrollbar, Shop-Raster passt sich der Breite an; Positionen im Terminal-, Gruppen- und Admin-Menue korrigiert. |
| Alte Konto-/Kampf-/Auswahldaten nach Serverwechsel auf dem Client | Client-Zustand beim Trennen zurueckgesetzt. |
| Alte Netzwerklisten akzeptierten negative oder unbegrenzte Laengen | Einheitliche obere Grenzen beim Lesen und Schreiben, mit Regressionstests fuer ungueltige Pakete. |
| Anzeige des Tagesbonus ignorierte Admin-Coins-Faktor; Segen-ID hing von Systemsprache ab | Angezeigter Bonus entspricht dem vergebenen Wert; IDs verwenden Locale.ROOT. |

## Nachweise

- Alle 370 Ressourcen-JSONs syntaktisch gueltig; eigene Modell-/Textur-Verweise vorhanden, feste Sprachschluessel und alle Shopkategorien vorhanden.
- Bestehende Tests plus neue Tests fuer reale Ereignishandler mit simulierten Spielern: Rohschaden/Tod, Wiederbeleben/Kamera, Killcam-Niederlage, Deserteure, Abgrund-Ergebnis, Spawnfehler, Respawn-Datenkopie und eigene Strukturen.
- Weitere Regressionstests: kombinierte Gegnergeschwindigkeit, Zielpunkte in allen vier Orientierungen, begehbare Bodenformen und Paketgrenzen.
- Java-21-Clean-Build erfolgreich; alle 48 Tests bestanden (0 Fehler, 0 uebersprungen). Lokale Arena-JAR auf Version 0.4.1 und SHA-256 geprueft.

## Noch erforderliche Laufzeitpruefung

Der vorbereitete Server-Selbsttest baut alle drei Arena-Varianten, prueft verfuegbare Mob-Spawns und Shopangebote und erprobt alle fuenf Zielarten sowie alle zwoelf Begleitrouten gegen die tatsaechlichen Bloecke. Er wird nur nach Freigabe gestartet (`gradle runServer -Dchronicle_arena.selftest=true`, neue lokale Testwelt).

Ein Client-/Koop-Test bleibt notwendig fuer Darstellung, Eingaben, echte Schadensreduktion/Totems und Wechselwirkungen mit den anderen Mods im vollstaendigen Pack. Die Code- und automatisierte Pruefung ist keine Garantie fuer Fehlerfreiheit im gesamten Spielbetrieb. Kein PR/Release angelegt.
