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

package com.sun.jini.discovery.internal;

import com.sun.jini.discovery.DatagramBufferFactory;
import com.sun.jini.logging.Levels;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.LDAPCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.security.auth.AuthPermission;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.AuthenticationPermission;

/**
 * Superclass for providers for the net.jini.discovery.x500.* discovery
 * formats.
 */
class X500Provider extends BaseProvider {

    private static final String NAME = "com.sun.jini.discovery.x500";
    private static final String JSSE = "javax.net.ssl";
    private static final int INT_LEN = 4;
    private static final Pattern hostPortPattern =
	Pattern.compile("^(.+):(\\d+?)$");
    private static final AuthPermission authPermission =
	new AuthPermission("getSubject");
    static final Logger logger = Logger.getLogger(NAME);

    /** The signature algorithm (for example, "SHA1withDSA"). */
    protected final String signatureAlgorithm;
    /** The maximum length of generated signatures, in bytes. */
    protected final int maxSignatureLength;
    /** The key algorithm name (for example, "DSA"). */
    protected final String keyAlgorithm;
    /** The key algorithm OID. */
    protected final String keyAlgorithmOID;

    private KeyStore trustStore = null;
    private CertStore[] certStores = null;
    private final Object storeLock = new Object();

    /**
     * Creates an instance with the given attributes.
     */
    X500Provider(String formatName,
		 String signatureAlgorithm,
		 int maxSignatureLength,
		 String keyAlgorithm,
		 String keyAlgorithmOID)
    {
	super(formatName);
	if (maxSignatureLength < 0) {
	    throw new IllegalArgumentException();
	}
	if (keyAlgorithm == null || keyAlgorithmOID == null) {
	    throw new NullPointerException();
	}
	this.signatureAlgorithm = signatureAlgorithm;
	this.maxSignatureLength = maxSignatureLength;
	this.keyAlgorithm = keyAlgorithm;
	this.keyAlgorithmOID = keyAlgorithmOID;
    }

    /**
     * Returns certificate corresponding to the given principal, or null if no
     * matching certificate can be found.  Subclasses can override this method
     * to customize the certificate search mechanism.
     * <p>
     * The default implementation of this method does the following: the first
     * time this method is called on this instance, a keystore containing trust
     * anchors for the certificate to return is loaded.  The location of the
     * file to load the keystore from can be specified (in order of precedence)
     * by the com.sun.jini.discovery.x500.trustStore and
     * javax.net.ssl.trustStore system properties; if no location is specified,
     * then the cacerts file in the lib/security subdirectory of the JDK
     * installation directory is used.  If specified, the location is treated as
     * a URL. If no protocol is specified in the URL or it is an unknown
     * protocol, then, the location is treated as a file name.
     * Depending on which system property is used to specify the keystore
     * location, the com.sun.jini.discovery.x500.trustStoreType and
     * com.sun.jini.discovery.x500.trustStorePassword or
     * javax.net.ssl.trustStoreType and javax.net.ssl.trustStorePassword system
     * properties can be used to specify the type of the keystore and the
     * password to use when loading it.  If no keystore type is specified, then
     * the type returned by KeyStore.getDefaultType() is used; if no password
     * is specified, then no password is used when loading the keystore.
     * Additionally, if the com.sun.jini.discovery.x500.ldapCertStores system
     * property is set, its value is interpreted as a comma-separated list of
     * "host[:port]" elements which are used to obtain references to LDAP-based
     * CertStore instances.
     * <p>
     * For each call, the default implementation of this method creates a PKIX
     * CertPathBuilder and calls its build method, passing as the argument a
     * PKIXBuilderParameters instance initialized with the aforementioned
     * keystore, CertStores (if any), and a CertSelector based on the provided
     * X.500 principal and the key algorithm OID for this instance.  If the
     * build operation succeeds, the resulting certificate is returned.
     */
    protected Certificate getCertificate(final X500Principal principal)
	throws IOException, GeneralSecurityException
    {
	try {
	    return (Certificate) AccessController.doPrivileged(
		new PrivilegedExceptionAction() {
		    public Object run()
			throws IOException, GeneralSecurityException
		    {
			return getCertificate0(principal);
		    }
		});
	} catch (PrivilegedActionException e) {
	    Throwable t = e.getCause();
	    if (t instanceof IOException) {
		throw (IOException) t;
	    } else {
		throw (GeneralSecurityException) t;
	    }
	}
    }

