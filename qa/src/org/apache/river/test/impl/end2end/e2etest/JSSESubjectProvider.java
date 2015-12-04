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

package org.apache.river.test.impl.end2end.e2etest;

/* JAAS imports */
import javax.security.auth.x500.X500PrivateCredential;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.Subject;

/* Java imports */
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;

import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Provides common utilities and constants.
 */
class JSSESubjectProvider implements SubjectProvider {

    /* Credentials */

    private static KeyStore keyStore;
    private static CertificateFactory certFactory;

    private static char[] keyStorePassword = "keypass".toCharArray();

    /* client principal names */
    private static final String clientDSA1 = "CN=clientDSA1";
    private static final String clientDSA2 = "CN=clientDSA2";
    private static final String clientRSA1 = "CN=clientRSA1";
    private static final String clientRSA2 = "CN=clientRSA2";

    /** a read-only Subject which contains client principals */
    private static final Subject clientSubject = new WithSubject() {
        {
        addX500Principal("clientDSA1", subject);
        addX500Principal("clientDSA2", subject);
        addX500Principal("clientRSA1", subject);
        addX500Principal("clientRSA2", subject);
        }
    }.subject();

    /* server principal names */
    private static final String serverDSA = "CN=serverDSA";
    private static final String serverRSA = "CN=serverRSA";

    /** a read-only Subject which contains server principals */
    private static final Subject serverSubject = new WithSubject() {
        {
        addX500Principal("serverDSA", subject);
        addX500Principal("serverRSA", subject);
        }
    }.subject();

    /** the ClientMinPrincipal for the CMinP method name token */
    private static final ClientMinPrincipal cminp =
        new ClientMinPrincipal(new X500Principal(clientRSA1));

    /** the ClientMinPrincipalType for the CMinTp method name token */
    private static final ClientMinPrincipalType cmintp =
        new ClientMinPrincipalType(X500Principal.class);

    /** the ClientMaxPrincipal for the client preferences */
    private static final ClientMaxPrincipal cmaxp = new ClientMaxPrincipal(
        new Principal[] {
            new X500Principal(clientDSA1),
            new X500Principal(clientRSA1)
        });

    /** the ConstraintAlternatives for the Alt1 method name token */
    private static final ConstraintAlternatives alt1 =
        new ConstraintAlternatives( new InvocationConstraint[]{
            new ClientMinPrincipal(new X500Principal(clientDSA1)),
        new ClientMinPrincipal(new X500Principal(clientRSA1))
        });

    /** the ConstraintAlternatives for the Alt2 method name token */
    private static final ConstraintAlternatives alt2 =
        new ConstraintAlternatives( new InvocationConstraint[] {
            new ClientMinPrincipal(new X500Principal(clientDSA2)),
        new ClientMinPrincipal(new X500Principal(clientRSA2))
        });

    /** the ConstraintAlternatives for ServerMinPrincipal */
    private static final ConstraintAlternatives sminp =
        new ConstraintAlternatives(new InvocationConstraint[]{
            new ServerMinPrincipal(new X500Principal(serverDSA)),
        new ServerMinPrincipal(new X500Principal(serverRSA))
        });

    public Subject getClientSubject() {
        return clientSubject;
    }

    public Subject getServerSubject() {
        return serverSubject;
    }

    public ClientMinPrincipal getClientMinPrincipal() {
        return cminp;
    }

    public ClientMinPrincipalType getClientMinPrincipalType() {
        return cmintp;
    }

    public ClientMaxPrincipal getClientMaxPrincipal() {
        return cmaxp;
    }

    public ConstraintAlternatives getConstraintAlternatives1() {
        return alt1;
    }

    public ConstraintAlternatives getConstraintAlternatives2() {
        return alt2;
    }

    public ConstraintAlternatives getServerMinPrincipal() {
        return sminp;
    }

    public ServerMinPrincipal getServerMainPrincipal() {
        return new ServerMinPrincipal(new X500Principal(serverDSA));
    }

    /**
     * A helper method which obtains the Subject of the current thread
     *
     * @return the subject associated with the <code>AccessControlContext</code>
     *         of the current thread
     */
    public Subject getSubject() {
        return Subject.getSubject(AccessController.getContext());
    }

