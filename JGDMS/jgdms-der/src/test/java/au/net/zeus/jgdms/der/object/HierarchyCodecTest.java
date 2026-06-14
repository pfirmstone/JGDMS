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

import au.net.zeus.jgdms.der.object.fixtures.Alpha;
import au.net.zeus.jgdms.der.object.fixtures.Beta;
import au.net.zeus.jgdms.der.object.fixtures.Gamma;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4.3 acceptance tests: {@code @AtomicSerial} hierarchy encode/decode
 * with per-class namespace isolation (JGDMS-STD-006 S3.9 / S3.10).
 *
 * <h2>What each test proves</h2>
 * <ul>
 *   <li><b>4.3.1</b> -- Two-level ({@code Beta extends Alpha}) round-trips with
 *       full field equality at both levels.</li>
 *   <li><b>4.3.2</b> -- Three-level ({@code Gamma extends Beta extends Alpha})
 *       round-trips with full field equality at all three levels.</li>
 *   <li><b>4.3.3</b> -- Namespace collision: {@code Alpha.x} and {@code Beta.x}
 *       carry different values and BOTH survive the round-trip unchanged
 *       (no collision / no overwrite). Proves that field name "x" is
 *       independent in the two SEQUENCEs.</li>
 *   <li><b>4.3.4</b> -- Visibility isolation: Alpha's constructor observes
 *       "ALPHA_DEFAULT" when it reads "betaOnly" via GetArg, proving that
 *       Alpha's stack frame can only see Alpha's DerFieldStore -- Beta's
 *       "betaOnly" field is completely invisible from Alpha's frame.</li>
 *   <li><b>4.3.5</b> -- Schema chain: {@link SchemaGenerator#generateChain} produces
 *       a correctly-linked Merkle chain with parentSchemaHash set for child
 *       records and absent for the root.</li>
 *   <li><b>4.3.6</b> -- Alpha-only round-trip (single @AtomicSerial class in
 *       hierarchy, no parent) via generateChain + encodeHierarchy/decodeHierarchy.</li>
 * </ul>
 */
class HierarchyCodecTest {

    // =========================================================================
    // 4.3.1 -- Two-level round-trip
    // =========================================================================

    /**
     * 4.3.1 -- {@code Beta extends Alpha}, both {@code @AtomicSerial}.
     * Full round-trip with field equality at both levels.
     */
    @Test
    void test_4_3_1_TwoLevel_Beta_RoundTrip() throws Exception {
        // Alpha.x = 10, Beta.x = 20 -- explicitly different values for the same field name
        Beta original = new Beta(10, "alpha-label", 20, "beta-secret");

        SchemaChain.Result chain = SchemaGenerator.generateChain(Beta.class);
        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Beta decoded = ObjectCodec.decodeHierarchy(Beta.class, chain, der);

        assertEquals(original, decoded, "Decoded Beta must equal original");

        // Verify individual fields
        assertEquals(10, decoded.getAlphaX(),   "Alpha.x must be 10");
        assertEquals("alpha-label", decoded.getAlphaLabel(), "Alpha.alphaLabel mismatch");
        assertEquals(20, decoded.getBetaX(),    "Beta.x must be 20");
        assertEquals("beta-secret", decoded.getBetaOnly(), "Beta.betaOnly mismatch");
    }

    // =========================================================================
    // 4.3.2 -- Three-level round-trip
    // =========================================================================

    /**
     * 4.3.2 -- {@code Gamma extends Beta extends Alpha}, all {@code @AtomicSerial}.
     * Full round-trip with field equality at all three levels.
     */
    @Test
    void test_4_3_2_ThreeLevel_Gamma_RoundTrip() throws Exception {
        Gamma original = new Gamma(
                100, "alpha-root",       // Alpha's fields
                200, "beta-middle",      // Beta's fields
                9999L, "gamma-leaf"      // Gamma's fields
        );

        SchemaChain.Result chain = SchemaGenerator.generateChain(Gamma.class);
        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Gamma decoded = ObjectCodec.decodeHierarchy(Gamma.class, chain, der);

        assertEquals(original, decoded, "Decoded Gamma must equal original");

        // Verify all fields
        assertEquals(100, decoded.getAlphaX(),        "Alpha.x must be 100");
        assertEquals("alpha-root", decoded.getAlphaLabel(), "Alpha.alphaLabel mismatch");
        assertEquals(200, decoded.getBetaX(),          "Beta.x must be 200");
        assertEquals("beta-middle", decoded.getBetaOnly(), "Beta.betaOnly mismatch");
        assertEquals(9999L, decoded.getGammaValue(),   "Gamma.gammaValue must be 9999");
        assertEquals("gamma-leaf", decoded.getGammaTag(), "Gamma.gammaTag mismatch");
    }

    // =========================================================================
    // 4.3.3 -- Namespace collision: Alpha.x and Beta.x are independent
    // =========================================================================

    /**
     * 4.3.3 -- Namespace collision test: {@code Alpha.x} and {@code Beta.x} share
     * the same field name "x" but live in separate SEQUENCEs and carry independent
     * values.
     *
     * <p><b>How this proves per-class isolation:</b>
     * Alpha.x is encoded into Alpha's private SEQUENCE; Beta.x is encoded into
     * Beta's private SEQUENCE. On decode, Alpha's DerFieldStore is populated from
     * Alpha's SEQUENCE, and Beta's DerFieldStore is populated from Beta's SEQUENCE.
     * StackWalker dispatch in {@link DerGetArg} ensures that {@code arg.get("x", 0)}
     * inside {@code Alpha.<init>} reads from Alpha's store (returns Alpha.x = 1111),
     * and {@code arg.get("x", 0)} inside {@code Beta.<init>} reads from Beta's store
     * (returns Beta.x = 2222). If dispatch were flat (one namespace), one would
     * overwrite the other and the decoded values would collide.
     */
    @Test
    void test_4_3_3_NamespaceCollision_AlphaX_and_BetaX_are_Independent() throws Exception {
        // Deliberately different values for "x" at both levels
        final int alphaXValue = 1111;
        final int betaXValue  = 2222;
        assertNotEquals(alphaXValue, betaXValue, "Pre-condition: values must differ");

        Beta original = new Beta(alphaXValue, "label-a", betaXValue, "only-beta");

        SchemaChain.Result chain = SchemaGenerator.generateChain(Beta.class);
        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Beta decoded = ObjectCodec.decodeHierarchy(Beta.class, chain, der);

        // Both "x" values must survive the round-trip independently
        assertEquals(alphaXValue, decoded.getAlphaX(),
                "Alpha.x must be " + alphaXValue + " after round-trip (not overwritten by Beta.x)");
        assertEquals(betaXValue, decoded.getBetaX(),
                "Beta.x must be " + betaXValue + " after round-trip (not overwritten by Alpha.x)");

        // Belt-and-suspenders: verify the values are still different (no merge/collision)
        assertNotEquals(decoded.getAlphaX(), decoded.getBetaX(),
                "Alpha.x and Beta.x must remain distinct after round-trip");
    }

    // =========================================================================
    // 4.3.4 -- Visibility isolation: Alpha cannot see Beta's namespace
    // =========================================================================

    /**
     * 4.3.4 -- Visibility isolation: proves that from Alpha's constructor frame,
     * Beta's "betaOnly" field is invisible.
     *
     * <p><b>How this proves per-class isolation:</b>
     * Alpha's deserialization constructor (see {@link Alpha#Alpha(org.apache.river.api.io.AtomicSerial.GetArg)})
     * explicitly calls {@code arg.get("betaOnly", "ALPHA_DEFAULT")}. With correct
     * StackWalker dispatch, this call is inside {@code Alpha.<init>}, so the walker
     * finds {@code Alpha} as the first registered class on the stack and routes the
     * call to Alpha's {@link au.net.zeus.jgdms.der.getarg.DerFieldStore}. "betaOnly"
     * is not in Alpha's schema, so the default {@code "ALPHA_DEFAULT"} is returned
     * and stored in {@link Alpha#betaOnlySeenByAlpha}.
     *
     * <p>If dispatch were wrong (e.g. flat or Beta-biased), Alpha would see Beta's
     * DerFieldStore and get the actual value {@code "beta-visible"}, not the default.
     * The assertion below catches that failure.
     */
    @Test
    void test_4_3_4_VisibilityIsolation_AlphaCannotSeeBetaNamespace() throws Exception {
        final String betaOnlyValue = "beta-visible";
        Beta original = new Beta(42, "alpha-label", 99, betaOnlyValue);

        SchemaChain.Result chain = SchemaGenerator.generateChain(Beta.class);
        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Beta decoded = ObjectCodec.decodeHierarchy(Beta.class, chain, der);

        // Beta can read its own betaOnly field (proves Beta's store has it)
        assertEquals(betaOnlyValue, decoded.getBetaOnly(),
                "Beta must be able to read its own betaOnly field");

        // Alpha's attempt to read "betaOnly" from its own GetArg frame must return the default,
        // NOT the value from Beta's namespace.
        assertEquals("ALPHA_DEFAULT", decoded.getBetaOnlySeenByAlpha(),
                "Alpha.betaOnlySeenByAlpha must be 'ALPHA_DEFAULT' -- "
                + "Alpha must NOT see Beta's 'betaOnly' field through GetArg dispatch. "
                + "If this fails, StackWalker dispatch is routing Alpha's get() calls "
                + "to Beta's DerFieldStore instead of Alpha's.");

        // Confirm Alpha's own x is correct (not contaminated)
        assertEquals(42, decoded.getAlphaX(),
                "Alpha.x must not be contaminated by Beta's namespace");
    }

    // =========================================================================
    // 4.3.5 -- Schema chain structure
    // =========================================================================

    /**
     * 4.3.5 -- {@link SchemaGenerator#generateChain} produces the correct Merkle chain:
     * root has no parentSchemaHash; child records have parentSchemaHash set.
     */
    @Test
    void test_4_3_5_SchemaChain_CorrectStructure() throws Exception {
        SchemaChain.Result chainResult = SchemaGenerator.generateChain(Beta.class);
        List<au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord> chain = chainResult.chain();

        // Chain is leaf-first, root-last
        assertEquals(2, chain.size(), "Beta hierarchy should have 2 schema records");

        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord leafRecord = chain.get(0);
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord rootRecord = chain.get(1);

        assertEquals(Beta.class.getName(), leafRecord.className(),
                "First record must be Beta (leaf)");
        assertEquals(Alpha.class.getName(), rootRecord.className(),
                "Second record must be Alpha (root)");

        // Root has no parentSchemaHash (parent is Object)
        assertTrue(rootRecord.parentSchemaHash().isEmpty(),
                "Alpha (root) must have no parentSchemaHash");

        // Leaf's parentSchemaHash must equal root's digest
        assertTrue(leafRecord.parentSchemaHash().isPresent(),
                "Beta (leaf) must have a parentSchemaHash");
        assertArrayEquals(rootRecord.schemaDigest(),
                leafRecord.parentSchemaHash().get(),
                "Beta's parentSchemaHash must equal Alpha's schemaDigest");

        // leafDigest must equal the leaf record's computed digest
        assertArrayEquals(chainResult.leafDigest(), leafRecord.schemaDigest(),
                "leafDigest must match the leaf record's own digest");
    }

    /**
     * 4.3.5b -- Three-level chain structure.
     */
    @Test
    void test_4_3_5b_SchemaChain_ThreeLevel() throws Exception {
        SchemaChain.Result chainResult = SchemaGenerator.generateChain(Gamma.class);
        List<au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord> chain = chainResult.chain();

        assertEquals(3, chain.size(), "Gamma hierarchy should have 3 schema records");

        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord gammaRec = chain.get(0);
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord betaRec  = chain.get(1);
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord alphaRec = chain.get(2);

        assertEquals(Gamma.class.getName(), gammaRec.className());
        assertEquals(Beta.class.getName(),  betaRec.className());
        assertEquals(Alpha.class.getName(), alphaRec.className());

        // Alpha: no parent hash
        assertTrue(alphaRec.parentSchemaHash().isEmpty());
        // Beta's parentSchemaHash = Alpha's digest
        assertArrayEquals(alphaRec.schemaDigest(), betaRec.parentSchemaHash().get());
        // Gamma's parentSchemaHash = Beta's digest
        assertArrayEquals(betaRec.schemaDigest(), gammaRec.parentSchemaHash().get());
    }

    // =========================================================================
    // 4.3.6 -- Single @AtomicSerial root via generateChain + hierarchy codec
    // =========================================================================

    /**
     * 4.3.6 -- Alpha alone (no superclass @AtomicSerial) round-trips through
     * encodeHierarchy / decodeHierarchy (chain has exactly one record).
     */
    @Test
    void test_4_3_6_SingleClass_Alpha_ViaHierarchyCodec() throws Exception {
        Alpha original = new Alpha(77, "standalone");

        SchemaChain.Result chain = SchemaGenerator.generateChain(Alpha.class);
        assertEquals(1, chain.chain().size(), "Alpha-only chain should have 1 record");

        byte[] der = ObjectCodec.encodeHierarchy(original, chain);
        Alpha decoded = ObjectCodec.decodeHierarchy(Alpha.class, chain, der);

        assertEquals(original, decoded, "Alpha round-trip must produce equal object");
        assertEquals(77, decoded.getX(), "Alpha.x must be 77");
        assertEquals("standalone", decoded.getAlphaLabel());
        assertEquals("ALPHA_DEFAULT", decoded.getBetaOnlySeenByAlpha(),
                "betaOnlySeenByAlpha must be ALPHA_DEFAULT (no Beta in chain)");
    }

    // =========================================================================
    // 4.3.7 -- Chain field schema content verification
    // =========================================================================

    /**
     * 4.3.7 -- Verifies that each schema record in the chain contains only the
     * fields declared by the respective class, not inherited fields.
     */
    @Test
    void test_4_3_7_SchemaChain_EachRecordContainsOnlyOwnFields() throws Exception {
        SchemaChain.Result chainResult = SchemaGenerator.generateChain(Gamma.class);
        List<au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord> chain = chainResult.chain();

        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord gammaRec = chain.get(0);
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord betaRec  = chain.get(1);
        au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord alphaRec = chain.get(2);

        // Alpha's schema: x (int), alphaLabel (String)
        assertEquals(2, alphaRec.fields().size(), "Alpha schema must have 2 fields");
        assertEquals("x",          alphaRec.fields().get(0).wireName());
        assertEquals("alphaLabel", alphaRec.fields().get(1).wireName());

        // Beta's schema: x (int), betaOnly (String) -- its OWN x, not Alpha's
        assertEquals(2, betaRec.fields().size(), "Beta schema must have 2 fields");
        assertEquals("x",        betaRec.fields().get(0).wireName());
        assertEquals("betaOnly", betaRec.fields().get(1).wireName());

        // Gamma's schema: gammaValue (long), gammaTag (String)
        assertEquals(2, gammaRec.fields().size(), "Gamma schema must have 2 fields");
        assertEquals("gammaValue", gammaRec.fields().get(0).wireName());
        assertEquals("gammaTag",   gammaRec.fields().get(1).wireName());
    }
}
