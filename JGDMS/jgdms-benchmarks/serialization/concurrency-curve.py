#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Latency and scalability curve: JERI (DER and JOSS; TCP, UDS, TLS) against Java RMI (plain and TLS), with N
# concurrent callers (platform or virtual threads) on one proxy/stub, for a list of caller counts up to thousands.
# Reports the latency distribution (p50, p90, p99, p99.9, max), calls per millisecond, and the threads, sockets,
# resident memory and CPU per call of the client and the server JVM (the last four from Linux /proc where there is
# one; elsewhere they are n/a). Each point is a fresh client JVM (au.net.zeus.jgdms.bench.serial.ConcurrencyCurve)
# against a server in its own JVM (au.net.zeus.jgdms.bench.serial.ServerMain), also fresh for each point unless
# --shared-server, so the server's threads and memory belong to that point alone.
#
#   python3 concurrency-curve.py --out curve-6core                              # the defaults below
#   python3 concurrency-curve.py --out curve-x --callers 1,16,256,4096 --kinds virtual --stacks DER,RMI
#   python3 concurrency-curve.py --out curve-x --transports UDS,TLS --warmup 5 --measure 10
#   python3 concurrency-curve.py --out curve-x --rate 20000 --callers 64,512    # open loop, 20000 calls/s in all
#   python3 concurrency-curve.py --out curve-x --prime 1 --callers 64,512,4096  # one connection exists first
#
# Works on Linux and Windows (no shell; JAVA_HOME or java on the PATH). Combinations that do not exist are skipped:
# RMI has no UDS, and its plain-socket variant is called PLAIN (TCP is accepted as a synonym for it, and PLAIN for
# JERI's TCP). Results: <out>/results.jsonl (one JSON object per point, written as the sweep goes; status ok or
# failed), <out>/summary.txt (also printed), <out>/environment.txt. A point that fails or times out is recorded as
# failed and the sweep goes on. Paste summary.txt and environment.txt back.
#
# Caveat: by default a point is a CLOSED loop (each caller calls again when the last call returns), so under
# saturation the percentiles are subject to coordinated omission. --rate R makes it an open loop (R calls per
# second in all, latency measured from each call's intended start time); see ConcurrencyCurve.java.
#
# The table ends with two columns that make starvation visible: 'mean L us', the mean time per call by Little's law
# (callers / throughput, closed loop only), and 'callers done', how many callers completed at least one call in the
# measured window. The percentiles are over the calls that completed, so a point with a p50 of 0.5 ms and a p99 of
# 600 ms and a 'callers done' far below the callers is a point where most callers were starved. The same two values are
# in results.jsonl as mean_little_us and callers_completed.
#
# Written with AI assistance (Claude); see the README.
import argparse
import json
import os
import platform
import subprocess
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_JAR = os.path.join(HERE, "target", "serialization-benchmarks.jar")
SERVER_CLASS = "au.net.zeus.jgdms.bench.serial.ServerMain"
CLIENT_CLASS = "au.net.zeus.jgdms.bench.serial.ConcurrencyCurve"
JERI_STACKS = ("DER", "JOSS")


def java_cmd():
    home = os.environ.get("JAVA_HOME")
    if home:
        exe = "java.exe" if os.name == "nt" else "java"
        return os.path.join(home, "bin", exe)
    return "java"


def csv(text):
    return [x.strip() for x in text.split(",") if x.strip()]


def combos(stacks, transports):
    """The (stack, transport) pairs that exist, in order, without duplicates."""
    out = []
    for stack in stacks:
        for t in transports:
            t = t.upper()
            if stack == "RMI":
                if t == "UDS":
                    continue                          # RMI has no Unix domain sockets
                t = "PLAIN" if t in ("TCP", "PLAIN") else t
            else:
                t = "TCP" if t == "PLAIN" else t
            if (stack, t) not in out:
                out.append((stack, t))
    return out


