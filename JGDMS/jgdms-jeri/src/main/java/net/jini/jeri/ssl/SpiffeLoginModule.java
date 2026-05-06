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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.cert.CertPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

/**
 * A JAAS {@link LoginModule} that populates a {@link Subject} with credentials
 * loaded from SPIFFE X.509-SVID PEM files on the local filesystem.
 *
 * <p>This module is designed for use in the JGDMS QA test harness as a
 * drop-in replacement for {@code KeyStoreLoginModule} in the {@code jsse}
 * configuration.  It reads SVID credentials from PEM files rather than PKCS12
 * keystores, which matches the production SPIRE credential delivery mechanism.
 *
 * <h2>Module options</h2>
 * <table border="1">
 *   <tr><th>Option</th><th>Description</th></tr>
 *   <tr><td>{@code serviceRole}</td>
 *       <td>The service role name (e.g. {@code reggie}, {@code fiddler}).
 *           Used to construct the PEM file path as
 *           {@code <spiffeDir>/<serviceRole>/svid.pem} and
 *           {@code <spiffeDir>/<serviceRole>/svid_key.pem}.</td></tr>
 *   <tr><td>{@code svidPem}</td>
 *       <td>Absolute path to the SVID certificate PEM file.  Overrides the
 *           path derived from {@code serviceRole} and the system
 *           property.</td></tr>
 *   <tr><td>{@code keyPem}</td>
 *       <td>Absolute path to the SVID private key PEM file.  Overrides the
 *           path derived from {@code serviceRole} and the system
 *           property.</td></tr>
 *   <tr><td>{@code debug}</td>
 *       <td>Set to {@code true} to enable verbose FINE-level logging.</td></tr>
 * </table>
 *
 * <h2>System property</h2>
 * <p>When {@code serviceRole} is used, the base directory is read from
 * {@value #SPIFFE_DIR_PROPERTY}.  Example:
 * <pre>
 *   -Dnet.jini.jeri.ssl.spiffe.dir=/qa/harness/trust/spiffe
 * </pre>
 * Results in paths:
 * <pre>
 *   /qa/harness/trust/spiffe/reggie/svid.pem
 *   /qa/harness/trust/spiffe/reggie/svid_key.pem
 * </pre>
 *
 * <h2>Subject population</h2>
 * <p>On successful {@code commit()}, the module adds the following items to
 * the subject:
 * <ul>
 *   <li>Principals: {@link X500Principal} (from SVID Subject DN) and zero or
 *       more {@link SpiffePrincipal} objects (from URI SANs in the SVID
 *       leaf).</li>
 *   <li>Public credentials: the {@link CertPath} containing the SVID leaf and
 *       any intermediates.</li>
 *   <li>Private credentials: an {@link X500PrivateCredential} coupling the
 *       SVID leaf certificate to its private key.</li>
 * </ul>
 *
 * <h2>Example JAAS configuration ({@code spiffelogins})</h2>
 * <pre>
 * org.apache.river.Reggie {
 *     net.jini.jeri.ssl.SpiffeLoginModule required
 *         serviceRole="reggie";
 * };
 * </pre>
 *
 * @see SpiffePrincipal
 * @see SpiffeCredentialManager
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public final class SpiffeLoginModule implements LoginModule {

    private static final Logger logger =
            Logger.getLogger(SpiffeLoginModule.class.getName());

    /**
     * System property specifying the base directory containing per-role SVID
     * subdirectories.  Each subdirectory is named after the service role and
     * must contain {@code svid.pem} and {@code svid_key.pem}.
     */
    public static final String SPIFFE_DIR_PROPERTY =
            "net.jini.jeri.ssl.spiffe.dir";

    // -----------------------------------------------------------------------
    // State set during initialize()
    // -----------------------------------------------------------------------

    private Subject subject;
    private Map<String, ?> options;
    private boolean debug;

    // -----------------------------------------------------------------------
    // State set during login()
    // -----------------------------------------------------------------------

    private SpiffeCredentialManager.Svid svid;

    // -----------------------------------------------------------------------
    // State set during commit() — tracked for logout()
    // -----------------------------------------------------------------------

    private final List<Principal> addedPrincipals = new ArrayList<>();
    private CertPath addedCertPath;
    private X500PrivateCredential addedPrivateCredential;
    private boolean committed;

    // -----------------------------------------------------------------------
    // LoginModule implementation
    // -----------------------------------------------------------------------

    @Override
    public void initialize(Subject subject,
                           CallbackHandler callbackHandler,
                           Map<String, ?> sharedState,
                           Map<String, ?> options) {
        this.subject = subject;
        this.options = options;
        this.debug   = Boolean.parseBoolean(
                optionString("debug", "false"));
    }

    /**
     * Resolves the SVID PEM file paths from module options / system property
     * and loads the SVID via {@link SpiffeCredentialManager.FileSvidSource}.
     *
     * @throws LoginException if the SVID cannot be read or parsed
     */
    @Override
    public boolean login() throws LoginException {
        Path svidPemPath = resolveSvidPem();
        Path keyPemPath  = resolveKeyPem();

        if (debug) {
            logger.log(Level.FINE,
                    "SpiffeLoginModule: loading SVID from {0}", svidPemPath);
        }

        SpiffeCredentialManager.FileSvidSource source =
                new SpiffeCredentialManager.FileSvidSource(svidPemPath, keyPemPath);
        try {
            svid = source.fetch();
        } catch (IOException | GeneralSecurityException e) {
            LoginException le = new LoginException(
                    "SpiffeLoginModule: cannot load SVID from "
                    + svidPemPath + ": " + e.getMessage());
            le.initCause(e);
            throw le;
        }
        return true;
    }

    /**
     * Adds the SVID principals and credentials to the subject.
     */
    @Override
    public boolean commit() throws LoginException {
        if (svid == null) {
            return false;
        }
        java.security.cert.X509Certificate leaf = svid.leafCertificate();

        // Principals derived from SVID leaf certificate.
        X500Principal x500 = leaf.getSubjectX500Principal();
        List<SpiffePrincipal> spiffes = SpiffePrincipal.fromCertificate(leaf);

        X500PrivateCredential xpc = new X500PrivateCredential(
                leaf, svid.privateKey);

        // Synchronize the entire add on subject so that a concurrent JERI SSL
        // handshake always observes a consistent state: either all three
        // components (principals, public credential, private credential) are
        // present or none of them are.
        synchronized (subject) {
            subject.getPrincipals().add(x500);
            subject.getPrincipals().addAll(spiffes);
            subject.getPublicCredentials().add(svid.certPath);
            subject.getPrivateCredentials().add(xpc);
        }
        addedPrincipals.add(x500);
        addedPrincipals.addAll(spiffes);
        addedCertPath = svid.certPath;
        addedPrivateCredential = xpc;

        committed = true;
        if (debug) {
            logger.log(Level.FINE,
                    "SpiffeLoginModule: committed principals {0}",
                    addedPrincipals);
        }
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        svid = null;
        clearAdded();
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        svid = null;
        clearAdded();
        return true;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void clearAdded() {
        if (!committed) return;
        // Synchronize on subject to match the atomicity established in commit().
        synchronized (subject) {
            subject.getPrincipals().removeAll(addedPrincipals);
            subject.getPublicCredentials().remove(addedCertPath);
            subject.getPrivateCredentials().remove(addedPrivateCredential);
        }
        addedPrincipals.clear();
        addedCertPath = null;
        addedPrivateCredential = null;
        committed = false;
    }

    /**
     * Resolves the path to the SVID certificate PEM file.
     * Priority: explicit {@code svidPem} option > derived from
     * {@code serviceRole} + system property {@value #SPIFFE_DIR_PROPERTY}.
     */
    private Path resolveSvidPem() throws LoginException {
        String explicit = optionString("svidPem", null);
        if (explicit != null) {
            return Paths.get(expandProperties(explicit));
        }
        return resolveRoleFile("svid.pem");
    }

    /**
     * Resolves the path to the SVID private key PEM file.
     * Priority: explicit {@code keyPem} option > derived from
     * {@code serviceRole} + system property {@value #SPIFFE_DIR_PROPERTY}.
     */
    private Path resolveKeyPem() throws LoginException {
        String explicit = optionString("keyPem", null);
        if (explicit != null) {
            return Paths.get(expandProperties(explicit));
        }
        return resolveRoleFile("svid_key.pem");
    }

    private Path resolveRoleFile(String filename) throws LoginException {
        String role = optionString("serviceRole", null);
        if (role == null || role.isEmpty()) {
            throw new LoginException(
                    "SpiffeLoginModule: 'serviceRole' option is required "
                    + "when 'svidPem'/'keyPem' are not specified");
        }
        String baseDir = System.getProperty(SPIFFE_DIR_PROPERTY);
        if (baseDir == null || baseDir.isEmpty()) {
            throw new LoginException(
                    "SpiffeLoginModule: system property '"
                    + SPIFFE_DIR_PROPERTY + "' must be set when using "
                    + "'serviceRole' option");
        }
        return Paths.get(baseDir, role, filename);
    }

    private String optionString(String key, String defaultValue) {
        Object val = options.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Expands {@code ${property.name}} references in {@code value} using
     * {@link System#getProperty}.  Unresolved references are left as-is.
     */
    private static String expandProperties(String value) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start < 0) {
                sb.append(value, i, value.length());
                break;
            }
            sb.append(value, i, start);
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                sb.append(value, start, value.length());
                break;
            }
            String propName = value.substring(start + 2, end);
            String propVal  = System.getProperty(propName);
            sb.append(propVal != null ? propVal
                                      : value.substring(start, end + 1));
            i = end + 1;
        }
        return sb.toString();
    }
}
