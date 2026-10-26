# Design Analysis: Identity Resolution for Discovery Credentials

**Status:** analysis only — no code changed. Decision required.
**Date:** 2026-08-01
**Trigger:** `org/apache/river/test/spec/joinmanager/TerminateDiscovery.td` — `discovery failed
-- waited 570 seconds -- 3 discovery event(s) expected, 0 discovery event(s) received`
**Related:** `JGDMS-AGENT-CONTEXT-SpiffeDiscoveryProvider-v1.4.md` §13.2, §5.2;
`SOW-discovery-transport-scoping.md`

---

## 1. Use case

A JGDMS node performing multicast discovery must **sign** its outgoing multicast requests
and announcements, and **verify** those it receives. Under the secure configurations this
is done by the X.500 format providers, which need the node's own **private credentials** —
an `X500PrivateCredential`, i.e. a certificate *plus its private key*.

The node's identity may come from either of two deployment shapes:

- **Workload / process identity** — the SPIFFE case. Identity is issued to the *process* by
  SPIRE infrastructure, and rotates automatically as the SVID nears expiry (~hourly).
- **User / caller identity** — the conventional case. Identity comes from a
  `LoginContext`, bound per-call to a thread.

Both must keep working. The question this document answers is: **how does the discovery
encoding path obtain the right credentials, for both shapes, on both the DirtyChai JDK and
a stock JDK?**

## 2. Observed failure

Nothing is ever encoded, so nothing is ever sent. Both sides log
`UnsupportedConstraintException` from every format provider, then
`DiscoveryProtocolException: no encoded requests` → 0 packets → 0 events → 570 s timeout.

Ruled out with evidence during investigation: missing/unmerged work; NIC selection
(`NICS_USE_AUTO` correctly selects `wlo1`); the transport itself (cross-JVM IPv6 multicast
on `FF05::155`:`4160` verified working); the DER `[8]` `Registrar` drop (by design); and
permissions — under polpAudit `SecurityPolicyWriter.checkPermission` always returns `true`,
so nothing can be denied.

**Diagnostic rule worth keeping:** `X500Provider.secureThrow` throws the *detailed*
`SecurityException` when `canGetSubject()` is true and the *generic*
`UnsupportedConstraintException` otherwise. Under polpAudit `canGetSubject()` is true, so
seeing the **generic** exception proves `se == null` — i.e. **zero credentials, not zero
permissions**. Today that distinction is invisible from the logs; see §7.

## 3. Root cause

`X500Provider.getPrivateCredentials()` (`X500Provider.java:186-204`) resolves identity as:

```java
final AccessControlContext acc = AccessController.getContext();
... Subject s = Subject.getSubject(acc);
```

On the DirtyChai JDK this cannot work, for two independent reasons:

1. **The ACC carries principals only.** `SubjectDomainCombiner` stamps `Principal[]` into
   each `ProtectionDomain` (`ProtectionDomain.java:142`, `:238`), merged additively from the
   workload Subject and any `callAs` user Subject (`SubjectDomainCombiner.java:50-56`).
   Principal *names* can never supply a private key. The ACC was therefore never a
   credential carrier — `Subject.getSubject(acc)` was a **back-door** that used the ACC as a
   *lookup key* to reach the whole Subject.
2. **That back-door now resolves elsewhere.** `Subject.getSubject(acc)` ignores its argument
   and delegates to `current()` (`Subject.java:310-317`, `:338-344`) — the `callAs`-bound
   **user** subject. In the qa run no user subject is bound, so it returns `null`.

⇒ `creds.length == 0` ⇒ `chosen == null` with `se == null` ⇒ generic
`UnsupportedConstraintException` from every provider ⇒ no request is encoded.

**This is not a JGDMS regression.** v1.4 §13.2 explicitly deferred wiring this seam, on the
stated grounds that *"the WorkerSubject is already in the AccessControlContext from the
service bootstrap `doAsPrivileged`. `getPrivateCredentials()` retrieves it from the ACC."*
That reasoning held against the old JDK contract. A platform contract change invalidated a
documented, load-bearing assumption.

**Corollary — §13.2's proposed future fix cannot work.** It suggests updating
`runWithDiscoverySubject` to use `Subject.callAs`. But a SPIFFE subject *is* a
`WorkerSubject`, and `callAs`/`doAs` reject `WorkerSubject` by design
(`Subject.java:556`, `:726-728`). The only Subject the SPI can supply for the SPIFFE case is
precisely the one kind that cannot be bound. Wiring it as written would replace the silent
timeout with `IllegalArgumentException: WorkerSubject is established by SPIRE
infrastructure`.

**There is also nothing to propagate.** `Subject.processWorker()` delegates to
`SpiffeCredentialManager.getInstance().getSubject()` (`Subject.java:298-308`) — a
**process-wide singleton**, reachable from any thread in any scope. `runWithDiscoverySubject`
being a no-op is harmless *by construction*, not by luck: binding is meaningless for a
process identity. The seam is not the defect. **The defect is that
`getPrivateCredentials()` asks the wrong API.**

## 4. The larger finding: four parallel identity-resolution mechanisms

Fixing one line would leave the real problem in place. JGDMS currently has four different
answers to "what is my identity?", none of them canonical:

| | Mechanism | Module | Returns | Resolution order | Callers |
|---|---|---|---|---|---|
| **A** | `Security.currentPrincipals()` + `LocalPrincipalProvider` SPI | jgdms-platform | `Principal[]` | `current()` + `getSubject(acc)`, else registered provider | `PreferredProxyCodebaseProvider:2102` |
| **B** | `Utilities.getTlsSubject()` | jgdms-rmi-tls | `Subject` | `processWorker()` → `current()` → ACC | `TlsRMIClientSocketFactory:42`, `TlsRMIServerSocketFactory:35` |
| **C** | `Utilities.spiffeWorkerSubject()` | jgdms-jeri (`net.jini.jeri.ssl`) | `Subject` (worker only) | single-tier; each call site adds its own fallback | `AuthManager:131`, `SslEndpointImpl:333`, `SslServerEndpointImpl:781,838`, `SpiffeDiscoveryCredentialProvider:34` |
| **D** | raw `Subject.getSubject(acc)` | various | `Subject` | none — **unremediated** | **`X500Provider:191`**, `KerberosEndpoint:650`, `KerberosServerEndpoint:407`, `AbstractDgcClient:402` |

Notes:

- **B is the most developed and the most correct.** Its comments independently document
  every constraint derived in this analysis: that the WorkerSubject is process-ambient, that
  `processWorker()` is its only accessor, and that it must not go through
  `callAs`/`doAs`.
