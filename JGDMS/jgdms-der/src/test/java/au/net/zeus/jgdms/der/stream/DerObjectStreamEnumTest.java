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

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Object-stream transport of top-level enum values over DER (STD-008 sec.15.2 context
 * tag {@code [7]} / sec.17.1). Verifies the {@code [7]} framing, round-trip (including the
 * constant-body {@code getDeclaringClass()} gotcha), and fail-secure rejection of an
 * unknown constant.
 *
 * <p>In package {@code au.net.zeus.jgdms.der.stream} to reach the package-private
 * {@link DerObjectStreamCodec}.
 */
class DerObjectStreamEnumTest {

    enum Color { RED, GREEN, BLUE }

    /** Constants with bodies: {@code Op.ADD.getClass()} is {@code Op$1}, not {@code Op}. */
    enum Op {
        ADD { int apply(int a, int b) { return a + b; } },
        MUL { int apply(int a, int b) { return a * b; } };
        abstract int apply(int a, int b);
    }

    private static byte[] write(Object o) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static Object read(byte[] b) throws IOException, ClassNotFoundException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(b);
        return c.readObject();
    }

    @Test
    void simpleEnum_roundTripsViaTag7() throws Exception {
        for (Color col : Color.values()) {
            byte[] b = write(col);
            assertEquals((byte) 0xA7, b[0],
                    "enum must use the constructed context tag [7] (0xA7)");
            assertEquals(col, read(b), "enum constant must round-trip");
        }
    }

    /** The getDeclaringClass() gotcha: a constant-body enum must still round-trip. */
    @Test
    void constantBodyEnum_roundTrips() throws Exception {
        for (Op op : Op.values()) {
            assertEquals(op, read(write(op)),
                    "constant-body enum must round-trip (declaring class, not Op$N)");
        }
    }

    /** Encoding the same constant twice is byte-identical (value, deterministic). */
    @Test
    void enumEncodingIsDeterministic() throws Exception {
        assertArrayEquals(write(Color.GREEN), write(Color.GREEN),
                "same enum constant must encode to identical bytes");
    }

    @Test
    void unknownConstant_rejectedFailSecure() throws Exception {
        // Hand-craft a [7] item naming a real enum class but a non-existent constant.
        byte[] cls  = DerWriter.writeUtf8String(Color.class.getName());
        byte[] name = DerWriter.writeUtf8String("MAUVE");
        byte[] content = new byte[cls.length + name.length];
        System.arraycopy(cls, 0, content, 0, cls.length);
        System.arraycopy(name, 0, content, cls.length, name.length);
        byte[] item = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 7), content);

        assertThrows(IOException.class, () -> read(item),
                "an unknown enum constant must be rejected fail-secure");
    }

    @Test
    void nullStillRoundTrips() throws Exception {
        assertNull(read(write(null)), "null must still encode as [0] and decode to null");
    }
}
