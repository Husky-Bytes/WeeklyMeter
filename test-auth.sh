#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
mapfile -t STUBS < <(find tests/auth-stubs -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/Json.java" "$SRC/Usage.java" "$SRC/NetworkPolicy.java" "$SRC/Api.java" "$SRC/Repo.java" "$SRC/BrowserAuth.java" "${STUBS[@]}" tests/AuthRegressionTests.java
java -cp "$OUT" dev.yerin.weeklymeter.AuthRegressionTests
