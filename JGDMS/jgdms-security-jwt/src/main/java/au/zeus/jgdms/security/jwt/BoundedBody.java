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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads an HTTP response body under one total time limit.
 *
 * <p>{@code HttpRequest.timeout} covers only the wait for the response headers,
 * and {@code java.net.http} has no per-read timeout, so a server that sends a byte
 * every few seconds would hold the reading thread for as long as the size cap
 * allows.  A watchdog closes the body stream when the deadline passes; closing
 * cancels the exchange and wakes a blocked read.  The reader then reports a
 * timeout whatever the read returned, because a stream closed early can look like
 * a normal end of data.
 */
final class BoundedBody {

    /** Default total time allowed for reading one response body. */
    static final long DEFAULT_DEADLINE_MS = 10_000L;
    /** Smallest and largest value of the {@code responseBodyTimeoutMs} option. */
    static final long MIN_DEADLINE_MS = 100L;
    static final long MAX_DEADLINE_MS = 120_000L;

    private static final ScheduledThreadPoolExecutor WATCHDOG = newWatchdog();

    private BoundedBody() {}

    private static ScheduledThreadPoolExecutor newWatchdog() {
        ScheduledThreadPoolExecutor e = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "JwtLoginModule-body-watchdog");
            t.setDaemon(true);
            return t;
        });
        e.setRemoveOnCancelPolicy(true);
        return e;
    }

    /**
     * Reads at most {@code limit + 1} bytes (one over the cap, so the caller can
     * tell that it was exceeded) within {@code deadlineMs} in total.  The stream is
     * closed on return.
     *
     * @throws SocketTimeoutException if the deadline passed before the body ended
     * @throws IOException            on any other read failure
     */
    static byte[] read(InputStream in, int limit, long deadlineMs) throws IOException {
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> watch = WATCHDOG.schedule(() -> {
            expired.set(true);
            try {
                in.close();
            } catch (IOException ignore) {
                // The reader sees the timeout flag; there is nothing else to do with it.
            }
        }, deadlineMs, TimeUnit.MILLISECONDS);
        try (InputStream is = in) {
            byte[] bytes;
            try {
                bytes = is.readNBytes(limit + 1);
            } catch (IOException e) {
                if (expired.get()) throw timeout(deadlineMs);
                throw e;
            }
            if (expired.get()) throw timeout(deadlineMs);
            return bytes;
        } finally {
            watch.cancel(false);
        }
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
