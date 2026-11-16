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

import java.net.URL;
import java.security.CodeSource;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import javax.security.auth.x500.X500Principal;
import org.apache.river.api.security.DefaultPolicyScanner.GrantEntry;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.SecurityCopy;

/**
 * The grant that {@link DefaultPolicyParser} resolves from a grant entry that
 * has principals only, which is a JUnit 4 class of its own, apart from
 * {@link DefaultPolicyParserTest}, so that it can be skipped: an older copy of
 * the grant classes in the JDK does not have the behaviour tested.
 */
public class PrincipalOnlyGrantParserTest {

    /**
     * A grant whose only discriminator is principals (no codeBase, no signedBy,
     * no digest) must resolve to a PrincipalGrant, which matches by principal
     * alone -- including a domain whose CodeSource is {@code null}. This is the
     * §7.3 two-gate requirement that a reducing-context domain may "use only the
     * Principal": a codebase-less authenticated frame must still satisfy a
     * principal grant. Regression for the parser previously mis-building such a
     * grant as a URIGrant (whose matcher rejects a null CodeSource).
     */
    @Test
    public void testResolveGrantPrincipalOnlyProducesPrincipalGrant() throws Exception {
        SecurityCopy.assumeCurrentCopy("a principal grant that implies a domain without a CodeSource");
        System.out.println("testResolveGrantPrincipalOnlyProducesPrincipalGrant");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        Collection<DefaultPolicyScanner.PrincipalEntry> pe =
                new ArrayList<DefaultPolicyScanner.PrincipalEntry>();
        pe.add(new DefaultPolicyScanner.PrincipalEntry(
                "javax.security.auth.x500.X500Principal", "CN=Test"));
        GrantEntry principalGe = new GrantEntry(null, null, null, pe, null);
        PermissionGrant grant = parser.resolveGrant(principalGe, null, new Properties(), false);
        assertNotNull(grant);
        assertTrue("principal-only grant must resolve to a PrincipalGrant",
                isPrincipalGrant(grant));
        Principal[] match = { new X500Principal("CN=Test") };
        Principal[] other = { new X500Principal("CN=Other") };
        // Matches a null-CodeSource domain carrying the principal -- the case the
        // mis-categorised URIGrant rejected.
        assertTrue("principal grant must match a null-CodeSource domain with the principal",
                grant.implies(new ProtectionDomain(null, null, null, match)));
        // PrincipalGrant ignores CodeSource, so a real-CS domain matches too.
        CodeSource cs = new CodeSource(new URL("file:/app/lib.jar"), (Certificate[]) null);
        assertTrue(grant.implies(new ProtectionDomain(cs, null, null, match)));
        // ...but a different principal does not match.
        assertFalse(grant.implies(new ProtectionDomain(null, null, null, other)));
    }

    /**
     * Compared by class name, not instanceof: the grant classes may be those
     * of a JDK, in java.base, where the package-private ones cannot be named.
     */
    private static boolean isPrincipalGrant(PermissionGrant grant) {
        return grant != null
                && "org.apache.river.api.security.PrincipalGrant"
                        .equals(grant.getClass().getName());
    }
}
