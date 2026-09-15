#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
LANGUAGE_OUT=build/language-tests
mkdir -p "$LANGUAGE_OUT"
mapfile -t LANGUAGE_SOURCES < <(find tests/language -name '*.java')
LANGUAGE_SRC=app/src/main/java/dev/yerin/weeklymeter
javac --release 8 -encoding UTF-8 -d "$LANGUAGE_OUT" "${LANGUAGE_SOURCES[@]}" "$LANGUAGE_SRC/LanguagePolicy.java" "$LANGUAGE_SRC/AppLanguage.java" "$LANGUAGE_SRC/Texts.java" "$LANGUAGE_SRC/BootReceiver.java" tests/LanguagePolicyTests.java
java -cp "$LANGUAGE_OUT" dev.yerin.weeklymeter.LanguagePolicyTests
java -cp "$LANGUAGE_OUT" dev.yerin.weeklymeter.AppLanguageTests
