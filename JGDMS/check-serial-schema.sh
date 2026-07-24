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
#   - @AtomicSerial (CLASSIFIED): serialForm() field drift is classified per
#     field:
#       ADD     -> informative (schema evolves via defaulted GetArg.get;
#                  new fields read as absent/default from old streams)
#       RETYPE  -> GATE FAIL (typed GetArg.get of streams written by earlier
#                  versions breaks -- the break class that forced the
#                  DerThrowableForm twin-class approach instead of retyping
#                  ThrowableSerializer in place)
#       REMOVE  -> GATE FAIL (readers requiring the field break; a rename
#                  classifies as REMOVE+ADD and therefore gates)
#       REORDER -> informative (fields are read by name)
#     Whole-class disappearance stays informative (indistinguishable from a
#     module that was not built in this reactor run).
#
# Run AFTER a full build: needs every module's target/classes plus reggie-dl,
# ASM and reggie's deps on the classpath.
#
# Override for an INTENTIONAL breaking change: regenerate the golden from a
# FULL build and commit it with the migration rationale:
#
#     ./check-serial-schema.sh --regenerate
#
# Exit 0 = clean (or not built); exit 1 = breaking @SerialEntry hash change,
# or @AtomicSerial serialForm() field RETYPE/REMOVE.

set -u
MODE="check"; MODE_ARGS="--fail-on-change"
if [ "${1:-}" = "--regenerate" ]; then MODE="generate"; MODE_ARGS=""; fi
ROOT="$(cd "$(dirname "$0")" && pwd)"
GOLDEN="$ROOT/serial-schema.golden"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
TOOL="$ROOT/tools/serial-schema-tracker/target/classes"

if [ ! -d "$TOOL" ]; then
    echo "serial-schema-tracker not built; run 'mvn -pl :serial-schema-tracker compile' first. Skipping." >&2
    exit 0
fi
if [ "$MODE" = "check" ] && [ ! -f "$GOLDEN" ]; then
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
     "$MODE" --golden "$GOLDEN" $SCAN $MODE_ARGS
