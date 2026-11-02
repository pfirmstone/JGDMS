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
package au.zeus.jgdms.security.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Behaviour of {@link BoundedBody} with no SecurityManager; the SecurityManager cases are in {@link WatchdogSecurityManagerTest}. */
public class BoundedBodyTest {

    /** Blocks in read() until closed. */
    private static class BlockingUntilClosed extends InputStream {
        final CountDownLatch closed = new CountDownLatch(1);
        final boolean eofAfterClose;

        BlockingUntilClosed(boolean eofAfterClose) {
            this.eofAfterClose = eofAfterClose;
        }

        @Override
        public int read() throws IOException {
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            if (eofAfterClose) return -1;
            throw new IOException("closed");
        }

        @Override
        public void close() throws IOException {
            closed.countDown();
        }
    }

    /**
     * Invariant: a body whose reader sees a normal end of data only because the
     * watchdog closed the stream is a timeout, never a (truncated) body.
     */
    @Test
    public void aStreamThatReturnsEofAfterTheWatchdogClosedItIsATimeout() throws Exception {
        try {
            byte[] b = BoundedBody.read(new BlockingUntilClosed(true), 10, 100);
            fail("a truncated body was returned: " + b.length + " bytes");
        } catch (SocketTimeoutException expected) {
            // the post-read check
        }
    }

