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
  * @version $Revision$
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
import net.jini.security.policy.PolicyInitializationException;


/**
 * <p>
 * Concurrent Policy implementation based on policy configuration files,
 * it is intended to provide concurrent implies() for greatly improved
 * throughput.  Caching limits scalability and consumes shared memory,
 * so no cache exists.
 * </p><p>
 * Set the following system properties to use this Policy instead of the
 * built in Java sun.security.provider.PolicyFile:
 * </p>
 *  <pre>
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * </pre>
 * 
 * This
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
 * The <i>grant </i> clause associates a CodeSource (consisting of an URL and a
 * set of certificates) of some executable code with a set of Permissions which
 * should be granted to the code. So, the CodeSource is defined by values of
 * <i>CodeBase </i> and <i>SignedBy </i> fields. The <i>CodeBase </i> value must
 * be in URL format, while <i>SignedBy </i> value is a (comma-separated list of)
 * alias(es) to keystore certificates. These fields can be omitted to denote any
 * codebase and any signers (including case of unsigned code), respectively.
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
 * <i>javax.security.auth.x500.X500Principal &quot; <i>DN </i>&quot; </i>
 * string, where <i>DN </i> is a certificate's subject distinguished name.
 * </dl>
 * <br>
 * <br>
 * This implementation is thread-safe and scalable.
 * @author Peter Firmstone.
 * @since 2.2.1
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
    
    private static final Guard guard = new SecurityPermission("getPolicy");
    
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
     */
    public ConcurrentPolicyFile() throws PolicyInitializationException {
        this(new DefaultPolicyParser(), new PermissionComparator());
    }

    /**
     * Extension constructor for plugging-in a custom parser.
     * @param dpr
     * @param comp Comparator to compare permissions. 
     */
    protected ConcurrentPolicyFile(PolicyParser dpr, Comparator<Permission> comp) throws PolicyInitializationException {
        guard.checkGuard(null);
        parser = dpr;
        comparator = comp;
        /*
         * The bootstrap policy makes implies decisions until this constructor
         * has returned.  We don't need to lock.
         */
        try {
            // Bug 4911907, do we need to do anything more?
            // The permissions for this domain must be retrieved before
            // construction is complete and this policy takes over.
            initialize(); // Instantiates myPermissions.
        } catch (SecurityException e){
            throw e;
        } catch (Exception e){
            throw new PolicyInitializationException("PolicyInitialization failed", e);
        }
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
        NavigableSet<Permission> perms = new TreeSet<Permission>(comparator);
        PermissionGrant [] grantRefCopy = grantArray;
        int l = grantRefCopy.length;
        for ( int j =0; j < l; j++ ){
            PermissionGrant ge = grantRefCopy[j];
            if (ge.implies(pd)){
                if (ge.isPrivileged()){// Don't stuff around finish early if you can.
                    PermissionCollection pc = new Permissions();
                    pc.add(new AllPermission());
                    return pc;
                }
                Collection<Permission> c = ge.getPermissions();
                Iterator<Permission> i = c.iterator();
                while (i.hasNext()){
                    Permission p = i.next();
                    perms.add(p);
                }
            }
        }
        // Don't forget to merge the static Permissions.
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
        for ( int j =0; j < l; j++ ){
            PermissionGrant ge = grantRefCopy[j];
            if (ge.implies(domain)){
                if (ge.isPrivileged()) return true; // Don't stuff around finish early if you can.
                Collection<Permission> c = ge.getPermissions();
                Iterator<Permission> i = c.iterator();
                while (i.hasNext()){
                    Permission p = i.next();
                    // Don't make it larger than necessary.
                    if (klass.isInstance(permission) || permission instanceof UnresolvedPermission){
                        perms.add(p);
                    }
                }
            }
        }
        // Don't forget to merge the static Permissions.
        PermissionCollection staticPC = null;
        if (domain != null) {
            staticPC =domain.getPermissions();
            if (staticPC != null){
                Enumeration<Permission> e = staticPC.elements();
                while (e.hasMoreElements()){
                    Permission p = e.nextElement();
                    // return early if possible.
                    if (p instanceof AllPermission ) return true;
                    // Don't make it larger than necessary, but don't worry about duplicates either.
                    if (klass.isInstance(permission) || permission instanceof UnresolvedPermission){
                        perms.add(p);
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
            initialize();
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    private void initialize() throws Exception{
        try {
            Collection<PermissionGrant> fresh = AccessController.doPrivileged( 
                new PrivilegedExceptionAction<Collection<PermissionGrant>>(){
                    public Collection<PermissionGrant> run() throws SecurityException {
                        Collection<PermissionGrant> fresh = new ArrayList<PermissionGrant>(120);
                        Properties system = System.getProperties();
                        system.setProperty("/", File.separator); //$NON-NLS-1$
                        URL[] policyLocations = PolicyUtils.getPolicyURLs(system,
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
                            } catch (Exception e){
                                // It's best to let a SecurityException bubble up
                                // in case there is a problem with our policy configuration
                                // or implementation.
                                if ( e instanceof SecurityException ) {
                                    e.printStackTrace(System.out);
                                    throw (SecurityException) e;
                                }
                                // ignore.
                            }
                        }
                        return fresh;
                    }
                }
            );
            // Volatile reference, publish after mutation complete.
            grantArray = fresh.toArray(new PermissionGrant[fresh.size()]);
            myPermissions = getPermissions(myDomain);
        }catch (PrivilegedActionException e){
            Throwable t = e.getCause();
            if ( t instanceof Exception ) throw (Exception) t;
            throw e;
        }
    }

    public Collection<PermissionGrant> getPermissionGrants(ProtectionDomain pd) {
        PermissionGrant [] grants = grantArray; // copy volatile reference target.
        int l = grants.length;
        List<PermissionGrant> applicable = new LinkedList<PermissionGrant>();
        for (int i =0; i < l; i++){
            if (grants[i].implies(pd)){
                applicable.add(grants[i]);
            }
        }
        // Merge any static permissions.
        PermissionCollection pc = pd != null ? pd.getPermissions() : null;
        if (pc != null){
            PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
            pgb.setDomain(new WeakReference<ProtectionDomain>(pd));
            pgb.context(PermissionGrantBuilder.PROTECTIONDOMAIN);
            Collection<Permission> perms = new LinkedList<Permission>();
            Enumeration<Permission> en = pc.elements();
            while (en.hasMoreElements()){
                perms.add(en.nextElement());
            }
            pgb.permissions(perms.toArray(new Permission[perms.size()]));
            applicable.add(pgb.build());
                        }
        return applicable;
    }
    
//    public Collection<PermissionGrant> getPermissionGrants(boolean recursive) {
//        PermissionGrant [] grants = grantArray; // copy volatile reference target.
//        return new LinkedList<PermissionGrant>(Arrays.asList(grants));
//    }

}
