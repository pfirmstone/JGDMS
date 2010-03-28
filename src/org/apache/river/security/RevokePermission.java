/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.security;

import java.security.Permission;

/**
 * RevokePermission allows for a permission to be granted at runtime or
 * revoked.  A Thread invoking this Permission must have the permission that
 * is to be granted.  A RevokePermission cannot grant itself a permission
 * it doesn't already have.
 * 
 * It should cache all revokes, such that a refresh operation, doesn't add
 * any revoked permissions.  I'm not sure about grant's though, should they be
 * refreshed and require re granting if they didn't exist in the configuration
 * -- seems logical.
 * 
 * 
 * @author peter
 */
public class RevokePermission extends Permission {
    private static final long serialVersionUID = 1L;
    private final String actions;
    
    public RevokePermission(String name){
        super(name);
        actions = "";
    }

    public RevokePermission(String name, String actions){
        super(name);
        this.actions = actions;
    }
    
    @Override
    public boolean implies(Permission permission) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean equals(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getActions() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    

}
