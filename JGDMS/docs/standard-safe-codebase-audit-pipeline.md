# JGDMS-STD-002 — Safe Codebase Audit Pipeline Standard

**Version:** 1.1  
**Status:** Active  
**Scope:** Deployment topology, trust boundaries, data flow, and service contracts for
the JGDMS dynamic static-analysis pipeline that audits bytecode before it is unmarshalled
by clients.

---

## Purpose

This standard defines the **five-host service architecture** that JGDMS uses to audit
third-party JARs for safety before any client unmarshals objects from them.  It
specifies the role, trust level, network connectivity, and key material of each host,
the data objects that flow between them, and the invariants that the system preserves
in the presence of a compromised analysis engine.

The architecture is formally named:

> **JGDMS Safe Codebase Audit Pipeline (SCAP)**

---

## Motivation

A distributed Java system that loads remote code (via Jini / RMI codebase annotation)
is exposed to a **supply-chain attack**: an attacker replaces a legitimate JAR with one
that contains malicious or denial-of-service bytecode.  Virtual-thread carrier pinning
caused by blocking `<clinit>` paths is a related liveness attack that can stall a JVM
with as few as `Runtime.availableProcessors()` concurrent class-load triggers.

SCAP defends against this by ensuring that:

1. A JAR is **audited by multiple independent analysis engines** before any client
   unmarshals from it.
2. A **quorum of positive verdicts** is required to approve a JAR; a single negative
   verdict from any engine immediately condemns it.
3. The **analysis engines are isolated** and treated as potentially compromisable:
   they never hold the signing key that clients trust, and they never fetch JARs
   themselves.
4. An **abnormal JVM exit** (Phoenix crash) is itself treated as a safety signal — the
   codebase that caused the crash is condemned automatically.
5. **Virtual-thread carrier pinning** caused by blocking `<clinit>` paths is detected
   and classified, protecting liveness as well as integrity.
6. Host 2 and Host 3 have **no direct connection**: a compromised analysis engine
   cannot write verdicts to the registry directly.
7. Host 4 and Host 5 have **no direct connection**: a flood of client JFR events
   cannot DOS the proactive analysis pipeline.

---

## Definitions

| Term | Meaning |
|------|---------|
| BAE | Bytecode Analysis Engine — a Phoenix-activated service that parses bytecode and emits signed `JarAnalysisReport` objects. Runs on Host 2. |
| VR | Verdict Registry — the authoritative, low-risk aggregation service that clients and Hosts 4/5 submit reports to. Runs on Host 3. |
| Lookup Service | Jini Lookup Service. The central service registry that stores opaque marshalled proxy items and fires discovery events. Runs on Host 1. |
| CD | Codebase Downloader — proactive service on Host 4 triggered by Host 1 events. The only component with outbound internet access. |
| JTS | JFR Telemetry Service — reactive service on Host 5 that receives `jdk.VirtualThreadPinned` events from client JVMs and triggers re-analysis. |
| `AnalysisRequest` | Self-contained request carrying Pack200-compressed JAR bytes + SHA-256 hash pushed by Host 4 to a BAE instance on Host 2. |
| `JarAnalysisReport` | Detailed per-class analysis result signed by a BAE instance's private key. Produced by Host 2. |
| `SignedVerdict` | A summarised per-engine verdict (URL set + `VerdictType` + timestamp + engine signature). Derived from a `JarAnalysisReport`. |
| `RegistryVerdict` | The authoritative, Host-3-signed verdict that clients use to gate unmarshalling. |
| `CrashReport` | A Phoenix-signed record of an abnormal JVM exit, submitted directly to Host 3. Treated as an implicit `DANGEROUS` verdict. |
| Quorum | The minimum number of independent BAE verdicts of `SAFE` that the VR (Host 3) requires before issuing a `SAFE` `RegistryVerdict`. |
| JERI | Jini Extensible Remote Invocation — the transport layer used for all inter-service communication. |
| SPIFFE ID | SPIRE-managed X.509 SVID identity used for JERI TLS on all five hosts and all clients. |

---

## The Five Hosts

