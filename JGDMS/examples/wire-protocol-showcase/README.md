# The wire format, shown six ways

This is a small set of runnable demonstrations. Each one takes a claim about how
this project puts objects onto the wire, and shows it happening, with real bytes,
in about fifteen seconds of terminal output. Each demonstration also checks itself,
so a person watching — or a build server — can confirm the claim actually held.

There is no jargon in what you see on screen. The six claims, in plain terms:

1. **Same object, same bytes — everywhere.** The same value always turns into exactly
   the same bytes. That makes a plain checksum of the bytes a real identity for the
   value, and it lets a signature over the bytes keep working after the object travels.

2. **Match a record without ever loading its class.** A server can hold records and
   answer "does this template match?" by comparing bytes — without the record's class,
   and without ever rebuilding the object. If it never rebuilds the object, a hostile
   record has nothing to attack.

3. **The shape description travels in the stream, and you pay for it once.** When many
   records of the same type go down one stream, the description of their shape is sent
   with the first one; every record after that just points back to it.

4. **Feed it garbage, it stops politely.** A message that is cut off partway through, one
   whose structure markers are scrambled, one nested inside itself far deeper than
   allowed, or a tiny one crafted to balloon into gigabytes when unpacked — each is
   refused cleanly, with bounded memory, no crash, and no attacker code ever running.

5. **Select records by a rule, without loading their class.** A reader picks the records
   it wants by evaluating a rule over their fields — comparing values, not just exact
   matches — over the bytes, without ever loading the record's class.

6. **Two different collection classes, one value, one set of bytes.** A `HashSet` and a
   `TreeSet` holding the same elements produce identical bytes — so they match and share a
   checksum — while standard Java serialization gives them different bytes. The same holds
   for the corresponding collection types in Rust and Haskell.

---

## What you need

The demonstrations use two small libraries from this project. A normal build of the
project puts them in your local build cache, which is all these need. Use a Java 25 or
newer runtime (this project's own runtime is fine).

If you have never built the project, run a build of it once first, so the two libraries
are available locally.

## Running them

From this folder:

```
# Windows
./run-demos.ps1

# macOS / Linux / Git Bash
./run-demos.sh
```

That builds the two libraries from the current source first (so nothing runs against a
stale cache), then runs all six demonstrations and the automated checks.
Demonstration 4 runs inside a small 128-megabyte memory ceiling on purpose — the
expansion bomb would want gigabytes, and you get to watch it refused without ever
reaching that ceiling.

Two of them (demonstrations 2 and 5) live in their own folders because they need two
separate processes with different classpaths — that difference is the whole point. The
parent script above runs them for you; you can also run either on its own:

```
cd demo2-match-without-the-class
./run.ps1          # or ./run.sh
```

Each demonstration also has its own short README next to its code.

---

## The six demonstrations

| Folder / file | What it shows | The automated check |
|---|---|---|
| `SameObjectSameBytesDemo` | equal values → identical bytes → identical checksum; a signature survives a round trip; Java's built-in serialization does **not** give equal values identical bytes | `SameObjectSameBytesTest` |
| `demo2-match-without-the-class/` | a server matches a template against a stored record by comparing bytes, with the record's class absent from its classpath | the server program exits non-zero if any claim fails; the launcher checks that |
| `SchemaSentOnceDemo` | writing 100 records to one stream sends the shape description once; the total stays far below sending it every time | `SchemaSentOnceTest` |
| `HostileInputDemo` | truncated, scrambled, over-nested, and expansion-bomb inputs are each refused cleanly, with bounded memory and no crash (see `README-demo4-feed-it-garbage.md`) | `HostileInputStopsPolitelyTest` |
| `demo5-filter-by-a-rule/` | a reader selects the records matching a rule, evaluated over their fields, with the record's class absent from its classpath | the reader program exits non-zero if any claim fails; the launcher checks that |
| `CollectionEqualityDemo` | two different collection classes holding the same value → identical bytes and checksum, while standard Java serialization gives them different bytes; plus the corresponding Rust/Haskell types (see `README-demo6-collection-equality.md`) | self-checks — exits non-zero on any failed claim |

### The real numbers this produced

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
