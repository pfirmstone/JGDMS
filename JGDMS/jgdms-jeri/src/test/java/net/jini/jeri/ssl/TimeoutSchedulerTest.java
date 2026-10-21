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
