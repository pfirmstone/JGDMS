/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Alexey V. Varlamov
* @author Peter Firmstone.
* @since 3.0.0
*/

package org.apache.river.api.security;

import org.apache.river.impl.Messages;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.river.api.net.Uri;
import org.apache.river.api.security.DefaultPolicyScanner.GrantEntry;
import org.apache.river.api.security.DefaultPolicyScanner.KeystoreEntry;
import org.apache.river.api.security.DefaultPolicyScanner.PermissionEntry;
import org.apache.river.api.security.DefaultPolicyScanner.PrincipalEntry;
import org.apache.river.api.security.PolicyUtils.ExpansionFailedException;
import org.apache.river.thread.NamedThreadFactory;


/**
 * This is a basic loader of policy files. It delegates lexical analysis to 
 * a pluggable scanner and converts received tokens to a set of 
 * {@link org.apache.river.api.security.PermissionGrant PermissionGrant's}. 
 * For details of policy format, which should be identical to Sun's Java Policy
 * files see the 
 * {@link org.apache.river.api.security.ConcurrentPolicyFile default policy description}.
 * <br>
 * For ordinary uses, this class has just one public method <code>parse()</code>, 
 * which performs the main task.
 * Extensions of this parser may redefine specific operations separately, 
 * by overriding corresponding protected methods. 
 * <br>
 * This implementation is effectively thread-safe, as it has no field references 
 * to data being processed (that is, passes all the data as method parameters).
 * 
 * @see org.apache.river.api.security.ConcurrentPolicyFile
 * @see org.apache.river.api.security.DefaultPolicyScanner
 * @see org.apache.river.api.security.PermissionGrant
 */
public class DefaultPolicyParser implements PolicyParser {
    // Delay logging until after the policy and security manager are constructed.
    final ExecutorService logExec;
    // Pluggable scanner for a specific file format
    private final DefaultPolicyScanner scanner;

    /** 
     * Default constructor, 
     * {@link org.apache.river.api.security.DefaultPolicyScanner DefaultPolicyScanner} 
     * is used. 
     */
    public DefaultPolicyParser() {
	this(new DefaultPolicyScanner());
    }

    /** 
     * Extension constructor for plugging-in custom scanner.
     */
    DefaultPolicyParser(DefaultPolicyScanner s) {
	this.logExec = 
//		Executors.newScheduledThreadPool(0,
		new ThreadPoolExecutor(0, 1, 1L, TimeUnit.SECONDS,
		    new LinkedBlockingQueue(),
		    new NamedThreadFactory("JGDMS Policy logger", true)
		);
        this.scanner = s;
    }

    /**
     * This is the main business method. It manages loading process as follows:
     * the associated scanner is used to parse the stream to a set of 
     * {@link org.apache.river.api.security.DefaultPolicyScanner.GrantEntry composite tokens},
     * then this set is iterated and each token is translated to a PermissionGrant.
     * Semantically invalid tokens are ignored, the same as void PermissionGrant's.
     * <br>
     * A policy file may refer to some KeyStore(s), and in this case the first
     * valid reference is initialized and used in processing tokens.   
     * 
     * @param location an URL of a policy file to be loaded
     * @param system system properties, used for property expansion
     * @return a collection of PermissionGrant objects, may be empty
     * @throws Exception IO error while reading location or file syntax error 
     */
    public Collection<PermissionGrant> parse(URL location, Properties system)
            throws Exception {
	log(Level.FINER, "\nDefaultPolicyParser::parse policy: " + location + "\n");
        boolean resolve = PolicyUtils.canExpandProperties();
        Reader r = new BufferedReader(new InputStreamReader(
                AccessController
                        .doPrivileged(new PolicyUtils.URLLoader(location)), "UTF-8"));

        Collection<GrantEntry> grantEntries = new HashSet<GrantEntry>();
        List<KeystoreEntry> keystores = new ArrayList<KeystoreEntry>();

        try {
            scanner.scanStream(r, grantEntries, keystores); // modifies keystores
        }
        finally {
            r.close();
        }

        //XXX KeyStore could be loaded lazily...
        KeyStore ks = initKeyStore(keystores, location, system, resolve);

        Collection<PermissionGrant> result = new HashSet<PermissionGrant>();
	for (DefaultPolicyScanner.GrantEntry ge : grantEntries) {
	    try {
		PermissionGrant pe = resolveGrant(ge, ks, system, resolve);
		if (!pe.isVoid()) {
		    result.add(pe);
		}
	    }
	    catch (Exception e) {
		if ( e instanceof SecurityException ) throw (SecurityException) e;
		// Bad policy syntax usually results in difficult to trace
		// problems, the sooner we fail, the sooner the problem
		// is evident, for example in an operating environment
		// with a misconfigured policy, it's better to fail 
		// immediately than to experience a problem later.
		log(Level.CONFIG, "security.1A9", new Object[]{ge}, e);
	    }
	}
        log(Level.FINEST, result.toString());
        return result;
    }

