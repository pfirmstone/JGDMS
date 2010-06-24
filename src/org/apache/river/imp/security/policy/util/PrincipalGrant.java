/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.imp.security.policy.util;

import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Principal;
import java.security.acl.Group;
import java.security.cert.Certificate;
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
    private final int hashCode;
    @SuppressWarnings("unchecked")
    protected PrincipalGrant(Principal[] pals){
        if ( pals != null ){
            principals = new ArrayList<Principal>(pals.length);
            principals.addAll(Arrays.asList(pals));
        }else {
            principals = Collections.EMPTY_LIST;
        }
        int hash = 5;
        hash = 97 * hash + (this.principals != null ? this.principals.hashCode() : 0);
        hashCode = hash;
    }
    
    @Override
    public boolean equals(Object o){
       if (o == null) return false;
       if (o == this) return true;
       if (o instanceof PrincipalGrant ){
           PrincipalGrant p = (PrincipalGrant) o;
           if (principals.equals(p.principals)) return true;
       }
       return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
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
     * Utility Method, really belongs somewhere else, but most subclasses use it.
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
    
    public boolean implies(ClassLoader cl, Principal[] pal) {
        return impliesClassLoader(cl) && implies(pal);
    }

    public boolean implies(CodeSource codeSource, Principal[] pal) {
        return impliesCodeSource(codeSource) && implies(pal);
    }

    public boolean implies(Certificate[] certs, Principal[] pal) {
        return impliesCertificates(certs) && implies(pal);
    }

    protected abstract boolean impliesCertificates(Certificate[] certs);

    protected abstract boolean impliesClassLoader(ClassLoader cl);

    protected abstract boolean impliesCodeSource(CodeSource codeSource);

    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = new PermissionGrantBuilder();
        return pgb.principals(principals.toArray(new Principal[principals.size()]));
    }
}