- **B also records a trap absent everywhere else:** *"Do NOT wrap in
  `doPrivileged`/`doPrivilegedWithCombiner` — that creates a fresh privileged context and
  severs the `SubjectDomainCombiner` installed by the outer `Subject.doAsPrivileged` call.
  Call `getContext()` directly on the current stack."*
- **Mirror drift.** `jgdms-rmi-tls` is a hand-copy of `net.jini.jeri.ssl` and is supposed to
  track it. Here the copy has **advanced beyond the original**: `net.jini.jeri.ssl.Utilities`
  has `spiffeWorkerSubject()` (:108) and `hasTlsIdentity()` (:1470) but **no `getTlsSubject()`
  ladder**. `net.jini.jeri.ssl` inlines a per-site fallback instead of centralising one.
- **Stale remediation comment.** `jgdms-rmi-tls/Utilities.java:607` states
  *"`Subject.getSubject(acc)` currently always throws on DirtyChai (BUG-001)"*. Verified
  false today — `Subject.java:338-344` returns `current()` and throws only `NPE` on a null
  argument. Remediated sites carry assumptions that have since drifted; worth a sweep.
- **D is where the bug lives**, and `X500Provider` is not the only unremediated site. The
  Kerberos endpoints and `AbstractDgcClient` resolve identity the same broken way. They are
  not exercised by this test, so their status is *unverified, not healthy*.

## 5. Constraints any option must satisfy

- **C1** — `WorkerSubject` cannot be bound. `callAs`/`doAs` throw. Not negotiable; it is the
  security model, not an implementation gap.
- **C2** — `WorkerSubject` must **never be captured for later use**. SVIDs rotate ~hourly;
  `Subject.java:51-54` requires a fresh `processWorker()` reference at each use. Caching the
  resolved Subject is silently wrong and fails only after an hour of uptime.
- **C3** — The ACC carries principals only. **No option may involve putting credentials back
  into the ACC.**
- **C4** — Private credentials require the `Subject` object itself, so a principals-only
  mechanism (A) is insufficient for this path.
- **C5** — `X500Provider` is the shared X.500 base, used by both SPIFFE and conventional
  `LoginContext` deployments. A SPIFFE-shaped fix must not degrade the conventional path.
- **C6** — Do not wrap ACC retrieval in `doPrivileged` (severs the combiner chain, per §4).
  Note the *current* code captures `acc` outside the `doPrivileged` block, which is the safe
  ordering; any rewrite must preserve that.
- **C7 — portability.** `Subject.processWorker()` is `@since 27` and DirtyChai-only. JGDMS
  must still build and run on a stock JDK. This is precisely why both `Utilities` classes
  reach it **reflectively** (`PROCESS_WORKER.invoke(...)`, null-safe when absent). Any fix in
  `jgdms-discovery-providers` must be reflective or otherwise guarded — it **cannot** take a
  compile-time dependency on `processWorker()`.

## 6. Options

### Option 1 — Point fix: reflective worker fallback inside `X500Provider`

Extend `getPrivateCredentials()` to resolve in B's order — `processWorker()` (reflective) →
`current()` → ACC — instead of ACC alone.

- **+** Smallest possible change; unblocks qa immediately; entirely reversible.
- **+** Satisfies C1–C7 as written; no config, no SPI plumbing, no scope work.
- **− INSUFFICIENT ALONE — falsified by Q3 (§8).** Resolving the correct Subject still
  yields an empty credential array, because the key-algorithm test rejects every SPIFFE
  credential. The symptom would be **identical**. Must be paired with Option 4 or 5.
- **−** Adds a *fifth* copy of the resolution ladder — worsens §4 if left standing.
- **−** Puts a workload-identity fallback into the shared X.500 base (C5 tension): a
  conventional deployment on DirtyChai would now silently prefer the process identity over
  the `LoginContext` user. **This ordering question is the one real design decision here**
  (see §8, Q1).

### Option 2 — Wire the existing SPI: configure `SpiffeDiscoveryCredentialProvider`

Set `discoveryCredentialProvider` in the qa configs and have the encoding path consume the
Subject the provider returns.

- **+** Uses the seam as designed; the provider already exists and returns the right thing.
- **−** **Does not work as-is.** The provider's Subject reaches `runWithDiscoverySubject`,
  which cannot bind a `WorkerSubject` (C1) — and `X500Provider` never receives it, because
  the format-provider SPI takes no `Subject` parameter. Delivering it means threading a
  Subject through that SPI: a public-surface change.
- **−** Holding the returned Subject across the encoding action sits against C2.
- **Verdict: rejected on its own.** The SPI is the right *shape* for injecting a
  non-default identity, but it is not the mechanism that fixes this failure.

### Option 3 — Promote one canonical resolver to `jgdms-platform`

Lift B's ladder into a single supported accessor and migrate all of A/B/C/D onto it.

- **+** Fixes the actual disease. One place to hold C1/C2/C6/C7 correctly, one place to
  audit, one place to fix when the platform contract next moves — which it just did.
- **+** Retires the mirror drift and the stale BUG-001 comments in one pass.
- **−** Broadest blast radius: touches SSL, Kerberos, DGC, RMI-TLS and discovery.
- **−** Needs a decision on the A-vs-B split (principals vs full Subject) — plausibly one
  resolver with two accessors rather than two mechanisms.
- **−** Does not unblock qa this week.

### Option 4 — Implement `SpiffeX500Provider` per v1.4 §5.2 — **now REQUIRED**

Keep `X500Provider` untouched; put both `KEY_ALGORITHM = "EC"` and worker-identity
resolution in the SPIFFE subclass.

- **+** Cleanest against C5 — the shared base never acquires a workload fallback, and Q1
  becomes moot for this failure.
- **+ It is the only option that addresses BOTH defects.** Per Q3, no existing provider's
  `KEY_ALGORITHM` can match a SunEC SVID, so a SPIFFE-specific provider carrying
  `KEY_ALGORITHM = "EC"` (v1.4 §8.2) is *necessary*, not optional. This reclassifies §5.2
  from deferred design to load-bearing.
- **+** The qa `der`/`spiffe` configSets already point at `spiffetrust.jks`, so it WOULD be
  exercised once written (corrects an earlier assumption in this document).
- **−** **`SpiffeX500Provider` does not exist** (verified: no such file). §5.2 is a
  substantial body of work — new format constants, packet structure, client/server wrappers.
