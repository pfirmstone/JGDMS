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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.fixtures.ForeignSerializer;
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.object.fixtures.NestedValueSub;
import au.net.zeus.jgdms.der.object.fixtures.PolySetHolder;
import au.net.zeus.jgdms.der.object.fixtures.ProbeValue;
import au.net.zeus.jgdms.der.object.fixtures.X500MapHolder;
import au.net.zeus.jgdms.der.object.fixtures.X500SetHolder;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part A (F1) — decode-admission gate extended to COLLECTION and MAP elements.
 *
 * <p>Hostile tests drive the exact typed {@code decodeCollection(..., expectedKeyType,
 * expectedValueType)} API that {@code DerGetArg.lookup} calls after recovering the field's
 * declared element type(s) from its generic signature. Legitimate tests round-trip real holders
 * end-to-end through {@code encode/decodeHierarchy}, exercising the {@code DerGetArg} generic
 * recovery for real. In every hostile case the foreign serializer's {@code (GetArg)} ctor is
 * proven not to run (probe stays {@code false}).
 */
class CollectionElementAdmissionGateTest {

    private static final String DN = "CN=Alice,O=Acme,C=AU";

    @BeforeEach
    void resetProbe() {
        ForeignSerializer.DECODE_CONSTRUCTED.set(false);
    }

    // ---- wire builders (a hostile peer's hand-shaped collection bytes) --------------------

