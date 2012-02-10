/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.BasicPermission;

/**
 * <p>A "revoke" or "REVOKE" PolicyPermission is allows updating a 
 * RemotePolicy or remove Dynamically granted permission from a 
 * RevokeableDynamicPolicy.</p>
 * 
 * <p>A "implementPermissionGrant" PolicyPermission allows a class to implement
 * org.apache.river.api.security.PermissionGrant interface and use it to
 * update a RemotePolicy.  This is not a permission to grant lightly, since
 * a poor implementation could destabilise a policy or worse allow the caller
 * to grant AllPermission anyone using mutation.</p>
 * 
 * @author Peter Firmstone
 */
public class PolicyPermission extends BasicPermission {
    private static final long serialVersionUID = 1L;
    
    public PolicyPermission(String name){
        super(name);
    }
    
}
