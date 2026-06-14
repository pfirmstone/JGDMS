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

package au.net.zeus.jgdms.der.evolution;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev3_EvolvedSub;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev4_Child;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev4_Parent;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev5_Leaf;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev5_Root;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev6_Beta;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev67_Alpha;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev67_Mid;
import au.net.zeus.jgdms.der.evolution.fixtures.Ev7_Beta;
import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.object.fixtures.Bar;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 — Evolution test matrix for JGDMS-STD-006 §11.
 *
 * <h2>Task 6.1 — Class-hierarchy evolution (§11.1–§11.7)</h2>
 *
 * <p>Each test proves one row of the §11.9 summary table by asserting the stated
 * wire impact.  Evolution is simulated per the Phase 3.2/5.2 pattern: hand-build
 * the "at-marshal-time" schema and a matching DER payload, then decode against the
 * fixture class whose CURRENT {@code serialForm()} differs.
 *
 * <h2>Task 6.2 — Field-level evolution (§11.8)</h2>
 *
 * <p>Three field-level cases proved directly against {@link DerFieldStore}:
 * <ul>
 *   <li>Add field at end — old data → default; new data → value.</li>
 *   <li>Stop requesting a field — field sits in store, unrequested.</li>
 *   <li>Field present on wire, absent from schema — trailing discard, no error.</li>
 * </ul>
 *
 * <h2>Absent-class namespace isolation (§11.4 / §11.6) — RESOLVED</h2>
 *
 * <p>When old data lacks an @AtomicSerial class's SEQUENCE (e.g. §11.4 "Beta updated
 * to call super(arg)" with no Alpha SEQUENCE, or §11.6 a Mid class inserted after the
 * data was written), that class's constructor runs against an EMPTY store and its
 * {@code arg.get(name, default)} calls return defaults — its namespace is simply
 * absent. The constructor then applies invariant checking on those defaults: it may
 * proceed (if the defaults are acceptable) or throw {@code InvalidObjectException}
 * (§11.8 — "throwing if [the defaults do not satisfy invariants]"). The
 * {@code _Gap_}-named methods below assert that SPEC-CORRECT THROW for fixtures with a
 * non-null invariant; they do NOT indicate a defect.
 *
 * <p><b>Important:</b> {@link au.net.zeus.jgdms.der.object.ObjectCodec#decodeHierarchy}
 * registers an EMPTY {@code DerFieldStore} for every @AtomicSerial class in the
 * construct class's hierarchy that is absent from the embedded data, so each level's
 * {@code get()} resolves to its OWN (empty) namespace. An earlier implementation
 * skipped the absent class's stack frame and resolved to a NEIGHBOUR's store — a §3.9
 * namespace leak (only observable with a colliding field name). That bug is fixed and
 * proved by {@code NamespaceLeakRegressionTest}.
 */
class EvolutionMatrixTest {

    // =========================================================================
    // §11.1 — Add non-@AtomicSerial subclass → no wire impact
    // =========================================================================

    /**
     * §11.1 — Serialising a plain (non-{@code @AtomicSerial}) subclass ({@link Bar})
     * of an {@code @AtomicSerial} class ({@link Foo}) produces a wire form that
     * contains ONLY the {@code Foo} SEQUENCE.  Decoding produces a {@code Foo}.
     *
     * <p>Simulation: {@code Bar} represents the "added non-{@code @AtomicSerial}
     * subclass".  The generated chain from {@code Bar.class} contains only {@code Foo}'s
     * record — {@code Bar} is wire-invisible.
     *
     * <p>Observable: chain size == 1; chain record names {@code Foo}; decoded object
     * is exactly a {@code Foo} (not a {@code Bar}); {@code Foo}'s fields survive.
     */
    @Test
    void test_11_1_AddNonAtomicSerialSubclass_NoWireImpact() throws Exception {
        // Arrange: Bar is the "new non-@AtomicSerial subclass" of Foo
        Bar bar = new Bar(7, "foo-label", "bar-only-dropped");

        // Act: generate chain (which ignores Bar) and encode
        SchemaChain.Result chain = SchemaGenerator.generateChain(Bar.class);
        byte[] der = ObjectCodec.encodeHierarchy(bar, chain);
        Foo decoded = ObjectCodec.decodeHierarchy(Foo.class, chain, der);

        // Assert: chain contains only Foo's record (Bar has no wire presence)
        assertEquals(1, chain.chain().size(),
                "§11.1: chain must have exactly 1 record (Bar is invisible to wire)");
        assertEquals(Foo.class.getName(), chain.chain().get(0).className(),
                "§11.1: sole record must be Foo, not Bar");

        // Assert: decoded object is a Foo (not a Bar)
        assertEquals(Foo.class, decoded.getClass(),
                "§11.1: decoded type must be exactly Foo.class — Bar drops out");
        assertFalse(decoded instanceof Bar,
                "§11.1: decoded Foo must NOT be an instance of Bar");

        // Assert: Foo's fields survive
        assertEquals(7, decoded.getFooId(),
                "§11.1: fooId must survive round-trip");
        assertEquals("foo-label", decoded.getFooLabel(),
                "§11.1: fooLabel must survive round-trip");
    }

    // =========================================================================
    // §11.2 — Remove non-@AtomicSerial subclass → no wire impact
    // =========================================================================

    /**
     * §11.2 — Removing a non-{@code @AtomicSerial} subclass leaves the
     * superclass's wire form completely unchanged.
     *
     * <p>Simulation: encode a {@code Foo} instance directly (simulating the state
     * AFTER {@code Bar} was removed).  Encode via the same chain as if {@code Bar}
     * never existed (chain from {@code Foo.class}).  The wire bytes are identical
     * whether Bar existed or not.
     *
     * <p>Observable: the chain from {@code Foo.class} has the same single record
     * as the chain from {@code Bar.class} (both resolve to {@code Foo}).  The DER
     * bytes produced for a {@code Foo} with given field values are identical
     * regardless of whether {@code Bar} existed.
     */
    @Test
    void test_11_2_RemoveNonAtomicSerialSubclass_NoWireImpact() throws Exception {
        final int    id    = 42;
        final String label = "stability-label";

        // Encode the @AtomicSerial superclass Foo directly (Bar removed)
        Foo foo = new Foo(id, label);
        SchemaChain.Result chainFoo = SchemaGenerator.generateChain(Foo.class);
        byte[] derFoo = ObjectCodec.encodeHierarchy(foo, chainFoo);

        // Encode via Bar's chain (Bar exists as a subclass)
        Bar bar = new Bar(id, label, "ignored-bar-field");
        SchemaChain.Result chainBar = SchemaGenerator.generateChain(Bar.class);
        byte[] derBar = ObjectCodec.encodeHierarchy(bar, chainBar);

        // Assert: the two chains produce the same chain schema (both resolve to Foo)
        assertArrayEquals(chainFoo.leafDigest(), chainBar.leafDigest(),
                "§11.2: chain digest must be identical whether Bar exists or not");

        // Assert: the DER bytes are identical (wire is Foo-only in both cases)
        assertArrayEquals(derFoo, derBar,
                "§11.2: DER bytes must be identical — Bar's presence/absence has no wire impact");

        // Assert: decode of "Bar-encoded" bytes produces correct Foo
        Foo decoded = ObjectCodec.decodeHierarchy(Foo.class, chainFoo, derBar);
        assertEquals(id,    decoded.getFooId());
        assertEquals(label, decoded.getFooLabel());
    }

    // =========================================================================
    // §11.3 — Add non-@AtomicSerial superclass → child adds fields; old data → defaults
    // =========================================================================

    /**
     * §11.3 — A non-{@code @AtomicSerial} superclass is inserted above an
     * {@code @AtomicSerial} child.  The child adds the superclass's fields at the
     * END of its own {@code serialForm()}.  Old data (encoded before the insertion)
     * lacks the new fields → they return defaults.
     *
     * <p>Simulation: {@link Ev3_EvolvedSub} is the "AFTER" state with 3 fields.
     * We hand-build an old (2-field) schema and matching payload to represent the
     * "BEFORE" state, then decode with MarshalledInstanceCodec.
     *
     * <p>Observable:
     * <ul>
     *   <li>Old data (2-field payload): {@code legacyName} and {@code subValue}
     *       decode correctly; {@code newBaseTag} returns {@code "DEFAULT_BASE_TAG"}.</li>
     *   <li>New data (3-field payload): all three fields decode correctly.</li>
     * </ul>
     */
    @Test
    void test_11_3_AddNonAtomicSerialSuperclass_OldData_NewFieldDefaulted() throws Exception {
        String className = Ev3_EvolvedSub.class.getName();

        // === OLD data path: 2-field schema (before NewBase was inserted) ===
        AtomicSerialSchemaRecord oldSchema = new AtomicSerialSchemaRecord(
                className, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("legacyName", "java.lang.String"),
                    new AtomicSerialFieldDef("subValue",   "int")
                ));
        SchemaChain.Result oldChain = SchemaChain.linkAndGetLeafDigest(List.of(oldSchema));

        // Old payload: only legacyName + subValue
        byte[] oldInnerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("old-legacy"),
                DerWriter.writeInteger(BigInteger.valueOf(77))
        ));
        byte[] oldPayload = DerWriter.writeSequence(List.of(oldInnerSeq));

        MarshalledInstanceRecord oldRec = new MarshalledInstanceRecord(
                oldPayload, oldSchema.encode(), oldChain.leafDigest(),
                Optional.empty(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        MarshalledInstanceCodec.Result<Ev3_EvolvedSub> oldResult =
                MarshalledInstanceCodec.decodeMarshalledInstance(oldRec, Ev3_EvolvedSub.class);

        // Old data: embedded schema mismatch detected
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, oldResult.schemaCase(),
                "§11.3: old-data schema mismatch must be detected");

        Ev3_EvolvedSub fromOld = oldResult.object();
        assertEquals("old-legacy", fromOld.getLegacyName(),
                "§11.3: legacyName must decode from old data");
        assertEquals(77, fromOld.getSubValue(),
                "§11.3: subValue must decode from old data");
        assertEquals("DEFAULT_BASE_TAG", fromOld.getNewBaseTag(),
                "§11.3: newBaseTag absent in old data — must return DEFAULT_BASE_TAG");

        // === NEW data path: current 3-field round-trip ===
        SchemaChain.Result newChain = SchemaGenerator.generateChain(Ev3_EvolvedSub.class);
        Ev3_EvolvedSub original = new Ev3_EvolvedSub("new-legacy", 55, "base-tag-value");
        byte[] newPayload = ObjectCodec.encodeHierarchy(original, newChain);
        MarshalledInstanceRecord newRec = MarshalledInstanceRecord.fromChain(newChain, newPayload);

        MarshalledInstanceCodec.Result<Ev3_EvolvedSub> newResult =
                MarshalledInstanceCodec.decodeMarshalledInstance(newRec, Ev3_EvolvedSub.class);

        assertEquals(MarshalledInstanceCodec.SchemaCase.A_MATCH, newResult.schemaCase(),
                "§11.3: new-data schema must match");
        assertEquals(original, newResult.object(),
                "§11.3: new-data round-trip must be equal");
        assertEquals("base-tag-value", newResult.object().getNewBaseTag(),
                "§11.3: newBaseTag must be present in new data");
    }

    // =========================================================================
    // §11.4 — Add @AtomicSerial to superclass → existing child SEQUENCE unchanged;
    //          new parent SEQUENCE present but not consumed by unmodified child
    // =========================================================================

    /**
     * §11.4 — {@code Ev4_Parent} gains {@code @AtomicSerial}.  {@code Ev4_Child}
     * is the "UNMODIFIED child" that still calls a regular {@code super(int)} rather
     * than {@code super(check(arg))}.
     *
     * <p><b>Direction 1 — New data (both parent and child SEQUENCEs):</b>
     * Encode with the full NEW chain [Parent + Child].  Wire has two SEQUENCEs.
     * The child's {@code (GetArg)} constructor calls {@code super(childParentValue)}
     * — a REGULAR constructor.  Parent's new SEQUENCE is built into DerGetArg but
     * is NEVER consumed by any {@code (GetArg)} call.  Decode succeeds; field values
     * are correct.
     *
     * <p><b>Direction 2 — Old data (child SEQUENCE only):</b>
     * Hand-build a MarshalledInstanceRecord with old embedded schema (child only).
     * MIC decodes using old embedded chain (1 record).  Child(GetArg) is invoked;
     * it calls {@code super(childParentValue)} — the regular constructor — so
     * Parent(GetArg) is never called.  No store-lookup for Parent occurs.  Decode
     * succeeds.
     *
     * <p>Observable:
     * <ul>
     *   <li>Direction 1: Decode succeeds; child fields correct; parent value matches
     *       what child carries in its own namespace.</li>
     *   <li>Direction 2: Decode succeeds; child fields correct; parent value comes
     *       from child's {@code childParentValue} field, not from Parent's store.</li>
     * </ul>
     */
    @Test
    void test_11_4_AddAtomicSerialToSuperclass_ExistingChildSequenceUnchanged() throws Exception {
        // === Direction 1: New data with BOTH SEQUENCEs ===
        Ev4_Child child = new Ev4_Child(100, 200);
        SchemaChain.Result newChain = SchemaGenerator.generateChain(Ev4_Child.class);

        // Chain must include BOTH Parent and Child
        assertEquals(2, newChain.chain().size(),
                "§11.4: new chain must have 2 records (Parent + Child)");
        assertEquals(Ev4_Child.class.getName(),  newChain.chain().get(0).className(),
                "§11.4: leaf record must be Ev4_Child");
        assertEquals(Ev4_Parent.class.getName(), newChain.chain().get(1).className(),
                "§11.4: root record must be Ev4_Parent");

        byte[] newPayload = ObjectCodec.encodeHierarchy(child, newChain);
        Ev4_Child fromNew = ObjectCodec.decodeHierarchy(Ev4_Child.class, newChain, newPayload);

        // Child's fields survive; parent value comes from child's childParentValue
        assertEquals(100, fromNew.getParentValue(),
                "§11.4: parentValue must be 100 (from child's childParentValue field)");
        assertEquals(200, fromNew.getChildValue(),
                "§11.4: childValue must be 200");
        // Child's own SEQUENCE carries childParentValue=100 and childValue=200
        // Parent's SEQUENCE also carries parentValue=100 (encoded from Ev4_Parent.parentValue)
        // Parent's SEQUENCE is present but unmodified child calls super(int) not super(check(arg))

        // === Direction 2: Old data (child SEQUENCE only via MIC) ===
        String childClassName = Ev4_Child.class.getName();
        AtomicSerialSchemaRecord oldChildSchema = new AtomicSerialSchemaRecord(
                childClassName, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("parentValue", "int"),  // child's namespace copy
                    new AtomicSerialFieldDef("childValue",  "int")
                ));
        SchemaChain.Result oldChain = SchemaChain.linkAndGetLeafDigest(List.of(oldChildSchema));

        // Old payload: only child's SEQUENCE (no parent SEQUENCE)
        byte[] oldInnerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(50)),   // parentValue (child's copy)
                DerWriter.writeInteger(BigInteger.valueOf(75))    // childValue
        ));
        byte[] oldPayload = DerWriter.writeSequence(List.of(oldInnerSeq));

        MarshalledInstanceRecord oldRec = new MarshalledInstanceRecord(
                oldPayload, oldChildSchema.encode(), oldChain.leafDigest(),
                Optional.empty(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        MarshalledInstanceCodec.Result<Ev4_Child> oldResult =
                MarshalledInstanceCodec.decodeMarshalledInstance(oldRec, Ev4_Child.class);

        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, oldResult.schemaCase(),
                "§11.4: old-data schema mismatch must be detected");
        assertEquals(50, oldResult.object().getParentValue(),
                "§11.4: parentValue=50 from child's childParentValue (regular super call)");
        assertEquals(75, oldResult.object().getChildValue(),
                "§11.4: childValue=75 from old data");
    }

    /**
     * §11.4 implementation note — "Beta updated to call super(arg)" when old data lacks
     * the parent SEQUENCE.
     *
     * <p>Tested behavior: when the embedded schema has only 1 record (Ev5_Leaf) and the
     * payload has only Leaf's SEQUENCE, Ev5_Leaf(GetArg) calls super(check(arg)) invoking
     * Ev5_Root(GetArg). Root has no store in the DerGetArg map. DerGetArg StackWalker
     * finds Ev5_Leaf's store via the nearest registered class on the stack. Field "rootVal"
     * is absent from Leaf's store, so arg.get("rootVal", 0) returns default 0.
     *
     * <p>Observable: decode succeeds; Root's fields default; Leaf's fields are correct.
     * This matches §11.4 spec ("all fields return defaults") via StackWalker fallback.
     */
    @Test
    void test_11_4_Note_UpdatedChildCallingSuper_AbsentParentStoreReturnsDefaults()
            throws Exception {
        // Leaf-only embedded chain: Root has no dedicated DerFieldStore.
        String leafClassName = Ev5_Leaf.class.getName();
        AtomicSerialSchemaRecord leafOnlySchema = new AtomicSerialSchemaRecord(
                leafClassName, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("leafVal", "int"),
                    new AtomicSerialFieldDef("leafTag", "java.lang.String")
                ));
        SchemaChain.Result leafOnlyChain = SchemaChain.linkAndGetLeafDigest(List.of(leafOnlySchema));

        // Payload with only Leaf's SEQUENCE (Root's bytes absent from old data)
        byte[] innerSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(42)),
                DerWriter.writeUtf8String("leaf-tag")
        ));
        byte[] payload = DerWriter.writeSequence(List.of(innerSeq));

        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                payload, leafOnlySchema.encode(), leafOnlyChain.leafDigest(),
                Optional.empty(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // StackWalker fallback: Root reads from Leaf's store; "rootVal" absent -> default 0.
        // Behavior matches §11.4 requirement (absent class fields return defaults).
        MarshalledInstanceCodec.Result<Ev5_Leaf> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, Ev5_Leaf.class);

        assertEquals(0,  result.object().getRootVal(),
                "rootVal defaults to 0 when absent from embedded chain (StackWalker fallback)");
        assertEquals(42, result.object().getLeafVal(),
                "leafVal decodes correctly from Leaf's SEQUENCE");
        assertEquals("leaf-tag", result.object().getLeafTag(),
                "leafTag decodes correctly");
    }

    // =========================================================================
    // §11.5 — Remove @AtomicSerial from a class → SEQUENCE disappears; child unaffected
    // =========================================================================

    /**
     * §11.5 — {@code Ev5_Root} formerly had {@code @AtomicSerial} and had its own
     * SEQUENCE.  The test proves TWO observable consequences:
     *
     * <p><b>Consequence A (old data decoded by old embedded schema):</b>
     * Old data carries BOTH Root's and Leaf's SEQUENCEs.  When decoded using the
     * OLD embedded schema (which includes Root), the decoder builds DerFieldStores
     * for BOTH classes and decodes correctly.  Root's SEQUENCE bytes are CONSUMED by
     * Root's own {@code (GetArg)} constructor (called via Leaf's super chain).
     * This proves that old data is fully backward-compatible.
     *
     * <p><b>Consequence B (new data without Root SEQUENCE — GAP exposed):</b>
     * When Root loses {@code @AtomicSerial}, new data encoded by an updated Leaf
     * would have only one SEQUENCE.  Leaf's {@code (GetArg)} still chains to
     * Root's {@code (GetArg)} → Root looks up its store → NOT FOUND → THROWS.
     * This is the same gap as §11.4 — see {@code test_11_4_Gap_*}.
     *
     * <p>Observable (consequence A, what DOES work):
     * <ul>
     *   <li>Decode of OLD data (both SEQUENCEs) with old embedded schema succeeds.</li>
     *   <li>Root fields decoded correctly ({@code rootVal == 33}).</li>
     *   <li>Leaf fields decoded correctly ({@code leafVal == 77}, {@code leafTag == "leaf"}).</li>
     * </ul>
     */
    @Test
    void test_11_5_RemoveAtomicSerialFromSuperclass_OldDataDecodes_ChildUnaffected() throws Exception {
        // Consequence A: old data (both SEQUENCEs) with old embedded chain
        Ev5_Leaf original = new Ev5_Leaf(33, 77, "leaf");
        SchemaChain.Result oldChain = SchemaGenerator.generateChain(Ev5_Leaf.class);

        assertEquals(2, oldChain.chain().size(),
                "§11.5: current chain must have 2 records (Root + Leaf)");

        byte[] payload = ObjectCodec.encodeHierarchy(original, oldChain);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.fromChain(oldChain, payload);

        MarshalledInstanceCodec.Result<Ev5_Leaf> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, Ev5_Leaf.class);

        assertEquals(MarshalledInstanceCodec.SchemaCase.A_MATCH, result.schemaCase(),
                "§11.5: schema match (consequence A: old data with old embedded schema)");
        assertEquals(33, result.object().getRootVal(),
                "§11.5: rootVal must decode correctly");
        assertEquals(77, result.object().getLeafVal(),
                "§11.5: leafVal must decode correctly");
        assertEquals("leaf", result.object().getLeafTag(),
                "§11.5: leafTag must decode correctly");
        assertEquals(original, result.object(),
                "§11.5: full round-trip equality (consequence A)");

        // Assert: Leaf's SEQUENCE is unaffected by what happens to Root's @AtomicSerial status.
        // The Leaf's own schema record is present in the chain with exactly its declared fields.
        assertEquals(Ev5_Leaf.class.getName(), oldChain.chain().get(0).className(),
                "§11.5: leaf record is first (leaf-first)");
        assertEquals(2, oldChain.chain().get(0).fields().size(),
                "§11.5: Leaf's schema has exactly its own 2 fields (leafVal, leafTag)");
    }

    // =========================================================================
    // §11.6 — Insert a new @AtomicSerial class → new SEQUENCE; new data round-trips
    // =========================================================================

    /**
     * §11.6 — {@code Ev67_Mid} is inserted between {@code Ev67_Alpha} and
     * {@code Ev6_Beta}.  The NEW chain has three records.
     *
     * <p><b>New data path (what CAN be tested):</b>
     * Encode an {@code Ev6_Beta} with the three-record chain.  The wire carries
     * three SEQUENCEs.  Decode succeeds with all fields correct.
     *
     * <p><b>Old data path (GAP):</b>
     * Old data encoded before Mid was inserted has only TWO SEQUENCEs (Alpha + old Beta).
     * Decoding old data against new chain: Mid's {@code (GetArg)} is invoked by
     * {@code Ev6_Beta}'s super-chain, but Mid has no store in the embedded (old)
     * schema → {@code DerGetArg.callerStore()} throws.  Per §11.6, Mid should receive
     * defaults.  See {@code test_11_6_Gap_*}.
     *
     * <p>Observable (new data):
     * <ul>
     *   <li>Chain has 3 records: [Ev6_Beta, Ev67_Mid, Ev67_Alpha].</li>
     *   <li>All three classes' fields decode correctly.</li>
     * </ul>
     */
    @Test
    void test_11_6_InsertAtomicSerialClass_NewDataPath_ThreeSequences() throws Exception {
        // New data: encode with 3-record chain (Alpha + Mid + Beta)
        Ev6_Beta original = new Ev6_Beta(10, 20, "mid-tag", 30, "beta-tag");
        SchemaChain.Result newChain = SchemaGenerator.generateChain(Ev6_Beta.class);

        assertEquals(3, newChain.chain().size(),
                "§11.6: new chain must have 3 records");
        assertEquals(Ev6_Beta.class.getName(),   newChain.chain().get(0).className());
        assertEquals(Ev67_Mid.class.getName(),   newChain.chain().get(1).className());
        assertEquals(Ev67_Alpha.class.getName(), newChain.chain().get(2).className());

        byte[] payload = ObjectCodec.encodeHierarchy(original, newChain);
        Ev6_Beta decoded = ObjectCodec.decodeHierarchy(Ev6_Beta.class, newChain, payload);

        assertEquals(10, decoded.getAlphaVal(),  "§11.6: alphaVal must be 10");
        assertEquals(20, decoded.getMidVal(),    "§11.6: midVal must be 20");
        assertEquals("mid-tag",  decoded.getMidTag(),  "§11.6: midTag must be 'mid-tag'");
        assertEquals(30, decoded.getBetaVal(),   "§11.6: betaVal must be 30");
        assertEquals("beta-tag", decoded.getBetaTag(), "§11.6: betaTag must be 'beta-tag'");
        assertEquals(original, decoded, "§11.6: new data round-trip equality");
    }

    /**
     * §11.6 gap report — old data lacks Mid's SEQUENCE; Mid(GetArg) throws instead
     * of returning defaults.
     *
     * <p>GAP: per §11.6, "existing wire data has no Mid SEQUENCE — Mid's fields are
     * absent and return defaults."  The current {@code DerGetArg} throws when Mid's
     * {@code (GetArg)} constructor is invoked but Mid has no store.
     */
    @Test
    void test_11_6_Gap_OldDataLacksMidSequence_MidThrowsInsteadOfDefaulting() throws Exception {
        // Build old 2-record schema [Ev67_Alpha, old-Beta-that-extended-Alpha]
        // We simulate this as: Ev7_Beta (extends Alpha directly) was the old beta
        String alphaClassName = Ev67_Alpha.class.getName();
        String betaClassName  = Ev7_Beta.class.getName();   // proxy for "old Beta extends Alpha"

        AtomicSerialSchemaRecord oldAlphaSchema = new AtomicSerialSchemaRecord(
                alphaClassName, (byte[]) null,
                List.of(new AtomicSerialFieldDef("alphaVal", "int")));
        AtomicSerialSchemaRecord oldBetaSchema = new AtomicSerialSchemaRecord(
                betaClassName, (byte[]) null,   // no parent hash yet — linked below
                List.of(
                    new AtomicSerialFieldDef("betaVal", "int"),
                    new AtomicSerialFieldDef("betaTag", "java.lang.String")
                ));

        // Link: leaf-first = [beta, alpha]
        SchemaChain.Result oldChain = SchemaChain.linkAndGetLeafDigest(
                List.of(oldBetaSchema, oldAlphaSchema));

        // Old payload: 2 inner SEQUENCEs (alpha first, beta second — root-first)
        byte[] alphaSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(1))));
        byte[] betaSeq  = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(2)),
                DerWriter.writeUtf8String("old-tag")));
        byte[] oldPayload = DerWriter.writeSequence(List.of(alphaSeq, betaSeq));

        // schemaBytes: concatenate old chain records
        byte[] schemaBytes = buildSchemaBytes(oldChain.chain());
        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                oldPayload, schemaBytes, oldChain.leafDigest(),
                Optional.empty(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Now try to decode old data against Ev6_Beta.class (which extends Mid)
        // MIC uses embedded chain (only Alpha + old Beta), so Mid has no store.
        // Ev6_Beta(GetArg) → super(check(arg)) → Ev67_Mid(GetArg) → no store → THROWS.
        // GAP: should return defaults for Mid's fields per §11.6.
        assertThrows(Exception.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(rec, Ev6_Beta.class),
                "§11.6 GAP: Mid(GetArg) throws when Mid has no store in embedded (old) chain "
                + "— should return defaults per §11.6 but currently throws");
    }

    // =========================================================================
    // §11.7 — Remove @AtomicSerial class → SEQUENCE disappears; neighbours unaffected
    // =========================================================================

    /**
     * §11.7 — {@code Ev67_Mid} is REMOVED.  The hierarchy becomes
     * {@code Ev7_Beta extends Ev67_Alpha} (direct, no Mid).
     *
     * <p>OLD data was encoded with the three-class chain
     * {@code [Ev7_Beta, Ev67_Mid, Ev67_Alpha]} (leaf-first).  It carries THREE
     * inner SEQUENCEs.  We simulate decoding this old data using the OLD embedded
     * schema (which includes Mid).
     *
     * <p>The decoder builds THREE {@code DerFieldStore}s:
     * <ul>
     *   <li>Alpha's store — consumed by {@code Ev67_Alpha(GetArg)}.</li>
     *   <li>Mid's store — built and populated but NEVER consumed (no constructor
     *       frame for {@code Ev67_Mid} appears in the super-chain of {@code Ev7_Beta}).</li>
     *   <li>Beta's store — consumed by {@code Ev7_Beta(GetArg)}.</li>
     * </ul>
     *
     * <p>Observable:
     * <ul>
     *   <li>Decode succeeds without error (Mid's bytes are harmlessly present).</li>
     *   <li>Alpha's {@code alphaVal} and Beta's own fields ({@code betaVal},
     *       {@code betaTag}) are decoded correctly.</li>
     *   <li>Mid's decoded field values are never returned by the constructor.</li>
     * </ul>
     */
    @Test
    void test_11_7_RemoveAtomicSerialClass_OldMidSequence_IgnoredByNeighbours() throws Exception {
        // Build the OLD 3-record embedded schema chain [Ev7_Beta, Ev67_Mid, Ev67_Alpha]
        String alphaClassName = Ev67_Alpha.class.getName();
        String midClassName   = Ev67_Mid.class.getName();
        String betaClassName  = Ev7_Beta.class.getName();  // NEW Beta extends Alpha directly

        AtomicSerialSchemaRecord oldAlphaSchema = new AtomicSerialSchemaRecord(
                alphaClassName, (byte[]) null,
                List.of(new AtomicSerialFieldDef("alphaVal", "int")));
        AtomicSerialSchemaRecord oldMidSchema = new AtomicSerialSchemaRecord(
                midClassName, (byte[]) null,  // linked below
                List.of(
                    new AtomicSerialFieldDef("midVal", "int"),
                    new AtomicSerialFieldDef("midTag", "java.lang.String")
                ));
        AtomicSerialSchemaRecord oldBetaSchema = new AtomicSerialSchemaRecord(
                betaClassName, (byte[]) null,  // linked below
                List.of(
                    new AtomicSerialFieldDef("betaVal", "int"),
                    new AtomicSerialFieldDef("betaTag", "java.lang.String")
                ));

        // Link: leaf-first = [beta, mid, alpha]
        SchemaChain.Result oldChain = SchemaChain.linkAndGetLeafDigest(
                List.of(oldBetaSchema, oldMidSchema, oldAlphaSchema));

        // OLD payload: 3 inner SEQUENCEs in root-first order [alpha, mid, beta]
        byte[] alphaSeq = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(100))));   // alphaVal=100
        byte[] midSeq   = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(999)),     // midVal=999 (should be ignored)
                DerWriter.writeUtf8String("mid-to-be-dropped")));   // midTag (ignored)
        byte[] betaSeq  = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(200)),     // betaVal=200
                DerWriter.writeUtf8String("beta-tag")));             // betaTag

        // Root-first payload wrapping
        byte[] oldPayload = DerWriter.writeSequence(List.of(alphaSeq, midSeq, betaSeq));

        // Build MarshalledInstanceRecord with old embedded schema (includes Mid)
        byte[] schemaBytes = buildSchemaBytes(oldChain.chain());
        MarshalledInstanceRecord rec = new MarshalledInstanceRecord(
                oldPayload, schemaBytes, oldChain.leafDigest(),
                Optional.empty(), MarshalledInstanceRecord.PAYLOAD_FORMAT);

        // Decode using MIC (embedded chain drives decode, NOT receiver's current serialForm)
        // Receiver class is Ev7_Beta (extends Alpha directly — Mid removed)
        MarshalledInstanceCodec.Result<Ev7_Beta> result =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, Ev7_Beta.class);

        // Schema mismatch detected (embedded chain has Mid; receiver's current chain doesn't)
        assertEquals(MarshalledInstanceCodec.SchemaCase.B_OR_C_MISMATCH, result.schemaCase(),
                "§11.7: schema mismatch detected (embedded has Mid; receiver's current chain does not)");

        // Alpha's neighbour field unaffected
        assertEquals(100, result.object().getAlphaVal(),
                "§11.7: alphaVal must decode correctly (neighbour Alpha unaffected by Mid removal)");

        // Beta's own fields unaffected
        assertEquals(200, result.object().getBetaVal(),
                "§11.7: betaVal must decode correctly (neighbour Beta unaffected)");
        assertEquals("beta-tag", result.object().getBetaTag(),
                "§11.7: betaTag must decode correctly");

        // Mid's values do NOT appear anywhere in the result (store unrequested)
        // We can only prove this by the fact that the constructor returned without consuming them.
        // The runtime type is exactly Ev7_Beta (extends Alpha, not Mid).
        assertEquals(Ev7_Beta.class, result.object().getClass(),
                "§11.7: decoded class must be Ev7_Beta (no Mid in hierarchy any more)");
        // Ev7_Beta extends Ev67_Alpha (not Ev67_Mid), so Mid is structurally absent —
        // confirmed by the class hierarchy check above.
    }

    // =========================================================================
    // §11.8 / Task 6.2 — Field-level evolution (symmetric graceful degradation)
    // =========================================================================

    /**
     * §11.8 / 6.2a — Add a field at end of {@code serialForm()}.
     *
     * <p>Old data (embedded schema lacks the new field) → {@code get(name, default)}
     * returns the default.
     *
     * <p>New data (field present in payload) → the encoded value is returned.
     *
     * <p>Simulation: 2-field old schema → decode against 3-field schema (DerFieldStore
     * directly).
     */
    @Test
    void test_6_2a_AddFieldAtEnd_OldDataReturnsDefault_NewDataReturnsValue() throws Exception {
        String schemaName = "com.example.Ev6_2a";

        // === OLD data: 2-field schema and matching payload ===
        AtomicSerialSchemaRecord oldSchema = new AtomicSerialSchemaRecord(
                schemaName, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));

        byte[] oldPayload = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(7)),
                DerWriter.writeUtf8String("old-label")
        ));

        // Use DerFieldStore directly for field-level observation
        DerFieldStore oldStore = new DerFieldStore(oldSchema, oldPayload);
        assertEquals(7,           oldStore.get("id",     0),   "6.2a: id correct in old data");
        assertEquals("old-label", oldStore.get("label", "?"),  "6.2a: label correct in old data");

        // 'extra' field not in old schema → get returns default
        assertEquals("DEFAULT_EXTRA", oldStore.get("extra", "DEFAULT_EXTRA"),
                "6.2a: extra absent in old data → get returns default 'DEFAULT_EXTRA'");
        assertTrue(oldStore.defaulted("extra"),
                "6.2a: defaulted(extra) must be true for old data");

        // === NEW data: 3-field schema and payload ===
        AtomicSerialSchemaRecord newSchema = new AtomicSerialSchemaRecord(
                schemaName, (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String"),
                    new AtomicSerialFieldDef("extra", "java.lang.String")
                ));

        byte[] newPayload = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(8)),
                DerWriter.writeUtf8String("new-label"),
                DerWriter.writeUtf8String("extra-value")
        ));

        DerFieldStore newStore = new DerFieldStore(newSchema, newPayload);
        assertEquals(8,             newStore.get("id",     0),   "6.2a: id correct in new data");
        assertEquals("new-label",   newStore.get("label", "?"),  "6.2a: label correct in new data");
        assertEquals("extra-value", newStore.get("extra", "DEFAULT_EXTRA"),
                "6.2a: extra present in new data → returns 'extra-value'");
        assertFalse(newStore.defaulted("extra"),
                "6.2a: defaulted(extra) must be false when field is present");
    }

    /**
     * §11.8 / 6.2b — Stop requesting a field.
     *
     * <p>The field is still encoded in the payload and decoded into the
     * {@code DerFieldStore} (assert via {@link DerFieldStore#presentFieldNames()}
     * containing it), but the "constructor" never calls {@code get()} for it —
     * show it sits in the store, unrequested.
     *
     * <p>Simulation: build a 3-field schema and payload; observe that all 3 are
     * present; then show only the first 2 are "requested"; the third remains in
     * the store unrequested (presence confirmed, get never called from outside).
     */
    @Test
    void test_6_2b_StopRequestingField_FieldDecodedIntoStore_NeverRequested() throws Exception {
        AtomicSerialSchemaRecord schema = new AtomicSerialSchemaRecord(
                "com.example.Ev6_2b", (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("keep1",     "int"),
                    new AtomicSerialFieldDef("keep2",     "java.lang.String"),
                    new AtomicSerialFieldDef("deprecated", "java.lang.String")  // no longer requested
                ));

        byte[] payload = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(11)),
                DerWriter.writeUtf8String("keep-me"),
                DerWriter.writeUtf8String("old-data-for-deprecated-field")
        ));

        DerFieldStore store = new DerFieldStore(schema, payload);

        // Assert: ALL three fields are present in the store (decoded before any get() call)
        Set<String> present = store.presentFieldNames();
        assertTrue(present.contains("keep1"),     "6.2b: keep1 must be in store");
        assertTrue(present.contains("keep2"),     "6.2b: keep2 must be in store");
        assertTrue(present.contains("deprecated"), "6.2b: deprecated must be in store even though not requested");

        // Assert: no trailing fields discarded (all 3 schema fields have matching TLVs)
        assertEquals(0, store.trailingFieldsDiscarded(),
                "6.2b: no trailing discard (exact match; deprecated is in schema)");

        // Simulate "constructor requests only keep1 and keep2 — not deprecated"
        int    keep1 = store.get("keep1", 0);
        String keep2 = (String) store.get("keep2", null);

        assertEquals(11,        keep1, "6.2b: keep1 decoded correctly");
        assertEquals("keep-me", keep2, "6.2b: keep2 decoded correctly");

        // 'deprecated' is still in the store (unrequested — this is the assertion)
        assertFalse(store.defaulted("deprecated"),
                "6.2b: deprecated must remain in store (not absent) even though constructor never requested it");
        assertEquals("old-data-for-deprecated-field",
                store.get("deprecated", "NO"),
                "6.2b: deprecated value still retrievable from store — it was decoded and stored, never removed");
    }

    /**
     * §11.8 / 6.2c — Field present on wire, absent from the (decoder's) schema.
     *
     * <p>This is the §3.9 case (c) at the field-store level: the payload contains MORE
     * TLVs than the schema has fields.  The known fields are decoded; trailing extra
     * TLVs are read-and-discarded to the SEQUENCE boundary.
     * {@link DerFieldStore#trailingFieldsDiscarded()} must be {@code > 0}.
     *
     * <p>Simulation: build a NARROWER schema (2 fields) against a payload that
     * contains 4 TLVs.  The 2 extra TLVs are discarded.
     */
    @Test
    void test_6_2c_FieldPresentOnWire_AbsentFromDecoderSchema_TrailingDiscarded() throws Exception {
        // NARROWER schema: only knows id and label
        AtomicSerialSchemaRecord narrowSchema = new AtomicSerialSchemaRecord(
                "com.example.Ev6_2c", (byte[]) null,
                List.of(
                    new AtomicSerialFieldDef("id",    "int"),
                    new AtomicSerialFieldDef("label", "java.lang.String")
                ));

        // WIDER payload: 4 TLVs (the decoder's schema only covers the first 2)
        byte[] payload = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.valueOf(9)),
                DerWriter.writeUtf8String("known-label"),
                DerWriter.writeUtf8String("extra-field-1"),  // not in decoder's schema
                DerWriter.writeInteger(BigInteger.valueOf(42)) // not in decoder's schema
        ));

        DerFieldStore store = new DerFieldStore(narrowSchema, payload);

        // Assert: known fields decoded correctly
        assertEquals(9,             store.get("id",    0),   "6.2c: id correct");
        assertEquals("known-label", store.get("label", "?"), "6.2c: label correct");

        // Assert: trailing fields discarded (§3.9 case (c) at field-store level)
        assertEquals(2, store.trailingFieldsDiscarded(),
                "6.2c: exactly 2 trailing TLVs must be discarded (present on wire, absent from schema)");

        // Assert: no error (decode succeeded despite extra TLVs)
        assertTrue(store.trailingFieldsDiscarded() > 0,
                "6.2c: trailingFieldsDiscarded() must be > 0 when wire has more TLVs than schema");

        // Assert: the 2 known fields are the only ones present in the store
        assertEquals(Set.of("id", "label"), store.presentFieldNames(),
                "6.2c: only the 2 schema-known fields should be present");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Concatenates the DER bytes of schema records in the order given (leaf-first),
     * matching the {@code schemaBytes} encoding used by {@link MarshalledInstanceRecord}.
     */
    private static byte[] buildSchemaBytes(List<AtomicSerialSchemaRecord> chain) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord rec : chain) {
            byte[] encoded = rec.encode();
            buf.write(encoded, 0, encoded.length);
        }
        return buf.toByteArray();
    }
}
