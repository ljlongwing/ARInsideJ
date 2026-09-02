#!/bin/sh
# ARInsideJ launcher. Run from anywhere - always uses this script's own folder as the working
# directory, so arinsidej.jar/settings.ini next to it are found regardless of where you call it
# from. Any arguments you pass are forwarded as-is (e.g. ./run-arinsidej.sh -s myserver -l Demo
# -p mypass); with no arguments it falls back to -i settings.ini in this same folder.
#
# The BMC AR System Java API jars (arapi*.jar, arlogger*.jar) are NOT bundled. Drop them into the
# lib/ folder next to this script; every *.jar in there is added to the classpath. See
# lib/README.txt for what is needed and where to get it.

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/arinsidej.jar"
LIB_DIR="$SCRIPT_DIR/lib"

if [ ! -f "$JAR" ]; then
    echo "[ERR] $JAR not found. Put it next to this script - download a release build from" >&2
    echo "      https://github.com/ljlongwing/ARInsideJ/releases/latest or build it with: mvn -o package" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "[ERR] java not found on PATH. Install a JDK/JRE 17+ and try again." >&2
    exit 1
fi

# Build the classpath: the app jar plus every jar in lib/.
CP="$JAR"
ARAPI_FOUND=0
if [ -d "$LIB_DIR" ]; then
    for j in "$LIB_DIR"/*.jar; do
        [ -f "$j" ] || continue
        CP="$CP:$j"
        case "$(basename "$j")" in arapi*.jar) ARAPI_FOUND=1 ;; esac
    done
fi

if [ "$ARAPI_FOUND" -ne 1 ]; then
    echo "[ERR] No arapi*.jar found in $LIB_DIR" >&2
    echo "      ARInsideJ needs BMC's AR System Java API jars (arapi*.jar, arlogger*.jar)." >&2
    echo "      They are proprietary and not bundled. Copy them into:" >&2
    echo "          $LIB_DIR" >&2
    echo "      See $LIB_DIR/README.txt for every place they can be found." >&2
    exit 1
fi

cd "$SCRIPT_DIR"
if [ "$#" -eq 0 ]; then
    exec java -cp "$CP" arinside.Launch -i settings.ini
else
    exec java -cp "$CP" arinside.Launch "$@"
fi
