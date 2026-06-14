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
 * Phase 4.4 fixture -- the {@code @AtomicSerial} subclass of the plain
 * (non-{@code @AtomicSerial}) superclass {@link PlainSuper}.
 *
 * <p>Per S3.10 (second rule): {@code PlainSuper} is invisible to the wire.
 * {@code Sub} carries {@code PlainSuper}'s {@code legacyName} field in its
 * OWN serial namespace. {@code Sub}'s {@code (GetArg)} constructor reads
 * {@code legacyName} from its own store and passes it as an ordinary argument
 * to {@code super(legacyName)}.
 *
 * <p>There is NO separate {@code PlainSuper} SEQUENCE on the wire. The chain
 * produced by {@code SchemaGenerator.generateChain(Sub.class)} has exactly
 * ONE record ({@code Sub}).
 *
 * <p>Serial fields (all in {@code Sub}'s own namespace):
 * <ul>
 *   <li>{@code legacyName} (String) -- carried on behalf of {@link PlainSuper}.</li>
 *   <li>{@code subValue}   (int)    -- {@code Sub}'s own field.</li>
 * </ul>
 */
@AtomicSerial
public final class Sub extends PlainSuper {

    // -------------------------------------------------------------------------
    // Serial form -- Sub's OWN namespace (includes legacyName for PlainSuper)
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("legacyName", String.class),
            new AtomicSerial.SerialForm("subValue",   int.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Sub's own field. */
    private final int subValue;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Sub(String legacyName, int subValue) {
        super(legacyName);
        this.subValue = subValue;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor.
     *
     * <p>Reads {@code legacyName} from Sub's own field store (Sub is on the stack
     * here, so StackWalker routes the call to Sub's DerFieldStore), then passes
     * it as an ordinary constructor argument to {@code super(legacyName)}.
     * {@code PlainSuper} has no {@code (GetArg)} constructor and receives no
     * {@code GetArg}. {@code PlainSuper}'s state is reconstituted solely from
     * what {@code Sub} chose to preserve in its own namespace.
     */
    public Sub(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // check runs first, from Sub's frame -> Sub's DerFieldStore
        super(check(arg));
        // Sub assigns its own field after super() returns
        this.subValue = arg.get("subValue", 0);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    /**
     * Validates Sub's fields and returns the {@code legacyName} string that
     * will be passed to {@code super(...)} via the checked constructor pattern.
     *
     * <p>Because this method is called as {@code super(check(arg))}, its return
     * value must be the argument passed to {@code PlainSuper(String)}. We return
     * the legacyName string read from Sub's own store.
     */
    public static String check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String name = (String) arg.get("legacyName", null);
        if (name == null) {
            throw new InvalidObjectException("Sub: legacyName must not be null");
        }
        return name;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getSubValue() { return subValue; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sub that)) return false;
        return subValue == that.subValue
                && Objects.equals(legacyName, that.legacyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legacyName, subValue);
    }

    @Override
    public String toString() {
        return "Sub{legacyName='" + legacyName + "', subValue=" + subValue + "}";
    }
}
