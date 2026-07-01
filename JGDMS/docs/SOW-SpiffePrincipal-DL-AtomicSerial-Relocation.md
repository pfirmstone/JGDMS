# SOW — SpiffePrincipal → downloadable (`-dl`), `@AtomicSerial`, by-value SPIFFE constraint matching

Status: DRAFT for review (2026-07-01). Author: Peter Firmstone + Claude (design discussion).

## 1. Why

1. **Marshalled classes must live in a `*-dl.jar`.** A wire principal placed in a
   non-downloaded module (`jgdms-platform`, `jgdms-jeri`) is on the receiver's
   **local classpath** and is never fetched. An older peer whose local jars predate
   the class throws **`ClassNotFoundException` on unmarshal**. A `-dl` class travels
   with the sender's codebase annotation and is downloaded, so even an older peer
   resolves it.
2. **The Principal is a Configuration / DI concern.** It is constructed by the
   Configuration / login layer and resolved **by-name** by the transport
   (`Class.forName` from `BasicInvocationDispatcher.PRINCIPAL_CTORS`). No transport
   module should *compile*-depend on it. `JwtPrincipal` already follows this (by-name
   only); `SpiffePrincipal` is the outlier — 5 compile-time users in the SSL layer.
3. **Java Serialization is discouraged.** New classes must not implement
   `Serializable`; use `@AtomicSerial`.

## 2. Current state / defects

| Class | Module (jar) | Namespace | Serial | Transport coupling |
|---|---|---|---|---|
| `net.jini.jeri.ssl.SpiffePrincipal` | jgdms-jeri (**not** `-dl`) | `net.jini.*` (not Jini-std) | `Serializable` | 5 compile-time SSL users + by-name |
| `net.jini.security.jwt.JwtPrincipal` | jgdms-platform (**not** `-dl`) | `net.jini.*` | (n/a) | by-name only (clean) |

Both are latent **CNFE-on-older-peers** because they are not downloadable.

## 3. Target