    /**
     * Return a keystore object initialized from the file whose name
     * is obtained from the <code>keyStoreFile</code> property.
     * The keystore is cached. The method is synchronized since
     * there is no appropriate object upon which to synchronize.
     * Synchronization in this case is not really necessary, since
     * this method is only called by the thread performing class
     * initialization.
     *
     * @return the generated keystore
     */
    synchronized private static KeyStore getKeyStore() {
        if (keyStore == null) {
        String filename = System.getProperty("keyStoreFile");
        if (filename == null) {
                return null;
            }
        try {
                InputStream in =
            new BufferedInputStream(new FileInputStream(filename));
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(in, null);
            in.close();
        } catch (IOException e) {
                throw new RuntimeException(e.toString());
        } catch (KeyStoreException e) {
        throw new RuntimeException(e.toString());
        } catch (GeneralSecurityException e) {
        throw new RuntimeException(e.toString());
        }
        }
    return keyStore;
    }

    /**
     * Add an X500 principal to a subject. The given alias is used
     * to look up a certificate chain in the keystore. The first certificate
     * in the chain is used as a source for the principal, public
     * credentials, and private keys which are added to the given subject.
     *
     * @param alias the alias of the principal in the keystore
     * @param subject the subject to which the principal is to be added
     *
     * @throws KeyStroreException for any key store exceptions
     * @throws GenericSecurityException for any security exceptions
     */
    private static void addX500Principal(String alias, Subject subject) {
        addX500Principal(alias, subject, true);
    }

    /**
     * Get the certificate chain for the given <code>alias</code>
     *
     * @param alias the alias to search for
     * @return the <code>CertPath</code> bound to <code>alias</code>
     */
    private static CertPath getCertificateChain(String alias) {
        try {
        Certificate[] chain = getKeyStore().getCertificateChain(alias);
        if (chain == null) {
                throw new RuntimeException(
                    "Certificate chain not found for alias " + alias);
        }
        return getCertFactory().generateCertPath(Arrays.asList(chain));
    } catch (KeyStoreException e) {
        throw new RuntimeException(e.toString());
    } catch (GeneralSecurityException e) {
        throw new RuntimeException(e.toString());
    }
    }

    private static CertificateFactory getCertFactory() {
        if (certFactory == null) {
        try {
                certFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
                throw new RuntimeException(e.toString());
        }
    }
    return certFactory;
    }

    /**
     * Add an X500 principal to a subject. The given alias is used
     * to look up a certificate chain in the keystore. The first certificate
     * in the chain is used as a source for the principal, public
     * credentials, and optionally private keys which are added to
     * the given subject.
     *
     * @param alias the alias of the principal in the keystore
     * @param subject the subject to which the principal is to be added
     * @param includePrivateKey if <code>true</code> private keys are provided
     *                          to the subject.
     *
     * @throws KeyStroreException for any key store exceptions
     * @throws GenericSecurityException for any security exceptions
     */
    private static void addX500Principal(String alias,
                 Subject subject,
                 boolean includePrivateKey)
    {
        try {
        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
                return;
        }
        CertPath certificateChain = getCertificateChain(alias);
        subject.getPublicCredentials().add(certificateChain);
        X509Certificate certificate =
                (X509Certificate) certificateChain.getCertificates().get(0);
        subject.getPrincipals().add(
                new X500Principal(certificate.getSubjectDN().getName()));
        if (includePrivateKey) {
                PublicKey publicKey = certificate.getPublicKey();
        PrivateKey privateKey =
            (PrivateKey) keyStore.getKey(alias, keyStorePassword);
        subject.getPrivateCredentials().add(
            new X500PrivateCredential(certificate, privateKey));
        }
    } catch (KeyStoreException e) {
        throw new RuntimeException(e.toString());
    } catch (GeneralSecurityException e) {
        throw new RuntimeException(e.toString());
    }
    }

    /* utility class to support building read-only subjects */
    private static class WithSubject {
        protected Subject subject = new Subject();
    public Subject subject() {
        subject.setReadOnly();
        return subject;
    }
    }
}
