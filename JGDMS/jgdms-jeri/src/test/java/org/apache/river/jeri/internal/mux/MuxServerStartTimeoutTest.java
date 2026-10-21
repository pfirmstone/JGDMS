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
     * (The processors are kept busy, so that the thread that starts the
     * connection is often descheduled before it gets on.)
     */
    @Test(timeout = 120000)
    public void theTimerDoesNotOutliveAConnectionThatStarted() throws Exception {
        byte[] header = clientHeader();
        assertTrue("the header", header.length >= 8);
        Thread.sleep(300);
        int base = org.apache.river.jeri.internal.TimeoutScheduler.pending();
        List<Thread> burners = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        for (int i = 0; i < 2 * Runtime.getRuntime().availableProcessors(); i++) {
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
        try {
            for (int i = 0; i < 1500; i++) {
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
        }
        assertEquals("connections that left their start timer behind", 0, leaked);
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
