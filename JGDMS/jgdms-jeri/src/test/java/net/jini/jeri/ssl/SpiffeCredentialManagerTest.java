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

import net.jini.jeri.ssl.SpiffeCredentialManager.FileSvidSource;
import net.jini.jeri.ssl.SpiffeCredentialManager.Svid;
import net.jini.jeri.ssl.SpiffeCredentialManager.SvidSource;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link SpiffeCredentialManager}.
 *
 * Stage 4: credential-manager lifecycle — start, close, rotation, and
 * constructor validation.  Uses the test SVID resources and a stub
 * {@link SvidSource} to avoid real filesystem/scheduling coupling where
 * unnecessary.
 */
public class SpiffeCredentialManagerTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Path resourcePath(String cp) throws Exception {
        URI uri = SpiffeCredentialManagerTest.class.getResource(cp).toURI();
        return Paths.get(uri);
    }

    private static FileSvidSource reggieSource() throws Exception {
        return new FileSvidSource(
                resourcePath("/spiffe/reggie/svid.pem"),
                resourcePath("/spiffe/reggie/svid_key.pem"));
    }

    private static Subject mutableSubject() {
        return new Subject();
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void constructNullSubject() throws Exception {
        new SpiffeCredentialManager(null, reggieSource(), 60L);
    }

    @Test(expected = NullPointerException.class)
    public void constructNullSvidSource() {
        new SpiffeCredentialManager(mutableSubject(), (SvidSource) null, 60L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructZeroRenewalLead() throws Exception {
        new SpiffeCredentialManager(mutableSubject(), reggieSource(), 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructNegativeRenewalLead() throws Exception {
        new SpiffeCredentialManager(mutableSubject(), reggieSource(), -1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructReadOnlySubject() throws Exception {
        Subject ro = new Subject();
        ro.setReadOnly();
        new SpiffeCredentialManager(ro, reggieSource(), 60L);
    }

    @Test(expected = NullPointerException.class)
    public void constructDirNullSubject() {
        new SpiffeCredentialManager(null, Paths.get("/tmp"));
    }

    // -----------------------------------------------------------------------
    // start() — populates Subject
    // -----------------------------------------------------------------------

    @Test
    public void startPopulatesX500Principal() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
            assertEquals("start() should add exactly one X500Principal", 1, x500s.size());
            String dn = x500s.iterator().next().getName();
            assertTrue("X500Principal DN should contain Reggie: " + dn,
                    dn.contains("Reggie"));
        }
    }

    @Test
    public void startPopulatesSpiffePrincipal() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
            assertEquals("start() should add exactly one SpiffePrincipal", 1, spiffes.size());
            assertEquals("spiffe://test.jgdms.local/svc/reggie",
                    spiffes.iterator().next().getName());
        }
    }

    @Test
    public void startPopulatesCertPath() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<CertPath> certPaths = subject.getPublicCredentials(CertPath.class);
            assertEquals("start() should add exactly one CertPath", 1, certPaths.size());
            assertFalse("CertPath must not be empty",
                    certPaths.iterator().next().getCertificates().isEmpty());
        }
    }

    @Test
    public void startPopulatesPrivateCredential() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<X500PrivateCredential> privs =
                    subject.getPrivateCredentials(X500PrivateCredential.class);
            assertEquals("start() should add exactly one X500PrivateCredential",
                    1, privs.size());
            assertNotNull(privs.iterator().next().getPrivateKey());
        }
    }

    // -----------------------------------------------------------------------
    // close() — clears Subject
    // -----------------------------------------------------------------------

    @Test
    public void closeClearsPrincipals() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        assertFalse("Principals should be present after start()",
                subject.getPrincipals().isEmpty());

        mgr.close();

        assertTrue("Principals should be cleared after close()",
                subject.getPrincipals().isEmpty());
    }

    @Test
    public void closeClearsCredentials() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();

        assertTrue("Public credentials should be cleared after close()",
                subject.getPublicCredentials(CertPath.class).isEmpty());
        assertTrue("Private credentials should be cleared after close()",
                subject.getPrivateCredentials(X500PrivateCredential.class).isEmpty());
    }

    @Test
    public void closeIsIdempotent() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        mgr.close(); // second close should not throw
    }

    // -----------------------------------------------------------------------
    // start() after close() throws IllegalStateException
    // -----------------------------------------------------------------------

    @Test
    public void startAfterCloseThrows() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        try {
            mgr.start();
            fail("Expected IllegalStateException after close()");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // refresh() after close() throws IllegalStateException
    // -----------------------------------------------------------------------

    @Test
    public void refreshAfterCloseThrows() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        try {
            mgr.refresh();
            fail("Expected IllegalStateException after close()");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // SVID rotation: refresh() replaces credentials atomically
    // -----------------------------------------------------------------------

    @Test
    public void refreshReplacesCredentials() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();

            CertPath firstCertPath =
                    subject.getPublicCredentials(CertPath.class).iterator().next();

            mgr.refresh();

            Set<CertPath> after = subject.getPublicCredentials(CertPath.class);
            assertEquals("Exactly one CertPath after refresh()", 1, after.size());
            // The CertPath content should still be the reggie cert (same source).
            assertNotNull(after.iterator().next());
        }
    }

    // -----------------------------------------------------------------------
    // start() failure propagates as IOException / GeneralSecurityException
    // -----------------------------------------------------------------------

    @Test
    public void startFailureWhenSvidSourceThrows() throws Exception {
        SvidSource failing = new SvidSource() {
            @Override
            public Svid fetch() throws IOException {
                throw new IOException("simulated fetch failure");
            }
        };
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, failing, 60L)) {
            mgr.start();
            fail("Expected IOException from failing SvidSource");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("simulated fetch failure"));
        }
    }

    // -----------------------------------------------------------------------
    // Subject isolation: pre-existing principals are untouched by close()
    // -----------------------------------------------------------------------

    @Test
    public void closeOnlyRemovesManagedPrincipals() throws Exception {
        Subject subject = mutableSubject();
        X500Principal external = new X500Principal("CN=External");
        subject.getPrincipals().add(external);

        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();

        assertTrue("Pre-existing external principal should survive close()",
                subject.getPrincipals().contains(external));
    }
}
