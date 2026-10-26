# Serialization-format benchmarks: JOSS vs DER

JMH benchmarks that compare JERI with `AtomicSerial` over the two wire
formats it supports:

| Format | Invocation layer       | Streams                                           |
|--------|------------------------|---------------------------------------------------|
| `JOSS` | `AtomicILFactory`      | `AtomicMarshalOutputStream` / `AtomicMarshalInputStream` (Java Object Serialization Stream, read atomically) |
| `DER`  | `AtomicDerILFactory`   | `DerMarshalOutputStream` / `DerMarshalInputStream` (JGDMS-STD-006 canonical DER) |

They exist so that the cost of moving from Java Object Serialization to DER
is a measured number with its conditions stated, not an assumption in either
direction.

## What is measured

Two tiers, so that the serialization protocol's share of a remote call can be
separated from the invocation layer and transport, which are the same for
both formats.

### Codec tier: `CodecBenchmark`

One payload through one fresh stream, in-memory, no JERI:

- **`marshal`**: write one object to a fresh output stream.
- **`unmarshal`**: read one object from a fresh input stream over pre-encoded
  bytes. This is the validating side: JOSS atomic reading checks the class
  descriptor and invokes the `GetArg` constructor; DER reading verifies the
  schema and the canonical encoding.
- **`roundTrip`**: both, back to back.

A fresh stream per operation is deliberate: JERI creates a new stream for
every request and every response, so every remote call pays the format's
fixed cost again (JOSS stream header and class descriptors; DER schema). The
numbers are therefore per-call costs, not the amortised cost of a long-lived
stream.

### JERI tier: `JeriBenchmark`

A complete remote call over TCP loopback (`TcpServerEndpoint` on 127.0.0.1)
against an `Echo` service exported with `BasicJeriExporter` and the format's
`ILFactory`. Each method echoes its argument, so the payload is marshalled
twice (argument, result) and unmarshalled twice (dispatcher, handler).

- **`ping`**: no argument, no result. The per-call floor: invocation layer,
  mux, loopback round trip. The JOSS and DER `ping` differ only in the
  invocation layer and the empty-stream framing.
- **`point`**, **`record`**, **`batch100`**, **`blob64k`**: one payload each.

The service is exported once per fork, so the client connection cache is
warm and what is timed is a call on a live proxy. DGC is off.

### Payloads

All `@AtomicSerial`, all encodable by both formats, all deterministic:

| Name       | Shape                                   | Stands in for                          |
|------------|-----------------------------------------|----------------------------------------|
| `point`    | two `int`s                              | the smallest object; fixed per-call cost |
| `record`   | `long` + three `String`s                | an entry or an event                   |
| `batch100` | `List` of 100 `record`s                 | a lookup result or bulk read; amortisation across one class |
| `blob64k`  | 64 KiB `byte[]`                         | the bulk-copy path                     |

`WireSize` (run by the scripts before JMH) prints the encoded size of each
payload in each format.

### Self-checks

Before measuring, each trial encodes and decodes its payload (codec tier) or
calls every `Echo` method (JERI tier) and fails if the result is not equal to
what was sent, so a format that silently produced garbage would not produce
numbers.

## Building

A JDK 25 or later and Maven. `jgdms-der` is compiled for release 25, so this
module is too. It depends on the installed `jgdms-jeri` 4.0.0-SNAPSHOT (and
transitively `jgdms-der` and `jgdms-platform`): `mvn install` the JGDMS root
first, then

    mvn -f jgdms-benchmarks/pom.xml package

produces `serialization/target/serialization-benchmarks.jar`.

## Running

The JERI tier needs a JDK on which `Permission.checkGuard` is permitted:
DirtyChai, or a stock JDK up to 23. Stock JDK 24+ throws from the TCP
endpoint's static initialiser ("checking permissions is not supported"),
which is the condition DirtyChai exists to remove. The codec tier runs on
any JDK 25+.

    JAVA_HOME=/path/to/dirtychai/jdk ./run.sh -rf json -rff results.json
    python3 summarise.py results.json

(`run.cmd` on Windows.) Arguments after the script name go to JMH:
`"CodecBenchmark.*"` or `"JeriBenchmark.*"` to run one tier, `-t 4` for
four concurrent callers, `-prof gc` for allocation per operation, `-h` for
the rest. Defaults: 2 forks, 5 warmup and 5 measurement iterations, average
time in microseconds.

No SecurityManager is installed. Measuring with one installed is a different
question (the policy-overhead benchmarks in `../harness` answer it) and would
need a policy for this jar.

## Profiling (JFR)

To see where a codec benchmark spends its time, attach JMH's JFR profiler to a
single benchmark and payload, then aggregate the samples:

    JAVA_HOME=/path/to/dirtychai/jdk ./run.sh "CodecBenchmark.marshal" \
        -p format=DER -p payload=record -f 1 -prof jfr
    python3 jfr-hotspots.py <profile.jfr from the JMH output> [frame-prefix] [top-n]

`jfr-hotspots.py` prints self and inclusive time per `au.net.zeus.jgdms.der.*`
frame as a percentage of samples (it needs the `jfr` tool from the same JDK on
`PATH`). Compare a profile before and after a change rather than reading one in
isolation; see `docs/SOW-DER-Codec-Performance.md` for how the DER codec work
uses this.

## Reading the results

`summarise.py` prints JOSS and DER side by side with the `DER/JOSS` ratio. A
ratio below 1 means DER is faster. Read the codec tier for the protocol
itself and the JERI tier for what a caller sees; the gap between `ping` and a
payload method in the JERI tier is what that payload costs the call.

Both formats are measured on the same JVM in the same run, so the ratios are
robust to the host; the absolute times are not, and should only be compared
with other runs on the same machine and JDK.

## Reference results

See `RESULTS.md` next to this file for a run on a reference host, with the
JDK and conditions it was taken under.

---
This module was written with AI assistance (Claude) under the direction of
the JGDMS maintainer.
