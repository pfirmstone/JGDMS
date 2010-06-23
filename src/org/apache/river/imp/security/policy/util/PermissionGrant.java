/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.util;

import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collection;

/**
 *
 * @author peter
 */
public interface PermissionGrant {
    // Implied Context of Grant
    public static final int CLASSLOADER = 0;
    public static final int CODESOURCE = 1;
    public static final int PROTECTIONDOMAIN = 2;
    public static final int CODESOURCE_CERTS = 3;
    /**
     * This is a multi comparison runtime permissions check for Policy's
     * this method must test based on the context.
     * @param pd ProtectionDomain
     * @return
     */
    public boolean implies(ProtectionDomain pd);
    /**
     * Checks if passed ClassLoader matches this PolicyEntry. Null ProtectionDomain of
     * PolicyEntry implies any ClassLoader, unless the ProtectionDomain has
     * become garbage collected, in which case it will be false;
     * 
     * This implies is public to assist in removal of Permission grants
     * from a ClassLoader space.  In other words it ignores context.
     * 
     * It isn't very smart, it misses other grants, so isn't a guarantee that
     * a permission grant won't apply to a particluar ClassLoader, in the
     * case of Principals and Certificate grants.
     * 
     * non-null ProtectionDomain's are
     * compared with equals();
     */
    public boolean implies(ClassLoader cl, Principal[] pal);
    /**
     * Checks if passed CodeSource matches this PolicyEntry. Null CodeSource of
     * PolicyEntry implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     * @param cs
     * @return 
     */
    public boolean implies(CodeSource codeSource, Principal[] pal);
    /**
     * Checks if specified Principals match this PolicyEntry. Null or empty set
     * of Principals of PolicyEntry implies any Principals; otherwise specified
     * array must contain all Principals of this PolicyEntry.
     * @param prs 
     * @return
     */
    public boolean implies(Principal[] prs);
    /**
     * Certificate was chosen as it allows multiple different implementations
     * even if a CodeSource was created with CodeSigner.
     */
    public boolean implies(Certificate[] certs, Principal[] pal);
    /**
     * Returns unmodifiable collection of permissions defined by this
     * PolicyEntry, may be <code>null</code>.
     * @return 
     */
    public Collection<Permission> getPermissions();
    /**
     * Returns true if this PolicyEntry defines no Permissions, false otherwise.
     */
    public boolean isVoid();
    @Override
    public boolean equals(Object o);
    @Override
    public int hashCode();
    @Override
    public String toString();
    
    
}
