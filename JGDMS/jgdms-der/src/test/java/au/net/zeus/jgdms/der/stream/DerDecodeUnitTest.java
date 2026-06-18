/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the decode-unit completion mechanism on {@link DerMarshalInputStream}
 * (JGDMS-STD-008 sec.6; SRC&nbsp;RR-116 sec.2.1 transmit-race invariant).
 *
 * <p>The DER codec, unlike {@link java.io.ObjectInputStream}, has no automatic "run
 * validations when the outermost {@code readObject} returns" trigger, so the
 * {@link org.apache.river.api.io.AtomicObjectInput#registerValidation} callbacks are
 * held and fired explicitly by {@link DerMarshalInputStream#endDecodeUnit()}. These
 * tests pin: fire-once + decreasing-priority order, idempotency across
 * {@code endDecodeUnit}/{@code close}, the dirty-before-ack ordering (callbacks run
 * before the underlying stream closes), null rejection, the empty back-compat case,
 * and registration after completion.
 */
class DerDecodeUnitTest {

    /** A DER input stream over an empty buffer (no objects decoded; mechanism only). */
    private static DerMarshalInputStream emptyStream() throws IOException {
        return new DerMarshalInputStream(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    void endDecodeUnit_firesEachOnce_inDecreasingPriorityOrder() throws Exception {
        List<String> log = new ArrayList<>();
        DerMarshalInputStream in = emptyStream();
        in.registerValidation(() -> log.add("p0"), 0);
        in.registerValidation(() -> log.add("p10"), 10);
        in.registerValidation(() -> log.add("p5"), 5);

        in.endDecodeUnit();

        assertEquals(List.of("p10", "p5", "p0"), log,
                "callbacks must run highest-priority-first, exactly once each");
    }

    @Test
    void endDecodeUnit_isIdempotent_acrossRepeatCallsAndClose() throws Exception {
        AtomicInteger count = new AtomicInteger();
        DerMarshalInputStream in = emptyStream();
        in.registerValidation(count::incrementAndGet, 0);

        in.endDecodeUnit();
        in.endDecodeUnit();   // second explicit flush: no-op
        in.close();           // defensive close-time flush: no-op

        assertEquals(1, count.get(), "each callback must fire exactly once in total");
    }

    @Test
    void close_flushesCallbacksBeforeUnderlyingClose() throws Exception {
        // The ordering proof for "dirty before ack": the registered callback must run
        // before the underlying stream is closed (close drives the mux Acknowledgment).
        List<String> log = new ArrayList<>();
        RecordingInputStream underlying = new RecordingInputStream(log);
        DerMarshalInputStream in = new DerMarshalInputStream(underlying);
        in.registerValidation(() -> log.add("validate"), 0);

        in.close(); // no explicit endDecodeUnit: close must still flush, then close underlying

        assertEquals(List.of("validate", "close"), log,
                "completion callbacks must run before the underlying close");
    }

    @Test
    void registerValidation_null_throwsInvalidObjectException() throws Exception {
        DerMarshalInputStream in = emptyStream();
        assertThrows(InvalidObjectException.class, () -> in.registerValidation(null, 0));
    }

    @Test
    void noRegistrations_endDecodeUnitAndClose_areNoOps() throws Exception {
        DerMarshalInputStream in = emptyStream();
        assertDoesNotThrow(in::endDecodeUnit);
        assertDoesNotThrow(in::close);
    }

    @Test
    void registerValidation_afterCompletion_throwsNotActiveException() throws Exception {
        DerMarshalInputStream in = emptyStream();
        in.endDecodeUnit(); // unit complete

        assertThrows(NotActiveException.class,
                () -> in.registerValidation(() -> { }, 0));
    }

    /** Empty input stream whose {@code close()} appends a marker to a shared log. */
    private static final class RecordingInputStream extends InputStream {
        private final List<String> log;
        RecordingInputStream(List<String> log) { this.log = log; }

        @Override public int read() { return -1; }

        @Override public void close() { log.add("close"); }
    }
}
