/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import javax.security.auth.Subject;

/**
 * Latency distribution and resource use of one proxy/stub called by N concurrent callers, platform or virtual
 * threads, against a server in its own JVM. One run is one point of a concurrency curve; {@code concurrency-curve.py}
 * drives a sweep of them.
 *
 * <p>Usage (every argument is {@code key=value}, or positional in this order):
 * <pre>
 *   java -Dthreadprofile.server=&lt;dir&gt; [-Dthreadprofile.serverpid=&lt;pid&gt;] [-Dbench.tls.dir=&lt;dir&gt;]
 *        -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.ConcurrencyCurve
 *        stack=DER transport=UDS payload=ping callers=64 kind=virtual warmup=5 measure=8 [rate=0] [prime=0]
 *
 *   stack      DER | JOSS | RMI
 *   transport  TCP | UDS | TLS   (RMI: PLAIN or TLS; RMI has no UDS)
 *   payload    ping | record
 *   callers    number of concurrent caller threads (default 1)
 *   kind       platform | virtual   (Thread.ofPlatform() or Thread.ofVirtual(); default platform)
 *   warmup     seconds of warm-up, not measured (default 5)
 *   measure    seconds measured (default 8)
 *   rate       0 (default) = closed loop; otherwise open loop at this many calls per second in total
 *   prime      K = make K calls before the callers start (1: from the main thread; K > 1: K concurrent threads, each of
 *              which finds no connection and opens its own, so about K connections exist when the callers start).
 *              Default 0: all N callers find none and each opens its own, a cold burst. Used by the many-callers
 *              diagnosis to hold the number of connections apart from the number of callers.
 * </pre>
 * The server is a {@code ServerMain <dir>} JVM (with the same {@code -Dbench.tls.dir} for TLS), named by
 * {@code -Dthreadprofile.server=<dir>}; {@code -Dthreadprofile.serverpid=<pid of ServerMain>} adds that JVM's
 * thread, socket, memory and CPU figures. Those need Linux {@code /proc}; elsewhere they print as n/a.
 *
 * <p><b>Closed loop (the default).</b> Each caller makes one call after another, so the offered load falls as
 * the system slows down, and when it saturates the latency percentiles understate what an independent arrival
 * stream would see (coordinated omission). Read the percentiles as "latency while N callers are in flight", and
 * use the open-loop mode ({@code rate=}) for latency at a fixed arrival rate.
 *
 * <p><b>Open loop ({@code rate=R}).</b> Each of the N callers owns an arrival schedule, one call every N/R
 * seconds, staggered across callers. The latency of a call is the time from its intended start to its completion,
 * so a caller that is late because an earlier call was slow charges that delay to the calls behind it (no
 * coordinated omission). N bounds the concurrency: if all N are busy, arrivals queue behind their caller. A
 * caller still working 10 s after the end of the window is abandoned and its remaining arrivals are counted as
 * missed. Timer slop (parkNanos, typically 50 us or more) is included in the latency of calls that had to wait.
 *
 * <p>The latency of every call that completes in the measured window is recorded with {@code System.nanoTime}
 * into a {@link LatencyHistogram} (3 significant digits, 100 ns to 100 s): one per caller up to 64 callers,
 * above that callers share 64 histograms with atomic increments, so memory does not grow with the caller count
 * and does not distort the resident-memory figures. One line {@code curve-json: {...}} per run is for the driver.
 *
 * <p><b>Mean latency and starved callers.</b> The percentiles describe the calls that completed, so callers that get
 * almost no service hardly appear in them: with 4096 callers a p50 of 488 us beside a p99 of 613 ms can mean that most
 * callers completed nothing in the window. Two figures make that visible. {@code mean_little_us} is the mean time a
 * caller spends per call by Little's law, callers / throughput (closed loop only; in an open loop the number of
 * callers is not the concurrency, so it is null). It is not the mean of the recorded latencies ({@code mean_us}),
 * which is over the calls that completed and so is blind to callers that did not. {@code callers_completed} is the
 * number of callers that completed at least one call inside the measured window; below {@code callers_started}
 * the rest were starved for the whole window.
 *
 * <p>Written with AI assistance (Claude); see the README.
 */
