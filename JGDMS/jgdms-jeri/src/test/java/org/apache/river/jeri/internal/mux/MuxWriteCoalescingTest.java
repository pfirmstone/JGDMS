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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.RequestDispatcher;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * The stream writer sends each mux message with one write to the connection's
 * output stream (on TLS, one record), not one write for the 4-byte header and
 * another for the payload, and still keeps every byte in order.
 */
public class MuxWriteCoalescingTest {

    private static final int RATION = 32768;
    private static final int FRAGMENT = 1024;
    private static final long WAIT_S = 60;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MuxWriteCoalescingTest");
        t.setDaemon(true);
        return t;
    });
    private final List<AutoCloseable> toClose = new ArrayList<>();

    @After
    public void tearDown() {
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
        pool.shutdownNow();
    }

    private static final class Counting extends OutputStream {
        private final OutputStream delegate;
        final AtomicInteger writes = new AtomicInteger();

        Counting(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override public void write(int b) throws IOException {
            writes.incrementAndGet();
            delegate.write(b);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            writes.incrementAndGet();
            delegate.write(b, off, len);
        }

        @Override public void flush() throws IOException {
            delegate.flush();
        }
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

    private final class Pair {
        final MuxClient client;
        final Counting clientOut;

        Pair() throws Exception {
            ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            toClose.add(listener);
            Socket cs = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            Socket ss = listener.accept();
            cs.setTcpNoDelay(true);
            ss.setTcpNoDelay(true);
            toClose.add(cs);
            toClose.add(ss);
            MuxServer server = new MuxServer(ss.getOutputStream(), ss.getInputStream(), ECHO, RATION, FRAGMENT);
            clientOut = new Counting(cs.getOutputStream());
            client = new MuxClient(clientOut, cs.getInputStream(), 15000, RATION, FRAGMENT);
            toClose.add(() -> client.shutdown("test over"));
            toClose.add(() -> server.shutdown("test over"));
            Future<?> started = pool.submit(() -> {
                server.start();
                return null;
            });
            client.start();
            started.get(WAIT_S, TimeUnit.SECONDS);
        }

        byte[] call(byte[] data) throws Exception {
            OutboundRequest request = client.newRequest();
            OutputStream out = request.getRequestOutputStream();
            out.write(data, 0, data.length);
            out.close();
            return readAll(request.getResponseInputStream());
        }
    }

    @Test
    public void aSmallRequestIsOneWrite() throws Exception {
        Pair p = new Pair();
        byte[] data = {1, 2, 3};
        assertArrayEquals(data, p.call(data));          // settle the handshake and the first request
        int before = p.clientOut.writes.get();
        int calls = 50;
        for (int i = 0; i < calls; i++) {
            assertArrayEquals(data, p.call(data));
        }
        int writes = p.clientOut.writes.get() - before;
        assertTrue(writes + " writes for " + calls + " one-message requests", writes <= calls + 2);
    }

    @Test
    public void orderIsKeptAcrossSizesAndPipelinedRequests() throws Exception {
        Pair p = new Pair();
        Random random = new Random(42);
        int[] sizes = {0, 1, 100, 1023, 1024, 1025, 65535, 65536, 70000, 200_000};
        for (int size : sizes) {
            byte[] data = new byte[size];
            random.nextBytes(data);
            assertArrayEquals("size " + size, data, p.call(data));
        }
        // concurrent callers interleave their messages on the one connection
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final byte[] data = new byte[3000 + i * 777];
            random.nextBytes(data);
            results.add(pool.submit(() -> Arrays.equals(data, p.call(data))));
        }
        for (Future<Boolean> f : results) {
            assertTrue(f.get(WAIT_S, TimeUnit.SECONDS));
        }
    }
}
