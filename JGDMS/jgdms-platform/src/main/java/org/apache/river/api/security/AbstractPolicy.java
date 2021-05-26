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

package org.apache.river.api.security;

import java.lang.ref.WeakReference;
import java.security.AllPermission;
import java.security.Guard;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import net.jini.security.GrantPermission;
import net.jini.security.policy.UmbrellaGrantPermission;

/**
 * A common superclass with utility methods for policy providers.
 * 
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public abstract class AbstractPolicy extends Policy {
    protected final Permission umbrella = new UmbrellaGrantPermission();
    protected final Permission ALL_PERMISSION = new AllPermission();
    protected final Comparator<Permission> comparator = new PermissionComparator();

    protected AbstractPolicy() {
	this(check());
    }
    
    private AbstractPolicy(boolean check){
	super();
    }
    
    private static boolean check(){
	SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SecurityPermission("createPolicy.JiniPolicy"));
        }
	return true;
    }

    /**
     * This method checks that the PermissionGrant's are authorised to be
     * granted by it's caller, if it Fails, it will throw a SecurityException
     * or AccessControlException.
     *
     * The PermissionGrant should not be requested for it's Permissions
     * again, since doing so would risk an escalation of privilege attack if the
     * PermissionGrant implementation was mutable.
     *
     * @param grants to check
     */
    protected final void checkCallerHasGrants(Collection<PermissionGrant> grants) throws SecurityException {
        Iterator<PermissionGrant> grantsItr = grants.iterator();
        while (grantsItr.hasNext()) {
            PermissionGrant grant = grantsItr.next();
            Collection<Permission> permCol = grant.getPermissions();
            Permission[] perms = permCol.toArray(new Permission[permCol.size()]);
            checkNullElements(perms);
            Guard g = new GrantPermission(perms);
            g.checkGuard(this);
        }
    }

    /**
     * Checks array for null elements
     * @param array to check for null elements.
     * @throws NullPointerException if array contains null elements or array is null.
     */
    protected final void checkNullElements(Object[] array) throws NullPointerException {
        int l = array.length;
        for (int i = 0; i < l; i++) {
            if (array[i] == null) {
                throw new NullPointerException();
            }
        }
    }

    /**
     * Creates an optimised PermissionCollection, firstly all permissions should
     * be sorted using {@link PermissionComparator}, this ensures that any
     * SocketPermission will be ordered to avoid reverse DNS calls if possible.
     * 
     * Other parameters enable the underlying {@link ConcurrentHashMap}
     * to be optimised, these parameters use identical names.
     * 
     * @param permissions to be optimized
     * @param initialCapacity see ConcurrentHashMap
     * @param loadFactor see ConcurrentHashMap
     * @param concurrencyLevel see ConcurrentHashMap
     * @param unresolvedCapacity  Capacity of Map used to store 
     * UnresolvedPermission instances
     * @return PermissionCollection
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or concurrencyLevel are
     * nonpositive.
     */
    protected final PermissionCollection convert(NavigableSet<Permission> permissions, 
                                                 int initialCapacity, 
                                                 float loadFactor, 
                                                 int concurrencyLevel, 
                                                 int unresolvedCapacity)
                                            throws IllegalArgumentException {
        PermissionCollection pc = new ConcurrentPermissions(initialCapacity, loadFactor, concurrencyLevel, unresolvedCapacity);
        // The descending iterator is for SocketPermission.
        Iterator<Permission> it = permissions.descendingIterator();
        while (it.hasNext()) {
            pc.add(it.next());
        }
        return pc;
    }

    /** River-26 Mark Brouwer suggested making UmbrellaPermission's expandable
     * from Dynamic Grants.
     * @param pc  PermissionCollection containing UmbrellaPermission's to be
     * expanded.
     */
    protected final void expandUmbrella(PermissionCollection pc) {
        if (pc.implies(umbrella)) {
            // Don't use Set, avoid calling equals and hashCode on SocketPermission.
            Collection<Permission> perms = new ArrayList<Permission>(120);
            Enumeration<Permission> e = pc.elements();
            while (e.hasMoreElements()){
                Permission p = e.nextElement();
                // Avoid unintended granting of GrantPermission 
                // and recursive UmbrellaGrantPermission
                if ( p instanceof GrantPermission || 
                        p instanceof UmbrellaGrantPermission){
                    continue;
                }
                perms.add(p);
            }
            pc.add(new GrantPermission(perms.toArray(new Permission[perms.size()])));
	}
    }

    /**
     * Adds Permission objects contained in PermissionGrant's to a NavigableSet
     * that is sorted using a PermissionComparator.
     * 
     * This method doesn't perform any checks on the conditions of the
     * PermissionGrant's, it simply collects their Permission objects.
     * 
     * @param grant  array of PermissionGrants.
     * @param permClass  optionally only add Permission objects that use this 
     * class or UnresolvedPermission.
     * @param stopIfAll  if true returns immediately when AllPermission is 
     * found.
     * @param setToAddPerms  Permission objects extracted from grant will be
     * added to this set.
     */
    protected final void processGrants(Collection<PermissionGrant> grant, 
                                       Class permClass, 
                                       boolean stopIfAll, 
                                       NavigableSet<Permission> setToAddPerms) {   
        Iterator<PermissionGrant> grants = grant.iterator();
        if (permClass == null) {
            while (grants.hasNext()) {
                PermissionGrant g = grants.next();
                if (stopIfAll && g.isPrivileged()) {
                    setToAddPerms.clear();
                    setToAddPerms.add(ALL_PERMISSION);
                    return;
                }
                Iterator<Permission> it = g.getPermissions().iterator();
                while (it.hasNext()) {
                    Permission p = it.next();
                    setToAddPerms.add(p);
                }
            }
        } else {
            while (grants.hasNext()) {
                PermissionGrant g = grants.next();
                if (stopIfAll && g.isPrivileged()) {
                    setToAddPerms.add(ALL_PERMISSION);
                    return;
                }
                Iterator<Permission> it = g.getPermissions().iterator();
                while (it.hasNext()) {
                    Permission p = it.next();
                    if (permClass.isInstance(p) || p instanceof UnresolvedPermission) {
                        setToAddPerms.add(p);
                    }
                }
            }
        }
    }
    
    protected PermissionGrant extractGrantFromPolicy(Policy p, ProtectionDomain domain){
        Collection<Permission> perms = new LinkedList<Permission>();
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.setDomain(new WeakReference<ProtectionDomain>(domain));
        PermissionCollection pc = p.getPermissions(domain);
        Enumeration<Permission> en = pc.elements();
        while (en.hasMoreElements()){
            perms.add(en.nextElement());
        }
        pgb.permissions(perms.toArray(new Permission[perms.size()]));
        pgb.context(PermissionGrantBuilder.PROTECTIONDOMAIN);
        return pgb.build();
    }
    
}
