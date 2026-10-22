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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.river.jeri.internal.TimeoutScheduler;

/**
 * The timer that ends server handshakes which have run out of time.
 *
 * <p>Every accepted connection has a task here from the moment it is
 * accepted; the task only marks the handshake as timed out, and another thread
 * closes the raw socket, which ends whatever read or write the handshake is
 * blocked in.  The thread is the shared {@link
 * TimeoutScheduler} one, which serves all the listeners of the JVM, so a flood
 * of connections costs queue entries and not threads.
 */
final class HandshakeWatchdog {

    private HandshakeWatchdog() { }

    /**
     * Runs the task after the delay, unless the returned future is cancelled
     * first.  The task must not block: it sets some state and calls {@link
     * #handOff} for anything that can.
     */
    static ScheduledFuture<?> schedule(Runnable task, long delayNanos) {
        return TimeoutScheduler.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Runs the task on a thread of its own, from a task of the watchdog, which
     * must not close sockets itself: a close can block.
     */
    static void handOff(Runnable task) {
        TimeoutScheduler.handOff(task);
    }

    /**
     * Runs the task as {@link #handOff(Runnable)} does; if that cannot be
     * done at all, runs the last resort on the calling thread, which must not
     * be held up by it: it is quick and does not block.
     */
    static void handOff(Runnable task, Runnable lastResort) {
        TimeoutScheduler.handOff(task, lastResort);
    }

    /** For tests: makes the hand-off of tasks fail, or no longer, with 0. */
    static void inject(int failures) {
        TimeoutScheduler.inject(failures);
    }

    /** Returns the number of tasks that were lost, for tests. */
    static long lost() {
        return TimeoutScheduler.lost();
    }

    /** Returns the number of tasks that are waiting, for tests. */
    static int pending() {
        return TimeoutScheduler.pending();
    }
}
