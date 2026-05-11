# JGDMS / DirtyChai — AI Agent Conversation Context

**Purpose:** This document provides a structured summary of a technical design conversation between Peter Firmstone (pfirmstone) and an AI agent, covering the DirtyChai OpenJDK fork and the JGDMS distributed services framework. It is intended to be given to a future AI agent to continue the conversation without loss of context.

**GitHub repositories:**
- DirtyChai: https://github.com/pfirmstone/DirtyChai
- JGDMS: https://github.com/pfirmstone/JGDMS

**Active GitHub issues from this conversation:**
- Issue #204 (JGDMS): Revise the implementation and goals of the Bytecode Analysis Engine
- Issue #205 (JGDMS): TLS Certificate Management for JGDMS/DirtyChai

---

## 1. DirtyChai — What It Is

DirtyChai is a community fork of OpenJDK that retains and actively improves Java's `SecurityManager` and `AccessController`/`ProtectionDomain` authorization infrastructure, which was deprecated in Java 17 and removed in Java 24.

**Important: DirtyChai is a full JDK binary distribution, not a Java library.** It is a C/C++ OpenJDK fork built from source and distributed as a pre-built `tar.gz` archive (`jdk-linux-x64.tar.gz`). Because it is a native binary JDK, it cannot be uploaded to Maven Central or any Maven repository. See **Section 11** for the CI/build mechanism used to install it.

**Key design goals (from project documents):**
- Prevent loading of untrusted code
- Break gadget attack chains
- Block injection attacks
- Maintain and extend guard hooks
- High performance and scalability
- NOT a sandbox — it does not attempt to run untrusted code safely

**Key completed work:**
- `ConcurrentPolicyFile` — replaces synchronized policy, RFC 3986 URI matching, no DNS lookups, <1% overhead
- `System.setSecurityManager(null)` now throws `IllegalArgumentException`
- New permissions: `LoadClassPermission`, `SerialObjectPermission`, `NativeInvocationPermission`, `NativeMemoryPermission`
- `AccessControlContext` made immutable with static builder methods
- Virtual thread support when SecurityManager is enabled
- Removal of XML parsing from trusted codebase
- JGDMS binary compatibility re-exported from `java.base`

**Ultimate goal:** Community-based redesign of the Authorization API for Java as a preview feature, with eventual integration into OpenJDK mainline.

---

## 2. Virtual Thread Carrier Pinning — DOS Threat Analysis

### Background: JEP 491 (Java 24)

JEP 491 resolved the three major pinning causes (`synchronized` blocks with blocking operations, monitor contention, `Object.wait()`). Remaining pinning cases after Java 24:

| # | Pinning cause | Controllable by untrusted code? |
|---|---|---|
| A | JNI native method → blocking callback | Yes, via `NativeInvocationPermission` |
| B | FFM → blocking callback | Yes, via `NativeInvocationPermission`/`NativeMemoryPermission` |
| C | Class loading (symbolic reference resolution) during blocking | **Yes — if untrusted code triggers class loading** |
| D | Class initializer (`<clinit>`) that blocks | **Yes — if untrusted code triggers `<clinit>`** |

### Key findings:
- Scheduler exhaustion threshold is very low: only `Runtime.availableProcessors()` concurrent pinning requests cause a complete scheduler stall
- `LoadClassPermission` is the most important guard against carrier-pin DOS post-JEP 491
- Circular `<clinit>` dependency detection is important: two threads waiting on each other's `<clinit>` both pin carriers
- JFR event `jdk.VirtualThreadPinned` provides runtime feedback

---

## 3. JGDMS — Architecture Overview

JGDMS is a distributed services framework (based on Jini/Apache River) running on DirtyChai. It provides:
- Authenticated service discovery over IPv6
- Smart proxy download and trust verification
- `@AtomicSerial` for safe deserialization
- JERI (Jini Extensible Remote Invocation) for authenticated transport

---

## 4. Five-Host Trust Pipeline Architecture

The core security architecture consists of five distinct hosts/services:

### Host 1 — Jini Lookup Service (any OS)
- Stores marshalled service items **opaquely** — does NOT unmarshal proxies
- Fires `ServiceRegistrar` events on foreign service registration
- No analysis pipeline, no BAE proxy, no VerdictRegistry proxy
- No URL fetching
- Engine instances (Host 2 pool) register here for discovery by Hosts 4 and 5

