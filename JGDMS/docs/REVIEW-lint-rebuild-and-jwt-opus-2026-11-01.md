claude-opus-5-5
# Adversarial review: c96183b6d (PolicyLint rebuild) and b4b9bd562 (JWT), 2026-11-01

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run.
Copy: `git archive HEAD` (c96183b6d) extracted to `<SP>\opus-r6\src`, where SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad`.
Fixtures `<SP>\opus-r6\adv\*.policy` (42 files); results `<SP>\opus-r6\adv-results.txt`; driver `<SP>\opus-r6\run.sh`.
Lint command per file and provider: `java [--add-exports java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED] -cp <tools classes;platform classes;jgdms-collections> org.apache.river.tool.PolicyLint --provider=platform|dirtychai [--strict] --format=tsv FILE`.
JWT probes: scratch JUnit classes added to the copy only (`OpusReviewVerifierTest` in jgdms-platform, `OpusReviewBindingTest` in jgdms-jeri), run with `mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=...`.
**Deviation, operator please note:** my first build ran `mvn -o -q -pl jgdms-platform,tools/policy-condenser -am install -DskipTests` in the copy, which wrote 4.0.0-SNAPSHOT jars for those modules and their upstream modules into `C:\Users\peter\.m2\repository`. They were built from an unmodified HEAD archive (live tree was clean), so they should match what trunk builds, but `~\.m2` was written. All later runs were `test` only, and every mutant was restored before the next one ran.

## Findings, ranked by exploitability

**1. JWT: principals are not bound to the token, and no token is needed at all. CONFIRMED.** (This refutes the report's "attacker holding a valid token" premise: the attacker needs no token.)
`readUserSubjects` (BasicInvocationDispatcher ~2299-2346) builds each user Subject from wire `(className, name)` pairs (the allowlist includes X500Principal, KerberosPrincipal, SpiffePrincipal and JwtPrincipal) and calls `verifyJwtWithCache` only on the tokens that are present. Nothing compares the claims to the principals, and `jwtCount = 0` skips verification. `dispatch` then runs the call under `UserSubjectSupport.callAsAll(userSubjects, ...)` (~2211), so principal-scoped grants and `ClientUserSubject` readers see the forged identity. The client picks the version byte (`VERSION_WITH_PRINCIPALS_AND_ACC`) that turns the block on.
Run with the real signature-mode `DefaultJwtVerifier(ISS, "svc-a", "RS256", {k1})` installed:
```
OPUSPROBE bob's valid token + principals alice/admins => accepted, principals=[CN=alice, JwtPrincipal[sub:alice@example.org], JwtPrincipal[group:administrators]]
OPUSPROBE no token at all + principals alice/admins, verifier installed => accepted   (same 3-principal set)
OPUSPROBE no token, no verifier => accepted   (same set)
```
The attack: any peer that gets through the endpoint's transport constraints sends a user Subject that contains `JwtPrincipal("sub:alice@example.org")`, `JwtPrincipal("group:administrators")` or another workload's `SpiffePrincipal`, either with no token or with its own valid token. It then gets every `grant ... principal net.jini.security.jwt.JwtPrincipal "..."` permission. The JwtPrincipal javadoc example policy is exactly this kind of grant. So the commit's fail-closed change only refuses a token that fails verification; it does not refuse a forged identity.
Minimal design (not written into the repo):
- (a) Change the SPI to `VerifiedJwt verify(String raw)`, or `Set<Principal> verify(...)`. The return is an immutable value carrying `sub`, `iss`, `aud`, `exp` and the principals derived from the verified claims (`JwtPrincipal("sub:"+sub)`, one `JwtPrincipal("group:"+g)` per group). The derivation rule belongs to the verifier because only it has parsed the signed claims.
- (b) In `readUserSubjects`, refuse any wire `JwtPrincipal` whose Subject carries no token. With tokens, replace the wire JwtPrincipals with the union of the derived principals, or refuse if a wire JwtPrincipal is not in that set.
- (c) Refuse wire principals of the other allowlisted classes in a user Subject unless an explicit rule lets the authenticated peer (its ClientSubject) assert them on someone else's behalf, e.g. a permission checked against the peer. Today the peer's say-so is the only evidence.
- (d) The cache value becomes `(until, derivedPrincipals)`, so a cache hit returns the principals the verifier derived and never the wire ones. The key stays the token's SHA-256.
- (e) Keep a `void` legacy verifier possible only as "accept nothing that needs principals": with a legacy verifier, refuse every JwtPrincipal.

**2. Lint: dangerous policies exit 0 by default. CONFIRMED** (both providers gave identical results on every file; default mode, `--strict` in brackets):
| fixture | real provider grants AllPermission to | kinds | exit (strict) |
|---|---|---|---|
| `grant codeBase "https://*/-"` (also `https://*.com/-`) | every https codebase: `https://evil.example.com/evil.jar` with no principal (my `Probe.java`) | S1 | **0** (1) |
| `principal * *`, `X500Principal *`, `X500Principal "*"` | any code run by any authenticated subject (VERDICT principal=true) | S3 | **0** (1) |
| `principal com.sun.security.auth.UserPrincipal *` | any code run by any UserPrincipal (`Probe`: all 9 codebases with `/p2`) | S3; VERDICT principal=false | **0** (1) |
| `codeBase "file:/-"`, `"file:${/}-"` | all local file code (VERDICT file=true) | S1 | **0** (1) |
| `codeBase "jrt:/-"` | every module (VERDICT platform=true) | S1 | **0** (1) |
| unscoped `SecurityPermission "*"`; `RuntimePermission "setSecurityManager"`; `SecurityPermission "setPolicy"` + `createClassLoader` + `suppressAccessChecks` + `<<ALL FILES>> execute` | AllPermission-equivalent | none | **0 (0)** |
| unscoped `GrantPermission "java.security.SecurityPermission \"setPolicy\""` | can grant itself setPolicy, then anything | none | **0 (0)** |
Causes. `PolicyVerdict` probes exactly one remote URL, `http://evil.example.com/evil.jar`, with no port and an X500Principal only. `lint()` turns only `stranger` into a failing kind (U3): `fileCode` and `platformModule` are computed, printed in the VERDICT line, and never affect the exit code. Only the AllPermission class is asked about. The https row is the lint-clean case where the provider grants AllPermission to a stranger. The last two rows fall outside the tool's stated AllPermission-only scope, but a gate that passes them is not a gate against dangerous policy.
Fix direction: probe several schemes, ports and principal classes; make `file`/`platform`/`principal` verdicts failing kinds; ask about a short list of sandbox-escape permissions (setSecurityManager, setPolicy, createClassLoader, suppressAccessChecks, `<<ALL FILES>>` execute, GrantPermission of any of these), not only AllPermission.

