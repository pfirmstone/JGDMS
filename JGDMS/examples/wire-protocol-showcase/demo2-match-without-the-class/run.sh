#!/usr/bin/env bash
#
# Runs the "match a template without ever loading the class" demonstration.
# See run.ps1 for the annotated version. The record class is compiled into one
# folder; a producer that HAS it writes records to the wire, and a matching server
# that does NOT have it on its classpath matches a template by comparing bytes.
#
# Note: this uses the Windows Java launcher's ';' classpath separator, matching the
# absolute paths produced by the Maven build on Windows.
set -euo pipefail

demo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module="$(dirname "$demo")"
jgdms="$(dirname "$(dirname "$module")")"
outrigger_dl="$jgdms/services/outrigger/outrigger-dl"
cp_file="$module/target/outrigger-dl-cp.txt"
entry_class="$outrigger_dl/target/classes/org/apache/river/outrigger/proxy/EntryRep.class"

if [[ ! -f "$entry_class" || ! -f "$cp_file" ]]; then
    echo "Building the shared-space library (one module, offline)..."
    ( cd "$jgdms" && mvn -o -q -pl services/outrigger/outrigger-dl -DskipTests \
        -Dtidy.skip=true -Drat.skip=true \
        package dependency:build-classpath "-Dmdep.outputFile=$cp_file" )
fi

cp_lib="$outrigger_dl/target/classes;$(cat "$cp_file")"
out_entry="$demo/out/entry"
out_app="$demo/out/app"
wire_file="$demo/out/records.wire"
rm -rf "$demo/out"; mkdir -p "$out_entry" "$out_app"

echo "Compiling the record class and the two programs..."
javac -d "$out_entry" -cp "$cp_lib" "$demo/src/au/net/zeus/jgdms/showcase/entry/SensorReadingEntry.java"
javac -d "$out_app" -cp "$cp_lib;$out_entry" \
    "$demo/src/au/net/zeus/jgdms/showcase/match/Producer.java" \
    "$demo/src/au/net/zeus/jgdms/showcase/match/Server.java"

rmi="-Djava.rmi.server.RMIClassLoaderSpi=default"

echo
echo "==================== PRODUCER (record class IS present) ===================="
java $rmi -cp "$cp_lib;$out_app;$out_entry" au.net.zeus.jgdms.showcase.match.Producer "$wire_file"

echo
echo "============ MATCHING SERVER (record class is NOT on the classpath) ========"
echo "server classpath deliberately EXCLUDES the folder with the record class."
echo
set +e
java $rmi -cp "$cp_lib;$out_app" au.net.zeus.jgdms.showcase.match.Server "$wire_file"
server_exit=$?
set -e

echo
if [[ $server_exit -eq 0 ]]; then
    echo "RESULT: demonstration succeeded (server exit code 0)."
else
    echo "RESULT: demonstration FAILED (server exit code $server_exit)."
    exit $server_exit
fi
