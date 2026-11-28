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
package org.apache.river.outrigger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Counts the faults the journal thread isolated for one watcher or one template, and
 * rate-limits their log lines.
 * <p>
 * The journal thread evaluates client-registered watchers and templates against every
 * transition. A fault while evaluating one (watcher or template, transition) pair is that
 * pair's "no match": it is recorded here and changes the outcome for no other pair. The
 * registration is never dropped or cancelled on a count, because the journal cannot tell a
 * fault caused by the entry from one caused by the watcher or by the server, so cancelling
 * would turn a lost event into a lost registration (board design 2026-11-21, Q1).
 * <p>
 * The first fault of a subject is logged at WARNING with its stack. Later faults are
 * logged as one stackless line at most once per log interval, carrying the number of
 * faults since the previous line, so a permanently failing registration cannot flood the
 * log from the journal thread.
 */
final class FaultTally {

    /** Minimum time between two log lines for one subject after its first. */
    static final long DEFAULT_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60L);

    /** Faults isolated by every tally in this VM. */
    private static final AtomicLong TOTAL = new AtomicLong();

    /** Same logger as {@link OperationJournal}, so one handler sees both. */
    private static final Logger logger =
        Logger.getLogger(OutriggerServerImpl.opsLoggerName);

    private final long logIntervalNanos;

    /** Guarded by this. */
    private long count;

    /** Faults counted since the last log line; guarded by this. */
    private long unlogged;

    /** {@link System#nanoTime} of the last log line; guarded by this. */
    private long lastLoggedNanos;

    FaultTally() {
        this(DEFAULT_LOG_INTERVAL_NANOS);
    }

    FaultTally(long logIntervalNanos) {
        this.logIntervalNanos = logIntervalNanos;
    }

    /** @return the number of faults isolated by every tally in this VM. */
    static long total() {
        return TOTAL.get();
    }

    /** @return the number of faults counted by this tally. */
    synchronized long count() {
        return count;
    }

    /**
     * Count one fault and log it unless the rate limit suppresses the line.
     * Never throws: a problem while logging must not end the journal thread.
     *
     * @param phase   what was being evaluated, for the log line.
     * @param subject the watcher or template handle that faulted, for the log line.
     * @param fault   the isolated exception.
     */
    void record(String phase, Object subject, RuntimeException fault) {
        TOTAL.incrementAndGet();
        final long n;
        final long since;
        synchronized (this) {
            n = ++count;
            final long now = System.nanoTime();
            if (n > 1 && now - lastLoggedNanos < logIntervalNanos) {
                unlogged++;
                return;
            }
            since = unlogged;
            unlogged = 0L;
            lastLoggedNanos = now;
        }
        try {
            final StringBuilder sb = new StringBuilder(phase)
                .append(" failed for ").append(subject)
                .append("; treated as no match, registration kept");
            if (n == 1L) {
                logger.log(Level.WARNING, sb.toString(), fault);
            } else {
                sb.append(" (fault ").append(n).append(", ")
                  .append(since).append(" not logged since the previous line): ")
                  .append(fault);
                logger.log(Level.WARNING, sb.toString());
            }
        } catch (Throwable t) {
            // don't let a problem in logging end the journal thread
        }
    }
}
