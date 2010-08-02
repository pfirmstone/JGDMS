

package org.apache.river.api.security;

import java.util.List;

/**
 * RevokeableDynamicPolicy, is a Java Security Policy Provider that supports
 * Runtime Dynamically Grantable and Revokeable Permission's, in the form
 * of PermissionGrant's
 * 
 * @author Peter Firmstone
 * @see java.security.Policy
 * @see java.security.ProtectionDomain
 * @see java.security.AccessController
 * @see java.security.DomainCombiner
 * @see java.security.AccessControlContext
 * @see java.security.Permission
 */
public interface RevokeableDynamicPolicy {
    /**
     * 
     * @param grants
     */
    public void grant(List<PermissionGrant> grants);
    /**
     * 
     * @param grants
     */
    public void revoke(List<PermissionGrant> grants);
    /**
     * 
     * @return
     */
    public List<PermissionGrant> getPermissionGrants();
    /**
     * 
     * @param denials
     */
    public void add(List<Denied> denials);
    /**
     * 
     * @param denials
     */
    public void remove(List<Denied> denials);
    /**
     * 
     * @return
     */
    public List<Denied> getDenied();
    /**
     * 
     * @return
     */
    public boolean revokeSupported();
}
