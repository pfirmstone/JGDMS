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

package au.net.zeus.jgdms.der.object.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Objects;

/**
 * Fixture 2 -- {@code @AtomicSerial} class with a {@code long} and a
 * {@link String} label, and a HARD INVARIANT: {@code value} must be &ge; 0.
 *
 * <p>This is the primary fixture for testing check-before-construction:
 * feeding a negative {@code value} must cause the decode to throw
 * {@link InvalidObjectException} from {@code check(GetArg)} BEFORE any
 * {@code CounterRecord} instance is created.
 */
@AtomicSerial
public final class CounterRecord {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("value", long.class),
            new AtomicSerial.SerialForm("label", String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): emit each serialForm() field by name. */
    public static void serialize(AtomicSerial.PutArg arg, CounterRecord o) throws IOException {
        arg.put("value", o.value);
        arg.put("label", o.label);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final long   value;
    private final String label;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public CounterRecord(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0: " + value);
        }
        this.value = value;
        this.label = Objects.requireNonNull(label, "label");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public CounterRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // check FIRST -- enforces value >= 0 before any field is assigned
        check(arg);
        this.value = arg.get("value", 0L);
        this.label = (String) arg.get("label", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction -- HARD INVARIANT: value >= 0
    // -------------------------------------------------------------------------

    /**
     * Enforces {@code value >= 0}.
     *
     * <p>If {@code value} &lt; 0, throws {@link InvalidObjectException} before
     * any field of {@code CounterRecord} is assigned. This is the check whose
     * failure must be observable in the check-before-construction test.
     *
     * @param arg the incoming {@code GetArg}
     * @return {@code arg} unchanged (fluent)
     * @throws InvalidObjectException if {@code value} is negative
     */
    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException {
        long v = arg.get("value", 0L);
        if (v < 0) {
            throw new InvalidObjectException(
                    "CounterRecord: value must be >= 0, got " + v);
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long   getValue() { return value; }
    public String getLabel() { return label; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CounterRecord that)) return false;
        return value == that.value && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, label);
    }

    @Override
    public String toString() {
        return "CounterRecord{value=" + value + ", label='" + label + "'}";
    }
}