    @Test
    public void aThrowingStreamAfterTheDeadlineIsATimeoutAndOtherFailuresAreNot() throws Exception {
        try {
            BoundedBody.read(new BlockingUntilClosed(false), 10, 100);
            fail("no timeout");
        } catch (SocketTimeoutException expected) {
            // expected
        }
        try {
            BoundedBody.read(new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("boom");
                }
            }, 10, 1000);
            fail("no exception");
        } catch (SocketTimeoutException e) {
            fail("a plain failure was reported as a timeout");
        } catch (IOException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    @Test
    public void aBodyReadWithinTheDeadlineIsReturned() throws Exception {
        assertArrayEquals(new byte[] {1, 2, 3},
                BoundedBody.read(new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), 10, 1000));
    }

    /**
     * Invariant: one close() that blocks must not delay another read's deadline
     * by more than a bounded amount.  With one thread that both fires the
     * deadlines and closes, the second read below ended after about 2 s.
     */
    @Test(timeout = 20000)
    public void aBlockingCloseDoesNotDelayAnotherDeadline() throws Exception {
        final CountDownLatch releaseClose = new CountDownLatch(1);
        final BlockingUntilClosed slowClose = new BlockingUntilClosed(false) {
            @Override
            public void close() throws IOException {
                super.close();
                try {
                    releaseClose.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        final AtomicReference<Throwable> first = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                BoundedBody.read(slowClose, 10, 100);
            } catch (Throwable x) {
                first.set(x);
            }
        });
        t.start();
        Thread.sleep(20);
        long start = System.nanoTime();
        try {
            BoundedBody.read(new BlockingUntilClosed(true), 10, 100);
            fail("no timeout");
        } catch (SocketTimeoutException expected) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("second deadline of 100 ms took " + ms + " ms behind a blocked close", ms < 1000);
        } finally {
            releaseClose.countDown();
            t.join(5000);
        }
        assertTrue("first read: " + first.get(), first.get() instanceof SocketTimeoutException);
    }

    /** A stream whose close() releases the reader at once and then blocks until {@code release} (or 3 s). */
    private static final class SlowClose extends BlockingUntilClosed {
        private final CountDownLatch release;

        SlowClose(CountDownLatch release) {
            super(false);
            this.release = release;
        }

        @Override
        public void close() throws IOException {
            super.close();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Result of one read on its own thread: how long it took and what it threw. */
    private static final class Timed {
        volatile long ms = -1;
        volatile Throwable thrown;
        final Thread thread;

        Timed(InputStream s, long deadline) {
            thread = new Thread(() -> {
                long t0 = System.nanoTime();
                try {
                    BoundedBody.read(s, 10, deadline);
                } catch (Throwable t) {
                    thrown = t;
                }
                ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Invariant: every deadline is met within a small margin whatever the closes of other
     * streams do.  Eight reads whose close() blocks for 3 s fill the four closer threads
     * and queue the rest; an unrelated read with a 100 ms deadline started after they
     * expired must still time out within deadline + 500 ms, and so must each of the eight
     * (a reader never waits for its own close).  On trunk the unrelated read took 2.8 s.
     */
    @Test(timeout = 60000)
    public void blockedClosesDelayNoDeadlineAndNoReader() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        try {
            Timed[] blocked = new Timed[8];
            for (int i = 0; i < blocked.length; i++) blocked[i] = new Timed(new SlowClose(release), 100);
            Thread.sleep(300);
            Timed unrelated = new Timed(new BlockingUntilClosed(true), 100);
            unrelated.thread.join(10000);
            assertTrue("unrelated read took " + unrelated.ms + " ms behind blocked closes: " + unrelated.thrown,
                    unrelated.thrown instanceof SocketTimeoutException);
            assertTrue("unrelated 100 ms deadline took " + unrelated.ms + " ms", unrelated.ms <= 600);
            for (Timed t : blocked) {
                t.thread.join(10000);
                assertTrue("blocked reader: " + t.thrown, t.thrown instanceof SocketTimeoutException);
                assertTrue("a reader waited " + t.ms + " ms, for its own close", t.ms <= 600);
            }
        } finally {
            release.countDown();
        }
        awaitIdleClosers();
    }

    /**
     * Invariant: the close pool and its queue are bounded.  Forty blocked closes against
     * four threads and a queue of 16: the queue never exceeds its capacity, no more than
     * four closer threads exist, the surplus is dropped (counted), and every reader still
     * times out on time.
     */
    @Test(timeout = 60000)
    public void theCloseQueueAndPoolAreBounded() throws Exception {
        assertEquals("queue capacity", 16, BoundedBody.closeQueueCapacity());
        final CountDownLatch release = new CountDownLatch(1);
        long droppedBefore = BoundedBody.droppedCloses();
        int maxQueue = 0, maxThreads = 0;
        try {
            Timed[] reads = new Timed[40];
            for (int i = 0; i < reads.length; i++) reads[i] = new Timed(new SlowClose(release), 100);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < end) {
                maxQueue = Math.max(maxQueue, BoundedBody.closeQueueSize());
                maxThreads = Math.max(maxThreads, BoundedBody.closerThreads());
                Thread.sleep(5);
            }
            for (Timed t : reads) {
                t.thread.join(10000);
                assertTrue("reader: " + t.thrown, t.thrown instanceof SocketTimeoutException);
                assertTrue("a reader took " + t.ms + " ms", t.ms <= 1000);
            }
            assertTrue("closes were dropped: " + (BoundedBody.droppedCloses() - droppedBefore),
                    BoundedBody.droppedCloses() > droppedBefore);
        } finally {
            release.countDown();
        }
        assertTrue("queue grew to " + maxQueue, maxQueue <= 16);
        assertTrue("closer threads grew to " + maxThreads, maxThreads <= 4);
        awaitIdleClosers();
    }

    /** Lets the closes of the test above finish so that the next test starts with a free pool. */
    private static void awaitIdleClosers() throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (BoundedBody.closeQueueSize() > 0 && System.nanoTime() < end) Thread.sleep(20);
        Thread.sleep(200);
    }

    /** The interrupt the watchdog raises to release the reader is not left set on the caller's thread. */
    @Test
    public void theCallersInterruptFlagIsClearAfterATimeout() throws Exception {
        try {
            BoundedBody.read(new BlockingUntilClosed(true), 10, 100);
            fail("no timeout");
        } catch (SocketTimeoutException expected) {
            assertTrue("interrupt flag left set", !Thread.currentThread().isInterrupted());
        }
    }

    /**
     * The closer thread is a daemon with the system class loader as context class
     * loader, whichever thread asked for the read, and is not named after the caller.
     */
    @Test
    public void theThreadsAreDaemonsWithAFixedContextClassLoader() throws Exception {
        final AtomicReference<Thread> closer = new AtomicReference<>();
        InputStream s = new BlockingUntilClosed(true) {
            @Override
            public void close() throws IOException {
                closer.compareAndSet(null, Thread.currentThread());
                super.close();
            }
        };
        ClassLoader mine = new ClassLoader(null) { };
        Thread caller = new Thread(() -> {
            try {
                BoundedBody.read(s, 10, 100);
            } catch (IOException expected) {
                // the timeout
            }
        });
        caller.setContextClassLoader(mine);
        caller.start();
        caller.join(10000);
        // The reader does not wait for the close, so the closer thread runs a moment later.
        for (int i = 0; i < 100 && closer.get() == null; i++) Thread.sleep(50);
        Thread c = closer.get();
        assertTrue("closer thread ran", c != null);
        assertTrue("daemon", c.isDaemon());
        assertTrue(c.getName(), c.getName().contains("JwtLoginModule-body-closer"));
        assertSame("the context class loader is not inherited from the caller",
                ClassLoader.getSystemClassLoader(), c.getContextClassLoader());
        boolean sawWatchdog = false;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("JwtLoginModule-body-watchdog")) {
                sawWatchdog = true;
                assertTrue(t.isDaemon());
                assertSame(ClassLoader.getSystemClassLoader(), t.getContextClassLoader());
            }
        }
        assertTrue("the watchdog thread exists", sawWatchdog);
    }
}
