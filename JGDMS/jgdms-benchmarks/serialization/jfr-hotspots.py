#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Aggregates JFR ExecutionSample events (from `-prof jfr`) into self-time and
# inclusive-time percentages per frame, for frames matching a prefix. This is
# the aggregation behind docs/investigation-der-codec-hotspots-2026-10-18.md.
#
#   java ... -jar target/serialization-benchmarks.jar "CodecBenchmark.marshal" \
#        -p format=DER -p payload=record -prof jfr
#   python3 jfr-hotspots.py path/to/profile.jfr [frame-prefix] [top-n]
#
# Needs the `jfr` tool from the same JDK on PATH. Written with AI assistance
# (Claude); see the README.
import re
import subprocess
import sys
from collections import Counter


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else "au.net.zeus.jgdms.der."
    top = int(sys.argv[3]) if len(sys.argv) > 3 else 20
    out = subprocess.run(
        ["jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", "64", path],
        check=True, capture_output=True, text=True).stdout
    frame = re.compile(r"^\s+([\w.$<>]+)\(.*\)\s+line:")
    self_t, incl_t, samples = Counter(), Counter(), 0
    for ev in out.split("jdk.ExecutionSample"):
        frames = []
        for line in ev.splitlines():
            m = frame.match(line)
            if m:
                frames.append(m.group(1))
        if not frames:
            continue
        samples += 1
        if frames[0].startswith(prefix):
            self_t[frames[0]] += 1
        for f in set(frames):
            if f.startswith(prefix):
                incl_t[f] += 1
    if not samples:
        sys.exit("no ExecutionSample events with stack frames found")
    print(f"{samples} samples; frames starting with {prefix!r}")
    for title, c in (("self", self_t), ("inclusive", incl_t)):
        print(f"\n-- {title} time, top {top}")
        for f, n in c.most_common(top):
            print(f"{100.0 * n / samples:6.1f}%  {f}")


if __name__ == "__main__":
    main()
