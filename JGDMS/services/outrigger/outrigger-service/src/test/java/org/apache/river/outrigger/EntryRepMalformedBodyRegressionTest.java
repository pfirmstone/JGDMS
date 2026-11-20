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
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.entry.Entry;
import org.apache.river.outrigger.proxy.EntryRep;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the malformed-body poison write (board review 2026-11-20, finding F1).
 *
 * <p>A client chooses the class identity that decides which stored reps a template is compared
 * with, and that identity is not bound to the {@code EntryRepV2Body}. A rep whose body has fewer
 * field slices than its claimed class therefore reaches {@link EntryRep#matches(EntryRep)}, which
 * indexed {@code theirs[f]} past the end, and the exception escaped
 * {@link OperationJournal#run()} and killed the fan-out thread for every client. Binding identity
 * to body is a separate decision and is NOT made here: these tests pin only the robustness
 * contract, that a comparison never throws and that one failed transition never kills the thread.
 *
 * <p>Fixtures follow the reviewer's probe {@code EntryRepV2ServerAdversarialFableTest}
 * (S5a-S5d, S7, honest controls): the production wire path is the DER encode then the
 * {@code EntryRep(GetArg)} decode, and the poison rep is built by {@link #claim}.
 *
 * <p>Runtime tier: fast (well under a second when green; the 5 s polling bound is only reached
 * when a journal thread has died).
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

    /** The production wire path: @AtomicSerial DER encode, then the EntryRep(GetArg) decode. */
    private static EntryRep wire(EntryRep rep) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos);
        out.writeObject(rep);
        out.flush();
        out.close();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                ResolutionContext.of(EntryRep.class.getClassLoader()));
        return in.readObject(EntryRep.class);
    }

    private static EntryRep wire(Entry entry) throws Exception {
        return wire(new EntryRep(entry));
    }

    private static void set(EntryRep rep, String field, Object value) throws Exception {
        Field f = EntryRep.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(rep, value);
    }

    /** What a hostile client sends: bodySource's body under identitySource's className/hash/superclasses. */
    private static EntryRep claim(EntryRep bodySource, EntryRep identitySource) throws Exception {
        set(bodySource, "className", identitySource.classFor());
        set(bodySource, "hash", Long.valueOf(identitySource.getHash()));
        set(bodySource, "superclasses", identitySource.superclasses());
        set(bodySource, "hashes", identitySource.getHashes());
        return bodySource;
    }

    /** Narrow (1 slice) body claiming Wide's identity. */
    private static EntryRep narrowBodyClaimingWide() throws Exception {
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

    /** S5d: an all-wildcard Wide template constrains no field, so it still matches the zero-slice entry. */
    @Test
    public void matches_S5d_allWildcardTemplateStillMatchesShortEntry() throws Exception {
        EntryRep allWild = wire(new Wide(null, null));
        assertTrue(allWild.matches(zeroBodyClaimingWide()));
        assertTrue(allWild.matches(narrowBodyClaimingWide()));
    }

    /** A wildcard at an index the candidate lacks is skipped; a value at an index it does have still decides. */
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
    private static OutriggerServerImpl stubServer() throws Exception {
        Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        Object unsafe = uf.get(null);
        return (OutriggerServerImpl) unsafe.getClass().getMethod("allocateInstance", Class.class)
                .invoke(unsafe, OutriggerServerImpl.class);
    }

    /** A journal wired to a watcher registered for a Wide template (a=*, b="x"), and its uncaught-exception record. */
    private static final class Rig {
        final TransitionWatchers watchers;
        final OperationJournal journal;
        final AtomicReference<Throwable> died = new AtomicReference<Throwable>();

        Rig(CountingWatcher watcher) throws Exception {
            watchers = new TransitionWatchers(stubServer());
            journal = new OperationJournal(watchers);
            journal.setDaemon(true);
            journal.setUncaughtExceptionHandler((th, ex) -> died.set(ex));
            watchers.add(watcher, wire(new Wide(null, "x")));
            journal.start();
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

    private static void await(AtomicInteger counter, int value, long millis) throws InterruptedException {
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
}
