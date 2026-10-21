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
 * accepted; the task closes the raw socket, which ends whatever read or write
 * the handshake is blocked in.  The thread is the shared {@link
 * TimeoutScheduler} one, which serves all the listeners of the JVM, so a flood
 * of connections costs queue entries and not threads.
 */
final class HandshakeWatchdog {

    private HandshakeWatchdog() { }

    /**
     * Runs the task after the delay, unless the returned future is cancelled
     * first.  The task must not block.
     */
    static ScheduledFuture<?> schedule(Runnable task, long delayNanos) {
        return TimeoutScheduler.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
    }

    /** Returns the number of tasks that are waiting, for tests. */
    static int pending() {
        return TimeoutScheduler.pending();
    }
}
