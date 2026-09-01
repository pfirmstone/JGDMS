# Demonstration 2: Match a record without ever loading its class

## What you will see

Two separate programs run, one after the other.

The **producer** has a record class on its classpath. It builds one stored record and
two templates, reduces them to their on-the-wire form (each field becomes canonical
bytes; the class name travels as plain text), and writes them to a file.

The **matching server** runs with that record class **deliberately left off its
classpath**. It:

1. confirms it cannot load the record class (it is genuinely absent);
2. reads the three records anyway — it does not need the class, because every field is
   opaque bytes and the class name is just text;
3. matches a template against the stored record by comparing bytes — the matching one
   returns *true*, the other returns *false*;
4. shows that if it tried to rebuild the record *object* from the bytes, that would
   fail, because rebuilding needs the class. Matching did not.

The server checks all four points and exits non-zero if any fails, so the launcher's
success is a real confirmation.

## How to run it

```
# Windows
./run.ps1

# macOS / Linux / Git Bash
./run.sh
```

The launcher compiles the record class into one folder and the two programs into
another, then runs the producer with the record class present and the server with it
absent. (If the shared-space library it builds on has not been built yet, the launcher
builds that one module first.)

## Why it matters

This is the property that anyone who lived through the Java serialization security
years will care about. In many systems, receiving an object means turning bytes back
into an object — running the object's own reconstruction code — before you can do
anything with it. That reconstruction step is exactly where a hostile message gets to
run code it chose.

Here, a server can store records and answer "does this template match?" without ever
taking that step. It compares bytes. The record's class is not present, is never
loaded, and its reconstruction code never runs. There is nothing there for a hostile
record to attack during a match. And because the stored bytes were produced by the
canonical format rather than by Java's built-in serialization, they were never the
output of the machinery those old attacks targeted in the first place.

**Honesty note:** the server still atomically deserializes the `EntryRep` *container*
(the class-name string and the field byte arrays) — that surface is bounded by the DER
reader's input limits (see demonstration 4). The class-free claim covers the entry's
*own* class, which is never loaded.

## A note on the transport used here

To keep the demonstration to two small programs, the producer writes the on-the-wire
records to a file and the server reads them back — which is exactly what a real
shared-space server receives from a client over a connection. The point being shown is
that the server reconstructs and matches those records with the record class absent.
