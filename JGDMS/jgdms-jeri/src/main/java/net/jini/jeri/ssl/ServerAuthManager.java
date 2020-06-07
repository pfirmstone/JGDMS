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

import org.apache.river.logging.Levels;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.Socket;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.security.AuthenticationPermission;

/**
 * An AuthManager for servers.  Invalidates sessions when a new key is returned
 * of a particular key type.
 *
 * 
 */
class ServerAuthManager extends AuthManager {

    /* -- Fields -- */

    /** Server transport logger */
    private static final Logger LOGGER = Utilities.SERVER_LOGGER;

    /** The SSLSessionContext for all connections. Null if stateless TSL*/
    private final SSLSessionContext sslSessionContext;

    /** The subject's private credentials, if the subject is read-only. */
    private final X500PrivateCredential[] readOnlyPrivateCredentials;

    /**
     * Maps a key type to last private credentials returned for that key type,
     * or a String describing problems that prevented getting private
     * credentials.
     */
    private final Map credentialCache = new HashMap(2);

    /* -- Constructors -- */

    /**
     * Creates an AuthManager that retrieves principals and credentials for
     * authentication from the specified subject.
     *
     * @param subject the subject for retrieving principals and credentials
     * @throws NoSuchAlgorithmException if the trust manager factory algorithm
     *	       is not found
     */
    ServerAuthManager(Subject subject,
		      Set permittedPrincipals,
		      SSLSessionContext sslSessionContext)
	throws NoSuchAlgorithmException, NoSuchProviderException
    {
	super(subject, permittedPrincipals, null);
	this.sslSessionContext = sslSessionContext;
	readOnlyPrivateCredentials =
	    !subjectIsReadOnly || subject == null ? null
	    : (X500PrivateCredential[]) AccessController.doPrivileged(
		new SubjectCredentials.GetAllPrivateCredentialsAction(
		    subject));
    }

    /* -- Methods -- */

    /**
     * Returns the principal that the server used to authenticate for the
     * specified session.  Returns null if the session is not found or if the
     * server did not authenticate itself.
     */
    X509Certificate getServerCertificate(SSLSession session) {
        if (session.isValid()){// Stateless
            Certificate [] certs = session.getLocalCertificates();
            if (certs[0] instanceof X509Certificate) {
                return (X509Certificate) certs[0];
            }
        } 
        return null;
    }
        
