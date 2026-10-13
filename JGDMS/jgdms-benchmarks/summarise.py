#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Pairs SecurityManager-off and -on results from a JMH JSON file and prints
# the difference and the relative overhead for each benchmark.
#
#   run.sh -rf json -rff results.json
#   python3 summarise.py results.json
#
# Written with AI assistance (Claude); see the README.
import json
import math
import sys
from collections import defaultdict

PAIRS = {
    # config suffix -> (thread label, SecurityManager on?)
    "SmOff": ("1 platform thread", False),
    "SmOn": ("1 platform thread", True),
    "VirtualSmOff": ("virtual, 1 per core", False),
    "VirtualSmOn": ("virtual, 1 per core", True),
}
PREFIX = "PolicyOverhead"


def parse(path):
    with open(path, encoding="utf-8") as f:
        runs = json.load(f)
    table = defaultdict(dict)
    for run in runs:
        cls, method = run["benchmark"].rsplit(".", 2)[-2:]
        config = cls[len(PREFIX):] if cls.startswith(PREFIX) else None
        if config not in PAIRS:
            continue
        threads, sm_on = PAIRS[config]
        params = ", ".join(f"{k}={v}" for k, v in sorted((run.get("params") or {}).items()))
        metric = run["primaryMetric"]
        # JMH writes "NaN" (a string) when there are too few samples for an error.
        try:
            error = float(metric.get("scoreError"))
        except (TypeError, ValueError):
            error = float("nan")
        key = (threads, method, params)
        table[key]["on" if sm_on else "off"] = (metric["score"], error, metric["scoreUnit"])
    return table


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: summarise.py results.json")
    table = parse(sys.argv[1])
    if not table:
        sys.exit("No PolicyOverhead results found in " + sys.argv[1])
    header = f"{'threads':<20} {'benchmark':<12} {'params':<26} {'SM off':>16} {'SM on':>16} {'difference':>16} {'overhead':>14}"
    print(header)
    print("-" * len(header))
    for key in sorted(table):
        threads, method, params = key
        row = table[key]
        if "off" not in row or "on" not in row:
            print(f"{threads:<20} {method:<12} {params:<26} (missing {'off' if 'off' not in row else 'on'} result)")
            continue
        (off, eoff, unit), (on, eon, _) = row["off"], row["on"]
        diff = on - off
        ediff = math.hypot(eoff, eon)
        pct = 100.0 * diff / off if off else float("nan")
        epct = 100.0 * ediff / off if off else float("nan")
        if math.isnan(ediff):
            note = "  (no error estimate: too few iterations)"
        else:
            note = "  (within error)" if abs(diff) <= ediff else ""
        # For guardedRead only the absolute difference (the cost of one check)
        # means anything: a percentage of a bare property read is not an
        # overhead anyone experiences, so don't print one to be quoted.
        overhead = f"{pct:>6.1f}%±{epct:<5.1f}" if method != "guardedRead" else f"{'n/a':>14}"
        print(f"{threads:<20} {method:<12} {params:<26} "
              f"{off:>9.1f}±{eoff:<6.1f} {on:>9.1f}±{eon:<6.1f} {diff:>9.1f}±{ediff:<6.1f} "
              f"{overhead}{note}  [{unit}]")
    print()
    print("guardedRead: the difference is the cost of one permission check (ns).")
    print("request:     the overhead is the policy cost for that request; "
          "guardedReadsPerRequest=0 is the control and should be within error.")


if __name__ == "__main__":
    main()
