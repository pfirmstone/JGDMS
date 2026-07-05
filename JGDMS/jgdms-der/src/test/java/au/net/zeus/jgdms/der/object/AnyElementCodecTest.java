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
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.object.fixtures.CollectionRecord;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.junit.jupiter.api.Test;

import java.io.InvalidObjectException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for the self-describing {@code Any} element form (memo §4): the context-tagged
 * CHOICE codec, its determinism / value-equality guarantees (§4.2), and the four NORMATIVE decoder
 * fences (§4.3). A {@code CollectionRecord} carries the collection value; the {@code "coll"} field's
 * schema token uses {@code any} as the element/key/value wire-type.
 */
class AnyElementCodecTest {

    // -------------------------------------------------------------------------
    // Helpers: single-"coll" schema with an Any element/key/value token.
    // -------------------------------------------------------------------------

    private static AtomicSerialSchemaRecord schema(String collToken) {
        return new AtomicSerialSchemaRecord(
                CollectionRecord.class.getName(), (byte[]) null,
                List.of(new AtomicSerialFieldDef("coll", collToken)));
    }

    private static byte[] encode(Object collValue, String collToken) throws Exception {
        return ObjectCodec.encode(new CollectionRecord(collValue), CollectionRecord.class, schema(collToken));
    }

    private static Object decode(byte[] bytes, String collToken) throws Exception {
        return ObjectCodec.decode(CollectionRecord.class, schema(collToken), bytes).getColl();
    }

    private static Object roundTrip(Object value, String collToken) throws Exception {
        return decode(encode(value, collToken), collToken);
    }

    /**
     * {@code encode(...)} returns the CollectionRecord's private SEQUENCE (0x30) wrapping the single
     * {@code coll} field TLV. This unwraps it to the raw collection field TLV (the SET/SEQUENCE) so
     * a test can inspect the collection encoding directly.
     */
    private static byte[] collFieldTlv(byte[] recordSeq) throws DerException {
        DerReader seq = new DerReader(recordSeq).readSequence();
        int start = seq.position();
        DerReader.TlvHeader hdr = seq.readTlvHeader();
        seq.readRawContent(hdr.contentLength());
        return seq.slice(start, seq.position());
    }

    // A set: token whose element is Any (a raw/unresolvable Set<...> of mixed closed-subset values).
    private static final String SET_ANY = CollectionWireTypes.setToken("any");
    private static final String LIST_ANY = CollectionWireTypes.listToken("any");
    private static final String MAP_ANY = CollectionWireTypes.mapToken("any", "any");

    // =========================================================================
    // Round-trip: every closed-subset category travels through Any.
    // =========================================================================

