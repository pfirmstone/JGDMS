// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// Cost of opening a TLS connection, resumed or full (needs a ServerMain started with -Dbench.tls.dir, see ConnectionCost).
// Linux only. A reconnect is a first call on a connection the idle reaper has just closed
// (run with -Dorg.apache.river.jeri.connectionTimeout=30), as in ConnectionCost.
//   java -Dbench.tls.dir=<dir> -Dorg.apache.river.jeri.connectionTimeout=30 [-Dtlsopen.serverpid=<pid of ServerMain>]
//        -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsOpenCost <serverdir> resumed|full [samples=60] [warm=15] [sleepMs=120]
//   resumed: the JDK's client session cache is left alone, so every reconnect after the first is a TLS 1.3 PSK resumption.
//   full:    before each reconnect the tickets in the client SSLContext's session cache are invalidated (by reflection into the
//            pool of client SSLContexts in net.jini.jeri.ssl.Utilities), so the ClientHello offers no PSK and the server sends
//            Certificate and CertificateVerify. Everything else is unchanged (same SSLContext and credentials, no new context).
// It prints the first-call time of each sample, per-block medians (to see the JIT converge), and the CPU time per reconnect of
// this JVM and of the server JVM by thread group. It commits one jdk.jfr event "bench.FirstCall" per sample, so a JFR
// recording of the client (method traces, execution samples) can be lined up with the samples.
package au.net.zeus.jgdms.bench.serial;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.auth.Subject;

public class TlsOpenCost {
    interface Call { void run() throws Exception; }

    @jdk.jfr.Name("bench.FirstCall")
    @jdk.jfr.Label("First call on a reconnect")
    @jdk.jfr.StackTrace(false)
    static class FirstCall extends jdk.jfr.Event {
        @jdk.jfr.Label("sample") int sample;
        @jdk.jfr.Label("kind") String kind;
    }

    /** Invalidates every session in the client session contexts of the pooled client SSLContexts; returns how many. */
    static int dropClientTickets() throws Exception {
        Class<?> u = Class.forName("net.jini.jeri.ssl.Utilities");
        Field mf = u.getDeclaredField("SSL_CONTEXT_MAP");
        mf.setAccessible(true);
        Object table = mf.get(null);
        Field hf = table.getClass().getDeclaredField("hash");
        hf.setAccessible(true);
        int n = 0;
        synchronized (table) {
            for (Object list : new ArrayList<>(((Map<?, ?>) hf.get(table)).values())) {
                for (Object ref : new ArrayList<>((List<?>) list)) {
                    Object ctx = ((java.lang.ref.Reference<?>) ref).get();
                    if (ctx instanceof SSLContext) {
                        SSLSessionContext sc = ((SSLContext) ctx).getClientSessionContext();
                        for (Enumeration<byte[]> en = sc.getIds(); en.hasMoreElements();) {
                            SSLSession s = sc.getSession(en.nextElement());
                            if (s != null) { s.invalidate(); n++; }
                        }
                    }
                }
            }
        }
        return n;
    }

    /** CPU nanoseconds by thread group of one process (Linux schedstat). Groups: jit (C1 and C2 compiler threads), gc, and the other threads by name. */
    static Map<String, Long> cpu(String pid) throws Exception {
        Map<String, Long> m = new TreeMap<>();
        try (var ds = Files.newDirectoryStream(Path.of("/proc/" + pid + "/task"))) {
            for (Path t : ds) {
                String name; long ns;
                try { name = Files.readString(t.resolve("comm")).trim(); ns = Long.parseLong(Files.readString(t.resolve("schedstat")).trim().split("\\s+")[0]); } catch (Exception gone) { continue; }
                String g = name.matches("C[12] Comp.*") ? "jit" : (name.startsWith("G1") || name.startsWith("GC") || name.startsWith("ZGC")) ? "gc" : name.replaceAll("[0-9]+", "#");
                m.merge(g, ns, Long::sum);
            }
        }
        return m;
    }

