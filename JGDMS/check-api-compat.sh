#!/bin/bash
#
# check-api-compat.sh
#
# Binary-compatibility gate (synchronic axis; companion to check-serial-schema.sh).
# For each in-scope published artifact, diffs the current build against its last
# release (3.1.0 on Maven Central) with japicmp (public API, binary-incompatible
# only), then classifies each break against the accepted-breaks ledger
# (docs/JGDMS-API-Compatibility-Accepted-Breaks.md, via classify_breaks.py) and
# FAILS only on UNCLASSIFIED (candidate accidental) breaks. The intended/forced
# programs (Serializable removal, java.rmi.activation migration, MarshalledObject
# migration, DiscoveryV1/SecurityManager/RemotePolicy removal) are accepted, and
# serial-form changes are reported on the serial axis (check-serial-schema.sh),
# not here.
#
# Run after a full build/install (current jars in module target/ or ~/.m2).
# Needs network access to Maven Central for the baselines + japicmp (cached under
# target/api-compat/, override with API_COMPAT_CACHE).
#
# Exit 0 = no unclassified breaks; exit 1 = unclassified break(s) found.

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
CACHE="${API_COMPAT_CACHE:-$ROOT/target/api-compat}"
mkdir -p "$CACHE"
M="https://repo1.maven.org/maven2"
GROUP="au/net/zeus/jgdms"
BASELINE="3.1.0"
JAPICMP_VER="0.26.1"

# In-scope shared/contract artifacts that have a 3.1.0 baseline (the cross-loader
# linkage surface; see memory jgdms-api-compat-tooling). -dl service entries are
# tracked via the pre-AI tag + serial-schema gate, not here.
ARTIFACTS="jgdms-platform jgdms-lib jgdms-jeri jgdms-collections jgdms-activation jgdms-discovery-providers jgdms-lib-dl jgdms-url-integrity"

JAPICMP="$CACHE/japicmp-${JAPICMP_VER}.jar"
if [ ! -f "$JAPICMP" ]; then
    curl -fsSL -o "$JAPICMP" \
      "$M/com/github/siom79/japicmp/japicmp/${JAPICMP_VER}/japicmp-${JAPICMP_VER}-jar-with-dependencies.jar" \
      || { echo "ERROR: cannot fetch japicmp" >&2; exit 2; }
fi

current_jar() {   # $1 = artifactId -> path to current jar (target jar, ~/.m2, or freshly jarred classes)
    local art="$1" j
    j=$(find "$ROOT" -path "*/${art}/target/${art}-*.jar" ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | head -1)
    [ -z "$j" ] && j=$(find "$HOME/.m2/repository/$GROUP/$art" -name "${art}-*.jar" ! -name '*sources*' 2>/dev/null | head -1)
    if [ -z "$j" ]; then
        local cls; cls=$(find "$ROOT" -path "*/${art}/target/classes" -type d 2>/dev/null | head -1)
        [ -n "$cls" ] && { j="$CACHE/${art}-current.jar"; (cd "$cls" && jar cf "$j" .); }
    fi
    echo "$j"
}

COMBINED="$CACHE/combined-report.txt"; : > "$COMBINED"
for art in $ARTIFACTS; do
    old="$CACHE/${art}-${BASELINE}.jar"
    if [ ! -f "$old" ]; then
        curl -fsSL -o "$old" "$M/$GROUP/$art/$BASELINE/$art-$BASELINE.jar" 2>/dev/null \
            || { echo "  $art: no $BASELINE baseline on Central (skip)"; rm -f "$old"; continue; }
    fi
    new="$(current_jar "$art")"
    [ -n "$new" ] || { echo "  $art: no current jar (build/install first; skip)"; continue; }
    echo "### $art  ($BASELINE -> current)" >> "$COMBINED"
    "$JAVA" -jar "$JAPICMP" -o "$old" -n "$new" -a public -b --ignore-missing-classes >> "$COMBINED" 2>/dev/null
done

echo "== japicmp binary-incompat classification (3.1.0 -> current) =="
python3 "$ROOT/classify_breaks.py" "$COMBINED"
