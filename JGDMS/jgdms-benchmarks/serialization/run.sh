#!/bin/sh
# Runs the serialization-format benchmarks (JOSS vs DER). Use a DirtyChai JDK
# (or any JDK <= 23 with the SecurityManager permitted) for the JERI tier.
#
#   JAVA_HOME=/path/to/dirtychai/jdk ./run.sh -rf json -rff results.json
#   python3 summarise.py results.json
#
# Arguments are passed to JMH (try -h). Useful ones:
#   "CodecBenchmark.*"     only the codec tier
#   "JeriBenchmark.*"      only the JERI tier
#   -t 4                   four concurrent callers (throughput rather than latency)
#   -prof gc               allocation per operation
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
JAR="$HERE/target/serialization-benchmarks.jar"
[ -f "$JAR" ] || { echo "Missing $JAR: run 'mvn -f $HERE/../pom.xml package' first" >&2; exit 1; }
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "== Wire sizes (bytes per object in a fresh stream)"
"$JAVA" -cp "$JAR" au.net.zeus.jgdms.bench.serial.WireSize
echo
echo "== JMH"
exec "$JAVA" -Djmh.link.address=127.0.0.1 -jar "$JAR" -foe true "$@"
