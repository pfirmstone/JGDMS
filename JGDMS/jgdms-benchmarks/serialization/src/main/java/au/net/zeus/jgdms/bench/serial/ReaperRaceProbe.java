// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance; see the README.
package au.net.zeus.jgdms.bench.serial;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;

/**
 * Calls a {@code ServerMain} with a short idle timeout and a call spacing near it, so that the client's idle reaper
 * closes connections as calls start, and counts the calls that fail and how (RESULTS.md, section 35). Run it
 * with {@code -Dorg.apache.river.jeri.connectionTimeout=2} (milliseconds) and, for TLS, the same
 * {@code -Dbench.tls.dir} as the server.
 *
 * <pre>
 *   java -Dorg.apache.river.jeri.connectionTimeout=2 -cp serialization-benchmarks.jar \
 *        au.net.zeus.jgdms.bench.serial.ReaperRaceProbe &lt;ServerMain dir&gt; DER TLS 2500 5 25 2
 *   args: dir stack transport calls-per-thread minSpacingMs maxSpacingMs [threads]
 * </pre>
 * Prints {@code ok N errors M} and one line per kind of failure. With the old {@code ConnectionManager} a few calls
 * in thousands fail ({@code Connection closed}, {@code server sent bad magic number: 8C000005},
 * {@code channel already registered}, {@code Socket closed}); with the fixed one none do.
 */
public final class ReaperRaceProbe {

    private ReaperRaceProbe() {
    }

    public static void main(String[] a) throws Exception {
        String dir = a[0];
        String stack = a[1];
        String transport = a[2];
        final int calls = Integer.parseInt(a[3]);
        final int lo = Integer.parseInt(a[4]);
        final int hi = Integer.parseInt(a[5]);
        int threads = a.length > 6 ? Integer.parseInt(a[6]) : 1;
        Format f = Format.valueOf(stack);
        Transport t = Transport.valueOf(transport);
        final Echo p = (Echo) ServerMain.read(f, dir + "/" + stack + "." + transport + ".proxy");
        final Subject cs = t.clientSubject();
        final AtomicInteger errors = new AtomicInteger();
        final AtomicInteger ok = new AtomicInteger();
        final Map<String, AtomicInteger> kinds = new ConcurrentHashMap<>();
        Thread[] ts = new Thread[threads];
        for (int th = 0; th < threads; th++) {
            final int seed = th;
            ts[th] = new Thread(() -> {
                Random r = new Random(seed);
                for (int i = 0; i < calls; i++) {
                    try {
                        if (cs == null) {
                            p.ping();
                        } else {
                            Subject.callAs(cs, () -> {
                                p.ping();
                                return null;
                            });
                        }
                        ok.incrementAndGet();
                    } catch (Throwable e) {
                        if (e instanceof java.util.concurrent.CompletionException && e.getCause() != null) {
                            e = e.getCause();
                        }
                        errors.incrementAndGet();
                        Throwable c = e;
                        while (c.getCause() != null) {
                            c = c.getCause();
                        }
                        kinds.computeIfAbsent(e.getClass().getSimpleName() + " / " + c.getClass().getSimpleName() + ": "
                                + c.getMessage(), x -> new AtomicInteger()).incrementAndGet();
                    }
                    try {
                        Thread.sleep(lo + r.nextInt(Math.max(1, hi - lo + 1)));
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "probe-" + th);
            ts[th].start();
        }
        for (Thread th : ts) {
            th.join();
        }
        System.out.println("ok " + ok + " errors " + errors);
        kinds.forEach((k, v) -> System.out.println("  " + v + " x " + k));
        System.exit(0);
    }
}
