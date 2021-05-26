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

import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Guard;
import java.security.Permission;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.apache.river.api.security.PermissionGrantBuilderImp.NullPermissionGrant;

/**
 * <code>PermissionGrant</code> implementations are expected to be immutable, non blocking,
 * thread safe and have a good hashCode implementation to perform well in
 * Collections.
 * <p>
 * Developers may use decorators to alter the behaviour of existing implementations.
 * Decorators should use a single transient volatile field to store the result of
 * an event notification, which must be immutable, state
 * may not be updated in the policy until {@link Policy#refresh()} is called.
 * It is the implementors responsibility to call refresh.
 * <p>
 * <code>PermissionGrant</code> does not implement <code>Serializable</code> for security reasons, 
 * however classes extending PermissionGrant may implement Serializable,
 * but are forced to use the
 * <a href=http://wiki.apache.org/river/Serialization>Serializable Builder Pattern</a>.
 * <p>
 * <code>PermissionGrant</code>'s are security sensitive objects and can
 * provide an attacker with information about granted <code>Permission</code>.  
 * For this reason, it is wise to guard references to <code>PermissionGrant</code>
 * or to avoid storing them in object fields altogether.
 * <p>
 * Caveat Implementor: <code>PermissionGrant</code>'s can only make calls to other objects
 * that themselves perform security checks during an implies call if
 * all ProtectionDomains involved are privileged and the call is performed within 
 * {@link java.security.PrivilegedAction}.  {@link java.security.PrivilegedAction}'s 
 * should generally be performed prior to creating a PermissionGrant if possible.
 * <p>
 * Only PermissionGrant's who's ProtectionDomain has AllPermission can perform 
 * {@link java.security.PrivilegedAction}'s during 
 * {@link java.security.Policy#implies(java.security.ProtectionDomain, java.security.Permission) }
 * calls, since the {@link java.security.Policy} checks privileged PermissionGrant's 
 * and returns without checking less privileged PermissionGrant's that would
 * cause an infinite recursion.  Infinite recursion could be used as a denial
 * of service by an attacker that can access an acting {@link java.security.Policy}
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
public abstract class PermissionGrant {
    /*
     * I had originally wanted this class to be an interface, however it became
     * obvious during development that securing an interface would be more
     * challenging despite the added flexibility it would provide.
     * 
     * The interface solution required the policy to have an additional cache
     * where the set of permissions were stored in a map, in case the 
     * implementor tried to mutate those permissions.
     * 
     * To avoid escallation of privileges, some fields and methods are final.
     * 
     * Overriding classes are restricted in how they can implement Serializable, such
     * that all guards are checked after deserialization is complete so
     * all ProtectionDomains are on the call stack.
     * 
     * Child classes are prevented from modifying the contained immutable Permissions.
     */
    private static final PermissionGrant nullGrant = new NullPermissionGrant();
    private static final Guard PD_GUARD = new RuntimePermission("getProtectionDomain");
    private static final Guard CL_GUARD = new RuntimePermission("getClassLoader");
    private final Set<Permission> perms;
    private final boolean privileged;
    private final PermissionGrant decorated;
    private final int hash;
    
    /**
     * Public constructor to enable serialization support?  No a serialization
     * builder is required.
     */
    PermissionGrant(){
        perms = Collections.emptySet();
        privileged = false;
        decorated = null;
        int hashcode = 7;
        hashcode = 97 * hashcode + (this.perms != null ? this.perms.hashCode() : 0);
        hashcode = 97 * hashcode + (this.privileged ? 1 : 0);
        hashcode = 97 * hashcode; // for null decorated.
        this.hash = hashcode;
    }
    
    PermissionGrant( Permission[] perm ){
        decorated = null;
        if (perm == null || perm.length == 0) {
            this.perms = Collections.emptySet();
            privileged = false;
        }else{
            // PermissionComparator is used to avoid broken hashCode and equals
	    Set<Permission> perms = new TreeSet<Permission>(new PermissionComparator());
            boolean privileged = false;
            int l = perm.length;
            for (int i = 0; i < l; i++){
		/*
		* Ensure any action fields are populated before 
		* final field is frozen after this constructor completes,
		* otherwise lazy initialization isn't visible to other threads.
		*/
                perm[i].getActions(); 
                perms.add(perm[i]);
                if (perm[i] instanceof AllPermission) privileged = true;
            }
	    this.perms = Collections.unmodifiableSet(perms);
            this.privileged = privileged;
        }
        int hashcode = 7;
        hashcode = 97 * hashcode + (this.perms != null ? this.perms.hashCode() : 0);
        hashcode = 97 * hashcode + (this.privileged ? 1 : 0);
        hashcode = 97 * hashcode; // decorated = null.
        this.hash = hashcode;
    }
    
    /**
     * <code>PermissionGrant</code> allows for extension to enable condition or
     * event based policy decisions.  Extending classes can add functionality 
     * using the decorator pattern.
     * <p>
     * A privileged ProtectionDomain that contains AllPermission cannot be 
     * decorated and will throw an IllegalArgumentException.  This is to 
     * prevent accidental infinite recursion; policy's that utilize PermissionGrant
     * will check privileged PermissionGrant's first to avoid infinite recursion.
     * <p>
     * A decorated PermissionGrant that calls methods that perform security
     * checks during implies, must do so from within a PrivilegedAction and
     * all subsequent domains on the stack required to perform the PrivilegedAction
     * must have AllPermission and to avoid infinite recursion.  If possible
     * avoid security checks while making implies determinations, by having an
     * external event thread perform the privileged action and update a volatile 
     * variable that can be checked during an implies call without invoking another
     * security check.
     * <p>
     * @param decorated PermissionGrant to be decorated.
     * @throws IllegalArgumentException if decorated is privileged
     * @throws SecurityException if caller doesn't have {@link java.lang.RuntimePermission}
     * "getProtectionDomain" or "getClassLoader".
     */
    protected PermissionGrant(PermissionGrant decorated){
        this(checkInvariants(decorated), decorated);
    }
    
    /**
     * Avoid finalizer attack.  Object#finalize will not be called if first 
     * call in our constructor is to Object constructor.
     * 
     * @param decorated
     * @return true if caller has privileges and decorated isn't privileged.
     * @throws AccessControlException
     * @throws IllegalArgumentException
     */
    private static boolean checkInvariants(PermissionGrant decorated) {
        PD_GUARD.checkGuard(null);
        CL_GUARD.checkGuard(null);
        if (decorated.isPrivileged()) throw 
            new IllegalArgumentException("Privileged PermissionGrant cannot be decorated");
        return true;
    }
    
    /*
     * Avoidance of finalizer attack.
     */
    private PermissionGrant(boolean check, PermissionGrant decorated ){
        this.decorated = decorated;
        perms = Collections.emptySet();
        privileged = false;
        hash = decorated.hashCode();
    }

    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public boolean equals(Object o){
        if ( !( o instanceof PermissionGrant)) return false;
        PermissionGrant that = (PermissionGrant) o;
        if (this.privileged != that.privileged) return false;
        if (decorated != null){
            return decorated.equals(that.decorated);
        }
        return perms.equals(that.perms);
    }
    
    protected final PermissionGrant decorated(){
        // REMIND: Consider null object pattern.
        return decorated != null ? decorated : nullGrant;
    }
    
    /**
     * Optimisation for AllPermission.
     * 
     * @return true - if PermissionGrant contains AllPermission.
     */
    public final boolean isPrivileged(){
        return privileged;
    }

    /**
     * A DynamicPolicy implementation can use a PermissionGrant as a container
     * for Dynamic Grant's.  A PermissionGrant is first asked by the Policy
     * if it applies to a Particular ProtectionDomain, if it does, the Policy
     * calls getPermissions.
     *
     * @param pd ProtectionDomain
     * @return true if implies.
     * @see RevocablePolicy
     */
    public abstract boolean implies(ProtectionDomain pd);  
    /**
     * Checks if this PermissionGrant applies to the passed in ClassLoader
     * and Principal's.
     * 
     * Note that if this method returns false, it doesn't necessarily mean
     * that the grant will not apply to the ClassLoader, since it will depend on 
     * the contents of the ClassLoader and that is indeterminate. It just
     * indicates that the grant definitely does apply if it returns true.
     * 
     * If this method returns false, follow up using the ProtectionDomain for a
     * more specific test, which may return true.
     * @param cl - ClassLoader possibly implied
     * @param pal - Principal's possibly implied
     * @return true if the ClassLoader and Principal's are implied.
     */
    public abstract boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in CodeSource
     * and Principal's.
     * @param codeSource
     * @param pal
     * @return true if implies.
     */
    public abstract boolean implies(CodeSource codeSource, Principal[] pal);
    
    /**
     * Checks whether grant logic is identical for the passed in PermissionGrant.
     * 
     * The permissions granted are not considered, only the imply logic.
     * 
     * The main purpose of this method is to allow external consolidation of
     * permissions from a number of grants into one equivalent PermissionGrant.
     * 
     * @param grant
     * @return true if the grant implies logic is identical to this grant.
     */
    public abstract boolean impliesEquivalent(PermissionGrant grant);
    
    /**
     * Returns true if the grant can only be dynamically granted at runtime. 
     * That is, it isn't possible to specify in a policy file grant.
     * @return 
     */
    public abstract boolean isDyanamic();

    /**
     * Returns an unmodifiable Collection of permissions defined by this
     * PermissionGrant, which may be empty, but not null.
     * @return Collection containing Permission objects.
     */
    public final Collection<Permission> getPermissions(){
        if (decorated != null) return decorated().getPermissions();
        return perms;
        }

    /**
     * Returns true if this PermissionGrant defines no Permissions, or if
     * a PermissionGrant was made to a ProtectionDomain that no longer exists.
     * @return true if void.
     */
    public abstract boolean isVoid();
    
    /**
     * Provide a PermissionGrantBuilder, suitable for
     * producing a new PermissionGrant.
     * 
     * @return PermissionGrantBuilder
     */
    public abstract PermissionGrantBuilder getBuilderTemplate();

}
