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

import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.AlwaysFailsInProcess;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.CountingWatcher;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.Rig;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.Wide;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.await;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.capturing;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.set;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.text;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.warnings;
import static org.apache.river.outrigger.EntryRepMalformedBodyRegressionTest.wire;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.river.outrigger.proxy.EntryRep;
import org.junit.Test;

/**
 * Tests of the counter and the rate-limited log that isolate one watcher's or one template's faults
 * on the journal thread (design 2026-11-21, Q1). The isolation itself is pinned, against the unfixed
 * code too, in {@link EntryRepMalformedBodyRegressionTest}; these tests use the new
 * {@code FaultTally} API and so only exist with the change.
 *
 * <p>Runtime tier: fast (one 400 ms sleep).
 */
public class FaultTallyTest {

    private static final Logger OPS = Logger.getLogger(OutriggerServerImpl.opsLoggerName);

    private static RuntimeException fault(String what) {
        return new IllegalStateException(what);
    }

    /** Every fault is counted; only the first is logged, with its stack, while the interval has not elapsed. */
    @Test
    public void countsEveryFaultAndLogsOnlyTheFirstWithinTheInterval() {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        OPS.addHandler(capture);
        try {
            FaultTally tally = new FaultTally(TimeUnit.HOURS.toNanos(1L));
            RuntimeException first = fault("first");
            long totalBefore = FaultTally.total();
            tally.record("phase", "subject", first);
            for (int i = 0; i < 4; i++) tally.record("phase", "subject", fault("later"));

            assertEquals(5L, tally.count());
            assertEquals(5L, FaultTally.total() - totalBefore);
            assertEquals(1, warnings(records));
            assertSame("the first fault is logged with its stack", first, records.get(0).getThrown());
            assertEquals(Level.WARNING, records.get(0).getLevel());
        } finally {
            OPS.removeHandler(capture);
        }
    }

    /** With no interval every fault is logged; each later line is stackless and carries its number. */
    @Test
    public void laterLinesAreStacklessAndNumbered() {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        OPS.addHandler(capture);
        try {
            FaultTally tally = new FaultTally(0L);
            tally.record("phase", "subject", fault("one"));
            tally.record("phase", "subject", fault("two"));
            tally.record("phase", "subject", fault("three"));

            assertEquals(3, warnings(records));
            assertTrue(records.get(0).getThrown() != null);
            assertNull(records.get(1).getThrown());
            assertNull(records.get(2).getThrown());
            assertTrue(records.get(2).getMessage(), records.get(2).getMessage().contains("fault 3"));
        } finally {
            OPS.removeHandler(capture);
        }
    }

    /** Faults inside the interval are counted in the next line that is written after it. */
    @Test
    public void aLineAfterTheIntervalReportsTheSuppressedFaults() throws InterruptedException {
        final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
        Handler capture = capturing(records);
        OPS.addHandler(capture);
        try {
            FaultTally tally = new FaultTally(TimeUnit.MILLISECONDS.toNanos(200L));
            tally.record("phase", "subject", fault("one"));
            tally.record("phase", "subject", fault("two"));
            tally.record("phase", "subject", fault("three"));
            assertEquals(1, warnings(records));
            Thread.sleep(400L);
            tally.record("phase", "subject", fault("four"));

            assertEquals(2, warnings(records));
            String line = records.get(1).getMessage();
            assertTrue(line, line.contains("fault 4") && line.contains("2 not logged"));
        } finally {
            OPS.removeHandler(capture);
        }
    }

    /** A logging handler that fails must not end the journal thread. */
    @Test
    public void aFailingLogHandlerDoesNotPropagate() {
        Handler failing = new Handler() {
            @Override public void publish(LogRecord r) { throw new IllegalStateException("log sink down"); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        failing.setLevel(Level.ALL);
        OPS.addHandler(failing);
        try {
            FaultTally tally = new FaultTally(0L);
            tally.record("phase", "subject", fault("one"));
            tally.record("phase", "subject", fault("two"));
            assertEquals(2L, tally.count());
        } finally {
            OPS.removeHandler(failing);
        }
    }

    /** The journal counts a failing watcher's faults on that watcher only. */
    @Test
    public void watcherFaultsAreCountedOnTheWatcherThatFaulted() throws Exception {
        AlwaysFailsInProcess thrower = new AlwaysFailsInProcess();
        CountingWatcher honest = new CountingWatcher();
        Rig rig = new Rig(thrower, honest);
        try {
            for (int i = 1; i <= 3; i++) rig.post(wire(new Wide(String.valueOf(i), "x")));
            await(honest.processed, 3, 5000);

            assertEquals(text("faults counted on the thrower: ", thrower.faultCount()), 3L, thrower.faultCount());
            assertEquals(0L, honest.faultCount());
        } finally {
            rig.stop();
        }
    }

    /** A faulting template is counted in the VM-wide total. */
    @Test
    public void templateFaultsAreCounted() throws Exception {
        EntryRep faulty = wire(new Wide(null, "x"));
        set(faulty, "absent", null);
        CountingWatcher faultyWatcher = new CountingWatcher();
        CountingWatcher honest = new CountingWatcher();
        Rig rig = new Rig();
        long before = FaultTally.total();
        try {
            rig.register(faultyWatcher, faulty);
            rig.register(honest, wire(new Wide("1", null)));
            for (int i = 0; i < 3; i++) rig.post(wire(new Wide("1", "x")));
            await(honest.processed, 3, 5000);

            assertEquals(3, honest.processed.get());
            assertEquals(3L, FaultTally.total() - before);
        } finally {
            rig.stop();
        }
    }
}
