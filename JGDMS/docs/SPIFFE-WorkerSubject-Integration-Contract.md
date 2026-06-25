# SPIFFE / DirtyChai `WorkerSubject` ↔ JGDMS Integration Contract

Status: design / advisory (2026-06-24). DirtyChai side is Peter's to implement (advise-only);
JGDMS side is the consumption work. Goal: collapse JGDMS's duplicate SPIFFE subject machinery
into DirtyChai's single ambient `WorkerSubject`, so connection identity == the ambient process
identity the policy actually checks, and the dynamic codebase-download grant authorizes uniformly.

## Why
Today JGDMS carries its own SPIFFE stack in `jgdms-jeri net.jini.jeri.ssl`:
`SpiffeCredentialManager` (reads the SVID), `SpiffeLoginModule` (JAAS), `SpiffePrincipal`,
`AuthManager.getSubject()` (returns a `WeakReference` subject), and `SpiffeSubjectHolder`
(a process-wide `AtomicReference<Subject>` — explicitly a workaround because
`Subject.getSubject(AccessControlContext)` is gone). DirtyChai independently has
`au.zeus.jdk.authorization.spire.SpiffeCredentialManager` + the ambient `WorkerSubject`
(`javax.security.auth.WorkerSubject`, `sealed permits SpiffeSubject`), read via
`Subject.processWorker()`, with the immutable leaf-cert `X500Principal` stamped into
`DomainIdentity` PDs by `SecureClassLoader`. Two readers of the same SVID = the tension.
See memory `dirtychai-subject-model`.

## Division of responsibility
- **DirtyChai (java.base):** `SpiffeCredentialManager` owns SPIFFE subject acquisition + rotation
  of the ambient `WorkerSubject`; `Subject.processWorker()` is the always-current accessor — **NO
  separate holder** (a holder would just be a staleness-prone cache in front of an already-current
  source); `SecureClassLoader`/`DomainIdentity` stamp the immutable principal into PDs for authz.
- **JGDMS:** *consumes* the ambient `WorkerSubject` for connection credentials; relies on
  `DomainIdentity`-stamped principals for authz (injects nothing). Retires its own
  `SpiffeSubjectHolder`/`SpiffeCredentialManager`/`SpiffeLoginModule` (or thins them to consumers).

## What JGDMS needs to READ from the ambient `WorkerSubject`
1. **Connection credentials** (jeri-SSL handshake): the *current* X.509 SVID cert chain + private
   key (the subject's private credentials). Consumed by `AuthManager`/`SslEndpointImpl` to present
   the local cert + sign in the TLS handshake.
2. **Local identity**: the immutable SPIFFE `X500Principal` (the subject's principal) — for
   "which local principal does this connection authenticate as" / constraint decisions.
3. **Authz: nothing.** The immutable principal is already in the (`DomainIdentity`) PDs; policy
   `grant principal javax.security.auth.x500.X500Principal "<spiffe-leaf-DN>" { ... }` matches it.
   JGDMS must NOT build or inject principals.

## How connections obtain the LIVE (rotating) subject
- The JERI connection obtains the subject **fresh on every invocation** via
  `Subject.processWorker()` (by reflection — JGDMS compiles against vanilla Zulu 21 where it is
  absent; see `dirtychai-jdk` build gotcha). Pulling fresh per call always yields the current SVID
  after rotation (~hourly); the connection logic reuses or re-handshakes based on the current subject.
- **This obviates BOTH a JGDMS-side holder AND a rotation event API.** There is nothing to cache
  (no `SpiffeSubjectHolder`, no wrapper) and nothing to be notified of (rotation is transparent —
  every call already reads the latest). The principals are rotation-invariant (immutable), so authz
  never needs refreshing either. (An event API would only earn its keep if some consumer needed to
  *react* to rotation rather than read-on-use — not the case for per-invocation connections.)

## Contract DirtyChai must satisfy (no holder)
- A **readable accessor** for the ambient `WorkerSubject` reachable from JGDMS (reflection):
  `Subject.processWorker()` (or a dedicated public accessor).
- `getPrincipals()` → the immutable SPIFFE `X500Principal`(s) (leaf-cert subject DN).
- Private credentials → the **current** SVID (cert chain + private key), readable under a defined
  permission (`PrivateCredentialPermission`/equivalent) so JGDMS endpoints can present them.
- **Atomic rotation**: `SpiffeCredentialManager` swaps the credentials on SVID rotation behind
  `Subject.processWorker()`; principal unchanged.
- **`DomainIdentity` stamping**: immutable principal present in class-loaded PDs (authz). Note the
  caveat — *synthetic* PDs (`new ProtectionDomain(cs,perms,null,null)`, e.g.
  `PreferredClassLoader.getLoaderAccessControlContext`) get NO principal; those stay matched by
  URI/codebase grants (which is why the dynamic codebase grant is URI-scoped, not principal-scoped).
- (Optional) a rotation listener/event API.

## JGDMS-side changes (consumption)
- `net.jini.jeri.ssl.AuthManager.getSubject()` → return the ambient `WorkerSubject`
  (`Subject.processWorker()`, reflection) instead of its private `subjectRef`.
- `SslEndpointImpl`/handshake → take cert chain + key from the `WorkerSubject`'s current creds.
- Retire `SpiffeSubjectHolder` (process `AtomicReference` workaround), JGDMS-side
  `SpiffeCredentialManager` (SVID reader; `-Dnet.jini.jeri.ssl.spiffe.dir` becomes redundant —
  DirtyChai reads the SVID), and `SpiffeLoginModule` (or keep only as a thin adapter).

## Open DirtyChai-side decisions (Peter)
1. Make `Subject.processWorker()` (or a dedicated accessor) callable by JGDMS — confirm visibility.
2. Permission gating private-credential read for endpoints.
3. Rotation: handled by per-invocation `processWorker()` pull — no holder/wrapper/event API
   needed (confirm no consumer requires reactive notification).
4. Confirm one-ambient-`WorkerSubject`-per-process matches JGDMS's old "1 SVID per host" assumption.
5. `WorkerSubject` sealing stays (JGDMS only reads via the `Subject` API; no JGDMS subclass).
