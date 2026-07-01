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
/* @test
 * @summary Integration test that the SPIFFE workload (worker) Subject and the
 *          JWT user (wire) Subjects remain separated across a JERI dispatch:
 *          the TLS/SPIFFE-authenticated worker Subject carries a TLS identity
 *          while a JwtPrincipal-only user Subject does not, and only the user
 *          Subject's JwtPrincipal is propagated over the wire and survives an
 *          encode/decode round-trip.  Exercises BasicInvocationHandler /
 *          BasicInvocationDispatcher / Utilities internals via reflection, which
 *          resolve at runtime from jgdms-jeri.jar on the jtreg classpath.  SVIDs
 *          are generated at test time using SpiffeTestSvidFactory (which shells
 *          out to keytool) — no certificate material is committed to the
 *          repository.
 * @build SpiffeJwtDispatchIntegrationTest
 * @run main/othervm SpiffeJwtDispatchIntegrationTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.security.jwt.JwtPrincipal;

import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;
import au.net.zeus.jgdms.spiffe.SpiffeTestSvidFactory;

/**
 * Integration test confirming that the SPIFFE worker Subject and the JWT wire
 * (user) Subjects remain separated across a JERI dispatch.
 *
 * <p>SVID certificates are generated at test time using
 * {@link SpiffeTestSvidFactory} — no certificate material is committed to
 * the repository.
 */
public class SpiffeJwtDispatchIntegrationTest {

    private static SpiffeTestSvidFactory.FixtureSet fixtures;

    // -----------------------------------------------------------------------
    // Fixture setup / teardown
    // -----------------------------------------------------------------------

    private static void setUpFixtures() throws Exception {
        fixtures = SpiffeTestSvidFactory.createTesterReggieFixtureSet(
            java.nio.file.Files.createTempDirectory("spiffe-jwt-dispatch-"));
    }

    private static void tearDownFixtures() throws Exception {
        if (fixtures != null) {
            java.nio.file.Files.walk(fixtures.dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { java.nio.file.Files.deleteIfExists(p); }
                    catch (java.io.IOException e) {
                        System.err.println("WARN: could not delete " + p + ": " + e);
                    }
                });
        }
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        try {
            setUpFixtures();

            run("spiffeWorkerSubjectAndJwtWireSubjectsRemainSeparated",
                    SpiffeJwtDispatchIntegrationTest::spiffeWorkerSubjectAndJwtWireSubjectsRemainSeparated);

            System.out.println("\nAll SpiffeJwtDispatchIntegrationTest tests PASSED.");
        } finally {
            tearDownFixtures();
        }
    }

    // -----------------------------------------------------------------------
    // Test case
    // -----------------------------------------------------------------------

    private static void spiffeWorkerSubjectAndJwtWireSubjectsRemainSeparated() throws Exception {
        Subject clientWorkerSubject = loadWorkerSubject(fixtures.tester);
        Subject userSubject = new Subject(
            true,
            Collections.singleton((Principal) new JwtPrincipal("sub:alice@example.org")),
            Collections.emptySet(),
            Collections.emptySet());

        assertTrue("worker Subject must carry a TLS identity",
                hasTlsIdentity(clientWorkerSubject));
        assertFalse("JWT-only user Subject must not carry a TLS identity",
                hasTlsIdentity(userSubject));

        Subject[] wireSubjects = javax.security.auth.Subject.doAsPrivileged(
            clientWorkerSubject,
            (java.security.PrivilegedExceptionAction<Subject[]>) () ->
                Subject.callAs(userSubject, SpiffeJwtDispatchIntegrationTest::invokeGetAllUserSubjects),
            null);
        assertEqual("exactly one wire Subject", 1, wireSubjects.length);
        assertTrue("wire Subject must carry the alice JwtPrincipal",
                wireSubjects[0].getPrincipals(JwtPrincipal.class).contains(
                    new JwtPrincipal("sub:alice@example.org")));

        byte[] bytes = encodeUserSubjects(wireSubjects);
        List<?> decoded = decodeUserSubjects(bytes);
        assertEqual("exactly one decoded Subject", 1, decoded.size());
        Subject decodedSubject = (Subject) decoded.get(0);
        assertTrue("decoded Subject must carry the alice JwtPrincipal",
                decodedSubject.getPrincipals(JwtPrincipal.class).contains(
                    new JwtPrincipal("sub:alice@example.org")));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Subject loadWorkerSubject(SpiffeTestSvidFactory.Fixture fixture)
        throws Exception
    {
        SpiffeCredentialManager.Svid svid =
            new SpiffeCredentialManager.FileSvidSource(fixture.svidPem, fixture.svidKeyPem)
                .fetch();
        X509Certificate leaf = svid.leafCertificate();
        X500PrivateCredential privateCredential =
            new X500PrivateCredential(leaf, svid.privateKey);

        return new Subject(
            true,
            Set.of((Principal) leaf.getSubjectX500Principal(),
                   (Principal) SpiffePrincipal.fromCertificate(leaf).get(0)),
            Set.of((Object) svid.certPath),
            Set.of((Object) privateCredential));
    }

    private static boolean hasTlsIdentity(Subject subject) throws Exception {
        // hasTlsIdentity is declared `protected static` on the package-private
        // net.jini.jeri.ssl.Utilities base class and merely inherited by
        // SslEndpointImpl; getDeclaredMethod does not see inherited methods, so
        // reflect on the declaring class.  This jtreg test lives in the default
        // package and cannot name the package-private Utilities class directly,
        // so resolve it by name from jgdms-jeri.jar on the classpath.
        Class<?> utilities = Class.forName("net.jini.jeri.ssl.Utilities");
        Method method = utilities.getDeclaredMethod("hasTlsIdentity", Subject.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, subject);
    }

    private static Subject[] invokeGetAllUserSubjects() throws Exception {
        Method method = BasicInvocationHandler.class.getDeclaredMethod("getAllUserSubjects");
        method.setAccessible(true);
        return (Subject[]) method.invoke(null);
    }

    private static byte[] encodeUserSubjects(Subject[] subjects) throws Exception {
        Method method = BasicInvocationHandler.class.getDeclaredMethod(
            "writeUserSubjects", java.io.OutputStream.class, Subject[].class);
        method.setAccessible(true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        method.invoke(null, out, subjects);
        return out.toByteArray();
    }

    private static List<?> decodeUserSubjects(byte[] bytes) throws Exception {
        Method method = BasicInvocationDispatcher.class.getDeclaredMethod(
            "readUserSubjects", java.io.InputStream.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, new ByteArrayInputStream(bytes));
    }

    private static void run(String name, ThrowingRunnable test) throws Exception {
        System.out.print("  " + name + " ... ");
        test.run();
        System.out.println("PASS");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // -----------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------

    private static void assertTrue(String message, boolean condition) {
        if (!condition) throw new RuntimeException("ASSERTION FAILED: " + message);
    }

    private static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private static void assertEqual(String message, long expected, long actual) {
        if (expected != actual)
            throw new RuntimeException("ASSERTION FAILED: " + message
                    + " [expected=" + expected + ", actual=" + actual + "]");
    }
}
