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
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.security.AuthenticationPermission;
import org.apache.river.logging.Levels;

/**
 *
 * @author peter
 */
class ServerSubjectKeyManager extends SubjectKeyManager{
    
    /** Server transport logger */
    private static final Logger logger = SERVER_LOGGER;

    /** The SSLSessionContext for all connections, null for stateless TLS. */
    private final SSLSessionContext sslSessionContext;

    /** The subject's private credentials, if the subject is read-only. */
    private final X500PrivateCredential[] readOnlyPrivateCredentials;

    /**
     * Maps a key type to last private credentials returned for that key type,
     * or a String describing problems that prevented getting private
     * credentials.
     */
    private final Map credentialCache = new HashMap(2);
    
    /** Returns the server logger */
    @Override
    Logger getLogger() {
	return logger;
    }

    /**
     * Creates an AuthManager that retrieves principals and credentials for
     * authentication from the specified subject.
     *
     * @param subject the subject for retrieving principals and credentials
     * @throws NoSuchAlgorithmException if the trust manager factory algorithm
     *	       is not found
     */
    ServerSubjectKeyManager(Subject subject,
			    SSLSessionContext sslSessionContext)
	throws NoSuchAlgorithmException, NoSuchProviderException
    {
	super(subject);
	this.sslSessionContext = sslSessionContext;
	readOnlyPrivateCredentials =
	    !subjectIsReadOnly || subject == null ? null
	    : (X500PrivateCredential[]) AccessController.doPrivileged(
		new GetAllPrivateCredentialsAction(
		    subject));
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
	return null;
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
	String[] result = getAliases(keyType, issuers);
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "get server aliases for key type {0}\n" +
		       "and issuers {1}\nreturns {2}",
		       new Object[] {
			   keyType, toString(issuers), toString(result)
		       });
	}
	return result;
    }
    
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
	return null;
    }
    
    /*
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
		    if (logger.isLoggable(Levels.HANDLED)) {
			logThrow(logger, Levels.HANDLED,
				 ServerSubjectKeyManager.class, "chooseServerAlias",
				 "choose server alias for key type {0}\n" +
				 "and issuers {1}\ncaught exception",
				 new Object[] { keyType, toString(issuers) },
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
                                if (keyType.equals(getKeyAlgorithm(suite))) {
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
	String result = (cred == null)
	    ? null
	    : getCertificateName(cred.getCertificate());
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "choose server alias for key type {0}\nissuers {1}\n" +
			   "returns {2}",
			   new Object[] { keyType, toString(issuers), result });
	    }
	return result;
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
	if (getPrincipal(subject, cert) == null) {
	    throw new SecurityException("Missing principal");
	}
	CertPath chain =
	    getCertificateChain(subject, cert);
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

	/* We can't check permission with RMI because there is an empty
	   unprivileged domain on the stack, to do so would require granting
	   this permission to everyone, just to complete a handshake. 
	   That would be a generally bad idea.  Instead
	   these methods remain private and inaccessible so the details
	   don't leak outside the api */
//	SecurityManager sm = System.getSecurityManager();
//	if (sm != null) {
//	    sm.checkPermission(
//		getAuthPermission(cert, peer, permissionAction));
//	}

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
	    new GetPrivateCredentialAction(subject, cert));
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
    synchronized X500PrivateCredential getPrivateCredential(X509Certificate cert) {
	return getPrivateCredential(cert, (String) null, "listen");
    }
    
    /**
     * A privileged action that returns all the X.500 private credentials for a
     * subject as an X500PrivateCredential array.  Assumes that the subject is
     * non-null.
     */
    static class GetAllPrivateCredentialsAction implements PrivilegedAction {
	private final Subject subject;

	GetAllPrivateCredentialsAction(Subject subject) {
	    this.subject = subject;
	}

	public Object run() {
	    Set pcs = subject.getPrivateCredentials();
	    List xpcs = new ArrayList(pcs.size());
	    synchronized (pcs) {
		/*
		 * XXX: Include this synchronization to work around BugID
		 * 4892913, Subject.getPrivateCredentials not thread-safe
		 * against changes to principals.  -tjb[22.Jul.2003]
		 *
		 * synchronized (subject.getPrincipals()) {
		 */
		for (Iterator iter = pcs.iterator(); iter.hasNext(); ) {
		    Object pc = iter.next();
		    if (pc instanceof X500PrivateCredential) {
			xpcs.add(pc);
		    }
		}
	    }
	    return xpcs.toArray(new X500PrivateCredential[xpcs.size()]);
	}
    }
}
