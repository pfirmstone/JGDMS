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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * Drives a {@link SSLEngine} over a (blocking) {@link SocketChannel}, replacing
 * the {@code SSLSocket} record layer used by the historical {@code ssl}
 * transport.  This is the shared <em>keystone</em> asset the QUIC-TLS plan
 * (P1) banks: classic TLS, the UDS transport, and a future QUIC engine loop all
 * drive TLS 1.3 through an {@code SSLEngine} obtained from the JGDMS
 * {@code SSLContext} (custom {@code AuthManager} + {@code SubjectCredentials} +
 * SPIFFE matching), so the identity/trust machinery is reused unchanged --
 * only <em>how</em> TLS is driven (engine vs. socket) differs.
 *
 * <h2>What this class is (and is not)</h2>
 *
 * <p>This driver operates the engine in <strong>blocking</strong> mode over a
 * blocking channel: {@link #getInputStream()} / {@link #getOutputStream()}
 * expose the decrypted plaintext of the connection.  It deliberately does
 * <em>not</em> expose the underlying {@link SocketChannel} as a JERI
 * {@code Connection.getChannel()} value: the raw channel carries
 * <em>ciphertext</em>, and the JERI multiplexer's channel path
 * ({@code SocketChannelConnectionIO}) would read/write that ciphertext directly,
 * bypassing TLS.  Plaintext therefore always flows through the wrap/unwrap
 * streams, exactly as the {@code SSLSocket} transport routed plaintext through
 * the socket streams and returned {@code null} from {@code getChannel()}.  The
 * banked win is the engine keystone and a virtual-thread-friendly, non-{@code
 * java.net.Socket} byte layer -- not a plaintext NIO channel.
 *
 * <h2>Delegated tasks run inline on the calling (virtual) thread</h2>
 *
 * <p>When the handshake reports {@link HandshakeStatus#NEED_TASK}, this driver
 * runs each delegated task <strong>inline</strong> on the thread driving the
 * handshake (a virtual thread in JGDMS; see {@code no-threadlocal-virtual-
 * threads}), rather than offloading to a separate executor.  This is an
 * explicit, tested decision: JGDMS targets virtual threads, so a blocking
 * delegated task parks a cheap virtual thread rather than pinning a platform
 * thread, and inline execution keeps the handshake on one carrier without a
 * hand-off.  The future QUIC engine loop shares this assumption
 * ({@code QuicTLSEngine.getDelegatedTask()} is likewise driven inline), so the
 * behaviour is pinned by {@code SslEngineChannelTest}.
 *
 * <p>Not thread-safe for concurrent reads, nor for concurrent writes; a single
 * reader and a single writer may proceed concurrently (the read and write sides
 * use disjoint buffers and the engine's {@code wrap}/{@code unwrap} are
 * independent).  Concurrent readers, or concurrent writers, must synchronize
 * externally -- which the JERI mux does (one reader task, serialized writes).
 */
final class SslEngineChannel {

    private static final Logger logger =
        Logger.getLogger("net.jini.jeri.ssl.engine");

    /** The engine driving TLS 1.3 for this connection. */
    private final SSLEngine engine;

    /** The blocking channel carrying ciphertext. */
    private final SocketChannel channel;

    /** Human-readable label for logging/toString. */
    private final String label;

    /*
     * Buffer conventions (all kept in "ready to read" i.e. flipped state
     * between calls, except netIn which is kept in "fill" state):
     *
     *  - netOut : ciphertext produced by wrap(), drained to the channel.
     *  - netIn  : ciphertext read from the channel, consumed by unwrap().
     *             Kept in WRITE (fill) mode between reads; flipped around each
     *             unwrap and compacted after.
     *  - appIn  : plaintext produced by unwrap(), drained by getInputStream().
     */
    private ByteBuffer netOut;
    private ByteBuffer netIn;
    private ByteBuffer appIn;

    /** Sink for handshake-only unwraps (never carries application data). */
    private final ByteBuffer handshakeScratch;

    /** Guards the wrap side (handshake + application writes + close). */
    private final Object writeLock = new Object();
    /** Guards the unwrap side (handshake + application reads). */
    private final Object readLock = new Object();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** True once the inbound side has seen close_notify / EOF. */
    private volatile boolean inboundClosed = false;

    /**
     * Default handshake read timeout (ms): a GENEROUS bound so a stalled /
     * slowloris peer cannot pin the single accept-loop thread forever, while
     * never breaking a legitimately slow-but-progressing handshake.  A peer that
     * sends <em>nothing</em> for this long during the handshake is treated as
     * dead.  Overridable via the system property.
     */
    static final long DEFAULT_HANDSHAKE_TIMEOUT_MILLIS =
        Long.getLong("org.apache.river.jeri.ssl.handshakeTimeout", 60_000L);

    /**
     * Absolute deadline (System.currentTimeMillis()) by which each handshake
     * read must make progress, or 0 for no deadline.  Set only while the
     * handshake runs; application-data reads are never deadline-bounded (they
     * block normally on the JERI request lifecycle).
     */
    private long handshakeReadDeadline = 0L;

    private final InputStream in = new EngineInputStream();
    private final OutputStream out = new EngineOutputStream();

    /**
     * Creates a driver for the given engine over the given blocking channel.
     * The channel must already be connected and in blocking mode.  The engine's
     * client/server mode and client-auth requirements must already be set by
     * the caller; the constructor does not perform the handshake (call
     * {@link #handshake()}).
     */
    SslEngineChannel(SSLEngine engine, SocketChannel channel, String label)
        throws IOException
    {
        this.engine = engine;
        this.channel = channel;
        this.label = label;
        channel.configureBlocking(true);
        SSLSession session = engine.getSession();
        int packet = session.getPacketBufferSize();
        int app = session.getApplicationBufferSize();
        this.netOut = ByteBuffer.allocate(packet);
        this.netIn = ByteBuffer.allocate(packet);
        this.appIn = ByteBuffer.allocate(app);
        this.handshakeScratch = ByteBuffer.allocate(app);
        // appIn starts "drained" (nothing to read).
        this.appIn.position(0).limit(0);
    }

    /** Returns the completed handshake session. */
    SSLSession getSession() {
        return engine.getSession();
    }

    InputStream getInputStream() {
        return in;
    }

    OutputStream getOutputStream() {
        return out;
    }

    SocketAddress getRemoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    SocketChannel rawChannel() {
        return channel;
    }

    /**
     * Runs the TLS handshake to completion.  Drives {@code NEED_WRAP},
     * {@code NEED_UNWRAP}, {@code NEED_TASK} and {@code FINISHED} correctly,
     * flushing handshake ciphertext to the channel and reading peer handshake
     * records from it.  Must be called before application I/O.
     *
     * @throws SSLException if the handshake fails (including peer trust / SPIFFE
     *         rejection surfaced by the {@code AuthManager})
     * @throws IOException on channel failure or premature EOF
     */
    void handshake() throws IOException {
        handshake(DEFAULT_HANDSHAKE_TIMEOUT_MILLIS);
    }

    /**
     * Runs the handshake with a read-progress deadline (F2): each blocking read
     * of peer handshake ciphertext must make progress within {@code
     * timeoutMillis}, or the handshake fails with an {@link SSLException}.  This
     * bounds a stalled / slowloris peer that would otherwise pin the (single)
     * accept-loop thread indefinitely -- the accept thread runs the handshake
     * synchronously and the unwrap/fillNetIn loop would otherwise block forever
     * on a peer that connects but never sends.  The bound is on <em>inactivity
     * per read</em>, not total handshake time, so a legitimately slow-but-
     * progressing handshake is never broken.
     *
     * @param timeoutMillis per-read progress timeout; {@code <= 0} disables it
     */
    void handshake(long timeoutMillis) throws IOException {
        this.handshakeReadDeadline = (timeoutMillis > 0)
            ? addClamped(System.currentTimeMillis(), timeoutMillis)
            : 0L;

        /*
         * Drive the handshake under doPrivileged: JSSE's internal cert-path
         * validation and record I/O need incidental permissions (e.g.
         * RuntimePermission "readModuleTopology", channel SocketPermission) that
         * must be satisfied at THIS transport frame rather than going viral to
         * the caller's ACC (which, mid-remote-call, lacks them).  The old
         * SSLSocket path relied on JSSE's own internal doPrivileged for the same
         * reason.  This does NOT relax authentication: the trust decision is
         * made by the AuthManager's check{Client,Server}Trusted / SPIFFE
         * matching, which run regardless of this privilege scope.
         */
        try {
            java.security.AccessController.doPrivileged(
                (java.security.PrivilegedExceptionAction<Void>) () -> {
                    handshake0();
                    return null;
                });
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("TLS handshake failed", cause);
        } finally {
            // Application-data reads are never deadline-bounded.
            this.handshakeReadDeadline = 0L;
        }
    }

    /** Adds two non-negative longs, clamping overflow to Long.MAX_VALUE. */
    private static long addClamped(long a, long b) {
        long sum = a + b;
        return (sum < a) ? Long.MAX_VALUE : sum;
    }

    private void handshake0() throws IOException {
        engine.beginHandshake();
        HandshakeStatus status = engine.getHandshakeStatus();
        while (status != HandshakeStatus.FINISHED
               && status != HandshakeStatus.NOT_HANDSHAKING)
        {
            switch (status) {
                case NEED_TASK:
                    status = runDelegatedTasks();
                    break;
                case NEED_WRAP:
                    status = handshakeWrap();
                    break;
                case NEED_UNWRAP:
                case NEED_UNWRAP_AGAIN:
                    status = handshakeUnwrap();
                    break;
                default:
                    throw new SSLException(
                        "Unexpected handshake status: " + status);
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "TLS handshake complete on {0}: {1}",
                       new Object[] { label, engine.getSession().getCipherSuite() });
        }
    }

    /**
     * How a delegated task is executed.  Production always uses
     * {@link #INLINE_RUNNER} (run on the calling thread); tests substitute an
     * observer to assert the inline-on-virtual-thread contract.
     */
    interface TaskRunner {
        void run(Runnable task);
    }

    /** The production policy: run each delegated task inline on this thread. */
    static final TaskRunner INLINE_RUNNER = Runnable::run;

    private volatile TaskRunner taskRunner = INLINE_RUNNER;

    /**
     * Runs all currently-pending delegated tasks inline on the calling
     * (virtual) thread.  See the class comment: this is the explicit,
     * virtual-thread-first policy shared with the future QUIC engine loop.
     *
     * @return the handshake status after the tasks complete
     */
    private HandshakeStatus runDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            // Inline, on THIS (virtual) thread -- no executor hand-off.
            taskRunner.run(task);
        }
        return engine.getHandshakeStatus();
    }

    /**
     * Test seam: drives the handshake exactly like {@link #handshake()} but
     * routes each delegated task through {@code observer} instead of running it
     * directly.  A conforming observer must still {@code run()} the task; this
     * lets a test capture the thread on which delegated tasks execute and prove
     * they run inline on the driving (virtual) thread.  Package-private, for
     * {@code SslEngineChannelTest} only.
     */
    void handshakeWithTaskObserver(TaskRunner observer) throws IOException {
        this.taskRunner = observer;
        try {
            handshake();
        } finally {
            this.taskRunner = INLINE_RUNNER;
        }
    }

    /** Produces one handshake record via wrap() and flushes it to the channel. */
    private HandshakeStatus handshakeWrap() throws IOException {
        synchronized (writeLock) {
            netOut.clear();
            SSLEngineResult result = engine.wrap(EMPTY, netOut);
            flush(netOut);
            switch (result.getStatus()) {
                case OK:
                case CLOSED:
                    return result.getHandshakeStatus();
                case BUFFER_OVERFLOW:
                    // netOut sized to packet buffer; a handshake record should
                    // never overflow it, but grow defensively and retry.
                    growNetOut();
                    return engine.getHandshakeStatus();
                default:
                    throw new SSLException(
                        "Unexpected wrap status during handshake: "
                        + result.getStatus());
            }
        }
    }

    /** Reads and consumes one or more handshake records via unwrap(). */
    private HandshakeStatus handshakeUnwrap() throws IOException {
        synchronized (readLock) {
            for (;;) {
                netIn.flip();
                SSLEngineResult result;
                try {
                    // Handshake records carry no application data; unwrap into a
                    // scratch sink so appIn is untouched (and stays "drained"
                    // for the first application read).
                    handshakeScratch.clear();
                    result = engine.unwrap(netIn, handshakeScratch);
                } finally {
                    netIn.compact();
                }
                switch (result.getStatus()) {
                    case OK:
                        return result.getHandshakeStatus();
                    case BUFFER_UNDERFLOW:
                        if (!fillNetIn()) {
                            throw new EOFException(
                                "Connection closed during TLS handshake");
                        }
                        break; // retry unwrap with more ciphertext
                    case CLOSED:
                        inboundClosed = true;
                        throw new EOFException(
                            "Peer closed the connection during TLS handshake");
                    case BUFFER_OVERFLOW:
                        // handshakeScratch is application-buffer-sized; a
                        // handshake message never carries application data, so
                        // this should not occur -- treat as a protocol error.
                        throw new SSLException(
                            "Unexpected buffer overflow during TLS handshake");
                    default:
                        throw new SSLException(
                            "Unexpected unwrap status during handshake: "
                            + result.getStatus());
                }
            }
        }
    }

    /* -- Application data I/O -- */

    /**
     * Reads up to {@code len} plaintext bytes into {@code b}, blocking until at
     * least one byte is available, EOF, or error.  Returns -1 at clean EOF.
     *
     * <p>Runs the {@code unwrap} + channel read under {@code doPrivileged} for
     * the same reason as the handshake: JSSE's record processing and the raw
     * channel I/O need incidental permissions that must not go viral to the
     * caller's ACC.  Authentication is unaffected -- trust was decided at the
     * handshake.
     */
    private int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        try {
            return java.security.AccessController.doPrivileged(
                (java.security.PrivilegedExceptionAction<Integer>) () ->
                    read0(b, off, len));
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("TLS read failed", cause);
        }
    }

    private int read0(byte[] b, int off, int len) throws IOException {
        synchronized (readLock) {
            while (!appIn.hasRemaining()) {
                if (inboundClosed) {
                    return -1;
                }
                if (!unwrapMore()) {
                    return -1; // clean inbound close_notify / EOF
                }
            }
            int n = Math.min(len, appIn.remaining());
            appIn.get(b, off, n);
            return n;
        }
    }

    /**
     * Pulls more ciphertext from the channel and unwraps it into {@code appIn}.
     *
     * @return {@code true} if plaintext became available; {@code false} on a
     *         clean inbound close (close_notify or EOF with no partial record)
     */
    private boolean unwrapMore() throws IOException {
        // appIn is drained here (caller checked !hasRemaining); prepare to fill.
        appIn.clear();
        for (;;) {
            netIn.flip();
            SSLEngineResult result;
            try {
                result = engine.unwrap(netIn, appIn);
            } finally {
                netIn.compact();
            }
            switch (result.getStatus()) {
                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        runDelegatedTasks();
                    }
                    if (appIn.position() > 0) {
                        appIn.flip();
                        return true;
                    }
                    // A post-handshake message (e.g. TLS 1.3 NewSessionTicket)
                    // with no app data: keep reading.
                    break;
                case BUFFER_UNDERFLOW:
                    if (!fillNetIn()) {
                        // EOF.  A truncated record is a protocol error; an empty
                        // one at a record boundary is a clean close.
                        inboundClosed = true;
                        if (netIn.position() > 0) {
                            throw new EOFException(
                                "Connection truncated mid-TLS-record");
                        }
                        return false;
                    }
                    break;
                case CLOSED:
                    inboundClosed = true;
                    return false;
                case BUFFER_OVERFLOW:
                    growAppIn();
                    break;
                default:
                    throw new SSLException(
                        "Unexpected unwrap status: " + result.getStatus());
            }
        }
    }

    /**
     * Encrypts and writes {@code len} plaintext bytes from {@code b}.  Runs
     * under {@code doPrivileged} (see {@link #read}) so JSSE's wrap and the raw
     * channel write do not leak incidental permissions to the caller's ACC.
     */
    private void write(byte[] b, int off, int len) throws IOException {
        try {
            java.security.AccessController.doPrivileged(
                (java.security.PrivilegedExceptionAction<Void>) () -> {
                    write0(b, off, len);
                    return null;
                });
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("TLS write failed", cause);
        }
    }

    private void write0(byte[] b, int off, int len) throws IOException {
        synchronized (writeLock) {
            if (closed.get()) {
                throw new IOException("Connection closed");
            }
            ByteBuffer src = ByteBuffer.wrap(b, off, len);
            while (src.hasRemaining()) {
                netOut.clear();
                SSLEngineResult result = engine.wrap(src, netOut);
                switch (result.getStatus()) {
                    case OK:
                        flush(netOut);
                        break;
                    case BUFFER_OVERFLOW:
                        growNetOut();
                        break;
                    case CLOSED:
                        throw new IOException(
                            "Connection closed by TLS engine during write");
                    default:
                        throw new SSLException(
                            "Unexpected wrap status: " + result.getStatus());
                }
            }
        }
    }

    /* -- Buffer / channel helpers -- */

    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    /** Writes the buffer (flipped) fully to the channel. */
    private void flush(ByteBuffer buf) throws IOException {
        buf.flip();
        while (buf.hasRemaining()) {
            if (channel.write(buf) < 0) {
                throw new EOFException("Channel closed during write");
            }
        }
    }

    /**
     * Reads more ciphertext into {@code netIn} (kept in fill/compacted state).
     *
     * @return {@code false} on channel EOF
     */
    private boolean fillNetIn() throws IOException {
        if (!netIn.hasRemaining()) {
            growNetIn();
        }
        if (handshakeReadDeadline > 0L) {
            return fillNetInByDeadline();
        }
        int n = channel.read(netIn);
        return n >= 0;
    }

    /**
     * F2: reads ciphertext into {@code netIn} with a per-read progress deadline
     * (handshake only).  Temporarily puts the channel in non-blocking mode and
     * waits on a {@link java.nio.channels.Selector} for readability up to the
     * remaining time; a read that makes no progress by the deadline fails the
     * handshake with an {@link SSLException} (a stalled/slowloris peer).  The
     * channel is always restored to blocking mode before returning, so
     * application-data I/O is unaffected.
     */
    private boolean fillNetInByDeadline() throws IOException {
        java.nio.channels.Selector selector = null;
        boolean wasBlocking = channel.isBlocking();
        try {
            channel.configureBlocking(false);
            selector = java.nio.channels.Selector.open();
            channel.register(selector, java.nio.channels.SelectionKey.OP_READ);
            for (;;) {
                long remaining = handshakeReadDeadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    throw new SSLException(
                        "TLS handshake read timed out on " + label
                        + " (no peer progress within the handshake timeout) -- "
                        + "fail-secure against a stalled/slowloris peer");
                }
                int ready = selector.select(remaining);
                if (ready == 0) {
                    // select() woke with nothing ready: either the deadline
                    // elapsed (loop re-checks) or a spurious wakeup (retry).
                    continue;
                }
                selector.selectedKeys().clear();
                int n = channel.read(netIn);
                if (n < 0) {
                    return false; // clean EOF
                }
                if (n > 0) {
                    return true;  // progress made
                }
                // n == 0: readable but no bytes yet; loop until deadline.
            }
        } finally {
            if (selector != null) {
                try { selector.close(); } catch (IOException e) { }
            }
            // Restore blocking mode for application-data I/O (best effort; if the
            // channel was already closed this throws harmlessly).
            try {
                if (wasBlocking && channel.isOpen()) {
                    channel.configureBlocking(true);
                }
            } catch (IOException e) {
                // channel closing concurrently; the caller handles the failure.
            }
        }
    }

    private void growNetOut() {
        // netOut is transient (cleared before each wrap), so nothing to
        // preserve; grow to at least the engine's current packet buffer size.
        int want = Math.max(engine.getSession().getPacketBufferSize(),
                            netOut.capacity() * 2);
        netOut = ByteBuffer.allocate(want);
    }

    private void growNetIn() {
        int want = Math.max(engine.getSession().getPacketBufferSize(),
                            netIn.capacity() * 2);
        ByteBuffer bigger = ByteBuffer.allocate(want);
        netIn.flip();
        bigger.put(netIn);
        netIn = bigger; // left in fill state
    }

    private void growAppIn() {
        int want = Math.max(engine.getSession().getApplicationBufferSize(),
                            appIn.capacity() * 2);
        ByteBuffer bigger = ByteBuffer.allocate(want);
        // appIn is in fill state here (mid-unwrap); preserve any partial data.
        appIn.flip();
        bigger.put(appIn);
        appIn = bigger;
    }

    /* -- Close -- */

    /**
     * Initiates an orderly TLS close (sends close_notify) and closes the
     * channel.  Idempotent.
     */
    void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = null;
        try {
            java.security.AccessController.doPrivileged(
                (java.security.PrivilegedExceptionAction<Void>) () -> {
                    synchronized (writeLock) {
                        engine.closeOutbound();
                        // Flush the close_notify record(s).
                        while (!engine.isOutboundDone()) {
                            netOut.clear();
                            SSLEngineResult result = engine.wrap(EMPTY, netOut);
                            flush(netOut);
                            if (result.getStatus() == Status.CLOSED) {
                                break;
                            }
                        }
                    }
                    return null;
                });
        } catch (java.security.PrivilegedActionException e) {
            Throwable cause = e.getCause();
            failure = (cause instanceof IOException)
                ? (IOException) cause
                : new IOException("TLS close failed", cause);
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    @Override
    public String toString() {
        return "SslEngineChannel[" + label + "]";
    }

    /* -- Stream adapters -- */

    private final class EngineInputStream extends InputStream {
        private final byte[] one = new byte[1];

        @Override
        public int read() throws IOException {
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xff);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            return SslEngineChannel.this.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            SslEngineChannel.this.close();
        }
    }

    private final class EngineOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            SslEngineChannel.this.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            SslEngineChannel.this.close();
        }
    }
}
