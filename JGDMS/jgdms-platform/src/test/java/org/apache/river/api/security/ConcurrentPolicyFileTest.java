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

import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import junit.framework.TestCase;
import net.jini.security.policy.PolicyInitializationException;
import tests.support.FakePrincipal;


/**
 * Tests for ConcurrentPolicyFile
 *
 * @author Alexey V. Varlamov
 * @version $Revision$
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
     * @throws PolicyInitializationException if test fails.
     */
    public void testRefresh() throws PolicyInitializationException {
        Permission sp = new SecurityPermission("sdf");
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        PermissionGrant[] pe = new PermissionGrant[] { 
            pgb.uri(null).principals(null)
               .permissions(new Permission[] { sp })
               .context(PermissionGrantBuilder.URI)
               .build()
        };
        TestParser tp = new TestParser(pe);
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(tp, new PermissionComparator());
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        ProtectionDomain pd = new ProtectionDomain(cs, null, null, null);
        assertTrue(policy.getPermissions(pd).implies(sp));

        tp.content = new PermissionGrant[0];
        policy.refresh();
        assertFalse(policy.getPermissions(pd).implies(sp));

        tp.content = null;
        policy.refresh();
        assertFalse(policy.getPermissions(pd).implies(sp));
    }

    /**
     * Tests that refresh() does not fail on failing parser.
     * @throws PolicyInitializationException if test fails.
     */
    public void testRefresh_Failure() throws PolicyInitializationException {
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(null), new PermissionComparator());
        policy.refresh();
        assertFalse(policy.getPermissions(cs).elements().hasMoreElements());
    }

    /**
     * Tests proper policy evaluation for CodeSource parameters.
     * @throws java.lang.Exception if test fails.
     */
    public void testGetPermissions_CodeSource() throws Exception {
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.context(PermissionGrantBuilder.URI);
        CodeSource cs = new CodeSource(null, (Certificate[])null);
        CodeSource cs2 = new CodeSource(new URL("http://a.b.c"),
            (Certificate[])null);
        Permission sp1 = new SecurityPermission("aaa");
        Permission sp2 = new SecurityPermission("bbb");
        Permission sp3 = new SecurityPermission("ccc");
        PermissionGrant pe1 = pgb.uri(null)
                .permissions(new Permission[] { sp1 })
                .build();
        pgb.reset().context(PermissionGrantBuilder.URI);
        PermissionGrant pe2 = pgb.uri("http://a.b.c")
                .principals(new Principal[0])
                .permissions(new Permission[] { sp2 })
                .build();
        pgb.reset().context(PermissionGrantBuilder.URI);
        PermissionGrant pe3 = pgb.uri(null)
                .principals( new Principal[] {new FakePrincipal("qqq") })
                .permissions(new Permission[] { sp3 })
                        .build();
        PermissionGrant[] peArray = new PermissionGrant[] { pe1, pe2, pe3};
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(peArray), new PermissionComparator());
        
        ProtectionDomain pd = new ProtectionDomain(cs, null, null, null);
        ProtectionDomain pd2 = new ProtectionDomain(cs2, null, null, null);

        assertTrue(policy.getPermissions(pd).implies(sp1));
        assertFalse(policy.getPermissions(pd).implies(sp2));
        assertFalse(policy.getPermissions(pd).implies(sp3));

        assertTrue(policy.getPermissions(pd2).implies(sp1));
        assertTrue(policy.getPermissions(pd2).implies(sp2));
        assertFalse(policy.getPermissions(pd2).implies(sp3));
    }

    /**
     * Tests proper policy evaluation for ProtectionDomain parameters.
     * @throws java.lang.Exception if test fails.
     */
    public void testGetPermissions_ProtectionDomain() throws Exception {
        PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.context(PermissionGrantBuilder.URI);
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
        
        PermissionGrant pe1 = pgb.uri(null)
                .permissions(new Permission[] { sp1 })
                .build();
        PermissionGrant pe2 = pgb.uri(cs2.getLocation().toString())
                .principals(new Principal[] { new UnresolvedPrincipal(
                UnresolvedPrincipal.WILDCARD, UnresolvedPrincipal.WILDCARD) })
                .permissions(new Permission[] { sp2 })
                .build();
        PermissionGrant pe3 = pgb.uri(null)
                .principals(new Principal[] { new UnresolvedPrincipal(
                FakePrincipal.class.getName(), "qqq") })
                .permissions(new Permission[] { sp3 })
                .build();
        PermissionGrant pe4 = pgb.uri(cs2.getLocation().toString())
                .principals(new Principal[] { new UnresolvedPrincipal(
                FakePrincipal.class.getName(), "ttt") })
                .permissions(new Permission[] { sp4 })
                .build();
        PermissionGrant[] peArray = new PermissionGrant[]{
            pe1, pe2, pe3, pe4 };
        ConcurrentPolicyFile policy = new ConcurrentPolicyFile(new TestParser(peArray), new PermissionComparator());

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
