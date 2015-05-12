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

import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Provides common utilities and constants.
 */
class CipherSuite {

    /** a map for binding protocol strings to protocol attribute flags */
    private static HashMap pMap;

    /** flags for a CipherSuite instance */
    private ProtocolFlags flags;

    static {

        /* initialize the suites map */
    pMap = new HashMap(15);
    String s;
    s = "SSL_DH_anon_WITH_DES_CBC_SHA";
        pMap.put(s, new ProtocolFlags(s, false, true, true, true));
    s = "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, false, true, true, false));
    s = "SSL_DHE_DSS_WITH_DES_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, true));
    s = "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, false));
    s = "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, false, true, true, true));
    s = "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, true));
    s = "SSL_RSA_WITH_RC4_128_MD5";
    pMap.put(s, new ProtocolFlags(s, true, true, true, false));
    s = "SSL_RSA_WITH_RC4_128_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, false));
    s = "SSL_RSA_WITH_DES_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, true));
    s = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";
    pMap.put(s, new ProtocolFlags(s, true, true, true, false));
    s = "SSL_DH_anon_WITH_RC4_128_MD5";
    pMap.put(s, new ProtocolFlags(s, false, true, true, false));
    s = "SSL_RSA_EXPORT_WITH_RC4_40_MD5";
    pMap.put(s, new ProtocolFlags(s, true, true, true, true));
    s = "SSL_RSA_WITH_NULL_MD5";
    pMap.put(s, new ProtocolFlags(s, true, false, true, true));
    s = "SSL_RSA_WITH_NULL_SHA";
    pMap.put(s, new ProtocolFlags(s, true, false, true, true));
    s = "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5";
    pMap.put(s, new ProtocolFlags(s, false, true, true, true));
    }

    /** a little inner class to simplify building the suites map */
    private static class ProtocolFlags {
    String name;
    boolean isAuthenticated;
    boolean isConfidential;
    boolean isIntegrity;
    boolean isWeak;

    ProtocolFlags(String name,
              boolean auth,
              boolean conf,
              boolean integ,
              boolean weak)
    {
        this.name = name;
        this.isAuthenticated = auth;
        this.isConfidential = conf;
        this.isIntegrity = integ;
        this.isWeak = weak;
    }
    }

    /**
     * Private constructor for CipherSuite factory
     */
    private CipherSuite(ProtocolFlags flags) {
        this.flags = flags;
    }

    /**
     * Returns the protocol flags associated with a ciphersuite
     *
     * @param protocolString the suite descriptor string
     * @return the flags bound the the suite
     */
    private static ProtocolFlags getFlags(String protocolString) {
    ProtocolFlags flags = (ProtocolFlags) pMap.get(protocolString);
    if (flags == null) {
        throw new IllegalArgumentException("Invalid Protocol Name: "
                          + protocolString);
    }
    return flags;
    }

    static CipherSuite getSuite(String suiteName) {
        return new CipherSuite(getFlags(suiteName));
    }

    /**
     * Test whether a protocol suite provides confidentiality
     *
     * @return <code>true</code> if the suite provides confidentiality
     */
    boolean isConfidential() {
    return flags.isConfidential;
    }

    /**
     * Test whether a protocol suite provides authentication
     *
     * @return <code>true</code> if the suite provides authentication
     */
    boolean isAuthenticated() {
    return flags.isAuthenticated;
    }

    /**
     * Test whether a protocol suite provides integity
     *
     * @return <code>true</code> if the suite provides integity
     */
    boolean isIntegrity() {
    return flags.isIntegrity;
    }

    /**
     * Test whether a protocol suite provides weak encryption
     *
     * @return <code>true</code> if the suite provides weak encryption
     */
    boolean isWeak() {
    return flags.isWeak;
    }

    /**
     * Test whether a protocol suite provides strong encryption
     *
     * @return <code>true</code> if the suite provides strong encryption
     */
    boolean isStrong() {
    return !flags.isWeak;
    }

    /**
     * The toString method returns the name of the CipherSuite
     *
     * @return the name of the suite
     */
    public String toString() {
        return flags.name;
    }
}