```
┌─────────────────────────────────────────────────────────────────┐
│  Host 1 — Jini Lookup Service (any OS)                          │
│  Trust level : LOW for analysis; HIGH for discovery             │
│  Stores opaque marshalled service items; fires discovery events  │
│  BAE pool (Host 2) instances register here for discovery        │
│  DOES NOT unmarshal proxies; DOES NOT fetch URLs                 │
└─────────────────────────────────────────────────────────────────┘
         ▲ ServiceRegistrar events            ▲ Jini lookup
         │                                    │
┌────────┴──────────┐              ┌──────────┴──────────────────┐
│ Host 4 — Codebase │              │ Host 5 — JFR Telemetry      │
│ Downloader (CD)   │              │ Service (JTS)               │
│ Trust : MEDIUM    │              │ Trust : LOW (observer)      │
│ Only host with    │              │ Receives VirtualThreadPinned│
│ outbound internet │              │ events from clients (JERI)  │
└────────┬──────────┘              └──────────┬──────────────────┘
         │  analyzeJar(AnalysisRequest)        │  requestAnalysis
         │  (round-robin pool)                 │  (BAE proxy pool)
         ▼                                     ▼
┌────────────────────────────────────────────────────────────────┐
│  Host 2 — SELinux Analysis Engine Pool (BAE)                   │
│  Replicated N times, completely stateless                       │
│  Trust level : UNTRUSTED (treated as potentially compromised)   │
│  SELinux: inbound JERI from Host 4 + Host 5 only               │
│  No outbound network; no internet; no exec; no JNI; no FFM     │
│  Each instance has its own key pair for signing JarAnalysisReport│
│  Discovered by Hosts 4 and 5 via Jini lookup at Host 1          │
└────────────────────────────────────────────────────────────────┘
         ▲                                      (NO direct path to Host 3)
         │  JarAnalysisReport returned to Host 4 / Host 5
         │  Host 4/5 submit reports to Host 3:
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Host 3 — Verdict Registry (VR) (any OS)                        │
│  Trust level : HIGH (holds clients' signing key)                │
│  Persistent store keyed by SHA-256 content hash                 │
│  Maintains set of trusted engine public keys (one per instance) │
│  Inbound JERI from Host 4, Host 5, all clients                  │
│  No connection to Host 2; no bytecode parsing                   │
└─────────────────────────────────────────────────────────────────┘
         │  RegistryVerdict (push/pull)
         ▼
    Clients (query Host 3 directly; report JFR events to Host 5)
```

> **AI-agent note:** The SVG `bae_replicated_host2.svg` in the repository root is the
> authoritative topology diagram.  Its description field reads: *"Same five-host topology
> as before but Host 2 is now shown as a pool of N replicated SELinux analysis nodes,
> all stateless. Host 4 and Host 5 each hold a pool of BAE smart proxy stubs, one per
> engine instance, discovered via Jini lookup. Requests are distributed across available
> engine instances. Each instance signs its report with its own engine private key.
> Host 3 maintains a set of trusted engine public keys and accepts signed reports from
> any registered engine instance. No coordination is needed between engine instances
> because each AnalysisRequest is fully self-contained."*

---

## Host Roles in Detail

### Host 1 — Jini Lookup Service

**Role.** Service registry.  Stores marshalled service items **opaquely** — it does NOT
unmarshal proxies, does NOT perform analysis, does NOT fetch URLs.

**Key behaviours:**
- Fires `ServiceRegistrar` discovery events when new services register.  Host 4 (CD)
  listens for these events to know when to download and analyse a new codebase.
- BAE instances (Host 2 pool) **register** their proxy stubs here so that Hosts 4
  and 5 can discover the pool via standard Jini lookup.
- Hosts 4 and 5 hold a **BAE smart proxy pool** (one proxy stub per engine instance)
  discovered from Host 1 and used for round-robin dispatch of `analyzeJar` calls.
- The VerdictRegistry proxy (Host 3) is also registered here for client discovery.

**Trust level.** LOW with respect to the analysis pipeline.  Host 1 never validates
content; it is a passive registry.

**SPIFFE ID:** `spiffe://jgdms.example.org/host/lookup`

---

### Host 2 — SELinux Analysis Engine Pool (BAE)

**Role.** Deep-parses untrusted bytecode and produces a signed `JarAnalysisReport`.
Deployed as N **completely stateless** replicated instances.

**Trust level.** **UNTRUSTED.**  A BAE must be treated as **potentially compromised
at all times**:
- Runs in a Phoenix activation group with restrictive SELinux policy.
- **No outbound network access** — JAR bytes are pushed to it by Host 4 or Host 5 via
  `analyzeJar(AnalysisRequest)`.  The `AnalysisRequest` is self-contained (bytes +
  SHA-256 hash).