### Host 2 — SELinux Analysis Engine Pool (SELinux only, stateless, replicated N times)
- Runs `BytecodeAnalysisEngineImpl`
- Completely stateless — `AnalysisRequest` is self-contained (bytes + hash)
- No inter-instance coordination required
- Each instance has its own key pair for signing reports
- SELinux policy: inbound JERI from Host 4 + Host 5 only, no outbound, no internet, no exec, no JNI, no FFM
- Discovered by Hosts 4 and 5 via standard Jini lookup on Host 1
- Scales horizontally — add instances as load requires
- Returns `JarAnalysisReport` signed by engine private key

### Host 3 — VerdictRegistry (any OS)
- Persistent verdict store keyed by SHA-256 content hash
- Inbound JERI from Host 4 (submit), Host 5 (submit), all clients (lookup)
- No connection to Host 2
- Maintains set of trusted engine public keys (one per engine instance)
- Verifies engine signature on every submitted report
- Clients query directly and independently of Host 1

### Host 4 — Codebase Downloader Service (any OS) — **proactive**
- Triggered by `ServiceRegistrar` events from Host 1
- **Only component with outbound internet access** (JAR URL fetching)
- Downloads JAR bytes, computes SHA-256 hash
- Checks VerdictRegistry (Host 3) first — proceeds only on cache miss
- Dispatches `AnalysisRequest` to BAE pool (Host 2) via BAE smart proxy pool
- Submits signed `JarAnalysisReport` to Host 3
- Discovers BAE instances from Host 1 (pool of proxy stubs, round-robin)
- No connection to Host 5

### Host 5 — JFR Telemetry Service (any OS) — **reactive**
- Receives `jdk.VirtualThreadPinned` JFR events from client hosts via JERI
- Independently scalable — separated from Host 4 to prevent client flood causing DOS on analysis pipeline
- Rate-limits and deduplicates per client identity (authenticated via JERI)
- Correlates events with content hash
- Triggers reanalysis via BAE proxy pool (Host 2)
- Submits updated signed verdict to Host 3
- JFR events are **advisory only** — cannot directly update Host 3
- All verdict updates must pass through signed analysis pipeline
- No connection to Host 4

### Client hosts (any OS)
- `ProxyCodebaseSPI` present on every client
- Discovers services from Host 1 (marshalled, opaque)
- `ProxyCodebaseSPI` fires **before unmarshal**: hashes JAR, queries Host 3 directly
- Clean verdict → unmarshal proxy; no/bad verdict → refuse, log, alert
- Reports `jdk.VirtualThreadPinned` JFR events to Host 5 (advisory, authenticated)
- Direct IPv6 connections to services after unmarshal (no relay)
- VerdictRegistry proxy stub connects to Host 3 independently of Host 1

### Connection matrix (no direct paths between these):
- Host 2 ↔ Host 3: no direct connection
- Host 4 ↔ Host 5: no direct connection
- Host 1 ↔ Host 2: no direct connection
- Clients ↔ Host 2: no direct connection
- Clients ↔ Host 4/5: Host 5 only (JFR events)

---

## 5. Bytecode Analysis Engine — Issue #204

### Current implementation problems (to be fixed):
- Uses constant-pool string pattern matching (`DANGEROUS_CP_ENTRIES`) — wrong technique
- Massive false positive rate (flags any class using reflection)
- No relationship to stated analysis goals
- Two-state `SAFE`/`DANGEROUS` verdict is too coarse
- Engine calls `url.openStream()` directly — SELinux-isolated process must not fetch URLs
- ~300 lines of metrics infrastructure obscures core logic

### What needs to be REMOVED:
- `DANGEROUS_CP_ENTRIES` array and `containsDangerousCode` method
- Two-state `SAFE`/`DANGEROUS` verdict model as primary output
- URL `openStream()` inside the analysis engine
- Metrics subsystem (extract to `AnalysisMetrics` or defer)

### What needs to be ADDED:

**Check 1: `<clinit>` blocking detection**
- ASM `ClassReader` in non-classloading mode (parses `byte[]`, never calls `Class.forName`)
- `ClinitBlockingVisitor` — visits `<clinit>`, records all outbound call sites
- **Blocking sink registry** — maintained `Set<String>` of `owner/name/descriptor` triples known to block:
  - `java/lang/Thread.sleep`
  - `java/lang/Object.wait`
  - `java/util/concurrent/locks/LockSupport.park`
  - `java/io/InputStream.read`
  - `java/net/Socket` methods
  - `java/io/RandomAccessFile` methods
  - `java/util/concurrent/CountDownLatch.await`
  - `java/util/concurrent/Semaphore.acquire`
  - `java/util/concurrent/BlockingQueue` family
- BFS/DFS call graph traversal from `<clinit>` across all classes in JAR
- **Native method opacity registry** — known-safe vs. known-blocking natives; unregistered natives → `WARN`
- **Circular `<clinit>` dependency detection** — separate pass building initialisation dependency graph, detecting cycles

**Check 2: `@AtomicSerial` compliance**
- `AtomicSerialComplianceVisitor`
- Checks constructor with signature `(Lorg/apache/river/api/io/AtomicSerial$GetArg;)V` exists
- Validates **validation-before-super call order** — static check method must be called BEFORE `super(...)` in constructor body
- Checks `public static SerialForm[] serialForm()` method present (unless `@Stateless`)
- Checks `public static void serialize(PutArg, T)` method present (unless `@Stateless`)
- Flags `ATOMIC_SERIAL_NOT_ANNOTATED` — class with `GetArg` constructor but no `@AtomicSerial` annotation
- Note: `readObject`/`writeObject` bypass detection NOT needed — JERI constraint + `SerializationPermission` guards this; `AtomicSerial` does not call Java serialization methods

**New API — push model (replaces pull/URL model):**
```java
JarAnalysisReport analyzeJar(AnalysisRequest request) throws AnalysisException;
```

```java
// AnalysisRequest — crosses JERI boundary, must be @AtomicSerial compliant
class AnalysisRequest {
    byte[]  jarBytes;        // JAR content — pushed by caller, not fetched by engine
    String  contentHash;     // SHA-256, computed by caller before sending
    Uri     originalUri;     // for traceability/reporting only
    int     maxBfsDepth;     // configurable BFS depth limit (default ~10)
}
```

**Structured output replacing binary verdict:**
```java
class ClassAnalysisResult {
    String           className;
    ClinitVerdict    clinitVerdict;    // CLEAN | BLOCKING | NATIVE_OPACITY | CYCLE
    AtomicSerialVerdict atomicVerdict; // COMPLIANT | NOT_ANNOTATED | MISSING_CONSTRUCTOR
                                       // VALIDATION_ORDER | MISSING_SERIAL_FORM | N/A
    List<String>     blockingCallPath; // call chain to blocking sink (for BLOCKING)
    List<String>     cycleParticipants;// classes in cycle (for CYCLE)
}

class JarAnalysisReport {
    String                        contentHash;
    Map<String, ClassAnalysisResult> results;
    byte[]                        engineSignature; // signed by engine private key
}
```

**Two-phase JAR analysis:**
- Phase 1: Index all classes from JAR bytes using ASM `ClassReader` → `Map<String, byte[]>`
- Phase 2: Run composed visitors over each class; BFS call graph traversal requires full index from Phase 1

**Fail-secure on parse failure:** Any class file that ASM cannot parse → treated as `CLINIT_BLOCKING` + `ATOMIC_SERIAL_VIOLATION` by default.

**Infrastructure to KEEP** (sound, worth preserving):
- Threading model and timeout protection
- Shutdown lifecycle
- Queue bounding
- Signing/registry submission infrastructure

---

## 6. TLS Certificate Management — Issue #205

### Two distinct certificate populations:

**Tier 2 — JERI TLS (SPIRE SVIDs, short-lived ~1 hour):**
- All JERI connections between all hosts and clients
- SPIFFE/SPIRE as identity fabric
- OS-agnostic, automatic rotation
- SPIFFE trust domain: `spiffe://jgdms.example.org/...`
- SPIFFE ID scheme:
  ```
  spiffe://jgdms.example.org/host/lookup       → Host 1
  spiffe://jgdms.example.org/host/bae/engine-N → Host 2 instances
  spiffe://jgdms.example.org/host/registry     → Host 3
  spiffe://jgdms.example.org/host/downloader   → Host 4
  spiffe://jgdms.example.org/host/telemetry    → Host 5
  spiffe://jgdms.example.org/client/<id>       → clients
  ```
