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

package org.apache.river.api.security;

import java.io.Serializable;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Comparator;
import javax.security.auth.PrivateCredentialPermission;

/**
 * A Comparator for Permission that avoids using equals and hashCode() on
 * Permission implementations.
 * 
 * This comparator orders the Permission first by Class, then Name, followed
 * by Actions.
 * 
 * Class is sorted by class hashcode.
 * 
 * Name is sorted using Unicode character order, so wildcards "*"  will 
 * preceed numbers, which will preceed letters.
 * 
 * UnresolvedPermission is a special case.
 * 
 * The comparator must be as fast as possible, the common case is not equal,
 * so that must be very fast.
 * 
 * Note that for SocketPermissionCollection that the desired order to add to 
 * SocketPermission's is with the most likely permissions added last.  
 * 
 * HINT: Use a NavigableMap to return a reverse order iterator for 
 * PermissionCollection's like SocketPermissionCollection and 
 * FilePermissionCollection.
 * 
 * @author Peter Firmstone.
 * @since 3.0.0
 */
public class PermissionComparator implements Comparator<Permission>, Serializable {
    private static final long serialVersionUID = 1L;

    public int compare(Permission o1, Permission o2) {
        if ( o1 == o2 ) return 0;
        if ( o1 == null ) return -1; // o1 is less
        if ( o2 == null ) return 1; // o1 is greater
        
        int hash1, hash2, comparison;
        // Permission not equal if Class hashCode not equal.
        Class c1 = o1.getClass();
        Class c2 = o2.getClass();
        hash1 = c1.hashCode();
        hash2 = c2.hashCode();
        if (hash1 < hash2) return -1;
        if (hash1 > hash2) return 1;
        //hashcodes equal.
        if (o1 instanceof UnresolvedPermission 
		&& o2 instanceof UnresolvedPermission){
            // Special case
            UnresolvedPermission u1 = (UnresolvedPermission) o1, u2 = (UnresolvedPermission) o2;
            String type1 = u1.getUnresolvedType(), type2 = u2.getUnresolvedType();
            if ( type1 == null ){
                if (type2 == null) return 0;
                return -1; // o1 is less
            }
            if ( type2 == null ) return 1; // o1 is greater
            comparison = type1.compareTo(type2);
            if ( comparison != 0 ) return comparison;
            // types equal.
            String name1 = u1.getUnresolvedName(), name2 = u2.getUnresolvedName();
            if ( name1 == null ){
                if (name2 == null) return 0;
                return -1; // o1 is less
            }
            if ( name2 == null ) return 1; // o1 is greater
            comparison = name1.compareTo(name2);
            if ( comparison != 0 ) return comparison;
            // names equal.
            String action1 = u1.getUnresolvedName(), action2 = u2.getUnresolvedName();
            if ( action1 == null ){
                if (action2 == null) return 0;
                return -1; // o1 is less
            }
            if ( action2 == null ) return 1; // o1 is greater
            comparison = action1.compareTo(action2);
            if ( comparison != 0 ) return comparison;
            // actions equal.
            Certificate[] cert1 = u1.getUnresolvedCerts(), cert2 = u2.getUnresolvedCerts();
            if ( cert1 == null ){
                if (cert2 == null) return 0;
                return -1; // o1 is less
            }
            if ( cert2 == null ) return 1; // o1 is greater
            int l1 = cert1.length, l2 = cert2.length;
            if (l1 < l2 ) return -1;
            if (l1 > l2 ) return 1;
            // Same length cert arrays.
            if (Arrays.asList(cert1).containsAll(Arrays.asList(cert2))) return 0;
            // compare each until they don't match don't be fussy they're not equal
            // but they're the same length.
            for (int i = 0; i < l1; i++){
                int c = cert1[i].toString().compareTo(cert2[i].toString());
                if (c != 0) return c;
            }
            return -1;
        } else if (o1 instanceof PrivateCredentialPermission && 
		o2 instanceof PrivateCredentialPermission){
	    // Ensure there are no duplicates
	    if(o1.equals(o2)) return 0;
	    // PrivateCredentialPermission.getName() may only include the credential
	    // while the action always equals read.
	    PrivateCredentialPermission p1 = (PrivateCredentialPermission) o1;
	    PrivateCredentialPermission p2 = (PrivateCredentialPermission) o2;
	    String cred1 = p1.getCredentialClass(), cred2 = p2.getCredentialClass();
	    if (cred1 == null){
		if (cred2 ==null) return 0;
		return -1; //o1 is less
	    }
	    if (cred2 == null) return 1; //o1 is greater
	    comparison = cred1.compareTo(cred2);
	    if ( comparison != 0 ) return comparison;
	    // credentials equal
	    String [][] prin1 = p1.getPrincipals();
	    String [][] prin2 = p2.getPrincipals();
	    int len1 = prin1.length, len2 = prin2.length;
	    if (len1 < len2) return -1;
	    if (len1 > len2) return 1;
	    // lengths equal
	    // Now we could get complex and order the Principal class names
	    // and principal names, for more accurate equals, however the only
	    // consequence of not doing so, is that equivalent permissions
	    // may be contained, in the same collection.
	    // To avoid that scenario, we check equals first.
	    // TODO: Review and revise
	    for (int i=0; i<len1; i++){
		String [] pr1 = prin1[i];
		String [] pr2 = prin2[i];
		// length should always be 2, but just in case.
		int l1 = pr1.length;
		int l2 = pr2.length;
		if (l1 < l2) return -1;
		if (l1 > l2) return 2;
		// length equal
		for (int j=0; j<l1; j++){
		    comparison = pr1[j].compareTo(pr2[j]);
		    if (comparison != 0) return comparison;
		}
	    }
	    return -1;
	}
        String name1 = o1.getName();
        String name2 = o2.getName();
        if ( name1 == null ){
            if (name2 == null) return 0;
            return -1; // o1 is less
        }
        if ( name2 == null ) return 1; // o1 is greater
        comparison = name1.compareTo(name2);
        if ( comparison != 0 ) return comparison;
        // names equal.
        String actions1 = o1.getActions();
        String actions2 = o2.getActions();
        if ( actions1 == null ){
            if (actions2 == null) return 0;
            return -1; // o1 is less
        }
        if ( actions2 == null ) return 1; // o1 is greater
        comparison = actions1.compareTo(actions2);
        if ( comparison != 0 ) return comparison;
        // actions equal.
        // Now we must be careful that these Permission's are truly equal.
        // Check they have same class
        if ( c1.equals(c2)) return 0;
        // if we get to here, someone might be trying to substitute
        return -1;
    }

}
