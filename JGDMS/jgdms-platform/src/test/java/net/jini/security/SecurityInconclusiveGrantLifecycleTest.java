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

package net.jini.security;

import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.ProtectionDomain;
import net.jini.security.policy.DynamicPolicyProvider;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityInconclusiveGrantLifecycleTest {

    private Policy originalPolicy;

    @After
    public void tearDown() {
        Security.invalidateInconclusiveProxyLoaderGrants();
        if (originalPolicy != null) {
            Policy.setPolicy(originalPolicy);
            originalPolicy = null;
        }
    }

    @Test
    public void retainedInconclusiveGrantsAreVoidedOnLaterGrant() {
        originalPolicy = Policy.getPolicy();
        DynamicPolicyProvider policy =
                new DynamicPolicyProvider(new EmptyPolicy());
        Policy.setPolicy(policy);

        Class<?> proxyClass = SecurityInconclusiveGrantLifecycleTest.class;
        Security.markInconclusiveProxyClassLoader(proxyClass.getClassLoader());

        Permission p1 = new RuntimePermission("inconclusive-grant-1");
        Security.grant(proxyClass, new Principal[0], new Permission[]{p1});
        Permission[] first = policy.getGrants(proxyClass, new Principal[0]);
        assertTrue(containsRuntimePermission(first, "inconclusive-grant-1"));

        Permission p2 = new RuntimePermission("inconclusive-grant-2");
        Security.grant(proxyClass, new Principal[0], new Permission[]{p2});
        Permission[] second = policy.getGrants(proxyClass, new Principal[0]);
        assertFalse(containsRuntimePermission(second, "inconclusive-grant-1"));
        assertTrue(containsRuntimePermission(second, "inconclusive-grant-2"));
    }

    @Test
    public void retainedInconclusiveGrantIsLoaderWideAndVoidable() throws Exception {
        originalPolicy = Policy.getPolicy();
        DynamicPolicyProvider policy =
                new DynamicPolicyProvider(new EmptyPolicy());
        Policy.setPolicy(policy);

        Class<?> proxyClass = SecurityInconclusiveGrantLifecycleTest.class;
        Security.markInconclusiveProxyClassLoader(proxyClass.getClassLoader());

        Permission p = new RuntimePermission("inconclusive-domain-scoped");
        Security.grant(proxyClass, new Principal[0], new Permission[]{p});

        // The retained grant is for the ClassLoader, an inconclusive verdict
        // is not verified code.
        ProtectionDomain pd = proxyClass.getProtectionDomain();
        assertTrue(grantsContain(policy.getPermissionGrants(pd), p));
        ProtectionDomain elsewhere = new ProtectionDomain(
                new CodeSource(new java.net.URL("file:/not/in/the/codebase.jar"), 
                        (java.security.cert.Certificate[]) null),
                null, proxyClass.getClassLoader(), new Principal[0]);
        assertTrue(grantsContain(policy.getPermissionGrants(elsewhere), p));

        Security.invalidateInconclusiveProxyLoaderGrants();
        assertFalse("voided", grantsContain(policy.getPermissionGrants(pd), p));
    }

    private static boolean grantsContain(
            java.util.List<org.apache.river.api.security.PermissionGrant> grants, 
            Permission p) 
    {
        for (org.apache.river.api.security.PermissionGrant g : grants) {
            if (g.getPermissions().contains(p)) return true;
        }
        return false;
    }

    private static boolean containsRuntimePermission(Permission[] perms, String name) {
        for (Permission p : perms) {
            if (p instanceof RuntimePermission && name.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    private static final class EmptyPolicy extends Policy {
        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            return new Permissions();
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new Permissions();
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            return false;
        }

        @Override
        public void refresh() {
        }
    }
}
