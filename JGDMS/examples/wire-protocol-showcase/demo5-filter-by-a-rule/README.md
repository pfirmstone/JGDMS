# Demonstration 5: Filter records by a written rule, without the class

## What you will see

Two separate programs run, one after the other.

The **producer** has two record classes on its classpath — a weather reading and a
survey observation. It reduces several readings to their on-the-wire form (each field
becomes canonical bytes; a small shape description travels alongside) and writes them to
a file.

The **reader** runs with those record classes **deliberately left off its classpath**.
It never turns a reading back into an object. Working only from the bytes and the
travelling shape, it:

1. **Filters the readings by a written rule** — `temperature above 20 and station name
   starts with "North"` — and selects exactly the readings that match. The rule is not
   Java, not downloaded code; it is a small rule that travelled as canonical bytes. The
   reader confirms it cannot even load the record class, so no reading was ever rebuilt
   into an object.
2. **Refuses any rule that could misbehave, at the moment it is registered.** The rule
   language has no loops and no way to call itself, so no rule can run forever. A rule
   that would be too expensive to run, or that is larger than the fixed ceilings allow,
   is refused up front — before it is ever run. A malformed rule, or one that reads a
   field that is not there, is refused with a definite, named answer — never a crash and
   never a wrong result.
3. **Gives the same answer every time — exactly.** The same yes/no rule, run a thousand
   times over the same reading, is identical every time. A transform rule — turning a
   survey observation's bearing, vertical angle and distance into a local direction
   vector, using the correctly-rounded trigonometry the language ships with — produces
   the same number down to the last bit on every run.

The reader checks every one of these points and exits non-zero if any fails, so the
launcher's success is a real, automated confirmation.

## How to run it

```
# Windows
./run.ps1

# macOS / Linux / Git Bash
./run.sh
```

The launcher builds the rule-language library and the wire-format libraries it needs
from the current source tree (one offline Maven build), then compiles the two record
classes into one folder and the two programs into another, and runs the producer with
the record classes present and the reader with them absent.

## Why it matters

Three properties come together here, and each one removes a whole category of risk.

**No class needed.** A server can hold records and answer "which of these match this
rule?" without ever having the record's class. It reads the fields straight from the
bytes, guided by the small shape description that travels in the message. There is no
class to download, so there is no download to secure, and no reconstruction code for a
hostile record to attack during a match.

**Nothing to run away.** The rule is not a program in a general language; it is a small
expression with a fixed, closed set of building blocks — comparisons, arithmetic, a
handful of named functions — and no loops or recursion at all. Before a rule is
accepted, it is checked against fixed ceilings and a cost model, so a rule that would be
too expensive to run is refused before it runs even once. It cannot hang the server and
it cannot blow up its memory. When a rule cannot produce an answer — a missing field, a
type that does not fit — it stops with a definite, named result, not a crash.

**The same answer everywhere.** The language is defined so precisely — pinned rounding,
a canonical form on the wire, one exact meaning for every operation — that the same rule
over the same data always gives the same answer. This demonstration shows one
implementation being exact and reproducible. The *design* goal is stronger: a second
reader written in another language (for example Rust or Haskell) would give the
byte-identical answer, checked by a shared corpus of conformance vectors that already
ships with the rule-language library. That second-language reader is **planned, not yet
running** — this demonstration deliberately makes no live cross-language comparison.

Put together: an in-message, class-free, bounded rule lets a server filter records it
was never taught the shape of, from senders it does not trust, without downloading code,
without needing the class, and without any way for the rule to hang or overrun.

## A note on the rule's form

The rule language has no text form to type. A rule is built as a small tree and travels
as canonical bytes, and a reader decodes those bytes — it never parses text. So this
demonstration builds the rule programmatically and prints it in a readable shorthand
(for example `temperatureCelsius > 20.0 AND stationName starts-with "North"`) so you can
see on screen exactly what travelled and what ran. The readable line and the bytes are
produced from the same source, so they cannot drift apart.

## A note on what is NOT shown here

This demonstrates the rule **engine** directly: a rule is registered and run against
records in the same program. Routing a rule to a shared-space server so it filters the
records already stored there (pushing the filter down to where the data lives) is the
natural next step and is designed for, but is **not** built yet — so it is not shown
here. Nothing in this demonstration depends on it.