    /**
     * Checks if the server subject still contains the proper credentials to
     * use the specified session.  Uses the credential cache to find the
     * credentials for sessions with this session's key type.  Callers should
     * only call this method if server authentication is being used.
     *
     * @param session the session to check
     * @param clientSubject the client subject for the connection, which should
     *	      be read-only if it is not null
     * @throws GeneralSecurityException if there is a problem with the
     *	       credentials
     * @throws SecurityException if the current access control context does not
     *	       have the proper AuthenticationPermission or if the subject does
     *	       not contain the proper credentials
     */
    void checkCredentials(SSLSession session, Subject clientSubject)
	throws GeneralSecurityException
    {
        if (!session.isValid()) {
		throw new SecurityException("Session not valid");
        }
        
        Subject serverSubject = getSubject();
        Principal pal = session.getLocalPrincipal();

        if (pal instanceof X500Principal){
            X500Principal x500pal = (X500Principal) pal;
            if (x500pal.implies(serverSubject)) {
                Certificate[] cert = session.getLocalCertificates();
                if (cert[0] instanceof X509Certificate){
                    X500PrivateCredential cred = 
                            getPrivateCredential((X509Certificate) cert[0]);
                    if (cred.isDestroyed()){
                        throw new SecurityException(
                            "Private credentials are destroyed");
                    } else {
                        if (System.currentTimeMillis() 
                            < checkCredentials(cred, clientSubject, "accept")) {
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Checks that the principals and credentials associated with the specified
     * private credential are present and valid in the server subject, and that
     * the caller has permission to access them given the specified client
     * subject and permission action.  Returns the time until which the
     * certificates are valid if successful, otherwise throws
     * SecurityException.  The clientSubject should be read-only if it is not
     * null.
     */
    private long checkCredentials(X500PrivateCredential cred,
				  Subject clientSubject,
				  String permissionAction)
    {
	Subject subject = getSubject();
	if (subject == null) {
	    throw new SecurityException("Missing subject");
	}
	X509Certificate cert = cred.getCertificate();
	if (SubjectCredentials.getPrincipal(subject, cert) == null) {
	    throw new SecurityException("Missing principal");
	}
	CertPath chain =
	    SubjectCredentials.getCertificateChain(subject, cert);
	if (chain == null) {
	    throw new SecurityException("Missing public credentials");
	}
	long validUntil = certificatesValidUntil(chain);
	if (clientSubject != null) {
	    assert clientSubject.isReadOnly();
	    CertPath clientChain = (CertPath)
		clientSubject.getPublicCredentials().iterator().next();
	    validUntil = Math.min(
		validUntil, certificatesValidUntil(clientChain));
	}
	if (System.currentTimeMillis() > validUntil) {
	    throw new SecurityException("Certificates no longer valid");
	}
	String peer = getPeerPrincipalName(clientSubject);
	X500PrivateCredential pc =
	    getPrivateCredential(cert, peer, permissionAction);
	if (pc == null) {
	    throw new SecurityException("Missing private credentials");
	} else if (!equalPrivateCredentials(cred, pc)) {
	    throw new SecurityException("Wrong private credential");
	}
	return validUntil;
    }
    
    /**
     * Returns the name of the principal for the peer subject, which should be
     * read-only if it is not null.
     */
    private String getPeerPrincipalName(Subject peerSubject) {
	if (peerSubject == null) {
	    return null;
	}
	assert peerSubject.isReadOnly();
	Principal p =
	    (Principal) peerSubject.getPrincipals().iterator().next();
	return p.getName();
    }

    /** Returns the server logger */
    @Override
    Logger getLogger() {
	return LOGGER;
    }

    /**
     * Gets the private credential for the specified X.509 certificate,
     * checking for AuthenticationPermission to listen for the specified local
     * principal and all peers.
     *
     * @param cert the certificate for the local principal
     * @return the associated private credential or null if not found
     * @throws SecurityException if the current access control context does not
     *	       have the proper AuthenticationPermission
     */
    @Override
    X500PrivateCredential getPrivateCredential(X509Certificate cert) {
	return getPrivateCredential(cert, (String) null, "listen");
    }

    /**
     * Checks for AuthenticationPermission to accept for the specified local
     * and peer principals.  The peer is specified as a String to avoid needing
     * to use the separate X.509 certificate type that JSSE uses for peer
     * certificate chains.
     *
     * @param cert the certificate for the local principal
     * @param peer the name of the peer principal or null if not known
     * @param permissionAction the AuthenticationPermission action
     * @return the associated private credential or null if not found
     * @throws SecurityException if the current access control context does not
     *	       have the proper AuthenticationPermission
     */
    private X500PrivateCredential getPrivateCredential(X509Certificate cert,
						       String peer,
						       String permissionAction)
    {
	Subject subject = getSubject();
	if (subject == null) {
	    return null;
	}

	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(
		getAuthPermission(cert, peer, permissionAction));
	}

	if (subjectIsReadOnly) {
	    for (int i = readOnlyPrivateCredentials.length; --i >= 0; ) {
		X500PrivateCredential xpc = readOnlyPrivateCredentials[i];
		if (cert.equals(xpc.getCertificate())) {
		    return xpc;
		}
	    }
	    return null;
	}
	return (X500PrivateCredential) AccessController.doPrivileged(
	    new SubjectCredentials.GetPrivateCredentialAction(
		subject, cert));
    }

    /**
     * Returns the authentication permission for the specified principals and
     * action.
     */
    private AuthenticationPermission getAuthPermission(X509Certificate cert,
						       String peer,
						       String action)
    {
	Set server = Collections.singleton(cert.getSubjectX500Principal());
	Set client = (peer == null)
	    ? null : Collections.singleton(new X500Principal(peer));
	return new AuthenticationPermission(server, client, action);
    }

    /* -- Implement X509KeyManager -- */

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
	return null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
	String[] result = getAliases(keyType, issuers);
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE,
		       "get server aliases for key type {0}\n" +
		       "and issuers {1}\nreturns {2}",
		       new Object[] {
			   keyType, Utilities.toString(issuers), Utilities.toString(result)
		       });
	}
	return result;
    }

    @Override
    public String chooseClientAlias(
	String[] keyTypes, Principal[] issuers, Socket socket)
    {
	return null;
    }

    /**
     * Returns the last server credential selected for this key type, if still
     * usable.  If not, then invalidate all sessions with the same key type and
     * attempt to find another key.
     */
    @Override
    public String chooseServerAlias(
	String keyType, Principal[] issuers, Socket socket)
    {
	X500PrivateCredential cred = null;
	synchronized (credentialCache) {
	    Object val = credentialCache.get(keyType);
	    if (val instanceof X500PrivateCredential) {
		cred = (X500PrivateCredential) val;
		try {
                        checkCredentials(cred, null, "listen");
		} catch (SecurityException e) {
		    if (LOGGER.isLoggable(Levels.HANDLED)) {
			Utilities.logThrow(LOGGER, Levels.HANDLED,
				 ServerAuthManager.class, "chooseServerAlias",
				 "choose server alias for key type {0}\n" +
				 "and issuers {1}\ncaught exception",
				 new Object[] { keyType, Utilities.toString(issuers) },
				 e);
		    }
		    /*
		     * This credential is no longer present or we don't have
		     * permission to use it.  Clear the cache and invalidate
		     * sessions with this key type.
		     */
		    cred = null;
		    credentialCache.remove(keyType);
                    if (sslSessionContext != null){
                        for (Enumeration en = sslSessionContext.getIds();
                             en.hasMoreElements(); )
                        {
                            SSLSession session =
                                sslSessionContext.getSession(
                                    (byte[]) en.nextElement());
                            if (session != null) {
                                String suite = session.getCipherSuite();
                                if (keyType.equals(Utilities.getKeyAlgorithm(suite))) {
                                    session.invalidate();
                                }
                            }
                        }
                    }
		}
	    }
	    if (cred == null) {
		/* Try to select a new alias */
		Exception exception = null;
		try {
		    cred = chooseCredential(keyType, issuers);
		    if (cred != null) {
			credentialCache.put(keyType, cred);
		    }
		} catch (GeneralSecurityException e) {
		    exception = e;
		} catch (SecurityException e) {
		    exception = e;
		}
		if (exception != null) {
		    credentialCache.put(keyType, exception.getMessage());
		    return null;
		}
	    }
	}
	String result = (cred == null)? null
	    : SubjectCredentials.getCertificateName(cred.getCertificate());
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE,
		       "choose server alias for key type {0}\nissuers {1}\n" +
		       "returns {2}",
		       new Object[] { keyType, Utilities.toString(issuers), result });
	}
	return result;
    }
    
    /* -- Implement X509ExtendedKeyManager -- */
    
    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
	return chooseServerAlias(keyType, issuers, null);
    }
}
