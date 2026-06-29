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

package org.apache.river.api.security;

import java.net.URL;
import java.security.CodeSource;
import java.security.KeyStore;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import javax.security.auth.x500.X500Principal;

import junit.framework.TestCase;
import org.apache.river.api.security.DefaultPolicyScanner.GrantEntry;
import org.apache.river.api.security.DefaultPolicyScanner.InvalidFormatException;
import org.apache.river.api.security.DefaultPolicyScanner.PermissionEntry;
import org.apache.river.api.security.*;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests for DefaultPolicyParser
 * 
 */

public class DefaultPolicyParserTest extends TestCase {
    private Properties system;
    private GrantEntry ge;
    private Collection<Permission> permissions;
    private PermissionGrant grant;
    private PermissionEntry pe0, pe1, pe2, pe3;
    private Permission perm0, perm1, perm2, perm3;
    public static void main(String[] args) {
        junit.textui.TestRunner.run(DefaultPolicyParserTest.class);
    }
    
    @Before
    public void setUp(){
       system = new Properties();
       system.setProperty("org.apache.river.jsk.home", "/opt/src/river/trunk");
       system.setProperty("/", "/");
       system.setProperty("org.apache.river.qa.harness.harnessJar", "/opt/src/river/trunk/qa/lib/harness.jar");
       pe0 = new PermissionEntry("permission org.apache.river.start.SharedActivationPolicyPermission", 
               "jar:file:${org.apache.river.qa.harness.harnessJar}!/harness/policy/sec-jeri-group.policy",
               null, null );
       pe1 = new PermissionEntry("permission org.apache.river.start.SharedActivationPolicyPermission", 
               "jar:file:${org.apache.river.qa.harness.harnessJar}!/harness/policy/all.policy",
               null, null );
       pe2 = new PermissionEntry("permission org.apache.river.start.SharedActivationPolicyPermission", 
               "jar:file:${org.apache.river.qa.harness.harnessJar}!/harness/policy/policy.all",
               null, null );
       pe3 = new PermissionEntry("permission org.apache.river.start.SharedActivationPolicyPermission", 
               "jar:file:${org.apache.river.qa.harness.harnessJar}!/harness/policy/defaultgroup.policy",
               null, null );
       List<PermissionEntry> pec = new ArrayList<PermissionEntry>(4);
       pec.add(0, pe0);
       pec.add(1, pe1);
       pec.add(2, pe2);
       pec.add(3, pe3);
       ge = new GrantEntry( null, "file:${org.apache.river.jsk.home}${/}lib${/}group.jar", null, null, pec );
       perm0 = new UnresolvedPermission("permission org.apache.river.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/sec-jeri-group.policy",
               "", null);
       perm1 = new UnresolvedPermission("permission org.apache.river.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/all.policy",
               "", null);
       perm2 = new UnresolvedPermission("permission org.apache.river.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/policy.all",
               "", null);
       perm3 = new UnresolvedPermission("permission org.apache.river.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/defaultgroup.policy",
               "", null);
       permissions = new ArrayList<Permission>(4);
       permissions.add(perm0);
       permissions.add(perm1);
       permissions.add(perm2);
       permissions.add(perm3);
       PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
       String uri = "file:/opt/src/river/trunk/lib/group.jar";
       grant = pgb
               .uri(uri)
               .permissions(permissions.toArray(new Permission[4]))
               .context(PermissionGrantBuilder.URI)
               .build();
    }

