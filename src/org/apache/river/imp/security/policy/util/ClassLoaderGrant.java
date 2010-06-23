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
class ClassLoaderGrant extends ProtectionDomainGrant implements PermissionGrant {

    @SuppressWarnings("unchecked")
    ClassLoaderGrant(WeakReference<ProtectionDomain> domain, Principal[] groups, 
            Permission[] perm){
        super(domain, groups, perm);
    }

    public boolean implies(ProtectionDomain pd) {
        ClassLoader cl = null;
        Principal[] pals = null;
        if (pd != null){
            cl = pd.getClassLoader();
            pals = pd.getPrincipals();
        }
        return implies(cl, pals);
    }

}
