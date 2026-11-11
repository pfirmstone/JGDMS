claude-opus-5-5

# Root cause: 11 jgdms-jeri tests that fail only under a SecurityManager (2026-10-31)

Skills loaded: handover-composition, production-code. The rule-7 training-docs sweep was NOT done: the brief forbids touching E:\.
Code state: JGDMS HEAD af64aea0c. jgdms-platform, jgdms-der and jgdms-jeri were clean before and after (`git status --short`).
JAVA_HOME was the DirtyChai jdk. Every run used the three-module reactor:
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri test -Pdirtychai -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true`.
Logs and surefire copies: C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\rc\run{A..F}-*.log and `*-reports\`.
Temporary edits (diagnostic stderr prints, mutation M1) were restored with `cp` from backups and checked with `cmp`: all 4 files identical.
Run F then recompiled all 3 modules and printed 0 RCDIAG lines. Nothing committed.

## Runs (POPULATION = surefire totals for the classes named; SOURCE = the log named)
| run | code state | classes | result |
|---|---|---|---|
| A | pristine | the 6 failing classes | 47 run, 0 F, **11 E**: exactly the 11 named in the brief (runA-six-sm.log) |
| B | pristine + the prior agent's logging2.properties (net.jini.jeri FINE) via JAVA_TOOL_OPTIONS | same 6 | 47 run, **11 E**; server logged "excessively" 29 times (runB-six-sm-fine.log) |
| C | diagnostics only (stderr prints at both dispatcher abort sites, encoder, codec) | 6 + 3 DER + CMCT + MarshallingFormatDeferralTest + UdsStagedBindTest | 85 run, 12 E (the 11 + CMCT) (runC-instr-sm.log) |
| D | diagnostics + **M1** (reader mutation, below) | 6 + CMCT + DER fault + 2 platform AMIS tests | 59 run, **0 E**; AtomicMarshalInputStream(ArrayBound)Test 13/13 pass (runD-M1-sm.log) |
| E | diagnostics, **no fix**, reactor run from `subst X:` = repo root (shorter path) | 6 + CMCT | 49 run, **0 E** (runE-subst-sm.log) |
| F | restored, byte-identical | same 6 | 47 run, **11 E** (runF-restored-sm.log) |

## 1. The hypothesis: CONFIRMED, with two refinements
VERIFIED chain, every link read at the cited line and observed in run C:
1. Client side: the ACC block is built only when a SecurityManager is installed (`System.getSecurityManager() != null`, BasicInvocationHandler.java:1016-1018).
   It is sent as wire version 0x02 (:1044-1052).
2. The JOSS handler encodes it with AtomicReducingContextCodec (marshallingFormat() = FORMAT_JOSS at :1293-1294; ReducingContextCodec.java:91-92; marshalRemoteContext :2245-2258).
   The writer's ObjOutputStream.drain cuts buffered primitives into chunks of at most 1024 bytes. It tags each chunk under 256 bytes as TC_BLOCKDATA and each of 256 bytes or more as **TC_BLOCKDATALONG** (ObjOutputStream.java:445-473, :455-462).
3. RemoteContextCodec.marshal writes count, kind, UTF and cert-count as primitives (RemoteContextCodec.java:203-228). Only a `writeObject` (digest, cert) drains the buffer.
   MEASURED (run C, 19 of 19 JOSS encodes across 7 classes): 5 domains in the order [URL target/classes, URL target/test-classes, Digest junit, Digest surefire-junit4, Digest surefire-booter].
   The block is 666 bytes with header `ac ed 00 05 7a 00 00 01 02`, so the first block is TC_BLOCKDATALONG of **258 bytes**.
   That reconciles to 4 + 83 + 88 + 83 if the digest-algorithm name is 7 bytes (ASSUMPTION; the name was not logged). The overshoot past 255 is 3 bytes.
4. Server side: the default dispatcher codec is AtomicReducingContextCodec (BasicInvocationDispatcher.java:346-347, :638-639).
   It reads with AtomicMarshalInputStream (AtomicReducingContextCodec.java:66) at the TOP level of a fresh stream (BasicInvocationDispatcher.java:2552-2556).
   The first `in.readInt()` (RemoteContextCodec.java:300) goes through read() and checkReadPrimitiveTypes (AtomicMarshalInputStream.java:525-528) to readBlockDataLong (:805-813).
   That rejects `length > arrayLenAllowedRemain`. The budget is 0 outside readObject: the field defaults to 0 (:222), is armed only at :3187-3188 and is reset to 0 at :3209-3210.
   The short-form readBlockData (:789-793) has no check. So **every top-level TC_BLOCKDATALONG is refused**.
5. The dispatcher catch aborts the request (BasicInvocationDispatcher.java:1078-1083), and the client sees "request aborted by remote endpoint".
   Run C, 80 abort lines at this site: the only server exception was `IOException: Attempt to excessively long array of raw bytes` at readBlockDataLong:811 < checkReadPrimitiveTypes:528 < readInt < RemoteContextCodec.unmarshal < dispatch:1061.
   The response-phase abort site (:1225) fired 0 times. The "injected ..." lines in the log come from fault injection in passing tests.

Refinement (a), VERIFIED: the trigger is not readInt outside readObject as such. It is any top-level primitive run of 256-1024 bytes.
In run E the first block was `77 57` (TC_BLOCKDATA, 87 bytes), and the same top-level readInt, readByte and readUTF calls succeeded: 610 UNMARSHAL-OK on AtomicMarshalInputStream, 0 aborts.
Refinement (b): it does not affect every JOSS call under a SecurityManager. It affects those whose ACC block contains a primitive run of 256 bytes or more, which depends on domain URL lengths, kinds and ORDER (section 4).
JOSS here means AtomicILFactory AND plain BasicILFactory: both fall to AtomicReducingContextCodec (VERIFIED, lines above). Production exposure: UNDETERMINED (not measured).

Discriminating intervention M1 (pre-registered: all 11 pass, CMCT loses its Unmarshal symptom, platform AMIS tests still pass; else-branch: a survivor has an extra cause).
M1 changed readBlockDataLong to accept up to 1024 bytes at `nestedLevels == 0` without debiting the budget, and left it unchanged inside readObject.
Run D: all 11 PASS, CMCT 2/2, AtomicMarshalInputStreamArrayBoundTest 9/9 (including the hostile in-object blockDataLong case), AtomicMarshalInputStreamTest 4/4, 0 aborts. **All three predictions held.**

## 2. The DER invocation layer (AtomicDerInvocationHandler / AtomicDerInvocationDispatcher)
- VERIFIED by reading: the client encodes with DerReducingContextCodec (AtomicDerInvocationHandler.java:321-322 -> ReducingContextCodec.java:94-95).
  The dispatcher decodes with DerReducingContextCodec (AtomicDerInvocationDispatcher.java:94-96, DerReducingContextCodec.java:79-85). DER has no block-data framing, so this threshold has no analogue there.
- VERIFIED by run (C, under SM): AtomicDerFaultRoundTripTest 10/10 and MarshallingFormatDeferralTest 8/8 pass.
  The DER block was 621 bytes, 5 domains, header `8f 01 01 02 01 05 ...`. The server logged `UNMARSHAL-OK count=5 kept=5 stream=DerMarshalInputStream` 15 times and aborted 0 times. **The DER path does not have this defect at this block size.**
- INERT, not evidence: AtomicDerInvocationLayerTest, which the prior report cited as passing under SM, produced **0** encoder lines in run C. It makes no wire call (SCENE link at zero).
  AtomicDerRequiredFormatCallTest encoded 2 blocks, but no server decode line was attributable to it.
- UNDETERMINED: DER with large ACC blocks (certificates, many domains) against DerInputLimits. The largest block observed was 621 bytes.

## 3. Server-side cause of each of the 11 failures
Server lines come from run C, attributed per CLASS by the surefire `Running` marker. Surefire does not delimit methods, so the log-to-method attribution is PLAUSIBLE wherever a class has more than one failing method.
Each METHOD is tied to the cause by intervention: VERIFIED that every one of the 11 passes under M1 (run D) and under the short path without any fix (run E).
| test | client symptom (A / C) | server, run C (aborts in class) | verdict |
|---|---|---|---|
| AuthenticateServerTest.aDnOnlyPinDoesNotPinTheWorkload | UnmarshalException | excessively @RemoteContextCodec.unmarshal (2) | SAME |
| AuthenticateServerTest.returnsTheAuthenticatedServerPrincipals | UnmarshalException | same (2) | SAME |
| HttpsServerEndpointListenTest.testCallOverHttpsEndpointCompletes | CompletionException(UnmarshalException) | same (1) | SAME |
| SslEngineRoundTripTest.testMutualTlsSpiffeRoundTripOverEngine | CompletionException(UnmarshalException) | same (12, interleaved) | SAME |
| SslEngineRoundTripTest.testConcurrentMutualTlsCalls | CompletionException(ExecutionException) | same (12) | SAME |
| SslServerAcceptPathTest.testDispatchRunsUnderTheServiceSubject | CompletionException(UnmarshalException) | same (1) | SAME |
| UdsConstraintEnforcementTest.testUnconstrainedRoundTripReportsIntegrityNotEnforced | UnmarshalException | same (1) | SAME |
| UdsEndpointRoundTripTest.testAtomicRoundTripOverUds | UnmarshalException | same (62; the concurrent test makes 64 calls) | SAME |
| UdsEndpointRoundTripTest.testConcurrentRequests | ExecutionException(UnmarshalException) | same (62) | SAME |
| UdsEndpointRoundTripTest.testConnectionIsUnixDomainNotTcp | UnmarshalException | same (62) | SAME |
| UdsEndpointRoundTripTest.testStaleSocketFileIsReclaimed | **A: NoSuchObjectException "no such object in table"** / C: UnmarshalException | same (62) | SAME root cause; the variant symptom is UNDETERMINED |
ConnectionManagerCapacityTest under SM: the same server cause (1 abort, run C). It passed 2/2 under M1 and under run E. Its no-SM assertion failure (:171) was not investigated.
testStaleSocketFileIsReclaimed showed UnmarshalException in runs B, C, F and in the prior run2 and run2-t, and NoSuchObjectException only in run A. Both are at :403 (`proxy.echo`). Why the client sometimes reports NoSuchObject: UNDETERMINED.

## 4. The reported "instability"
- "UdsEndpointRoundTripTest fails 4 of 16": **deterministic, not unstable** (VERIFIED). The 4 are exactly the methods that make a remote call (UdsEndpointRoundTripTest.java:113-135, :178-201, :238-286, :383-407).
  The other 12 export without calling, or do not export. The same 4 errored in runs A, B, C and F.
- "With FINE logging most pass": **NOT REPRODUCED.** Run B used the same logging2.properties under the profile in the three-module reactor and still had 11 of 11 errors.
  The prior "masked" run (sm-run\run2-all-log.log) was single-module ("total of 1 modules", line 3), and its command line was not recorded.
  Its ConnectionManagerCapacityTest failure was the no-SM assertion `expected:<1> but was:<2>` at :171, not the under-SM UnmarshalException at :153 (sm-run\run2.log).
  PLAUSIBLE: that run had no SecurityManager (profile omitted). UNDETERMINED beyond that.
- The real environmental dependence is the checkout PATH (VERIFIED, run E). With the repo reached through `subst X:`, without the fix, all 11 pass.
  Domain order flipped to [Digest x3, URL, URL] in 26 of 26 encodes (long path: URL-first in 47 of 47 encodes, runs C and D). The first block shrank to 87 bytes and the URL domains fell after the last digest drain.
  My pre-registered length prediction (about 176 bytes, same order) FAILED; the pass prediction held. Why the order follows the path: UNDETERMINED.
  Live candidates: (i) a hash-ordered domain collection in DirtyChai's ACC or combiner keyed on CodeSource value; (ii) another stack-dependent effect. DirtyChai source is out of bounds for this brief.
  Discriminating test: print AccessController.getContext() domain order from one fixed class at two checkout paths.
  ⇒ **The existing 11 tests are not a reliable detector**: a CI checkout with a shorter path passes them with the defect present.

## Root cause
AtomicMarshalInputStream charges TC_BLOCKDATALONG primitive blocks to the per-readObject array budget, which is 0 at the top level of a stream (AtomicMarshalInputStream.java:805-813, :222, :3187-3188, :3209-3210).
So a top-level primitive run of 256-1024 bytes, which its own writer legitimately emits (ObjOutputStream.java:455-462), is refused.
RemoteContextCodec is a top-level primitive writer, and it runs only under a SecurityManager. In this workspace its first run is 258 bytes, so every JOSS call under SM aborts at the server. There is one root cause for all 11.
Production impact is conditional on block composition (section 1, refinement b). Services default to AtomicDerILFactory (commit b17f72c3f subject), which is not affected at the size tested.

## Smallest proposed fix (not applied; a human writes it)
File: jgdms-platform/src/main/java/org/apache/river/api/io/AtomicMarshalInputStream.java:805-813 (readBlockDataLong). This is M1, verified in run D:
```java
int length = input.readInt();
long allowed = (nestedLevels == 0) ? MAX_BLOCK_DATA_LEN : arrayLenAllowedRemain; // MAX_BLOCK_DATA_LEN = 1024
if (length < 0 || length > allowed) { /* close + throw, unchanged */ }
if (nestedLevels != 0) arrayLenAllowedRemain -= length;
```
Name 1024 once, shared with ObjOutputStream.drain:455, which today uses a literal.
DoS: a top-level block stays at most 1 KiB, its bytes must actually arrive (readBytesChunked, :818), and the ACC block is already capped at 1 MiB (BasicInvocationDispatcher.java:207). The in-object budget is unchanged.
Rejected alternative: flushing per domain on the writer side. A single URL over about 248 characters still overflows, and a third-party JOSS writer is not covered.

## Tests that would catch it
- T1 (jgdms-platform, beside AtomicMarshalInputStreamArrayBoundTest). Invariant: a stream AtomicMarshalOutputStream writes is readable by AtomicMarshalInputStream at every nesting level.
  At top level, `writeInt` + `writeUTF` with primitive runs of 200, 300 and 1100 bytes, then flush; read back with `AtomicMarshalInputStream.create(in, null, false, null, null, false)` and assert equality. Keep a hostile top-level TC_BLOCKDATALONG with a 2e9 length rejected.
  Fails today: the 300 and 1100 cases throw "Attempt to excessively long array of raw bytes", the exception seen 158 times in run C. The 200 case is the control and passes either way.
  Passes with M1. Show the load-bearing failure by running T1 on trunk before the fix.
- T2 (jgdms-jeri): a RemoteContextCodec round-trip through the PRODUCTION codec (`ReducingContextCodec.forFormat(FORMAT_JOSS)` on both ends), with 3 URL-kind domains of fixed URLs of about 100 characters.
  That gives a first run of at least 325 bytes in any order on any checkout path. RemoteContextCodecJavaBaseExclusionTest.java:115-127 reads with a plain JDK ObjectInputStream and short URLs, so it bypasses the production reader (WIRING gap).
- CI: run jgdms-jeri with -Pdirtychai (jgdms-jeri/pom.xml:206-214), the production posture.

## Not verified
1. Why domain order follows the checkout path (section 4). 2. The client mechanism of the NoSuchObjectException variant.
3. Production exposure per JOSS export: the JOSS factories are still constructed in phoenix, browser, extra ExportHelper and others (grep hits, not examined).
4. Other top-level primitive runs of 256 bytes or more (for example a method with more than 31 `long` arguments) would hit the same reader defect, SecurityManager or not. PLAUSIBLE from the code, not tested.
5. DER with large ACC blocks. 6. That the JDK's ObjectOutputStream also caps blocks at 1024: from memory of the JDK source, not re-read.
7. The no-SM failures of CMCT (:171) and MuxServerStartTimeoutTest. 8. The rule-7 corpus sweep (E:\ forbidden).

**Decision-blocking:** only whether to adopt the reader-side change in AtomicMarshalInputStream. It is a DoS-hardening site, so a security reviewer should sign off on bounding top-level blocks at 1024 bytes instead of refusing every one over 255. Nothing else blocks.
