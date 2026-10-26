#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Where the CPU and the waiting go at N concurrent callers: one ConcurrencyCurve point (a fresh ServerMain JVM and a
# fresh client JVM, as concurrency-curve.py runs it) with an external sampler on the two JVMs (Linux /proc only).
# Inside the measured window it takes, for the client and for the server JVM, a snapshot of every thread before and
# after a sampling interval, and reports per thread group (the thread name with its digits replaced by #):
#   cpu    CPU used, as a share of one core (schedstat, user + system)
#   wait   time spent runnable on a run queue, as a share of one core (schedstat; > 0 means more runnable threads than cores)
#   vol/s, invol/s   voluntary and involuntary context switches per second
# plus the whole machine (/proc/stat: user, system, idle, iowait, irq, softirq, steal, context switches per second, the
# mean number of runnable threads from procs_running, sampled 10 times) and the calls/ms of the point itself.
# Optionally, in the same run: --dump takes jcmd Thread.print of the client and the server JVM near the end of the window and
# groups the threads by state and top frames; --strace client|server runs `strace -c -f` on that JVM for the interval
# (it slows the run down a lot, so use it for system call counts per call, not for throughput).
#
#   python3 collapse-probe.py --out probe-x --callers 512 --kind platform --stack DER --transport UDS
#   python3 collapse-probe.py --out probe-x --callers 512 --kind virtual --jvm-args '-Djdk.virtualThreadScheduler.parallelism=4'
#   python3 collapse-probe.py --out probe-x --callers 64 --dump --client-jvm-args '-Xlog:gc:file=probe-x/gc-client.log'
#
# JAVA_HOME or java on the PATH; the jar is the shaded benchmark jar (see concurrency-curve.py). Output: the text report
# on standard output and <out>/probe.txt, <out>/probe.json, <out>/client.out, <out>/dump-*.txt.
# Written with AI assistance (Claude); see the README.
import argparse
import collections
import importlib.util
import json
import os
import re
import subprocess
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("concurrency_curve", os.path.join(HERE, "concurrency-curve.py"))
cc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cc)

HZ = os.sysconf("SC_CLK_TCK")


def group(name):
    return re.sub(r"[0-9]+", "#", name)


def snap_threads(pid):
    """{tid: (group, cpu_ns, wait_ns, vol, invol, state)}"""
    out = {}
    base = "/proc/%d/task" % pid
    try:
        tids = os.listdir(base)
    except OSError:
        return out
    for t in tids:
        try:
            with open("%s/%s/comm" % (base, t)) as f:
                name = f.read().strip()
            with open("%s/%s/schedstat" % (base, t)) as f:
                s = f.read().split()
            with open("%s/%s/status" % (base, t)) as f:
                st = f.read()
        except OSError:
            continue
        vol = invol = 0
        state = "?"
        for line in st.splitlines():
            if line.startswith("voluntary_ctxt_switches:"):
                vol = int(line.split()[1])
            elif line.startswith("nonvoluntary_ctxt_switches:"):
                invol = int(line.split()[1])
            elif line.startswith("State:"):
                state = line.split()[1]
        out[t] = (group(name), int(s[0]), int(s[1]), vol, invol, state)
    return out


def proc_stat():
    d = {}
    with open("/proc/stat") as f:
        for line in f:
            p = line.split()
            if p[0] == "cpu":
                d["cpu"] = [int(x) for x in p[1:]]
            elif p[0] in ("ctxt", "intr", "procs_running", "procs_blocked", "softirq"):
                d[p[0]] = int(p[1])
    return d


