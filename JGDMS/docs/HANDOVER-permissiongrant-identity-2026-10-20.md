# Handover: PermissionGrant mirror, DigestDomainGrant, verdict binding, JERI authenticateServer

**AS-OF 2026-10-20, branch `claude/sow-permissiongrant-domain-grants`, trunk at `25b5b013` (source: `git log -1`).**
Everything after that commit is not reflected here: run `git log --oneline 25b5b013..HEAD` first.
Format follows `HANDOVER-der-codec-performance-2026-10-18.md`. No `handover` skill existed in the session that wrote this.

## 1. Where the truth lives

| Question | File |
|---|---|
| Scope, steps, decisions (sections 6-8) | `JGDMS/docs/SOW-PermissionGrant-Mirror-and-ProtectionDomain-Grants.md` |
| Investigation, review verdicts, decisions taken and open | `JGDMS/docs/DESIGN-dynamic-grant-solutions-2026-10-18.md` |
| JERI authenticated-server accessor design | `JGDMS/docs/DESIGN-jeri-authenticated-server-accessor.md` |
| What DirtyChai must change (humans write it; DirtyChai forbids AI contributions) | `JGDMS/docs/DirtyChai-Required-Changes-PermissionGrant-SOW.md` |
| Review heuristics | `.claude/skills/jgdms-board-review/SKILL.md` |

## 2. State, with provenance

- Done and merged to trunk: `DigestDomainGrant` (context DIGEST_DOMAIN=7, inlined constant, must match DirtyChai), `ClassLoaderGrant`, `CodeSourceKey` digest helpers, builder refusals, signer/URI/digest grant hardening; verdict binding in `PreferredProxyCodebaseProvider` (hash URN compared, freshness, `VERDICT_MAX_AGE_MS_PROPERTY`); `BasicInvocationHandler.authenticateServer(Method)` (exact-class checks, `ContextPermission` gate, one ServerSubject, immutable principals); Kerberos `ServerSubject` plus mutual-auth check.
- Tests I ran earlier (javac + JUnit harness, not Maven): verdict test 92, server-identity, relay-stream, `AuthenticateServerTest` 8 passed WITH SpiffePrincipal compiled in. The harness scripts were in the session scratchpad and are gone; this session did NOT re-run anything after the trunk pull.
- `AuthenticateServerTest` failure on Maven: `SpiffePrincipal` is in jgdms-lib-dl, which depends on jgdms-jeri, so it is absent from jeri's test classpath and the SSL ServerSubject holds only the X500 principal. Three tests failed for that reason. Fix committed with this handover (`spiffeAvailable()`, `Assume`, conditional assertions). UNVERIFIED under Maven: run `mvn -o -pl jgdms-jeri test -Dtest=AuthenticateServerTest`.
- Weakness of that fix: with SPIFFE absent, the SPIFFE-pinning behaviour is not tested in jgdms-jeri at all. A better home for those tests is jgdms-lib-dl (or a module that has both).

## 3. Decisions taken

- Identity comes from the bootstrap proxy's own authenticated call (`authenticateServer`), never the delivering stream (relay case is real on Atomic JOSS). Pin must be a single principal (`ServerMinPrincipal` accepts only one).
- Remotely reconstructed domains (RemoteContextCodec) are never "verified"; `DigestGrant` does not imply them, `DigestDomainGrant` may.
- Process isolation is the only true class isolation.
- Fable seat cannot be the security reviewer; board is one Fable, one Sonnet, one Opus.

## 4. Open decisions and my recommendations

1. Steady-state identity gate beyond the verdict: recommend NO. `BootstrapPermission` stays boot-window only (lookup services/registries); the signed hash-URN verdict plus pinned identity at resolve covers steady state, and reviewers withdrew the steady-state move.
2. `checkBootstrapPermissionByDigest` (non-TLS digest boot check): recommend KEEP until a replacement exists. The new identity rule would silently remove it for non-TLS transports. Dropping it must be an explicit decision with a negative test, not a side effect.

## 5. Next steps

1. Run the Maven test above; fix if red.
2. In `PreferredProxyCodebaseProvider.resolve`: check handler class, call `authenticateServer` with the `CodebaseAccessor.getClassAnnotation` Method, require exactly one SpiffePrincipal or the single KerberosPrincipal, pin via `ServerMinPrincipal` + `ServerAuthentication.YES` before any further call, delete the stream-context `ServerSubject` read. A failed probe takes an explicit refuse/unverified branch. Negative tests required.
3. Then: loader expected-hash map, directory/Class-Path gating, provider-private registry with read-only SPI + `VerifiedCodebaseProxyPreparer`, remote-attested tier.
4. DirtyChai (human-authored): `issued(pd)` first, narrowed R1, write-once `readExternal`, permission-gated verified factory, principal-merge gate.

## 6. Build recipe

Maven does not build here (Pack200 dependency). Use `javac` with a cross-module `-sourcepath`; add `--enable-preview --release 21` for jgdms-der and jgdms-jeri (ScopedValue); compile `MarshalDelegateProcessor` first and run it as the annotation processor or JOSS fails with `serialForm() not reachable`; digest tests need a stub `java.security.DigestCodeSource` via `--patch-module java.base=...`. `Subject.callAs` wraps exceptions in `CompletionException`: unwrap. Do not put `rm -rf` on variable paths in shell loops (safety check blocks it); use fresh output directory names.
