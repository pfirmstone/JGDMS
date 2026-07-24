# Demonstration 4: Feed it garbage, it stops politely

## What you will see

The reader that turns wire bytes back into objects is handed four hostile inputs, one
after another. Each is refused cleanly — a plain, checked error the program is expected
to catch, with memory kept in bounds, no hang, and no crash. Nothing on screen uses an
acronym or a piece of internal jargon.

The four hostile inputs:

1. **A message cut off partway through.** A genuine message is written out, then its last
   bytes are dropped. The length markers now promise more data than the message holds, so
   the reader refuses it.
2. **A message whose structure markers have been scrambled.** A genuine message is
   written out, then the marker that says "what kind of thing comes next" is overwritten
   with a value that means nothing to the reader, so it refuses it.
3. **A message nested inside itself far deeper than the reader will follow.** The reader
   follows nesting up to a fixed depth (16) and no further. A message nested exactly to
   that depth reads back fine; one nested thousands of levels deep is stopped at the
   depth limit — with a plain error, **not** by the program running out of stack and
   crashing. The crafted message is genuine, not malformed: its bytes are byte-for-byte
   identical to what the writer would produce, so the refusal is truly the depth limit
   doing its job.
4. **A tiny message crafted to balloon into gigabytes when unpacked.** This is the
   headline. One large shape is sent once, then a long run of tiny back-references to it.
   Each back-reference is a few dozen bytes on the wire but would re-grow the whole large
   shape when unpacked — so a small wire message would balloon into gigabytes in memory.
   The reader refuses it at a fixed ceiling, and memory never follows the expansion up.

## How to run it

```
./run-demos.ps1        # or ./run-demos.sh
```

or on its own, after `mvn -q package -DskipTests`:

```
java -Xmx128m -cp "target/classes;target/lib/*" au.net.zeus.jgdms.showcase.demo.HostileInputDemo
```

The `-Xmx128m` is the point of the fourth input: the whole demonstration runs inside a
128-megabyte memory ceiling, and the expansion bomb — which would want gigabytes — is
refused without ever coming close to it. The demonstration checks itself and exits
non-zero if any refusal does not hold, so the launcher's success is a real confirmation.

The automated check is `HostileInputStopsPolitelyTest` (run by `mvn test`). It asserts
each hostile input is refused with a checked error (never a crash), that the depth limit
fires before the stack is exhausted, and — for the expansion bomb — that growth stops at
or below the reader's ceiling and that per-message memory stays flat across a flood.

## The numbers from a real run

For the expansion bomb (input 4), driven with a deliberately small input budget so the
ceiling is small and the refusal fires quickly, without ever buffering the gigabytes a
full-size budget would permit:

| measured | value |
|---|---|
| one tiny back-reference on the wire | 42 bytes |
| what that one reference would re-grow to | 65,514 bytes |
| amplification, per wire byte | about 1,560× |
| the hostile message on the wire | 107,544 bytes |
| the reader's memory ceiling for one message | about 2 megabytes |
| growth reached, then stopped, under that ceiling | 2,096,000 bytes |
| the same trick at the normal, full-size input budget | would reach about **24 gigabytes** — refused there too |

And the slow-burn variant — a flood of small messages, each re-growing just one shape:
**255** small messages were accepted, then the flood was refused at a separate cumulative
ceiling; memory for each message stayed flat at **65,500 bytes** the whole way (the reader
lets go of one message before starting the next), so the flood never accumulates.

## The contrast

Java's built-in deserialization historically had none of these fences. A message that was
truncated, malformed, over-nested, or crafted to expand could hang, crash, exhaust memory,
or — worst — cause attacker-chosen code to run while the object was being rebuilt. This
reader refuses all four by construction, and it never rebuilds an object it has not first
accepted — so a refused message never gets the chance to do anything at all.

## Honesty note (important)

This demonstration claims only what it shows: truncated, malformed, over-nested, and
expansion-bomb inputs are each refused cleanly with bounded memory. The expansion-bomb
fence is genuinely closed now — both the single-message (peak-memory) ceiling and the
many-message (cumulative-work) ceiling are in place and measured here. The "about 24
gigabytes" figure is a projection from the measured per-reference amplification to the
normal input budget, **not** a run that actually allocated 24 gigabytes; the demonstration
deliberately uses a small budget so it can prove the fence without ever buffering that
much. It does not claim resistance to any threat it does not exercise.

## Why it matters

A format that reads attacker-controlled bytes has to assume every message is hostile. The
value here is that hostility is handled by refusal, not by damage: each bad message costs
the reader a cheap, checked error and bounded memory, and nothing an attacker sends is
ever reconstructed into a live object — let alone into running code — unless the reader
has first accepted it as well-formed and within its limits.