- Inbound JERI connections accepted only from Host 4 and Host 5.
- **No direct connection to Host 3** (VerdictRegistry).  This is the key isolation
  invariant: a compromised engine cannot write verdicts to the registry directly.
- Each instance holds its own asymmetric key-pair.  Individual revocation without
  taking down the pool.

**Analysis performed on each JAR:**

| Visitor | What it checks | Verdict type |
|---------|---------------|-------------|
| `ClinitBlockingVisitor` | BFS from `<clinit>` to blocking sinks | `ClinitVerdict` |
| `AtomicSerialComplianceVisitor` | `@AtomicSerial` protocol adherence | `AtomicSerialVerdict` |
| Cycle detector | Circular `<clinit>` dependency | `ClinitVerdict.CYCLE` |

Any class file that cannot be parsed by ASM is assigned
`ClinitVerdict.BLOCKING` + `AtomicSerialVerdict.MISSING_CONSTRUCTOR` (fail-secure).

**Scalability.** Add instances to the pool as load requires.  No inter-instance
coordination is needed.

**Crash signal.** If a BAE's Phoenix activation group exits abnormally while analysing
a JAR, Phoenix submits a `CrashReport` to Host 3 for the codebase under analysis.

**SPIFFE ID pattern:** `spiffe://jgdms.example.org/host/bae/engine-N`  
(Each instance has its own SPIFFE ID and own engine signing key-pair.)

---

### Host 3 — Verdict Registry (VR)

**Role.** The single source of truth for codebase safety.  Clients only trust
`RegistryVerdict` objects signed by this host's private key.

**Trust level.** **HIGH.**  This host:
- Holds the registry private signing key (the only key clients trust).
- Never parses bytecode; never fetches URLs.
- Receives only well-typed, signed data objects over authenticated JERI connections.
- Has **no connection to Host 2** (BAE pool).

**Inputs:**

| Method | Caller | What it does |
|--------|--------|--------------|
| `registerAnalysisEngine(engineId, engineKey, sigAlgorithm)` | Operator | Registers a BAE instance public key |
| `revokeAnalysisEngine(engineId)` | Operator | Removes a BAE instance from the registry |
| `submitReport(engineId, JarAnalysisReport)` | Host 4 or Host 5 | Submits a detailed signed report; VR derives `VerdictType` and applies quorum policy |
| `submitVerdict(engineId, SignedVerdict)` | Host 4 or Host 5 | Submits a pre-aggregated verdict (legacy path) |
| `reportCrash(CrashReport)` | Phoenix (via Host 4 or direct) | Immediately condemns the affected codebase |
| `registerVerdictListener(...)` | Clients | Subscribe to verdict push events |
| `getVerdictByHash(contentHash)` | Clients | Poll for verdict by SHA-256 hash |
| `getVerdict(codebaseUrls)` | Clients | Poll for verdict by URL set |

**Quorum policy:**
- Verdicts from at least **K of N** registered BAE instances must be `SAFE`
  (operator-configured K and N).
- A single `DANGEROUS` verdict from **any** engine immediately issues a `DANGEROUS`
  `RegistryVerdict`.
- A `CrashReport` is treated as an implicit `DANGEROUS` vote.
- `INCONCLUSIVE` verdicts do not count toward the `SAFE` quorum.

**SPIFFE ID:** `spiffe://jgdms.example.org/host/registry`

---

### Host 4 — Codebase Downloader (CD)

**Role.** **Proactive** mediator between the Jini lookup infrastructure and the
analysis pipeline.

**Trust level.** **MEDIUM.**  Trusted Jini participant; not on the critical signing
path.

**Trigger:** `ServiceRegistrar` discovery events from Host 1.

**Workflow for a new JAR:**
```
Host 1 fires ServiceRegistrar event (new codebase URL discovered)
  → CD checks VR (Host 3): getVerdictByHash(H) — skip if already known
  → CD downloads JAR bytes B over TLS (only host with outbound internet)
  → CD computes SHA-256 hash H of B
  → CD builds AnalysisRequest(Pack200-compressed(B), H, bfsDepth, codebaseUrls)
  → For each BAE instance in pool (Host 2):
       report = BAE.analyzeJar(request)          // round-robin dispatch
       VR.submitReport(engineId, report)          // CD submits to Host 3
  → Wait for RegistryVerdict via event or poll
  → Gate client access on RegistryVerdict.getVerdictType()
```