**3. Lint: a directory walk silently skips policy files whose names it does not expect. CONFIRMED.**
Dir `d1` = `good.policy` (clean) plus the unscoped AllPermission policy saved as `java.Policy`, `server.pol` and `all.policy~` gives `TOTAL files=1 ... exit=0`. Linting `d1/java.Policy` as a file gives U1, exit 1. `builtInMatch` is case-sensitive (the file system here is not). The runtime loads any name given by `java.security.policy`. Passing an empty directory next to a good file also gives exit 0, with no note that the directory held nothing. An empty directory alone correctly gives exit 2.

**4. JWT: the claims parser lifts members of nested objects to top level. CONFIRMED.**
Both probes are signed RS256, expected aud `svc-a`:
- `{"aud":"svc-b",...,"profile":{"n":1,"aud":"svc-a"}}` gives ACCEPT.
- `{"exp":<now-3600>,...,"profile":{"n":1,"exp":<now+3600>}}` gives **ACCEPT: an expired token is accepted**.

`readRawScalar` stops at the first `,` inside the nested object, and the outer loop then reads the rest as top-level keys (last write wins). This is exploitable when the issuer signs a nested claim object an attacker can influence. Separately, `extractJwtExp` takes the first `"exp"` text match, so the cache TTL can disagree with the verified exp; the 300 s cap bounds that. The parser needs to reject duplicate keys and skip nested values properly, or be replaced by a strict parser.

**5. JWT audience handling. CONFIRMED.**
- Scalar `aud:"evil,svc-a"` and `"evil, svc-a"` both give ACCEPT against expected `svc-a`. This is the agent's open item B, and it is true. It is exploitable where the IdP lets a client choose the audience string.
- The 4-arg signature-mode constructor accepts `expectedAudience = null`, and then `aud:"svc-b"` gives ACCEPT. Any token for any service at the same issuer replays to this one. The commit's own dispatcher test configures it this way (`new DefaultJwtVerifier(ISS, null, "RS256", ...)`).
- `typ` is not checked: `typ: refresh+jwt` gives ACCEPT.
- `exp` 253402300799 (year 9999) gives ACCEPT, because there is no maximum lifetime. `exp` given as a string gives ACCEPT.
- Recommendation: make audience mandatory in signature mode, match `aud` as a JSON array or scalar exactly with no comma splitting, and add a maximum lifetime.

