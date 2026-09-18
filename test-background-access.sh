#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mapfile -t STUBS < <(find tests/background-access -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -d "$OUT" app/src/main/java/dev/yerin/weeklymeter/BackgroundAccess.java "${STUBS[@]}"
java -cp "$OUT" dev.yerin.weeklymeter.BackgroundAccessTests
