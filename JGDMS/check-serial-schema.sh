#!/bin/bash
#
# check-serial-schema.sh
#
# Serialization-schema evolution gate (diachronic axis; companion to the
# binary-compat gate). Compares the current build against the committed
# serial-schema.golden:
#
#   - @SerialEntry (GATE): a change to a class's registrar type hash means
#     previously-stored entries become invisible (JGDMS-STD-005 RULE-7) -> FAIL.
#     The hash is computed by the runtime reggie ClassMapper, so it is identical
#     to the registrar's.
#   - @AtomicSerial (INFORMATIVE): serialForm() field add/remove/retype is
#     reported but never fails the build (the schema is designed to evolve via
#     defaulted GetArg.get).
#
# Run AFTER a full build: needs every module's target/classes plus reggie-dl,
# ASM and reggie's deps on the classpath. Regenerate the golden after an
# intentional change with:  (replace 'check --fail-on-change' with 'generate')
#
# Exit 0 = clean (or not built); exit 1 = breaking @SerialEntry hash change.

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
GOLDEN="$ROOT/serial-schema.golden"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
TOOL="$ROOT/tools/serial-schema-tracker/target/classes"

if [ ! -d "$TOOL" ]; then
    echo "serial-schema-tracker not built; run 'mvn -pl :serial-schema-tracker compile' first. Skipping." >&2
    exit 0
fi
if [ ! -f "$GOLDEN" ]; then
    echo "ERROR: golden not found: $GOLDEN" >&2
    exit 2
fi

CP="$TOOL"
SCAN=""
for d in $(find "$ROOT" -type d -path '*/target/classes' | sort); do
    CP="$CP:$d"
    SCAN="$SCAN --scan $d"
done
# third-party (ASM + reggie's runtime deps): prefer the built dist/lib, fall back to ~/.m2 ASM.
for j in $(find "$ROOT/dist/target" -path '*/lib/*.jar' 2>/dev/null); do CP="$CP:$j"; done
# Core asm artifact only (org/ow2/asm/asm/<ver>/asm-<ver>.jar), highest version --
# 'find ... | head -1' used to pick asm-analysis-4.0.jar, which cannot read modern
# class files: every class is skipped "unreadable" and the gate reports garbage.
for j in $(find "$HOME/.m2/repository/org/ow2/asm/asm" -maxdepth 2 -name 'asm-[0-9]*.jar' ! -name '*-sources*' ! -name '*-javadoc*' 2>/dev/null | sort -V | tail -1); do CP="$CP:$j"; done

exec "$JAVA" -cp "$CP" org.apache.river.tool.serial.SerialSchemaTracker \
     check --golden "$GOLDEN" $SCAN --fail-on-change
