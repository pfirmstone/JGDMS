# Handover — reggie discovery thread (branch `fix/reggie-discovery-backend-proxy`)

**Written:** 2026-08-29. **Provenance caveat:** the author session's live context is from **2026-07-27**
(Bug 2 fix + jgdms-atomicserial skill + Bug 1 round-1/2 diagnostics — the only work it did first-hand).
Everything dated 07-31 onward below is **reconstructed from memory + the working tree, not first-hand** —
treat the cited authoritative docs as ground truth over this file.

## Authoritative sources (READ THESE, not my 07-27 conclusions)
- Memory **`jgdms-discovery-der-nested8-resolution-gap`** — RESOLVED 2026-08-01. Supersedes the 07-27
  "nested-[8] resolution gap is a defect" framing.
- **`JGDMS/docs/DESIGN-discovery-credential-resolution.md`** — primary reference: full analysis, impl log,
  open items. (Header still says "analysis only — no code changed"; memory says code LANDED uncommitted —
  see discrepancy note below.)
- **`scratchpad/bug1-diagnostics-removal-map.md`** — round-4 diagnostic inventory + removal recipe
  (BUG1-DIAG tags). Currently at ROUND 4.

## Verified git state (checked 2026-08-29)
- Branch **`fix/reggie-discovery-backend-proxy`**, HEAD = **`f1db486e8`** — UNCHANGED since 07-27
  (tip = the two commits this author landed: `78a53e5f7` Bug 2 ConstrainableAdminProxy, `f1db486e8` skill).
- Branch is **4 ahead / 40 BEHIND `origin/trunk`** (trunk tip `41f6a2106`). Diverged and well behind —
  a rebase/merge onto current trunk is owed before this can land.
- **29 uncommitted changes** in the working tree (24 modified + 5 untracked), mixing THREE things:
  1. **Real fix — discovery credential resolution (SPIFFE)**, per the resolved memory:
     `jgdms-discovery-providers/.../internal/X500Provider.java`, 4× `x500/*/Constants.java` (§8.4 OID
     fixes), `jgdms-platform/.../net/jini/security/Security.java`, untracked `SpiffeTrustProvider.java`,
     `internal/SpiffeSupport.java`, `discovery/spiffe/` package. Memory: "LANDED, compiling, UNCOMMITTED";
     remaining = §5.1 packet work, wrappers, service registration.
  2. **Temporary BUG1-DIAG diagnostics (rounds 1/3/4)** — across `ObjectCodec`, `ProxyWireSupport`,
     `DerObjectStreamCodec`, `MarshalInputStream`, `AbstractLookupDiscovery`, `Plaintext`,
     `ProxySerializer`, `PreferredProxyCodebaseProvider`, `RegistrarImpl`, and service diagnostics in
     `fiddler`/`mercury`/`norm`. ALL tagged `BUG1-DIAG` / `// TEMP diagnostic, remove`. Must be stripped
     before commit (removal map has the recipe).
  3. **qa config/policy** — `qa/harness/configs/jsse/test.config`, `defaultsecure{test,phoenix}.policy`
     (polpAudit output, Peter's — not the author's), `qa/src/.../qa1.logging`.
- Untracked docs: this file, `DESIGN-discovery-credential-resolution.md`, the stale
  `SOW-...-2026-07-26.md`.

## The two findings (per 08-01 resolution — NOT the 07-27 story)
1. **DER `[8]` `Registrar` drop is BY DESIGN, do not "fix".** Traced to `DerProxySerializer(GetArg)`
   eagerly decoding the `ActivatableInvocationHandler` graph (incl. the nested `[8]` reggie stub) under
   the stream loader *before* the codebase is known — the bootstrap hop precedes the endpoint loader's
   existence, so `resolveTolerant` narrowing is the intended graceful degradation. The author's 07-27
   "thread the endpoint ResolutionContext into the nested [8]" fix is **circular** — see memory for why.
   Worth doing instead: make `resolveTolerant` quiet on bootstrap decode vs fail-loud when a codebase IS
   present (Peter's STD-009 §6.4 call).
2. **0-events ROOT CAUSE = discovery Subject credential resolution.** `X500Provider.getPrivateCredentials()`
   asks `Subject.getSubject(AccessController.getContext())` (the closed `callAs`-bound back-door); for
   SPIFFE workload identity it must obtain `Subject.processWorker()` (a process-wide singleton). Plus 4
   §8.4 key-vs-signature OID bugs (cert lookup never worked, failed silently). Options 1+4 ADOPTED by
   Peter 2026-08-01. NOTE: an earlier "zero multicast announcements arrive" hypothesis (removal-map
   round 4) was refined by the credential-resolution finding — nothing gets *encoded*, so nothing sends.

## Risks / landmines for the resuming session
- **Big mixed uncommitted diff:** real SPIFFE fix + throwaway diagnostics + qa tweaks all interleaved.
  Separate them: strip BUG1-DIAG (removal map), commit the SPIFFE credential work as its own change,
  leave Peter's polpAudit policy files alone.
- **Branch 40 behind trunk** — reconcile with `origin/trunk` (`41f6a2106`) before landing; expect the DER
  codec area to have moved (trunk had Outrigger CEL DER work in flight).
- **Doc discrepancy:** `DESIGN-...credential-resolution.md` header says "no code changed" but memory says
  code landed uncommitted. Trust the working tree (code IS present) + memory; the doc header is stale.
- **Stale doc to delete/supersede:** `SOW-Discovery-DER-BackendProxy-and-AtomicSerial-2026-07-26.md`
  (author's 07-26 SOW) — its Bug 1 plan (roots X/Y) is fully refuted; keep only for history or remove.

## Suggested next moves
1. Read the resolved memory + `DESIGN-discovery-credential-resolution.md` §11.6 / open items.
2. Strip all BUG1-DIAG diagnostics (removal map recipe) — unless still actively debugging.
3. Finish the credential-resolution remaining work (§5.1 packet, wrappers, service registration).
4. Rebase branch onto `origin/trunk`; commit the SPIFFE fix cleanly; then re-run the joinmanager td.
