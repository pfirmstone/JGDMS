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

import java.net.Socket;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.AuthenticationPermission;

/**
 * An AuthManager for clients.  Uses the fact that client connections only
 * share SSLContexts after a single client and server principal have been
 * chosen.
 *
 * 
 */
class ClientAuthManager extends AuthManager {

    /* -- Fields -- */

    /** Client logger */
    private static final Logger logger = CLIENT_LOGGER;

    /** The server certificate chosen by the first handshake. */
    private X509Certificate serverCredential;

    /** The server principal chosen by the first handshake. */
    private X500Principal serverPrincipal;

    /**
     * The private credential supplied by chooseClientAlias in the last
     * handshake or null if none was supplied.
     */
    private X500PrivateCredential clientCredential;

    /** The client principal chosen by the first handshake. */
    private X500Principal clientPrincipal;

    /**
     * The exception that occurred within the last call to chooseClientAlias if
     * no credential could be supplied.
     */
    private Exception clientCredentialException;

    /**
     * The latest time for which all client and server credentials remain
     * valid.
     */
    private long credentialsValidUntil = 0;

    /** The permission to check for the last cached credential */
    private AuthenticationPermission authenticationPermission;

    /* -- Constructors -- */

    /**
     * Creates an AuthManager that retrieves principals and credentials for
     * authentication from the specified subject.  If permittedLocalPrincipals
     * is non-null, then the principals used for authentication are restricted
     * to the elements of that set.  If permittedRemotePrincipals is non-null,
     * then the server principals accepted are restricted to the elements of
     * that set.
     *
     * @param subject the subject for retrieving principals and credentials
     * @param permittedLocalPrincipals if non-null, then only principals in
     *	      this set may be used for authentication
     * @param permittedRemotePrincipals if non-null, then only principals in
     *	      this set will be trusted when authenticating the peer
     * @throws NoSuchAlgorithmException if the trust manager factory algorithm
     *	       is not found
     */
    ClientAuthManager(Subject subject,
		      Set permittedLocalPrincipals,
		      Set permittedRemotePrincipals)
	throws NoSuchAlgorithmException
    {
	super(subject, permittedLocalPrincipals, permittedRemotePrincipals);
    }

    /* -- Methods -- */

    /**
     * Returns true if the last handshake authenticated the client, else
     * false.
     */
    synchronized boolean getClientAuthenticated() {
	return clientCredential != null;
    }