**Key properties:**
- **Only** component with outbound internet access.
- Discovers BAE instances from Host 1 (standard Jini lookup); holds pool of proxy stubs.
- Does **not** trust the `JarAnalysisReport` itself — it forwards it to Host 3 for
  signature verification.  Host 3 is the sole interpreter of verdicts.
- Has **no connection to Host 5**.

**SPIFFE ID:** `spiffe://jgdms.example.org/host/downloader`

---

### Host 5 — JFR Telemetry Service (JTS)

**Role.** **Reactive** runtime cross-check.  Receives `jdk.VirtualThreadPinned` JFR
events from client JVMs and triggers re-analysis of suspicious codebases.

**Trust level.** **LOW (observer only).**  The JTS:
- Never submits verdicts to Host 3 directly — all verdict updates must pass through
  the signed analysis pipeline (Host 2 → Host 4 or Host 5 → Host 3).
- Receives JFR events from client JVMs via authenticated JERI.
- Rate-limits and deduplicates per client identity.
- Correlates pinning events with content hash; triggers re-analysis on Host 2.
- After re-analysis, submits the updated `JarAnalysisReport` to Host 3.
- Has **no connection to Host 4**.

**Rationale for separation from Host 4:** A flood of client JFR events cannot cause a
denial of service on the proactive analysis pipeline.

**SPIFFE ID:** `spiffe://jgdms.example.org/host/telemetry`

---

### Client Hosts

- `ProxyCodebaseSPI` is present on every client.
- Discovers services from Host 1 (marshalled, opaque).
- `ProxyCodebaseSPI` fires **before unmarshal**: hashes the JAR, queries Host 3
  directly for a `RegistryVerdict`.
- Clean verdict (`SAFE`) → unmarshal proxy; absent or `DANGEROUS` → refuse, log, alert.
- Reports `jdk.VirtualThreadPinned` JFR events to Host 5 (advisory, authenticated).
- After unmarshal, connects directly to services over IPv6 (no relay).

**SPIFFE ID pattern:** `spiffe://jgdms.example.org/client/<id>`

---

## Connection Matrix

The following connections are the **only** permitted inter-host paths.  All others
are explicitly forbidden.

| From | To | Protocol | Purpose |
|------|----|----------|---------|
| Host 4 (CD) | Host 1 (Lookup) | JERI | Listen for `ServiceRegistrar` events; discover BAE pool |
| Host 5 (JTS) | Host 1 (Lookup) | JERI | Discover BAE pool |
| Host 4 (CD) | Host 2 (BAE pool) | JERI | Push `AnalysisRequest`; receive `JarAnalysisReport` |
| Host 5 (JTS) | Host 2 (BAE pool) | JERI | Trigger re-analysis |
| Host 4 (CD) | Host 3 (VR) | JERI | Submit `JarAnalysisReport`; check verdict cache |
| Host 5 (JTS) | Host 3 (VR) | JERI | Submit re-analysis `JarAnalysisReport` |
| Clients | Host 1 (Lookup) | JERI | Discover registered services |
| Clients | Host 3 (VR) | JERI | Query `RegistryVerdict` by hash or URL |
| Clients | Host 5 (JTS) | JERI | Report `jdk.VirtualThreadPinned` events |

### Explicitly forbidden connections (key isolation invariants)

| Forbidden path | Rationale |
|---------------|-----------|
| Host 2 (BAE) → Host 3 (VR) | Compromised engine cannot write verdicts directly |
| Host 4 (CD) ↔ Host 5 (JTS) | JFR flood from clients cannot DOS proactive pipeline |
| Host 1 (Lookup) ↔ Host 2 (BAE) | Lookup service does not trigger analysis |
| Clients ↔ Host 2 (BAE) | Clients never interact with analysis engines |
| Clients ↔ Host 4 (CD) | Clients do not drive the download pipeline |

---

## Data Objects

### `AnalysisRequest` (`@AtomicSerial`)

Carries JAR bytes from Host 4 or Host 5 to a BAE instance on Host 2.

