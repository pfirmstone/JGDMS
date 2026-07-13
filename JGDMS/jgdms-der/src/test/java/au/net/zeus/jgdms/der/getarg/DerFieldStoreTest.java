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
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for Phase 3: {@link DerFieldStore} and {@link WireTypes}.
 *
 * <p>All tests use hand-built schemas ({@link AtomicSerialSchemaRecord}) and
 * hand-built DER payloads (encoded via {@link DerWriter}) -- no real {@code @AtomicSerial}
 * class is involved. This verifies the field store in isolation.
 *
 * <h2>Test grouping</h2>
 * <ul>
 *   <li><b>3.1.x</b> -- Complete field store semantics</li>
 *   <li><b>3.2.x</b> -- Three decoding cases (a)(b)(c) + schema-is-the-passed-in-schema</li>
 *   <li><b>3.3.x</b> -- Wire type decode (each supported wireType, overflow, unsupported)</li>
 * </ul>
 */
class DerFieldStoreTest {

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Build an AtomicSerialSchemaRecord from parallel name/type arrays. */
    private static AtomicSerialSchemaRecord schema(String className,
                                                    String[] names, String[] types) {
        assert names.length == types.length;
        List<AtomicSerialFieldDef> defs = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            defs.add(new AtomicSerialFieldDef(names[i], types[i]));
        }
        return new AtomicSerialSchemaRecord(className, defs);
    }

    /**
     * Wrap a list of pre-encoded child TLVs in a SEQUENCE and return the full
     * DER bytes (tag + length + content) -- this is the "payload SEQUENCE" that
     * DerFieldStore accepts.
     */
    private static byte[] payloadSeq(List<byte[]> children) {
        return DerWriter.writeSequence(children);
    }

    /**
     * Convenience: build a minimal payload whose only contents are the supplied
     * children in order (no wrapper SEQUENCE around the children themselves).
     */
    private static byte[] seqOf(byte[]... children) {
        return payloadSeq(List.of(children));
    }

    // =========================================================================
    // 3.1 -- Complete field store semantics
    // =========================================================================

    /**
     * 3.1a -- All fields decoded and present in the store BEFORE any get() call.
     * Assert via presentFieldNames() immediately after construction.
     */
    @Test
    void task3_1a_allFieldsPresentBeforeGet() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Foo",
                new String[]{"alpha", "beta", "gamma"},
                new String[]{"int", "java.lang.String", "boolean"});

        byte[] payload = seqOf(
                DerWriter.writeInteger(42L),
                DerWriter.writeUtf8String("hello"),
                DerWriter.writeBoolean(true)
        );

        DerFieldStore store = new DerFieldStore(sch, payload);

        // Assert ALL fields present BEFORE any get() is called
        Set<String> present = store.presentFieldNames();
        assertEquals(Set.of("alpha", "beta", "gamma"), present,
                "All schema fields must be present immediately after construction");
        assertEquals(3, present.size());

        // Also check via presentFields map
        Map<String, Object> map = store.presentFields();
        assertTrue(map.containsKey("alpha"));
        assertTrue(map.containsKey("beta"));
        assertTrue(map.containsKey("gamma"));
    }

    /**
     * 3.1b -- get(name, default) returns the decoded value when present.
     */
    @Test
    void task3_1b_getReturnsDecodedValueWhenPresent() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Foo",
                new String[]{"count", "label", "active"},
                new String[]{"int", "java.lang.String", "boolean"});

        byte[] payload = seqOf(
                DerWriter.writeInteger(99L),
                DerWriter.writeUtf8String("test"),
                DerWriter.writeBoolean(false)
        );

        DerFieldStore store = new DerFieldStore(sch, payload);

        assertEquals(99, (int) store.get("count", 0), "int field");
        assertEquals("test", store.get("label", "?"), "String field");
        assertEquals(false, store.get("active", true), "boolean field");
        // Typed overloads
        assertEquals(99, store.get("count", 0), "typed int get");
        assertEquals(false, store.get("active", true), "typed boolean get");
    }

    /**
     * 3.1c -- get(name, default) returns the default when field is not in schema.
     * (Different from case (b) which is about payload missing TLVs; here the
     * field name simply isn't in the schema at all.)
     */
    @Test
    void task3_1c_getReturnsDefaultForUnknownField() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Foo",
                new String[]{"value"},
                new String[]{"int"});

        byte[] payload = seqOf(DerWriter.writeInteger(7L));

        DerFieldStore store = new DerFieldStore(sch, payload);

        // "nonexistent" is not in the schema
        Object defaultObj = new Object();
        assertSame(defaultObj, store.get("nonexistent", defaultObj),
                "get() must return provided default for field not in schema");
        assertTrue(store.defaulted("nonexistent"),
                "defaulted() must be true for field not in schema");
    }

    /**
     * 3.1d -- Unrequested fields remain in the store.
     * Decode 3 fields, call get() on only 1, assert the other 2 are still present.
     */
    @Test
    void task3_1d_unrequestedFieldsRemainInStore() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Trio",
                new String[]{"x", "y", "z"},
                new String[]{"int", "int", "int"});

        byte[] payload = seqOf(
                DerWriter.writeInteger(10L),
                DerWriter.writeInteger(20L),
                DerWriter.writeInteger(30L)
        );

        DerFieldStore store = new DerFieldStore(sch, payload);

        // Request only "y"
        int y = store.get("y", -1);
        assertEquals(20, y, "y must decode to 20");

        // "x" and "z" must still be present and correct
        assertFalse(store.defaulted("x"), "x must still be present");
        assertFalse(store.defaulted("z"), "z must still be present");
        assertEquals(10, (int) store.get("x", -1), "x still accessible");
        assertEquals(30, (int) store.get("z", -1), "z still accessible");

        // All three should appear in presentFieldNames
        Set<String> present = store.presentFieldNames();
        assertTrue(present.contains("x"), "x in present set");
        assertTrue(present.contains("y"), "y in present set");
        assertTrue(present.contains("z"), "z in present set");
    }

    /**
     * 3.1e -- A field decoded but never requested is released when the store goes
     * out of scope. Tests using a WeakReference to a stored-but-unrequested byte[].
     *
     * <p>This test is best-effort: it calls System.gc() in a loop and waits up
     * to 5 seconds for the weak reference to be cleared. A JVM that never GCs
     * within 5 seconds would cause a false failure, but that is extremely
     * unlikely in practice and acceptable for a CI test. The test proves there
     * is no static retention of the field values.
     */
    @Test
    void task3_1e_unrequestedFieldReleasedWhenStoreGcEligible() throws DerException, InterruptedException {
        AtomicSerialSchemaRecord sch = schema("com.example.Blob",
                new String[]{"data", "tag"},
                new String[]{"byte[]", "int"});

        // A fresh byte array that we will never request via get()
        byte[] unrequestedBytes = new byte[]{0x01, 0x02, 0x03};

        byte[] payload = seqOf(
                DerWriter.writeOctetString(unrequestedBytes),
                DerWriter.writeInteger(1L)
        );

        DerFieldStore store = new DerFieldStore(sch, payload);

        // Obtain a weak reference to the *copy* held inside the store.
        // We can't get a direct reference to the stored byte[] without calling get(),
        // so we use get() once here and then ensure we don't keep a strong ref to it.
        byte[] storedBytes = (byte[]) store.get("data", null);
        assertNotNull(storedBytes, "data must be decoded");
        WeakReference<byte[]> weakRef = new WeakReference<>(storedBytes);

        // Release our strong reference to the value itself
        storedBytes = null;

        // Now release the store -- no more strong references to it
        store = null;

        // GC loop: wait for the weak reference to be cleared (up to 5 seconds)
        long deadline = System.currentTimeMillis() + 5_000;
        while (weakRef.get() != null && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(50);
        }

        assertNull(weakRef.get(),
                "Stored-but-store-dereferenced byte[] should become GC-eligible; "
                + "weak reference must clear after store is nulled out. "
                + "If this fails, check for accidental static retention.");
    }

    // =========================================================================
    // 3.2 -- Three decoding cases (hand-built schema + DER payloads)
    // =========================================================================

    /**
     * 3.2a -- Case (a): schema matches payload exactly.
     * Assert every byte consumed (trailingFieldsDiscarded==0) and all fields present.
     */
    @Test
    void task3_2a_exactMatch_allFieldsPresentNoBytesDiscarded() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Exact",
                new String[]{"name", "score"},
                new String[]{"java.lang.String", "long"});

        byte[] payload = seqOf(
                DerWriter.writeUtf8String("Alice"),
                DerWriter.writeInteger(BigInteger.valueOf(9876543210L))
        );

        DerFieldStore store = new DerFieldStore(sch, payload);

        // Case (a): all fields present, nothing discarded
        assertEquals(0, store.trailingFieldsDiscarded(),
                "Case (a): no trailing fields should be discarded");
        assertFalse(store.defaulted("name"), "Case (a): 'name' must be present");
        assertFalse(store.defaulted("score"), "Case (a): 'score' must be present");
        assertEquals("Alice", store.get("name", "?"));
        assertEquals(9876543210L, store.get("score", 0L));
        assertEquals(2, store.presentFieldNames().size(),
                "Case (a): exactly 2 fields present");
    }

    /**
     * 3.2b -- Former case (b): schema has MORE fields than the payload provides.
     * Build a payload SEQUENCE with FEWER TLVs than the schema lists.
     * This is now a TLV-count mismatch against the transmitted schema (corruption
     * or tampering, STD-006 S3.9/S11.8), so decoding must fail rather than
     * silently default the missing fields.
     */
    @Test
    void task3_2b_schemaMoreThanPayload_absentFieldsReturnDefaults() {
        // Schema declares 3 fields; payload only provides 1
        AtomicSerialSchemaRecord sch = schema("com.example.Old",
                new String[]{"a", "b", "c"},
                new String[]{"int", "java.lang.String", "boolean"});

        // Payload: only field "a" is present -- "b" and "c" TLVs are missing
        byte[] payload = seqOf(
                DerWriter.writeInteger(55L)
                // "b" and "c" TLVs absent from payload
        );

        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "payload with fewer TLVs than schema fields must fail to decode -- "
                + "missing 'b'/'c' TLVs against the transmitted 3-field schema is "
                + "corruption or tampering, not schema evolution");
    }

    /**
     * 3.2c -- Former case (c): payload has MORE TLVs than the schema has fields.
     * Build a payload SEQUENCE with MORE TLVs than the schema lists. This is now a
     * TLV-count mismatch against the transmitted schema (corruption or tampering,
     * STD-006 S3.9/S11.8), so decoding must fail rather than silently discard the
     * extra trailing TLVs.
     */
    @Test
    void task3_2c_payloadMoreThanSchema_extraTlvsDiscarded() {
        // Schema declares 2 fields; payload provides 4
        AtomicSerialSchemaRecord sch = schema("com.example.New",
                new String[]{"x", "y"},
                new String[]{"int", "boolean"});

        // Payload: 4 TLVs -- first 2 match schema, trailing 2 are unknown extras
        byte[] payload = seqOf(
                DerWriter.writeInteger(77L),           // x
                DerWriter.writeBoolean(false),          // y
                DerWriter.writeUtf8String("extra1"),    // extra TLV #1
                DerWriter.writeInteger(999L)            // extra TLV #2
        );

        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "payload with 2 extra trailing TLVs beyond the 2-field schema must "
                + "fail to decode, not silently discard the extras");
    }

    /**
     * 3.2d -- Schema-is-the-passed-in-schema test.
     * Build two DIFFERENT schemas over the SAME payload bytes and show that
     * decoding follows the schema passed in, not any ambient schema.
     */
    @Test
    void task3_2d_schemaIsAlwaysThePassedInSchema() throws DerException {
        // One payload: 3 TLVs -- int, boolean, String
        byte[] sharedPayload = seqOf(
                DerWriter.writeInteger(42L),
                DerWriter.writeBoolean(true),
                DerWriter.writeUtf8String("hello")
        );

        // Schema A: interprets TLV positions as (int "id"), (boolean "flag"), (String "msg")
        AtomicSerialSchemaRecord schemaA = schema("com.example.A",
                new String[]{"id", "flag", "msg"},
                new String[]{"int", "boolean", "java.lang.String"});

        // Schema B: declares only 1 field -- deliberately mismatched against
        // sharedPayload's 3 TLVs, to show decoding follows whichever schema is
        // passed in (never an ambient/global schema), strictly.
        AtomicSerialSchemaRecord schemaB = schema("com.example.B",
                new String[]{"count"},
                new String[]{"int"});

        DerFieldStore storeA = new DerFieldStore(schemaA, sharedPayload);

        // storeA interprets all 3 TLVs as per schemaA (exact match)
        assertEquals("com.example.A", storeA.className());
        assertEquals(42, storeA.get("id", 0));
        assertEquals(true, storeA.get("flag", false));
        assertEquals("hello", storeA.get("msg", "?"));
        assertEquals(0, storeA.trailingFieldsDiscarded());
        assertSame(schemaA, storeA.schema(), "storeA schema must be the passed-in schemaA");

        // Decoding sharedPayload against schemaB (1 field, 3 TLVs) is a TLV-count
        // mismatch against schemaB's own contract -- corruption/tampering, not a
        // legitimate evolution case, so it must throw rather than silently discard
        // the 2 TLVs schemaB doesn't declare.
        assertThrows(DerException.class, () -> new DerFieldStore(schemaB, sharedPayload),
                "schemaB declares only 1 field; sharedPayload has 3 TLVs, so decoding "
                + "must fail rather than silently discard the 2 extra TLVs");

        // Constructing schemaB against a payload that actually matches its own field
        // count succeeds and confirms schema identity is the passed-in schemaB, not
        // schemaA or any ambient schema.
        byte[] matchingPayload = seqOf(DerWriter.writeInteger(42L));
        DerFieldStore storeB = new DerFieldStore(schemaB, matchingPayload);
        assertEquals("com.example.B", storeB.className());
        assertEquals(42, storeB.get("count", 0));
        assertEquals(0, storeB.trailingFieldsDiscarded());
        assertSame(schemaB, storeB.schema(), "storeB schema must be the passed-in schemaB");

        // "flag"/"msg" don't exist in schemaB's namespace at all -- the untouched
        // "name not in schema" defaulting mechanism, distinct from a TLV-count
        // mismatch.
        assertTrue(storeB.defaulted("flag"), "storeB knows nothing about 'flag'");
        assertTrue(storeB.defaulted("msg"), "storeB knows nothing about 'msg'");
    }

    // =========================================================================
    // 3.3 -- Wire type decode (each supported wireType, overflow, unsupported)
    // =========================================================================

    /** boolean / java.lang.Boolean */
    @Test
    void task3_3_wireType_boolean() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"p", "q"},
                new String[]{"boolean", "java.lang.Boolean"});
        byte[] payload = seqOf(
                DerWriter.writeBoolean(true),
                DerWriter.writeBoolean(false)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals(Boolean.TRUE, store.get("p", null));
        assertEquals(Boolean.FALSE, store.get("q", null));
    }

    /** byte / java.lang.Byte */
    @Test
    void task3_3_wireType_byte() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"a", "b"},
                new String[]{"byte", "java.lang.Byte"});
        byte[] payload = seqOf(
                DerWriter.writeInteger(127L),
                DerWriter.writeInteger(-128L)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals((byte) 127, (Byte) store.get("a", null));
        assertEquals((byte) -128, (Byte) store.get("b", null));
    }

    /** byte overflow -> DerException */
    @Test
    void task3_3_wireType_byte_overflow() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"x"},
                new String[]{"byte"});
        byte[] payload = seqOf(DerWriter.writeInteger(200L)); // > Byte.MAX_VALUE
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "byte overflow must throw DerException");
    }

    /** short / java.lang.Short */
    @Test
    void task3_3_wireType_short() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"s"},
                new String[]{"short"});
        byte[] payload = seqOf(DerWriter.writeInteger(32767L));
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals((short) 32767, (Short) store.get("s", null));
    }

    /** short overflow -> DerException */
    @Test
    void task3_3_wireType_short_overflow() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"s"},
                new String[]{"short"});
        byte[] payload = seqOf(DerWriter.writeInteger(40000L));
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "short overflow must throw DerException");
    }

    /** int / java.lang.Integer */
    @Test
    void task3_3_wireType_int() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"n", "m"},
                new String[]{"int", "java.lang.Integer"});
        byte[] payload = seqOf(
                DerWriter.writeInteger(Integer.MAX_VALUE),
                DerWriter.writeInteger(Integer.MIN_VALUE)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals(Integer.MAX_VALUE, (int) store.get("n", 0));
        assertEquals(Integer.MIN_VALUE, (int) store.get("m", 0));
    }

    /** int overflow -> DerException */
    @Test
    void task3_3_wireType_int_overflow() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"n"},
                new String[]{"int"});
        byte[] payload = seqOf(DerWriter.writeInteger(3_000_000_000L)); // > Integer.MAX_VALUE
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "int overflow must throw DerException");
    }

    /** long / java.lang.Long */
    @Test
    void task3_3_wireType_long() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"t", "u"},
                new String[]{"long", "java.lang.Long"});
        byte[] payload = seqOf(
                DerWriter.writeInteger(Long.MAX_VALUE),
                DerWriter.writeInteger(Long.MIN_VALUE)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals(Long.MAX_VALUE, (long) store.get("t", 0L));
        assertEquals(Long.MIN_VALUE, (long) store.get("u", 0L));
    }

    /** long overflow -> DerException */
    @Test
    void task3_3_wireType_long_overflow() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"n"},
                new String[]{"long"});
        // Value just past Long.MAX_VALUE
        BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        byte[] payload = seqOf(DerWriter.writeInteger(tooBig));
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "long overflow must throw DerException");
    }

    /** java.lang.String */
    @Test
    void task3_3_wireType_string() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"greeting"},
                new String[]{"java.lang.String"});
        byte[] payload = seqOf(DerWriter.writeUtf8String("Hello, World!"));
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals("Hello, World!", store.get("greeting", "?"));
    }

    /** byte[] and [B */
    @Test
    void task3_3_wireType_byteArray() throws DerException {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"blob1", "blob2"},
                new String[]{"byte[]", "[B"});
        byte[] content1 = new byte[]{0x00, 0x01, 0x02};
        byte[] content2 = new byte[]{(byte) 0xFF};
        byte[] payload = seqOf(
                DerWriter.writeOctetString(content1),
                DerWriter.writeOctetString(content2)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertArrayEquals(content1, (byte[]) store.get("blob1", null));
        assertArrayEquals(content2, (byte[]) store.get("blob2", null));
    }

    /** Unsupported wire type -> DerException at construction time */
    @Test
    void task3_3_wireType_unsupported_throwsAtConstruction() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"x"},
                new String[]{"double"}); // deferred per S7.6
        byte[] payload = seqOf(DerWriter.writeBoolean(false)); // content doesn't matter
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "Unsupported wire type must throw DerException at construction");
    }

    /** float is also deferred -> DerException */
    @Test
    void task3_3_wireType_float_deferred() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"f"},
                new String[]{"float"});
        byte[] payload = seqOf(DerWriter.writeBoolean(false));
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "float wireType must throw DerException (deferred per S7.6)");
    }

    /** char is also deferred -> DerException */
    @Test
    void task3_3_wireType_char_deferred() {
        AtomicSerialSchemaRecord sch = schema("T",
                new String[]{"c"},
                new String[]{"char"});
        byte[] payload = seqOf(DerWriter.writeBoolean(false));
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "char wireType must throw DerException (deferred per S7.6)");
    }

    // =========================================================================
    // 3.4 -- Additional edge cases
    // =========================================================================

    /** Empty schema + empty payload -> zero fields, no error */
    @Test
    void task3_4_emptySchemaAndPayload() throws DerException {
        AtomicSerialSchemaRecord sch = new AtomicSerialSchemaRecord("com.example.Empty",
                List.of());
        byte[] payload = seqOf(); // empty SEQUENCE
        DerFieldStore store = new DerFieldStore(sch, payload);
        assertEquals(0, store.presentFieldNames().size());
        assertEquals(0, store.trailingFieldsDiscarded());
        assertTrue(store.defaulted("anything"));
    }

    /**
     * defaulted() returns false for present fields, true for a name the schema
     * doesn't declare at all. A schema-declared field whose TLV is missing from
     * the payload is now a decode-time error (TLV-count mismatch), not a
     * defaulted() case -- see {@link #task3_2b_schemaMoreThanPayload_absentFieldsReturnDefaults()}.
     */
    @Test
    void task3_4_defaulted_presentVsAbsent() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.D",
                new String[]{"p", "q"},
                new String[]{"int", "int"});

        // Both schema fields present -> defaulted() is false for both.
        byte[] fullPayload = seqOf(DerWriter.writeInteger(1L), DerWriter.writeInteger(2L));
        DerFieldStore store = new DerFieldStore(sch, fullPayload);
        assertFalse(store.defaulted("p"), "'p' is present, defaulted must be false");
        assertFalse(store.defaulted("q"), "'q' is present, defaulted must be false");

        // "r" isn't declared in the schema at all -- the untouched, legitimate
        // "name not in schema" defaulting mechanism.
        assertTrue(store.defaulted("r"), "'r' isn't declared in the schema at all");

        // Providing "p"'s TLV but omitting "q"'s is now a TLV-count mismatch
        // against the schema; decoding must fail, not silently default "q".
        byte[] partialPayload = seqOf(DerWriter.writeInteger(1L));
        assertThrows(DerException.class, () -> new DerFieldStore(sch, partialPayload),
                "payload with fewer TLVs than schema fields must fail to decode, "
                + "not silently default the missing field");
    }

    /**
     * Typed get overloads return correct defaults for fields not defined in the
     * schema at all. A schema that declares fields with no corresponding payload
     * TLVs is now a decode-time error, not a source of universally-defaulted
     * fields -- see {@link #task3_2b_schemaMoreThanPayload_absentFieldsReturnDefaults()}.
     */
    @Test
    void task3_4_typedGetDefaultsForAbsentFields() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Defaults",
                new String[]{"a", "b", "c", "d", "e"},
                new String[]{"boolean", "byte", "short", "int", "long"});

        // Payload is empty while the schema declares 5 fields -- a TLV-count
        // mismatch against the schema; decoding must fail rather than silently
        // default every field.
        byte[] payload = seqOf();
        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "empty payload against a 5-field schema must fail to decode, not "
                + "silently default every field");

        // The typed get overloads' default-return behavior is unaffected by the
        // strict-decode fix: it still applies to names the schema doesn't declare
        // at all (an empty schema against an empty, exactly-matching payload).
        AtomicSerialSchemaRecord emptySch = new AtomicSerialSchemaRecord("com.example.Empty", List.of());
        DerFieldStore store = new DerFieldStore(emptySch, seqOf());

        assertEquals(true, store.get("a", true));
        assertEquals((byte) 42, store.get("b", (byte) 42));
        assertEquals((short) -1, store.get("c", (short) -1));
        assertEquals(100, store.get("d", 100));
        assertEquals(-999L, store.get("e", -999L));
    }

    /** Trailing bytes in the outer SEQUENCE wrapper -> DerException */
    @Test
    void task3_4_trailingBytesAfterPayload_throwsDerException() {
        AtomicSerialSchemaRecord sch = schema("com.example.T",
                new String[]{"x"},
                new String[]{"int"});
        // Append an extra byte after the SEQUENCE
        byte[] seqBytes = seqOf(DerWriter.writeInteger(1L));
        byte[] withTrailing = new byte[seqBytes.length + 1];
        System.arraycopy(seqBytes, 0, withTrailing, 0, seqBytes.length);
        withTrailing[seqBytes.length] = 0x00;
        assertThrows(DerException.class, () -> new DerFieldStore(sch, withTrailing),
                "Trailing bytes after payload SEQUENCE must throw DerException");
    }

    /** presentFields() map is ordered by schema field order */
    @Test
    void task3_4_presentFieldsPreservesSchemaOrder() throws DerException {
        AtomicSerialSchemaRecord sch = schema("com.example.Order",
                new String[]{"first", "second", "third"},
                new String[]{"int", "int", "int"});
        byte[] payload = seqOf(
                DerWriter.writeInteger(1L),
                DerWriter.writeInteger(2L),
                DerWriter.writeInteger(3L)
        );
        DerFieldStore store = new DerFieldStore(sch, payload);
        List<String> names = List.copyOf(store.presentFieldNames());
        assertEquals(List.of("first", "second", "third"), names,
                "presentFieldNames() must preserve schema field order");
    }

    /**
     * A payload providing TLVs for only some schema-declared fields (the first N,
     * with the rest missing) is a TLV-count mismatch against the schema -- decoding
     * must fail, not mark the trailing fields defaulted.
     */
    @Test
    void task3_2b_partialPayload_firstTwoPresent_lastAbsent() {
        AtomicSerialSchemaRecord sch = schema("com.example.Partial",
                new String[]{"a", "b", "c", "d"},
                new String[]{"int", "boolean", "java.lang.String", "byte[]"});
        // Only "a" and "b" have TLVs in the payload; "c" and "d" are missing
        byte[] payload = seqOf(
                DerWriter.writeInteger(10L),
                DerWriter.writeBoolean(true)
        );

        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "payload with 2 TLVs against a 4-field schema must fail to decode, "
                + "not silently default the missing 'c'/'d' fields");
    }

    /**
     * A payload providing one known field's TLV plus several trailing TLVs the
     * schema doesn't declare is a TLV-count mismatch against the schema --
     * decoding must fail, not discard the trailing TLVs.
     */
    @Test
    void task3_2c_manyTrailingTlvsDiscarded() {
        AtomicSerialSchemaRecord sch = schema("com.example.One",
                new String[]{"id"},
                new String[]{"int"});
        // Payload: 1 known + 5 extra
        byte[] payload = seqOf(
                DerWriter.writeInteger(1L),
                DerWriter.writeBoolean(true),
                DerWriter.writeUtf8String("x"),
                DerWriter.writeOctetString(new byte[]{1, 2}),
                DerWriter.writeInteger(99L),
                DerWriter.writeBoolean(false)
        );

        assertThrows(DerException.class, () -> new DerFieldStore(sch, payload),
                "payload with 5 extra trailing TLVs beyond the 1-field schema must "
                + "fail to decode, not silently discard the extras");
    }
}
