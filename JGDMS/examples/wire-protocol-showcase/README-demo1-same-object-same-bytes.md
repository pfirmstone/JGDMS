# Demonstration 1: Same object, same bytes — everywhere

## What you will see

A reading is written to the wire in the canonical format. Then the same value is
written again, built freshly and separately. The two results are shown, and they are
byte-for-byte identical — so their checksums are identical too. Run the program again
in a brand-new process and you get the very same checksum, because the bytes depend
only on the value, never on the run or the machine.

Then the program makes a signature over the bytes, sends the reading out and reads it
back in, and checks the signature again: it still verifies.

Finally, the program contrasts this with Java's built-in serialization. It builds two
batches that are **equal by value** — one where the same reading object appears twice,
one where two separate-but-equal readings appear. The canonical format gives both
batches identical bytes. Java's built-in serialization gives them **different** bytes,
because it also records whether the two parts were the same object.

## How to run it

```
# from the showcase folder
./run-demos.ps1        # or ./run-demos.sh   (runs this and demonstration 3)
```

or on its own, after `mvn -q package -DskipTests`:

```
java -cp "target/classes;target/lib/*" au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo
```

The automated check is `SameObjectSameBytesTest` (run by `mvn test`). It asserts:
equal values give identical bytes and checksum; a signature survives the round trip;
the canonical format ignores whether objects were shared, while Java's built-in
serialization does not.

## Why it matters

If the same value always produces the same bytes, then the checksum of those bytes is
a name for the value that anyone can compute, anywhere, with nothing shared between
them. Two systems that have never spoken can agree on whether they hold the same record
just by comparing a checksum. A signature made once keeps meaning something after the
record has been stored, forwarded, and read back. None of that is safe to rely on when
"the same value" can come out as different bytes.
