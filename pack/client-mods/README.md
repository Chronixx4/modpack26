# Lokale Client-Mods

JARs in diesem Ordner kommen in `client-overrides/mods` der Modrinth-Datei und
in `mods` der Client-ZIP. Sie kommen nicht in die Server-ZIP.

## Keybind Overview 1.1.0

Eigene Mod aus Marvins Projekt `KeybindOverview`, Quellen in `quellen/keybind-overview`
(Build: `./gradlew build` dort, JAR hierher kopieren). Stand 15.10.2026.
Minecraft 1.21.1, NeoForge ab 21.1.0, Java 21; Lizenz laut Mod-Metadaten: MIT.

SHA-256: `b28488c36904b809256dc27d89986c391f261da2c3192744109f44c08c68df82`

Ersetzt die Tastenbelegungsseite durch eine Tastatur- und Kategorienansicht.
Suche nach Aktion, Kategorie oder Taste (Strg+F), Filter fuer Mehrfachbelegungen
und unbelegte Aktionen, bis zu vier Spalten und einklappbare Kategorien.

Neu in 1.1.0:
- Klick auf eine Aktion in der Liste oeffnet die Tastatur: gruen = frei, blau = belegt,
  rot = echter Konflikt, hell umrandet = Vorschlag (freie Buchstaben nahe WASD).
- Hinweis nach dem Zuweisen, wenn die Taste schon belegt ist; "Rueckgaengig" bleibt.
- Strg/Shift/Alt-Kombinationen zaehlen nicht als Konflikt mit der einfachen Taste.
- Export/Import: `config/keybindoverview/keybindings.txt` (gleiches Format wie options.txt).
- Standardtaste zum Oeffnen ist jetzt unbelegt (K gehoert dem Skilltree). Aufruf ueber
  Optionen -> Steuerung -> Tastenbelegung.
