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

package au.net.zeus.jgdms.der.conformance;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.ClassName;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.getarg.EnumName;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.EnumRecord;
import au.net.zeus.jgdms.der.object.fixtures.Status;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JGDMS-STD-006 S3.11 (data independence from code) for the field kinds whose wire type
 * <em>names a class</em>: {@code enum:<className>}, {@code java.lang.Class}, and arrays of
 * either.
 *
 * <p><b>Invariant defended.</b> S3.11's normative statement: an implementation MUST be able to
 * decode a DER-encoded {@code @AtomicSerial} object into a named field map "given only the DER
 * bytes and the corresponding {@code AtomicSerialSchemaRecord} chain, without loading any class
 * from the originating codebase". The class names in a schema are chosen by whoever sent it, so
 * {@link ObjectCodec#decodeToFieldMap} must not resolve, load or initialise any of them.
 *
 * <p><b>What these tests fail on.</b> Before this invariant was enforced, {@code decodeToFieldMap}
 * decoded an {@code enum:} field through {@code WireTypes.decodeEnum}, which loaded the named
 * class and called {@code Enum.valueOf}. Against that code
 * {@link #absentClasses_decodeToNames()} fails with "cannot load class", and
 * {@link #presentEnumClass_isNeitherInitialisedNorConsulted()} fails either the same way or on
 * its tripwire, depending on which loader the name reaches. {@code SA} in
 * {@link Std006ConformanceTest} did not catch it because its fixture has no such field.
 *
 * <p>The object-constructing path is deliberately unchanged and still needs the class;
 * {@link #constructingPath_stillResolvesTheClass()} pins that, so the class-free mode cannot
 * silently become the only mode.
 */
class Std006DataIndependenceClassNamingFieldsTest {

    /** DER NULL. */
    private static final byte[] NULL = {0x05, 0x00};

    /** Names that no loader in this test run can resolve. */
    private static final String ABSENT_HOLDER = "com.example.absent.Holder";
    private static final String ABSENT_ENUM = "com.example.absent.Colour";

    /** Set by {@link Tripwire}'s static initialiser, and by nothing else. */
    static volatile boolean tripwireInitialised;

    /**
     * An enum that IS resolvable by name in this test run. Nothing in this class refers to it
     * except by name, so it is initialised only if a decode initialises it.
     */
    enum Tripwire {
        ARMED;

        static {
            tripwireInitialised = true;
        }
    }

    /** The binary name of {@link Tripwire}, built without touching the class. */
    private static final String TRIPWIRE_NAME =
            Std006DataIndependenceClassNamingFieldsTest.class.getName() + "$Tripwire";

    private static byte[] utf8(String s) {
        return DerWriter.writeUtf8String(s);
    }

    /** One class's private SEQUENCE wrapped in the outer hierarchy SEQUENCE. */
    private static byte[] hierarchyOf(List<byte[]> fieldTlvs) {
        return DerWriter.writeSequence(List.of(DerWriter.writeSequence(fieldTlvs)));
    }

    private static SchemaChain.Result chainOf(String className, AtomicSerialFieldDef... fields) {
        return SchemaChain.linkAndGetLeafDigest(
                List.of(new AtomicSerialSchemaRecord(className, List.of(fields))));
    }

    /**
     * Every class named by the schema is absent, and the record still decodes: enum and Class
     * values come back as names, scalars as themselves.
     */
    @Test
    void absentClasses_decodeToNames() throws Exception {
        SchemaChain.Result chain = chainOf(ABSENT_HOLDER,
                new AtomicSerialFieldDef("colour", "enum:" + ABSENT_ENUM),
                new AtomicSerialFieldDef("kind", "java.lang.Class"),
                new AtomicSerialFieldDef("colours", "array:enum:" + ABSENT_ENUM),
                new AtomicSerialFieldDef("kinds", "array:java.lang.Class"),
                new AtomicSerialFieldDef("count", "int"),
                new AtomicSerialFieldDef("label", "java.lang.String"),
                new AtomicSerialFieldDef("sizes", "array:int"),
                new AtomicSerialFieldDef("noColour", "enum:" + ABSENT_ENUM),
                new AtomicSerialFieldDef("noKind", "java.lang.Class"),
                new AtomicSerialFieldDef("noColours", "array:enum:" + ABSENT_ENUM));
        byte[] payload = hierarchyOf(List.of(
                utf8("RED"),
                utf8("com.example.absent.Kind"),
                DerWriter.writeSequence(List.of(utf8("RED"), NULL, utf8("BLUE"))),
                DerWriter.writeSequence(List.of(utf8("com.example.absent.A"), NULL)),
                DerWriter.writeInteger(7),
                utf8("x"),
                DerWriter.writeSequence(List.of(DerWriter.writeInteger(1), DerWriter.writeInteger(2))),
                NULL,
                NULL,
                NULL));

        LinkedHashMap<String, Map<String, Object>> fieldMap = ObjectCodec.decodeToFieldMap(chain, payload);

        assertEquals(List.of(ABSENT_HOLDER), List.copyOf(fieldMap.keySet()));
        Map<String, Object> f = fieldMap.get(ABSENT_HOLDER);
        assertEquals(10, f.size(), "every schema field is present");
        assertEquals(new EnumName(ABSENT_ENUM, "RED"), f.get("colour"));
        assertEquals(new ClassName("com.example.absent.Kind"), f.get("kind"));
        assertArrayEquals(
                new EnumName[] {new EnumName(ABSENT_ENUM, "RED"), null, new EnumName(ABSENT_ENUM, "BLUE")},
                (EnumName[]) f.get("colours"));
        assertArrayEquals(
                new ClassName[] {new ClassName("com.example.absent.A"), null},
                (ClassName[]) f.get("kinds"));
        assertEquals(7, f.get("count"));
        assertEquals("x", f.get("label"));
        assertArrayEquals(new int[] {1, 2}, (int[]) f.get("sizes"));
        assertTrue(f.containsKey("noColour"), "a wire-null field is present");
        assertNull(f.get("noColour"), "a wire-null enum is null, not an EnumName");
        assertNull(f.get("noKind"), "a wire-null Class is null, not a ClassName");
        assertNull(f.get("noColours"), "a wire-null array is null");
    }

    /**
     * The schema names an enum this JVM CAN resolve. The decode must still not load or initialise
     * it, and must not consult it: a constant name the enum does not declare is returned as
     * written, because checking it needs the class.
     */
    @Test
    void presentEnumClass_isNeitherInitialisedNorConsulted() throws Exception {
        SchemaChain.Result chain = chainOf(ABSENT_HOLDER,
                new AtomicSerialFieldDef("declared", "enum:" + TRIPWIRE_NAME),
                new AtomicSerialFieldDef("undeclared", "enum:" + TRIPWIRE_NAME),
                new AtomicSerialFieldDef("several", "array:enum:" + TRIPWIRE_NAME),
                new AtomicSerialFieldDef("type", "java.lang.Class"),
                new AtomicSerialFieldDef("types", "array:java.lang.Class"));
        byte[] payload = hierarchyOf(List.of(
                utf8("ARMED"),
                utf8("NO_SUCH_CONSTANT"),
                DerWriter.writeSequence(List.of(utf8("ARMED"))),
                utf8(TRIPWIRE_NAME),
                DerWriter.writeSequence(List.of(utf8(TRIPWIRE_NAME)))));

        Map<String, Object> f = ObjectCodec.decodeToFieldMap(chain, payload).get(ABSENT_HOLDER);

        assertFalse(tripwireInitialised,
                "decodeToFieldMap initialised an enum class named by the schema (STD-006 S3.11)");
        assertEquals(new EnumName(TRIPWIRE_NAME, "ARMED"), f.get("declared"));
        assertEquals(new EnumName(TRIPWIRE_NAME, "NO_SUCH_CONSTANT"), f.get("undeclared"));
        assertArrayEquals(new EnumName[] {new EnumName(TRIPWIRE_NAME, "ARMED")}, (EnumName[]) f.get("several"));
        assertEquals(new ClassName(TRIPWIRE_NAME), f.get("type"));
        assertArrayEquals(new ClassName[] {new ClassName(TRIPWIRE_NAME)}, (ClassName[]) f.get("types"));
    }

    /** What the real encoder writes for an enum field decodes to that enum's names. */
    @Test
    void encodedEnumField_decodesToItsNames() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);

        byte[] set = ObjectCodec.encodeHierarchy(new EnumRecord("a", Status.ACTIVE), chain);
        Map<String, Object> f = ObjectCodec.decodeToFieldMap(chain, set).get(EnumRecord.class.getName());
        assertEquals("a", f.get("label"));
        assertEquals(new EnumName(Status.class.getName(), "ACTIVE"), f.get("status"));

        byte[] unset = ObjectCodec.encodeHierarchy(new EnumRecord("b", null), chain);
        Map<String, Object> g = ObjectCodec.decodeToFieldMap(chain, unset).get(EnumRecord.class.getName());
        assertTrue(g.containsKey("status"));
        assertNull(g.get("status"));
    }

    /**
     * The class-free decode refuses what the class-free admission validator refuses, with a
     * {@link DerException} and never an unchecked exception.
     */
    @Test
    void classFreeDecode_refusesWhatAdmissionRefuses() {
        // A component type that is neither a value type, String, Class nor an enum.
        assertThrows(DerException.class, () -> ObjectCodec.decodeToFieldMap(
                chainOf(ABSENT_HOLDER, new AtomicSerialFieldDef("a", "array:java.lang.Object")),
                hierarchyOf(List.of(DerWriter.writeSequence(List.of(utf8("x")))))));
        // More than one array dimension.
        assertThrows(DerException.class, () -> ObjectCodec.decodeToFieldMap(
                chainOf(ABSENT_HOLDER, new AtomicSerialFieldDef("a", "array:array:int")),
                hierarchyOf(List.of(DerWriter.writeSequence(List.<byte[]>of())))));
        // A NULL element in an array of a primitive type.
        assertThrows(DerException.class, () -> ObjectCodec.decodeToFieldMap(
                chainOf(ABSENT_HOLDER, new AtomicSerialFieldDef("a", "array:int")),
                hierarchyOf(List.of(DerWriter.writeSequence(List.of(DerWriter.writeInteger(1), NULL))))));
        // An enum constant that is not a UTF8String.
        assertThrows(DerException.class, () -> ObjectCodec.decodeToFieldMap(
                chainOf(ABSENT_HOLDER, new AtomicSerialFieldDef("e", "enum:" + ABSENT_ENUM)),
                hierarchyOf(List.of(DerWriter.writeInteger(1)))));
        // An unknown wire type.
        assertThrows(DerException.class, () -> ObjectCodec.decodeToFieldMap(
                chainOf(ABSENT_HOLDER, new AtomicSerialFieldDef("u", "com.example.absent.Unknown")),
                hierarchyOf(List.of(utf8("x")))));
    }

    /**
     * The object-constructing store is unchanged: it resolves the enum's class, so an absent
     * class is a decode failure there. (This is what the class-free store exists to avoid.)
     */
    @Test
    void constructingPath_stillResolvesTheClass() {
        AtomicSerialSchemaRecord schema = new AtomicSerialSchemaRecord(ABSENT_HOLDER,
                List.of(new AtomicSerialFieldDef("colour", "enum:" + ABSENT_ENUM)));
        byte[] classSequence = DerWriter.writeSequence(List.of(utf8("RED")));

        DerException e = assertThrows(DerException.class, () -> new DerFieldStore(schema, classSequence));
        assertTrue(e.getMessage().contains(ABSENT_ENUM), e.getMessage());
    }
}
