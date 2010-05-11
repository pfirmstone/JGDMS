/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.spi;

import org.apache.river.api.security.RevokeablePolicy;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import net.jini.security.policy.PolicyInitializationException;

/**
 * An implementer of this interface isn't required to extend Policy and isn't
 * required to implement dynamic grants either.
 * @author Peter Firmstone
 */
public interface RevokeableDynamicPolicySpi extends RevokeablePolicy {
    public boolean basePolicy(Policy basePolicy);
    public void initialize()throws PolicyInitializationException;
    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    public void ensureDependenciesResolved();
        // All the java.security.Policy methods     
    public PermissionCollection getPermissions(CodeSource codesource);
    public PermissionCollection getPermissions(ProtectionDomain domain);
    public boolean implies(ProtectionDomain domain, Permission permission);        
    public void refresh();

}
