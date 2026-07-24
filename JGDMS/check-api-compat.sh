#!/bin/bash
#
# check-api-compat.sh
#
# Binary-compatibility gate (synchronic axis; companion to check-serial-schema.sh).
# For each in-scope published artifact, diffs the current build against TWO
# baselines with japicmp (public API, binary-incompatible only), then classifies
# each break against the accepted-breaks ledger
# (docs/JGDMS-API-Compatibility-Accepted-Breaks.md, via classify_breaks.py) and
# FAILS only on UNCLASSIFIED (candidate accidental) breaks. The intended/forced
# programs (Serializable removal, java.rmi.activation migration, MarshalledObject
# migration, DiscoveryV1/SecurityManager/RemotePolicy removal) are accepted, and
# serial-form changes are reported on the serial axis (check-serial-schema.sh),
# not here.
#
# Dual baseline (T3, docs/SOW-Compat-Gate-CI-Enforcement.md sec 2.2):
#   1. RELEASE_BASELINE (default 3.1.0): the last released version, fetched from
#      Maven Central. Protects the released-API contract.
#   2. TRUNK_BASELINE_TAG (default baseline/pre-ai-agents): a git tag anchored to
#      a later trunk state, built locally once and cached. Catches API surface
#      that appeared *and* was reverted entirely within the RELEASE_BASELINE..HEAD
#      window, which is invisible to the release-only diff (observed case:
#      MapSerializer went public post-3.1.0, then back to package-private --
#      undetectable against 3.1.0, detectable against a trunk snapshot that saw
#      the intervening public state).
# Both env vars are overridable; wired from Maven properties by compat-gate/pom.xml
# (jgdms.previous-release.version, jgdms.trunk-baseline.tag) so a future release
# rolls the baseline forward via a property edit, not a script edit.
#
# Run after a full build/install (current jars in module target/ or ~/.m2).
# Needs network access to Maven Central for the japicmp jar + the release
# baseline, and (once, cached thereafter) local git+mvn to build the trunk
# baseline. Everything here is designed to WARN AND SKIP -- never hard-fail the
# build -- when a baseline is genuinely unreachable (offline/disconnected build);
# it only fails on a break it actually detected.
#
# Toolchain note for the trunk baseline: it is compiled from real historical
# source, which may predate JDK API changes the current default JAVA_HOME
# doesn't have (observed: JDK 11 compiles baseline/pre-ai-agents cleanly;
# JDK >=21 rejects it -- java.io.ObjectInputStream.GetField.get(String,Object)
# gained a declared ClassNotFoundException at some point after JDK 11, and
# this tag's @AtomicSerial deserialization constructors predate that). If the
# ambient JDK can't compile an old tag, set TRUNK_BASELINE_JAVA_HOME to a JDK
# that can; the nested build uses it for that step only (never affects the
# outer reactor build). Never fails the gate either way: an unbuildable trunk
# baseline just means that diff is skipped, same as an unreachable release jar.
#
# Exit 0 = no unclassified breaks (or nothing to compare); exit 1 = unclassified
# break(s) found in either baseline's diff.

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# Python 3 interpreter for classify_breaks.py -- overridable via PYTHON env var.
# On Windows the first python3/python on PATH is typically the Microsoft Store
# alias stub, which prints an install prompt and exits nonzero without running
# anything, so a candidate must prove it can execute code, not merely exist on
# PATH. Falls back to the py launcher, then the standard per-user install dir.
# A missing interpreter is a hard ERROR, not warn-and-skip: without
# classify_breaks.py the gate cannot classify anything and a green result
# would be vacuous.
PYTHON="${PYTHON:-}"
if [ -z "$PYTHON" ]; then
    for cand in python3 python py; do
        if "$cand" -c 'import sys' >/dev/null 2>&1; then PYTHON="$cand"; break; fi
    done
fi
if [ -z "$PYTHON" ] && [ -n "${LOCALAPPDATA:-}" ] && command -v cygpath >/dev/null 2>&1; then
    for cand in "$(cygpath "$LOCALAPPDATA")"/Programs/Python/Python3*/python.exe; do
        if [ -x "$cand" ] && "$cand" -c 'import sys' >/dev/null 2>&1; then PYTHON="$cand"; break; fi
    done
fi
if [ -z "$PYTHON" ]; then
    echo "ERROR: no working Python 3 interpreter found (needed for classify_breaks.py); set PYTHON" >&2
    exit 1
fi
CACHE="${API_COMPAT_CACHE:-$ROOT/target/api-compat}"
mkdir -p "$CACHE"
M="https://repo1.maven.org/maven2"
GROUP="au/net/zeus/jgdms"

# Release baseline -- parameterized (was hardcoded "3.1.0"). Override via env
# RELEASE_BASELINE (wired from Maven property jgdms.previous-release.version).
BASELINE="${RELEASE_BASELINE:-3.1.0}"

