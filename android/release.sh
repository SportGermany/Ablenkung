#!/usr/bin/env bash
# Baut die Handybremse, kopiert die APK und schreibt version.json für das Online-Update.
# Aufruf: ./release.sh "Kurze Beschreibung, was neu ist"
set -euo pipefail
cd "$(dirname "$0")"
NOTES="${1:-Verbesserungen und Fehlerbehebungen}"
./gradlew assembleRelease -q
cp app/build/outputs/apk/release/app-release.apk Handybremse.apk
CODE=$(grep -oP 'versionCode = \K\d+' app/build.gradle.kts)
NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
python3 - "$CODE" "$NAME" "$BRANCH" "$NOTES" <<'PY'
import json, sys
code, name, branch, notes = sys.argv[1:]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apk": f"https://github.com/SportGermany/Ablenkung/raw/{branch}/android/Handybremse.apk",
    "notes": notes,
}, open("version.json", "w"), ensure_ascii=False, indent=2)
PY
echo "Fertig: Version $NAME ($CODE) – jetzt committen und pushen."
cat version.json
