/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
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
    
    /*
     * ClassLoader based permission revocation is broken and cannot be fixed.
     * It will be removed shortly.
     * 
     * The reason is if a Permission is granted to a CodeSource or Certificate's loaded into
     * a ClassLoader, then that Permission will not be revokeable for that
     * ClassLoader.
     * 
     * @param cl
     * @param principals
     * @param permissions
     * @throws java.lang.UnsupportedOperationException
     */
//    public void revoke(Class cl, Principal[] principals, Permission[] permissions)
//            throws UnsupportedOperationException;
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
    public void grantCodeSource(CodeSource cs, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    
    public void grantProtectionDomain(Class cl, Permission[] permissions)
            throws UnsupportedOperationException;
    
    public void revokeProtectionDomain(Class cl, Permission[] permissions)
            throws UnsupportedOperationException;
    
    public void grant(Certificate[] certs, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    
    public void revoke(Certificate[] certs, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    /**
     * 
     * @return
     */
    public boolean revokeSupported();
}