# Trunk baseline tag -- override via env TRUNK_BASELINE_TAG (wired from Maven
# property jgdms.trunk-baseline.tag). Sanitized for use in cache filenames.
TRUNK_BASELINE_TAG="${TRUNK_BASELINE_TAG:-baseline/pre-ai-agents}"
TRUNK_BASELINE_NAME="$(echo "$TRUNK_BASELINE_TAG" | tr '/' '-')"

JAPICMP_VER="0.26.1"

# In-scope shared/contract artifacts that have a 3.1.0 baseline (the cross-loader
# linkage surface; see memory jgdms-api-compat-tooling). -dl service entries are
# tracked via the pre-AI tag + serial-schema gate, not here.
ARTIFACTS="jgdms-platform jgdms-lib jgdms-jeri jgdms-collections jgdms-activation jgdms-discovery-providers jgdms-lib-dl jgdms-url-integrity"

JAPICMP="$CACHE/japicmp-${JAPICMP_VER}.jar"
if [ ! -f "$JAPICMP" ]; then
    curl -fsSL -o "$JAPICMP" \
      "$M/com/github/siom79/japicmp/japicmp/${JAPICMP_VER}/japicmp-${JAPICMP_VER}-jar-with-dependencies.jar" \
      || { echo "WARN: cannot fetch japicmp ${JAPICMP_VER} (offline?) -- skipping api-compat gate this run" >&2; rm -f "$JAPICMP"; exit 0; }
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

