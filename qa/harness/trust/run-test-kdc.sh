#!/bin/bash
# ============================================================================
# run-test-kdc.sh  (task-kdc-kerberos-e2e, 2026-07-19)
#
# Stand up a real, wire-protocol-compliant test Kerberos KDC (Apache Kerby,
# pure Java -- https://directory.apache.org/kerby/) for validating
# net.jini.jeri.kerberos against a live KDC, without root or a container
# runtime (neither was available in the sandbox this was built in; Kerby
# implements the actual KRB5 ASN.1 wire protocol, so it is a real KDC from
# the client's point of view, not a mock).
#
# Usage:
#   qa/harness/trust/run-test-kdc.sh start   # (default)
#   qa/harness/trust/run-test-kdc.sh stop
#
# Env overrides:
#   REALM       default JGDMSQA.TEST
#   KDC_HOST    default 127.0.0.1
#   KDC_PORT    default 60088
#   WORKDIR     default ${TMPDIR:-/tmp}/jgdms-testkdc
#   PRINCIPALS  default "reggie test" (space-separated, realm appended
#               automatically) -- reggie (server) + test (tester/client) is
#               the minimum set for the reggie kerberos config set.
#
# Requires: a JDK on PATH (or $JAVA_HOME) with javac -- any modern JDK works
# for the KDC itself (Apache Kerby has no SecurityManager dependency). Maven
# is used once to resolve the kerb-simplekdc classpath (see pom.xml in this
# directory's testkdc/ subdirectory); after that first resolution it is
# cached under testkdc/target and mvn is not invoked again.
#
# Output: writes ${WORKDIR}/krb5.conf and ${WORKDIR}/aggregate.keytab --
# point run-reggie-kerberos-e2e.sh (or any other Kerberos client/server code)
# at those two files.
# ============================================================================
set -euo pipefail
TRUST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TESTKDC_DIR="$TRUST/testkdc"

REALM="${REALM:-JGDMSQA.TEST}"
KDC_HOST="${KDC_HOST:-127.0.0.1}"
KDC_PORT="${KDC_PORT:-60088}"
WORKDIR="${WORKDIR:-${TMPDIR:-/tmp}/jgdms-testkdc}"
PRINCIPALS="${PRINCIPALS:-reggie test}"
PIDFILE="$WORKDIR/testkdc.pid"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

if [ "${1:-start}" = "stop" ]; then
    if [ -f "$PIDFILE" ]; then
        kill "$(cat "$PIDFILE")" 2>/dev/null || true
        rm -f "$PIDFILE"
    fi
    echo "test KDC stopped"
    exit 0
fi

mkdir -p "$WORKDIR"

CP_FILE="$TESTKDC_DIR/target/kerby-classpath.txt"
if [ ! -f "$CP_FILE" ]; then
    echo "resolving Apache Kerby (kerb-simplekdc) classpath via maven (one-time) ..."
    mvn -q -f "$TESTKDC_DIR/pom.xml" dependency:build-classpath \
        -Dmdep.outputFile="$CP_FILE"
fi
CP="$(cat "$CP_FILE")"

CLASSES_DIR="$TESTKDC_DIR/target/classes"
SRC="$TESTKDC_DIR/src/main/java/org/apache/river/qa/harness/trust/testkdc/TestKdc.java"
if [ ! -f "$CLASSES_DIR/org/apache/river/qa/harness/trust/testkdc/TestKdc.class" ] \
   || [ "$SRC" -nt "$CLASSES_DIR/org/apache/river/qa/harness/trust/testkdc/TestKdc.class" ]; then
    mkdir -p "$CLASSES_DIR"
    "$JAVAC" -cp "$CP" -d "$CLASSES_DIR" "$SRC"
fi

PRINC_ARGS=()
for p in $PRINCIPALS; do
    PRINC_ARGS+=("$p@$REALM")
done

nohup "$JAVA" -cp "$CP:$CLASSES_DIR" \
    org.apache.river.qa.harness.trust.testkdc.TestKdc \
    "$REALM" "$KDC_HOST" "$KDC_PORT" "$WORKDIR" "$WORKDIR/aggregate.keytab" \
    "${PRINC_ARGS[@]}" \
    > "$WORKDIR/testkdc.log" 2>&1 < /dev/null &
echo $! > "$PIDFILE"

for _ in $(seq 1 100); do
    [ -f "$WORKDIR/READY" ] && break
    sleep 0.2
done
if [ ! -f "$WORKDIR/READY" ]; then
    echo "test KDC did not become ready; see $WORKDIR/testkdc.log" >&2
    exit 1
fi

echo "test KDC up: realm=$REALM host=$KDC_HOST port=$KDC_PORT (pid $(cat "$PIDFILE"))"
echo "  krb5.conf : $WORKDIR/krb5.conf"
echo "  keytab    : $WORKDIR/aggregate.keytab"
echo "  principals: ${PRINC_ARGS[*]}"
echo "  log       : $WORKDIR/testkdc.log"
echo "stop with: $0 stop"