    /**
     * Returns non-null array containing the usable X.500 private credentials
     * of the current subject (if any).  This method does not check that the
     * caller has AuthenticationPermission to use the credentials.
     */
    X500PrivateCredential[] getPrivateCredentials() {
	final AccessControlContext acc = AccessController.getContext();
	Collection[] subjInfo = (Collection[]) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    Subject s = Subject.getSubject(acc);
		    // filter principals & credentials manually, due to 4892841
		    return (s != null) ?
			new Collection[]{
			    syncGetInstances(
				s.getPrincipals(), X500Principal.class),
			    syncGetInstances(
				s.getPrivateCredentials(),
				X500PrivateCredential.class)
			} :
			new Collection[]{ 
			    Collections.EMPTY_SET, Collections.EMPTY_SET
			};
		}
	    });
	Collection ppals = subjInfo[0];
	Collection creds = subjInfo[1];
	List l = new ArrayList();
	for (Iterator i = creds.iterator(); i.hasNext(); ) {
	    X500PrivateCredential cred = (X500PrivateCredential) i.next();
	    X509Certificate cert = cred.getCertificate();
	    try {
		checkCertificate(cert);
	    } catch (CertificateException e) {
		logger.log(Levels.HANDLED, "invalid certificate", e);
		continue;
	    }
	    if (keyAlgorithm.equals(cred.getPrivateKey().getAlgorithm()) &&
		ppals.contains(cert.getSubjectX500Principal()))
	    {
		l.add(cred);
	    }
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "obtained private credentials {0}",
		       new Object[]{ l });
	}
	return (X500PrivateCredential[]) l.toArray(
	    new X500PrivateCredential[l.size()]);
    }

    /**
     * Test whether the caller has AuthPermission("getSubject").
     * 
     * @return true if the caller has AuthPermission("getSubject"),
     *         false otherwise
     */
    private static boolean canGetSubject() {
	try {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		sm.checkPermission(authPermission);
	    }
	    return true;
	} catch (SecurityException e) {
	    return false;
	}
    }
    
    /**
     * Only throw non-generic exception if caller has getSubject
     * permission.
     *
     * @param detailedException the real
     *        <code>SecurityException</code> to be thrown if caller
     *        has the "getSubject" <code>AuthPermission</code>
     * @param genericException the generic
     *        <code>UnsupportedConstraintException</code> to be thrown
     *        if caller does not have the "getSubject"
     *        <code>AuthPermission</code>
     */
    static void secureThrow(SecurityException detailedException,
			    UnsupportedConstraintException genericException)
	throws UnsupportedConstraintException
    {
	if (canGetSubject()) { // has "getSubject" permission
	    throw detailedException;
	} else {
	    throw genericException;
	}
    }
    
    /**
     * If a security manager is installed, checks that the calling context has
     * AuthenticationPermission for the given principal and action (with no
     * peer principal specified).
     */
    void checkAuthenticationPermission(X500Principal principal, String action)
    {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new AuthenticationPermission(
		Collections.singleton(principal), null, action));
	}
    }

    /**
     * Returns true if the sig buffer contains the signature of the contents of
     * the data buffer; returns false otherwise. The passed in buffers will be
     * modified in case they do not have a backing array.
     */
    boolean verify(ByteBuffer data, ByteBuffer sig, PublicKey key)
	throws SignatureException, InvalidKeyException, NoSuchAlgorithmException
    {
	data = ensureArrayBacking(data);
	sig = ensureArrayBacking(sig);
	Signature s = getSignature();
	s.initVerify(key);
	s.update(
	    data.array(),
	    data.arrayOffset() + data.position(),
	    data.remaining());
	return s.verify(
	    sig.array(),
	    sig.arrayOffset() + sig.position(),
	    sig.remaining());
    }

    /**
     * Main body of getCertificate(), called from within a doPrivileged block.
     */
    private Certificate getCertificate0(X500Principal principal)
	throws IOException, GeneralSecurityException
    {
	synchronized (storeLock) {
	    if (trustStore == null) {
		initStores();
	    }
	}

	X509CertSelector selector = new X509CertSelector();
	selector.setSubject(principal.getName());
	selector.setSubjectPublicKeyAlgID(keyAlgorithmOID);
	selector.setCertificateValid(new Date());
	// element 0 of keyUsage array is for digital signatures
	selector.setKeyUsage(new boolean[]{ true });
	PKIXBuilderParameters params =
	    new PKIXBuilderParameters(trustStore, selector);
	for (int j = 0; j < certStores.length; j++) {
	    params.addCertStore(certStores[j]);
	}
	PKIXCertPathBuilderResult result;
	try {
	    result = (PKIXCertPathBuilderResult)
		CertPathBuilder.getInstance("PKIX").build(params);
	} catch (CertPathBuilderException e) {
	    logger.log(Levels.HANDLED,
		       "exception building certificate path", e);
	    return null;
	}

	List certs = result.getCertPath().getCertificates();
	return certs.isEmpty() ?
	    result.getTrustAnchor().getTrustedCert() :
	    (Certificate) certs.get(0);
    }

    /**
     * Initializes trust store and cert stores based on system property values.
     */
    private void initStores() throws IOException, GeneralSecurityException {
	String path, type, passwd;
	if ((path = System.getProperty(NAME + ".trustStore")) != null) {
	    type = System.getProperty(
		NAME + ".trustStoreType", KeyStore.getDefaultType());
	    passwd = System.getProperty(NAME + ".trustStorePassword");
	} else if ((path = System.getProperty(JSSE + ".trustStore")) != null) {
	    type = System.getProperty(
		JSSE + ".trustStoreType", KeyStore.getDefaultType());
	    passwd = System.getProperty(JSSE + ".trustStorePassword");
	} else {
	    path = System.getProperty("java.home") + "/lib/security/cacerts";
	    type = KeyStore.getDefaultType();
	    passwd = null;
	}
	KeyStore kstore = KeyStore.getInstance(type);
	InputStream in;
	URL url = null;
	try {
	    url = new URL(path);
	} catch (MalformedURLException e) {
	}
	if (url != null) {
	    in = url.openStream();
	} else {
	    in = new FileInputStream(path);
	}
	try {
	    kstore.load(in, (passwd != null) ? passwd.toCharArray() : null);
	} finally {
	    in.close();
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "loaded trust store from {0} ({1})",
		       new Object[]{ path, type });
	}

	String cstores = System.getProperty(NAME + ".ldapCertStores");
	List l = new ArrayList();
	if (cstores != null) {
	    StringTokenizer tok = new StringTokenizer(cstores, ",");
	    while (tok.hasMoreTokens()) {
		String s = tok.nextToken().trim();
		Matcher m = hostPortPattern.matcher(s);
		try {
		    CertStoreParameters params = m.matches() ?
			new LDAPCertStoreParameters(
			    m.group(1), Integer.parseInt(m.group(2))) :
			new LDAPCertStoreParameters(s);
		    l.add(CertStore.getInstance("LDAP", params));
		} catch (Exception e) {
		    logger.log(Level.WARNING,
			       "exception initializing cert store", e);
		}
	    }
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "using cert stores {0}",
		       new Object[]{ l });
	}
	certStores = (CertStore[]) l.toArray(new CertStore[l.size()]);
	trustStore = kstore;
    }

    /**
     * Returns newly obtained Signature implementing the signature algorithm
     * for this instance.
     */
    private Signature getSignature() throws NoSuchAlgorithmException {
	return Signature.getInstance(signatureAlgorithm);
    }

    /**
     * Returns a new collection containing all instances of the specified class
     * contained in the given collection.  All operations on the given
     * collection are performed while synchronized on the collection.
     */
    private static Collection syncGetInstances(Collection coll, Class cl) {
	synchronized (coll) {
	    Collection c = new ArrayList(coll.size());
	    for (Iterator i = coll.iterator(); i.hasNext(); ) {
		Object obj = i.next();
		if (cl.isInstance(obj)) {
		    c.add(obj);
		}
	    }
	    return c;
	}
    }

    /**
     * Throws a CertificateException if the given certificate is not currently
     * valid, or specifies a KeyUsage extension which prohibits use in digital
     * signatures.
     */
    private static void checkCertificate(X509Certificate cert)
	throws CertificateException
    {
	cert.checkValidity();
	boolean[] keyUsage = cert.getKeyUsage();
	// element 0 of keyUsage array is for digital signatures
	if (keyUsage != null && keyUsage.length > 0 && !keyUsage[0]) {
	    throw new CertificateException(
		"certificate not permitted for digital signatures: " + cert);
	}
    }

    /**
     * Returns given buffer if it is backed by an array; otherwise, returns a
     * newly created array-backed buffer into which the remaining contents of
     * the given buffer have been transferred.
     */
    private static ByteBuffer ensureArrayBacking(ByteBuffer buf) {
	return buf.hasArray() ?
	    buf : (ByteBuffer)
		    ByteBuffer.allocate(buf.remaining()).put(buf).flip();
    }

    /**
     * Buffer factory which signs data written into the buffers it dispenses.
     */
    class SigningBufferFactory implements DatagramBufferFactory {

	private final List buffers = new ArrayList();
	private final DatagramBufferFactory factory;
	private final byte[] principalName;
	private final Signature signature;

	SigningBufferFactory(DatagramBufferFactory factory,
			     X500PrivateCredential cred)
	    throws InvalidKeyException,
		   UTFDataFormatException,
		   NoSuchAlgorithmException
	{
	    this.factory = factory;
	    principalName = Plaintext.toUtf(
		cred.getCertificate().getSubjectX500Principal().getName());
	    signature = getSignature();
	    signature.initSign(cred.getPrivateKey());
	}

	public ByteBuffer newBuffer() {
	    BufferInfo bi = new BufferInfo(factory.newBuffer());
	    buffers.add(bi);
	    return bi.getDataBuffer();
	}

	public void sign() throws SignatureException {
	    for (Iterator i = buffers.iterator(); i.hasNext(); ) {
		((BufferInfo) i.next()).sign();
	    }
	}

	private class BufferInfo {

	    private final ByteBuffer buf;
	    private final ByteBuffer data;
	    private final boolean overflow;

	    BufferInfo(ByteBuffer buf) {
		this.buf = buf;
		data = buf.duplicate();
		int authBlockLen = principalName.length + maxSignatureLength;
		if (data.remaining() >= INT_LEN + authBlockLen) {
		    data.position(data.position() + INT_LEN);
		    data.limit(data.limit() - authBlockLen);
		    overflow = false;
		} else {
		    data.limit(data.position());
		    overflow = true;
		}
	    }

	    ByteBuffer getDataBuffer() {
		return data;
	    }

	    void sign() throws SignatureException {
		if (overflow) {
		    throw new BufferOverflowException();
		}
		buf.putInt(data.position() - (buf.position() + INT_LEN));
		buf.position(data.position());
		buf.put(principalName);

		ByteBuffer b =
		    ensureArrayBacking((ByteBuffer) data.duplicate().flip());
		signature.update(
		    b.array(), b.arrayOffset() + b.position(), b.remaining());
		buf.put(signature.sign());
	    }
	}
    }
}