    /**
     * Tests parsing of a sample policy from temporary file, validates returned
     * PolicyEntries. 
     * 
     * This test prone to false failure, qa test suite provides more comprehensive
     * test coverage.
     */
//    public void testParse() throws Exception {
//        File tmp = File.createTempFile("policy", null);
//        try {
//        FileWriter out = new FileWriter(tmp);
//        out.write("grant{}KeyStore \"url2\", \"type2\" "
//                + "GRANT signedby \"duke,Li\", codebase\"\", principal a.b.c \"guest\" "
//                + "{permission XXX \"YYY\", SignedBy \"ZZZ\" \n \t };;;"
//                + "GRANT codebase\"http://a.b.c/-\", principal * * "
//                + "{permission java.security.SecurityPermission \"YYY\";}"
//                + "GRANT {permission java.security.SecurityPermission \"ZZZ\";}"
//                + "GRANT {permission java.security.UnresolvedPermission \"NONE\";}");
//        out.flush();
//        out.close();
//
//        DefaultPolicyParser parser = new DefaultPolicyParser();
//        Collection entries = parser.parse(tmp.toURI().toURL(), null);
//        assertEquals(2, entries.size());
//        for (Iterator iter = entries.iterator(); iter.hasNext();) {
//            PermissionGrant element = (PermissionGrant)iter.next();
//            Collection<Permission> permissions = element.getPermissions();
//            if (permissions
//                .contains(new SecurityPermission("ZZZ"))) {
//                assertTrue(element.implies(new CodeSource(null,
//                    (Certificate[])null), null));
//            } else if (permissions
//                .contains(new SecurityPermission("YYY"))) {
//                assertFalse(element.implies((CodeSource) null, (Principal[]) null));
//                assertTrue(element.implies(new CodeSource(new URL(
//                    "http://a.b.c/-"), (Certificate[])null), 
//                    new Principal[] { new FakePrincipal("qqq") }));
//            } else {
//                fail("Extra entry parsed");
//            }
//        }
//        } finally {
//            tmp.delete();
//        }
//    }
    
//    /**
//     * Test of segment method, of class DefaultPolicyParser.
//     */
//    @Test
//    public void testSegment() throws Exception {
//        System.out.println("segment");
//        String s = "";
//        Properties p = null;
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        List expResult = new ArrayList();
//        List result = instance.segment(s, p);
//        assertEquals(expResult, result);
//    }
//
//    /**
//     * Test of expandURLs method, of class DefaultPolicyParser.
//     */
//    @Test
//    public void testExpandURLs() throws Exception {
//        System.out.println("expandURLs");
//        String s = "";
//        Properties p = null;
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        Collection expResult = null;
//        Collection result = instance.expandURLs(s, p);
//        assertEquals(expResult, result);
//    }
//

    /**
     * Test of resolveGrant method, of class DefaultPolicyParser.
     */
//    @Test
//    public void testResolveGrant() throws Exception {
//        System.out.println("resolveGrant");
//        KeyStore ks = null;
//        boolean resolve = true;
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        PermissionGrant expResult = grant;
//        PermissionGrant result = instance.resolveGrant(ge, ks, system, resolve);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
////        fail("The test case is a prototype.");
//    }

    /**
     * Test of resolvePermission method, of class DefaultPolicyParser.
     * @throws Exception if the test fails.
     */
    @Test
    public void testResolvePermission() throws Exception {
        System.out.println("resolvePermission");
        KeyStore ks = null;
        boolean resolve = true;
        DefaultPolicyParser instance = new DefaultPolicyParser();
        Permission expResult = perm0;
        Permission result = instance.resolvePermission(pe0, ge, ks, system, resolve);
        assertEquals(expResult, result);
        expResult = perm1;
        result = instance.resolvePermission(pe1, ge, ks, system, resolve);
        assertEquals(expResult, result);
        expResult = perm2;
        result = instance.resolvePermission(pe2, ge, ks, system, resolve);
        assertEquals(expResult, result);
        expResult = perm3;
        result = instance.resolvePermission(pe3, ge, ks, system, resolve);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
    }

    /**
     * Returns {@code true} when {@code grant} is an instance of
     * {@code DigestGrant} regardless of which class loader served it.
     * <p>
     * A direct {@code instanceof DigestGrant} check cannot be used on
     * DirtyChai: {@code DigestGrant} is package-private inside {@code java.base}
     * and the bootstrap class loader serves it ahead of the classpath copy,
     * making it inaccessible from the unnamed module.
     */
    private static boolean isDigestGrant(PermissionGrant grant) {
        return grant != null
                && "org.apache.river.api.security.DigestGrant"
                        .equals(grant.getClass().getName());
    }

