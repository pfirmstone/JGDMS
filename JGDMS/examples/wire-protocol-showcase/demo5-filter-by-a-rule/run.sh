#!/usr/bin/env bash
#
# Runs the "filter records by a written rule, without the class" demonstration.
# See run.ps1 for the annotated version. The record classes are compiled into one
# folder; a producer that HAS them writes readings to the wire, and a reader that does
# NOT have them on its classpath filters those readings by a written rule.
#
# Note: the Windows Java launcher uses ';' as its classpath separator. The Maven build
# emits Windows-style absolute paths (C:\...), while paths derived here under Git Bash
# are Unix-style (/c/...). Git Bash auto-converts standalone path arguments for native
# tools, but it does NOT touch paths buried inside a ';'-joined -cp string, so those
# Unix-style entries would silently drop out of the classpath. We therefore convert
# every path we control to Windows style (via cygpath) so the whole classpath is
# consistently resolvable by the Windows javac/java.
set -euo pipefail

# Convert a Unix/Git-Bash path to a Windows path the native javac/java can resolve.
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
cel="$jgdms/jgdms-cel"
cp_file="$module/target/cel-cp.txt"
marker="$cel/target/classes/au/net/zeus/jgdms/cel/verifier/CelVerifier.class"

if [[ ! -f "$marker" || ! -f "$cp_file" ]]; then
    echo "Building the rule-language library and its wire-format dependencies (offline)..."
    # -am builds jgdms-cel plus the modules it depends on (the wire-format libraries) from
    # the current source tree; -Dmaven.test.skip=true skips test compile+run so the offline
    # build is not blocked by any test-scoped dependency.
    ( cd "$jgdms" && mvn -o -q -pl jgdms-cel -am -Dmaven.test.skip=true \
        -Dtidy.skip=true -Drat.skip=true \
        install dependency:build-classpath "-Dmdep.outputFile=$cp_file" )
fi

cel_classes_win="$(to_win "$cel/target/classes")"
cp_lib="$cel_classes_win;$(cat "$cp_file")"

out_entry="$demo/out/entry"
out_app="$demo/out/app"
wire_file="$demo/out/records.wire"
rm -rf "$demo/out"; mkdir -p "$out_entry" "$out_app"

out_entry_win="$(to_win "$out_entry")"
out_app_win="$(to_win "$out_app")"
wire_file_win="$(to_win "$wire_file")"
src="$demo/src/au/net/zeus/jgdms/showcase/rule"
records_win="$(to_win "$src/records/WeatherReading.java") $(to_win "$src/records/SurveyObservation.java")"
app_win="$(to_win "$src/RuleWire.java") $(to_win "$src/Blocks.java") $(to_win "$src/Producer.java") $(to_win "$src/Reader.java")"

echo "Compiling the record classes and the two programs..."
javac -d "$out_entry_win" -cp "$cp_lib" $records_win
javac -d "$out_app_win" -cp "$cp_lib;$out_entry_win" $app_win

rmi="-Djava.rmi.server.RMIClassLoaderSpi=default"

echo
echo "==================== PRODUCER (record classes ARE present) ===================="
java $rmi -cp "$cp_lib;$out_app_win;$out_entry_win" au.net.zeus.jgdms.showcase.rule.Producer "$wire_file_win"

echo
echo "============ READER (record classes are NOT on the classpath) ================="
echo "reader classpath deliberately EXCLUDES the folder with the record classes."
set +e
java $rmi -cp "$cp_lib;$out_app_win" au.net.zeus.jgdms.showcase.rule.Reader "$wire_file_win"
reader_exit=$?
set -e

echo
if [[ $reader_exit -eq 0 ]]; then
    echo "RESULT: demonstration succeeded (reader exit code 0)."
else
    echo "RESULT: demonstration FAILED (reader exit code $reader_exit)."
    exit $reader_exit
fi
