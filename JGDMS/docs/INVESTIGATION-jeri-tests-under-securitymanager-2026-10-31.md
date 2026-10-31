claude-sonnet-5-5

# jgdms-jeri tests under a SecurityManager (2026-10-31)

Scratch/logs: C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\sm-run
(run1b.log, run2.log, run3c.log, audit.policy, r1-jeri\, r2-jeri\ surefire .txt copies).
No production code, test, pom or policy file was edited. Nothing committed. The pre-existing dirty tree (root pom.xml, tools/policy-condenser, docs) is not mine.

## Setup facts (VERIFIED)
- Profile `dirtychai` (jgdms-jeri/pom.xml:205-221) sets surefire argLine `-Djava.security.manager=default -Djava.security.policy=${project.build.testOutputDirectory}/test.policy`.
- jgdms-jeri/src/test/resources/test.policy is ONE grant: `grant { permission java.security.AllPermission; };` -> a missing-grant AccessControlException (class a) is essentially impossible there.
- The profile exists only in jgdms-jeri (grep dirtychai: 0 hits in jgdms-der/pom.xml and jgdms-platform/pom.xml). So under `-Pdirtychai` the jgdms-der tests (DerLazyMarshalledInstanceFormatTest, DerProxyRequiredFormatTest) and jgdms-platform tests run WITHOUT a SecurityManager. VERIFIED by pom grep; not tested otherwise.
- JAVA_HOME = C:\Program Files\DirtyChai\jdk (java 27-internal).
- Deviation: the plain command `mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test` stops at jgdms-platform (16 failing tests there, same with or without SM; jgdms-der/jeri SKIPPED). To reach jeri I added `-Dmaven.test.failure.ignore=true` to BOTH runs (command line only).

## RUN 1 baseline (no SM)
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Dmaven.test.failure.ignore=true`
- jgdms-platform: `Tests run: 465, Failures: 3, Errors: 13, Skipped: 1` (DigestDomainGrantTest NoSuchMethod PermissionGrantBuilder.codeSources(CodeSource[]), GrantSignerTest; unrelated to jeri)
- jgdms-der:      `Tests run: 1526, Failures: 0, Errors: 0, Skipped: 0`
- jgdms-jeri:     `Tests run: 293, Failures: 1, Errors: 1, Skipped: 29`
  - ConnectionManagerCapacityTest.testMoreThanOneConnectionsWorthOfConcurrentRequests:171 (expected:<1> but was:<2>)
  - MuxServerStartTimeoutTest.theTimerDoesNotOutliveAConnectionThatStarted:304 (TestTimedOut 120000 ms)

## RUN 2 SecurityManager
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Pdirtychai -Dmaven.test.failure.ignore=true`
- platform: `Tests run: 465, Failures: 3, Errors: 13, Skipped: 1` (identical; no SM there)
- der:      `Tests run: 1526, Failures: 0, Errors: 0, Skipped: 0` (no SM there)
- jeri:     `Tests run: 293, Failures: 0, Errors: 13, Skipped: 29`
No "profile not found" warning appeared.

## Named classes (all PASS in run 1 and run 2)
- net.jini.jeri.AtomicDerInvocationLayerTest 7/7; AtomicDerRequiredFormatCallTest 3/3 (jeri, run 2 WITH SM)
- au.net.zeus.jgdms.der.object.DerLazyMarshalledInstanceFormatTest 3/3; DerProxyRequiredFormatTest 3/3 (der, run 2 WITHOUT SM, see above)

## COMPARE
Same failure in both runs: MuxServerStartTimeoutTest (timeout), ConnectionManagerCapacityTest (different symptom: run 1 assertion `expected:<1> but was:<2>`; run 2 `Unmarshal exception unmarshalling response`; the run-2 symptom is the new one below).

Pass in run 1, fail in run 2 (11 tests, plus ConnectionManagerCapacityTest changing symptom):
AuthenticateServerTest.aDnOnlyPinDoesNotPinTheWorkload, .returnsTheAuthenticatedServerPrincipals; HttpsServerEndpointListenTest.testCallOverHttpsEndpointCompletes; SslEngineRoundTripTest.testConcurrentMutualTlsCalls, .testMutualTlsSpiffeRoundTripOverEngine; SslServerAcceptPathTest.testDispatchRunsUnderTheServiceSubject; UdsConstraintEnforcementTest.testUnconstrainedRoundTripReportsIntegrityNotEnforced; UdsEndpointRoundTripTest.testAtomicRoundTripOverUds, .testConcurrentRequests, .testConnectionIsUnixDomainNotTcp, .testStaleSocketFileIsReclaimed.
All show client-side `java.rmi.UnmarshalException ... Caused by: java.io.IOException: request aborted by remote endpoint` (HTTPS one: `EOFException: connection closed by server`).

