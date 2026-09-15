#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mapfile -t SOURCES < <(find tests/browser-service -name '*.java')
javac --release 8 -encoding UTF-8 -d "$OUT" "${SOURCES[@]}" app/src/main/java/dev/yerin/weeklymeter/BrowserLoginService.java
java -cp "$OUT" dev.yerin.weeklymeter.BrowserServiceTests
