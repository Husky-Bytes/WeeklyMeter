#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
mapfile -t STUBS < <(find tests/floating-lifecycle -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/FloatingWidgetService.java" "$SRC/FloatingGeometry.java" "$SRC/FloatingGesture.java" "$SRC/DisplayExpiry.java" "$SRC/AppSignals.java" "${STUBS[@]}"
java -cp "$OUT" dev.yerin.weeklymeter.FloatingLifecycleTests
