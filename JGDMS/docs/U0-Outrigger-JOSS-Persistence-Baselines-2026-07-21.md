# U0 — Outrigger snaplogstore JOSS persistence baselines (captured 2026-07-21)

- **Task:** U0 of `SOW-Outrigger-DER-Only-JOSS-Rejection.md` (§8 execution plan; §4
  "Performance acceptance criteria" item (i): *capture JOSS baselines — log size, snapshot
  size, recovery time on a populated store — BEFORE U1a lands*). Unit 2's parity gates
  (§4 items ii and iv) compare against the numbers in this document.
- **Trunk state measured:** local trunk `290a3243f` ("ProxyTrust Stage 2: remove
  getProxyTrustIterator() from reggie-dl proxies"), 2026-07-21 — i.e. **current pre-U1a
  trunk**, `useDerForEntries` default `FALSE` (JOSS entry marshalling), JOSS
  `ObjectOutputStream` snaplogstore container.
- **Status of every number below: MEASURED** (single timed run per scenario after one
  discarded warm-up scenario; no numbers in this document are estimated or interpolated).
  The §4 gates that are *not* U0's to produce (fsync-count instrumentation for gate iii,
  torn-record equivalence for gate v, U1c's wire-size probe) were **not measured** and are
  so noted in §6.

---

## 1. Environment

| Item | Value |
|---|---|
| Machine | Intel Xeon E5-1650 v3 @ 3.50 GHz (6C/12T), 64 GB DDR4-2133 |
| OS | Windows 10 Pro 10.0.19045 |
| Store disk | Samsung SSD 850 EVO 1 TB (SATA SSD behind RAID bus), C: — store scratch dirs under `%TEMP%` |
| JVM (run) | Azul Zulu OpenJDK 25.0.2+10-LTS (same JVM for JOSS and ATOMIC_DER scenarios) |
| Build | Maven 3.9.15, `-pl services/outrigger/outrigger-snaplogstore -am -DskipTests install`, JAVA_HOME = Zulu 25 (jgdms-der is `--release 25`) |
| Runtime flags | `-Djava.rmi.server.RMIClassLoaderSpi=default` (no PreferredClassProvider on a plain classpath run); default heap; no other tuning |

## 2. Harness

**Source:** `services/outrigger/outrigger-snaplogstore/src/test/java/org/apache/river/outrigger/snaplogstore/JossPersistenceBaselineHarness.java`
— a standalone `main()` benchmark harness in the `BornFormatGuardTest` shape (test tree
only, clearly marked **NOT A UNIT TEST / DO NOT COMMIT**; it is an artifact of this U0
capture, archived with this document's SOW). It drives the **real**
`LogStore`/`BackEnd`/`LogOutputFile(Impl)` persistence path with a minimal in-memory
`Configuration` (persistence dir + `maxOps`); no service boot, nothing exported.

Per scenario (format × entry-kind × population), against a fresh directory:

1. **Boot 1 — write path.** N `EntryRep`s are pre-marshalled *before* the clock starts
   (field marshalling is client-side in production; `EntryRep(entry, format)` +
   `pickID()` + `setExpiration(MAX_VALUE)`), then timed: N × `LogOps.writeOp(rep, null)`
   followed by `store.close()` (which drains the log executor — every non-transactional
   op is individually fsync'd by `LogOutputFileImpl.flush`'s deferred-zero-header
   protocol, so close-drain = durability). `maxOps` is configured above N so the log
   never rolls and no mid-run snapshot happens. Log size read off disk after close.
2. **Boot 2 — recovery from log.** `setupStore` consumes every log record into the
   `BackEnd` maps, writes the store's **first snapshot**, then dispatches
   `recoverWrite` per entry; the harness `Recover` performs the same
   `new EntryRep()`/`restore()` reconstruction `OutriggerServerImpl.recoverWrite` does
   (and asserts the recovered count). Snapshot size read off disk after close.
3. **Boot 3 — recovery from snapshot.** `setupStore` again: snapshot read + N ×
   restore dispatch. *Composition note:* on this path `BackEnd.setupStore` also consumes
   boot 2's residual (empty) log file, and `consumeLogs` re-writes **one full snapshot**
   per consumed log — so boot 3 wall-clock = snapshot read + dispatch + one snapshot
   write. Identical composition on both formats, so before/after comparisons are
   like-for-like. The cumulative in-dispatch `restore()` time is reported separately.

**Entry fixtures** (mirroring `EntryRepDerFormatTest`'s):

- `simple` — `OrderEntry extends AbstractEntry` { `String customer`, `String product`,
  `Integer quantity`, `Integer priority` }, values cycled across the population.
- `custom` — `StampedOrderEntry` { `String customer`, `Integer quantity`, `Stamp stamp` }
  where `Stamp` is a custom `@AtomicSerial` value class (proper
  `serialForm()`/`serialize()`/`GetArg` contract, also `Serializable` so the identical
  fixture marshals under both formats) — the schema-carrying case §5's C5 note is about.

**What varies between the JOSS and ATOMIC_DER scenarios is exactly what the U1a flip
changes:** the per-field `MarshalledInstance` payload encoding inside `EntryRep`. The
container (log records, snapshot, `BaseObject` blobs) is the JOSS `ObjectOutputStream`
container in **all** scenarios on this trunk — the container rewrite is Unit 2.

**Reproduce:**
```
# classpath = outrigger-snaplogstore target/classes + its test-scope deps
#            + jgdms-der jar and its runtime deps (DER MarshalFactoryProvider SPI)
mvn -pl services/outrigger/outrigger-snaplogstore dependency:build-classpath -DincludeScope=test
mvn -pl jgdms-der dependency:build-classpath -DincludeScope=runtime
javac -cp <that> -d <out> .../JossPersistenceBaselineHarness.java
java  -cp <that>;<out> -Djava.rmi.server.RMIClassLoaderSpi=default \
      org.apache.river.outrigger.snaplogstore.JossPersistenceBaselineHarness <scratch-dir>
```

## 3. JOSS baselines (the §4 gate numbers)

### 3.1 Sizes on disk

| Scenario | N | Log file (bytes) | Log B/entry | Snapshot (bytes) | Snapshot B/entry |
|---|---:|---:|---:|---:|---:|
| JOSS / simple | 1,000 | 1,087,973 | 1,088.0 | 917,539 | 917.5 |
| JOSS / simple | 10,000 | 10,879,613 | 1,088.0 | 9,168,650 | 916.9 |
| JOSS / custom | 1,000 | 1,103,613 | 1,103.6 | 932,149 | 932.1 |
| JOSS / custom | 10,000 | 11,036,013 | 1,103.6 | 9,314,750 | 931.5 |

Per-entry cost is flat in N (no cross-record sharing — confirms §4's "no back-reference
compression, `out.reset()` per record" baseline note). Snapshot ≈ 0.84× log for the same
population (log carries per-record framing + op byte + txn slot).

### 3.2 Write path (ops/sec)

| Scenario | N | Write+close wall (ms) | Ops/sec |
|---|---:|---:|---:|
| JOSS / simple | 1,000 | 2,415 | 414.1 |
| JOSS / simple | 10,000 | 23,315 | 428.9 |
| JOSS / custom | 1,000 | 2,349 | 425.7 |
| JOSS / custom | 10,000 | 25,274 | 395.7 |

~400 ops/sec ≈ **2.4 ms/op**, flat across entry kind, population, *and* (see §4) payload
format — i.e. the write path is **fsync-dominated** (two syncs per committed
non-transactional op under the sentinel protocol), exactly seat 3's premise for §4 gate
(iii): halving the fsync count is the claimable Unit-2 improvement, serialization cost is
noise at this record size. Client-side `EntryRep` marshalling (excluded from the
timing, reported for context): ~0.03–0.26 ms/entry warm.

### 3.3 Recovery wall-clock

| Scenario | N | From log (boot 2, ms) | of which restore dispatch | From snapshot (boot 3, ms) | of which restore dispatch |
|---|---:|---:|---:|---:|---:|
| JOSS / simple | 1,000 | 148 | 51 | 120 | 31 |
| JOSS / simple | 10,000 | 832 | 196 | 968 | 161 |
| JOSS / custom | 1,000 | 105 | 18 | 104 | 17 |
| JOSS / custom | 10,000 | 763 | 192 | 1,110 | 199 |

Boot-2 includes writing the first
snapshot; boot-3 includes re-writing one snapshot (composition per §2 item 3). Either
way: **~0.8–1.2 s to recover a 10k-entry store, ~0.1–0.15 s for 1k** — this is the §4
gate (iv) parity target. Restore dispatch (deserializing each `Resource` blob back into
an `EntryRep`) is ~20% of it; container stream parse + snapshot write is the rest.

## 4. ATOMIC_DER comparison (same harness, same run, `useDerForEntries=TRUE` equivalent)

Captured by constructing the same populations with
`EntryRep(entry, MarshallingFormat.ATOMIC_DER)` — the entry-marshalling half of the U1a
flip — through the identical (still-JOSS) container.

| Scenario | N | Log B/entry | Δ vs JOSS | Snapshot B/entry | Δ vs JOSS | Ops/sec | Recovery from log (ms) | From snapshot (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| DER / simple | 1,000 | 943.3 | **−13.3%** | 771.6 | **−15.9%** | 405.2 | 68 | 111 |
| DER / simple | 10,000 | 943.2 | **−13.3%** | 770.9 | **−15.9%** | 390.1 | 856 | 1,009 |
| DER / custom | 1,000 | 1,064.0 | **−3.6%** | 894.2 | **−4.1%** | 403.7 | 105 | 101 |
| DER / custom | 10,000 | 1,064.0 | **−3.6%** | 893.5 | **−4.1%** | 402.5 | 755 | 1,244 |

**Findings, stated against the SOW's predictions:**

- **Typical (scalar/String-field) entries shrink on the flip** — log −13%, snapshot −16%.
  This *measures* §5's seat-3 note ("scalar/String/enum/byte[] field values take the
  empty-schema path... typical entries carry no duplication and likely shrink") as
  correct: DER's canonical encoding beats JOSS's per-field
  stream-header/class-descriptor overhead outright, with no schema bytes to pay.
- **`@AtomicSerial`-valued fields still shrink, but only ~4%** — each `Stamp` field value
  carries its full `schemaBytes` chain per occurrence (never interned on disk), eating
  most of the JOSS-overhead win. This is precisely C5's target (§5: disk half rides
  Unit 2's intern-by-digest; heap half may be pulled forward) and quantifies what C5 has
  to reclaim: ~120 B/entry of the gap between the custom (1,064) and simple (943)
  per-entry log cost is schema carriage for one small two-field value class.
- **Write throughput is format-invariant** (~400 ops/sec both formats) — fsync-bound, so
  the U1a flip is performance-neutral on the write path; only Unit 2's one-fsync
  protocol (§4 gate iii) can move this number.
- **Recovery is parity-to-slightly-better under DER at equal population** (smaller blobs
  to parse; differences at these times are within run-to-run noise). No recovery
  regression signal from the entry-format half of the flip.

## 5. Raw run output

Archived alongside the harness; per-scenario `files after boot3` listings confirm the
single-log/no-rollover discipline (residual `LogStore.n` files of 12 bytes are the empty
version-header+sentinel logs of boots 2/3; on some scenarios Windows defers deletion of
the consumed boot-1 log, which does not affect any measured number — log size is read
after boot 1's close, before boot 2 opens anything).

Full stdout of the measured run is reproduced here for the record:

```
JVM: Azul Systems, Inc. 25.0.2+10-LTS / OS: Windows 10 10.0

JOSS / simple / n=1000:   write+close 2415 ms (414.1 ops/s); log 1,087,973 B;
                          rec-from-log 148 ms; snapshot 917,539 B; rec-from-snap 120 ms
JOSS / simple / n=10000:  write+close 23315 ms (428.9 ops/s); log 10,879,613 B;
                          rec-from-log 832 ms; snapshot 9,168,650 B; rec-from-snap 968 ms
JOSS / custom / n=1000:   write+close 2349 ms (425.7 ops/s); log 1,103,613 B;
                          rec-from-log 105 ms; snapshot 932,149 B; rec-from-snap 104 ms
JOSS / custom / n=10000:  write+close 25274 ms (395.7 ops/s); log 11,036,013 B;
                          rec-from-log 763 ms; snapshot 9,314,750 B; rec-from-snap 1110 ms
DER  / simple / n=1000:   write+close 2468 ms (405.2 ops/s); log 943,253 B;
                          rec-from-log 68 ms;  snapshot 771,579 B; rec-from-snap 111 ms
DER  / simple / n=10000:  write+close 25634 ms (390.1 ops/s); log 9,432,413 B;
                          rec-from-log 856 ms; snapshot 7,708,690 B; rec-from-snap 1009 ms
DER  / custom / n=1000:   write+close 2477 ms (403.7 ops/s); log 1,064,013 B;
                          rec-from-log 105 ms; snapshot 894,189 B; rec-from-snap 101 ms
DER  / custom / n=10000:  write+close 24842 ms (402.5 ops/s); log 10,640,013 B;
                          rec-from-log 755 ms; snapshot 8,934,790 B; rec-from-snap 1244 ms
```

## 6. Not measured here (and why)

- **Fsync count per op (gate iii):** not instrumented — U0's charter is size/time
  baselines; the two-fsync sentinel cost is *inferred* from the flat ~2.4 ms/op floor,
  the count itself is verified by code inspection (`LogOutputFileImpl.flush` `:439,:488`).
- **Partial-tail / torn-record behavior (gate v):** an equivalence property for Unit 2's
  tests, not a baseline number.
- **Wire sizes (DER vs JOSS+DEFLATE, MatchSet batches):** U1c's probe (§3 item 8 / §7),
  not U0.
- **Transactional-write deferred-sync throughput:** all baseline writes were
  non-transactional (`txnId == null`, the fsync-per-op path — the conservative bound).

*Baseline captured before U1a per §8 sequencing; archive with the SOW. No production
source was modified: the only artifacts are this document and the benchmark harness in
the snaplogstore test tree (uncommitted).*
