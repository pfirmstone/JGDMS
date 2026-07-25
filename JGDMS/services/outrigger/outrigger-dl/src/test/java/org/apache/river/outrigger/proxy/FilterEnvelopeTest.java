/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger.proxy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import net.jini.space.FilterRejectedException;
import org.junit.Test;

/**
 * Canonical-form + fail-closed vectors for the {@link FilterEnvelope} codec
 * (SOW Part B, unit B1). Every malformed/non-canonical/over-ceiling input must
 * be rejected with a {@link FilterRejectedException} of reason
 * {@link FilterRejectedException.Reason#ENVELOPE_MALFORMED}.
 */
public class FilterEnvelopeTest {

    private static byte[] cel() {
        return new byte[] { 0x30, 0x03, 0x02, 0x01, 0x07 }; // arbitrary opaque payload
    }

    private static void assertMalformed(byte[] envelope) {
        try {
            FilterEnvelope.decode(envelope);
            fail("expected FilterRejectedException for " + Arrays.toString(envelope));
        } catch (FilterRejectedException e) {
            assertEquals(FilterRejectedException.Reason.ENVELOPE_MALFORMED, e.reason());
        }
    }

    @Test
    public void roundTripPreservesVersionAndPayload() throws Exception {
        byte[] payload = cel();
        byte[] env = FilterEnvelope.encode(payload);
        FilterEnvelope decoded = FilterEnvelope.decode(env);
        assertEquals(FilterEnvelope.VERSION, decoded.version());
        assertArrayEquals(payload, decoded.celWire());
    }

    @Test
    public void emptyCelWireIsLegal() throws Exception {
        byte[] env = FilterEnvelope.encode(new byte[0]);
        FilterEnvelope decoded = FilterEnvelope.decode(env);
        assertEquals(0, decoded.celWire().length);
    }

    @Test
    public void celWireAccessorIsDefensiveCopy() throws Exception {
        byte[] env = FilterEnvelope.encode(cel());
        FilterEnvelope decoded = FilterEnvelope.decode(env);
        byte[] first = decoded.celWire();
        first[0] = (byte) 0xAA;
        assertNotNull(decoded.celWire());
        // Mutating the returned array must not affect the envelope's own copy.
        org.junit.Assert.assertFalse(Arrays.equals(first, decoded.celWire()));
    }

    @Test
    public void rejectsTrailingBytesAfterSequence() {
        byte[] env = FilterEnvelope.encode(cel());
        byte[] withTrailer = Arrays.copyOf(env, env.length + 1);
        withTrailer[env.length] = 0x00;
        assertMalformed(withTrailer);
    }

    @Test
    public void rejectsWrongOuterTag() {
        byte[] env = FilterEnvelope.encode(cel());
        byte[] bad = env.clone();
        bad[0] = 0x31; // SET instead of SEQUENCE
        assertMalformed(bad);
    }

    @Test
    public void rejectsUnsupportedVersion() {
        // A canonical envelope carrying version 2 is well-formed DER but refused.
        byte[] env = FilterEnvelope.encode(2, cel());
        assertMalformed(env);
    }

    @Test
    public void rejectsNegativeVersion() {
        byte[] env = FilterEnvelope.encode(-1, cel());
        assertMalformed(env);
    }

    @Test
    public void rejectsIndefiniteLength() {
        // SEQUENCE with indefinite-form length octet 0x80.
        assertMalformed(new byte[] { 0x30, (byte) 0x80, 0x02, 0x01, 0x01, 0x04, 0x00, 0x00, 0x00 });
    }

    @Test
    public void rejectsNonMinimalLongFormLength() {
        // SEQUENCE with long-form length (0x81 0x06) that should have been short form.
        byte[] inner = new byte[] { 0x02, 0x01, 0x01, 0x04, 0x01, 0x07 }; // ver=1, celWire={07}
        byte[] bad = new byte[3 + inner.length];
        bad[0] = 0x30;
        bad[1] = (byte) 0x81;              // long form, 1 length octet
        bad[2] = (byte) inner.length;      // value 6 -> should be short form
        System.arraycopy(inner, 0, bad, 3, inner.length);
        assertMalformed(bad);
    }

    @Test
    public void rejectsNonCanonicalIntegerLeadingZero() {
        // version INTEGER encoded as 0x00 0x01 (non-minimal 2-byte form of 1).
        byte[] inner = new byte[] { 0x02, 0x02, 0x00, 0x01, 0x04, 0x00 };
        byte[] bad = new byte[2 + inner.length];
        bad[0] = 0x30;
        bad[1] = (byte) inner.length;
        System.arraycopy(inner, 0, bad, 2, inner.length);
        assertMalformed(bad);
    }

    @Test
    public void rejectsMissingOctetStringElement() {
        // SEQUENCE { version } with no celWire element.
        byte[] bad = new byte[] { 0x30, 0x03, 0x02, 0x01, 0x01 };
        assertMalformed(bad);
    }

    @Test
    public void rejectsExtraTrailingElementInsideSequence() {
        // SEQUENCE { version, celWire, BOOLEAN } -- an unexpected third element.
        byte[] inner = new byte[] { 0x02, 0x01, 0x01, 0x04, 0x00, 0x01, 0x01, 0x00 };
        byte[] bad = new byte[2 + inner.length];
        bad[0] = 0x30;
        bad[1] = (byte) inner.length;
        System.arraycopy(inner, 0, bad, 2, inner.length);
        assertMalformed(bad);
    }

    @Test
    public void rejectsWrongSecondElementTag() {
        // SEQUENCE { version INTEGER, INTEGER } -- second element should be OCTET STRING.
        byte[] inner = new byte[] { 0x02, 0x01, 0x01, 0x02, 0x01, 0x07 };
        byte[] bad = new byte[2 + inner.length];
        bad[0] = 0x30;
        bad[1] = (byte) inner.length;
        System.arraycopy(inner, 0, bad, 2, inner.length);
        assertMalformed(bad);
    }

    @Test
    public void rejectsTruncatedInput() {
        assertMalformed(new byte[] { 0x30, 0x05, 0x02, 0x01 }); // declares 5, only 2 follow
    }

    @Test
    public void rejectsEmptyInput() {
        assertMalformed(new byte[0]);
    }

    @Test
    public void rejectsCelWireOverCeiling() {
        // Hand-build an envelope whose OCTET STRING declares a length above the
        // ceiling (without allocating the whole thing): length 0x00100001 (> 64 KiB).
        // SEQUENCE content = INTEGER(1) + OCTET STRING with long-form length.
        byte[] verTlv = new byte[] { 0x02, 0x01, 0x01 };
        // OCTET STRING header declaring 0x100001 bytes (3 length octets).
        byte[] osHeader = new byte[] { 0x04, (byte) 0x83, 0x10, 0x00, 0x01 };
        int innerLen = verTlv.length + osHeader.length;
        byte[] bad = new byte[2 + innerLen];
        bad[0] = 0x30;
        bad[1] = (byte) innerLen;
        System.arraycopy(verTlv, 0, bad, 2, verTlv.length);
        System.arraycopy(osHeader, 0, bad, 2 + verTlv.length, osHeader.length);
        assertMalformed(bad);
    }

    @Test(expected = NullPointerException.class)
    public void decodeRejectsNull() throws Exception {
        FilterEnvelope.decode(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encodeRejectsOverCeilingCelWire() {
        FilterEnvelope.encode(new byte[FilterEnvelope.MAX_CEL_WIRE_BYTES + 1]);
    }
}
