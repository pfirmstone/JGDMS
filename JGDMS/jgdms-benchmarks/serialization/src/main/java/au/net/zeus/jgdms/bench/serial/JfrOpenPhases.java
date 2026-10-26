// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// Phase breakdown of a reconnect from JFR recordings made with TlsOpenCost: the client recording holds one "bench.FirstCall"
// event per sample plus jdk.MethodTrace events (see open-phases.sh); the optional server recording holds the method traces of
// the accept thread. For each sample the traced methods that ran on the calling thread inside the sample, and on the server's
// accept thread while the sample ran, are listed with the median offset from the start of the sample, the median inclusive
// time (summed over repeats within the sample) and the median number of repeats. Execution samples (if recorded) are
// summarised per frame, as the share of samples of the calling thread / accept thread inside the open.
//   java -cp ... au.net.zeus.jgdms.bench.serial.JfrOpenPhases client.jfr [server.jfr] [skip=0]
package au.net.zeus.jgdms.bench.serial;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import jdk.jfr.consumer.*;

public class JfrOpenPhases {
    record Ev(String name, long start, long end, String thread, RecordedEvent e) {}   // nanoseconds since epoch

    static long ns(Instant i) { return i.getEpochSecond() * 1_000_000_000L + i.getNano(); }

    static List<Ev> read(String file) throws IOException {
        List<Ev> out = new ArrayList<>();
        try (RecordingFile rf = new RecordingFile(Path.of(file))) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                String type = e.getEventType().getName();
                String name;
                if (type.equals("jdk.MethodTrace")) {
                    RecordedMethod m = e.getValue("method");
                    name = m.getType().getName() + "." + m.getName();
                } else if (type.equals("jdk.ExecutionSample") || type.equals("bench.FirstCall") || type.equals("jdk.TLSHandshake") || type.equals("jdk.JavaMonitorEnter") || type.equals("jdk.ThreadPark")) {
                    name = type;
                } else continue;
                long s = ns(e.getStartTime());
                long d = type.equals("jdk.ExecutionSample") ? 0 : e.getDuration().toNanos();
                RecordedThread t = type.equals("jdk.ExecutionSample") ? e.getValue("sampledThread") : e.getThread();
                out.add(new Ev(name, s, s + d, t == null ? "?" : t.getJavaName() + "#" + t.getJavaThreadId(), e));
            }
        }
        out.sort(Comparator.comparingLong(Ev::start));
        return out;
    }

    static double med(List<Long> v) { if (v.isEmpty()) return Double.NaN; List<Long> s = new ArrayList<>(v); Collections.sort(s); return s.get(s.size() / 2); }

    /** per-method accumulators over the samples */
    static class Acc {
        final List<Long> offsets = new ArrayList<>(), totals = new ArrayList<>(), counts = new ArrayList<>(); int samplesWith;
    }

    static void report(String title, Map<String, Acc> m, int samples) {
        System.out.println("\n" + title);
        System.out.printf("%-92s %9s %9s %7s %5s%n", "method", "offset ms", "time ms", "repeats", "in");
        m.entrySet().stream().sorted(Comparator.comparingDouble(en -> med(en.getValue().offsets))).forEach(en -> {
            Acc a = en.getValue();
            System.out.printf("%-92s %9.3f %9.3f %7.0f %2d/%d%n", en.getKey(), med(a.offsets) / 1e6, med(a.totals) / 1e6, med(a.counts), a.samplesWith, samples);
        });
    }

    public static void main(String[] args) throws Exception {
        List<Ev> client = read(args[0]);
        List<Ev> server = args.length > 1 && !args[1].equals("-") ? read(args[1]) : List.of();
        int skip = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        List<Ev> samples = new ArrayList<>();
        for (Ev e : client) if (e.name.equals("bench.FirstCall")) samples.add(e);
        samples = samples.subList(Math.min(skip, samples.size()), samples.size());
        System.out.println("samples: " + samples.size());
        List<Long> sampleDur = new ArrayList<>();
        Map<String, Acc> cm = new HashMap<>(), sm = new HashMap<>();
        Map<String, Integer> cframes = new HashMap<>(), sframes = new HashMap<>(); int cn = 0, sn = 0;
        Map<String, Integer> cleaf = new HashMap<>(), sleaf = new HashMap<>();
        List<Long> serverTotal = new ArrayList<>(), serverOffset = new ArrayList<>();
        for (Ev s : samples) {
            sampleDur.add(s.end - s.start);
            // client: methods on the sample's own thread inside the sample
            Map<String, long[]> per = new HashMap<>();   // name -> {firstOffset, total, count}
            for (Ev e : client) {
                if (e.start > s.end) break;
                if (e.start < s.start || e.end > s.end + 50_000 || !e.thread.equals(s.thread)) continue;
                if (e.name.equals("bench.FirstCall")) continue;
                if (e.name.equals("jdk.ExecutionSample")) { RecordedStackTrace st = e.e.getStackTrace(); if (st == null) continue; cn++; Set<String> seen = new HashSet<>(); int i = 0;
                    for (RecordedFrame f : st.getFrames()) { String k = f.getMethod().getType().getName() + "." + f.getMethod().getName(); if (i++ == 0) cleaf.merge(k, 1, Integer::sum); if (seen.add(k)) cframes.merge(k, 1, Integer::sum); } continue; }
                long[] a = per.computeIfAbsent(e.name, k -> new long[] { e.start - s.start, 0, 0 });
                a[1] += e.end - e.start; a[2]++;
            }
            for (var en : per.entrySet()) { Acc a = cm.computeIfAbsent(en.getKey(), k -> new Acc()); a.offsets.add(en.getValue()[0]); a.totals.add(en.getValue()[1]); a.counts.add(en.getValue()[2]); a.samplesWith++; }
            // server: find the accept-thread serverConnection event that starts inside the sample
            Ev root0 = null;
            for (Ev e : server) { if (e.start > s.end) break; if (e.start >= s.start - 2_000_000 && e.name.endsWith("SslListenHandle.serverConnection")) { root0 = e; break; } }
            if (root0 != null) {
                final Ev root = root0;
                serverOffset.add(root.start - s.start);
                Map<String, long[]> sper = new HashMap<>();
                long wEnd = s.end + 2_000_000;
                for (Ev e : server) {
                    if (e.start > wEnd) break;
                    if (e.start < root.start - 3_000_000) continue;
                    if (e.name.equals("jdk.ExecutionSample")) { if (!e.thread.equals(root.thread) || e.start > root.end + 2_000_000) continue; RecordedStackTrace st = e.e.getStackTrace(); if (st == null) continue; sn++; Set<String> seen = new HashSet<>(); int i = 0;
                        for (RecordedFrame f : st.getFrames()) { String k = f.getMethod().getType().getName() + "." + f.getMethod().getName(); if (i++ == 0) sleaf.merge(k, 1, Integer::sum); if (seen.add(k)) sframes.merge(k, 1, Integer::sum); } continue; }
                    if (!e.thread.equals(root.thread) && !e.name.contains("Mux") && !e.name.contains("StreamConnectionIO")) continue;
                    if (e.start < root.start || e.end > wEnd) continue;
                    long[] a = sper.computeIfAbsent(e.name, k -> new long[] { e.start - root.start, 0, 0 });
                    a[1] += e.end - e.start; a[2]++;
                }
                for (var en : sper.entrySet()) { Acc a = sm.computeIfAbsent(en.getKey(), k -> new Acc()); a.offsets.add(en.getValue()[0]); a.totals.add(en.getValue()[1]); a.counts.add(en.getValue()[2]); a.samplesWith++; }
                serverTotal.add(root.end - root.start);
            }
        }
        System.out.printf("first call (bench.FirstCall): median %.3f ms%n", med(sampleDur) / 1e6);
        report("CLIENT: traced methods on the calling thread (offset from the start of the first call)", cm, samples.size());
        if (!server.isEmpty()) {
            System.out.printf("%nSERVER: accept-thread serverConnection found for %d samples; median start %.3f ms after the client's call began, median duration %.3f ms%n", serverTotal.size(), med(serverOffset) / 1e6, med(serverTotal) / 1e6);
            report("SERVER: traced methods on the accept thread (offset from the start of serverConnection) and on mux threads", sm, serverTotal.size());
        }
        topFrames("CLIENT execution samples (calling thread, inside the first call): " + cn, cframes, cleaf, cn);
        if (sn > 0) topFrames("SERVER execution samples (accept thread, inside serverConnection): " + sn, sframes, sleaf, sn);
    }

    static void topFrames(String title, Map<String, Integer> incl, Map<String, Integer> leaf, int n) {
        if (n == 0) return;
        System.out.println("\n" + title + "  (share of samples with the frame anywhere in the stack / as the top frame)");
        incl.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(130).forEach(en ->
            System.out.printf("  %5.1f%%  top %5.1f%%  %s%n", 100.0 * en.getValue() / n, 100.0 * leaf.getOrDefault(en.getKey(), 0) / n, en.getKey()));
        System.out.println("  -- by self time (top frame):");
        leaf.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30).forEach(en ->
            System.out.printf("  self %5.1f%%  %s%n", 100.0 * en.getValue() / n, en.getKey()));
    }
}