    static String diff(Map<String, Long> a, Map<String, Long> b, int n) {
        StringBuilder sb = new StringBuilder();
        long tot = 0; List<Map.Entry<String, Long>> l = new ArrayList<>();   // a thread that exited (a compiler thread) counts as 0, not negative
        for (var e : b.entrySet()) { long d = Math.max(0L, e.getValue() - a.getOrDefault(e.getKey(), 0L)); tot += d; l.add(Map.entry(e.getKey(), d)); }
        l.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(9, l.size()); i++) sb.append(String.format("%n      %-24s %7.0f us", l.get(i).getKey(), l.get(i).getValue() / 1e3 / n));
        return String.format("total %.0f us per sample, by thread:%s", tot / 1e3 / n, sb);
    }

    static double median(long[] v) { long[] s = v.clone(); Arrays.sort(s); return s[s.length / 2] / 1e3; }

    public static void main(String[] a) throws Exception {
        String dir = a[0], mode = a[1];
        int n = a.length > 2 ? Integer.parseInt(a[2]) : 60;
        int warm = a.length > 3 ? Integer.parseInt(a[3]) : 15;
        long sleep = a.length > 4 ? Long.parseLong(a[4]) : 120;
        boolean full = mode.equals("full");
        Echo p = (Echo) ServerMain.read(Format.DER, dir + "/DER.TLS.proxy");
        Subject cs = Transport.TLS.clientSubject();
        Call call = () -> Subject.callAs(cs, () -> { p.ping(); return null; });
        String serverPid = System.getProperty("tlsopen.serverpid");
        String selfPid = Long.toString(ProcessHandle.current().pid());

        for (int i = 0; i < 3000; i++) call.run();
        int warmRaced = 0;
        for (int i = 0; i < warm; i++) {
            Thread.sleep(sleep); if (full) dropClientTickets();
            try { call.run(); } catch (Exception e) { warmRaced++; try { call.run(); } catch (Exception again) { } }   // reaper race, see below
        }
        long[] warmCalls = new long[3000];
        for (int i = 0; i < warmCalls.length; i++) { long t0 = System.nanoTime(); call.run(); warmCalls[i] = System.nanoTime() - t0; }

        Map<String, Long> c0 = cpu(selfPid), s0 = serverPid != null ? cpu(serverPid) : null;
        long[] first0 = new long[n]; int got = 0, raced = 0;
        int dropped = 0;
        for (int i = 0; i < n; i++) {
            Thread.sleep(sleep);
            if (full) dropped += dropClientTickets();
            FirstCall ev = new FirstCall(); ev.sample = i; ev.kind = mode;
            ev.begin(); long t0 = System.nanoTime();
            try {
                call.run();
                first0[got++] = System.nanoTime() - t0; ev.commit();
            } catch (Exception e) {
                // the idle reaper closed the connection just as the call started (only seen with a very short connectionTimeout)
                raced++;
                try { call.run(); } catch (Exception again) { }
            }
        }
        long[] first = Arrays.copyOf(first0, got);
        Map<String, Long> c1 = cpu(selfPid), s1 = serverPid != null ? cpu(serverPid) : null;

        double wm = median(warmCalls), fm = median(first);
        System.out.printf("TLS %s: warm call median %.0f us; first call on a new connection median %.0f us (min %.0f, max %.0f); opening costs about %.0f us (%d samples, %d warm-up reconnects, %d ms between)%n",
            mode, wm, fm, Arrays.stream(first).min().getAsLong() / 1e3, Arrays.stream(first).max().getAsLong() / 1e3, fm - wm, first.length, warm, sleep);
        if (full) System.out.println("tickets dropped in total: " + dropped);
        if (raced + warmRaced > 0) System.out.println("calls that failed because the idle reaper closed the connection as the call started (discarded): " + (raced + warmRaced) + " of " + (n + warm));
        StringBuilder sb = new StringBuilder("first-call us, in order:");
        for (long v : first) sb.append(' ').append(Math.round(v / 1e3));
        System.out.println(sb);
        int block = n >= 600 ? n / 15 : Math.max(10, n / 6); n = first.length;
        sb = new StringBuilder("median first-call us per block of " + block + ":");
        for (int i = 0; i + block <= n; i += block) sb.append(String.format(" %.0f", median(Arrays.copyOfRange(first, i, i + block))));
        System.out.println(sb);
        // CPU over the whole sampling loop, which includes the sleeps (idle) but not the warm-up
        System.out.println("client JVM cpu per sample (includes the idle reaper): " + diff(c0, c1, n));
        if (s0 != null) System.out.println("server JVM cpu per sample: " + diff(s0, s1, n));
        System.exit(0);
    }
}