    private static byte[] foreignElementTlv() throws Exception {
        byte[] tlv = ObjectCodec.encodeNested(new ForeignSerializer(new ProbeValue()), "e", 0);
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "encode-side ctor must not trip the decode probe");
        return tlv;
    }

    private static byte[] legitX500ElementTlv() throws Exception {
        return ObjectCodec.encodeNested(new X500Principal(DN), "e", 0);
    }

    /** A `list:@AtomicSerial` (preserve, SEQUENCE OF) collection body over the given element TLVs. */
    private static byte[] listWire(byte[]... elements) {
        return DerWriter.writeSequence(List.of(elements));
    }

    /** An `orderedmap:` (preserve, SEQUENCE OF SEQUENCE{key,value}) map body over one entry. */
    private static byte[] orderedMapWire(byte[] keyTlv, byte[] valTlv) {
        byte[] entry = DerWriter.writeSequence(List.of(keyTlv, valTlv));
        return DerWriter.writeSequence(List.of(entry));
    }

    // =========================================================================
    // HOSTILE: foreign serializer element in a concretely-typed collection / map slot.
    // =========================================================================

    @Test
    void foreignSerializerSetElementRejectedBeforeConstruction() throws Exception {
        byte[] wire = listWire(foreignElementTlv());
        String wt = CollectionWireTypes.listToken("@AtomicSerial");

        // Set<X500Principal> element: expectedValueType = X500Principal.
        assertThrows(IOException.class, () ->
                ObjectCodec.decodeCollection(wire, wt, 0, null, ResolutionContext.NONE,
                        List.class, Object.class, X500Principal.class));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "PROOF: a foreign serializer element in a Set<X500Principal> slot must be "
                + "rejected before its (GetArg) ctor runs");
    }

    @Test
    void foreignSerializerMapValueRejectedBeforeConstruction() throws Exception {
        // Map<X500Principal,X500Principal>: legit key (decodes), hostile value (rejected).
        byte[] wire = orderedMapWire(legitX500ElementTlv(), foreignElementTlv());
        String wt = CollectionWireTypes.orderedMapToken("@AtomicSerial", "@AtomicSerial");

        assertThrows(IOException.class, () ->
                ObjectCodec.decodeCollection(wire, wt, 0, null, ResolutionContext.NONE,
                        Map.class, X500Principal.class, X500Principal.class));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "a foreign serializer in the map VALUE slot must be rejected before its ctor runs");
    }

    @Test
    void foreignSerializerMapKeyRejectedBeforeConstruction() throws Exception {
        // Hostile key (rejected first), value never reached.
        byte[] wire = orderedMapWire(foreignElementTlv(), legitX500ElementTlv());
        String wt = CollectionWireTypes.orderedMapToken("@AtomicSerial", "@AtomicSerial");

        assertThrows(IOException.class, () ->
                ObjectCodec.decodeCollection(wire, wt, 0, null, ResolutionContext.NONE,
                        Map.class, X500Principal.class, X500Principal.class));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "a foreign serializer in the map KEY slot must be rejected before its ctor runs");
    }

    // =========================================================================
    // END-TO-END injection (S2): full decodeHierarchy / DerGetArg path, NOT the 8-arg API.
    // =========================================================================

    @Test
    void injectedForeignElementInSetX500PrincipalRejectedThroughDecodeHierarchy() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500SetHolder.class);

        // Prove the hand-built wire layout matches encodeHierarchy: a LEGIT wire built the SAME
        // way (outer SEQ { per-class SEQ { SET-OF { element } } }) must decode end-to-end, so a
        // rejection of the hostile variant below is definitively the element gate, not a
        // structural parse error (guards against a false-positive throw).
        byte[] legitSet  = DerWriter.writeSet(List.of(legitX500ElementTlv()));
        byte[] legitWire = DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(legitSet))));
        X500SetHolder ok = ObjectCodec.decodeHierarchy(X500SetHolder.class, chain, legitWire);
        assertEquals(Set.of(new X500Principal(DN)), ok.principals(),
                "hand-built legit wire must round-trip (confirms the injected layout is correct)");

        // Now inject a foreign-serializer element into the SAME collection layout and decode via
        // the FULL path DerGetArg uses at runtime: decodeHierarchy -> DerFieldStore ->
        // DerGetArg.lookup -> declaredElementTypes recovery (Set<X500Principal> -> X500Principal)
        // -> decodeCollection(recovered) -> element gate -> reject BEFORE the foreign ctor runs.
        byte[] hostileSet  = DerWriter.writeSet(List.of(foreignElementTlv()));
        byte[] hostileWire = DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of(hostileSet))));

        IOException ex = assertThrows(IOException.class, () ->
                ObjectCodec.decodeHierarchy(X500SetHolder.class, chain, hostileWire));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "END-TO-END PROOF: a foreign element injected into a Set<X500Principal> field must "
                + "be rejected through the real decodeHierarchy/DerGetArg path before its ctor runs");
        assertTrue(rootMessage(ex).contains("admissible"),
                "the end-to-end failure must be the pre-construction admission gate, not a "
                + "structural parse error; got: " + ex);
    }

    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null) {
                sb.append(c.getMessage()).append(" | ");
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // LEGITIMATE: end-to-end holder round-trips (exercise DerGetArg generic recovery).
    // =========================================================================

    @Test
    void setOfX500PrincipalRoundTripsEndToEnd() throws Exception {
        Set<X500Principal> in = Set.of(new X500Principal(DN), new X500Principal("CN=Bob,C=AU"));
        X500SetHolder holder = new X500SetHolder(in);
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500SetHolder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(holder, chain);

        X500SetHolder back = ObjectCodec.decodeHierarchy(X500SetHolder.class, chain, wire);
        assertEquals(in, back.principals(),
                "Set<X500Principal> must round-trip (recovered element type admits X500PrincipalSerializer)");
    }

    @Test
    void mapOfStringToX500PrincipalRoundTripsEndToEnd() throws Exception {
        Map<String, X500Principal> in = Map.of("alice", new X500Principal(DN));
        X500MapHolder holder = new X500MapHolder(in);
        SchemaChain.Result chain = SchemaGenerator.generateChain(X500MapHolder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(holder, chain);

        X500MapHolder back = ObjectCodec.decodeHierarchy(X500MapHolder.class, chain, wire);
        assertEquals(in, back.byName(),
                "Map<String,X500Principal> must round-trip (key=String, value=X500Principal recovered)");
    }

    @Test
    void listOfPolymorphicSubtypeRoundTripsEndToEnd() throws Exception {
        List<NestedValue> in = List.of(
                new NestedValueSub(1, "a", "x"),
                new NestedValueSub(2, "b", "y"));
        PolySetHolder holder = new PolySetHolder(in);
        SchemaChain.Result chain = SchemaGenerator.generateChain(PolySetHolder.class);
        byte[] wire = ObjectCodec.encodeHierarchy(holder, chain);

        PolySetHolder back = ObjectCodec.decodeHierarchy(PolySetHolder.class, chain, wire);
        assertEquals(in, back.values(),
                "List<NestedValue> holding NestedValueSub must round-trip (clause-1 polymorphism)");
        assertInstanceOf(NestedValueSub.class, back.values().get(0));
    }

    // =========================================================================
    // RESIDUALS (documented, NOT closed).
    // =========================================================================

    @Test
    void rawCollectionElementIsTheDocumentedObjectResidual() throws Exception {
        byte[] wire = listWire(foreignElementTlv());
        String wt = CollectionWireTypes.listToken("@AtomicSerial");

        // A RAW Set/List recovers Object.class for its element type (clause 1 always admits):
        // the foreign serializer's ctor DOES run here -- the acknowledged residual.
        Object decoded = ObjectCodec.decodeCollection(wire, wt, 0, null, ResolutionContext.NONE,
                List.class, Object.class, Object.class);
        assertTrue(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "RESIDUAL: a raw/wildcard element type (Object.class) is NOT closed by this gate");
        assertInstanceOf(List.class, decoded);
        assertInstanceOf(ProbeValue.class, ((List<?>) decoded).get(0));
    }

    @Test
    void nestedGenericOuterElementIsGatedAtErasureInnerIsResidual() throws Exception {
        // Set<List<X>>: the recovered OUTER element type is List (erasure); the INNER element
        // stays an Object.class residual. Prove the OUTER is gated at List: a wire element that
        // is NOT a List (here a ForeignSerializer, replaceObType=ProbeValue) is rejected because
        // List.isAssignableFrom(ProbeValue) is false.
        byte[] wire = listWire(foreignElementTlv());
        String wt = CollectionWireTypes.listToken("@AtomicSerial");

        assertThrows(IOException.class, () ->
                ObjectCodec.decodeCollection(wire, wt, 0, null, ResolutionContext.NONE,
                        Set.class, Object.class, List.class));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "outer element gated at List.class erasure -> a non-List element is rejected "
                + "before construction (inner element type remains the documented residual)");
    }
}
