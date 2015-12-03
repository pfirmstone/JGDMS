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
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

/**
 * Implements X509KeyManager by getting its key and credential information from
 * a JAAS subject, and X509TrustManager by using FilterX509TrustManager.
 * Combines both the key and trust managers to enable subclasses to use
 * information for both when checking permissions.
 *
 * 
 */
abstract class AuthManager extends FilterX509TrustManager
    implements X509KeyManager
{

    /* -- Fields -- */

    /**
     * The Subject from which to retrieve principals and credentials.  Use a
     * weak reference to insure that the subject can be GC'ed if it is only
     * being referenced by the SSLContextMap that maps subjects to
     * AuthManagers.  The weak key map won't be weak if the value contains a
     * strong reference to the key.
     */
    private final WeakReference subjectRef;

    /**
     * Whether the subject was read-only when it was stored -- used to avoid
     * checking for changes in the contents of the subject.
     */
    final boolean subjectIsReadOnly;

    /**
     * If non-null, then only principals in this set may be used to
     * authenticate the local end of the connection.
     */
    private final Set permittedLocalPrincipals;

    /* -- Constructors -- */

    /**
     * Creates an AuthManager that retrieves principals and credentials for
     * authentication from the specified subject.  If permittedLocalPrincipals
     * or permittedRemotePrincipals are set, then only those principals may be
     * used for authentication.
     *
     * @param subject the subject for retrieving principals and credentials
     * @param permittedLocalPrincipals if non-null, then only principals in
     *	      this set may be used to authenticate the local end of the
     *	      connection
     * @param permittedRemotePrincipals if non-null, then only principals in
     *	      this set will be trusted to authenticate the peer
     * @throws NoSuchAlgorithmException if the trust manager factory algorithm
     *	       is not found
     */
    AuthManager(Subject subject,
		Set permittedLocalPrincipals,
		Set permittedRemotePrincipals)
	throws NoSuchAlgorithmException
    {
	super(permittedRemotePrincipals);
	subjectRef = new WeakReference(subject);
	subjectIsReadOnly = (subject == null) || subject.isReadOnly();
	this.permittedLocalPrincipals = (permittedLocalPrincipals == null)
	    ? null : new HashSet(permittedLocalPrincipals);
    }

    /* -- Methods -- */

    /**
     * Gets the private credential for the specified X.509 certificate.
     *
     * @param cert the X.509 certificate
     * @return the associated X500PrivateCredential or null if it is not found
     * @throws SecurityException if the current access control context does not
     *	       have the proper AuthenticationPermission
     */
    abstract X500PrivateCredential getPrivateCredential(X509Certificate cert);

    /** Returns the logger to use for logging. */
    abstract Logger getLogger();

    /**
     * Returns the Subject, or null if the subject was null or if the weak
     * reference has been cleared.
     */
    Subject getSubject() {
	return (Subject) subjectRef.get();
    }

    /**
     * Returns all the aliases that match the specified key type and issuers
     * for which checkChain succeeds.  Returns null if no matching aliases are
     * found.
     */
    String[] getAliases(String keyType, Principal[] issuers) {
	List certPaths = SubjectCredentials.getCertificateChains(getSubject());
	if (certPaths == null) {
	    return null;
	}
	Collection result = null;
	for (int i = certPaths.size(); --i >= 0; ) {
	    CertPath chain = (CertPath) certPaths.get(i);
	    Exception exception;
	    try {
		if (checkChain(chain, keyType, issuers) != null) {
		    if (result == null) {
			result = new ArrayList(certPaths.size());
		    }
		    result.add(
			SubjectCredentials.getCertificateName(
			    firstX509Cert(chain)));
		}
		continue;
	    } catch (GeneralSecurityException e) {
		exception = e;
	    } catch (SecurityException e) {
		exception = e;
	    }
	    Logger logger = getLogger();
	    if (logger.isLoggable(Levels.HANDLED)) {
		logThrow(logger, Levels.HANDLED,
			 AuthManager.class, "getAliases",
			 "get aliases for key type {0}\nand issuers {1}\n" +
			 "caught exception",
			 new Object[] { keyType, toString(issuers) },
			 exception);
	    }
	}
	if (result == null) {
	    return null;
	} else {
	    return (String[]) result.toArray(new String[result.size()]);
	}
    }

    /**
     * Returns a private credential that matches the specified key type and
     * issuers for which checkChain returns a non-null value, or null if no
     * matching credentials are found.  Throws a GeneralSecurityException or
     * SecurityException if a problem occurs with all matching credentials.
     */
    X500PrivateCredential chooseCredential(String keyType,
					   Principal[] issuers)
	throws GeneralSecurityException
    {
	List certPaths = SubjectCredentials.getCertificateChains(getSubject());
	if (certPaths == null) {
	    return null;
	}
	List exceptions = null;
	for (int i = certPaths.size(); --i >= 0; ) {
	    CertPath chain = (CertPath) certPaths.get(i);
	    Exception exception;
	    try {
		X500PrivateCredential pc = checkChain(chain, keyType, issuers);
		if (pc == null) {
		    continue;
		} else {
		    return pc;
		}
	    } catch (GeneralSecurityException e) {
		exception = e;
	    } catch (SecurityException e) {
		exception = e;
	    }
	    if (exceptions == null) {
		exceptions = new ArrayList();
	    }
	    exceptions.add(exception);
	    Logger logger = getLogger();
	    if (logger.isLoggable(Levels.HANDLED)) {
		logThrow(logger, Levels.HANDLED,
			 AuthManager.class, "chooseCredential",
			 "choose credential for key type {0}\n" +
			 "and issuers {1}\ncaught exception",
			 new Object[] { keyType, toString(issuers) },
			 exception);
	    }
	}
	if (exceptions == null) {
	    return null;
	} else if (exceptions.size() > 1) {
	    for (int i = exceptions.size(); --i >= 0; ) {
		Exception e = (Exception) exceptions.get(i);
		if (!(e instanceof SecurityException)) {
		    throw new GeneralSecurityException(exceptions.toString());
		}
	    }
	    throw new SecurityException(exceptions.toString());
	} else if (exceptions.get(0) instanceof SecurityException) {
	    throw (SecurityException) exceptions.get(0);
	} else {
	    throw (GeneralSecurityException) exceptions.get(0);
	}
    }

    /**
     * Checks if the specified certificate chain can be used for keys of the
     * specified type and with the specified issuers.  Returns null if the
     * chain has the wrong key type, throws an exception if the credentials or
     * subject has problems, and otherwise returns the associated private
     * credential. <p>
     *
     * Checks that:
     * <ul>
     * <li> The key algorithm matches that of the certificate's public key
     * <li> The subject contains the principal for the certificate's issuer DN
     * <li> That principal is a permitted local principal
     * <li> The certificate chain terminates with one of the issuers, if issuers
     *      are specified
     * <li> The certificate chain is currently valid
     * <li> If the certificate specifies a KeyUsage extension, that the
     *      extension permits use in digital signatures
     * <li> The caller has the proper AuthenticationPermission
     * </ul> <p>
     *
     * Because the following things should only occur because of a
     * configuration problem, this method does not check for:
     * <ul>
     * <li> Gaps in certificate chains
     * <li> CA extensions
     * <li> Incorrect private key
     * </ul>
     */
    private X500PrivateCredential checkChain(CertPath chain,
					     String keyType,
					     Principal[] issuers)
      	throws GeneralSecurityException
    {
	X509Certificate head = firstX509Cert(chain);
	String certKeyType = head.getPublicKey().getAlgorithm();
	if (!certKeyType.equals(keyType)) {
	    return null;
	}
	Subject subject = getSubject();
	X500Principal principal =
	    SubjectCredentials.getPrincipal(subject, head);
	if (principal == null) {
	    throw new GeneralSecurityException(
		"Principal not found: " + head.getSubjectDN());
	} else if (permittedLocalPrincipals != null
		   && !permittedLocalPrincipals.contains(principal))
	{
	    throw new GeneralSecurityException(
		"Local principal not permitted: " + head.getSubjectDN());
	}
	X500Principal[] x500Issuers = null;
	if (issuers != null) {
	    x500Issuers = new X500Principal[issuers.length];
	    for (int i = issuers.length; --i >= 0; ) {
		x500Issuers[i] = (issuers[i] instanceof X500Principal)
		    ? (X500Principal) issuers[i]
		    : new X500Principal(issuers[i].getName());
	    }
	}
	checkValidity(chain, x500Issuers);

	/*
	 * Check that a critical key usage extension, if present, permits use
	 * for digital signatures.
	 */
	boolean[] keyUsage = head.getKeyUsage();
	if (keyUsage != null
	    && keyUsage.length > 0
	    /* Element 0 is for digitalSignature */
	    && !keyUsage[0])
	{
	    throw new CertificateException(
		"Certificate not permitted for digital signatures: " + head);
	}

	/* Also throws SecurityException */
	X500PrivateCredential xpc = getPrivateCredential(head);
	if (xpc == null) {
	    throw new KeyException(
		"Private key not found for certificate: " + head);
	}

	return xpc;
    }

    /**
     * Returns the latest time for which all of the X.509 certificates in the
     * certificate chain are valid.
     */
    static long certificatesValidUntil(CertPath chain) {
	long result = Long.MAX_VALUE;
	List<? extends Certificate> certs = chain.getCertificates();
	for (int i = certs.size(); --i >= 0; ) {
	    X509Certificate cert = (X509Certificate) certs.get(i);
	    long until = cert.getNotAfter().getTime();
	    if (until < result) {
		result = until;
	    }
	}
	return result;
    }

    /**
     * Returns the latest time for which all of the X.509 certificates in the
     * certificate chain are valid.
     */
    static long certificatesValidUntil(X509Certificate[] chain) {
	long result = Long.MAX_VALUE;
	for (int i = chain.length; --i >= 0; ) {
	    X509Certificate cert = chain[i];
	    long until = cert.getNotAfter().getTime();
	    if (until < result) {
		result = until;
	    }
	}
	return result;
    }

    /**
     * Checks if the two private credentials refer to the same principal and
     * have the equivalent private key.
     */
    boolean equalPrivateCredentials(X500PrivateCredential cred1,
				    X500PrivateCredential cred2)
    {
	if (cred1 == null || cred2 == null) {
	    return false;
	}
	X509Certificate cert1 = cred1.getCertificate();
	X509Certificate cert2 = cred2.getCertificate();
	if (cert1 == null
	    || cert2 == null
	    || !safeEquals(cert1.getSubjectDN(), cert2.getSubjectDN()))
	{
	    return false;
	}
	/*
	 * I'm assuming I can depend on the equals method for private keys to
	 * check if the two objects represent the same key without being
	 * identical objects.  Although that behavior isn't documented, at
	 * least the sun.security.pkcs.PKCS8Key class does that.
	 * -tjb[8.Jan.2001]
	 */
	PrivateKey key1 = cred1.getPrivateKey();
	return key1 != null && key1.equals(cred2.getPrivateKey());
    }

    /* -- Implement X509KeyManager -- */

    public X509Certificate[] getCertificateChain(String alias) {
	CertPath chain = 
	    SubjectCredentials.getCertificateChain(getSubject(), alias);
	List certs = chain.getCertificates();
	return (X509Certificate[]) certs.toArray(
	    new X509Certificate[certs.size()]);
    }

    public PrivateKey getPrivateKey(String alias) {
	CertPath chain =
	    SubjectCredentials.getCertificateChain(getSubject(), alias);
	if (chain != null) {
	    try {
		X500PrivateCredential xpc =
		    getPrivateCredential(firstX509Cert(chain));
		if (xpc != null) {
		    return xpc.getPrivateKey();
		}
	    } catch (SecurityException e) {
		Logger logger = getLogger();
		if (logger.isLoggable(Levels.HANDLED)) {
		    logThrow(logger, Levels.HANDLED,
			     AuthManager.class, "getPrivateKey",
			     "get private key for alias {0}\n" +
			     "caught exception",
			     new Object[] { alias },
			     e);
		}
	    }
	}
	return null;
    }
}
