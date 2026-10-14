# Chronicle FastStart — Diagnosemod (Entwurf 0.1.0)

Ziel: die Startzeit von Chronicle (Minecraft 1.21.1, NeoForge 21.1.x, Java 21) messbar reduzieren, ohne Mods zu entfernen oder Registrierungen unsicher zu parallelisieren.

## Ausgangslage
- Diagnoseprofil vom 14.10.2026: `chronicle-start.jfr`, aufgenommen mit einem Chronicle-0.9.22-Client.
- Im GitHub-Repository liegt inzwischen ein neueres Modpack. Ergebnisse deshalb nicht ungeprüft auf aktuelle Releases übertragen.
- Besonders untersuchen: Ressourcen- und Modellverarbeitung, insbesondere Blockzustände und Modell-Baking; KubeJS sowie JVM-Klassenladen/GC.
- Eine JFR-Aufzeichnungsdauer ist **nicht** automatisch die Zeit vom Klick auf »Spielen« bis zum Hauptmenü.

## MVP 0.1.0: Messungen statt Optimierung
1. Eigenständiges NeoForge-Clientmod (`chronicle_faststart`), getrennt von `chronicle-arena` und zunächst nicht im Paket enthalten.
2. Monotone Zeitmessung (`System.nanoTime`) ab dem frühestmöglichen eigenen Mod-Einstieg; bei definierten NeoForge-Clientereignissen Zeitmarken setzen.
3. »Hauptmenü sichtbar« separat und explizit instrumentieren. Keine Gleichsetzung eines frühen Client-Setup-Events mit tatsächlich sichtbarem Hauptmenü.
4. Bericht in `logs/chronicle-faststart/` als JSON oder CSV mit Zeitmarken, Minecraft-/NeoForge-/Modversion, Messgrenzen und anonymisierbaren Metadaten.
5. Keine Speicherung von Zugangsdaten, Benutzernamen, Systempfaden oder Token im Diagnosebericht.
6. Ein Build- und Starttest auf Java 21 / NeoForge 21.1.x, anschließend Vergleich mehrerer Warmstarts mit identischem Modpack und denselben Java-Optionen.

## Messgrenzen
- Ein normales NeoForge-Mod kann frühe Phasen vor der eigenen Initialisierung nicht messen. Dafür JFR weiter verwenden.
- CPU-Stichproben und Log-Lücken zeigen Verdachtsmomente, aber keine exakte Verursachung pro Mod.
- Die Erkennung des Hauptmenüs muss mit der tatsächlichen Version der NeoForge-/Minecraft-API geprüft werden.

## Später (nur nach Messung)
- Optimierungskandidaten anhand von Profilen priorisieren.
- Geeignete Caches nur mit Versions-/Ressourcen-Invalidierung und klarer Abschaltoption testen.
- Keine Eingriffe in Registrierung, Mixin-Reihenfolge oder Thread-Sicherheit anderer Mods ohne isolierte Tests.

## Nächste Entwicklungsschritte
- Gradle-Scaffold nach dem existierenden `mod/chronicle-arena`-Projekt (ModDevGradle 2.0.78, Java 21).
- Mod-Metadaten und minimalen Client-Mod-Einstieg anlegen.
- Ereignismessung und Diagnoseausgabe implementieren; auf einem Testprofil verifizieren.
