#!/bin/sh
# Runs the policy-overhead benchmarks. Use a DirtyChai JDK.
#
# 1. Generate the policy once (DirtyChai's polpAudit records the permissions
#    the SecurityManager runs need and writes conf/generated.policy):
#      JAVA_HOME=/path/to/dirtychai/jdk BENCH_MODE=audit ./run.sh
#    Review conf/generated.policy before using it.
#
# 2. Measure:
#      JAVA_HOME=/path/to/dirtychai/jdk ./run.sh -rf json -rff results.json
#    Arguments are passed to JMH (try -h).
#
# BENCH_POLICY=stock runs the SecurityManager forks with the JDK's standard
# PolicyFile instead of ConcurrentPolicyFile.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
HARNESS="$HERE/harness/target/benchmarks.jar"
WORKLOAD="$HERE/harness/target/lib/jgdms-bench-workload.jar"
for f in "$HARNESS" "$WORKLOAD"; do
    [ -f "$f" ] || { echo "Missing $f: run 'mvn -f $HERE/pom.xml package' first" >&2; exit 1; }
done
case "${BENCH_POLICY:-dirtychai}" in
    dirtychai) SECURITY=dirtychai.security; POLICY_CLASS=au.zeus.jdk.authorization.policy.ConcurrentPolicyFile ;;
    stock)     SECURITY=stock.security;     POLICY_CLASS=sun.security.provider.PolicyFile ;;
    *) echo "BENCH_POLICY must be dirtychai or stock" >&2; exit 1 ;;
esac
GENERATED="$HERE/conf/generated.policy"
if [ -f "$GENERATED" ]; then POLICY_FILE="$GENERATED"; else POLICY_FILE="$HERE/conf/bench.policy"; fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# The host JVM's -D options are inherited by every JMH fork; each benchmark
# class then appends its own SecurityManager / executor options.
jmh() {
    "$JAVA" \
        "-Djgdms.bench.harness.url=file:$HARNESS" \
        "-Djgdms.bench.workload.url=file:$WORKLOAD" \
        "-Djgdms.bench.harness.path=$HARNESS" \
        "-Djgdms.bench.workload.path=$WORKLOAD" \
        "-Djava.security.policy==$POLICY_FILE" \
        "-Djava.security.properties=$HERE/conf/$SECURITY" \
        "-Djgdms.bench.expectedPolicy=$POLICY_CLASS" \
        -Djmh.link.address=127.0.0.1 \
        -Djgdms.bench.p0=value0 -Djgdms.bench.p1=value1 -Djgdms.bench.p2=value2 \
        -Djgdms.bench.p3=value3 -Djgdms.bench.p4=value4 -Djgdms.bench.p5=value5 \
        -Djgdms.bench.p6=value6 -Djgdms.bench.p7=value7 \
        -cp "$HARNESS:$WORKLOAD" org.openjdk.jmh.Main "$@"
}

if [ "${BENCH_MODE:-run}" = audit ]; then
    # Seed with the hand-written grants so polpAudit only appends what is missing.
    [ -f "$GENERATED" ] || cp "$HERE/conf/bench.policy" "$GENERATED"
    POLICY_FILE="$GENERATED"
    # polpAudit writes these properties in place of the matching paths, so the
    # generated grants stay portable: codeBase "${jgdms.bench.workload.url}",
    # FilePermission "${jgdms.bench.workload.path}".
    PATHS="$HERE/conf/audit-paths.properties"
    printf 'jgdms.bench.harness.url=%s\njgdms.bench.workload.url=%s\njgdms.bench.harness.path=%s\njgdms.bench.workload.path=%s\n' \
        "file:$HARNESS" "file:$WORKLOAD" "$HARNESS" "$WORKLOAD" > "$PATHS"
    AUDIT="-Djava.security.manager=polpAudit -DpolpAudit.path.properties=$PATHS -Djgdms.bench.audit=true"
    jmh 'PolicyOverheadSmOn\.' -f 1 -wi 1 -w 200ms -i 1 -r 200ms -jvmArgsAppend "$AUDIT"
    jmh 'PolicyOverheadVirtualSmOn\.' -f 1 -wi 1 -w 200ms -i 1 -r 200ms -jvmArgsAppend "$AUDIT -Djmh.executor=VIRTUAL"
    # The diagnostic split reaches private JDK methods; record its grants too.
    jmh 'ContextCaptureSplitPlatform\.' -f 1 -wi 1 -w 200ms -i 1 -r 200ms -p extraFrames=0 -jvmArgsAppend "$AUDIT"
    jmh 'ContextCaptureSplitVirtual\.' -f 1 -wi 1 -w 200ms -i 1 -r 200ms -p extraFrames=0 -jvmArgsAppend "$AUDIT -Djmh.executor=VIRTUAL"
    echo
    echo "Wrote $GENERATED. Review it: every grant in it is one the benchmarks used."
    echo "Then run without BENCH_MODE to measure."
    exit 0
fi

echo "# jgdms-bench: policy file $POLICY_FILE"
jmh "$@"
