# Demonstration 3: The shape description travels once

## What you will see

The program writes 100 records of the same type to one stream and measures the result
three ways:

- **shape sent once** — this project's stream format. The description of the record's
  shape (its family tree of field names and types) is sent with the first record; every
  record after that points back to it.
- **shape repeated every time** — each record carries its own full shape description.
- **JSON (field names on every record)** — the same data written as minified JSON text,
  which spells out every field name on every record.

You will see the shape description measured (461 bytes — about three quarters of a
single self-describing record), then watch what each extra record adds to the stream:
the first record is large because it carries the shape; the second and third add only
their own data plus a short back-reference. A table shows the running total at several
counts, and a bar chart shows the three totals side by side at 100 records.

## How to run it

```
./run-demos.ps1        # or ./run-demos.sh
```

or on its own, after `mvn -q package -DskipTests`:

```
java -cp "target/classes;target/lib/*" au.net.zeus.jgdms.showcase.demo.SchemaSentOnceDemo
```

The automated check is `SchemaSentOnceTest` (run by `mvn test`). It measures **real
bytes** and asserts: the shape description is a genuine fraction of each record; each
record after the first costs far less than a full self-describing record; and at 100
records the stream is smaller than repeating the shape and smaller than the equivalent
JSON text. It also decodes all 100 records back out of the stream.

## The numbers from a real run

| records | shape sent once | shape repeated | JSON w/ names |
|--------:|----------------:|---------------:|-------------:|
| 1       | 606             | 633            | 234        |
| 10      | 2,104           | 6,298          | 2,317      |
| 100     | 17,136          | 63,000         | 23,346     |

At 100 records, sending the shape once instead of every time saved **72.8%**, and the
result was **26.6%** smaller than the equivalent JSON text (field names on every record).

## Honesty note (important)

None of these lines is flat, and the win depends on the record type. Storing N records
always costs at least N records' worth of data; what changes is how much each extra
record adds. The saving is large here because the shape description is a meaningful,
shared fraction of each record. On a flatter record type with a tiny shared shape, an
earlier measurement still shrank 22,180 → 9,378 bytes (to 42.3%). But there is one
storage layout in this project where each field is a separate, unique capture, and
there the saving is only a few percent. The demonstration always measures the real
bytes for the record type in front of it; it never draws a saving that is not there.

## Why it matters

The reader never has to already know the record's shape. It arrives in the stream,
once, and every later record points back to it. You get a stream that fully describes
itself without paying to describe every single record — the convenience of a
self-describing format without the usual size penalty at scale.