public final class ConcurrencyCurve {

    interface Call { void run() throws Exception; }

    private static final int SHARDS = 64;
    private static final long DRAIN_NANOS = 10_000_000_000L;
    private static final List<Object> KEEP = new ArrayList<>();

    private static volatile boolean stop;
    private static volatile long winStart = Long.MAX_VALUE;
    private static volatile long winEnd = Long.MAX_VALUE;
    private static final LongAdder errors = new LongAdder();
    private static final LongAdder missed = new LongAdder();
    private static final LongAdder allDone = new LongAdder();      // every successful call, warm-up included
    private static final LongAdder completedInWindow = new LongAdder();     // open loop only
    /** Per caller: 1 once the caller has completed a call inside the measured window. */
    private static volatile AtomicIntegerArray served = new AtomicIntegerArray(0);
    private static volatile String firstError;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Map<String, String> opt = parse(args);
        String stack = opt.getOrDefault("stack", "DER").toUpperCase(Locale.ROOT);
        String transport = opt.getOrDefault("transport", "TCP").toUpperCase(Locale.ROOT);
        String payload = opt.getOrDefault("payload", "ping").toLowerCase(Locale.ROOT);
        int callers = Integer.parseInt(opt.getOrDefault("callers", "1"));
        String kind = opt.getOrDefault("kind", "platform").toLowerCase(Locale.ROOT);
        double warmup = Double.parseDouble(opt.getOrDefault("warmup", "5"));
        double measure = Double.parseDouble(opt.getOrDefault("measure", "8"));
        double rate = Double.parseDouble(opt.getOrDefault("rate", "0"));
        boolean open = rate > 0;
        if (!kind.equals("platform") && !kind.equals("virtual")) {
            throw new IllegalArgumentException("kind must be platform or virtual");
        }
        String serverDir = System.getProperty("threadprofile.server");
        if (serverDir == null) {
            throw new IllegalArgumentException("start a ServerMain <dir> and pass -Dthreadprofile.server=<dir>");
        }
        String spid = System.getProperty("threadprofile.serverpid");

        Subject client = null;
        Call call;
        if (stack.equals("RMI")) {
            if (transport.equals("UDS")) {
                throw new IllegalArgumentException("RMI has no UDS transport");
            }
            if (transport.equals("TCP")) {
                transport = "PLAIN";
            }
            if (transport.equals("TLS")) {
                client = TlsFixture.current().client;     // before the stub is read: it creates the SSL factories
            }
            RmiEcho stub = (RmiEcho) ServerMain.readStub(serverDir + "/rmi." + transport + ".proxy");
            KEEP.add(stub);
            JdkPayloads.Record r = (JdkPayloads.Record) JdkPayloads.create("record");
            call = payload.equals("ping") ? stub::ping : () -> stub.record(r);
        } else {
            Format f = Format.valueOf(stack);
            Transport t = Transport.valueOf(transport.equals("PLAIN") ? "TCP" : transport);
            client = t.clientSubject();
            Echo p = (Echo) ServerMain.read(f, serverDir + "/" + stack + "." + t + ".proxy");
            KEEP.add(p);
            Payloads.Record r = (Payloads.Record) Payloads.create("record");
            call = payload.equals("ping") ? p::ping : () -> p.record(r);
        }
        final Subject cs = client;
        final Call c = cs == null ? call : () -> Subject.callAs(cs, () -> {
            call.run();
            return null;
        });

        int cores = Runtime.getRuntime().availableProcessors();
        String jdk = System.getProperty("java.version") + " " + System.getProperty("java.vm.name");
        System.out.printf("ConcurrencyCurve %s %s %s, %d %s callers, %.0f s warm-up + %.0f s measured, %d cores, JDK %s%n",
                stack, transport, payload, callers, kind, warmup, measure, cores, jdk);
        if (open) {
            System.out.printf("OPEN LOOP at %.0f calls/s: latency is measured from each call's intended start time.%n", rate);
        } else {
            System.out.println("CLOSED LOOP: each caller calls again as soon as the last call returns, so the offered load "
                    + "falls as the system slows. Under saturation the percentiles are subject to coordinated omission "
                    + "(they understate the delay an independent arrival stream would see).");
        }

