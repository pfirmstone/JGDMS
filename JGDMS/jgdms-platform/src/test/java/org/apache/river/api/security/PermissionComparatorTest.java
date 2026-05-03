/*
 * Copyright 2024 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//package org.apache.river.api.security;

//import java.security.cert.Certificate;
//import java.security.Permission;
//import java.security.UnresolvedPermission;
//import java.util.Collection;
//import java.util.TreeSet;
//import net.jini.security.AuthenticationPermission;
//import org.junit.Test;
//import static org.junit.Assert.*;
//import org.apache.river.api.security.*;
//
///**
// *
// * @author peter
// */
//public class PermissionComparatorTest {
//    
//    public PermissionComparatorTest() {
//    }
//
//    /**
//     * Test of compare method, of class PermissionComparator.
//     */
//    @Test
//    public void testCompare() {
//        System.out.println("compare");
//        Permission o1 = new AuthenticationPermission(
//                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
//                "connect"
//        );
//        Permission o2 = new AuthenticationPermission(
//                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
//                "accept"
//        );
//        Permission u1 = new UnresolvedPermission(
//                "net.jini.security.AuthenticationPermission",
//                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
//                "connect" , new Certificate[]{}
//        );
//        Permission u2 = new UnresolvedPermission(
//                "net.jini.security.AuthenticationPermission",
//                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
//                "accept" , new Certificate[]{}
//        );
//        PermissionComparator instance = new PermissionComparator();
//        Collection<Permission> perms = new TreeSet<Permission>(instance);
//        perms.add(o1);
//        perms.add(o2);
//        assertTrue(perms.contains(o2));
//        assertTrue(perms.contains(o1));
//        
//        boolean expResult = false;
//        boolean result = instance.compare(o1, o2) < 0;
//        assertEquals(expResult, result);
//        result = instance.compare(u1, u2) < 0;
//        assertEquals(expResult, result);
//        expResult = true;
//        result = instance.compare(o2, o1) < 0;
//        assertEquals(expResult, result);
//        result = instance.compare(u2, u1) < 0;
//        assertEquals(expResult, result);
//    }
//    
//}
