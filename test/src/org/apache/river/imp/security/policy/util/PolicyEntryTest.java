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

package org.apache.river.imp.security.policy.util;

import org.apache.river.imp.security.policy.util.UnresolvedPrincipal;
import org.apache.river.imp.security.policy.util.PolicyEntry;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.SecurityPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import junit.framework.TestCase;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;


/**
 * TODO Put your class description here
 * 
 */

public class PolicyEntryTest extends TestCase {

    /** 
     * Tests constructor and accessors of PolicyEntry 
     */
    public void testCtor() {
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        PermissionGrant pe = pgb.build(); //everything set to null
//        PolicyEntry pe =
//            new PolicyEntry((CodeSource) null, (Collection<Principal>) null,
//                (Collection<Permission>)null);
        assertTrue(pe.isVoid());
        assertTrue(pe.getPermissions().length == 0);

//        pe = new PolicyEntry(new CodeSource(null, (Certificate[])null),
//            new ArrayList<Principal>(), new ArrayList<Permission>());
        pe = pgb.codeSource(new CodeSource(null, (Certificate[])null))
                .principals(new Principal[0])
                .permissions(new Permission[0])
                .build();
        assertTrue(pe.isVoid());
        assertTrue(pe.getPermissions().length == 0);

        Permission[] perms = new Permission[] {
            new SecurityPermission("dsfg"), new AllPermission() };
        //pe = new PolicyEntry((CodeSource) null, (Collection<Principal>) null, perms);
        pe = pgb.codeSource(null).principals(null).permissions(perms).build();
        assertFalse(pe.isVoid());
        assertEquals(perms, pe.getPermissions());
    }