    /**
     * When a GrantEntry carries a digest clause, resolveGrant() must return a
     * DigestGrant rather than a plain URIGrant.
     */
    @Test
    public void testResolveGrantWithDigestProducesDigestGrant() throws Exception {
        System.out.println("testResolveGrantWithDigestProducesDigestGrant");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        // SHA-256 of a single zero byte
        String hexDigest = "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d";
        GrantEntry digestGe = new GrantEntry(null, "file:/app/lib.jar",
                "SHA-256:" + hexDigest, null, null);
        PermissionGrant result = parser.resolveGrant(digestGe, null, system, false);
        assertNotNull(result);
        assertTrue("resolveGrant with digest clause must return DigestGrant",
                isDigestGrant(result));
    }

    /**
     * A GrantEntry without a digest must still produce a URIGrant (not a DigestGrant).
     */
    @Test
    public void testResolveGrantWithoutDigestProducesURIGrant() throws Exception {
        System.out.println("testResolveGrantWithoutDigestProducesURIGrant");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        GrantEntry plainGe = new GrantEntry(null, "file:/app/lib.jar",
                null, null, null);
        PermissionGrant result = parser.resolveGrant(plainGe, null, system, false);
        assertNotNull(result);
        assertFalse("resolveGrant without digest clause must NOT return DigestGrant",
                isDigestGrant(result));
    }

    /**
     * Uppercase hex digits must be accepted by hexDecode.
     */
    @Test
    public void testResolveGrantUppercaseHexAccepted() throws Exception {
        System.out.println("testResolveGrantUppercaseHexAccepted");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        GrantEntry upperGe = new GrantEntry(null, null, "SHA-256:DEADBEEF", null, null);
        PermissionGrant result = parser.resolveGrant(upperGe, null, system, false);
        assertNotNull(result);
        assertTrue(isDigestGrant(result));
    }

    /**
     * Mixed-case hex digits must be accepted.
     */
    @Test
    public void testResolveGrantMixedCaseHexAccepted() throws Exception {
        System.out.println("testResolveGrantMixedCaseHexAccepted");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        GrantEntry mixedGe = new GrantEntry(null, null, "SHA-256:DeAdBeEf", null, null);
        PermissionGrant result = parser.resolveGrant(mixedGe, null, system, false);
        assertNotNull(result);
        assertTrue(isDigestGrant(result));
    }

    /**
     * Compared by class name (not instanceof) for the same split-package reason
     * as {@link #isDigestGrant}: under DirtyChai the grant classes are embedded
     * in java.base while this test is loaded from the unnamed module, so a
     * java.base-resolved grant is not an {@code instanceof} the classpath copy.
     */
    private static boolean isPrincipalGrant(PermissionGrant grant) {
        return grant != null
                && "org.apache.river.api.security.PrincipalGrant"
                        .equals(grant.getClass().getName());
    }

