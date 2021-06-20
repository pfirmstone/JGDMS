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
  * Default Policy implementation taken from Apache Harmony, refactored for
  * concurrency.
  * 
  * @author Alexey V. Varlamov
  * @author Peter Firmstone
  * @since 3.0.0
  */

package org.apache.river.api.security;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Guard;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import net.jini.security.policy.PolicyInitializationException;


/**
 * <p>
 * Concurrent Policy implementation based on policy configuration URL's,
 * it is intended to provide concurrent implies() for greatly improved
 * throughput.  Caching limits scalability and consumes shared memory,
 * so no cache exists.
 * </p><p>
 * By default all River Policy implementations now utilise ConcurrentPolicyFile.
 * </p>
 * The default PolicyParser
 * implementation recognises text files, consisting of clauses with the
 * following syntax:
 * 
 * <pre>
 * keystore &quot;some_keystore_url&quot; [, &quot;keystore_type&quot;];
 * </pre>
 <pre>
 * grant [SignedBy &quot;signer_names&quot;] [, CodeBase &quot;URL&quot;]
 *  [, Principal [principal_class_name] &quot;principal_name&quot;]
 *  [, Principal [principal_class_name] &quot;principal_name&quot;] ... {
 *  permission permission_class_name [ &quot;target_name&quot; ] [, &quot;action&quot;] 
 *  [, SignedBy &quot;signer_names&quot;];
 *  permission ...
 *  };
 *  
 * </pre>
 * 
 * The <i>keystore </i> clause specifies reference to a keystore, which is a
 * database of private keys and their associated digital certificates. The
 * keystore is used to look up the certificates of signers specified in the
 * <i>grant </i> entries of the file. The policy file can contain any number of
 * <i>keystore </i> entries which can appear at any ordinal position. However,
 * only the first successfully loaded keystore is used, others are ignored. The
 * keystore must be specified if some grant clause refers to a certificate's
 * alias. <br>
 * The <i>grant </i> clause associates a CodeSource (consisting of a URI and a
 * set of certificates) of some executable code with a set of Permissions which
 * should be granted to the code. So, the CodeSource is defined by values of
 * <i>CodeBase </i> and <i>SignedBy </i> fields. The <i>CodeBase </i> value must
 * be in URI format, while <i>SignedBy </i> value is a (comma-separated list of)
 * alias(es) to keystore certificates. These fields can be omitted to denote any
 * codebase and any signers (including case of unsigned code), respectively.
 * <br>
 * URI is case sensitive and does not perform DNS lookup or reverse lookup
 * to resolve a URI IP address to determine if a <i>grant</i> clause implies 
 * CodeSource URL, instead CodeSource URL objects are converted to URI instances
 * during implies determination.  If grants are made to an IP address, 
 * these will not imply a DNS name address URI and vice versa.
 * <br>
 * "FILE:" URI's are case sensitive, although some file systems allow access
 * to the same file using upper or lower case, one URI will not imply another
 * with different case, even if both URI refer to the same file, for this 
 * reason case sensitive and case preserving file systems are recommended.  
 * The PolicyParser must escape any excluded characters according to RFC2396
 * and in addition it must convert all MS Windows platform drive letters 
 * to upper case.
 * <br>
 * Also, the code may be required to be executed on behalf of some Principals
 * (in other words, code's ProtectionDomain must have the array of Principals
 * associated) in order to possess the Permissions. This fact is indicated by
 * specifying one or more <i>Principal </i> fields in the <i>grant </i> clause.
 * Each Principal is specified as class/name pair; name and class can be either
 * concrete value or wildcard <i>* </i>. As a special case, the class value may
 * be omitted and then the name is treated as an alias to X.509 Certificate, and
 * the Principal is assumed to be javax.security.auth.x500.X500Principal with a
 * name of subject's distinguished name from the certificate. <br>
 * The order between the <i>CodeBase </i>, <i>SignedBy </i>, and <i>Principal
 * </i> fields does not matter. The policy file can contain any number of grant
 * clauses. <br>
 * Each <i>grant </i> clause must contain one or more <i>permission </i> entry.
 * The permission entry consist of a fully qualified class name along with
 * optional <i>name </i>, <i>actions </i> and <i>signedby </i> values. Name and
 * actions are arguments to the corresponding constructor of the permission
 * class. SignedBy value represents the keystore alias(es) to certificate(s)
 * used to sign the permission class. That is, this permission entry is
 * effective (i.e., access control permission will be granted based on this
 * entry) only if the bytecode implementation of permission class is verified to
 * be correctly signed by the said alias(es). <br>
 * <br>
 * The policy content may be parameterized via property expansion. Namely,
 * expressions like <i>${key} </i> are replaced by values of corresponding
 * system properties. Also, the special <i>slash </i> key (i.e. ${/}) is
 * supported, it is a shortcut to &quot;file.separator&quot; key. Property
 * expansion is performed anywhere a double quoted string is allowed in the
 * policy file. However, this feature is controlled by security properties and
 * should be turned on by setting &quot;policy.expandProperties&quot; property
 * to <i>true </i>. <br>
 * If property expansion fails (due to a missing key), a corresponding entry is
 * ignored. For fields of <i>keystore </i> and <i>grant </i> clauses, the whole
 * clause is ignored, and for <i>permission </i> entry, only that entry is
 * ignored. <br>
 * <br>
 * The policy also supports generalized expansion in permissions names, of
 * expressions like <i>${{protocol:data}} </i>. Currently the following
 * protocols supported:
 * <dl>
 * <dt>self
 * <dd>Denotes substitution to a principal information of the parental Grant
 * entry. Replaced by a space-separated list of resolved Principals (including
 * wildcarded), each formatted as <i>class &quot;name&quot; </i>. If parental
 * Grant entry has no Principals, the permission is ignored.
 * <dt>alias: <i>name </i>
 * <dd>Denotes substitution of a KeyStore alias. Namely, if a KeyStore has an
 * X.509 certificate associated with the specified name, then replaced by
 * <code>javax.security.auth.x500.X500Principal &quot; <i>DN </i>&quot; </code>
 * string, where <i>DN </i> is a certificate's subject distinguished name.
 * </dl>
 * <br>
 * If there is sufficient interest, we can implement a DENY clause, 
 * in this case DENY cannot apply to GRANT clauses that contain
 * {@link java.security.AllPermission}, the domains to which a DENY clause
 * would apply will be a less privileged domain.  For example a user could be
 * granted SocketPermission("*", "connect"), while a DENY clause might
 * list specific SocketPermission domains that are disallowed, where a DENY 
 * clause has precedence over all GRANT clause Permissions except for AllPermission.
 * <br>
 * This implementation is thread-safe and scalable.
 */

