/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Alexey V. Varlamov
* @author Peter Firmstone.
* @version $Revision$
*/

package org.apache.river.imp.security.policy.util;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * This class represents an elementary block of a security policy. It associates
 * a CodeSource of an executable code, Principals allowed to execute the code,
 * and a set of granted Permissions.
 * 
 * Immutable
 * 
 * 
 */
public final class PolicyEntry {

    // Store CodeSource
    private final CodeSource cs;
    
    private final WeakReference<ProtectionDomain> domain;
    private final boolean hasDomain;

    // Array of principals 
    private final List<Principal> principals;

    // Permissions collection
    private final Collection<Permission> permissions;
    
    private transient final int hashcode;

    /**
     * Constructor with initialization parameters. Passed collections are not
     * referenced directly, but copied.
     */
    public PolicyEntry(CodeSource cs, Collection<? extends Principal> prs,
            Collection<? extends Permission> permissions) {
        this.cs = (cs != null) ? normalizeCodeSource(cs) : null;
        if ( prs == null || prs.isEmpty()) {
            this.principals = Collections.emptyList(); // Java 1.5
        }else{
            this.principals = new ArrayList<Principal>(prs.size());
            this.principals.addAll(prs);
        }
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        domain = null;
        hasDomain = false;
        /* Effectively immutable, this will make any hash this is contained in perform.
         * May need to consider Serializable for this class yet, we'll see.
         */ 
        if (this.cs == null){
            hashcode = (principals.hashCode() + this.permissions.hashCode()
                    - Boolean.valueOf(hasDomain).hashCode());
        } else {
        hashcode = (this.cs.hashCode() + principals.hashCode() 
                + this.permissions.hashCode() 
                - Boolean.valueOf(hasDomain).hashCode());
        }
    }

    
    public PolicyEntry(ProtectionDomain pd, Collection<? extends Principal> prs,
            Collection<? extends Permission> permissions ){
        CodeSource cs = null;
        if (pd != null){
            cs = pd.getCodeSource();
        }
        this.cs = (cs != null) ? normalizeCodeSource(cs) : null;
        if ( prs == null || prs.isEmpty()) {
            this.principals = Collections.emptyList(); // Java 1.5
        }else{
            this.principals = new ArrayList<Principal>(prs.size());
            this.principals.addAll(prs);
        }
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        domain = new WeakReference<ProtectionDomain>(pd);
        hasDomain = ( pd != null);
        /* Effectively immutable, this will make any hash this is contained in perform.
         * May need to consider Serializable for this class yet, we'll see.
         */
        if (pd == null){
            hashcode = (principals.hashCode() + this.permissions.hashCode() 
                    - Boolean.valueOf(hasDomain).hashCode());
        } else {
            int codeBaseHash = 0;
            if (cs != null){
                codeBaseHash = cs.hashCode();
            }
            hashcode = (pd.hashCode() + principals.hashCode() 
                + this.permissions.hashCode() + codeBaseHash 
                - Boolean.valueOf(hasDomain).hashCode());
        }
    }
    
    /**
     * Checks if passed ProtectionDomain matches this PolicyEntry. Null ProtectionDomain of
     * PolicyEntry implies any ProtectionDomain; non-null ProtectionDomain is
     * compared with equals();
     */
    public boolean impliesProtectionDomain(ProtectionDomain pd) {
        if (hasDomain == false) return true;
        if (pd == null) return false;       
        if (domain.get() == null ) return false; // hasDomain already true
        return pd.equals(domain.get()); // pd not null
    }

    /**
     * Checks if passed CodeSource matches this PolicyEntry. Null CodeSource of
     * PolicyEntry implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    public boolean impliesCodeSource(CodeSource codeSource) {
        if (cs == null) return true;
        if (codeSource == null) return false;       
        return cs.implies(normalizeCodeSource(codeSource));
    }

    private CodeSource normalizeCodeSource(CodeSource codeSource) {
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

    /**
     * Checks if specified Principals match this PolicyEntry. Null or empty set
     * of Principals of PolicyEntry implies any Principals; otherwise specified
     * array must contain all Principals of this PolicyEntry.
     */
    public boolean impliesPrincipals(Principal[] prs) {
       // return PolicyUtils.matchSubset(principals, prs);
        if ( principals.isEmpty()) return true;
        if ( prs == null || prs.length == 0 ) return false;
        List<Principal> princp = Arrays.asList(prs);
        return princp.containsAll(principals);      
    }

    /**
     * Returns unmodifiable collection of permissions defined by this
     * PolicyEntry, may be <code>null</code>.
     */
    public Collection<Permission> getPermissions() {
//        if (permissions.isEmpty()) return null; // not sure if this is good needs further investigation
        return Collections.unmodifiableCollection(permissions);
    }

    /**
     * Returns true if this PolicyEntry defines no Permissions, false otherwise.
     */
    public boolean isVoid() {
        if (permissions.size() == 0 ) return true;
        if (hasDomain == true && domain.get() == null) return true;
        return false;
    }
    
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if ( !(o instanceof PolicyEntry)) return false;
        PolicyEntry pe = (PolicyEntry) o;
        if ( hasDomain == pe.hasDomain
                && (domain == pe.domain || domain.get().equals(pe.domain.get()))
                && (cs == pe.cs || cs.equals(pe.cs)) 
                && principals.equals(pe.principals) 
                && permissions.equals(pe.permissions) ) 
            return true;
        return false;
    }
    
    @Override
    public int hashCode(){
        return hashcode;        
    }
    
    @Override
    public String toString(){
        String domainString = ( domain == null || domain.get() == null) 
                ?  "" : domain.get().toString();
        String csString = (cs == null)?  "": cs.toString();
        String prinStr = principals.toString();
        String permStr = permissions.toString();
        int size = domainString.length()+ csString.length() 
                + prinStr.length() + permStr.length();
        StringBuilder sb = new StringBuilder(size);
        sb.append(domainString).append(csString).append(prinStr).append(permStr);
        return sb.toString();
    }
}
