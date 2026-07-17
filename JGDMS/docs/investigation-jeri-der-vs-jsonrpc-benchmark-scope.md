# Scoping — a fair JERI + Atomic DER vs. JSON-RPC performance benchmark

**Status:** scoping only. **No benchmark has been built or run for this document.**
There are no numbers here. This lays out what would have to be built, decided, and
measured before anyone could responsibly say "JERI/Atomic DER is faster/slower than
JSON-RPC," and roughly how much work that is.

**Why this document exists.** A claim of the form "JERI/Atomic DER outperforms
JSON-RPC" surfaced in an external discussion. The repo has no measured comparison to
back it — the closest artifacts are `blog-post-5-performance.md` (unbenchmarked prose:
"outperforms standard Java serialization," "demonstrably faster" — no methodology, no
numbers; treat as unverified draft marketing copy, not fact) and
`investigation-jeri-transport-compression.md` (a real, reproducible DEFLATE-throughput
benchmark on synthetic DER bytes — a good methodology model, but it measures
compression, not JERI-vs-JSON-RPC). This document follows the same discipline as those
two and as `JGDMS-STD-006-comparison.md`: state what's real, flag what isn't measured,
and disclose asymmetries rather than picking a favorable framing.

---

## 0. What was read to ground this scope

- `jgdms-jeri/src/main/java/net/jini/jeri/BasicInvocationHandler.java` and
  `BasicInvocationDispatcher.java` — the client/server invoke path shared by every
  JERI codec (`invoke` → `marshalMethod`/`marshalArguments` → wire → dispatch →
  `unmarshalReturn`/`unmarshalThrow`).
- `jgdms-jeri/src/main/java/net/jini/jeri/AtomicDerInvocationHandler.java` and
  `AtomicDerInvocationDispatcher.java` — the DER-specific subclasses. They override
  only `createMarshalOutputStream`/`createMarshalInputStream` to swap in
  `au.net.zeus.jgdms.der.stream.DerMarshalOutputStream`/`DerMarshalInputStream`
  (which implement `ObjectOutput`/`ObjectInput` directly, not the
  `ObjectOutputStream`/`ObjectInputStream` subclasses JOSS uses) and to route
  through `AtomicDerILFactory` + `MarshallingFormat.ATOMIC_DER`. Everything else —
  method dispatch, constraint checking, the ACC/Subject block written to the raw
  (pre-codec) stream — is inherited unchanged from `BasicInvocationHandler`/
  `BasicInvocationDispatcher`.
- `jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/ObjectCodec.java` (encode/
  decode entry points, `decodeHierarchy`, `decodeCollection`/`decodeMap` with the
  canonicalise/preserve ordering discipline) and
  `.../der/getarg/DerFieldStore.java`, `.../der/object/DerGetArg.java` — the
  `GetArg`-based read path every `@AtomicSerial` deserializing constructor goes
  through, which is where STD-001 RULE-3's `check(GetArg)` invariant validation and
  per-field typed reads happen before an object exists.
- `jgdms-jeri/src/main/java/net/jini/jeri/ssl/` — confirms JERI has **two** real
  transports today: `SslEndpoint`/`SslServerEndpoint` (raw TCP wrapped in an
  `SSLEngine`, TLS 1.3, no HTTP framing) and `HttpsEndpoint`/`HttpsServerEndpoint`
  (JERI framed over HTTPS, sharing most of the SSL machinery via
  `SslEndpointImpl`/`SslConnection`). Both matter for §2 below.
- `services/bytecode-analysis-engine/` (`BytecodeAnalysisEngineImpl.java`,
  `JarAnalyzer.java`) — confirms BAE reports asynchronously to a
  content-addressed `VerdictRegistry` (keyed by JAR SHA-256, per its own
  Javadoc: "never calls `VerdictRegistry` methods; it merely ... `submitReport`").
  This is a **JAR-digest-scoped, cache-after-first-verdict** admission check, not
  something invoked per RPC call. Confirmed by reading the code, not assumed from
  the blog post.
