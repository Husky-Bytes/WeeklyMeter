#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
mapfile -t STUBS < <(find tests/widget-publish -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/WeeklyWidget.java" "$SRC/WidgetAppearance.java" "$SRC/Store.java" "$SRC/Json.java" "$SRC/Usage.java" "$SRC/WidgetStyle.java" "$SRC/Messages.java" "${STUBS[@]}"
java -cp "$OUT" dev.yerin.weeklymeter.WidgetPublishTests
