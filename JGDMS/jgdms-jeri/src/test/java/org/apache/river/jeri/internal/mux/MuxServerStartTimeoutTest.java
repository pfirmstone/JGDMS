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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.RequestDispatcher;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A server connection whose peer does not send its connection header is taken
 * down when the start timeout has passed: the connection is closed and its
 * threads end.  Until then, and for a peer that does send the header, nothing
 * changes.
 */
public class MuxServerStartTimeoutTest {

    private static final int RATION = 32768;
    private static final int FRAGMENT = 1024;
    private static final String PROPERTY = "org.apache.river.jeri.mux.serverStartTimeout";

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @After
    public void tearDown() {
        System.clearProperty(PROPERTY);
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static final RequestDispatcher ECHO = new RequestDispatcher() {
        @Override public void dispatch(InboundRequest request) {
            try {
                InputStream in = request.getRequestInputStream();
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    data.write(buf, 0, n);
                }
                OutputStream out = request.getResponseOutputStream();
                out.write(data.toByteArray());
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }
    };

    /** A server whose down is noted. */
    private static final class Server extends MuxServer {
        final CountDownLatch down = new CountDownLatch(1);

        Server(OutputStream out, InputStream in, long startTimeout)
            throws IOException
        {
            super(out, in, ECHO, RATION, FRAGMENT, startTimeout);
        }

        @Override
        protected void handleDown() {
            down.countDown();
        }
    }

    private static int threadsNamed(String name) {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals(name) && t.isAlive()) {
                n++;
            }
        }
        return n;
    }

    @Test(timeout = 60000)
    public void aSilentPeerIsTakenDownAndItsThreadsEnd() throws Exception {
        /* Threads of earlier connections may still be ending. */
        int readers;
        int writers;
        do {
            readers = threadsNamed("mux reader");
            writers = threadsNamed("mux writer");
            Thread.sleep(300);
        } while (readers != threadsNamed("mux reader")
                 || writers != threadsNamed("mux writer"));
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        toClose.add(listener);
        Socket peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket accepted = listener.accept();
        toClose.add(peer);
        toClose.add(accepted);
        Server server = new Server(accepted.getOutputStream(),
                                   accepted.getInputStream(), 600);
        long t0 = System.nanoTime();
        server.start();
        assertTrue("the server's threads", threadsNamed("mux reader") > readers);
        assertTrue("the connection must go down",
                   server.down.await(10, TimeUnit.SECONDS));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue("down after " + ms + " ms", ms >= 500 && ms < 3000);
        /* The peer sees the connection closed. */
        peer.setSoTimeout(5000);
        assertTrue(peer.getInputStream().read(new byte[64]) < 0);
        /* And the reader and writer threads are gone. */
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((threadsNamed("mux reader") > readers
                || threadsNamed("mux writer") > writers)
               && System.nanoTime() < end)
        {
            Thread.sleep(20);
        }
        assertTrue(threadsNamed("mux reader") <= readers);
        assertTrue(threadsNamed("mux writer") <= writers);
    }

    @Test(timeout = 60000)
    public void aPeerThatSendsTheHeaderIsNotTakenDown() throws Exception {
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        toClose.add(listener);
        Socket cs = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket ss = listener.accept();
        toClose.add(cs);
        toClose.add(ss);
        Server server = new Server(ss.getOutputStream(), ss.getInputStream(), 500);
        MuxClient client = new MuxClient(cs.getOutputStream(), cs.getInputStream(),
                                         15000, RATION, FRAGMENT);
        toClose.add(() -> client.shutdown("test over"));
        toClose.add(() -> server.shutdown("test over"));
        server.start();
        client.start();
        Thread.sleep(1600);
        assertFalse("a connection whose peer started it must stay up",
                    server.down.await(0, TimeUnit.SECONDS));
        byte[] data = { 1, 2, 3 };
        OutboundRequest request = client.newRequest();
        OutputStream out = request.getRequestOutputStream();
        out.write(data, 0, data.length);
        out.close();
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        InputStream in = request.getResponseInputStream();
        byte[] buf = new byte[16];
        int n;
        while ((n = in.read(buf)) != -1) {
            got.write(buf, 0, n);
        }
        assertArrayEquals(data, got.toByteArray());
    }

    @Test(timeout = 60000)
    public void aSilentPeerOnTheChannelPathIsTakenDown() throws Exception {
        System.setProperty(PROPERTY, "600");
        ServerSocketChannel listener = ServerSocketChannel.open();
        listener.bind(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        toClose.add(listener);
        SocketChannel peer = SocketChannel.open(listener.getLocalAddress());
        SocketChannel accepted = listener.accept();
        toClose.add(peer);
        toClose.add(accepted);
        CountDownLatch down = new CountDownLatch(1);
        MuxServer server = new MuxServer(accepted, ECHO) {
            @Override
            protected void handleDown() {
                down.countDown();
            }
        };
        long t0 = System.nanoTime();
        server.start();
        assertTrue("the connection must go down", down.await(10, TimeUnit.SECONDS));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue("down after " + ms + " ms", ms >= 500 && ms < 3000);
    }

    /**
     * The client's connection header, as the real client sends it.
     */
    private static byte[] clientHeader() throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        MuxClient client = new MuxClient(header, new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("no server");
            }
        }, 1000, RATION, FRAGMENT);
        try {
            client.start();
        } catch (IOException expected) {
        }
        client.shutdown("done");
        for (int i = 0; i < 100 && header.size() < 8; i++) {
            Thread.sleep(20);
        }
        return header.toByteArray();
    }

    /** An input that gives the header at once, then says when it is asked for more. */
    private static final class HeaderThenWait extends InputStream {
        private final byte[] header;
        private int at;
        final CountDownLatch headerConsumed = new CountDownLatch(1);
        private final CountDownLatch end = new CountDownLatch(1);

        HeaderThenWait(byte[] header) {
            this.header = header;
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (at < header.length) {
                int n = Math.min(len, header.length - at);
                System.arraycopy(header, at, b, off, n);
                at += n;
                return n;
            }
            /* The header has been processed: the reader is back for more. */
            headerConsumed.countDown();
            try {
                end.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }

        @Override public void close() {
            end.countDown();
        }
    }

    /**
     * The timer for the client's header does not outlive a connection whose
     * client has sent it, however quickly the reader gets there: a header that
     * is processed before start returns must still find the timer to cancel.
     * (Half the processors are kept busy, so that the thread that starts the
     * connection is often descheduled before it gets on, while the reader
     * still finds a processor at once.)
     *
     * <p>The loop is bounded by time as well as by count: each connection
     * costs a few scheduling quanta of the machine, which are much longer on
     * some systems (Windows) than on others, so a fixed count would make the
     * test's duration scale with the scheduler rather than with the code.  It
     * starts at least {@link #MIN_STARTS} connections, and at most {@link
     * #MAX_STARTS} or as many as {@link #START_BUDGET_NANOS} allows.  With the
     * timer scheduled after the I/O starts (the bug this guards against),
     * about one connection in ten to one in three left its timer behind on a
     * 4-processor Linux container, so the minimum is far more than enough.
     */
    @Test(timeout = 120000)
    public void theTimerDoesNotOutliveAConnectionThatStarted() throws Exception {
        byte[] header = clientHeader();
        assertTrue("the header", header.length >= 8);
        Thread.sleep(300);
        int base = org.apache.river.jeri.internal.TimeoutScheduler.pending();
        List<Thread> burners = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        int burnerCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        for (int i = 0; i < burnerCount; i++) {
            burners.add(Thread.ofPlatform().daemon().start(() -> {
                long x = 0;
                while (!stop.get()) {
                    x += System.nanoTime();
                }
                if (x == 42) {
                    System.out.print("");
                }
            }));
        }
        int leaked = 0;
        int starts = 0;
        long began = System.nanoTime();
        try {
            for (; starts < MAX_STARTS
                   && (starts < MIN_STARTS
                       || System.nanoTime() - began < START_BUDGET_NANOS);
                 starts++)
            {
                HeaderThenWait in = new HeaderThenWait(header);
                Server server = new Server(OutputStream.nullOutputStream(), in, 60000);
                server.start();
                assertTrue("header processed", in.headerConsumed.await(10, TimeUnit.SECONDS));
                if (org.apache.river.jeri.internal.TimeoutScheduler.pending() > base) {
                    leaked++;
                }
                server.shutdown("test over");
                in.close();
            }
        } finally {
            stop.set(true);
            for (Thread t : burners) {
                t.join(5000);
            }
        }
        assertEquals("connections, of " + starts + ", that left their start "
                     + "timer behind", 0, leaked);
    }

    /** The fewest connections that the timer test starts, however slow. */
    private static final int MIN_STARTS = 200;

    /** The most connections that the timer test starts. */
    private static final int MAX_STARTS = 1500;

    /** After this, the timer test starts no more than the minimum. */
    private static final long START_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(20);

    /**
     * The timer thread serves every connection, so the expiry of one start
     * timer must not wait for the lock of the connection: a connection whose
     * lock is held for seconds does not delay the deadline of another.
     */
    @Test(timeout = 60000)
    public void aHeldLockDoesNotDelayTheDeadlineOfAnotherConnection() throws Exception {
        ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
        toClose.add(listener);
        Socket[] peers = new Socket[2];
        Socket[] accepted = new Socket[2];
        for (int i = 0; i < 2; i++) {
            peers[i] = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            accepted[i] = listener.accept();
            toClose.add(peers[i]);
            toClose.add(accepted[i]);
        }
        Server held = new Server(accepted[0].getOutputStream(),
                                 accepted[0].getInputStream(), 300);
        Server other = new Server(accepted[1].getOutputStream(),
                                  accepted[1].getInputStream(), 1000);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> {
            synchronized (held.muxLock) {
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            long t0 = System.nanoTime();
            held.start();
            other.start();
            assertTrue("the other connection must go down on time",
                       other.down.await(4, TimeUnit.SECONDS));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertTrue("down after " + ms + " ms", ms >= 900 && ms < 3500);
            assertFalse("the connection with the lock waits for it",
                        held.down.await(300, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            holder.join(10_000);
        }
        assertTrue("and goes down when the lock is free",
                   held.down.await(10, TimeUnit.SECONDS));
    }

    /**
     * A take-down that was handed to another thread before the header arrived,
     * and runs after it, leaves the connection up: whether the header has been
     * processed is decided under the lock that the processing is done under.
     */
    @Test(timeout = 60000)
    public void aTakeDownThatRunsAfterTheHeaderIsNotCarriedOut() throws Exception {
        HeaderThenWait in = new HeaderThenWait(clientHeader());
        Server server = new Server(OutputStream.nullOutputStream(), in, 60000);
        server.start();
        assertTrue("header processed", in.headerConsumed.await(10, TimeUnit.SECONDS));
        java.lang.reflect.Method expired =
            Mux.class.getDeclaredMethod("startTimeExpired");
        expired.setAccessible(true);
        expired.invoke(server);
        assertFalse("a connection that has started stays up",
                    server.down.await(500, TimeUnit.MILLISECONDS));
        server.shutdown("test over");
        in.close();
    }

    @Test
    public void thePropertyIsTheDefaultOtherwise() {
        System.clearProperty(PROPERTY);
        assertEquals(15000L, MuxServer.serverStartTimeout());
        System.setProperty(PROPERTY, "250");
        assertEquals(250L, MuxServer.serverStartTimeout());
        System.setProperty(PROPERTY, "0");
        assertEquals(0L, MuxServer.serverStartTimeout());
    }
}
