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
import java.security.cert.Certificate;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permissions;
import java.security.CodeSource;
import java.security.Permission;
import java.security.ProtectionDomain;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class DelegateSecurityManagerTest {
    
    public DelegateSecurityManagerTest() {
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
     * Test of checkPermission method, of class DelegateSecurityManager.
     */
    @Test
    public void testCheckPermission() throws MalformedURLException {
        System.out.println("checkPermission");
        Permissions perms = new Permissions();
        Permission p1 = new SocketPermission("localhost", "accept" );
        perms.add(p1);
        Permission p = DelegatePermission.get(p1);
        ProtectionDomain pd = new ProtectionDomain(new CodeSource(new URL("file:///foo"), (Certificate[]) null), perms);
        DelegateSecurityManager instance = new DelegateSecurityManager();
        boolean expResult = true;
        boolean result = instance.checkPermission(pd, p);
        assertEquals(expResult, result);
    }
}
