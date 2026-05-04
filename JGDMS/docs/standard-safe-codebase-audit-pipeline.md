# JGDMS-STD-002 — Safe Codebase Audit Architecture Standard

**Version:** 1.0  
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
that contains malicious or denial-of-service bytecode.

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

---

## Definitions

| Term | Meaning |
|------|---------|
| BAE | Bytecode Analysis Engine — a Phoenix-activated service that parses bytecode and emits signed `JarAnalysisReport` objects. |
| VR | Verdict Registry — the authoritative, low-risk aggregation service that clients trust. |
| Codebase Downloader (CD) | Host 4 — fetches JARs, hashes them, pushes `AnalysisRequest` objects to BAEs, and forwards signed reports to the VR. |
| JFR Telemetry Service (JTS) | Host 5 — consumes JDK Flight Recorder events (particularly `jdk.VirtualThreadPinned`) and triggers re-analysis of suspicious codebases. |
| `SignedVerdict` | A per-engine, per-codebase opinion signed with the engine's private key. |
| `JarAnalysisReport` | A detailed per-class breakdown signed by the BAE. |
| `RegistryVerdict` | The authoritative, registry-signed verdict that clients use to gate unmarshalling. |
| `CrashReport` | A Phoenix-signed record of an abnormal JVM exit, treated as an implicit `DANGEROUS` verdict. |
| Quorum | The minimum number of independent BAE verdicts of `SAFE` that the VR requires before issuing a `SAFE` `RegistryVerdict`. |
| JERI | Jini Extensible Remote Invocation — the transport layer used for all inter-service communication. |

---

## The Five Hosts

```
┌───────────────────────────────────────────────────────────┐
│  Host 1 — Verdict Registry (VR)                           │
│  Trust level : HIGH (holds clients' signing key)          │
│  Network     : inbound from Host 2, Host 3, Host 4, Host 5│
│                outbound to clients (event notifications)  │
└───────────────────────────────────────────────────────────┘
         ▲                       ▲
 submitReport / reportCrash    event
         │                       │
┌────────┴──────────┐   ┌────────┴──────────┐
│ Host 2 — BAE #1   │   │ Host 3 — BAE #N   │
│ Trust : UNTRUSTED │   │ Trust : UNTRUSTED │
│ Phoenix-activated │   │ Phoenix-activated │
└────────▲──────────┘   └────────▲──────────┘
         │  analyzeJar           │  analyzeJar
         │                       │
┌────────┴───────────────────────┴──────────┐
│  Host 4 — Codebase Downloader (CD)         │
│  Trust level : MEDIUM                      │
│  Fetches JARs, hashes them, pushes to BAEs │
│  Forwards JarAnalysisReport to VR          │
└───────────────────────────────────────────┘
         ▲
 requestReanalysis (on VirtualThreadPinned events)
         │
┌────────┴──────────────────────┐
│  Host 5 — JFR Telemetry (JTS) │
│  Trust level : LOW (observer) │
│  Listens to JFR streams        │
└───────────────────────────────┘
```

> **Implementation note:** BAE instances 1..N may be deployed on separate hosts or in
> separate Phoenix activation groups on the same host.  Each BAE **must** have its own
> asymmetric key-pair.  What is mandatory is **network-level isolation**: a compromised
> BAE must not be able to reach Host 1 directly except via the documented JERI
> interface.

---

## Host 1 — Verdict Registry (VR)

### Role

The single source of truth for codebase safety.  Clients only trust `RegistryVerdict`
objects signed by this host's private key.

### Trust level

**HIGH.**  This host:
- Holds the registry private signing key (the only key clients trust).
- Never parses bytecode.
- Receives only well-typed, signed data objects over authenticated JERI connections.

### Inputs

| Method | Caller | What it does |
|--------|--------|--------------|
| `registerAnalysisEngine(engineId, engineKey, sigAlgorithm)` | Operator | Registers a BAE public key |
| `revokeAnalysisEngine(engineId)` | Operator | Removes a BAE from the registry |
| `submitReport(engineId, JarAnalysisReport)` | Host 4 (CD) | Submits a detailed signed report; VR derives `VerdictType` |
| `submitVerdict(engineId, SignedVerdict)` | Host 4 (CD) | Submits a pre-aggregated verdict (legacy path) |
| `reportCrash(CrashReport)` | Host 4 or Host 5 | Immediately condemns the affected codebase |
| `registerVerdictListener(...)` | Clients | Subscribe to verdict events |
| `getVerdictByHash(contentHash)` | Clients | Poll for current verdict by SHA-256 hash |
| `getVerdict(codebaseUrls)` | Clients | Poll for current verdict by URL set |

