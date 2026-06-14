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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.object.fixtures.CounterRecord;
import au.net.zeus.jgdms.der.object.fixtures.MultiTypeRecord;
import au.net.zeus.jgdms.der.object.fixtures.SimpleRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.InvalidObjectException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for Phase 4.1 and 4.2:
 * <ul>
 *   <li>4.1.1 -- {@code SimpleRecord} encode -> decode round-trip.</li>
 *   <li>4.1.2 -- {@code MultiTypeRecord} encode -> decode round-trip (all wire types).</li>
 *   <li>4.1.3 -- {@code CounterRecord} encode -> decode round-trip (happy path).</li>
 *   <li>4.1.4 -- Encoded form decodes correctly via {@link DerFieldStore} + schema.</li>
 *   <li>4.1.5 -- check-before-construction: {@code CounterRecord} with negative value
 *               throws {@link InvalidObjectException} from {@code check(GetArg)}.</li>
 *   <li>4.2.1 -- Schema field names and order match {@code serialForm()}.</li>
 *   <li>4.2.2 -- Re-generating schema produces byte-identical record + identical digest.</li>
 *   <li>4.2.3 -- Type mapping: all supported primitives + String + byte[].</li>
 *   <li>4.2.4 -- Unsupported field type ({@code double}) raises {@link DerException}.</li>
 *   <li>4.2.5 -- Unsupported field type ({@code java.util.Date}) raises {@link DerException}.</li>
 * </ul>
 */
class ObjectCodecTest {

    // =========================================================================
    // Phase 4.1 -- encode / decode round-trip
    // =========================================================================

    /**
     * 4.1.1 -- {@code SimpleRecord} with typical values round-trips with field equality.
     */
    @Test
    void test_4_1_1_SimpleRecord_RoundTrip() throws Exception {
        SimpleRecord original = new SimpleRecord(
                true, 42, "hello", new byte[]{1, 2, 3});

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] der = ObjectCodec.encode(original, SimpleRecord.class, schema);
        SimpleRecord decoded = ObjectCodec.decode(SimpleRecord.class, schema, der);