Classification:
- (a) missing grant: NONE. test.policy is AllPermission; `-Djava.security.debug=access,failure` on a failing test printed no "access denied" (grep "denied"/AccessControlException in run2.log: 0 lines). VERIFIED.
- UdsEndpointRoundTripTest.testAtomicRoundTripOverUds, single test, with FINEST jeri logging, server side log:
  `java.io.IOException: Attempt to excessively long array of raw bytes` at `org.apache.river.api.io.AtomicMarshalInputStream.readBlockDataLong(AtomicMarshalInputStream.java:811)`, called from `net.jini.jeri.RemoteContextCodec.unmarshal(RemoteContextCodec.java:300)` <- `BasicInvocationDispatcher.unmarshalRemoteContext(:2556)` <- `dispatch(:1061)`. That is the server aborting the request. VERIFIED for this test.
  Classification: (c) probable real defect, not (a)/(b). Reading the code (not an experiment): readBlockDataLong rejects length > arrayLenAllowedRemain (AtomicMarshalInputStream:807); the budget is set to MAX only inside a readObject entry (:3188) and reset to 0 on exit (:3210); RemoteContextCodec.unmarshal calls in.readInt() (:300) on a fresh stream outside any readObject, so budget is 0. The branch is reached only when the caller sends a serialized ACC (`hasSerializedAcc`, BasicInvocationDispatcher:1048), which a client does when a SecurityManager is present (BasicInvocationHandler:1017 tests getSecurityManager() != null). Reason the code path is SM-only: UNDETERMINED beyond that (I did not read the client send condition).
- The other 10 tests: same client symptom; server-side cause NOT captured for them. UNDETERMINED individually (probably the same cause, not shown).
- CAVEAT (VERIFIED): the failure is not stable under logging. With FINE on net.jini.jeri only (JAVA_TOOL_OPTIONS logging2.properties), the 7 failing classes together ran with only ConnectionManagerCapacityTest failing (`Tests run: 49, Failures: 1, Errors: 0, Skipped: 5`) and UDS/SSL passed. Without logging, `-Dtest=UdsEndpointRoundTripTest` under the profile gives `Tests run: 16, Failures: 0, Errors: 4, Skipped: 1`; the same class without the profile gives `Tests run: 16, Failures: 0, Errors: 0, Skipped: 1`. Why logging masks it: UNDETERMINED.
- (b) tests that cannot run under SM for another reason: none identified.
- MuxServerStartTimeoutTest: fails in both runs, so not SM related.

## RUN 3 polpAudit
Properties from SKILL.md: `-Djava.security.manager=polpAudit` and `-Djava.security.policy=<file>`; optional `-DpolpAudit.path.properties=<props>` (not used); output = grants appended to the policy file at JVM shutdown.
Command (cwd JGDMS root; SP = scratchpad sm-run dir):
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test "-Dtest=AtomicDerRequiredFormatCallTest,AtomicDerInvocationLayerTest" -Dsurefire.failIfNoSpecifiedTests=false "-DargLine=-Djava.security.manager=polpAudit -Djava.security.policy=$SP/audit.policy"`
- Skill statement "created if it doesn't exist" is WRONG at startup: with a nonexistent file the fork died: `Error occurred during initialization of VM`, `java.lang.SecurityException: Unable to load policy url` (PolicyUtils.getPolicyURLs:402). Workaround: pre-create an empty audit.policy in scratchpad. Skill needs correcting (operator decision).
- With `-pl jgdms-jeri` alone, stale jgdms-der from ~/.m2 gave NoSuchMethod ResolutionContext.withRequiredPayloadFormat; use the 3-module reactor (that attempt's file kept as audit-attempt1-stale-der.policy, ignore it).
- Result: `Tests run: 7` (InvocationLayer) and `Tests run: 3` (RequiredFormatCall) pass, total 10/0/0/0, BUILD SUCCESS. Policy produced: audit.policy, 324 lines, 19 grant blocks.
- The two classes ran in one JVM, so permissions cannot be attributed per class. Recorded (types:count): FilePermission 86, PropertyPermission 68, RuntimePermission 28, LoadClassPermission "ALLOW" 19, DeSerializationPermission 10 ("MARSHALL" x7, "ATOMIC" x3), SecurityPermission 8, SerializablePermission 7 ("enableSubclassImplementation"), SerialObjectPermission 6 ("java.util.logging.Level"), AuthPermission 5 ("getSubject"), DefineClassPermission "ALLOW" 4, ReflectPermission 4 ("suppressAccessChecks"), URLPermission 2, ManagementPermission 1.
  Notable: RuntimePermission setContextClassLoader, getStackWalkerWithClassReference, accessDeclaredMembers, setIO, createPlatformThread, shutdownHooks; SecurityPermission getDomainCombiner, createAccessControlContext; PropertyPermission au.net.zeus.jgdms.der.{maxInputBytes,maxMarshalledInstanceNesting,stream.reconstitutionFactor,stream.streamReconstitutionFactor} read, `org.apache.river.jeri.server.suppressStackTraces`, and `*` read,write x3; FilePermission read on META-INF/services (MarshalDelegate, ProxyCodebaseSpi), META-INF/jgdms/der-serializers, jgdms-jeri target\surefire-reports.
  Grants are keyed by codebase (jgdms-platform/der/jeri target/classes, jeri target/test-classes, ~/.m2 jars, jrt:/java.security.jgss, jrt:/jdk.localedata).

## Needs the operator
1. Whether to fix/triage the RemoteContextCodec.unmarshal budget issue (c) and whether the SM-only run should be gated in CI.
2. Correct the polpAudit skill (policy file must pre-exist).
3. Platform failures (DigestDomainGrantTest/GrantSignerTest) block a plain reactor run; unrelated to jeri.
