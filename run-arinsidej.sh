#!/bin/sh
# ARInsideJ launcher. Run from anywhere - always uses this script's own folder as the working
# directory, so arinsidej.jar/settings.ini next to it are found regardless of where you call it
# from. Any arguments you pass are forwarded as-is (e.g. ./run-arinsidej.sh -s myserver -l Demo
# -p mypass); with no arguments it falls back to -i settings.ini in this same folder.

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/arinsidej.jar"

if [ ! -f "$JAR" ]; then
    echo "[ERR] $JAR not found. Build it first with: mvn -o package" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "[ERR] java not found on PATH. Install a JDK/JRE 17+ and try again." >&2
    exit 1
fi

cd "$SCRIPT_DIR"
if [ "$#" -eq 0 ]; then
    exec java -jar "$JAR" -i settings.ini
else
    exec java -jar "$JAR" "$@"
fi
