#!/usr/bin/env bash
#
# demo7 - "filter records server-side, by a value rule, without the class".
#
# Starts a LIVE transient Outrigger space + transient Mahalo transaction manager
# in a SERVER JVM whose classpath deliberately OMITS the entry class
# WeatherReading, then runs a CLIENT JVM (which HAS WeatherReading) that authors a
# CEL value predicate and drives filtered space operations. The server proves,
# class-free and server-side, that the predicate was evaluated fail-closed and
# confused-deputy-safe. See run.ps1 for the annotated PowerShell parity version.
#
# Classpath note (same as demo5): the Windows Java launcher uses ';' as its
# classpath separator and Git Bash does not convert Unix paths buried inside a
# ';'-joined -cp string, so every path we control is converted to Windows style
# via cygpath.
set -euo pipefail

to_win() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        echo "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#'
    fi
}

demo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module="$(dirname "$demo")"                       # wire-protocol-showcase
jgdms="$(dirname "$(dirname "$module")")"         # JGDMS

# Modules whose freshly-compiled target/classes we prepend (they win over the
# installed jars, so a local edit to FilterEval/FilterAdmission is exercised).
mods=(
    services/outrigger/outrigger-service
    services/outrigger/outrigger-dl
    services/outrigger/outrigger-cel-authoring
    services/mahalo/mahalo-service
    services/mahalo/mahalo-dl
    jgdms-lib-dl
    jgdms-cel
    jgdms-der
    jgdms-rmi-tls
    service-starter
    jgdms-discovery-providers
)
# Roots whose dependency:build-classpath union covers every third-party and
# sibling dependency the two JVMs need.
roots=(
    services/outrigger/outrigger-service
    services/mahalo/mahalo-service
    services/outrigger/outrigger-cel-authoring
    jgdms-rmi-tls
    service-starter
)

marker="$jgdms/services/outrigger/outrigger-service/target/classes/org/apache/river/outrigger/FilterAdmission.class"
cpdir="$demo/target"; mkdir -p "$cpdir"

if [[ ! -f "$marker" ]]; then
    echo "Building the reactor modules (offline, tests skipped)..."
    ( cd "$jgdms" && mvn -o -q -pl "$(IFS=,; echo "${mods[*]}")" -am \
        -Dmaven.test.skip=true -Dtidy.skip=true -Drat.skip=true install )
fi

# Assemble the dependency classpath (union of the roots), once.
depcp="$cpdir/dep-cp.txt"
if [[ ! -f "$depcp" ]]; then
    : > "$depcp"
    for r in "${roots[@]}"; do
        ( cd "$jgdms" && mvn -o -q -pl "$r" dependency:build-classpath \
            "-Dmdep.outputFile=$cpdir/one-cp.txt" )
        printf '%s;' "$(cat "$cpdir/one-cp.txt")" >> "$depcp"
    done
fi

# Prepend the fresh module classes to the dependency classpath.
libcp=""
for m in "${mods[@]}"; do libcp+="$(to_win "$jgdms/$m/target/classes");"; done
cp_lib="$libcp$(cat "$depcp")"

out_entry="$demo/out/entry"
out_app="$demo/out/app"
rm -rf "$demo/out"; mkdir -p "$out_entry" "$out_app"

out_entry_win="$(to_win "$out_entry")"
out_app_win="$(to_win "$out_app")"
ocfg_win="$(to_win "$demo/configs/outrigger.config")"
mcfg_win="$(to_win "$demo/configs/mahalo.config")"

src="$demo/src/au/net/zeus/jgdms/showcase/pushdown"
# out/entry = the entry classes AND the client logic that uses them; loaded by
# the CHILD class loader that the orchestrator creates. out/app = the
# orchestrator, whose loader deliberately does NOT have out/entry, so
# WeatherReading is provably absent from the space's class loader.
entry_win="$(to_win "$src/records/WeatherReading.java") $(to_win "$src/records/NorthStationReading.java") $(to_win "$src/ClientLogic.java")"
app_win="$(to_win "$src/Server.java")"

echo "Compiling the entry+client classes (out/entry) and the orchestrator (out/app)..."
javac -d "$out_entry_win" -cp "$cp_lib" $entry_win
javac -d "$out_app_win" -cp "$cp_lib" $app_win

rmi="-Djava.rmi.server.RMIClassLoaderSpi=default"
pkg="au.net.zeus.jgdms.showcase.pushdown"

echo
echo "Running demo7 (one JVM; the space's app loader OMITS WeatherReading, a child"
echo "loader over out/entry supplies it to the client logic)..."
set +e
# The orchestrator's classpath EXCLUDES out/entry -> WeatherReading absent from
# the space's loader; out/entry is passed as an argument for the child loader.
java $rmi -cp "$cp_lib;$out_app_win" "$pkg.Server" \
    "$out_entry_win" "$ocfg_win" "$mcfg_win"
exit_code=$?
set -e

echo
echo "exit code = $exit_code"
if [[ $exit_code -eq 0 ]]; then
    echo "RESULT: demo7 succeeded (exit code 0)."
    exit 0
else
    echo "RESULT: demo7 FAILED (exit code $exit_code)."
    exit 1
fi