### Outputs

- `RegistryVerdict` — signed authoritative result.  Published via event notification
  (push) or returned via `getVerdict` / `getVerdictByHash` (pull).

### Quorum policy

Before issuing a `SAFE` `RegistryVerdict`:
- Verdicts from at least **K of N** registered BAEs must be `SAFE` (operator-configured
  K and N).
- A single `DANGEROUS` verdict from **any** engine immediately issues a `DANGEROUS`
  `RegistryVerdict` regardless of quorum state.
- A `CrashReport` is treated as an implicit `DANGEROUS` vote.
- `INCONCLUSIVE` verdicts do not count toward the `SAFE` quorum.

### Security invariants

1. The VR private key never leaves Host 1.
2. The VR never fetches URLs or parses bytecode.
3. A revoked engine's past verdicts are re-evaluated; any `RegistryVerdict` that relied
   solely on revoked engines is invalidated.

---

## Host 2 / Host 3 — Bytecode Analysis Engines (BAE)

> Hosts 2 and 3 represent **N ≥ 2 independent BAE instances**.  The following applies
> to each one.

### Role

Deep-parses untrusted bytecode and produces a signed `JarAnalysisReport`.

### Trust level

**UNTRUSTED.**  A BAE must be treated as **potentially compromised at all times**:
- It runs in a Phoenix activation group with restrictive security permissions.
- It **never** holds the registry's private signing key.
- It **never** makes outbound network requests; JAR bytes are pushed to it by Host 4.
- Inbound JERI connections accepted only from Host 4 (Codebase Downloader) and
  Host 5 (JFR Telemetry Service).

### Analysis performed on each JAR

For every `.class` entry:

| Visitor | What it checks | Verdict type |
|---------|---------------|-------------|
| `ClinitBlockingVisitor` | BFS from `<clinit>` to blocking sinks | `ClinitVerdict` |
| `AtomicSerialComplianceVisitor` | `@AtomicSerial` protocol adherence | `AtomicSerialVerdict` |
| Cycle detector | Circular `<clinit>` dependency | `ClinitVerdict.CYCLE` |

Any class file that cannot be parsed by ASM is assigned
`ClinitVerdict.BLOCKING` + `AtomicSerialVerdict.MISSING_CONSTRUCTOR` (fail-secure).

### Verdict derivation

The `JarAnalysisReport.deriveVerdictType()` method maps per-class results to an
aggregate `VerdictType` using the following priority order:

1. Any `ClinitVerdict.BLOCKING`, `ClinitVerdict.CYCLE`, or any
   `AtomicSerialVerdict` of `MISSING_CONSTRUCTOR`, `VALIDATION_ORDER`,
   `MISSING_SERIAL_FORM`, or `UNTYPED_GET` → **`DANGEROUS`**
2. Any `ClinitVerdict.NATIVE_OPACITY`, `ClinitVerdict.BLOCKING_GUARDED`, or
   `AtomicSerialVerdict.NOT_ANNOTATED` → **`INCONCLUSIVE`** (unless already `DANGEROUS`)
3. All results `CLEAN` / (`COMPLIANT` or `NA`) → **`SAFE`**

### Crash signal

If a BAE's Phoenix activation group exits abnormally **while analysing a JAR**, Phoenix
must submit a `CrashReport` to the VR for the codebase that was under analysis.  The
VR treats it as an implicit `DANGEROUS` verdict.

### Key material

Each BAE holds:
- Its own asymmetric key-pair (private key used to sign `JarAnalysisReport`).
- The VR's public key (to verify that its own JERI connection target is genuine).

The BAE does **not** hold the VR private signing key.

---

## Host 4 — Codebase Downloader (CD)

### Role

