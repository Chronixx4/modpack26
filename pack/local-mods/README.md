# Eigene Mods fuer den Windows-Builder

`chronicle-arena-0.3.1.jar` ist der veroeffentlichte Build aus
`arena-v0.3.1` (Quellcommit `f6a3be4a2538316238f13809036892e7d6c76943`).
Die SHA-256-Pruefsumme stammt aus diesem Release.

Der PowerShell-Builder liest `mod_version` aus `mod/chronicle-arena/gradle.properties`,
prueft die passende JAR per SHA-256 und kopiert sie in Client und Server.
Fehlt die passende Version, bricht er ab statt eine alte Arena einzupacken.
Der Linux-/GitHub-Builder baut denselben Quellstand mit Gradle und fuehrt
auch die automatisierten Tests aus. Ein Clean-Build verhindert doppelte alte JARs.

Bei einem kuenftigen Arena-Update den freigegebenen Quellstand, die lokale
Release-JAR und ihre Pruefsumme gemeinsam aktualisieren. Alte lokale Arena-JARs
entfernen; auf Server und allen Clients muss dieselbe Arena-Version laufen.