- **−** Still needs the subject-resolution change from Option 1, either inherited from a
  fixed base or overridden in the subclass. Option 4 does not subsume Option 1's mechanism;
  it relocates it.
- **−** v1.4 §8.4 flags a pre-existing `KEY_ALGORITHM_OID` signature-vs-key OID bug as a
  *pre-implementation blocker* for this work. That must be resolved first.

### Option 5 — Interim: accept `"EC"` as an alias for `"ECDSA"`

Treat `"EC"` and `"ECDSA"` as equivalent in the key-algorithm test, so the existing
`sha512withecdsa` provider can consume a SunEC SVID. Pairs with Option 1.

- **+** By far the cheapest path to a green test — a comparison change plus Option 1.
- **+** Defensible as a genuine bug fix independent of SPIFFE: the JDK reports `"EC"` for EC
  keys and `"ECDSA"` is a BouncyCastle-ism, so the current test rejects *any* SunEC
  credential, not just SPIFFE ones. ECDSA-with-SHA512 signing works correctly with such a key.
- **−** Makes the *existing* general-purpose provider consume SPIFFE credentials, which may
  not be the intended architecture — this is the question, not a detail.
- **−** Touches the same area as the §8.4 OID bug; the two should be assessed together.
- **−** Risks becoming permanent and removing the pressure to implement §5.2 properly.

## 7. Secondary: make this failure legible

Whatever is chosen, this failure mode cost a multi-day investigation because it is silent by
design. `secureThrow` (`X500Provider.java:262-276`) deliberately suppresses the detailed
`SecurityException` when the caller may not learn why authentication failed — sound for a
remote peer, actively harmful for a local operator, and it cannot distinguish *"you lack
permission"* from *"you have no credentials at all"*.

Cheap, non-invasive improvement: log the suppressed detail at `FINE` on
`org.apache.river.discovery` before substituting the generic exception, and emit a distinct
message when `creds.length == 0` — that case is not a security-sensitive disclosure, it is a
misconfiguration. This is independent of the option chosen and is recommended regardless.

## 8. Recommendation

**Revised after Q3.** There are **two independent defects**, and any fix must address both:

| # | Defect | Fixed by |
|---|---|---|
| **D1** | Wrong identity API — `getSubject(acc)` returns the user subject (`null` here) | Option 1 (in base) or Option 4 (in subclass) |
| **D2** | Key-algorithm test rejects every SunEC SVID — `"ECDSA" != "EC"` | Option 4 (`KEY_ALGORITHM = "EC"`) or Option 5 (alias) |

Fixing either alone leaves the test failing **with an identical symptom**, which is the trap
this whole investigation fell into once already.

- **Correct destination: Option 1 + Option 4** — worker resolution plus a real
  `SpiffeX500Provider`. Honours C5, moots Q1, and matches the documented architecture.
  Gated on the v1.4 §8.4 OID blocker.
- **Pragmatic interim: Option 1 + Option 5** — if a green suite is needed before §5.2 can be
  scheduled. Option 5 stands on its own merit as a bug fix (the current test rejects *any*
  SunEC credential), but it must be recorded as interim or it will become the architecture
  by default.
- **Option 3 remains the strategic direction** for the four-mechanism problem in §4,
  independent of both.

Deliberately **not** recommended: wiring the `DiscoveryCredentialProvider` config in qa. It
would not fix either defect, and it would create the appearance of a fix.

## 9. Open questions — need Peter's decision

- **Q1 (blocking Option 1).** In the shared `X500Provider`, which identity wins when *both*
  a process WorkerSubject and a `callAs` user subject are present? B prefers the worker
  unconditionally. For discovery, is the *node's* identity always the right signer — or
  should a bound user subject take precedence? This is a security-model question, not an
  implementation detail.
- **Q2.** Should the canonical resolver in Option 3 live in `jgdms-platform` alongside
  `Security.currentPrincipals()`, and should A become a thin principals-only accessor over
  it?
- **Q3 — ANSWERED 2026-08-01. Subject shape correct; key algorithm mismatched.**
  `getPrivateCredentials()` applies four conjunctive tests (`X500Provider.java:186-230`):
  1. an `X500PrivateCredential` in the private credential set — **PASSES**;
     `SpiffeCredentialManager:773,795` adds `new X500PrivateCredential(leaf, svid.privateKey)`.
  2. `checkCertificate(cert)` — assumed to pass (validity only).
  3. `keyAlgorithm.equals(cred.getPrivateKey().getAlgorithm())` — **FAILS.**
  4. `ppals.contains(cert.getSubjectX500Principal())` — **PASSES**;
     `SpiffeCredentialManager:779,796` adds `leaf.getSubjectX500Principal()` to the principals.

  On (3): `SpiffeTestSvidFactory:71` uses `KEY_ALG = "EC"` (passed to `keytool -keyalg`), and
  SunEC reports `PrivateKey.getAlgorithm() == "EC"` — confirmed empirically on the live JDK.
  Every existing provider declares `"ECDSA"`, `"RSA"` or `"DSA"`; all three compare `false`.
  ⇒ **every credential is filtered out and the array comes back empty even with the correct
  Subject resolved.** This is defect D2 and it falsified Option 1 as a standalone fix.

  Also confirmed: qa SVIDs use `CN=Reggie` / `CN=Tester`, so v1.4 §2.5's empty-Subject-DN
  hazard is already avoided in qa. The `der` and `spiffe` configSets both point at
  `harness/trust/spiffetrust.jks`, so the failing run **is** the SPIFFE configuration.
- **Q4.** Are the Kerberos endpoints and `AbstractDgcClient` (§4, row D) in scope now, or
  tracked separately? They are broken the same way but unexercised by this test.

## 10. Verification plan

1. **Q3 first** — confirm the credential type in a SPIFFE `WorkerSubject`. Falsifies cheaply.
2. Apply the chosen change; run `TerminateDiscovery.td`. Expect discovery events, not the
   570 s timeout.
3. Confirm the *conventional* (non-SPIFFE, `LoginContext`) secure config still discovers —
   this is the C5 regression risk and must not be skipped.
4. Re-run the wider secure discovery suite before considering it settled.

**Housekeeping owed regardless:** remove the temporary `BUG1-DIAG` probes across six files
(removal map maintained) and restore three `qa1.logging` levels to `SEVERE`.

---

## 11. Implementation log — Options 1 + 4 adopted 2026-08-01

### 11.1 DONE — §8.4 OID blocker resolved (uncommitted)

