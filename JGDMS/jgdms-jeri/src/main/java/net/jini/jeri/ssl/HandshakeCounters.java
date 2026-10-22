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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * operator sees a summary line with the counts by kind.  The line says how
 * many handshakes failed, were accepted and completed in the time since the
 * last line; they are the counts of that time, not of one set of connections,
 * for a connection that fails now may have been accepted earlier.
 *
 * <p>A failure is reported at once if the interval has passed since the last
 * line, by the thread that records it, and otherwise by a timer, when the
 * interval has passed, so that the last failures of a burst are reported
 * within about an interval although no more come; there is at most one timer
 * task, and a line is written by another thread than the timer's, which must
 * not wait.  Closing the listener writes what is left.  The counters are
 * always current and are read by tests through {@link #getFailed(Kind)} and
 * the other accessors.
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
        /** The peer did not answer a flight of the server in the time allowed. */
        FLIGHT_TIMEOUT("client did not answer the server's flight"),
        /**
         * The connection did not send its ClientHello, or was given no turn
         * to work, before the time that a handshake needs was gone.
         */
        QUEUE_TIMEOUT("timed out waiting for a turn"),
        /** The bytes were not a TLS ClientHello that the server takes. */
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

    /** Whether a task of the timer is waiting to write the summary. */
    private final AtomicBoolean timerPending = new AtomicBoolean();

    /** The task that is waiting, while {@code timerPending}. */
    private volatile ScheduledFuture<?> timer;

    /** Set when the listener is closed; no timer is started after it. */
    private volatile boolean closed;

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

    /**
     * Notes a handshake that failed, and writes a summary if one is due, or
     * has a timer write it when it falls due.
     */
    void noteFailed(Kind kind) {
        failed.incrementAndGet(kind.ordinal());
        if (summaryIntervalNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        long last = lastSummaryNanos.get();
        if (now - last >= summaryIntervalNanos) {
            if (lastSummaryNanos.compareAndSet(last, now)) {
                summarize(now - last);
            }
        } else {
            startTimer(last + summaryIntervalNanos - now);
        }
    }

    /**
     * Has the summary written after the delay, unless a timer task is waiting
     * to write it already.
     */
    private void startTimer(long delayNanos) {
        if (closed || !timerPending.compareAndSet(false, true)) {
            return;
        }
        try {
            timer = HandshakeWatchdog.schedule(this::timerDue, delayNanos);
            if (closed) {
                /* Closed meanwhile: close may not have seen the task. */
                timer.cancel(false);
            }
        } catch (RuntimeException e) {
            /* The timer is shut down or out of memory: close reports. */
            timerPending.set(false);
        }
    }

    /**
     * Runs on the timer's thread, which must not wait: only hands the
     * writing of the line to another thread.
     */
    private void timerDue() {
        HandshakeWatchdog.handOff(this::report);
    }

    /** Writes the summary of what the timer was waiting for, if it is due. */
    private void report() {
        timerPending.set(false);
        if (closed) {
            return;
        }
        long now = System.nanoTime();
        long last = lastSummaryNanos.get();
        if (now - last >= summaryIntervalNanos) {
            if (lastSummaryNanos.compareAndSet(last, now)) {
                summarize(now - last);
            }
        } else if (unreported()) {
            /*
             * A line was written after this task was scheduled, and a failure
             * that came after it found the task pending and scheduled none:
             * it is reported when the interval from that line has passed.
             */
            startTimer(last + summaryIntervalNanos - now);
        }
    }

    /** Returns whether a failure has been counted that no line has reported. */
    private synchronized boolean unreported() {
        for (int i = 0; i < KINDS.length; i++) {
            if (failed.get(i) > reportedFailed[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops the timer and writes what has not been reported, whether the
     * interval has passed or not.  Called when the listener is closed; a
     * failure that is counted after that, as the handshakes that the close
     * cut off are, is reported by the next failure of an interval later, if
     * there is one.
     */
    void close() {
        closed = true;
        ScheduledFuture<?> t = timer;
        if (t != null) {
            t.cancel(false);
        }
        if (summaryIntervalNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        long last = lastSummaryNanos.getAndSet(now);
        summarize(now - last);
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
        if (t instanceof ClientHelloReader.NotTlsException) {
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
                   "{0}: TLS handshakes failed {1} ({2}), accepted {3}, "
                   + "completed {4} in the last {5} s",
                   new Object[] {
                       listener, total, sb, acceptedDelta, completedDelta,
                       Math.max(1L, elapsedNanos / 1_000_000_000L)
                   });
    }
}
