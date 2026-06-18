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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip test for a {@code @AtomicSerial.Stateless} subclass (JGDMS-STD-008 sec.16).
 *
 * <p>{@code @AtomicSerial.Stateless} marks an {@code @AtomicSerial} class that "has no
 * arguments, Objects or data to write to the stream" and therefore implements neither
 * {@code serialForm()} nor {@code serialize(PutArg)}. The DER codec must treat such a class
 * as contributing an empty private SEQUENCE rather than demanding the write contract from
 * it. This pattern is used by trivial preferred-class subclasses (e.g.
 * {@code net.jini.id.UuidFactory$Impl}) and no-extra-state Throwables (e.g.
 * {@code net.jini.core.lease.LeaseException}).
 */
class StatelessSubclassRoundTripTest {

    /** Stateful @AtomicSerial base. */
    @AtomicSerial
    public static class Base {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("v", int.class)
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Base b) throws IOException {
            arg.put("v", b.v);
            arg.writeArgs();
        }
        final int v;
        public Base(int v) { this.v = v; }
        public Base(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.v = arg.get("v", 0);
        }
        public int v() { return v; }
    }

    /** Stateless subclass that adds no serial state (mirrors UuidFactory$Impl). */
    @AtomicSerial
    @AtomicSerial.Stateless
    public static final class Derived extends Base {
        public Derived(int v) { super(v); }
        public Derived(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            super(arg);
        }
    }

    @Test
    void statelessSubclass_roundTrips_preservingTypeAndInheritedState() throws Exception {
        Derived original = new Derived(42);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(original);
        }
        try (DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object decoded = in.readObject();
            assertTrue(decoded instanceof Derived,
                    "a @Stateless subclass must decode to its own concrete type");
            assertEquals(42, ((Derived) decoded).v(),
                    "inherited serial state must round-trip through the @Stateless leaf");
        }
    }
}
