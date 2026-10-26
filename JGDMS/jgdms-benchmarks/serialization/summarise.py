#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Pairs the JOSS and DER results from a JMH JSON file and prints, for each
# benchmark and payload, both times and the DER/JOSS ratio.
#
#   run.sh -rf json -rff results.json
#   python3 summarise.py results.json
#
# Written with AI assistance (Claude); see the README.
import json
import sys
from collections import defaultdict


def parse(path):
    with open(path, encoding="utf-8") as f:
        runs = json.load(f)
    table = defaultdict(dict)   # (class, method, payload) -> format -> (score, error, unit)
    for run in runs:
        cls, method = run["benchmark"].rsplit(".", 2)[-2:]
        params = run.get("params") or {}
        fmt = params.get("format")
        if fmt not in ("JOSS", "DER"):
            continue
        payload = params.get("payload", "")
        metric = run["primaryMetric"]
        try:
            err = float(metric.get("scoreError", "nan"))
        except (TypeError, ValueError):
            err = float("nan")
        table[(cls, method, payload)][fmt] = (float(metric["score"]), err, metric["scoreUnit"])
    return table


def main(path):
    table = parse(path)
    if not table:
        sys.exit("no JOSS/DER results in " + path)
    print(f"{'benchmark':<28} {'payload':<9} {'JOSS':>16} {'DER':>16} {'DER/JOSS':>9}")
    last_cls = None
    for (cls, method, payload), by_fmt in sorted(table.items(), key=sort_key):
        if cls != last_cls:
            print(f"-- {cls}")
            last_cls = cls
        joss = by_fmt.get("JOSS")
        der = by_fmt.get("DER")
        if not joss or not der:
            print(f"{method:<28} {payload:<9} (missing one format)")
            continue
        unit = joss[2].replace("/op", "")
        ratio = der[0] / joss[0] if joss[0] else float("nan")
        print(f"{method:<28} {payload:<9} {fmt(joss):>16} {fmt(der):>16} {ratio:>9.2f}   {unit}")


PAYLOAD_ORDER = {"": 0, "point": 1, "record": 2, "batch100": 3, "blob64k": 4}
METHOD_ORDER = {"ping": 0, "point": 1, "record": 2, "batch100": 3, "blob64k": 4,
                "marshal": 0, "unmarshal": 1, "roundTrip": 2}


def sort_key(item):
    (cls, method, payload), _ = item
    return (cls, PAYLOAD_ORDER.get(payload, 9), METHOD_ORDER.get(method, 9), method)


def fmt(v):
    score, err, _ = v
    if err != err:  # NaN
        return f"{score:.2f}"
    return f"{score:.2f} ± {err:.2f}"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: summarise.py results.json")
    main(sys.argv[1])
