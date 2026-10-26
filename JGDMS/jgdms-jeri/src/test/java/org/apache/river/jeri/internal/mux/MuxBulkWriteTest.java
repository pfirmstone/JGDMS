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

package org.apache.river.jeri.internal.mux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.RequestDispatcher;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@code MuxOutputStream.write(byte[], int, int)} sends a write of at least one
 * fragment straight from the caller's array, in pieces bounded by the ration
 * and by 0xFFFF, and waits for all of them before it returns.
 *
 * <p>Each test runs a real {@link MuxClient} against a real {@link MuxServer}
 * over a loopback socket, so the framing, the rations and the writer thread are
 * the production ones. The ration and the fragment size are set per test
 * through the package-private constructors.
 */
public class MuxBulkWriteTest {

    private static final int DEFAULT_RATION = 32768;
    private static final int FRAGMENT = 1024;
    private static final long TIMEOUT_S = 60;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MuxBulkWriteTest");
        t.setDaemon(true);
        return t;
    });
    private final List<AutoCloseable> toClose = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
        pool.shutdownNow();
    }

    /** Holds back the client's socket writes while closed, so queued pieces stay queued. */
    private static final class Gate extends OutputStream {
        private final OutputStream delegate;
        private boolean open = true;
        private boolean failing = false;

        Gate(OutputStream delegate) {
            this.delegate = delegate;
        }

        synchronized void setOpen(boolean open) {
            this.open = open;
            notifyAll();
        }

        /** From now on a write that was waiting at the gate, and every later one, fails. */
        synchronized void fail() {
            failing = true;
            notifyAll();
        }

        private synchronized void pass() throws IOException {
            while (!open && !failing) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            if (failing) {
                throw new IOException("simulated socket failure");
            }
        }

        @Override public void write(int b) throws IOException {
            pass();
            delegate.write(b);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            pass();
            delegate.write(b, off, len);
        }

        @Override public void flush() throws IOException {
            pass();
            delegate.flush();
        }
    }

    /** A connected client and server with the server's dispatcher supplied. */
    private final class Pair {
        final MuxClient client;
        final MuxServer server;
        final Gate clientOut;

        Pair(RequestDispatcher dispatcher, int ration, int fragment) throws Exception {
            ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            toClose.add(listener);
            Socket cs = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            Socket ss = listener.accept();
            cs.setTcpNoDelay(true);
            ss.setTcpNoDelay(true);
            toClose.add(cs);
            toClose.add(ss);
            server = new MuxServer(ss.getOutputStream(), ss.getInputStream(), dispatcher, ration, fragment);
            clientOut = new Gate(cs.getOutputStream());
            client = new MuxClient(clientOut, cs.getInputStream(), 15000, ration, fragment);
            toClose.add(() -> client.shutdown("test over"));
            toClose.add(() -> server.shutdown("test over"));
            Future<?> started = pool.submit(() -> {
                server.start();
                return null;
            });
            client.start();
            started.get(TIMEOUT_S, TimeUnit.SECONDS);
        }
    }

    private static byte[] pattern(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** The server reads the whole request and writes it straight back. */
    private static final RequestDispatcher ECHO = new RequestDispatcher() {
        @Override public void dispatch(InboundRequest request) {
            try {
                byte[] data = readAll(request.getRequestInputStream());
                OutputStream out = request.getResponseOutputStream();
                out.write(data);
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }
    };

    /** Writes the parts in order to a new request, closes it, reads the response. */
    private byte[] call(Pair p, byte[]... parts) throws Exception {
        OutboundRequest request = p.client.newRequest();
        OutputStream out = request.getRequestOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        out.close();
        return readAll(request.getResponseInputStream());
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    private <T> T within(Callable<T> task) throws Exception {
        Future<T> f = pool.submit(task);
        try {
            return f.get(TIMEOUT_S, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            fail("timed out: a bulk write or read hung");
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void sizesAroundTheThresholdAndThePieceLimits() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        // below, at and above one fragment; around the 32 KiB ration; around 0xFFFF; several rations
        int[] sizes = {1, FRAGMENT - 1, FRAGMENT, FRAGMENT + 1, 2 * FRAGMENT, DEFAULT_RATION - 1, DEFAULT_RATION,
            DEFAULT_RATION + 1, 0xFFFF - 1, 0xFFFF, 0x10000, 0x10001, 300_001, 2_000_000};
        for (int size : sizes) {
            byte[] data = pattern(size, size);
            final byte[] copy = data.clone();
            byte[] back = within(() -> call(p, data));
            assertArrayEquals("size " + size, copy, back);
        }
    }

    @Test
    public void aLargeWriteAfterBufferedBytesKeepsTheOrder() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        // k small bytes sit in the fragment buffer when the large write arrives
        int[] prefixes = {1, 100, FRAGMENT - 1, FRAGMENT, FRAGMENT + 5};
        for (int k : prefixes) {
            byte[] head = pattern(k, 7 + k);
            byte[] big = pattern(150_000, 11 + k);
            byte[] tail = pattern(77, 13 + k);
            byte[] back = within(() -> call(p, head, big, tail));
            assertArrayEquals("prefix " + k, concat(head, big, tail), back);
        }
    }

    @Test
    public void alternatingSmallAndLargeWritesKeepTheirOrder() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        byte[][] parts = new byte[12][];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = pattern(i % 2 == 0 ? 17 + i : 40_000 + i, i);
        }
        assertArrayEquals(concat(parts), within(() -> call(p, parts)));
    }

    @Test
    public void rationsOtherThanTheDefaultAndOtherFragmentSizes() throws Exception {
        // a ration above 0xFFFF forces the 0xFFFF piece limit; a tiny ration forces many waits;
        // a fragment of 64 bytes moves the bulk threshold
        int[][] configs = {{1 << 20, 1024}, {512, 1024}, {DEFAULT_RATION, 64}, {0xFFFF00, 4096}, {256, 64}};
        for (int[] cfg : configs) {
            Pair p = new Pair(ECHO, cfg[0], cfg[1]);
            for (int size : new int[]{cfg[1], cfg[1] + 1, 70_000, 400_000}) {
                byte[] data = pattern(size, size + cfg[0]);
                final byte[] copy = data.clone();
                byte[] back = within(() -> call(p, data));
                assertArrayEquals("ration " + cfg[0] + " fragment " + cfg[1] + " size " + size, copy, back);
            }
        }
    }

    @Test
    public void theServerResponseIsSentTheSameWay() throws Exception {
        byte[] response = pattern(500_000, 99);
        Pair p = new Pair(request -> {
            try {
                readAll(request.getRequestInputStream());
                OutputStream out = request.getResponseOutputStream();
                out.write(response, 0, response.length);
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }, DEFAULT_RATION, FRAGMENT);
        assertArrayEquals(response, within(() -> call(p, new byte[]{1})));
    }

    @Test
    public void writeDoesNotReturnWhilePiecesAreStillQueuedOnTheCallersArray() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        // the pieces wrap the caller's array: write must not return before they are written
        byte[] work = pattern(20_000, 21);          // under the ration: no wait for ration inside write
        byte[] expected = work.clone();
        OutboundRequest request = p.client.newRequest();
        OutputStream out = request.getRequestOutputStream();
        p.clientOut.setOpen(false);                  // the writer thread cannot put anything on the socket
        Future<?> write = pool.submit(() -> {
            out.write(work, 0, work.length);
            return null;
        });
        try {
            write.get(500, TimeUnit.MILLISECONDS);
            fail("write returned while its pieces were still waiting for the writer thread");
        } catch (TimeoutException expectedWait) {
            // still blocked, as it must be
        }
        p.clientOut.setOpen(true);
        write.get(TIMEOUT_S, TimeUnit.SECONDS);
        Arrays.fill(work, (byte) 0xEE);              // reuse at once
        out.close();
        assertArrayEquals(expected, within(() -> readAll(request.getResponseInputStream())));
    }

    @Test
    public void aConnectionThatFailsAfterThePiecesWereQueuedFailsTheWrite() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        OutboundRequest request = p.client.newRequest();
        OutputStream out = request.getRequestOutputStream();
        byte[] work = pattern(20_000, 22);           // all of it fits in the ration: nothing waits for ration
        p.clientOut.setOpen(false);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Future<?> write = pool.submit(() -> {
            try {
                out.write(work, 0, work.length);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Thread.sleep(300);
        assertTrue("write should still be waiting for its pieces", !write.isDone());
        p.clientOut.fail();                          // the writer thread's socket write fails
        write.get(TIMEOUT_S, TimeUnit.SECONDS);
        assertTrue("expected an IOException, got " + thrown.get(), thrown.get() instanceof IOException);
    }

    @Test
    public void anInterruptWhileWaitingForThePiecesStillWaitsForThemThenFails() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        OutboundRequest request = p.client.newRequest();
        OutputStream out = request.getRequestOutputStream();
        byte[] work = pattern(20_000, 23);
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        p.clientOut.setOpen(false);
        Future<?> write = pool.submit(() -> {
            writerThread.set(Thread.currentThread());
            try {
                out.write(work, 0, work.length);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Thread.sleep(300);
        writerThread.get().interrupt();
        try {
            write.get(500, TimeUnit.MILLISECONDS);
            fail("an interrupt let write return while its pieces were still queued on the caller's array");
        } catch (TimeoutException expectedWait) {
            // still waiting, as it must be
        }
        p.clientOut.setOpen(true);
        write.get(TIMEOUT_S, TimeUnit.SECONDS);
        assertTrue("expected an IOException, got " + thrown.get(), thrown.get() instanceof IOException);
        assertTrue(String.valueOf(thrown.get().getMessage()), thrown.get().getMessage().contains("interrupted"));
    }

    @Test
    public void theCallerMayReuseTheArrayAsSoonAsWriteReturns() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        byte[][] expected = new byte[6][];
        byte[] work = new byte[1_500_000];
        byte[] back = within(() -> {
            OutboundRequest request = p.client.newRequest();
            OutputStream out = request.getRequestOutputStream();
            for (int i = 0; i < expected.length; i++) {
                new Random(i).nextBytes(work);
                expected[i] = work.clone();
                out.write(work, 0, work.length);
                Arrays.fill(work, (byte) 0xEE);        // reuse at once
            }
            out.close();
            return readAll(request.getResponseInputStream());
        });
        assertArrayEquals(concat(expected), back);
    }

    @Test
    public void concurrentRequestsOnOneConnectionDoNotMixTheirData() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        int n = 12;
        List<Future<byte[]>> results = new ArrayList<>();
        List<byte[]> sent = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] data = pattern(250_000 + i * 1000, 1000 + i);
            sent.add(data.clone());
            results.add(pool.submit(() -> call(p, data)));
        }
        for (int i = 0; i < n; i++) {
            assertArrayEquals("request " + i, sent.get(i), results.get(i).get(TIMEOUT_S, TimeUnit.SECONDS));
        }
    }

    @Test
    public void aWriteToARequestThePeerAbortedFailsOrReturnsAndNeverHangs() throws Exception {
        Pair p = new Pair(InboundRequest::abort, DEFAULT_RATION, FRAGMENT);
        // the peer aborts without reading: the 4 MiB cannot be delivered
        Object outcome = within(() -> {
            OutboundRequest request = p.client.newRequest();
            OutputStream out = request.getRequestOutputStream();
            try {
                byte[] big = pattern(4_000_000, 5);
                out.write(big, 0, big.length);
                out.close();
                return "returned";
            } catch (IOException expected) {
                return expected;
            }
        });
        assertNotNull(outcome);
    }

    @Test
    public void aServerThatAnswersWithoutReadingDoesNotHangTheWriter() throws Exception {
        // the response completes while the client is still writing (the faked-OK-to-write path)
        Pair p = new Pair(request -> {
            try {
                OutputStream out = request.getResponseOutputStream();
                out.write(new byte[]{42});
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }, DEFAULT_RATION, FRAGMENT);
        Object outcome = within(() -> {
            OutboundRequest request = p.client.newRequest();
            OutputStream out = request.getRequestOutputStream();
            try {
                byte[] big = pattern(4_000_000, 6);
                out.write(big, 0, big.length);
                out.close();
                return "returned";
            } catch (IOException expected) {
                return expected;
            }
        });
        assertNotNull(outcome);
    }

    @Test
    public void takingTheConnectionDownDuringABulkWriteThrowsIOException() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Pair p = new Pair(request -> {      // never reads: the writer blocks for ration after 32 KiB
            try {
                release.await(TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
            }
        }, DEFAULT_RATION, FRAGMENT);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> writer = pool.submit(() -> {
            try {
                OutboundRequest request = p.client.newRequest();
                OutputStream out = request.getRequestOutputStream();
                byte[] big = pattern(3_000_000, 8);
                started.countDown();
                out.write(big, 0, big.length);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        assertTrue(started.await(TIMEOUT_S, TimeUnit.SECONDS));
        Thread.sleep(500);          // let it fill the ration and block
        assertTrue("the write should be blocked on the ration", !writer.isDone());
        p.client.shutdown("taken down by the test");
        writer.get(TIMEOUT_S, TimeUnit.SECONDS);
        release.countDown();
        assertTrue("expected an IOException, got " + thrown.get(), thrown.get() instanceof IOException);
    }

    @Test
    public void anInterruptWhileWaitingForRationEndsTheWriteWithIOException() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Pair p = new Pair(request -> {
            try {
                release.await(TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
            }
        }, DEFAULT_RATION, FRAGMENT);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> writer = pool.submit(() -> {
            writerThread.set(Thread.currentThread());
            try {
                OutboundRequest request = p.client.newRequest();
                OutputStream out = request.getRequestOutputStream();
                byte[] big = pattern(3_000_000, 9);
                started.countDown();
                out.write(big, 0, big.length);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        assertTrue(started.await(TIMEOUT_S, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertTrue(!writer.isDone());
        writerThread.get().interrupt();
        writer.get(TIMEOUT_S, TimeUnit.SECONDS);
        release.countDown();
        assertTrue("expected an IOException, got " + thrown.get(), thrown.get() instanceof IOException);
        assertTrue(String.valueOf(thrown.get().getMessage()), thrown.get().getMessage().contains("interrupted"));
    }

    @Test
    public void smallWritesStillGoThroughTheFragmentBuffer() throws Exception {
        Pair p = new Pair(ECHO, DEFAULT_RATION, FRAGMENT);
        // many sub-fragment writes: the original path, unchanged
        byte[][] parts = new byte[500][];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = pattern(1 + (i * 7) % (FRAGMENT - 1), i);
        }
        assertArrayEquals(concat(parts), within(() -> call(p, parts)));
    }
}
