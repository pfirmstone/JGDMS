#!/bin/bash
#
# check-spiffe-policy-no-broad-grants.sh
#
# Regression guard for the 2026-07-20 fix that removed unqualified
# AllPermission / AuthPermission("*") / RuntimePermission("*") grants from
# the QA harness's "spiffe" config-set policy files.
#
# Why: AdminPrincipalAuthenticator (jgdms-pref-class-loader/.../loader/
# isolation/AdminPrincipalAuthenticator.java) fails closed unless a
# SecurityManager is installed AND the deployed policy denies
# AuthPermission("callAs"), AuthPermission("doAs") and
# RuntimePermission("setSecurityManager") to hosted/business protection
# domains. A policy grant block with no "codebase" and no "principal"
# qualifier applies to EVERY protection domain in the JVM, including
# hosted/business code -- so an unqualified grant of AllPermission (which
# implies all three) or of AuthPermission("*")/RuntimePermission("*")
# (which each imply one or more of them) silently reopens that gap.
#
# This script statically parses each policy file's grant blocks and fails
# if any block WITHOUT a "codebase" or "principal" qualifier grants:
#   - java.security.AllPermission (any target/actions), or
#   - javax.security.auth.AuthPermission "*", "callAs", or "doAs", or
#   - java.lang.RuntimePermission "*" or "setSecurityManager"
#
# Qualified grants (grant codebase "..." / grant principal ...) are exempt
# by design -- those scope the permission to a specific trusted codebase or
# identity, which is the correct way to hand out something broad (see
# deploy/policy/host1-lookup.policy's jrt:/file:${jgdms.lib}/* pattern).
#
# Exit 0 = clean; exit 1 = violation(s) found; exit 2 = setup/parse error.

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"

FILES=(
    "$ROOT/defaultspiffemahalo.policy"
    "$ROOT/defaultspiffeoutrigger.policy"
    "$ROOT/defaultspiffereggie.policy"
    "$ROOT/defaultspiffefiddler.policy"
    "$ROOT/defaultspiffegroup.policy"
    "$ROOT/defaultspiffemercury.policy"
    "$ROOT/defaultspiffenorm.policy"
)

violations=0

for f in "${FILES[@]}"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: expected policy file not found: $f" >&2
        exit 2
    fi

    # Walk the file, tracking whether we're inside a grant block and whether
    # that block's header (the "grant ... {" line, possibly wrapped) carries
    # a codebase/principal qualifier.
    awk -v fname="$f" '
        BEGIN { in_block = 0; qualified = 0; header = ""; depth = 0 }
        {
            line = $0
            # Strip // line comments (policy files do not use them inside
            # strings we care about here, so a simple strip is safe enough).
            sub(/\/\/.*$/, "", line)

            if (!in_block) {
                if (line ~ /grant[ \t]*/ && line ~ /\{/ || line ~ /^[ \t]*grant\b/) {
                    header = header " " line
                }
                if (header ~ /grant/ && line ~ /\{/) {
                    in_block = 1
                    depth = 1
                    qualified = (header ~ /codebase/ || header ~ /principal/) ? 1 : 0
                    header = ""
                    # Handle single-line "grant { ... };" case: strip up to first "{"
                    sub(/^[^{]*\{/, "", line)
                } else {
                    next
                }
            }

            # Count braces to find the end of this grant block (handles the
            # remainder of the line after entering the block above).
            n = length(line)
            for (i = 1; i <= n; i++) {
                c = substr(line, i, 1)
                if (c == "{") depth++
                else if (c == "}") {
                    depth--
                    if (depth == 0) {
                        # end of block reached at this char; nothing more to scan on this line
                        break
                    }
                }
            }

            if (!qualified) {
                if (line ~ /java\.security\.AllPermission/) {
                    print fname ": UNQUALIFIED grant of java.security.AllPermission"
                    bad = 1
                }
                if (line ~ /javax\.security\.auth\.AuthPermission[ \t]*"(\*|callAs|doAs)"/) {
                    print fname ": UNQUALIFIED grant of javax.security.auth.AuthPermission implying callAs/doAs"
                    bad = 1
                }
                if (line ~ /java\.lang\.RuntimePermission[ \t]*"(\*|setSecurityManager)"/) {
                    print fname ": UNQUALIFIED grant of java.lang.RuntimePermission implying setSecurityManager"
                    bad = 1
                }
            }

            if (depth == 0) {
                in_block = 0
                qualified = 0
            }
        }
        END { exit (bad ? 1 : 0) }
    ' "$f"
    status=$?
    if [ "$status" -ne 0 ]; then
        violations=1
    fi
done

if [ "$violations" -ne 0 ]; then
    echo
    echo "FAIL: one or more defaultspiffe*.policy files grant AllPermission/AuthPermission/" >&2
    echo "      RuntimePermission(setSecurityManager) unconditionally (no codebase/principal" >&2
    echo "      qualifier). This reopens the AdminPrincipalAuthenticator fail-closed gap." >&2
    echo "      Scope the grant to a specific trusted codebase or principal instead." >&2
    exit 1
fi

echo "OK: no unqualified AllPermission/AuthPermission(callAs|doAs)/RuntimePermission(setSecurityManager)"
echo "    grants in any defaultspiffe*.policy file."
exit 0
