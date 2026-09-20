#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/Json.java" "$SRC/NetworkPolicy.java" "$SRC/Api.java" tests/ApiDeadlineTests.java
java -cp "$OUT" dev.yerin.weeklymeter.ApiDeadlineTests
