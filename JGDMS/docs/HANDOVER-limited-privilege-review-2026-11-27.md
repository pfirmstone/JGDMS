<!-- CLAIM-HEADER (edit in place; body is append-only)
GOVERNING STATE: E:\Claude\claim_register\rulings\DIRTYCHAI\INDEX.md  (rulings this file touches: RL-DIRTYCHAI-0002, RL-DIRTYCHAI-0003, RL-DIRTYCHAI-0008, RL-DIRTYCHAI-0012, RL-DIRTYCHAI-0013); hypotheses: hypotheses\PERMGRANT\INDEX.md (CL-PERMGRANT-0015 is the same recursion family as section 4); this session owns no hypothesis scope and appended no entries
LATEST ADDENDUM: "Note, 2026-11-27 23:45 +1000, by another session" at the end of this file
SUPERSEDED BY: none
FASTEST-AGEING SECTION: "State of the working tree" - the operator edited AccessController.java several times after each read; re-read the disk (git diff HEAD) before acting on it
-->

# Handover: review of the limited-privilege change in DirtyChai (session 2026-10-25 to 2026-10-26)

Author: Claude (claude-sonnet-5-5), in a DirtyChai session. DirtyChai's policy is advise-only, so no DirtyChai source or
test was written; this file is JGDMS-side analysis. **Nothing was built, run or benchmarked by this session.** Every
behavioural statement below comes from reading source (and the one pasted tierAI log), not from execution.

## 1. What was asked

1. Why two `tierAI` tests failed (log pasted 2026-10-25, image built before the limited-privilege change).
2. Review of the operator's uncommitted limited-privilege work (`docs/DESIGN-privilege-ceiling-2026-10-19.md` section 13
   in DirtyChai), over several rounds as the operator edited.

## 2. The two tierAI failures (10 run, 8 passed, 2 failed)

- `ai/org/apache/river/api/security/CodebaseDomainGrantTest.java`: reads `PermissionGrantBuilder.CODEBASE_DOMAIN` by
  reflection; the constant is gone from `src/`, and `DIGEST_DOMAIN = 7` replaced it. The operator confirmed the
  rename. A rename alone will not pass: `build()` for `DIGEST_DOMAIN` needs a loader and verified SHA-256
  `DigestCodeSource`s, identity is by digest not location (see RL-DIRTYCHAI-0013), and a null class returns the null
  grant. The test header's "location only, signers not pinned" cases need rewriting.
- `ai/org/apache/river/api/security/GrantSignerTest.java`: six digest cases return false because
  `DigestGrant.implies` rejects `dcs.unverified()`, and the test builds sources with the public pre-computed-digest
  constructor, which sets `unverified`. The check came in with DirtyChai `acfdc52f259` (2026-10-20); the operator
  confirmed the policy now rejects unverified domains (see RL-DIRTYCHAI-0012). The test predates that policy.
- Neither test installs a SecurityManager; neither exercises the operator's modified files.
- Both are Zone T (AI-authored) tests, so a rewrite by an AI is permitted; the operator deferred it to Friday
  (weekly limit). The rewrite is still to do.

## 3. State of the working tree

Modified at last read: `CombinerSecurityManager`, `AccessControlContext`, `AccessController`, `DomainIdentity`,
`DigestDomainGrant` (`src/java.base/share/classes/...`). The last full read of `AccessController.java` was after the
operator fixed the `doPrivilegedWithCombiner` caller and the null-protection-domain handling; the operator then said the
module lookup was fixed and that `doUnlimited` was removed, and this session did not re-read after that final edit.

Design as implemented (RL-DIRTYCHAI-0002 is the governing ruling): every `doPrivileged` form is intersected with a
per-class `DomainIdentity` built in `AccessController.limited(...)`, carrying the caller's `ProtectionDomain`
(`callerDomain`) read through the native `getProtectionDomain`. `CombinerSecurityManager` evaluates with
`ContextBuilder.combine` instead of a `doPrivileged`. RL-DIRTYCHAI-0008 applies to the new field.

## 4. Review findings, in the order they were raised

Raised and reported fixed by the operator (not re-verified by reading after the last edit):
- `doPrivilegedWithCombiner` delegated to the public `doPrivileged`, so `Reflection.getCallerClass()` named
  `AccessController`. `JVM_GetCallerClass` skips only `Method.invoke`, `MethodAccessorImpl` and method-handle adapter
  frames ([jvm.cpp] and [method.cpp] `is_ignored_by_security_stack_walk`), not `AccessController`. Fixed by capturing the real caller.
