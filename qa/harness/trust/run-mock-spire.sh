#!/bin/bash
# ============================================================================
# run-mock-spire.sh  (Peter / Claude, 2026-06-24)
#
# Launch per-role mock SPIRE agents for the JGDMS QA "spiffe" config set.
# Each agent serves that role's X.509-SVID over a per-role Unix domain socket,
# so each per-service VM's DirtyChai Subject.processWorker() acquires that
# role's ambient SPIFFE identity (one OS process = one identity).
#
# Sockets match qa/harness/configs/spiffe/configSet.properties:
#   reggie/mahalo/outrigger  -> per-service serverjvmargs
#   tester                   -> global vm arg (the test VM)
#
# Usage (run BEFORE the harness, from any dir):
#   JAVA_HOME=<jdk16+> qa/harness/trust/run-mock-spire.sh        # start
#   qa/harness/trust/run-mock-spire.sh stop                      # stop + cleanup
# ============================================================================
set -euo pipefail
TRUST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPIFFE="$TRUST/spiffe"
OUT="${TMPDIR:-/tmp}/mock-spire"
PIDFILE="$OUT/pids"
ROLES="reggie mahalo outrigger tester"

if [ "${1:-start}" = "stop" ]; then
    [ -f "$PIDFILE" ] && kill $(cat "$PIDFILE") 2>/dev/null || true
    rm -f "$PIDFILE"
    for r in $ROLES; do rm -f "/tmp/spire-$r.sock"; done
    echo "mock SPIRE agents stopped"
    exit 0
fi

: "${JAVA_HOME:?set JAVA_HOME to a JDK 16+ (DirtyChai or Zulu) with Unix-domain-socket support}"
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
mkdir -p "$OUT"
if [ ! -f "$OUT/MockSpireAgent.class" ] || [ "$TRUST/MockSpireAgent.java" -nt "$OUT/MockSpireAgent.class" ]; then
    "$JAVAC" -d "$OUT" "$TRUST/MockSpireAgent.java"
fi

TD="spiffe://test.jgdms.local"
# matching-test roles: reggie/mahalo/outrigger services + the tester client (svc/tester)
declare -A ID=( [reggie]="$TD/svc/reggie" [mahalo]="$TD/svc/mahalo" \
                [outrigger]="$TD/svc/outrigger" [tester]="$TD/svc/tester" )

: > "$PIDFILE"
for role in $ROLES; do
    sock="/tmp/spire-$role.sock"
    rm -f "$sock"
    "$JAVA" -cp "$OUT" MockSpireAgent \
        "$sock" "$SPIFFE/$role/svid.pem" "$SPIFFE/$role/svid_key.pem" "$SPIFFE/ca/ca.pem" "${ID[$role]}" \
        > "$OUT/$role.log" 2>&1 &
    echo $! >> "$PIDFILE"
    echo "  mock $role  ->  $sock  (${ID[$role]}, pid $!)"
done
echo "mock SPIRE agents up; logs in $OUT; stop with: $0 stop"
