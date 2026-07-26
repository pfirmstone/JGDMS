/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.showcase.pushdown;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.space.FilteredTupleSpace;
import net.jini.space.JavaSpace;
import net.jini.space.MatchSet;

import org.apache.river.outrigger.authoring.EntryFilter;

import au.net.zeus.jgdms.showcase.pushdown.records.NorthStationReading;
import au.net.zeus.jgdms.showcase.pushdown.records.WeatherReading;

/**
 * demo7 client logic (design memo B3 &sect;8). Loaded by a <b>child</b> class
 * loader that has {@code WeatherReading} on its classpath; the orchestrator
 * ({@code Server}) invokes each phase method in turn, checking the server-side
 * counters and JFR stream between them. The space / transaction-manager proxies
 * are passed in as live objects (shared in-JVM), so no proxy marshalling and no
 * codebase server is involved; the filtered operations still make real loopback
 * JERI calls, DER-marshalling entries the space filters class-free.
 *
 * <p>Everything the client can verify from its own side — the <em>identities</em>
 * of the entries returned, and whether P's standing-query listener fired — it
 * checks here. The counter / JFR <em>mechanism</em> proofs live in {@code Server}.
 */
public final class ClientLogic {

    private static final String RULE =
            "temperatureCelsius > 20.0 && stationName.startsWith(\"North\")";

    private final FilteredTupleSpace fjs;
    private final JavaSpace js;
    private final TransactionManager mgr;
    private final byte[] filter;

    private boolean allHeld = true;

    // §8.3 state.
    private final AtomicInteger pHits = new AtomicInteger(0);
    private Exporter listenerExporter;
    private Transaction tq;
    private int pBeforeUncommitted;

    /** Invoked reflectively by {@code Server} across the class-loader boundary. */
    public ClientLogic(Object space, Object txnMgr) throws Exception {
        this.fjs = (FilteredTupleSpace) space;
        this.js = (JavaSpace) space;
        this.mgr = (TransactionManager) txnMgr;
        this.filter = EntryFilter.compile(RULE, WeatherReading.class);
        System.out.println("  client (child loader) can load WeatherReading and authored the filter:");
        System.out.println("    " + RULE + "  (" + filter.length + " canonical FilterEnvelope bytes)");
        System.out.println();
    }

