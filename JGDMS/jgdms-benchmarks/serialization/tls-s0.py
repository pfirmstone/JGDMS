#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Measures the TLS accept path of a JERI listener (RESULTS.md, thirty-sixth section): new connections a second, the latency
# of opening one, what the listener costs in threads and memory, what silent and junk peers do to a legitimate open, and what
# a storm of new connections does to calls on connections that already exist. Run it once for each build to compare, each
# time with that build's shaded jar (--cp); every point starts a fresh server JVM (au.net.zeus.jgdms.bench.serial.ServerMain),
# so its threads and memory belong to that point alone. Linux only (/proc, kernel ListenOverflows).
#
#   python3 tls-s0.py accept    --cp target/serialization-benchmarks.jar --threads 1,64,512 --reps 3 --out accept.jsonl
#   python3 tls-s0.py adversary --cp target/serialization-benchmarks.jar --reps 3 --out adversary.jsonl
#   python3 tls-s0.py storm     --cp target/serialization-benchmarks.jar --reps 3 --out storm.jsonl
#
#   accept     TlsAcceptRate: N raw SSLSocket client threads that connect, handshake, exchange the mux headers and close,
#              resumed and full; accepted connections a second, p50/p99/max connect-to-mux-header, failures, kernel
#              ListenOverflows, and the server's peak thread count, peak resident memory and heap in use after the run.
#   adversary  TlsAdversary: K silent and J junk peers, then M legitimate opens timed.
#   storm      ConcurrencyCurve (DER ping over TLS, open loop, 8 callers) against the server with and without a concurrent
#              TlsAcceptRate storm of full handshakes; the call latency percentiles.
#
# The client JVMs share the machine with the server, so every figure is for comparing builds on the same machine.
# Written with AI assistance (Claude); see the README.
import argparse, json, os, re, subprocess, sys, tempfile, threading, time

PKG = "au.net.zeus.jgdms.bench.serial."


def java():
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", "java") if home else "java"


def jcmd():
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", "jcmd") if home else "jcmd"


def listen_overflows():
    try:
        lines = [l.split() for l in open("/proc/net/netstat") if l.startswith("TcpExt:")]
        return int(lines[1][lines[0].index("ListenOverflows")])
    except Exception:
        return -1


def proc_status(pid):
    try:
        t = open("/proc/%d/status" % pid).read()
        return int(re.search(r"Threads:\s+(\d+)", t).group(1)), int(re.search(r"VmRSS:\s+(\d+)", t).group(1))
    except Exception:
        return -1, -1


def cpu_ns(pid):
    total = 0
    for t in os.listdir("/proc/%d/task" % pid):
        try:
            total += int(open("/proc/%d/task/%s/schedstat" % (pid, t)).read().split()[0])
        except Exception:
            pass
    return total


