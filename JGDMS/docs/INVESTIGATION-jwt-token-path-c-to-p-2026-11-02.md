claude-sonnet-5-5 (Sonnet 5.5). Read-only trace, 2026-11-02. Paths relative to C:\Users\peter\Documents\GitHub\JGDMS\JGDMS. R = READ, I = INFERRED.

ANSWER (4 lines)
1. NOT IMPLEMENTED: no code in this repo moves a Subject or token from C to P. writeUserSubjects reads the Subject bound to the calling thread in the JVM that makes the JERI call; it is the same object the login module refreshes only if login and call share one JVM.
2. The Subject gets only JwtPrincipal(s), JwtExpiryClaim and JwtRawToken. Refresh token and client secret are in private fields of RefreshThread and the options map, never in the Subject; the wire writes principals (class, name) and raw JWTs only.
3. Refresh is an in-place swap in the login JVM's Subject, read again on every call; no push, no callback, no per-call re-send to P exists. The C-to-P hand-off is a design item (D18, O26) with no code.
4. Nothing proxy-side depends on jgdms-security-jwt, so the login module is not on a dependency path to P; whether P's runtime classpath excludes it is UNKNOWN.

1. WHERE LOGIN HAPPENS, PATH TO THE WIRE
- Main code constructs no Subject and calls no callAs/doAs with it. Search `JwtLoginModule|JwtRawToken` over JGDMS/ (non-docs: 8 files): JwtLoginModule.java, JwtValidator.java, JwtExpiryClaim.java (module itself), tests JwtLoginModuleTest, JwtPrincipalBindingTest, WriteUserSubjectsLockTest, MultiSubjectWireProtocolTest, JwtDispatcherFailClosedTest. Also qa/ (9355 files, grep): qa/jtreg/net/jini/security/jwt/JwtLoginModuleTest/JwtLoginModuleTest.java (login/commit/abort test only; no callAs/doAs hits) and qa/build/deps/qa1-mercury.deps:252-254 (class list for JwtRawToken, not the login module). No demo/example. Every login is a test: the JAAS Subject is passed to JwtLoginModule.initialize (JwtLoginModule.java:226) by the test. All in one JVM.
- Only wire-side callAs: MultiSubjectWireProtocolTest.java:300 (test, same JVM as the handler).
- Path: login() validates the token and keeps it in field rawToken (JwtLoginModule.java:297). commit() builds JwtExpiryClaim and JwtRawToken (:324,:330) and adds them to subject.getPublicCredentials() under synchronized(subject) (:343-348). The Javadoc says it is public because writeUserSubjects runs outside doAs (:325-328; JwtRawToken.java:35-40).
- Caller side: BasicInvocationHandler.java:1047 getAllUserSubjects() -> :2132-2143 Subject.currentAll() by reflection (CURRENT_ALL_METHOD :195-205) else Subject.current() -> writeUserSubjects(ros, userSubjects) :1051 (only when the reducing-context block is non-empty, :1044). So the Subject is the thread-bound Subject of the calling JVM (R), not a serialised copy.
- writeUserSubjects (:2171-2212): under synchronized(subjects[si]) (:2183) copies getPrincipals() and getPublicCredentials(JwtRawToken.class) (:2184-2187); writes per principal class name + name (UTF-8, 16-bit lengths), then jwtCount (8-bit, max 255) and each raw JWT (writeJwtBytes). Wire protocol 0x02, own byte format, not Java serialisation. Server side: BasicInvocationDispatcher.java:1076-1079 readUserSubjects (:2383), invokeWithClientSubject :2187-2248 binds with UserSubjectSupport.callAsAll.
- C-to-P: JwtRawToken is not Serializable (JwtRawToken.java:49, no implements; R); JwtPrincipal Javadoc says serialisation is unsupported (JwtPrincipal.java:60). Search `RemoteContextCodec|ClientSubject|SCOPED_SUBJECT` in jgdms-jeri/src/main: RemoteContextCodec carries codebases only, "no principals" (BasicInvocationHandler.java:2243); ClientSubject is the server-side mTLS worker Subject (Util.java:742-818). SCOPED_SUBJECT: 0 hits (jgdms-jeri/src/main). No class forwards a Subject C to P: NOT FOUND (patterns `Subject` + `forward|transfer|hand` over docs + main not exhaustive; I).

