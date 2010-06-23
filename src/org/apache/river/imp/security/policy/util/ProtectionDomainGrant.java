/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.util;

import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author Peter Firmstone
 */
class ProtectionDomainGrant extends PrincipalGrant implements PermissionGrant {
    private final boolean hasDomain;
    private final WeakReference<ProtectionDomain> domain;
    private final Collection<Permission> permissions;
    
    @SuppressWarnings("unchecked")
    ProtectionDomainGrant(WeakReference<ProtectionDomain> domain, Principal[] groups, 
            Permission[] perm){
        super(groups);
        if (domain == null){
            hasDomain = false;
        }else{
            hasDomain = true;
        }
        this.domain = domain;
        if (perm == null || perm.length == 0) {
            this.permissions = Collections.EMPTY_LIST;
        }else{
            this.permissions = new HashSet<Permission>(perm.length);
            this.permissions.addAll(Arrays.asList(perm));
        }
    }
    // for grant
    public boolean implies(ProtectionDomain pd) {
        // ProtectionDomain comparison
        return impliesProtectionDomain(pd);
    }

    /**
     * Checks if passed ProtectionDomain matches this PolicyEntry. Null ProtectionDomain of
     * PolicyEntry implies any ProtectionDomain; non-null ProtectionDomain's are
     * compared with equals();
     */
    protected boolean impliesProtectionDomain(ProtectionDomain pd) {
        if (hasDomain == false) return true;
        if (pd == null) return false;       
        if (domain.get() == null ) return false; // hasDomain already true
        return pd.equals(domain.get()); // pd not null
    }
    // This is only here for revoke.
    protected boolean impliesClassLoader(ClassLoader cl) {
        if (hasDomain == false) return true;
        if (cl == null) return false;       
        if (domain.get() == null ) return false; // hasDomain already true
        return domain.get().getClassLoader().equals(cl); // pd not null
    }
    // This is only here for revoke.
    protected boolean impliesCodeSource(CodeSource codeSource) {
        ProtectionDomain pd = domain.get();
        if (pd == null) return true;
        CodeSource cs = normalizeCodeSource(pd.getCodeSource());
        if (cs == null) return true;
        if (codeSource == null) return false;       
        return cs.implies(normalizeCodeSource(codeSource));
    }
    
    protected boolean impliesCertificates(Certificate[] signers){
        ProtectionDomain pd = domain.get();
        if (pd == null) return true;
        List<Certificate> certs = Arrays.asList(pd.getCodeSource().getCertificates());
        if ( certs.isEmpty()) return true;
        if ( signers == null || signers.length == 0 ) return false;
        List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(certs);    
    }

    public Collection<Permission> getPermissions() {
        return Collections.unmodifiableCollection(permissions);
    }

    public boolean isVoid() {        
        if (permissions.size() == 0 ) return true;
        if (hasDomain == true && domain.get() == null) return true;
        return false;
    }
    
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return impliesClassLoader(cl) && implies(pal);
    }

    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return impliesCodeSource(codeSource) && implies(pal);
    }

    public boolean implies(Certificate[] certs, Principal[] pal) {
        return impliesCertificates(certs) && implies(pal);
    }
  
}