    // =====================================================================
    // §8.2 class-free filtered contents/take = warm-northern only (+ §8.4a/b).
    // =====================================================================
    public boolean happyPath() throws Exception {
        boolean ok = true;
        js.write(new WeatherReading(25.0, "North Ridge"), null, Lease.FOREVER); // warm-northern
        js.write(new WeatherReading(25.0, "South Bay"),   null, Lease.FOREVER); // warm-southern
        js.write(new WeatherReading(10.0, "North Pole"),  null, Lease.FOREVER); // cold-northern
        js.write(new WeatherReading(5.0,  "South Cape"),  null, Lease.FOREVER); // cold-southern
        js.write(new NorthStationReading(22.0, "Northgate", 300),
                 null, Lease.FOREVER);                                          // §8.4b subclass, warm-northern

        final TreeSet<String> expected = new TreeSet<>(List.of(
                key(25.0, "North Ridge"), key(22.0, "Northgate")));

        TreeSet<String> contents = new TreeSet<>();
        boolean subclassSeen = false;
        MatchSet ms = fjs.contents(Collections.singletonList(new WeatherReading()),
                null, 60_000L, 100L, filter);
        try {
            Entry e;
            while ((e = ms.next()) != null) {
                WeatherReading w = (WeatherReading) e;
                contents.add(key(w.temperatureCelsius, w.stationName));
                if (e instanceof NorthStationReading) subclassSeen = true;
            }
        } finally {
            try { ms.getLease().cancel(); } catch (Exception ignore) {}
        }
        System.out.println("  filtered contents returned: " + contents);
        ok &= check(contents.equals(expected),
              "§8.2 filtered contents = exactly the warm-northern entries " + expected);
        ok &= check(subclassSeen,
              "§8.4b a warm-northern NorthStationReading (subclass) was returned, filtered by inherited fields");

        Collection taken = fjs.take(Collections.singletonList(new WeatherReading()),
                null, 5_000L, 100L, filter);
        TreeSet<String> takenKeys = new TreeSet<>();
        for (Object o : taken) {
            WeatherReading w = (WeatherReading) o;
            takenKeys.add(key(w.temperatureCelsius, w.stationName));
        }
        System.out.println("  filtered take returned:     " + takenKeys);
        ok &= check(takenKeys.equals(expected),
              "§8.2 filtered take removed exactly the warm-northern entries " + expected);

        drain();

        // §8.4a: blocking filtered read resolves only on a genuine match.
        final AtomicReference<Entry> readResult = new AtomicReference<>();
        final AtomicReference<Throwable> readError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try { readResult.set(fjs.read(new WeatherReading(), null, 20_000L, filter)); }
            catch (Throwable t) { readError.set(t); }
        }, "demo7-blocking-read");
        reader.start();
        Thread.sleep(500);
        js.write(new WeatherReading(10.0, "North Pole"), null, Lease.FOREVER); // cold-northern
        Thread.sleep(1_500);
        ok &= check(reader.isAlive() && readResult.get() == null,
              "§8.4a a cold-northern write did NOT satisfy the filtered read (evaluated false, not fail-open)");
        js.write(new WeatherReading(26.0, "North Star"), null, Lease.FOREVER); // warm-northern
        reader.join(10_000);
        if (readError.get() != null) throw new Exception("blocking read failed", readError.get());
        Entry resolved = readResult.get();
        boolean resolvedOk = resolved instanceof WeatherReading
                && key(((WeatherReading) resolved).temperatureCelsius,
                       ((WeatherReading) resolved).stationName).equals(key(26.0, "North Star"));
        ok &= check(resolvedOk,
              "§8.4a the blocking filtered read resolved to the later warm-northern write "
              + (resolved == null ? "(null)" : "(" + resolved + ")"));
        drain();
        return ok;
    }

    // =====================================================================
    // §8.3 confused-deputy.
    // =====================================================================
    public void registerP() throws Exception {
        listenerExporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicILFactory(null, null, RemoteEventListener.class.getClassLoader()),
                false, true);
        RemoteEventListener pStub = (RemoteEventListener) listenerExporter.export(
                new RemoteEventListener() {
                    public void notify(RemoteEvent e) { pHits.incrementAndGet(); }
                });
        fjs.registerForAvailabilityEvent(
                Collections.singletonList(new WeatherReading()),
                null,   // P is NOT a transaction participant
                false,  // availability (not visibility-only)
                pStub, 120_000L, null, filter);
        System.out.println("  P registered a filtered standing availability query (non-participant).");
    }

    public boolean qWriteUncommitted() throws Exception {
        Transaction.Created tqc = TransactionFactory.create(mgr, 120_000L);
        tq = tqc.transaction;
        pBeforeUncommitted = pHits.get();
        js.write(new WeatherReading(25.0, "North Ridge"), tq, Lease.FOREVER);
        System.out.println("  Q wrote a warm-northern entry under its uncommitted transaction Tq.");
        Thread.sleep(1_500); // grace for any (erroneous) delivery
        return check(pHits.get() == pBeforeUncommitted,
              "§8.3 P's listener did NOT receive Q's txn-private entry while Tq uncommitted");
    }

    public boolean qCommit() throws Exception {
        tq.commit();
        System.out.println("  Q committed Tq.");
        boolean delivered = false;
        long deadline = System.nanoTime() + 8_000L * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (pHits.get() > pBeforeUncommitted) { delivered = true; break; }
            Thread.sleep(50);
        }
        return check(delivered,
              "§8.3 after commit P's listener NOW receives the warm-northern entry");
    }

    // =====================================================================
    // §8.4c projection budget.
    // =====================================================================
    public boolean budgetCandidate() throws Exception {
        String hugeNorthName = "North" + "x".repeat(70 * 1024); // >64 KiB, starts "North"
        js.write(new WeatherReading(30.0, hugeNorthName), null, Lease.FOREVER);
        // Byte-equality template matching ONLY the huge candidate; the filter must
        // still EXCLUDE it (projection budget) -> readIfExists returns null.
        Entry hit = fjs.readIfExists(new WeatherReading(30.0, hugeNorthName),
                null, 3_000L, filter);
        boolean ok = check(hit == null,
              "§8.4c the >64 KiB warm-northern candidate was EXCLUDED (budget); targeted filtered read returned null");
        try { if (listenerExporter != null) listenerExporter.unexport(true); } catch (Exception ignore) {}
        return ok;
    }

    // =====================================================================
    // helpers
    // =====================================================================
    private void drain() throws Exception {
        Entry e;
        int n = 0;
        while ((e = js.takeIfExists(new WeatherReading(), null, 0)) != null) {
            if (++n > 10_000) break;
        }
    }

    private static String key(Double temp, String name) { return temp + "|" + name; }

    private boolean check(boolean condition, String label) {
        System.out.println("  " + (condition ? "[PASS] " : "[FAIL] ") + label);
        if (!condition) allHeld = false;
        return condition;
    }
}
