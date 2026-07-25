# The wire format, shown six ways

**The same object always turns into exactly the same bytes — so a checksum of the
bytes becomes a real identity for the value, and a signature over them keeps working
after the object travels.** Java's own serialization does not do this. Everything else
here builds on it.

Each demonstration below is a tiny program that shows one such claim happening, with
real bytes, in about fifteen seconds — and checks itself, so you (or a build server)
can confirm the claim actually held.

### See it in one file (30 seconds)

The shortest way in is one self-contained source file — read it top to bottom, no
project knowledge needed:

**→ [`SameObjectSameBytesDemo.java`](src/main/java/au/net/zeus/jgdms/showcase/demo/SameObjectSameBytesDemo.java)** — 138 lines. It builds a value, prints its
bytes and checksum, does it again from a different object, and shows the bytes are
identical while Java's built-in serialization's are not. ([short walk-through](README-demo1-same-object-same-bytes.md).)

Prefer to run everything? Jump to [running them](#running-them) — one command, all six.

---

## The six demonstrations

Every row links to the source you can read and the short walk-through beside it.
**Start with demo 1.**

| # | Demo (read the source) | What it shows | Walk-through | Self-check |
|---|---|---|---|---|
| 1 | [`SameObjectSameBytesDemo`](src/main/java/au/net/zeus/jgdms/showcase/demo/SameObjectSameBytesDemo.java) **← start here** | equal values → identical bytes → identical checksum; a signature survives a round trip; Java's built-in serialization does **not** give equal values identical bytes | [demo 1](README-demo1-same-object-same-bytes.md) | `SameObjectSameBytesTest` |
| 2 | [`demo2` server](demo2-match-without-the-class/src/au/net/zeus/jgdms/showcase/match/Server.java) | a server matches a template against a stored record by comparing bytes, with the record's class **absent** from its classpath | [demo 2](demo2-match-without-the-class/README.md) | server exits non-zero on any failed claim; the launcher checks that |
| 3 | [`SchemaSentOnceDemo`](src/main/java/au/net/zeus/jgdms/showcase/demo/SchemaSentOnceDemo.java) | writing 100 records to one stream sends the shape description once; the total stays far below sending it every time | [demo 3](README-demo3-shape-sent-once.md) | `SchemaSentOnceTest` |
| 4 | [`HostileInputDemo`](src/main/java/au/net/zeus/jgdms/showcase/demo/HostileInputDemo.java) | truncated, scrambled, over-nested, and expansion-bomb inputs are each refused cleanly, with bounded memory and no crash | [demo 4](README-demo4-feed-it-garbage.md) | `HostileInputStopsPolitelyTest` |
| 5 | [`demo5` reader](demo5-filter-by-a-rule/src/au/net/zeus/jgdms/showcase/rule/Reader.java) | a reader selects records matching a rule, evaluated over their fields, with the record's class **absent** from its classpath | [demo 5](demo5-filter-by-a-rule/README.md) | reader exits non-zero on any failed claim; the launcher checks that |
| 6 | [`CollectionEqualityDemo`](src/main/java/au/net/zeus/jgdms/showcase/demo/CollectionEqualityDemo.java) | two different collection classes holding the same value → identical bytes and checksum, while standard Java serialization gives them different bytes; plus the matching Rust/Haskell types | [demo 6](README-demo6-collection-equality.md) | self-checks — exits non-zero on any failed claim |

The six claims in one line each:

1. **Same object, same bytes — everywhere.** (the file above)
2. **Match a record without ever loading its class** — so a hostile record has nothing to attack, because the object is never rebuilt.
3. **The shape description travels in the stream, and you pay for it once** — many records of one type share the first one's shape.
4. **Feed it garbage, it stops politely** — cut-off, scrambled, over-nested, or bomb inputs are refused cleanly, bounded memory, no attacker code runs.
5. **Select records by a rule, without loading their class** — pick records by comparing field *values*, not just exact matches, over the bytes.
6. **Two collection classes, one value, one set of bytes** — a `HashSet` and a `TreeSet` of the same elements produce identical bytes; standard Java serialization does not.

---

## Running them

You need two small libraries from this project in your local build cache (a normal
build of the project puts them there) and a Java 25+ runtime (this project's own
runtime is fine). If you have never built the project, build it once first.

From this folder:

```
# Windows
./run-demos.ps1

# macOS / Linux / Git Bash
./run-demos.sh
```

That rebuilds the two libraries from current source first (so nothing runs against a
stale cache), then runs all six demonstrations and their checks. Demonstration 4 runs
inside a 128-megabyte memory ceiling on purpose — the expansion bomb would want
gigabytes, and you watch it refused without ever reaching that ceiling.

Demonstrations 2 and 5 live in their own folders because they need two processes with
different classpaths — that difference is the whole point. The parent script runs them
for you; you can also run either alone:

```
cd demo2-match-without-the-class
./run.ps1          # or ./run.sh
```

---

## The real numbers this produced

These came out of an actual run on the sample record type used here (a three-level
record with descriptive field names, so its shape description is a real fraction of
each record — 461 bytes, about three quarters of a single self-describing record):

- **100 records, shape sent once:** 17,136 bytes.
- **100 records, shape repeated every time:** 63,000 bytes. Sending it once saved
  **72.8%**.
- **100 records, as JSON text with the field names spelled out every time:** 23,346
  bytes. The shape-sent-once form was **26.6%** smaller.

Reported honestly: at small counts the JSON form is smaller (it has no shape
description to amortise); the stream format overtakes it after a handful of records.

---

## Honesty notes (please keep these)

- **The size win in demonstration 3 depends on the record type.** It is real here
  because the shape description is a meaningful fraction of each record and is shared
  across many records. On a different, flatter record type (100 records with a tiny
  125-byte shared shape) an earlier measurement still shrank 22,180 → 9,378 bytes
  (to 42.3%). But there is one storage layout in this project where every field is a
  separate, unique capture, and there the saving is only a few percent — we would
  report that number, not this one. The demonstration measures real bytes every time;
  it never draws a flat line that is not there.

- **On demonstration 4, "feed it garbage":** this demonstration was deliberately held
  back when the first three shipped, because one denial-of-service case — a small message
  that expands into a very large object on the way in — was not yet fixed, and shipping it
  then would have made a "bomb-proof" claim that was not yet true. That fix has since
  landed: the reader now refuses the expansion bomb at a fixed ceiling (both the
  single-message peak-memory ceiling and the many-message cumulative ceiling), with memory
  staying bounded. So the demonstration now claims only what it actually shows. The "about
  24 gigabytes" figure in its output and README is a projection from the measured
  per-reference amplification to the normal input budget, not a run that allocated that
  much — the demonstration uses a small budget on purpose so it can prove the fence
  without buffering gigabytes. It does not claim resistance to any threat it does not
  exercise.

---

## "But isn't Avro or protocol-buffers smaller?"

Usually, yes. Those formats are smaller because they trust a shape description that
lives somewhere else, agreed by both sides ahead of time and not sent with the data —
and because they do not promise that the same value always comes out as the same bytes.

This format makes a different trade. It spends a few extra bytes per field to buy three
things those compact formats do not offer:

1. the checksum of the bytes is a genuine identity for the value, and a signature over
   the bytes keeps verifying after the value has travelled;
2. a server can hold and match records whose classes it never loads — so a hostile
   record cannot run code on it during a match; and
3. a malformed, truncated, over-nested, or expanding message fails safely instead of
   turning into attacker-chosen behaviour (demonstration 4).

And there is one thing none of the compact formats can do at all: compare two records
for a match by looking only at the bytes. They have to decode first. This format does
not.
