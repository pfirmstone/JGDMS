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

import java.io.ByteArrayOutputStream;
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
            Assert.assertEquals(1, readInt(encoded));

            Subject authenticated = new Subject();
            authenticated.getPrincipals().add(new X500Principal("CN=worker"));
            AccessControlContext reconstructed =
                    AccessControlContextSerializer.unmarshalForTransport(encoded, authenticated);
            byte[] reencoded = AccessControlContextSerializer.marshalForTransport(reconstructed);
            Assert.assertEquals(1, readInt(reencoded));
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
    public void testUnmarshalRejectsUnknownFormatVersion() throws Exception {
        // A transport blob starting with byte 0x02 is neither old-format (0x00)
        // nor new-format-v1 (0x01) and should be rejected.
        byte[] payload = new byte[]{0x02, 0, 0, 1, 0, 0, 0, 0};
        try {
            AccessControlContextSerializer.unmarshalForTransport(payload, null);
            Assert.fail("Expected InvalidObjectException for unknown format version");
        } catch (java.io.InvalidObjectException expected) {
            Assert.assertTrue(expected.getMessage().contains("Unknown ACC transport format version"));
        }
    }

    /**
     * Tests that a crafted new-format (version 0x01) transport blob containing a
     * single DigestCodeSource record is accepted by {@code unmarshalForTransport}.
     * On a standard JDK (no {@code java.security.DigestCodeSource}), the domain is
     * dropped (fail-secure) so the result may be {@code null} or an ACC with 0
     * domains; we only assert that parsing does not throw.
     */
    @Test
    public void testUnmarshalNewFormatDigestRecordDoesNotThrow() throws Exception {
        // Build a minimal new-format blob:
        // [0x01][4-byte count=1]
        // [record-type=0x01 (DigestCodeSource)]
        // [2-byte urlLen=0]                         -- null URL
        // [2-byte algLen][algLen bytes "SHA-256"]
        // [4-byte digestLen=32][32 zero bytes]       -- fake SHA-256 digest
        // [2-byte principalCount=0]
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01);               // FORMAT_VERSION_DIGEST
        writeInt(baos, 1);              // count = 1
        baos.write(0x01);               // RECORD_TYPE_DIGEST
        writeShort(baos, 0);            // urlLen = 0 (null URL)
        byte[] alg = "SHA-256".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeShort(baos, alg.length);   // algLen
        baos.write(alg);
        writeInt(baos, 32);             // digestLen = 32
        baos.write(new byte[32]);       // 32 zero bytes (fake digest)
        writeShort(baos, 0);            // principalCount = 0

        byte[] payload = baos.toByteArray();
        // Must not throw (DigestCodeSource absent → domain dropped → null or empty ACC)
        AccessControlContext result = AccessControlContextSerializer.unmarshalForTransport(payload, null);
        // result is either null (domain dropped, no remaining domains) or a valid ACC.
        // Both outcomes are acceptable on a standard JDK.
    }

    /**
     * Verifies that the old-format (HTTPMD-only) transport bytes produced by
     * {@code marshalForTransport} are still parseable after the new format
     * detection logic was introduced.
     */
    @Test
    public void testOldFormatStillReadableAfterFormatDetectionChange() throws Exception {
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
            // Old format: first 4 bytes encode the count (= 1)
            Assert.assertEquals(1, readInt(encoded));
            // Must be parseable on round-trip
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

    // ---- binary helpers used by the test methods above -----------------------

    private static int readInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8)  & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static final class PassThroughHandler extends URLStreamHandler {
        @Override
        protected java.net.URLConnection openConnection(URL u) {
            throw new UnsupportedOperationException();
        }
    }
}
