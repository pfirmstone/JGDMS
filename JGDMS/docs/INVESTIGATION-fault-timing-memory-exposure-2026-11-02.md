claude-sonnet-5-5
# Investigation: fault, timing and memory exposure (Rowhammer, key shielding, fault attacks) - 2026-11-02

Read-only survey. READ = code read (file:line); INFERRED = my reasoning. Paths: DB = DirtyChai `src/java.base/share/classes/`, JG = `JGDMS/JGDMS/`. The note about Aether was treated as a checklist, not as fact.

## Verdict per attack class
Operator ruling (2026-11-02, relayed): Rowhammer and cache/memory side channels cannot be defended when untrusted code is local; the control is physical separation (one board per identity, short lifetimes, no long-lived key on a board running untrusted code). Findings are framed as (i) hygiene for trusted hosts and (ii) non-local attackers (induced faults, remote timing).
| Class | Exposed? | Evidence | Confidence | Relevant if untrusted code is local? |
|---|---|---|---|---|
| RSA-CRT fault (Bellcore), software JCA | NO | `RSACore.java:208` re-encrypts and compares; both signers pass `verify=true` | High (READ) | Not defensible; separation is the control |
| ECDSA fault, software JCA | YES: no verify-after-sign; random (not hedged) nonce | `ECDSASignature.java:424-483` | High (READ) | Not defensible; separation is the control |
| EdDSA fault | YES: no verify-after-sign; deterministic | `EdDSASignature.java:200-213` | High (READ) | Not defensible; separation is the control |
| Fault in TLS CertificateVerify | PARTLY: RSA covered, EC/Ed not; TLS layer adds nothing | `CertificateVerify.java:86,340,612,939` release `signer.sign()`; `verify(` only on receive (144,397,675) | High (READ) | Not defensible; separation is the control |
| JGDMS own signers (3 services, none on TLS) | PARTLY: no verify-before-release | section B | High (READ) | Not defensible; separation is the control |
| Rowhammer / RAMBleed / cache | Not defended in code, by design; control is separation | section E | Medium (INFERRED) | Not defensible; separation is the control |
| Secret wiping / shielding | Absent in JGDMS; partial in JDK | section C | High (READ) | Not defensible; separation is the control |
| Remote timing on secret comparisons | NO material exposure found | section D | Medium-high | n/a (remote case) |

## A. DirtyChai signing
- RSA PKCS1: `RSASignature.java:193` `RSACore.rsa(padded, privateKey, true)`. RSA-PSS: `RSAPSSSignature.java:428` same, `verify=true`. READ.
- The check: `RSACore.java:208` `if (verify && !c0.equals(m.modPow(e, n))) throw BadPaddingException`. Applies only on the CRT path (`RSACore.java:128-130`). Non-CRT keys use `priCrypt` (`:149`), which has blinding but no check; a non-CRT key has no Bellcore weakness. READ.
- Blinding (u, v) is on: `ENABLE_BLINDING = true`, `RSACore.java:59`, used at `:153-158` and `:184-187`. READ.
- ECDSA: nonce from `random.nextBytes(seedBytes)` with seed = order bits + 64 (`ECDSASignature.java:437,457`), `JCAUtil.getSecureRandom()` default (`:463`). That is a random nonce, not RFC 6979 and not hedged with the key or message. No verify of the output. Search "rfc6979|rfc 6979|DeterministicECDSA|withDeterministic" over `java.base/share/classes` (all files in the tree): 0 hits, so no deterministic ECDSA mode exists. The private scalar array is zeroed only for non-`ECPrivateKeyImpl` keys (`:441-446`). READ.
- EdDSA: deterministic by spec; `engineSign` calls `ops.sign` (`:207`) and zeroes the key bytes after (`:211`); no fault check. `EdDSAOperations.java` contains no self-verify on the sign path (grep "verify|sign(": only the verify methods at :128,:171). READ.
- Plain statement: the only fault check in the signing code is the RSA-CRT one. ECDSA and EdDSA have none.
- Not examined: SunMSCAPI/PKCS11 providers (they would sign outside this code). INFERRED relevant on Windows or HSM deployments.

