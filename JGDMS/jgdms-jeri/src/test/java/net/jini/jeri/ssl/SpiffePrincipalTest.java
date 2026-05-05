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
package net.jini.jeri.ssl;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link SpiffePrincipal}.
 *
 * Stage 1: pure in-memory tests — no filesystem access required.
 */
public class SpiffePrincipalTest {

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Test
    public void constructValid() {
        SpiffePrincipal p = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertEquals("spiffe://example.org/svc/foo", p.getName());
    }

    @Test(expected = NullPointerException.class)
    public void constructNull() {
        new SpiffePrincipal(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructHttpsPrefix() {
        new SpiffePrincipal("https://example.org/svc/foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructEmptyString() {
        new SpiffePrincipal("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructUpperCaseScheme() {
        // scheme matching is case-sensitive per the SPIFFE spec
        new SpiffePrincipal("SPIFFE://example.org/svc/foo");
    }

    // -----------------------------------------------------------------------
    // getName / equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Test
    public void getNameReturnsSameId() {
        String id = "spiffe://td.example/workload/backend";
        assertEquals(id, new SpiffePrincipal(id).getName());
    }

    @Test
    public void equalsSameInstance() {
        SpiffePrincipal p = new SpiffePrincipal("spiffe://example.org/svc/a");
        assertEquals(p, p);
    }

    @Test
    public void equalsSymmetric() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal b = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void equalsHashCodeConsistent() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal b = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsOtherUri() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        SpiffePrincipal c = new SpiffePrincipal("spiffe://example.org/svc/bar");
        assertNotEquals(a, c);
    }

    @Test
    public void notEqualsNull() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertFalse(a.equals(null));
    }

    @Test
    public void notEqualsOtherType() {
        SpiffePrincipal a = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertFalse(a.equals("spiffe://example.org/svc/foo"));
    }

    @Test
    public void toStringContainsSpiffeId() {
        SpiffePrincipal p = new SpiffePrincipal("spiffe://example.org/svc/foo");
        assertTrue(p.toString().contains("spiffe://example.org/svc/foo"));
    }

    // -----------------------------------------------------------------------
    // Serialization
    // -----------------------------------------------------------------------

    @Test
    public void serializationRoundTrip() throws Exception {
        SpiffePrincipal original = new SpiffePrincipal("spiffe://example.org/svc/foo");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        SpiffePrincipal deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (SpiffePrincipal) ois.readObject();
        }
        assertEquals(original, deserialized);
        assertEquals(original.getName(), deserialized.getName());
    }

    @Test
    public void deserializationRejectsInvalidPrefix() throws Exception {
        // Serialize a valid instance, then byte-patch "spiffe://" to "SPIFFE://"
        // (same byte length, but does not match the lower-case prefix check).
        SpiffePrincipal valid = new SpiffePrincipal("spiffe://example.org/svc/foo");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(valid);
        }
        byte[] bytes = baos.toByteArray();
        byte[] find    = "spiffe://".getBytes("UTF-8");
        byte[] replace = "SPIFFE://".getBytes("UTF-8");
        byte[] tampered = patchFirstOccurrence(bytes, find, replace);

        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(tampered))) {
            ois.readObject();
            fail("Expected IOException for invalid SPIFFE ID on deserialization");
        } catch (IOException e) {
            // expected — readObject validates the prefix
        }
    }

    // -----------------------------------------------------------------------
    // fromCertificate
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void fromCertificateNullThrows() {
        SpiffePrincipal.fromCertificate(null);
    }

    @Test
    public void fromCertificateExtractsSpiffeUri() throws Exception {
        X509Certificate cert = loadFirstCertFromResource("/spiffe/reggie/svid.pem");
        List<SpiffePrincipal> principals = SpiffePrincipal.fromCertificate(cert);
        assertEquals(1, principals.size());
        assertEquals("spiffe://test.jgdms.local/svc/reggie",
                principals.get(0).getName());
    }

    @Test
    public void fromCertificateReturnsUnmodifiableList() throws Exception {
        X509Certificate cert = loadFirstCertFromResource("/spiffe/reggie/svid.pem");
        List<SpiffePrincipal> principals = SpiffePrincipal.fromCertificate(cert);
        try {
            principals.add(new SpiffePrincipal("spiffe://example.org/svc/x"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void fromCertificateNoCertReturnsEmpty() throws Exception {
        // The CA cert in our test resources has no URI SAN.
        X509Certificate caCert = loadFirstCertFromResource("/spiffe/ca/ca.pem");
        List<SpiffePrincipal> principals = SpiffePrincipal.fromCertificate(caCert);
        assertTrue("CA cert should yield no SPIFFE principals", principals.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static X509Certificate loadFirstCertFromResource(String resource)
            throws Exception {
        try (InputStream in = SpiffePrincipalTest.class.getResourceAsStream(resource)) {
            assertNotNull("Test resource not found: " + resource, in);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    /**
     * Replaces the first occurrence of {@code find} in {@code src} with
     * {@code replace} (must be same length).
     */
    private static byte[] patchFirstOccurrence(byte[] src, byte[] find,
                                                byte[] replace) {
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
        throw new IllegalStateException("Pattern not found in serialized bytes");
    }
}
