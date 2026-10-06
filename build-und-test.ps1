# Modpack bauen und testen (Minecraft 1.21.1, NeoForge)
# Start: Rechtsklick -> "Mit PowerShell ausfuehren"  (oder START.bat doppelklicken)
# Ergebnis: ERGEBNIS.txt und das fertige Pack (.mrpack) im selben Ordner.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$MC      = '1.21.1'
$LOADER  = 'neoforge'
$PACK    = 'Unser Modpack'
$PACKVER = '0.9.4'
$RAM     = '8G'
$TIMEOUT_MIN = 20

$Root   = Split-Path -Parent $MyInvocation.MyCommand.Path
$Work   = Join-Path $Root 'arbeit'
$Server = Join-Path $Work 'testserver'
$Cache  = Join-Path $Work 'download-cache'
$CfDir  = Join-Path $Root 'curseforge-mods'   # hier .jar-Dateien von CurseForge reinlegen
$Report = Join-Path $Root 'ERGEBNIS.txt'
$UA     = @{ 'User-Agent' = 'chronixx-modpack-builder/1.0 (private)' }
New-Item -ItemType Directory -Force -Path $Work, $Cache, $CfDir | Out-Null

# CurseForge-Mods automatisch aus dem Download-Ordner holen (falls dort vorhanden)
$dl = Join-Path $env:USERPROFILE 'Downloads'
$cfPatterns = @('*twilightforest*1.21.1*.jar','ExtendedAE-1.21-*neoforge*.jar','ars_elemental-1.21.1-*.jar','ars_technica-1.21.1-*.jar',
                '*ftb*quests*2101*.jar','*ftb*library*2101*.jar','*ftb*teams*2101*.jar','*ftbbackups2*neoforge*.jar','AE2NetworkAnalyzer-1.21-*neoforge*.jar','allthemodium-*_mc_1.21.1.jar')
if (Test-Path $dl) {
  foreach ($pat in $cfPatterns) {
    $hit = Get-ChildItem -Path $dl -Filter $pat -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch 'fabric' } | Sort-Object LastWriteTime | Select-Object -Last 1
    if ($hit -and -not (Test-Path (Join-Path $CfDir $hit.Name))) {
      Copy-Item -LiteralPath $hit.FullName $CfDir -Force
      Write-Host ("  aus Downloads uebernommen: " + $hit.Name)
    } elseif (-not $hit -and -not (Get-ChildItem -Path $CfDir -Filter $pat -ErrorAction SilentlyContinue)) {
      Write-Host ("  WARNUNG: nicht in Downloads gefunden: " + $pat) -ForegroundColor Yellow
    }
  }
}
$cfJars = @(Get-ChildItem -Path $CfDir -Filter *.jar -ErrorAction SilentlyContinue)