## B. JGDMS signing paths
Search `\.sign\(\)|initSign|Cipher.getInstance|\.doFinal\(` over 1446 `src/main` Java files (target/ excluded; qa not included): signing call sites are
- `X500Provider.java:646,697` (discovery datagram signing with the credential's private key, `signature.sign()` at :697), driven by `X500Client:124`, `X500Server:160`. No verify before release. READ.
- `JarAnalyzer.java:536-538` (bytecode-analysis-engine report signing). No verify. READ.
- `VerdictRegistryImpl.java:1164-1166` (RegistryVerdict signing). No verify. READ.
- No `Cipher` with a PrivateKey found (only `Mac.doFinal` in an example, `SameObjectSameBytesDemo.java:138`).
- JWT: `JwtValidator.java:195-201` and `DefaultJwtVerifier.java:272-277` only verify (`Signature.verify`); JGDMS never mints a JWT. SPIFFE: `SpiffeDiscoveryCredentialProvider.java` has no PrivateKey (grep); no SVID is issued or signed by JGDMS code. SSL endpoints (`jeri/ssl`, `rmi-tls`) hand the private key to JSSE, so TLS signing is DirtyChai's code (section A). READ.
- Consequence: JGDMS adds no fault defence on its 3 services/discovery signers; their algorithm depends on configuration (not checked which).

## C. In-memory secret handling (hygiene for trusted hosts only; not a defence on boards running untrusted code)
- JGDMS: `Arrays.fill|Destroyable|\.destroy\(\)|wipe|zeroize` hit 30 files out of 1446, but they are `Administrable.destroy()` service-lifecycle calls, log/file buffers and DER buffers, not secret wiping (spot-checked `CompactDerEncoder`, `AdminProxy`, `SocketChannelConnectionIO`; the rest INFERRED by name). No `Destroyable`-based wiping of key material found. Private keys sit in `X500PrivateCredential` / Subject credentials; `isDestroyed()` is only queried (`ClientAuthManager.java:159`, `ServerAuthManager.java:147`). READ.
- `JwtRawToken` holds the token in a `String` (`JwtRawToken.java`, field `token`); `JwtLoginModule` holds `refreshToken` (`:533`) and `clientSecret` (`:535`) as Strings, taken from the options map (`:284,306-308`), and builds the refresh POST body as a String (`:685-687`). The one wipe: `pwCb.clearPassword()` (`:488`), after the `char[]` has already been copied into a String (`:486-487`), so it does not help. Session keys: JSSE's. READ.
- DirtyChai java.base: wiping exists piecemeal. `Arrays.fill` appears in 47 of 479 `sun/security` files (RSACore.toByteArray, EdDSASignature, PKCS12KeyStore.java:429,434,629, EdDSAKeyFactory). `KeyUtil.destroySecretKeys` (`KeyUtil.java:469`) is best-effort. `SecretKeySpec.java` has no `destroy` override (0 matches). Search "mlock|MADV|memfd_secret" over `sun/security`: 0 hits. RSA/EC key objects hold BigInteger copies that cannot be wiped (INFERRED from the BigInteger API). No key shielding, no encrypted-at-rest key in RAM, no `mlock`, anywhere in the tree. READ for the searches.

## D. Constant-time comparison
- JWT signature: `Signature.verify` in both verifiers, confirmed (`JwtValidator:201`, `DefaultJwtVerifier:277`). HMAC algorithms rejected: `JwtValidator.java:78` lists `none`, `HS*` as refused; `DefaultJwtVerifier` allows only ES256/RS256 (`:245-281`). So there is no MAC comparison of a shared secret. READ.
- `BasicInvocationDispatcher`: cache key = Base64(SHA-256(token)) (`:2570-2573`), lookup `cache.get(key)` (`:2526`) in a `ConcurrentHashMap<String,..>`. A timing channel exists in principle (hash-bucket / String.equals), but the key is a digest of the token, so a prober would need to find a SHA-256 preimage from timing: no practical leak. Entries are only trusted after the signature check. INFERRED, low risk.
- `JwtLoginModule.java:437-438` `installedSub.equals(newSub)`, `Objects.equals(iss)`: public claims. `JwtValidator.java:250` issuer equals: public. `JwtRawToken.equals` (`token.equals`): used for credential-set membership on the local Subject, not on a wire value; low risk.
- Digests: `DigestCodeSource.java:1033` `Arrays.equals(trusted, computed)` and `:617`; `CodeSourceKey.java:89`. These compare code digests of public jars; an attacker who learns the first byte match from timing gains nothing (the digests are public). Not secret-dependent. READ.
- Constant-time helpers in use: `MessageDigest.isEqual` at `X500PrincipalSerializer.java:96` and `MdInputStream.java:137` (3 of 1446 files). Search for `String.equals` against bearer tokens in security paths: none found beyond the above.

## E. Rowhammer exposure by deployment
- Long-lived secrets (INFERRED; the memo names no placement): SPIRE/CA signing keys on the host/chassis; per-identity SVID key and JWT on each board; refresh tokens/client secrets wherever `JwtLoginModule` runs; digest trust anchors on every JVM.
- Board secrets are co-resident with their own identity's untrusted code (memo lines 877, 882: a compromised board yields that board's SVID key, JWT and ACC). Under the operator ruling this is accepted and bounded by one identity per board and Ruling 21(1) lifetimes (line 883). READ for the lines; the conclusion is INFERRED. No long-lived key on a board is stated or found; the memo is silent on whether one is provisioned there (INFERRED: none, else Ruling 21(3) fails).
- Chassis keys: not co-resident with untrusted code (task statement). Memo silent on HSM/enclave versus JVM heap; if heap, section C is hygiene only.
- Hardware: the SoM survey examined only the i.MX95 family in depth (EdgeLock Secure Enclave); the conga-SMX95 draft datasheet lists "Inline ECC" (survey line 39). Memory encryption (TME/SME): not stated. ECC on the other candidates: not stated.

