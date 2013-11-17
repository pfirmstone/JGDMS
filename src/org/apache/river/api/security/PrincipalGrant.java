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

package org.apache.river.api.security;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Peter Firmstone.
 */
class PrincipalGrant extends PermissionGrant implements Serializable{
    private static final long serialVersionUID = 1L;
    // Null object pattern for CodeSource.
    protected static final CodeSource nullCS = new CodeSource((URL) null, (Certificate[]) null);
    protected final Set<Principal> pals;
    private final int hashCode;
    @SuppressWarnings("unchecked")
    PrincipalGrant(Principal[] pals, Permission[] perm){
        super(perm);
        if ( pals != null ){
	    Set<Principal> palCol = new HashSet<Principal>(pals.length);
            palCol.addAll(Arrays.asList(pals));
	    this.pals = Collections.unmodifiableSet(palCol);
        }else {
            this.pals = Collections.emptySet();
        }
        int hash = 5;
        hash = 97 * hash + (this.pals != null ? this.pals.hashCode() : 0);
        Iterator<Permission> i = getPermissions().iterator();
        while (i.hasNext()){
            Permission p = i.next();
            if (p instanceof UnresolvedPermission){
                hash = 97 * hash + p.hashCode();
            } else if (p != null){
                Class c = p.getClass();
                String name = p.getName();
                String actions = p.getActions();
                hash = 97 * hash + c.hashCode();
                hash = 97 * hash + (name != null ? name.hashCode() : 0);
                hash = 97 * hash + (actions != null ? actions.hashCode() : 0);
            }
        }
        hashCode = hash;
    }
    
    @Override
    public boolean equals(Object o){
       if (o == null) return false;
       if (o == this) return true;
       if (o.hashCode() != this.hashCode()) return false;
       if (!super.equals(o)) return false;
       if (o instanceof PrincipalGrant ){
           PrincipalGrant p = (PrincipalGrant) o;
           if (pals.equals(p.pals)) return true;
       }
       return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder(300);
        sb.append("Principals: \n");
        Iterator<Principal> palIt = pals.iterator();
        while (palIt.hasNext()){
            sb.append(palIt.next().toString())
              .append("\n");
        }
        sb.append("\nPermissions: \n");
        Iterator<Permission> permIt = getPermissions().iterator();
        while (permIt.hasNext()){
            sb.append(permIt.next().toString())
              .append("\n");
        }     
        return sb.toString();
    }
        
    boolean implies(Principal[] prs) {
        if ( pals.isEmpty()) return true;
        if ( prs == null || prs.length == 0 ) return false;
        // PermissionGrant Principals match if equal or if they are Groups and
        // the Principals being tested are their members.  Every Principal
        // in this PermissionGrant must have a match.
        List<Principal> princp = Arrays.asList(prs);
        int matches = 0;
        Iterator<Principal> principalItr = pals.iterator();
        while (principalItr.hasNext()){
            Principal entrypal = principalItr.next();
//            Group g = null;
//            if ( entrypal instanceof Group ){
//                g = (Group) entrypal;
//            }
            Iterator<Principal> p = princp.iterator();
            // The first match breaks out of internal loop.
            while (p.hasNext()){
                Principal implied = p.next();
                if (entrypal.equals(implied)) {
                    matches++;
                    break;
                }
                /* Having thought further about the following, I'm hesitant
                 * to allow a positive match for a Principal belonging to a 
                 * Group defined in PrincipalGrant, my reasoning is that
                 * a PrincipalGrant is immutable and therefore shouldn't change.
                 * Group represents a mutable component which can change the
                 * result of implies.  A PrincipalGrant, should
                 * have the same behaviour on all calls to implies, ie: be idempotent.
                 * 
                 * The reason for this choice at this time; there is
                 * no way a Policy can be aware the PrincipalGrant
                 * has changed its behaviour. Since PrincpalGrant doesn't
                 * make the determination of a Permission implies, it only
                 * stores the PermissionGrant, a permission may continue to be
                 * implied after a Principal has been removed from a Group due
                 * to caching of PermissionCollections within the Policy.
                 * 
                 * Use Subject instead to group Principal's to grant additional
                 * Privileges to a user Principal.
                 */ 
//                if ( g != null && g.isMember(implied) ) {
//                    matches++;
//                    break;
//                }
            }  
        }
        if (matches == pals.size()) return true;
        return false;
    }
      
    /* Dynamic grant's and file policy grant's have different semantics,
     * this class was originally abstract, it might be advisable to make it so
     * again.
     * 
     * dynamic grant's check if the contained protection domain is null first
     * and if so return true.  policy file grant's check if the passed in
     * pd is null first and if so return false.
     * 
     */
    public boolean implies(ProtectionDomain pd) {
        if (pals.isEmpty()) return true;
	if (pd == null) return false;
	Principal[] hasPrincipals = getPrincipals(pd);
	return implies(hasPrincipals);
    }
    
    Principal[] getPrincipals(final ProtectionDomain pd){
        if (pd instanceof SubjectDomain){
            final Set<Principal> principals = ((SubjectDomain) pd).getSubject().getPrincipals();
            // principals is a synchronized Set, always up to date.
            // Contention should be minimal even if Subject run on many threads.
            return (Principal[]) principals.toArray();
        }
        return pd.getPrincipals();
    }
    
    public boolean implies(ClassLoader cl, Principal[] pal) {
	// A null ClassLoader indicates the system domain.
        return implies(pal);
    }

    public boolean implies(CodeSource codeSource, Principal[] pal) {
	return implies(pal);
    }

    public PermissionGrantBuilder getBuilderTemplate() {
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        Collection<Permission> perms = getPermissions();
        pgb.context(PermissionGrantBuilder.PRINCIPAL)
           .principals(pals.toArray(new Principal[pals.size()]))
           .permissions(perms.toArray(new Permission[perms.size()]));
        return pgb;
    }
    
    public boolean isVoid() {        
        if (getPermissions().isEmpty() ) return true;
        return false;
    }
    
    //writeReplace method for serialization proxy pattern
    private Object writeReplace() {
        return getBuilderTemplate();
    }
    
    //readObject method for the serialization proxy pattern
    private void readObject(ObjectInputStream stream) 
            throws InvalidObjectException{
        throw new InvalidObjectException("PermissionGrantBuilder required");
    }
}
