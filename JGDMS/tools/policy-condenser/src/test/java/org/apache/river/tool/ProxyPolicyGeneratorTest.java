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

package org.apache.river.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.regex.Pattern;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.AdvisoryPermissionParser;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link ProxyPolicyGenerator}: proxy detection by codebase
 * convention, round-tripping of the generated {@code GrantPermission} ceiling
 * (via {@link DefaultPolicyParser}) and {@code PERMISSIONS.LIST}
 * (via {@link AdvisoryPermissionParser}), minimality, meta-permission
 * exclusion, and fail-closed behaviour.
 */
public class ProxyPolicyGeneratorTest {

    private File tempDir;

    @Before
    public void setUp() {
        tempDir = new File(
            System.getProperty("java.io.tmpdir"),
            "proxy-policy-gen-test-" + Thread.currentThread().getId() + "-" + System.nanoTime());
        tempDir.mkdirs();
    }

    @After
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    // ---------------------------------------------------------------- tests

    /**
     * A {@code *-dl.jar} codebase is recognised as a proxy; a normal jar is
     * not.  The ceiling round-trips through the policy parser and admits
     * exactly the proxy's permissions, and the manifest round-trips through the
     * advisory parser.
     */
    @Test
    public void testDlCodebaseDetectedCeilingAndManifest() throws Exception {
        File policy = writePolicy("audit.policy",
            "grant codebase \"file:/lib/mahalo-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.util.PropertyPermission \"java.home\", \"read\";",
            "};",
            "grant codebase \"file:/lib/mahalo.jar\" {",
            "    permission java.lang.RuntimePermission \"exitVM\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals("exactly one proxy codebase detected", 1, r.proxyCount());

        // --- ceiling round-trips and is minimal ---
        File ceiling = new File(policy.getAbsolutePath() + ".grantperm");
        assertTrue("ceiling fragment written", ceiling.exists());
        List<GrantPermission> gps = parseCeiling(ceiling);
        assertEquals("one GrantPermission ceiling", 1, gps.size());
        GrantPermission gp = gps.get(0);
        assertTrue("admits exercised RuntimePermission",
            gp.implies(new GrantPermission(new Permission[]{
                new RuntimePermission("getClassLoader")})));
        assertTrue("admits exercised PropertyPermission",
            gp.implies(new GrantPermission(new Permission[]{
                new PropertyPermission("java.home", "read")})));
        assertFalse("does NOT admit a permission the proxy never exercised",
            gp.implies(new GrantPermission(new Permission[]{
                new RuntimePermission("exitVM")})));

        // --- manifest round-trips ---
        File list = new File(outDir, "mahalo-dl/META-INF/PERMISSIONS.LIST");
        assertTrue("PERMISSIONS.LIST written for proxy artefact", list.exists());
        Set<String> perms = parseManifest(list);
        assertEquals("manifest has the two exercised permissions", 2, perms.size());
        assertTrue(perms.contains("java.lang.RuntimePermission|getClassLoader|"));
        assertTrue(perms.contains("java.util.PropertyPermission|java.home|read"));
    }

    /** The {@code httpmd:} codebase scheme is recognised as a proxy. */
    @Test
    public void testHttpmdCodebaseDetected() throws Exception {
        File policy = writePolicy("httpmd.policy",
            "grant codebase \"httpmd://host/reggie-dl.jar;sha-256=abc123\" {",
            "    permission java.lang.RuntimePermission \"getProtectionDomain\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals(1, r.proxyCount());
        File list = new File(outDir, "reggie-dl/META-INF/PERMISSIONS.LIST");
        assertTrue("artefact name derived from httpmd codebase", list.exists());
        Set<String> perms = parseManifest(list);
        assertEquals(1, perms.size());
        assertTrue(perms.contains("java.lang.RuntimePermission|getProtectionDomain|"));
    }

    /**
     * {@code GrantPermission}, {@code AllPermission} and {@code
     * UmbrellaGrantPermission} meta-permissions are excluded from the
     * declared-needs manifest and the ceiling.
     */
    @Test
    public void testMetaPermissionsExcluded() throws Exception {
        File policy = writePolicy("meta.policy",
            "grant codebase \"file:/lib/norm-dl.jar\" {",
            "    permission java.security.AllPermission;",
            "    permission net.jini.security.GrantPermission \"java.lang.RuntimePermission \\\"getClassLoader\\\"\";",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);
        assertEquals(1, r.proxyCount());

        File list = new File(outDir, "norm-dl/META-INF/PERMISSIONS.LIST");
        Set<String> perms = parseManifest(list);
        assertEquals("only the non-meta permission survives", 1, perms.size());
        assertTrue(perms.contains("java.lang.RuntimePermission|getClassLoader|"));

        List<GrantPermission> gps = parseCeiling(new File(policy.getAbsolutePath() + ".grantperm"));
        assertEquals(1, gps.size());
        // AllPermission would, if present, imply everything: assert it does NOT.
        assertFalse("ceiling must not have absorbed AllPermission",
            gps.get(0).implies(new GrantPermission(new Permission[]{
                new RuntimePermission("exitVM")})));
    }

    /** No proxy-targeted grant means no output is written (fail-closed). */
    @Test
    public void testFailClosedNoProxyNoOutput() throws Exception {
        File policy = writePolicy("noproxy.policy",
            "grant codebase \"file:/lib/service.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals(0, r.proxyCount());
        assertFalse("no ceiling fragment for a non-proxy policy",
            new File(policy.getAbsolutePath() + ".grantperm").exists());
        assertFalse("no manifest tree created", outDir.exists());
    }

    /**
     * A proxy codebase that exercised only meta permissions has no declared
     * needs, so nothing is emitted (fail-closed).
     */
    @Test
    public void testProxyWithOnlyMetaPermsNoOutput() throws Exception {
        File policy = writePolicy("onlymeta.policy",
            "grant codebase \"file:/lib/foo-dl.jar\" {",
            "    permission java.security.AllPermission;",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals(0, r.proxyCount());
        assertFalse(new File(policy.getAbsolutePath() + ".grantperm").exists());
    }

    /**
     * In strict mode a proxy must also carry a corroborating proxy-shape
     * permission (e.g. {@code AccessPermission}) to be classified as proxy.
     */
    @Test
    public void testStrictRequiresProxyShape() throws Exception {
        Pattern pat = Pattern.compile(ProxyPolicyGenerator.DEFAULT_PROXY_REGEX);

        File noShape = writePolicy("strict-noshape.policy",
            "grant codebase \"file:/lib/bar-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        ProxyPolicyGenerator strict =
            new ProxyPolicyGenerator(pat, true, java.util.Collections.<String, String>emptyMap());
        assertEquals("strict: no proxy-shape => not a proxy",
            0, strict.generate(noShape, new File(tempDir, "o1")).proxyCount());

        File withShape = writePolicy("strict-shape.policy",
            "grant codebase \"file:/lib/bar-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission net.jini.security.AccessPermission \"someMethod\";",
            "};");
        assertEquals("strict: proxy-shape present => proxy",
            1, strict.generate(withShape, new File(tempDir, "o2")).proxyCount());
    }

    /**
     * Permission names with backslashes (Windows file paths) round-trip through
     * both the manifest encoder and the ceiling, and the ceiling fragment still
     * parses.
     */
    @Test
    public void testEscapingRoundTrips() throws Exception {
        File policy = writePolicy("escaping.policy",
            "grant codebase \"file:/lib/outrigger-dl.jar\" {",
            "    permission java.io.FilePermission \"C:\\\\work\\\\-\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);
        assertEquals(1, r.proxyCount());

        File list = new File(outDir, "outrigger-dl/META-INF/PERMISSIONS.LIST");
        Set<String> perms = parseManifest(list);
        assertEquals(1, perms.size());
        assertTrue("backslash path round-trips through the manifest encoder",
            perms.contains("java.io.FilePermission|C:\\work\\-|read"));

        // The ceiling must still parse (escaping is correct for the policy grammar).
        List<GrantPermission> gps = parseCeiling(new File(policy.getAbsolutePath() + ".grantperm"));
        assertEquals(1, gps.size());
    }

    /**
     * Several grant blocks for the same proxy codebase are merged into one
     * manifest and one ceiling.
     */
    @Test
    public void testMultipleBlocksSameCodebaseMerged() throws Exception {
        File policy = writePolicy("merge.policy",
            "grant codebase \"file:/lib/fiddler-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};",
            "grant codebase \"file:/lib/fiddler-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getProtectionDomain\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);
        assertEquals("one merged proxy codebase", 1, r.proxyCount());

        Set<String> perms = parseManifest(
            new File(outDir, "fiddler-dl/META-INF/PERMISSIONS.LIST"));
        assertEquals(2, perms.size());
        assertTrue(perms.contains("java.lang.RuntimePermission|getClassLoader|"));
        assertTrue(perms.contains("java.lang.RuntimePermission|getProtectionDomain|"));
    }

    /**
     * A policy file written with a leading UTF-8 BOM must still be parsed
     * (StreamTokenizer treats U+FEFF as a word char, which would otherwise fuse
     * it onto the first {@code grant} keyword).
     */
    @Test
    public void testBomPrefixedPolicyParsed() throws Exception {
        File policy = new File(tempDir, "bom.policy");
        String body =
            "grant codebase \"file:/lib/mercury-dl.jar\" {\n"
            + "    permission java.lang.RuntimePermission \"getClassLoader\";\n"
            + "};\n";
        java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
            new java.io.FileOutputStream(policy), "UTF-8");
        try {
            w.write(0xFEFF); // BOM (Writer.write(int) writes the low 16 bits)
            w.write(body);
        } finally {
            w.close();
        }
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals("BOM-prefixed policy must still be parsed", 1, r.proxyCount());
        assertTrue(new File(outDir, "mercury-dl/META-INF/PERMISSIONS.LIST").exists());
    }

    // -------------------------------------------------------------- helpers

    private File writePolicy(String name, String... lines) throws Exception {
        File f = new File(tempDir, name);
        PrintWriter pw = new PrintWriter(new FileWriter(f));
        try {
            for (String l : lines) pw.println(l);
        } finally {
            pw.close();
        }
        return f;
    }

    /** Collects every {@link GrantPermission} from a parsed policy fragment. */
    private static List<GrantPermission> parseCeiling(File f) throws Exception {
        PolicyParser parser = new DefaultPolicyParser();
        Collection<PermissionGrant> grants =
            parser.parse(f.toURI().toURL(), new Properties());
        List<GrantPermission> result = new ArrayList<GrantPermission>();
        for (PermissionGrant g : grants) {
            for (Permission p : g.getPermissions()) {
                if (p instanceof GrantPermission) result.add((GrantPermission) p);
            }
        }
        return result;
    }

    /**
     * Parses a {@code PERMISSIONS.LIST} into a set of
     * {@code class|name|actions} keys, skipping {@code #} / {@code //} comments
     * and blank lines.
     */
    private static Set<String> parseManifest(File f) throws Exception {
        Set<String> result = new HashSet<String>();
        ClassLoader loader = ProxyPolicyGeneratorTest.class.getClassLoader();
        BufferedReader br = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
                Permission p = AdvisoryPermissionParser.parse(t, loader);
                String actions = p.getActions() == null ? "" : p.getActions();
                result.add(p.getClass().getName() + "|" + p.getName() + "|" + actions);
            }
        } finally {
            br.close();
        }
        return result;
    }

    private static void deleteRecursively(File f) {
        if (f == null) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
