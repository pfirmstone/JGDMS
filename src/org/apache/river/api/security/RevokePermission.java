/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.Permission;

/**
 * <p>RevokePermission allows for a permission to be revoked at runtime provided
 * it has been dynamically granted.<p>
 * 
 * A RevokePermission cannot dynamically grant itself a permission.<p>
 * 
 * A domain with revoke permission can not revoke a RevokePermission
 * unless it has been granted dynamically. </p>
 *
 * -- seems logical.
 * 
 * 
 * @author Peter Firmstone
 */
public class RevokePermission extends Permission {
    private static final long serialVersionUID = 1L;
    
    public RevokePermission(){
        super("");
    }
    
    @Override
    public boolean implies(Permission permission) {
        if (permission instanceof RevokePermission) return true;
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RevokePermission) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return RevokePermission.class.hashCode();
    }

    @Override
    public String getActions() {
        return "";
    }
}
