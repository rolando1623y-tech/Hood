#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
kotlinc "$ROOT/app/src/main/java/com/panahoops/next/FitCounter.kt" \
        "$ROOT/app/src/main/java/com/panahoops/next/FpsPolicy.kt" \
        "$ROOT/app/src/main/java/com/panahoops/next/ResetPolicy.kt" \
        "$ROOT/app/src/main/java/com/panahoops/next/FitDistance.kt" \
        "$ROOT/verification/CoreChecks.kt" -include-runtime -d "$TEMP/checks.jar"
java -jar "$TEMP/checks.jar"
