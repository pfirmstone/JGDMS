// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
// Cost of opening a connection (needs a ServerMain: see ThreadProfile). args: dir stack transport cold|burst [n]
//   cold: first call on a fresh connection against a warm call (run with -Dorg.apache.river.jeri.connectionTimeout=1 so the reaper closes idle connections)
//   burst: n callers start together and open about n connections: threads, sockets and memory before and after (Linux)
package au.net.zeus.jgdms.bench.serial;


import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import javax.security.auth.Subject;

/** args: dir stack transport mode(cold|burst) [n]. cold: latency of the first call on a fresh connection vs a warm one. */
public class ConnectionCost {
    interface Call { void run() throws Exception; }
    static String proc(String key) throws Exception {
        for (String l : Files.readAllLines(Path.of("/proc/self/status"))) if (l.startsWith(key)) return l.substring(key.length()).trim();
        return "?";
    }
    static int sockets() throws Exception { int n = 0; try (var ds = Files.newDirectoryStream(Path.of("/proc/self/fd"))) { for (Path p : ds) { try { if (Files.readSymbolicLink(p).toString().startsWith("socket:")) n++; } catch (Exception x) { } } } return n; }
    static long heap() { System.gc(); System.gc(); try { Thread.sleep(300); } catch (Exception e) { } MemoryMXBean m = ManagementFactory.getMemoryMXBean(); return m.getHeapMemoryUsage().getUsed() + m.getNonHeapMemoryUsage().getUsed(); }
    public static void main(String[] a) throws Exception {
        String dir = a[0], stack = a[1], transport = a[2], mode = a[3];
        int n = a.length > 4 ? Integer.parseInt(a[4]) : 1;
        Format f = Format.valueOf(stack); Transport t = Transport.valueOf(transport);
        Echo p = (Echo) ServerMain.read(f, dir + "/" + stack + "." + transport + ".proxy"); Subject cs = t.clientSubject();
        Call call = cs == null ? p::ping : () -> Subject.callAs(cs, () -> { p.ping(); return null; });
        if (mode.equals("cold")) {
            for (int i = 0; i < 3000; i++) call.run();                              // warm up the call path
            for (int i = 0; i < 15; i++) { Thread.sleep(120); call.run(); }          // and the connection-opening path
            long[] warm = new long[3000], cold = new long[n];
            for (int i = 0; i < warm.length; i++) { long t0 = System.nanoTime(); call.run(); warm[i] = System.nanoTime() - t0; }
            for (int i = 0; i < n; i++) { Thread.sleep(120); long t0 = System.nanoTime(); call.run(); cold[i] = System.nanoTime() - t0; }  // reaper has closed the connection
            Arrays.sort(warm); Arrays.sort(cold);
            System.out.printf("%s %s: warm call median %.0f us; first call on a new connection median %.0f us (min %.0f, max %.0f), so opening costs about %.0f us%n",
                stack, transport, warm[warm.length / 2] / 1e3, cold[cold.length / 2] / 1e3, cold[0] / 1e3, cold[cold.length - 1] / 1e3, (cold[cold.length / 2] - warm[warm.length / 2]) / 1e3);
        } else {
            call.run(); long h0 = heap(); int th0 = Integer.parseInt(proc("Threads:")); int s0 = sockets(); String rss0 = proc("VmRSS:");
            // n concurrent callers started together open about n connections
            java.util.concurrent.CyclicBarrier go = new java.util.concurrent.CyclicBarrier(n + 1);
            boolean[] stop = new boolean[1];
            List<Thread> ts = new ArrayList<>();
            for (int i = 0; i < n; i++) { Thread th = new Thread(() -> { try { go.await(); while (!stop[0]) { call.run(); Thread.sleep(20); } } catch (Exception e) { } }); th.setDaemon(true); th.start(); ts.add(th); }
            go.await(); Thread.sleep(3000);
            long h1 = heap(); int th1 = Integer.parseInt(proc("Threads:")); int s1 = sockets(); String rss1 = proc("VmRSS:");
            stop[0] = true;
            System.out.printf("%s %s %d callers: threads %d -> %d, sockets %d -> %d, heap+nonheap %.1f -> %.1f MB, RSS %s -> %s%n", stack, transport, n, th0, th1, s0, s1, h0 / 1e6, h1 / 1e6, rss0, rss1);
        }
        System.exit(0);
    }
}
