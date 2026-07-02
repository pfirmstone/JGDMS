# SOW — Role-Neutral Worker Bootstrap `main`

*Companion to [DESIGN-attested-role-neutral-worker.md](DESIGN-attested-role-neutral-worker.md).
Scopes the one static executable that turns an attested identity into a running role.
Status: SOW / not started.*

## 1. Problem

The role-neutral-worker design needs exactly one role-agnostic executable baked into
the image: a bootstrap `main` that, given the worker's SVID, downloads and enacts the
plan for its identity (codebases + configuration + start sequence). It exists because
you cannot `java -cp https://…` — the system class loader takes file paths, not
protocol URLs — so a minimal launcher must be present statically to perform the first,
pre-runtime fetch. It is part of the TCB (it decides what loads), so the overriding
constraint is **minimal and fail-closed**.

## 2. Scope — what the bootstrap does

A single sequential flow, each step failing **closed** (become no role; never fall
back to loading arbitrary code):

1. Obtain the SPIFFE Subject / SVID (from the SPIRE Workload API via DirtyChai's
   `SpiffeCredentialManager`).
2. Contact the bootstrap HTTPS server over **SPIFFE mTLS** — present own SVID, and
   **verify the server's SVID** (e.g. `spiffe://…/host/bootstrap`) against the trust
   bundle before trusting anything it returns.
3. Retrieve the identity-scoped **plan**: `{ codebase digest-grants, Configuration,
   start sequence }`.
4. Install the policy so `LoadClassPermission` is set for exactly the granted digests.
5. Fetch each granted codebase from **any URL** (plain `https`/`file`/CAS via stock JDK
   handlers); the class-defining `SecureClassLoader` computes + stamps the content digest,
   and the two-factor gate (digest + `LoadClassPermission`) admits only granted content.
6. Apply the `net.jini.config.Configuration`; a config requesting a capability outside
   the grants fails closed under the SecurityManager.
7. Execute the **start sequence** (a declarative launch plan) — instantiate the entry
   codebase / `main` (ServiceStarter-style hand-off). From here the worker enters the
   JERI/Jini world.

## 3. What it owns vs. what it does NOT

**Owns:** SVID acquisition wiring; the SPIFFE-mTLS HTTPS client + mutual auth; plan
parsing + validation; policy install; digest-verified codebase fetch orchestration
under the gate; config application (bounded-by-grants); the declarative start-sequence
interpreter; fail-closed error handling.

**Does NOT own (separate items):**
- the **bootstrap HTTPS server** and the role→plan authoring (server-side).
- SPIRE deployment / node-attestor configuration.
- (Prerequisite, **DirtyChai** `java.base`, advisory:) the `SecureClassLoader`
  **digest-stamp gate** — the class-defining loader computes the content digest of fetched
  bytes and gates on `LoadClassPermission` for that digest (flips the existing per-*URL*
  grant in `PreferredProxyCodebaseProvider` to per-*digest*). This replaces httpmd on the
  worker path; httpmd stays a plain JGDMS jar for legacy proxy-codebase integrity only.
  Humans write this `java.base` source ([SOW-httpmd-JPMS-Module.md](SOW-httpmd-JPMS-Module.md)
  is retired).
- the **digest-only grant convention** (comment the `codeBase` URL) in policy
  generation — a `tools/policy-condenser` change.
- **stateful-role** state binding (identity-scoped volumes / decryption).
- the **offline cached-plan** path beyond the minimal hook (see P5).

## 4. Module & build placement — JPMS static root, mind the highlander principle

The bootstrap `main` does **not** belong in DirtyChai `java.base`; it stays a **JGDMS**
artifact, built as an explicit **JPMS module** (`module au.net.zeus.jgdms.bootstrap`)
and launched with `java -p <static-modules> -m au.net.zeus.jgdms.bootstrap/<Main>`.
Launching a *resolved module* (rather than a classpath) is itself the clean
chicken-and-egg breaker — no `-cp` file-path list, no `java -cp https://…`. The bootstrap
module is the **only** explicit module the worker needs: the integrity half of the
two-factor gate is the `java.base` `SecureClassLoader` **digest stamp** (boot-loaded,
unoverridable), so — unlike the earlier design — **no httpmd module is required** (that
SOW is retired; httpmd stays a plain JGDMS jar for legacy proxy-codebase integrity).
Fetching uses stock JDK URL handlers (`https`/`file`), because the URL is untrusted and
the stamped digest is what the gate keys on. Being resolved into the boot/app module layer
at launch makes the bootstrap module **non-overridable by downloaded code** (which loads
into child/codebase classloaders, i.e. the unnamed module) — and the digest stamp stays
trustworthy because it lives in `java.base`. A `module-info` is ignored on the classpath
and coexists with the existing bnd/OSGi manifest, so this stays non-breaking for
non-modular use.

Run target: these run on the **worker's DirtyChai JVM** (not downloaded to arbitrary
clients), so the `-dl` Java-8-floor rule does **not** apply — they can be modern
modules (and `module-info` needs 9+ anyway; a multi-release jar can keep the classes at
a lower floor if ever needed).

**Highlander caveat (JPMS "there can be only one").** JPMS demands each package be owned
by exactly ONE module, unique module names, and one `URLStreamHandlerProvider` winning
per scheme. JGDMS today is **entirely non-JPMS** (0 `module-info`; OSGi/bnd, which
tolerates flexible sharing) and has split-package history — wholesale JPMS-modularising
JGDMS would collide immediately. Therefore:

