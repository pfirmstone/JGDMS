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

import org.apache.river.action.GetPropertyAction;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import net.jini.security.Security;

/**
 * Implements an X509TrustManager that only trusts certificate chains whose
 * first certificate identifies one of a set of principals.
 *
 * 
 */
class FilterX509TrustManager extends Utilities implements X509TrustManager {

    /* -- Fields -- */

    /** The trust manager to delegate to. */
    private static X509TrustManager trustManager;

    /** Use for synchronizing initialization of the trustManager field. */
    private static final Object lock = new Object();

    /** The trust manager factory algorithm. */
    private static final String trustManagerFactoryAlgorithm =
	(String) Security.doPrivileged(
	    new GetPropertyAction(
		"org.apache.river.jeri.ssl.trustManagerFactoryAlgorithm",
		TrustManagerFactory.getDefaultAlgorithm()));

    /** The set of permitted remote principals, or null if no restriction. */
    private Set principals;

    /* -- Constructors -- */

    /**
     * Creates an X509TrustManager that only trusts certificate chains whose
     * first certificate identifies one of a set of principals.
     *
     * @param principals the set of permitted remote principals, or null if no
     *	      restriction
     * @throws NoSuchAlgorithmException if the trust manager factory algorithm
     *	       is not found
     */
    FilterX509TrustManager(Set principals) throws NoSuchAlgorithmException {
	synchronized (lock) {
	    if (trustManager == null) {
		trustManager = getTrustManager();
	    }
	}
	setPermittedRemotePrincipals(principals);
    }

    /* -- Implement X509TrustManager -- */

    public void checkClientTrusted(X509Certificate[] chain, String authType)
	throws CertificateException
    {
	trustManager.checkClientTrusted(chain, authType);
	check(chain);
	if (serverLogger.isLoggable(Level.FINE)) {
	    serverLogger.log(Level.FINE,
			     "check client trusted succeeds " +
			     "for auth type {0}\nchain {1}",
			     new Object[] { authType, toString(chain) });
	}
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType)
	throws CertificateException
    {
	trustManager.checkServerTrusted(chain, authType);
	check(chain);
	if (clientLogger.isLoggable(Level.FINE)) {
	    clientLogger.log(Level.FINE,
			     "check server trusted succeeds " +
			     "for auth type {0}\nchain {1}",
			     new Object[] { authType, toString(chain) });
	}
    }

    public X509Certificate[] getAcceptedIssuers() {
	return trustManager.getAcceptedIssuers();
    }

    /* -- Other methods -- */

    /**
     * Specifies the set of permitted remote principals.
     *
     * @param principals the set of permitted remote principals, or null if no
     *	      restriction
     */
    void setPermittedRemotePrincipals(Set principals) {
	this.principals = (principals == null) ? null : new HashSet(principals);
    }	

    /**
     * Make sure the subject of the leaf certificate is one of the permitted
     * principals.
     */
    private void check(X509Certificate[] chain) throws CertificateException {
	if (principals != null &&
	    !principals.contains(chain[0].getSubjectX500Principal()))
	{
	    throw new CertificateException("Remote principal is not trusted");
	}
    }

    /** Returns the X509TrustManager to delegate to. */
    private static X509TrustManager getTrustManager()
	throws NoSuchAlgorithmException
    {
	final TrustManagerFactory factory =
	    TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm);
	Security.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    /*
		     * Initialize the trust managers for the trust manager
		     * factory.  Call in a doPrivileged because access to the
		     * CA certificates file should be allowed to all programs
		     * that use this provider.
		     */
		    try {
			/*
			 * Calling init with null reads the default truststore
			 */
			factory.init((KeyStore) null);
		    } catch (KeyStoreException e) {
			initLogger.log(
			    Level.WARNING,
			    "Problem initializing JSSE trust manager keystore",
			    e);
		    }
		    return null;
		}
	    });

	/*
	 * Although JSSE doesn't document this, there should be only one X.509
	 * trust manager for X.509 certificates read from KeyStores.
	 */
	return (X509TrustManager) factory.getTrustManagers()[0];
    }
}