class Server:
    def __init__(self, cp, work, both):
        self.work = work
        self.tls = os.path.join(work, "tls")
        if not os.path.exists(os.path.join(self.tls, "truststore.p12")):
            subprocess.run([java(), "-cp", cp, PKG + "TlsFixture", "generate", self.tls], check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.dir = os.path.join(work, "srv")
        log = open(os.path.join(work, "srv.log"), "w")
        cmd = [java()] + both + ["-Dbench.tls.dir=" + self.tls, "-cp", cp, PKG + "ServerMain", self.dir]
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        for _ in range(120):
            time.sleep(1)
            if "ready" in open(os.path.join(work, "srv.log")).read():
                break
            if self.proc.poll() is not None:
                sys.exit("server did not start: see " + os.path.join(work, "srv.log"))
        self.peak_threads = 0
        self.peak_rss = 0
        self._stop = False
        self._t = threading.Thread(target=self._sample, daemon=True)
        self._t.start()

    @property
    def pid(self):
        return self.proc.pid

    def _sample(self):
        while not self._stop:
            th, rss = proc_status(self.pid)
            self.peak_threads = max(self.peak_threads, th)
            self.peak_rss = max(self.peak_rss, rss)
            time.sleep(0.25)

    def reset_peaks(self):
        self.peak_threads, self.peak_rss = proc_status(self.pid)

    def heap_used_mb(self):
        """Heap in use after a full collection, from jcmd."""
        try:
            subprocess.run([jcmd(), str(self.pid), "GC.run"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
            out = subprocess.run([jcmd(), str(self.pid), "GC.heap_info"], capture_output=True, text=True, timeout=60).stdout
            m = re.search(r"used (\d+)K", out)
            return int(m.group(1)) / 1024.0 if m else -1
        except Exception:
            return -1

    def stop(self):
        self._stop = True
        try:
            self.proc.stdin.close()
            self.proc.wait(60)
        except Exception:
            self.proc.kill()


def client(cp, server, both, cls, args, extra=None):
    cmd = [java()] + both + ["-Dbench.tls.dir=" + server.tls] + (extra or []) + ["-cp", cp, PKG + cls] + args
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def emit(out, rec):
    line = json.dumps(rec)
    print(line, flush=True)
    if out:
        open(out, "a").write(line + "\n")


def cmd_accept(a):
    pat = re.compile(r"(\w+), (\d+) threads: (\d+) connections in ([\d.]+) s = (\d+) per second; failures (\d+); latency \(connect to mux header\) p50 ([\d.]+) ms p99 ([\d.]+) ms max ([\d.]+) ms")
    for threads in a.threads.split(","):
        for mode in a.modes.split(","):
            for rep in range(a.reps):
                work = tempfile.mkdtemp(prefix="tls-s0-accept-")
                srv = Server(a.cp, work, a.both)
                try:
                    cpu0, o0, th0 = cpu_ns(srv.pid), listen_overflows(), proc_status(srv.pid)[0]
                    srv.reset_peaks()
                    out = client(a.cp, srv, a.both, "TlsAcceptRate", [srv.dir, threads, str(a.seconds), mode, str(a.warmup)]).stdout
                    cpu1, o1 = cpu_ns(srv.pid), listen_overflows()
                    m = pat.search(out)
                    rec = {"what": "accept", "build": a.label, "mode": mode, "connectors": int(threads), "rep": rep}
                    if m:
                        rec.update(per_second=int(m.group(5)), connections=int(m.group(3)), failures=int(m.group(6)),
                                   p50_ms=float(m.group(7)), p99_ms=float(m.group(8)), max_ms=float(m.group(9)))
                    else:
                        rec["error"] = out[-300:]
                    wall = a.seconds + a.warmup
                    rec.update(listen_overflows=o1 - o0, server_cpu_cores=round((cpu1 - cpu0) / 1e9 / wall, 2),
                               server_threads_before=th0, server_threads_peak=srv.peak_threads,
                               server_rss_peak_mb=round(srv.peak_rss / 1024.0, 1), server_heap_used_mb=round(srv.heap_used_mb(), 1))
                    emit(a.out, rec)
                finally:
                    srv.stop()


def cmd_adversary(a):
    pat = re.compile(r"adversary: (\d+) silent, (\d+) junk.*?: (\d+) legitimate opens in ([\d.]+) s, (\d+) failed; open p50 ([\d.]+) ms p99 ([\d.]+) ms max ([\d.]+) ms")
    scenarios = []
    for s in a.scenarios.split(";"):
        silent, junk, every, opens, timeout = (s.split(",") + ["0", "0", "0", "20", "30000"][len(s.split(",")):])[:5]
        scenarios.append((int(silent), int(junk), int(every), int(opens), int(timeout)))
    for silent, junk, every, opens, timeout in scenarios:
        for rep in range(a.reps):
            work = tempfile.mkdtemp(prefix="tls-s0-adv-")
            srv = Server(a.cp, work, a.both)
            try:
                srv.reset_peaks()
                args = [srv.dir, "silent=%d" % silent, "junk=%d" % junk, "junkevery=%d" % every, "opens=%d" % opens, "timeout=%d" % timeout]
                out = client(a.cp, srv, a.both, "TlsAdversary", args).stdout
                m = pat.search(out)
                rec = {"what": "adversary", "build": a.label, "silent": silent, "junk": junk, "junk_every_ms": every, "opens": opens, "timeout_ms": timeout, "rep": rep}
                if m:
                    rec.update(wall_s=float(m.group(4)), failed=int(m.group(5)), p50_ms=float(m.group(6)), p99_ms=float(m.group(7)), max_ms=float(m.group(8)))
                else:
                    rec["error"] = out[-300:]
                rec.update(server_threads_peak=srv.peak_threads, server_rss_peak_mb=round(srv.peak_rss / 1024.0, 1))
                emit(a.out, rec)
            finally:
                srv.stop()


def cmd_storm(a):
    for storm in ((True,) if a.storm_only else (False, True)):
        for rep in range(a.reps):
            work = tempfile.mkdtemp(prefix="tls-s0-storm-")
            srv = Server(a.cp, work, a.both)
            try:
                warm, measure = 5, a.seconds
                call = [java()] + a.both + ["-Dbench.tls.dir=" + srv.tls, "-Dthreadprofile.server=" + srv.dir, "-Dthreadprofile.serverpid=%d" % srv.pid,
                        "-cp", a.cp, PKG + "ConcurrencyCurve", "stack=DER", "transport=TLS", "payload=ping", "callers=%d" % a.callers, "kind=platform",
                        "warmup=%d" % warm, "measure=%d" % measure, "rate=%d" % a.rate]
                p = subprocess.Popen(call, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                storm_out = None
                if storm:
                    time.sleep(warm - 2)
                    # starts 3 s into the callers' warm-up and covers the measured window
                    if a.storm_rate > 0:   # a paced storm: the same offered load for every build
                        sargs = ["TlsStorm", srv.dir, str(a.storm_threads), str(measure + 2), a.storm_mode, str(a.storm_rate)]
                    else:                  # as fast as the listener lets it
                        sargs = ["TlsAcceptRate", srv.dir, str(a.storm_threads), str(measure + 2), a.storm_mode, "2"]
                    sp = subprocess.Popen([java()] + a.both + ["-Dbench.tls.dir=" + srv.tls, "-cp", a.cp, PKG + sargs[0]] + sargs[1:],
                                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                out = p.communicate(timeout=warm + measure + 120)[0]
                if storm:
                    storm_out = sp.communicate(timeout=120)[0]
                rec = {"what": "storm", "build": a.label, "storm": storm, "storm_threads": a.storm_threads if storm else 0, "storm_rate": a.storm_rate if storm else 0, "rep": rep, "rate": a.rate, "callers": a.callers}
                for line in out.splitlines():
                    if line.startswith("curve-json: "):
                        j = json.loads(line[len("curve-json: "):])
                        rec.update({k: j.get(k) for k in ("calls", "errors", "missed", "p50_us", "p90_us", "p99_us", "p999_us", "max_us", "calls_per_ms")})
                if storm_out:
                    m = re.search(r"= (\d+) per second; failures (\d+)", storm_out)
                    if m:
                        rec.update(storm_per_second=int(m.group(1)), storm_failures=int(m.group(2)))
                emit(a.out, rec)
            finally:
                srv.stop()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("accept", "adversary", "storm"):
        p = sub.add_parser(name)
        p.add_argument("--cp", default="target/serialization-benchmarks.jar")
        p.add_argument("--label", default="build", help="name of the build, recorded in each result")
        p.add_argument("--both", action="append", default=[], help="a JVM option for every JVM (repeatable)")
        p.add_argument("--reps", type=int, default=3)
        p.add_argument("--out", default=None, help="append one JSON object per point to this file")
        if name == "accept":
            p.add_argument("--threads", default="1,64,512")
            p.add_argument("--modes", default="resumed,full")
            p.add_argument("--seconds", type=int, default=10)
            p.add_argument("--warmup", type=int, default=5)
        elif name == "adversary":
            p.add_argument("--scenarios", default="0,0,0,20,30000;0,12,0,20,30000;0,12,1000,10,30000;1,0,0,2,12000;8,0,0,2,12000;64,0,0,2,12000;600,0,0,3,12000;64,12,1000,10,12000",
                           help="semicolon-separated silent,junk,junkevery_ms,opens,timeout_ms")
        else:
            p.add_argument("--seconds", type=int, default=20)
            p.add_argument("--callers", type=int, default=8)
            p.add_argument("--rate", type=int, default=2000)
            p.add_argument("--storm-threads", type=int, default=64)
            p.add_argument("--storm-mode", default="full")
            p.add_argument("--storm-only", action="store_true", help="skip the run without a storm")
            p.add_argument("--storm-rate", type=int, default=0, help="new connections a second in the storm; 0: as fast as the listener accepts (TlsAcceptRate)")
    a = ap.parse_args()
    {"accept": cmd_accept, "adversary": cmd_adversary, "storm": cmd_storm}[a.cmd](a)


if __name__ == "__main__":
    main()
