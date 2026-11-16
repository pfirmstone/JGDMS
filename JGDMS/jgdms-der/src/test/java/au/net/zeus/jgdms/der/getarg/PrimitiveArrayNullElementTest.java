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

package au.net.zeus.jgdms.der.getarg;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.object.fixtures.Status;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DER NULL element in an array whose Java form is a primitive array.
 *
 * <p><b>Invariant defended.</b> JGDMS-STD-006 S9 point 3, fail-secure: malformed input is
 * refused with a {@link DerException}, never with an unchecked exception. An {@code int[]}
 * (or any other primitive array) cannot hold null, so a NULL element is malformed input.
 *
 * <p><b>What these tests fail on.</b> The object-constructing array decoder used to accept the
 * NULL element as a null value and then fail unboxing it while building the array, throwing a
 * {@link NullPointerException} out of the decoder. Whole-stream admission refused the same
 * input, so the defect showed only where a field store was built without it. Against that code
 * every {@code refusesNull} case below fails with "expected DerException but got
 * NullPointerException".
 */
class PrimitiveArrayNullElementTest {

    /** DER NULL. */
    private static final byte[] NULL = {0x05, 0x00};

    /** Every component wire type that is built into a primitive array. */
    private static final List<String> PRIMITIVE_COMPONENTS = List.of(
            "boolean", "java.lang.Boolean", "byte", "java.lang.Byte", "short", "java.lang.Short",
            "int", "java.lang.Integer", "long", "java.lang.Long");

    private static AtomicSerialSchemaRecord schema(String wireType) {
        return new AtomicSerialSchemaRecord("com.example.Holder",
                List.of(new AtomicSerialFieldDef("a", wireType)));
    }

    private static byte[] valueOf(String componentWT) {
        return componentWT.endsWith("oolean") ? DerWriter.writeBoolean(true) : DerWriter.writeInteger(1);
    }

    /** One class's private SEQUENCE holding a single array field. */
    private static byte[] classSequence(byte[]... elements) {
        return DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(elements))));
    }

    @Test
    void constructingStore_refusesNull() {
        for (String c : PRIMITIVE_COMPONENTS) {
            // NULL alone, NULL after a value, NULL before a value.
            for (byte[] seq : List.of(classSequence(NULL), classSequence(valueOf(c), NULL),
                    classSequence(NULL, valueOf(c)))) {
                DerException e = assertThrows(DerException.class,
                        () -> new DerFieldStore(schema("array:" + c), seq), c);
                assertTrue(e.getMessage().contains("NULL element"), e.getMessage());
            }
        }
    }

    @Test
    void classFreeStore_refusesNull() {
        for (String c : PRIMITIVE_COMPONENTS) {
            assertThrows(DerException.class, () -> DerFieldStore.classFree(schema("array:" + c),
                    new au.net.zeus.jgdms.der.DerReader(classSequence(valueOf(c), NULL))), c);
        }
    }

    @Test
    void decodePrimitiveArray_refusesNull() {
        for (String c : PRIMITIVE_COMPONENTS) {
            assertThrows(DerException.class, () -> DerFieldStore.decodePrimitiveArray(
                    DerWriter.writeSequence(List.of(valueOf(c), NULL)), "array:" + c,
                    ResolutionContext.NONE), c);
        }
    }

    /** A NULL element stays legal where the element type can be null. */
    @Test
    void nullableComponents_stillAcceptNull() throws Exception {
        DerFieldStore strings = new DerFieldStore(schema("array:java.lang.String"),
                classSequence(DerWriter.writeUtf8String("a"), NULL));
        assertArrayEquals(new String[] {"a", null}, (String[]) strings.get("a", (Object) null));

        DerFieldStore enums = new DerFieldStore(schema("array:enum:" + Status.class.getName()),
                classSequence(NULL, DerWriter.writeUtf8String("ACTIVE")));
        assertArrayEquals(new Status[] {null, Status.ACTIVE}, (Status[]) enums.get("a", (Object) null));

        DerFieldStore classes = new DerFieldStore(schema("array:java.lang.Class"),
                classSequence(DerWriter.writeUtf8String("java.lang.String"), NULL));
        assertArrayEquals(new Class<?>[] {String.class, null}, (Class<?>[]) classes.get("a", (Object) null));
    }

    /** A well-formed primitive array is unaffected. */
    @Test
    void wellFormedPrimitiveArray_decodes() throws Exception {
        DerFieldStore ints = new DerFieldStore(schema("array:int"),
                classSequence(DerWriter.writeInteger(1), DerWriter.writeInteger(-2)));
        assertArrayEquals(new int[] {1, -2}, (int[]) ints.get("a", (Object) null));
    }
}
