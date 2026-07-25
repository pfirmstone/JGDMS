#!/usr/bin/env bash
#
# Single-command parent: builds the JGDMS modules the showcase depends on from the
# CURRENT source tree, then runs all six wire-protocol-showcase demonstrations in
# sequence, then the automated checks, and prints a pass/fail summary.
# See run-demos.ps1 for the annotated version and for why demo2/demo5 are invoked
# as their own scripts (both launch two separate JVMs with different classpaths).
#
# Demonstration 4 (the cumulative-bomb refusal) only proves anything if the
# jgdms-der it runs against is built from the CURRENT source tree, so Step 0
# below builds and installs jgdms-der (and everything else the showcase and its
# sub-demos depend on) from source before any demo runs.
#
# Unlike the PowerShell version, bash already runs each invoked script (demo2/demo5's
# run.sh, which use `set -euo pipefail` and can exit non-zero) as its own child
# process, so no special isolation is needed here -- we just disable this script's
# own `set -e` around each demo so one failure doesn't abort the whole run, and
# capture $? immediately after.
set -uo pipefail

# Convert a Unix/Git-Bash path to a Windows path the native javac/java can
# resolve. Prefers cygpath -m (mixed C:/... form, safe from further Git-Bash
# mangling); falls back to a /c/foo -> C:/foo transform when cygpath is
# unavailable. Same helper as demo2-match-without-the-class/run.sh and
# demo5-filter-by-a-rule/run.sh -- see the note there: the Windows Java
# launcher uses ';' as its classpath separator, and Git Bash does not
# convert Unix-style paths buried inside a ';'-joined -cp string, so those
# entries would silently drop out of the classpath.
to_win() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        echo "$1" | sed -E 's#^/([a-zA-Z])/#\1:/#'
    fi
}

showcase="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
jgdms="$(dirname "$(dirname "$showcase")")"

results_names=()
results_pass=()
add_result() { results_names+=("$1"); results_pass+=("$2"); }

header() {
    echo
    echo "########################################################################"
    echo "# $1"
    echo "########################################################################"
}

# ---------------------------------------------------------------------------
# Step 0: build and install, from the current source tree, every module the
# showcase (jgdms-der, jgdms-platform) and its sub-demos (jgdms-lib-dl for
# demo2, jgdms-cel for demo5) depend on. One combined reactor build covers
# both -am closures; -Dmaven.test.skip=true keeps it to main-code compile+install.
# ---------------------------------------------------------------------------
# `clean install`, not plain `install`: a *failed* compile leaves whatever
# .class files an earlier successful build already produced sitting in
# target/classes untouched (maven-compiler-plugin does not wipe stale
# output on failure). Without `clean`, a module that fails to build here
# can silently leave a stale, pre-fix jar behind in the shared local repo
# (~/.m2) instead of failing loudly -- which is exactly how a demo that
# depends on a just-changed jgdms-der API can go on to fail downstream
# with a confusing ClassNotFoundException instead of a build error.
header "Step 0: building JGDMS modules from current source (jgdms-der, jgdms-platform, jgdms-lib-dl, jgdms-cel, ...)"
( cd "$jgdms" && mvn -pl services/outrigger/outrigger-dl,jgdms-cel -am \
    -Dmaven.test.skip=true -Dtidy.skip=true -Drat.skip=true clean install )
if [[ $? -ne 0 ]]; then
    echo "FATAL: module build failed; cannot run any demo against current source." >&2
    exit 1
fi
echo "Modules built and installed from current source."

# ---------------------------------------------------------------------------
# Build the main showcase module (demos 1, 3, 4, 6 live here) against the jars
# just installed above. `clean package`, for the same stale-target reason as
# Step 0 above: a compile failure here must not be masked by leftover
# .class files from an earlier, unrelated successful build.
# ---------------------------------------------------------------------------
header "Building the showcase module (demos 1, 3, 4, 6)"
( cd "$showcase" && mvn -q clean package -DskipTests )
if [[ $? -ne 0 ]]; then
    echo "FATAL: showcase build failed." >&2
    exit 1
fi

cp="$(to_win "$showcase/target/classes");$(to_win "$showcase/target/lib")/*"

