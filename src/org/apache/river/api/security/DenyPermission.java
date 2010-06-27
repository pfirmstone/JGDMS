/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.security;

import java.security.Permission;

/**
 * <p>DenyPermission allows for a permission to be Denied at runtime provided
 * it has been dynamically granted.<p>
 * 
 * A DenyPermission cannot dynamically grant itself a permission.<p>
 * 
 * A domain with DenyPermissoin can not deny a Permission
 * unless it has been granted dynamically. </p>
 *
 * -- seems logical.
 * 
 * 
 * @author Peter Firmstone
 */
public class DenyPermission extends Permission {
    private static final long serialVersionUID = 1L;
    
    public DenyPermission(){
        super("");
    }
    
    @Override
    public boolean implies(Permission permission) {
        if (permission instanceof DenyPermission) return true;
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DenyPermission) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return DenyPermission.class.hashCode();
    }

    @Override
    public String getActions() {
        return "";
    }
}