2. WHAT THE SUBJECT HOLDS (commit, :317-350) and what the wire can carry
| item | where | on wire? |
| JwtPrincipal (sub:, email: only if email_verified, group:), max 64 | public principals :346 | yes: class name + name |
| JwtExpiryClaim(Instant) | public cred :324; toString "JwtExpiryClaim[instant]" (JwtExpiryClaim.java:61) | no (only JwtRawToken.class credentials are selected, :2187) |
| JwtRawToken(compact JWT) | public cred :330; toString prints header only (JwtRawToken.java:89-94) | yes, in full |
- No private credentials are added (grep getPrivateCredentials in JwtLoginModule.java: 0 hits). No credential holds the refresh token, client secret or JWKS URI (R).
- Refresh token: RefreshThread field `refreshToken` (:584, rotated :802), `clientSecret` :586; also the `options` map (:167) held by the module, and option "token" (the login access token). Not reachable via Subject sets. They are sent only in the HTTPS POST body to refreshTokenUri (:744-747).
- Log/toString: no LOG call in JwtLoginModule.java prints options, refreshToken or clientSecret (grep of 13 LOG/log sites read; I for other module files: jgdms-security-jwt main has 10 files). Failure strings carry class name/status/claim names only (:669-691, :718); exception passed to LOG at :691 is from httpClient/validator (message text could include the URI; not token text; I). Residual: a SecurityManager-less same-JVM caller can read the options map only if given the module instance (I).

3. REFRESH
- RefreshThread wakes at 80% of remaining lifetime (:731), POSTs the grant (:743-760), validates, then installRefreshed (:475-511) removes what it added and adds new JwtPrincipals, JwtExpiryClaim, JwtRawToken to the SAME Subject object under synchronized(target) (:478,:505-509). writeUserSubjects locks the same monitor (:2183), so it never sees half old, half new (R). The next RPC in that JVM sends the new token.
- P between refreshes: nothing in the repo (no P-side code). If P is a separate JVM it can only hold what was handed to it; no mechanism, push, pull or per-call re-send exists (NOT FOUND: searches `JwtRawToken|JwtLoginModule` main code = 8 files, plus `Subject` forwarding patterns above). Failure of refresh: failClosed removes token and principals (:434-439), so a same-JVM caller then sends no JWT; a copy at P would persist until its own exp (I).
- On expiry the server decides: DefaultJwtVerifier checks exp/maxLifetime (default 1 h per Javadoc :140-143); the client does not enforce it.

4. PROXY TIER
- jgdms-security-jwt/pom.xml:47 depends on jgdms-platform only (and osgi.annotation, junit test). Search `jgdms-security-jwt` over 93 pom.xml under JGDMS/: hits only JGDMS/pom.xml:68 (module list) and its own pom:29. So jgdms-jeri (pom deps: osgi.annotation, jgdms-platform, jgdms-der, jgdms-activation-parameters, high-scale-lib), jgdms-lib-dl (jgdms-url-integrity, platform, jeri, activation) do not depend on it (R). There is no jgdms-platform-dl module (ls). Main-code references to JwtLoginModule outside the module: 0.
- Present in P: jgdms-platform (JwtRawToken, JwtPrincipal, JwtVerifier, DefaultJwtVerifier) and jgdms-jeri (BasicInvocationHandler with the JWT-writing code). BoundedBody (watchdog thread JwtLoginModule-body-watchdog, static, BoundedBody.java:47-53) lives in jgdms-security-jwt and loads only if that jar is on P's classpath, which the poms do not force. Conclusion: not present via dependencies; UNKNOWN for a deployed classpath (no P assembly/launcher in the repo; design doc has none).
- Design doc (docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md:830-843, 859): in the code-bearing tier the JWT DOES enter P (O26, operator), as a narrowed (audience S, minimal scope, minutes), certificate-bound token; no login module is mentioned in P. Author's reading, unreviewed, no code.

5. ATTACKER WITH CODE IN P
- Obtains: the raw access token(s) it is handed (valid to exp, server cap 1 h; replay to S until then, unless certificate-bound is built, which is not: O27/O29 design), the principals (decoded from the token anyway). If P shares a JVM with the Subject, anything in the Subject (3 types above) and the module's public API.
- Cannot obtain (by this code, if P holds only a Subject copy / token string): refresh token, client secret (private RefreshThread fields and options map, absent from Subject, never written to the wire), JWKS config, the ability to refresh. They exist only in C's JVM. Condition: the login module must be in C only (UNKNOWN deployed; item 4).

COULD NOT DETERMINE
- How a token or Subject is delivered C to P: no code, no config. A design choice is open: re-send a fresh token per call vs. P-side holder with push.
- Whether any P launcher puts jgdms-security-jwt or the options map in P's JVM: nothing in repo.
- Whether Subject.currentAll() exists on the target JDK (reflection fall back to Subject.current(), :2141): not run.
- Did not exhaustively read docs/blog and the AI_Agent_* context files beyond grep hits; did not run any test.
