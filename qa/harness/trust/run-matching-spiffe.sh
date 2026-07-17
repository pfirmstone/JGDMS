#!/bin/bash
# ============================================================================
# run-matching-spiffe.sh  (Peter / Claude, 2026-06-25)
#
# Drive ONE qa outrigger "matching" test end-to-end over SPIFFE/TLS JERI, using
# the ambient Subject.processWorker() credential model + the mock SPIRE agents.
# Recreated durably here (in-repo) after a power-loss reboot wiped the original
# /tmp runner -- /tmp does NOT survive reboot, this dir does.
#
# What it does:
#   1. starts the per-role mock SPIRE agents (run-mock-spire.sh) on their UDS sockets,
#   2. runs a single matching .td under the `spiffe` config set with the DirtyChai JDK,
#   3. tees all output to a durable log and greps out the DIAG-CCA client-auth line,
#   4. stops the mock SPIRE agents.
#
# It does NOT build/stage jeri -- do that first (see header "STAGING" below).
#
# Usage (from anywhere):
#   qa/harness/trust/run-matching-spiffe.sh [TEST_TD]
#     TEST_TD defaults to org/apache/river/test/impl/outrigger/matching/WriteOneTakeRead.td
#   Extra env:
#     JAVA_HOME   run JDK (default: the DirtyChai image -- needs Subject.processWorker())
#     KEEP_SPIRE=1  leave the mock SPIRE agents running after the test (for re-runs)
#
# STAGING (do before running, when jeri source changed):
#   cd JGDMS/JGDMS
#   JAVA_HOME=<dirtychai> mvn -o -pl jgdms-platform,jgdms-jeri -Dmaven.test.skip=true package
#   cp jgdms-jeri/target/jgdms-jeri-4.0.0-SNAPSHOT.jar \
#      ../dist/target/JGDMS-4.0.0-SNAPSHOT/lib/jgdms-jeri-4.0.0-SNAPSHOT.jar
#   # (stage ONLY the jeri jar -- platform built under DirtyChai is tainted by the
#   #  --release 21 embedded-copy trap; net.jini.jeri.* is NOT embedded, so jeri is safe.)
# ============================================================================
set -uo pipefail

# --- locate repo dirs from this script's location (trust/ -> harness/ -> qa/ -> root) ---
TRUST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA="$(cd "$TRUST/../.." && pwd)"
ROOT="$(cd "$QA/.." && pwd)"

TEST_TD="${1:-org/apache/river/test/impl/outrigger/matching/WriteOneTakeRead.td}"
GITHUB="$(cd "$ROOT/.." && pwd)"   # DirtyChai sits beside JGDMS under .../GitHub
DIRTYCHAI="$GITHUB/DirtyChai/build/linux-x86_64-server-release/images/jdk"
export JAVA_HOME="${JAVA_HOME:-$DIRTYCHAI}"

LOGDIR="$ROOT/JGDMS/dist/target/matching-spiffe-logs"
mkdir -p "$LOGDIR"
RUNLOG="$LOGDIR/run.log"

echo "== run-matching-spiffe =="
echo "  JAVA_HOME : $JAVA_HOME"
echo "  test      : $TEST_TD"
echo "  runlog    : $RUNLOG"
"$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/  jdk: /'

# --- 1. mock SPIRE agents (serve each role's SVID on its UDS socket) ---
echo "-- starting mock SPIRE agents --"
JAVA_HOME="$JAVA_HOME" bash "$TRUST/run-mock-spire.sh" start

cleanup() {
    if [ "${KEEP_SPIRE:-0}" != "1" ]; then
        echo "-- stopping mock SPIRE agents --"
        bash "$TRUST/run-mock-spire.sh" stop || true
    else
        echo "-- KEEP_SPIRE=1: leaving mock SPIRE agents up --"
    fi
}
trap cleanup EXIT

# --- 2. run the single matching test under the spiffe config set ---
echo "-- running matching test under -Dharness.configs=spiffe --"
cd "$QA"
ant -Dharness.configs=spiffe -Drun.tests="$TEST_TD" run-tests 2>&1 | tee "$RUNLOG"
ANT_STATUS=${PIPESTATUS[0]}

# --- 3. surface the client-auth diagnostic ---
echo "============================================================"
echo "DIAG-CCA lines (client alias selection -- keyTypes / subjectNull):"
grep -rn "DIAG-CCA" "$RUNLOG" "$QA"/result 2>/dev/null | sed 's/^/  /' \
    || echo "  (none found -- check $RUNLOG and $QA/result)"
echo "ant run-tests exit status: $ANT_STATUS"
echo "============================================================"
exit "$ANT_STATUS"
