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

package com.sun.jini.test.impl.end2end.e2etest;

import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.kerberos.KerberosServerEndpoint;
import javax.net.ssl.SSLContext;
import java.security.AccessController;
import javax.net.ssl.SSLServerSocketFactory;
import javax.security.auth.Subject;
import com.sun.jini.test.impl.end2end.jssewrapper.ServerEndpointWrapper;

/**
 * A class which manages the Security Providers and wrappers used for the
 * test. This class is also used to obtain the endpoints for exporters,
 * allowing the export to be restricted to the providers explicitly
 * desired by the test.
 */
class ProviderManager implements Constants {

    /** if true, inhibit installation of the provider wrapper */
    private static boolean inhibitWrapper;

    /** if true, the JSSE provider is used */
    private static boolean doJSSE;

    /** if true, the HTTPS provider is used */
    private static boolean doHTTPS;

    /** if true, the Kerberos provider is used */
    private static boolean doKerberos;

    /** flag for strong encryption supported */
    private static boolean strong;

    /** contains the subject provider to be used */
    private static SubjectProvider subjectProvider;

    /*
     * initialize inhibitWrapper and install providers:
     *
     * read the system property. Print a warning message if the wrapper
     * is to be inhibited. Since inhibiting the wrapper is only expected
     * to be done when debugging a suspected wrapper related bug, printing
     * to <code>System.out</code> seems more appropriate than logging
     * the warning. Install providers based on the property value.
     */
    public static void initialize() throws Exception {
        inhibitWrapper = System.getProperty("end2end.inhibitWrapper") != null;
        if (inhibitWrapper) {
            System.out.println("WARNING - Provider wrapper is disabled");
        }
        doHTTPS = System.getProperty("end2end.https") != null;
        doJSSE = System.getProperty("end2end.jsse") != null;
        doKerberos = System.getProperty("end2end.kerberos") != null;
        if (!doKerberos) {
            installProviders();
        }
        if (doJSSE && !doHTTPS && !doKerberos){
            subjectProvider = new JSSESubjectProvider();
            System.out.println("Using the JSSE provider");
        } else if (doHTTPS && !doJSSE & !doKerberos) {
            subjectProvider = new JSSESubjectProvider();
            System.out.println("Using the HTTPS provider");
        } else if (doKerberos && !doJSSE && !doHTTPS) {
            KerberosSubjectProvider.initialize();
            subjectProvider = new KerberosSubjectProvider();
            System.out.println("Using the Kerberos provider");
        } else {
            subjectProvider = new JSSESubjectProvider();
            System.out.println("No provider specified, the system will " +
                "default to the JSSE provider");
        }
    }

    public static boolean isKerberosProvider() {
        return doKerberos;
    }

    /**
     * determine whether the Provider wrapper is installed
     *
     * @return <code>true</code> if the wrapper is installed
     */
    static boolean isWrapped() {
        return !inhibitWrapper;
    }

    /**
     * Determine the encryption strength of the JSSE security provider.
     * The JSSE provider is distribued in two forms, one supporting
     * only weak encryption, and one supporting both strong and weak.
     * This method returns the strength supported by the underlying
     * provider.
     *
     * @return <code>true</code> if the provider supports strong encryption
     */
    static boolean isStrong() {
        return strong;
    }

    /**
     * Return a <code>ServerEndpoint</code> object for
     * the providers being used in this test run.
     *
     * @return the server endpoint
     */
    static ServerEndpoint getEndpoint()
                                  throws UnsupportedConstraintException
    {
        try {
            ServerEndpoint endpoint = null;
            if (doKerberos) {
                endpoint = KerberosServerEndpoint.getInstance(
                    subjectProvider.getServerSubject(),null,null,0,null,null);
            } else if (doHTTPS) {
                endpoint = HttpsServerEndpoint.getInstance(subjectProvider
                    .getServerSubject(), null, null, 0);
            } else {
                endpoint = SslServerEndpoint.getInstance(subjectProvider
                    .getServerSubject(), null, null, 0);
            }
            if (!inhibitWrapper) {
                endpoint = new ServerEndpointWrapper(endpoint);
            }
            return endpoint;
        } catch (Exception e) {
            e.printStackTrace();
            throw new TestException("Failed to instantiate factory", e);
        }
    }

    /**
     *
     * This method determines the strength supported by the underlying
     * provider, and also examines the value of the optional property
     * <code>end2end.strongSupported</code> which can be used to verify
     * that the strength of the provider matches expectations. A warning
     * is printed to the log if expectations are not met.
     */
    static void installProviders() {
        SSLContext c;
        try {
            c = SSLContext.getInstance("TLS", "SunJSSE");
            c.init(null,null,null);
        } catch (Exception e) {
            throw new TestException("Failed to get suites", e);
        }
        SSLServerSocketFactory f = c.getServerSocketFactory();
        String[] suites = f.getSupportedCipherSuites();
        strong = false;
        for (int i = suites.length ; --i >= 0; ) {
            if (suites[i].equals("SSL_RSA_WITH_RC4_128_SHA")) {
                strong = true;
                break;
            }
        }
        String supported = System.getProperty("end2end.strongSupported");
        Logger logger = new Logger();
        if ("true".equals(supported) && !strong) {
            logger.log(ALWAYS, "Warning: the end2end.strongSupported "
                + "property is 'true', but the SSL "
                + "provider does not support strong encryption");
        }
        if ("false".equals(supported) && strong) {
            logger.log(ALWAYS, "Warning: the end2end.strongSupported "
                + "property is 'false', but the SSL "
                + "provider does support strong encryption");
        }
        logger.writeLog();
    }

    static SubjectProvider getSubjectProvider() {
        return subjectProvider;
    }
}
