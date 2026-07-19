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
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.object.fixtures.Greeter;
import au.net.zeus.jgdms.der.object.fixtures.GreeterHandler;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import net.jini.core.event.RemoteEvent;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip / determinism / adversarial tests for {@code net.jini.core.event.RemoteEvent.source}
 * routed through the STD-006 {@code Any} form -- SOW {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md}
 * test-plan items 1, 3, 5, and 7. This is the demonstrated real bug this SOW exists to fix:
 * {@code RemoteEvent}'s {@code serialForm()} declares {@code source} as {@code Object.class} and,
 * before this change, {@code SchemaGenerator} hard-rejected that at schema-generation time for
 * EVERY {@code RemoteEvent} (and every subclass), regardless of what value {@code source} held.
 *
 * <h2>IMPORTANT deviation from the SOW's premise -- read before editing this file</h2>
 * <p><b>{@code SchemaGenerator.generateChain(RemoteEvent.class)} (the real, fully-automatic
 * production path) still throws {@link au.net.zeus.jgdms.der.DerException} today</b> -- not on
 * {@code source} (fixed by this change) but on the SEPARATE, independent {@code handback} field
 * (declared {@code java.rmi.MarshalledObject.class}). This was NOT surfaced by the SOW's source
 * reading because {@code SchemaGenerator}'s per-class field loop throws on the FIRST unsupported
 * field it reaches, and {@code source} (index 0) always threw first, masking {@code handback}
 * (index 3) entirely. {@code MarshalledObject} is DELIBERATELY not in the active DER serializer
 * registry (see {@code jgdms-der/src/main/resources/META-INF/jgdms/der-serializers}: "Throwable,
 * Properties, and MarshalledObject are DEFERRED (unresolved security/canonicality defects)") --
 * {@code org.apache.river.api.io.MarshalledObjectSerializer} exists but composes the SAME
 * {@code MarshalledInstance} wire-controlled-{@code payloadFormat} downgrade hazard this SOW's own
 * "Finding 3" / follow-up item already tracks as a separate HIGH-severity item. Flipping that
 * registry is a versioned-schema, board-review decision (per the registry file's own comment) --
 * explicitly out of THIS SOW's scope, not a call this test suite makes unilaterally.
 *
 * <p>Consequence: {@code RemoteEvent} (and every subclass) still cannot be schema-generated
 * end-to-end via {@link SchemaGenerator#generateChain(Class)} today. These tests instead use an
 * EXPLICIT schema covering {@code RemoteEvent}'s real {@code source}/{@code eventID}/{@code
 * seqNum}/{@code miHandback} fields (the exact wire-type tokens {@code SchemaGenerator} produces
 * for each, via {@link SchemaGenerator#ANY} and {@link SchemaGenerator#toWireType(Class, Class)})
 * while omitting the independently-blocked {@code handback} field, so the {@code source} fix can
 * be verified end-to-end against the REAL {@code RemoteEvent} class (real {@code serialize()}/
 * {@code check()}/{@code (GetArg)} constructor) without being masked by the unrelated blocker. See
 * this agent's final report for the full analysis; the {@code handback}/{@code MarshalledObject}
 * gap is reported as a separate finding, not fixed here.
 */
class RemoteEventAnySourceFieldTest {

    /**
     * The schema {@code SchemaGenerator} would produce for {@code RemoteEvent} if not for the
     * independent {@code handback: MarshalledObject.class} blocker documented in the class
     * Javadoc above -- built field-by-field from the SAME wire-type derivation
     * {@code SchemaGenerator} uses ({@link SchemaGenerator#ANY} for {@code source} post-fix;
     * {@link SchemaGenerator#toWireType(Class, Class)} for the scalar/{@code @AtomicSerial}
     * fields), omitting only {@code handback}.
     */
    private static AtomicSerialSchemaRecord remoteEventSchema() throws Exception {
        return new AtomicSerialSchemaRecord(
                RemoteEvent.class.getName(), (byte[]) null,
                List.of(
                        new AtomicSerialFieldDef("source", SchemaGenerator.ANY),
                        new AtomicSerialFieldDef("eventID",
                                SchemaGenerator.toWireType(long.class, RemoteEvent.class)),
                        new AtomicSerialFieldDef("seqNum",
                                SchemaGenerator.toWireType(long.class, RemoteEvent.class)),
                        new AtomicSerialFieldDef("miHandback",
                                SchemaGenerator.toWireType(MarshalledInstance.class, RemoteEvent.class))
                ));
    }

    private static byte[] encode(RemoteEvent in) throws Exception {
        return ObjectCodec.encode(in, RemoteEvent.class, remoteEventSchema());
    }

    private static RemoteEvent decode(byte[] bytes) throws Exception {
        return ObjectCodec.decode(RemoteEvent.class, remoteEventSchema(), bytes);
    }

    private static RemoteEvent roundTrip(RemoteEvent in) throws Exception {
        return decode(encode(in));
    }

    private static Greeter newGreeterProxy(String greeting) {
        return (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class},
                new GreeterHandler(greeting));
    }

    // =========================================================================
    // The independent handback:MarshalledObject blocker, pinned so a future fix to THAT is
    // visible here (this test must be revisited, not silently left stale, if it ever starts
    // passing -- at which point the real generateChain-based round trip should replace this
    // explicit-schema workaround).
    // =========================================================================

    @Test
    void schemaGenerateChain_stillFailsOnUnrelatedHandbackField_documentedGap() {
        au.net.zeus.jgdms.der.DerException ex = assertThrows(au.net.zeus.jgdms.der.DerException.class,
                () -> SchemaGenerator.generateChain(RemoteEvent.class),
                "if this stops throwing, the handback/MarshalledObject blocker has been "
                + "independently resolved -- replace this workaround schema with real "
                + "generateChain(RemoteEvent.class) throughout this file");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("MarshalledObject"),
                "the failure must be the handback field (MarshalledObject), not source "
                + "(source is fixed by this SOW); got: " + ex.getMessage());
    }

    @Test
    void schemaGenerator_toWireTypeClass_stillRejectsRawObject_byDesign() {
        // Item 1's patch-location note: the field-level Any interception lives in the
        // package-private deriveFieldWireType (non-collection branch), NOT in the public
        // toWireType(Class,...) -- patching the latter would also silently flip Object[]-typed
        // fields to array:any. So the PUBLIC toWireType(Object.class,...) must still hard-reject,
        // exactly as before this SOW; only the field-derivation entry point (exercised via the
        // real SchemaGenerator.generate/generateChain path in AnyFieldPositionConformanceTest and
        // the schemaGenerator_objectTypedField_resolvesToAny assertion there) sees the new rule.
        assertThrows(au.net.zeus.jgdms.der.DerException.class,
                () -> SchemaGenerator.toWireType(Object.class, RemoteEvent.class),
                "toWireType(Class,...) must still reject raw Object.class directly; only "
                + "deriveFieldWireType's non-collection branch intercepts it");
        assertEquals("any", SchemaGenerator.ANY);
    }

    // =========================================================================
    // Item 1 + 3: round-trip each production source shape; getSource() .equals() original.
    // =========================================================================

    @Test
    void roundTrip_atomicSerialProxySource() throws Exception {
        // Matches reggie/outrigger/norm/fiddler's real production shape: source is the service's
        // own @AtomicSerial proxy. Travels as Any [20].
        Foo source = new Foo(3, "lookup-service");
        RemoteEvent in = new RemoteEvent(source, 1L, 2L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        assertEquals(source, out.getSource(), "an @AtomicSerial source must round-trip by value");
    }

    @Test
    void roundTrip_integerSource_mustSucceed() throws Exception {
        // The demonstrated real case (BadEventCodebaseTest's "good events" leg): a bare
        // non-@AtomicSerial Integer source. MUST succeed via the [3] scalar arm, not degrade.
        RemoteEvent in = new RemoteEvent(Integer.valueOf(0), 1L, 2L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        assertEquals(Integer.valueOf(0), out.getSource(), "Integer source must round-trip via [3]");
    }

    @Test
    void roundTrip_stringSource_mustSucceed() throws Exception {
        RemoteEvent in = new RemoteEvent("a-string-source", 1L, 2L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        assertEquals("a-string-source", out.getSource(), "String source must round-trip via [8]");
    }

    @Test
    void roundTrip_booleanScalarSource_mustSucceed() throws Exception {
        // "at least one more boxed scalar" per the test plan.
        RemoteEvent in = new RemoteEvent(Boolean.TRUE, 1L, 2L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        assertEquals(Boolean.TRUE, out.getSource());
    }

    @Test
    void roundTrip_dynamicProxySource_mustSucceed() throws Exception {
        // Matches PolicyUpdateEvent/VerdictEvent's shape: source is a dynamic Proxy over an
        // @AtomicSerial InvocationHandler. Travels as Any [20] wrapping a [8] proxy record.
        Greeter proxy = newGreeterProxy("hello");
        RemoteEvent in = new RemoteEvent(proxy, 1L, 2L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        Object src = out.getSource();
        assertTrue(Proxy.isProxyClass(src.getClass()), "dynamic Proxy source must reconstruct as a Proxy");
        assertTrue(src instanceof Greeter);
        assertEquals("hello: world", ((Greeter) src).greet("world"),
                "reconstructed proxy source must actually dispatch through its decoded handler");
    }

    @Test
    void roundTrip_otherFields_unaffected() throws Exception {
        RemoteEvent in = new RemoteEvent(42, 111L, 222L, (MarshalledInstance) null);
        RemoteEvent out = roundTrip(in);
        assertEquals(111L, out.getID());
        assertEquals(222L, out.getSequenceNumber());
    }

    // =========================================================================
    // Item 5: determinism -- two independently-constructed, .equals() RemoteEvents produce
    // byte-identical source field TLVs.
    // =========================================================================

    @Test
    void determinism_integerSource_byteIdentical() throws Exception {
        RemoteEvent a = new RemoteEvent(Integer.valueOf(7), 1L, 1L, (MarshalledInstance) null);
        RemoteEvent b = new RemoteEvent(Integer.valueOf(7), 1L, 1L, (MarshalledInstance) null);
        byte[] ea = encode(a);
        byte[] eb = encode(b);
        assertArrayEquals(ea, eb, "two independent encodes of equal Integer-source RemoteEvents "
                + "must be byte-identical (fence (d) at field position)");
    }

    @Test
    void determinism_stringSource_byteIdentical() throws Exception {
        RemoteEvent a = new RemoteEvent(new String("same-source"), 1L, 1L, (MarshalledInstance) null);
        RemoteEvent b = new RemoteEvent(new String("same-source"), 1L, 1L, (MarshalledInstance) null);
        byte[] ea = encode(a);
        byte[] eb = encode(b);
        assertArrayEquals(ea, eb, "two independent encodes of equal String-source RemoteEvents "
                + "must be byte-identical");
    }

    // =========================================================================
    // Item 7: malformed/hostile source TLV surfaces as a loud decode failure from
    // RemoteEvent(GetArg) construction -- never a silent null source.
    // =========================================================================

    @Test
    void malformedSourceTlv_reservedTag_failsLoudNotSilentNull() throws Exception {
        byte[] corrupted = replaceSourceFieldTlv(validEncoding(),
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 15),
                        DerWriter.writeInteger(BigInteger.valueOf(1))));
        IOException ex = assertThrows(IOException.class, () -> decode(corrupted),
                "a reserved-tag Any source TLV must be rejected, never silently accepted as null");
        // Must NOT be RemoteEvent.check()'s "source cannot be null" message -- that would mean the
        // decode failure was masked into a generic null-check rather than surfacing the real cause.
        assertFalse(messageChainContains(ex, "source cannot be null"),
                "the decode failure must not be masked as a null-source invariant violation; "
                + "got: " + ex);
    }

    @Test
    void malformedSourceTlv_malformedAtomicObjectBody_failsLoud() throws Exception {
        byte[] bogus = DerWriter.writeOctetString(new byte[]{0x01, 0x02});
        byte[] anyObj = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 20), bogus);
        byte[] corrupted = replaceSourceFieldTlv(validEncoding(), anyObj);
        assertThrows(IOException.class, () -> decode(corrupted),
                "a malformed [20] atomicSerialObject source body must be rejected loudly");
    }

    @Test
    void malformedSourceTlv_deeplyNestedAny_rejectedBeforeStackOverflow() throws Exception {
        // G13-style RUN (not reasoned-about) adversarial input: a deeply-nested Any collection
        // chain at field position, confirming the fence (a) depth bound triggers before stack
        // exhaustion, specifically for RemoteEvent.source (not just a synthetic fixture).
        int depthLevels = ObjectCodec.MAX_NESTING + 40;
        byte[] node = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 30), DerWriter.writeSet(List.of()));
        for (int i = 0; i < depthLevels; i++) {
            byte[] setOfOne = DerWriter.writeSet(List.of(node));
            node = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 30), setOfOne);
        }
        byte[] deepNode = node;
        byte[] corrupted = replaceSourceFieldTlv(validEncoding(), deepNode);
        IOException ex = assertThrows(IOException.class, () -> decode(corrupted),
                "a deeply-nested Any RemoteEvent.source must be rejected by the depth guard, "
                + "not by StackOverflow");
        assertTrue(messageChainContains(ex, "nesting"), "got: " + ex);
    }

    @Test
    void nullSource_stillRejectedByRemoteEventCheck() throws Exception {
        // Sanity: RemoteEvent.check()'s own Valid.notNull("source cannot be null") invariant is
        // untouched by this change -- a genuinely wire-null source (the Any DER NULL sentinel,
        // 05 00) decoded via (GetArg) is still rejected by RemoteEvent's OWN check, not silently
        // accepted, and not by the Any decode machinery raising some other error. (The ordinary
        // value constructor's null rejection is a DIFFERENT, unrelated guard --
        // java.util.EventObject's own constructor -- so this is deliberately exercised via the
        // decode path, matching what a hostile/malformed wire NULL source actually triggers.)
        byte[] anyNull = new byte[]{0x05, 0x00}; // DER NULL: the Any "no value" sentinel
        byte[] corrupted = replaceSourceFieldTlv(validEncoding(), anyNull);
        InvalidObjectException ex = assertThrows(InvalidObjectException.class, () -> decode(corrupted),
                "a wire-NULL source must still be rejected by RemoteEvent.check()'s own "
                + "Valid.notNull, unchanged by this SOW");
        assertTrue(messageChainContains(ex, "source cannot be null"), "got: " + ex);
    }

    // -------------------------------------------------------------------------
    // Helpers: build a valid encoding, then splice a crafted TLV in place of the source field's
    // TLV (field index 0 in remoteEventSchema() order) inside RemoteEvent's private SEQUENCE.
    // -------------------------------------------------------------------------

    private static byte[] validEncoding() throws Exception {
        RemoteEvent in = new RemoteEvent(Integer.valueOf(0), 1L, 2L, (MarshalledInstance) null);
        return encode(in);
    }

    /**
     * Replaces the first field TLV (the {@code source} field, index 0 in {@link
     * #remoteEventSchema()} order) of the private SEQUENCE with {@code replacement}, leaving
     * every other field TLV untouched. Parses every field TLV explicitly (rather than assuming
     * fixed offsets) so this stays correct regardless of the other fields' encoded lengths.
     */
    private static byte[] replaceSourceFieldTlv(byte[] recordBytes, byte[] replacement)
            throws Exception {
        au.net.zeus.jgdms.der.DerReader outer = new au.net.zeus.jgdms.der.DerReader(recordBytes);
        au.net.zeus.jgdms.der.DerReader seq = outer.readSequence();
        java.util.List<byte[]> fieldTlvs = new java.util.ArrayList<>();
        boolean first = true;
        while (seq.hasMore()) {
            int fStart = seq.position();
            au.net.zeus.jgdms.der.DerReader.TlvHeader h = seq.readTlvHeader();
            seq.readRawContent(h.contentLength());
            int fEnd = seq.position();
            fieldTlvs.add(first ? replacement : seq.slice(fStart, fEnd));
            first = false;
        }
        return DerWriter.writeSequence(fieldTlvs);
    }

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
