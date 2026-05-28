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
package net.jini.core.transaction.server;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A simple Lamport logical clock used by {@link AbstractTimestampParticipant}
 * to generate monotonically-increasing timestamps for the Granola
 * single-round commit optimisation (Opt-3).
 *
 * <p>The sentinel value {@link #NO_TIMESTAMP} ({@code 0L}) means "no
 * timestamp available"; any value {@code > 0} is a valid timestamp.
 *
 * <p>All mutating methods are thread-safe.
 *
 * @see AbstractTimestampParticipant
 * @see TransactionParticipant.TimestampedVote
 */
@AtomicSerial
public final class LamportClock implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Sentinel value indicating that no Lamport timestamp is available and
     * the Granola single-round optimisation is not applicable.
     */
    public static final long NO_TIMESTAMP = 0L;

    /**
     * The persisted clock value; captured at serialisation time.
     *
     * @serial
     */
    private final long value;

    /** Live counter (transient – reconstructed from {@link #value}). */
    private final transient AtomicLong clock;

    // -----------------------------------------------------------------------
    // @AtomicSerial support
    // -----------------------------------------------------------------------

    /** @return serialisation field descriptors */
    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("value", Long.TYPE)
        };
    }

    /**
     * Serialises the current (live) clock value so that a restarted process
     * resumes from the last observed tick rather than from the initial value.
     */
    public static void serialize(PutArg arg, LamportClock lc) throws IOException {
        arg.put("value", lc.clock.get());
        arg.writeArgs();
    }

    /**
     * @AtomicSerial deserialisation constructor.
     */
    public LamportClock(GetArg arg) throws IOException, ClassNotFoundException {
        this(arg.get("value", 1L));
    }

    // -----------------------------------------------------------------------
    // Public constructors
    // -----------------------------------------------------------------------

    /** Creates a new clock starting at {@code 1}. */
    public LamportClock() {
        this(1L);
    }

    /**
     * Creates a new clock starting at {@code initialValue}.
     *
     * @param initialValue the first value returned by {@link #tick()}; must
     *        be {@code > 0} (i.e. not {@link #NO_TIMESTAMP})
     */
    public LamportClock(long initialValue) {
        if (initialValue <= NO_TIMESTAMP) {
            throw new IllegalArgumentException(
                    "initialValue must be > NO_TIMESTAMP (0), got: " + initialValue);
        }
        this.value = initialValue;
        this.clock = new AtomicLong(initialValue);
    }

    // -----------------------------------------------------------------------
    // Clock operations
    // -----------------------------------------------------------------------

    /**
     * Observes a remote timestamp and advances this clock to
     * {@code max(local, remoteTimestamp) + 1}, as required by the
     * Lamport-clock update rule.
     *
     * @param remoteTimestamp the timestamp received from a remote participant
     * @return the updated local timestamp (always {@code > 0})
     */
    public long observe(long remoteTimestamp) {
        return clock.updateAndGet(local -> Math.max(local, remoteTimestamp) + 1);
    }

    /**
     * Increments the clock by one and returns the new value.
     *
     * @return the new timestamp (always {@code > 0})
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Returns the current clock value without advancing it.
     *
     * @return current timestamp (always {@code > 0} after construction)
     */
    public long get() {
        return clock.get();
    }
}
