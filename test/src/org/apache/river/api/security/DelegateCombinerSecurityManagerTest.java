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

import java.security.Permissions;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.CodeSource;
import java.security.AllPermission;
import java.util.PropertyPermission;
import org.apache.river.api.delegates.DelegatePermission;
import java.security.AccessControlContext;
import java.net.SocketPermission;
import java.security.ProtectionDomain;
import java.security.Permission;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class DelegateCombinerSecurityManagerTest {
    
    public DelegateCombinerSecurityManagerTest() {
    }
    
    private ProtectionDomain[] context;
    private AccessControlContext acc;
    Permission p1, p2, p3, p4, p5, p6, p7, p8;
    SecurityManager sm;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        try {
            sm = new DelegateCombinerSecurityManager();
            CodeSource cs0, cs10, cs11, cs12;
            p1 = new SocketPermission("*", "connect,accept");
            p2 = DelegatePermission.get(p1);
            p3 = new RuntimePermission("readFileDescriptor");
            p4 = DelegatePermission.get(p3);
            p5 = new PropertyPermission("java.home", "read,write");
            p6 = new PropertyPermission("java.home", "read");
            p7 = DelegatePermission.get(p6);
            p8 = new AllPermission();
            cs0  = new CodeSource(null, (Certificate[]) null);
            cs10 = new CodeSource(new URL("file:/foo.bar"), (Certificate[]) null);
            cs11 = new CodeSource(new URL("file:/foo.too"), (Certificate[]) null);
            cs12 = new CodeSource(new URL("file:/too.foo"), (Certificate[]) null);
            Permissions pc1, pc2, pc3, pc4;
            pc1 = new Permissions();
            pc1.add(p1);
            pc1.add(p3);
            pc1.add(p5);
            pc2 = new Permissions();
            pc2.add(p2);
            pc2.add(p4);
            pc2.add(p7);
            pc3 = new Permissions();
            pc3.add(p2);
            pc3.add(p4);
            pc3.add(p7);
            pc4 = new Permissions();
            pc4.add(p8);
            ProtectionDomain pd1, pd2, pd3, pd4;
            pd1 = new ProtectionDomain(cs0, pc4);
            pd2 = new ProtectionDomain(cs10,pc3);
            pd3 = new ProtectionDomain(cs11,pc2);
            pd4 = new ProtectionDomain(cs12,pc1);
            context = new ProtectionDomain[]{pd1, pd2, pd3, pd4};
            acc = new AccessControlContext(context);
        } catch (MalformedURLException ex) {
            ex.printStackTrace(System.out);
        }
    }

    /**
     * Test of checkPermission method, of class DelegateCombinerSecurityManager.
     */
    @Test
    public void testCheckPermission1() {
        System.out.println("checkPermission1");
        Boolean result = Boolean.FALSE;
        Boolean expectedResult = Boolean.FALSE;
        try {
                sm.checkPermission(p1, acc);
            result = Boolean.TRUE;
        } catch (SecurityException e){
            result = Boolean.FALSE;
        }
        assertEquals(expectedResult,result);
    }
    
    @Test
    public void testCheckPermission2() {
        System.out.println("checkPermission2");
        Boolean result = Boolean.FALSE;
        Boolean expectedResult = Boolean.TRUE;
        try {
            /* This permission check is cached, lets test performance.
             */
            for (int i = 0; i < 200000; i++ ){
                sm.checkPermission(p2, acc);
            }
            result = Boolean.TRUE;
        } catch (Exception e){
            e.printStackTrace(System.out);
            result = Boolean.FALSE;
        }
        assertEquals(expectedResult,result);
    }
    
    @Test
    public void testCheckPermission3() {
        System.out.println("checkPermission3");
        Boolean result = Boolean.FALSE;
        Boolean expectedResult = Boolean.FALSE;
        try {
            sm.checkPermission(p3, acc);
            result = Boolean.TRUE;
        } catch (Exception e){
            e.printStackTrace(System.out);
            result = Boolean.FALSE;
        }
        assertEquals(expectedResult,result);
    }
    
    @Test
    public void testCheckPermission4() {
        System.out.println("checkPermission4");
        Boolean result = Boolean.FALSE;
        Boolean expectedResult = Boolean.TRUE;
        try {
            sm.checkPermission(p4, acc);
            result = Boolean.TRUE;
        } catch (Exception e){
            e.printStackTrace(System.out);
            result = Boolean.FALSE;
        }
        assertEquals(expectedResult,result);
    }
    /**
     * Test of checkPermission method, of class DelegateCombinerSecurityManager.
     */
//    @Test
//    public void testCheckPermission_Permission_Object() {
//        System.out.println("checkPermission");
//        Permission perm = null;
//        Object context = null;
//        DelegateCombinerSecurityManager instance = new DelegateCombinerSecurityManager();
//        instance.checkPermission(perm, context);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of clearFromCache method, of class DelegateCombinerSecurityManager.
//     */
//    @Test
//    public void testClearFromCache() {
//        System.out.println("clearFromCache");
//        Set<Permission> perms = null;
//        DelegateCombinerSecurityManager instance = new DelegateCombinerSecurityManager();
//        instance.clearFromCache(perms);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
}