        assertEquals(original, decoded,
                "Decoded SimpleRecord must equal original");
    }

    /**
     * 4.1.1b -- {@code SimpleRecord} with null payload and false flag.
     */
    @Test
    void test_4_1_1b_SimpleRecord_NullPayload_RoundTrip() throws Exception {
        SimpleRecord original = new SimpleRecord(false, -1, "empty", null);

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] der = ObjectCodec.encode(original, SimpleRecord.class, schema);
        SimpleRecord decoded = ObjectCodec.decode(SimpleRecord.class, schema, der);

        assertEquals(original, decoded);
    }

    /**
     * 4.1.2 -- {@code MultiTypeRecord} exercises all wire types in one encode/decode cycle.
     */
    @Test
    void test_4_1_2_MultiTypeRecord_RoundTrip_AllWireTypes() throws Exception {
        MultiTypeRecord original = new MultiTypeRecord(
                true,
                (byte) 127,
                (short) 32767,
                Integer.MAX_VALUE,
                Long.MIN_VALUE,
                "unicode é test",
                new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(MultiTypeRecord.class);
        byte[] der = ObjectCodec.encode(original, MultiTypeRecord.class, schema);
        MultiTypeRecord decoded = ObjectCodec.decode(MultiTypeRecord.class, schema, der);

        assertEquals(original, decoded,
                "Decoded MultiTypeRecord must equal original");
    }

    /**
     * 4.1.3 -- {@code CounterRecord} (has value >= 0 invariant) happy-path round-trip.
     */
    @Test
    void test_4_1_3_CounterRecord_RoundTrip() throws Exception {
        CounterRecord original = new CounterRecord(999L, "requests");

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(CounterRecord.class);
        byte[] der = ObjectCodec.encode(original, CounterRecord.class, schema);
        CounterRecord decoded = ObjectCodec.decode(CounterRecord.class, schema, der);

        assertEquals(original, decoded);
    }

    /**
     * 4.1.4 -- Encoded SEQUENCE can be decoded by {@link DerFieldStore} + schema,
     * confirming that field order and types match the schema.
     */
    @Test
    void test_4_1_4_EncodedFormMatchesSchema() throws Exception {
        SimpleRecord original = new SimpleRecord(true, 7, "verify", new byte[]{9, 8});

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);
        byte[] der = ObjectCodec.encode(original, SimpleRecord.class, schema);

        // Decode the raw SEQUENCE with DerFieldStore -- confirms field structure
        DerFieldStore store = new DerFieldStore(schema, der);

        // Check all fields are present and have correct values
        assertFalse(store.defaulted("active"),  "active should be present");
        assertFalse(store.defaulted("count"),   "count should be present");
        assertFalse(store.defaulted("name"),    "name should be present");
        assertFalse(store.defaulted("payload"), "payload should be present");

        assertTrue((Boolean) store.get("active", null),       "active must be true");
        assertEquals(7, (Integer) store.get("count", null),   "count must be 7");
        assertEquals("verify", store.get("name", null),       "name must be 'verify'");
        assertArrayEquals(new byte[]{9, 8},
                (byte[]) store.get("payload", null),          "payload mismatch");

        // Also verify field count matches schema
        assertEquals(4, schema.fields().size(), "schema should have 4 fields");
        assertEquals(4, store.presentFieldNames().size(), "all 4 fields should be present");
    }

    /**
     * 4.1.5 -- check-before-construction: a negative {@code value} in {@code CounterRecord}
     * must cause {@link InvalidObjectException} from {@code check(GetArg)}, and
     * NO partially-constructed object is returned.
     *
     * <p>We build the DER payload by hand to inject the negative value.
     */
    @Test
    void test_4_1_5_CheckBeforeConstruction_NegativeValue_ThrowsBeforeObject()
            throws Exception {
        // Build DER SEQUENCE for CounterRecord with value = -1 (violates value >= 0)
        // Schema: value:long, label:String
        byte[] valueTlv = DerWriter.writeInteger(BigInteger.valueOf(-1L));
        byte[] labelTlv = DerWriter.writeUtf8String("bad-counter");
        byte[] sequenceDer = DerWriter.writeSequence(List.of(valueTlv, labelTlv));

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(CounterRecord.class);

        // The decode must throw InvalidObjectException from check(GetArg), not
        // from the CounterRecord constructor body -- no CounterRecord is created.
        InvalidObjectException ex = assertThrows(
                InvalidObjectException.class,
                () -> ObjectCodec.decode(CounterRecord.class, schema, sequenceDer),
                "Decoding a negative value must throw InvalidObjectException");

        assertTrue(ex.getMessage().contains("value must be >= 0")
                        || ex.getMessage().contains("CounterRecord"),
                "Exception message should mention the invariant: " + ex.getMessage());
    }

    /**
     * 4.1.5b -- check-before-construction with null name in {@code SimpleRecord}.
     */
    @Test
    void test_4_1_5b_CheckBeforeConstruction_NullName_ThrowsBeforeObject()
            throws Exception {
        // Build DER SEQUENCE for SimpleRecord with name=null (absent from payload)
        // To produce a null name, we encode the payload without the name field,
        // but SimpleRecord.check reads name via arg.get("name", null) -- absent means null.
        // Easier: write an OCTET STRING null is not representable, so write a short payload.
        // Simplest: encode only active, count, and payload -- omit name (payload shorter than schema)
        byte[] activeTlv  = DerWriter.writeBoolean(false);
        byte[] countTlv   = DerWriter.writeInteger(0);
        // Omit name (absent -> null when GetArg returns default null)
        // That means sequence has only 2 TLVs; DerFieldStore will set name=ABSENT -> returns null
        byte[] sequenceDer = DerWriter.writeSequence(List.of(activeTlv, countTlv));

        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);

        InvalidObjectException ex = assertThrows(
                InvalidObjectException.class,
                () -> ObjectCodec.decode(SimpleRecord.class, schema, sequenceDer),
                "Decoding with null name must throw InvalidObjectException");

        assertTrue(ex.getMessage().contains("name"),
                "Exception message should mention 'name': " + ex.getMessage());
    }

    // =========================================================================
    // Phase 4.2 -- schema generation
    // =========================================================================

    /**
     * 4.2.1 -- Generated schema field names and order match {@code serialForm()} exactly.
     */
    @Test
    void test_4_2_1_SchemaFieldNamesAndOrderMatchSerialForm() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(SimpleRecord.class);

        List<AtomicSerialFieldDef> fields = schema.fields();
        assertEquals(4, fields.size(), "SimpleRecord schema should have 4 fields");

        assertEquals("active",  fields.get(0).wireName());
        assertEquals("count",   fields.get(1).wireName());
        assertEquals("name",    fields.get(2).wireName());
        assertEquals("payload", fields.get(3).wireName());

        // Verify types
        assertEquals("boolean",          fields.get(0).wireType());
        assertEquals("int",              fields.get(1).wireType());
        assertEquals("java.lang.String", fields.get(2).wireType());
        assertEquals("byte[]",           fields.get(3).wireType());
    }

    /**
     * 4.2.1b -- Same check for {@code MultiTypeRecord} (all 7 wire types).
     */
    @Test
    void test_4_2_1b_MultiTypeRecord_SchemaFieldOrder() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(MultiTypeRecord.class);
        List<AtomicSerialFieldDef> fields = schema.fields();

        assertEquals(7, fields.size(), "MultiTypeRecord schema should have 7 fields");

        String[] expectedNames = {"flag","tiny","small","medium","large","text","data"};
        String[] expectedTypes = {"boolean","byte","short","int","long","java.lang.String","byte[]"};
        for (int i = 0; i < expectedNames.length; i++) {
            assertEquals(expectedNames[i], fields.get(i).wireName(), "name at " + i);
            assertEquals(expectedTypes[i], fields.get(i).wireType(), "type at " + i);
        }
    }

    /**
     * 4.2.2 -- Re-generating from the same class produces a byte-identical schema record
     * and an identical {@code schemaDigest()}.
     */
    @Test
    void test_4_2_2_RegeneratedSchemaIsByteIdentical() throws Exception {
        AtomicSerialSchemaRecord schema1 = SchemaGenerator.generate(SimpleRecord.class);
        AtomicSerialSchemaRecord schema2 = SchemaGenerator.generate(SimpleRecord.class);

        byte[] encoded1 = schema1.encode();
        byte[] encoded2 = schema2.encode();

        assertArrayEquals(encoded1, encoded2,
                "Re-generated schema DER must be byte-identical");

        assertArrayEquals(schema1.schemaDigest(), schema2.schemaDigest(),
                "Re-generated schema digest must be identical");
    }

    /**
     * 4.2.3 -- Type mapping covers all supported wire types.
     * (Verified transitively by 4.2.1b, but this test directly tests
     * {@link SchemaGenerator#toWireType} for each entry in the table.)
     */
    @Test
    void test_4_2_3_TypeMappingCoversAllSupportedTypes() throws Exception {
        // All supported primitive types
        assertEquals("boolean",          SchemaGenerator.toWireType(boolean.class,    Object.class));
        assertEquals("boolean",          SchemaGenerator.toWireType(Boolean.class,    Object.class));
        assertEquals("byte",             SchemaGenerator.toWireType(byte.class,       Object.class));
        assertEquals("byte",             SchemaGenerator.toWireType(Byte.class,       Object.class));
        assertEquals("short",            SchemaGenerator.toWireType(short.class,      Object.class));
        assertEquals("short",            SchemaGenerator.toWireType(Short.class,      Object.class));
        assertEquals("int",              SchemaGenerator.toWireType(int.class,        Object.class));
        assertEquals("int",              SchemaGenerator.toWireType(Integer.class,    Object.class));
        assertEquals("long",             SchemaGenerator.toWireType(long.class,       Object.class));
        assertEquals("long",             SchemaGenerator.toWireType(Long.class,       Object.class));
        assertEquals("java.lang.String", SchemaGenerator.toWireType(String.class,     Object.class));
        assertEquals("byte[]",           SchemaGenerator.toWireType(byte[].class,     Object.class));
    }

    /**
     * 4.2.4 -- An unsupported field type ({@code double}) raises {@link DerException}
     * (explicitly deferred per S7.6), not a silent fallback.
     */
    @Test
    void test_4_2_4_UnsupportedType_Double_RaisesClearError() {
        DerException ex = assertThrows(
                DerException.class,
                () -> SchemaGenerator.toWireType(double.class, Object.class),
                "double should throw DerException (deferred per S7.6)");
        assertTrue(ex.getMessage().contains("double") || ex.getMessage().contains("deferred"),
                "Error message should mention 'double' or 'deferred': " + ex.getMessage());
    }

    /**
     * 4.2.5 -- An unsupported Object type ({@code java.util.Date}) raises {@link DerException}.
     */
    @Test
    void test_4_2_5_UnsupportedType_Date_RaisesClearError() {
        DerException ex = assertThrows(
                DerException.class,
                () -> SchemaGenerator.toWireType(java.util.Date.class, Object.class),
                "java.util.Date should throw DerException");
        assertTrue(ex.getMessage().contains("java.util.Date") || ex.getMessage().contains("unsupported"),
                "Error message should mention 'java.util.Date': " + ex.getMessage());
    }

    /**
     * 4.2.5b -- A class annotated with a {@code double} field raises {@link DerException}
     * from {@link SchemaGenerator#generate(Class)}.
     */
    @Test
    void test_4_2_5b_GenerateWithDoubleField_ThrowsDerException() {
        assertThrows(DerException.class,
                () -> SchemaGenerator.generate(BadDoubleFixture.class),
                "Generating schema for a class with a double field must throw DerException");
    }

    // =========================================================================
    // Inner fixture for 4.2.5b (not @AtomicSerial, only needs serialForm)
    // =========================================================================

    /** Synthetic fixture: has a {@code double} field (unsupported per S7.6). */
    @AtomicSerial
    static final class BadDoubleFixture {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("x", double.class),
            };
        }
        // No constructor needed -- the test only calls SchemaGenerator.generate()
        public BadDoubleFixture(AtomicSerial.GetArg arg) throws java.io.IOException {
            throw new java.io.IOException("not implemented");
        }
    }
}