Mediates between the Jini lookup infrastructure and the analysis pipeline.  It:
1. Intercepts codebase URLs discovered via Jini / RMI annotation.
2. Downloads the JAR bytes over TLS (or from a local cache).
3. Computes the SHA-256 hash of the raw bytes.
4. Wraps the bytes in an `AnalysisRequest` (Pack200-compressed) and pushes it to
   each registered BAE via `BytecodeAnalysisEngine.analyzeJar(AnalysisRequest)`.
5. Receives the signed `JarAnalysisReport` from each BAE and forwards it to the VR via
   `VerdictRegistry.submitReport(engineId, report)`.
6. Polls or subscribes to `RegistryVerdict` events from the VR to determine when a
   codebase has been cleared (or condemned).

### Trust level

**MEDIUM.**  The CD is a trusted Jini participant but it is not on the critical signing
path; it never constructs `RegistryVerdict` objects.

### Key data flow for a new JAR

```
CD fetches JAR bytes (B)
  → compute SHA-256 hash H
  → build AnalysisRequest(B, H, bfsDepth, codebaseUrls)
  → for each BAE:
       report = BAE.analyzeJar(request)          // Pack200 compressed in transit
       VR.submitReport(engineId, report)
  → poll VR.getVerdictByHash(H)  or  await VerdictEvent
  → gate client unmarshalling on RegistryVerdict.getVerdictType()
```

### Security invariants

1. The CD verifies the BAE's JERI server authentication before sending JAR bytes.
2. The CD verifies the VR's JERI server authentication before submitting reports.
3. The CD does not trust the `JarAnalysisReport` itself — it forwards it to the VR
   for signature verification.  The VR is the sole interpreter of verdicts.

---

## Host 5 — JFR Telemetry Service (JTS)

### Role

Listens to JDK Flight Recorder (JFR) event streams from running JVMs in the cluster.
When a `jdk.VirtualThreadPinned` event is observed for a class that originates from a
known codebase URL, the JTS calls:

```java
BAE.requestAnalysis(codebaseUrls)
```

