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

import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.security.auth.Subject;
import net.jini.security.Security;
import org.apache.river.action.GetIntegerAction;

/**
 * Runs the cryptographic work of server handshakes on a few platform threads.
 *
 * <p>A server handshake runs on a virtual thread, and a virtual thread keeps
 * its carrier until it blocks.  The work that the engine hands out as
 * delegated tasks (the key exchange and signature of the first flight, the
 * certificate chain and signature verification of the second) takes
 * milliseconds without blocking, and the carriers are shared with the threads
 * that dispatch calls, which queue behind it: with as many handshakes at work
 * as there are carriers, a call on a connection that exists waits for a
 * handshake to block, not for the processor.  The operating system, unlike
 * the scheduler of the virtual threads, shares the processors among platform
 * threads by time and wakes a thread that blocks often ahead of one that
 * computes, so the delegated tasks run here, on at most {@code threads}
 * threads, and the handshake thread waits for the result and gives up its
 * carrier meanwhile.  The number of threads is also the most processors that
 * handshakes can use for their cryptography at once.
 *
 * <p>The threads are daemon threads created in a privileged action under
 * {@code Subject.callAs(null, ...)}, so that they are subject-neutral like the
 * accept thread, and not those of the system thread pool.  One pool of each
 * size serves the JVM.
 */
final class HandshakeCompute {

    /**
     * The system property that sets the number of threads: 0 runs the tasks
     * inline on the handshake's virtual thread, as before.
     */
    static final String THREADS_PROPERTY =
        "org.apache.river.jeri.ssl.serverHandshakeComputeThreads";

    private static final Map<Integer, HandshakeCompute> SHARED = new HashMap<>();

    private final ThreadPoolExecutor pool;
    private final int threads;
    private final AtomicLong ran = new AtomicLong();

    private HandshakeCompute(int threads) {
        this.threads = threads;
        final AtomicInteger n = new AtomicInteger();
        pool = new ThreadPoolExecutor(
            threads, threads, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            task -> AccessController.doPrivileged(
                (PrivilegedAction<Thread>) () -> Subject.callAs(null, () -> {
                    Thread t = new Thread(task, "TLS handshake compute "
                                                + n.incrementAndGet());
                    t.setDaemon(true);
                    t.setContextClassLoader(
                        HandshakeCompute.class.getClassLoader());
                    return t;
                })));
        pool.allowCoreThreadTimeOut(true);
    }

    /**
     * Returns the default number of threads: a quarter of the processors, at
     * least one, which leaves the rest to the threads that serve established
     * connections and, on a machine that its clients share, to them.  Half
     * of the processors left the tail latency of calls during a storm of
     * new connections at seconds on four processors, one thread at
     * milliseconds; RESULTS.md, thirty-seventh section.
     */
    static int defaultThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    }

    /**
     * Returns the pool that the system property asks for (the default size if
     * it is unset or negative), or null if it asks for none.
     */
    static HandshakeCompute get() {
        int threads = ((Integer) Security.doPrivileged(
            new GetIntegerAction(THREADS_PROPERTY, -1))).intValue();
        if (threads < 0) {
            threads = defaultThreads();
        }
        if (threads == 0) {
            return null;
        }
        synchronized (SHARED) {
            HandshakeCompute c = SHARED.get(threads);
            if (c == null) {
                c = new HandshakeCompute(threads);
                SHARED.put(threads, c);
            }
            return c;
        }
    }

    int threads() {
        return threads;
    }

    /** For tests: the number of tasks that have run. */
    long ran() {
        return ran.get();
    }

    /**
     * Returns the runner for the delegated tasks of one handshake.
     *
     * @param ended says that the handshake is over (timed out, or its socket
     *        closed), so that a task which has not started is dropped and the
     *        handshake fails, not that the pool computes for a connection that
     *        is gone
     */
    SslEngineChannel.TaskRunner runner(BooleanSupplier ended) {
        return task -> run(task, ended);
    }

    /**
     * Runs the task on one of the threads and waits for it, which does not
     * hold the calling virtual thread's carrier.  The wait is not
     * interruptible: a task that has started is short and bounded, and the
     * owner ends a handshake by closing its socket.
     */
    private void run(final Runnable task, final BooleanSupplier ended) {
        FutureTask<Boolean> f = new FutureTask<Boolean>(() -> {
            if (ended.getAsBoolean()) {
                return Boolean.FALSE;
            }
            task.run();
            ran.incrementAndGet();
            return Boolean.TRUE;
        });
        pool.execute(f);
        boolean interrupted = false;
        boolean done;
        try {
            for (;;) {
                try {
                    done = f.get().booleanValue();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof RuntimeException) {
                        throw (RuntimeException) c;
                    }
                    if (c instanceof Error) {
                        throw (Error) c;
                    }
                    throw new RuntimeException(c);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (!done) {
            throw closed();
        }
    }

    private static UncheckedIOException closed() {
        return new UncheckedIOException(new ClosedChannelException());
    }
}
