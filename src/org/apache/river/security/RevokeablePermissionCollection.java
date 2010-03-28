/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.security;

import java.security.Permission;
import java.util.Enumeration;

/**
 *
 * @author Peter Firmstone
 * @see PermissionCollection
 * @see Permission
 */
public interface RevokeablePermissionCollection {
    /**
     * This method is slow and to be used only when correctness is perferred
     * over performance.
     * 
     * In fact revokeAll() followed by add() is the preferred method.
     * 
     * However when the new Permission set depends upon the old and there is
     * a possibility that the old set may be updated with an add during processing
     * this method will fail and as such not loose a particular required permission.
     * 
     * Partial success may occur in which case some of the permissions will be
     * revoked and others not. However only a subset of those permissions that 
     * are requested to be revoked, shall be.  This should not be a problem as
     * the required permissions will still exist in the set and a simple retry
     * should suffice if the return was 0.
     * 
     * Attempt to revoke a Permission, if an intervening write occurs an
     * integer value of 0 is returned, the suggested strategy is to try again,
     * however if several attempts are likely to fail use revokeAll instead.
     * 
     * A return value of -1 indicates that the Permission cannot be revoked
     * from the collection.  In this case use revokeAll.  It may be possible
     * to correct this condition by utilising a finer grained permission set.
     * 
     * A return value of 1 indicates success.
     * 
     * @param permissions
     * @return result, 0 for intervening write, -1 failed not possible, 1 for true
     */
    public int revoke(Permission ... permissions);

    /**
     * Due to the overlapping nature of Permission's, attempts to revoke
     * a permission may fail, in wich case it is best to remove all
     * permissions related by class and later add the required Permissions.
     * This method should always succeed.
     * 
     * This method should only revoke Permission's related to an individual
     * class type.
     * 
     * @param permission 
     */
    public void revokeAll(Permission permission);
    
    /**
     * @see PermissionCollection.add(Permission permission)
     * @param permission
     */
    public void add(Permission permission);
    /**
     * @see PermissionCollection.elements()
     * @return
     */
    public Enumeration<Permission> elements();
    /**
     * @see PermissionCollection.implies()
     * @param permission
     * @return
     */
    public boolean implies(Permission permission);
    /**
     * @see PermissionCollection.isReadOnly()
     * @return true or false
     */
    public boolean isReadOnly();
    /**
     * @see PermissionCollection.setReadOnly()
     */
    public void setReadOnly();
    @Override
    public String toString();
}
