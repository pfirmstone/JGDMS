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

import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.Subject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The cryptography of a server handshake runs on a few platform threads, not
 * on the carriers of the virtual threads that also dispatch calls.
 */
public class SslServerHandshakeComputeTest {

    private static final String THREADS = HandshakeCompute.THREADS_PROPERTY;

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    private static HandshakeCompute pool(String threads) throws Exception {
        return S0Fixture.withProperty(THREADS, threads, () -> HandshakeCompute.get());
    }

    @Test
    public void testDefaultIsAQuarterOfTheProcessors() throws Exception {
        int expected = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        Assert.assertEquals(expected, HandshakeCompute.defaultThreads());
        System.clearProperty(THREADS);
        Assert.assertEquals(expected, HandshakeCompute.get().threads());
        Assert.assertEquals(3, pool("3").threads());
        Assert.assertNull("0 is inline", pool("0"));
        Assert.assertEquals("negative is the default", expected, pool("-1").threads());
        Assert.assertSame("one pool of each size", pool("3"), pool("3"));
    }

    /**
     * The task runs on a daemon platform thread of the pool with no subject,
     * and the virtual thread that asked waits for it.
     */
    @Test(timeout = 60000)
    public void testTaskRunsOnAPlatformThreadAndTheCallerWaits() throws Exception {
        HandshakeCompute c = pool("1");
        AtomicReference<Thread> ran = new AtomicReference<>();
        AtomicReference<Subject> subject = new AtomicReference<>();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Subject caller = new Subject();
        Thread vt = Thread.ofVirtual().unstarted(() ->
            Subject.callAs(caller, () -> {
                c.runner(() -> false).run(() -> {
                    ran.set(Thread.currentThread());
                    subject.set(Subject.current());
                    inside.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return null;
            }));
        vt.start();
        Assert.assertTrue(inside.await(10, TimeUnit.SECONDS));
        Assert.assertNotSame(vt, ran.get());
        Assert.assertFalse("a platform thread", ran.get().isVirtual());
        Assert.assertTrue("a daemon", ran.get().isDaemon());
        Assert.assertTrue(ran.get().getName(), ran.get().getName().startsWith("TLS handshake compute"));
        Assert.assertNull("subject-neutral", subject.get());
        Assert.assertTrue("the caller waits", S0Fixture.await(
            () -> vt.getState() == Thread.State.WAITING, 5000));
        release.countDown();
        vt.join(10_000);
        Assert.assertFalse(vt.isAlive());
    }

    /** An exception of the task reaches the thread that asked. */
    @Test(timeout = 60000)
    public void testExceptionOfTheTaskReachesTheCaller() throws Exception {
        HandshakeCompute c = pool("1");
        try {
            c.runner(() -> false).run(() -> { throw new IllegalStateException("task"); });
            Assert.fail();
        } catch (IllegalStateException expected) {
            Assert.assertEquals("task", expected.getMessage());
        }
    }

    /**
     * A task that has not started when its handshake ends is dropped, and the
     * handshake fails: the pool does not compute for a connection that is gone.
     */
    @Test(timeout = 60000)
    public void testTaskOfAnEndedHandshakeIsDropped() throws Exception {
        HandshakeCompute c = pool("1");
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread blocker = Thread.ofPlatform().start(() ->
            c.runner(() -> false).run(() -> {
                busy.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        AtomicBoolean ended = new AtomicBoolean();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiting;
        try {
            Assert.assertTrue(busy.await(10, TimeUnit.SECONDS));
            waiting = Thread.ofVirtual().start(() -> {
                try {
                    c.runner(ended::get).run(() -> ran.set(true));
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            Assert.assertTrue("queued behind the first", S0Fixture.await(
                () -> waiting.getState() == Thread.State.WAITING, 5000));
            ended.set(true);
        } finally {
            release.countDown();
        }
        waiting.join(10_000);
        blocker.join(10_000);
        Assert.assertFalse("the task must not run", ran.get());
        Assert.assertTrue("the handshake fails: " + failure.get(),
                          failure.get() instanceof UncheckedIOException);
        /* And a handshake that has ended asks for nothing. */
        try {
            c.runner(() -> true).run(() -> ran.set(true));
            Assert.fail();
        } catch (UncheckedIOException expected) {
            Assert.assertFalse(ran.get());
        }
    }

    /** A real handshake does its cryptography on the pool. */
    @Test(timeout = 60000)
    public void testHandshakeUsesThePool() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.withProperty(THREADS, "2",
                 () -> S0Fixture.listen()))
        {
            Assert.assertEquals(2, l.handle.compute.threads());
            long before = l.handle.compute.ran();
            S0Fixture.connectLegit(l);
            Assert.assertTrue("tasks run on the pool: " + (l.handle.compute.ran() - before),
                              l.handle.compute.ran() - before >= 2);
        }
    }

    /** With none, the handshake works inline as before. */
    @Test(timeout = 60000)
    public void testNoThreadsRunsInline() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.withProperty(THREADS, "0",
                 () -> S0Fixture.listen()))
        {
            Assert.assertNull(l.handle.compute);
            S0Fixture.connectLegit(l);
        }
    }

    /** A handshake that times out while its task waits for the pool ends. */
    @Test(timeout = 60000)
    public void testHandshakeThatTimesOutWhileComputingEnds() throws Exception {
        try (S0Fixture.Listener l = S0Fixture.withProperties(new String[] {
                 THREADS, "1",
                 SslServerEndpointImpl.SslListenHandle.SERVER_HANDSHAKE_TIMEOUT_PROPERTY, "1500" },
                 () -> S0Fixture.listen()))
        {
            CountDownLatch busy = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread blocker = Thread.ofPlatform().start(() ->
                l.handle.compute.runner(() -> false).run(() -> {
                    busy.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            long ran;
            try {
                Assert.assertTrue(busy.await(10, TimeUnit.SECONDS));
                /* The only thread is busy: a client's hello waits, then times out. */
                Assert.assertFalse(S0Fixture.connectOffering(l, new String[0]));
                /* The task is dropped when the pool reaches it, not computed. */
                ran = l.handle.compute.ran();
            } finally {
                release.countDown();
            }
            blocker.join(10_000);
            Assert.assertTrue("the handshake is gone", S0Fixture.await(
                () -> l.handle.inProgressCount() == 0, 10_000));
            Assert.assertEquals("the pool computed nothing for it", ran + 1,
                                l.handle.compute.ran());
            Assert.assertTrue(S0Fixture.await(() -> l.handle.counters()
                .getFailed(HandshakeCounters.Kind.TIMEOUT) >= 1, 3000));
            S0Fixture.connectLegit(l);
        }
    }
}
