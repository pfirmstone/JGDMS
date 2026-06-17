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

import au.net.zeus.jgdms.der.object.fixtures.Bar;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4.4 acceptance tests: S3.10 wire-visibility rules for non-{@code @AtomicSerial}
 * classes in a hierarchy (JGDMS-STD-006 S3.10).
 *
 * <h2>S3.10 rule under test</h2>
 *
 * <p><b>Rule 1 -- Non-{@code @AtomicSerial} subclass dropped to its superclass.</b>
 * When {@code Bar extends Foo} and only {@code Foo} carries {@code @AtomicSerial}:
 * serialising a {@code Bar} instance writes only {@code Foo}'s SEQUENCE; deserialisation
 * produces a {@code Foo} -- not a {@code Bar}. {@code Bar}'s extra state ({@code barOnly})
 * is silently dropped. {@code generateChain(Bar.class)} yields a single-record chain whose
 * sole entry names {@code Foo}, not {@code Bar}.
 *
 * <p>(S3.10's "rule 2" -- an {@code @AtomicSerial} class carrying a non-{@code @AtomicSerial}
 * superclass's field in its own namespace -- was REMOVED under STD-008 full enforcement:
 * a class serializes only its OWN namespace, and a non-{@code @AtomicSerial} class has no
 * namespace, so reaching into a non-{@code @AtomicSerial} superclass's state is a contract
 * violation. The {@code Sub}/{@code PlainSuper} fixtures and their cases were deleted.)
 *
 * <h2>Tests in this class</h2>
 * <ul>
 *   <li><b>4.4.1</b> -- Chain from {@code Bar.class} contains only the {@code Foo} record.</li>
 *   <li><b>4.4.2</b> -- Encoding a {@code Bar} produces exactly one per-class SEQUENCE
 *       (Foo's only -- no Bar SEQUENCE on the wire).</li>
 *   <li><b>4.4.3</b> -- Round-trip of {@code Bar}: decoded runtime class is exactly
 *       {@code Foo} (not {@code Bar}); Foo's fields survive; {@code barOnly} is gone.</li>
 *   <li><b>4.4.6</b> -- Phase 4.3 regression: a fully-{@code @AtomicSerial} chain
 *       (Alpha) is unchanged.</li>
 * </ul>
 */
class WireVisibilityTest {

    // =========================================================================
    // 4.4.1 -- Chain from Bar.class: only the Foo record
    // =========================================================================

    /**
     * 4.4.1 -- {@code generateChain(Bar.class)} must yield a chain with exactly ONE
     * record, and that record must name {@code Foo} (the lowest {@code @AtomicSerial}
     * class in the hierarchy), NOT {@code Bar}.
     *
     * <p>This proves that the hierarchy walk skips non-{@code @AtomicSerial} classes
     * (S3.10, first rule): {@code Bar} is plain -> invisible to the wire; {@code Foo}
     * is {@code @AtomicSerial} -> the sole wire contributor.
     */
    @Test
    void test_4_4_1_Chain_From_Bar_Contains_Only_Foo_Record() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Bar.class);
        List<AtomicSerialSchemaRecord> records = chain.chain();

        assertEquals(1, records.size(),
                "Chain from Bar.class must have exactly 1 record (Bar is plain -- invisible to wire)");

        AtomicSerialSchemaRecord onlyRecord = records.get(0);
        assertEquals(Foo.class.getName(), onlyRecord.className(),
                "The sole chain record must name Foo, not Bar");

        // The record must have Foo's fields: fooId and fooLabel
        assertEquals(2, onlyRecord.fields().size(),
                "Foo's schema record must have exactly 2 fields");
        assertEquals("fooId",    onlyRecord.fields().get(0).wireName());
        assertEquals("fooLabel", onlyRecord.fields().get(1).wireName());
    }

    // =========================================================================
    // 4.4.2 -- Encoding a Bar produces exactly one SEQUENCE (Foo's) on the wire
    // =========================================================================

    /**
     * 4.4.2 -- Encoding a {@code Bar} instance via {@code encodeHierarchy} produces
     * an outer SEQUENCE containing exactly ONE child SEQUENCE (Foo's SEQUENCE).
     *
     * <p>Proves that {@code Bar} does not exist on the wire (S3.10, first rule: "the
     * set of SEQUENCEs on the wire == the set of {@code @AtomicSerial} classes").
     * The chain has one record -> one child SEQUENCE in the outer wrapper.
     */
    @Test
    void test_4_4_2_Encoded_Bar_Has_Exactly_One_Sequence_On_Wire() throws Exception {
        Bar bar = new Bar(42, "foo-label", "bar-dropped");

        SchemaChain.Result chain = SchemaGenerator.generateChain(Bar.class);
        byte[] der = ObjectCodec.encodeHierarchy(bar, chain);

        // The chain has exactly 1 record, so exactly 1 child SEQUENCE inside the outer wrapper.
        assertEquals(1, chain.chain().size(),
                "Chain must have exactly 1 record (= 1 SEQUENCE on wire)");

        // Additionally verify the DER structure: outer SEQUENCE must contain exactly one child.
        // We decode to confirm there are no extra SEQUENCEs lurking.
        Foo decoded = ObjectCodec.decodeHierarchy(Foo.class, chain, der);
        assertNotNull(decoded, "Decoded result must not be null");

        // If there were a second child SEQUENCE (Bar's), decodeHierarchy would throw
        // "trailing bytes in outer SEQUENCE (more SEQUENCEs than schema records)".
        // The fact that decode succeeds with no exception proves there is exactly one.
    }

    // =========================================================================
    // 4.4.3 -- Round-trip of Bar: decoded class is exactly Foo; barOnly is gone
    // =========================================================================

    /**
     * 4.4.3 -- Full round-trip of {@code Bar}:
     * <ul>
     *   <li>The decoded object's runtime class is exactly {@code Foo} (not {@code Bar}).</li>
     *   <li>{@code Foo}'s fields ({@code fooId}, {@code fooLabel}) survive.</li>
     *   <li>{@code Bar}'s extra field ({@code barOnly}) is completely gone -- the result
     *       has no way to access it (it's a plain {@code Foo}, not a {@code Bar}).</li>
     * </ul>
     *
     * <p>The decoded object is NOT an instance of {@code Bar} -- Bar opted out of the
     * wire contract by not carrying {@code @AtomicSerial}.
     */
    @Test
    void test_4_4_3_RoundTrip_Bar_DecodesTo_Foo_BarOnly_Dropped() throws Exception {
        int    fooId    = 99;
        String fooLabel = "hello-from-foo";
        String barOnly  = "bar-state-should-be-dropped";

        Bar bar = new Bar(fooId, fooLabel, barOnly);

        // Pre-condition: bar IS a Bar (and IS a Foo, since Bar extends Foo)
        assertEquals(Bar.class, bar.getClass(), "Pre-condition: bar is a Bar");
        assertTrue(bar instanceof Foo,           "Pre-condition: bar instanceof Foo");
        assertEquals(barOnly, bar.getBarOnly(),  "Pre-condition: barOnly is accessible on Bar");

        SchemaChain.Result chain = SchemaGenerator.generateChain(Bar.class);
        byte[] der = ObjectCodec.encodeHierarchy(bar, chain);

        // Decode: expectedSupertype is Foo (Bar's @AtomicSerial superclass)
        Foo decoded = ObjectCodec.decodeHierarchy(Foo.class, chain, der);

        // CRITICAL: the decoded object's runtime class must be exactly Foo, NOT Bar
        assertEquals(Foo.class, decoded.getClass(),
                "Decoded object's runtime class must be exactly Foo.class -- "
                + "Bar is a non-@AtomicSerial subclass and must not exist in the result");

        assertFalse(decoded instanceof Bar,
                "Decoded Foo must NOT be an instance of Bar -- "
                + "Bar dropped out of the wire contract");

        // Foo's own fields must survive
        assertEquals(fooId,    decoded.getFooId(),
                "fooId must survive the round-trip");
        assertEquals(fooLabel, decoded.getFooLabel(),
                "fooLabel must survive the round-trip");

        // Foo.equals checks fooId + fooLabel -- the decoded Foo must equal a Foo built
        // with the same values (proves field fidelity from Bar's inherited Foo state).
        assertEquals(new Foo(fooId, fooLabel), decoded,
                "Decoded Foo must equal a Foo constructed with the same field values");

        // barOnly is inaccessible: decoded is a Foo, not a Bar -- there is no getBarOnly().
        // This is proven structurally by decoded.getClass() == Foo.class above.
    }

    // =========================================================================
    // 4.4.3b -- decodeHierarchy with Bar.class as expectedSupertype throws
    // =========================================================================

    /**
     * 4.4.3b -- When {@code expectedSupertype = Bar.class}, the assignability check
     * {@code Bar.class.isAssignableFrom(Foo.class)} is {@code false} (Bar extends Foo,
     * not the reverse), so {@code decodeHierarchy(Bar.class, chain, der)} must throw a
     * {@link au.net.zeus.jgdms.der.DerException} identifying the mismatch.
     *
     * <p>This documents the assignability check boundary: the caller cannot ask for
     * "decode as a Bar" when the construct class is Foo (Foo is not a Bar).
     */
    @Test
    void test_4_4_3b_DecodeHierarchy_With_Bar_As_ExpectedSupertype_Throws() throws Exception {
        Bar bar = new Bar(1, "label", "extra");
        SchemaChain.Result chain = SchemaGenerator.generateChain(Bar.class);
        byte[] der = ObjectCodec.encodeHierarchy(bar, chain);

        // Foo is NOT assignable to Bar -> DerException expected
        assertThrows(au.net.zeus.jgdms.der.DerException.class,
                () -> ObjectCodec.decodeHierarchy(Bar.class, chain, der),
                "decodeHierarchy(Bar.class, ...) must throw DerException "
                + "because Foo (the construct class) is not a Bar");
    }

    // =========================================================================
    // 4.4.6 -- Phase 4.3 regression: all-@AtomicSerial chain unchanged
    // =========================================================================

    /**
     * 4.4.6 -- Regression: a fully {@code @AtomicSerial} hierarchy (Phase 4.3 style)
     * continues to work correctly after the Phase 4.4 change.
     *
     * <p>Uses the existing Phase 4.3 fixture {@link au.net.zeus.jgdms.der.object.fixtures.Alpha}
     * to prove that when {@code expectedSupertype == chain.chain().get(0).className()},
     * the behaviour is identical to the old code.
     */
    @Test
    void test_4_4_6_Phase43_Regression_AllAtomicSerial_Chain_Unchanged() throws Exception {
        au.net.zeus.jgdms.der.object.fixtures.Alpha alpha =
                new au.net.zeus.jgdms.der.object.fixtures.Alpha(55, "regression-label");

        SchemaChain.Result chain = SchemaGenerator.generateChain(
                au.net.zeus.jgdms.der.object.fixtures.Alpha.class);
        assertEquals(1, chain.chain().size());
        assertEquals(au.net.zeus.jgdms.der.object.fixtures.Alpha.class.getName(),
                chain.chain().get(0).className());

        byte[] der = ObjectCodec.encodeHierarchy(alpha, chain);
        au.net.zeus.jgdms.der.object.fixtures.Alpha decoded =
                ObjectCodec.decodeHierarchy(
                        au.net.zeus.jgdms.der.object.fixtures.Alpha.class, chain, der);

        assertEquals(alpha, decoded, "Alpha round-trip must be unchanged after Phase 4.4 refactor");
        assertEquals(55, decoded.getX());
        assertEquals("regression-label", decoded.getAlphaLabel());
    }
}
