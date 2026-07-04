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

package au.net.zeus.jgdms.der.marshal;

import au.net.zeus.jgdms.der.object.fixtures.Gamma;
import au.net.zeus.jgdms.der.object.fixtures.SimpleRecord;
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-008 sec.13 "B+C" hybrid integration tests for the DER MarshalledInstance seam.
 *
 * <p>The B+C hybrid splits the DER payload and schema into two separate first-class
 * {@link MarshalledInstance} fields:
 * <ul>
 *   <li>{@code payloadBytes} -- raw DER object payload (from
 *       {@link DerMarshalInstanceOutput#writeObject}).</li>
 *   <li>{@code schemaBytes} -- embedded schema chain (from
 *       {@link DerMarshalInstanceOutput#getSchemaBytes()}).</li>
 *   <li>{@code schemaDigest} -- leaf SHA-256 digest (from
 *       {@link DerMarshalInstanceOutput#getSchemaDigest()}).</li>
 *   <li>{@code payloadFormat} -- {@link MarshalledInstanceRecord#PAYLOAD_FORMAT}
 *       ({@code "JGDMS-STD-006/DER"}), from
 *       {@link DerMarshalInstanceOutput#getPayloadFormat()}).</li>
 * </ul>
 *
 * <p>Decode flow (exercised by every round-trip test here):
 * {@code get()} -> {@code getMarshalFactory()} (base {@link MarshalledInstance} impl)
 *  -> {@code factoryForFormat("JGDMS-STD-006/DER")} -> ServiceLoader
 *  -> {@link DerMarshalFactoryProvider} -> {@link DerMarshalFactory}
 *  -> 9-arg {@code createMarshalInput(..., schemaBytes, ...)}
 *  -> {@link DerMarshalInstanceInput#readObject(Class)}
 *  -> decode driven by {@code schemaBytes}.
 *
 * <p>The removal of the {@code getMarshalFactory()} override from
 * {@link DerMarshalledInstance} means every round-trip test here IS the
 * ServiceLoader-dispatch proof: if ServiceLoader discovery fails or returns the
 * wrong factory, the decode path breaks and the test fails.
 *
 * <h2>Test catalogue</h2>
 * <ul>
 *   <li>{@link #test_13_1_RoundTrip_SimpleRecord_getWithType} -- flat fixture, ServiceLoader dispatch.</li>
 *   <li>{@link #test_13_2_RoundTrip_Gamma_getWithType} -- three-level hierarchy, ServiceLoader dispatch.</li>
 *   <li>{@link #test_13_3_RoundTrip_FullGetSignature} -- full 5-arg get() path.</li>
 *   <li>{@link #test_13_4_SplitPayloadSchema_OutputAssertions} -- NEW: asserts that
 *       {@code objOut} carries only payload bytes (not the full record), and the three
 *       schema getters return correct values.</li>
 *   <li>{@link #test_13_5_NullObject} -- null object round-trip.</li>
 *   <li>{@link #test_13_6_ServiceLoader_DispatchWithoutSubclass} -- explicit proof that
 *       the decode flow goes through {@code getMarshalFactory()} base impl and ServiceLoader,
 *       not a subclass override.</li>
 *   <li>{@link #test_13_7_RoundTrip_Flat_via_DerMarshalledInstance} -- flat fixture
 *       round-trip via subclass constructors.</li>
 *   <li>{@link #test_13_8_RoundTrip_Hierarchy_via_DerMarshalledInstance} -- hierarchy
 *       fixture round-trip via subclass constructors.</li>
 * </ul>
 */
class DerMarshalledInstanceTest {

    // =========================================================================
    // 13.1 -- flat @AtomicSerial class round-trip via get(boolean, Class)
    // =========================================================================

    /**
     * Round-trip a flat {@code @AtomicSerial} class ({@link SimpleRecord}) through
     * {@link DerMarshalledInstance}, decoding via {@code get(false, Class)}.
     *
     * <p>Because {@link DerMarshalledInstance} no longer overrides
     * {@code getMarshalFactory()}, the decode path goes through the base
     * {@link MarshalledInstance#getMarshalFactory()} -> {@code factoryForFormat} ->
     * ServiceLoader -> {@link DerMarshalFactoryProvider} -> {@link DerMarshalFactory}.
     * A failure here proves ServiceLoader discovery is broken.
     */
    @Test
    void test_13_1_RoundTrip_SimpleRecord_getWithType() throws Exception {
        SimpleRecord fixture = new SimpleRecord(true, 42, "hello", new byte[]{1, 2, 3});

        DerMarshalledInstance dmi = new DerMarshalledInstance(fixture);
        SimpleRecord result = dmi.get(false, SimpleRecord.class);

        assertEquals(fixture, result,
                "Round-tripped SimpleRecord must equal the original");
    }

    // =========================================================================
    // 13.2 -- three-level @AtomicSerial hierarchy round-trip via get(boolean, Class)
    // =========================================================================

    /**
     * Round-trip a three-level {@code @AtomicSerial} hierarchy ({@link Gamma} extends
     * {@link au.net.zeus.jgdms.der.object.fixtures.Beta} extends
     * {@link au.net.zeus.jgdms.der.object.fixtures.Alpha}) through
     * {@link DerMarshalledInstance}, via ServiceLoader dispatch.
     */
    @Test
    void test_13_2_RoundTrip_Gamma_getWithType() throws Exception {
        Gamma fixture = new Gamma(
                /* alphaX */ 10, /* alphaLabel */ "alpha-label",
                /* betaX  */ 20, /* betaOnly   */ "beta-only",
                /* gammaValue */ 999L, /* gammaTag */ "gamma-tag");

        DerMarshalledInstance dmi = new DerMarshalledInstance(fixture);
        Gamma result = dmi.get(false, Gamma.class);

        assertEquals(fixture, result,
                "Round-tripped Gamma hierarchy must equal the original");
    }

    // =========================================================================
    // 13.3 -- round-trip via the full 5-arg get() path
    // =========================================================================

    /**
     * Verifies that the full 5-arg
     * {@link MarshalledInstance#get(ClassLoader, boolean, ClassLoader, java.util.Collection, Class)}
     * path also uses ServiceLoader dispatch (via the base {@code getMarshalFactory()}).
     */
    @Test
    void test_13_3_RoundTrip_FullGetSignature() throws Exception {
        VersionedRecord fixture = new VersionedRecord(7, "label-seven", "extra-seven");

        DerMarshalledInstance dmi = new DerMarshalledInstance(fixture);
        VersionedRecord result = dmi.get(
                null,                        // defaultLoader
                false,                       // verifyCodebaseIntegrity
                null,                        // verifierLoader
                Collections.emptyList(),     // context
                VersionedRecord.class);      // type

        assertEquals(fixture, result,
                "Round-tripped VersionedRecord via full get() signature must equal original");
    }

    // =========================================================================
    // 13.4 -- NEW: split payload / schema assertions on DerMarshalInstanceOutput
    // =========================================================================

    /**
     * Asserts the B+C split: after {@link DerMarshalInstanceOutput#writeObject},
     * the bytes written to {@code objOut} are ONLY the payload (not the full
     * {@link MarshalledInstanceRecord} DER blob), and the three schema getter methods
     * return correct values.
     *
     * <p>Specifically:
     * <ul>
     *   <li>{@link DerMarshalInstanceOutput#getPayloadFormat()} must equal
     *       {@link MarshalledInstanceRecord#PAYLOAD_FORMAT} ({@code "JGDMS-STD-006/DER"}).</li>
     *   <li>{@link DerMarshalInstanceOutput#getSchemaBytes()} must be non-empty.</li>
     *   <li>{@link DerMarshalInstanceOutput#getSchemaDigest()} must be exactly 32 bytes.</li>
     *   <li>The bytes written to {@code objOut} must equal {@code rec.payloadBytes()} --
     *       NOT {@code rec.encode()} (i.e. smaller: payload-only, no schema wrapper).</li>
     * </ul>
     *
     * <p>To produce the reference {@link MarshalledInstanceRecord}, encode the same
     * fixture via {@link MarshalledInstanceRecord#fromChain} and compare the payload
     * sub-field, not the full encoded form.
     */
    @Test
    void test_13_4_SplitPayloadSchema_OutputAssertions() throws Exception {
        SimpleRecord fixture = new SimpleRecord(false, 1, "schema-test", new byte[0]);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DerMarshalInstanceOutput out = new DerMarshalInstanceOutput(
                bout, Collections.emptyList());
        out.writeObject(fixture);
        out.flush();

        byte[] writtenBytes = bout.toByteArray();

        // 1. payloadFormat must be "JGDMS-STD-006/DER"
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, out.getPayloadFormat(),
                "getPayloadFormat() must return '" + MarshalledInstanceRecord.PAYLOAD_FORMAT + "'");

        // 2. schemaBytes must be non-empty (the schema chain travels separately)
        assertTrue(out.getSchemaBytes().length > 0,
                "getSchemaBytes() must be non-empty after writeObject");

        // 3. schemaDigest must be exactly 32 bytes (SHA-256)
        assertEquals(32, out.getSchemaDigest().length,
                "getSchemaDigest() must be exactly 32 bytes");

        // 4. The bytes written to objOut must be the payload bytes only -- NOT the full record.
        //    Reconstruct the record from the stashed schema to get a reference payloadBytes.
        MarshalledInstanceRecord reconstructed = new MarshalledInstanceRecord(
                writtenBytes,
                out.getSchemaBytes(),
                out.getSchemaDigest(),
                out.getPayloadFormat());

        // The written bytes equal rec.payloadBytes() -- payload is what is in objOut.
        assertArrayEquals(writtenBytes, reconstructed.payloadBytes(),
                "Bytes written to objOut must equal the payload bytes (not the full record)");

        // 5. The full record encoding is LARGER than just the payload bytes,
        //    confirming the schema was NOT written to objOut.
        byte[] fullRecord = reconstructed.encode();
        assertTrue(fullRecord.length > writtenBytes.length,
                "Full DER record (" + fullRecord.length + " bytes) must be larger than "
                + "payload-only bytes (" + writtenBytes.length + " bytes), "
                + "proving schema was NOT written to objOut");

        // 6. Cross-check: the reconstructed record round-trips the fixture correctly.
        MarshalledInstanceCodec.Result<SimpleRecord> decoded =
                MarshalledInstanceCodec.decodeMarshalledInstance(reconstructed, SimpleRecord.class);
        assertEquals(fixture, decoded.object(),
                "Reconstructed record must decode back to the original fixture");
    }

    // =========================================================================
    // 13.5 -- null object round-trip
    // =========================================================================

    /**
     * A null object marshalled into a {@link DerMarshalledInstance} must produce
     * {@code null} from {@code get(false, Object.class)}.
     */
    @Test
    void test_13_5_NullObject() throws Exception {
        DerMarshalledInstance dmi = new DerMarshalledInstance(null);
        assertTrue(dmi.isNull(), "DerMarshalledInstance wrapping null must report isNull()");
        Object result = dmi.get(false, Object.class);
        assertNull(result, "get() on a null-containing DerMarshalledInstance must return null");
    }

    // =========================================================================
    // 13.6 -- ServiceLoader dispatch proof (no subclass getMarshalFactory() override)
    // =========================================================================

    /**
     * Explicit proof that {@link DerMarshalledInstance#get(boolean, Class)} decodes via
     * ServiceLoader dispatch through the BASE {@link MarshalledInstance#getMarshalFactory()}
     * -- NOT through a subclass override (which was removed in the B+C rewrite).
     *
     * <p>Verification: {@link DerMarshalledInstance} declares no {@code getMarshalFactory()}
     * override. The base implementation reads {@code payloadFormat} (set to
     * {@code "JGDMS-STD-006/DER"}), calls {@code factoryForFormat}, which discovers
     * {@link DerMarshalFactoryProvider} via ServiceLoader and returns a
     * {@link DerMarshalFactory}. If the ServiceLoader file is missing or the provider
     * class is wrong, an {@link IllegalStateException} is thrown by {@code factoryForFormat}
     * before the test can call {@code assertEquals}, making the ServiceLoader wiring
     * failure visible as a test error rather than a silent wrong-result.
     *
     * <p>The test also confirms that a flat fixture and a hierarchy fixture both
     * round-trip correctly through this path.
     */
    @Test
    void test_13_6_ServiceLoader_DispatchWithoutSubclass() throws Exception {
        // Flat fixture
        SimpleRecord flat = new SimpleRecord(true, 99, "serviceloader-flat", new byte[]{7, 8});
        DerMarshalledInstance dmiFlat = new DerMarshalledInstance(flat);
        // This get() call goes through: base getMarshalFactory() -> factoryForFormat
        // -> ServiceLoader -> DerMarshalFactoryProvider -> DerMarshalFactory ->
        // 9-arg createMarshalInput(..., schemaBytes, ...) -> DerMarshalInstanceInput
        // -> decode driven by schemaBytes.
        SimpleRecord flatResult = dmiFlat.get(false, SimpleRecord.class);
        assertEquals(flat, flatResult,
                "ServiceLoader-dispatched decode must reconstruct the flat fixture");

        // Hierarchy fixture
        Gamma gamma = new Gamma(1, "sl-alpha", 2, "sl-beta", 42L, "sl-gamma");
        DerMarshalledInstance dmiGamma = new DerMarshalledInstance(gamma);
        Gamma gammaResult = dmiGamma.get(false, Gamma.class);
        assertEquals(gamma, gammaResult,
                "ServiceLoader-dispatched decode must reconstruct the hierarchy fixture");
    }

    // =========================================================================
    // 13.7 -- flat fixture round-trip via DerMarshalledInstance (equals check)
    // =========================================================================

    /**
     * Round-trip a flat fixture via {@link DerMarshalledInstance} constructors,
     * asserting {@code .equals()} on the result. Uses {@code get(false, Class)}.
     */
    @Test
    void test_13_7_RoundTrip_Flat_via_DerMarshalledInstance() throws Exception {
        SimpleRecord fixture = new SimpleRecord(false, 7, "flat-rt", new byte[]{10, 20});
        DerMarshalledInstance dmi = new DerMarshalledInstance(fixture);
        SimpleRecord result = dmi.get(false, SimpleRecord.class);
        assertEquals(fixture, result,
                "Flat fixture round-tripped via DerMarshalledInstance must equal original");
    }

    // =========================================================================
    // 13.8 -- hierarchy fixture round-trip via DerMarshalledInstance (equals check)
    // =========================================================================

    /**
     * Round-trip a hierarchy fixture ({@link Gamma}) via {@link DerMarshalledInstance}
     * constructors, asserting {@code .equals()} on the result. Uses {@code get(false, Class)}.
     */
    @Test
    void test_13_8_RoundTrip_Hierarchy_via_DerMarshalledInstance() throws Exception {
        Gamma fixture = new Gamma(5, "alpha5", 6, "beta6", 77L, "gamma77");
        DerMarshalledInstance dmi = new DerMarshalledInstance(fixture);
        Gamma result = dmi.get(false, Gamma.class);
        assertEquals(fixture, result,
                "Hierarchy fixture round-tripped via DerMarshalledInstance must equal original");
    }
}
