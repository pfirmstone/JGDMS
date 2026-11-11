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

import java.net.MalformedURLException;
import java.security.CodeSource;
import java.net.URL;
import java.security.ProtectionDomain;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.security.Permission;
import java.security.Principal;
import junit.framework.Assert;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tests.support.MyPrincipal;
import org.apache.river.api.security.*;

/**
 *
 * @author peter
 */
public class URIGrantTest {
    PermissionGrant instance;
    PermissionGrant instance1;
    ProtectionDomain pd;
    ProtectionDomain pd1;
    public URIGrantTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() throws URISyntaxException, MalformedURLException {
        String [] u = new String[2];
        u[0] = "file:/foo/*";
        u[1] = "file:/C:/FOO/*";
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder();
        instance = b.uri(u[0]).uri(u[1]).certificates(new Certificate[0], new String[0])
                .principals(new Principal[0]).permissions(new Permission[0]).context(PermissionGrantBuilder.URI).build();
        instance1 = b.reset().uri("file:/home/lib/harness-killer.jar")
                .certificates(new Certificate[0], new String[0])
                .principals(new Principal[]{new MyPrincipal("CN=Phoenix")})
                .permissions(new Permission[0]).context(PermissionGrantBuilder.URI).build();
        //instance = new URIGrant(u, new Certificate[0], new String[0], new Principal[0], new Permission[0]);
        pd = new ProtectionDomain( new CodeSource(new URL("file:/foo/bar"), (Certificate []) null), null);
//        instance1 = new URIGrant(
//                new String[]{"file:/home/lib/harness-killer.jar"},
//                new Certificate[0],
//                new String[0],
//                new Principal[]{new MyPrincipal("CN=Phoenix")},
//                new Permission[0]
//        );
        pd1 = new ProtectionDomain(
                new CodeSource(new URL("file:/home/lib/harness-killer.jar"), (Certificate[]) null),
                null,
                null,
                new Principal[]{new MyPrincipal("CN=Phoenix")}
        );
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void testImpliesPD() throws URISyntaxException {
        System.out.println("Test implies ProtectionDomain");
        Assert.assertTrue(instance.implies(pd));
        Assert.assertTrue(instance1.implies(pd1));
    }
    private static boolean grantImplies(String grantUri, String codeSourceUrl) throws Exception {
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .context(PermissionGrantBuilder.URI)
                .uri(grantUri)
                .permissions(new Permission[]{new RuntimePermission("uriGrantTest")})
                .build();
        return g.implies(new CodeSource(new URL(codeSourceUrl), (Certificate[]) null), null);
    }

    /**
     * Positive control for {@link #directoryWildcardStopsAtTheDirectoryBoundary()}:
     * "/-" covers the directory and everything below it, "/*" the directory's
     * own entries only. Holds in both copies.
     */
    @Test
    public void directoryWildcardsStillCoverTheirOwnDirectory() throws Exception {
        org.junit.Assert.assertTrue(grantImplies("file:/opt/app/-", "file:/opt/app/x.jar"));
        org.junit.Assert.assertTrue(grantImplies("file:/opt/app/-", "file:/opt/app/lib/y.jar"));
        org.junit.Assert.assertTrue(grantImplies("file:/opt/app/*", "file:/opt/app/x.jar"));
        org.junit.Assert.assertFalse(grantImplies("file:/opt/app/*", "file:/opt/app/lib/y.jar"));
    }

    /**
     * "file:/opt/app/-" must not reach "file:/opt/application/x.jar": the
     * prefix that is compared has to keep the trailing slash, as
     * {@code CodeSource.implies} does. DirtyChai's {@code Uri.implies} compared
     * the prefix without the slash until its commit 0071db760 (2026-10-26, "fix
     * Uri.implies bug"); this module's {@code org.apache.river.api.net.Uri} had
     * the same defect (non-conformance J8, fixed), so the test runs on both
     * copies.
     */
    @Test
    public void directoryWildcardStopsAtTheDirectoryBoundary() throws Exception {
        tests.support.SecurityCopy.assumeCurrentCopy("the Uri.implies prefix fix of DirtyChai 0071db760");
        org.junit.Assert.assertFalse(grantImplies("file:/opt/app/-", "file:/opt/application/x.jar"));
        org.junit.Assert.assertFalse(grantImplies("http://h/foo/-", "http://h/foobar/x"));
        org.junit.Assert.assertFalse(grantImplies("http://h/foo/*", "http://h/foobar"));
    }
}
