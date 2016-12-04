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

import java.lang.reflect.ReflectPermission;
import java.net.NetPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.PropertyPermission;
import java.util.logging.LoggingPermission;
import net.jini.security.AccessPermission;
import net.jini.security.AuthenticationPermission;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class ConcurrentPermissionsTest {

    public ConcurrentPermissionsTest() {
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

    @After
    public void tearDown() {
    }

    /**
     * Test of add method, of class ConcurrentPermissions.
     */
    @Test
    public void implies1() {
        System.out.println("add");
        Permission permission = new RuntimePermission("getClassLoader");
        ConcurrentPermissions instance = new ConcurrentPermissions();
        instance.add(permission);
        boolean result = instance.implies(permission);
        assertEquals(true, result);
    }
    
    
    @Test
    public void impliesOrig(){
        System.out.println("add Permissions");
        Permission permission = new RuntimePermission("getClassLoader");
        Permissions instance = new Permissions();
        boolean result = false;
        synchronized (instance){
            try {
            instance.add(permission);
            result = instance.implies(permission);
            } catch (Exception e) {
                System.out.println("Test Failed" + e.getMessage());
            }
        }
        assertEquals(true, result);   
    }

    /**
     * Test of implies method, of class ConcurrentPermissions.
     */
    @Test
    public void implies2() {
        System.out.println("implies2");
        Permission permission = new AuthenticationPermission("javax.security.auth.x500.X500Principal \"CN=serverRSA\"", "listen");
        ConcurrentPermissions instance = new ConcurrentPermissions();
        instance.add(permission);
        boolean expResult = true;
        boolean result = instance.implies(permission);
        assertEquals(expResult, result);
    }
    
    /**
     * Test of implies method, of class ConcurrentPermissions.
     */
    @Test
    public void implies3() {
        System.out.println("implies3");
        Permission permission = new LoggingPermission ("control", null);
        Permission permisCheck = new LoggingPermission ("control", null);
        ConcurrentPermissions instance = new ConcurrentPermissions();
        instance.add(permission);
        boolean expResult = true;
        boolean result = instance.implies(permisCheck);
        assertEquals(expResult, result);
    }
    /**
     * Test of implies method, of class ConcurrentPermissions, when there is
     * no PermissionCollection, present it is created by ConcurrentPermissions,
     * when it resolves an Unresolved Permission during an implies call,
     * this check ensures that the Permission isn't added to the collection
     * by mistake.
     * 
     * This occurred during a refactoring of ConcurrentPermission's and
     * MultiReadPermissionCollection, relating to the addition of 
     * UmbrellaPermissionGrant's to the RevokeableDynamicPolicy when 
     * elements() returned a ConcurrentModificationException.  This test
     * case was proven to fail on 12th August 2010 - Peter Firmstone.
     */
    @Test
    public void doubleImpliesUnresolved() {
        System.out.println("doubleImplies - consistent results");
        Permission permission = new AccessPermission("unlucky");
	Permission unresolved = 
		new UnresolvedPermission("net.jini.security.AccessPermission", 
		"lucky", null, null);
        ConcurrentPermissions instance = new ConcurrentPermissions();
	instance.add(unresolved);
        boolean expResult = false;
        boolean result = instance.implies(permission);
	result = instance.implies(permission);
	assertEquals(expResult, result);
    }
    
    /**
     * Test of elements method, of class ConcurrentPermissions.
     * TODO Concurrent adds.
     */
    @Test
    public void elements() {
        System.out.println("elements");
        Permission permission0 = new ReflectPermission ("suppressAccessChecks");
        Permission permission1 = new PropertyPermission ("sun.security.key.serial.interop", "read");
        Permission permission2 = new NetPermission ("specifyStreamHandler");
        Permission permission3 = new LoggingPermission ("control", null);
        ConcurrentPermissions instance = new ConcurrentPermissions();
        instance.add(permission0);
        instance.add(permission1);
        instance.add(permission2);
        instance.add(permission3);
        ArrayList<Permission> expResult = new ArrayList<Permission>();
        expResult.add(permission0);
        expResult.add(permission1);
        expResult.add(permission2);
        expResult.add(permission3);
        Enumeration<Permission> elem = instance.elements();
        ArrayList<Permission> result = new ArrayList<Permission>();
        while (elem.hasMoreElements()){
            result.add(elem.nextElement());
        }
        int expRes = expResult.size();
        int res = result.size();
        assertEquals(expRes, res);
    }
    
    /**
     * This tests the ability of the ConcurrentPermission to return
     * UnresolvedPermission's via an Enumerator.
     */
    @org.junit.Test
    public void resolvesAndReturnsEnumerator() {
        System.out.println("resolves and returns Enumerators correctly");
        Permission permission0 = new ReflectPermission ("suppressAccessChecks");
        Permission permission1 = new PropertyPermission ("sun.security.key.serial.interop", "read");
        Permission permission2 = new NetPermission ("specifyStreamHandler");
        Permission permission3 = new UnresolvedPermission("java.net.NetPermission",
                "specifyStreamHandler", null, null);
        PermissionCollection instance = new ConcurrentPermissions();
        instance.add(permission0);
        instance.add(permission1);
        instance.add(permission3);
        List<Permission> expectedResult = new ArrayList<Permission>(3);
        expectedResult.add(permission0);
        expectedResult.add(permission1);
        expectedResult.add(permission3);
        Enumeration<Permission> en = instance.elements();
        List<Permission> result = new ArrayList<Permission>(3);
        while (en.hasMoreElements()){
            result.add(en.nextElement());
        }
        assertTrue(result.containsAll(expectedResult));
        assertTrue(instance.implies(permission0));
        assertTrue(instance.implies(permission1));
        assertTrue(instance.implies(permission2));
        en = instance.elements();
        result = new ArrayList<Permission>(3);
        while (en.hasMoreElements()){
            result.add(en.nextElement());
        }
        expectedResult = new ArrayList<Permission>(3);
        expectedResult.add(permission0);
        expectedResult.add(permission1);
        expectedResult.add(permission2);
        assertTrue(result.containsAll(expectedResult));
    }
 
    /**
     * This test adds an UnresolvedPermission, the implies() method
     * must resolve any UnresolvedPermission's.
     */
    @org.junit.Test
    public void resolvesUnresolvedPermission() {
        System.out.println("resolveUnresolvedPermission");      
        Permission permission2 = new NetPermission ("specifyStreamHandler");
        Permission permission3 = new UnresolvedPermission("java.net.NetPermission",
                "specifyStreamHandler", null, null);
        PermissionCollection instance = new ConcurrentPermissions();       
        instance.add(permission3);     
        //check implies for permission2, that will cause resolution.
        assertFalse(instance.implies(permission3));
        assertTrue(instance.implies( permission2));       
    }


}