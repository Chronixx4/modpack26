#!/usr/bin/env bash
# Baut aus pack/modrinth.index.json + curseforge-mods/*.jar die Release-Dateien:
#   Chronicle-<ver>.mrpack        (Modrinth App)
#   Chronicle-<ver>-Client.zip    (mods-Ordner fuer andere Launcher)
#   Chronicle-<ver>-Server.zip    (Server-Mods, NeoForge-Installer, Startdateien)
# Aufruf: pakete-bauen.sh [Ausgabeordner]   (Standard: dist/)
# Braucht: jq, curl, sha512sum, zip
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mkdir -p "${1:-$ROOT/dist}" && cd "${1:-$ROOT/dist}" && pwd)"
IDX="$ROOT/pack/modrinth.index.json"
CACHE="${CACHE_DIR:-$OUT/cache}"
VER="$(jq -r .versionId "$IDX")"
MC="$(jq -r .dependencies.minecraft "$IDX")"
NF="$(jq -r .dependencies.neoforge "$IDX")"
NAME="Chronicle-$VER"
mkdir -p "$CACHE"

fill() {  # Platzhalter in Vorlagen ersetzen
  sed -e "s/@VERSION@/$VER/g" -e "s/@MC@/$MC/g" -e "s/@NEOFORGE@/$NF/g" "$1" > "$2"
}

echo "== $NAME (Minecraft $MC, NeoForge $NF)"

# 0) Eigene Mods aus mod/* bauen (Chronicle Arena); die Jars landen wie die CurseForge-Jars in mods/
OWN="$OUT/eigene-mods" && rm -rf "$OWN" && mkdir -p "$OWN"
for d in "$ROOT"/mod/*/; do
  [ -f "$d/build.gradle" ] || continue
  echo "  baue eigene Mod $(basename "$d") ..."
  gradle --no-daemon -q -p "$d" build -x test >&2
  cp "$d"/build/libs/*.jar "$OWN/"
done

# 1) .mrpack
rm -rf "$OUT/mrpack" && mkdir -p "$OUT/mrpack/overrides/mods"
cp "$IDX" "$OUT/mrpack/"
cp "$ROOT"/curseforge-mods/*.jar "$OWN"/*.jar "$OUT/mrpack/overrides/mods/"
[ -d "$ROOT/pack/config" ] && cp -r "$ROOT/pack/config" "$OUT/mrpack/overrides/"   # Questbuch
[ -d "$ROOT/pack/kubejs" ] && cp -r "$ROOT/pack/kubejs" "$OUT/mrpack/overrides/"   # Chronicle-Kern
[ -d "$ROOT/pack/defaultconfigs" ] && cp -r "$ROOT/pack/defaultconfigs" "$OUT/mrpack/overrides/"   # Server-Configs fuer neue Welten
rm -f "$OUT/$NAME.mrpack"
(cd "$OUT/mrpack" && zip -q -r -D "$OUT/$NAME.mrpack" modrinth.index.json overrides)
echo "  $NAME.mrpack"

# 2) Alle Mods von Modrinth laden und Pruefsummen checken
echo "  lade $(jq '.files | length' "$IDX") Mods ..."
jq -r '.files[] | [.path, .hashes.sha512, .downloads[0]] | @tsv' "$IDX" |
while IFS=$'\t' read -r path sha url; do
  f="$CACHE/$(basename "$path")"
  if [ -f "$f" ] && echo "$sha  $f" | sha512sum -c --status; then continue; fi
  curl -fsSL --retry 3 -o "$f" "$url"
  echo "$sha  $f" | sha512sum -c --status || { echo "FEHLER: Pruefsumme falsch: $path" >&2; exit 1; }
done

# Server-Ausschluss: Muster aus pack/server-ausschluss.txt
mapfile -t EXCL < <(grep -v '^\s*#' "$ROOT/pack/server-ausschluss.txt" | sed 's/\r$//' | grep -v '^\s*$' || true)
excluded() {
  local n="$1" p
  for p in "${EXCL[@]}"; do [[ "$n" == $p ]] && return 0; done
  return 1
}

# 3) Client-ZIP
C="$OUT/client" && rm -rf "$C" && mkdir -p "$C/mods"
jq -r '.files[] | select(.env.client != "unsupported") | .path' "$IDX" | while read -r path; do
  cp "$CACHE/$(basename "$path")" "$C/mods/"
done
cp "$ROOT"/curseforge-mods/*.jar "$OWN"/*.jar "$C/mods/"
[ -d "$ROOT/pack/config" ] && cp -r "$ROOT/pack/config" "$C/"
[ -d "$ROOT/pack/kubejs" ] && cp -r "$ROOT/pack/kubejs" "$C/"
[ -d "$ROOT/pack/defaultconfigs" ] && cp -r "$ROOT/pack/defaultconfigs" "$C/"
fill "$ROOT/pack/client/LIES-MICH.txt" "$C/LIES-MICH.txt"
cp "$ROOT/MODLISTE.txt" "$C/"
rm -f "$OUT/$NAME-Client.zip"
(cd "$C" && zip -q -r -D "$OUT/$NAME-Client.zip" .)
echo "  $NAME-Client.zip ($(ls "$C/mods" | wc -l) Mods)"

# 4) Server-ZIP
S="$OUT/server" && rm -rf "$S" && mkdir -p "$S/mods"
jq -r '.files[] | select(.env.server != "unsupported") | .path' "$IDX" | while read -r path; do
  n="$(basename "$path")"
  if excluded "$n"; then echo "    ohne (Client-Mod): $n"; continue; fi
  cp "$CACHE/$n" "$S/mods/"
done
cp "$ROOT"/curseforge-mods/*.jar "$OWN"/*.jar "$S/mods/"
[ -d "$ROOT/pack/config" ] && cp -r "$ROOT/pack/config" "$S/"
[ -d "$ROOT/pack/kubejs" ] && cp -r "$ROOT/pack/kubejs" "$S/"
[ -d "$ROOT/pack/defaultconfigs" ] && cp -r "$ROOT/pack/defaultconfigs" "$S/"
INST="$CACHE/neoforge-$NF-installer.jar"
[ -f "$INST" ] || curl -fsSL --retry 3 -o "$INST" \
  "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NF/neoforge-$NF-installer.jar"
cp "$INST" "$S/"
for t in start.sh start.bat user_jvm_args.txt eula.txt server.properties LIES-MICH.txt; do
  fill "$ROOT/pack/server/$t" "$S/$t"
done
chmod +x "$S/start.sh"
cp "$ROOT/MODLISTE.txt" "$S/"
rm -f "$OUT/$NAME-Server.zip"
(cd "$S" && zip -q -r -D "$OUT/$NAME-Server.zip" .)
echo "  $NAME-Server.zip ($(ls "$S/mods" | wc -l) Mods)"

ls -la "$OUT"/*.mrpack "$OUT"/*.zip
