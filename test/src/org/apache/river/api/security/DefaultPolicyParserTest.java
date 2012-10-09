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
* 
* @version $Revision$
*/

package org.apache.river.api.security;

import com.sun.jini.start.SharedActivationPolicyPermission;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Properties;
import java.util.SortedSet;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.SecurityPermission;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;
import org.apache.river.api.security.DefaultPolicyScanner.GrantEntry;
import org.apache.river.api.security.DefaultPolicyScanner.PermissionEntry;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
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
       system.setProperty("com.sun.jini.jsk.home", "/opt/src/river/trunk");
       system.setProperty("/", "/");
       system.setProperty("com.sun.jini.qa.harness.harnessJar", "/opt/src/river/trunk/qa/lib/harness.jar");
       pe0 = new PermissionEntry("permission com.sun.jini.start.SharedActivationPolicyPermission", 
               "jar:file:${com.sun.jini.qa.harness.harnessJar}!/harness/policy/sec-jeri-group.policy",
               null, null );
       pe1 = new PermissionEntry("permission com.sun.jini.start.SharedActivationPolicyPermission", 
               "jar:file:${com.sun.jini.qa.harness.harnessJar}!/harness/policy/all.policy",
               null, null );
       pe2 = new PermissionEntry("permission com.sun.jini.start.SharedActivationPolicyPermission", 
               "jar:file:${com.sun.jini.qa.harness.harnessJar}!/harness/policy/policy.all",
               null, null );
       pe3 = new PermissionEntry("permission com.sun.jini.start.SharedActivationPolicyPermission", 
               "jar:file:${com.sun.jini.qa.harness.harnessJar}!/harness/policy/defaultgroup.policy",
               null, null );
       List<PermissionEntry> pec = new ArrayList<PermissionEntry>(4);
       pec.add(0, pe0);
       pec.add(1, pe1);
       pec.add(2, pe2);
       pec.add(3, pe3);
       ge = new GrantEntry( null, "file:${com.sun.jini.jsk.home}${/}lib${/}group.jar", null, pec );
       perm0 = new UnresolvedPermission("permission com.sun.jini.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/sec-jeri-group.policy",
               "", null);
       perm1 = new UnresolvedPermission("permission com.sun.jini.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/all.policy",
               "", null);
       perm2 = new UnresolvedPermission("permission com.sun.jini.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/policy.all",
               "", null);
       perm3 = new UnresolvedPermission("permission com.sun.jini.start.SharedActivationPolicyPermission",
               "jar:file:/opt/src/river/trunk/qa/lib/harness.jar!/harness/policy/defaultgroup.policy",
               "", null);
       permissions = new ArrayList<Permission>(4);
       permissions.add(perm0);
       permissions.add(perm1);
       permissions.add(perm2);
       permissions.add(perm3);
       PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
       URI uri = null;
        try {
            uri = new URI("file:/opt/src/river/trunk/lib/group.jar");
        } catch (URISyntaxException ex) {
            System.err.println(ex);
        }
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