    /**
     * Translates GrantEntry token to PermissionGrant object. It goes step by step, 
     * trying to resolve each component of the GrantEntry:
     * <ul>
     * <li> If <code>codebase</code> is specified, expand it and construct an URL.
     * <li> If <code>signers</code> is specified, expand it and obtain 
     * corresponding Certificates.
     * <li> If <code>principals</code> collection is specified, iterate over it. 
     * For each PrincipalEntry, expand name and if no class specified, 
     * resolve actual X500Principal from a KeyStore certificate; otherwise keep it 
     * as UnresolvedPrincipal. 
     * <li> Iterate over <code>permissions</code> collection. For each PermissionEntry,
     * try to resolve (see method 
     * {@link #resolvePermission(DefaultPolicyScanner.PermissionEntry, DefaultPolicyScanner.GrantEntry, KeyStore, Properties, boolean) resolvePermission()}) 
     * a corresponding permission. If resolution failed, ignore the PermissionEntry. 
     * </ul>
     * In fact, property expansion in the steps above is conditional and is ruled by
     * the parameter <i>resolve</i>.  
     * <br>
     * Finally a new PermissionGrant is created, which associates the trinity 
     * of resolved URL, Certificates and Principals to a set of granted Permissions.
     * 
     * @param ge GrantEntry token to be resolved
     * @param ks KeyStore for resolving Certificates, may be <code>null</code> 
     * @param system system properties, used for property expansion 
     * @param resolve flag enabling/disabling property expansion
     * @return resolved PermissionGrant
     * @throws Exception if unable to resolve codebase, signers or principals 
     * of the GrantEntry
     * @see DefaultPolicyScanner.PrincipalEntry
     * @see DefaultPolicyScanner.PermissionEntry
     * @see org.apache.river.api.security.PolicyUtils
     */
    PermissionGrant resolveGrant(DefaultPolicyScanner.GrantEntry ge,
            KeyStore ks, Properties system, boolean resolve) throws Exception {
        if ( ge == null ) return null;
        List<String> codebases = new ArrayList<String>(8);
        Certificate[] signers = null;
        Set<Principal> principals = new HashSet<Principal>();
        Set<Permission> permissions = new HashSet<Permission>();
        String cb = ge.getCodebase(null);
        String signerString = ge.getSigners();
        if ( cb != null ) {
            if ( resolve ) {
                try {
                    Collection<String> cbstr = expandURLs(cb, system);
                    Iterator<String> it = cbstr.iterator();
                    while (it.hasNext()){
                        codebases.add(getURI(it.next()));
                    }
                } catch (ExpansionFailedException e) {
                    log(Level.CONFIG, "security.1A7", new Object[]{e.getMessage()});
                }
            } else {
                codebases.add(getURI(cb));
            }
        }
	String[] aliases = new String[0];
        if ( signerString != null) {
	    try {
		if (resolve) {
		    signerString = PolicyUtils.expand(signerString, system);
		}
	    } catch (ExpansionFailedException e){
		log(Level.CONFIG, "security.1A6", new Object[]{e.getMessage()});
	    }
            
	    StringTokenizer snt = new StringTokenizer(signerString, ",");
	    List<String> alias = new ArrayList<String>(snt.countTokens());
	    while (snt.hasMoreTokens()){
		alias.add(snt.nextToken().trim());
	    }
	    aliases = alias.toArray(new String[alias.size()]);
	    signers = resolveSigners(ks, aliases);
        }
        if (ge.getPrincipals(null) != null) {
            String principalName;
            String principalClass;
	    for (PrincipalEntry pe : ge.getPrincipals(system)) {
		principalName = pe.getName();
		principalClass = pe.getKlass();
		try {
		    if (resolve) {
			principalName = PolicyUtils.expand(principalName, system);
		    }
		} catch (ExpansionFailedException e){
		    log(Level.CONFIG, "security.1A4", new Object[]{e.getMessage()});
		}
		if (principalClass == null) {
		    principals.add(getPrincipalByAlias(ks, principalName));
		} else {
		    principals.add(new UnresolvedPrincipal(principalClass, principalName));
		}
	    }
        }
        Collection<PermissionEntry> pec = ge.getPermissions();
        if (pec != null) {
            Iterator<PermissionEntry> iter = pec.iterator();
            while ( iter.hasNext()) {
                DefaultPolicyScanner.PermissionEntry pe = iter.next();
                try {
                    permissions.add(resolvePermission(pe, ge, ks, system, resolve));
                } catch (ExpansionFailedException e){
		    log(Level.CONFIG, "security.1A5", new Object[]{pe.toString(),e.getMessage()});
		} catch (Exception e) {
                    if ( e instanceof SecurityException ) throw (SecurityException) e;
                    log(Level.CONFIG, "security.1A5", new Object[]{pe.toString(),e.getMessage()});
                }
            }
        }
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        Iterator<String> iter = codebases.iterator();
        while (iter.hasNext()){
            pgb.uri(iter.next());
        }
	
        return pgb
            .certificates(signers, aliases)
            .principals(principals.toArray(new Principal[principals.size()]))
            .permissions(permissions.toArray(new Permission[permissions.size()]))
            .context(PermissionGrantBuilder.URI)
            .build();
    }
    