- Repo-wide search for JMH: `grep -r "jmh\|@Benchmark\|openjdk.jmh"` across every
  `pom.xml` and `.java` file returns **nothing**. **There is no JMH infrastructure
  anywhere in this repository today.** Any benchmark module starts from zero:
  no harness, no JMH Maven plugin wiring, no existing `@Benchmark` code to imitate
  in-repo (the closest precedent is the ad hoc timing harness in
  `investigation-jeri-transport-compression.md` Appendix A, which is not JMH).

---

## 1. What's actually being compared

JSON-RPC 2.0 is a message-format spec, not an implementation. Comparing "JERI" (a
concrete, in-repo invocation stack) against "JSON-RPC" (nothing concrete) is a
category error unless a specific baseline is nailed down and the choice defended.

**Decision: run two JSON-RPC baselines, not one, and label them differently.**

1. **Representative baseline — a mainstream, unmodified Java JSON-RPC library**
   (e.g. `jsonrpc4j`, or a JSON-RPC 2.0 layer on top of `com.fasterxml.jackson` used
   the way most Java services actually use it — `ObjectMapper` with default
   settings, no custom codec tuning). This is "what a team reaching for JSON-RPC in
   2026 would actually ship." It is the fairest *representative* comparator but it
   confounds two things: JSON-RPC's serialization cost and that particular
   library's/transport's implementation quality.
2. **Transport-matched baseline — a hand-rolled Jackson-based JSON-RPC 2.0
   encoder/decoder run over the *same* transport code JERI uses** (see §2). This
   isolates "cost of the JSON-RPC wire format + JSON codec" from "cost of whatever
   HTTP client/server library a JSON-RPC framework happens to bundle." Without this
   baseline, any delta is impossible to attribute — it could be JERI's DER codec, or
   it could just be that library A's HTTP stack is slower than JERI's `SslEndpoint`
   mux, which says nothing about serialization.

**Reasoning for running both, not picking one:** a benchmark that only used baseline
(1) and found JERI faster would invite "you just picked a slow JSON-RPC library" —
a real, fair objection given the debate's standing discipline against strawmanning.
A benchmark that only used baseline (2) would invite "no one deploys JSON-RPC that
way" — also fair. Reporting both, with the confound named, is the non-overclaiming
choice. If the two baselines disagree substantially, that disagreement is itself the
most interesting and citable finding (it would mean the gap is mostly transport/
library-quality, not format).

**Do not compare against JOSS/RMI here.** `blog-post-5-performance.md`'s "faster
than RMI" and "@AtomicSerial outperforms standard Java serialization" claims are a
*different* comparison (DER vs. Java Object Serialization) and are themselves
unmeasured — out of scope for this document, and should not be conflated with a
JSON-RPC comparison in any write-up.

---

## 2. Transport parity

JERI's invocation-layer codec swap (`AtomicInvocationHandler` → JOSS,
`AtomicDerInvocationHandler` → DER) is transport-agnostic by construction — both run
identically over `SslEndpoint` (raw TLS socket) or `HttpsEndpoint` (TLS + HTTP
framing). JSON-RPC in the wild is almost always HTTP(S)-based. Comparing
JERI-over-raw-TLS against JSON-RPC-over-HTTP would let transport framing (HTTP
headers, chunked encoding, connection: keep-alive negotiation, etc.) leak into a
number presented as "serialization cost."

**Decision: run matched-transport pairs, and report which pairing produced which
number.**

- **Pair A — raw TLS socket, no HTTP framing.** JERI over `SslEndpoint` vs. the
  transport-matched JSON-RPC baseline (§1.2) run over a plain `SSLSocket`/
  `SSLEngine` with no HTTP layer on either side. This isolates codec + dispatch
  cost as tightly as the two systems allow.
