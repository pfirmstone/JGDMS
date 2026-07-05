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

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.object.fixtures.CollectionRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1 depth-bound DoS regression (STD-008 §16.2, symmetric with
 * {@link NestedAtomicSerialTest} / {@link NestedDepthGuardTest} for the {@code @AtomicSerial}
 * path). The collection wire-type token ({@code list:}/{@code set:}/{@code map:} nesting) is
 * ATTACKER-CONTROLLED: JGDMS does not locally regenerate the schema, so {@code schemaDigest}
 * verification is self-consistent-only (a SHA-256 of the wire record, not bound to a locally
 * regenerated schema). A hostile peer can therefore declare a nested-collection token
 * ({@code list:list:...:X}) plus matching N-deep nested {@code SEQUENCE OF} bytes.
 *
 * <p>Before the fix, {@code ObjectCodec.decodeCollection -> decodeSetOrList/decodeMap ->
 * decodeElementValue -> decodeCollection} threaded {@code depth} UNCHANGED and never checked it,
 * so the token drove unbounded recursion into a {@link StackOverflowError} BEFORE any
 * {@code check(GetArg)} ran -- a live remote DoS (empirically confirmed at N=5000 by the reviewer).
 *
 * <p>The fix increments {@code depth} on the collection->collection (and map->collection-value)
 * recursion and rejects {@code depth > MAX_NESTING} in {@code decodeCollection}, exactly as the
 * {@code @AtomicSerial} path does in {@code decodeNested}. The over-deep token is rejected with a
 * {@code DerException} ("nesting depth ... exceeds MAX_NESTING"), surfaced through the GetArg
 * accessor as an {@link java.io.InvalidObjectException} whose cause chain carries it -- either way
 * the object is NOT constructed and the decoder NEVER {@link StackOverflowError}s.
 *
 * <p>Note the token-length cap ({@code wireType} SIZE 1..1024, STD-006 §7.8) is a SEPARATE outer
 * guard: a token past ~200 {@code list:} levels (each 5 UTF-8 bytes) is rejected at
 * {@link AtomicSerialFieldDef} construction before decode runs. The reviewer's literal 5000-deep
 * probe trips that outer cap; these tests target the DEPTH guard itself with tokens that pass the
 * length cap but exceed {@code MAX_NESTING} (see {@link #reviewer5000Probe_rejectedByTokenLengthCap}).
 */
class CollectionDepthGuardTest {

    // -------------------------------------------------------------------------
    // Helpers -- a single-"coll"-field schema carrying a given collection token, decoded via
    // the real GetArg path (DerGetArg.lookup -> ObjectCodec.decodeCollection at depth 0).
    // -------------------------------------------------------------------------

    private static AtomicSerialSchemaRecord schema(String collToken) {
        return new AtomicSerialSchemaRecord(
                CollectionRecord.class.getName(),
                (byte[]) null,
                List.of(new AtomicSerialFieldDef("coll", collToken)));
    }

    private static Object decode(byte[] classSeq, String collToken) throws Exception {
        return ObjectCodec.decode(CollectionRecord.class, schema(collToken), classSeq).getColl();
    }

    /** {@code list:} repeated {@code levels} times, innermost element type {@code int}. */
    private static String nestedListToken(int levels) {
        String tok = "int";
        for (int i = 0; i < levels; i++) {
            tok = SchemaGenerator.collectionWireType(ArrayList.class, tok); // prepends "list:"
        }
        return tok;
    }

    /** The nested {@code list:} value node (no class-SEQUENCE wrapper). */
    private static byte[] nestedListNode(int levels) {
        byte[] node = DerWriter.writeSequence(List.of()); // innermost empty list (30 00)
        for (int i = 1; i < levels; i++) {
            node = DerWriter.writeSequence(List.of(node)); // outer list with one element
        }
        return node;
    }

    /**
     * A {@code levels}-deep chain of preserve {@code SEQUENCE OF} bodies matching
     * {@link #nestedListToken(int)}, wrapped in the class SEQUENCE {@code ObjectCodec.decode}
     * expects. The innermost body is EMPTY (an empty inner list, so the {@code int} leaf is never
     * decoded); each outer level holds exactly the one inner-collection element.
     */
    private static byte[] nestedListBytes(int levels) {
        return DerWriter.writeSequence(List.of(nestedListNode(levels)));
    }

    // =========================================================================
    // list: -- the primary F1 attack vector.
    // =========================================================================

    @Test
    void nestedList_atMaxNesting_roundTrips() throws Exception {
        // Outer decodeCollection runs at depth 0, each nested level +1; the deepest level of a
        // (MAX_NESTING + 1)-token chain runs at depth MAX_NESTING -- exactly allowed.
        int levels = ObjectCodec.MAX_NESTING + 1;
        String tok = nestedListToken(levels);
        Object decoded = assertDoesNotThrow(() -> decode(nestedListBytes(levels), tok),
                "a nested list: chain whose deepest level is exactly MAX_NESTING must decode");
        assertTrue(decoded instanceof Collection<?>,
                "the outer list: field must decode to a Collection");
    }

    @Test
    void nestedList_oneLevelPastMaxNesting_isRejectedNotStackOverflow() {
        // One level deeper -> the deepest level runs at depth MAX_NESTING + 1 -> rejected.
        int levels = ObjectCodec.MAX_NESTING + 2;
        String tok = nestedListToken(levels);
        byte[] bytes = nestedListBytes(levels);
        Exception ex = assertThrows(Exception.class, () -> decode(bytes, tok),
                "a nested list: chain one level past MAX_NESTING must be rejected (fail-secure), "
                + "never a StackOverflowError");
        assertTrue(messageChainContains(ex, "MAX_NESTING"),
                "rejection must cite the nesting/depth fault; got: " + describe(ex));
    }

    @Test
    void deeplyNestedList_manyLevels_isRejectedWithNestingFault_notStackOverflow() {
        // The reviewer's over-deep probe as a bounded assertion, sized to pass the STD-006 §7.8
        // token-length cap (200 * 5 bytes = 1000 <= 1024) so the DEPTH guard -- not the length cap
        // -- is what rejects it. Far past MAX_NESTING; must fail-secure, never StackOverflow.
        final int levels = 200;
        String tok = nestedListToken(levels);
        byte[] bytes = nestedListBytes(levels);
        Exception ex = assertThrows(Exception.class, () -> decode(bytes, tok),
                "a deeply-nested list: token (past MAX_NESTING) must be rejected with a nesting "
                + "fault, not crash the decoder with StackOverflowError (F1 live remote DoS)");
        assertTrue(messageChainContains(ex, "MAX_NESTING"),
                "rejection must cite the nesting/depth fault; got: " + describe(ex));
    }

    @Test
    void reviewer5000Probe_rejectedByTokenLengthCap_beforeDecode() {
        // The reviewer's literal 5000-deep token (25003 UTF-8 bytes) is rejected by the SEPARATE
        // outer guard -- the wireType SIZE(1..1024) cap (STD-006 §7.8) at AtomicSerialFieldDef
        // construction -- before any decode runs. Documented here so the two layers are explicit:
        // the length cap stops the pathological token; the depth guard (tests above) stops any
        // nested token that slips under the cap.
        Exception ex = assertThrows(Exception.class,
                () -> new AtomicSerialFieldDef("coll", nestedListToken(5000)),
                "a 5000-deep list: token must be rejected by the wireType length cap");
        assertTrue(messageChainContains(ex, "1024") || messageChainContains(ex, "length")
                        || messageChainContains(ex, "outside"),
                "rejection should cite the wireType SIZE cap; got: " + describe(ex));
    }

    // =========================================================================
    // set: -- canonicalise variant (outer tag SET 0x31). A single-element body is trivially in
    // strictly-ascending order, so only the depth guard can trip.
    // =========================================================================

    /** {@code set:} repeated {@code levels} times, innermost element type {@code int}. */
    private static String nestedSetToken(int levels) {
        String tok = "int";
        for (int i = 0; i < levels; i++) {
            tok = SchemaGenerator.collectionWireType(HashSet.class, tok); // prepends "set:"
        }
        return tok;
    }

    /** A {@code levels}-deep chain of SET OF bodies (single inner element per level). */
    private static byte[] nestedSetBytes(int levels) {
        byte[] node = DerWriter.writeSet(List.of()); // innermost empty set (31 00)
        for (int i = 1; i < levels; i++) {
            node = DerWriter.writeSet(List.of(node));
        }
        return DerWriter.writeSequence(List.of(node)); // class SEQUENCE { <coll field> }
    }

    @Test
    void nestedSet_oneLevelPastMaxNesting_isRejectedNotStackOverflow() {
        int levels = ObjectCodec.MAX_NESTING + 2;
        String tok = nestedSetToken(levels);
        byte[] bytes = nestedSetBytes(levels);
        Exception ex = assertThrows(Exception.class, () -> decode(bytes, tok),
                "a nested set: chain one level past MAX_NESTING must be rejected fail-secure");
        assertTrue(messageChainContains(ex, "MAX_NESTING"),
                "rejection must cite the nesting/depth fault; got: " + describe(ex));
    }

    // =========================================================================
    // map: -- nested via a canonicalise map whose VALUE type is itself a (deeply nested)
    // collection. Exercises the decodeMap -> decodeElementValue -> decodeCollection recursion.
    // =========================================================================

    @Test
    void nestedMapValueCollection_pastMaxNesting_isRejectedNotStackOverflow() {
        // map:{int}{ list:...:int } where the value is a (MAX_NESTING + 2)-deep list: chain.
        // The map decode runs at depth 0; its single value (a list:) recurses to depth 1, then the
        // list chain to depth MAX_NESTING + 1 -> rejected.
        int valueLevels = ObjectCodec.MAX_NESTING + 2;
        String valueTok = nestedListToken(valueLevels);
        String mapTok = SchemaGenerator.mapWireType(HashMap.class, "int", valueTok);

        // Build the single map entry: SEQUENCE { key=INTEGER 1, value=<nested list bytes> }.
        byte[] key = DerWriter.writeInteger(java.math.BigInteger.valueOf(1));
        byte[] value = nestedListNode(valueLevels);
        byte[] entry = DerWriter.writeSequence(List.of(key, value));
        byte[] mapBody = DerWriter.writeSet(List.of(entry));               // canonicalise map -> SET
        byte[] classSeq = DerWriter.writeSequence(List.of(mapBody));

        Exception ex = assertThrows(Exception.class, () -> decode(classSeq, mapTok),
                "a canonicalise map whose value is a nested collection past MAX_NESTING must be "
                + "rejected fail-secure, never StackOverflow");
        assertTrue(messageChainContains(ex, "MAX_NESTING"),
                "rejection must cite the nesting/depth fault; got: " + describe(ex));
    }

    @Test
    void nestedMapValueCollection_atMaxNesting_roundTrips() throws Exception {
        // Value is a list: chain whose deepest level lands at exactly MAX_NESTING (map at depth 0,
        // value list at depth 1..MAX_NESTING): a map value list of MAX_NESTING levels.
        int valueLevels = ObjectCodec.MAX_NESTING;
        String valueTok = nestedListToken(valueLevels);
        String mapTok = SchemaGenerator.mapWireType(HashMap.class, "int", valueTok);

        byte[] key = DerWriter.writeInteger(java.math.BigInteger.valueOf(1));
        byte[] value = nestedListNode(valueLevels);
        byte[] entry = DerWriter.writeSequence(List.of(key, value));
        byte[] mapBody = DerWriter.writeSet(List.of(entry));
        byte[] classSeq = DerWriter.writeSequence(List.of(mapBody));

        Object decoded = assertDoesNotThrow(() -> decode(classSeq, mapTok),
                "a map value nested to exactly MAX_NESTING must decode");
        assertTrue(decoded instanceof Map<?, ?>, "the field must decode to a Map");
    }

    // -------------------------------------------------------------------------
    // Diagnostics.
    // -------------------------------------------------------------------------

    private static boolean messageChainContains(Throwable t, String needle) {
        String lower = needle.toLowerCase();
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.toLowerCase().contains(lower)) return true;
        }
        return false;
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