- Each Host 2 engine instance has its own key pair — individual revocation without pool disruption
- Engine **signing key** (for `JarAnalysisReport`) is distinct from TLS SVID — longer-lived, hardware-backed if possible, deliberately rotated

**`SpiffeCredentialManager` module (to be implemented):**
```java
// Reusable across all five hosts and all clients
class SpiffeCredentialManager {
    // Opens SPIRE Workload API socket
    // Receives X.509-SVID + trust bundle
    // Populates in-memory KeyStore + TrustStore (no filesystem keystore)
    // Constructs SSLContext for JERI
    // Listens for SVID rotation → rebuilds SSLContext without JVM restart
}
```

**Tier 1 — Multicast signing certificates (DEFERRED — lower priority):**
- Longer-lived (months), for x500 multicast announcement/request packet signing
- Dedicated offline JGDMS Discovery CA (`step-ca` + offline root)
- NOT needed for current priority work (see Section 8)

---

## 7. Global Discovery — Simplified Model

**Chosen approach: `lookup.<domain>:4160` + IPv6**

- Port 4160 is the standard Jini unicast discovery port
- Client constructs `LookupLocator("jini://lookup.example.org:4160")` with zero additional infrastructure
- Works on any network with DNS + TCP access
- IPv6 restores point-to-point connectivity — no NAT, no relay
- `lookup.<domain>:4160` is the entry point into the entire service ecosystem for that domain
- After connecting to lookup service, client can discover all services registered at that domain
- Each discovered service proxy connects directly over IPv6 (no relay)
- DNSSEC on A/AAAA record recommended to prevent DNS poisoning

**DNS-SD (optional, for multi-instance enumeration only):**
- PTR/SRV/TXT records if multiple named lookup instances need enumeration
- Not needed for typical single-lookup-service deployments
- TXT record can carry SPIFFE URI + proxy hash for pre-validation

**Multicast (deferred):**
- Site-local: within single site/campus LAN
- Organisation-local: within controlled enterprise WAN
- Both require x500 Tier 1 signing certificates
- Not viable on public internet
- Lower priority — deferred until foundational work complete

**Discovery hierarchy (simple to complex):**
```
1. lookup.<domain>:4160      — plain A/AAAA + LookupLocator, zero infrastructure  ← CURRENT PRIORITY
2. DNS-SD SRV records        — multi-instance enumeration
3. Org-local multicast       — controlled WAN, x500 signed                         ← DEFERRED
4. Site-local multicast      — single site, x500 signed                            ← DEFERRED
```

---

## 8. Agreed Priorities — Next Work Items

**IN SCOPE (current focus):**

