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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;

import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Option B: an {@code Enum} sitting in a polymorphic ({@code @AtomicSerial} / interface / abstract)
 * slot is encoded as a self-describing {@code [7]} CTX_ENUM leaf and decoded via
 * {@link Enum#valueOf} through the endpoint loader, gated by declared-type assignability.
 *
 * <p>Motivating real graph (the Layer-11 activatable-reggie failure): {@code
 * net.jini.core.constraint.AtomicInputValidation} is a {@code public enum implements
 * InvocationConstraint}; its five peer constraints are migrated {@code @AtomicSerial} classes. The
 * field {@code InvocationConstraints.reqs} is declared {@code InvocationConstraint[]} (polymorphic
 * "@AtomicSerial" slot), so the enum element must travel as an enum leaf, not an @AtomicSerial
 * hierarchy.
 */
class EnumInPolymorphicSlotTest {

    private static final Tag CTX_ENUM = new Tag(Tag.CLASS_CONTEXT, true, 7);

    // =========================================================================
    // Core: enum in a polymorphic @AtomicSerial slot round-trips to the same singleton,
    // byte-canonical.
    // =========================================================================

    @Test
    void enumInPolymorphicSlot_roundTripsToSameSingleton() throws Exception {
        byte[] wire = ObjectCodec.encodeNested(AtomicInputValidation.YES, "req", 0);

        // The per-value discriminator is the [7] CTX_ENUM tag, NOT a nested SEQUENCE.
        assertEquals(CTX_ENUM, new au.net.zeus.jgdms.der.DerReader(wire).peekTag(),
                "an enum in a polymorphic slot must carry the [7] CTX_ENUM discriminator");

        Object decoded = ObjectCodec.decodeNested(
                wire, InvocationConstraint.class, 0, null, ResolutionContext.NONE);
        assertSame(AtomicInputValidation.YES, decoded,
                "an enum must round-trip to the identical singleton (== , not just equals)");
    }

    @Test
    void enumEncoding_isCanonical_byteStable() throws Exception {
        byte[] a = ObjectCodec.encodeNested(AtomicInputValidation.NO, "req", 0);
        byte[] b = ObjectCodec.encodeNested(AtomicInputValidation.NO, "req", 0);
        assertArrayEquals(a, b, "enum-by-name must be canonical: one encoding per constant");

        // Re-encoding the decoded value reproduces the exact bytes.
        Object decoded = ObjectCodec.decodeNested(a, InvocationConstraint.class, 0, null,
                ResolutionContext.NONE);
        byte[] reencoded = ObjectCodec.encodeNested(decoded, "req", 0);
        assertArrayEquals(a, reencoded, "decode->encode must reproduce the canonical bytes");
    }

    // =========================================================================
    // The actual failing shape: InvocationConstraints whose reqs[] contains AtomicInputValidation.
    // =========================================================================

    @Test
    void invocationConstraints_withEnumReq_roundTripsThroughStream() throws Exception {
        InvocationConstraints original =
                new InvocationConstraints(AtomicInputValidation.YES, null);

        InvocationConstraints decoded = streamRoundTrip(original);
        assertEquals(original, decoded, "InvocationConstraints{AtomicInputValidation.YES} must round-trip");
        assertTrue(decoded.requirements().contains(AtomicInputValidation.YES),
                "the enum requirement must survive the round-trip as the singleton");
    }

    /**
     * Mixed reqs[] holding BOTH an enum constraint (AtomicInputValidation) and an @AtomicSerial
     * class constraint (Integrity) -- both must round-trip in the same array.
     */
    @Test
    void invocationConstraints_mixedEnumAndClassReqs_roundTrip() throws Exception {
        InvocationConstraints original = new InvocationConstraints(
                new InvocationConstraint[] { Integrity.YES, AtomicInputValidation.YES },
                null);

        InvocationConstraints decoded = streamRoundTrip(original);
        assertEquals(original, decoded, "mixed enum + @AtomicSerial-class reqs must round-trip");
        assertTrue(decoded.requirements().contains(Integrity.YES), "class constraint must survive");
        assertTrue(decoded.requirements().contains(AtomicInputValidation.YES), "enum constraint must survive");
    }

    // =========================================================================
    // Enum in a Set<InvocationConstraint> (polymorphic element) canonicalises deterministically.
    // =========================================================================

    @Test
    void enumInSet_canonicalises_orderIndependent() throws Exception {
        String token = SchemaGenerator.collectionWireType(java.util.HashSet.class, "@AtomicSerial");

        Set<InvocationConstraint> s1 = new LinkedHashSet<>();
        s1.add(AtomicInputValidation.YES);
        s1.add(AtomicInputValidation.NO);
        Set<InvocationConstraint> s2 = new LinkedHashSet<>();
        s2.add(AtomicInputValidation.NO);   // reversed insertion order
        s2.add(AtomicInputValidation.YES);

        byte[] enc1 = ObjectCodec.encodeCollection(s1, token, "s", 0);
        byte[] enc2 = ObjectCodec.encodeCollection(s2, token, "s", 0);
        assertArrayEquals(enc1, enc2,
                "a Set of enums must canonicalise to identical bytes regardless of insertion order");

        Object decoded = ObjectCodec.decodeCollection(enc1, token, 0, null, ResolutionContext.NONE);
        assertTrue(decoded instanceof Set, "set: token must decode to a Set");
        assertTrue(((Set<?>) decoded).contains(AtomicInputValidation.YES));
        assertTrue(((Set<?>) decoded).contains(AtomicInputValidation.NO));
    }

    // =========================================================================
    // Enum in an Any-typed slot ([20] EXPLICIT wrapping the [7] enum leaf) round-trips: the Any
    // path routes its inner bytes through the same decodeNested, so encode and decode agree.
    // (Previously an enum in an Any slot was an encode-time hard failure.)
    // =========================================================================

    @Test
    void enumInAnySlot_roundTrips() throws Exception {
        byte[] any = AnyCodec.encode(AtomicInputValidation.YES, "elem", 0);
        Object decoded = AnyCodec.decode(new au.net.zeus.jgdms.der.DerReader(any), 0, null,
                ResolutionContext.NONE);
        assertSame(AtomicInputValidation.YES, decoded,
                "an enum in an Any slot must round-trip to the identical singleton");
    }

    // =========================================================================
    // Decode-admission: fail-closed BEFORE resolution when the enum class is not assignable to
    // the declared slot type; fail-closed on an unknown constant name.
    // =========================================================================

    @Test
    void enum_notAssignableToDeclaredSlot_failsClosedBeforeResolution() throws Exception {
        byte[] wire = ObjectCodec.encodeNested(AtomicInputValidation.YES, "req", 0);

        // Declared slot X500Principal; the wire enum AtomicInputValidation is NOT assignable to it.
        DerException ex = assertThrows(DerException.class, () ->
                ObjectCodec.decodeNested(wire, X500Principal.class, 0, null, ResolutionContext.NONE));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("admissible"),
                "an enum named into a slot it is not assignable to must fail closed as an "
                + "admission error; got: " + ex);
    }

    @Test
    void unknownEnumConstant_failsClosed() throws Exception {
        // Hand-craft a well-formed [7] CTX_ENUM naming a bogus constant.
        byte[] wire = enumLeaf(AtomicInputValidation.class.getName(), "MAYBE"); // no such constant

        DerException ex = assertThrows(DerException.class, () ->
                ObjectCodec.decodeNested(wire, InvocationConstraint.class, 0, null, ResolutionContext.NONE));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("unknown enum constant"),
                "an unknown enum constant must fail closed; got: " + ex);
    }

    // =========================================================================
    // Negative / malformed [7] leaves (S2): a [7] naming a non-enum class, and a [7]
    // naming an absent class -- both fail closed with a DerException.
    // =========================================================================

    @Test
    void ctxEnumNamingNonEnumClass_failsClosed() throws Exception {
        // Integrity is a NON-enum InvocationConstraint class -> hits the isEnum()==false branch
        // (checked before admission), even though it IS assignable to the InvocationConstraint slot.
        byte[] wire = enumLeaf(Integrity.class.getName(), "YES");

        DerException ex = assertThrows(DerException.class, () ->
                ObjectCodec.decodeNested(wire, InvocationConstraint.class, 0, null, ResolutionContext.NONE));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("is not an enum"),
                "a [7] leaf naming a non-enum class must fail closed; got: " + ex);
    }

    @Test
    void ctxEnumNamingAbsentClass_failsClosed() throws Exception {
        // A class name that resolves nowhere -> loadClass CNFE, wrapped as DerException.
        byte[] wire = enumLeaf("com.example.nonexistent.NoSuchEnum", "ALPHA");

        DerException ex = assertThrows(DerException.class, () ->
                ObjectCodec.decodeNested(wire, InvocationConstraint.class, 0, null, ResolutionContext.NONE));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("cannot load class"),
                "a [7] leaf naming an unloadable class must fail closed; got: " + ex);
    }

    // =========================================================================
    // G13: the security-critical ORDERING made observable -- admission is enforced BEFORE the
    // enum class is initialized, so a non-assignable enum named into a slot never triggers its
    // <clinit>. ProbeEnum's static initializer flips a flag on a SEPARATE holder class; that flag
    // is read WITHOUT initializing ProbeEnum (reading ClinitProbe.ENUM_CLINIT_RAN initializes only
    // ClinitProbe). loadClass (init-free) + isEnum() (init-free) + the admission rejection all run
    // before Enum.valueOf, the only step that would initialize ProbeEnum. ProbeEnum is used by no
    // other test, and this test only ever references it by name (ProbeEnum.class.getName() loads
    // but does not initialize), so it is guaranteed uninitialized at decode time.
    // =========================================================================

    @Test
    void nonAssignableEnum_rejectedBeforeClassInitialization() throws Exception {
        // ProbeEnum does NOT implement InvocationConstraint -> not assignable to the slot.
        byte[] wire = enumLeaf(ProbeEnum.class.getName(), "ALPHA");

        // Sanity: reading the flag initializes ONLY the holder (default false); ProbeEnum stays
        // uninitialized because nothing has actively used it yet.
        assertFalse(ClinitProbe.ENUM_CLINIT_RAN,
                "precondition: ProbeEnum's <clinit> must not have run before decode");

        DerException ex = assertThrows(DerException.class, () ->
                ObjectCodec.decodeNested(wire, InvocationConstraint.class, 0, null, ResolutionContext.NONE));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("admissible"),
                "a non-assignable enum must fail the admission gate; got: " + ex);

        // THE OBSERVABLE ORDERING GUARANTEE: admission fail-closed happened BEFORE Enum.valueOf,
        // so ProbeEnum's <clinit> never ran.
        assertFalse(ClinitProbe.ENUM_CLINIT_RAN,
                "PROOF (G13): admissibleConstructClass rejected the enum BEFORE Enum.valueOf could "
                + "trigger ProbeEnum.<clinit> -- the class was never initialized on the reject path");
    }

    // =========================================================================
    // Helpers + probe fixtures
    // =========================================================================

    /** Builds a well-formed {@code [7]} CTX_ENUM leaf naming {@code className} + {@code constant}. */
    private static byte[] enumLeaf(String className, String constant) throws DerException {
        byte[] clsName = DerWriter.writeUtf8String(className);
        byte[] name    = DerWriter.writeUtf8String(constant);
        byte[] content = new byte[clsName.length + name.length];
        System.arraycopy(clsName, 0, content, 0, clsName.length);
        System.arraycopy(name, 0, content, clsName.length, name.length);
        return DerWriter.writeTlv(CTX_ENUM, content);
    }

    /**
     * Flag holder written by {@link ProbeEnum}'s static initializer. Kept SEPARATE from the probe
     * enum so the flag can be read (which initializes only this holder) without initializing
     * {@code ProbeEnum} -- the whole point of the ordering guard test.
     */
    static final class ClinitProbe {
        static volatile boolean ENUM_CLINIT_RAN = false;
        private ClinitProbe() {}
    }

    /**
     * A probe enum used by NO other test. It deliberately does NOT implement
     * {@link InvocationConstraint}, so it is never assignable to that slot. Its {@code <clinit>}
     * (which constructs the constants and runs the static block) flips
     * {@link ClinitProbe#ENUM_CLINIT_RAN} -- observable only if {@code Enum.valueOf} is reached.
     */
    enum ProbeEnum {
        ALPHA, BETA;
        static { ClinitProbe.ENUM_CLINIT_RAN = true; }
    }

    private static InvocationConstraints streamRoundTrip(InvocationConstraints original)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            out.writeObject(original);
        }
        try (DerMarshalInputStream in =
                     new DerMarshalInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (InvocationConstraints) in.readObject();
        }
    }
}
