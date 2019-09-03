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

import java.net.Socket;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.AuthenticationPermission;
import org.apache.river.logging.Levels;

/**
 *
 * @author peter
 */
class ClientSubjectKeyManager extends SubjectKeyManager {
    /** Client logger */
    private static final Logger logger = CLIENT_LOGGER;
   
    
    /** Returns the client logger */
    Logger getLogger() {
	return logger;
    }
    
    ClientSubjectKeyManager(Subject subject) throws NoSuchAlgorithmException, NoSuchProviderException {
	super(subject);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
	List certPaths = getCertificateChains(getSubject());
	if (certPaths == null) {
	    return null;
	}
	Collection result = null;
	for (int i = certPaths.size(); --i >= 0;) {
	    CertPath chain = (CertPath) certPaths.get(i);
	    Exception exception;
	    try {
		if (checkChain(chain, keyType, issuers) != null) {
		    if (result == null) {
			result = new ArrayList(certPaths.size());
		    }
		    result.add(getCertificateName(firstX509Cert(chain)));
		}
		continue;
	    } catch (SecurityException e) {
		exception = e;
	    } catch (GeneralSecurityException ex) {
		exception = ex;
	    }
	    Logger logger = Logger.getLogger(SubjectKeyManager.class.getName());
	    if (logger.isLoggable(Levels.HANDLED)) {
		logger.log(Levels.HANDLED, "Swallowed SecurityException thrown", exception);
	    }
	}
	if (result == null) {
	    return null;
	} else {
	    return (String[]) result.toArray(new String[result.size()]);
	}
    }

    @Override
    public synchronized String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
	/*
	 * Only choose new client credentials for the first handshake.
	 * Otherwise, just use the previous client credentials.
	 */
	if (clientCredential == null) {
	    for (String keyType : keyTypes) {
		try {
		    if (exceptionMap.get(keyType) != null) {
			// Prior exception found for keytype
			return null;
		    }

		    clientCredential = chooseCredential(keyType, issuers);
		    if (clientCredential != null) {
                        // clientCredential found
			exceptionMap.put(keyType, null);
			break;
                        
                    } else {
			exceptionMap.put(keyType,
                            new GeneralSecurityException("Credentials not found"));
		    }
		    continue;
                    
		} catch (GeneralSecurityException e) {
		    exceptionMap.put(keyType, e);
		} catch (SecurityException e) {
		    exceptionMap.put(keyType, e);
		}
	    }
	    if (clientCredential == null) {
		return null;
	    }
	}
        
	X509Certificate cert = clientCredential.getCertificate();
	clientPrincipal = cert.getSubjectX500Principal();
	credentialsValidUntil = Math.min(credentialsValidUntil, certificatesValidUntil(getCertificateChain(getSubject(), cert)));
	authenticationPermission = getAuthenticationPermission(cert);
	String result = getCertificateName(clientCredential.getCertificate());
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(
			Level.FINE,
			"choose client alias for key types {0}\nand issuers {1}\n" +
			"returns {2}",
			new Object[] { toString(keyTypes), toString(issuers), result });
		}
	return result;
    }

    /* -- X500TrustManager -- */
    /**
     * Override this X509TrustManager method in order to cache the server
     * principal and to continue to choose the same one.
     */
    @Override
    public synchronized void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
	super.checkServerTrusted(chain, authType);
	if (serverPrincipal == null) {
	    serverCredential = chain[0];
	    serverPrincipal = serverCredential.getSubjectX500Principal();
	    setPermittedRemotePrincipals(Collections.singleton(serverPrincipal));
	    credentialsValidUntil = certificatesValidUntil(chain);
	} else if (!serverCredential.equals(chain[0])) {
	    throw new CertificateException("Server credentials changed");
	}
    }

    @Override
    public String[] getServerAliases(String arg0, Principal[] arg1) {
	return null;
    }

    @Override
    public String chooseServerAlias(String arg0, Principal[] arg1, Socket arg2) {
	return null;
    }

    /**
     * Returns the permission needed to connect to the last server principal
     * with the specified client certificate.
     */
    AuthenticationPermission getAuthenticationPermission(X509Certificate cert) {
	Set client = Collections.singleton(cert.getSubjectX500Principal());
	Set server = (serverPrincipal == null) ? null : Collections.singleton(serverPrincipal);
	return new AuthenticationPermission(client, server, "connect");
    }

    /**
     * Gets the private credential for the specified X.509 certificate,
     * checking for AuthenticationPermission to connect with the last server
     * principal.
     *
     * @param cert the certificate for the local principal
     * @return the associated private credential or null if not found
     * @throws SecurityException if the access control context does not have
     *	       the proper AuthenticationPermission
     */
    @Override
    synchronized X500PrivateCredential getPrivateCredential(X509Certificate cert) {
	return getPrivateCredential(cert, getAuthenticationPermission(cert));
    }

    /**
     * Gets the private credential for the specified X.509 certificate,
     * checking for the specified AuthenticationPermission.
     *
     * @param cert the certificate for the local principal
     * @param ap the permission needed to connect to the peer
     * @return the associated private credential or null if not found
     * @throws SecurityException if the access control context does not have
     *	       the proper AuthenticationPermission
     */
    protected X500PrivateCredential getPrivateCredential(X509Certificate cert, AuthenticationPermission ap) {
	Subject subject = getSubject();
	if (subject == null) {
	    return null;
	}
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(ap);
	}
	return (X500PrivateCredential) AccessController.doPrivileged(new GetPrivateCredentialAction(subject, cert));
    }
    
    
    /**
     * Checks if the subject still contains the proper credentials, and the
     * current access control context has the proper AuthenticationPermission,
     * to use the current session.  Callers should only call this method if
     * client authentication is being used.
     *
     * @throws SecurityException if the access control context does not have
     *	       the proper AuthenticationPermission
     * @throws UnsupportedConstraintException if the subject does not contain
     *	       the proper credentials
     */
    synchronized void checkAuthentication()
	throws GeneralSecurityException
    {
	if (clientCredential == null) {
	    throw new GeneralSecurityException(
		"Client is not authenticated");
	} else if (clientCredential.isDestroyed()) {
	    throw new GeneralSecurityException(
		"Private credentials are destroyed");
	} else if (System.currentTimeMillis() > credentialsValidUntil) {
	    throw new GeneralSecurityException(
		"Certificates are no longer valid");
	}
	if (subjectIsReadOnly) {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(authenticationPermission);
	    }
	} else {
	    Subject subject = getSubject();
	    X509Certificate cert = clientCredential.getCertificate();
	    if (getPrincipal(subject, cert) == null) {
		throw new GeneralSecurityException("Missing principal");
	    }
	    CertPath chain =
		getCertificateChain(subject, cert);
	    if (chain == null) {
		throw new GeneralSecurityException(
		    "Missing public credentials");
	    }
	    X500PrivateCredential pc = getPrivateCredential(
		cert, authenticationPermission);
	    if (pc == null) {
		throw new GeneralSecurityException(
		    "Missing private credentials");
	    } else if (!equalPrivateCredentials(clientCredential, pc)) {
		throw new GeneralSecurityException(
		    "Wrong private credentials");
	    }
	}
    }
}
