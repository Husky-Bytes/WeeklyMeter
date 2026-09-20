#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 8 -encoding UTF-8 -d "$OUT" app/src/main/java/dev/yerin/weeklymeter/{PreviewGeometry,HomeWidgetPreviewSizes}.java tests/PreviewTests.java
java -cp "$OUT" dev.yerin.weeklymeter.PreviewTests