        int shards = Math.min(callers, SHARDS);
        LatencyHistogram[] hist = new LatencyHistogram[shards];
        for (int i = 0; i < shards; i++) {
            hist[i] = new LatencyHistogram();
        }
        boolean shared = callers > SHARDS;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger finished = new AtomicInteger();
        // the schedule for the open loop is derived from base, so it is set before the callers are released
        long[] baseBox = new long[1];
        final long periodNs = open ? Math.max(1, (long) (callers * 1e9 / rate)) : 0;

        int prime = Integer.parseInt(opt.getOrDefault("prime", "0"));
        if (prime == 1) {
            c.run();
        } else if (prime > 1) {      // prime concurrent calls: each finds no connection and opens its own, about prime in all
            Thread[] pt = new Thread[prime];
            for (int i = 0; i < prime; i++) {
                pt[i] = Thread.ofPlatform().daemon().name("prime-" + i).start(() -> {
                    try {
                        c.run();
                    } catch (Exception e) {
                        errors.increment();
                    }
                });
            }
            for (Thread t : pt) {
                t.join();
            }
        }
        served = new AtomicIntegerArray(callers);
        int started = 0;
        String startFailure = null;
        List<Thread> threads = new ArrayList<>(Math.min(callers, 8192));
        for (int i = 0; i < callers; i++) {
            final int id = i;
            final LatencyHistogram h = hist[i % shards];
            Runnable body = () -> {
                try {
                    go.await();
                    long base = baseBox[0];
                    if (open) {
                        openLoop(c, h, shared, id, base + (long) id * periodNs / callers, periodNs);
                    } else {
                        closedLoop(c, h, shared, id);
                    }
                } catch (InterruptedException ie) {
                    // leaving
                } finally {
                    finished.incrementAndGet();
                }
            };
            try {
                Thread th = kind.equals("virtual") ? Thread.ofVirtual().name("caller-" + i).unstarted(body)
                        : Thread.ofPlatform().daemon().name("caller-" + i).unstarted(body);
                th.start();
                threads.add(th);
                started++;
            } catch (OutOfMemoryError oom) {
                startFailure = "only " + started + " of " + callers + " callers started: " + oom.getMessage();
                break;
            }
        }
        if (startFailure != null) {
            System.out.println("WARNING " + startFailure);
        }
        // with fewer callers than asked the open-loop schedule stays the planned one, so the arrival rate is lower;
        // offered_rate in the output states what was actually offered
        final long period = periodNs;
        final int startedFinal = started;
        long base = System.nanoTime();
        long ws = base + (long) (warmup * 1e9);
        long we = ws + (long) (measure * 1e9);
        baseBox[0] = base;
        winStart = ws;
        winEnd = we;      // set before release: the callers compare each completion with the window
        go.countDown();

        Resources cl = new Resources("self"), sv = spid == null ? null : new Resources(spid);
        sleepUntil(ws);
        // CPU is divided by the calls completed between the two snapshots, wherever a busy main thread got to run them
        long[] cpuC0 = cl.cpuNanos(), cpuS0 = sv == null ? null : sv.cpuNanos();
        long done0 = allDone.sum();
        long[] sampleTimes = {ws + (we - ws) / 4, ws + (we - ws) / 2, ws + (we - ws) * 9 / 10};
        for (long st : sampleTimes) {
            sleepUntil(st);
            cl.sample();
            if (sv != null) {
                sv.sample();
            }
        }
        sleepUntil(we);
        long[] cpuC1 = cl.cpuNanos(), cpuS1 = sv == null ? null : sv.cpuNanos();
        long cpuCalls = allDone.sum() - done0;
        stop = true;
        if (open) {      // let the callers finish the arrivals scheduled inside the window
            long drainBy = System.nanoTime() + DRAIN_NANOS + 1_000_000_000L;
            while (finished.get() < startedFinal && System.nanoTime() < drainBy) {
                Thread.sleep(20);
            }
        }
        int unfinished = startedFinal - finished.get();