- **Pair B — HTTP(S), as normally deployed.** JERI over `HttpsEndpoint` vs. the
  representative JSON-RPC baseline (§1.1) over its normal HTTP(S) client/server
  (e.g. `java.net.http.HttpClient` or the library's bundled stack). This is the
  "as most people would actually run it" number and will include HTTP framing
  overhead on both sides — legitimately, since that's how JSON-RPC is usually
  deployed.

**Never mix pairs** (e.g. JERI/`SslEndpoint` vs. JSON-RPC/HTTP) and present it as a
single "JERI vs JSON-RPC" number — that conflates transport choice with format
choice and is exactly the kind of confound the debate's fact-checking discipline
would (rightly) flag.

---

## 3. Payload shapes

One synthetic blob does not represent JGDMS traffic. Reuse the shapes already
established as representative in `investigation-jeri-transport-compression.md`
(same precedent, so results are comparable across the two investigations) and add
one shape that isolates fixed per-call overhead:

1. **Simple primitive-argument RPC** — e.g. `int add(int, int)` or
   `String echo(String)`. No object graph, minimal payload. This isolates the
   *fixed* per-call cost (method dispatch, stream setup, ACC/constraint handling on
   the JERI side; JSON parse of a trivial object on the JSON-RPC side) from
   payload-size effects. Without this shape, a benchmark risks conflating "cost of
   one call" with "cost of encoding N bytes."
2. **A `ServiceItem`** — a real, moderately-nested JGDMS object graph (id, service
   proxy, an entry array), the same class of payload used as the "Exported proxy" /
   "ServiceItem (3 entries)" rows in the compression investigation. Representative
   of a typical registration/lookup-result call.
3. **Lookup batch (50 `ServiceItem`s)** — explicitly reusing the compression
   investigation's "Lookup batch (50 ServiceItems)" shape (its raw-DER measurement:
   83 305 bytes). This is the shape where DER's self-describing schema chain repeats
   most, so it is the shape most likely to show the largest *wire-size* delta
   against JSON-RPC's design; a fair benchmark must include it rather than only
   testing shapes flattering to one side.

Each shape should be run through both matched-transport pairs (§2) and both JSON-RPC
baselines (§1), i.e. the full cross product, not a cherry-picked subset.

---

## 4. Metrics

Report all of these, not just one headline number:

- **Throughput** — ops/sec at saturation (JMH `Throughput` mode), per payload shape,
  per transport pair, per JSON-RPC baseline.
- **Latency percentiles** — p50/p99 (and p999 if noise permits) via JMH
  `SampleTime` mode, not just a mean — remote-call latency is not normally
  distributed and a mean alone hides tail behavior that matters for a service
  platform.
- **Wire bytes per payload** — reuse the byte-counting method from the compression
  investigation (raw vs. any wire framing) for both DER and the JSON-RPC baselines,
  per shape. This is orthogonal to CPU/latency and matters independently — a format
  can win on latency and lose on bytes or vice versa; report both, don't fold them
  into one score.
- **Allocation / GC pressure** — JMH's built-in GC profiler (`-prof gc`): bytes
  allocated per operation and GC pause attribution. `DerMarshalOutputStream`/
  `DerMarshalInputStream` implement `ObjectOutput`/`ObjectInput` directly rather
  than extending `ObjectOutputStream`/`ObjectInputStream`; whether that produces a
  measurably different allocation profile than a typical Jackson-based codec is a
  real, currently unknown question this metric would answer — not an assumption to
  bake into the write-up beforehand.
- **One-time / cold-start cost, reported separately from steady-state** — see §5.
  Never blend this into the per-call throughput/latency numbers above.

---

## 5. The honesty requirement — the asymmetry that must be disclosed either way

JERI + Atomic DER, used as JGDMS actually uses it, pays real per-call security costs
that a typical JSON-RPC service (Jackson `ObjectMapper` deserializing into a POJO,
no invariant layer) does not pay by default. Concretely, grounded in the code read
for this scope:

- **Per-field constructor validation.** Every `@AtomicSerial` deserializing
  constructor reads its fields through `GetArg`/`DerGetArg` and — per STD-001
  RULE-3 — a static `check(GetArg)` runs *before* the object is constructed,
  enforcing invariants (e.g. rejecting an out-of-range field) at decode time.
  Jackson's default POJO deserialization does not run an equivalent invariant gate
  unless the application code adds one.
- **Deterministic canonical encoding.** The DER codec (`ObjectCodec`) does
  tag/length framing plus, for unordered collections, an X.690 §11.6 octet-sort
  canonicalisation pass so `.equals` objects produce byte-identical output
  (`JGDMS-STD-006-comparison.md`'s VEq property). A typical JSON encoder does no
  equivalent canonicalisation work.
- **BAE static verification — one-time, cached, NOT per-call.** Confirmed from
  `BytecodeAnalysisEngineImpl`/`JarAnalyzer`: BAE analyzes a JAR once per
  content-hash (SHA-256) and reports asynchronously to a content-addressed
  `VerdictRegistry`; it is not invoked on every RPC. JSON-RPC has **no equivalent
  admission-control step at all** — there is nothing on the JSON-RPC side to even
  compare it against. **A benchmark that puts BAE analysis time inside a per-call
  latency number is comparing apples to oranges and must not be built that way.**
  The correct treatment: measure/report the one-time admission cost (BAE quorum
  time on first sight of a digest + first classload) as its own number, separate
  from and never summed into the steady-state per-call figures, with a clear note
  that it amortizes to ~0 over the lifetime of a long-running codebase and has no
  JSON-RPC counterpart to compare against (not "JSON-RPC is faster because it skips
  this" — it skips it because it does not offer it, which is a different claim).
- **Digest-based class identity checks.** JERI's codebase-download and
  `DigestCodeSource` machinery verify content hashes on the class-loading path.
  `blog-post-5-performance.md` claims this "short-circuits on a cache hit" and is
  "faster than the standard alternative" — **that specific claim is unverified**
  (no benchmark backs it in this repo) and must not be asserted as fact in any
  write-up derived from this scope; if it's relevant to the benchmark, it needs its
  own measurement, not a citation to the blog post.

**How the eventual write-up must present this, regardless of which way the number
points:**

- If DER-over-JERI turns out slower per call than JSON-RPC: say so, and explain
  *why* in terms of the specific costs above (constructor validation, canonical
  encoding) rather than treating it as a defect to explain away.
- If DER-over-JERI turns out faster per call: say so, **and** explicitly list what
  the JSON-RPC baseline is *not* doing that DER is doing (no per-field invariant
  check, no canonical-encoding pass, no digest-based class identity check) so the
  number isn't read as "DER is just faster" when part of the honest story is "DER
  does more work and is still competitive" or "DER does more work and that shows up
  in the number." A bare "X beats Y" headline without this disclosure would be
  misleading in whichever direction it points — matching the register
  `JGDMS-STD-006-comparison.md` already uses for DER's wire-size and zero-copy
  trade-offs (it says plainly that DER is "not optimised for the smallest wire" and
  "more verbose ... for the same payload" rather than picking a flattering framing).
- Any claim using the words "outperforms" or "faster" in a future public post must
  cite this benchmark's actual measured numbers, methodology, and the caveats above
  — not `blog-post-5-performance.md`'s prose, which remains unbenchmarked marketing
  copy until (if ever) this work is done.

---

## 6. JMH methodology

Baseline JMH hygiene, all currently absent from the repo (§0) and needing to be
built from scratch:

- **Warmup vs. measurement separation** — enough warmup iterations (typically 5+,
  each running long enough, e.g. ≥1s) for both the JERI/DER path and the JSON
  codec/library path to reach steady JIT compilation state before measurement
  iterations count. Two very different code paths compiled in the same JVM process
  need enough warmup each; under-warming one side, especially the less
  battle-tested DER path, would bias results.
- **Forking** — run each benchmark configuration in its own forked JVM (JMH
  `@Fork`, typically 3+ forks) so JIT profiling/inlining decisions from one codec
  path (e.g. megamorphic call sites if JERI and JSON-RPC dispatch code share a
  process) don't pollute the other's measurements. This matters more than usual
  here because the two systems under test literally run in the same benchmark
  harness process if not forked apart.
- **Avoiding dead-code elimination** — return values (marshalled bytes, unmarshalled
  return objects) must be consumed via `Blackhole.consume(...)`, not just computed
  and discarded, or the JIT may eliminate the very work being measured — a bigger
  risk for the DER path's validation/canonicalisation work than for a JSON parse,
  since validation-only side effects (the `check(GetArg)` call) could plausibly be
  seen as dead if its result is unused.
- **GC profiling and allocation attribution** — use `-prof gc` (or async-profiler if
  available) per §4, and be careful that `@State(Scope.Benchmark)` object reuse
  (e.g. reusing one `ServiceItem` instance across iterations) doesn't hide
  allocation that would occur on a real per-call basis (a fresh argument object per
  call, not one shared mutable instance).

**JGDMS-specific complications a generic JMH setup would miss:**

- **BAE's async/quorum architecture must be excluded from the per-call loop
  entirely** (§5) — it is not synchronous with RPC dispatch and has no natural place
  inside a JMH `@Benchmark` method measuring call latency. If cold-start numbers are
  wanted, they belong in a separate, explicitly-labeled one-shot benchmark (JMH
  `SingleShotTime` mode makes sense here, run outside the steady-state suite), never
  inside the warmed-up throughput/latency loop.
- **JERI connection reuse/pooling** (`net.jini.jeri.connection.Connection` /
  `ConnectionManager`) — the benchmark must warm up and hold a live connection
  across measurement iterations, not reconnect (and re-handshake TLS) per call, or
  the number becomes "connection setup cost" rather than "per-call codec/dispatch
  cost." TLS/connection-establishment cost is legitimate to measure, but as its own
  separate, labeled number (analogous to the BAE cold-start split above), using JMH
  `SingleShotTime`, not folded into steady-state throughput.
- **SecurityManager / ACC path.** Per `blog-post-5-performance.md`'s (unverified,
  but architecturally real per repo docs) description of `DomainIdentity` and
  immutable-ACC caching, a live SecurityManager+policy deployment adds ACC
  construction/combination cost to every dispatch on the JERI side, with no JSON-RPC
  equivalent. Running the benchmark **only** without a SecurityManager would
  undercount JERI's real deployed cost (exactly the kind of favorable-framing
  omission this document's discipline forbids); running it **only** with one makes
  the JSON-RPC comparison less representative of how JSON-RPC libraries are
  normally deployed (typically no SecurityManager at all). **Recommendation: run
  both configurations (SM+policy on vs. off) for the JERI/DER side and report which
  configuration produced which number**, rather than silently picking one.
- **First-call caches on the dispatch path** — `BasicInvocationHandler`/
  `BasicInvocationDispatcher` use a method-hash (`Util.getMethodHash`) written per
  call but whose computation may cache; confirm during implementation whether any
  such cache needs explicit warmup beyond the generic JMH warmup, rather than
  assuming generic warmup covers every JGDMS-specific cache.
- **DoS input limits** (`DerInputLimits` on `AtomicDerInvocationHandler`/
  `AtomicDerInvocationDispatcher`) impose bounds-checking on every decode. For
  fairness, the JSON-RPC baselines should have a comparable input-size cap
  configured (most JSON libraries support one) rather than running uncapped — an
  uncapped JSON parser is not doing equivalent defensive work.

---

## 7. Effort estimate

Rough, for scoping/decision purposes only — not a commitment or a schedule.

| Piece | Why it's needed | Rough size |
|---|---|---|
| New benchmark Maven module (e.g. alongside `jgdms-der`/`jgdms-jeri` per the existing `<module>` list in the root `pom.xml`) with JMH wired in (`jmh-generator-annprocess`, shade/uberjar packaging for `java -jar benchmarks.jar`) | **No JMH infra exists anywhere in the repo today** (§0) — this is greenfield, not "add a benchmark to the existing harness" | 0.5–1 day |
| Minimal client/server harness reusing real `AtomicDerInvocationHandler`/`Dispatcher` and `AtomicInvocationHandler`/`Dispatcher` (JOSS) over `SslEndpoint` and `HttpsEndpoint`, with connection warmup control | Needs a real exported/imported JERI proxy pair per transport, not a mock — the whole point is measuring the actual dispatch path read in §0 | 1–1.5 days |
| Representative JSON-RPC baseline (library integration) + transport-matched JSON-RPC baseline (hand-rolled Jackson codec over raw `SSLSocket`) | Two baselines per §1, each needs its own client/server harness | 1–1.5 days |
| Payload generators for the three shapes (§3), reusing/adapting the DER TLV-building approach from `investigation-jeri-transport-compression.md` Appendix A, plus equivalent POJOs for the JSON side | Shapes must be logically equivalent data on both sides for a fair comparison, not just "similar size" | 0.5 day |
| Wiring the SM+policy on/off split, BAE cold-start single-shot benchmark, and connection-establishment single-shot benchmark as separate labeled suites (§5, §6) | The parts of this scope most likely to be skipped under time pressure, and the parts most load-bearing for honesty | 1 day |
| Running, sanity-checking (repeat runs for noise, forked isolation, GC profiler review), and writing up results with the disclosures in §5 | Methodology-follow-through, not implementation | 1–1.5 days |
| **Total** | | **~5–7 focused engineer-days**, before any CI integration |

**CI / run story.** JMH results are noisy and machine-dependent; do **not** gate
normal CI on them. Recommend a manual or nightly/on-demand run (a Maven profile or a
separate `mvn -pl <bench-module> ... jmh:jmh`-style invocation, not part of the
default `mvn install` build), with results archived alongside the run's JVM/hardware
metadata (JMH already reports this) so any published number is reproducible and
dateable — the same "reproducible from the harness" standard
`investigation-jeri-transport-compression.md` already sets.

**Bottom line for the maintainer's decision:** this is a real week of focused work,
mostly in building two fair baselines and the SM/BAE/connection-warmup discipline
that keeps the eventual number honest — not in running JMH itself, which is the easy
part once the harnesses exist. Worth doing before any public performance claim;
not a small aside to bolt onto an unrelated task.

---

## 8. Phase 0 — a cheap way to get a rough indication first (internal DER hotspots, not a JSON-RPC comparison)

The §1–7 scope above is expensive mainly *because it's an external, citable
comparison*: two fair JSON-RPC baselines, matched transports, SM/BAE/connection
splits — none of that machinery is needed to answer a narrower, purely internal
question: **where does Atomic DER itself spend its time, and is there an easy win
before spending a week building a comparison against something else?** That
narrower question can be answered directionally in well under a day, using
infrastructure that already exists, with no JSON-RPC baseline at all.

**Why this is the right thing to do first.** Any hotspot Phase 0 finds and fixes is
real, useful work regardless of how JERI/DER ever compares to JSON-RPC — it's not
contingent on the external-comparison project happening at all. It also de-risks
§1–7: if the full comparison were built today and it turned out DER was dominated by
an easily-fixable inefficiency, the resulting "X vs Y" number would be measuring that
inefficiency, not DER's inherent cost, and would need redoing after the fix anyway.
Finding and fixing hotspots first means the eventual comparison (if still wanted)
measures a reasonable implementation.

**Two concrete, low-effort options — do the first one at minimum:**

1. **Isolated codec driver under JDK Flight Recorder (JFR).** JFR ships in the JDK
   already (`-XX:StartFlightRecording=...` or `jcmd <pid> JFR.start`) — **no new
   infrastructure, no new Maven module, unlike JMH** (§0 found zero JMH in the repo;
   JFR needs none). Write a small driver (not a JMH harness — just a loop) that
   calls `ObjectCodec.encode`/`decode` directly, several hundred-thousand times, over
   the same three payload shapes already scoped in §3 (primitive-arg call,
   `ServiceItem`, 50-item lookup batch — reusing the TLV-building approach from
   `investigation-jeri-transport-compression.md` Appendix A). Run it under JFR,
   inspect the method-profiling event stream (`jdk.ExecutionSample`) with `jfr print`
   or JDK Mission Control. This directly answers "where does DER spend CPU" —
   e.g. whether it's the octet-sort collection canonicalisation, `GetArg`/
   `DerGetArg` reflective field reads, the §7.8 schema-digest chain walk, or
   something else — without needing a JSON-RPC comparator to exist at all.
   **Rough size: half a day**, most of it writing the payload builders (which §1–7
   needs anyway, so it isn't wasted if the fuller benchmark happens later).
2. **Real traffic via the existing qa outrigger stress test, also under JFR.**
   `qa/src/org/apache/river/test/impl/outrigger/matching/StressTest.java` (config:
   `StressTest.td`) is a real, already-built, configurable load generator: N writer
   threads and M reader threads hammering a genuine `Outrigger`/Reggie-registered
   `JavaSpace` with `write`/`read`/`take`, over whatever `InvocationLayerFactory` the
   service is exported with. This is real marshal/dispatch traffic through the
   actual JERI stack, not a synthetic microbenchmark — a better precedent match to
   "how JGDMS really behaves" than a bare codec loop. Two things need to change to
   use it for this purpose, both confirmed by reading the code, not assumed:
   - **It's currently throttled, not a stress load.** `StressTest.td` runs only 7
     writers / 13 readers, and both `WriteRandomEntryTask` and
     `ReadAndTakeEntryTask` call `Thread.sleep(100L)` between operations
     (`StressTest.java` lines ~563, ~796). As shipped this keeps the JVM mostly
     idle between calls — a JFR profile of it would mostly show sleep, not codec
     hotspots. To use it for hotspot-finding, the sleep needs removing (or cutting
     drastically) and `num_entries`/`num_readers`/`num_writers` raised, so the
     service is actually CPU-saturated during the run.
   - **It doesn't exercise DER by default.** Outrigger's shipped `.config` files
     don't reference `AtomicDerILFactory` (confirmed: no match anywhere in the
     `services/outrigger` tree). `AtomicDerILFactory` is a documented drop-in
     replacement for the default `InvocationLayerFactory` (its own Javadoc: "no
     other service code change is required" — just swap the factory passed to
     `BasicJeriExporter`), so pointing the outrigger service's exporter
     configuration at `AtomicDerILFactory` instead of the default is a
     configuration change, not a code change — but it is a real change that has to
     be made and verified before a JFR run under this option says anything about
     DER specifically (as opposed to whatever the current default codec is).
   **Rough size: half a day** (config change + de-throttling + a JFR-attached run
   + reading the profile), assuming the qa harness environment is already working
   locally (per the existing `mvn-single-instance-across-agents` / `qa harness`
   operational notes elsewhere in this project).

**What Phase 0 does *not* give you:** a citable "DER is N% faster/slower than
JSON-RPC" number (there is still no JSON-RPC comparator involved), statistically
rigorous JMH-grade measurement (no warmup/fork discipline, no forked-JVM isolation),
or the SM/BAE/connection-warmup honesty splits from §5–6. It gives *directional*
signal — "here is where the CPU/allocations concentrate today" — sufficient to
decide (a) whether there's an obvious, cheap DER optimization worth doing before
anything else, and (b) whether the full §1–7 comparative benchmark is likely to be
worth its ~5–7 days, or whether that money is better spent elsewhere for now.

**Recommended sequencing:** run Phase 0 (≤1 day total for both options) → if it
surfaces a fixable hotspot, fix it and re-profile to confirm → only then decide
whether to commit the ~5–7 days in §1–7 for an external, citable JERI-vs-JSON-RPC
comparison. Phase 0 is not a substitute for §1–7 if a public performance claim is
still wanted eventually — it's a cheap gate in front of that larger, more expensive
decision.