# T3: build (once, cached) the jars for the trunk-baseline tag. Mirrors the
# release baseline's fetch-and-cache pattern: instead of downloading a release
# jar from Central, check out the tag into an isolated git worktree, build just
# the in-scope modules with an isolated local repo (never touches the real
# ~/.m2 or the caller's checkout/branch), and cache the resulting jars under
# $CACHE exactly like the release-baseline jars. Never fails the gate: any
# failure here just means the trunk-baseline diff is skipped for this run (the
# release-baseline diff still runs); it is retried on the next invocation since
# no ".built" marker is written on failure.
build_trunk_baseline() {
    local marker="$CACHE/trunk-baseline-${TRUNK_BASELINE_NAME}.built"
    [ -f "$marker" ] && return 0
    command -v git >/dev/null 2>&1 || { echo "  trunk-baseline ($TRUNK_BASELINE_TAG): git not found (skip)"; return 1; }
    local gitroot; gitroot="$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null)"
    [ -n "$gitroot" ] || { echo "  trunk-baseline ($TRUNK_BASELINE_TAG): not a git checkout (skip)"; return 1; }
    # git on Windows prints C:/-style paths; renormalize through the shell so
    # "realpath --relative-to" below can relativize $ROOT (POSIX-style) against
    # it. Harmless no-op on Linux. Without this, relsub comes back absolute,
    # $sub misses, and the nested build runs at the worktree toplevel (no pom).
    gitroot="$(cd "$gitroot" && pwd)"
    git -C "$gitroot" rev-parse -q --verify "refs/tags/$TRUNK_BASELINE_TAG" >/dev/null 2>&1 \
        || { echo "  trunk-baseline: tag '$TRUNK_BASELINE_TAG' not found (skip)"; return 1; }

    # IMPORTANT: the worktree must live OUTSIDE $ROOT's directory tree. Maven's
    # .mvn/maven.config discovery walks up from the build's cwd looking for the
    # nearest ancestor .mvn directory; if the worktree were nested under
    # $ROOT (e.g. under $ROOT/target/...), it would find *this* checkout's
    # .mvn/maven.config (which may postdate the tag, e.g. a "-s .mvn/settings.xml"
    # entry added after $TRUNK_BASELINE_TAG was cut) and resolve its relative
    # paths against the wrong tree, breaking the nested build. A system temp dir
    # has no such ancestor.
    local wt; wt="$(mktemp -d "${TMPDIR:-/tmp}/jgdms-trunk-baseline-XXXXXX")"
    if ! git -C "$gitroot" worktree add --detach "$wt" "refs/tags/$TRUNK_BASELINE_TAG" \
            >"$CACHE/trunk-baseline-checkout.log" 2>&1; then
        echo "  trunk-baseline: cannot check out '$TRUNK_BASELINE_TAG' (skip; see $CACHE/trunk-baseline-checkout.log)"
        rmdir "$wt" 2>/dev/null
        return 1
    fi

    # This script's own directory may be nested one level below the git
    # toplevel (monorepo layout: <toplevel>/JGDMS/check-api-compat.sh). Find the
    # equivalent module root inside the worktree rather than assuming a fixed depth.
    local relsub sub
    relsub="$(realpath --relative-to="$gitroot" "$ROOT")"
    sub="$wt/$relsub"
    [ -f "$sub/pom.xml" ] || sub="$wt"

    # jgdms-marshal-delegate-processor is an annotation-processor-only module on
    # current trunk: the root pom wires it into every module's compile step via
    # <annotationProcessorPaths>, which is plugin config, not a reactor
    # <dependency> -- so plain "-am" won't pull it in, and compilation of any
    # in-scope artifact fails with "Resolution of annotationProcessorPath
    # dependencies failed". Add it explicitly when the checked-out tag has it
    # (older tags, e.g. baseline/pre-ai-agents, predate this processor and
    # don't need/have it -- -pl-ing a nonexistent module would fail the whole
    # build, so only add it if present).
    local pl="" a
    [ -d "$sub/jgdms-marshal-delegate-processor" ] && pl=":marshal-delegate-processor,"
    for a in $ARTIFACTS; do pl="$pl,:$a"; done
    pl="$(echo "$pl" | sed 's/^,*//; s/,,*/,/g')"

    # -Ddependency-check.skip: the tag's own build wires OWASP dependency-check
    # (network CVE-database fetch) into a few module poms; irrelevant to -- and
    # liable to rate-limit/fail -- a throwaway jar-only baseline build here.
    if ( cd "$sub" \
            && export JAVA_HOME="${TRUNK_BASELINE_JAVA_HOME:-${JAVA_HOME:-}}" \
            && [ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"; \
            echo "== nested build: cwd=$(pwd) pl=$pl JAVA_HOME=${JAVA_HOME:-<unset>}"; \
            mvn -q -B -DskipTests -Dsurefire.redirectTestOutputToFile=true \
            -Ddependency-check.skip=true \
            -Dmaven.repo.local="$CACHE/trunk-baseline-m2" -pl "$pl" -am install ) \
            >"$CACHE/trunk-baseline-build.log" 2>&1; then
        for a in $ARTIFACTS; do
            j=$(find "$sub" -path "*/${a}/target/${a}-*.jar" ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | head -1)
            [ -n "$j" ] && cp "$j" "$CACHE/${a}-${TRUNK_BASELINE_NAME}.jar"
        done
        touch "$marker"
    else
        echo "  trunk-baseline: build of '$TRUNK_BASELINE_TAG' failed (skip; see $CACHE/trunk-baseline-build.log)"
    fi
    git -C "$gitroot" worktree remove --force "$wt" >/dev/null 2>&1
    rm -rf "$wt"
}
build_trunk_baseline || true

RELEASE_REPORT="$CACHE/combined-report-release.txt"; : > "$RELEASE_REPORT"
TRUNK_REPORT="$CACHE/combined-report-trunk.txt"; : > "$TRUNK_REPORT"

for art in $ARTIFACTS; do
    new="$(current_jar "$art")"
    [ -n "$new" ] || { echo "  $art: no current jar (build/install first; skip)"; continue; }

    # (i) release baseline -- unreachable is a per-artifact skip, not a failure.
    old="$CACHE/${art}-${BASELINE}.jar"
    if [ ! -f "$old" ]; then
        curl -fsSL -o "$old" "$M/$GROUP/$art/$BASELINE/$art-$BASELINE.jar" 2>/dev/null \
            || { echo "  $art: no $BASELINE baseline on Central (skip)"; rm -f "$old"; }
    fi
    if [ -f "$old" ]; then
        echo "### $art  ($BASELINE -> current)" >> "$RELEASE_REPORT"
        "$JAVA" -jar "$JAPICMP" -o "$old" -n "$new" -a public -b --ignore-missing-classes >> "$RELEASE_REPORT" 2>/dev/null
    fi

    # (ii) trunk baseline -- likewise a per-artifact skip if the tag build didn't
    # produce a jar for this artifact.
    told="$CACHE/${art}-${TRUNK_BASELINE_NAME}.jar"
    if [ -f "$told" ]; then
        echo "### $art  ($TRUNK_BASELINE_TAG -> current)" >> "$TRUNK_REPORT"
        "$JAVA" -jar "$JAPICMP" -o "$told" -n "$new" -a public -b --ignore-missing-classes >> "$TRUNK_REPORT" 2>/dev/null
    else
        echo "  $art: no $TRUNK_BASELINE_TAG baseline available (skip trunk-baseline diff)"
    fi
done

echo "== japicmp binary-incompat classification ($BASELINE -> current) =="
"$PYTHON" "$ROOT/classify_breaks.py" "$RELEASE_REPORT"; rc1=$?
echo
echo "== japicmp binary-incompat classification ($TRUNK_BASELINE_TAG -> current) =="
"$PYTHON" "$ROOT/classify_breaks.py" "$TRUNK_REPORT"; rc2=$?

rc=0
[ "$rc1" -ne 0 ] && rc=1
[ "$rc2" -ne 0 ] && rc=1
exit $rc
