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
import java.util.Arrays;
import java.util.Objects;

/**
 * Fixture 1 — simple Object-rooted {@code @AtomicSerial} class with a
 * {@code boolean}, an {@code int}, a {@link String}, and a {@code byte[]}.
 *
 * <p>Demonstrates the basic encode / decode round-trip for Phase 4.1.
 * {@code check(GetArg)} enforces that {@code name} is non-null, so feeding
 * a null name tests check-before-construction.
 */
@AtomicSerial
public final class SimpleRecord {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    /** Wire-ordered serial form (Phase 4.2 source of truth). */
    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("active",  boolean.class),
            new AtomicSerial.SerialForm("count",   int.class),
            new AtomicSerial.SerialForm("name",    String.class),
            new AtomicSerial.SerialForm("payload", byte[].class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields (names MUST match serialForm wire names)
    // -------------------------------------------------------------------------

    private final boolean active;
    private final int     count;
    private final String  name;
    private final byte[]  payload;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public SimpleRecord(boolean active, int count, String name, byte[] payload) {
        this.active  = active;
        this.count   = count;
        this.name    = Objects.requireNonNull(name, "name");
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor — check FIRST, then assign
    // -------------------------------------------------------------------------

    /**
     * @AtomicSerial deserialization constructor.
     * Calls {@link #check(AtomicSerial.GetArg)} BEFORE assigning any field,
     * enforcing the check-before-construction invariant.
     */
    public SimpleRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // check runs first — throws before any field assignment on violation
        check(arg);
        this.active  = arg.get("active",  false);
        this.count   = arg.get("count",   0);
        this.name    = (String) arg.get("name",    null);
        byte[] p     = (byte[]) arg.get("payload", null);
        this.payload = p == null ? new byte[0] : p.clone();
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    /**
     * Validates invariants before any field assignment:
     * <ul>
     *   <li>{@code name} must not be null</li>
     * </ul>
     *
     * @param arg the incoming {@code GetArg}
     * @return {@code arg} unchanged (fluent)
     * @throws InvalidObjectException if {@code name} is null
     */
    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String n = (String) arg.get("name", null);
        if (n == null) {
            throw new InvalidObjectException("SimpleRecord: name must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isActive()   { return active; }
    public int     getCount()   { return count; }
    public String  getName()    { return name; }
    public byte[]  getPayload() { return payload.clone(); }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleRecord that)) return false;
        return active == that.active
                && count == that.count
                && Objects.equals(name, that.name)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(active, count, name);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "SimpleRecord{active=" + active + ", count=" + count
                + ", name='" + name + "', payload=" + Arrays.toString(payload) + '}';
    }
}