class Server:
    """A ServerMain JVM; it exits when its standard input closes."""

    def __init__(self, jar, jvm_args, tls_dir):
        self.dir = tempfile.mkdtemp(prefix="curve-server-")
        cmd = [java_cmd()] + jvm_args + (["-Dbench.tls.dir=" + tls_dir] if tls_dir else []) + \
              ["-cp", jar, SERVER_CLASS, self.dir]
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     text=True)
        self.lines = []
        self.state = None                              # "ready" or "failed"
        self.changed = threading.Event()
        threading.Thread(target=self._drain, daemon=True).start()

    def _drain(self):
        try:
            for line in self.proc.stdout:              # keep reading so the server never blocks on a full pipe
                self.lines.append(line.rstrip())
                if line.strip() == "ready":
                    self.state = "ready"
                    self.changed.set()
                elif line.startswith("failed:"):
                    self.state = "failed"
                    self.changed.set()
        finally:
            if self.state is None:
                self.state = "exited"
            self.changed.set()

    def wait_ready(self, timeout=90):
        self.changed.wait(timeout)
        return self.state == "ready"

    @property
    def pid(self):
        return self.proc.pid

    def stop(self):
        try:
            self.proc.stdin.close()
        except OSError:
            pass
        try:
            # a server that failed or exited has nothing to shut down: do not wait the full time
            self.proc.wait(timeout=3 if self.state in ("failed", "exited") else 15)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            self.proc.kill()
            self.proc.wait()


def run_point(jar, jvm_args, server, tls_dir, stack, transport, payload, kind, callers, args):
    """One client JVM. Returns the result dict (status ok) or a failure record."""
    rec = {"stack": stack, "transport": transport, "payload": payload, "kind": kind, "callers_requested": callers,
           "warmup_s": args.warmup, "measure_s": args.measure}
    cmd = [java_cmd()] + jvm_args + (["-Dbench.tls.dir=" + tls_dir] if tls_dir else []) + [
        "-Dthreadprofile.server=" + server.dir, "-Dthreadprofile.serverpid=" + str(server.pid),
        "-cp", jar, CLIENT_CLASS,
        "stack=" + stack, "transport=" + transport, "payload=" + payload, "callers=%d" % callers, "kind=" + kind,
        "warmup=%s" % args.warmup, "measure=%s" % args.measure, "rate=%s" % args.rate, "prime=%d" % args.prime]
    started = time.time()
    try:
        done = subprocess.run(cmd, capture_output=True, text=True, timeout=args.timeout)
    except subprocess.TimeoutExpired as e:
        rec.update(status="failed", reason="timeout after %d s" % args.timeout, wall_s=round(time.time() - started, 1))
        return rec, tail_text(e.stdout, e.stderr)
    rec["wall_s"] = round(time.time() - started, 1)
    out = (done.stdout or "") + (done.stderr or "")
    for line in (done.stdout or "").splitlines():
        if line.startswith("curve-json: "):
            try:
                result = json.loads(line[len("curve-json: "):])
            except ValueError:
                break
            result.update(status="ok", wall_s=rec["wall_s"])
            return result, out
    rec.update(status="failed", reason="exit %d: %s" % (done.returncode, last_line(out)))
    return rec, out


def last_line(text):
    lines = [l for l in text.splitlines() if l.strip() and not l.startswith("Picked up")]
    return lines[-1].strip()[:200] if lines else "no output"


def tail_text(*parts):
    text = ""
    for p in parts:
        if p:
            text += p.decode("utf-8", "replace") if isinstance(p, bytes) else p
    return text


def fmt(v, spec="%.0f"):
    return "-" if v is None else spec % v


def table(rows):
    cols = ["stack", "transport", "kind", "payload", "callers", "calls/ms", "p50 us", "p99 us", "p99.9 us", "max us",
            "cli thr", "srv thr", "cli RSS MB", "srv RSS MB", "errors", "status", "mean L us", "callers done"]
    body = []
    for r in rows:
        if r.get("status") != "ok":
            body.append([r["stack"], r["transport"], r["kind"], r["payload"], str(r["callers_requested"])] +
                        ["-"] * 10 + ["FAILED: " + r.get("reason", "")[:60], "-", "-"])
            continue
        started, asked = r.get("callers_started"), r["callers_requested"]
        callers = str(asked) if started == asked else "%s of %s" % (started, asked)
        mb = lambda kb: None if kb is None else kb / 1024.0
        body.append([r["stack"], r["transport"], r["kind"], r["payload"], callers, fmt(r["calls_per_ms"], "%.2f"),
                     fmt(r["p50_us"], "%.1f"), fmt(r["p99_us"], "%.1f"), fmt(r["p999_us"], "%.1f"),
                     fmt(r["max_us"], "%.0f"), fmt(r.get("client_threads")), fmt(r.get("server_threads")),
                     fmt(mb(r.get("client_rss_kb"))), fmt(mb(r.get("server_rss_kb"))), str(r.get("errors", 0)), "ok",
                     fmt(r.get("mean_little_us"), "%.1f"),
                     "-" if r.get("callers_completed") is None else "%d" % r["callers_completed"]])
    widths = [max(len(cols[i]), *(len(b[i]) for b in body)) if body else len(cols[i]) for i in range(len(cols))]
    lines = ["  ".join(cols[i].ljust(widths[i]) if i < 4 else cols[i].rjust(widths[i]) for i in range(len(cols)))]
    for b in body:
        lines.append("  ".join(b[i].ljust(widths[i]) if i < 4 else b[i].rjust(widths[i]) for i in range(len(cols))))
    return "\n".join(lines)