1. **BAE core analysis rewrite (Issue #204)**
   - Replace constant-pool scan with ASM-based visitors
   - `ClinitBlockingVisitor` + BFS call graph + blocking sink registry
   - `ClinitCycleVisitor` for circular `<clinit>` dependency detection
   - `AtomicSerialComplianceVisitor`
   - Structured `ClassAnalysisResult` + `JarAnalysisReport` output
   - Push API (`analyzeJar(AnalysisRequest)`)

2. **VerdictRegistry service (Issue #206 — not yet created)**
   - Persistent store keyed by SHA-256 content hash
   - JERI server skeleton
   - `submit(engineId, JarAnalysisReport)` — verifies engine signature
   - `lookup(contentHash)` — for `ProxyCodebaseSPI` and Host 4/5
   - Trusted engine public key set (per engine instance)

3. **`SpiffeCredentialManager` module (Issue #205)**
   - SPIRE Workload API integration
   - In-memory `KeyStore` + rotating `SSLContext`
   - Reusable across all five hosts and clients
   - `DiscoveryCredentialProvider` interface (Tier 2 only for now)

4. **Host 4 — Codebase downloader service**
   - `ServiceRegistrar` listener on Host 1
   - BAE smart proxy pool (discovers engines from Host 1)
   - VerdictRegistry proxy
   - SPIRE SVID identity

5. **`ProxyCodebaseSPI` integration**
   - VerdictRegistry lookup before unmarshal
   - SPIRE SVID for registry connection
   - Refuse unmarshal on bad/missing verdict

**DEFERRED (lower priority):**
- Multicast discovery (site-local and organisation-local)
- Tier 1 multicast signing certificates (`step-ca` offline CA)
- `DiscoveryCredentialProvider` Tier 1 path
- DNS-SD multi-instance enumeration
- JFR telemetry service (Host 5) — depends on VerdictRegistry

---

## 9. Key Design Decisions and Rationale

| Decision | Rationale |
|---|---|
| BAE receives pushed bytes, not URLs | SELinux-isolated process must not initiate outbound network connections |
| Each Host 2 engine has own key pair | Individual revocation without taking down pool |
| Host 2 and Host 3 have no direct connection | Compromised analysis host cannot write to verdict store |
| Host 4 and Host 5 separated | Client JFR flood cannot DOS the proactive analysis pipeline |
| JFR events advisory only | Clients less trusted than engine; verdicts must be engine-signed |
| VerdictRegistry keyed by content hash, not URL | Same JAR at different URLs analysed once; URL changes don't invalidate verdicts |
| SPIRE SVIDs ~1 hour lifetime | Short-lived limits breach blast radius; passive revocation |
| Engine signing key separate from TLS SVID | Different rotation cadences; signing key warrants hardware backing |
| `lookup.<domain>:4160` over DNS-SD | Simpler, zero infrastructure, works everywhere with DNS+TCP |
| IPv6 assumed | Restores point-to-point connectivity; eliminates NAT traversal complexity |
| Multicast deferred | Not viable globally; adds complexity; `LookupLocator` covers the use case |
| `readObject`/`writeObject` bypass NOT checked | JERI constraint + `SerializationPermission` already guard this path |
| Fail-secure on BAE parse failure | Unparseable class file treated as BLOCKING + VIOLATION |

---

## 10. Relevant Files and Locations

**DirtyChai (https://github.com/pfirmstone/DirtyChai):**
- `README.md`, `EXECUTIVE_SUMMARY.md`, `PHILOSOPHY.md`, `SECURITY_MODEL.md`
- `VULNERABILITIES_ADDRESSED.md`, `HISTORY.md`, `CONTRIBUTING.md`

**JGDMS (https://github.com/pfirmstone/JGDMS):**
- BAE implementation: `JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/main/java/au/net/zeus/jgdms/bae/BytecodeAnalysisEngineImpl.java`
- AtomicSerial: `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java`
- x500 discovery providers: `JGDMS/jgdms-discovery-providers/src/main/java/org/apache/river/discovery/x500/`
- **JERI ACC transport for DigestCodeSource:** `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/io/AccessControlContextSerializer.java`
  — new `digestTransportBytes` serial field; `DigestCodeSource` serialized via `AtomicMarshalOutputStream`/`AtomicMarshalInputStream`; `DomainIdentityRecord.from()` skips `DigestCodeSource` to prevent httpmd-URL duplication; fail-secure on standard JDK (ClassNotFoundException → domain dropped)
- Issue #204: https://github.com/pfirmstone/JGDMS/issues/204
- Issue #205: https://github.com/pfirmstone/JGDMS/issues/205

---

## 11. DirtyChai & Pack200 Binary Distribution — CI/Build Mechanism

### Why not Maven?

DirtyChai is a full JDK binary (C/C++ sources, produces native executables + JVM). It cannot be installed as a Maven artifact. The same applies to **Pack200-ex-openjdk**, which is a standalone JAR published as a GitHub Release binary rather than via Maven Central (it is a fork of a removed JDK tool).

### How DirtyChai is obtained in CI

DirtyChai is distributed via GitHub Releases on the rolling tag `dirty-chai-latest` at `pfirmstone/DirtyChai`. The release contains two assets:

| Asset | Purpose |
|---|---|
| `jdk-linux-x64.tar.gz` | Pre-built JDK binary (linux/x64) |
| `jdk-linux-x64.tar.gz.sha256` | SHA-256 checksum file |

The CI step downloads both assets using `gh release download`, verifies the checksum, extracts the tarball to `/opt/dirtychai`, then locates the real `JAVA_HOME` by searching for `javac` inside the extracted directory tree (the tarball expands into a versioned subdirectory such as `jdk-24.0.1+7`).

```sh
gh release download dirty-chai-latest \
  --repo pfirmstone/DirtyChai \
  --pattern 'jdk-linux-x64.tar.gz' \
  --pattern 'jdk-linux-x64.tar.gz.sha256' \
  --dir "$DL_DIR"

(cd "$DL_DIR" && sha256sum -c jdk-linux-x64.tar.gz.sha256)
sudo tar -xzf "$DL_DIR/jdk-linux-x64.tar.gz" -C /opt/dirtychai

JDK_DIR="$(dirname "$(find /opt/dirtychai -name 'javac' -type f | head -1)")"
JDK_DIR="${JDK_DIR%/bin}"
echo "JAVA_HOME=$JDK_DIR" >> "$GITHUB_ENV"
echo "$JDK_DIR/bin"       >> "$GITHUB_PATH"
```

**Graceful degradation:** If the `dirty-chai-latest` release does not exist yet, `gh release download` exits non-zero and the step sets `installed=false`. Downstream steps gate on this output, so the workflow continues using the runner's system JDK (Temurin 17). This means CI always passes; the DirtyChai matrix leg is **opportunistic**.

### Where the mechanism lives

| File | Purpose |
|---|---|
| `.github/workflows/copilot-setup-steps.yml` | Agent pre-setup — installs DirtyChai once before all agent tasks |
| `.github/workflows/spiffe-unit-tests.yml` | SPIFFE unit test matrix — Temurin-17 leg + DirtyChai leg |

### Maven profile for DirtyChai builds

`JGDMS/jgdms-jeri/pom.xml` contains a profile `dirtychai` (activated with `-Pdirtychai`) that enables SecurityManager-related test configuration when compiling/testing against DirtyChai. Pass this flag on the Maven command line when DirtyChai is the active JDK:

```sh
mvn test -pl jgdms-jeri -Dtest=SpiffeCredentialManagerTest -Pdirtychai
```

### Pack200-ex-openjdk — same pattern, installed into Maven local repo

`Pack200-ex-openjdk` is also distributed via GitHub Releases (not Maven Central). Its version is read from `<pack200.version>` in the root `pom.xml`. The CI step downloads the JAR and installs it into the local Maven repository using `mvn install:install-file`:

```sh
PACK_VER=$(grep -m1 '<pack200.version>' pom.xml | sed 's|.*<pack200.version>\(.*\)</pack200.version>.*|\1|')
gh release download "${PACK_VER}" \
  --repo pfirmstone/Pack200-ex-openjdk \
  --pattern "Pack200-ex-openjdk-${PACK_VER}.jar" \
  --dir /tmp/pack200-dl
mvn install:install-file \
  -Dfile="/tmp/pack200-dl/Pack200-ex-openjdk-${PACK_VER}.jar" \
  -DgroupId=au.net.zeus.pack200-ex-openjdk \
  -DartifactId=Pack200-ex-openjdk \
  -Dversion="${PACK_VER}" \
  -Dpackaging=jar
```

Unlike DirtyChai (which is a JDK toolchain), Pack200-ex-openjdk is a plain JAR so `mvn install:install-file` installs it into `~/.m2/repository` and Maven resolves it normally from there.

---

## 12. Suggested Next Actions for AI Agent

When continuing this conversation, the agent should:

1. **Read Issue #204** (https://github.com/pfirmstone/JGDMS/issues/204) for the current BAE revision specification — it captures the analysis from this conversation in issue form.

2. **Read Issue #205** (https://github.com/pfirmstone/JGDMS/issues/205) for the TLS/SPIRE certificate management specification.

3. **Fetch `BytecodeAnalysisEngineImpl.java`** before proposing code changes — the current implementation is the baseline.

4. **Fetch `AtomicSerial.java`** before implementing the compliance visitor — the contract is in the annotations and Javadoc.

5. **Draft Issue #206** for the VerdictRegistry service if asked — it is the next unwritten issue.

6. The most productive entry points for new work are:
   - Implementing the three ASM visitor classes for the BAE
   - Designing the VerdictRegistry service API and data model
   - Designing the `SpiffeCredentialManager` module interface