# ---------------------------------------------------------------------------
# Demonstration 1: Same object, same bytes -- everywhere
# ---------------------------------------------------------------------------
header "Demonstration 1: Same object, same bytes -- everywhere"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo
rc=$?
if [[ $rc -eq 0 ]]; then
    echo
    echo "(Running it a second time in a fresh process -- the checksum is identical,"
    echo " because the bytes depend only on the value, not on the run.)"
    java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo | grep "checksum (SHA-256) of pass 1" | sed 's/^/  fresh process: /'
    rc=${PIPESTATUS[0]}
fi
if [[ $rc -eq 0 ]]; then add_result "Demo 1: Same object, same bytes" pass; else
    echo "DEMO 1 FAILED (exit $rc)"; add_result "Demo 1: Same object, same bytes" fail
fi

# ---------------------------------------------------------------------------
# Demonstration 2: Match a template without ever loading the class
# ---------------------------------------------------------------------------
header "Demonstration 2: Match a template without ever loading the class"
bash "$showcase/demo2-match-without-the-class/run.sh"
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Demo 2: Match without the class" pass; else
    echo "DEMO 2 FAILED (exit $rc)"; add_result "Demo 2: Match without the class" fail
fi

# ---------------------------------------------------------------------------
# Demonstration 3: The shape description travels once
# ---------------------------------------------------------------------------
header "Demonstration 3: The shape description travels once"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Demo 3: Schema sent once" pass; else
    echo "DEMO 3 FAILED (exit $rc)"; add_result "Demo 3: Schema sent once" fail
fi

# ---------------------------------------------------------------------------
# Demonstration 4: Feed it garbage, it stops politely
# ---------------------------------------------------------------------------
header "Demonstration 4: Feed it garbage, it stops politely"
# A deliberately small memory ceiling (128 MB) makes the point concrete: the
# expansion bomb would want gigabytes, yet the whole demonstration runs inside
# it without ever running out of memory -- but only because jgdms-der (built
# fresh in Step 0) actually contains the bomb-refusal fix.
java -Xmx128m -cp "$cp" au.net.zeus.jgdms.showcase.demo.HostileInputDemo
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Demo 4: Feed it garbage" pass; else
    echo "DEMO 4 FAILED (exit $rc)"; add_result "Demo 4: Feed it garbage" fail
fi

# ---------------------------------------------------------------------------
# Demonstration 5: Filter records by a written rule, without the class
# ---------------------------------------------------------------------------
header "Demonstration 5: Filter records by a written rule, without the class"
bash "$showcase/demo5-filter-by-a-rule/run.sh"
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Demo 5: Filter by a rule" pass; else
    echo "DEMO 5 FAILED (exit $rc)"; add_result "Demo 5: Filter by a rule" fail
fi

# ---------------------------------------------------------------------------
# Demonstration 6: Two different collection classes, one value, one encoding
# ---------------------------------------------------------------------------
header "Demonstration 6: Two different collection classes, one value, one encoding"
java -cp "$cp" au.net.zeus.jgdms.showcase.demo.CollectionEqualityDemo
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Demo 6: Collection equality" pass; else
    echo "DEMO 6 FAILED (exit $rc)"; add_result "Demo 6: Collection equality" fail
fi

# ---------------------------------------------------------------------------
# Automated checks (a build server can run these) -- not one of the 6 demos,
# but the existing regression coverage for all of them; kept as a bonus step.
# ---------------------------------------------------------------------------
header "Automated checks (mvn test)"
( cd "$showcase" && mvn -q test -DredirectTestOutputToFile=true )
rc=$?
if [[ $rc -eq 0 ]]; then add_result "Automated checks (mvn test)" pass; else
    echo "AUTOMATED CHECKS FAILED (exit $rc)"; add_result "Automated checks (mvn test)" fail
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
header "Summary"
any_failed=0
for i in "${!results_names[@]}"; do
    if [[ "${results_pass[$i]}" == "pass" ]]; then
        status="PASS"
    else
        status="FAIL"
        any_failed=1
    fi
    printf "  [%s] %s\n" "$status" "${results_names[$i]}"
done
echo
if [[ $any_failed -ne 0 ]]; then
    echo "RESULT: one or more demonstrations FAILED."
    exit 1
else
    echo "RESULT: all demonstrations and automated checks passed."
    exit 0
fi