**6. Tests that do not catch a mutation (one mutation each, applied to the copy, restored after). CONFIRMED:**
| mutant | test class | result |
|---|---|---|
| JW1 `crit` check removed | DefaultJwtVerifierNegativeTest | killed (1 failure) |
| JW2 ES256 `rawToDer` passes a non-64-byte signature through instead of refusing | same | **survived (24/24 pass)**: no ES256 negative test |
| JW3 `iat`-in-future check removed | same | **survived**: no iat test |
| JD1 cache key = raw token | JwtDispatcherFailClosedTest | killed (`verifiedTokenIsCachedAndCacheDoesNotRetainTheToken`) |
| L1 verdict-derived S3 branch removed | PolicyLintTest | **survived (36 run, 0 fail)** |
| L2 provider-rejection P1 removed | PolicyLintTest | killed (4 failures) |
| L3 `.policy.old` match removed | PolicyLintTest | survived, but equivalent: `contains(".policy.")` still matches |
The mutant with the most impact, "verdict file/platform ignored", is what HEAD already does (finding 2). No test asserts that a `file:/-` or `jrt:/-` AllPermission grant fails.

**7. PLAUSIBLE.**
- (a) `--define` and the lint JVM's own system properties are trusted as the runtime's. A property defined for the lint but not in the service JVM turns a reported S1 into an unscoped grant in production. U4 cannot see this.
- (b) The default `auto` provider falls back to jgdms-platform unless the add-exports flag is passed (the ant target does not pass it by default). The two providers agreed on all 42 of my files, so I found no divergence.
- (c) `ProxyPolicyGenerator.parseGrants` text changes are shared with the proxy generator. I ran no generator regression beyond the agent's.

## Run and found fine (CONFIRMED)
- **Lint:** these exit 1 by default and under `--strict`:
  - unscoped AllPermission (U1);
  - `${undefined.prop}` codeBase (U3+U4);
  - comment-hidden, odd whitespace (`\t\f\v`, CRLF), second grant block, `gr\u0061nt` and `java.security.\u0041llPermission`, uppercase `GRANT`, AllPermission with name/actions (all U1 via the verdict or the text);
  - `http://*/-` (U3);
  - GrantPermission AllPermission, GrantPermission `*`, UmbrellaGrantPermission (U2);
  - parse-rejected files: a `\` continuation, an unterminated block, `codeBase "http:"`, an undefined principal class (P1).
- **Lint, no AllPermission granted (so S1/S2 is a fair report):** `signedBy` with an unknown alias, `signedBy "${undef}"`, a bad keystore URL, an undefined principal name, and an undefined digest.
- **Lint, path handling:** an empty directory, a directory with only `.txt`, and a missing path all exit 2. A file and its directory give the same result. Null CodeSource is never granted.
- **JWT refusals:** `alg none`; HS256 MAC keyed with the RSA public key; wrong kid; no kid with 2 keys; kid k2 signed by k1; truncated signature; another token's signature; nbf in the future; iss mismatch; a 70000-char token; ES256 DER-form signature; ES256 r=s=0; an RS256 token sent to an ES256 verifier.
- **JWT acceptances:** ES256 raw R||S is accepted.
- **JWT malleability:** a high-S ES256 signature and a signature with `==` appended are both accepted. This is harmless for authentication. It matters only if something treats the token string as unique.
- **Cost:** 2000 good RS256 verifies took 342 ms, and 2000 junk 64 KiB tokens took 285 ms.
- **Dispatcher (code read, plus JD1):** the cache key is SHA-256 of the token. Entries are written only after the verifier accepts, into the state object read at entry, so a verifier swap cannot be poisoned. Size is bounded at 1024 under a lock. The `len < 0` wire check is present. A RuntimeException from the verifier is a refusal, and an Error reaches the dispatch-level catch that aborts the request.

## Not checked
- The ant `policy-lint` target (no ant run).
- Linux and macOS.
- The JDK default `java.policy` or `policy.url.N` files combined with a linted file at runtime.
- A real polpAudit or JERI end-to-end call over TLS: finding 1 was shown at `readUserSubjects`, not over a socket.
- Concurrency of the verifier swap.
- `JwtLoginModule`.
- The agent's 28 fixtures and 41-file corpus, beyond their test suite passing under my mutants.

## For the operator to settle
1. On DirtyChai, do principals from a *user* Subject (`callAsAll`) satisfy `principal SpiffePrincipal` grants the same way the worker's do? If yes, finding 1 also lets a peer impersonate another workload, not just a user.
2. Which peers are meant to be allowed to assert user principals at all (any authenticated peer, or a named gateway)? The design in finding 1(c) depends on this.
3. Should PolicyLint stay AllPermission-only, which makes finding 2's last two rows out of scope, or become a "dangerous policy" gate?
4. The `~\.m2` SNAPSHOT jars I installed (see Deviation): rebuild from trunk if you want certainty.
