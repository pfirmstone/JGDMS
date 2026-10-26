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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import net.jini.security.Security;
import org.apache.river.action.GetLongAction;

/**
 * Counts the server handshakes of one listener, by outcome, and reports
 * failures at INFO at most once per interval.
 *
 * <p>A handshake fails for reasons the server cannot control, and on a
 * listener that is reachable from the internet a scanner or a health check
 * can make thousands of them, so each failure is logged at FINE only and the
 * operator sees a summary line with the counts by kind.  The summary is
 * written by the thread that records a failure, when the interval has passed
 * since the last one; there is no timer.  The counters are always current and
 * are read by tests through {@link #getFailed(Kind)} and the other accessors.
 */
final class HandshakeCounters {

    /** Why a handshake did not complete. */
    enum Kind {
        /** The peer closed the connection or it was reset. */
        PEER_CLOSED("peer closed"),
        /** A TLS protocol error or alert, including an untrusted peer. */
        TLS("TLS failure"),
        /** The handshake completed but the connection was refused. */
        REJECTED("rejected"),
        /** The connection failed in some other way. */
        IO("I/O error"),
        /** The handshake was working and did not complete in time. */
        TIMEOUT("timed out working"),
        /**
         * The connection did not send its first record, or was given no turn
         * to work, before the time that a handshake needs was gone.
         */
        QUEUE_TIMEOUT("timed out waiting for a turn"),
        /** The first bytes were not the start of a TLS ClientHello. */
        JUNK("not TLS"),
        /** The listener was closed while the handshake was in progress. */
        LISTENER_CLOSED("listener closed"),
        /** A RuntimeException or Error in the handshake task. */
        INTERNAL("internal error");

        final String description;

        Kind(String description) {
            this.description = description;
        }
    }

    /** The default interval between summary lines: 30 seconds. */
    static final long DEFAULT_SUMMARY_INTERVAL_MILLIS = 30_000L;

    private static final Kind[] KINDS = Kind.values();

    private final Logger logger;
    private final String listener;
    private final long summaryIntervalNanos;

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLongArray failed = new AtomicLongArray(KINDS.length);

    /** The counts at the last summary; guarded by this. */
    private long reportedAccepted;
    private long reportedCompleted;
    private final long[] reportedFailed = new long[KINDS.length];

    private final AtomicLong lastSummaryNanos = new AtomicLong(System.nanoTime());

    HandshakeCounters(Logger logger, String listener) {
        this.logger = logger;
        this.listener = listener;
        long millis = ((Long) Security.doPrivileged(
            new GetLongAction(
                "org.apache.river.jeri.ssl.handshakeSummaryInterval",
                DEFAULT_SUMMARY_INTERVAL_MILLIS))).longValue();
        this.summaryIntervalNanos = millis > 0
            ? Math.min(millis, Long.MAX_VALUE / 1_000_000L) * 1_000_000L
            : 0L;
    }

    /** Notes a connection accepted and handed to a handshake task. */
    void noteAccepted() {
        accepted.incrementAndGet();
    }

    /** Notes a handshake that completed. */
    void noteCompleted() {
        completed.incrementAndGet();
    }

    /** Notes a handshake that failed, and writes a summary if one is due. */
    void noteFailed(Kind kind) {
        failed.incrementAndGet(kind.ordinal());
        if (summaryIntervalNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        long last = lastSummaryNanos.get();
        if (now - last >= summaryIntervalNanos
            && lastSummaryNanos.compareAndSet(last, now))
        {
            summarize(now - last);
        }
    }

    /** Returns the number of connections accepted. */
    long getAccepted() {
        return accepted.get();
    }

    /** Returns the number of handshakes that completed. */
    long getCompleted() {
        return completed.get();
    }

    /** Returns the number of handshakes that failed for the given reason. */
    long getFailed(Kind kind) {
        return failed.get(kind.ordinal());
    }

    /** Returns the number of handshakes that failed for any reason. */
    long getFailed() {
        long sum = 0L;
        for (int i = 0; i < KINDS.length; i++) {
            sum += failed.get(i);
        }
        return sum;
    }

    /** Classifies a handshake failure. */
    static Kind classify(Throwable t) {
        if (t instanceof FirstRecord.NotTlsException) {
            return Kind.JUNK;
        }
        int depth = 0;
        for (Throwable c = t; c != null && depth < 10; c = c.getCause(), depth++) {
            if (c instanceof SocketTimeoutException) {
                return Kind.TIMEOUT;
            }
        }
        if (t instanceof EOFException
            || t instanceof ClosedChannelException
            || t instanceof SocketException)
        {
            return Kind.PEER_CLOSED;
        } else if (t instanceof SSLException) {
            Throwable cause = t.getCause();
            if (cause instanceof IOException && !(cause instanceof SSLException)) {
                return classify(cause);
            }
            return Kind.TLS;
        } else if (t instanceof IOException) {
            return t.getCause() instanceof SecurityException
                ? Kind.REJECTED : Kind.IO;
        }
        return Kind.INTERNAL;
    }

    private synchronized void summarize(long elapsedNanos) {
        long a = accepted.get();
        long c = completed.get();
        StringBuilder sb = new StringBuilder();
        long total = 0L;
        for (int i = 0; i < KINDS.length; i++) {
            long f = failed.get(i);
            long delta = f - reportedFailed[i];
            reportedFailed[i] = f;
            if (delta > 0L) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(KINDS[i].description).append(' ').append(delta);
                total += delta;
            }
        }
        long acceptedDelta = a - reportedAccepted;
        long completedDelta = c - reportedCompleted;
        reportedAccepted = a;
        reportedCompleted = c;
        if (total == 0L || !logger.isLoggable(Level.INFO)) {
            return;
        }
        logger.log(Level.INFO,
                   "{0}: {1} of {2} connections failed the TLS handshake in "
                   + "the last {3} s ({4}), {5} completed",
                   new Object[] {
                       listener, total, acceptedDelta,
                       Math.max(1L, elapsedNanos / 1_000_000_000L),
                       sb, completedDelta
                   });
    }
}
