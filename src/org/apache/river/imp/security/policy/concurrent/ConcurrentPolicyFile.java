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

package org.apache.river.imp.security.policy.se;

import java.io.File;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import org.apache.river.imp.security.policy.util.DefaultPolicyParser;
import org.apache.river.imp.security.policy.util.PolicyEntry;
import org.apache.river.imp.security.policy.util.PolicyParser;
import org.apache.river.imp.security.policy.util.PolicyUtils;
import org.apache.river.imp.util.ConcurrentWeakIdentityMap;


/**
 * Concurrent Policy implementation based on policy configuration files,
 * it is intended to provide concurrent implies() for greatly improved
 * throughput at the expense of memory usage.
 * 
 * Set the following system properties to use this Policy instead of the
 * built in Java sun.security.provider.PolicyFile:
 *  
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * 
 * 
 * This
 * implementation recognizes text files, consisting of clauses with the
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
 * This implementation is thread-safe. The policy caches sets of calculated
 * permissions for the requested objects (ProtectionDomains and CodeSources) via
 * WeakHashMap; the cache is cleaned either explicitly during refresh()
 * invocation, or naturally by garbage-collecting the corresponding objects.
 * 
 * @see org.apache.harmony.security.PolicyUtils#getPolicyURLs(Properties, String,
 *      String)
 */

public class ConcurrentPolicyFile extends Policy {

    /**
     * System property for dynamically added policy location.
     */
    public static final String JAVA_SECURITY_POLICY = "java.security.policy"; //$NON-NLS-1$

    /**
     * Prefix for numbered Policy locations specified in security.properties.
     */
    public static final String POLICY_URL_PREFIX = "policy.url."; //$NON-NLS-1$

    // A set of PolicyEntries constituting this Policy.
    private final ReentrantReadWriteLock rwl;
    private final ReadLock rl;
    private final WriteLock wl;
    
    private Set<PolicyEntry> grants = new HashSet<PolicyEntry>(); // protected by rwl

    // Calculated Permissions cache, organized as
    // Map{Object->Collection&lt;Permission&gt;}.
    // The Object is a ProtectionDomain, a CodeSource or
    // any other permissions-granted entity.
    private final ConcurrentMap<Object, Collection<Permission>> cache = 
            new ConcurrentWeakIdentityMap<Object, Collection<Permission>>();

    // A specific parser for a particular policy file format.
    private final PolicyParser parser;
    
    /**
     * Default constructor, equivalent to
     * <code>ConcurrentPolicyFile(new DefaultPolicyParser())</code>.
     */
    public ConcurrentPolicyFile() {
        this(new DefaultPolicyParser());
    }

    /**
     * Extension constructor for plugging-in a custom parser.
     * @param dpr 
     */
    public ConcurrentPolicyFile(PolicyParser dpr) {
        parser = dpr;
        rwl = new ReentrantReadWriteLock();
        rl = rwl.readLock();
        wl = rwl.writeLock();
        refresh();
    }

    /**
     * Returns collection of permissions allowed for the domain 
     * according to the policy. The evaluated characteristics of the 
     * domain are it's codesource and principals; they are assumed
     * to be <code>null</code> if the domain is <code>null</code>.
     * @param pd ProtectionDomain
     * @see ProtectionDomain
     */
    @Override
    public PermissionCollection getPermissions(ProtectionDomain pd) {
        CodeSource cs = pd.getCodeSource();
        Collection<Permission> pc = cache.get(cs); // saves new object creation.
        if (pc == null){
            // Just because the new object is contained within a ConcurrentMap
            // doesn't mean it doesn't need to be synchronized!
            pc = Collections.synchronizedSet( new HashSet<Permission>() );
            Collection<Permission> existed = cache.putIfAbsent(cs, pc);
            if ( !(existed == null) ){ pc = existed;}
        }
        try {
            rl.lock();
            Iterator<PolicyEntry> it = grants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if (ge.impliesPrincipals(pd == null ? null : pd.getPrincipals())
                    && ge.impliesCodeSource(pd == null ? null : pd.getCodeSource())) {
                    pc.addAll(ge.getPermissions());
                }
            }               
        } finally { rl.unlock(); }        
        return PolicyUtils.toPermissionCollection(pc);
    }

    /**
     * Returns collection of permissions allowed for the codesource 
     * according to the policy. 
     * The evaluation assumes that current principals are undefined.
     * @param cs CodeSource
     * @see CodeSource
     */
    @Override
    public PermissionCollection getPermissions(CodeSource cs) {
        Collection<Permission> pc = cache.get(cs); // saves new object creation.
        if (pc == null){
            // Just because the new object is contained within a ConcurrentMap
            // doesn't mean it doesn't need to be synchronized!
            pc = Collections.synchronizedSet( new HashSet<Permission>() );
            Collection<Permission> existed = cache.putIfAbsent(cs, pc);
            if ( !(existed == null) ){ pc = existed;}
        }
        try {
            rl.lock();
            Iterator<PolicyEntry> it = grants.iterator();
            while (it.hasNext()) {
                PolicyEntry ge = it.next();
                if (ge.impliesPrincipals(null)
                    && ge.impliesCodeSource(cs)) {
                    pc.addAll(ge.getPermissions()); // we still hold a reference
                }
            }     
        } finally { rl.unlock(); }
        return PolicyUtils.toPermissionCollection(pc);
    }
    
    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
	PermissionCollection pc = getPermissions(domain);
	if (pc == null) {
	    return false;
	}
	return pc.implies(permission);
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
        Set<PolicyEntry> fresh = new HashSet<PolicyEntry>();
        Properties system = new Properties(AccessController
                .doPrivileged(new PolicyUtils.SystemKit()));
        system.setProperty("/", File.separator); //$NON-NLS-1$
        URL[] policyLocations = PolicyUtils.getPolicyURLs(system,
                                                          JAVA_SECURITY_POLICY,
                                                          POLICY_URL_PREFIX);
        for (int i = 0; i < policyLocations.length; i++) {
            try {
                //TODO debug log
                //System.err.println("Parsing policy file: " + policyLocations[i]);
                Collection<PolicyEntry> pc = parser.parse(policyLocations[i], system);
                fresh.addAll(pc);
            } catch (Exception e) {
                // TODO log warning
                //System.err.println("Ignoring policy file: " 
                //                 + policyLocations[i] + ". Reason:\n"+ e);
            }
        }
        try {
            wl.lock();
            grants = fresh;
            cache.clear();
        }finally {wl.unlock();}
    }
}
