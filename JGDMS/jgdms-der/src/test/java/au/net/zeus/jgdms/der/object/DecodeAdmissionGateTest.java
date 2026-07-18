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
import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.fixtures.ForeignSerializer;
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.object.fixtures.NestedValueSub;
import au.net.zeus.jgdms.der.object.fixtures.ProbeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part A -- decode-admission pre-construction gate ({@code ObjectCodec.admissibleConstructClass},
 * threaded through {@link ObjectCodec#decodeNested(byte[], Class, int,
 * au.net.zeus.jgdms.der.object.DeserializationCompletion, ResolutionContext)}).
 *
 * <p>These tests drive the <em>exact</em> API that {@code DerGetArg.lookup} calls for a nested
 * {@code @AtomicSerial} field: {@code decodeNested(bytes, declaredFieldType, depth, unit, res)}.
 * A hostile peer's capability is a hand-shaped wire naming an arbitrary serializer class in a
 * slot the graph expects to be a concrete type -- reproduced here by encoding a foreign
 * serializer's nested record ({@link #foreignSerializerNestedBytes()}) and then decoding it with
 * an incompatible declared type. The critical assertion is that the foreign serializer's
 * {@code (GetArg)} ctor is proven <b>not</b> to run (its {@link ForeignSerializer#DECODE_CONSTRUCTED}
 * probe stays {@code false}) -- construction is refused before the dangerous ctor side effect.
 */
class DecodeAdmissionGateTest {

    private static final String DN = "CN=Alice,O=Acme,C=AU";

    @BeforeEach
    void resetProbe() {
        ForeignSerializer.DECODE_CONSTRUCTED.set(false);
    }

    /**
     * Build a hostile nested record whose wire leaf is {@link ForeignSerializer}
     * (a {@code @Serializer(replaceObType = ProbeValue.class)} class). Since
     * {@code ForeignSerializer} is itself {@code @AtomicSerial}, {@code encodeNested}
     * encodes it natively -- exactly the bytes a hostile peer would place in a nested slot.
     * The encode-side ctor does NOT trip the decode probe.
     */
    private static byte[] foreignSerializerNestedBytes() throws Exception {
        byte[] bytes = ObjectCodec.encodeNested(
                new ForeignSerializer(new ProbeValue()), "hostile", 0);
        // Encoding must not have tripped the DECODE probe (encode uses the (ProbeValue) ctor).
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "encode-side construction must not trip the decode probe");
        return bytes;
    }

    // =========================================================================
    // THE ATTACK: foreign serializer named in a concrete X500Principal slot.
    // =========================================================================

    @Test
    void foreignSerializerInConcreteSlotIsRejectedBeforeConstruction() throws Exception {
        byte[] hostile = foreignSerializerNestedBytes();

        // Declared slot type X500Principal; wire leaf ForeignSerializer (replaceObType=ProbeValue).
        // X500Principal.isAssignableFrom(ForeignSerializer)=false AND
        // X500Principal.isAssignableFrom(ProbeValue)=false -> REJECT (clause 2), before ctor.
        IOException ex = assertThrows(IOException.class, () ->
                ObjectCodec.decodeNested(hostile, X500Principal.class, 0, null, ResolutionContext.NONE));

        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "PROOF: the foreign serializer's (GetArg) ctor MUST NOT have run -- decode "
                + "admission fails closed before construction; got exception " + ex);
        assertTrue(ex.getMessage() == null || ex.getMessage().contains("not admissible")
                        || ex.getMessage().contains("admissible"),
                "expected a fail-closed admission error, got: " + ex);
    }

    /**
     * The same rejection holds for any narrowly-typed slot the foreign serializer's
     * {@code replaceObType} ({@code ProbeValue}) is not assignable to -- here {@code NestedValue}.
     */
    @Test
    void foreignSerializerInUnrelatedConcreteSlotIsRejectedBeforeConstruction() throws Exception {
        byte[] hostile = foreignSerializerNestedBytes();

        assertThrows(IOException.class, () ->
                ObjectCodec.decodeNested(hostile, NestedValue.class, 0, null, ResolutionContext.NONE));
        assertFalse(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "the foreign serializer's ctor must not run for an unrelated concrete slot");
    }

    // =========================================================================
    // LEGITIMATE: @Serializer substitution for the matching declared type still decodes.
    // =========================================================================

    @Test
    void legitimateX500PrincipalSerializerSubstitutionStillDecodes() throws Exception {
        X500Principal p = new X500Principal(DN);
        // encodeNested substitutes the registered X500PrincipalSerializer for the X500Principal.
        byte[] wire = ObjectCodec.encodeNested(p, "principal", 0);

        // Declared X500Principal; wire leaf X500PrincipalSerializer (replaceObType=X500Principal):
        // clause 2 admits (X500Principal.isAssignableFrom(X500Principal)=true), then resolve()
        // rebuilds the X500Principal via readResolve().
        Object decoded = ObjectCodec.decodeNested(wire, X500Principal.class, 0, null,
                ResolutionContext.NONE);
        assertInstanceOf(X500Principal.class, decoded, "legit @Serializer substitution must decode");
        assertEquals(p, decoded, "X500Principal must round-trip by value through the typed gate");
    }

    // =========================================================================
    // LEGITIMATE: ordinary polymorphism (declared supertype, wire subtype) not regressed.
    // =========================================================================

    @Test
    void legitimatePolymorphicSubtypeStillDecodes() throws Exception {
        NestedValueSub sub = new NestedValueSub(42, "hello", "extra");
        byte[] wire = ObjectCodec.encodeNested(sub, "inner", 0);

        // Declared NestedValue (supertype); wire leaf NestedValueSub: clause 1 admits.
        Object decoded = ObjectCodec.decodeNested(wire, NestedValue.class, 0, null,
                ResolutionContext.NONE);
        assertInstanceOf(NestedValueSub.class, decoded,
                "a declared-supertype slot must still decode a wire subtype (polymorphism)");
        assertEquals(sub, decoded);
    }

    // =========================================================================
    // RESIDUAL (documented, NOT closed): a genuinely Object-typed slot admits any leaf,
    // so the foreign serializer's ctor DOES run. This proves the gate closes concrete
    // fields only -- broad/Object slots still rely on the ATOMIC gate + check(GetArg).
    // =========================================================================

    @Test
    void objectTypedSlotIsTheDocumentedResidual_foreignSerializerConstructs() throws Exception {
        byte[] hostile = foreignSerializerNestedBytes();

        // Declared Object.class: clause 1 (Object.isAssignableFrom(anything)=true) admits, so the
        // foreign serializer IS constructed here -- the acknowledged residual for broad slots.
        Object decoded = ObjectCodec.decodeNested(hostile, Object.class, 0, null,
                ResolutionContext.NONE);
        assertTrue(ForeignSerializer.DECODE_CONSTRUCTED.get(),
                "RESIDUAL: an Object-typed slot is NOT closed by this gate; the serializer's "
                + "ctor runs (bounded only by the ATOMIC gate + check(GetArg))");
        // readResolve rebuilt the ProbeValue.
        assertInstanceOf(ProbeValue.class, decoded);
    }
}
