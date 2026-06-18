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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip test for an {@code @AtomicSerial} class with an <b>interface-typed</b>
 * {@code serialForm} field (JGDMS-STD-008 sec.16).
 *
 * <p>A field declared as an interface (or abstract class) is a polymorphic slot:
 * {@code SchemaGenerator} maps it to the {@code "@AtomicSerial"} wire type, the encoder
 * writes the field's <em>runtime</em> concrete {@code @AtomicSerial} class (via the
 * embedded schema chain), and the decoder reconstructs that concrete type and checks it
 * against the declared interface. This is what lets a live remote reference (e.g.
 * {@code net.jini.jeri.BasicObjectEndpoint}, whose {@code ep} field is declared as the
 * {@code Endpoint} interface) travel on the DER wire.
 */
class InterfaceFieldRoundTripTest {

    /** Polymorphic interface used as a declared {@code serialForm} field type. */
    public interface Animal {
        String name();
    }

    /** Concrete {@code @AtomicSerial} implementation carried at runtime. */
    @AtomicSerial
    public static final class Dog implements Animal {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("name", String.class)
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, Dog d) throws IOException {
            arg.put("name", d.name);
            arg.writeArgs();
        }
        private final String name;
        public Dog(String name) { this.name = name; }
        public Dog(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.name = (String) arg.get("name", null);
        }
        @Override public String name() { return name; }
        @Override public boolean equals(Object o) {
            return (o instanceof Dog) && Objects.equals(name, ((Dog) o).name);
        }
        @Override public int hashCode() { return Objects.hashCode(name); }
    }

    /** Holder whose serialForm declares the field as the {@link Animal} INTERFACE. */
    @AtomicSerial
    public static final class AnimalHolder {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("animal", Animal.class) // interface-typed slot
            };
        }
        public static void serialize(AtomicSerial.PutArg arg, AnimalHolder h) throws IOException {
            arg.put("animal", h.animal);
            arg.writeArgs();
        }
        private final Animal animal;
        public AnimalHolder(Animal animal) { this.animal = animal; }
        public AnimalHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.animal = arg.get("animal", null, Animal.class); // assignability check vs interface
        }
        public Animal animal() { return animal; }
    }

    private static byte[] encode(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(o);
        }
        return baos.toByteArray();
    }

    @Test
    void interfaceTypedField_roundTrips_viaRuntimeConcreteType() throws Exception {
        AnimalHolder original = new AnimalHolder(new Dog("Rex"));

        byte[] bytes = encode(original);
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            Object decoded = in.readObject();
            assertTrue(decoded instanceof AnimalHolder);
            AnimalHolder h = (AnimalHolder) decoded;
            assertTrue(h.animal() instanceof Dog,
                    "interface-typed field must decode to its runtime concrete @AtomicSerial type");
            assertEquals("Rex", h.animal().name());
        }
    }

    @Test
    void interfaceTypedField_null_roundTrips() throws Exception {
        AnimalHolder original = new AnimalHolder((Animal) null);

        byte[] bytes = encode(original);
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            AnimalHolder h = (AnimalHolder) in.readObject();
            assertNull(h.animal(), "null interface-typed field must round-trip as null");
        }
    }
}
