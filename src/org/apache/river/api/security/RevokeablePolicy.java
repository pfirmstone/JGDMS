/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import net.jini.security.policy.DynamicPolicy;

/**
 * A DynamicPolicy that supports revoking as well as permission grants.
 * 
 * All Policy methods are implemented so RevokeableDynamicPolicySpi 
 * implementions aren't necessarily required to extend Policy.
 * 
 * @author Peter Firmstone
 * @see DynamicPolicy
 */
public interface RevokeablePolicy extends DynamicPolicy {
    
    /**
     * Revokes permissions based on Principal's and a ProtectionDomain belonging
     * to the class cl.
     * @param cl
     * @param principals
     * @param permissions
     * @throws java.lang.UnsupportedOperationException
     */
    public void revoke(Class cl, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    /**
     * Revokes permissions based on CodeSource and Principals.
     * @param cs
     * @param principals
     * @param permissions
     * @throws java.lang.UnsupportedOperationException
     */
    public void revoke(CodeSource cs, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    /**
     * Grants permissions based on a CodeSource and Principal's.  This may be
     * useful to perform dynamic grants based on a CodeSource rather than
     * a PermissionDomain.  Granting Permission's by CodeSource can apply to 
     * multiple PermissionDomain's.
     * @param cs
     * @param principals
     * @param permissions
     * @throws java.lang.UnsupportedOperationException
     */
    public void grant(CodeSource cs, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    /**
     * 
     * @return
     */
    public boolean revokeSupported();
}
