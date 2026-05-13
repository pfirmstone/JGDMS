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

package net.jini.jeri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.security.jwt.JwtRawToken;
import net.jini.security.jwt.JwtVerificationException;
import net.jini.security.jwt.JwtVerifier;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the multi-Subject wire-protocol encoding added to
 * {@link BasicInvocationHandler#writeUserSubjects} and read by
 * {@link BasicInvocationDispatcher}.
 *
 * <p>The wire format for version 0x02 user-Subject block is:
 * <pre>
 *   subjectCount    : u16
 *   for each Subject:
 *     principalCount  : u16
 *     for each principal:
 *       classNameLength : u16
 *       classNameBytes  : UTF-8
 *       nameLength      : u16
 *       nameBytes       : UTF-8
 *     jwtCount        : u8
 *     for each JWT:
 *       jwtLength     : u32
 *       jwtBytes      : UTF-8
 * </pre>
 */
public class MultiSubjectWireProtocolTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads a 2-byte big-endian unsigned short from {@code buf} at {@code off}. */
    private static int readU16(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 8) | (buf[off + 1] & 0xFF);
    }

    private static String readUtf8Prefixed(byte[] buf, int[] cursor) {
        int len = readU16(buf, cursor[0]);
        cursor[0] += 2;
        String s = new String(buf, cursor[0], len, java.nio.charset.StandardCharsets.UTF_8);
        cursor[0] += len;
        return s;
    }

    /** Helper: invoke the private {@code readUserSubjects} method via reflection. */
    @SuppressWarnings("unchecked")
    private static List<Subject> invokeReadUserSubjects(byte[] bytes) throws Exception {
        Method m = BasicInvocationDispatcher.class
            .getDeclaredMethod("readUserSubjects", java.io.InputStream.class);
        m.setAccessible(true);
        return (List<Subject>) m.invoke(null, new ByteArrayInputStream(bytes));
    }

    /** Builds a minimal valid JWT payload Base64url-encoded string with given exp epoch. */
    private static String buildJwt(long expEpoch) {
        return buildJwt(expEpoch, "testuser");
    }

    /** Builds a minimal valid JWT with a unique subject to avoid cross-test cache collisions. */
    private static String buildJwt(long expEpoch, String subject) {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + subject + "\",\"iss\":\"https://issuer\","
                        + "\"exp\":" + expEpoch + "}").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        return header + "." + payload + ".fakesig";
    }

    // -------------------------------------------------------------------------
    // Lifecycle: reset JwtVerifier after each test to avoid state leakage
    // -------------------------------------------------------------------------

    @Before
    @After
    public void resetJwtVerifier() {
        BasicInvocationDispatcher.setJwtVerifier(null);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testEmptySubjectArrayWritesZeroCount() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[0]);
        byte[] bytes = baos.toByteArray();
        Assert.assertEquals("should be exactly 2 bytes for count=0", 2, bytes.length);
        Assert.assertEquals(0, readU16(bytes, 0));
    }

    @Test
    public void testSingleSubjectSinglePrincipalRoundTrip() throws IOException {
        X500Principal p = new X500Principal("CN=alice");
        Set<Principal> principals = Collections.singleton(p);
        Subject s = new Subject(true, principals, Collections.emptySet(), Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});
        byte[] bytes = baos.toByteArray();

        int[] cursor = {0};

        // subjectCount = 1
        int subjectCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, subjectCount);

        // principalCount = 1
        int principalCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, principalCount);

        // className
        String className = readUtf8Prefixed(bytes, cursor);
        Assert.assertEquals(X500Principal.class.getName(), className);

        // name
        String name = readUtf8Prefixed(bytes, cursor);
        Assert.assertEquals(p.getName(), name);

        // jwtCount = 0 (no JwtRawToken credentials)
        int jwtCount = bytes[cursor[0]++] & 0xFF;
        Assert.assertEquals(0, jwtCount);

        Assert.assertEquals("should have consumed all bytes", bytes.length, cursor[0]);
    }

    @Test
    public void testMultipleSubjectsRoundTrip() throws IOException {
        X500Principal pA = new X500Principal("CN=alice");
        X500Principal pB = new X500Principal("CN=bob");
        X500Principal pC = new X500Principal("CN=charlie");

        Subject s1 = new Subject(true,
            Collections.singleton((Principal) pA),
            Collections.emptySet(), Collections.emptySet());
        Subject s2 = new Subject(true,
            new HashSet<Principal>(Arrays.asList(pB, pC)),
            Collections.emptySet(), Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s1, s2});
        byte[] bytes = baos.toByteArray();

        int[] cursor = {0};

        // subjectCount = 2
        int subjectCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(2, subjectCount);

        // Subject 1: 1 principal
        int pc1 = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, pc1);
        readUtf8Prefixed(bytes, cursor); // className
        readUtf8Prefixed(bytes, cursor); // name
        int jwtCount1 = bytes[cursor[0]++] & 0xFF; // jwtCount = 0
        Assert.assertEquals(0, jwtCount1);

        // Subject 2: 2 principals
        int pc2 = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(2, pc2);
        for (int i = 0; i < 2; i++) {
            readUtf8Prefixed(bytes, cursor); // className
            readUtf8Prefixed(bytes, cursor); // name
        }
        int jwtCount2 = bytes[cursor[0]++] & 0xFF; // jwtCount = 0
        Assert.assertEquals(0, jwtCount2);

        Assert.assertEquals("should have consumed all bytes", bytes.length, cursor[0]);
    }

    @Test
    public void testSubjectWithNoPrincipals() throws IOException {
        // A Subject with no principals should encode principalCount=0 and jwtCount=0.
        Subject empty = new Subject(true,
            Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{empty});
        byte[] bytes = baos.toByteArray();
        // 2 bytes subjectCount + 2 bytes principalCount + 1 byte jwtCount = 5 bytes
        Assert.assertEquals(5, bytes.length);
        Assert.assertEquals(1, readU16(bytes, 0)); // subjectCount
        Assert.assertEquals(0, readU16(bytes, 2)); // principalCount
        Assert.assertEquals(0, bytes[4] & 0xFF);   // jwtCount
    }

    /**
     * §16.7 Option A — a Principal whose UTF-8-encoded class name or name
     * exceeds 65 535 bytes must cause {@link IOException} at write time rather
     * than being silently truncated.
     */
    @Test
    public void testOversizedPrincipalFieldThrowsIOException() {
        // Build a name whose UTF-8 encoding is exactly 65 536 bytes (> 0xFFFF).
        String oversized = "A".repeat(65536);
        Principal oversizedPrincipal = new java.security.Principal() {
            @Override public String getName() { return oversized; }
        };
        Set<Principal> principals = Collections.singleton(oversizedPrincipal);
        Subject s = new Subject(true, principals,
            Collections.emptySet(), Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});
            Assert.fail("Expected IOException for oversized principal field");
        } catch (IOException expected) {
            // pass: the message should mention the byte count
            Assert.assertTrue(
                "Exception message should mention byte count",
                expected.getMessage().contains("65536"));
        }
    }

    /**
     * Verifies that the cached {@code CURRENT_ALL_METHOD} field is set on a
     * DirtyChai JDK (where {@code Subject.currentAll()} exists) and is null on a
     * standard JDK.
     */
    @Test
    public void testCurrentAllMethodCachedCorrectly() throws Exception {
        java.lang.reflect.Field f = BasicInvocationHandler.class
            .getDeclaredField("CURRENT_ALL_METHOD");
        f.setAccessible(true);
        java.lang.reflect.Method cached = (java.lang.reflect.Method) f.get(null);

        // Probe whether Subject.currentAll() exists in this JVM at runtime.
        java.lang.reflect.Method probe = null;
        try {
            probe = Subject.class.getMethod("currentAll");
        } catch (NoSuchMethodException ignored) {
            // standard JDK
        }

        if (probe != null) {
            Assert.assertNotNull("CURRENT_ALL_METHOD must be set on a DirtyChai JDK", cached);
            Assert.assertEquals(probe, cached);
        } else {
            Assert.assertNull("CURRENT_ALL_METHOD must be null on a standard JDK", cached);
        }
    }

    /**
     * Verifies that {@code getAllUserSubjects()} returns the Subject(s) bound to
     * the current thread.  On DirtyChai it uses {@code Subject.currentAll()};
     * on a standard JDK it falls back to {@code Subject.current()}.
     */
    @Test
    public void testGetAllUserSubjectsReturnsCurrentSubject() throws Exception {
        X500Principal p = new X500Principal("CN=testuser");
        Subject s = new Subject(true,
            Collections.singleton((Principal) p),
            Collections.emptySet(), Collections.emptySet());

        java.lang.reflect.Method getAllUserSubjects = BasicInvocationHandler.class
            .getDeclaredMethod("getAllUserSubjects");
        getAllUserSubjects.setAccessible(true);

        Subject[] result = Subject.callAs(s, () -> {
            try {
                return (Subject[]) getAllUserSubjects.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Assert.assertNotNull("getAllUserSubjects must not return null", result);
        Assert.assertTrue("getAllUserSubjects must return at least one Subject", result.length >= 1);
        // The first Subject must contain our principal.
        boolean found = false;
        for (Subject sub : result) {
            if (sub.getPrincipals().contains(p)) { found = true; break; }
        }
        Assert.assertTrue("bound Subject must be present in the returned array", found);
    }

    // -------------------------------------------------------------------------
    // JWT wire extension tests (Option D, Work Item 44)
    // -------------------------------------------------------------------------

    /**
     * A Subject that has a {@link JwtRawToken} public credential encodes
     * {@code jwtCount=1} followed by the raw token bytes.
     */
    @Test
    public void testSubjectWithJwtRawTokenEncodesJwtBlock() throws IOException {
        long futureExp = System.currentTimeMillis() / 1000L + 3600;
        String rawJwt = buildJwt(futureExp, "alice-encode-test");

        X500Principal p = new X500Principal("CN=alice");
        Set<Principal> principals = Collections.singleton(p);
        Set<Object> pubCreds = Collections.singleton((Object) new JwtRawToken(rawJwt));
        Subject s = new Subject(true, principals, pubCreds, Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});
        byte[] bytes = baos.toByteArray();

        int[] cursor = {0};
        int subjectCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, subjectCount);
        int principalCount = readU16(bytes, cursor[0]); cursor[0] += 2;
        Assert.assertEquals(1, principalCount);
        readUtf8Prefixed(bytes, cursor); // className
        readUtf8Prefixed(bytes, cursor); // name

        // jwtCount should be 1
        int jwtCount = bytes[cursor[0]++] & 0xFF;
        Assert.assertEquals(1, jwtCount);

        // jwtLength (4 bytes BE)
        int jwtLen = ((bytes[cursor[0]] & 0xFF) << 24)
                   | ((bytes[cursor[0] + 1] & 0xFF) << 16)
                   | ((bytes[cursor[0] + 2] & 0xFF) << 8)
                   | (bytes[cursor[0] + 3] & 0xFF);
        cursor[0] += 4;
        byte[] jwtBytes = Arrays.copyOfRange(bytes, cursor[0], cursor[0] + jwtLen);
        cursor[0] += jwtLen;

        Assert.assertEquals(rawJwt,
            new String(jwtBytes, java.nio.charset.StandardCharsets.UTF_8));
        Assert.assertEquals("should have consumed all bytes", bytes.length, cursor[0]);
    }

    /**
     * A round-trip (write + read) of a Subject carrying a {@link JwtRawToken}
     * should successfully deserialise when no verifier is registered.
     */
    @Test
    public void testJwtRoundTripNoVerifier() throws Exception {
        long futureExp = System.currentTimeMillis() / 1000L + 3600;
        String rawJwt = buildJwt(futureExp, "alice-roundtrip-no-verifier");

        X500Principal p = new X500Principal("CN=alice");
        Set<Object> pubCreds = Collections.singleton((Object) new JwtRawToken(rawJwt));
        Subject s = new Subject(true,
            Collections.singleton((Principal) p), pubCreds, Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});

        // Read back — no verifier registered, so JWT bytes are silently discarded.
        List<Subject> decoded = invokeReadUserSubjects(baos.toByteArray());
        Assert.assertEquals(1, decoded.size());
        Assert.assertTrue(decoded.get(0).getPrincipals().stream()
            .anyMatch(pr -> pr.getName().contains("alice")));
    }

    /**
     * When a {@link JwtVerifier} is registered and the token is valid,
     * the round-trip should succeed and the verifier should be called.
     */
    @Test
    public void testJwtRoundTripWithPassingVerifier() throws Exception {
        long futureExp = System.currentTimeMillis() / 1000L + 3600;
        String rawJwt = buildJwt(futureExp, "bob-passing-verifier");

        final boolean[] called = {false};
        BasicInvocationDispatcher.setJwtVerifier(jwt -> { called[0] = true; });

        X500Principal p = new X500Principal("CN=bob");
        Set<Object> pubCreds = Collections.singleton((Object) new JwtRawToken(rawJwt));
        Subject s = new Subject(true,
            Collections.singleton((Principal) p), pubCreds, Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});

        List<Subject> decoded = invokeReadUserSubjects(baos.toByteArray());
        Assert.assertEquals(1, decoded.size());
        Assert.assertTrue("Verifier must have been called", called[0]);
    }

    /**
     * When the {@link JwtVerifier} throws {@link JwtVerificationException},
     * {@code readUserSubjects} must propagate an {@link java.io.IOException}.
     */
    @Test
    public void testJwtVerificationFailureThrowsIOException() throws Exception {
        long futureExp = System.currentTimeMillis() / 1000L + 3600;
        String rawJwt = buildJwt(futureExp, "charlie-verification-failure");

        BasicInvocationDispatcher.setJwtVerifier(jwt -> {
            throw new JwtVerificationException("test rejection");
        });

        X500Principal p = new X500Principal("CN=charlie");
        Set<Object> pubCreds = Collections.singleton((Object) new JwtRawToken(rawJwt));
        Subject s = new Subject(true,
            Collections.singleton((Principal) p), pubCreds, Collections.emptySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, new Subject[]{s});

        try {
            invokeReadUserSubjects(baos.toByteArray());
            Assert.fail("Expected IOException from JWT verification failure");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            Assert.assertTrue("Should be IOException, got: " + cause,
                cause instanceof java.io.IOException);
            Assert.assertTrue(cause.getMessage().contains("test rejection"));
        }
    }

    /**
     * A second call with the same token should NOT re-invoke the verifier
     * (connection-level cache hit).
     */
    @Test
    public void testJwtCachePreventsDoubleVerification() throws Exception {
        long futureExp = System.currentTimeMillis() / 1000L + 3600;
        String rawJwt = buildJwt(futureExp, "dave-cache-test-unique-" + System.nanoTime());

        final int[] callCount = {0};
        BasicInvocationDispatcher.setJwtVerifier(jwt -> { callCount[0]++; });

        X500Principal p = new X500Principal("CN=dave");
        Set<Object> pubCreds = Collections.singleton((Object) new JwtRawToken(rawJwt));
        Subject s = new Subject(true,
            Collections.singleton((Principal) p), pubCreds, Collections.emptySet());

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos1, new Subject[]{s});
        invokeReadUserSubjects(baos1.toByteArray());

        // Second call with same token — should be served from cache.
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos2, new Subject[]{s});
        invokeReadUserSubjects(baos2.toByteArray());

        Assert.assertEquals("Verifier should only be called once for the same token", 1, callCount[0]);
    }
}
