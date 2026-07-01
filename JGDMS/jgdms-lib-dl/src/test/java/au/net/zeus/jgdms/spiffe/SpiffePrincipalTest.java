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
package au.net.zeus.jgdms.spiffe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link SpiffePrincipal}.
 *
 * <p>Pure in-memory tests: construction/validation, canonical {@code getName()}
 * (RFC 3986 normalization), {@code equals}/{@code hashCode}/{@code toString},
 * and an <em>AtomicSerial</em> marshal/unmarshal round-trip through
 * {@link AtomicMarshalOutputStream} / {@link AtomicMarshalInputStream} — the
 * path that actually exercises {@code serialize}/{@code serialForm} and the
 * atomic {@code (GetArg)} validation.  Certificate-SAN ({@code fromCertificate})
 * coverage against real SVIDs lives in the qa jtreg SPIFFE tests, which own the
 * {@code SpiffeCredentialManager} SVID-loading fixtures.
 */
public class SpiffePrincipalTest {

    /** Java Object Serialization stream tag for a short UTF-8 string. */
    private static final byte TC_STRING = 0x74;

    // -----------------------------------------------------------------------
    // Construction / validation
    // -----------------------------------------------------------------------

    @Test
    public void constructValid() {
        SpiffePrincipal p = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertEquals("spiffe://example.org/svc/foo", p.getName());
    }

    @Test(expected = NullPointerException.class)
    public void constructNull() {
        new SpiffePrincipal((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructHttpsPrefix() {
        new SpiffePrincipal("https://example.org/svc/foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructEmpty() {
        new SpiffePrincipal("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructUppercaseScheme() {
        new SpiffePrincipal("SPIFFE://example.org/svc/foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructSchemeOnly() {
        new SpiffePrincipal("spiffe://");
    }

    // -----------------------------------------------------------------------
    // Canonical getName / normalization
    // -----------------------------------------------------------------------

    @Test
    public void getNameReturnsCanonicalId() {
        String id = "spiffe://example.org/svc/foo";
        assertEquals(id, new SpiffePrincipal(id).getName());
    }

    @Test
    public void normalizationIsIdempotent() {
        // getName() must be a fixed point: re-parsing the canonical form yields
        // the same canonical form.  This is the property that makes canonical
        // getName() a reliable cross-implementation match key.
        String id = "spiffe://EXAMPLE.org/svc/foo";
        String once = new SpiffePrincipal(id).getName();
        String twice = new SpiffePrincipal(once).getName();
        assertEquals(once, twice);
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Test
    public void equalsAndHashCodeBySpiffeId() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal b = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualDifferentId() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal c = new SpiffePrincipal("spiffe://example.org/svc/bar");
        assertNotEquals(a, c);
    }

    @Test
    public void notEqualOtherType() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertNotEquals(a, "spiffe://example.org/svc/foo");
        assertFalse(a.equals(null));
    }

    @Test
    public void toStringContainsId() {
        SpiffePrincipal p = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertTrue(p.toString().contains("spiffe://example.org/svc/foo"));
    }

    // -----------------------------------------------------------------------
    // AtomicSerial round-trip
    // -----------------------------------------------------------------------

    @Test
    public void atomicRoundTrip() throws Exception {
        SpiffePrincipal original = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal back = (SpiffePrincipal) atomicRoundTrip(original);
        assertEquals("identity preserved across round-trip", original, back);
        assertEquals("canonical name preserved", original.getName(), back.getName());
    }

    @Test
    public void atomicDeserializationRejectsInvalidScheme() throws Exception {
        // Marshal a valid instance, then byte-patch "spiffe://" to "SPIFFE://"
        // (same byte length) so the deserialized value fails the scheme check.
        // AtomicMarshalOutputStream extends MarshalOutputStream, so the spiffeId
        // String is written with the standard TC_STRING framing.
        byte[] bytes = atomicMarshal(new SpiffePrincipal("spiffe://example.org/svc/foo"));
        byte[] tampered = patchFirstOccurrence(bytes,
                "spiffe://".getBytes("UTF-8"), "SPIFFE://".getBytes("UTF-8"));
        try {
            atomicUnmarshal(tampered);
            fail("Expected IOException for invalid SPIFFE scheme on deserialization");
        } catch (IOException e) {
            // expected — the (GetArg) constructor validates the scheme atomically
        }
    }

    @Test
    public void atomicDeserializationRejectsEmptyTrustDomain() throws Exception {
        // Marshal SpiffePrincipal("spiffe://x") (10 chars) then shorten the
        // TC_STRING to "spiffe://" (9 chars, empty trust domain).
        byte[] bytes = atomicMarshal(new SpiffePrincipal("spiffe://x"));
        byte[] find    = concat(new byte[]{TC_STRING, 0x00, 0x0a},
                "spiffe://x".getBytes("UTF-8"));
        byte[] replace = concat(new byte[]{TC_STRING, 0x00, 0x09},
                "spiffe://".getBytes("UTF-8"));
        byte[] tampered = patchVariableLength(bytes, find, replace);
        try {
            atomicUnmarshal(tampered);
            fail("Expected IOException for SPIFFE ID with empty trust domain");
        } catch (IOException e) {
            // expected — the (GetArg) constructor rejects an empty trust domain
        }
    }

    // -----------------------------------------------------------------------
    // fromCertificate
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void fromCertificateNullThrows() {
        SpiffePrincipal.fromCertificate(null);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] atomicMarshal(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(o);
        oos.flush();
        oos.close();
        return baos.toByteArray();
    }

    private static Object atomicUnmarshal(byte[] bytes) throws Exception {
        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bytes), null, false, null, null, false);
        return ois.readObject();
    }

    private static Object atomicRoundTrip(Object o) throws Exception {
        return atomicUnmarshal(atomicMarshal(o));
    }

    /** Replaces the first occurrence of {@code find} with {@code replace} (same length). */
    private static byte[] patchFirstOccurrence(byte[] src, byte[] find, byte[] replace) {
        if (find.length != replace.length)
            throw new IllegalArgumentException("find and replace must be same length");
        outer:
        for (int i = 0; i <= src.length - find.length; i++) {
            for (int j = 0; j < find.length; j++) {
                if (src[i + j] != find[j]) continue outer;
            }
            byte[] result = src.clone();
            System.arraycopy(replace, 0, result, i, replace.length);
            return result;
        }
        throw new IllegalStateException("Pattern not found in marshalled bytes");
    }

    /** Replaces the first occurrence of {@code find} with {@code replace} (may differ in length). */
    private static byte[] patchVariableLength(byte[] src, byte[] find, byte[] replace) {
        outer:
        for (int i = 0; i <= src.length - find.length; i++) {
            for (int j = 0; j < find.length; j++) {
                if (src[i + j] != find[j]) continue outer;
            }
            byte[] result = new byte[src.length - find.length + replace.length];
            System.arraycopy(src, 0, result, 0, i);
            System.arraycopy(replace, 0, result, i, replace.length);
            System.arraycopy(src, i + find.length, result,
                    i + replace.length, src.length - i - find.length);
            return result;
        }
        throw new IllegalStateException("Pattern not found in marshalled bytes");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
