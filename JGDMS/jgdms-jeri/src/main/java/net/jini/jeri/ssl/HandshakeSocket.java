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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 * The socket that a server's {@code SSLSocket} is layered over, which tells
 * when the server's first flight has been written.
 *
 * <p>The JDK does the whole of a handshake inside {@code startHandshake}, so
 * the work for the processor (the key exchange and the signature that follow
 * the ClientHello) cannot be told from the waits for the peer, as it can with
 * an {@code SSLEngine}.  What can be seen is the streams: the JDK writes the
 * server's flight and then reads the answer of the peer.  So the first read
 * after the first write ends the window in which the handshake holds its
 * permit to compute, and a peer that replays a ClientHello and then stalls
 * holds that permit for no longer than the work takes.
 *
 * <p>Everything else is the underlying socket's: the addresses, the options,
 * the timeouts, the shutdowns and the close.  The streams are the ones that
 * the owner already holds, so that bytes that a stream has buffered are not
 * lost to a second stream from the same socket.
 */
final class HandshakeSocket extends Socket {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Runnable flightWritten;

    /** Set by the first write, after which the next read ends the window. */
    private volatile boolean written;

    /** Set when the window has ended, once. */
    private volatile boolean ended;

    /**
     * @param socket the connected socket of the peer
     * @param rawIn the one input stream of the socket
     * @param rawOut the one output stream of the socket
     * @param flightWritten run once, on the first read that follows the first
     *        write
     */
    HandshakeSocket(Socket socket, InputStream rawIn, OutputStream rawOut,
                    Runnable flightWritten)
    {
        this.socket = socket;
        this.flightWritten = flightWritten;
        this.in = new InputStream() {
            public int read() throws IOException {
                end();
                return rawIn.read();
            }

            public int read(byte[] b, int off, int len) throws IOException {
                end();
                return rawIn.read(b, off, len);
            }

            public int available() throws IOException {
                return rawIn.available();
            }

            public void close() throws IOException {
                rawIn.close();
            }
        };
        this.out = new OutputStream() {
            public void write(int b) throws IOException {
                written = true;
                rawOut.write(b);
            }

            public void write(byte[] b, int off, int len) throws IOException {
                written = true;
                rawOut.write(b, off, len);
            }

            public void flush() throws IOException {
                rawOut.flush();
            }

            public void close() throws IOException {
                rawOut.close();
            }
        };
    }

    private void end() {
        if (written && !ended) {
            ended = true;
            flightWritten.run();
        }
    }

    @Override
    public InputStream getInputStream() {
        return in;
    }

    @Override
    public OutputStream getOutputStream() {
        return out;
    }

    /** Null, like the channel of the socket that this one stands for. */
    @Override
    public SocketChannel getChannel() {
        return null;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        socket.connect(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        socket.connect(endpoint, timeout);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        socket.bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return socket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return socket.getPort();
    }

    @Override
    public int getLocalPort() {
        return socket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return socket.getLocalSocketAddress();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        socket.setTcpNoDelay(on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return socket.getTcpNoDelay();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        socket.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return socket.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        socket.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        socket.setOOBInline(on);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return socket.getOOBInline();
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return socket.getSoTimeout();
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        socket.setSendBufferSize(size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return socket.getSendBufferSize();
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        socket.setReceiveBufferSize(size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return socket.getReceiveBufferSize();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        socket.setKeepAlive(on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return socket.getKeepAlive();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        socket.setTrafficClass(tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return socket.getTrafficClass();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        socket.setReuseAddress(on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return socket.getReuseAddress();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public void shutdownInput() throws IOException {
        socket.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        socket.shutdownOutput();
    }

    @Override
    public String toString() {
        return socket.toString();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public boolean isBound() {
        return socket.isBound();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
                                          int bandwidth)
    {
        socket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    @Override
    public <T> Socket setOption(SocketOption<T> name, T value) throws IOException {
        socket.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return socket.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return socket.supportedOptions();
    }
}