def table_for(label, s0, s1, dt):
    """Per group: threads, cpu, wait, vol/s, invol/s over the interval; threads that appear or vanish count from their own snapshots."""
    g = collections.defaultdict(lambda: [0, 0.0, 0.0, 0.0, 0.0])
    for tid, (name, cpu1, w1, v1, i1, _) in s1.items():
        a = s0.get(tid)
        c0, w0, v0, i0 = (a[1], a[2], a[3], a[4]) if a else (0, 0, 0, 0)
        r = g[name]
        r[0] += 1
        r[1] += (cpu1 - c0) / 1e9 / dt
        r[2] += (w1 - w0) / 1e9 / dt
        r[3] += (v1 - v0) / dt
        r[4] += (i1 - i0) / dt
    tot = [sum(r[i] for r in g.values()) for i in range(5)]
    lines = ["%s: threads %d, cpu %.2f cores, runnable-wait %.2f cores, voluntary %.0f/s, involuntary %.0f/s" % (
        label, tot[0], tot[1], tot[2], tot[3], tot[4])]
    lines.append("  %-30s %7s %8s %8s %10s %10s" % ("thread group", "threads", "cpu", "wait", "vol/s", "invol/s"))
    for name, r in sorted(g.items(), key=lambda kv: -kv[1][1])[:12]:
        lines.append("  %-30s %7d %7.0f%% %7.0f%% %10.0f %10.0f" % (name[:30], r[0], r[1] * 100, r[2] * 100, r[3], r[4]))
    return lines, {"threads": tot[0], "cpu_cores": tot[1], "wait_cores": tot[2], "vol_per_s": tot[3],
                   "invol_per_s": tot[4],
                   "groups": {n: {"threads": r[0], "cpu_cores": r[1], "wait_cores": r[2], "vol_per_s": r[3],
                                  "invol_per_s": r[4]} for n, r in g.items()}}


def states(s):
    c = collections.Counter(v[5] for v in s.values())
    return c


