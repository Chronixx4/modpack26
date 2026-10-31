# Eigene Mods (fertige JARs)

Die Chronicle Arena wird nicht mehr in diesem Repo entwickelt, sondern in einem eigenen
GitHub-Projekt. Hier liegt nur die fertige JAR (aktuell `chronicle-arena-0.6.3.jar`, aus Marvins Profil)
mit ihrer SHA-256-Pruefsumme in `SHA256SUMS.txt`.

Alle JARs in diesem Ordner kommen in Client und Server (.mrpack, Client-ZIP, Server-ZIP). Beide Builder
(PowerShell und `pakete-bauen.sh`) pruefen die Pruefsumme und brechen ab, wenn sie fehlt oder falsch ist.

Arena-Update: alte JAR loeschen, neue JAR hineinlegen, `SHA256SUMS.txt` neu schreiben
(`sha256sum *.jar > SHA256SUMS.txt`). Auf Server und allen Clients muss dieselbe Arena-Version laufen.