- `substring` typo; `DomainIdentity.equals` ignoring `callerDomain`; `Class.getProtectionDomain()` (permission-checked) on
  the hot path, replaced by the native; a null `ProtectionDomain` for boot-loaded classes dereferenced in
  `getResource`; module name wrongly forced to `java.base` for a null domain (should come from `clazz.getModule()`).

Still open as of the last read:
- Null `caller` in `limited()`: guarded to a null-codesource domain (denies silently) while `getResource` throws; the
  design says deny with an exception. Pick one.
- `DomainIdentity.getCallerDomain()` is public. Low risk (the domain only lives inside an interned context and the
  loader and codesource are already public on a `ProtectionDomain`), but an internal accessor is tidier.
- No per-class cache yet; the operator will add a `ClassValue` once the functionality is settled. Cost of its absence
  was estimated, not measured: a URI parse, a `CodeSource`, a `DomainIdentity` and an interning lookup per call.
- Re-entrancy: `toURL()` for `jrt:` and `file:` makes no permission check and no handler provider lookup
  (`URL.isOverrideable`); any other scheme on first use goes through `URL.lookupViaProviders`, which uses `doPrivileged`
  under a `ScopedValue` that throws `Error` on re-entry. `doUnlimited` would not have avoided that, because the problem
  is re-entry, not privilege. A re-entrancy guard in `limited()` (deny, not throw) would. Same family as CL-PERMGRANT-0015.
- `getResource` strips from the first `$`; the policy matcher and polpAudit's writer must apply the same rule or grants
  silently fail to match.
- Window before `AccessControlContext.hasStarted()`: calls are unlimited; confirm nothing untrusted runs then.

Found incidentally, not in the operator's diff:
- `au.zeus.jdk.net.Uri.toURL()`: `sb.delete(0, sb.length()-1)` leaves a character behind after the user-info join;
  `setLength(0)` clears it. `Uri.java` is on the do-not-modify list, so it is the operator's to fix.
- `DigestCodeSource.computeDigestFromStream` ignores its `algorithm` argument and always uses SHA-256.

Withdrawn during the session: a concern that the public `getCallerDomain()` was a bypass; the operator pointed out the
domain stays inside the `AccessControlContext`.

## 5. Open items for the next session

1. Re-read `git diff HEAD` in DirtyChai, then rebuild and rerun `make test TEST=tierAI`. Expect the two failures in
   section 2 until the tests are rewritten; the `ContextCache*` and `DomainIdentityPrincipalSetTest` tests are the ones
   that would show the `callerDomain` field disturbing context equality or interning.
2. Zone T tests under `test/jdk/ai/` (each shown to fail when its invariant is broken, stating the invariant, `@author`
   naming the model): `doPrivileged` bounded by the caller's own policy grant; `DigestDomainGrant` evaluated against a
   limiting domain returns a boolean and does not throw; limiting domains for different callers are not equal;
   `doPrivilegedWithCombiner` binds to the real caller; boot-loaded callers from a module other than `java.base`
   (for example `java.logging`) get their own module in the codesource. Plus the rewrites in section 2.
3. A `doPrivileged` benchmark in `JGDMS/JGDMS/jgdms-benchmarks` (JMH, four configurations, called through
   `RequestHandler` so the caller is the restricted workload jar; `-prof gc` for allocation). Re-run the audit step
   first: per-class codesources are `<location>/<Class>`, which a jar-URL grant will not match. Read that repo's
   contributing and AI notes before writing there.
4. Optional: the operator may want section 13 of the design note updated to say `callerDomain` (a pinned
   `ProtectionDomain`) rather than a `Class` field.

## 6. Register

No entries were appended: this session had no hypothesis scope, and nothing above was settled by a test. The statements in
section 4 are reading-based and are candidates for `PROPOSED` entries in a scope the operator names.

## Note, 2026-11-27 23:45 +1000, by another session

Appended by Claude Fable 5.2 (proxy-board review session 018wGHqgexvwKfVXuCVJMAzR) with the operator's agreement, after
reading this file against DirtyChai on disk. Only `Uri.java` and its log were opened; nothing was built or run.

- Section 4, `Uri.toURL()`: fixed by the operator in DirtyChai `b2f892d8d24` (2026-10-27); the method now calls
  `sb.setLength(0)`.
- Section 3: the limited-privilege work is no longer uncommitted; the log of `Uri.java` shows DirtyChai `0071db76084`
  (2026-10-26, "Policy limited privilege #233").
- Section 4, `computeDigestFromStream`: not found under `java/security` or `au` in `java.base`; not searched further, so
  it may be renamed, moved or removed.
- This file was written about a month after the session it describes. Every item under "Still open as of the last read"
  needs a fresh read of the source before anyone acts on it.
