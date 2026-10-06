#!/usr/bin/env sh
# Startet den Server. Beim ersten Start wird NeoForge automatisch installiert.
cd "$(dirname "$0")" || exit 1
NEOFORGE=@NEOFORGE@
JAVA="${JAVA:-java}"
ARGS="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"

if ! "$JAVA" -version >/dev/null 2>&1; then
  echo "Java nicht gefunden. Es wird Java 21 gebraucht."
  exit 1
fi
if [ ! -f "$ARGS" ]; then
  echo "Installiere NeoForge $NEOFORGE ..."
  "$JAVA" -jar "neoforge-$NEOFORGE-installer.jar" --installServer || { echo "NeoForge-Installation fehlgeschlagen"; exit 1; }
  rm -f run.sh run.bat
fi
exec "$JAVA" @user_jvm_args.txt @"$ARGS" nogui "$@"
