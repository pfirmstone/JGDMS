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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpiffeJwtDispatchIntegrationTest {

    private static SpiffeTestSvidFactory.FixtureSet fixtures;

    @BeforeClass
    public static void setUpFixtures() throws Exception {
        fixtures = SpiffeTestSvidFactory.createTesterReggieFixtureSet(
            java.nio.file.Files.createTempDirectory("spiffe-jwt-dispatch-"));
    }

    @AfterClass
    public static void tearDownFixtures() throws Exception {
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

    @Test
    public void spiffeWorkerSubjectAndJwtWireSubjectsRemainSeparated() throws Exception {
        Subject clientWorkerSubject = loadWorkerSubject(fixtures.tester);
        Subject userSubject = new Subject(
            true,
            Collections.singleton((Principal) new JwtPrincipal("sub:alice@example.org")),
            Collections.emptySet(),
            Collections.emptySet());

        assertTrue(hasTlsIdentity(clientWorkerSubject));
        assertFalse(hasTlsIdentity(userSubject));

        Subject[] wireSubjects = javax.security.auth.Subject.doAsPrivileged(
            clientWorkerSubject,
            (java.security.PrivilegedExceptionAction<Subject[]>) () ->
                Subject.callAs(userSubject, SpiffeJwtDispatchIntegrationTest::invokeGetAllUserSubjects),
            null);
        assertEquals(1, wireSubjects.length);
        assertTrue(wireSubjects[0].getPrincipals(JwtPrincipal.class).contains(
            new JwtPrincipal("sub:alice@example.org")));

        byte[] bytes = encodeUserSubjects(wireSubjects);
        List<?> decoded = decodeUserSubjects(bytes);
        assertEquals(1, decoded.size());
        Subject decodedSubject = (Subject) decoded.get(0);
        assertTrue(decodedSubject.getPrincipals(JwtPrincipal.class).contains(
            new JwtPrincipal("sub:alice@example.org")));
    }

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
        Method method = SslEndpointImpl.class.getDeclaredMethod("hasTlsIdentity", Subject.class);
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
}