| Field | Wire form | Notes |
|-------|-----------|-------|
| `packedJarBytes` | `byte[]` | Pack200-compressed JAR bytes (serial form only) |
| `jarBytes` (in-memory) | `byte[]` | Decompressed in `check(GetArg)` — never serialized |
| `contentHash` | `String` | SHA-256 hex digest of the **raw** bytes, computed by Host 4/5 |
| `originalUri` | `URI` | Primary codebase URL (traceability only) |
| `maxBfsDepth` | `int` | Maximum BFS depth for `<clinit>` analysis |

Decompression happens exactly once inside `AnalysisRequest.check(GetArg)`.  The
bridge constructor receives the raw bytes directly — no double unpacking.

### `JarAnalysisReport` (`@AtomicSerial`)

Produced by a BAE instance.  Contains per-class `ClassAnalysisResult` records and is
signed with the BAE instance's private key.  The aggregate `VerdictType` is derived
via `JarAnalysisReport.deriveVerdictType()`.

### `SignedVerdict` (`@AtomicSerial`)

A summary verdict (URL set + `VerdictType` + timestamp + BAE instance signature).
Used by the legacy submission path.

### `RegistryVerdict` (`@AtomicSerial`)

The sole artefact clients trust.  Contains URL set + `VerdictType` + timestamp +
Host-3 signature.

### `CrashReport` (`@AtomicSerial`)

Submitted by Phoenix on an abnormal group exit.  Contains URL set + exit code +
incarnation number + sanitised stderr excerpt + Phoenix signature.

---

## Verdict Derivation

`JarAnalysisReport.deriveVerdictType()` maps per-class results to an aggregate
`VerdictType` using the following priority order:

1. Any `ClinitVerdict.BLOCKING`, `ClinitVerdict.CYCLE`, or any
   `AtomicSerialVerdict` of `MISSING_CONSTRUCTOR`, `VALIDATION_ORDER`,
   `MISSING_SERIAL_FORM`, or `UNTYPED_GET` → **`DANGEROUS`**
2. Any `ClinitVerdict.NATIVE_OPACITY`, `ClinitVerdict.BLOCKING_GUARDED`, or
   `AtomicSerialVerdict.NOT_ANNOTATED` → **`INCONCLUSIVE`** (unless already `DANGEROUS`)
3. All results `CLEAN` / (`COMPLIANT` or `NA`) → **`SAFE`**

`BLOCKING_GUARDED` means the blocking path is only reachable when the caller holds a
specific Java permission (guard precedes the blocking sink in the call graph).  This
is `INCONCLUSIVE` because granting that permission implicitly accepts the pinning risk.

---

## SPIFFE Identity Scheme

All JERI connections use SPIRE-managed X.509 SVIDs (~1 hour lifetime, automatic
rotation).  Engine signing keys are distinct from JERI SVIDs (different rotation
cadence; hardware backing recommended).

```
spiffe://jgdms.example.org/host/lookup          → Host 1
spiffe://jgdms.example.org/host/bae/engine-N    → Host 2 instance N
spiffe://jgdms.example.org/host/registry        → Host 3
spiffe://jgdms.example.org/host/downloader      → Host 4
spiffe://jgdms.example.org/host/telemetry       → Host 5
spiffe://jgdms.example.org/client/<id>          → clients
```

---

## Full Verdict Flow

```
Host 1 fires ServiceRegistrar event (new codebase detected)
     │
     ▼
Host 4 (CD): check Host 3 cache by hash — skip if already known
     │
     ▼
Host 4: download JAR bytes B; compute SHA-256 hash H
     │
     ▼
AnalysisRequest (Pack200-compressed B, H, bfsDepth, codebaseUrls)
     │ pushed to each BAE instance in pool via proxy stub
     ▼
BAE #1 analyzeJar()     BAE #2 analyzeJar()  ...  BAE #N analyzeJar()
     │                        │                         │
  JarAnalysisReport        JarAnalysisReport         JarAnalysisReport
  (BAE-1-signed)           (BAE-2-signed)            (BAE-N-signed)
     │                        │                         │
     └──────── Host 4 → VR.submitReport(engineId, report) ──────────┘
                              │  (Host 3)
                        verifySignature()
                        deriveVerdictType()
                        applyQuorumPolicy()
                              │
                        RegistryVerdict (Host-3-signed)
                              │
                  ┌───────────┴──────────────┐
                  │                          │
            Event push                getVerdictByHash(H)
            to clients                (client poll)


Separately — reactive path (Host 5):
Client JVM fires jdk.VirtualThreadPinned event
     │
     ▼
Host 5 (JTS): rate-limit + deduplicate; correlate with content hash
     │
     ▼
Host 5 → BAE pool: requestAnalysis(codebaseUrls) [fire-and-forget]
     │
     ▼
BAE re-analyses; Host 5 submits updated report to Host 3
```

