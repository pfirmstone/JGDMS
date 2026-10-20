#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Scalability sweep: JERI (JOSS and DER) against Java RMI, with N concurrent callers on one
# proxy/stub, for a list of caller counts. One command per machine:
#
#   python3 sweep.py --out sweep-6core                       # sensible defaults for this machine
#   python3 sweep.py --out sweep-80core --threads 1,8,16,40,80,160,320
#   python3 sweep.py --out sweep-x --local                   # server in the same JVM as the callers
#
# By default the services run in a separate JVM (au.net.zeus.jgdms.bench.serial.ServerMain) so the
# callers and the server do not share a heap or the JIT. Results: <out>/threads-N.json per count,
# <out>/summary.txt (also printed), <out>/environment.txt. Paste summary.txt and environment.txt back.
#
# Written with AI assistance (Claude); see the README.
import argparse
import json
import os
import platform
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_JAR = os.path.join(HERE, "target", "serialization-benchmarks.jar")


def java_cmd():
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", "java") if home else "java"


def default_threads(cores):
    out, n = [], 1
    while n < cores:
        out.append(n)
        n *= 2
    out += [cores, cores * 2, cores * 4]
    return sorted(set(out))


def start_server(jar, jvm_args, directory):
    cmd = [java_cmd()] + jvm_args + ["-cp", jar, "au.net.zeus.jgdms.bench.serial.ServerMain", directory]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    deadline = time.time() + 60
    for line in proc.stdout:
        if line.strip() == "ready":
            return proc
        if time.time() > deadline:
            break
    proc.kill()
    sys.exit("the server JVM did not become ready")


def run_jmh(jar, jvm_args, benchmarks, threads, out_json, extra, remote_dir, args):
    cmd = [java_cmd(), "-Djmh.link.address=127.0.0.1"] + jvm_args + ["-jar", jar, benchmarks,
           "-t", str(threads), "-bm", "thrpt", "-tu", "ms", "-f", str(args.forks),
           "-wi", str(args.warmups), "-w", "%ds" % args.warmup_seconds,
           "-i", str(args.iterations), "-r", "%ds" % args.seconds, "-rf", "json", "-rff", out_json]
    child = list(jvm_args)
    if remote_dir:
        for kind in ("JOSS", "DER", "rmi"):
            child.append("-Dbench.proxy.%s=%s" % (kind, os.path.join(remote_dir, kind + ".proxy")))
    if child:
        cmd += ["-jvmArgsAppend", " ".join(child)]
    cmd += extra
    print("== %d caller thread(s)" % threads, flush=True)
    subprocess.run(cmd, check=True)


def summarise(out_dir, threads):
    table = {}
    for t in threads:
        path = os.path.join(out_dir, "threads-%d.json" % t)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for run in json.load(f):
                cls, method = run["benchmark"].rsplit(".", 2)[-2:]
                fmt = (run.get("params") or {}).get("format")
                name = "RMI" if cls == "RmiBenchmark" else "JERI " + fmt
                table.setdefault((method, name), {})[t] = float(run["primaryMetric"]["score"])
    lines = []
    cols = [t for t in threads if any(t in v for v in table.values())]
    lines.append("Throughput, calls per millisecond, summed over all caller threads")
    lines.append("%-10s %-10s" % ("payload", "stack") + "".join("%10d" % t for t in cols))
    order = {"ping": 0, "record": 1, "batch100": 2}
    for (method, name), row in sorted(table.items(), key=lambda kv: (order.get(kv[0][0], 9), kv[0][1])):
        lines.append("%-10s %-10s" % (method, name) + "".join("%10.2f" % row[t] if t in row else "%10s" % "-" for t in cols))
    lines.append("")
    lines.append("Speed-up over one caller thread")
    lines.append("%-10s %-10s" % ("payload", "stack") + "".join("%10d" % t for t in cols))
    for (method, name), row in sorted(table.items(), key=lambda kv: (order.get(kv[0][0], 9), kv[0][1])):
        base = row.get(1)
        lines.append("%-10s %-10s" % (method, name) + "".join(
            "%10.2f" % (row[t] / base) if base and t in row else "%10s" % "-" for t in cols))
    lines.append("")
    lines.append("JERI DER throughput over RMI throughput")
    lines.append("%-10s" % "payload" + "".join("%10d" % t for t in cols))
    for method in ("ping", "record", "batch100"):
        der, rmi = table.get((method, "JERI DER"), {}), table.get((method, "RMI"), {})
        lines.append("%-10s" % method + "".join(
            "%10.2f" % (der[t] / rmi[t]) if t in der and t in rmi and rmi[t] else "%10s" % "-" for t in cols))
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--jar", default=DEFAULT_JAR)
    ap.add_argument("--threads", help="comma-separated caller counts (default: powers of two up to the core count, then x2, x4)")
    ap.add_argument("--benchmarks", default="(JeriBenchmark|RmiBenchmark).(ping|record|batch100)")
    ap.add_argument("--local", action="store_true", help="export the services in the benchmark JVM instead of a separate server JVM")
    ap.add_argument("--jvm-args", default="", help="extra JVM arguments for the benchmark and server JVMs, e.g. '-Xmx8g -XX:+UseParallelGC'")
    ap.add_argument("--forks", type=int, default=1)
    ap.add_argument("--warmups", type=int, default=3)
    ap.add_argument("--warmup-seconds", type=int, default=2)
    ap.add_argument("--iterations", type=int, default=5)
    ap.add_argument("--seconds", type=int, default=3, help="seconds per measured iteration")
    ap.add_argument("extra", nargs="*", help="further JMH arguments")
    args = ap.parse_args()
    if not os.path.exists(args.jar):
        sys.exit("missing %s: run 'mvn -f ../pom.xml -pl serialization package' first" % args.jar)
    cores = os.cpu_count() or 1
    threads = [int(x) for x in args.threads.split(",")] if args.threads else default_threads(cores)
    jvm_args = args.jvm_args.split() if args.jvm_args else []
    os.makedirs(args.out, exist_ok=True)
    version = subprocess.run([java_cmd(), "-version"], capture_output=True, text=True).stderr.strip()
    env = "cores: %d\nos: %s %s\nmachine: %s\njava: %s\njvm args: %s\nthreads: %s\nserver: %s\nsettings: forks=%d warmups=%dx%ds iterations=%dx%ds\n" % (
        cores, platform.system(), platform.release(), platform.machine(), version.replace("\n", " | "),
        args.jvm_args or "(none)", threads, "same JVM" if args.local else "separate JVM",
        args.forks, args.warmups, args.warmup_seconds, args.iterations, args.seconds)
    with open(os.path.join(args.out, "environment.txt"), "w", encoding="utf-8") as f:
        f.write(env)
    print(env)
    server, remote = None, None
    if not args.local:
        remote = tempfile.mkdtemp(prefix="bench-server-")
        server = start_server(args.jar, jvm_args, remote)
    try:
        for t in threads:
            run_jmh(args.jar, jvm_args, args.benchmarks, t, os.path.join(args.out, "threads-%d.json" % t),
                    args.extra, remote, args)
    finally:
        if server is not None:
            server.stdin.close()
            try:
                server.wait(timeout=15)
            except subprocess.TimeoutExpired:
                server.kill()
    text = summarise(args.out, threads)
    with open(os.path.join(args.out, "summary.txt"), "w", encoding="utf-8") as f:
        f.write(text + "\n")
    print()
    print(text)


if __name__ == "__main__":
    main()
