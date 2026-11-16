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

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.river.jeri.internal.TimeoutScheduler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Who may use the connection timeout scheduler, and that a task of the
 * scheduler cannot hold up the deadlines of the others.
 */
public class TimeoutSchedulerTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    /** A class that is not one of the callers cannot use the scheduler. */
    @Test(timeout = 30000)
    public void testAForeignCallerCannotScheduleOrHandOff() throws Exception {
        AtomicLong ran = new AtomicLong();
        try {
            TimeoutScheduler.schedule(ran::incrementAndGet, 1, TimeUnit.MILLISECONDS);
            Assert.fail("a foreign class scheduled a task");
        } catch (SecurityException expected) {
        }
        try {
            TimeoutScheduler.handOff(ran::incrementAndGet);
            Assert.fail("a foreign class handed off a task");
        } catch (SecurityException expected) {
        }
        /* Not through reflection either. */
        try {
            TimeoutScheduler.class.getMethod("schedule", Runnable.class,
                long.class, TimeUnit.class).invoke(null,
                (Runnable) ran::incrementAndGet, 1L, TimeUnit.MILLISECONDS);
            Assert.fail("a foreign class scheduled a task by reflection");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            Assert.assertTrue(String.valueOf(expected.getCause()),
                              expected.getCause() instanceof SecurityException);
        }
        Thread.sleep(300);
        Assert.assertEquals("nothing ran", 0L, ran.get());
    }

    /**
     * A class of the name of a caller, defined by another class loader (as in
     * another bundle revision or web application), is not that caller.
     */
    @Test(timeout = 30000)
    public void testAClassOfTheSameNameFromAnotherLoaderIsRefused() throws Exception {
        IsolatedLoader loader = new IsolatedLoader(getClass().getClassLoader(),
            java.util.Set.of("net.jini.jeri.ssl.HandshakeWatchdog"),
            getClass().getProtectionDomain());
        Class<?> copy = Class.forName("net.jini.jeri.ssl.HandshakeWatchdog", true, loader);
        Assert.assertNotSame("a copy", HandshakeWatchdog.class, copy);
        Assert.assertSame(loader, copy.getClassLoader());
        java.lang.reflect.Method schedule = copy.getDeclaredMethod(
            "schedule", Runnable.class, long.class);
        schedule.setAccessible(true);
        java.lang.reflect.Method handOff = copy.getDeclaredMethod(
            "handOff", Runnable.class);
        handOff.setAccessible(true);
        AtomicLong ran = new AtomicLong();
        try {
            schedule.invoke(null, (Runnable) ran::incrementAndGet, 1_000_000L);
            Assert.fail("the copy scheduled a task");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            Assert.assertTrue(String.valueOf(expected.getCause()),
                              expected.getCause() instanceof SecurityException);
        }
        try {
            handOff.invoke(null, (Runnable) ran::incrementAndGet);
            Assert.fail("the copy handed off a task");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            Assert.assertTrue(String.valueOf(expected.getCause()),
                              expected.getCause() instanceof SecurityException);
        }
        Thread.sleep(300);
        Assert.assertEquals("nothing ran", 0L, ran.get());
    }

    /**
     * A hand-off that cannot be made, nor repeated, is not dropped: the last
     * resort runs on the calling thread, which is the timer's, and the loss
     * is counted.
     */
    @Test(timeout = 30000)
    public void testAHandOffThatCannotBeMadeRunsTheLastResortAndIsCounted()
        throws Exception
    {
        AtomicReference<Thread> timer = new AtomicReference<>();
        AtomicReference<Thread> resort = new AtomicReference<>();
        AtomicLong handedOff = new AtomicLong();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        long lost = HandshakeWatchdog.lost();
        HandshakeWatchdog.inject(true, true);
        try {
            HandshakeWatchdog.schedule(() -> {
                timer.set(Thread.currentThread());
                HandshakeWatchdog.handOff(handedOff::incrementAndGet, () -> {
                    resort.set(Thread.currentThread());
                    done.countDown();
                });
            }, TimeUnit.MILLISECONDS.toNanos(20));
            Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            HandshakeWatchdog.inject(false, false);
        }
        Assert.assertSame("on the timer thread", timer.get(), resort.get());
        /* Others may lose tasks meanwhile, but this one was counted. */
        long afterLoss = HandshakeWatchdog.lost();
        Assert.assertTrue("counted: " + lost + " -> " + afterLoss, afterLoss >= lost + 1);
        /* One that can be repeated is not lost: it is handed off a second later. */
        HandshakeWatchdog.inject(true, false);
        java.util.concurrent.CountDownLatch repeated = new java.util.concurrent.CountDownLatch(1);
        AtomicLong resorted = new AtomicLong();
        try {
            HandshakeWatchdog.handOff(repeated::countDown, resorted::incrementAndGet);
            Thread.sleep(200);
        } finally {
            HandshakeWatchdog.inject(false, false);
        }
        Assert.assertTrue("handed off at the repeat", repeated.await(10, TimeUnit.SECONDS));
        Assert.assertEquals("and its last resort did not run", 0L, resorted.get());
        Assert.assertEquals(0L, handedOff.get());
    }

    /**
     * An injected failure is the injecting thread's: a hand-off of another
     * thread, and a task another thread scheduled, are not failed meanwhile,
     * and only the watchdog's class may inject.
     */
    @Test(timeout = 30000)
    public void testAnInjectedFailureIsTheInjectingThreadsOwn() throws Exception {
        try {
            TimeoutScheduler.inject(true, true);
            Assert.fail("a foreign class injected a failure");
        } catch (SecurityException expected) {
        }
        java.util.concurrent.CountDownLatch injected = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread injecting = new Thread(() -> {
            try {
                HandshakeWatchdog.inject(true, true);
                injected.countDown();
                release.await(10, TimeUnit.SECONDS);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                HandshakeWatchdog.inject(false, false);
            }
        });
        injecting.start();
        try {
            Assert.assertTrue(injected.await(10, TimeUnit.SECONDS));
            /*
             * While it is in force, this thread's hand-offs, and the tasks it
             * schedules, are not failed: a failed one, with no repeat
             * possible, would run its last resort at once.
             */
            AtomicLong resorted = new AtomicLong();
            java.util.concurrent.CountDownLatch direct = new java.util.concurrent.CountDownLatch(1);
            HandshakeWatchdog.handOff(direct::countDown, resorted::incrementAndGet);
            Assert.assertTrue("a hand-off of another thread", direct.await(5, TimeUnit.SECONDS));
            java.util.concurrent.CountDownLatch scheduled = new java.util.concurrent.CountDownLatch(1);
            HandshakeWatchdog.schedule(() -> HandshakeWatchdog.handOff(scheduled::countDown,
                resorted::incrementAndGet), TimeUnit.MILLISECONDS.toNanos(10));
            Assert.assertTrue("a task of another thread", scheduled.await(5, TimeUnit.SECONDS));
            Assert.assertEquals("and no last resort ran", 0L, resorted.get());
        } finally {
            release.countDown();
            injecting.join(10_000);
        }
        Assert.assertNull(failure.get());
    }

    /**
     * Tasks that are lost in a burst are all counted and logged once, not
     * once for each: a flood of expiries while the JVM is out of threads must
     * not become a flood of lines.
     */
    @Test(timeout = 30000)
    public void testALossBurstIsLoggedOnceAtMost() throws Exception {
        java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(TimeoutScheduler.class.getName());
        java.util.List<java.util.logging.LogRecord> records =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord r) {
                records.add(r);
            }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        long lost = HandshakeWatchdog.lost();
        int burst = 6;
        java.util.concurrent.CountDownLatch resorted = new java.util.concurrent.CountDownLatch(burst);
        HandshakeWatchdog.inject(true, true);
        try {
            for (int i = 0; i < burst; i++) {
                HandshakeWatchdog.handOff(() -> { }, resorted::countDown);
            }
            Assert.assertTrue(resorted.await(10, TimeUnit.SECONDS));
        } finally {
            HandshakeWatchdog.inject(false, false);
            logger.removeHandler(handler);
        }
        Assert.assertTrue("all counted", HandshakeWatchdog.lost() - lost >= burst);
        long lines = records.stream()
            .filter(r -> r.getLevel() == java.util.logging.Level.SEVERE).count();
        Assert.assertTrue("lines for a burst of " + burst + ": " + lines, lines <= 1);
    }

    /**
     * A handshake whose close cannot be handed off, nor the hand-off
     * repeated, is still closed at its deadline, by the watchdog's own
     * thread: the socket and the permits are not held for ever.
     */
    @Test(timeout = 30000)
    public void testAHandshakeWhoseCloseCannotBeHandedOffIsStillClosed() throws Exception {
        long lost = HandshakeWatchdog.lost();
        try (S0Fixture.Listener l = S0Fixture.listen(2, 5, 1000, null, false)) {
            S0Fixture.connectLegit(l); // warm up
            /*
             * The injection is the accept thread's, which arms the watchdog of
             * the connection, not this thread's: it fails the hand-offs of the
             * tasks that thread schedules and no others.
             */
            l.handle.beforeHandshake = () -> HandshakeWatchdog.inject(true, true);
            try (Socket s = new Socket("localhost", l.port)) {
                long ms = S0Fixture.awaitClosed(s, 6000);
                Assert.assertTrue("closed after " + ms + " ms", ms >= 0 && ms < 4000);
            } finally {
                l.handle.beforeHandshake = null;
            }
            Assert.assertTrue("counted: " + lost + " -> " + HandshakeWatchdog.lost(),
                              HandshakeWatchdog.lost() >= lost + 1);
            Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                .getFailed(HandshakeCounters.Kind.QUEUE_TIMEOUT) == 1
                && l.handle.inProgressCount() == 0
                && l.handle.admittedConnections() == 0, 5000));
        }
    }

    /** The watchdog, which is a caller, can use it, and its tasks run neutral. */
    @Test(timeout = 30000)
    public void testTheWatchdogCanScheduleAndHandOff() throws Exception {
        AtomicReference<Thread> timer = new AtomicReference<>();
        AtomicReference<Thread> handed = new AtomicReference<>();
        AtomicReference<javax.security.auth.Subject> subject =
            new AtomicReference<>(S0Fixture.server.subject);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        javax.security.auth.Subject.callAs(S0Fixture.server.subject, () -> {
            HandshakeWatchdog.schedule(() -> {
                timer.set(Thread.currentThread());
                HandshakeWatchdog.handOff(() -> {
                    handed.set(Thread.currentThread());
                    subject.set(javax.security.auth.Subject.current());
                    done.countDown();
                });
            }, TimeUnit.MILLISECONDS.toNanos(20));
            return null;
        });
        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        Assert.assertNotSame(timer.get(), handed.get());
        Assert.assertTrue("handed off to a virtual thread", handed.get().isVirtual());
        Assert.assertNull("Subject.current() on the thread handed to", subject.get());
    }

    /**
     * A task that is handed a long wait takes the thread that it is handed to,
     * not the timer's: the deadlines that follow are on time.
     */
    @Test(timeout = 30000)
    public void testABlockingHandedOffTaskDoesNotDelayOtherDeadlines() throws Exception {
        AtomicLong fired = new AtomicLong();
        long t0 = System.nanoTime();
        HandshakeWatchdog.schedule(() -> HandshakeWatchdog.handOff(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), TimeUnit.MILLISECONDS.toNanos(50));
        HandshakeWatchdog.schedule(
            () -> fired.set(System.nanoTime()), TimeUnit.MILLISECONDS.toNanos(300));
        Assert.assertTrue(S0Fixture.await(() -> fired.get() != 0, 3000));
        long ms = TimeUnit.NANOSECONDS.toMillis(fired.get() - t0);
        Assert.assertTrue("the second deadline was on time: " + ms + " ms", ms < 1500);
    }

    /** A socket whose close takes a long time, as with a linger time. */
    private static final class SlowClose extends Socket {
        final java.util.concurrent.CountDownLatch closing =
            new java.util.concurrent.CountDownLatch(1);

        @Override
        public void close() throws IOException {
            closing.countDown();
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.close();
        }
    }

    /**
     * The watchdog of a handshake whose socket is slow to close does not hold
     * up the watchdog of another: it decides, and leaves the close to a thread
     * of its own.
     */
    @Test(timeout = 30000)
    public void testASlowCloseOfAHandshakeDoesNotDelayAnotherDeadline() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.listen()) {
            SlowClose slow = new SlowClose();
            SslServerEndpointImpl.SslListenHandle.Handshake h =
                l.handle.new Handshake(slow, System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(100));
            AtomicLong fired = new AtomicLong();
            long t0 = System.nanoTime();
            h.arm();
            HandshakeWatchdog.schedule(
                () -> fired.set(System.nanoTime()), TimeUnit.MILLISECONDS.toNanos(400));
            Assert.assertTrue("the close was started", slow.closing.await(3, TimeUnit.SECONDS));
            Assert.assertTrue(S0Fixture.await(() -> fired.get() != 0, 3000));
            long ms = TimeUnit.NANOSECONDS.toMillis(fired.get() - t0);
            Assert.assertTrue("the second deadline was on time: " + ms + " ms", ms < 1500);
            Assert.assertTrue(h.isTimedOut());
        }
    }
}