$log = New-Object System.Collections.Generic.List[string]
function Say($t) { Write-Host $t; $log.Add($t) }
$apiFails = New-Object System.Collections.Generic.List[string]
function Api($path) {
  # Modrinth erlaubt ca. 300 Anfragen pro Minute. Wir bleiben deutlich darunter
  # und warten bei "zu viele Anfragen" (429) so lange, bis es wieder geht.
  for ($i = 0; $i -lt 10; $i++) {
    try { Start-Sleep -Milliseconds 350; return Invoke-RestMethod -Uri ("https://api.modrinth.com/v2/" + $path) -Headers $UA -ErrorAction Stop }
    catch {
      $code = $null; try { $code = [int]$_.Exception.Response.StatusCode } catch {}
      if ($code -eq 404) { return $null }
      if ($code -eq 429) { Write-Host " (Modrinth bremst, warte 20 s)" -NoNewline; Start-Sleep -Seconds 20 }
      else { Start-Sleep -Seconds (3 * ($i + 1)) }
    }
  }
  $script:apiFails.Add($path)
  return $null
}
function Norm($s) { return (($s.ToLower()) -replace '[^a-z0-9]', '') }
function Write-Report($result, $secs, $serverMods) {
$out = New-Object System.Collections.Generic.List[string]
  $out.Add("ERGEBNIS Modpack-Test  $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
  $out.Add("Minecraft $MC  NeoForge $nf  Mods im Pack: $($files.Count)  davon auf Server: $serverMods")
  $out.Add("SERVERSTART: $result  ($secs s)")
  $out.Add("")
  $out.Add("--- Eingepackte CurseForge-Mods ---")
  if ($cfJars.Count) { $cfJars | ForEach-Object { $out.Add("  " + $_.Name) } } else { $out.Add("  keine") }
  $out.Add("--- Nicht auf Modrinth gefunden (von CurseForge laden und in den Ordner curseforge-mods legen) ---")
  if ($missing.Count) { $missing | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("--- Kein exakter Treffer (nicht eingebaut) ---")
  if ($unsure.Count) { $unsure | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("--- Fehlende Abhaengigkeiten ---")
  if ($depMissing.Count) { $depMissing | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("--- Anfragen, die trotz Wiederholung fehlschlugen ---")
  if ($apiFails.Count) { $apiFails | Select-Object -First 30 | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("--- Versionshinweise ---")
  if ($versionWarn.Count) { $versionWarn | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("--- Inkompatibilitaeten laut Modrinth ---")
  if ($conflicts.Count) { $conflicts | ForEach-Object { $out.Add("  $_") } } else { $out.Add("  keine") }
  $out.Add("")
  $out.Add("--- Fehler aus dem Serverlog ---")
  if ($latest -and (Test-Path $latest)) {
    $err = Select-String -Path $latest -Pattern 'ERROR|FATAL|Missing or unsupported|Mod loading|requires|Exception' | Select-Object -First 80
    if ($err) { $err | ForEach-Object { $out.Add("  " + $_.Line) } } else { $out.Add("  keine") }
  } else { $out.Add("  kein Log gefunden (Server ist nicht gestartet)") }
  $crash = $null
  if ($latest) { $crash = Get-ChildItem (Join-Path $Server 'crash-reports') -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 }
  if ($crash) {
    $out.Add(""); $out.Add("--- Crash-Report: $($crash.Name) ---")
    Get-Content $crash.FullName -TotalCount 120 | ForEach-Object { $out.Add($_) }
  }
  $out.Add("")
  $out.Add("--- Alle Mods im Pack ---")
  foreach ($e in ($picked.Values | Sort-Object { $_.project.title })) {
    $out.Add(("  {0}  {1}  [{2}]" -f $e.project.title, $e.version.version_number, $e.reason))
  }
  [IO.File]::WriteAllLines($Report, $out.ToArray(), (New-Object Text.UTF8Encoding $true))
  
}

# ---------------------------------------------------------------------------
# Mod-Liste: slug-Vorschlag | Name  (passt der slug nicht, wird nach dem Namen gesucht)
# Zeilen mit '# ' sind Kategorien fuer MODLISTE.txt
# ---------------------------------------------------------------------------
$ModListRaw = @'
# Performance und Grafik
sodium|Sodium
modernfix|ModernFix
ferrite-core|FerriteCore
lithium|Lithium
immediatelyfast|ImmediatelyFast
entityculling|Entity Culling
distanthorizons|Distant Horizons
clumps|Clumps
# Komfort und Oberflaeche
emi|EMI
jade|Jade
xaeros-minimap|Xaero's Minimap
xaeros-world-map|Xaero's World Map
appleskin|AppleSkin
mouse-tweaks|Mouse Tweaks
controlling|Controlling
better-advancements|Better Advancements
default-options|Default Options
polymorph|Polymorph
cameraoverhaul|Camera Overhaul
sound-physics-remastered|Sound Physics Remastered
ambientsounds|AmbientSounds
visual-workbench|Visual Workbench
easy-anvils|Easy Anvils
grind-enchantments|Grind Enchantments
carry-on|Carry On
corpse|Corpse
comforts|Comforts
torchmaster|Torchmaster
explorers-compass|Explorer's Compass
natures-compass|Nature's Compass
spark|Spark
# Multiplayer, Quests und Verwaltung
ftb-quests|FTB Quests
ftb-library|FTB Library
ftb-backups-2|FTB Backups 2
open-parties-and-claims|Open Parties and Claims
simple-voice-chat|Simple Voice Chat
kubejs|KubeJS
# Lagerung und Rucksaecke
sophisticated-backpacks|Sophisticated Backpacks
sophisticated-storage|Sophisticated Storage
storagedrawers|Storage Drawers
travelersbackpack|Traveler's Backpack
# Magie
ars-nouveau|Ars Nouveau
ars-additions|Ars Additions
ars-elemental|Ars Elemental
ars-creo|Ars Creo
ars-energistique|Ars Energistique
ars-technica|Ars Technica
ars-ocultas|Ars Ocultas
not-enough-glyphs|Not Enough Glyphs
irons-spells-n-spellbooks|Iron's Spells 'n Spellbooks
cataclysm-spellbooks|Cataclysm: Spellbooks
occultism|Occultism
# Create und Addons
create|Create
createaddition|Create Crafts & Additions
create-steam-n-rails-1.21.1|Create: Steam 'n' Rails
create-enchantment-industry|Create Enchantment Industry
create-new-age|Create: New Age
slice-and-dice|Create Slice & Dice
create-connected|Create Connected
copycats|Create: Copycats+
create-design-n-decor|Create: Design n' Decor
create-deco|Create Deco
create-aeronautics|Create Aeronautics
sable|Sable
waystones-sable|Waystones: Sable
create-big-cannons|Create Big Cannons
create-big-cannons-advanced-technologies|Create Big Cannons: Advanced Technologies
create-transmission|Create: Transmission
create-jetpack|Create Jetpack
create-central-kitchen|Create: Central Kitchen
create-garnished|Create: Garnished
create-dreams-and-desires|Create: Dreams & Desires
create-stuff-additions|Create Stuff 'N Additions
create_factory_logistics|Create Factory Logistics
create-power-loader|Create: Power Loader
create-ore-excavation|Create Ore Excavation
create-bits-n-bobs|Create: Bits 'n' Bobs
create-diesel-generators|Create: Diesel Generators
interiors|Create: Interiors
create-trainutilities|Create Train Utilities
create-railways-navigator|Create Railways Navigator
create-metallurgy|Create: Metallurgy
create-mechanical-spawner|Create Mechanical Spawner
# AE2 und Technik
ae2|Applied Energistics 2
applied-energistics-2-wireless-terminals|AE2 Wireless Terminals
extendedae|ExtendedAE
advanced-ae|Advanced AE
mega|MEGA Cells
applied-flux|Applied Flux
ae2-network-analyser|AE2 Network Analyser
mekanism|Mekanism
applied-mekanistics|Applied Mekanistics
powah|Powah
# Ressourcen und Landwirtschaft
mystical-agriculture|Mystical Agriculture
productivebees|Productive Bees
farmers-delight|Farmer's Delight
aquaculture|Aquaculture 2
# Dimensionen
the-twilight-forest|The Twilight Forest
aether|The Aether
deeper-and-darker|Deeper and Darker
the-undergarden|The Undergarden
the-bumblezone|The Bumblezone
betternether-neoforge|BetterNether Neoforge
betterend-neoforge|BetterEnd Neoforge
kSsLSjEt|Alex's Caves (Unofficial Port)
# Weltgenerierung und Biome
terralith|Terralith
tectonic|Tectonic
biomes-o-plenty|Biomes O' Plenty
regions-unexplored|Regions Unexplored
# Strukturen und Dungeons
when-dungeons-arise|When Dungeons Arise
when-dungeons-arise-seven-seas|When Dungeons Arise - Seven Seas
repurposed-structures-forge|Repurposed Structures
yungs-better-dungeons|YUNG's Better Dungeons
yungs-better-mineshafts|YUNG's Better Mineshafts
yungs-better-strongholds|YUNG's Better Strongholds
yungs-better-ocean-monuments|YUNG's Better Ocean Monuments
yungs-better-nether-fortresses|YUNG's Better Nether Fortresses
yungs-better-desert-temples|YUNG's Better Desert Temples
yungs-better-jungle-temples|YUNG's Better Jungle Temples
yungs-better-witch-huts|YUNG's Better Witch Huts
yungs-bridges|YUNG's Bridges
towns-and-towers|Towns and Towers
# Mobs und Bosse
l_enders-cataclysm|L_Ender's Cataclysm
mowzies-mobs|Mowzie's Mobs
integrated-mowzies-mobs|Integrated Mowzie's Mobs
born-in-chaos|Born in Chaos
eternal-hunts|Eternal Hunts
iceandfire-ce|IceAndFire Community Edition
illager-invasion|Illager Invasion
friends-and-foes-forge|Friends&Foes
mutant-monsters|Mutant Monsters
aquamirae|Aquamirae
# Waffen, Ruestung und Ausruestung
simply-swords|Simply Swords
simply-swords-cataclysm|Simply Swords: Cataclysm
archers|Archers (RPG Series)
archers-expansion|Archers Expansion
rogues-and-warriors|Rogues & Warriors
apotheosis|Apotheosis
apothic-attributes|Apothic Attributes
artifacts|Artifacts
immersive-armors|Immersive Armors
# Transport
waystones|Waystones
# Deko und Bauen
supplementaries|Supplementaries
amendments|Amendments
quark|Quark
macaws-bridges|Macaw's Bridges
macaws-doors|Macaw's Doors
macaws-windows|Macaw's Windows
macaws-roofs|Macaw's Roofs
macaws-furniture|Macaw's Furniture
macaws-lights-and-lamps|Macaw's Lights and Lamps
chipped|Chipped
handcrafted|Handcrafted
framedblocks|FramedBlocks
# Bibliotheken (fehlen auf Modrinth als Abhaengigkeit)
irons-lib|Iron's Lib|^1\.21\.1-2
bundle-api|Bundle API
smartbrainlib|SmartBrainLib
lithostitched|Lithostitched
structure-pool-api|Structure Pool API
polylib|PolyLib
'@
$ModList = @($ModListRaw -split "`r?`n" | Where-Object { $_ -match '\|' })
$catOf = [ordered]@{}   # Name aus der Liste -> Kategorie
$curCat = 'Sonstiges'
foreach ($l in ($ModListRaw -split "`r?`n")) {
  if ($l -match '^#\s*(.+)$') { $curCat = $Matches[1].Trim() }
  elseif ($l -match '\|') { $catOf[$l.Split('|')[1].Trim()] = $curCat }
}
# Kategorien der CurseForge-Jars (Dateiname -> Kategorie)
$cfCat = [ordered]@{
  'twilightforest' = 'Dimensionen'; 'ExtendedAE' = 'AE2 und Technik'; 'AE2NetworkAnalyzer' = 'AE2 und Technik'
  'ars_' = 'Magie'; 'ftb' = 'Multiplayer, Quests und Verwaltung'; 'allthemodium' = 'Dimensionen'
}

$vq = '?loaders=' + [uri]::EscapeDataString('["neoforge"]') + '&game_versions=' + [uri]::EscapeDataString('["1.21.1"]')

function Get-BestVersion($projectId, $pattern) {
  $vs = Api ("project/$projectId/version" + $vq)
  if (-not $vs -or $vs.Count -eq 0) { return $null }
  if ($pattern) {
    $m = @($vs | Where-Object { $_.version_number -match $pattern })
    if ($m.Count -gt 0) { $vs = $m } else { $script:versionWarn.Add("$projectId : keine Version passt zu '$pattern', nehme neueste") }
  }
  $rel = @($vs | Where-Object { $_.version_type -eq 'release' })
  if ($rel.Count -gt 0) { return $rel[0] } else { return @($vs)[0] }
}

function Find-Project($slug, $name, $pattern) {
  $p = Api ("project/" + $slug)
  if ($p -and $p.project_type -eq 'mod') {
    $v = Get-BestVersion $p.id $pattern
    if ($v) { return @{ project = $p; version = $v; how = 'slug' } }
  }
  $facets = [uri]::EscapeDataString('[["versions:1.21.1"],["categories:neoforge"],["project_type:mod"]]')
  $res = Api ("search?limit=5&query=" + [uri]::EscapeDataString($name) + "&facets=" + $facets)
  if (-not $res -or $res.hits.Count -eq 0) { return $null }
  $want = Norm $name
  $hit = $null
  # nur exakte Namenstreffer, sonst lieber "nicht gefunden" als eine falsche Mod
  foreach ($h in $res.hits) { if ((Norm $h.title) -eq $want) { $hit = $h; break } }
  $how = 'suche'
  if (-not $hit) {
    $script:unsure.Add("$name : kein exakter Treffer (naechster waere: $($res.hits[0].title)) -> NICHT eingebaut")
    return $null
  }
  $p = Api ("project/" + $hit.project_id)
  $v = Get-BestVersion $hit.project_id $pattern
  if ($p -and $v) { return @{ project = $p; version = $v; how = $how } }
  return $null
}

# ---------------------------------------------------------------------------
Say "== Schritt 1/4: Mods auf Modrinth aufloesen ($($ModList.Count) Eintraege) =="
$picked   = @{}   # project_id -> @{project, version, reason}
$versionWarn = New-Object System.Collections.Generic.List[string]
$missing  = New-Object System.Collections.Generic.List[string]
$unsure   = New-Object System.Collections.Generic.List[string]
$conflicts= New-Object System.Collections.Generic.List[string]

foreach ($line in $ModList) {
  $parts = $line.Split('|')
  $slug = $parts[0].Trim(); $name = $parts[1].Trim(); $pattern = $null
  if ($parts.Count -gt 2) { $pattern = $parts[2].Trim() }
  Write-Host ("  " + $name + " ...") -NoNewline
  $r = Find-Project $slug $name $pattern
  if (-not $r) { Write-Host " NICHT GEFUNDEN"; $missing.Add($name); continue }
  if ($r.how -eq 'suche-UNSICHER') { $unsure.Add("$name -> $($r.project.title) ($($r.project.slug))") }
  $picked[$r.project.id] = @{ project = $r.project; version = $r.version; reason = $name }
  Write-Host (" ok: " + $r.project.title + " " + $r.version.version_number)
}

Say "== Schritt 2/4: Abhaengigkeiten aufloesen =="
$queue = New-Object System.Collections.Queue
foreach ($k in @($picked.Keys)) { $queue.Enqueue($k) }
$depMissing = New-Object System.Collections.Generic.List[string]
while ($queue.Count -gt 0) {
  $pid0 = $queue.Dequeue()
  $entry = $picked[$pid0]
  foreach ($d in $entry.version.dependencies) {
    if (-not $d.project_id) { continue }
    if ($d.dependency_type -eq 'incompatible' -and $picked.ContainsKey($d.project_id)) {
      $conflicts.Add("$($entry.project.title) ist inkompatibel mit $($picked[$d.project_id].project.title)")
    }
    if ($d.dependency_type -ne 'required') { continue }
    if ($picked.ContainsKey($d.project_id)) { continue }
    $p = Api ("project/" + $d.project_id)
    $v = Get-BestVersion $d.project_id
    if ($p -and $v) {
      $picked[$d.project_id] = @{ project = $p; version = $v; reason = "Abhaengigkeit von $($entry.project.title)" }
      Say ("  + " + $p.title + " (fuer " + $entry.project.title + ")")
      $queue.Enqueue($d.project_id)
    } else {
      $title = if ($p) { $p.title } else { $d.project_id }
      $depMissing.Add("$title (gebraucht von $($entry.project.title)) hat keine 1.21.1-NeoForge-Version")
    }
  }
}

Say "== Schritt 3/4: Pack bauen (.mrpack) =="
$files = @()
foreach ($e in $picked.Values) {
  $f = @($e.version.files | Where-Object { $_.primary })
  if ($f.Count -eq 0) { $f = @($e.version.files) }
  $f = $f[0]
  $cs = $e.project.client_side; $ss = $e.project.server_side
  $files += [ordered]@{
    path = "mods/" + $f.filename
    hashes = [ordered]@{ sha1 = $f.hashes.sha1; sha512 = $f.hashes.sha512 }
    env = [ordered]@{
      client = 'required'   # im Client alles installieren: viele Bibliotheken sind auf Modrinth als 'nur Server' markiert, werden aber trotzdem gebraucht
      server = $(if ($ss -eq 'unsupported') { 'unsupported' } elseif ($ss -eq 'optional') { 'optional' } else { 'required' })
    }
    downloads = @($f.url)
    fileSize = $f.size
  }
}

# NeoForge-Version ermitteln
$meta = [xml](Invoke-WebRequest -UseBasicParsing -Uri 'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml' -Headers $UA).Content
$nf = @($meta.metadata.versioning.versions.version | Where-Object { $_ -match '^21\.1\.\d+$' } | Sort-Object { [int]($_.Split('.')[2]) })[-1]
Say "  NeoForge: $nf"

$index = [ordered]@{
  formatVersion = 1; game = 'minecraft'; versionId = $PACKVER; name = $PACK
  files = $files
  dependencies = [ordered]@{ minecraft = $MC; neoforge = $nf }
}
$packDir = Join-Path $Work 'mrpack'
Remove-Item -Recurse -Force $packDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $packDir | Out-Null
$json = $index | ConvertTo-Json -Depth 10
[IO.File]::WriteAllText((Join-Path $packDir 'modrinth.index.json'), $json, (New-Object Text.UTF8Encoding $false))
if ($cfJars.Count -gt 0) {
  $ov = Join-Path $packDir 'overrides\mods'
  New-Item -ItemType Directory -Force -Path $ov | Out-Null
  foreach ($j in $cfJars) { Copy-Item -LiteralPath $j.FullName $ov -Force }
  Say "  $($cfJars.Count) CurseForge-Mods aus dem Ordner curseforge-mods mit eingepackt"
}
$mrpack = Join-Path $Root ("Unser-Modpack-" + $PACKVER + ".mrpack")
Remove-Item -Force $mrpack -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($packDir, $mrpack)
Say "  Pack erstellt: $mrpack  ($($files.Count) Dateien)"
# Pack-Definition fuers Repo (daraus baut der GitHub-Release-Workflow das .mrpack)
$packOut = Join-Path $Root 'pack'
New-Item -ItemType Directory -Force -Path $packOut | Out-Null
Copy-Item -LiteralPath (Join-Path $packDir 'modrinth.index.json') (Join-Path $packOut 'modrinth.index.json') -Force

# MODLISTE.txt: alle Mods nach Kategorie (Kategorien aus den '# '-Zeilen der Mod-Liste)
$libCat = 'Bibliotheken (werden automatisch gebraucht)'
$groups = [ordered]@{}
foreach ($c in $catOf.Values) { if ($c -notlike 'Bibliotheken*' -and -not $groups.Contains($c)) { $groups[$c] = New-Object System.Collections.Generic.List[string] } }
$groups['Sonstiges'] = New-Object System.Collections.Generic.List[string]
$groups[$libCat] = New-Object System.Collections.Generic.List[string]
foreach ($e in $picked.Values) {
  $g = $catOf[$e.reason]
  if (-not $g -or $g -like 'Bibliotheken*') { $g = $libCat }
  $note = ''
  if ($e.project.server_side -eq 'unsupported') { $note = '  (nur Client)' }
  $groups[$g].Add(("  {0,-46} {1}{2}" -f $e.project.title, $e.version.version_number, $note))
}
foreach ($j in $cfJars) {
  $g = 'Sonstiges'
  foreach ($k in $cfCat.Keys) { if ($j.Name -like "$k*") { $g = $cfCat[$k]; break } }
  if (-not $groups.Contains($g)) { $groups[$g] = New-Object System.Collections.Generic.List[string] }
  # Name und Version aus META-INF/neoforge.mods.toml in der Jar lesen
  $title = [IO.Path]::GetFileNameWithoutExtension($j.Name); $ver = ''
  try {
    $zip = [IO.Compression.ZipFile]::OpenRead($j.FullName)
    $ent = $zip.GetEntry('META-INF/neoforge.mods.toml')
    if ($ent) {
      $rd = New-Object IO.StreamReader($ent.Open()); $toml = $rd.ReadToEnd(); $rd.Close()
      if ($toml -match '(?m)^\s*displayName\s*=\s*"([^"]+)"') { $title = $Matches[1] }
      if ($toml -match '(?m)^\s*version\s*=\s*"([^"$]+)"') { $ver = $Matches[1] }
    }
    $zip.Dispose()
  } catch {}
  if (-not $ver) { $ver = [IO.Path]::GetFileNameWithoutExtension($j.Name) }
  $groups[$g].Add(("  {0,-46} {1}  (CurseForge)" -f $title, $ver))
}
$ml = New-Object System.Collections.Generic.List[string]
$total = $picked.Count + $cfJars.Count
$ml.Add("$PACK $PACKVER - Modliste")
$ml.Add("Minecraft $MC, NeoForge $nf   |   $total Mods inkl. Bibliotheken   |   Stand $(Get-Date -Format 'yyyy-MM-dd')")
foreach ($g in $groups.Keys) {
  if ($groups[$g].Count -eq 0) { continue }
  $ml.Add(""); $ml.Add("== $g ($($groups[$g].Count)) ==")
  $groups[$g] | Sort-Object | ForEach-Object { $ml.Add($_) }
}
[IO.File]::WriteAllLines((Join-Path $Root 'MODLISTE.txt'), $ml.ToArray(), (New-Object Text.UTF8Encoding $true))
Say "  Modliste geschrieben: MODLISTE.txt"
$latest = $null
Write-Report "noch nicht getestet" 0 0
Say "  Bericht geschrieben: $Report (wird nach dem Servertest aktualisiert)"

# ---------------------------------------------------------------------------
Say "== Schritt 4/4: Testserver starten =="
# Java 21 pruefen, sonst portabel laden
$java = $null
$ErrorActionPreference = 'Continue'
try { $jv = (& java -version 2>&1 | Out-String); if ($jv -match 'version "(2[1-9]|[3-9]\d)') { $java = 'java' } } catch {}
if (-not $java) {
  $jdkDir = Join-Path $Work 'jdk21'
  $exe = Get-ChildItem -Path $jdkDir -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $exe) {
    Say "  Java 21 fehlt, lade portables JDK (Eclipse Temurin) ..."
    $zip = Join-Path $Work 'jdk21.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $jdkDir -Force
    $exe = Get-ChildItem -Path $jdkDir -Recurse -Filter java.exe | Select-Object -First 1
  }
  $java = $exe.FullName
}
$ErrorActionPreference = 'Stop'
Say "  Java: $java"

Remove-Item -Recurse -Force $Server -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $Server 'mods') | Out-Null
$inst = Join-Path $Cache "neoforge-$nf-installer.jar"
if (-not (Test-Path $inst)) {
  Invoke-WebRequest -UseBasicParsing -Uri "https://maven.neoforged.net/releases/net/neoforged/neoforge/$nf/neoforge-$nf-installer.jar" -OutFile $inst
}
Push-Location $Server
$ErrorActionPreference = 'Continue'
& $java -jar $inst --installServer $Server *> (Join-Path $Work 'neoforge-install.log')
$ErrorActionPreference = 'Stop'
Pop-Location
if (-not (Test-Path (Join-Path $Server "libraries/net/neoforged/neoforge/$nf/win_args.txt"))) { Say '  FEHLER: NeoForge-Installation fehlgeschlagen, siehe arbeit\neoforge-install.log' }

$serverMods = 0
foreach ($f in $files) {
  if ($f.env.server -eq 'unsupported') { continue }
  $name = Split-Path $f.path -Leaf
  $c = Join-Path $Cache $name
  # -LiteralPath: Dateinamen wie born_in_chaos_[Neoforge]_... enthalten eckige Klammern (sonst Platzhalter)
  if (-not (Test-Path -LiteralPath $c)) {
    $tmp = Join-Path $Cache 'download.tmp'
    Invoke-WebRequest -UseBasicParsing -Uri $f.downloads[0] -OutFile $tmp
    Move-Item -LiteralPath $tmp -Destination $c -Force
  }
  Copy-Item -LiteralPath $c (Join-Path $Server 'mods') -Force
  $serverMods++
}
Say "  $serverMods Mods im Testserver (reine Client-Mods wie Sodium oder Karten werden serverseitig ausgelassen)"
foreach ($j in $cfJars) { Copy-Item -LiteralPath $j.FullName (Join-Path $Server 'mods') -Force; $serverMods++ }
Set-Content -Path (Join-Path $Server 'eula.txt') -Value 'eula=true'
Set-Content -Path (Join-Path $Server 'user_jvm_args.txt') -Value "-Xms2G`n-Xmx$RAM"

$argsFile = "libraries/net/neoforged/neoforge/$nf/win_args.txt"
$psi = New-Object Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = "@user_jvm_args.txt @$argsFile nogui"
$psi.WorkingDirectory = $Server
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$proc = [Diagnostics.Process]::Start($psi)
$start = Get-Date
$latest = Join-Path $Server 'logs\latest.log'
$result = 'TIMEOUT'
Say "  Server startet, das dauert beim ersten Mal ein paar Minuten (Weltgenerierung) ..."
while ((Get-Date) - $start -lt [TimeSpan]::FromMinutes($TIMEOUT_MIN)) {
  Start-Sleep -Seconds 5
  if ($proc.HasExited) { $result = 'ABGESTUERZT'; break }
  if (Test-Path $latest) {
    $tail = Get-Content $latest -Tail 40 -ErrorAction SilentlyContinue | Out-String
    if ($tail -match 'Done \(\d') { $result = 'OK'; break }
  }
}
if (-not $proc.HasExited) {
  try { $proc.StandardInput.WriteLine('stop'); $proc.StandardInput.Flush() } catch {}
  if (-not $proc.WaitForExit(120000)) { $proc.Kill() }
}
$secs = [int]((Get-Date) - $start).TotalSeconds

# ---------------------------------------------------------------------------
Write-Report $result $secs $serverMods

Write-Host ""
Write-Host "FERTIG. Serverstart: $result" -ForegroundColor Cyan
Write-Host "Bericht: $Report"
Write-Host "Pack:    $mrpack"
Write-Host "Schick mir den Inhalt von ERGEBNIS.txt."
Read-Host "Enter zum Schliessen"