    /**
     * Returns the last SecurityException or GeneralSecurityException that
     * occurred when attempting to choose client credentials, or null if no
     * exception occurred.
     */
    synchronized Exception getClientCredentialException() {
	return clientCredentialException;
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
	throws UnsupportedConstraintException
    {
	if (clientCredential == null) {
	    throw new UnsupportedConstraintException(
		"Client is not authenticated");
	} else if (clientCredential.isDestroyed()) {
	    throw new UnsupportedConstraintException(
		"Private credentials are destroyed");
	} else if (System.currentTimeMillis() > credentialsValidUntil) {
	    throw new UnsupportedConstraintException(
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
	    if (SubjectCredentials.getPrincipal(subject, cert) == null) {
		throw new UnsupportedConstraintException("Missing principal");
	    }
	    CertPath chain =
		SubjectCredentials.getCertificateChain(subject, cert);
	    if (chain == null) {
		throw new UnsupportedConstraintException(
		    "Missing public credentials");
	    }
	    X500PrivateCredential pc = getPrivateCredential(
		cert, authenticationPermission);
	    if (pc == null) {
		throw new UnsupportedConstraintException(
		    "Missing private credentials");
	    } else if (!equalPrivateCredentials(clientCredential, pc)) {
		throw new UnsupportedConstraintException(
		    "Wrong private credentials");
	    }
	}
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
    synchronized X500PrivateCredential getPrivateCredential(
	X509Certificate cert)
    {
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
    private X500PrivateCredential getPrivateCredential(
	X509Certificate cert, AuthenticationPermission ap)
    {
	Subject subject = getSubject();
	if (subject == null) {
	    return null;
	}
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(ap);
	}
	return (X500PrivateCredential) AccessController.doPrivileged(
	    new SubjectCredentials.GetPrivateCredentialAction(
		subject, cert));
    }

    /** Returns the client logger */
    Logger getLogger() {
	return logger;
    }

    /**
     * Returns the permission needed to connect to the last server principal
     * with the specified client certificate.
     */
    private AuthenticationPermission getAuthenticationPermission(
	X509Certificate cert)
    {
	Set client = Collections.singleton(cert.getSubjectX500Principal());
	Set server = (serverPrincipal == null)
	    ? null : Collections.singleton(serverPrincipal);
	return new AuthenticationPermission(client, server, "connect");
    }

    /** Returns the server principal chosen. */
    synchronized X500Principal getServerPrincipal() {
	return serverPrincipal;
    }

    /** Returns the client principal chosen. */
    synchronized X500Principal getClientPrincipal() {
	return clientPrincipal;
    }

    /* -- X500TrustManager -- */

    /**
     * Override this X509TrustManager method in order to cache the server
     * principal and to continue to choose the same one.
     */
    public synchronized void checkServerTrusted(X509Certificate[] chain,
						String authType)
	throws CertificateException
    {
	super.checkServerTrusted(chain, authType);
	if (serverPrincipal == null) {
	    serverCredential = chain[0];
	    serverPrincipal = serverCredential.getSubjectX500Principal();
	    setPermittedRemotePrincipals(
		Collections.singleton(serverPrincipal));
	    credentialsValidUntil = certificatesValidUntil(chain);
	} else if (!serverCredential.equals(chain[0])) {
	    throw new CertificateException("Server credentials changed");
	}
    }

    /* -- Implement X509KeyManager -- */

    public String[] getClientAliases(String keyType, Principal[] issuers) {
	String[] result = getAliases(keyType, issuers);
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "get client aliases for key type {0}\n" +
		       "and issuers {1}\nreturns {2}",
		       new Object[] {
			   keyType, toString(issuers), toString(result)
		       });
	}
	return result;
    }
    
    public String[] getServerAliases(String keyType, Principal[] issuers) {
	return null;
    }

    public synchronized String chooseClientAlias(
	String[] keyTypes, Principal[] issuers, Socket socket)
    {
	/*
	 * Only choose new client credentials for the first handshake.
	 * Otherwise, just use the previous client credentials.
	 */
	if (clientCredentialException != null) {
	    return null;
	} else if (clientCredential == null) {
	    List exceptions = null;
	    for (int i = 0; i < keyTypes.length; i++) {
		Exception exception;
		try {
		    clientCredential = chooseCredential(keyTypes[i], issuers);
		    if (clientCredential != null) {
			break;
		    }
		    continue;
		} catch (GeneralSecurityException e) {
		    exception = e;
		} catch (SecurityException e) {
		    exception = e;
		}
		if (exceptions == null) {
		    exceptions = new ArrayList();
		}
		exceptions.add(exception);
	    }
	    if (clientCredential == null) {
		if (exceptions == null) {
		    clientCredentialException =
			new GeneralSecurityException("Credentials not found");
		} else if (exceptions.size() == 1) {
		    clientCredentialException = (Exception) exceptions.get(0);
		} else {
		    for (int i = exceptions.size(); --i >= 0; ) {
			Exception e = (Exception) exceptions.get(i);
			if (!(e instanceof SecurityException)) {
			    clientCredentialException =
				new GeneralSecurityException(
				    exceptions.toString());
			    break;
			}
		    }
		    if (clientCredentialException == null) {
			clientCredentialException = 
			    new SecurityException(exceptions.toString());
		    }
		}
		return null;
	    }
	}
	X509Certificate cert = clientCredential.getCertificate();
	clientPrincipal = cert.getSubjectX500Principal();
	credentialsValidUntil =
	    Math.min(credentialsValidUntil,
		     certificatesValidUntil(
			 SubjectCredentials.getCertificateChain(
			     getSubject(), cert)));
	authenticationPermission = getAuthenticationPermission(cert);
	String result = SubjectCredentials.getCertificateName(
	    clientCredential.getCertificate());
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE,
		"choose client alias for key types {0}\nand issuers {1}\n" +
		"returns {2}",
		new Object[] { toString(keyTypes), toString(issuers), result });
	}
	return result;
    }

    public String chooseServerAlias(
	String keyType, Principal[] issuers, Socket socket)
    {
	return null;
    }
}
