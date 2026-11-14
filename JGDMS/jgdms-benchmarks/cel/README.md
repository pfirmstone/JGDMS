# CEL evaluator speed benchmarks (STD-011, stage 1: measurement only)

What one scan record costs the production DETERMINISTIC CEL evaluator (`jgdms-cel` `Evaluator`, trees from
`jgdms-cel-authoring` `CelTextParser`), against native Java doing the same arithmetic. Stage 2 (optimisation) is designed
from these numbers; nothing here changes `jgdms-cel` or `jgdms-cel-authoring`.

## Where things are (package `au.net.zeus.jgdms.bench.cel`)

| Class | Role |
|---|---|
| `RawScanColumns` | input fixture: de-pages a `.rawscan`, CRC-checks every page, keeps range/azimuth/elevation as float arrays |
| `RecordProjection` | flyweight `CandidateProjection` over the current record |
| `Stage1Expressions` | E1 (vector) and E2 (camera projection): text templates, parse, substitution, size/cost/verdict |
| `NativeBaselines`, `Trig` | B-CR and B-STRICT baselines of the same arithmetic |
| `CelRunner` | one `Evaluator` + projection; the unit both the benchmarks and the test drive |
| `CompiledRunner` | the compiled twin of `CelRunner`: one production `CompiledExpression` Session + projection (public API only) |
| `CompiledSelfCheck` | runs once per fork before the compiled benchmarks: walker vs compiled bit identity, and correctly-rounded calls per record (`SELFCHECK`, `CALLCOUNT` lines) |
| `Sampling` | which records are run (one rule for the test and the benchmarks) |
| `CelEvalBenchmark` | the JMH benchmarks; the operation is ONE RECORD |
| `Stage1CorrectnessTest` | sealed predictions P1-P10 and the bit-identity checks (read its javadoc first) |

## Build and test (from `JGDMS/`, both repository flags, never the shared `~/.m2`)

The two flags keep installs out of the shared repository other worktrees use:

    mvn -Dmaven.repo.local=J:/std011-speed-2026-11-11/m2 -Dmaven.repo.local.tail=C:/Users/peter/.m2/repository \
        -pl jgdms-cel,jgdms-cel-authoring -am install -DskipTests
    mvn -Dmaven.repo.local=J:/std011-speed-2026-11-11/m2 -Dmaven.repo.local.tail=C:/Users/peter/.m2/repository \
        -f jgdms-benchmarks/pom.xml -pl cel package

Run Maven from `JGDMS/` and name the benchmarks pom with `-f`: from inside `jgdms-benchmarks` Maven looks for
`.mvn/settings.xml` there and stops. `package` runs the correctness test (about a minute, reads the scan) and shades
`cel/target/benchmarks.jar`. The scan defaults to the stage-1 SX12 file on J: and is overridable with
`-Dcel.bench.rawscan=<path>`; it is read-only input.

## Running the timing (needs BOTH build slots; a smoke run is not a measurement)

See the stage-1 READY file for the exact command. `-prof gc` reports bytes per record. `celE2` (the written-out camera
projection under correct rounding) is orders of magnitude slower per record than the rest; budget for it.

Which records are timed: every cursor steps by 1031 (odd, coprime with the 4,096-record sample); `startOffset` is a JMH
parameter (0, 1365, 2731), so `-f 1` gives one fork per offset. `celE2` (single shot, one record per iteration) and
`nativeE2CrMatched` cycle the same 4-record ring, so `celE2 / nativeE2CrMatched` is over one population. Do not pass
`-wi`/`-i` for `celE2` (the annotation's 8 measured iterations time each ring record twice). The records actually timed are
printed as `TIMED-RECORDS ...` lines on stdout: keep the console log beside the JSON.
