/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.security.Principal;
import javax.security.auth.x500.X500Principal;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * UnresolvedPrincipal.implies requires the class to match exactly; only the
 * name may be the wildcard.  A definition whose class is "*" implies no real
 * principal.  Before 2026-11-01 the condition was parsed as
 * wildcardClass || (classMatch &amp;&amp; nameMatch), so "principal * name"
 * matched every principal whatever its name.  Mirrors the DirtyChai test
 * UnresolvedPrincipalImpliesTest (test/jdk/ai/org/apache/river/api/security).
 */
public class UnresolvedPrincipalImpliesTest {

    private static final class Other implements Principal {
        private final String name;
        Other(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    private final String x500 = X500Principal.class.getName();
    private final Principal alice = new X500Principal("CN=alice");
    private final Principal bob = new X500Principal("CN=bob");
    private final Principal otherAlice = new Other("CN=alice");
    private final Principal otherBob = new Other("CN=bob");

    @Test
    public void classWildcardImpliesNothing() {
        UnresolvedPrincipal anyAny = new UnresolvedPrincipal("*", "*");
        assertFalse(anyAny.implies(alice));
        assertFalse(anyAny.implies(otherBob));
        assertFalse(anyAny.implies((Principal) null));
    }

    @Test
    public void classWildcardWithNameImpliesNothing() {
        UnresolvedPrincipal anyAlice = new UnresolvedPrincipal("*", "CN=alice");
        assertFalse(anyAlice.implies(alice));
        assertFalse(anyAlice.implies(otherAlice));
        assertFalse("the original defect: name must still be tested", anyAlice.implies(bob));
        assertFalse(anyAlice.implies(otherBob));
    }

    @Test
    public void exactClassWithWildcardNameImpliesAnyNameOfThatClassOnly() {
        UnresolvedPrincipal x500Any = new UnresolvedPrincipal(x500, "*");
        assertTrue(x500Any.implies(alice));
        assertTrue(x500Any.implies(bob));
        assertFalse(x500Any.implies(otherAlice));
        assertFalse(x500Any.implies((Principal) null));
    }

    @Test
    public void exactClassAndNameBothMustMatch() {
        UnresolvedPrincipal x500Alice = new UnresolvedPrincipal(x500, "CN=alice");
        assertTrue(x500Alice.implies(alice));
        assertFalse(x500Alice.implies(bob));
        assertFalse(x500Alice.implies(otherAlice));
        assertFalse(x500Alice.implies(otherBob));
    }

    @Test
    public void nullNameMatchesOnlyANullNamedPrincipal() {
        UnresolvedPrincipal nullName = new UnresolvedPrincipal(Other.class.getName(), null);
        assertTrue(nullName.implies(new Other(null)));
        assertFalse(nullName.implies(otherAlice));
    }

    @Test
    public void classIsComparedExactlyNotAsAPrefix() {
        UnresolvedPrincipal prefix = new UnresolvedPrincipal("javax.security.auth.x500", "CN=alice");
        assertFalse(prefix.implies(alice));
    }
}
