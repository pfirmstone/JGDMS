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

package org.apache.river.imp.security.policy.se;

import tests.support.FakePrincipal;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;

import org.apache.river.imp.security.policy.util.PolicyEntry;
import org.apache.river.imp.security.policy.util.UnresolvedPrincipal;
import org.apache.river.imp.security.policy.util.DefaultPolicyParser;
import junit.framework.TestCase;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.imp.security.policy.se.ConcurrentPolicyFile;
import org.apache.river.imp.security.policy.util.PermissionGrantBuilderImp;


/**
 * Tests for ConcurrentPolicyFile
 * 
 */
public class ConcurrentPolicyFileTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ConcurrentPolicyFileTest.class);
    }

    static class TestParser extends DefaultPolicyParser {

        PermissionGrant[] content;

        public TestParser(PermissionGrant[] content) {
            this.content = content;
        }

        public Collection<PermissionGrant> parse(URL location, Properties system)
            throws Exception {
            if (content != null) {
                return Arrays.asList(content);
            }
            throw new RuntimeException();
        }
    }

    /**
     * Tests that policy is really resetted on refresh(). 
     */
    public void testRefresh() {
        Permission sp = new SecurityPermission("sdf");
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        PermissionGrant[] pe = new PermissionGrant[] { 
            pgb.codeSource(null).principals(null)
               .permissions(new Permission[] { sp })
               .build()
        };
        TestParser tp = new TestParser(pe);
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(tp);
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        assertTrue(policy.getPermissions(cs).implies(sp));

        tp.content = new PermissionGrant[0];
        policy.refresh();
        assertFalse(policy.getPermissions(cs).implies(sp));

        tp.content = null;
        policy.refresh();
        assertFalse(policy.getPermissions(cs).implies(sp));
    }

    /**
     * Tests that refresh() does not fail on failing parser.
     */
    public void testRefresh_Failure() {
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(null));
        policy.refresh();
        assertFalse(policy.getPermissions(cs).elements().hasMoreElements());
    }

    /**
     * Tests proper policy evaluation for CodeSource parameters.
     * @throws java.lang.Exception 
     */
    public void testGetPermissions_CodeSource() throws Exception {
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        CodeSource cs2 = new CodeSource(new URL("http://a.b.c"),
            (Certificate[])null);
        Permission sp1 = new SecurityPermission("aaa");
        Permission sp2 = new SecurityPermission("bbb");
        Permission sp3 = new SecurityPermission("ccc");
        PermissionGrant pe1 = pgb.codeSource(cs)
                .permissions(new Permission[] { sp1 })
                .build();
        PermissionGrant pe2 = pgb.codeSource(cs2)
                .principals(new Principal[0])
                .permissions(new Permission[] { sp2 })
                .build();
        PermissionGrant pe3 = pgb.codeSource(cs)
                .principals( new Principal[] {new FakePrincipal("qqq") })
                .permissions(new Permission[] { sp3 })
                        .build();
        PermissionGrant[] peArray = new PermissionGrant[] { pe1, pe2, pe3};
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(peArray));

        assertTrue(policy.getPermissions(cs).implies(sp1));
        assertFalse(policy.getPermissions(cs).implies(sp2));
        assertFalse(policy.getPermissions(cs).implies(sp3));

        assertTrue(policy.getPermissions(cs2).implies(sp1));
        assertTrue(policy.getPermissions(cs2).implies(sp2));
        assertFalse(policy.getPermissions(cs2).implies(sp3));
    }

    /**
     * Tests proper policy evaluation for ProtectionDomain parameters.
     * @throws java.lang.Exception 
     */
    public void testGetPermissions_ProtectionDomain() throws Exception {
        PermissionGrantBuilder pgb = new PermissionGrantBuilderImp();
        Permission sp1 = new SecurityPermission("aaa");
        Permission sp2 = new SecurityPermission("bbb");
        Permission sp3 = new SecurityPermission("ccc");
        Permission sp4 = new SecurityPermission("ddd");
        Permission spZ = new SecurityPermission("zzz");
        PermissionCollection pcZ = spZ.newPermissionCollection();
        pcZ.add(spZ);
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        CodeSource cs2 = new CodeSource(new URL("http://a.b.c"),
            (Certificate[])null);
        ProtectionDomain pd1 = new ProtectionDomain(cs, null);
        ProtectionDomain pd2 = new ProtectionDomain(cs2, pcZ, null,
            new Principal[] { new FakePrincipal("qqq") });
        
        PermissionGrant pe1 = pgb.codeSource(cs)
                .permissions(new Permission[] { sp1 })
                .build();
        PermissionGrant pe2 = pgb.codeSource(cs2)
                .principals(new Principal[] { new UnresolvedPrincipal(
                UnresolvedPrincipal.WILDCARD, UnresolvedPrincipal.WILDCARD) })
                .permissions(new Permission[] { sp2 })
                .build();
        PermissionGrant pe3 = pgb.codeSource(cs)
                .principals(new Principal[] { new UnresolvedPrincipal(
                FakePrincipal.class.getName(), "qqq") })
                .permissions(new Permission[] { sp3 })
                .build();
        PermissionGrant pe4 = pgb.codeSource(cs2)
                .principals(new Principal[] { new UnresolvedPrincipal(
                FakePrincipal.class.getName(), "ttt") })
                .permissions(new Permission[] { sp4 })
                .build();
        PermissionGrant[] peArray = new PermissionGrant[]{
            pe1, pe2, pe3, pe4 };
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(peArray));

        assertTrue(policy.getPermissions(pd1).implies(sp1));
        assertFalse(policy.getPermissions(pd1).implies(sp2));
        assertFalse(policy.getPermissions(pd1).implies(sp3));
        assertFalse(policy.getPermissions(pd1).implies(sp4));

        assertTrue(policy.getPermissions(pd2).implies(sp1));
        assertTrue(policy.getPermissions(pd2).implies(sp2));
        assertTrue(policy.getPermissions(pd2).implies(sp3));
        assertFalse(policy.getPermissions(pd2).implies(sp4));
    }
}
