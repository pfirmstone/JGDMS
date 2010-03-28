/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.security.policy.spi;

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
    
    public void revoke(Class cl, Principal[] principals, Permission[] permissions)
            throws UnsupportedOperationException;
    public boolean revokeSupported();
}
