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
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.PermissionGrantBuilderImp.NullPermissionGrant;

/**
 * PermissionGrant implementations are expected to be immutable, non blocking,
 * thread safe and have a good hashCode implementation to perform well in
 * Collections.
 * 
 * Developers may use decorators to alter the behaviour of existing implementations.
 * Decorators should use a single transient volatile field to store the result of
 * an event notification, which must be immutable, state
 * may not be updated in the policy until {@link Policy#refresh()} is called.
 * 
 * PermissionGrant does not implement Serializable for security reasons, 
 * however classes extending PermissionGrant may implement Serializable,
 * but are forced to use the Serializable Builder Pattern.  Child classes
 * cannot contain circular references to themselves if they implement
 * Serializable. {@link http://wiki.apache.org/river/Serialization}
 * 
 * You shouldn't pass around PermissionGrant's to just anyone; they can
 * provide an attacker with information about granted Permissions.
 * 
 * Caveat Implementor: PermissionGrant's cannot perform privileged actions, 
 * whilst being used by the policy to make policy decisions, any privileged 
 * actions should performed prior to creating a PermissionGrant.  
 * Only PermissionGrant's belonging to the same ProtectionDomain as the
 * active Policy can perform PrivilegedAction's, since the Policy caches it's 
 * own domain Permissions during initialisation, it doesn't consult
 * PermissionGrant's thereafter.
 * 
 * @author Peter Firmstone
 * @since 2.2.1
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
        hashcode = 97 * hashcode + (this.decorated != null ? this.decorated.hashCode() : 0);
        this.hash = hashcode;
    }
    
    PermissionGrant( Permission[] perm ){
        decorated = null;
        if (perm == null || perm.length == 0) {
            this.perms = Collections.emptySet();
            privileged = false;
        }else{
            // PermissionComparator is used to avoid broken hashCode and equals
	    Set<Permission> perms = new ConcurrentSkipListSet<Permission>(new PermissionComparator());
            boolean privileged = false;
            int l = perm.length;
            for (int i = 0; i < l; i++){
                perms.add(perm[i]);
                if (perm[i] instanceof AllPermission) privileged = true;
            }
	    this.perms = Collections.unmodifiableSet(perms);
            this.privileged = privileged;
        }
        int hashcode = 7;
        hashcode = 97 * hashcode + (this.perms != null ? this.perms.hashCode() : 0);
        hashcode = 97 * hashcode + (this.privileged ? 1 : 0);
        hashcode = 97 * hashcode + (this.decorated != null ? this.decorated.hashCode() : 0);
        this.hash = hashcode;
    }
    
    protected PermissionGrant(PermissionGrant decorated){
        PD_GUARD.checkGuard(null);
        CL_GUARD.checkGuard(null);
        this.decorated = decorated;
        perms = Collections.emptySet();
        privileged = decorated.isPrivileged();
        hash = decorated.hashCode();
    }

    @Override
    public int hashCode() {
        return hash;
    }
    
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
        if (decorated() != null) return decorated().isPrivileged();
        return privileged;
    }

    /**
     * A DynamicPolicy implementation can use a PermissionGrant as a container
     * for Dynamic Grant's.  A PermissionGrant is first asked by the Policy
     * if it applies to a Particular ProtectionDomain, if it does, the Policy
     * calls getPermissions.
     *
     * @param pd ProtectionDomain
     * @return
     * @see RevokeableDynamicPolicy
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
     */
    public abstract boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if this PermissionGrant applies to the passed in CodeSource
     * and Principal's.
     * @param cs
     * @return 
     */
    public abstract boolean implies(CodeSource codeSource, Principal[] pal);

    /**
     * Returns an unmodifiable Collection of permissions defined by this
     * PermissionGrant, which may be empty, but not null.
     * @return
     */
    public final Collection<Permission> getPermissions(){
        if (decorated() != null) return decorated().getPermissions();
        return perms;
        }

    /**
     * Returns true if this PermissionGrant defines no Permissions, or if
     * a PermissionGrant was made to a ProtectionDomain that no longer exists.
     */
    public abstract boolean isVoid();
    
    /**
     * Provide a PermissionGrantBuilder, suitable for
     * producing a new PermissionGrant.
     * 
     * @return
     */
    public abstract PermissionGrantBuilder getBuilderTemplate();

}
