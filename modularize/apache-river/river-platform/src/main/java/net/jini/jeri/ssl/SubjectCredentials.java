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

import java.math.BigInteger;
import java.security.Key;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.security.Security;

/**
 * Provides methods for retrieving credentials from a Subject.
 *
 * 
 */
class SubjectCredentials extends Utilities {

    /** This class shouldn't be instantiated */
    private SubjectCredentials() { }

    /**
     * Retrieves the X.509 CertPath for a credential name.  Returns null if the
     * chain associated with the credential name is not found.  Does not check
     * if either principal or private key associated with the chain are
     * present.
     *
     * @param subject the Subject containing the credentials or null
     * @param name the name of the credentials
     * @return the certificate chain or null
     */
    static CertPath getCertificateChain(Subject subject, String name) {
	if (subject == null) {
	    return null;
	}
	CertificateMatcher matcher = CertificateMatcher.create(name);
	if (matcher != null) {
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext(); ) {
		    Object cred = it.next();
		    if (isX509CertificateChain(cred)) {
			CertPath chain = (CertPath) cred;
			if (matcher.matches(firstX509Cert(chain))) {
			    return chain;
			}
		    }
		}
	    }
	}
	return null;
    }

    /**
     * Returns the credential name for an X.509 certificate.
     *
     * @param cert the certificate
     * @return the credential name
     */
    static String getCertificateName(X509Certificate cert) {
	return CertificateMatcher.getName(cert);
    }

    /**
     * Provides utilities for converting between X.509 certificates and unique
     * certificate names.
     */
    private static class CertificateMatcher {
	private final BigInteger serialNumber;
	private final String issuerName;

	/**
	 * Creates an object that can be compared with an X.509 certificate.
	 * Returns null if the argument is not a valid certificate name.
	 */
	static CertificateMatcher create(String certificateName) {
	    if (certificateName == null) {
		return null;
	    }
	    int atSignPosition = certificateName.indexOf('@');
	    if (atSignPosition < 0) {
		return null;
	    }
	    BigInteger serialNumber;
	    try {
		serialNumber = new BigInteger(
		    certificateName.substring(0, atSignPosition),
		    16);
	    } catch (NumberFormatException e) {
		return null;
	    }
	    String issuerName = certificateName.substring(atSignPosition + 1);
	    return new CertificateMatcher(serialNumber, issuerName);
	}

	private CertificateMatcher(BigInteger serialNumber, String issuerName)
	{
	    this.serialNumber = serialNumber;
	    this.issuerName = issuerName;
	}

	/** Returns the unique certificate name for an X.509 certificate */
	static String getName(X509Certificate certificate) {
	    /*
	     * Use the certificate serial number, which is unique for all
	     * certificates from a given issuer, plus the issuer name, to get a
	     * unique name for the certificate.
	     */
	    return certificate.getSerialNumber().toString(16) + "@" +
		getIssuerName(certificate);
	}

	/**
	 * Returns true if an X.509 certificate matches the certificate name
	 * specified in the constructor.
	 */
	boolean matches(X509Certificate certificate) {
	    return certificate.getSerialNumber().equals(serialNumber)
		&& getIssuerName(certificate).equals(issuerName);
	}

	/** Returns the canonical issuer name for an X.509 certificate. */
	private static String getIssuerName(X509Certificate certificate) {
	    return certificate.getIssuerX500Principal().getName(
		X500Principal.CANONICAL);
	}
    }

    /**
     * Returns the X.509 CertPaths stored in the public credentials of the
     * subject.  Does not check if the associated principals or private keys
     * are present.  Returns null if none are found.
     *
     * @param subject the subject containing the X.509 CertPaths or null
     * @return List of the X.509 CertPaths in the subject
     */
    static List getCertificateChains(Subject subject) {
	List result = null;
	if (subject != null) {
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext(); ) {
		    Object cred = it.next();
		    if (isX509CertificateChain(cred)) {
			if (result == null) {
			    result = new ArrayList(publicCreds.size());
			}
			result.add(cred);
		    }
		}
	    }
	}
	return result;
    }	    

    /**
     * Checks if the subject's public credentials contain a certificate chain
     * that starts with a certificate with the same subject and public key, and
     * returns the certificate chain if it does.  Does not check the validity
     * of the certificate chain, or for associated private credentials or
     * principal.
     *
     * @param cert the certificate
     * @return the certificate chain starting with an equivalent certificate,
     *	       if present, otherwise null
     */
    static CertPath getCertificateChain(Subject subject, X509Certificate cert)
    {
	if (subject != null) {
	    Principal subjectDN = null;
	    PublicKey key = null;
	    Set publicCreds = subject.getPublicCredentials();
	    synchronized (publicCreds) {
		for (Iterator it = publicCreds.iterator(); it.hasNext(); ) {
		    Object cred = it.next();
		    if (!isX509CertificateChain(cred)) {
			continue;
		    }
		    CertPath chain = (CertPath) cred;
		    X509Certificate start = firstX509Cert(chain);
		    if (cert.equals(start)) {
			return chain;
		    }
		    if (subjectDN == null) {
			subjectDN = cert.getSubjectDN();
			key = cert.getPublicKey();
		    }
		    if (subjectDN.equals(start.getSubjectDN())
			&& key.equals(start.getPublicKey()))
		    {
			return chain;
		    }
		}
	    }
	}
	return null;
    }

    /**
     * Retrieves the principals in the subject with X.509 CertPaths which use
     * the specified key algorithm and, optionally, have associated private
     * credentials.  Uses the specified private credentials rather than getting
     * them from the Subject to permit callers to cache them and avoid the cost
     * of repeated permission checks to access them.
     *
     * @param subject the Subject containing the principals
     * @param keyAlgorithms the permitted key algorithms, an OR of any of
     *	      DSA_KEY_ALGORITHM and RSA_KEY_ALGORITHM
     * @param privateCredentials the available private credentials, or null if
     *	      private credentials are not required
     * @return set of matching principals
     */
    static Set getPrincipals(Subject subject,
			     int keyAlgorithms,
			     X500PrivateCredential[] privateCredentials)
    {
	Set result = new HashSet(subject.getPrincipals().size());
	List certPaths = getCertificateChains(subject);
	if (certPaths != null) {
	    for (int i = certPaths.size(); --i >= 0; ) {
		CertPath chain = (CertPath) certPaths.get(i);
		X509Certificate cert = firstX509Cert(chain);
		String alg = cert.getPublicKey().getAlgorithm();
		if (!permittedKeyAlgorithm(alg, keyAlgorithms)) {
		    continue;
		}
		X500Principal principal = getPrincipal(subject, cert);
		if (principal != null) {
		    boolean pcOK = privateCredentials == null;
		    if (!pcOK) {
			for (int j = privateCredentials.length; --j >= 0; ) {
			    X500PrivateCredential xpc = privateCredentials[j];
			    if (cert.equals(xpc.getCertificate())) {
				pcOK = true;
				break;
			    }
			}
		    }
		    if (pcOK) {
			result.add(principal);
		    }
		}
	    }
	}
	return result;
    }

    /**
     * A privileged action that gets the private credentials for an X.509
     * certificate.
     */
    static class GetPrivateCredentialAction implements PrivilegedAction {
	private final Subject subject;
	private final X509Certificate cert;

	GetPrivateCredentialAction(Subject subject, X509Certificate cert) {
	    this.subject = subject;
	    this.cert = cert;
	}

	public Object run() {
	    return SubjectCredentials.getPrivateCredential(subject, cert);
	}
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

    /**
     * Returns the X500PrivateCredential for an X.509 certificate.  Returns
     * null if the associated private credential is missing from the subject.
     * Does not check if the public credential or principal are present.
     * Assumes that the subject is non-null.  The caller should check for
     * AuthenticationPermission and then call this method from within
     * AccessController.doPrivileged to give it private credential permissions.
     *
     * @param subject the Subject containing the credentials
     * @param cert the X.509 certificate
     * @return the X500PrivateCredential or null
     */
    static X500PrivateCredential getPrivateCredential(Subject subject,
						      X509Certificate cert)
    {
	X500PrivateCredential result = null;
	Set privateCreds = subject.getPrivateCredentials();
	synchronized (privateCreds) {
	    /*
	     * XXX: Include this synchronization to work around BugID 4892913,
	     * Subject.getPrivateCredentials not thread-safe against changes to
	     * principals.  -tjb[18.Jul.2003]
	     *
	     * synchronized (subject.getPrincipals()) {
	     */
	    for (Iterator it = privateCreds.iterator(); it.hasNext(); ) {
		Object cred = it.next();
		if (cred instanceof X500PrivateCredential) {
		    X500PrivateCredential xpc =
			(X500PrivateCredential) cred;
		    if (cert.equals(xpc.getCertificate())) {
			result = xpc;
			break;
		    }
		}
	    }
	}
	return result;
    }

    /**
     * Returns the subject principal matching the X.509 certificate.  Returns
     * null if the principal is not found.  Does not check if the associated
     * private key is present.  Assumes that the subject is non-null.
     *
     * @param subject the Subject containing the credentials 
     * @param cert the X.509 certificate
     * @return the X.500 principal or null
     */
    static X500Principal getPrincipal(Subject subject, X509Certificate cert) {
	X500Principal x500 = cert.getSubjectX500Principal();
	String name = x500.getName(X500Principal.CANONICAL);
	Set principals = subject.getPrincipals();
	synchronized (principals) {
	    for (Iterator i = principals.iterator(); i.hasNext(); ) {
		Object next = i.next();
		if (!(next instanceof X500Principal)) {
		    continue;
		}
		X500Principal principal = (X500Principal) next;
		if (principal.getName(X500Principal.CANONICAL).equals(name)) {
		    return principal;
		}
	    }
	}
	return null;
    }

    /**
     * Returns a String that describes the credentials in the subject.
     *
     * @param subject the Subject containing the credentials
     * @return a String describing the credentials
     * @throws NullPointerException if the subject is null
     */
    static String credentialsString(Subject subject) {
	List certPaths = getCertificateChains(subject);
	if (certPaths == null) {
	    return "";
	}
	StringBuffer buf = new StringBuffer();
	for (int i = certPaths.size(); --i >= 0; ) {
	    CertPath chain = (CertPath) certPaths.get(i);
	    X509Certificate cert = firstX509Cert(chain);
	    X500Principal principal = getPrincipal(subject, cert);
	    if (principal != null) {
		buf.append("  Principal: ").append(principal).append('\n');
		buf.append("    Public key: ");
		appendKeyString(cert.getPublicKey(), buf);
		buf.append('\n');
		buf.append("    Private key: ");
		try {
		    X500PrivateCredential cred =
			(X500PrivateCredential) Security.doPrivileged(
			    new GetPrivateCredentialAction(subject, cert));
		    PrivateKey privateKey =
			cred != null ? cred.getPrivateKey() : null;
		    if (privateKey == null) {
			buf.append("Not found");
		    } else {
			appendKeyString(privateKey, buf);
		    }
		} catch (SecurityException e) {
		    buf.append("No permission");
		}
	    }
	}
	return buf.toString();
    }

    /** Appends information about a key to a StringBuffer. */
    private static void appendKeyString(Key key, StringBuffer buf) {
	String className = key.getClass().getName();
	buf.append(className.substring(className.lastIndexOf('.') + 1));
	buf.append('@');
	buf.append(Integer.toHexString(System.identityHashCode(key)));
    }

    /**
     * Determines if the argument is an X.509 certificate CertPath.  Returns
     * true if the argument is a non-null CertPath, has at least one
     * certificate, and has type X.509.
     */
    private static boolean isX509CertificateChain(Object credential) {
	if (!(credential instanceof CertPath)) {
	    return false;
	}
	CertPath certPath = (CertPath) credential;
	if (certPath.getCertificates().isEmpty()) {
	    return false;
	} else if (!certPath.getType().equals("X.509")) {
	    return false;
	}
	return true;
    }
}
