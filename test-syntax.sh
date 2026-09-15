#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" tests/SyntaxCheck.java
mapfile -t FILES < <(find app/src/main/java -name '*.java' -print | sort)
java -cp "$OUT" SyntaxCheck "${FILES[@]}"
