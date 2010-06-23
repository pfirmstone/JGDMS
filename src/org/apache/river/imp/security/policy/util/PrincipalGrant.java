/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.imp.security.policy.util;

import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Principal;
import java.security.acl.Group;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Peter Firmstone.
 */
abstract class PrincipalGrant implements PermissionGrant {
        private final Collection<Principal> principals;
    @SuppressWarnings("unchecked")
        protected PrincipalGrant(Principal[] pals){
            if ( pals != null ){
                principals = new ArrayList<Principal>(pals.length);
                principals.addAll(Arrays.asList(pals));
            }else {
                principals = Collections.EMPTY_LIST;
            }
          
        }
        
        public boolean implies(Principal[] prs) {
        if ( principals.isEmpty()) return true;
        if ( prs == null || prs.length == 0 ) return false;
        // PolicyEntry Principals match if equal or if they are Groups and
        // the Principals being tested are their members.  Every Principal
        // in this PolicyEntry must have a match.
        List<Principal> princp = Arrays.asList(prs);
        int matches = 0;
        Iterator<Principal> principalItr = principals.iterator();
        while (principalItr.hasNext()){
            Principal entrypal = principalItr.next();
            Group g = null;
            if ( entrypal instanceof Group ){
                g = (Group) entrypal;
            }
            Iterator<Principal> p = princp.iterator();
            // The first match breaks out of internal loop.
            while (p.hasNext()){
                Principal implied = p.next();
                if (entrypal.equals(implied)) {
                    matches++;
                    break;
                }
                if ( g != null && g.isMember(implied) ) {
                    matches++;
                    break;
                }
            }  
        }
        if (matches == principals.size()) return true;
        return false;
    }
      
    /**
     * Utility Method, really belongs somewhere else.
     * @param codeSource
     * @return
     */    
    protected CodeSource normalizeCodeSource(CodeSource codeSource) {
        if (codeSource == null ) return null;
        URL codeSourceURL = PolicyUtils.normalizeURL(codeSource.getLocation());
        CodeSource result = codeSource;

        if (codeSourceURL != codeSource.getLocation()) {
            // URL was normalized - recreate codeSource with new URL
            CodeSigner[] signers = codeSource.getCodeSigners();
            if (signers == null) {
                result = new CodeSource(codeSourceURL, codeSource
                        .getCertificates());
            } else {
                result = new CodeSource(codeSourceURL, signers);
            }
        }
        return result;
    }  

}