    String getURI(String uriString) throws MalformedURLException, URISyntaxException{
        // We do this to support windows, this is to ensure that path
        // capitalisation is correct and illegal strings are escaped correctly.
        if (uriString == null) return null;
        return Uri.fixWindowsURI(uriString);
    }
    
    Segment segment(String s, Properties p) throws ExpansionFailedException{
        final String ARRAY_START_MARK = "${{";
        final String ARRAY_END_MARK = "}}";
        final String ARRAY_SEPARATOR = p.getProperty("path.separator");
        final String START_MARK = "${"; //$NON-NLS-1$
        final String END_MARK = "}"; //$NON-NLS-1$
        Segment primary = new Segment(s, null);
        primary.divideAndReplace(ARRAY_START_MARK, ARRAY_END_MARK,
                ARRAY_SEPARATOR, p);
        primary.divideAndReplace(START_MARK, END_MARK, null, p);
        // Repeat twice for nested properties
        primary.divideAndReplace(START_MARK, END_MARK, null, p);
        primary.divideAndReplace(START_MARK, END_MARK, null, p);
        return primary;
    }
    
    Collection<String> expandURLs(String s, Properties p) throws ExpansionFailedException{
        Segment seg = segment(s,p);
        Collection<String> urls = new ArrayList<String>();
        while ( seg.hasNext() ){
//            urls.add(seg.next().replace(File.separatorChar, '/'));
            urls.add(seg.next());
        }
        return urls;
    }   

