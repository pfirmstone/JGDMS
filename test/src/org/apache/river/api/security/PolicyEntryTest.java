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
* @version $Revision$
*/

package org.apache.river.api.security;

import java.security.cert.Certificate;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.SecurityPermission;
import java.util.Arrays;

import junit.framework.TestCase;


/**
 * TODO Put your class description here
 * 
 */

public class PolicyEntryTest extends TestCase {

    /** 
     * Tests constructor and accessors of PolicyEntry 
     */
    public void testCtor() {
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        PermissionGrant pe = pgb.build(); //everything set to null
//        PolicyEntry pe =
//            new PolicyEntry((CodeSource) null, (Collection<Principal>) null,
//                (Collection<Permission>)null);
        assertTrue(pe.isVoid());
        assertTrue(pe.getPermissions().isEmpty());

//        pe = new PolicyEntry(new CodeSource(null, (Certificate[])null),
//            new ArrayList<Principal>(), new ArrayList<Permission>());
        pe = pgb.uri(null)
                .principals(new Principal[0])
                .permissions(new Permission[0])
                .context(PermissionGrantBuilder.URI)
                .build();
        assertTrue(pe.isVoid());
        assertTrue(pe.getPermissions().isEmpty());

        Permission[] perms = new Permission[] {
            new SecurityPermission("dsfg"), new AllPermission() };
        //pe = new PolicyEntry((CodeSource) null, (Collection<Principal>) null, perms);
        pe = pgb.uri(null)
                .principals(null)
                .permissions(perms)
                .build();
        assertFalse(pe.isVoid());
        assertTrue(Arrays.asList(perms).containsAll(pe.getPermissions()));
    }

    /**
     * Null or empty set of Principals of PolicyEntry implies any Principals
     * if CodeSource != null;
     * otherwise tested set must contain all Principals of PolicyEntry.
     */
    public void testImpliesPrincipals() {
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        PermissionGrant pe = pgb.context(PermissionGrantBuilder.URI).build(); // Everything set to null;
        
//        PolicyEntry pe =
//            new PolicyEntry((CodeSource) null, (Collection<Principal>) null,
//                (Collection<Permission>)null);
        Principal[] pp1 = new Principal[] {};
        Principal[] pp2 = new Principal[] { new UnresolvedPrincipal("a.b.c",
            "XXX") };
        Principal[] pp3 = new Principal[] {
            new UnresolvedPrincipal("a.b.c", "YYY"),
            new UnresolvedPrincipal("a.b.c", "XXX"),
            new UnresolvedPrincipal("e.f.g", "ZZZ") };

        assertFalse(pe.implies( (CodeSource) null, (Principal[]) null));
        assertFalse(pe.implies( (CodeSource) null, pp1)); //originally  assert true.

//        pe = new PolicyEntry((CodeSource)null, new HashSet<Principal>(),
//                (Collection<Permission>) null);
        pe = pgb.principals(new Principal[0]).permissions(new Permission[0]).build();
        assertFalse(pe.implies( (CodeSource) null, pp3)); //originally assert true.

//        pe = new PolicyEntry((CodeSource) null, Arrays.asList(pp2),
//                (Collection<Permission>) null);
        pe = pgb.principals(pp2).build(); //Builder still contains empty Permission[]
        assertFalse(pe.implies( (CodeSource) null, (Principal[]) null));
        assertFalse(pe.implies( (CodeSource) null, pp1));
        assertFalse(pe.implies( (CodeSource) null, pp3)); //originally assert true.
    }
}
