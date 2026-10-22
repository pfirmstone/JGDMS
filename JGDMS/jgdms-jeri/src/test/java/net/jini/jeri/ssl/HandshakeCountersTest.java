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
package net.jini.jeri.ssl;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The summary line of the handshake counters: written for the failures that
 * come last as well as for those that come in a run, once at most for each
 * interval, and when the listener closes.
 */
public class HandshakeCountersTest {

    private static final String INTERVAL =
        "org.apache.river.jeri.ssl.handshakeSummaryInterval";

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /** Collects the INFO records of a logger of its own. */
    private static final class Lines extends java.util.logging.Handler {
        final List<String> lines = new ArrayList<>();

        public synchronized void publish(LogRecord r) {
            lines.add(new java.util.logging.SimpleFormatter().formatMessage(r));
        }

        public void flush() { }

        public void close() { }

        synchronized List<String> copy() {
            return new ArrayList<>(lines);
        }
    }

    private static Logger logger(Lines lines) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        logger.addHandler(lines);
        return logger;
    }

    private static HandshakeCounters counters(Logger logger, long intervalMillis)
        throws Exception
    {
        return S0Fixture.withProperty(INTERVAL, Long.toString(intervalMillis),
            () -> new HandshakeCounters(logger, "listener-x"));
    }

    /**
     * Failures that come shortly after the start and then stop are reported
     * within about an interval, although no more failures come to write the
     * line: the line is written by a timer.
     */
    @Test(timeout = 30000)
    public void testFailuresThenSilenceAreReported() throws Exception {
        Lines lines = new Lines();
        HandshakeCounters c = counters(logger(lines), 500);
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            c.noteAccepted();
        }
        c.noteCompleted();
        c.noteFailed(HandshakeCounters.Kind.TLS);
        c.noteFailed(HandshakeCounters.Kind.TLS);
        c.noteFailed(HandshakeCounters.Kind.PEER_CLOSED);
        Assert.assertEquals("not at once: the interval has not passed", 0,
                            lines.copy().size());
        Assert.assertTrue("a line is written by the timer: " + lines.copy(),
                          S0Fixture.await(() -> lines.copy().size() == 1, 5000));
        long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - t0);
        Assert.assertTrue("after about an interval: " + millis + " ms",
                          millis >= 400 && millis < 4000);
        String line = lines.copy().get(0);
        Assert.assertTrue(line, line.startsWith("listener-x: "));
        Assert.assertTrue(line, line.contains("failed 3 "));
        Assert.assertTrue(line, line.contains("TLS failure 2"));
        Assert.assertTrue(line, line.contains("peer closed 1"));
        Assert.assertTrue(line, line.contains("accepted 3, completed 1 in the last"));
        Assert.assertFalse("no counts of different sets put against each other: "
                           + line, line.contains(" of "));
        /* Nothing more to report: no more lines. */
        Thread.sleep(1500);
        Assert.assertEquals(lines.copy().toString(), 1, lines.copy().size());
        c.close();
        Assert.assertEquals("nothing is left to report at close",
                            1, lines.copy().size());
    }

    /** Closing the listener writes what the interval has not yet let out. */
    @Test(timeout = 30000)
    public void testCloseReportsWhatIsLeft() throws Exception {
        Lines lines = new Lines();
        HandshakeCounters c = counters(logger(lines), 60_000);
        c.noteAccepted();
        c.noteFailed(HandshakeCounters.Kind.JUNK);
        c.noteFailed(HandshakeCounters.Kind.JUNK);
        Assert.assertEquals(0, lines.copy().size());
        c.close();
        Assert.assertEquals(lines.copy().toString(), 1, lines.copy().size());
        String line = lines.copy().get(0);
        Assert.assertTrue(line, line.contains("failed 2 (not TLS 2), accepted 1, "
                                              + "completed 0 in the last"));
        c.close();
        Assert.assertEquals("and only once", 1, lines.copy().size());
    }

    /** With nothing to report there is no line: not at close, not later. */
    @Test(timeout = 30000)
    public void testNothingToReportWritesNothing() throws Exception {
        Lines lines = new Lines();
        HandshakeCounters c = counters(logger(lines), 300);
        c.noteAccepted();
        c.noteAccepted();
        c.noteCompleted();
        c.noteCompleted();
        c.close();
        Thread.sleep(900);
        Assert.assertEquals(lines.copy().toString(), 0, lines.copy().size());
    }

    /** An interval of 0 disables the line altogether. */
    @Test(timeout = 30000)
    public void testNoIntervalWritesNothing() throws Exception {
        Lines lines = new Lines();
        HandshakeCounters c = counters(logger(lines), 0);
        c.noteFailed(HandshakeCounters.Kind.TLS);
        c.close();
        Thread.sleep(300);
        Assert.assertEquals(0, lines.copy().size());
        Assert.assertEquals("but it is counted", 1, c.getFailed());
    }

    /** One line for each interval at most, however many failures come. */
    @Test(timeout = 30000)
    public void testAtMostOneLineForEachInterval() throws Exception {
        Lines lines = new Lines();
        HandshakeCounters c = counters(logger(lines), 400);
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1700);
        int failures = 0;
        while (System.nanoTime() < end) {
            c.noteFailed(HandshakeCounters.Kind.TIMEOUT);
            failures++;
            Thread.sleep(5);
        }
        Assert.assertTrue(S0Fixture.await(() -> lines.copy().size() >= 3, 3000));
        c.close();
        Thread.sleep(200);
        int n = lines.copy().size();
        /* 1700 ms in intervals of 400 ms: lines at about 400, 800, 1200 and
         * 1600 ms, the last failures at close or at 2000 ms. */
        Assert.assertTrue("lines: " + lines.copy(), n >= 3 && n <= 6);
        long reported = 0;
        for (String line : lines.copy()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("failed (\\d+) ").matcher(line);
            Assert.assertTrue(line, m.find());
            reported += Long.parseLong(m.group(1));
        }
        Assert.assertEquals("every failure is reported once", failures, reported);
    }

    /**
     * The timer is one task for the counters, and closing cancels it: a
     * flood of failures leaves one task in the queue, and none after close.
     */
    @Test(timeout = 30000)
    public void testOneTimerTaskAtMostAndClosingCancelsIt() throws Exception {
        Lines lines = new Lines();
        int before = HandshakeWatchdog.pending();
        HandshakeCounters c = counters(logger(lines), 60_000);
        for (int i = 0; i < 1000; i++) {
            c.noteFailed(HandshakeCounters.Kind.IO);
        }
        Assert.assertEquals("one task for a thousand failures",
                            before + 1, HandshakeWatchdog.pending());
        c.close();
        Assert.assertEquals("cancelled at close", before, HandshakeWatchdog.pending());
        c.noteFailed(HandshakeCounters.Kind.IO);
        Assert.assertEquals("and none is started after it", before,
                            HandshakeWatchdog.pending());
    }

    /** Returns the number of junk connections that the lines report. */
    private static long junk(Lines lines) {
        long n = 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("not TLS (\\d+)");
        for (String line : lines.copy()) {
            java.util.regex.Matcher m = p.matcher(line);
            while (m.find()) {
                n += Long.parseLong(m.group(1));
            }
        }
        return n;
    }

    /**
     * A listener that is closed writes the failures of its handshakes: junk
     * sent to a listener is reported when the listener closes, or after an
     * interval, whichever is first.
     */
    @Test(timeout = 60000)
    public void testAListenerReportsItsFailures() throws Exception {
        Lines lines = new Lines();
        Logger server = Utilities.SERVER_LOGGER;
        Level level = server.getLevel();
        server.addHandler(lines);
        if (!server.isLoggable(Level.INFO)) {
            server.setLevel(Level.INFO);
        }
        try {
            try (S0Fixture.Listener l = S0Fixture.withProperty(INTERVAL, "600",
                     () -> S0Fixture.listen()))
            {
                S0Fixture.connectLegit(l);
                S0Fixture.sendJunk(l);
                S0Fixture.sendJunk(l);
                /* In one line or two, according to how long the start took. */
                Assert.assertTrue("reported within an interval: " + lines.copy(),
                    S0Fixture.await(() -> junk(lines) == 2, 5000));
            }
            try (S0Fixture.Listener l = S0Fixture.withProperty(INTERVAL, "60000",
                     () -> S0Fixture.listen()))
            {
                S0Fixture.sendJunk(l);
                Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                    .getFailed(HandshakeCounters.Kind.JUNK) == 1, 5000));
                int before = lines.copy().size();
                l.close();
                Assert.assertEquals("the close wrote the line", before + 1,
                                    lines.copy().size());
                Assert.assertTrue(lines.copy().get(before),
                    lines.copy().get(before).contains("failed 1 (not TLS 1)"));
            }
        } finally {
            server.removeHandler(lines);
            server.setLevel(level);
        }
    }
}
