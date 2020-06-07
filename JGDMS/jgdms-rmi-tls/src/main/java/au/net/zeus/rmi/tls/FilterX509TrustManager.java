/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.rmi.tls;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivilegedAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import net.jini.security.Security;
import org.apache.river.action.GetPropertyAction;

/**
 *
 * 
 */
abstract class FilterX509TrustManager extends Utilities implements X509TrustManager {
    
    /* -- Fields -- */
    /** The trust manager to delegate to. */
    private static volatile X509TrustManager trustManager;

    /** Use for synchronizing initialization of the trustManager field. */
    private static final Object lock = new Object();

    /** The trust manager factory algorithm. */
    private static final String trustManagerFactoryAlgorithm =
	Security.doPrivileged(
	    new GetPropertyAction(
		"org.apache.river.jeri.ssl.trustManagerFactoryAlgorithm",
		TrustManagerFactory.getDefaultAlgorithm()
	    )
	);
    
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
    FilterX509TrustManager() throws NoSuchAlgorithmException, NoSuchProviderException {
	synchronized (lock) {
	    if (trustManager == null) {
		trustManager = getTrustManager();
	    }
	}
//	setPermittedRemotePrincipals(principals);
    }

    /* -- Implement X509TrustManager -- */

    public void checkClientTrusted(X509Certificate[] chain, String authType)
	throws CertificateException
    {
	trustManager.checkClientTrusted(chain, authType);
	check(chain);
	if (SERVER_LOGGER.isLoggable(Level.FINE)) {
	    SERVER_LOGGER.log(Level.FINE,
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
	if (CLIENT_LOGGER.isLoggable(Level.FINE)) {
	    CLIENT_LOGGER.log(Level.FINE,
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
    synchronized void setPermittedRemotePrincipals(Set principals) {
	this.principals = (principals == null) ? null : new HashSet(principals);
    }	

    /**
     * Make sure the subject of the leaf certificate is one of the permitted
     * principals.
     */
    private synchronized void check(X509Certificate[] chain) throws CertificateException {
	if (principals != null &&
	    !principals.contains(chain[0].getSubjectX500Principal()))
	{
	    throw new CertificateException("Remote principal is not trusted");
	}
    }

    /** Returns the X509TrustManager to delegate to. */
    private static X509TrustManager getTrustManager()
	throws NoSuchAlgorithmException, NoSuchProviderException
    {
	final TrustManagerFactory factory = JSSE_PROVIDER != null ?
		TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm, JSSE_PROVIDER)
		: TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm);
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
                        
                        KeyStore keyStore =
                        KeyStore.getInstance(
                            System.getProperty(
                                "javax.net.ssl.trustStoreType",
                                KeyStore.getDefaultType()
                            )
                        );
                        InputStream in;
                        try {
                            in = new FileInputStream(
                                    System.getProperty("javax.net.ssl.trustStore"));
                            String keyStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
                            char[] password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];
                            keyStore.load(in, password);
                            in.close();
                        } catch (FileNotFoundException ex) {
                            Logger.getLogger(FilterX509TrustManager.class.getName()).log(Level.CONFIG, "Unable to find trustStore", ex);
                        } catch (IOException ex) {
                            Logger.getLogger(FilterX509TrustManager.class.getName()).log(Level.CONFIG, "Unable to load trustStore", ex);
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(FilterX509TrustManager.class.getName()).log(Level.CONFIG, "Unable to load trustStore", ex);
                        } catch (CertificateException ex) {
                            Logger.getLogger(FilterX509TrustManager.class.getName()).log(Level.CONFIG, "Unable to load trustStore", ex);
                        }
			factory.init(keyStore);
		    } catch (KeyStoreException e) {
			INIT_LOGGER.log(
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
