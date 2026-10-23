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
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.RequestDispatcher;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The channel (non-blocking) connection I/O gathers what several threads
 * queued into one write, hands a write that meets a full socket buffer to the
 * selection thread, keeps every byte in order and fails what is waiting when
 * the connection goes.  Real {@link MuxClient} and {@link MuxServer} over a
 * loopback socket channel pair with small socket buffers.
 */
public class MuxChannelBatchingTest {

    private static final long WAIT_S = 30;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MuxChannelBatchingTest");
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
                out.write(data, 0, data.length);
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }
    };

    private final class Pair {
        final MuxClient client;
        final MuxServer server;
        final SocketChannel clientChannel;
        final SocketChannel serverChannel;

        Pair(int bufferSize) throws Exception {
            this(bufferSize, ECHO);
        }

        Pair(int bufferSize, RequestDispatcher dispatcher) throws Exception {
            // A Unix domain socket pair: small socket buffers on loopback TCP stall the
            // sender until the persist timer fires (the receive window never reaches one
            // segment), which says nothing about the mux.
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("muxchan");
            java.nio.file.Path path = dir.resolve("s");
            toClose.add(() -> {
                java.nio.file.Files.deleteIfExists(path);
                java.nio.file.Files.deleteIfExists(dir);
            });
            ServerSocketChannel listener = ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            toClose.add(listener);
            listener.bind(java.net.UnixDomainSocketAddress.of(path));
            clientChannel = SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            if (bufferSize > 0) {
                clientChannel.setOption(StandardSocketOptions.SO_SNDBUF, bufferSize);
                clientChannel.setOption(StandardSocketOptions.SO_RCVBUF, bufferSize);
            }
            clientChannel.connect(listener.getLocalAddress());
            serverChannel = listener.accept();
            if (bufferSize > 0) {
                serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, bufferSize);
                serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, bufferSize);
            }
            toClose.add(clientChannel);
            toClose.add(serverChannel);
            MuxChannelBatchingTest.this.pairRef = this;
            server = new MuxServer(serverChannel, dispatcher);
            client = new MuxClient(clientChannel, 15000);
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

    private volatile Pair pairRef;

    private <T> T get(Future<T> f) throws Exception {
        try {
            return f.get(WAIT_S, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            for (Mux m : new Mux[] {pairRef.client, pairRef.server}) {
                try {
                    java.lang.reflect.Field cf = Mux.class.getDeclaredField("connectionIO");
                    cf.setAccessible(true);
                    Object io = cf.get(m);
                    StringBuilder sb = new StringBuilder(m.getClass().getSimpleName()).append(':');
                    for (String name : new String[] {"sendQueue", "notifyQueue", "writing", "awaitingWriteReady", "key"}) {
                        try {
                            java.lang.reflect.Field fld = io.getClass().getDeclaredField(name);
                            fld.setAccessible(true);
                            Object v = fld.get(io);
                            if (v instanceof java.util.Collection) {
                                v = ((java.util.Collection<?>) v).size();
                            } else if (name.equals("key")) {
                                StringBuilder k = new StringBuilder();
                                for (String kn : new String[] {"interestMask", "renewMask", "readyMask"}) {
                                    java.lang.reflect.Field kf = v.getClass().getDeclaredField(kn);
                                    kf.setAccessible(true);
                                    k.append(kn).append('=').append(kf.get(v)).append(' ');
                                }
                                v = k;
                            }
                            sb.append(' ').append(name).append('=').append(v);
                        } catch (NoSuchFieldException nf) {
                            sb.append(' ').append(name).append("=n/a");
                        }
                    }
                    System.err.println("STATE " + sb);
                } catch (Exception ex) {
                    System.err.println("STATE failed " + ex);
                }
            }
            try {
                java.nio.file.Path dump = java.nio.file.Files.createTempFile("mux-hang", ".txt");
                java.nio.file.Files.delete(dump);
                java.lang.management.ManagementFactory.getPlatformMXBean(
                    com.sun.management.HotSpotDiagnosticMXBean.class)
                    .dumpThreads(dump.toString(),
                        com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
                System.err.println("thread dump: " + dump);
            } catch (Exception | LinkageError dumpFailed) {
                System.err.println("no thread dump: " + dumpFailed);
            }
            fail("timed out");
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void sizesKeepTheirBytesInOrder() throws Exception {
        Pair p = new Pair(0);
        Random random = new Random(7);
        int[] sizes = {0, 1, 1023, 1024, 1025, 32767, 32768, 65535, 65536, 300_000, 2_000_000};
        for (int size : sizes) {
            byte[] data = new byte[size];
            random.nextBytes(data);
            assertArrayEquals("size " + size, data, get(pool.submit(() -> p.call(data))));
        }
    }

    @Test
    public void manyThreadsOnOneConnectionAllGetTheirOwnBytesBack() throws Exception {
        Pair p = new Pair(0);
        int threads = 24;
        int calls = 200;
        List<Future<Boolean>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            results.add(pool.submit(() -> {
                Random random = new Random(seed);
                for (int i = 0; i < calls; i++) {
                    byte[] data = new byte[1 + random.nextInt(3000)];
                    random.nextBytes(data);
                    if (!Arrays.equals(data, p.call(data))) {
                        return false;
                    }
                }
                return true;
            }));
        }
        for (Future<Boolean> f : results) {
            assertTrue(get(f));
        }
    }

    @Test
    public void fullSocketBuffersAreHandedToTheSelectionThread() throws Exception {
        Pair p = new Pair(4096);
        int threads = 8;
        List<Future<Boolean>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = 100 + t;
            results.add(pool.submit(() -> {
                Random random = new Random(seed);
                for (int i = 0; i < 6; i++) {
                    byte[] data = new byte[100_000 + random.nextInt(200_000)];
                    random.nextBytes(data);
                    if (!Arrays.equals(data, p.call(data))) {
                        return false;
                    }
                }
                return true;
            }));
        }
        for (Future<Boolean> f : results) {
            assertTrue(get(f));
        }
    }

    @Test
    public void losingTheConnectionFailsWhatIsWaitingInsteadOfHanging() throws Exception {
        // The server holds every reply until the connection has been cut, so no call can
        // complete first however fast the transport is.
        final CountDownLatch cut = new CountDownLatch(1);
        Pair p = new Pair(4096, new RequestDispatcher() {
            @Override public void dispatch(InboundRequest request) {
                try {
                    readAll(request.getRequestInputStream());
                    cut.await();
                    OutputStream out = request.getResponseOutputStream();
                    out.write(1);
                    out.close();
                } catch (IOException | InterruptedException e) {
                    request.abort();
                }
            }
        });
        List<Future<Object>> results = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            results.add(pool.submit((Callable<Object>) () -> {
                byte[] data = new byte[3_000_000];
                try {
                    p.call(data);
                    return "completed";
                } catch (IOException e) {
                    return e;
                }
            }));
        }
        Thread.sleep(200);
        p.serverChannel.close();
        cut.countDown();
        for (Future<Object> f : results) {
            Object r = get(f);
            assertTrue("a call over a lost connection must fail, not " + r, r instanceof IOException);
        }
    }
}
