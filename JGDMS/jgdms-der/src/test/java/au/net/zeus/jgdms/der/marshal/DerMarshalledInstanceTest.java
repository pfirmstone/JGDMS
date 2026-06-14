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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-008 S13.6 "Option A" integration tests -- non-invasive DER MarshalledInstance spike.
 *
 * <p>Proves that a DER codec plugs into the existing {@link MarshalledInstance} factory
 * seam end-to-end with ZERO platform changes. All four classes
 * ({@link DerMarshalledInstance}, {@link DerMarshalFactory},
 * {@link DerMarshalInstanceOutput}, {@link DerMarshalInstanceInput}) live exclusively in
 * {@code jgdms-der}; no jgdms-platform files are modified.
 *
 * <h2>Test catalogue</h2>
 * <ul>
 *   <li>{@link #test_13_1_RoundTrip_SimpleRecord_getWithType} -- flat @AtomicSerial class;
 *       round-trip via {@code get(false, Class)}.</li>
 *   <li>{@link #test_13_2_RoundTrip_Gamma_getWithType} -- three-level @AtomicSerial hierarchy
 *       (Gamma extends Beta extends Alpha); round-trip via {@code get(false, Class)}.</li>
 *   <li>{@link #test_13_3_RoundTrip_FullGetSignature} -- round-trip via the 5-arg
 *       {@code get(ClassLoader, boolean, ClassLoader, Collection, Class)} path.</li>
 *   <li>{@link #test_13_4_ObjBytesCarriesEmbeddedSchema} -- confirms that the DER
 *       {@link MarshalledInstanceRecord} stored in {@code objBytes} has non-empty
 *       schemaBytes and the canonical payloadFormat string.</li>
 *   <li>{@link #test_13_5_NullObject} -- null object produces a null from get().</li>
 * </ul>
 *
 * <h2>Scope note -- serialization of DerMarshalledInstance itself</h2>
 * <p>
 * Round-tripping a {@code DerMarshalledInstance} instance through Java serialization
 * reconstructs a base {@link MarshalledInstance}. The base get() would then use the
 * default JOSS factory, which cannot decode the DER bytes in {@code objBytes}.
 * Format-detection / ServiceLoader dispatch (the STD-008 S13 hybrid B+C follow-on) is
 * out of scope for this spike and is not tested here.
 */
class DerMarshalledInstanceTest {

    // =========================================================================
    // 13.1 -- flat @AtomicSerial class round-trip via get(boolean, Class)
    // =========================================================================

    /**
     * Round-trip a flat {@code @AtomicSerial} class ({@link SimpleRecord}) through
     * {@link DerMarshalledInstance}, decoding via {@code get(false, Class)}.
     *
     * <p>Flow:
     * {@code new DerMarshalledInstance(fixture)}
     *  -> {@link DerMarshalFactory#createMarshalOutput}
     *  -> {@link DerMarshalInstanceOutput#writeObject}
     *  -> {@link MarshalledInstanceRecord#encode()} stored as {@code objBytes}
     *  -> {@code get(false, SimpleRecord.class)}
     *  -> {@link DerMarshalFactory#createMarshalInput}
     *  -> {@link DerMarshalInstanceInput#readObject(Class)}
     *  -> {@link MarshalledInstanceCodec#decodeMarshalledInstance}
     *  -> reconstructed object equals fixture.
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
     * {@link DerMarshalledInstance}.
     *
     * <p>This proves the hierarchy encoding (one private SEQUENCE per class, root-first
     * on wire, StackWalker-dispatched deserialization) works through the MarshalledInstance
     * seam -- not just the direct ObjectCodec path.
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
     * path also uses the DER factory (via {@link DerMarshalledInstance#getMarshalFactory()}).
     *
     * <p>Parameters: defaultLoader=null, verifyCodebaseIntegrity=false,
     * verifierLoader=null, context=emptyList, type=VersionedRecord.class.
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
    // 13.4 -- objBytes carries the embedded schema (payloadFormat + non-empty schemaBytes)
    // =========================================================================

    /**
     * Confirms that {@code DerMarshalledInstance}'s {@code objBytes} (read back via
     * {@link MarshalledInstance#get(boolean, Class)} decoding) carries a
     * {@link MarshalledInstanceRecord} with:
     * <ul>
     *   <li>non-empty {@code schemaBytes} (embedded schema chain travels in the record)</li>
     *   <li>{@code payloadFormat} == {@value MarshalledInstanceRecord#PAYLOAD_FORMAT}</li>
     * </ul>
     *
     * <p>To access {@code objBytes} without breaking encapsulation, the test re-encodes
     * via a {@link DerMarshalFactory} directly and decodes the raw bytes as a record.
     * This mirrors what MarshalledInstance stores: the factory writes to a
     * {@code ByteArrayOutputStream} that becomes {@code objBytes}; we replicate this.
     */
    @Test
    void test_13_4_ObjBytesCarriesEmbeddedSchema() throws Exception {
        SimpleRecord fixture = new SimpleRecord(false, 1, "schema-test", new byte[0]);

        // Encode directly via the factory to get the raw DER bytes
        // (same bytes that are stored as objBytes by the MarshalledInstance ctor).
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        DerMarshalInstanceOutput out = new DerMarshalInstanceOutput(
                bout, Collections.emptyList());
        out.writeObject(fixture);
        out.flush();

        byte[] rawBytes = bout.toByteArray();

        // Decode the raw bytes as a MarshalledInstanceRecord
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.decode(rawBytes);

        // Assert: embedded schema is non-empty
        assertTrue(rec.schemaBytes().length > 0,
                "schemaBytes in the MarshalledInstanceRecord must be non-empty -- " +
                "the schema travels embedded in objBytes");

        // Assert: payloadFormat is the canonical DER format string
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, rec.payloadFormat(),
                "payloadFormat must be '" + MarshalledInstanceRecord.PAYLOAD_FORMAT + "'");

        // Assert: the embedded record can be decoded back to the original fixture
        MarshalledInstanceCodec.Result<SimpleRecord> decoded =
                MarshalledInstanceCodec.decodeMarshalledInstance(rec, SimpleRecord.class);
        assertEquals(fixture, decoded.object(),
                "Decoded object from raw objBytes must equal the original fixture");
    }

    // =========================================================================
    // 13.5 -- null object round-trip
    // =========================================================================

    /**
     * A null object marshalled into a {@link DerMarshalledInstance} must produce
     * {@code null} from {@code get(false, Object.class)}.
     *
     * <p>{@link MarshalledInstance}'s protected ctor skips {@code writeObject} entirely
     * for null (it leaves {@code objBytes} as null). The {@code get()} path returns
     * {@code null} immediately when {@code objBytes == null}, without calling the factory.
     */
    @Test
    void test_13_5_NullObject() throws Exception {
        DerMarshalledInstance dmi = new DerMarshalledInstance(null);
        assertTrue(dmi.isNull(), "DerMarshalledInstance wrapping null must report isNull()");
        Object result = dmi.get(false, Object.class);
        assertNull(result, "get() on a null-containing DerMarshalledInstance must return null");
    }
}