1. **`au.net.zeus.jgdms.spiffe.SpiffePrincipal`** in **`jgdms-lib-dl`**
   (`jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/spiffe/SpiffePrincipal.java`).
   - `@AtomicSerial`, **not** `Serializable`. Serial form: single `spiffeId` `String`
     (draft already written).
   - **Constructor normalizes** `spiffeId` through `org.apache.river.api.net.Uri`
     (RFC 3986 — the class DirtyChai's `au.zeus.jdk.net.Uri` was forked from), storing
     the canonical form — in BOTH the `String` ctor and the `@AtomicSerial` `GetArg`
     ctor. This mirrors DirtyChai's `toUri(...)` so both classes store the *same*
     canonical URI (matching linchpin, §5). `jgdms-lib-dl` already depends on
     `jgdms-platform`, so `Uri` is available and stays on every receiver's local
     classpath (stable class, no CNFE).
   - `getName()` = the canonical `spiffe://` URI — this is the **match key** (§5), the
     formal `java.security.Principal` contract accessor. `toString()` is display-only and
     is NOT relied on for matching.
2. Transport references it **by name only** → no compile edge `jeri`/`platform` → `-dl`,
   so no version-skew CNFE and no dependency inversion.
3. **`JwtPrincipal`** — same treatment as a follow-up (relocate to a `-dl` module under a
   JGDMS namespace; already by-name in the transport, so only a move + namespace change).

## 4. SSL de-coupling (`jgdms-jeri`) — prerequisite for the `-dl` move

Remove the 5 compile-time references so `jeri` no longer imports the class:

- **`AuthManager.permittedViaSan`** — instead of `SpiffePrincipal.fromCertificate(cert)`,
  read the SPIFFE principal(s) from DirtyChai's authenticated **`RemoteSubject`** as
  generic `Principal`, and match against the constraint's permitted principals by a
  **normalized `toString()`** form (§5).
- **`Utilities.hasTlsIdentity` / `instanceof SpiffePrincipal` / `getPrincipals(SpiffePrincipal.class)`**
  — detect a SPIFFE identity by principal **simple class name `"SpiffePrincipal"`**
  (or `toString()` prefix), not `instanceof`/`.class`.
- **`SslConnection` / `SslEndpointImpl` / `FilterX509TrustManager`** — the
  `fromCertificate` derivations that populate local subjects for matching: rely on the
  DirtyChai `RemoteSubject`-provided SPIFFE principals instead of re-deriving; drop where
  redundant.

## 5. Matching rule (the linchpin) — VERIFIED

The constraint carries the **JGDMS** `SpiffePrincipal` (downloaded from `-dl`); the
authenticated peer carries DirtyChai's **`au.zeus.jdk.authorization.spire.SpiffePrincipal`**
in the `RemoteSubject`. Different classes → class-exact `equals`/`Set.contains` cannot
match them (and is unreliable for a downloadable class across codebases/classloaders
anyway).

Bridge by **value** on **`Principal.getName()`** — the formal `java.security.Principal`
accessor — **not** `toString()` (display-only, not a stable contract). Both classes
return `getName()` = the SPIFFE URI (VERIFIED: DirtyChai lines 90–93; JGDMS `getName()`
returns `spiffeId`).

**Caveat (VERIFIED):** DirtyChai canonicalizes the URI through `au.zeus.jdk.net.Uri`
(`new Uri(spiffeId).toString()`); the JGDMS class currently stores the *raw* input, so the
two `getName()` values can differ for a non-canonical input and a naive compare fails.
Resolution: the JGDMS `SpiffePrincipal` normalizes through `org.apache.river.api.net.Uri`
in its constructor (§3) so **both** `getName()` values are the same canonical URI.

Match reduces to: for each principal in the `RemoteSubject` whose `getName()` is a
`spiffe://` URI (the scheme scopes it to SPIFFE — no `instanceof`/class reference needed),
compare its canonical `getName()` for equality against each permitted constraint
principal's canonical `getName()`. The `spiffe://` scheme prefix supplies the SPIFFE
scoping the class name otherwise would; the constructors already canonicalized, so no
re-normalization at match time beyond an optional defensive re-parse.

Requirement: any principal matched this way returns a canonical `spiffe://` URI from
`getName()`.

## 6. Mechanical updates (by-name strings, tests, policy)

- `BasicInvocationDispatcher:225` allowlist string
  `"net.jini.jeri.ssl.SpiffePrincipal"` → `"au.net.zeus.jgdms.spiffe.SpiffePrincipal"`.
- `jgdms-rmi-tls/.../Utilities.java:166` `Class.forName("net.jini.jeri.ssl.SpiffePrincipal")`.
- `jgdms-pref-class-loader/.../BootstrapPermission.java` javadoc (×2).
- **Tests:** move `SpiffePrincipalTest` to the new module/package; retarget its 3 JOSS
  serialization tests to `AtomicMarshalOutputStream` / `AtomicMarshalInputStream.create`
  round-trip (TC_STRING byte-patch helpers still apply — `AtomicMarshalOutputStream
  extends MarshalOutputStream`); add a cross-class `toString`-matching test.
- **Deploy policy (separate change):** `deploy/policy/host*.policy`
  `grant principal net.jini.jeri.ssl.SpiffePrincipal …` should name
  `au.zeus.jdk.authorization.spire.SpiffePrincipal` — that is the *authorization*
  principal in the `RemoteSubject`/ProtectionDomain, a distinct class from the
  constraint principal. (Flagged during this analysis; not part of this SOW.)

## 7. Open decisions

1. ~~Exact SPIFFE URI normalization rule for §5.~~ RESOLVED + **VERIFIED 2026-07-01**:
   normalize via `org.apache.river.api.net.Uri` (same RFC 3986 impl DirtyChai's
   `au.zeus.jdk.net.Uri` was forked from) in the constructor. Source-level diff of the
   two forked `Uri.java` (after stripping CRLF and the expected namespace/`Messages`
   import differences) shows the parse/normalize/`toString()` logic is **byte-identical**
   — the only deltas are one javadoc `@since 3.0.0` line (JGDMS) and one
   `@SuppressWarnings("deprecation")` class annotation (DirtyChai). So
   `new Uri(spiffeId).toString()` produces the same canonical string on both sides; no
   compare-time re-normalization is required. (A runtime assertion on the DirtyChai JDK
   in §8 remains the belt-and-braces check.)
2. Does `fromCertificate` (SAN parsing) survive as a transport-side utility, or is all
   SPIFFE-from-cert derivation replaced by reading the `RemoteSubject`?
3. `JwtPrincipal`'s exact `-dl` module + namespace.
4. Sequencing vs. the pending test-only relocation (`SpiffeCredentialManager`,
   `SpiffeLoginModule` → `qa`) which also reference `SpiffePrincipal`.

## 8. Validation

Full `mvn install` (processor + platform + jeri + lib-dl `-am`) on the **DirtyChai JDK**;
jgdms-jeri SSL suite; the relocated `SpiffePrincipalTest`. Build compiles on vanilla
javac `--release 21`, runs on DirtyChai.

## 9. Interim note

`net.jini.jeri.ssl.SpiffePrincipal` was already converted to `@AtomicSerial` in place
(Serializable dropped) during discussion. That edit is part of the target and will move
with the class in §3; its test is currently red (JOSS round-trip) until §6 retargets it.

## 10. Implementation status — COMPLETE (2026-07-01)

Implemented and validated on the DirtyChai JDK:

- **`au.net.zeus.jgdms.spiffe.SpiffePrincipal`** created in `jgdms-lib-dl` (@AtomicSerial,
  not Serializable, ctors normalize via `org.apache.river.api.net.Uri`). Uri fork parity
  verified byte-identical (§7.1). New AtomicSerial `SpiffePrincipalTest` in
  `jgdms-lib-dl/src/test` — **16/16 green** (round-trip + atomic `(GetArg)` rejection of
  bad scheme / empty trust domain).
- **5 SSL sites de-coupled** (AuthManager, FilterX509TrustManager, Utilities ×2,
  SslEndpointImpl ×2) — matching by canonical `getName()` / `spiffe://` scheme; the
  `SslConnection.populateContext` ServerSubject path uses **reflective by-name
  construction** of the `-dl` class because policy `PrincipalGrant` matching keys on the
  principal's runtime class (§5 refinement). jeri suite **27/27 green**.
- Old `net.jini.jeri.ssl.SpiffePrincipal` **deleted**; production jar clean.
- By-name FQN updates: `BasicInvocationDispatcher.PRINCIPAL_CTORS`, rmi-tls `Utilities`,
  `BootstrapPermission` javadoc.
- Test-only `SpiffeCredentialManager` / `SpiffeLoginModule` / `SpiffeTestSvidFactory`
  relocated to **`qa/src/au/net/zeus/jgdms/spiffe/`**, packaged into a **dedicated
  `spiffe-testlib.jar`** imported by both suites (jtreg `-cpa` + harness-controller cp +
  `testClasspath`/`altClasspath`). Only the test-client JVM needs it (service configs are
  `loginContext=null` → SPIRE-ambient). No new policy grant required (the test policy's
  permissions come from codebase-independent universal `grant {}` blocks).
- **§6 policy correction (bug-fix):** `defaultspiffe*.policy` + `deploy/policy/host*.policy`
  `AccessPermission` grants moved from the constraint class to
  `au.zeus.jdk.authorization.spire.SpiffePrincipal` (the RemoteSubject authorization
  principal) — they previously named a class that never matched.
- 3 jeri JUnit tests migrated to jtreg (`SpiffeCredentialManagerTest`,
  `FileSvidSourceTest`, `SpiffeJwtDispatchIntegrationTest`); all 5 jtreg SPIFFE tests
  compile (vanilla javac).

**Build note:** `qa/src` must be compiled with **vanilla javac** (not DirtyChai's) —
`SpiffeCredentialManager` uses the non-embedded `org.apache.river.api.security.LocalPrincipalProvider`,
which DirtyChai's `java.base` shadows (the [[dirtychai-jdk]] embedded-class trap). Same
compile-vanilla / run-DirtyChai split the jgdms build already uses.

**Remaining:** full ant qa-jars build + jtreg run + `run-matching-spiffe.sh` harness run
(dist rebuild required; Peter's qa environment).
