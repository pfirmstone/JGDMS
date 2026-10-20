// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// Where the TLS listener's accept thread spends its time: reads a server recording made with method traces of
// SslListenHandle.serverConnection, handleConnection, SslEngineChannel.handshake0 / fillNetInByDeadline / runDelegatedTasks /
// handshakeWrap, ServerSocketAdaptor.accept, Selector.open, Thread.start and Mux.start (see tls-open-cost.py accept --jfr) and
// prints, for the thread that ran serverConnection, each method's inclusive time as a share of the wall time between the first
// and the last connection, and per connection. The handshake waits for the peer (fillNetInByDeadline) are on this thread.
//   java -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.JfrAcceptThread server.jfr
package au.net.zeus.jgdms.bench.serial;

import java.nio.file.Path;
import java.util.*;
import jdk.jfr.consumer.*;

public class JfrAcceptThread {
    public static void main(String[] a) throws Exception {
        List<RecordedEvent> evs = new ArrayList<>();
        try (RecordingFile rf = new RecordingFile(Path.of(a[0]))) {
            while (rf.hasMoreEvents()) { RecordedEvent e = rf.readEvent(); if (e.getEventType().getName().equals("jdk.MethodTrace")) evs.add(e); }
        }
        String thread = null;
        for (RecordedEvent e : evs) { RecordedMethod m = e.getValue("method"); if (m.getName().equals("serverConnection")) thread = e.getThread().getJavaName() + "#" + e.getThread().getJavaThreadId(); }
        long t0 = Long.MAX_VALUE, t1 = 0, nn = 0;
        for (RecordedEvent e : evs) {
            RecordedMethod m = e.getValue("method");
            if (m.getName().equals("serverConnection") && (e.getThread().getJavaName() + "#" + e.getThread().getJavaThreadId()).equals(thread)) { nn++; t0 = Math.min(t0, e.getStartTime().toEpochMilli()); t1 = Math.max(t1, e.getEndTime().toEpochMilli()); }
        }
        Map<String, long[]> sums = new TreeMap<>();
        for (RecordedEvent e : evs) {
            RecordedMethod m = e.getValue("method");
            if (!(e.getThread().getJavaName() + "#" + e.getThread().getJavaThreadId()).equals(thread)) continue;
            long s = e.getStartTime().toEpochMilli();
            if (s < t0 || s > t1) continue;
            long[] v = sums.computeIfAbsent(m.getType().getName().replaceAll(".*\\.", "") + "." + m.getName(), k -> new long[2]);
            v[0] += e.getDuration().toNanos(); v[1]++;
        }
        final double wall = (t1 - t0) * 1e6; final long n = nn;
        System.out.printf("accept thread %s: %d connections over %.1f s (%.0f per second)%n", thread, n, wall / 1e9, n / (wall / 1e9));
        sums.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).forEach(en ->
            System.out.printf("  %5.1f%% of wall  %8.3f ms per connection  %5.1f calls per connection  %s%n", 100 * en.getValue()[0] / wall, en.getValue()[0] / 1e6 / n, en.getValue()[1] / (double) n, en.getKey()));
    }
}