## F. Next steps (two cases only)
- (i) Trusted hosts, JGDMS code: add re-verify-before-release to `X500Provider:697`, `JarAnalyzer:538`, `VerdictRegistryImpl:1166` (A, B). Hygiene against induced faults.
- (i) Trusted hosts, JGDMS code: hold `clientSecret`/`refreshToken` as `char[]` and clear after use in `JwtLoginModule` (C). Hygiene only; not a defence on boards running untrusted code.
- (ii) Non-local attackers, DirtyChai code (human-authored, advise only): consider verify-after-sign and hedged nonces in `ECDSASignature`/`EdDSASignature`; a Zone T test under `test/jdk/ai/` could assert a faulted signature is not released, shown load-bearing by fault injection (A).
- Change nothing about the design: the Rowhammer/cache/wiping findings (C, E), the JWT cache-key timing (D), and the memory-hardware gaps. Operator action, not code: record where the chassis SPIRE/CA key lives.

## Not determined
- Whether the three JGDMS signers use RSA or EC keys in deployment (configuration not read).
- Whether TLS keys in production come from the Sun providers or from PKCS11/MSCAPI/HSM (those would bypass section A entirely).
- The SPIRE agent/server and SVID-fetching code: not in this repository's `src/main` (no module found); the SVID private key's in-memory handling is therefore unverified.
- DRAM ECC/encryption on the candidate boards beyond conga-SMX95's "Inline ECC" line; actual refresh/leak rates.
- The 30 wipe-pattern hits were only spot-checked (3 of 30 files); `qa/` and `target/` directories were excluded from the counts.
