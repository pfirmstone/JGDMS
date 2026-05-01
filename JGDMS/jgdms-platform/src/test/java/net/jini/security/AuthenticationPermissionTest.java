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
package net.jini.security;

import java.security.Permission;
import java.security.PermissionCollection;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class AuthenticationPermissionTest {
    
    public AuthenticationPermissionTest() {
    }

    /**
     * Test of implies method, of class AuthenticationPermission.
     */
    @Test
    public void testImplies() {
        System.out.println("implies");
        Permission perm = new AuthenticationPermission(
                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
                "connect"
        );
        AuthenticationPermission instance = new AuthenticationPermission(
                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
                "connect"
        );
        boolean expResult = true;
        boolean result = instance.implies(perm);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Test of getActions method, of class AuthenticationPermission.
     */
    @Test
    public void testGetActions() {
        System.out.println("getActions");
        AuthenticationPermission instance = new AuthenticationPermission(
                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
                "connect"
        );
        String expResult = "connect";
        String result = instance.getActions();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Test of newPermissionCollection method, of class AuthenticationPermission.
     */
    @Test
    public void testNewPermissionCollection() {
        System.out.println("newPermissionCollection");
        AuthenticationPermission instance = new AuthenticationPermission(
                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
                "connect"
        );
        Permission perm = new AuthenticationPermission(
                "javax.security.auth.x500.X500Principal \"CN=Tester\" peer javax.security.auth.x500.X500Principal \"CN=Phoenix\"",
                "connect"
        );
        boolean expResult = true;
        PermissionCollection collection = instance.newPermissionCollection();
        collection.add(instance);
        boolean result = collection.implies(perm);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Test of equals method, of class AuthenticationPermission.
     */
//    @Test
//    public void testEquals() {
//        System.out.println("equals");
//        Object obj = null;
//        AuthenticationPermission instance = null;
//        boolean expResult = false;
//        boolean result = instance.equals(obj);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of hashCode method, of class AuthenticationPermission.
//     */
//    @Test
//    public void testHashCode() {
//        System.out.println("hashCode");
//        AuthenticationPermission instance = null;
//        int expResult = 0;
//        int result = instance.hashCode();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//    
}
