# Keybind Overview

Bearbeitbarer Quellcode der Mod fuer Minecraft 1.21.1 und NeoForge.
Die Mod ersetzt das Tastenbelegungsmenue und bietet eine bearbeitbare Tastaturansicht,
Suche, Filter, kompakte Kategorien und Rueckgaengig.

## Weiterbearbeiten

ZIP entpacken und den Ordner KeybindOverview in Claude Code, Codex, IntelliJ IDEA
oder einem anderen Editor oeffnen. Der Quellcode steht unter src/main/java;
Sprachdateien und Mod-Metadaten unter src/main/resources.

## Bauen

Voraussetzung: Java JDK 21. Beim ersten Build ist Internetzugang fuer Gradle
und die Minecraft-/NeoForge-Abhaengigkeiten erforderlich.

Windows:
    .\gradlew.bat build

Linux/macOS:
    chmod +x gradlew
    ./gradlew build

Das Ergebnis liegt in build/libs/keybindoverview-1.0.0.jar.
Das JAR gehoert in den mods-Ordner einer NeoForge-1.21.1-Instanz.

AGENTS.md und CLAUDE.md enthalten bestehende Projekthinweise. Bei abweichenden
Angaben sind build.gradle, gradle.properties und der aktuelle Quellcode massgeblich:
Das Projekt verwendet NeoForge, nicht Forge.