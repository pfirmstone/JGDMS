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

import java.security.Permission;
import java.security.PermissionCollection;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link AssertPrincipalPermission} grants only what its class and name
 * pattern name, and nothing to another permission type.
 */
public class AssertPrincipalPermissionTest {

    private static final String X500 = "javax.security.auth.x500.X500Principal";
    private static final String KRB = "javax.security.auth.kerberos.KerberosPrincipal";

    private static AssertPrincipalPermission p(String c, String n) {
        return new AssertPrincipalPermission(c, n);
    }

    @Test
    public void exactGrantImpliesOnlyThatPrincipal() {
        Assert.assertTrue(p(X500, "CN=alice").implies(p(X500, "CN=alice")));
        Assert.assertFalse(p(X500, "CN=alice").implies(p(X500, "CN=bob")));
        Assert.assertFalse(p(X500, "CN=alice").implies(p(KRB, "CN=alice")));
    }

    @Test
    public void wildcardNameCoversAnyNameOfThatClassOnly() {
        Assert.assertTrue(p(X500, "*").implies(p(X500, "CN=anyone")));
        Assert.assertFalse(p(X500, "*").implies(p(KRB, "x@R")));
    }

    @Test
    public void wildcardClassCoversAnyClass() {
        Assert.assertTrue(p("*", "*").implies(p(KRB, "x@R")));
        Assert.assertTrue(p("*", "CN=alice").implies(p(X500, "CN=alice")));
        Assert.assertFalse(p("*", "CN=alice").implies(p(X500, "CN=bob")));
    }

    @Test
    public void prefixAndSuffixPatterns() {
        // a wildcard in the middle is not a pattern: it is an exact name
        Assert.assertFalse(p(X500, "CN=*,O=Example").implies(p(X500, "CN=alice,O=Example")));
        Assert.assertTrue(p(X500, "CN=a*").implies(p(X500, "CN=alice,O=Example")));
        Assert.assertFalse(p(X500, "CN=a*").implies(p(X500, "CN=bob,O=Example")));
        Assert.assertTrue(p(X500, "*,O=Example").implies(p(X500, "CN=alice,O=Example")));
        Assert.assertFalse(p(X500, "*,O=Example").implies(p(X500, "CN=alice,O=Other")));
    }

    @Test
    public void doesNotImplyOtherPermissionTypes() {
        Assert.assertFalse(p("*", "*").implies(new RuntimePermission("exitVM")));
        Assert.assertFalse(p("*", "*").implies(new java.security.AllPermission()));
    }

    @Test
    public void collectionImpliesWhenAnyMemberDoes() {
        PermissionCollection c = p(X500, "x").newPermissionCollection();
        c.add(p(X500, "CN=alice"));
        c.add(p(KRB, "*"));
        Assert.assertTrue(c.implies(p(X500, "CN=alice")));
        Assert.assertTrue(c.implies(p(KRB, "z@R")));
        Assert.assertFalse(c.implies(p(X500, "CN=bob")));
        Assert.assertFalse(c.implies((Permission) new RuntimePermission("exitVM")));
    }

    @Test
    public void equalsHashCodeAndActions() {
        Assert.assertEquals(p(X500, "CN=a"), p(X500, "CN=a"));
        Assert.assertEquals(p(X500, "CN=a").hashCode(), p(X500, "CN=a").hashCode());
        Assert.assertNotEquals(p(X500, "CN=a"), p(X500, "CN=b"));
        Assert.assertNotEquals(p(X500, "CN=a"), p(KRB, "CN=a"));
        Assert.assertEquals("CN=a", p(X500, "CN=a").getActions());
        Assert.assertEquals(X500, p(X500, "CN=a").getName());
    }

    @Test(expected = NullPointerException.class)
    public void nullClassIsRefused() { p(null, "x"); }

    @Test(expected = NullPointerException.class)
    public void nullNameIsRefused() { p(X500, null); }

    @Test(expected = IllegalArgumentException.class)
    public void emptyNameIsRefused() { p(X500, ""); }
}