    /**
     * Translates PermissionEntry token to Permission object.
     * First, it performs general expansion for non-null <code>name</code> and
     * properties expansion for non-null <code>name</code>, <code>action</code> 
     * and <code>signers</code>.
     * Then, it obtains signing Certificates(if any), tries to find a class specified by 
     * <code>klass</code> name and instantiate a corresponding permission object.
     * If class is not found or it is signed improperly, returns UnresolvedPermission.
     *
     * @param pe PermissionEntry token to be resolved
     * @param ge parental GrantEntry of the PermissionEntry 
     * @param ks KeyStore for resolving Certificates, may be <code>null</code>
     * @param system system properties, used for property expansion
     * @param resolve flag enabling/disabling property expansion
     * @return resolved Permission object, either of concrete class or UnresolvedPermission
     * @throws Exception if failed to expand properties, 
     * or to get a Certificate, 
     * or to newBuilder an instance of a successfully found class 
     */
    Permission resolvePermission(
            DefaultPolicyScanner.PermissionEntry pe,
            DefaultPolicyScanner.GrantEntry ge, KeyStore ks, Properties system,
            boolean resolve) throws Exception {
        String className = pe.getKlass(), name=pe.getName(), 
                actions=pe.getActions(), signer=pe.getSigners();
        if (name != null) {
            name = PolicyUtils.expandGeneral(name, new PermissionExpander(ge, ks));
        }
        if (resolve) {
            if (name != null) {
                name = PolicyUtils.expand(name, system);
            }
            if (actions != null) {
                actions = PolicyUtils.expand(actions, system);
            }
            if (signer != null) {
                signer = PolicyUtils.expand(signer, system);
            }
        }
        Certificate[] signers = (signer == null) ? null : resolveSigners(
                ks, signer);
        try {
            Class<?> klass = Class.forName(className);
            if (PolicyUtils.matchSubset(signers, klass.getSigners())) {
                return PolicyUtils.instantiatePermission(klass, name, actions);
            }
        }
        catch (ClassNotFoundException cnfe) {}
        //maybe properly signed class will be loaded later
        return new UnresolvedPermission(className, name, actions, signers);
    }

    /** 
     * Specific handler for expanding <i>self</i> and <i>alias</i> protocols. 
     */
    class PermissionExpander implements PolicyUtils.GeneralExpansionHandler {

        // Store KeyStore
        private final KeyStore ks;

        // Store GrantEntry
        private final DefaultPolicyScanner.GrantEntry ge;

        /** 
         * Combined setter of all required fields. 
         */
        PermissionExpander(DefaultPolicyScanner.GrantEntry ge,
                KeyStore ks) {
            this.ge = ge;
            this.ks = ks;
        }