This is a **fire-and-forget** re-analysis trigger that does not supply JAR bytes; it
relies on the BAE (or the CD on the BAE's behalf) to obtain the bytes and re-submit to
the VR.

> **Rationale for re-analysis:** A codebase that previously received a `SAFE` verdict
> may have been silently replaced by a new JAR with a blocking `<clinit>`.  The JFR
> signal provides a runtime cross-check on the static analysis.

### Trust level

**LOW (observer only).**  The JTS:
- Never submits verdicts to the VR directly.
- Never holds analysis or registry key material.
- Can only trigger re-analysis requests.

---

## Data Objects

### `AnalysisRequest`

Carries the JAR bytes from the Codebase Downloader to a BAE.

| Field | Type | Notes |
|-------|------|-------|
| `jarBytes` (in-memory) | `byte[]` | Raw, uncompressed JAR |
| `packedJarBytes` (serial) | `byte[]` | Pack200-compressed — only exists in transit |
| `contentHash` | `String` | SHA-256 hex digest of the **raw** bytes, computed by the CD |
| `originalUri` | `URI` | The primary codebase URL |
| `maxBfsDepth` | `int` | Maximum BFS depth for `<clinit>` analysis |

Decompression happens exactly once, inside `AnalysisRequest.check(GetArg)`, and the raw
bytes are passed directly to the bridge constructor.

### `JarAnalysisReport`

Produced by the BAE.  Contains per-class `ClassAnalysisResult` records and is signed
with the BAE's private key.

### `SignedVerdict`

A summary verdict (URL set + `VerdictType` + timestamp + BAE signature).  Derived from
a `JarAnalysisReport` when the report-based submission path is not used.

### `RegistryVerdict`

The sole artefact clients trust.  Contains URL set + `VerdictType` + timestamp + **VR
signature**.

### `CrashReport`

Submitted by Phoenix on an abnormal group exit.  Contains URL set + exit code +
incarnation number + sanitised stderr excerpt + Phoenix signature.

---

## Trust Hierarchy

```
Client (trusts VR certificate)
    │
    └─▶ VerdictRegistry (Host 1)
            │ registers / revokes / aggregates
            └─▶ BytecodeAnalysisEngine × N (Hosts 2..3)
                    │ receives AnalysisRequest from
                    └─▶ Codebase Downloader (Host 4)
                                │ triggered by
                                └─▶ JFR Telemetry Service (Host 5)
```

A client's security policy must:
1. Apply `Integrity` + `ServerAuthentication` constraints to all calls to the VR.
2. Refuse to unmarshal from a codebase unless `RegistryVerdict.getVerdictType() == SAFE`.
3. Be indifferent to which BAEs produced the constituent `SignedVerdict` objects —
   the VR is the sole authority.

---

## Failure Modes and Invariants

| Event | System response |
|-------|----------------|
| BAE crashes while analysing a JAR | Phoenix submits `CrashReport` → VR immediately issues `DANGEROUS` |
| BAE is compromised and submits a false `SAFE` report | Quorum requires K of N; a single compromised engine cannot reach quorum alone |
| BAE is compromised and submits `DANGEROUS` | The false negative is safe — the codebase is condemned, which is conservative |
| VR receives a report signed by an unknown engine key | Report silently discarded |
| Operator revokes a BAE | All `RegistryVerdict` objects that relied solely on that engine are re-evaluated |
| Network partition between CD and BAE | CD cannot submit new analyses; existing verdicts remain valid; clients block on new codebases |
| JFR Telemetry Service fails | Re-analysis triggers stop; existing verdicts remain; runtime cross-check unavailable (degraded mode) |

---

## Verdict Flow Diagram

```
New JAR detected by CD
     │
     ▼
CD fetches bytes, computes SHA-256 hash H
     │
     ▼
AnalysisRequest(Pack200-compressed bytes, H, ...)
     │ pushed to each BAE
     ▼
BAE #1 analyzeJar()     BAE #2 analyzeJar()     BAE #N analyzeJar()
     │                        │                        │
  JarAnalysisReport        JarAnalysisReport        JarAnalysisReport
  (BAE-signed)             (BAE-signed)             (BAE-signed)
     │                        │                        │
     └──────────────▶ VR.submitReport() ◀──────────────┘
                          │
                    verifySignature()
                    deriveVerdictType()
                    applyQuorumPolicy()
                          │
                    RegistryVerdict (VR-signed)
                          │
                ┌─────────┴──────────┐
                │                    │
          EventPush            getVerdictByHash(H)
          to listeners         (client poll)
```

---

## Interface Contract Summary

### `BytecodeAnalysisEngine` (Hosts 2..N)

```java
JarAnalysisReport analyzeJar(AnalysisRequest request)
        throws AnalysisException, RemoteException;

void requestAnalysis(Set<Uri> codebaseUrls)
        throws RemoteException;
```

### `VerdictRegistry` (Host 1)

```java
// BAE lifecycle
void registerAnalysisEngine(String engineId, PublicKey engineKey, String sigAlgorithm);
void revokeAnalysisEngine(String engineId);

// Verdict submission (preferred path)
void submitReport(String engineId, JarAnalysisReport report);
// Verdict submission (legacy path)
void submitVerdict(String engineId, SignedVerdict verdict);
// Crash signal
void reportCrash(CrashReport report);

// Client access
RegistryVerdict getVerdictByHash(String contentHash);   // hash-keyed (push model)
RegistryVerdict getVerdict(Set<Uri> codebaseUrls);       // URL-keyed (legacy)

// Event subscription
EventRegistration registerVerdictListener(RemoteEventListener, Set<Uri>, MarshalledInstance, long);
long renewEventLease(Uuid leaseId, long duration);
void cancelEventLease(Uuid leaseId);
```

---

## Relationship to JGDMS-STD-001

Every `@AtomicSerial` class serialized and transmitted within SCAP (including
`AnalysisRequest`, `JarAnalysisReport`, `SignedVerdict`, `RegistryVerdict`,
`CrashReport`) **must** comply with JGDMS-STD-001.  The BAE validates all inbound
serialized classes from external JARs against that standard.

---

*Document maintained alongside `BytecodeAnalysisEngine.java`, `VerdictRegistry.java`,
`JarAnalysisReport.java`, `AnalysisRequest.java`, and related API classes in the
`au.net.zeus.jgdms.api.codebase` package.*
