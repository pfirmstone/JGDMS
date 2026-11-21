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
package org.apache.river.outrigger;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.entry.Entry;
import net.jini.id.Uuid;
import org.apache.river.outrigger.proxy.EntryRep;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression tests for the malformed-body poison write (board review 2026-11-20, finding F1)
 * and for the three places where one client-supplied rep could still change the outcome for
 * other clients (design 2026-11-21: P1 pairwise independence, P2 validate before the commit
 * point).
 *
 * <p>A client chooses the class identity that decides which stored reps a template is compared
 * with, and that identity is not bound to the {@code EntryRepV2Body}. A rep whose body has fewer
 * field slices than its claimed class therefore reaches {@link EntryRep#matches(EntryRep)}, which
 * indexed {@code theirs[f]} past the end, and the exception escaped
 * {@link OperationJournal#run()} and killed the fan-out thread for every client. Binding identity
 * to body is a separate decision and is NOT made here: these tests pin only the robustness
 * contract, that a comparison never throws, that one failed transition never kills the thread,
 * that a fault in one watcher or template changes nothing for the others, that the quick-reject
 * scans use each candidate's own field count, and that a null superclass name is refused at
 * unmarshal.
 *
 * <p>Fixtures: the production wire path is the DER encode then the {@code EntryRep(GetArg)}
 * decode, and the poison rep is built by {@link #claim}. The reviewer's probe
 * ({@code EntryRepV2ServerAdversarialFableTest}, S5a-S5d, S7) is not in the tree.
 *
 * <p>This file uses only classes that exist on the unfixed code, so it compiles and runs there
 * to show which tests fail without the change. Tests of the new counters
 * ({@code FaultTally}, {@code EntryHolder.mixedFieldCountScans}) are in separate files.
 *
 * <p>Runtime tier: fast (well under a second when green; the 5 s polling bound is only reached
 * when a journal thread has died or a watcher is silenced).
 */
public class EntryRepMalformedBodyRegressionTest {

    // ------------------------------------------------------------------ fixtures

    public static class Wide implements Entry {
        private static final long serialVersionUID = 1L;
        public String a;
        public String b;
        public Wide() {}
        public Wide(String a, String b) { this.a = a; this.b = b; }
    }

    public static class Narrow implements Entry {
        private static final long serialVersionUID = 1L;
        public String a;
        public Narrow() {}
        public Narrow(String a) { this.a = a; }
    }

    /** A subclass of {@link Narrow}: the normal "superclass template, subclass candidate" shape. */
    public static class Sub extends Narrow {
        private static final long serialVersionUID = 1L;
        public String c;
        public Sub() {}
        public Sub(String a, String c) { super(a); this.c = c; }
    }

    public static class Zero implements Entry {
        private static final long serialVersionUID = 1L;
        public Zero() {}
    }

    /** Three fields: a body longer than the Wide identity it is made to claim. */
    public static class Wide3 implements Entry {
        private static final long serialVersionUID = 1L;
        public String a;
        public String b;
        public String c;
        public Wide3() {}
        public Wide3(String a, String b, String c) { this.a = a; this.b = b; this.c = c; }
    }

    /** The production wire path: @AtomicSerial DER encode, then the EntryRep(GetArg) decode. */
    static EntryRep wire(EntryRep rep) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos);
        out.writeObject(rep);
        out.flush();
        out.close();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                ResolutionContext.of(EntryRep.class.getClassLoader()));
        return in.readObject(EntryRep.class);
    }

    static EntryRep wire(Entry entry) throws Exception {
        return wire(new EntryRep(entry));
    }

    static void set(EntryRep rep, String field, Object value) throws Exception {
        Field f = EntryRep.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(rep, value);
    }

    /** What a hostile client sends: bodySource's body under identitySource's className/hash/superclasses. */
    static EntryRep claim(EntryRep bodySource, EntryRep identitySource) throws Exception {
        set(bodySource, "className", identitySource.classFor());
        set(bodySource, "hash", Long.valueOf(identitySource.getHash()));
        set(bodySource, "superclasses", identitySource.superclasses());
        set(bodySource, "hashes", identitySource.getHashes());
        return bodySource;
    }

    /** Narrow (1 slice) body claiming Wide's identity. */
    static EntryRep narrowBodyClaimingWide() throws Exception {
        return wire(claim(new EntryRep(new Narrow("y")), new EntryRep(new Wide("z", "z"))));
    }

    /** Zero (0 slice) body claiming Wide's identity. */
    private static EntryRep zeroBodyClaimingWide() throws Exception {
        return wire(claim(new EntryRep(new Zero()), new EntryRep(new Wide("z", "z"))));
    }

    // ------------------------------------------------------------------ matches(): never throws

    @Test
    public void matches_honestNarrowTemplateAgainstHonestNarrowEntry() throws Exception {
        assertTrue(wire(new Narrow(null)).matches(wire(new Narrow("y"))));
        assertTrue(wire(new Narrow("y")).matches(wire(new Narrow("y"))));
        assertFalse(wire(new Narrow("q")).matches(wire(new Narrow("y"))));
    }

    /** S5a: a hostile wider template (Wide body, a=*, b=x) claiming Narrow, against an honest Narrow entry. */
    @Test
    public void matches_S5a_hostileWiderTemplateIsNoMatchNotAnException() throws Exception {
        EntryRep hostileTemplate = wire(claim(new EntryRep(new Wide(null, "x")), new EntryRep(new Narrow("n"))));
        assertFalse(hostileTemplate.matches(wire(new Narrow("y"))));
    }

    /** S5b: an honest Wide template (a=*, b=x) against a poison entry holding one slice. */
    @Test
    public void matches_S5b_honestTemplateAgainstOneSliceEntryIsNoMatch() throws Exception {
        assertFalse(wire(new Wide(null, "x")).matches(narrowBodyClaimingWide()));
    }

    /** S5c: an honest Wide template (a=q, b=*) against a poison entry holding no slices. */
    @Test
    public void matches_S5c_honestTemplateAgainstZeroSliceEntryIsNoMatch() throws Exception {
        assertFalse(wire(new Wide("q", null)).matches(zeroBodyClaimingWide()));
    }

    /**
     * S5d: an all-wildcard Wide template constrains no field, so it still matches the zero-slice entry.
     * This pins a policy that only malformed input can reach (an honest candidate has at least as many
     * slices as its template), so the identity-binding decision may reverse it.
     */
    @Test
    public void matches_S5d_allWildcardTemplateStillMatchesShortEntry() throws Exception {
        EntryRep allWild = wire(new Wide(null, null));
        assertTrue(allWild.matches(zeroBodyClaimingWide()));
        assertTrue(allWild.matches(narrowBodyClaimingWide()));
    }

    /**
     * A wildcard at an index the candidate lacks is skipped; a value at an index it does have still decides.
     * Like S5d, a policy only malformed input can reach, which the identity-binding decision may reverse.
     */
    @Test
    public void matches_wildcardBeyondCandidateIsSkippedButEarlierFieldsStillCompare() throws Exception {
        EntryRep oneSlice = narrowBodyClaimingWide();
        assertTrue(wire(new Wide("y", null)).matches(oneSlice));
        assertFalse(wire(new Wide("q", null)).matches(oneSlice));
    }

    /** The normal case: a superclass template (fewer slices) against a subclass candidate (more slices). */
    @Test
    public void matches_superclassTemplateAgainstSubclassCandidateStillMatches() throws Exception {
        EntryRep candidate = wire(new Sub("y", "z"));
        assertTrue(wire(new Narrow("y")).matches(candidate));
        assertTrue(wire(new Narrow(null)).matches(candidate));
        assertFalse(wire(new Narrow("q")).matches(candidate));
    }

    // ------------------------------------------------------------------ OperationJournal: thread survives

    /** Counts the transitions it is asked to process; can be told to throw on chosen attempts. */
    static class CountingWatcher extends TransitionWatcher {
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger attempts = new AtomicInteger();
        CountingWatcher() { super(System.currentTimeMillis(), 0L); }
        @Override boolean addTemplateHandle(TemplateHandle h) { return true; }
        @Override public long getExpiration() { return Long.MAX_VALUE; }
        @Override void removeIfExpired(long now) { }
        @Override boolean isInterested(EntryTransition transition, long ordinal) { return true; }
        @Override void process(EntryTransition transition, long now) {
            attempts.incrementAndGet();
            failFor(attempts.get());
            processed.incrementAndGet();
        }
        void failFor(int attempt) { }
    }

    /** Throws the given throwable on the first attempt only. */
    static final class FailsOnceWatcher extends CountingWatcher {
        private final Throwable failure;
        FailsOnceWatcher(Throwable failure) { this.failure = failure; }
        @Override void failFor(int attempt) {
            if (attempt == 1) {
                if (failure instanceof Error) throw (Error) failure;
                throw (RuntimeException) failure;
            }
        }
    }

    /** A non-null owner for the watcher tables; never dereferenced on the paths exercised here. */
    static OutriggerServerImpl stubServer() throws Exception {
        Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        Object unsafe = uf.get(null);
        return (OutriggerServerImpl) unsafe.getClass().getMethod("allocateInstance", Class.class)
                .invoke(unsafe, OutriggerServerImpl.class);
    }

    /** A journal wired to a watcher registered for a Wide template (a=*, b="x"), and its uncaught-exception record. */
    static final class Rig {
        final TransitionWatchers watchers;
        final OperationJournal journal;
        final AtomicReference<Throwable> died = new AtomicReference<Throwable>();

        /** Registers each watcher, in the order given, for the template Wide(a=*, b="x"). */
        Rig(CountingWatcher... registered) throws Exception {
            watchers = new TransitionWatchers(stubServer());
            journal = new OperationJournal(watchers);
            journal.setDaemon(true);
            journal.setUncaughtExceptionHandler((th, ex) -> died.set(ex));
            for (CountingWatcher watcher : registered) {
                watchers.add(watcher, wire(new Wide(null, "x")));
            }
            journal.start();
        }

        /** Registers a watcher for its own template; allowed while the journal runs. */
        void register(TransitionWatcher watcher, EntryRep template) {
            watchers.add(watcher, template);
        }

        void post(EntryRep rep) {
            EntryHandle h = new EntryHandle(rep, null, null, new ConcurrentLinkedQueue<EntryHandle>());
            journal.recordTransition(new EntryTransition(h, null, true, true, true));
        }

        void stop() throws InterruptedException {
            journal.terminate();
            journal.join(3000);
        }
    }

    static void await(AtomicInteger counter, int value, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (counter.get() < value && System.currentTimeMillis() < end) Thread.sleep(20);
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t == null ? "no exception" : t.getClass().getName());
        if (t != null) sb.append(": ").append(t.getMessage());
        return sb.toString();
    }

    /** Honest-only control: two matching entries are delivered, the non-matching one is not, thread alive. */
    @Test
    public void journal_control_honestEntriesOnly() throws Exception {
        CountingWatcher w = new CountingWatcher();
        Rig rig = new Rig(w);
        try {
            rig.post(wire(new Wide("1", "x")));
            rig.post(wire(new Wide("2", "x")));
            rig.post(wire(new Wide("3", "NOT-x")));
            await(w.processed, 2, 5000);
            Thread.sleep(200);
            assertEquals(2, w.processed.get());
            assertTrue(rig.journal.isAlive());
            assertNull(describe(rig.died.get()), rig.died.get());
        } finally {
            rig.stop();
        }
    }

    /**
     * S7: one poison write (a one-slice body claiming Wide) between two honest entries. The poison
     * is no match, the journal thread stays alive, and the later honest event is delivered.
     */
    @Test
    public void journal_S7_onePoisonWriteDoesNotStopLaterDelivery() throws Exception {
        CountingWatcher w = new CountingWatcher();
        Rig rig = new Rig(w);
        try {
            rig.post(wire(new Wide("1", "x")));
            await(w.processed, 1, 5000);
            assertEquals(1, w.processed.get());

            rig.post(narrowBodyClaimingWide());
            rig.post(wire(new Wide("2", "x")));
            await(w.processed, 2, 5000);

            assertNull(describe(rig.died.get()), rig.died.get());
            assertTrue("journal thread must survive one poison write", rig.journal.isAlive());
            assertEquals("the honest event posted after the poison must be delivered", 2, w.processed.get());
        } finally {
            rig.stop();
        }
    }

    /**
     * Defence in depth, independent of the matcher fix: a RuntimeException while processing one
     * transition is logged at WARNING and the thread carries on with the next node.
     */
    @Test
    public void journal_runtimeExceptionInOneTransitionIsLoggedAndSkipped() throws Exception {
        final IllegalStateException failure = new IllegalStateException("simulated poison transition");
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        capture.setLevel(Level.ALL);
        Logger opsLogger = Logger.getLogger(OutriggerServerImpl.opsLoggerName);
        opsLogger.addHandler(capture);
        FailsOnceWatcher w = new FailsOnceWatcher(failure);
        Rig rig = new Rig(w);
        try {
            rig.post(wire(new Wide("1", "x")));
            rig.post(wire(new Wide("2", "x")));
            await(w.attempts, 2, 5000);
            await(w.processed, 1, 5000);

            assertNull(describe(rig.died.get()), rig.died.get());
            assertTrue("journal thread must survive a RuntimeException", rig.journal.isAlive());
            assertEquals("the second transition must be delivered", 1, w.processed.get());

            LogRecord skipped = null;
            for (LogRecord r : records) {
                if (r.getThrown() == failure) skipped = r;
            }
            assertTrue("the skipped transition must be logged with its cause", skipped != null);
            assertEquals("a skipped transition is logged at WARNING", Level.WARNING, skipped.getLevel());
        } finally {
            opsLogger.removeHandler(capture);
            rig.stop();
        }
    }

    /** An Error is still rethrown and ends the thread: only RuntimeException is survivable. */
    @Test
    public void journal_errorStillTerminatesTheThread() throws Exception {
        final AssertionError failure = new AssertionError("simulated fatal error");
        FailsOnceWatcher w = new FailsOnceWatcher(failure);
        Rig rig = new Rig(w);
        try {
            rig.post(wire(new Wide("1", "x")));
            rig.journal.join(5000);
            assertFalse("an Error must still end the journal thread", rig.journal.isAlive());
            assertSame(failure, rig.died.get());
        } finally {
            rig.stop();
        }
    }

    // ------------------------------------------------------------------ one fault, one pair (P1)

    static String text(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) sb.append(part);
        return sb.toString();
    }

    /** Throws from process on every attempt. */
    static final class AlwaysFailsInProcess extends CountingWatcher {
        @Override void failFor(int attempt) { throw new IllegalStateException("simulated process fault"); }
    }

    /** Throws from isInterested on every attempt, so process is never reached. */
    static final class AlwaysFailsInIsInterested extends CountingWatcher {
        @Override boolean isInterested(EntryTransition transition, long ordinal) {
            attempts.incrementAndGet();
            throw new IllegalStateException("simulated isInterested fault");
        }
    }

    private static void postWide(Rig rig, int count) throws Exception {
        for (int i = 1; i <= count; i++) rig.post(wire(new Wide(String.valueOf(i), "x")));
    }

    /** A handler that keeps every record it is given. */
    static Handler capturing(final CopyOnWriteArrayList<LogRecord> records) {
        Handler capture = new Handler() {
            @Override public void publish(LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        capture.setLevel(Level.ALL);
        return capture;
    }

    static int warnings(CopyOnWriteArrayList<LogRecord> records) {
        int n = 0;
        for (LogRecord r : records) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) n++;
        }
        return n;
    }

    /**
     * T1: the watcher that sorts first throws from process on every transition. A watcher registered
     * after it must still receive every transition: a fault for one pair changes no other pair.
     */
    @Test
    public void journal_T1_throwingWatcherDoesNotSilenceALaterWatcher() throws Exception {
        AlwaysFailsInProcess thrower = new AlwaysFailsInProcess();
        CountingWatcher honest = new CountingWatcher();
        assertTrue("the thrower must sort first or this test proves nothing", thrower.compareTo(honest) < 0);
        Rig rig = new Rig(thrower, honest);
        try {
            postWide(rig, 3);
            await(thrower.attempts, 3, 5000);
            await(honest.processed, 3, 5000);

            assertNull(describe(rig.died.get()), rig.died.get());
            assertTrue(rig.journal.isAlive());
            assertEquals("the thrower is still called for every transition", 3, thrower.attempts.get());
            assertEquals("the honest watcher must receive every transition", 3, honest.processed.get());
        } finally {
            rig.stop();
        }
    }

    /** T1 control: the same two watchers, the honest one sorted first. Reads the same with or without the change. */
    @Test
    public void journal_T1control_honestWatcherSortedFirstReceivesEveryTransition() throws Exception {
        CountingWatcher honest = new CountingWatcher();
        AlwaysFailsInProcess thrower = new AlwaysFailsInProcess();
        assertTrue("the honest watcher must sort first in the control", honest.compareTo(thrower) < 0);
        Rig rig = new Rig(honest, thrower);
        try {
            postWide(rig, 3);
            await(thrower.attempts, 3, 5000);
            await(honest.processed, 3, 5000);

            assertTrue(rig.journal.isAlive());
            assertEquals(3, thrower.attempts.get());
            assertEquals(3, honest.processed.get());
        } finally {
            rig.stop();
        }
    }

    /**
     * T2: a watcher whose isInterested throws is collected together with an honest one. The throw must not
     * discard the honest watcher from the set collected for the transition.
     */
    @Test
    public void journal_T2_isInterestedFaultDoesNotDiscardOtherWatchers() throws Exception {
        AlwaysFailsInIsInterested thrower = new AlwaysFailsInIsInterested();
        CountingWatcher honest = new CountingWatcher();
        Rig rig = new Rig(thrower, honest);
        try {
            postWide(rig, 3);
            await(thrower.attempts, 3, 5000);
            await(honest.processed, 3, 5000);

            assertNull(describe(rig.died.get()), rig.died.get());
            assertTrue(rig.journal.isAlive());
            assertEquals("isInterested is still called for every transition", 3, thrower.attempts.get());
            assertEquals("process is never reached for the thrower", 0, thrower.processed.get());
            assertEquals("the honest watcher must receive every transition", 3, honest.processed.get());
        } finally {
            rig.stop();
        }
    }

    /**
     * T3: one registered template faults while it is compared with the transitioning entry (its quick-reject
     * descriptor cannot be computed). The watcher of another template for the same class must still be told.
     * The fault is injected by clearing the template's absent-field marker; a client cannot reach it today,
     * so this pins the isolation boundary, not an attack.
     */
    @Test
    public void journal_T3_templateFaultDoesNotDiscardOtherTemplates() throws Exception {
        EntryRep faulty = wire(new Wide(null, "x"));
        set(faulty, "absent", null);
        try {
            EntryHandle.descFor(faulty, 2);
            fail("precondition: descFor must throw for the injected template");
        } catch (NullPointerException expected) {
            // the injected fault
        }
        CountingWatcher faultyWatcher = new CountingWatcher();
        CountingWatcher honest = new CountingWatcher();
        Rig rig = new Rig();
        try {
            rig.register(faultyWatcher, faulty);
            rig.register(honest, wire(new Wide("1", null)));
            for (int i = 0; i < 3; i++) rig.post(wire(new Wide("1", "x")));
            await(honest.processed, 3, 5000);

            assertNull(describe(rig.died.get()), rig.died.get());
            assertTrue(rig.journal.isAlive());
            assertEquals("the honest template's watcher must receive every transition", 3, honest.processed.get());
            assertEquals("the faulty template's watcher is never reached", 0, faultyWatcher.processed.get());
        } finally {
            rig.stop();
        }
    }

    /**
     * T4: 50 failures of one watcher must not write 50 WARNING records from the journal thread. The first
     * is logged (a fault is never silent); the rest are rate limited.
     */
    @Test
    public void journal_T4_oneFailingWatcherDoesNotFloodTheLog() throws Exception {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        Logger opsLogger = Logger.getLogger(OutriggerServerImpl.opsLoggerName);
        opsLogger.addHandler(capture);
        AlwaysFailsInProcess thrower = new AlwaysFailsInProcess();
        Rig rig = new Rig(thrower);
        try {
            postWide(rig, 50);
            await(thrower.attempts, 50, 10000);
            Thread.sleep(300);

            assertEquals(50, thrower.attempts.get());
            int logged = warnings(records);
            assertTrue(text("50 failures of one watcher logged ", logged, " WARNING records, expected 1 or 2"),
                    logged >= 1 && logged <= 2);
        } finally {
            opsLogger.removeHandler(capture);
            rig.stop();
        }
    }

    /** T4 control: 50 honest transitions write no WARNING record. Reads the same with or without the change. */
    @Test
    public void journal_T4control_honestTransitionsWriteNoWarning() throws Exception {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        Logger opsLogger = Logger.getLogger(OutriggerServerImpl.opsLoggerName);
        opsLogger.addHandler(capture);
        CountingWatcher honest = new CountingWatcher();
        Rig rig = new Rig(honest);
        try {
            postWide(rig, 50);
            await(honest.processed, 50, 10000);
            Thread.sleep(300);

            assertEquals(50, honest.processed.get());
            assertEquals(0, warnings(records));
        } finally {
            opsLogger.removeHandler(capture);
            rig.stop();
        }
    }

    // ------------------------------------------------------------------ scans: the descriptor follows the candidate (P1)

    /** Builds the entry that is filed first in a holder; may be null for the no-poison control. */
    interface PoisonFactory {
        EntryRep poison() throws Exception;
    }

    /** A 1-slice body claiming Wide, with a="y". */
    static final PoisonFactory SHORT_POISON = new PoisonFactory() {
        @Override public EntryRep poison() throws Exception { return narrowBodyClaimingWide(); }
    };

    /** A 3-slice body claiming Wide, with a="y" and b not "x", so Wide(y,x) does not match it. */
    static final PoisonFactory LONG_POISON = new PoisonFactory() {
        @Override public EntryRep poison() throws Exception {
            return wire(claim(new EntryRep(new Wide3("y", "NOT-x", "z")), new EntryRep(new Wide("z", "z"))));
        }
    };

    static EntryHolder newHolder() throws Exception {
        return new EntryHolder(stubServer(), new ConcurrentHashMap<Uuid, EntryHandle>());
    }

    static EntryHandle put(EntryHolder holder, EntryRep rep) {
        if (rep.id() == null) rep.pickID();
        rep.setExpiration(Long.MAX_VALUE);
        EntryHandle handle = holder.newEntryHandle(rep, null);
        holder.add(handle, null);
        return handle;
    }

    /** Files the poison (if any) first, then three honest Wide entries; returns the honest Wide(y,x) rep. */
    static EntryRep fill(EntryHolder holder, PoisonFactory poison) throws Exception {
        if (poison != null) put(holder, poison.poison());
        EntryRep honest = wire(new Wide("y", "x"));
        put(holder, honest);
        put(holder, wire(new Wide("q", "x")));
        put(holder, wire(new Wide("r", "x")));
        return honest;
    }

    /** The honest template Wide(y,x); asserts that it matches the honest entry. */
    private static EntryRep template(EntryRep honest) throws Exception {
        EntryRep tmpl = wire(new Wide("y", "x"));
        assertTrue("the template must match the honest entry", tmpl.matches(honest));
        return tmpl;
    }

    /** In how many of {@code trials} fresh holders does hasMatch return the honest entry. */
    private static int hasMatchFinds(PoisonFactory poison, int trials) throws Exception {
        int found = 0;
        for (int i = 0; i < trials; i++) {
            EntryHolder holder = newHolder();
            EntryRep honest = fill(holder, poison);
            EntryHandle handle = holder.hasMatch(template(honest), null, false, null, null, null);
            if (handle != null && handle.rep() == honest) found++;
        }
        return found;
    }

    /** Whether the continuing query (contents, take multiple) returns the honest entry. */
    private static boolean nextFindsHonest(PoisonFactory poison) throws Exception {
        EntryHolder holder = newHolder();
        EntryRep honest = fill(holder, poison);
        EntryHolder.ContinuingQuery query = holder.continuingQuery(
                new EntryRep[] { template(honest) }, null, false, System.currentTimeMillis());
        EntryHandle handle = query.next(null, null, null);
        return handle != null && handle.rep() == honest;
    }

    /**
     * S1: hasMatch, one 1-slice poison entry filed beside the honest ones. The candidates of the
     * index bucket come in hash order, so over 40 holders the poison is first in about half. A
     * descriptor built from the first candidate's field count rejected the honest entry then.
     */
    @Test
    public void scan_S1_hasMatchFindsTheHonestEntryBesideAShortPoison() throws Exception {
        int found = hasMatchFinds(SHORT_POISON, 40);
        assertEquals(text("hasMatch with a 1-slice poison beside the honest entries found it in ", found, " of 40"),
                40, found);
    }

    /** S2: the continuing query; the poison is filed first, so it is the first candidate every time. */
    @Test
    public void scan_S2_continuingQueryFindsTheHonestEntryAfterAShortPoison() throws Exception {
        assertTrue("next() must return the honest entry filed after a 1-slice poison", nextFindsHonest(SHORT_POISON));
    }

    /**
     * S3: a poison with MORE slices than its class. It never threw in matches, so a loud failure never
     * hid it, but the first-candidate descriptor made the honest entry invisible the same way.
     */
    @Test
    public void scan_S3_hasMatchFindsTheHonestEntryBesideALongPoison() throws Exception {
        int found = hasMatchFinds(LONG_POISON, 40);
        assertEquals(text("hasMatch with a 3-slice poison beside the honest entries found it in ", found, " of 40"),
                40, found);
    }

    @Test
    public void scan_S3_continuingQueryFindsTheHonestEntryAfterALongPoison() throws Exception {
        assertTrue("next() must return the honest entry filed after a 3-slice poison", nextFindsHonest(LONG_POISON));
    }

    /** S1/S2 control: no poison. Reads the same with or without the change. */
    @Test
    public void scan_control_honestEntriesOnly() throws Exception {
        assertEquals(40, hasMatchFinds(null, 40));
        assertTrue(nextFindsHonest(null));
    }

    // ------------------------------------------------------------------ unmarshal refuses a null superclass name (P2)

    private static EntryRep withSuperclasses(String[] names, long[] hashes) throws Exception {
        EntryRep rep = new EntryRep(new Wide("a", "b"));
        set(rep, "superclasses", names);
        set(rep, "hashes", hashes);
        return rep;
    }

    private static InvalidObjectException invalidObject(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InvalidObjectException) return (InvalidObjectException) c;
        }
        return null;
    }

    /**
     * N1: a null element of superclasses is used as a class name by the server (a null key in
     * allMatches and the type tree), so the production decode must refuse it.
     */
    @Test
    public void wire_N1_nullSuperclassElementIsRefusedAtUnmarshal() throws Exception {
        EntryRep[] poisoned = {
            withSuperclasses(new String[] { "probe.X", null }, new long[] { 1L, 2L }),
            withSuperclasses(new String[] { null }, new long[] { 0L }),
        };
        for (EntryRep rep : poisoned) {
            EntryRep decoded = null;
            Exception refused = null;
            try {
                decoded = wire(rep);
            } catch (Exception e) {
                refused = e;
            }
            assertNull(text("a null superclasses element was accepted: ",
                    decoded == null ? null : java.util.Arrays.toString(decoded.superclasses())), decoded);
            assertTrue(text("refusal must be an InvalidObjectException, was ", refused),
                    invalidObject(refused) != null);
        }
    }

    /** N1 control: superclasses without a null element decode and keep their names. Reads the same either way. */
    @Test
    public void wire_N1control_honestSuperclassesAreAccepted() throws Exception {
        EntryRep decoded = wire(withSuperclasses(new String[] { "probe.X", "probe.Y" }, new long[] { 1L, 2L }));
        assertEquals(2, decoded.superclasses().length);
        assertEquals("probe.X", decoded.superclasses()[0]);
        assertEquals("probe.Y", decoded.superclasses()[1]);
    }
}
