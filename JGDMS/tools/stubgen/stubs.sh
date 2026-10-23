#!/bin/bash
# Regenerates, or checks, the compile-time stubs in jgdms-dirtychai-stubs.
#
#   tools/stubgen/stubs.sh regenerate [DIR|jrt:]   write the stubs
#   tools/stubgen/stubs.sh check      [DIR|jrt:]   exit 1 if the checked-in stubs differ
#
# DIR is the root of compiled DirtyChai classes (the directory that holds
# org/apache/river/api/security); the default is jrt:, the java.base of the
# JDK that runs this script, which must be the DirtyChai build of the commit in
# jgdms-dirtychai-stubs/DIRTYCHAI-COMMIT and must have java.lang.classfile (JDK 24+).
# Update DIRTYCHAI-COMMIT in the same commit that changes the stubs.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
mode=${1:?usage: stubs.sh regenerate|check [DIR|jrt:]}
src=${2:-jrt:}
pin=$(tr -d '[:space:]' < "$root/jgdms-dirtychai-stubs/DIRTYCHAI-COMMIT")
if [ "$src" = "jrt:" ]; then
    built=$(grep -o '^SOURCE="[^:]*:git:[0-9a-f]*' "${JAVA_HOME:?JAVA_HOME must name the DirtyChai JDK}/release" | sed 's/.*://' | head -1)
    # The JDK's release file names the commit it was built from (an abbreviated id).
    case "$pin" in
        "$built"*) ;;
        *) echo "warning: JAVA_HOME was built from $built, the stubs are pinned to $pin" >&2 ;;
    esac
fi
cd "$root"
case "$mode" in
    regenerate) exec java tools/stubgen/StubGen.java --classes "$src" --out jgdms-dirtychai-stubs/src/main/java ;;
    check)      exec java tools/stubgen/StubGen.java --classes "$src" --out jgdms-dirtychai-stubs/src/main/java --check ;;
    *) echo "unknown mode $mode" >&2; exit 2 ;;
esac
