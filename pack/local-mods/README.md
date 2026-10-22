# Eigene Mods fuer den Windows-Builder

`chronicle-arena-0.5.0.jar` ist der Build des Arena-Quellstands auf diesem
Arbeitsbranch (0.4.5 plus neuer Modus "Burg halten" (Tower Defense); Netzwerkversion 12; noch nicht veroeffentlicht). Die passende SHA-256-Pruefsumme
steht in `SHA256SUMS.txt`. Build- und Teststatus: Java-21-Build, 71 automatisierte Tests gruen, im Spiel ungeprueft.

Der PowerShell-Builder liest `mod_version` aus `mod/chronicle-arena/gradle.properties`,
prueft die passende JAR per SHA-256 und kopiert sie in Client und Server.
Fehlt die passende Version, bricht er ab statt eine alte Arena einzupacken.
Der Linux-/GitHub-Builder baut denselben Quellstand mit Gradle und fuehrt
auch die automatisierten Tests aus. Ein Clean-Build verhindert doppelte alte JARs.

Bei einem kuenftigen Arena-Update den freigegebenen Quellstand, die lokale
Build-JAR und ihre Pruefsumme gemeinsam aktualisieren. Alte lokale Arena-JARs
entfernen; auf Server und allen Clients muss dieselbe Arena-Version laufen.
