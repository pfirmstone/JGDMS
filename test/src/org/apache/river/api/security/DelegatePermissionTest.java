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

import java.net.SocketPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests fail when run from IDE.
 * 
 * 
 */
public class DelegatePermissionTest {
   
    public DelegatePermissionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
       
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }

    /**
     * Test of get method, of class DelegatePermission.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        Permission expResult = DelegatePermission.get(new SocketPermission("Localhost", "accept"));
        Permission result = DelegatePermission.get(new SocketPermission("Localhost","accept"));
        assertTrue(expResult == result);
    }

    /**
     * Test of implies method, of class DelegatePermission.
     */
    @Test
    public void testImplies() {
        System.out.println("implies");
        Permission permission = new SocketPermission("Localhost", "connect, accept");
        Permission instance = DelegatePermission.get(permission);
        boolean expResult = false;
        boolean result = instance.implies(permission);
        assertEquals(expResult, result);
        permission = DelegatePermission.get(new SocketPermission("Localhost", "connect"));
        assertTrue(instance.implies(permission));
    }

    /**
     * Test of getPermission method, of class DelegatePermission.
     */
    @Test
    public void testGetPermission() {
        System.out.println("getPermission");
        Permission instance = DelegatePermission.get(new SocketPermission("Localhost", "accept"));
        Permission expResult = new SocketPermission("Localhost", "accept");
        Permission result = ((DelegatePermission)instance).getPermission();
        assertEquals(expResult, result);
    }

    /**
     * Test of newPermissionCollection method, of class DelegatePermission.
     */
//    @Test
//    public void testNewPermissionCollection() {
//        System.out.println("newPermissionCollection");
//        DelegatePermission instance = null;
//        PermissionCollection expResult = null;
//        PermissionCollection result = instance.newPermissionCollection();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
}
