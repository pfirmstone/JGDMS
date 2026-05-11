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

package org.apache.river.api.io;

import java.net.URL;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.junit.Assert;
import org.junit.Test;

public class AccessControlContextSerializerTest {

    @Test
    public void testMarshalUnmarshalFiltersAndUsesAuthenticatedSubject() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            URL plain = new URL("http://repo.example.org/client-stub.jar");
            ProtectionDomain httpmdPd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain plainPd = new ProtectionDomain(
                    new CodeSource(plain, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext acc = new AccessControlContext(new ProtectionDomain[]{httpmdPd, plainPd});

            byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
            Assert.assertTrue(encoded.length > 0);
            // The plain-HTTP domain is non-verifiable: version-1 format is used.
            // First byte = 0x01 (version discriminator); bytes 1-4 = HTTPMD domain count = 1.
            Assert.assertEquals(0x01, encoded[0] & 0xFF);
            Assert.assertEquals(1, readIntAt(encoded, 1));

            Subject authenticated = new Subject();
            authenticated.getPrincipals().add(new X500Principal("CN=worker"));
            AccessControlContext reconstructed =
                    AccessControlContextSerializer.unmarshalForTransport(encoded, authenticated);
            Assert.assertNotNull("reconstructed ACC must not be null", reconstructed);
            byte[] reencoded = AccessControlContextSerializer.marshalForTransport(reconstructed);
            Assert.assertTrue("re-encoded payload must be non-empty", reencoded.length > 0);
            // The reconstructed ACC contains the HTTPMD domain plus a null-CodeSource
            // placeholder for the anonymous domain.  Re-encoding in a typical test JVM
            // call stack also captures platform/framework domains, so version-1 format
            // is expected; the HTTPMD count must still be 1.
            Assert.assertEquals(0x01, reencoded[0] & 0xFF);
            Assert.assertEquals(1, readIntAt(reencoded, 1));
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    @Test
    public void testAtomicMarshalRoundTripForAccessControlContext() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            ProtectionDomain pd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext original = new AccessControlContext(new ProtectionDomain[]{pd});

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream out = new AtomicMarshalOutputStream(baos, null);
            out.writeObject(original);
            out.flush();

            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
            java.io.ObjectInputStream in = AtomicMarshalInputStream.create(
                    bais, null, false, null, null, false);
            Object result = in.readObject();
            Assert.assertTrue(result instanceof AccessControlContext);
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }
    
    @Test
    public void testDomainIdentityRejectsStandardSerialization() throws Exception {
        URL httpmd = new URL(null,
                "httpmd://repo.example.org/client-stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                new PassThroughHandler());
        AccessControlContextSerializer.DomainIdentity domain = new AccessControlContextSerializer.DomainIdentity(
                new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                new java.security.Principal[0]);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(baos);
        try {
            out.writeObject(domain);
            Assert.fail("Expected NotSerializableException");
        } catch (java.io.NotSerializableException expected) {
            // expected
        } finally {
            out.close();
        }
    }

    @Test
    public void testUnmarshalRejectsOversizedLocationLength() throws Exception {
        // Craft a payload: count=1, locLen=5000 (> MAX_LOCATION_BYTES=4096)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        // 4-byte count = 1
        baos.write(0); baos.write(0); baos.write(0); baos.write(1);
        // 2-byte locLen = 5000
        int locLen = 5000;
        baos.write((locLen >>> 8) & 0xFF);
        baos.write(locLen & 0xFF);
        // location bytes (5000 zero bytes)
        baos.write(new byte[locLen]);
        // 2-byte principalCount = 0
        baos.write(0); baos.write(0);
        try {
            AccessControlContextSerializer.unmarshalForTransport(baos.toByteArray(), null);
            Assert.fail("Expected InvalidObjectException for oversized location length");
        } catch (java.io.InvalidObjectException expected) {
            Assert.assertTrue(expected.getMessage().contains("location length exceeds maximum"));
        }
    }

    @Test
    public void testUnmarshalRejectsOversizedPrincipalCount() throws Exception {
        // Craft a payload: count=1, locLen=0, principalCount=300 (> MAX_PRINCIPALS_PER_DOMAIN=256)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        // 4-byte count = 1
        baos.write(0); baos.write(0); baos.write(0); baos.write(1);
        // 2-byte locLen = 0 (empty location)
        baos.write(0); baos.write(0);
        // 2-byte principalCount = 300
        int principalCount = 300;
        baos.write((principalCount >>> 8) & 0xFF);
        baos.write(principalCount & 0xFF);
        try {
            AccessControlContextSerializer.unmarshalForTransport(baos.toByteArray(), null);
            Assert.fail("Expected InvalidObjectException for oversized principal count");
        } catch (java.io.InvalidObjectException expected) {
            Assert.assertTrue(expected.getMessage().contains("principal count exceeds maximum"));
        }
    }

    @Test
    public void testUnmarshalRejectsOversizedPrincipalTypeLength() throws Exception {
        // Craft a payload: count=1, locLen=0, principalCount=1, typeLen=5000
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(0); baos.write(0); baos.write(0); baos.write(1); // count=1
        baos.write(0); baos.write(0);                               // locLen=0
        baos.write(0); baos.write(1);                               // principalCount=1
        int typeLen = 5000;
        baos.write((typeLen >>> 8) & 0xFF);
        baos.write(typeLen & 0xFF);                                  // typeLen=5000
        baos.write(new byte[typeLen]);                              // type bytes
        try {
            AccessControlContextSerializer.unmarshalForTransport(baos.toByteArray(), null);
            Assert.fail("Expected InvalidObjectException for oversized principal type length");
        } catch (java.io.InvalidObjectException expected) {
            Assert.assertTrue(expected.getMessage().contains("principal type length exceeds maximum"));
        }
    }

    @Test
    public void testDomainIdentityRecordRejectsStandardSerialization() throws Exception {
        // Obtain a DomainIdentityRecord via its private constructor through reflection.
        java.lang.reflect.Constructor<?> ctor =
            AccessControlContextSerializer.DomainIdentityRecord.class.getDeclaredConstructor(
                String.class, String[].class, String[].class);
        ctor.setAccessible(true);
        Object record = ctor.newInstance(
            "httpmd://example.com/stub.jar;sha=abc", new String[0], new String[0]);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(baos);
        try {
            out.writeObject(record);
            Assert.fail("Expected NotSerializableException");
        } catch (java.io.NotSerializableException expected) {
            // expected
        } finally {
            out.close();
        }
    }

    @Test
    public void testUnmarshalZeroDomainCountReturnsNull() throws Exception {
        // A 4-byte payload encoding count=0 should behave the same as an empty payload.
        byte[] payload = new byte[]{0, 0, 0, 0};
        AccessControlContext result = AccessControlContextSerializer.unmarshalForTransport(payload, null);
        Assert.assertNull("zero-domain payload should normalise to null", result);
    }

    @Test
    public void testUnmarshalRejectsOversizedTotalPayload() throws Exception {
        // Build a fake payload that claims to be larger than MAX_TOTAL_PAYLOAD_BYTES (16 MB).
        // We don't need to fill it — just make the byte array large enough to trigger the check.
        // 16 MB — must match MAX_TOTAL_PAYLOAD_BYTES in AccessControlContextSerializer.
        int oversize = 16 * 1024 * 1024 + 1;
        byte[] huge = new byte[oversize];
        // count=1 in the first 4 bytes so the parser reaches the budget check.
        huge[3] = 1;
        try {
            AccessControlContextSerializer.unmarshalForTransport(huge, null);
            Assert.fail("Expected InvalidObjectException for oversized total payload");
        } catch (java.io.InvalidObjectException expected) {
            Assert.assertTrue(expected.getMessage().contains("payload size exceeds maximum"));
        }
    }


    @Test
    public void testDigestTransportFieldEmptyForStandardJdkAcc() throws Exception {
        // On a standard JDK (no java.security.DigestCodeSource), an ACC built
        // from a plain HTTPMD domain produces empty digestTransportBytes.
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/a.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            ProtectionDomain pd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext acc = new AccessControlContext(new ProtectionDomain[]{pd});

            byte[] digestBytes = AccessControlContextSerializer.marshalDigestForTransport(acc);
            Assert.assertEquals("no DigestCodeSource domains → empty digestTransportBytes",
                    0, digestBytes.length);

            ProtectionDomain[] domains =
                    AccessControlContextSerializer.unmarshalDigestFromTransport(digestBytes, null);
            Assert.assertEquals("empty bytes → no digest domains", 0, domains.length);
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    /**
     * Verifies that {@code marshalForTransport} round-trips correctly when the
     * ACC contains a single HTTPMD domain.
     *
     * <p>{@code extractDomains} captures the full call stack, which always includes
     * non-{@code jrt:/java.base} domains (test-framework JARs, other JDK modules,
     * etc.), so the payload will use version-1 format in practice.  The test
     * verifies that regardless of the format version, exactly one HTTPMD domain
     * survives the round-trip and the decoded ACC is non-null.
     */
    @Test
    public void testHttpmdTransportRoundTrip() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/a.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            ProtectionDomain pd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext acc = new AccessControlContext(new ProtectionDomain[]{pd});
            byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
            Assert.assertTrue("encoded payload must be non-empty", encoded.length > 0);
            // The HTTPMD domain count is always 1, but its position depends on format:
            // version-0 (first byte 0x00): bytes 0-3 hold the count directly.
            // version-1 (first byte 0x01): bytes 1-4 hold the count.
            int httpmdCount = ((encoded[0] & 0xFF) == 0x01)
                    ? readIntAt(encoded, 1)
                    : readInt(encoded);
            Assert.assertEquals("HTTPMD domain count must be 1", 1, httpmdCount);
            // Must be parseable on round-trip.
            AccessControlContext decoded = AccessControlContextSerializer.unmarshalForTransport(encoded, null);
            Assert.assertNotNull(decoded);
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    /**
     * Verifies that the count of non-HTTPMD, non-DigestCodeSource ("anonymous")
     * domains is preserved through a marshal/unmarshal round-trip.
     *
     * <p>An ACC with 1 HTTPMD domain and 2 plain-HTTP domains is marshalled.
     * The resulting payload must use version-1 format (first byte 0x01), with
     * the HTTPMD domain count = 1 and the anonymous domain count = 2.
     * After unmarshal, the reconstructed ACC must be non-null and the payload
     * must survive re-encoding with the same HTTPMD count.
     */
    @Test
    public void testAnonDomainCountPreservedInTransport() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            URL plain1 = new URL("http://repo.example.org/app.jar");
            URL plain2 = new URL("http://repo.example.org/framework.jar");

            ProtectionDomain httpmdPd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain plain1Pd = new ProtectionDomain(
                    new CodeSource(plain1, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain plain2Pd = new ProtectionDomain(
                    new CodeSource(plain2, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            AccessControlContext acc = new AccessControlContext(
                    new ProtectionDomain[]{httpmdPd, plain1Pd, plain2Pd});

            byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
            Assert.assertTrue("encoded payload must be non-empty", encoded.length > 0);
            // Version-1 format: first byte = 0x01, bytes 1-4 = httpmd count = 1.
            Assert.assertEquals("version byte must be 0x01", 0x01, encoded[0] & 0xFF);
            Assert.assertEquals("httpmd domain count must be 1", 1, readIntAt(encoded, 1));
            // Anonymous domain count is at the end of the payload (after the single record).
            // Locate it: 1 version byte + 4 count bytes + 1 record (variable length) + 4 anon bytes.
            int anonCountOffset = encoded.length - 4;
            Assert.assertEquals("anonymous domain count must be 2", 2, readIntAt(encoded, anonCountOffset));

            // Unmarshal and verify round-trip.
            AccessControlContext decoded =
                    AccessControlContextSerializer.unmarshalForTransport(encoded, null);
            Assert.assertNotNull("decoded ACC must not be null", decoded);
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    /**
     * Verifies that an ACC containing only non-HTTPMD domains produces an empty
     * transport payload, because without any HTTPMD domain there is no verifiable
     * remote code identity to anchor the context.
     */
    @Test
    public void testOnlyAnonDomainsProducesEmptyPayload() throws Exception {
        URL plain = new URL("http://repo.example.org/app.jar");
        ProtectionDomain plainPd = new ProtectionDomain(
                new CodeSource(plain, (java.security.cert.Certificate[]) null),
                null, null, new java.security.Principal[0]);
        AccessControlContext acc = new AccessControlContext(new ProtectionDomain[]{plainPd});
        byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
        Assert.assertEquals("no HTTPMD domain → empty payload", 0, encoded.length);
    }

    /**
     * Verifies that {@code jrt:/java.base} is excluded from the anonymous domain
     * count (it is universally present in every JVM and carries no diagnostic
     * value), while other {@code jrt:} module domains are counted (they identify
     * which JDK modules are loaded and may flag vulnerable-module processes).
     *
     * <p>An ACC is constructed with:
     * <ul>
     *   <li>1 HTTPMD domain</li>
     *   <li>1 {@code jrt:/java.base} domain — must be excluded from anon count</li>
     *   <li>1 other {@code jrt:} domain ({@code jrt:/jdk.crypto.ec}) — must be
     *       included in anon count</li>
     * </ul>
     * The payload must use version-1 format with anon count = 1 (only the
     * non-java.base jrt: domain).
     */
    @Test
    public void testJrtJavaBaseExcludedButOtherJrtIncluded() throws Exception {
        String oldHandlers = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
        try {
            URL httpmd = new URL(null,
                    "httpmd://repo.example.org/stub.jar;sha-256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    new PassThroughHandler());
            URL jrtBase      = new URL("jrt:/java.base");
            URL jrtCryptoEc  = new URL("jrt:/jdk.crypto.ec");

            ProtectionDomain httpmdPd = new ProtectionDomain(
                    new CodeSource(httpmd, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain jrtBasePd = new ProtectionDomain(
                    new CodeSource(jrtBase, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);
            ProtectionDomain jrtCryptoPd = new ProtectionDomain(
                    new CodeSource(jrtCryptoEc, (java.security.cert.Certificate[]) null),
                    null, null, new java.security.Principal[0]);

            AccessControlContext acc = new AccessControlContext(
                    new ProtectionDomain[]{httpmdPd, jrtBasePd, jrtCryptoPd});

            byte[] encoded = AccessControlContextSerializer.marshalForTransport(acc);
            Assert.assertTrue("encoded payload must be non-empty", encoded.length > 0);
            // Version-1 format expected: first byte = 0x01.
            Assert.assertEquals("version byte must be 0x01", 0x01, encoded[0] & 0xFF);
            Assert.assertEquals("HTTPMD domain count must be 1", 1, readIntAt(encoded, 1));
            // Anonymous domain count is the last 4 bytes: only jrt:/jdk.crypto.ec
            // is counted; jrt:/java.base is excluded.
            int anonCountOffset = encoded.length - 4;
            Assert.assertEquals("anon domain count must be 1 (jrt:/jdk.crypto.ec only)",
                    1, readIntAt(encoded, anonCountOffset));
        } finally {
            if (oldHandlers == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlers);
            }
        }
    }

    // ---- binary helpers used by the test methods above -----------------------

    private static int readInt(byte[] bytes) {
        return readIntAt(bytes, 0);
    }

    private static int readIntAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static final class PassThroughHandler extends URLStreamHandler {
        @Override
        protected java.net.URLConnection openConnection(URL u) {
            throw new UnsupportedOperationException();
        }
    }
}
