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
