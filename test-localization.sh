#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
LOCALE_OUT=$(mktemp -d)
trap 'rm -rf "$LOCALE_OUT"' EXIT
SRC=app/src/main/java/dev/yerin/weeklymeter
javac --release 8 -encoding UTF-8 -d "$LOCALE_OUT" "$SRC/Messages.java" tests/MessageLocalizationTests.java
java -cp "$LOCALE_OUT" dev.yerin.weeklymeter.MessageLocalizationTests "$PWD"