---

## Failure Modes and Invariants

| Event | System response |
|-------|----------------|
| BAE instance crashes while analysing a JAR | Phoenix submits `CrashReport` to Host 3 → immediate `DANGEROUS` verdict |
| BAE instance compromised; submits false `SAFE` | Quorum requires K of N; single compromised instance cannot satisfy quorum alone |
| BAE instance compromised; submits false `DANGEROUS` | Conservative false negative — codebase condemned; operationally safe |
| Host 3 receives report with unknown engine key | Report silently discarded |
| Operator revokes a BAE instance | Re-evaluate any `RegistryVerdict` that relied solely on that instance |
| Network partition between Host 4 and BAE pool | New codebases cannot be analysed; existing verdicts remain valid |
| Host 5 (JTS) fails | Re-analysis triggers from JFR events stop; existing verdicts remain; runtime cross-check unavailable (degraded mode) |
| Client JFR event flood | Host 5 rate-limits per client identity; no impact on Host 4 proactive pipeline |

---

## Interface Contract Summary

### `BytecodeAnalysisEngine` (Host 2 — each pool instance)

```java
JarAnalysisReport analyzeJar(AnalysisRequest request)
        throws AnalysisException, RemoteException;

void requestAnalysis(Set<Uri> codebaseUrls)
        throws RemoteException;
```

### `VerdictRegistry` (Host 3)

```java
// BAE lifecycle (operator)
void registerAnalysisEngine(String engineId, PublicKey engineKey, String sigAlgorithm);
void revokeAnalysisEngine(String engineId);

// Verdict submission (Host 4 and Host 5)
void submitReport(String engineId, JarAnalysisReport report);    // preferred
void submitVerdict(String engineId, SignedVerdict verdict);       // legacy
void reportCrash(CrashReport report);

// Client access
RegistryVerdict getVerdictByHash(String contentHash);            // hash-keyed
RegistryVerdict getVerdict(Set<Uri> codebaseUrls);               // URL-keyed

// Event subscription (clients)
EventRegistration registerVerdictListener(RemoteEventListener, Set<Uri>,
                                          MarshalledInstance, long);
long renewEventLease(Uuid leaseId, long duration);
void cancelEventLease(Uuid leaseId);
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| BAE receives pushed bytes, not URLs | SELinux-isolated process must not initiate outbound network |
| Host 2 → Host 3: no direct connection | Compromised engine cannot write verdicts to registry |
| Host 4 and Host 5 separated | Client JFR flood cannot DOS proactive analysis pipeline |
| Each BAE instance has its own key pair | Individual revocation without pool disruption |
| JFR events are advisory only | Clients less trusted than engines; verdicts must pass engine-signing pipeline |
| VerdictRegistry keyed by content hash | Same JAR at different URLs analysed once; URL changes don't invalidate verdicts |
| SPIRE SVIDs ~1 hour lifetime | Short-lived limits breach blast radius; passive revocation |
| Engine signing key separate from TLS SVID | Different rotation cadences; signing key warrants hardware backing |
| Fail-secure on BAE parse failure | Unparseable class file treated as `BLOCKING` + `MISSING_CONSTRUCTOR` |
| `LoadClassPermission` on DirtyChai | Most important guard against carrier-pin DOS post-JEP 491 |

---

## Relationship to JGDMS-STD-001

Every `@AtomicSerial` class serialized and transmitted within SCAP (including
`AnalysisRequest`, `JarAnalysisReport`, `SignedVerdict`, `RegistryVerdict`,
`CrashReport`, `VerdictEvent`) **must** comply with JGDMS-STD-001.  The BAE on
Host 2 validates all inbound serialized classes from external JARs against that
standard.

---

*Document maintained alongside `BytecodeAnalysisEngine.java`, `VerdictRegistry.java`,
`JarAnalysisReport.java`, `AnalysisRequest.java`, and the topology diagram
`bae_replicated_host2.svg` in the repository root.*
