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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLParameters;

/**
 * A client that drives its {@code SSLEngine} by hand over a socket, one flight
 * at a time, so that a test can stop it between any two: after its
 * ClientHello, after the server's HelloRetryRequest, after the server's
 * flight, which no client library will do.  Everything it sends is what a
 * real JDK client sends.
 */
final class S0EngineClient implements AutoCloseable {

    final Socket socket;
    final SSLEngine engine;
    private final InputStream in;
    private final OutputStream out;
    private final ByteBuffer net;
    private final ByteBuffer app;
    private final ByteBuffer rx = ByteBuffer.allocate(1 << 16);

    /**
     * @param port the port of a listener on this host
     * @param groups the named groups to offer, the first of which gets the key
     *        share, or none for the JDK's own
     */
    S0EngineClient(int port, String... groups) throws IOException {
        this(port, engineOffering(groups));
    }

    /** A client over a new connection that is driven by the given engine. */
    S0EngineClient(int port, SSLEngine engine) throws IOException {
        socket = new Socket("localhost", port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(10_000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        this.engine = engine;
        net = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        app = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        rx.limit(0);
        engine.beginHandshake();
    }

    private static SSLEngine engineOffering(String... groups) {
        SSLEngine engine = S0Fixture.clientEngine();
        if (groups.length > 0) {
            SSLParameters p = engine.getSSLParameters();
            p.setNamedGroups(groups);
            engine.setSSLParameters(p);
        }
        return engine;
    }

    private void tasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
    }

    /**
     * Sends the client's next flight, doing the local work that comes first,
     * and returns the bytes sent.
     */
    byte[] sendFlight() throws IOException {
        for (;;) {
            HandshakeStatus st = engine.getHandshakeStatus();
            if (st == HandshakeStatus.NEED_TASK) {
                tasks();
            } else if (st == HandshakeStatus.NEED_WRAP) {
                net.clear();
                engine.wrap(ByteBuffer.allocate(0), net);
                net.flip();
                byte[] bytes = new byte[net.remaining()];
                net.get(bytes);
                out.write(bytes);
                out.flush();
                return bytes;
            } else {
                throw new IllegalStateException("nothing to send: " + st);
            }
        }
    }

    /**
     * Limits the packets that the engine makes, so that the records it sends
     * carry as little as the JDK lets them, from here on.
     */
    void setMaximumPacketSize(int size) {
        SSLParameters p = engine.getSSLParameters();
        p.setMaximumPacketSize(size);
        engine.setSSLParameters(p);
    }

    /**
     * Makes everything that the client has to send before it waits for the
     * server, without sending it, and returns the bytes: the flight, however
     * many records the engine makes of it.
     */
    byte[] flight() throws IOException {
        java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
        for (;;) {
            HandshakeStatus st = engine.getHandshakeStatus();
            if (st == HandshakeStatus.NEED_TASK) {
                tasks();
            } else if (st == HandshakeStatus.NEED_WRAP) {
                net.clear();
                engine.wrap(ByteBuffer.allocate(0), net);
                net.flip();
                byte[] bytes = new byte[net.remaining()];
                net.get(bytes);
                all.write(bytes);
            } else {
                return all.toByteArray();
            }
        }
    }

    /** Sends the bytes in one write. */
    void send(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    /** Sends everything that the client has to send before it waits for the server. */
    byte[] sendAll() throws IOException {
        byte[] bytes = flight();
        send(bytes);
        return bytes;
    }

    /**
     * Reads what the server sends until the engine has something to send or
     * to compute again, which is when the server's flight is in.
     */
    void receiveFlight() throws IOException {
        for (;;) {
            HandshakeStatus st = engine.getHandshakeStatus();
            if (st == HandshakeStatus.NEED_TASK) {
                tasks();
            } else if (st == HandshakeStatus.NEED_UNWRAP) {
                SSLEngineResult r = engine.unwrap(rx, app);
                if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    rx.compact();
                    int n = in.read(rx.array(), rx.position(), rx.remaining());
                    if (n < 0) {
                        throw new EOFException("the server closed the connection");
                    }
                    rx.position(rx.position() + n);
                    rx.flip();
                }
            } else {
                return;
            }
        }
    }

    /** Whether the handshake is over, for the client. */
    boolean finished() {
        HandshakeStatus st = engine.getHandshakeStatus();
        return st == HandshakeStatus.FINISHED || st == HandshakeStatus.NOT_HANDSHAKING;
    }

    /** Sends one byte of application data, which the server reads once it has the handshake. */
    void sendApplicationByte() throws IOException {
        net.clear();
        engine.wrap(ByteBuffer.wrap(new byte[] { 0 }), net);
        net.flip();
        byte[] bytes = new byte[net.remaining()];
        net.get(bytes);
        out.write(bytes);
        out.flush();
    }

    /** Runs the rest of the handshake. */
    void complete() throws IOException {
        while (!finished()) {
            if (engine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
                receiveFlight();
            } else {
                sendFlight();
            }
        }
    }

    public void close() throws IOException {
        socket.close();
    }
}