        /**
         * Resolves the following protocols:
         * <dl>
         * <dt>self
         * <dd>Denotes substitution to a principal information of the parental 
         * GrantEntry. Returns a space-separated list of resolved Principals 
         * (including wildcarded), formatting each as <b>class &quot;name&quot;</b>.
         * If parental GrantEntry has no Principals, throws ExpansionFailedException.
         * <dt>alias:<i>name</i>
         * <dd>Denotes substitution of a KeyStore alias. Namely, if a KeyStore has 
         * an X.509 certificate associated with the specified name, then returns 
         * <b>javax.security.auth.x500.X500Principal &quot;<i>DN</i>&quot;</b> string, 
         * where <i>DN</i> is a certificate's subject distinguished name.  
         * </dl>
         * @throws ExpansionFailedException - if protocol is other than 
         * <i>self</i> or <i>alias</i>, or if data resolution failed 
         */
        public String resolve(String protocol, String data)
                throws PolicyUtils.ExpansionFailedException {

            if ("self".equals(protocol)) { //$NON-NLS-1$
                //need expanding to list of principals in grant clause 
                if (ge.getPrincipals(null) != null && !ge.getPrincipals(null).isEmpty()) {
                    StringBuilder sb = new StringBuilder();
		    for (DefaultPolicyScanner.PrincipalEntry pr : ge.getPrincipals(null)) {
			if (pr.getKlass() == null) {
			    // aliased X500Principal
			    try {
				sb.append(pc2str(getPrincipalByAlias(ks, pr.getName())));
			    }
			    catch (KeyStoreException e) {
				throw new PolicyUtils.ExpansionFailedException(
					Messages.getString("security.143", pr.getName()), e); //$NON-NLS-1$
			    } catch (CertificateException e) {
				throw new PolicyUtils.ExpansionFailedException(
					Messages.getString("security.143", pr.getName()), e); //$NON-NLS-1$
			    }
			} else {
			    sb.append(pr.getKlass()).append(" \"").append(pr.getName()) //$NON-NLS-1$
				    .append("\" "); //$NON-NLS-1$
			}
		    }
                    return sb.toString();
                } else {
                    throw new PolicyUtils.ExpansionFailedException(
                            Messages.getString("security.144")); //$NON-NLS-1$
                }
            }
            if ("alias".equals(protocol)) { //$NON-NLS-1$
                try {
                    return pc2str(getPrincipalByAlias(ks, data));
                }
                catch (KeyStoreException e) {
                    throw new PolicyUtils.ExpansionFailedException(
                            Messages.getString("security.143", data), e); //$NON-NLS-1$
                } catch (CertificateException e) {
		    throw new PolicyUtils.ExpansionFailedException(
			    Messages.getString("security.143", data), e); //$NON-NLS-1$
		}
            }
            throw new PolicyUtils.ExpansionFailedException(
                    Messages.getString("security.145", protocol)); //$NON-NLS-1$
        }

        // Formats a string describing the passed Principal. 
        private String pc2str(Principal pc) {
            String klass = pc.getClass().getName();
            String name = pc.getName();
            StringBuilder sb = new StringBuilder(klass.length() + name.length()
                    + 5);
            return sb.append(klass).append(" \"").append(name).append("\"") //$NON-NLS-1$ //$NON-NLS-2$
                    .toString();
        }
    }

    /**
     * Takes a comma-separated list of aliases and obtains corresponding 
     * certificates.
     * @param ks KeyStore for resolving Certificates, may be <code>null</code> 
     * @param signers comma-separated list of certificate aliases, 
     * must be not <code>null</code>
     * @return an array of signing Certificates
     * @throws Exception if KeyStore is <code>null</code> 
     * or if it failed to provide a certificate  
     */
    Certificate[] resolveSigners(KeyStore ks, String signers)
            throws Exception {
        if (ks == null) {
            throw new KeyStoreException(Messages.getString("security.146", //$NON-NLS-1$
                    signers));
        }

        Collection<Certificate> certs = new ArrayList<Certificate>();
        StringTokenizer snt = new StringTokenizer(signers, ","); //$NON-NLS-1$
        while (snt.hasMoreTokens()) {
            //XXX cache found certs ??
            certs.add(ks.getCertificate(snt.nextToken().trim()));
        }
        return certs.toArray(new Certificate[certs.size()]);
    }
    
    Certificate[] resolveSigners(KeyStore ks, String[] signers) throws KeyStoreException{
	if (signers == null || signers.length == 0) return new Certificate[0];
	Collection<Certificate> certs = new ArrayList<Certificate>(signers.length);
	for (int i=0,l=signers.length; i<l; i++){
	    certs.add(ks.getCertificate(signers[i]));
	}
	return certs.toArray(new Certificate[certs.size()]);
    }