        LatencyHistogram all = new LatencyHistogram();
        for (LatencyHistogram h : hist) {
            all.add(h);
        }
        long n = all.count();
        double windowMs = (we - ws) / 1e6;     // the window the callers used, not when the main thread happened to wake
        double callsPerMs = inWindowCount(open, n) / windowMs;     // open loop: completions inside the window
        double p50 = all.percentile(50) / 1e3, p90 = all.percentile(90) / 1e3, p99 = all.percentile(99) / 1e3,
                p999 = all.percentile(99.9) / 1e3, max = all.max() / 1e3, mean = all.mean() / 1e3;
        long inWindow = open ? completedInWindow.sum() : n;
        int callersCompleted = callersServed();
        double littleUs = open ? Double.NaN : LatencyHistogram.littleMeanMicros(started, callsPerMs);
        double cpuClient = perCall(cpuC0, cpuC1, cpuCalls), cpuServer = perCall(cpuS0, cpuS1, cpuCalls);

        System.out.printf("%s %s %s %s x%d: %.2f calls/ms, %d calls recorded, window %.1f s, %d errors%s%n", stack, transport, payload, kind,
                started, callsPerMs, n, windowMs / 1e3, errors.sum(), firstError == null ? "" : " (first: " + firstError + ")");
        System.out.printf("  latency us: p50 %.1f  p90 %.1f  p99 %.1f  p99.9 %.1f  max %.1f  mean %.1f%n", p50, p90, p99, p999, max, mean);
        System.out.printf("  mean per call by Little's law (callers / throughput): %s us; callers that completed a call in the window: %d of %d%n",
                Double.isNaN(littleUs) ? "n/a (open loop)" : String.format(Locale.ROOT, "%.1f", littleUs), callersCompleted, started);
        if (open) {
            System.out.printf("  offered %.0f calls/s over %.0f s = %.0f arrivals, completed %d (%d inside the window), missed %d, callers unfinished %d%n",
                    rate, measure, rate * measure, n, completedInWindow.sum(), missed.sum(), unfinished);
        }
        System.out.printf("  client JVM: threads %s (jvm %d), sockets %s, RSS %s, cpu %s us/call%n", fmt(cl.maxThreads), jvmThreads(),
                fmt(cl.maxSockets), mb(cl.maxRssKb), fmt1(cpuClient));
        if (sv != null) {
            System.out.printf("  server JVM: threads %s, sockets %s, RSS %s, cpu %s us/call%n", fmt(sv.maxThreads), fmt(sv.maxSockets),
                    mb(sv.maxRssKb), fmt1(cpuServer));
        }