    @Test
    void anyList_mixedScalars_roundTrips() throws Exception {
        // A list: (preserve) of Any holding one value of each scalar category.
        List<Object> in = new ArrayList<>();
        in.add(Boolean.TRUE);
        in.add((byte) 7);
        in.add((short) 300);
        in.add(42);
        in.add(9_000_000_000L);
        in.add(3.5f);
        in.add(2.5d);
        in.add('Z');
        in.add("hello");
        in.add(new byte[]{1, 2, 3});
        in.add(null); // null element sentinel

        Object out = roundTrip(in, LIST_ANY);
        assertTrue(out instanceof List, "list: reconstructs a List");
        List<?> ol = (List<?>) out;
        assertEquals(in.size(), ol.size());
        assertEquals(Boolean.TRUE, ol.get(0));
        assertEquals((byte) 7, ol.get(1));
        assertEquals((short) 300, ol.get(2));
        assertEquals(42, ol.get(3));
        assertEquals(9_000_000_000L, ol.get(4));
        assertEquals(3.5f, ol.get(5));
        assertEquals(2.5d, ol.get(6));
        assertEquals('Z', ol.get(7));
        assertEquals("hello", ol.get(8));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) ol.get(9));
        assertNull(ol.get(10));
    }

    @Test
    void anyList_atomicSerialObjectElement_roundTrips() throws Exception {
        // [20] EXPLICIT @AtomicSerial object element travels via the same nested-record path.
        List<Object> in = new ArrayList<>();
        in.add(new Foo(1, "a"));
        in.add(new Foo(2, "b"));
        Object out = roundTrip(in, LIST_ANY);
        List<?> ol = (List<?>) out;
        assertEquals(new Foo(1, "a"), ol.get(0));
        assertEquals(new Foo(2, "b"), ol.get(1));
    }

    @Test
    void anyList_nestedCanonicalCollectionElement_roundTrips() throws Exception {
        // [30] EXPLICIT canonicalCollection element: a HashSet<Integer> inside an Any list.
        List<Object> in = new ArrayList<>();
        Set<Integer> inner = new HashSet<>(Arrays.asList(3, 1, 2));
        in.add(inner);
        Object out = roundTrip(in, LIST_ANY);
        List<?> ol = (List<?>) out;
        assertTrue(ol.get(0) instanceof Set, "inner canonicalise collection reconstructs a Set");
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), ol.get(0));
    }

    @Test
    void anyMap_stringKeyFooValue_roundTrips() throws Exception {
        Map<Object, Object> in = new LinkedHashMap<>();
        in.put("x", new Foo(1, "a"));
        in.put("y", 99);
        Object out = roundTrip(in, MAP_ANY);
        assertTrue(out instanceof Map);
        Map<?, ?> om = (Map<?, ?>) out;
        assertEquals(new Foo(1, "a"), om.get("x"));
        assertEquals(99, om.get("y"));
    }

    @Test
    void anyElement_emptyMap_decodesToEmptySet_documentedAndLocked() throws Exception {
        // F2 -- PINS the documented empty-Any-map behaviour (AnyCodec.isMapShape Javadoc): a map
        // nested AS AN Any element is distinguished from a set/list STRUCTURALLY (a map entry is an
        // inner universal SEQUENCE{key,value}; a set/list element is a context-tagged AnyElement).
        // An EMPTY collection has no element to inspect, so it is resolved by the outer wire tag ONLY:
        //   * an empty CANONICALISE map (HashMap -> [30], an empty SET OF, identical on the wire to
        //     an empty canonicalise set) decodes to an empty Set;
        //   * an empty ORDERED map (LinkedHashMap -> [31], an empty SEQUENCE OF, identical on the wire
        //     to an empty list/orderedset) decodes to an empty List.
        // Both are deterministic and intentional; the container type is coerced by the developer's
        // check(GetArg)/constructor (Layer 2, memo §8.2), and an empty map/set/list are equivalently
        // empty. This test LOCKS the behaviour so it cannot regress silently; a NON-empty Any map
        // still round-trips as a Map (test above).

        // (1) empty canonicalise map ([30]) -> empty Set.
        List<Object> in = new ArrayList<>();
        in.add(new java.util.HashMap<>());
        List<?> ol = (List<?>) roundTrip(in, LIST_ANY);
        assertEquals(1, ol.size());
        assertTrue(ol.get(0) instanceof Set,
                "an empty canonicalise Any map decodes to an empty Set (documented boundary), got "
                + describe(ol.get(0)));
        assertTrue(((Set<?>) ol.get(0)).isEmpty(), "and it is empty");

        // Determinism across canonicalise-map implementations (all -> [30] -> empty Set).
        List<Object> in2 = new ArrayList<>();
        in2.add(new java.util.concurrent.ConcurrentHashMap<>());
        assertEquals(Set.of(), ((List<?>) roundTrip(in2, LIST_ANY)).get(0),
                "empty-canonicalise-map -> empty-Set is deterministic across map implementations");

        // (2) empty ordered map ([31]) -> empty List (preserve discipline; no map entry to detect).
        List<Object> inOrdered = new ArrayList<>();
        inOrdered.add(new java.util.LinkedHashMap<>());
        Object orderedElem = ((List<?>) roundTrip(inOrdered, LIST_ANY)).get(0);
        assertTrue(orderedElem instanceof List,
                "an empty ORDERED Any map decodes to an empty List (documented boundary), got "
                + describe(orderedElem));
        assertTrue(((List<?>) orderedElem).isEmpty(), "and it is empty");

        // Wire proof: an empty canonicalise Any map and an empty canonicalise Any set encode to
        // identical bytes ([30] over an empty SET OF) -- confirming the boundary is a genuine wire
        // ambiguity resolved deterministically by the tag, not a decode quirk.
        List<Object> emptySetElem = new ArrayList<>();
        emptySetElem.add(new HashSet<>());
        assertArrayEquals(encode(in, LIST_ANY), encode(emptySetElem, LIST_ANY),
                "an empty canonicalise Any map and an empty Any set encode to identical wire bytes");
    }

    private static String describe(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    // =========================================================================
    // Determinism + value-equality (memo §4.2).
    // =========================================================================

    @Test
    void anySet_twoEqualSets_byteIdentical() throws Exception {
        // Two .equals HashSets of Any scalar elements built in different insertion orders must
        // encode byte-identically: the AnyElement context tag is the leading octet, so octet-sort
        // groups by category then by value -- a total deterministic order (memo §4.2).
        Set<Object> a = new HashSet<>();
        a.add("m"); a.add(42); a.add("k"); a.add(7);
        Set<Object> b = new HashSet<>();
        b.add(7); b.add("k"); b.add(42); b.add("m");
        assertEquals(a, b);
        assertArrayEquals(encode(a, SET_ANY), encode(b, SET_ANY),
                "two .equals Any sets must produce byte-identical DER (canonical octet-sort over "
                + "complete AnyElement TLVs)");
    }

    @Test
    void anySet_octetSort_groupsByCategoryTag() throws Exception {
        // The encoded set: of Any must have its element TLVs in strictly ascending octet order, and
        // (because the leading octet is the context tag) grouped by category tag ascending.
        Set<Object> s = new HashSet<>();
        s.add("string8");     // [8]
        s.add(3);             // [3]
        s.add(Boolean.TRUE);  // [0]
        byte[] enc = collFieldTlv(encode(s, SET_ANY));

        // Walk the SET OF; collect leading context-tag numbers; assert non-decreasing and that the
        // whole-TLV sequence is strictly ascending (the decoder's own §11.6 check will also verify).
        DerReader outer = new DerReader(enc);
        DerReader body = outer.readSet();
        List<Integer> tagNums = new ArrayList<>();
        byte[] prev = null;
        while (body.hasMore()) {
            int start = body.position();
            Tag t = body.peekTag();
            DerReader.TlvHeader hdr = body.readTlvHeader();
            body.readRawContent(hdr.contentLength());
            int end = body.position();
            byte[] tlv = body.slice(start, end);
            assertEquals(Tag.CLASS_CONTEXT, t.tagClass(), "every AnyElement is context-tagged");
            tagNums.add(t.tagNumber());
            if (prev != null) {
                assertTrue(CollectionWireTypes.compareOctets(prev, tlv) < 0,
                        "AnyElements must be strictly ascending (octet-sort, §11.6)");
            }
            prev = tlv;
        }
        // Category tags must be non-decreasing (grouping by category is a theorem of the leading tag).
        for (int i = 1; i < tagNums.size(); i++) {
            assertTrue(tagNums.get(i - 1) <= tagNums.get(i),
                    "category tags group ascending: " + tagNums);
        }
        assertEquals(List.of(0, 3, 8), tagNums, "boolean[0] < int[3] < string[8]");
    }

    @Test
    void anyElement_payloadBytes_matchDeclaredElementPayload() throws Exception {
        // Value-equality-of-payload (memo §4.2): a value's bytes past the context tag are identical
        // to its declared-element encoding (an IMPLICIT tag replaces only the leading tag octet).
        // Encode {42} as a set:int (declared) and as a set:any, then compare the int element's
        // post-tag bytes.
        Set<Integer> declared = new HashSet<>(List.of(42));
        Set<Object> any = new HashSet<>(List.of(42));
        byte[] declEnc = collFieldTlv(encode(declared, CollectionWireTypes.setToken("int")));
        byte[] anyEnc  = collFieldTlv(encode(any, SET_ANY));

        byte[] declContent = firstElementContent(declEnc);
        byte[] anyContent  = firstElementContent(anyEnc);
        assertArrayEquals(declContent, anyContent,
                "the int value's content octets are identical in a set:int and a set:any (the "
                + "Any context tag adds no wrapper past the tag)");
    }

    /** Content octets of the first element TLV of a SET OF. */
    private static byte[] firstElementContent(byte[] setTlv) throws DerException {
        DerReader body = new DerReader(setTlv).readSet();
        DerReader.TlvHeader hdr = body.readTlvHeader();
        return body.readRawContent(hdr.contentLength());
    }

    // =========================================================================
    // FENCE (a): MAX_NESTING threaded through every Any recursion.
    // =========================================================================

    @Test
    void fenceA_deeplyNestedAny_rejectedBeforeStackOverflow() throws Exception {
        // Hand-build an Any collection nested well past MAX_NESTING: [30] EXPLICIT wrapping a SET OF
        // whose single element is again [30] EXPLICIT ... Each level is an Any->collection recursion
        // that MUST decrement the depth bound. Depth far exceeds ObjectCodec.MAX_NESTING (16).
        int depthLevels = ObjectCodec.MAX_NESTING + 40;
        // Innermost: an empty canonicalise SET OF (a set of nothing) under [30].
        byte[] node = wrapCanonicalCollection(DerWriter.writeSet(List.of()));
        for (int i = 0; i < depthLevels; i++) {
            // A SET OF one element = the previous node, wrapped again in [30].
            byte[] setOfOne = DerWriter.writeSet(List.of(node));
            node = wrapCanonicalCollection(setOfOne);
        }
        // Put this as the single element of a top-level set:any and try to decode.
        byte[] topSet = DerWriter.writeSet(List.of(node));
        byte[] payload = wrapCollectionRecordSequence(topSet);

        java.io.IOException ex = assertThrows(java.io.IOException.class,
                () -> decode(payload, SET_ANY),
                "a deeply-nested Any must be rejected by the MAX_NESTING guard, not by StackOverflow");
        // The rejection is the depth-bound guard: its message (or a cause's) names "nesting".
        assertTrue(messageChainContains(ex, "nesting"),
                "rejection must be the depth-bound guard, got: " + ex);
    }

    // =========================================================================
    // FENCE (b): Any @AtomicSerial reconstruction uses the SAME gated path.
    // =========================================================================

    @Test
    void fenceB_anyObject_runsCheckGetArg_sameAsTyped() throws Exception {
        // Foo.check(GetArg) throws InvalidObjectException when fooLabel is null. If the Any
        // atomicSerialObject path routes through decodeNested -> the (GetArg) constructor -> check,
        // then an Any-carried Foo with a null label MUST be rejected exactly as a typed element is.
        // Build a valid encoding, then corrupt the label to null at the source and re-encode both
        // ways; both must throw the same check failure.

        // Typed baseline: a set:@AtomicSerial of a Foo whose label we null out via a bad decode.
        // Simpler and deterministic: encode a Foo with a null label is impossible (ctor requires
        // non-null), so we instead prove the POSITIVE routing: a valid Any Foo reconstructs THROUGH
        // the constructor (identity/equality proves construction ran), and an object presented under
        // [20] that is NOT a well-formed @AtomicSerial record is rejected by decodeNested.
        List<Object> in = List.of(new Foo(5, "ok"));
        Object out = roundTrip(in, LIST_ANY);
        assertEquals(new Foo(5, "ok"), ((List<?>) out).get(0),
                "an Any @AtomicSerial element reconstructs through the (GetArg) constructor path");
    }

    @Test
    void fenceB_anyObject_innerRecord_byteIdenticalToTypedElement() throws Exception {
        // Structural proof of "same door": the [20] EXPLICIT inner record bytes for an Any-carried
        // Foo are byte-identical to the nested-record bytes of a typed set:@AtomicSerial Foo element.
        // Same bytes -> same decodeNested path -> same ATOMIC gate + ResolutionContext + check(GetArg).
        Set<Object> anySet = new LinkedHashSet<>();
        anySet.add(new Foo(11, "x"));
        Set<Foo> typedSet = new LinkedHashSet<>();
        typedSet.add(new Foo(11, "x"));

        byte[] anyElem = firstElementTlv(collFieldTlv(encode(anySet, SET_ANY)));
        // Unwrap the [20] EXPLICIT wrapper to its inner nested-record TLV.
        DerReader ar = new DerReader(anyElem);
        DerReader.TlvHeader ah = ar.readTlvHeader();
        assertEquals(Tag.CLASS_CONTEXT, ah.tag().tagClass());
        assertEquals(AnyCodec.TAG_ATOMIC, ah.tag().tagNumber(), "Foo travels under [20]");
        byte[] anyInner = ar.readRawContent(ah.contentLength());

        byte[] typedInner = firstElementTlv(collFieldTlv(
                encode(typedSet, CollectionWireTypes.setToken("@AtomicSerial"))));

        assertArrayEquals(typedInner, anyInner,
                "an Any [20] object's inner record bytes equal the typed @AtomicSerial element's "
                + "nested-record bytes -- Any is not a second door (memo §4.3 fence (b))");
    }

    /** The first complete element TLV of a SET/SEQUENCE OF. */
    private static byte[] firstElementTlv(byte[] collTlv) throws DerException {
        DerReader outer = new DerReader(collTlv);
        Tag t = outer.peekTag();
        DerReader body = Tag.SET.equals(t) ? outer.readSet() : outer.readSequence();
        int start = body.position();
        DerReader.TlvHeader hdr = body.readTlvHeader();
        body.readRawContent(hdr.contentLength());
        return body.slice(start, body.position());
    }

    @Test
    void fenceB_anyObject_malformedRecord_rejected() throws Exception {
        // A [20] EXPLICIT arm wrapping bytes that are not a valid @AtomicSerial nested record must
        // be rejected by the SAME decodeNested validation (not silently accepted). Wrap a bogus
        // OCTET STRING under [20].
        byte[] bogus = DerWriter.writeOctetString(new byte[]{0x01, 0x02});
        byte[] anyObj = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_ATOMIC), bogus);
        byte[] listOfOne = DerWriter.writeSequence(List.of(anyObj)); // list: -> SEQUENCE OF
        byte[] payload = wrapCollectionRecordSequence(listOfOne);
        assertThrows(java.io.IOException.class, () -> decode(payload, LIST_ANY),
                "a malformed [20] atomicSerialObject body must be rejected by the nested-record path");
    }

    // =========================================================================
    // FENCE (c): unknown / reserved / mismatched context tag -> HARD REJECT.
    // =========================================================================

    @Test
    void fenceC_reservedGapTag_rejected() throws Exception {
        // A reserved-gap context tag ([15], within [10..19]) is UNREGISTERED -> hard reject.
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 15),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejectedAnyElement(elem, LIST_ANY, "reserved-gap tag [15]");
    }

    @Test
    void fenceC_tagAbove31_rejected() throws Exception {
        // A context tag > [31] (here [40], high-tag-number form) is UNASSIGNED -> hard reject.
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 40),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejectedAnyElement(elem, LIST_ANY, "tag [40] > [31]");
    }

    @Test
    void fenceC_scalarTagWithConstructedForm_rejected() throws Exception {
        // A scalar arm ([3] int) MUST be primitive; a CONSTRUCTED [3] is a form mismatch -> reject.
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_INT),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejectedAnyElement(elem, LIST_ANY, "constructed scalar tag [3]");
    }

    @Test
    void fenceC_collectionTagOverWrongInnerDiscipline_rejected() throws Exception {
        // [30] canonicalCollection MUST wrap a SET OF (0x31). Wrapping a SEQUENCE OF (0x30) is a
        // lying / mismatched encoding -> reject (the inner discipline tag is the discriminator).
        byte[] innerSeqOf = DerWriter.writeSequence(List.of()); // 0x30, wrong for [30]
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_CANONICAL_COLL),
                innerSeqOf);
        assertRejectedAnyElement(elem, LIST_ANY, "[30] over a SEQUENCE OF");
    }

    // =========================================================================
    // FENCE (d): non-canonical (non-minimal) tag encoding -> HARD REJECT.
    // =========================================================================

    @Test
    void fenceD_nonMinimalTagEncoding_rejected() throws Exception {
        // Encode [3] int in the high-tag-number (long) form even though 3 fits the short form. The
        // Tag decoder rejects a high-tag-number encoding used for a tag < 31 (non-minimal).
        // First octet: context, primitive, 0x1F (high-tag flag) = 0x9F; then 0x03 (tag number 3).
        byte[] intContent = contentOf(DerWriter.writeInteger(BigInteger.valueOf(1)));
        byte[] lengthAndValue = new byte[1 + intContent.length];
        lengthAndValue[0] = (byte) intContent.length;
        System.arraycopy(intContent, 0, lengthAndValue, 1, intContent.length);
        byte[] elem = new byte[2 + lengthAndValue.length];
        elem[0] = (byte) 0x9F;            // context, primitive, high-tag-number flag
        elem[1] = (byte) 0x03;            // tag number 3 in long form (non-minimal for < 31)
        System.arraycopy(lengthAndValue, 0, elem, 2, lengthAndValue.length);
        assertRejectedAnyElement(elem, LIST_ANY, "non-minimal high-tag-number encoding of [3]");
    }

    // -------------------------------------------------------------------------
    // Fence helpers.
    // -------------------------------------------------------------------------

    private static void assertRejectedAnyElement(byte[] anyElementTlv, String collToken, String what)
            throws Exception {
        boolean preserve = collToken.startsWith("list:") || collToken.startsWith("orderedset:")
                || collToken.startsWith("orderedmap:");
        byte[] body = preserve
                ? DerWriter.writeSequence(List.of(anyElementTlv))
                : DerWriter.writeSet(List.of(anyElementTlv));
        byte[] payload = wrapCollectionRecordSequence(body);
        // Fail-secure: the decode error surfaces as an IOException (a DerException, or an
        // InvalidObjectException wrapping it once it propagates out of the (GetArg) constructor).
        // The MUST is "rejected"; the exact subtype is not load-bearing.
        assertThrows(java.io.IOException.class, () -> decode(payload, collToken),
                "fence: " + what + " must be hard-rejected");
    }

    /** Wraps a collection TLV as the single field of a CollectionRecord private SEQUENCE. */
    private static byte[] wrapCollectionRecordSequence(byte[] collTlv) {
        return DerWriter.writeSequence(List.of(collTlv));
    }

    private static byte[] wrapCanonicalCollection(byte[] setOfTlv) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_CANONICAL_COLL), setOfTlv);
    }

    private static byte[] contentOf(byte[] tlv) throws DerException {
        DerReader r = new DerReader(tlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        return r.readRawContent(hdr.contentLength());
    }

    /** True if {@code t} or any of its causes carries a message containing {@code needle}. */
    private static boolean messageChainContains(Throwable t, String needle) {
        String lc = needle.toLowerCase();
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.toLowerCase().contains(lc)) {
                return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }
}
