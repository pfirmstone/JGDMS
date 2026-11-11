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
package au.zeus.jgdms.security.jwt;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.security.AccessController;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads an HTTP response body under one total time limit.
 *
 * <p>{@code HttpRequest.timeout} covers only the wait for the response headers,
 * and {@code java.net.http} has no per-read timeout, so a server that sends a byte
 * every few seconds would hold the reading thread for as long as the size cap
 * allows.  When the deadline passes a watchdog (1) interrupts the reading thread, which
 * releases a read that blocks interruptibly (the JDK's response body stream does) and
 * makes the reader throw {@link SocketTimeoutException} at once, and (2) hands the
 * stream's {@code close()} to a small, bounded pool of closer threads; closing cancels
 * the exchange.  The reader never waits for that close, so a {@code close()} that
 * blocks, or a backlog of them, cannot delay any deadline.  The reader reports a
 * timeout whatever the read returned, because a stream cut short can look like a
 * normal end of data.  A read that blocks uninterruptibly (not the JDK's stream) is
 * still released only by the close, so for such a stream the bound holds only while
 * a closer thread is free.
 *
 * <p>The close pool has {@value #MAX_CLOSERS} threads and a queue of
 * {@value #CLOSE_QUEUE_CAPACITY}; a close that finds both full is dropped and logged
 * (SEVERE, no token text), because the other choices, a close on the scheduler thread
 * or a new thread per close, would let a server that never answers a close either stall
 * every deadline or exhaust threads.  A dropped close leaks only that exchange until
 * its {@code HttpClient} is closed at logout.
 *
 * <p><b>Security manager.</b>  This class runs in the trusted client JVM, which
 * runs under a SecurityManager.  Every thread it needs is created inside
 * {@code AccessController.doPrivileged} by {@link NewDaemonThread}, so the one
 * permission needed ({@code RuntimePermission "createPlatformThread"}, plus
 * {@code "setContextClassLoader"}, which {@link NewDaemonThread} uses) is checked against
 * this module's protection domain and not against whichever application code
 * happened to read a body first.  That keeps a policy that grants them to the
 * module's codebase enough, and an auditing SecurityManager (polpAudit) records
 * them against that codebase only.  The scheduler thread is started eagerly when
 * this class is initialised, so a missing grant fails there, loudly
 * ({@link ExceptionInInitializerError}), and not on a first read.  Threads created
 * later (a replacement, or a closer) go through the same privileged factory, so no
 * thread keeps the context of the caller that happened to cause its creation.
 * NewDaemonThread, which calls nothing outside this module and the JDK, so that no
 * other jar enters the new thread's inherited context, gives each thread the system class loader as its context class
 * loader, as the rest of JGDMS does, so a thread cannot pin an application loader.
 */
final class BoundedBody {

    /** Default total time allowed for reading one response body. */
    static final long DEFAULT_DEADLINE_MS = 10_000L;
    /** Smallest and largest value of the {@code responseBodyTimeoutMs} option. */
    static final long MIN_DEADLINE_MS = 100L;
    static final long MAX_DEADLINE_MS = 120_000L;

    /** Most threads that may be closing streams at once. */
    private static final int MAX_CLOSERS = 4;
    /** Closes that may wait for a closer thread; a close beyond this is dropped. */
    static final int CLOSE_QUEUE_CAPACITY = 16;

    private static final Logger LOG = Logger.getLogger(BoundedBody.class.getName());
    private static final AtomicLong DROPPED_CLOSES = new AtomicLong();

    /**
     * Fires the deadlines and does nothing else, so a slow close cannot hold a
     * later deadline: the close runs on {@link #CLOSERS}.
     */
    private static final ScheduledThreadPoolExecutor WATCHDOG;
    /**
     * Closes the streams whose deadline passed, and the clients of a logout.  A close that
     * blocks holds one of at most {@link #MAX_CLOSERS} threads; at most
     * {@link #CLOSE_QUEUE_CAPACITY} more wait; idle threads end after 30 s.
     */
    private static final ThreadPoolExecutor CLOSERS;

    static {
        ScheduledThreadPoolExecutor w;
        ThreadPoolExecutor c;
        try {
            w = new ScheduledThreadPoolExecutor(1, r -> newThread(r, "JwtLoginModule-body-watchdog"));
            w.setRemoveOnCancelPolicy(true);
            c = new ThreadPoolExecutor(MAX_CLOSERS, MAX_CLOSERS, 30L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(CLOSE_QUEUE_CAPACITY), r -> newThread(r, "JwtLoginModule-body-closer"));
            c.allowCoreThreadTimeOut(true);
            // Started now, so that a missing permission fails here and not at the first read.
            w.prestartAllCoreThreads();
        } catch (RuntimeException | Error e) {
            // Whatever the cause (a SecurityException for a missing thread permission, or an
            // Error from the lambda machinery under a SecurityManager) the class must not
            // initialise: a read without a deadline is not an option.
            throw new ExceptionInInitializerError(e);
        }
        WATCHDOG = w;
        CLOSERS = c;
    }

    private BoundedBody() {}

    /**
     * Creates a daemon thread in a privileged block of its own, each time the
     * executor needs one, so the new thread inherits no caller's context.
     */
    private static Thread newThread(Runnable r, String name) {
        return AccessController.doPrivileged(new NewDaemonThread(r, name, false));
    }

    /**
     * Creates and starts a daemon thread named {@code name} in one privileged block, so
     * the thread permissions are checked against this module and
     * never against the code that called the module, and the thread inherits no caller's
     * context.  Its context class loader is the system class loader ({@link NewDaemonThread}).
     * Used for the long-lived refresh thread.
     *
     * @throws SecurityException if the module is not granted the thread permissions
     */
    static Thread startThread(Runnable r, String name) {
        return AccessController.doPrivileged(new NewDaemonThread(r, name, true));
    }

    /**
     * Runs {@code close} on a closer thread without waiting for it.  Never blocks and
     * never runs on the caller or the scheduler thread: if the pool and its queue are
     * full the close is dropped and logged (see the class comment).  Anything the close
     * throws is logged at FINE and ends that task only.
     */
    static void closeAsync(Runnable close) {
        Runnable guarded = () -> {
            try {
                close.run();
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "close failed: " + e.getClass().getName());
            }
        };
        try {
            CLOSERS.execute(guarded);
        } catch (RejectedExecutionException e) {
            long n = DROPPED_CLOSES.incrementAndGet();
            if (n == 1 || n % 100 == 0)
                LOG.log(Level.SEVERE, "a close was dropped because " + MAX_CLOSERS + " closes are blocked and "
                        + CLOSE_QUEUE_CAPACITY + " more are waiting (" + n + " dropped so far)");
        }
    }

    /** Test hook: the most closes that may wait, as the real queue reports it. */
    static int closeQueueCapacity() {
        return CLOSERS.getQueue().size() + CLOSERS.getQueue().remainingCapacity();
    }

    /** Test hook: closes waiting for a closer thread. */
    static int closeQueueSize() {
        return CLOSERS.getQueue().size();
    }

    /** Test hook: closer threads that exist. */
    static int closerThreads() {
        return CLOSERS.getPoolSize();
    }

    /** Test hook: closes dropped because the pool and queue were full. */
    static long droppedCloses() {
        return DROPPED_CLOSES.get();
    }

    /** What the reader and the watchdog agree on, under this object's monitor. */
    private static final class Reading {
        private boolean done;
        private boolean expired;

        /** The deadline passed: true if the read was still running (then it has been interrupted). */
        synchronized boolean expire(Thread reader) {
            if (done) return false;
            expired = true;
            try {
                reader.interrupt();
            } catch (SecurityException e) {
                // No right to interrupt that thread: the close, which follows, is the only wake-up.
            }
            return true;
        }

        /** The read ended: true if the deadline passed first. */
        synchronized boolean finish() {
            done = true;
            return expired;
        }
    }

    /**
     * Reads at most {@code limit + 1} bytes (one over the cap, so the caller can
     * tell that it was exceeded) within {@code deadlineMs} in total.  The stream is
     * closed: by this method when the read ended in time, by a closer thread when the
     * deadline passed first (this method does not wait for that close).
     *
     * @throws SocketTimeoutException if the deadline passed before the body ended
     * @throws IOException            on any other read failure
     */
    static byte[] read(InputStream in, int limit, long deadlineMs) throws IOException {
        final Thread reader = Thread.currentThread();
        final Reading reading = new Reading();
        ScheduledFuture<?> watch = WATCHDOG.schedule(() -> {
            if (reading.expire(reader)) closeAsync(() -> {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // The reader sees the timeout flag; there is nothing else to do with it.
                }
            });
        }, deadlineMs, TimeUnit.MILLISECONDS);
        byte[] bytes = null;
        IOException failure = null;
        boolean timedOut;
        try {
            bytes = in.readNBytes(limit + 1);
        } catch (IOException e) {
            failure = e;
        } finally {
            watch.cancel(false);
            timedOut = reading.finish();
            if (timedOut) {
                // Clear the interrupt the watchdog raised; finish() orders it before this point.
                Thread.interrupted();
            } else {
                try {
                    in.close();
                } catch (IOException e) {
                    if (failure == null) failure = e; else failure.addSuppressed(e);
                }
            }
        }
        if (timedOut) throw timeout(deadlineMs);
        if (failure != null) throw failure;
        return bytes;
    }

    private static SocketTimeoutException timeout(long deadlineMs) {
        return new SocketTimeoutException("response body not complete within " + deadlineMs + " ms");
    }

    /**
     * Parses the {@code responseBodyTimeoutMs} option.
     *
     * @param value the option value, or null for the default
     * @throws IllegalArgumentException if it is not a whole number in the allowed range
     */
    static long parseDeadline(Object value) {
        if (value == null || value.toString().isBlank()) return DEFAULT_DEADLINE_MS;
        long ms;
        try {
            ms = Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("responseBodyTimeoutMs is not a whole number");
        }
        if (ms < MIN_DEADLINE_MS || ms > MAX_DEADLINE_MS)
            throw new IllegalArgumentException("responseBodyTimeoutMs must be between "
                    + MIN_DEADLINE_MS + " and " + MAX_DEADLINE_MS);
        return ms;
    }
}