v1.4 §8.4 required reading `X500Provider.getCertificate0()` before any Phase 2 work.
**Confirmed live, not latent:** `keyAlgorithmOID` is passed straight to
`X509CertSelector.setSubjectPublicKeyAlgID()` (`X500Provider.java:323`), and there is **no
fallback** — a `CertPathBuilderException` is logged at `Levels.HANDLED` and `null` returned
(`:336-340`). `Levels.HANDLED` was itself muted at `SEVERE` in `qa1.logging`, so the failure
was completely invisible.

**The doc's table understated the damage — four of six formats were wrong, not two:**

| format | was | what it actually was | now |
|---|---|---|---|
| sha1withdsa | `1.2.840.10040.4.1` | id-dsa (key) — correct | unchanged |
| sha1withrsa | `1.2.840.113549.1.1.1` | rsaEncryption (key) — correct | unchanged |
| sha256withdsa | `2.16.840.1.101.3.4.3.2` | id-dsa-with-sha256 — **signature** | `1.2.840.10040.4.1` |
| sha256withrsa | `2.16.840.1.101.3.4.2.3` | id-**sha512** — a *hash* OID, wrong hash too | `1.2.840.113549.1.1.1` |
| sha512withecdsa | `1.2.840.10045.4.3.4` | ecdsa-with-SHA512 — **signature** | `1.2.840.10045.2.1` |
| sha512withrsa | `2.16.840.1.101.3.4.2.3` | id-sha512 — *hash* OID | `1.2.840.113549.1.1.1` |

Both RSA formats shared one NIST hash OID — copy-paste damage, as were the "OID for DSA
keys" comments sitting in the ECDSA and RSA files. Each fix carries a comment explaining
key-vs-signature OID. **Per §8.4 this should land as its own commit**, separate from the
SPIFFE work; it is a standalone bug fix that makes cert lookup work for four formats that
have never worked. It is also a **behaviour change**: verification that previously always
failed silently will now actually run.

### 11.2 §5.2 `[OPEN]` items — resolved against source

- `SpiffeCredentialManager.getTrustBundle()` → **confirmed**, `:293`, returns
  `X509Certificate[]`.
- `addSvidRotationListener(listener)` → **does not exist.** The real API is
  `addListener(SvidRotationListener)` / `removeListener(SvidRotationListener)` (`:354`,
  `:370`). v1.4 §5.2 should be corrected.

### 11.3 BLOCKER — §5.2 as written violates C7 and cannot compile

`SpiffeCredentialManager` exists in exactly two places, **neither reachable from
`jgdms-discovery-providers`**:

1. `DirtyChai/src/java.base/share/classes/au/zeus/jdk/authorization/spire/` — and
   `au.zeus.jdk.authorization.spire` is **not exported** from `java.base`.
2. `qa/src/au/net/zeus/jgdms/spiffe/` — test code, not on the production path.

So §5.2's `SpiffeCredentialManager.getInstance().getTrustBundle()` will not compile against
a stock JDK **or** against DirtyChai without `--add-exports`. This is the same constraint
that already forced `net.jini.jeri.ssl.Utilities` to reach `Subject.processWorker()`
**reflectively** — the precedent is in-tree and was set for exactly this reason.

**Options — needs a decision:**

- **(a) Reflection**, mirroring `Utilities.spiffeWorkerSubject()`. Consistent with existing
  precedent; no new public surface; null-safe when SPIFFE is absent. But reflection over an
  unexported package still needs `--add-exports` at *runtime* unless the accessor lives in
  an already-privileged component, and it is unverifiable by the compiler.