        Map<String, Object> j = new LinkedHashMap<>();
        j.put("stack", stack);
        j.put("transport", transport);
        j.put("payload", payload);
        j.put("kind", kind);
        j.put("mode", open ? "open" : "closed");
        j.put("prime", opt.getOrDefault("prime", "0"));
        j.put("callers_requested", callers);
        j.put("callers_started", started);
        j.put("warmup_s", warmup);
        j.put("measure_s", measure);
        j.put("offered_rate", open ? (Object) (started * 1e9 / period) : null);
        j.put("calls", n);
        j.put("completed_in_window", inWindow);
        j.put("errors", errors.sum());
        j.put("missed", missed.sum());
        j.put("callers_unfinished", open ? unfinished : 0);
        j.put("first_error", firstError);
        j.put("calls_per_ms", round(callsPerMs));
        j.put("p50_us", round(p50));
        j.put("p90_us", round(p90));
        j.put("p99_us", round(p99));
        j.put("p999_us", round(p999));
        j.put("max_us", round(max));
        j.put("mean_us", round(mean));
        j.put("client_threads", cl.maxThreads < 0 ? null : (Object) cl.maxThreads);
        j.put("client_jvm_threads", jvmThreads());
        j.put("client_sockets", cl.maxSockets < 0 ? null : (Object) cl.maxSockets);
        j.put("client_rss_kb", cl.maxRssKb < 0 ? null : (Object) cl.maxRssKb);
        j.put("client_cpu_us_per_call", Double.isNaN(cpuClient) ? null : (Object) round(cpuClient));
        j.put("server_threads", sv == null || sv.maxThreads < 0 ? null : (Object) sv.maxThreads);
        j.put("server_sockets", sv == null || sv.maxSockets < 0 ? null : (Object) sv.maxSockets);
        j.put("server_rss_kb", sv == null || sv.maxRssKb < 0 ? null : (Object) sv.maxRssKb);
        j.put("server_cpu_us_per_call", Double.isNaN(cpuServer) ? null : (Object) round(cpuServer));
        j.put("cores", cores);
        j.put("os", System.getProperty("os.name"));
        j.put("jdk", System.getProperty("java.version"));
        j.put("mean_little_us", Double.isNaN(littleUs) ? null : (Object) round(littleUs));
        j.put("callers_completed", callersCompleted);
        System.out.println("curve-json: " + json(j));
        System.out.flush();
        java.lang.ref.Reference.reachabilityFence(KEEP);
        Runtime.getRuntime().halt(0);     // callers may be blocked in calls; the measurement is complete
    }

    // ---------------------------------------------------------------- callers

    private static void closedLoop(Call c, LatencyHistogram h, boolean shared, int id) {
        while (!stop) {
            long t0 = System.nanoTime();
            try {
                c.run();
            } catch (Throwable x) {
                error(x);
                pause();
                continue;
            }
            allDone.increment();
            long t1 = System.nanoTime();
            if (t1 >= winStart && t1 < winEnd) {
                markServed(id);
                if (shared) {
                    h.recordShared(t1 - t0);
                } else {
                    h.record(t1 - t0);
                }
            }
        }
    }

    private static void openLoop(Call c, LatencyHistogram h, boolean shared, int id, long first, long period) {
        long ws = winStart, we = winEnd, hardStop = we + DRAIN_NANOS;
        for (long k = 0;; k++) {
            long intended = first + k * period;
            if (intended >= we) {
                return;
            }
            long now = System.nanoTime();
            while (intended - now > 0) {
                LockSupport.parkNanos(intended - now);
                now = System.nanoTime();
            }
            if (now > hardStop) {
                missed.add((we - intended + period - 1) / period);
                return;
            }
            try {
                c.run();
            } catch (Throwable x) {
                error(x);
                continue;
            }
            allDone.increment();
            long t1 = System.nanoTime();
            if (t1 >= ws && t1 < we) {
                completedInWindow.increment();
                markServed(id);
            }
            if (intended >= ws) {
                if (shared) {
                    h.recordShared(t1 - intended);
                } else {
                    h.record(t1 - intended);
                }
            }
        }
    }

    private static void markServed(int id) {
        AtomicIntegerArray s = served;
        if (s.get(id) == 0) {
            s.set(id, 1);
        }
    }

    private static int callersServed() {
        AtomicIntegerArray s = served;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            n += s.get(i);
        }
        return n;
    }

    private static void error(Throwable x) {
        while ((x instanceof java.util.concurrent.CompletionException || x instanceof java.lang.reflect.InvocationTargetException)
                && x.getCause() != null) {
            x = x.getCause();
        }
        long t = System.nanoTime();
        if (t >= winStart && t < winEnd) {
            errors.increment();
            if (firstError == null) {
                String msg = (x.getClass().getSimpleName() + ": " + x.getMessage()).replaceAll("\\s+", " ");
                firstError = msg.length() > 400 ? msg.substring(0, 400) : msg;
            }
        }
    }

    private static void pause() {
        LockSupport.parkNanos(1_000_000L);    // do not spin on a failing call
    }

    private static void sleepUntil(long nanoTime) throws InterruptedException {
        long d;
        while ((d = nanoTime - System.nanoTime()) > 0) {
            TimeUnit.NANOSECONDS.sleep(Math.min(d, 50_000_000L));
        }
    }

    // ---------------------------------------------------------------- resources (Linux /proc, optional)

    /** Thread, socket, resident memory and CPU figures of a process, from /proc where it exists. */
    private static final class Resources {
        final String pid;
        int maxThreads = -1, maxSockets = -1;
        long maxRssKb = -1;

        Resources(String pid) {
            this.pid = pid;
        }

        void sample() {
            try {
                for (String line : Files.readAllLines(Path.of("/proc/" + pid + "/status"))) {
                    if (line.startsWith("Threads:")) {
                        maxThreads = Math.max(maxThreads, Integer.parseInt(line.substring(8).trim()));
                    } else if (line.startsWith("VmRSS:")) {
                        maxRssKb = Math.max(maxRssKb, Long.parseLong(line.substring(6).replace("kB", "").trim()));
                    }
                }
            } catch (Exception unavailable) {
                // no /proc, or the process is gone
            }
            try (var ds = Files.newDirectoryStream(Path.of("/proc/" + pid + "/fd"))) {
                int n = 0;
                for (Path p : ds) {
                    try {
                        if (Files.readSymbolicLink(p).toString().startsWith("socket:")) {
                            n++;
                        }
                    } catch (Exception gone) {
                        // closed while listing
                    }
                }
                maxSockets = Math.max(maxSockets, n);
            } catch (Exception unavailable) {
                // no /proc, or not permitted
            }
        }

        /** {cpu ns over all threads, 1 if from schedstat (0 if from the portable ProcessHandle)}, or null. */
        long[] cpuNanos() {
            long sum = 0;
            boolean any = false;
            try (var ds = Files.newDirectoryStream(Path.of("/proc/" + pid + "/task"))) {
                for (Path t : ds) {
                    try {
                        sum += Long.parseLong(Files.readString(t.resolve("schedstat")).trim().split("\\s+")[0]);
                        any = true;
                    } catch (Exception gone) {
                        // thread exited
                    }
                }
            } catch (Exception unavailable) {
                // no /proc
            }
            if (any) {
                return new long[] {sum, 1};
            }
            try {
                ProcessHandle ph = pid.equals("self") ? ProcessHandle.current() : ProcessHandle.of(Long.parseLong(pid)).orElse(null);
                if (ph != null && ph.info().totalCpuDuration().isPresent()) {
                    return new long[] {ph.info().totalCpuDuration().get().toNanos(), 0};
                }
            } catch (Exception unavailable) {
                // not permitted
            }
            return null;
        }
    }

    private static long inWindowCount(boolean open, long n) {
        return open ? completedInWindow.sum() : n;
    }

    private static double perCall(long[] a, long[] b, long calls) {
        if (a == null || b == null || calls == 0) {
            return Double.NaN;
        }
        return (b[0] - a[0]) / 1e3 / calls;
    }

    private static int jvmThreads() {
        return java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
    }

    private static String fmt(int v) {
        return v < 0 ? "n/a" : Integer.toString(v);
    }

    private static String fmt1(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String mb(long kb) {
        return kb < 0 ? "n/a" : String.format(Locale.ROOT, "%.0f MB", kb / 1024.0);
    }

    // ---------------------------------------------------------------- arguments and JSON

    private static Map<String, String> parse(String[] args) {
        String[] positional = {"stack", "transport", "payload", "callers", "kind", "warmup", "measure"};
        Map<String, String> m = new LinkedHashMap<>();
        int pos = 0;
        for (String a : args) {
            int eq = a.indexOf('=');
            if (eq > 0) {
                m.put(a.substring(0, eq), a.substring(eq + 1));
            } else if (pos < positional.length) {
                m.put(positional[pos++], a);
            }
        }
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String json(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number num) {
                double d = num.doubleValue();
                sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : v.toString());
            } else {
                sb.append('"');
                for (char ch : v.toString().toCharArray()) {
                    if (ch == '"' || ch == '\\') {
                        sb.append('\\').append(ch);
                    } else if (ch < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(ch);
                    }
                }
                sb.append('"');
            }
        }
        return sb.append('}').toString();
    }
}
