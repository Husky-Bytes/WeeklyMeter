#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
javac --release 8 -encoding UTF-8 -d "$OUT" "$SRC/Json.java" "$SRC/Usage.java" "$SRC/NetworkPolicy.java" "$SRC/WidgetStyle.java" "$SRC/RefreshFeedbackModel.java" tests/CoreTests.java tests/UsageRegressionTests.java tests/WidgetStyleTests.java tests/RefreshFeedbackTests.java
java -cp "$OUT" dev.yerin.weeklymeter.CoreTests
java -cp "$OUT" dev.yerin.weeklymeter.UsageRegressionTests
java -cp "$OUT" dev.yerin.weeklymeter.WidgetStyleTests
java -cp "$OUT" dev.yerin.weeklymeter.RefreshFeedbackTests
javac --release 8 -encoding UTF-8 -d "$OUT" tests/FontAssetTests.java
java -Djava.awt.headless=true -cp "$OUT" FontAssetTests app/src/main/assets/fonts
