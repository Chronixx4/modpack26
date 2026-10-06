#!/usr/bin/env bash
# Entpackt das Server-ZIP, startet den Server und wartet auf "Done (".
# Aufruf: servertest.sh <Server-ZIP> [Testordner]
# Umgebung: TEST_RAM (Standard 6G), TEST_TIMEOUT_MIN (Standard 20)
set -euo pipefail

ZIP="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
DIR="${2:-$(dirname "$ZIP")/servertest}"
RAM="${TEST_RAM:-6G}"
TIMEOUT=$(( ${TEST_TIMEOUT_MIN:-20} * 60 ))

rm -rf "$DIR" && mkdir -p "$DIR" && cd "$DIR"
unzip -q "$ZIP"
sed -i 's/^eula=false/eula=true/' eula.txt
printf -- '-Xms2G\n-Xmx%s\n' "$RAM" > user_jvm_args.txt

mkfifo console.in
sleep infinity > console.in &
KEEP=$!
./start.sh < console.in > console.log 2>&1 &
PID=$!

result=TIMEOUT
start=$(date +%s)
while [ $(( $(date +%s) - start )) -lt "$TIMEOUT" ]; do
  sleep 5
  if grep -q 'Done (' console.log 2>/dev/null; then result=OK; break; fi
  if ! kill -0 "$PID" 2>/dev/null; then result=ABGESTUERZT; break; fi
done

if kill -0 "$PID" 2>/dev/null; then
  echo stop > console.in
  for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 2; done
  kill "$PID" 2>/dev/null || true
fi
kill "$KEEP" 2>/dev/null || true

echo "== Servertest: $result nach $(( $(date +%s) - start )) s ($(ls mods | wc -l) Mods im mods-Ordner)"
grep -m1 'Done (' console.log || true
if [ -f logs/latest.log ]; then
  echo "== ERROR/FATAL (ohne bekannte harmlose Meldungen):"
  grep -E '/(ERROR|FATAL)\]' logs/latest.log \
    | grep -vE 'Parsing error loading recipe|copycat_catwalk|Trade result entry|loot_table\]:(railways|create_connected|dndecor)|Yggdrasil|Couldn.t load advancements' \
    | cut -c1-300 | head -40 || true
fi
if ls crash-reports/*.txt >/dev/null 2>&1; then
  echo "== Crash-Report:"
  head -60 "$(ls -t crash-reports/*.txt | head -1)"
fi
[ "$result" = OK ]