def dump_threads(pid, path, label):
    """jcmd Thread.print, grouped by thread-name group, state and the top two frames."""
    jcmd = os.path.join(os.environ["JAVA_HOME"], "bin", "jcmd") if os.environ.get("JAVA_HOME") else "jcmd"
    try:
        r = subprocess.run([jcmd, str(pid), "Thread.print"], capture_output=True, text=True, timeout=60)
    except Exception as e:                                     # noqa
        return ["%s: jcmd failed: %s" % (label, e)]
    with open(path, "w") as f:
        f.write(r.stdout)
    blocks = r.stdout.split("\n\n")
    counts = collections.Counter()
    for b in blocks:
        lines = [l for l in b.splitlines() if l.strip()]
        if not lines or not lines[0].startswith('"'):
            continue
        name = re.match(r'"([^"]*)"', lines[0]).group(1)
        st = re.search(r"java.lang.Thread.State: (\S+)", b)
        frames = [l.strip()[3:] for l in lines if l.strip().startswith("at ")]
        top = " < ".join(re.sub(r"\(.*", "", fr) for fr in frames[:3])
        counts[(group(name), st.group(1) if st else "?", top)] += 1
    out = ["%s: %d threads in Thread.print (saved in %s)" % (label, sum(counts.values()), os.path.basename(path))]
    for (name, st, top), n in counts.most_common(14):
        out.append("  %5d  %-26s %-14s %s" % (n, name[:26], st, top[:150]))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--jar", default=cc.DEFAULT_JAR)
    ap.add_argument("--stack", default="DER")
    ap.add_argument("--transport", default="UDS")
    ap.add_argument("--payload", default="ping")
    ap.add_argument("--callers", type=int, default=64)
    ap.add_argument("--kind", default="platform")
    ap.add_argument("--warmup", type=float, default=4)
    ap.add_argument("--measure", type=float, default=12)
    ap.add_argument("--start", type=float, default=2.0, help="seconds into the window where sampling starts (default 2)")
    ap.add_argument("--interval", type=float, default=6.0, help="sampling interval in seconds (default 6)")
    ap.add_argument("--jvm-args", default="", help="JVM arguments for both JVMs")
    ap.add_argument("--client-jvm-args", default="", help="JVM arguments for the client JVM only")
    ap.add_argument("--server-jvm-args", default="", help="JVM arguments for the server JVM only")
    ap.add_argument("--prime", type=int, default=0, help="K calls before the callers start, concurrent if K > 1, so about K "
                    "connections exist (ConcurrencyCurve prime=K; default 0 = a cold burst of one connection per caller)")
    ap.add_argument("--light", action="store_true", help="no thread-state samples in the interval (only the two snapshots)")
    ap.add_argument("--jfr", action="store_true", help="record the client JVM with JFR (monitor enters from 0 ms, execution samples "
                    "every 10 ms) and dump <out>/client.jfr near the end of the window")
    ap.add_argument("--dump", action="store_true", help="jcmd Thread.print of both JVMs near the end of the window")
    ap.add_argument("--strace", choices=["client", "server"], help="strace -c -f one JVM during the interval")
    ap.add_argument("--strace-fd", action="store_true",
                    help="with --strace: instead of the summary table, log write/read-type calls and report how they spread over file descriptors")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    jv = args.jvm_args.split()
    tls_dir = None
    if args.transport.upper() == "TLS":
        import tempfile
        tls_dir = tempfile.mkdtemp(prefix="bench-tls-")
        subprocess.run([cc.java_cmd(), "-cp", args.jar, "au.net.zeus.jgdms.bench.serial.TlsFixture", "generate", tls_dir], check=True)
    server = cc.Server(args.jar, jv + args.server_jvm_args.split(), tls_dir)
    if not server.wait_ready():
        sys.exit("server not ready: " + "\n".join(server.lines[-10:]))
    jfr_opt = ["-XX:StartFlightRecording=name=probe,settings=profile,jdk.JavaMonitorEnter#threshold=0ms,"
               "jdk.ExecutionSample#period=10ms"] if args.jfr else []
    cmd = [cc.java_cmd()] + jv + args.client_jvm_args.split() + jfr_opt + (["-Dbench.tls.dir=" + tls_dir] if tls_dir else []) + [
        "-Dthreadprofile.server=" + server.dir, "-Dthreadprofile.serverpid=" + str(server.pid), "-cp", args.jar,
        cc.CLIENT_CLASS, "stack=" + args.stack, "transport=" + args.transport, "payload=" + args.payload,
        "callers=%d" % args.callers, "kind=" + args.kind, "warmup=%s" % args.warmup, "measure=%s" % args.measure, "prime=%d" % args.prime]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    t_start = [None]
    lines = []

    def reader():
        for line in proc.stdout:
            lines.append(line)
            if t_start[0] is None and line.startswith("ConcurrencyCurve "):
                t_start[0] = time.time()
    th = threading.Thread(target=reader, daemon=True)
    th.start()
    while t_start[0] is None and proc.poll() is None:
        time.sleep(0.05)
    if t_start[0] is None:
        sys.exit("client failed: " + "".join(lines[-10:]))
    win0 = t_start[0] + args.warmup               # the window starts about here (the callers start just after the header)
    report = []
    time.sleep(max(0, win0 + args.start - time.time()))
    cpid, spid = proc.pid, server.pid
    c0, s0, m0 = snap_threads(cpid), snap_threads(spid), proc_stat()
    ts0 = time.time()
    self0 = time.process_time()
    strace_proc = None
    if args.strace:
        target = cpid if args.strace == "client" else spid
        sflags = ["-e", "trace=write,writev,sendto,sendmsg,read,readv,recvfrom,recvmsg"] if args.strace_fd else ["-c"]
        strace_proc = subprocess.Popen(["strace"] + sflags + ["-f", "-p", str(target), "-o",
                                        os.path.join(args.out, "strace-%s.txt" % args.strace)],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    running = []
    state_c, state_s = collections.Counter(), collections.Counter()
    nsamp = 10
    for i in range(nsamp):
        time.sleep(max(0, ts0 + args.interval * (i + 0.5) / nsamp - time.time()))
        running.append(proc_stat().get("procs_running", 0))
        if not args.light:
            state_c.update(states(snap_threads(cpid)))
            state_s.update(states(snap_threads(spid)))
    time.sleep(max(0, ts0 + args.interval - time.time()))
    c1, s1, m1 = snap_threads(cpid), snap_threads(spid), proc_stat()
    dt = time.time() - ts0
    self_cores = (time.process_time() - self0) / dt     # this sampler's own CPU, part of the machine figures
    if strace_proc:
        strace_proc.terminate()
        strace_proc.wait()
    dump_lines = []
    if args.strace and args.strace_fd:
        per = collections.defaultdict(collections.Counter)         # call kind -> fd -> count
        try:
            with open(os.path.join(args.out, "strace-%s.txt" % args.strace)) as f:
                for line in f:
                    m = re.match(r"(?:\[pid\s+\d+\]\s+|\d+\s+)?(\w+)\((\d+),", line)
                    if m and "resumed" not in line:
                        kind = "write" if m.group(1) in ("write", "writev", "sendto", "sendmsg") else "read"
                        per[kind][int(m.group(2))] += 1
        except OSError:
            pass
        for kind in ("write", "read"):
            c = per[kind]
            tot = sum(c.values())
            if tot:
                top = c.most_common(5)
                over1 = sum(1 for v in c.values() if v >= 0.01 * tot)
                dump_lines.append("strace %s %s: %d %s calls on %d descriptors; top 5 hold %.0f%% (%s); %d descriptors have >= 1%% each" % (
                    args.strace, "fd traffic", tot, kind, len(c), 100.0 * sum(v for _, v in top) / tot,
                    ", ".join("fd %d %.0f%%" % (fd, 100.0 * v / tot) for fd, v in top), over1))
    if args.jfr:
        time.sleep(max(0, win0 + args.measure - 2.5 - time.time()))
        jcmd = os.path.join(os.environ["JAVA_HOME"], "bin", "jcmd") if os.environ.get("JAVA_HOME") else "jcmd"
        subprocess.run([jcmd, str(cpid), "JFR.dump", "name=probe", "filename=" + os.path.abspath(os.path.join(args.out, "client.jfr"))],
                       capture_output=True, text=True, timeout=120)
    if args.dump:
        time.sleep(max(0, win0 + args.measure - 1.5 - time.time()))
        dump_lines += dump_threads(cpid, os.path.join(args.out, "dump-client.txt"), "client")
        dump_lines += dump_threads(spid, os.path.join(args.out, "dump-server.txt"), "server")
    proc.wait(timeout=args.measure + 120)
    th.join(5)
    server.stop()
    text = "".join(lines)
    with open(os.path.join(args.out, "client.out"), "w") as f:
        f.write(text)
    result = None
    for l in lines:
        if l.startswith("curve-json: "):
            result = json.loads(l[len("curve-json: "):])
    a, b = m0["cpu"], m1["cpu"]
    d = [y - x for x, y in zip(a, b)]
    tot = float(sum(d[:8])) or 1.0                     # user nice system idle iowait irq softirq steal
    names = ["user", "nice", "system", "idle", "iowait", "irq", "softirq", "steal"]
    machine = ", ".join("%s %.1f%%" % (n, 100.0 * x / tot) for n, x in zip(names, d[:8]) if n in ("user", "system", "idle", "iowait", "softirq", "steal"))
    report.append("%s %s %s %s x%d (warm-up %gs, window %gs, sampled %.1fs from +%.1fs)" % (
        args.stack, args.transport, args.payload, args.kind, args.callers, args.warmup, args.measure, dt, args.start))
    if result:
        report.append("point: %.2f calls/ms, p50 %.0f us, p99 %.0f us, errors %s, client sockets %s, server sockets %s, "
                      "client cpu %s us/call, server cpu %s us/call, client RSS %s MB" % (
                          result["calls_per_ms"], result["p50_us"], result["p99_us"], result["errors"],
                          result.get("client_sockets"), result.get("server_sockets"),
                          result.get("client_cpu_us_per_call"), result.get("server_cpu_us_per_call"),
                          (result.get("client_rss_kb") or 0) // 1024))
    report.append("sampler itself: %.2f cores" % self_cores)
    report.append("machine: %s; context switches %.0f/s; runnable threads (procs_running) mean %.1f max %d" % (
        machine, (m1["ctxt"] - m0["ctxt"]) / dt, sum(running) / float(len(running)), max(running)))
    tl, cj = table_for("client JVM", c0, c1, dt)
    report += tl
    report.append("  client thread states over %d samples (mean count): %s" % (
        nsamp, ", ".join("%s %.0f" % (k, v / float(nsamp)) for k, v in sorted(state_c.items()))))
    tl, sj = table_for("server JVM", s0, s1, dt)
    report += tl
    report.append("  server thread states over %d samples (mean count): %s" % (
        nsamp, ", ".join("%s %.0f" % (k, v / float(nsamp)) for k, v in sorted(state_s.items()))))
    report += dump_lines
    out = "\n".join(report)
    print(out)
    with open(os.path.join(args.out, "probe.txt"), "w") as f:
        f.write(out + "\n")
    with open(os.path.join(args.out, "probe.json"), "w") as f:
        json.dump({"args": vars(args), "point": result, "client": cj, "server": sj,
                   "machine": machine, "runnable_mean": sum(running) / float(len(running))}, f)


if __name__ == "__main__":
    main()