def environment(args, java_version, combo_list):
    lines = ["cores: %d" % (os.cpu_count() or 1), "os: %s %s" % (platform.system(), platform.release()),
             "machine: %s" % platform.machine(), "java: %s" % java_version.replace("\n", " | "),
             "jvm args: %s" % (args.jvm_args or "(none)"),
             "stacks/transports: %s" % ", ".join("%s/%s" % c for c in combo_list),
             "callers: %s" % args.callers, "kinds: %s" % args.kinds, "payloads: %s" % args.payloads,
             "prime: %d" % args.prime,
             "mode: %s" % ("open loop, %s calls/s" % args.rate if float(args.rate) > 0 else "closed loop"),
             "settings: warmup=%ss measure=%ss timeout=%ss server=%s" % (
                 args.warmup, args.measure, args.timeout, "shared" if args.shared_server else "fresh per point")]
    try:
        import resource                                   # Linux/Unix only
        lines.append("ulimit -n: %s" % (resource.getrlimit(resource.RLIMIT_NOFILE),))
        lines.append("ulimit -u: %s" % (resource.getrlimit(resource.RLIMIT_NPROC),))
    except (ImportError, AttributeError, ValueError, OSError):
        lines.append("ulimit: n/a on this OS")
    try:
        with open("/proc/meminfo", encoding="utf-8") as f:
            lines.append(f.readline().strip())
    except OSError:
        pass
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--jar", default=DEFAULT_JAR)
    ap.add_argument("--callers", default="1,8,64,512,4096", help="comma-separated caller counts (default 1,8,64,512,4096)")
    ap.add_argument("--stacks", default="DER,JOSS,RMI", help="comma-separated: DER, JOSS, RMI")
    ap.add_argument("--transports", default="TCP,UDS,TLS",
                    help="comma-separated: TCP, UDS, TLS (RMI: TCP means PLAIN; no UDS). PLAIN is accepted for TCP.")
    ap.add_argument("--kinds", default="platform,virtual", help="caller threads: platform, virtual, or both")
    ap.add_argument("--payloads", default="ping", help="ping, record, or both (default ping)")
    ap.add_argument("--warmup", type=float, default=5, help="warm-up seconds per point (default 5)")
    ap.add_argument("--measure", type=float, default=8, help="measured seconds per point (default 8)")
    ap.add_argument("--prime", type=int, default=0,
                    help="make this many calls before the callers start (1: one connection exists when they start; "
                         "default 0 = a cold start, every caller finds none); see ConcurrencyCurve")
    ap.add_argument("--rate", default="0", help="0 (default) = closed loop; R = open loop at R calls per second in all")
    ap.add_argument("--timeout", type=int, help="seconds before a client JVM is killed and the point recorded as "
                    "failed (default warm-up + measure + 120)")
    ap.add_argument("--jvm-args", default="", help="extra JVM arguments for client and server JVMs, e.g. "
                    "'-Xmx8g -XX:+UseParallelGC -Djdk.virtualThreadScheduler.parallelism=8'")
    ap.add_argument("--shared-server", action="store_true",
                    help="one server JVM for the whole sweep instead of a fresh one per point (faster, but server "
                         "threads and memory then carry over from earlier points)")
    args = ap.parse_args()
    if not os.path.exists(args.jar):
        sys.exit("missing %s: run 'mvn -f pom.xml package' in this directory first" % args.jar)
    if args.timeout is None:
        args.timeout = int(args.warmup + args.measure + 120)
    callers_list = [int(x) for x in csv(args.callers)]
    stacks = [s.upper() for s in csv(args.stacks)]
    kinds = [k.lower() for k in csv(args.kinds)]
    payloads = [p.lower() for p in csv(args.payloads)]
    combo_list = combos(stacks, csv(args.transports))
    jvm_args = args.jvm_args.split() if args.jvm_args else []
    os.makedirs(args.out, exist_ok=True)
    version = "\n".join(l for l in subprocess.run([java_cmd(), "-version"], capture_output=True, text=True).stderr.splitlines()
                        if not l.startswith("Picked up"))
    env = environment(args, version, combo_list)
    with open(os.path.join(args.out, "environment.txt"), "w", encoding="utf-8") as f:
        f.write(env)
    print(env)

    tls_dir = None
    if any(t == "TLS" for _, t in combo_list):
        tls_dir = tempfile.mkdtemp(prefix="bench-tls-")
        subprocess.run([java_cmd(), "-cp", args.jar, "au.net.zeus.jgdms.bench.serial.TlsFixture", "generate", tls_dir],
                       check=True)
    jsonl = os.path.join(args.out, "results.jsonl")
    open(jsonl, "w", encoding="utf-8").close()
    rows = []
    shared = None
    total = len(combo_list) * len(kinds) * len(payloads) * len(callers_list)
    done_n = 0
    try:
        if args.shared_server:
            shared = Server(args.jar, jvm_args, tls_dir)
            if not shared.wait_ready():
                sys.exit("the server JVM did not become ready:\n" + "\n".join(shared.lines[-10:]))
        for stack, transport in combo_list:
            for payload in payloads:
                for kind in kinds:
                    for callers in callers_list:
                        done_n += 1
                        server = shared
                        if server is None:
                            server = Server(args.jar, jvm_args, tls_dir)
                        label = "[%d/%d] %s %s %s %s x%d" % (done_n, total, stack, transport, payload, kind, callers)
                        try:
                            if not server.wait_ready():
                                if not any(r.get("status") == "ok" for r in rows):
                                    # the same server runs every point: failing before any point has worked means the
                                    # set-up is wrong, so say so now instead of failing all the points one by one
                                    server.stop()
                                    sys.exit("the server JVM did not become ready (%s). Its last output:\n%s\n\n"
                                             "Rebuild the jar after updating the modules it bundles (for example "
                                             "'mvn -q -pl jgdms-rmi-tls install -DskipTests' and then 'mvn -q package "
                                             "-DskipTests' in this directory) and run again." % (
                                                 server.state or "timeout", "\n".join(server.lines[-20:])))
                                rec = {"stack": stack, "transport": transport, "payload": payload, "kind": kind,
                                       "callers_requested": callers, "status": "failed",
                                       "reason": "server JVM %s: %s" % (server.state or "timeout", last_line("\n".join(server.lines)))}
                                out = ""
                            else:
                                rec, out = run_point(args.jar, jvm_args, server, tls_dir, stack, transport, payload,
                                                     kind, callers, args)
                        finally:
                            if shared is None:
                                server.stop()
                        rows.append(rec)
                        with open(jsonl, "a", encoding="utf-8") as f:
                            f.write(json.dumps(rec) + "\n")
                        if rec["status"] == "ok":
                            print("%s: %.2f calls/ms, p50 %.0f us, p99 %.0f us, p99.9 %.0f us, mean(L) %s us, "
                                  "callers done %s of %s, errors %s%s" % (
                                label, rec["calls_per_ms"], rec["p50_us"], rec["p99_us"], rec["p999_us"],
                                fmt(rec.get("mean_little_us")), rec.get("callers_completed", "-"),
                                rec["callers_started"], rec["errors"],
                                "" if rec["callers_started"] == callers else ", ONLY %d STARTED" % rec["callers_started"]),
                                flush=True)
                        else:
                            print("%s: FAILED (%s)" % (label, rec["reason"]), flush=True)
    except KeyboardInterrupt:
        print("\ninterrupted: writing the summary of the %d points run so far" % len(rows), flush=True)
    finally:
        if shared is not None:
            shared.stop()
    text = table(rows)
    note = ("\nLatency is per call, in microseconds, over the measured window; 'mean L us' is the mean per call by "
            "Little's law, callers / throughput (closed loop only), and 'callers done' the number of callers that "
            "completed at least one call in the window (fewer than the callers = some were starved); "
            "threads are OS threads of the JVM "
            "(Linux /proc), RSS the largest resident set sampled in the window. '-' = not available. " +
            ("Open loop at %s calls/s: latency runs from each call's intended start.\n" % args.rate if float(args.rate) > 0 else
             "CLOSED LOOP: under saturation the percentiles are subject to coordinated omission.\n"))
    for r in rows:
        if r.get("status") == "ok" and r.get("errors"):
            note += "errors at %s %s %s %s x%d: %d, first: %s\n" % (r["stack"], r["transport"], r["kind"], r["payload"],
                                                                r["callers_requested"], r["errors"], r.get("first_error"))
    with open(os.path.join(args.out, "summary.txt"), "w", encoding="utf-8") as f:
        f.write(text + "\n" + note)
    print()
    print(text + "\n" + note)


if __name__ == "__main__":
    main()