- **Confine explicit JPMS modules to the minimal STATIC ROOT** (just the bootstrap
  module + only what it strictly needs), **self-contained** — depending on `java.base`
  (+ DirtyChai-exported SPIFFE APIs), **not** on the broader split-package JGDMS jars.
- **Do NOT modularise the rest of JGDMS.** The downloaded runtime (all of JGDMS + role
  code, split packages + OSGi manifests) loads **dynamically into child/codebase
  classloaders** (the unnamed module), where the one-package-one-module rule does not
  bind it. We get a clean modular static root without untangling JGDMS's package layout.
- **Split-package rule (the JGDMS invariant, Peter 07-02):** the ONLY split package
  permitted in JGDMS is **`org.apache.river.api.security`** — and it exists **purely as
  a compatibility bridge for JGDMS classes that migrated INTO DirtyChai**: `java.base`
  holds the authoritative copy, and JGDMS retains the package only so non-DirtyChai /
  legacy classpaths still resolve it (on DirtyChai the `java.base` copy is what runs).
  Every other package is single-owner. The one rule for the static-root bootstrap module:
  it must **source `org.apache.river.api.security` from `java.base`** (DirtyChai owns that
  package in the module graph) and **never own or bundle it** — else it collides with
  `java.base` and JPMS rejects it. (This split is also precisely why the broader JGDMS
  jars cannot be wholesale JPMS-modularised — the same reason the abandoned httpmd-module
  effort would have had to migrate three more leaf types
  (`ExternallyVoidablePermissionGrant`/`AdvisoryDynamicPermissions`/`LocalPrincipalProvider`)
  into `java.base` — and why the downloaded runtime stays in the unnamed module.)

## 5. The plan wire format (P0)

Define a minimal, versioned, integrity-protected plan document:
- **codebases**: a list of digest-grants (algorithm + digest; a fetch-hint URL that is
  *not* authoritative — see the design doc §7). This IS the codebase list.
- **configuration**: the `Configuration` source (or a digest reference to it), itself
  content-pinned; must resolve within the grants.
- **start sequence**: declarative only (entry codebase/digest + main, ordering, config
  args). No embedded code. The interpreter must reject anything that isn't a
  whitelisted declarative step.
Delivered over the SVID-mTLS channel; the plan's authenticity = that channel (sign the
document too for defence-in-depth).

## 6. Verification / acceptance criteria

Green means all of:
- a worker with SVID `X` receives and enacts the plan for `X`, loading exactly the
  granted digests, and becomes the role;
- an **ungranted** digest named anywhere (plan or transitive dep) is refused by the
  gate (no `LoadClassPermission`) — the plan cannot escalate past the policy;
- a **tampered** codebase is rejected by the gate — its stamped digest holds no
  `LoadClassPermission`;
- a **rogue bootstrap server** (SVID not verifiable against the trust bundle, or wrong
  identity) is rejected before any plan is trusted;
- a **config** requesting a capability outside the grants fails closed under the SM;
- any auth/fetch/parse failure → **fail closed** (no role), never arbitrary-code
  fallback.
Test rig: a **mock SPIRE agent** (reuse the qa mock SPIRE agent) + a **mock bootstrap
HTTPS server**; plus one **end-to-end** test where a worker becomes a minimal service
(e.g. the hello-world service) purely from its SVID + plan.

## 7. Phasing

- **P0** — scaffold the bootstrap JPMS module (§4); plan wire format + interpreter
  skeleton (parse + validate only; no fetch). *(No httpmd module — integrity is the
  DirtyChai `SecureClassLoader` digest stamp, advisory.)*
- **P1** — SPIFFE-mTLS HTTPS client + mutual auth (present SVID; verify server SVID;
  fail-closed); uses the DirtyChai SPIFFE key/trust managers.
- **P2** — policy install + codebase fetch from any URL (stock JDK handlers) under the
  two-factor gate (SecureClassLoader digest stamp + `LoadClassPermission`).
- **P3** — `Configuration` retrieval + application + bounded-by-grants check.
- **P4** — start-sequence execution + ServiceStarter-style hand-off to the entry role.
- **P5** — fail-closed hardening + a minimal offline **cached-plan** hook
  (`{SVID, policy, config, sequence}` with expiry tolerance).
- **P6** — tests (mock SPIRE + mock server + end-to-end become-a-service).

## 8. Dependencies

Exist: the SPIFFE stack in DirtyChai (`SpiffeCredentialManager`/`X509KeyManager`/
`X509TrustManager`), `LoadClassPermission` / `DigestGrant` / `SpiffePolicyFile`,
`ServiceStarter` (`org.apache.river.start`), `net.jini.config.Configuration`,
`CodebaseAccessor`.
Blocked-on / parallel items (§3): the DirtyChai `SecureClassLoader` digest-stamp gate
(advisory — replaces the retired httpmd JPMS-module conversion), the bootstrap HTTPS
server + role→plan mapping, and the policy-condenser digest-only-grant convention.

## 9. Caveats / non-goals

- **TCB discipline:** the `main` is a declarative interpreter with *no role logic*;
  keep it small and auditable. Anything that could be role code belongs in a
  downloaded, gated codebase, not here.
- The server side and SPIRE topology are out of scope (separate SOWs/ops).
- The `java.base` variant is **DirtyChai** — advisory; the JGDMS thin-jar is the
  AI-implementable, testable form and the reference implementation.