    private static boolean isURIGrant(PermissionGrant grant) {
        return grant != null
                && "org.apache.river.api.security.URIGrant"
                        .equals(grant.getClass().getName());
    }

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
        System.out.println("testResolveGrantPrincipalOnlyProducesPrincipalGrant");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        Collection<DefaultPolicyScanner.PrincipalEntry> pe =
                new ArrayList<DefaultPolicyScanner.PrincipalEntry>();
        pe.add(new DefaultPolicyScanner.PrincipalEntry(
                "javax.security.auth.x500.X500Principal", "CN=Test"));
        GrantEntry principalGe = new GrantEntry(null, null, null, pe, null);
        PermissionGrant grant = parser.resolveGrant(principalGe, null, system, false);
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
     * A BARE grant (no codeBase, no signedBy, no digest, no principals) must
     * resolve to a URIGrant (a codebase wildcard), NOT a PrincipalGrant -- so it
     * applies to codebase-bearing code but NOT to a null-CodeSource domain,
     * preserving the "null CodeSource is unprivileged" invariant (HC-5).
     *
     * <p>The scanner supplies a non-null EMPTY principals collection for a bare
     * grant, so this reproduces and guards against the regression where the
     * guard {@code ge.getPrincipals(null) != null} (always true) routed bare
     * grants into the PRINCIPAL branch, yielding a PrincipalGrant with empty
     * principals whose {@code implies} returns true for every domain.
     */
    @Test
    public void testResolveGrantBareProducesURIGrantAndExcludesNullCodeSource() throws Exception {
        System.out.println("testResolveGrantBareProducesURIGrantAndExcludesNullCodeSource");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        // Non-null empty principals, exactly as DefaultPolicyScanner yields for a bare grant.
        GrantEntry bareGe = new GrantEntry(null, null, null,
                new ArrayList<DefaultPolicyScanner.PrincipalEntry>(), null);
        PermissionGrant grant = parser.resolveGrant(bareGe, null, system, false);
        assertNotNull(grant);
        assertTrue("bare grant must resolve to a URIGrant, not a PrincipalGrant",
                isURIGrant(grant));
        assertFalse("bare grant must NOT match a null-CodeSource domain",
                grant.implies(new ProtectionDomain(null, null, null,
                        new Principal[]{ new X500Principal("CN=Test") })));
        // A bare grant is an empty-URI wildcard: it matches any non-null CodeSource.
        CodeSource cs = new CodeSource(new URL("file:/app/lib.jar"), (Certificate[]) null);
        assertTrue("bare grant (codebase wildcard) must match a codebase-bearing domain",
                grant.implies(new ProtectionDomain(cs, null, null, null)));
    }

    /**
     * A codeBase grant must remain a URIGrant and must not match a
     * null-CodeSource domain, regardless of the domain's principals (HC-5).
     */
    @Test
    public void testResolveGrantCodebaseExcludesNullCodeSource() throws Exception {
        System.out.println("testResolveGrantCodebaseExcludesNullCodeSource");
        DefaultPolicyParser parser = new DefaultPolicyParser();
        GrantEntry cbGe = new GrantEntry(null, "file:/app/lib.jar", null, null, null);
        PermissionGrant grant = parser.resolveGrant(cbGe, null, system, false);
        assertNotNull(grant);
        assertTrue(isURIGrant(grant));
        Principal[] p = { new X500Principal("CN=Test") };
        assertFalse("codeBase grant must NOT match a null-CodeSource domain",
                grant.implies(new ProtectionDomain(null, null, null, p)));
    }

//    /**
//     * Test of resolveSigners method, of class DefaultPolicyParser.
//     */
//    @Test
//    public void testResolveSigners() throws Exception {
//        System.out.println("resolveSigners");
//        KeyStore ks = null;
//        String signers = "";
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        Certificate[] expResult = null;
//        Certificate[] result = instance.resolveSigners(ks, signers);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

//    /**
//     * Test of getPrincipalByAlias method, of class DefaultPolicyParser.
//     */
//    @Test
//    public void testGetPrincipalByAlias() throws Exception {
//        System.out.println("getPrincipalByAlias");
//        KeyStore ks = null;
//        String alias = "";
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        Principal expResult = null;
//        Principal result = instance.getPrincipalByAlias(ks, alias);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of initKeyStore method, of class DefaultPolicyParser.
//     */
//    @Test
//    public void testInitKeyStore() {
//        System.out.println("initKeyStore");
//        List<KeystoreEntry> keystores = null;
//        URL base = null;
//        Properties system = null;
//        boolean resolve = false;
//        DefaultPolicyParser instance = new DefaultPolicyParser();
//        KeyStore expResult = null;
//        KeyStore result = instance.initKeyStore(keystores, base, system, resolve);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
}

class FakePrincipal implements Principal {

    private String name;

    public FakePrincipal(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