    /**
     * Null CodeSource of PolicyEntry implies any CodeSource; non-null
     * CodeSource should delegate to its own imply() functionality
     * @throws java.lang.Exception 
     */
    public void testImpliesCodeSource() throws Exception {
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        
        
        CodeSource cs0  = new CodeSource(null, (Certificate[]) null);

        CodeSource cs10 = new CodeSource(new URL("file:"), (Certificate[]) null);
        CodeSource cs11 = new CodeSource(new URL("file:/"), (Certificate[]) null);
        CodeSource cs12 = new CodeSource(new URL("file://"), (Certificate[]) null);
        CodeSource cs13 = new CodeSource(new URL("file:///"), (Certificate[]) null);

        CodeSource cs20 = new CodeSource(new URL("file:*"), (Certificate[]) null);
        CodeSource cs21 = new CodeSource(new URL("file:/*"), (Certificate[]) null);
        CodeSource cs22 = new CodeSource(new URL("file://*"), (Certificate[]) null);
        CodeSource cs23 = new CodeSource(new URL("file:///*"), (Certificate[]) null);

        CodeSource cs30 = new CodeSource(new URL("file:-"), (Certificate[]) null);
        CodeSource cs31 = new CodeSource(new URL("file:/-"), (Certificate[]) null);
        CodeSource cs32 = new CodeSource(new URL("file://-"), (Certificate[]) null);
        CodeSource cs33 = new CodeSource(new URL("file:///-"), (Certificate[]) null);

//        PolicyEntry pe0  = 
//                new PolicyEntry((CodeSource) null, (Collection<Principal>) null,
//                (Collection<Permission>)null);
        
        PermissionGrant pe0 = pgb.codeSource(null).principals(null).permissions(null).build();
        
        PermissionGrant pe10 = pgb.codeSource(cs10).build();
         PermissionGrant pe11 = pgb.codeSource(cs11).build();
         PermissionGrant pe12 = pgb.codeSource(cs12).build();
         PermissionGrant pe13 = pgb.codeSource(cs13).build();
             
//        PolicyEntry pe10 = new PolicyEntry(cs10, null, null);
//        PolicyEntry pe11 = new PolicyEntry(cs11, null, null);
//        PolicyEntry pe12 = new PolicyEntry(cs12, null, null);
//        PolicyEntry pe13 = new PolicyEntry(cs13, null, null);
         
         PermissionGrant pe20 = pgb.codeSource(cs20).build();
         PermissionGrant pe21 = pgb.codeSource(cs21).build();
         PermissionGrant pe22 = pgb.codeSource(cs22).build();
         PermissionGrant pe23 = pgb.codeSource(cs23).build();

//        PolicyEntry pe20 = new PolicyEntry(cs20, null, null);
//        PolicyEntry pe21 = new PolicyEntry(cs21, null, null);
//        PolicyEntry pe22 = new PolicyEntry(cs22, null, null);
//        PolicyEntry pe23 = new PolicyEntry(cs23, null, null);

         PermissionGrant pe30 = pgb.codeSource(cs30).build();
         PermissionGrant pe31 = pgb.codeSource(cs31).build();
         PermissionGrant pe32 = pgb.codeSource(cs32).build();
         PermissionGrant pe33 = pgb.codeSource(cs33).build();
         
//        PolicyEntry pe30 = new PolicyEntry(cs30, null, null);
//        PolicyEntry pe31 = new PolicyEntry(cs31, null, null);
//        PolicyEntry pe32 = new PolicyEntry(cs32, null, null);
//        PolicyEntry pe33 = new PolicyEntry(cs33, null, null);

        assertTrue (pe0.implies( (CodeSource) null, (Principal[])null ));
        assertTrue (pe0.implies( cs10, (Principal[])null ));
        assertTrue (pe0.implies(cs11, (Principal[])null ));
        assertTrue (pe0.implies(cs12, (Principal[])null ));
        assertTrue (pe0.implies(cs13, (Principal[])null ));
        assertTrue (pe0.implies(cs20, (Principal[])null ));
        assertTrue (pe0.implies(cs21, (Principal[])null ));
        assertTrue (pe0.implies(cs22, (Principal[])null ));
        assertTrue (pe0.implies(cs23, (Principal[])null ));
        assertTrue (pe0.implies(cs30, (Principal[])null ));
        assertTrue (pe0.implies(cs31, (Principal[])null ));
        assertTrue (pe0.implies(cs32, (Principal[])null ));
        assertTrue (pe0.implies(cs33, (Principal[])null ));

        assertFalse(pe10.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe10.implies(cs10, (Principal[])null ));
        assertFalse(pe10.implies(cs11, (Principal[])null ));
        assertTrue (pe10.implies(cs12, (Principal[])null ));
        assertFalse(pe10.implies(cs13, (Principal[])null ));
        assertTrue (pe10.implies(cs20, (Principal[])null ));
        assertFalse(pe10.implies(cs21, (Principal[])null ));
        assertFalse(pe10.implies(cs22, (Principal[])null ));
        assertFalse(pe10.implies(cs23, (Principal[])null ));
        assertTrue (pe10.implies(cs30, (Principal[])null ));
        assertFalse(pe10.implies(cs31, (Principal[])null ));
        assertFalse(pe10.implies(cs32, (Principal[])null ));
        assertFalse(pe10.implies(cs33, (Principal[])null ));

        assertFalse(pe11.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe11.implies(cs10, (Principal[])null ));
        assertTrue (pe11.implies(cs11, (Principal[])null ));
        assertFalse(pe11.implies(cs12, (Principal[])null ));
        assertTrue (pe11.implies(cs13, (Principal[])null ));
        assertFalse(pe11.implies(cs20, (Principal[])null ));
        assertFalse(pe11.implies(cs21, (Principal[])null ));
        assertFalse(pe11.implies(cs22, (Principal[])null ));
        assertFalse(pe11.implies(cs23, (Principal[])null ));
        assertFalse(pe11.implies(cs30, (Principal[])null ));
        assertFalse(pe11.implies(cs31, (Principal[])null ));
        assertFalse(pe11.implies(cs32, (Principal[])null ));
        assertFalse(pe11.implies(cs33, (Principal[])null ));

        assertFalse(pe12.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe12.implies(cs10, (Principal[])null ));
        assertFalse(pe12.implies(cs11, (Principal[])null ));
        assertTrue (pe12.implies(cs12, (Principal[])null ));
        assertFalse(pe12.implies(cs13, (Principal[])null ));
        assertTrue (pe12.implies(cs20, (Principal[])null ));
        assertFalse(pe12.implies(cs21, (Principal[])null ));
        assertFalse(pe12.implies(cs22, (Principal[])null ));
        assertFalse(pe12.implies(cs23, (Principal[])null ));
        assertTrue (pe12.implies(cs30, (Principal[])null ));
        assertFalse(pe12.implies(cs31, (Principal[])null ));
        assertFalse(pe12.implies(cs32, (Principal[])null ));
        assertFalse(pe12.implies(cs33, (Principal[])null ));

        assertFalse(pe13.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe13.implies(cs10, (Principal[])null ));
        assertTrue (pe13.implies(cs11, (Principal[])null ));
        assertFalse(pe13.implies(cs12, (Principal[])null ));
        assertTrue (pe13.implies(cs13, (Principal[])null ));
        assertFalse(pe13.implies(cs20, (Principal[])null ));
        assertFalse(pe13.implies(cs21, (Principal[])null ));
        assertFalse(pe13.implies(cs22, (Principal[])null ));
        assertFalse(pe13.implies(cs23, (Principal[])null ));
        assertFalse(pe13.implies(cs30, (Principal[])null ));
        assertFalse(pe13.implies(cs31, (Principal[])null ));
        assertFalse(pe13.implies(cs32, (Principal[])null ));
        assertFalse(pe13.implies(cs33, (Principal[])null ));

        assertFalse(pe20.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe20.implies(cs10, (Principal[])null ));
        assertFalse(pe20.implies(cs11, (Principal[])null ));
        assertTrue (pe20.implies(cs12, (Principal[])null ));
        assertFalse(pe20.implies(cs13, (Principal[])null ));
        assertTrue (pe20.implies(cs20, (Principal[])null ));
        assertFalse(pe20.implies(cs21, (Principal[])null ));
        assertFalse(pe20.implies(cs22, (Principal[])null ));
        assertFalse(pe20.implies(cs23, (Principal[])null ));
        assertTrue (pe20.implies(cs30, (Principal[])null ));
        assertFalse(pe20.implies(cs31, (Principal[])null ));
        assertFalse(pe20.implies(cs32, (Principal[])null ));
        assertFalse(pe20.implies(cs33, (Principal[])null ));

        assertFalse(pe21.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe21.implies(cs10, (Principal[])null ));
        assertTrue (pe21.implies(cs11, (Principal[])null ));
        assertFalse(pe21.implies(cs12, (Principal[])null ));
        assertTrue (pe21.implies(cs13, (Principal[])null ));
        assertFalse(pe21.implies(cs20, (Principal[])null ));
        assertTrue (pe21.implies(cs21, (Principal[])null ));
        assertFalse(pe21.implies(cs22, (Principal[])null ));
        assertTrue (pe21.implies(cs23, (Principal[])null ));
        assertFalse(pe21.implies(cs30, (Principal[])null ));
        assertTrue (pe21.implies(cs31, (Principal[])null ));
        assertFalse(pe21.implies(cs32, (Principal[])null ));
        assertTrue (pe21.implies(cs33, (Principal[])null ));

        assertFalse(pe22.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe22.implies(cs10, (Principal[])null ));
        // assertFalse(pe22.implies(cs11));
        assertFalse(pe22.implies(cs12, (Principal[])null ));
        // assertFalse(pe22.implies(cs13));
        assertFalse(pe22.implies(cs20, (Principal[])null ));
        assertFalse(pe22.implies(cs21, (Principal[])null ));
        assertTrue (pe22.implies(cs22, (Principal[])null ));
        assertFalse(pe22.implies(cs23, (Principal[])null ));
        assertFalse(pe22.implies(cs30, (Principal[])null ));
        assertFalse(pe22.implies(cs31, (Principal[])null ));
        assertTrue (pe22.implies(cs32, (Principal[])null ));
        assertFalse(pe22.implies(cs33, (Principal[])null ));

        assertFalse(pe23.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe23.implies(cs10, (Principal[])null ));
        assertTrue (pe23.implies(cs11, (Principal[])null ));
        assertFalse(pe23.implies(cs12, (Principal[])null ));
        assertTrue (pe23.implies(cs13, (Principal[])null ));
        assertFalse(pe23.implies(cs20, (Principal[])null ));
        assertTrue (pe23.implies(cs21, (Principal[])null ));
        assertFalse(pe23.implies(cs22, (Principal[])null ));
        assertTrue (pe23.implies(cs23, (Principal[])null ));
        assertFalse(pe23.implies(cs30, (Principal[])null ));
        assertTrue (pe23.implies(cs31, (Principal[])null ));
        assertFalse(pe23.implies(cs32, (Principal[])null ));
        assertTrue (pe23.implies(cs33, (Principal[])null ));

        assertFalse(pe30.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe30.implies(cs10, (Principal[])null ));
        assertFalse(pe30.implies(cs11, (Principal[])null ));
        assertTrue (pe30.implies(cs12, (Principal[])null ));
        assertFalse(pe30.implies(cs13, (Principal[])null ));
        assertTrue (pe30.implies(cs20, (Principal[])null ));
        assertFalse(pe30.implies(cs21, (Principal[])null ));
        assertFalse(pe30.implies(cs22, (Principal[])null ));
        assertFalse(pe30.implies(cs23, (Principal[])null ));
        assertTrue (pe30.implies(cs30, (Principal[])null ));
        assertFalse(pe30.implies(cs31, (Principal[])null ));
        assertFalse(pe30.implies(cs32, (Principal[])null ));
        assertFalse(pe30.implies(cs33, (Principal[])null ));

        assertFalse(pe31.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe31.implies(cs10, (Principal[])null ));
        assertTrue (pe31.implies(cs11, (Principal[])null ));
        assertTrue (pe31.implies(cs12, (Principal[])null ));
        assertTrue (pe31.implies(cs13, (Principal[])null ));
        assertTrue (pe31.implies(cs20, (Principal[])null ));
        assertTrue (pe31.implies(cs21, (Principal[])null ));
        assertFalse(pe31.implies(cs22, (Principal[])null ));
        assertTrue (pe31.implies(cs23, (Principal[])null ));
        assertTrue (pe31.implies(cs30, (Principal[])null ));
        assertTrue (pe31.implies(cs31, (Principal[])null ));
        assertFalse(pe31.implies(cs32, (Principal[])null ));
        assertTrue (pe31.implies(cs33, (Principal[])null ));

        assertFalse(pe32.implies((CodeSource)null, (Principal[])null ));
        assertFalse(pe32.implies(cs10, (Principal[])null ));
        assertFalse(pe32.implies(cs11, (Principal[])null ));
        assertFalse(pe32.implies(cs12, (Principal[])null ));
        assertFalse(pe32.implies(cs13, (Principal[])null ));
        assertFalse(pe32.implies(cs20, (Principal[])null ));
        assertFalse(pe32.implies(cs21, (Principal[])null ));
        assertFalse(pe32.implies(cs22, (Principal[])null ));
        assertFalse(pe32.implies(cs23, (Principal[])null ));
        assertFalse(pe32.implies(cs30, (Principal[])null ));
        assertFalse(pe32.implies(cs31, (Principal[])null ));
        assertTrue (pe32.implies(cs32, (Principal[])null ));
        assertFalse(pe32.implies(cs33, (Principal[])null ));

        assertFalse(pe33.implies((CodeSource)null, (Principal[])null ));
        assertTrue (pe33.implies(cs10, (Principal[])null ));
        assertTrue (pe33.implies(cs11, (Principal[])null ));
        assertTrue (pe33.implies(cs12, (Principal[])null ));
        assertTrue (pe33.implies(cs13, (Principal[])null ));
        assertTrue (pe33.implies(cs20, (Principal[])null ));
        assertTrue (pe33.implies(cs21, (Principal[])null ));
        assertFalse(pe33.implies(cs22, (Principal[])null ));
        assertTrue (pe33.implies(cs23, (Principal[])null ));
        assertTrue (pe33.implies(cs30, (Principal[])null ));
        assertTrue (pe33.implies(cs31, (Principal[])null ));
        assertFalse(pe33.implies(cs32, (Principal[])null ));
        assertTrue (pe33.implies(cs33, (Principal[])null ));
    }

    /**
     * Null or empty set of Principals of PolicyEntry implies any Principals;
     * otherwise tested set must contain all Principals of PolicyEntry.
     */
    public void testImpliesPrincipals() {
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        PermissionGrant pe = pgb.build(); // Everything set to null;
        
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

        assertTrue(pe.implies( (CodeSource) null, (Principal[]) null));
        assertTrue(pe.implies( (CodeSource) null, pp1));

//        pe = new PolicyEntry((CodeSource)null, new HashSet<Principal>(),
//                (Collection<Permission>) null);
        pe = pgb.principals(new Principal[0]).permissions(new Permission[0]).build();
        assertTrue(pe.implies( (CodeSource) null, pp3));

//        pe = new PolicyEntry((CodeSource) null, Arrays.asList(pp2),
//                (Collection<Permission>) null);
        pe = pgb.principals(pp2).build(); //Builder still contains empty Permission[]
        assertFalse(pe.implies( (CodeSource) null, (Principal[]) null));
        assertFalse(pe.implies( (CodeSource) null, pp1));
        assertTrue(pe.implies( (CodeSource) null, pp3));
    }
}