- **(b) New SPI in JGDMS — recommended.** A `SpiffeTrustProvider` (trust bundle + rotation
  callback) defined in JGDMS and *implemented* by DirtyChai or qa, resolved via
  `ServiceLoader` or `Configuration`. This is precisely the pattern the codebase already
  uses twice — `DiscoveryCredentialProvider` and `LocalPrincipalProvider` (the latter
  existing specifically because "the SPIFFE workload is not propagated via
  `Subject.doAs()`"). Keeps `jgdms-discovery-providers` portable and testable without
  DirtyChai, and inverts the dependency the right way.
- **(c) `--add-exports`** — rejected. Poisons every downstream consumer.

### 11.5 DONE — SPI adopted (option b), Option 1 seam, Phase 1 constants

All of the following compiles clean (`mvn -o -pl jgdms-platform,jgdms-discovery-providers
compile`, EXIT=0). **Uncommitted.**

- **`org.apache.river.api.security.SpiffeTrustProvider`** (new, jgdms-platform) — trust
  material is *pushed in* by whoever owns it, inverting the dependency that §11.3 showed
  cannot be satisfied. Modelled directly on `LocalPrincipalProvider`.
- **`Security.registerSpiffeTrustProvider(...)` / `Security.spiffeTrustBundle()`** (new) —
  registration mirrors `registerLocalPrincipalProvider`, **plus a permission guard**:
  `SecurityPermission("registerSpiffeTrustProvider")`. Registering trust material decides
  which CAs this process believes, so it must not be freely settable.
- **`registerLocalPrincipalProvider` guarded too** (Peter's call, 2026-08-01) — it had no
  guard, so anything able to reach it could assert an arbitrary local identity, and those
  principals select which principal-scoped policy grants apply. Now requires
  `SecurityPermission("registerLocalPrincipalProvider")`.
  **This is a behaviour change on a shipped API.** The only live callers are
  `qa/src/au/net/zeus/jgdms/spiffe/SpiffeCredentialManager.java:657` (register) and `:701`
  (clear). Under a security manager both will now throw unless granted:
  ```
  permission java.security.SecurityPermission "registerLocalPrincipalProvider";
  ```
  That grant is **not yet added** to the qa policy files — required before the secure
  configs will run. Note most `defaultsecure*.policy` files are emptied stubs
  (`defaultsecuretest.policy` and `defaultsecurephoenix.policy` are the substantive ones),
  which is a pre-existing condition tracked separately.
- **Deliberate simplification of §5.2.** The doc specifies a cached `volatile` bundle plus
  an `SvidRotationListener`. The SPI instead exposes only `getTrustBundle()`, fetched per
  validation. Equivalent when correct, but removes a listener lifecycle that DirtyChai's own
  API warns must be unregistered "to prevent leaks" (`:350`), and removes the window in
  which a stale bundle is used. v1.4 §5.2 should be updated to match.
- **`X500Provider.getSubject(AccessControlContext)`** (new `protected` hook) — the Option 1
  mechanism, placed so that C5 holds: the default is *unchanged* behaviour, and only
  subclasses whose identity is process-wide override it. The conventional path needs no
  change (a `LoginContext` subject bound via `callAs` is what `getSubject(acc)` already
  returns on DirtyChai). Javadoc records the C6 constraint about not re-capturing the ACC.
- **`spiffe/sha256withecdsa/Constants.java`**, **`spiffe/sha512withecdsa/Constants.java`**
  (new) — §4.1 Phase 1, with `KEY_ALGORITHM = "EC"` and `KEY_ALGORITHM_OID =
  1.2.840.10045.2.1`, each carrying the rationale inline.

### 11.6 REMAINING — Phase 2, and one open design point

Not yet written: `SpiffeMulticastClient`/`SpiffeMulticastServer` (§5.1 packet format), the
four `Client`/`Server` wrappers, and the
`META-INF/services/org.apache.river.discovery.DiscoveryFormatProvider` registrations.
(`SpiffeX500Provider` itself is superseded — see `SpiffeSupport` in §11.5.)

**Exact seams, located 2026-08-01.** The remaining change is small in volume but touches
base classes shared by all six x500 formats, so it needs care rather than haste:

*Signing side* — `X500Provider.SigningBufferFactory` (`:502`) and its inner `BufferInfo`:
- `:543` `int authBlockLen = principalName.length + maxSignatureLength;` — must become
  `+ 2 + certLen` to reserve room for the leaf.
- `:558` `void sign()` — writes principal name then signature; must interleave
  `[2B cert length][cert DER]` between them, per §5.1.

*Verification side* — `X500Client.X500MulticastAnnouncement.checkConstraints()` (`:196`):
- `:204` `Certificate cert = getCertificate(p);` — the SPIFFE path must instead read the
  leaf from the packet and call `SpiffeSupport.validateSpiffeCert(p, leaf)`.
- `:213` `verify(signed, signature, cert.getPublicKey())` — unchanged.
- `X500Server.java` mirrors this for the request direction.

**Obstacle, and how it is resolved (revised 2026-08-02).** `X500MulticastAnnouncement` is a
*private* inner class of `X500Client`, so the cert-acquisition step cannot currently be
overridden. An earlier draft of this section recommended a `protected` hook. **That is not
the right answer.**

Widening it to `protected` would make "which certificate is this signature verified
against" an extension point reachable by any subclass. The marginal risk is admittedly
small — `X500Provider.getCertificate(X500Principal)` at `:159` is *already* `protected`, so
the trust decision is already overridable, and a hostile override only matters if it is
selected via `META-INF/services`, which requires classpath control and therefore full
compromise anyway. But "no worse than today" is not a reason to widen a trust decision.

**The seam should be package-private instead**, which costs nothing because it matches the
layout the codebase already uses: shared format implementation lives in
`org.apache.river.discovery.internal` (`X500Client`, `X500Server`, `X500Provider`), and only
the thin per-format wrappers live in `org.apache.river.discovery.x500.*`. Therefore:

- `SpiffeMulticastClient` / `SpiffeMulticastServer` belong in `.internal`, beside
  `X500Client` / `X500Server` — not in a `spiffe.*` package.
- The cert-acquisition hook is then package-private: no new `protected` surface, and the
  seam is unreachable from outside the package regardless of who subclasses what.
- Only the `spiffe.sha256withecdsa` / `spiffe.sha512withecdsa` wrappers stay in `spiffe.*`.

**DONE:** `SpiffeSupport` has been moved accordingly — from
`org.apache.river.discovery.spiffe` to `org.apache.river.discovery.internal`, and reduced
from `public` to package-private, with both methods package-private. `validateSpiffeCert`
is a trust decision, so narrowing it is worth doing on POLP grounds alone. Compiles clean.

**MTU:** §5.1 flags this as possibly `[OPEN]`. With `MAX_SIGNATURE_LEN` now 72 (P-256)
rather than the x500 formats' 128, there is ~56 bytes of headroom recovered per packet to
offset a 400-600 byte leaf. Whether that closes the budget is unverified.

**Open design point — how does `SpiffeX500Provider` obtain the signing Subject?**
`SpiffeTrustProvider` answers *whom to trust*; it does not answer *what to sign with*, and
the two should not be conflated. Three candidates were considered:

1. **REFUTED (2026-08-29) — Reuse `DiscoveryCredentialProvider`.** The premise was that
   `SpiffeDiscoveryCredentialProvider` already returns the worker subject, so plumbing that
   instance through to the format provider would be enough. It is not: this SPI's contract
   is "return a `Subject` to bind" — callers pass the result to
   `Subject.callAs`/`Subject.doAs`, and `AbstractLookupDiscovery.runWithDiscoverySubject` does
   exactly that. But a DirtyChai `WorkerSubject` is ambient and process-wide, not
   thread-scoped; it rotates hourly; and `Subject.callAs`/`doAs` reject it outright, throwing
   `IllegalArgumentException`, because binding is meaningless for an identity that is always
   in scope (see `DirtyChai/.../javax/security/auth/Subject.java:44-54,556`). Configuring
   `SpiffeDiscoveryCredentialProvider` as written would therefore make **every** discovery
   operation throw as soon as SPIFFE credentials are set up — not a plumbing gap, a defect.
   `SpiffeDiscoveryCredentialProvider.getSubject()` has been changed to return `null` (2026-08-29),
   meaning "identity is ambient — establish nothing," so `AbstractLookupDiscovery`'s existing
   null branch runs and no binding is attempted. This candidate cannot be reused for the
   SPIFFE case; do not plumb it through to a format provider.
2. **A second registration SPI** alongside `SpiffeTrustProvider`, symmetric and consistent
   with what was just built. Not needed — see 3.
3. **CORRECT, and already implemented.** Reflection to `Subject.processWorker()`, mirroring
   `net.jini.jeri.ssl.Utilities.spiffeWorkerSubject()`. Same `--add-exports` caveat as §11.3
   option (a) does not apply here — `javax.security.auth.Subject` *is* exported — so this is
   more viable for the subject than it was for the trust bundle. This is what
   `SpiffeSupport.processWorkerSubject()` (`org.apache.river.discovery.internal.SpiffeSupport:93`)
   does: it resolves the ambient `WorkerSubject` reflectively, fresh at each call, and
   deliberately does not cache it (SVIDs rotate hourly; a cached reference goes stale). It
   sidesteps the binding problem in candidate 1 entirely, because signing/verification code
   calls it directly for a fresh reference at the moment of use, rather than routing an
   identity through a bind-then-read seam. The signing-side wiring into
   `X500Provider.SigningBufferFactory`/`X500Client`/`X500Server` described above in this
   section is still outstanding (Phase 2), but the resolution mechanism itself is done.

**Also note:** `sha256withrsa` is absent from the `DiscoveryFormatProvider` services file —
the format exists but has never been registered. Unrelated to this work; flagged in passing.

### 11.7 D1's ACTUAL MECHANISM — found 2026-08-09 from a live jsse run

**Two corrections to earlier sections first.**

1. **The failing run is the `jsse` configSet, NOT spiffe.** §11.6/Q3 stated otherwise. The
   VM args show `discovery.x500.trustStore=harness/trust/truststore`,
   `trustStorePassword=trustpw`, `auth.login.config=jsselogins`, principals `CN=Reggie` /
   `CN=Tester`. **D2 (`"EC"` vs `"ECDSA"`) is real but is not what fails here.**
2. The run used stale `dist/target/.../lib/*.jar`; none of §11.5's diagnostics fired.
   `mvn install` + dist rebuild is required before the new logging appears.

**The mechanism, evidenced by comparing two thread stacks in one log.**

Reggie's announce thread bottoms out at:
```
java.lang.Thread.run(Thread.java:1701)
java.lang.Thread$SubjectAccess.callNoCheck(Thread.java:1739)
javax.security.auth.Subject$NoCheck.callAs(Subject.java:471)
```
The discoverer's encoding thread bottoms out at:
```
net.jini.discovery.AbstractLookupDiscovery$Requestor.run(AbstractLookupDiscovery.java:790)
```
— no `Thread.run`, no `SubjectAccess`, no `callAs`.

**DirtyChai propagates the Subject into new threads by wrapping `Thread.run()`. Every
thread in `AbstractLookupDiscovery` extends `Thread` and *overrides* `run()`, so that
wrapper is never invoked and the Subject never arrives.** Confirmed: `Notifier` (:312),
`AnnouncementListener` (:437), `ResponseListener` (:644), `Requestor` (:719),
`AnnouncementTimerThread` (:836) — all `extends Thread`.

The ScopedValue Subject established by `MasterTest.doTestWithLogin` →
`Subject.doAsPrivileged` (visible at the base of every test-side trace) is bound on the
*calling* thread only. ScopedValue bindings do not cross thread creation. This is why
`runWithDiscoverySubject` exists at all — someone hit this and stubbed the seam.

**Consequence: §13.2's proposed `callAs` fix is CORRECT for this case** — and §11.6's
framing needs splitting, because the two deployment shapes need *different* mechanisms:

| shape | Subject | fix |
|---|---|---|
| conventional / `jsse` (`LoginContext`) | `UserSubject` | capture at construction (where the ScopedValue *is* bound) and bind per-use with `Subject.callAs` — `callAs` accepts a UserSubject |
| SPIFFE | `WorkerSubject` | process-ambient; `SpiffeSupport.processWorkerSubject()`; **no binding possible or needed** |

`getDiscoverySubject()` currently returns `NoOpDiscoveryCredentialProvider`'s `null`, so
nothing is captured. **Next step: have it fall back to a Subject captured during
`AbstractLookupDiscovery` construction** (construction runs on the caller's thread, inside
the login scope), and make `runWithDiscoverySubject` actually bind it via `callAs`.

**Wider implication — this is not discovery-specific.** Any `Thread` subclass overriding
`run()` anywhere in JGDMS loses the workload/user Subject on DirtyChai. Worth an audit.

### 11.4 Scope note — §5.1 packet change is probably NOT optional

Considered a cheaper "Phase 1 only" variant: new `spiffe/*` Constants with
`KEY_ALGORITHM = "EC"` plus wrappers over the *existing* `X500Client`/`X500Server`, relying
on the qa static truststore (`org.apache.river.discovery.x500.trustStore=spiffetrust.jks`)
for verification instead of §5.1's packet-embedded leaf cert. With the §8.4 OID fix applied,
that path is now at least functional in principle.

**But it likely does not work for SPIFFE.** `getCertificate0()` builds a path to a trust
anchor and must *find the signer's leaf* in the truststore or an added `CertStore`.
`spiffetrust.jks` is 521 bytes — consistent with a single CA certificate, not per-node
leaves. SVIDs are issued dynamically and rotate hourly, so pre-populating leaves in a JKS is
not viable in general. **This is exactly why §5.1 embeds the leaf in the packet.** Treat the
shortcut as unavailable pending a truststore inspection (password not in the configSet;
`javax.net.ssl.trustStorePassword=spiffetrustpw` at `configSet.properties:15` is the JSSE
store, and did not open it).

---

### 11.8 DECISION — `DiscoveryCredentialProvider` SPI REMOVED (2026-08-29)

**Operator-approved removal, following a board-guided critical review. Applied.**

#### What was removed

| File | Fate |
|---|---|
| `jgdms-platform/.../net/jini/discovery/DiscoveryCredentialProvider.java` | **deleted** |
| `jgdms-platform/.../net/jini/discovery/NoOpDiscoveryCredentialProvider.java` | **deleted** |
| `jgdms-jeri/.../net/jini/jeri/ssl/SpiffeDiscoveryCredentialProvider.java` | **deleted** |
| `AbstractLookupDiscovery` config entry `discoveryCredentialProvider`, its `Initializer` field, its instance field, and `getDiscoverySubject()` | **deleted** |

`runWithDiscoverySubject` is **retained unchanged** — `Subject.callAs` + `CompletionException`
unwrap + null passthrough. Only its *source* changed.

#### Why — five findings, none of which the SPI survives

1. `[M]` **The SPI shipped with a working binder and lost it on day two.** Added 2026-05-15
   (`c89faa84b`) with `return Subject.doAs(discoverySubject, action);`. Commit `2c1b27a6a`
   (2026-05-16, `copilot-swe-agent[bot]`, "Remove Subject.doAs wrapping from discovery
   credential flow") deleted the binding **and softened the javadoc** from "used to execute …
   under `Subject.doAs`" to "supplies credential identity context". The unit test went later
   (`ac280695d`). The contract has been unimplementable since day two, with the documentation
   adjusted to conceal it.
2. `[M]` **The implementation space is empty in both directions.** SPIFFE is *prohibited* from
   satisfying it — `Subject.callAs`/`doAs` throw `IllegalArgumentException` for a
   `WorkerSubject` (DirtyChai `Subject.java:556–558`, `:726–728`, `:776–778`) — and a
   conventional implementation degenerates to a constant function returning the login Subject
   the caller already holds. Both shipped implementations returned `null`.
3. `[M]` **Never configured.** Zero `.config`/`.properties` in repo history set it; it is absent
   from `LookupDiscovery`'s normative javadoc config table.
4. `[M]` **Zero release-compat cost.** `@since 3.1.1` names an unreleased version; the only tag
   is `jgdms-3.1.0`.
5. `[M]` **It could never have fixed the live failure.** No qa config sets it, so
   `getDiscoverySubject()` returned `null` and the null-passthrough branch ran — see
   `ADVICE-discovery-subject-sourcing.md` §1.1, §8(1).

This supersedes §11.6's earlier framing of a provider-shaped seam for SPIFFE, and settles
`ADVICE` §1.3(2)'s "keep the SPI, but demote it" in the stronger direction: **delete it.**
The knowledge that motivated `SpiffeDiscoveryCredentialProvider`'s existence — why a
`WorkerSubject` cannot be bound — is preserved as a comment on
`SpiffeSupport.processWorkerSubject()`, which is where a future reader will be standing when
they are tempted to reintroduce the seam.

#### What replaces it

`AbstractLookupDiscovery` now captures the constructing caller's `Subject` into a final field
and feeds that to `runWithDiscoverySubject`.

`[M]` **The capture is not vacuous — measured, not assumed.** `ADVICE` §9 recorded this as
unverified, and it is the exact failure mode ("looks correct, does nothing") that has cost
this project days. Established two ways:

- *Source trace.* `MasterTest.java:174–189` → `Subject.doAsPrivileged(context.getSubject(),
  action, null)` → `doTest(td)` → `testEnv.construct(config)` (`MasterTest.java:242`) and
  `test.run()` (`:276`), **all on the same platform thread inside the binding**. DirtyChai's
  `doAsPrivileged` binds via `doNoCheck` → `ScopedValue.where(SCOPED_SUBJECT, subject)`
  (`Subject.java:634–640`), which `Subject.current()` reads (`:386–392`, `NoCheck.current()`
  `:456–458`). Spot-checked construction sites are inside `construct()`/`run()`:
  `qa/src/.../spec/lookupdiscovery/GetGroups.java:82`,
  `qa/src/.../spec/discoverymanager/AbstractBaseTest.java:117`.
- *Empirical probe*, run against the DirtyChai JDK at
  `/home/user/GitHub/DirtyChai/build/linux-x86_64-server-release/images/jdk`, modelling the
  qa path exactly (`doAsPrivileged(login, …)` → capture → platform worker thread →
  `callAs(captured, …)`):

  ```
  [1] inside doAsPrivileged: Subject.current() = Subject: Principal: CN=Tester …
  [1] identical to login subject? true
  [2] on platform worker thread: Subject.current() = null      <- why the capture is needed
  [3] inside callAs(captured): private creds now visible: [fake-x500-private-credential]
  ```

  `[2]` confirms `ADVICE` §1.3(2)'s premise (platform threads do not inherit the scoped
  Subject — `Thread.java:846–847` `this.scopedSubject = new Subject[0]`); `[3]` confirms the
  captured Subject re-establishes the private credentials that `X500Provider` was failing to
  find.

`[M]` **What this does not cover.** `RunningServiceAdmin.java:137` builds a
`LookupDiscoveryManager` in harness code whose thread/scope was not traced, and the services
construct theirs under their own login. Those are unverified; both degrade to `null` →
passthrough, i.e. today's behaviour, not a regression.

#### Hazard: never capture a `WorkerSubject`

DirtyChai's `WorkerSubject` is ambient, rotates hourly, and "must never be captured for later
use". `captureDiscoverySubject()` therefore uses an **allow-list of exact class names** —
`javax.security.auth.Subject` (what `LoginContext.login()` creates, `LoginContext.java:581–582`)
and `javax.security.auth.UserSubject` — and captures nothing else.

The direction matters. A deny-list naming `WorkerSubject` fails **open** against anything
DirtyChai adds later: `WorkerSubject` is already `sealed … permits SpiffeSubject,
RemoteSubject` (`WorkerSubject.java:36`), and the hierarchy is sealed precisely so it can
grow. An allow-list fails closed — an unrecognised type is refused, logged at `CONFIG`, and
falls through to passthrough, which for an ambient identity is the *correct* behaviour
(`SpiffeSupport.processWorkerSubject()` resolves it afresh per use). Subclasses are
deliberately not matched.

Explicitly **not** done: passing the Subject to `callAs` and catching the resulting
`IllegalArgumentException`. That decides the question by provoking a failure and swallowing
it, once per discovery operation — the fail-open pattern the review criticised.

`[M]` Note that on this DirtyChai checkout `Subject.current()` **cannot currently** return a
`WorkerSubject` at all: every public binder rejects one (`callAs` `:556`, `doAs` `:726`/`:776`,
`doAsPrivileged` `:842`/`:930`), and the only unchecked binder is `Thread.SubjectAccess`
(`Thread.java:1730–1743`), which merely re-binds what was already bound. The guard is
therefore defence in depth against an invariant that lives in another repository, not a live
code path.

#### API/binary compatibility — gate impact, reported not suppressed

`[M]` This deletes **public API in the `net.jini` specification namespace**. The live lint
gates (`check-api-compat.sh`) will report binary-incompatible changes:

- `net.jini.discovery.DiscoveryCredentialProvider` — public interface removed
- `net.jini.discovery.NoOpDiscoveryCredentialProvider` — public final class, public field
  `INSTANCE` and public constructor removed
- `net.jini.jeri.ssl.SpiffeDiscoveryCredentialProvider` — public final class removed

No gate has been suppressed, and no baseline has been touched. The removals are correct to
report: they *are* binary-incompatible. They carry no release-compat cost because all three
types are `@since 3.1.1`, an unreleased version — the only tag is `jgdms-3.1.0` — so no
released artefact can reference them. The gate needs an explicit operator acknowledgement of
these three entries; that is a decision for whoever runs the release, not something to be
silenced here.

#### Not verified

- Nothing was built with maven or ant. `AbstractLookupDiscovery.java` was compiled standalone
  with the DirtyChai `javac` against the existing `target/classes` and `~/.m2` and is clean;
  the reactor build, the qa suite, and the compat gates were **not** run.
- Whether the capture fixes the 44 × "no X.500 private credentials" and 15 ×
  `UnsupportedConstraintException` failures **in the live run** — the mechanism is measured,
  the outcome is not.
- Whether principal-scoped policy grants resolve under `callAs` alone once a SecurityManager
  returns (`ADVICE` §9, unchanged by this work).

---

## 12. Open items: clock skew, time source, and offline reissuance

Recorded 2026-08-02/03 from design discussion. **These belong with the offline-SPIRE / mesh
federation work, not with discovery credential resolution** — they are captured here only
because they arose from it and would otherwise be lost. Relocate when that document exists.

### 12.1 Clock skew tolerance — quantified

The failure condition is `now < notBefore` or `now > notAfter` on the peer's leaf, evaluated
against each node's own clock, so what matters is *relative* skew between sender and
verifier. It is **asymmetric**:

- **Verifier behind issuer — the binding constraint.** The certificate reads as
  not-yet-valid and the entire tolerance is the issuer's backdating window. SPIRE backdates
  `NotBefore` by a small fixed amount, on the order of **tens of seconds**.
  **[VERIFY]** the exact constant against the SPIRE CA config — the whole budget hinges on
  it and it is not confirmed here.
- **Verifier ahead — forgiving.** Tolerance is the leaf's *remaining* lifetime. SPIRE agents
  rotate at half-life, so a node normally holds a certificate with ≥50% left: roughly
  **30 minutes** for a 1 h SVID, but near **zero** when catching a peer immediately before
  its rotation.

**JGDMS adds no tolerance of its own.** `X509CertSelector.setCertificateValid(new Date())`
(`X500Provider.java:324`) and the `checkCertificate` path both use system time with no slop,
and Java's `CertPathValidator` has no built-in skew allowance. The entire budget is whatever
SPIRE backdates.

**Holdover budget** against a ~30 s window:

| Oscillator | Drift | Time to exhaust ~30 s |
|---|---|---|
| Bare crystal RTC | ~20 ppm (1.7 s/day) | ~17 days |
| Poor crystal / wide temperature swing | ~100 ppm | ~3.5 days |
| TCXO (single-digit dollars, commodity) | ~1 ppm | ~1 year |
| OCXO | ~0.05 ppm | decades |
| Chip-scale atomic | ~0.001 ppm | irrelevant |

**An atomic clock is orders of magnitude past the requirement; a TCXO is sufficient.** Two
things ease it further: holdover need only span the interval *between resyncs*, not the
deployment; and the window is ours to widen — the budget scales linearly, so 30 s → 5 min
takes a bare crystal from ~17 days to ~5 months, at the cost of a 5-minute replay window on
certificate validity (a modest trade against hourly SVIDs).

**Metric note:** ppm is the right measure for drift/holdover but the *wrong* one for phase
noise or short-term stability, where Allan deviation over the averaging interval applies.
Frequency offset largely calibrates out; stability does not. The two are near-independent.

**Recommendation:** add explicit, configurable skew tolerance to certificate validity
checking for the mesh case, and — more importantly — stop swallowing the failure. Validity
rejections currently log at `Levels.HANDLED`, which is muted in the qa configuration, so a
node that has drifted out of tolerance is **indistinguishable from one that cannot hear
anyone**. That is the real operational risk, not the drift itself.

### 12.2 Time source — GPS-disciplined NTP

GPS-backed NTP is a good fit because time arrives as a **physical signal** rather than as an
assertion from a peer that would itself need authenticating — so a cold-booted node has
correct time before it holds any trust relationship, which is the ordering the SPIFFE
bootstrap requires. It also keeps JGDMS out of the time business: `gpsd` + `chrony` is
platform infrastructure, the same relationship SPIRE already has with the clock. This is
emphatically not a precision problem — NTP over a LAN holds single-digit milliseconds
against a budget of tens of seconds — it is a *presence* problem.

**A Jini-shaped time service would be structurally wrong for this job:** using it requires
discovering it, discovery requires validating a certificate, and that requires the time.
Authenticated time is circular with certificate validation in general; any design that does
not confront that fails precisely on a cold boot after a long outage.

Caveats to design in rather than discover later:

- **Civilian GPS is unauthenticated and spoofable**, and spoofing is a direct attack on
  certificate validity: walk a node's clock forward and expired SVIDs verify; walk it back
  and a revoked-then-reissued identity revives. This converts a positioning attack into an
  authentication bypass, and on a leaderless mesh there is no quorum to notice the outlier.
  **Galileo OSNMA** (authenticated navigation data, operational since 2023) is the
  mitigation worth specifying.
- **Holdover for nodes without sky view** must be a defined behaviour, not an accident —
  see 12.1. Thermally stable environments (tunnels, mines) favour the budget; nodes cycling
  between surface and underground do not.
- **Leap seconds:** GPS time runs ahead of UTC (18 s currently) with the offset in the
  navigation message. `gpsd` handles it; a receiver read directly over NMEA is a classic
  place to be 18 s wrong. Harmless against hourly certificates, not harmless if anything
  later needs tighter agreement.

### 12.3 The offline reissuance tension — unresolved

**Time is not trust.** A correct clock is a precondition for *evaluating* a trust decision;
it is not a source of one. The trust root is irreducible and must arrive **out of band** —
the SPIRE CA anchor provisioned at manufacture or deployment, before the node meets a peer.

The genuinely hard problem offline is what SPIFFE traded away to get its properties: it
substitutes **short-lived credentials for revocation infrastructure**. Hourly SVIDs mean no
CRL or OCSP is needed, because a compromised identity expires before it is worth much. That
trade only works if something can **reissue** — and a leaderless offline mesh has no SPIRE
server to reissue from. Both horns are bad:

- **Long-lived credentials** — the mesh keeps working, but the revocation substitute is gone
  with nothing replacing it. A compromised node stays trusted until expiry and there is no
  way to tell anyone.
- **Short-lived credentials** — the security model holds, but the mesh stops working once it
  has been offline longer than the SVID lifetime, which for a HaLOW deployment is likely
  most of the time.

Neither is acceptable as stated, and this does not appear resolvable inside the SPIFFE model
as-is. It needs either defined connectivity windows for reissuance, or something
structurally different.

**Direction worth exploring (not a conclusion):** with no authority reachable, "is this peer
who it claims" may be better posed as a justified-belief question than a
signature-verification one — multiple independent attestations from nodes that have
themselves been attested, weighted by provenance, rather than one CA's say-so. That is close
to how the survey adjustment work already reasons about trust in observations, and it
degrades gracefully when the authority is unreachable, which is the failure mode PKI handles
worst. See [[corroboration-framework]], [[attested-capability-matching]],
[[spiffe-halow-mesh-federation]]. **Flagged as a direction, not a design** — it is a large
claim and has not been tested.