public class ConcurrentPolicyFile extends Policy implements ScalableNestedPolicy {

    /**
     * System property for dynamically added policy location.
     */
    private static final String JAVA_SECURITY_POLICY = "java.security.policy"; //$NON-NLS-1$

    /**
     * Prefix for numbered Policy locations specified in security.properties.
     */
    private static final String POLICY_URL_PREFIX = "policy.url."; //$NON-NLS-1$
    
    private static final Permission ALL_PERMISSION = new AllPermission();
    
    // Reference must be defensively copied before access, once published, never mutated.
    private volatile PermissionGrant [] grantArray;
    
    // A specific parser for a particular policy file format.
    private final PolicyParser parser;
    
    // If created with defined policy.
    private final URL[] policies;
    
    private static final Guard guard = new SecurityPermission("createPolicy.JiniPolicy");
    
    private static final ProtectionDomain myDomain = 
        AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>(){
            
            public ProtectionDomain run() {
                return ConcurrentPolicyFile.class.getProtectionDomain();
            }
        });
    
    private final Comparator<Permission> comparator;
    
    // reference must be defensively copied before access, once published, never mutated.
    private volatile PermissionCollection myPermissions;
    
    /**
     * Default constructor, equivalent to
     * <code>ConcurrentPolicyFile(new DefaultPolicyParser())</code>.
     * @throws net.jini.security.policy.PolicyInitializationException in instantiation unsuccessful
     */
    public ConcurrentPolicyFile() throws PolicyInitializationException {
        this(new DefaultPolicyParser(), new PermissionComparator());
    }
    
    /**
     * 
     * @param policies
     * @throws PolicyInitializationException 
     */
    public ConcurrentPolicyFile(URL[] policies) throws PolicyInitializationException{
	this(new DefaultPolicyParser(), new PermissionComparator(), policies);
    }
    
    /** All exceptions are thrown by this method during construction,
     * to avoid a finalizer attack from an overriding class attempting
     * to avoid the construction guard, catching the exception then calling
     * refresh from the finalizer to instantiate a complete policy.
     * 
     * This method is called during construction prior to the implicit
     * super() call to Object and prior to any final fields being assigned.
     */
    private static PermissionGrant [] readPolicyPermissionGrants(PolicyParser parser, URL[] policies) throws PolicyInitializationException{
        guard.checkGuard(null);
        try {
            // Bug 4911907, do we need to do anything more?
            // The permissions for this domain must be retrieved before
            // construction is complete and this policy takes over.
            return readPoliciesNoCheckGuard(parser, policies); // Instantiates myPermissions.
        } catch (SecurityException e){
            throw e;
        } catch (Exception e){
            throw new PolicyInitializationException("PolicyInitialization failed", e);
        }
    }
    
    /**
     * 
     * @param dpr
     * @param comp
     * @throws PolicyInitializationException 
     */
    protected ConcurrentPolicyFile(PolicyParser dpr, Comparator<Permission> comp) 
            throws PolicyInitializationException {
        this (dpr, null, comp, readPolicyPermissionGrants(dpr, null));
    }
    
    /**
     * 
     * @param dpr
     * @param comp
     * @param policyLocations
     * @throws PolicyInitializationException 
     */
    protected ConcurrentPolicyFile(PolicyParser dpr, Comparator<Permission> comp, URL[] policyLocations) 
            throws PolicyInitializationException {
        this (dpr, policyLocations, comp, readPolicyPermissionGrants(dpr, policyLocations));
    }

    /**
     * Constructor to allow for custom policy providers, for example a database
     * policy provider, can make administration simpler than traditional
     * policy files.
     * 
     * @param dpr
     * @param comp Comparator to compare permissions. 
     */
    private ConcurrentPolicyFile(PolicyParser dpr,
				URL[] policies, 
				Comparator<Permission> comp,
				PermissionGrant[] grants) 
            throws PolicyInitializationException {
        /*
         * The bootstrap policy makes implies decisions until this constructor
         * has returned.
         */
        parser = dpr;
        comparator = comp;
        grantArray = grants;
        myPermissions = getP(myDomain);
	this.policies = policies == null ? null : policies.clone();
    }
    
    private PermissionCollection convert(NavigableSet<Permission> permissions){
        PermissionCollection pc = new Permissions();
        // The descending iterator is for SocketPermission.
        Iterator<Permission> it = permissions.descendingIterator();
        while (it.hasNext()) {
            pc.add(it.next());
        }
        return pc;
    }

    /**
     * Returns collection of permissions allowed for the domain 
     * according to the policy. The evaluated characteristics of the 
     * domain are it's codesource and principals; they are assumed
     * to be <code>null</code> if the domain is <code>null</code>.
     * 
     * Each PermissionCollection returned is a unique instance.
     * 
     * @param pd ProtectionDomain
     * @see ProtectionDomain
     */
    @Override
    public PermissionCollection getPermissions(ProtectionDomain pd) {
        return getP(pd);
    }
    
    private PermissionCollection getP(ProtectionDomain pd) {
        NavigableSet<Permission> perms = new TreeSet<Permission>(comparator);
        PermissionGrant [] grantRefCopy = grantArray;
        int l = grantRefCopy.length;
        /* Check only privileged grants first, this allows privileged domains
         * to avoid infinite recursion when they implement PermissionGrant
         * and perform privileged actions during an implies call.
         * 
         * It also ensures privileged checks are are fast.
         */ 
        for ( int j = 0; j < l; j++){
            PermissionGrant ge = grantRefCopy[j];
            if (ge.isPrivileged()){
                if (ge.implies(pd)){
                    PermissionCollection pc = new Permissions();
                    pc.add(ALL_PERMISSION);
                    return pc;
                }
            }
        }
        /* Merge  static Permissions and check for AllPermission */
        PermissionCollection staticPC = null;
        if (pd != null) {
            staticPC = pd.getPermissions();
            if (staticPC != null){
                Enumeration<Permission> e = staticPC.elements();
                while (e.hasMoreElements()){
                    Permission p = e.nextElement();
                    if (p instanceof AllPermission) {
                        PermissionCollection pc = new Permissions();
                        pc.add(p);
                        return pc;
                    }
                    perms.add(p);
                }
            }
        }       
        /* Now find less privileged cases */
        for ( int j =0; j < l; j++ ){
            PermissionGrant ge = grantRefCopy[j];
            if (!ge.isPrivileged()){
                if (ge.implies(pd)){
                    Collection<Permission> c = ge.getPermissions();
                    Iterator<Permission> i = c.iterator();
                    while (i.hasNext()){
                        Permission p = i.next();
                        perms.add(p);
                    }
                }
            }
        }
        
        return convert(perms);
    }

    /**
     * This returns a java.security.Permissions collection, which allows
     * ProtectionDomain to optimise for the AllPermission case, which avoids
     * unnecessarily consulting the policy.
     * 
     * This implementation only returns a Permissions that contains
     * AllPermission for privileged domains, otherwise it 
     * calls super.getPermissions(cs).
     * 
     * @param cs CodeSource
     * @see CodeSource
     */
    @Override
    public PermissionCollection getPermissions(CodeSource cs) {
        if (cs == null) throw new NullPointerException("CodeSource cannot be null");
        // for ProtectionDomain AllPermission optimisation.
        /* Infinite recursion is not an issue for CodeSource */
        PermissionGrant [] grantRefCopy = grantArray;
        int l = grantRefCopy.length;        
        for ( int j =0; j < l; j++ ){
            PermissionGrant ge = grantRefCopy[j];
            if (ge.implies(cs, null)){ // No Principal's
                if (ge.isPrivileged()){
                    PermissionCollection pc = new Permissions();
                    pc.add(ALL_PERMISSION);
                    return pc;
                }
            }
        }
        return super.getPermissions(cs);
    }
    
    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (permission == null) throw new NullPointerException("permission not allowed to be null");
        if (domain == myDomain) {
            PermissionCollection pc = myPermissions;
            return pc.implies(permission);
        }
        Class klass = permission.getClass();
        // Need to have a list of Permission's we can sort if permission is SocketPermission.
        NavigableSet<Permission> perms = new TreeSet<Permission>(comparator);
        PermissionGrant [] grantRefCopy = grantArray;
        int l = grantRefCopy.length;
        /* Check for privileged grants first to avoid recursion when 
         * privileged domains become involved in policy decisions */
        for (int j = 0; j < l; j++){
            PermissionGrant ge = grantRefCopy[j];
            if (ge.isPrivileged()){
                if (ge.implies(domain)){
                    return true;
                }
            }
        }
        /* Merge the static Permissions, check for Privileged */
        PermissionCollection staticPC = null;
        if (domain != null) {
            staticPC =domain.getPermissions();
            if (staticPC != null){
                Enumeration<Permission> e = staticPC.elements();
                while (e.hasMoreElements()){
                    Permission p = e.nextElement();
                    // return early if possible.
                    if (p instanceof AllPermission ) return true;
                    // Only add relevant permissions to minimise size.
                    if (klass.isInstance(permission) || permission instanceof UnresolvedPermission){
                        perms.add(p);
                    }
                }
            }
        }
        /* Check less privileged grants */
        for ( int j =0; j < l; j++ ){
            PermissionGrant ge = grantRefCopy[j];
            if (!ge.isPrivileged()){
                if (ge.implies(domain)){
                    Collection<Permission> c = ge.getPermissions();
                    Iterator<Permission> i = c.iterator();
                    while (i.hasNext()){
                        Permission p = i.next();
                        // Only add relevant permissions to minimise size.
                        if (klass.isInstance(permission) || permission instanceof UnresolvedPermission){
                            perms.add(p);
                        }
                    }
                }
            }
        }      
        return convert(perms).implies(permission);
    }

    /**
     * Gets fresh list of locations and tries to load all of them in sequence;
     * failed loads are ignored. After processing all locations, old policy
     * settings are discarded and new ones come into force. <br>
     * 
     * @see PolicyUtils#getPolicyURLs(Properties, String, String)
     */
    @Override
    public void refresh() {
        try {
            grantArray = readPoliciesNoCheckGuard(parser, policies == null ? null : policies.clone());
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    private static PermissionGrant [] readPoliciesNoCheckGuard(final PolicyParser parser, final URL[] policies) throws Exception{
        try {
            Collection<PermissionGrant> fresh = AccessController.doPrivileged(new PrivilegedExceptionAction<Collection<PermissionGrant>>(){
                    public Collection<PermissionGrant> run() throws SecurityException {
                        Collection<PermissionGrant> fresh = new ArrayList<PermissionGrant>(120);
                        Properties system = System.getProperties();
                        system.setProperty("/", File.separator); //$NON-NLS-1$
                        URL[] policyLocations = policies != null ? 
				policies : PolicyUtils.getPolicyURLs(system,
                                                          JAVA_SECURITY_POLICY,
                                                          POLICY_URL_PREFIX);
                        int l = policyLocations.length;
                        for (int i = 0; i < l; i++) {
                            //TODO debug log
//                                System.err.println("Parsing policy file: " + policyLocations[i]);
                            try {
                                Collection<PermissionGrant> pc = null;
                                pc = parser.parse(policyLocations[i], system);
                                fresh.addAll(pc);
                            } catch (Exception ex){
				if (ex instanceof PrivilegedActionException) 
				    ex =
					((PrivilegedActionException)ex).getException();
                                // It's best to let a SecurityException bubble up
                                // in case there is a problem with our policy configuration
                                // or implementation.
                                if ( ex instanceof SecurityException ) {
//                                    e.printStackTrace(System.out);
                                    throw (SecurityException) ex;
                                }
				if (parser instanceof DefaultPolicyParser){
				    ((DefaultPolicyParser) parser).log(
					Level.CONFIG, 
						"security.1A8",
						new Object[]{policyLocations[i], ex.getMessage()}
				    );
				}
                            }
                        }
                        return fresh;
                    }
                }
            );
            // Volatile reference, publish after mutation complete.
            return fresh.toArray(new PermissionGrant[fresh.size()]);
        }catch (PrivilegedActionException e){
            Throwable t = e.getCause();
            if ( t instanceof Exception ) throw (Exception) t;
            throw e;
        }
    }

    public List<PermissionGrant> getPermissionGrants(ProtectionDomain pd) {
        PermissionGrant [] grants = grantArray; // copy volatile reference target.
        int l = grants.length;
        List<PermissionGrant> applicable = new LinkedList<PermissionGrant>();
        /* First check for privileged grants */
        for (int i = 0; i < l; i++){
            if (grants[i].isPrivileged()){
                if (grants[i].implies(pd)){
                    applicable.add(grants[i]);
                    return applicable;
                }
                
            }
        }
        /* Merge static permissions and check for privileged */
        PermissionCollection pc = pd != null ? pd.getPermissions() : null;
        if (pc != null){
            PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
            pgb.setDomain(new WeakReference<ProtectionDomain>(pd));
            pgb.context(PermissionGrantBuilder.PROTECTIONDOMAIN);
            Collection<Permission> perms = new LinkedList<Permission>();
            Enumeration<Permission> en = pc.elements();
            while (en.hasMoreElements()){
                Permission p = en.nextElement();
                perms.add(p);
            }
            pgb.permissions(perms.toArray(new Permission[perms.size()]));
            PermissionGrant pg = pgb.build();
            applicable.add(pg);
            // Return now if privileged, to avoid infinite recursion.
            if (pg.isPrivileged()) return applicable;
        }
        /* Gather less privileged grants */
        for (int i =0; i < l; i++){
            if (!grants[i].isPrivileged()){
                if (grants[i].implies(pd)){
                    applicable.add(grants[i]);
                }
            }
        }
        
        return applicable;
    }
    
    public String toString(){
	String nl = "\n";
	StringBuilder b = new StringBuilder(256);
	b.append(super.toString()).append(nl);
	b.append("Policy file grants:\n");
	b.append(Arrays.asList(grantArray)).append(nl);
	return b.toString();
    }
}