    /**
     * Returns a subject's X500Principal of an X509Certificate, 
     * which is associated with the specified keystore alias. 
     * @param ks KeyStore for resolving Certificate, may be <code>null</code>
     * @param alias alias to a certificate
     * @return X500Principal with a subject distinguished name
     * @throws KeyStoreException if KeyStore is <code>null</code> 
     * or if it failed to provide a certificate
     * @throws CertificateException if found certificate is not 
     * an X509Certificate 
     */
    Principal getPrincipalByAlias(KeyStore ks, String alias)
            throws KeyStoreException, CertificateException {

        if (ks == null) {
            throw new KeyStoreException(
                    Messages.getString("security.147", alias)); //$NON-NLS-1$
        }
        //XXX cache found certs ??
        Certificate x509 = ks.getCertificate(alias);
        if (x509 instanceof X509Certificate) {
            return ((X509Certificate) x509).getSubjectX500Principal();
        } else {
            throw new CertificateException(Messages.getString("security.148", //$NON-NLS-1$
                    alias, x509));
        }
    }

    /**
     * Returns the first successfully loaded KeyStore, from the specified list of
     * possible locations. This method iterates over the list of KeystoreEntries;
     * for each entry expands <code>url</code> and <code>type</code>,
     * tries to construct instances of specified URL and KeyStore and to load 
     * the keystore. If it is loaded, returns the keystore, otherwise proceeds to 
     * the next KeystoreEntry. 
     * <br>
     * <b>Note:</b> an url may be relative to the policy file location or absolute.
     * @param keystores list of available KeystoreEntries
     * @param base the policy file location
     * @param system system properties, used for property expansion
     * @param resolve flag enabling/disabling property expansion
     * @return the first successfully loaded KeyStore or <code>null</code>
     */
    KeyStore initKeyStore(List<KeystoreEntry>keystores,
            URL base, Properties system, boolean resolve) {
	for (KeystoreEntry ke : keystores) {
	    try {
		String url = ke.getUrl(), type = ke.getType();
		if (resolve) {
		    url = PolicyUtils.expandURL(url, system);
		    if (type != null) {
			type = PolicyUtils.expand(type, system);
		    }
		}
		if (type == null || type.length() == 0) {
		    type = KeyStore.getDefaultType();
		}
		KeyStore ks = KeyStore.getInstance(type);
		URL location = new URL(base, url);
		InputStream is = AccessController
			.doPrivileged(new PolicyUtils.URLLoader(location));
		try {
		    ks.load(is, null);
		}
		finally {
		    is.close();
		}
		return ks;
	    }
	    catch (ExpansionFailedException e) {
		log(Level.CONFIG, "security.8A", e);
	    } catch (KeyStoreException e) {
		log(Level.CONFIG, "security.8A", e);
	    } catch (PrivilegedActionException e) {
		log(Level.CONFIG, "security.8A", e);
	    } catch (IOException e) {
		log(Level.CONFIG, "security.8A", e);
	    } catch (NoSuchAlgorithmException e) {
		log(Level.CONFIG, "security.8A", e);
	    } catch (CertificateException e) {
		log(Level.CONFIG, "security.8A", e);
	    }
	}
        return null;
    }
    
    void log(Level level, String message){
        log(level, message, null, null);
    }
    
    void log(Level level, String message, Throwable thrown){
        log(level, message, null, thrown);
    }
    
    void log(Level level, String message, Object[] parameters){
        log(level, message, parameters, null);
    }
    
    void log(final Level level,
		    final String message,
		    final Object[] parameters,
		    final Throwable thrown)
    {
	logExec.submit(
	    new Runnable(){
		public void run() {
                    try {
		    Logger logger = Logger.getLogger("net.jini.security.policy");
		    if (logger.isLoggable(level)){
			logger.log(
			    level,
			    Messages.getString(message, parameters),
			    thrown);
                    }
                    } catch (SecurityException e){
                        // If there's a syntax error in the policy file that
                        // grants permission to access the logger, there will
                        // be no information for debugging.
                        System.err.println(Messages.getString(message, parameters));
                        if (thrown != null) thrown.printStackTrace(System.err);
                    }
                }
	    }
	);
    }
    
}
