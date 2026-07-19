#!/bin/bash
# ============================================================================
# run-reggie-kerberos-e2e.sh  (task-kdc-kerberos-e2e, 2026-07-19)
#
# Validates qa/harness/configs/kerberos/reggie/reggie.config's Kerberos JERI
# transport end-to-end against a real KDC (see run-test-kdc.sh):
#   - real JAAS Krb5LoginModule logins (server "reggie" + client "test"
#     Subjects) via the actual qa/harness/trust/kerberos.login used by the
#     QA harness, against a real KDC (AS-REQ/AS-REP, with pre-auth)
#   - the *unmodified* reggie.config's "exporter" component (real
#     KerberosServerEndpoint.getInstance(0) + AtomicILFactory +
#     BasicJeriExporter, exactly as configured), exporting a Registrar
#     ("org.apache.river.reggie.proxy.Registrar" -- the real reggie
#     client<->server wire protocol interface) test double
#   - a real client-side KerberosEndpoint call under
#     ClientAuthentication.YES + ServerAuthentication.YES + Integrity.YES +
#     ServerMinPrincipal(reggie) constraints (the same shape
#     test.config's reggiePreparer applies), i.e. a real mutual GSS-API
#     handshake (AP-REQ/AP-REP) followed by an authenticated RMI call
#   - a negative case: the same call with a ServerMinPrincipal the KDC never
#     issued the server a key for, which must be (and is) rejected
#
# See ReggieKerberosE2E.java for a documented, load-bearing finding
# surfaced by this driver: modern JDKs' Krb5LoginModule (useKeyTab=true +
# storeKey=true + a bound principal -- exactly what kerberos.login uses)
# stores a lazy javax.security.auth.kerberos.KeyTab credential rather than a
# javax.security.auth.kerberos.KerberosKey, which
# net.jini.jeri.kerberos.KerberosServerEndpoint.getKey() does not recognize
# -- so a KerberosServerEndpoint listen endpoint cannot be started via this
# exact JAAS login pattern on any JDK since ~8u40/9 without a workaround.
# This driver applies that workaround itself (does not patch JGDMS
# production code); the underlying gap is a real, separate finding.
#
# Prerequisites:
#   1. qa/harness/trust/run-test-kdc.sh must be running (start it first).
#   2. JAVA_HOME must point at a SecurityManager-capable JDK -- see
#      dirtychai-build memory / DirtyChai project. Vanilla OpenJDK 24+ has
#      SecurityManager machinery removed and net.jini.security.Security's
#      static init in this exact call path throws
#      "SecurityException: checking permissions is not supported"
#      (a JGDMS/JDK compatibility fact, unrelated to Kerberos).
#   3. The JGDMS maven reactor's "dist" module must be built so
#      JGDMS/dist/target/JGDMS-<version>/{lib,lib-dl} exist:
#        cd JGDMS/JGDMS && mvn -pl :dist -am -DskipTests install
#
# Usage:
#   JAVA_HOME=/path/to/dirtychai/build/*/images/jdk \
#     qa/harness/trust/run-reggie-kerberos-e2e.sh
#
# Env overrides (must match what run-test-kdc.sh was started with):
#   REALM, KDC_HOST, KDC_PORT, WORKDIR   -- see run-test-kdc.sh
#   JGDMS_DIST_DIR  default <repo>/JGDMS/dist/target/JGDMS-4.0.0-SNAPSHOT
#   DEBUG           set to 1 to enable -Dsun.security.krb5.debug=true
#                   -Dsun.security.jgss.debug=true (verbose wire-level proof)
# ============================================================================
set -euo pipefail
TRUST="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA_ROOT="$(cd "$TRUST/../.." && pwd)"
REPO_ROOT="$(cd "$QA_ROOT/.." && pwd)"
TESTKDC_DIR="$TRUST/testkdc"

REALM="${REALM:-JGDMSQA.TEST}"
WORKDIR="${WORKDIR:-${TMPDIR:-/tmp}/jgdms-testkdc}"
JGDMS_DIST_DIR="${JGDMS_DIST_DIR:-$REPO_ROOT/JGDMS/dist/target/JGDMS-4.0.0-SNAPSHOT}"

: "${JAVA_HOME:?set JAVA_HOME to a SecurityManager-capable JDK (see script header)}"
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"

if [ ! -f "$WORKDIR/READY" ]; then
    echo "test KDC not running / not ready (expected $WORKDIR/READY)." >&2
    echo "start it first: qa/harness/trust/run-test-kdc.sh" >&2
    exit 1
fi
if [ ! -d "$JGDMS_DIST_DIR/lib" ] || [ ! -d "$JGDMS_DIST_DIR/lib-dl" ]; then
    echo "JGDMS dist not built at $JGDMS_DIST_DIR (need lib/ and lib-dl/)." >&2
    echo "build it: cd JGDMS/JGDMS && mvn -pl :dist -am -DskipTests install" >&2
    exit 1
fi

JGDMS_CP="$(find "$JGDMS_DIST_DIR/lib" "$JGDMS_DIST_DIR/lib-dl" -iname '*.jar' | tr '\n' ':')"

CLASSES_DIR="$TESTKDC_DIR/target/classes"
SRC="$TESTKDC_DIR/src/main/java/org/apache/river/qa/harness/trust/testkdc/ReggieKerberosE2E.java"
if [ ! -f "$CLASSES_DIR/org/apache/river/qa/harness/trust/testkdc/ReggieKerberosE2E.class" ] \
   || [ "$SRC" -nt "$CLASSES_DIR/org/apache/river/qa/harness/trust/testkdc/ReggieKerberosE2E.class" ]; then
    mkdir -p "$CLASSES_DIR"
    "$JAVAC" -cp "$JGDMS_CP" -d "$CLASSES_DIR" "$SRC"
fi

REGGIE_CONFIG="$QA_ROOT/harness/configs/kerberos/reggie/reggie.config"
LOGIN_CONFIG="$TRUST/kerberos.login"

DEBUG_ARGS=()
if [ "${DEBUG:-0}" = "1" ]; then
    DEBUG_ARGS=(-Dsun.security.krb5.debug=true -Dsun.security.jgss.debug=true)
fi

exec "$JAVA" -cp "$JGDMS_CP:$CLASSES_DIR" \
    -Djava.security.krb5.conf="$WORKDIR/krb5.conf" \
    -Djavax.security.auth.useSubjectCredsOnly=false \
    -Dkeytab="$WORKDIR/aggregate.keytab" \
    -Dreggie="reggie@$REALM" -Dtest="test@$REALM" -Dphoenix="phoenix@$REALM" \
    -Dmahalo="mahalo@$REALM" -Doutrigger="outrigger@$REALM" -Dmercury="mercury@$REALM" \
    -Dnorm="norm@$REALM" -Dfiddler="fiddler@$REALM" -Dgroup="group@$REALM" \
    "${DEBUG_ARGS[@]}" \
    org.apache.river.qa.harness.trust.testkdc.ReggieKerberosE2E \
    "$REGGIE_CONFIG" "$LOGIN_CONFIG" "reggie@$REALM"
