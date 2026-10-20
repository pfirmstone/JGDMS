// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
//
// Linux only (reads /proc/self/task): per-thread CPU time (schedstat), voluntary/involuntary context
// switches and socket descriptor count per call, for N concurrent callers on one proxy, in-process.
//   java -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.ThreadProfile DER UDS ping 16 8
//   args: stack (DER|JOSS|RMI) transport (TCP|UDS|TLS) payload (ping|record) [callers=1] [seconds=5]
// Written with AI assistance (Claude); see the README.
package au.net.zeus.jgdms.bench.serial;


import au.net.zeus.rmi.tls.*;
import java.lang.management.*;
import java.nio.file.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import javax.security.auth.Subject;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
public class ThreadProfile {
    static final List<Object> KEEP = new ArrayList<>();
    interface Call { void run() throws Exception; }
    static Map<String, long[]> snap() throws Exception {
        Map<String, long[]> m = new TreeMap<>();
        try (var ds = Files.newDirectoryStream(Path.of("/proc/self/task"))) {
            for (Path t : ds) {
                String name; try { name = Files.readString(t.resolve("comm")).trim(); } catch (java.io.IOException gone) { continue; } long v = 0, nv = 0, cpu = 0;
                List<String> sl; try { sl = Files.readAllLines(t.resolve("status")); } catch (java.io.IOException gone) { continue; }
                for (String l : sl) {
                    if (l.startsWith("voluntary_ctxt_switches:")) v = Long.parseLong(l.split("\\s+")[1]);
                    if (l.startsWith("nonvoluntary_ctxt_switches:")) nv = Long.parseLong(l.split("\\s+")[1]);
                }
                try { cpu = Long.parseLong(Files.readString(t.resolve("schedstat")).trim().split("\\s+")[0]); } catch (Exception x) { }
                name = name.replaceAll("[0-9]+", "#");
                long[] a = m.computeIfAbsent(name, k -> new long[3]); a[0] += v; a[1] += nv; a[2] += cpu;
            }
        }
        return m;
    }
    static int sockets() throws Exception { int n = 0; try (var ds = Files.newDirectoryStream(Path.of("/proc/self/fd"))) { for (Path p : ds) { try { if (Files.readSymbolicLink(p).toString().startsWith("socket:")) n++; } catch (Exception x) { } } } return n; }
    public static void main(String[] a) throws Exception { try { run(a); } catch (Throwable t) { t.printStackTrace(); System.exit(1); } }
    static void run(String[] a) throws Exception {
        String stack = a[0], transport = a[1], what = a[2];
        ClassLoader cl = ThreadProfile.class.getClassLoader();
        Subject client = null; Call call;
        Object arg = null;
        if (stack.equals("RMI")) {
            RmiEcho.Impl impl = new RmiEcho.Impl(); KEEP.add(impl); RmiEcho stub;
            if (transport.equals("TLS")) { TlsFixture f = TlsFixture.current(); client = f.client;
                stub = Subject.callAs(f.server, () -> (RmiEcho) UnicastRemoteObject.exportObject(impl, 0, new TlsRMIClientSocketFactory(), new TlsRMIServerSocketFactory())); }
            else stub = (RmiEcho) UnicastRemoteObject.exportObject(impl, 0);
            JdkPayloads.Record r = (JdkPayloads.Record) JdkPayloads.create("record");
            call = what.equals("ping") ? stub::ping : () -> stub.record(r);
        } else {
            Format f = Format.valueOf(stack); Transport t = Transport.valueOf(transport);
            Exporter e = new BasicJeriExporter(t.serverEndpoint(), f.ilFactory(cl, t.constraints()), false, true);
            Echo.Impl impl = new Echo.Impl(); KEEP.add(impl); KEEP.add(e); Echo p = (Echo) t.asServer(() -> e.export(impl)); client = t.clientSubject();
            Payloads.Record r = (Payloads.Record) Payloads.create("record");
            call = what.equals("ping") ? p::ping : () -> p.record(r);
        }
        final Subject cs = client;
        Call c = cs == null ? call : () -> Subject.callAs(cs, () -> { call.run(); return null; });
        int threads = a.length > 3 ? Integer.parseInt(a[3]) : 1;
        double secs = a.length > 4 ? Double.parseDouble(a[4]) : 5;
        final Call cc = c;
        final long[] counts = new long[threads];
        final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        Thread[] ts = new Thread[threads];
        java.util.concurrent.CyclicBarrier go = new java.util.concurrent.CyclicBarrier(threads + 1);
        final long[] measureFrom = new long[1];
        for (int i = 0; i < threads; i++) { final int id = i;
            ts[i] = new Thread(() -> { try { go.await(); long wend = System.nanoTime() + 5_000_000_000L;
                    while (System.nanoTime() < wend) cc.run();
                    go.await();
                    while (!stop.get()) { cc.run(); counts[id]++; } } catch (Exception ex) { ex.printStackTrace(); } }, "caller-" + i);
            ts[i].start(); }
        go.await(); go.await();   // warm-up 5 s
        Map<String, long[]> s0 = snap(); int fd0 = sockets();
        long t0 = System.nanoTime(); Thread.sleep((long) (secs * 1000)); int fd1 = sockets();
        long t1 = System.nanoTime(); Map<String, long[]> s1 = snap(); stop.set(true);
        for (Thread t : ts) t.join();
        long n = 0; for (long x : counts) n += x;
        double wall = (t1 - t0) / 1e3 / n * 1.0;
        System.out.printf("%s %s %s x%d: %.1f calls/ms total, %.1f us wall per call per caller, sockets in fds %d%n", stack, transport, what, threads, n / ((t1 - t0) / 1e6), (t1 - t0) / 1e3 / n * threads, fd1);
        double totV = 0, totN = 0, totC = 0;
        for (var e : s1.entrySet()) { long[] b = s0.getOrDefault(e.getKey(), new long[3]); double v = (e.getValue()[0] - b[0]) / (double) n, nv = (e.getValue()[1] - b[1]) / (double) n, cpu = (e.getValue()[2] - b[2]) / 1e3 / n;
            if (v > 0.05 || nv > 0.05 || cpu > 0.5) System.out.printf("   %-22s voluntary/call %.2f  involuntary/call %.2f  cpu us/call %.1f%n", e.getKey(), v, nv, cpu); totV += v; totN += nv; totC += cpu; }
        System.out.printf("   TOTAL per call: voluntary %.2f involuntary %.2f cpu %.1f us%n", totV, totN, totC);
        java.lang.ref.Reference.reachabilityFence(KEEP); System.exit(0);
    }
}
