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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.jini.io.context.AcknowledgmentSource;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.RequestDispatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A server response that asks for an acknowledgment tells its listeners
 * exactly once, whichever way the session ends: {@code true} when the client
 * acknowledges, {@code false} when the connection is lost or no
 * acknowledgment arrives within the timeout.
 */
public class MuxAckListenerTest {

    private static final int RATION = 32768;
    private static final int FRAGMENT = 1024;
    private static final long WAIT_S = 30;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MuxAckListenerTest");
        t.setDaemon(true);
        return t;
    });
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private final BlockingQueue<Boolean> outcomes = new LinkedBlockingQueue<>();
    private long savedTimeout;

    @Before
    public void saveTimeout() {
        savedTimeout = Session.ackTimeoutMs;
    }

    @After
    public void tearDown() {
        Session.ackTimeoutMs = savedTimeout;
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
        pool.shutdownNow();
    }

    /** Reads the request, registers a listener, writes "ok" and closes. */
    private final RequestDispatcher dispatcher = new RequestDispatcher() {
        @Override public void dispatch(InboundRequest request) {
            try {
                Collection<Object> context = new ArrayList<>();
                request.populateContext(context);
                for (Object o : context) {
                    if (o instanceof AcknowledgmentSource) {
                        ((AcknowledgmentSource) o).addAcknowledgmentListener(
                            outcomes::add);
                    }
                }
                InputStream in = request.getRequestInputStream();
                while (in.read() != -1) {
                }
                OutputStream out = request.getResponseOutputStream();
                out.write('o');
                out.write('k');
                out.close();
            } catch (IOException e) {
                request.abort();
            }
        }
    };

    private final class Pair {
        final MuxClient client;
        final MuxServer server;
        final Socket clientSocket;

        Pair() throws Exception {
            ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            toClose.add(listener);
            clientSocket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            Socket ss = listener.accept();
            toClose.add(clientSocket);
            toClose.add(ss);
            server = new MuxServer(ss.getOutputStream(), ss.getInputStream(), dispatcher, RATION, FRAGMENT);
            client = new MuxClient(clientSocket.getOutputStream(), clientSocket.getInputStream(), 15000, RATION, FRAGMENT);
            toClose.add(() -> client.shutdown("test over"));
            toClose.add(() -> server.shutdown("test over"));
            Future<?> started = pool.submit(() -> {
                server.start();
                return null;
            });
            client.start();
            started.get(WAIT_S, TimeUnit.SECONDS);
        }
    }

    /** Sends a request and reads the whole response, leaving the response stream open. */
    private InputStream callWithoutClosingResponse(Pair p, OutboundRequest[] holder) throws Exception {
        OutboundRequest request = p.client.newRequest();
        holder[0] = request;
        OutputStream out = request.getRequestOutputStream();
        out.write(1);
        out.close();
        InputStream in = request.getResponseInputStream();
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        got.write(in.read());
        got.write(in.read());
        assertEquals("ok", got.toString("US-ASCII"));
        assertEquals(-1, in.read());
        return in;
    }

    private Boolean next(long ms) throws InterruptedException {
        return outcomes.poll(ms, TimeUnit.MILLISECONDS);
    }

    private void assertNoMore() throws InterruptedException {
        assertNull("listeners must be told once", next(300));
    }

    @Test
    public void acknowledgmentNotifiesTrueOnce() throws Exception {
        Pair p = new Pair();
        InputStream in = callWithoutClosingResponse(p, new OutboundRequest[1]);
        in.close();     // the acknowledgment is sent when the response stream is closed
        assertEquals(Boolean.TRUE, next(WAIT_S * 1000));
        assertNoMore();
    }

    @Test
    public void connectionLostBeforeTheAcknowledgmentNotifiesFalseOnce() throws Exception {
        Pair p = new Pair();
        callWithoutClosingResponse(p, new OutboundRequest[1]);
        p.clientSocket.close();
        assertEquals(Boolean.FALSE, next(WAIT_S * 1000));
        assertNoMore();
    }

    @Test
    public void noAcknowledgmentWithinTheTimeoutNotifiesFalseAndTheConnectionStaysUsable() throws Exception {
        Session.ackTimeoutMs = 300;
        Pair p = new Pair();
        InputStream in = callWithoutClosingResponse(p, new OutboundRequest[1]);
        long start = System.nanoTime();
        assertEquals(Boolean.FALSE, next(WAIT_S * 1000));
        assertTrue("not before the timeout", System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(200));
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            assertFalse("the ack timeout has no timer thread of its own: " + t,
                        t.getName().equals("Mux ack timeout"));
        }
        // the acknowledgment arriving late is accepted and does not tell the listeners again
        in.close();
        assertNoMore();
        InputStream second = callWithoutClosingResponse(p, new OutboundRequest[1]);
        second.close();
        assertEquals(Boolean.TRUE, next(WAIT_S * 1000));
    }

    @Test
    public void anAcknowledgmentCancelsTheTimeout() throws Exception {
        Session.ackTimeoutMs = 400;
        Pair p = new Pair();
        InputStream in = callWithoutClosingResponse(p, new OutboundRequest[1]);
        in.close();
        assertEquals(Boolean.TRUE, next(WAIT_S * 1000));
        Thread.sleep(700);
        assertNull("the cancelled timeout must not fire", outcomes.poll());
    }

    @Test
    public void aDisabledTimeoutNeverFires() throws Exception {
        Session.ackTimeoutMs = 0;
        Pair p = new Pair();
        callWithoutClosingResponse(p, new OutboundRequest[1]);
        assertNull(next(700));
    }
}
