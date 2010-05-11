/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.Permission;

/**
 * RevokePermission allows for a permission to be granted at runtime or
 * revoked.  The revoker thread needs no permission other than a this.  
 * A RevokePermission cannot grant itself a permission it doesn't already have.
 * 
 * A domain with revoke permission can not revoke a RevokePermission
 * unless it has been 
 * 
 * It should cache all revokes, such that a refresh operation, doesn't add
 * any revoked permissions.  I'm not sure about grant's though, should they be
 * refreshed and require re granting if they didn't exist in the configuration
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
