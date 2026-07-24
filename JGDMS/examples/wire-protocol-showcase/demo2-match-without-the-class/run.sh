#!/usr/bin/env bash
#
# Runs the "match a template without ever loading the class" demonstration.
# See run.ps1 for the annotated version. The record class is compiled into one
# folder; a producer that HAS it writes records to the wire, and a matching server
# that does NOT have it on its classpath matches a template by comparing bytes.
#
# Note: the Windows Java launcher uses ';' as its classpath separator. The Maven
# build emits Windows-style absolute paths (C:\...), while paths derived here under
# Git Bash are Unix-style (/c/...). Git Bash auto-converts standalone path arguments
# for native tools, but it does NOT touch paths buried inside a ';'-joined -cp
# string, so those Unix-style entries would silently drop out of the classpath.
# We therefore convert every path we control to Windows style (via cygpath) so the
# whole classpath is consistently resolvable by the Windows javac/java.
set -euo pipefail

# Convert a Unix/Git-Bash path to a Windows path the native javac/java can resolve.
# Prefers cygpath -m (mixed C:/... form, safe from further Git-Bash mangling);
# falls back to a /c/foo -> C:/foo transform when cygpath is unavailable.
to_win() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        echo "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#'
    fi
}

demo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module="$(dirname "$demo")"
jgdms="$(dirname "$(dirname "$module")")"
outrigger_dl="$jgdms/services/outrigger/outrigger-dl"
cp_file="$module/target/outrigger-dl-cp.txt"
entry_class="$outrigger_dl/target/classes/org/apache/river/outrigger/proxy/EntryRep.class"

if [[ ! -f "$entry_class" || ! -f "$cp_file" ]]; then
    echo "Building the shared-space library (one module, offline)..."
    # -Dmaven.test.skip=true skips test COMPILE and run (unlike -DskipTests, which
    # still compiles test sources); the demo needs only main classes, and this keeps
    # the offline build from being blocked by any test-scoped dependency.
    ( cd "$jgdms" && mvn -o -q -pl services/outrigger/outrigger-dl -Dmaven.test.skip=true \
        -Dtidy.skip=true -Drat.skip=true \
        package dependency:build-classpath "-Dmdep.outputFile=$cp_file" )
fi

# All bash-derived paths converted to Windows style so they survive inside the
# ';'-joined -cp string alongside the (already Windows-style) Maven cp_file entries.
outrigger_classes_win="$(to_win "$outrigger_dl/target/classes")"
cp_lib="$outrigger_classes_win;$(cat "$cp_file")"
out_entry="$demo/out/entry"
out_app="$demo/out/app"
wire_file="$demo/out/records.wire"
rm -rf "$demo/out"; mkdir -p "$out_entry" "$out_app"

out_entry_win="$(to_win "$out_entry")"
out_app_win="$(to_win "$out_app")"
wire_file_win="$(to_win "$wire_file")"
src_entry_win="$(to_win "$demo/src/au/net/zeus/jgdms/showcase/entry/SensorReadingEntry.java")"
src_producer_win="$(to_win "$demo/src/au/net/zeus/jgdms/showcase/match/Producer.java")"
src_server_win="$(to_win "$demo/src/au/net/zeus/jgdms/showcase/match/Server.java")"

echo "Compiling the record class and the two programs..."
javac -d "$out_entry_win" -cp "$cp_lib" "$src_entry_win"
javac -d "$out_app_win" -cp "$cp_lib;$out_entry_win" \
    "$src_producer_win" \
    "$src_server_win"

rmi="-Djava.rmi.server.RMIClassLoaderSpi=default"

echo
echo "==================== PRODUCER (record class IS present) ===================="
java $rmi -cp "$cp_lib;$out_app_win;$out_entry_win" au.net.zeus.jgdms.showcase.match.Producer "$wire_file_win"

echo
echo "============ MATCHING SERVER (record class is NOT on the classpath) ========"
echo "server classpath deliberately EXCLUDES the folder with the record class."
echo
set +e
java $rmi -cp "$cp_lib;$out_app_win" au.net.zeus.jgdms.showcase.match.Server "$wire_file_win"
server_exit=$?
set -e

echo
if [[ $server_exit -eq 0 ]]; then
    echo "RESULT: demonstration succeeded (server exit code 0)."
else
    echo "RESULT: demonstration FAILED (server exit code $server_exit)."
    exit $server_exit
fi
