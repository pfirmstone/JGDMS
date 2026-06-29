# Advice (DirtyChai, human-to-implement): principal-only policy grants must build a `PrincipalGrant`, not a `URIGrant`

**Status:** Analysis / advice only. Per the DirtyChai OpenJDK-AI policy, the source change
below must be **written by a human**. This note describes *what* to change and *why*; it does
not contain the implementation.

**Scope:** DirtyChai `src/java.base/share/classes/au/zeus/jdk/authorization/policy/DefaultPolicyParser.java`
(method `resolveGrant`). The identical bug exists in the JGDMS-jar parser
`org.apache.river.api.security.DefaultPolicyParser` — that one is JGDMS-side and will be changed
to match *after* this DirtyChai change is reviewed (Peter's sequence).

---

## Symptom

A policy grant whose only discriminator is principals — e.g.

```
grant principal javax.security.auth.x500.X500Principal "CN=Tester",
      principal au.zeus.jdk.authorization.spire.SpiffePrincipal "spiffe://test.jgdms.local/svc/tester" {
    permission net.jini.security.AccessPermission "*";
};
```

does **not** apply to a `ProtectionDomain` whose `getCodeSource()` is `null`, even when that
domain carries the matching principals. It *does* apply to a domain with a non-null
`CodeSource` (including a `CodeSource` with a null location). This blocks the §7.3 two-gate
requirement that a caller may "use only the Principal": a reducing-context domain with no
codebase (a dynamic proxy / lambda / a deliberately principal-only frame) can never satisfy a
principal grant, so retaining it as a reducer denies every call.

## Verified root cause (empirical + code path)

It is **not** a `ProtectionDomain`-level or policy-wide "null CodeSource ⇒ unprivileged" rule,
**not** the static two-argument `ProtectionDomain` constructor, and **not** the (now-fixed)
`UnresolvedPrincipal` split-package visibility issue. Empirically, with the 4-arg *dynamic*
`ProtectionDomain` constructor and identical principals, in **both** the `au.zeus` and the
JGDMS-jar `org.apache.river` `ConcurrentPolicyFile`:

- `new ProtectionDomain(null,  null, null, [X500,Spiffe])`  → `implies(getAdmin)` = **false**
- `new ProtectionDomain(emptyCS, null, null, [X500,Spiffe])` → `implies(getAdmin)` = **true**
  (`emptyCS = new CodeSource(null, (Certificate[]) null)` — a non-null object with null location)

The cause is the **grant type the parser builds**. `resolveGrant` sets the builder context to
only ever `DIGEST` or `URI` — never `PRINCIPAL`:

```java
// DefaultPolicyParser.resolveGrant, ~line 282-298 (current)
String rawDigest = ge.getDigest();
if (rawDigest != null) {
    ...
    pgb.digest(algorithm, hexDecode(hexValue))
       .context(PermissionGrantBuilder.DIGEST);
} else {
    pgb.context(PermissionGrantBuilder.URI);   // <-- principal-only grants land here too
}
```

So the example above becomes a `URIGrant` with an empty `uri[]`. `URIGrant`'s matcher requires
a non-null `CodeSource`:

```
URIGrant extends CertificateGrant extends PrincipalGrant
CertificateGrant.implies(pd):  return implies(pd.getCodeSource(), getPrincipals(pd));
URIGrant.implies(CodeSource cs, Principal[] p):
    if (!implies(p)) return false;     // principals DO match
    ...
    if (cs == null) return false;      // "Null CodeSource is not implied"  <-- rejects null-CS
```

That guard is *correct for a codebase grant* (a codebase grant should not apply to
codebase-less code — the HC-5 spirit). It is *wrong* for a principal-only grant, which by
standard policy semantics applies regardless of codebase. The grant was simply mis-categorised.

## Required change

In `resolveGrant`, choose the builder context with a three-way decision instead of two:

1. If a **digest** clause is present (`rawDigest != null`) → `DIGEST` context (unchanged).
2. **Else if** the grant is **principal-only** — i.e. **no codebase, no digest, no signer
   certificates**, but **at least one principal** — → **`PRINCIPAL`** context, which makes
   `PermissionGrantBuilderImp.build()` return `new PrincipalGrant(principals, permissions)`.
3. Else → `URI` context (unchanged).

The precise "principal-only" predicate, in terms of the locals already computed in
`resolveGrant`:

- `codebases.isEmpty()`  (no `codeBase` clause)
- `signers == null`  **and**  `aliases.length == 0`  (no `signedBy` clause / certs)
- `rawDigest == null`  (no `digest` clause)
- `!principals.isEmpty()`  (at least one `principal` clause)

`PermissionGrantBuilder.PRINCIPAL` already exists and is what `PrincipalGrant.getBuilderTemplate()`
uses, and `.principals(...)`/`.permissions(...)` are already set on the builder before the
context switch, so the `PRINCIPAL` branch needs nothing else. (The earlier unconditional
`pgb.uri(uri)` loop and `pgb.certificates(signers, aliases)` calls are no-ops for a
principal-only grant — `codebases` and the certs are empty — and `build()`'s `PRINCIPAL` case
ignores them regardless.)

## Why this is correct (and does not weaken HC-5)

- `PrincipalGrant.implies(pd)` checks **only** the domain's principals (`if (pals.isEmpty())
  return true; if (pd == null) return false; return implies(pd.getPrincipals());`) — no
  CodeSource guard. So a null-CodeSource domain with matching principals now matches a
  principal-only grant, which is standard policy-file semantics.
- Codebase and digest grants are **untouched** — they remain `URIGrant`/`DigestGrant` and still
  reject a null `CodeSource`. So dynamically-generated / codebase-less *code* still cannot gain
  **codebase-scoped** authority; only **principal-scoped** authority applies to it. This is the
  HC-5 intent (HC-5 concerns code identity, not authenticated principals). A null-CS domain
  therefore still **reduces** in the §7.3 AND-check — it just no longer poisons a principal
  grant.
- Net effect for §7.3: a reconstructed reducing domain with no codebase (literal `null`
  CodeSource) + the authenticated worker principals matches a worker **principal** grant but not
  a **codebase** grant — exactly "the caller may use only the Principal," with no `emptyCS`
  workaround.

## Edge cases to preserve (do **not** route these to `PRINCIPAL`)

- `grant codeBase "…" principal X { … }` — codebase present → stays `URI` (codebase **and**
  principal grant).
- `grant digest "…" … { … }` — stays `DIGEST`.
- `grant signedBy "…" principal X { … }` — signer certs present → stays `URI`/cert path
  (`PrincipalGrant` would drop the certs; don't lose them).
- `grant { … }` with no principals and no codebase — no principals → predicate false → stays
  `URI` (unchanged; whether a bare `grant {}` should reach null-CS code is a separate question,
  out of scope here).

## Suggested tests (human-written)

1. A principal-only grant **matches** a 4-arg dynamic `ProtectionDomain(null, null, null,
   {principal})` carrying that principal (the case that currently returns false).
2. A **codebase** grant still **does not** match a null-CodeSource `ProtectionDomain` (HC-5
   preserved).
3. A `codeBase + principal` grant is unchanged (matches the codebase domain with the principal;
   does not match a different codebase).
4. Round-trip through `SecurityPolicyWriter` still emits/parses the principal-only grant.

## FOLLOW-UP (2026-06-29, post-implementation): the `PRINCIPAL` guard must use the *resolved* principals

The first implementation guarded the `PRINCIPAL` branch with
`ge.getPrincipals(null) != null`. **That does not work.**
`DefaultPolicyScanner.GrantEntry.getPrincipals()` returns the `principals` field,
which is initialised to a **non-null empty `ArrayList`** and only receives entries
when a `principal` clause is present — so `getPrincipals(null)` is *never* null.
The guard is therefore always true, and a **bare** `grant { … }` (no codebase, no
signedBy, no principals) still takes the `PRINCIPAL` branch →
`new PrincipalGrant(emptyPals)` → `implies(pd)` returns `true` for **every**
domain, **including a null CodeSource** (`PrincipalGrant.implies`: `if
(pals.isEmpty()) return true;`). That re-introduces the HC-5 weakening this change
was meant to avoid — empirically confirmed: a bare `grant {}` matches a 4-arg
dynamic `ProtectionDomain(null, …)` in the rebuilt au.zeus engine.

**Required fix:** guard on the **resolved** principal set instead — i.e. the
`Set<Principal> principals` local already populated in `resolveGrant` (it gets an
entry per `principal` clause and stays empty for a bare grant). Use
`!principals.isEmpty() && codebases.isEmpty()` for the `PRINCIPAL` branch (or,
equivalently, `!ge.getPrincipals(null).isEmpty()`). A bare grant then falls
through to `URI` (codebase wildcard, which rejects a null CodeSource), while a
genuine principal-only grant still becomes a `PrincipalGrant`. The JGDMS-jar
parser has been corrected this way; the au.zeus parser needs the same change.

**VERIFIED 2026-06-29 (DirtyChai rebuilt with this fix):** both engines now agree.
Against `au.zeus.jdk.authorization.policy.ConcurrentPolicyFile` *and* the JGDMS-jar
`org.apache.river.api.security.ConcurrentPolicyFile`, with a 4-arg dynamic
`ProtectionDomain` carrying `[X500, Spiffe]`:

- principal-only grant: null-CS → **true**, emptyCS → true (the "use only the Principal" case works)
- **bare** grant: null-CS → **false** (was true — fixed), emptyCS → true (codebase wildcard still applies to codebase-bearing code)

Regression coverage added JGDMS-side in `DefaultPolicyParserTest`
(`testResolveGrantPrincipalOnlyProducesPrincipalGrant`,
`testResolveGrantBareProducesURIGrantAndExcludesNullCodeSource`,
`testResolveGrantCodebaseExcludesNullCodeSource`) — green on the DirtyChai JDK.

### polpAudit review: `SecurityPolicyWriter` (DirtyChai, advise-only)

Full read of `au/zeus/jdk/authorization/tool/SecurityPolicyWriter.java` (the
`polpAudit` least-privilege generator). Each emission path checked against the
now-fixed parser:

| Audited domain | Emits | Parses to | Matches? |
|---|---|---|---|
| codebase (`cs.getLocation() != null`) | `grant codebase "…" {…}` | `URIGrant` | yes |
| `DigestCodeSource` | `grant codebase "…", digest "…" {…}` | `DigestGrant` | yes |
| **principal-only** (`cs == null`, `principals.length > 0`) | `grant principal X "…" {…}` (l.441, 489-500) | **`PrincipalGrant`** | **yes — only because of the parser fix** |
| `emptyCS` (non-null, null location), no principals | bare `grant {…}` | `URIGrant` (empty uri) | yes (empty-uri wildcard matches any non-null CS) |
| **literal null `CodeSource`, no principals** (lambda/`$Proxy`/generated) | bare `grant {…}` (`else`, l.501-505) | `URIGrant` (empty uri) | **no** |

**Good news — no generator change needed for the principal case.** The
principal-clause emission (l.489-500) was already present; it was the *parser*
that mis-categorised the resulting grant. With the parser fixed, polpAudit's
principal-only output now round-trips correctly. Likewise `AllPermission` is
correctly excluded from the floor (l.360) and ephemeral/one-shot grants are
correctly excluded (l.341).

**Defect — the `else` branch (l.501-505) for a literal-null-CS, no-principal
domain.** Such a domain is **un-grantable by design** (HC-5: a dynamically
generated class with no CodeSource and no principals cannot receive policy
grants). The bare `grant {}` polpAudit writes for it:

- **pre-parser-fix:** *over-granted* — it matched **all** null-CS code (the HC-5
  weakening this whole change removes); and
- **post-parser-fix:** is *futile* — a bare grant is a codebase wildcard (empty
  `uri[]`) whose matcher rejects a null `CodeSource`, so it never matches the very
  domain that produced it. Worse, because `POLICY.implies(pd, p)` (l.410) then
  stays **false** on every subsequent run, the same bare grant is **re-emitted
  every audit run** → unbounded duplication of identical bare blocks.

(Note the distinction: an `emptyCS` domain — non-null `CodeSource`, null location
— is *not* affected; its bare grant matches, so it neither over-grants nor
re-emits. Only a **literal null** `CodeSource` with no principals hits the defect.)

**Recommendation (advise-only; human to implement in DirtyChai):** in the `else`
branch, do **not** emit a bare `grant {}`. Instead emit an explanatory **comment**
into the policy file — preferably a *commented-out* grant so it both explains the
problem and shows exactly what was requested, while staying inert. For example:

```
// polpAudit: UN-GRANTABLE domain — null CodeSource, no principals (generated code:
//   lambda / $Proxy / reflection accessor). Cannot be granted by policy (HC-5).
//   Fix by wrapping the operation in doPrivileged at the responsible frame, or by
//   giving the code a real CodeSource / authenticated principal. Requested:
// grant {
//     permission net.jini.security.AccessPermission "net.jini.admin.Administrable.getAdmin";
// };
```

This is better than a log line because the policy file is the artifact the operator
actually edits (the class JavaDoc directs editing after each integration test); the
comment is co-located, durable, and carries the full diagnostic. The security win
(no more over-granting `grant {}`) comes from *not emitting the grant*; the comment
is its diagnostic replacement.

- **Viability:** confirmed — `DefaultPolicyScanner.configure` sets
  `slashSlashComments(true)` and `slashStarComments(true)` (l.100-101), so `//`
  and `/* */` lines are stripped on the next parse and never break re-loading.
- **Idempotency caveat:** comments are invisible to the writer's dedup mechanism
  (`POLICY.implies`, l.410), so a naive comment would be re-appended every run
  (the same re-emission shape as the bare grant, but now cosmetic, not a security
  issue). Two handling options: (a) **rely on the workflow** — the intended loop
  edits the file between runs; once the operator applies the `doPrivileged` fix the
  domain stops requesting the permission and the comment stops appearing (lingering
  duplicates only accrue if the warning is ignored = appropriate nagging); or
  (b) **textual dedup** — before appending, read the existing file once for a
  per-domain sentinel marker and skip if present (the writer already holds the
  file/URL; today it only loads it as a `Policy`, so this adds a one-time text read).
- Keep a single **log** line as an optional secondary signal (some CI gates scan
  logs rather than policy diffs).
- Also drop/adjust the misleading "applies to all code regardless of origin"
  comment (a bare grant now applies to codebase-bearing code only, never to
  generated/null-CS code). The codebase/digest/principal branches need no change.

## After this lands

- The JGDMS-jar `org.apache.river.api.security.DefaultPolicyParser` gets the same three-way
  context decision (JGDMS-side; will be copied from the reviewed DirtyChai change).
- `net/jini/jeri/RemoteContextCodec` reverts the null-CodeSource *skip*: `marshal` re-emits
  `KIND_NULL_CS`, `unmarshal` reconstructs it with a literal `null` CodeSource + the stamped
  worker principals (no `emptyCS`). The retained domain then reduces to principal-only authority
  via the now-correct `PrincipalGrant`.
