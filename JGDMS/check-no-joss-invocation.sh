#!/bin/bash
#
# check-no-joss-invocation.sh
#
# Fails if any main-source Java file introduces a JOSS-based invocation layer
# that is not on the allowlist, i.e. `new BasicILFactory(...)` or
# `extends BasicILFactory`.
#
# Why: BasicILFactory marshals JERI invocation data with Java Object
# Serialization. ObjectInputStream runs readObject/readResolve gadget chains
# during deserialization, BEFORE (and independent of) caller authentication —
# a remote-code-execution surface. net.jini.jeri.AtomicILFactory uses the
# AtomicSerial path (DeSerializationPermission-gated; objects constructed only
# via vetted (GetArg) constructors), which removes the gadget surface.
#
# Legitimate framework subclasses and not-yet-migrated debt are listed in
# no-joss-invocation-allowlist.txt next to this script.
#
# Exit 0 = clean; exit 1 = new forbidden site(s) found.

set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
ALLOW="$ROOT/no-joss-invocation-allowlist.txt"

if [ ! -f "$ALLOW" ]; then
    echo "ERROR: allowlist not found: $ALLOW" >&2
    exit 2
fi

# Active (non-comment, non-blank) allowlist fragments.
ALLOW_FRAGMENTS="$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$ALLOW")"

violations=0
# Match instantiation or subclassing; drop generated/test code and comment lines.
while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    file="${hit%%:*}"
    if [ -n "$ALLOW_FRAGMENTS" ] && grep -qF -f <(printf '%s\n' "$ALLOW_FRAGMENTS") <<<"$file"; then
        continue
    fi
    echo "FORBIDDEN: $hit"
    violations=1
done < <(grep -rnE 'new[[:space:]]+BasicILFactory[[:space:]]*\(|extends[[:space:]]+BasicILFactory\b' \
             --include='*.java' "$ROOT" 2>/dev/null \
         | grep -vE '/target/|/src/test/' \
         | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)')

if [ "$violations" -ne 0 ]; then
    echo
    echo "FAIL: new JOSS invocation-layer site(s) above. Use net.jini.jeri.AtomicILFactory."
    echo "      If a site is genuinely required, add it to no-joss-invocation-allowlist.txt with justification."
    exit 1
fi

echo "OK: no new JOSS invocation-layer sites (BasicILFactory)."
exit 0
