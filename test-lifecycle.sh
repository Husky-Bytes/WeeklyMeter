#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
mapfile -t STUBS < <(find tests/lifecycle -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/Scheduler.java" "$SRC/RefreshInterval.java" "$SRC/UsageJob.java" "$SRC/AutoRefreshDiagnostics.java" "$SRC/RefreshFeedback.java" "$SRC/RefreshFeedbackModel.java" "${STUBS[@]}"
java -cp "$OUT" dev.yerin.weeklymeter.LifecycleTests
