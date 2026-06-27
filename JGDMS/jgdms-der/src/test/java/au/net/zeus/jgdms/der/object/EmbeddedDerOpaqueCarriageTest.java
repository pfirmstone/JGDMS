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
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tripwire for the <b>opaque-octet carve-out</b> (STD-006 sec.3.8) that the
 * signature/digest-bearing records depend on for correctness: an externally-produced
 * DER structure embedded in an {@code @AtomicSerial} object -- an X.509
 * {@code Certificate} (sec.7.3, sec.7.7.7), an X.501 {@code Name} / {@code X500Principal}
 * (sec.7.6), a nested signature -- MUST be carried as opaque {@code byte[]} captured
 * verbatim from {@code getEncoded()}, and MUST NEVER be parsed-and-re-encoded by this
 * codec.
 *
 * <p><b>Why this matters.</b> Real-world certificates and Names are frequently <i>not</i>
 * strict DER (non-minimal lengths, legacy {@code TeletexString} attribute values, other
 * BER-permissive-but-valid encodings). When such a value sits under a signature or digest
 * (a SCAP verdict sec.7.4, a multicast announcement sec.7.7.7, a SPIFFE/X.509 principal
 * chain sec.7.1), re-encoding it through a fresh DER writer would change its bytes and
 * break the very signature that authenticates it. The acceptability of the inner bytes is
 * the external authority's contract (PKIX path validation, the signature check), never
 * this codec's.
 *
 * <p>The cert/Name <i>records</i> (DigestCodeSourceRecord, MulticastAnnouncementRecord,
 * X500PrincipalSerializer) are not yet implemented; these tests guard the underlying
 * carriage mechanism -- a {@code byte[]} field -- so that the invariant is locked
 * <i>before</i> those records are built. The discriminating test
 * {@link #berishCert_wouldBeRejectedIfParsedAsDer()} fails loudly the moment anyone
 * routes an embedded DER blob through {@link DerReader} instead of carrying it opaquely.
 *
 * <p>All literals are ASCII / explicit byte values -- no embedded multi-byte chars
 * (consistent with the project's ASCII-only source policy).
 */
class EmbeddedDerOpaqueCarriageTest {

    // =========================================================================
    // Adversarial vectors
    // =========================================================================

    /**
     * A deliberately NON-canonical-DER blob standing in for a real-world ("BER-ish")
     * X.509 certificate: {@code SEQUENCE { INTEGER 0x000001 }} where the INTEGER carries
     * two non-minimal leading zero bytes. Strict DER would encode the integer as
     * {@code 02 01 01}; a strict DER parser ({@link DerReader#readInteger()}) rejects the
     * {@code 02 03 00 00 01} form. Certificates in the wild routinely carry such
     * non-strict encodings, which is exactly why they must be carried opaquely.
     */
    private static final byte[] BERISH_CERT = {
            0x30, 0x05,                         // SEQUENCE, length 5
            0x02, 0x03, 0x00, 0x00, 0x01        // INTEGER, length 3, value 0x000001 (NON-canonical)
    };

    /**
     * A minimal X.501 {@code Name} whose single CN attribute value uses the legacy
     * {@code TeletexString} (T61String, tag {@code 0x14}) rather than {@code UTF8String}.
     * This is valid DER, but a serializer that "normalises" string types would rewrite the
     * tag (0x14 -> 0x0C) and the bytes -- breaking any signature over the Name. It must
     * therefore survive verbatim.
     *
     * <pre>
     *   SEQUENCE {                    -- RDNSequence            30 0D
     *     SET {                       -- RelativeDistinguishedName 31 0B
     *       SEQUENCE {                -- AttributeTypeAndValue   30 09
     *         OID 2.5.4.3 (commonName)                          06 03 55 04 03
     *         TeletexString "CA"                                14 02 43 41
     *       }
     *     }
     *   }
     * </pre>
     */
    private static final byte[] TELETEX_NAME = {
            0x30, 0x0D,
            0x31, 0x0B,
            0x30, 0x09,
            0x06, 0x03, 0x55, 0x04, 0x03,       // OID commonName
            0x14, 0x02, 0x43, 0x41              // TeletexString "CA"
    };

    // =========================================================================
    // Fixture -- a single opaque byte[] carrier (the cert/Name mechanism)
    // =========================================================================

    @AtomicSerial
    public static final class OpaqueCarrier {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("bytes", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, OpaqueCarrier o) throws IOException {
            arg.put("bytes", o.bytes); arg.writeArgs();
        }
        private final byte[] bytes;
        public OpaqueCarrier(byte[] bytes) {
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }
        public OpaqueCarrier(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            byte[] b = (byte[]) arg.get("bytes", null);
            this.bytes = b == null ? new byte[0] : b.clone();
        }
        public byte[] bytes() { return bytes.clone(); }
    }

    // =========================================================================
    // Helpers (mirroring FloatDoubleCharCanonicalTest)
    // =========================================================================

    private static <T> MarshalledInstanceRecord recordOf(T value, Class<T> cls) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        byte[] payload = ObjectCodec.encodeHierarchy(value, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value, Class<T> cls) throws Exception {
        MarshalledInstanceRecord rec = recordOf(value, cls);
        return (T) MarshalledInstanceCodec.decodeMarshalledInstance(rec, cls).object();
    }

    /** True iff {@code needle} occurs contiguously inside {@code haystack}. */
    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    // =========================================================================
    // Verbatim preservation
    // =========================================================================

    /** A non-strict-DER certificate survives encode -> decode byte-for-byte. */
    @Test
    void berishCert_survivesRoundTripVerbatim() throws Exception {
        OpaqueCarrier out = roundTrip(new OpaqueCarrier(BERISH_CERT.clone()), OpaqueCarrier.class);
        assertArrayEquals(BERISH_CERT, out.bytes(),
                "embedded certificate bytes MUST be preserved byte-for-byte (no re-canonicalization)");
    }

    /** A Name with a TeletexString AVA survives encode -> decode byte-for-byte. */
    @Test
    void teletexNameAva_survivesRoundTripVerbatim() throws Exception {
        OpaqueCarrier out = roundTrip(new OpaqueCarrier(TELETEX_NAME.clone()), OpaqueCarrier.class);
        assertArrayEquals(TELETEX_NAME, out.bytes(),
                "embedded X.501 Name (TeletexString AVA) MUST be preserved byte-for-byte;"
                        + " a re-encode that normalised the string type would break a signature over it");
    }

    /**
     * The embedded bytes appear verbatim <i>on the wire</i> (inside the encoded payload),
     * not merely after a decode round-trip. This is what makes a signature/digest computed
     * over the wire bytes reproducible: the OCTET STRING content is the cert's own octets.
     */
    @Test
    void embeddedCert_appearsVerbatimInPayload() throws Exception {
        MarshalledInstanceRecord rec = recordOf(new OpaqueCarrier(BERISH_CERT.clone()), OpaqueCarrier.class);
        assertTrue(contains(rec.payloadBytes(), BERISH_CERT),
                "the wire payload MUST contain the embedded DER bytes verbatim (carried as OCTET STRING content)");
    }

    // =========================================================================
    // Determinism (signature / digest stability)
    // =========================================================================

    /**
     * Encoding the same embedded value twice yields byte-identical payload AND schema
     * digest. A signature or digest computed over the enclosing structure is therefore
     * stable regardless of the embedded blob's internal (non-)canonicality.
     */
    @Test
    void embeddedDer_payloadAndDigestAreDeterministic() throws Exception {
        MarshalledInstanceRecord a = recordOf(new OpaqueCarrier(BERISH_CERT.clone()), OpaqueCarrier.class);
        MarshalledInstanceRecord b = recordOf(new OpaqueCarrier(BERISH_CERT.clone()), OpaqueCarrier.class);
        assertArrayEquals(a.payloadBytes(), b.payloadBytes(),
                "identical embedded bytes MUST produce identical wire payload");
        assertArrayEquals(a.schemaDigest(), b.schemaDigest(),
                "schema digest MUST be stable for identical embedded bytes");
    }

    // =========================================================================
    // DISCRIMINATING: the hazard the opaque carve-out avoids
    // =========================================================================

    /**
     * DISCRIMINATING. The BER-ish certificate, if it were ever routed through the strict
     * DER parser ({@link DerReader}) instead of carried opaquely, would be REJECTED -- its
     * inner INTEGER is non-canonical. This proves the two paths are not interchangeable:
     * carrying certs/Names as opaque octets is load-bearing, and any future change that
     * decodes an embedded cert as an inline ASN.1 structure (e.g. reading sec.7.3
     * {@code SEQUENCE OF Certificate} as the named X.509 type) would break a perfectly
     * valid real-world certificate. If this test ever stops throwing, the carve-out has
     * been violated.
     */
    @Test
    void berishCert_wouldBeRejectedIfParsedAsDer() throws Exception {
        DerReader inner = new DerReader(BERISH_CERT).readSequence();
        assertThrows(DerException.class, inner::readInteger,
                "a non-strict-DER ('BER-ish') certificate is REJECTED if routed through the DER parser;"
                        + " certs MUST be carried as opaque octets, never parsed-and-re-encoded (STD-006 sec.3.8/7.3)");
    }
}
