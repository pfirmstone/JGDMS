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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.security.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.TreeMap;
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
     * parses.  The path is relative: an absolute one is held back from the list
     * as an audit-host path (see testHostAndRunSpecificEntriesAreHeldBack).
     */
    @Test
    public void testEscapingRoundTrips() throws Exception {
        File policy = writePolicy("escaping.policy",
            "grant codebase \"file:/lib/outrigger-dl.jar\" {",
            "    permission java.io.FilePermission \"work\\\\data\\\\-\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator().generate(policy, outDir);
        assertEquals(1, r.proxyCount());

        File list = new File(outDir, "outrigger-dl/META-INF/PERMISSIONS.LIST");
        Set<String> perms = parseManifest(list);
        assertEquals(1, perms.size());
        assertTrue("backslash path round-trips through the manifest encoder",
            perms.contains("java.io.FilePermission|work\\data\\-|read"));

        // The written text, not only the entry count: a list line with each
        // backslash doubled, and a ceiling line escaped twice (once for the
        // GrantPermission grammar, once for the policy string it sits in).
        assertEquals(Arrays.asList("(java.io.FilePermission \"work\\\\data\\\\-\" \"read\")"),
            liveLines(list));
        String ceilingText = readFile(new File(policy.getAbsolutePath() + ".grantperm"));
        assertTrue(ceilingText, ceilingText.contains(
            "permission net.jini.security.GrantPermission "
            + "\"java.io.FilePermission \\\"work\\\\\\\\data\\\\\\\\-\\\", \\\"read\\\";\";"));

        // The ceiling must still parse, and admit exactly that path.
        List<GrantPermission> gps = parseCeiling(new File(policy.getAbsolutePath() + ".grantperm"));
        assertEquals(1, gps.size());
        assertTrue("the parsed ceiling admits the backslash path",
            gps.get(0).implies(new GrantPermission(new Permission[]{
                new java.io.FilePermission("work\\data\\-", "read")})));
        assertFalse("and not the path with the backslashes lost",
            gps.get(0).implies(new GrantPermission(new Permission[]{
                new java.io.FilePermission("workdata-", "read")})));
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

    // ------------------------------------- list safety: gate, host/run, provenance

    private static final String RUNTIME_ENTRY = "(java.lang.RuntimePermission \"getClassLoader\")";

    /**
     * Invariant: a proxy jar never requests its own admission.  LoadClassPermission
     * (both recorded names), URLPermission and DownloadPermission are left out of
     * the list, each reported on the result and as a comment; neighbours in the
     * same package stay.
     */
    @Test
    public void testGatePermissionsAreNeverEmitted() throws Exception {
        File policy = writePolicy("gate.policy",
            "grant codebase \"file:/lib/gate-dl.jar\" {",
            "    permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";",
            "    permission net.jini.loader.LoadClassPermission \"ALLOW\";",
            "    permission java.net.URLPermission \"http://host/-\", \"GET:\";",
            "    permission net.jini.loader.DownloadPermission;",
            "    permission au.zeus.jdk.authorization.guards.SerialObjectPermission \"org.example.Kept\";",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);

        File list = new File(outDir, "gate-dl/META-INF/PERMISSIONS.LIST");
        assertEquals("only the non-gate entries are live",
            new HashSet<String>(Arrays.asList(RUNTIME_ENTRY,
                "(au.zeus.jdk.authorization.guards.SerialObjectPermission \"org.example.Kept\")")),
            new HashSet<String>(liveLines(list)));
        assertEquals("each gate entry is recorded in a comment", 4,
            linesStartingWith(list, "# DROPPED").size());
        assertEquals(4, r.droppedCount());
        assertEquals(4, r.notes().size());
        for (String note : r.notes()) {
            assertTrue(note, note.startsWith("DROPPED gate-dl: ("));
        }
        assertTrue("the entry text is in the comment",
            readFile(list).contains("# DROPPED (admission-gate permission: the proxy provider "
                + "grants it after verifying the codebase, a jar must not request its own "
                + "admission): (java.net.URLPermission \"http://host/-\" \"GET:\")"));
    }

    /**
     * Invariant: an entry that only makes sense on the audit host or in the audit
     * run is never live.  Absolute Unix, Windows and UNC paths, file: URLs and
     * generated proxy class names are written commented out with a reason; a
     * relative path and a normal class name stay live.
     */
    @Test
    public void testHostAndRunSpecificEntriesAreHeldBack() throws Exception {
        File policy = writePolicy("host.policy", hostPolicyLines());
        File outDir = new File(tempDir, "out");

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);

        File list = new File(outDir, "host-dl/META-INF/PERMISSIONS.LIST");
        assertEquals(Arrays.asList(
                "(java.io.FilePermission \"data/relative.txt\" \"read\")", RUNTIME_ENTRY),
            liveLines(list));
        List<String> held = linesStartingWith(list, "# HELD");
        assertEquals(5, held.size());
        assertEquals(5, r.heldCount());
        assertHeld(held, "/home/user/GitHub", "absolute path on the audit host");
        assertHeld(held, "file:/C:/Users", "file: URL");
        assertHeld(held, "C:\\\\Users", "absolute path on the audit host");
        assertHeld(held, "server", "absolute path on the audit host");
        assertHeld(held, "jdk.proxy6", "generated proxy class name");
    }

    /**
     * With failOnHeld the same input fails and nothing at all is written.  The
     * fixture has no proxy-shape permission (no AccessPermission): a refusal
     * tied to the classification flag would find no proxy here and not refuse.
     */
    @Test
    public void testFailOnHeldRefusesHostSpecificEntriesAndWritesNothing() throws Exception {
        File policy = writePolicy("held-host.policy",
            "grant codebase \"file:/lib/host-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.io.FilePermission \"/home/user/host-dl.jar\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");

        try {
            new ProxyPolicyGenerator().failOnHeld(true).generate(policy, outDir);
            fail("failOnHeld must refuse a host-specific entry");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("--fail-on-held"));
            assertTrue(e.getMessage(), e.getMessage().contains("/home/user/host-dl.jar"));
        }
        assertFalse("no list tree", outDir.exists());
        assertFalse("no ceiling", new File(policy.getAbsolutePath() + ".grantperm").exists());

        // the same input without the flag is written, so the flag is the cause
        assertEquals(1, new ProxyPolicyGenerator().generate(policy, outDir).proxyCount());
    }

    /**
     * --strict keeps its meaning (a proxy needs a proxy-shape permission) and
     * does not refuse: a strict run over a fixture with AccessPermission and a
     * host path still writes the list, with the entry held.
     */
    @Test
    public void testStrictKeepsItsClassificationMeaningAndDoesNotRefuse() throws Exception {
        File policy = writePolicy("strict-host.policy",
            "grant codebase \"file:/lib/host-dl.jar\" {",
            "    permission net.jini.security.AccessPermission \"someMethod\";",
            "    permission java.io.FilePermission \"/home/user/host-dl.jar\", \"read\";",
            "};");
        Pattern pat = Pattern.compile(ProxyPolicyGenerator.DEFAULT_PROXY_REGEX);

        ProxyPolicyGenerator.Result r =
            new ProxyPolicyGenerator(pat, true, Collections.<String, String>emptyMap())
                .generate(policy, new File(tempDir, "out"));

        assertEquals(1, r.proxyCount());
        assertEquals(1, r.heldCount());
        assertEquals(Arrays.asList("(net.jini.security.AccessPermission \"someMethod\")"),
            liveLines(new File(tempDir, "out/host-dl/META-INF/PERMISSIONS.LIST")));
    }

    /**
     * The existing property replacement is the one place host values are
     * mapped: the ceiling still gets ${property}; in the list that placeholder
     * would reach the loader unexpanded, so the entry is held back, not emitted.
     */
    @Test
    public void testMappedHostValueIsHeldNotEmittedAsPlaceholder() throws Exception {
        File policy = writePolicy("mapped.policy",
            "grant codebase \"file:/lib/mapped-dl.jar\" {",
            "    permission java.io.FilePermission \"/home/user/dist/lib/a.jar\", \"read\";",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        File outDir = new File(tempDir, "out");
        ProxyPolicyGenerator gen = new ProxyPolicyGenerator(
            Pattern.compile(ProxyPolicyGenerator.DEFAULT_PROXY_REGEX), false,
            Collections.singletonMap("dist.dir", "/home/user/dist"));

        gen.generate(policy, outDir);

        File list = new File(outDir, "mapped-dl/META-INF/PERMISSIONS.LIST");
        assertEquals(Arrays.asList(RUNTIME_ENTRY), liveLines(list));
        List<String> held = linesStartingWith(list, "# HELD");
        assertEquals(1, held.size());
        assertTrue(held.get(0), held.get(0).contains("${dist.dir}/lib/a.jar"));
        assertTrue(held.get(0), held.get(0).contains("unexpanded ${...} placeholder"));
        assertTrue("the ceiling keeps the mapped value",
            readFile(new File(policy.getAbsolutePath() + ".grantperm"))
                .contains("${dist.dir}/lib/a.jar"));
    }

    /** The header records the source policy, generator and date, and the grant digest when present. */
    @Test
    public void testHeaderRecordsProvenance() throws Exception {
        File policy = writePolicy("prov.policy",
            "grant codebase \"file:/lib/prov-dl.jar\", digest \"SHA-256=cafe01\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};",
            "grant codebase \"file:/lib/nodigest-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};");
        File outDir = new File(tempDir, "out");

        new ProxyPolicyGenerator()
            .clock(Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneId.of("UTC")))
            .generate(policy, outDir);

        List<String> head = linesStartingWith(
            new File(outDir, "prov-dl/META-INF/PERMISSIONS.LIST"), "# ");
        assertTrue(head.toString(), head.contains("# Source policy: prov.policy"));
        assertTrue(head.toString(), head.contains("# Proxy codebase digest: SHA-256=cafe01"));
        assertTrue(head.toString(), head.contains("# Generated: 2026-10-31"));
        assertTrue(head.toString(), head.contains(
            "# Source policy SHA-256: " + sha256Of(policy)));
        boolean generatorLine = false;
        for (String line : head) {
            generatorLine |= line.startsWith("# Generator: ProxyPolicyGenerator ")
                && line.endsWith(", list format 3");
        }
        assertTrue(head.toString(), generatorLine);
        assertTrue("no digest line when the grant has none", linesStartingWith(
            new File(outDir, "nodigest-dl/META-INF/PERMISSIONS.LIST"),
            "# Proxy codebase digest").isEmpty());
    }

    /**
     * A directory that holds a list from another source policy, or from before
     * provenance, is refused untouched; --clean replaces it; the same source
     * regenerates and a list it no longer produces is reported stale.
     */
    @Test
    public void testDifferentSourcePolicyRefusedUnlessClean() throws Exception {
        File a = writePolicy("a.policy", proxyGrant("alpha-dl"));
        File b = writePolicy("b.policy", proxyGrant("beta-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(a, outDir);
        File alpha = new File(outDir, "alpha-dl/META-INF/PERMISSIONS.LIST");
        String alphaBefore = readFile(alpha);

        try {
            new ProxyPolicyGenerator().generate(b, outDir);
            fail("a list from a.policy is in the way");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("alpha-dl (from a.policy)"));
        }
        assertEquals("the older list is untouched", alphaBefore, readFile(alpha));
        assertFalse("nothing of b.policy was written", new File(outDir, "beta-dl").exists());

        File legacy = new File(outDir, "legacy-dl/META-INF/PERMISSIONS.LIST");
        legacy.getParentFile().mkdirs();
        Files.write(legacy.toPath(), Arrays.asList("# written before provenance", RUNTIME_ENTRY));
        try {
            new ProxyPolicyGenerator().generate(a, outDir);
            fail("a list without provenance is in the way");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("legacy-dl (no provenance line)"));
        }

        new ProxyPolicyGenerator().clean(true).generate(b, outDir);
        assertFalse("clean removed the list from a.policy", new File(outDir, "alpha-dl").exists());
        assertTrue("clean keeps a list this tool did not write", new File(outDir, "legacy-dl/META-INF/PERMISSIONS.LIST").isFile());
        assertTrue(linesStartingWith(new File(outDir, "beta-dl/META-INF/PERMISSIONS.LIST"), "# ")
            .contains("# Source policy: b.policy"));

        // same source and content: regenerates, and a list it no longer produces
        // (here because the proxy pattern changed) is flagged
        File two = writePolicy("two.policy", proxyGrant("one-dl") + "\n" + proxyGrant("two-dl"));
        File sameOut = new File(tempDir, "same");
        new ProxyPolicyGenerator().generate(two, sameOut);
        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator(
            Pattern.compile(".*/one-dl\\.jar"), false, Collections.<String, String>emptyMap())
            .generate(two, sameOut);
        assertEquals(1, r.notes().size());
        assertTrue(r.notes().get(0), r.notes().get(0).startsWith("STALE two-dl: "));
    }

    // ----------------------------------------------------------- validation

    /**
     * Invariant: a line the loader cannot parse never leaves the generator.
     * The loader would drop the jar's whole list on it, logged at CONFIG only.
     * The bad entry sorts second, so a check of the first line alone fails.
     */
    @Test
    public void testUnparseableLineFailsGenerationAndLeavesNoList() throws Exception {
        File policy = writePolicy("bad.policy",
            "grant codebase \"file:/lib/bad-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.util.PropertyPermission \"java.home\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");
        ProxyPolicyGenerator gen = new ProxyPolicyGenerator() {
            @Override
            String encodeEntry(String type, String name, String actions) {
                return type.endsWith("PropertyPermission")
                    ? "(java.util.PropertyPermission \"java.home\" \"read\""
                    : super.encodeEntry(type, name, actions);
            }
        };

        try {
            gen.generate(policy, outDir);
            fail("an unparseable entry must fail generation");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("entry does not parse"));
        }
        assertFalse("no unvalidated list is left behind",
            new File(outDir, "bad-dl/META-INF/PERMISSIONS.LIST").exists());
        assertFalse("and no ceiling either",
            new File(policy.getAbsolutePath() + ".grantperm").exists());
    }

    @Test
    public void testValidateListNamesTheBadLine() throws Exception {
        File list = new File(tempDir, "PERMISSIONS.LIST");
        Files.write(list.toPath(), Arrays.asList(
            "# comment", "", "// another", RUNTIME_ENTRY, "  (java.lang.RuntimePermission \"x\" ", "()"));
        try {
            ProxyPolicyGenerator.validateList(list);
            fail("line 5 does not parse");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(":5: "));
        }
        Files.write(list.toPath(), Arrays.asList("# comment", "", "// another", RUNTIME_ENTRY));
        ProxyPolicyGenerator.validateList(list);
    }

    // ----------------------------------------------------------------- diff

    /**
     * The diff compares by parsed entry (an empty actions string equals none),
     * matches a versioned artefact to the module directory, ignores build
     * output, names the unmatched on both sides, and writes nothing.
     */
    @Test
    public void testDiffAgainstReportsAddedAndRemovedAndWritesNothing() throws Exception {
        File checked = new File(tempDir, "checked");
        writeList(checked, "mahalo-dl/src/main/resources", "# hand written",
            RUNTIME_ENTRY,
            "(java.util.PropertyPermission \"java.home\" \"read\")",
            "(java.lang.reflect.ReflectPermission \"newProxyInPackage.x\" \"\")");
        writeList(checked, "mahalo-dl/target/classes", "(java.lang.RuntimePermission \"setIO\")");
        writeList(checked, "orphan-dl/src/main/resources", RUNTIME_ENTRY);
        File policy = writePolicy("diff.policy",
            "grant codebase \"file:/dist/lib-dl/mahalo-dl-4.0.0-SNAPSHOT.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.lang.RuntimePermission \"getProtectionDomain\";",
            "    permission java.lang.reflect.ReflectPermission \"newProxyInPackage.x\";",
            "};",
            proxyGrant("fresh-dl"));
        File outDir = new File(tempDir, "out");
        Map<String, String> before = snapshot(checked);

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().diffAgainst(checked)
            .generate(policy, outDir);

        String diff = r.diff();
        assertTrue(diff, diff.contains("diff mahalo-dl-4.0.0-SNAPSHOT vs "
            + "mahalo-dl/src/main/resources/META-INF/PERMISSIONS.LIST: +1 -1 =2\n"));
        assertTrue(diff, diff.contains("  + (java.lang.RuntimePermission \"getProtectionDomain\")\n"));
        assertTrue(diff, diff.contains("  - (java.util.PropertyPermission \"java.home\" \"read\")\n"));
        assertFalse("build output is not a checked-in list", diff.contains("setIO"));
        assertTrue(diff, diff.contains("diff fresh-dl: no checked-in list under "));
        assertTrue(diff, diff.contains(
            "diff: checked-in lists with no generated counterpart: [orphan-dl]\n"));
        assertEquals("the compared directory is only read", before, snapshot(checked));
        assertTrue("the list itself is still written to --out",
            new File(outDir, "mahalo-dl-4.0.0-SNAPSHOT/META-INF/PERMISSIONS.LIST").isFile());
    }

    /** --out pointing at the compared lists must not overwrite them, not even with --clean. */
    @Test
    public void testDiffRefusesToOverwriteACheckedInList() throws Exception {
        File checked = new File(tempDir, "checked");
        // the shape of the committed lists: the old generator line, no '# Source policy:' line
        writeList(checked, "mahalo-dl", "# Generated by ProxyPolicyGenerator (do not edit by hand).",
            RUNTIME_ENTRY);
        File policy = writePolicy("over.policy", proxyGrant("mahalo-dl"));
        Map<String, String> before = snapshot(checked);

        try {
            new ProxyPolicyGenerator().clean(true).diffAgainst(checked).generate(policy, checked);
            fail("the plan overwrites a checked-in list");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is a checked-in list"));
        }
        assertEquals(before, snapshot(checked));
    }

    // ------------------------------------------------------------------ CLI

    @Test
    public void testCliFailOnHeldRefusalExitsOneAndDiffGoesToStdout() throws Exception {
        File policy = writePolicy("cli.policy",
            "grant codebase \"file:/lib/cli-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.net.URLPermission \"http://host/-\", \"GET:\";",
            "    permission java.io.FilePermission \"/home/user/cli-dl.jar\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");
        File checked = new File(tempDir, "checked");
        writeList(checked, "cli-dl", RUNTIME_ENTRY);
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();

        int rc = ProxyPolicyGenerator.run(new String[]{"--fail-on-held", "--out", outDir.getPath(),
            policy.getPath()}, new PrintStream(so, true, "UTF-8"), new PrintStream(se, true, "UTF-8"));
        assertEquals(1, rc);
        assertTrue(se.toString("UTF-8"),
            se.toString("UTF-8").contains("cli.policy: REFUSED: --fail-on-held"));
        assertFalse(outDir.exists());

        so.reset();
        se.reset();
        rc = ProxyPolicyGenerator.run(new String[]{"--out", outDir.getPath(), "--clean",
            "--diff-against", checked.getPath(), policy.getPath()},
            new PrintStream(so, true, "UTF-8"), new PrintStream(se, true, "UTF-8"));
        assertEquals(0, rc);
        String err = se.toString("UTF-8");
        assertTrue(err, err.contains("DROPPED cli-dl: (java.net.URLPermission "));
        assertTrue(err, err.contains("HELD cli-dl: (java.io.FilePermission \"/home/user/cli-dl.jar\""));
        assertTrue(so.toString("UTF-8"),
            so.toString("UTF-8").contains("diff cli-dl vs cli-dl/META-INF/PERMISSIONS.LIST: +"));
        assertTrue(so.toString("UTF-8"), so.toString("UTF-8").contains("dropped=1, held=1"));

        assertEquals(2, ProxyPolicyGenerator.run(new String[]{"--nonsense", policy.getPath()},
            new PrintStream(so, true, "UTF-8"), new PrintStream(se, true, "UTF-8")));
    }

    // ------------------------------------------ empty lists, provenance, atomicity

    /**
     * Invariant: an empty list requests the same as no list, so a group left
     * with no live entry gets none.  The reason is a note (and on stderr from
     * the CLI), the neighbour is written, and --fail-on-empty makes it a refusal
     * that writes nothing, ceiling included.
     */
    @Test
    public void testGroupWithNoLiveEntryGetsNoListAndSaysSo() throws Exception {
        File policy = writePolicy("empty.policy",
            "grant codebase \"file:/lib/gateonly-dl.jar\" {",
            "    permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";",
            "    permission java.io.FilePermission \"/home/user/x\", \"read\";",
            "};",
            proxyGrant("fine-dl"));
        File ceiling = new File(policy.getAbsolutePath() + ".grantperm");
        File outDir = new File(tempDir, "out");

        try {
            new ProxyPolicyGenerator().failOnEmpty(true).generate(policy, outDir);
            fail("--fail-on-empty must refuse");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("--fail-on-empty"));
            assertTrue(e.getMessage(), e.getMessage().contains("gateonly-dl"));
        }
        assertFalse("nothing written for the neighbour either", outDir.exists());
        assertFalse("no ceiling", ceiling.exists());

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);

        assertFalse("no list for the empty group", new File(outDir, "gateonly-dl").exists());
        assertTrue(new File(outDir, "fine-dl/META-INF/PERMISSIONS.LIST").isFile());
        assertEquals(1, r.permissionListFiles().size());
        assertEquals(1, r.noListCount());
        assertTrue(r.notes().toString(), r.notes().contains(
            "NO LIST WRITTEN: all 2 entries dropped or held for gateonly-dl"));
        assertTrue("the ceiling still bounds the group",
            readFile(ceiling).contains("gateonly-dl.jar"));

        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        File out2 = new File(tempDir, "out2");
        assertEquals(0, ProxyPolicyGenerator.run(new String[]{"--out", out2.getPath(),
            policy.getPath()}, new PrintStream(so, true, "UTF-8"), new PrintStream(se, true, "UTF-8")));
        assertTrue(se.toString("UTF-8"), se.toString("UTF-8").contains(
            "NO LIST WRITTEN: all 2 entries dropped or held for gateonly-dl"));
        assertEquals(1, ProxyPolicyGenerator.run(new String[]{"--out", new File(tempDir, "out3").getPath(),
            "--fail-on-empty", policy.getPath()}, new PrintStream(so, true, "UTF-8"),
            new PrintStream(se, true, "UTF-8")));
    }

    /**
     * The provenance check runs when the policy has no proxy grants too: lists
     * already under --out are reported, not left to look current, and are not touched.
     */
    @Test
    public void testPolicyWithNoGrantsStillReportsListsUnderOut() throws Exception {
        File policy = writePolicy("src.policy", proxyGrant("old-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(policy, outDir);
        writeList(outDir, "legacy-dl", RUNTIME_ENTRY);
        File old = new File(outDir, "old-dl/META-INF/PERMISSIONS.LIST");
        String before = readFile(old);
        writePolicy("src.policy", "// every grant removed");

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);

        assertEquals(0, r.proxyCount());
        assertEquals(2, r.notes().size());
        assertTrue(r.notes().toString(), r.notes().get(0).startsWith("STALE legacy-dl: "));
        assertTrue(r.notes().toString(), r.notes().get(0).contains("no provenance line"));
        assertTrue(r.notes().toString(), r.notes().get(1).startsWith("STALE old-dl: "));
        assertEquals("nothing is written or removed", before, readFile(old));
        assertTrue(new File(outDir, "legacy-dl/META-INF/PERMISSIONS.LIST").isFile());
    }

    /**
     * The provenance key is the file name and the SHA-256 of its content: the
     * same name with other content (another host's audit) is refused unless
     * --clean, the same content regenerates.
     */
    @Test
    public void testSameFileNameWithDifferentContentIsRefusedUnlessClean() throws Exception {
        File policy = writePolicy("host.policy", proxyGrant("a-dl"), proxyGrant("b-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(policy, outDir);
        new ProxyPolicyGenerator().generate(policy, outDir); // same content: fine
        Map<String, String> before = snapshot(outDir);

        writePolicy("host.policy", proxyGrant("a-dl")); // another host, same name
        try {
            new ProxyPolicyGenerator().generate(policy, outDir);
            fail("same name, other content");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("different content"));
            assertTrue(e.getMessage(), e.getMessage().contains("b-dl"));
        }
        assertEquals("refused untouched", before, snapshot(outDir));

        new ProxyPolicyGenerator().clean(true).generate(policy, outDir);
        assertFalse("clean removed the other host's list", new File(outDir, "b-dl").exists());
        assertTrue(linesStartingWith(new File(outDir, "a-dl/META-INF/PERMISSIONS.LIST"), "# ")
            .contains("# Source policy SHA-256: " + sha256Of(policy)));
    }

    /**
     * Invariant: no value that reaches the header or an entry may carry a
     * control character; a newline in a digest or codebase would otherwise
     * start a live line such as AllPermission.  Nothing is written.
     */
    @Test
    public void testControlCharactersAreRejectedEverywhereTheyCouldInject() throws Exception {
        String perm = "    permission java.lang.RuntimePermission \"getClassLoader\";";
        String[][] cases = {
            {"digest", "grant codebase \"file:/lib/inj-dl.jar\", digest "
                + "\"SHA-256=ab\\n(java.security.AllPermission)\" {", perm},
            {"codebase", "grant codebase \"file:/lib/inj-dl.jar\\n(java.security.AllPermission)\", "
                + "digest \"SHA-256=ab\" {", perm},
            {"permission name", "grant codebase \"file:/lib/inj-dl.jar\" {",
                "    permission java.lang.RuntimePermission \"a\\nb\";"},
            {"carriage return", "grant codebase \"file:/lib/inj-dl.jar\", digest "
                + "\"SHA-256=ab\\r(java.security.AllPermission)\" {", perm},
        };
        for (String[] c : cases) {
            File policy = writePolicy("inj-" + c[0].replace(' ', '-') + ".policy", c[1], c[2], "};");
            File outDir = new File(tempDir, "out-" + c[0].replace(' ', '-'));
            try {
                new ProxyPolicyGenerator().generate(policy, outDir);
                fail(c[0] + ": a control character must be rejected");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(c[0] + ": " + e.getMessage(), e.getMessage().contains("character U+"));
            }
            assertFalse(c[0] + ": no lists", outDir.exists());
            assertFalse(c[0] + ": no ceiling",
                new File(policy.getAbsolutePath() + ".grantperm").exists());
        }

        File ok = writePolicy("ok.policy", "grant codebase \"file:/lib/inj-dl.jar\", digest "
            + "\"SHA-256=ab\" {", perm, "};");
        assertEquals("the same shape without the newline is written", 1,
            new ProxyPolicyGenerator().generate(ok, new File(tempDir, "ok")).proxyCount());
    }

    /**
     * Invariant: a refusal leaves everything as it was.  The failing list is
     * the second of two, after one that validated; earlier good lists and the
     * ceiling survive, with and without --clean, and no staging directory is left.
     */
    @Test
    public void testValidationFailureLeavesOutDirAndCeilingExactlyAsTheyWere() throws Exception {
        File policy = writePolicy("atomic.policy", proxyGrant("a-dl"),
            "grant codebase \"file:/lib/b-dl.jar\" {",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "    permission java.util.PropertyPermission \"java.home\", \"read\";",
            "};");
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(policy, outDir);
        File ceiling = new File(policy.getAbsolutePath() + ".grantperm");
        Files.write(ceiling.toPath(), Arrays.asList("// GrantPermission ceilings synthesised by ProxyPolicyGenerator sentinel"));
        Map<String, String> before = snapshot(tempDir);

        for (boolean clean : new boolean[]{false, true}) {
            ProxyPolicyGenerator gen = new ProxyPolicyGenerator() {
                @Override
                String encodeEntry(String type, String name, String actions) {
                    return type.endsWith("PropertyPermission")
                        ? "(java.util.PropertyPermission \"java.home\" \"read\""
                        : super.encodeEntry(type, name, actions);
                }
            }.clean(clean);
            try {
                gen.generate(policy, outDir);
                fail("clean=" + clean + ": the unparseable entry must fail the run");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("entry does not parse"));
            }
            assertEquals("clean=" + clean + ": the tree is as it was", before, snapshot(tempDir));
        }
    }

    // ---------------------------------------------------- round 3 hardening

    private static ProxyPolicyGenerator.ProxyGroup groupFor(String codebase) {
        ProxyPolicyGenerator.GrantBlock g = new ProxyPolicyGenerator.GrantBlock();
        g.codebase = codebase;
        return new ProxyPolicyGenerator.ProxyGroup(g);
    }

    /**
     * The mapping defect: a codebase naming the jar by a property ({@code ${reggie-dl.jar}}) or
     * hiding the separator ({@code ${/}}) was named after the placeholder text.  The five shapes
     * are those the real harness policies produced; ordinary names are unchanged.
     */
    @Test
    public void testArtefactNameUnwrapsAJarPropertyAndTheSeparatorProperty() throws Exception {
        String[][] cases = {
            {"file:${jsk.home}${/}lib-dl${/}${phoenix-dl.jar}", "phoenix-dl"},
            {"file:/${jsk.home}/lib-dl/${phoenix-dl.jar}", "phoenix-dl"},
            {"file:${jsk.home}${/}lib${/}${reggie-dl.jar}", "reggie-dl"},
            {"httpmd://${HOST}:${jsk-dl.jar}", "jsk-dl"},
            {"httpmd://${HOST}:${reggie-dl.jar};sha=abc", "reggie-dl"},
            {"file:/lib/mahalo-dl-4.0.0-SNAPSHOT.jar", "mahalo-dl-4.0.0-SNAPSHOT"},
            {"httpmd://${HOST}:9080/*", "_"},
            {"file:${jsk.home}${/}lib${/}${jsk.home}", "__jsk.home_"},
        };
        for (String[] c : cases) {
            assertEquals(c[0], c[1], ProxyPolicyGenerator.artefactName(groupFor(c[0])));
        }
    }

    /** Format characters (bidi overrides, zero-width, tag characters) reorder or hide what a reviewer reads. */
    @Test
    public void testFormatCharactersAreRejectedInEveryValueOfAGrant() throws Exception {
        String perm = "    permission java.lang.RuntimePermission \"getClassLoader\";";
        String[][] cases = {
            {"bidi in a name", "grant codebase \"file:/lib/fmt-dl.jar\" {",
                "    permission java.lang.RuntimePermission \"‮nib\";"},
            {"zero-width in a name", "grant codebase \"file:/lib/fmt-dl.jar\" {",
                "    permission java.lang.RuntimePermission \"a​b\";"},
            {"tag character in a name", "grant codebase \"file:/lib/fmt-dl.jar\" {",
                "    permission java.lang.RuntimePermission \"a󠁁b\";"},
            {"bidi in the codebase", "grant codebase \"file:/lib/fm‮t-dl.jar\" {", perm},
            {"bidi in signedBy", "grant signedBy \"a‮b\", codebase \"file:/lib/fmt-dl.jar\" {", perm},
            {"bidi in a principal class", "grant codebase \"file:/lib/fmt-dl.jar\", principal p.Q‮x \"n\" {", perm},
            {"bidi in a principal name", "grant codebase \"file:/lib/fmt-dl.jar\", principal p.Q \"n‮m\" {", perm},
        };
        for (String[] c : cases) {
            File policy = new File(tempDir, "fmt-" + c[0].replace(' ', '-') + ".policy");
            Files.write(policy.toPath(), Arrays.asList(c[1], c[2], "};"),
                java.nio.charset.Charset.forName("UTF-8"));
            File outDir = new File(tempDir, "out-" + c[0].replace(' ', '-'));
            try {
                new ProxyPolicyGenerator().generate(policy, outDir);
                fail(c[0] + ": a format character must be rejected");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(c[0] + ": " + e.getMessage(), e.getMessage().contains("format character U+"));
            }
            assertFalse(c[0] + ": no lists", outDir.exists());
            assertFalse(c[0] + ": no ceiling", new File(policy.getAbsolutePath() + ".grantperm").exists());
        }
    }

    /** --clean deletes the lists this tool wrote, never a hand-written one under --out. */
    @Test
    public void testCleanKeepsAListThisToolDidNotWrite() throws Exception {
        File a = writePolicy("a.policy", proxyGrant("alpha-dl"));
        File b = writePolicy("b.policy", proxyGrant("beta-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(a, outDir);
        writeList(outDir, "hand-dl", RUNTIME_ENTRY);
        File hand = new File(outDir, "hand-dl/META-INF/PERMISSIONS.LIST");
        String handBefore = readFile(hand);

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().clean(true).generate(b, outDir);

        assertEquals("a hand-written list survives --clean", handBefore, readFile(hand));
        assertFalse("a list this tool wrote does not", new File(outDir, "alpha-dl").exists());
        assertTrue(new File(outDir, "beta-dl/META-INF/PERMISSIONS.LIST").isFile());
        assertEquals(r.notes().toString(), 1, r.notes().size());
        assertTrue(r.notes().toString(), r.notes().get(0).startsWith("KEPT hand-dl: "));
    }

    /** A .grantperm this tool did not write is neither deleted (no proxy) nor overwritten (proxy). */
    @Test
    public void testAGrantpermThisToolDidNotWriteIsNeverDeletedOrOverwritten() throws Exception {
        File policy = writePolicy("c.policy", proxyGrant("gamma-dl"));
        File ceiling = new File(policy.getAbsolutePath() + ".grantperm");
        File outDir = new File(tempDir, "out");
        Files.write(ceiling.toPath(), Arrays.asList("// my own hand-written ceiling"));
        String mine = readFile(ceiling);

        try {
            new ProxyPolicyGenerator().generate(policy, outDir);
            fail("the ceiling is somebody else's");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("was not written by ProxyPolicyGenerator"));
        }
        assertEquals(mine, readFile(ceiling));
        assertFalse("nothing else was written either", outDir.exists());

        writePolicy("c.policy", "// no grants at all");
        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);
        assertEquals("not deleted", mine, readFile(ceiling));
        assertEquals(r.notes().toString(), 1, r.notes().size());
        assertTrue(r.notes().toString(), r.notes().get(0).startsWith("KEPT c.policy.grantperm"));

        // one this tool wrote is still deleted when the policy loses its proxies
        assertTrue(ceiling.delete());
        writePolicy("c.policy", proxyGrant("gamma-dl"));
        new ProxyPolicyGenerator().generate(policy, outDir);
        assertTrue(ceiling.isFile());
        writePolicy("c.policy", "// no grants at all");
        new ProxyPolicyGenerator().generate(policy, outDir);
        assertFalse("its own stale ceiling is removed", ceiling.exists());
    }

    /**
     * A list this run replaces never refuses, whatever it was made from; only a list that survives
     * the run unreplaced, from another policy or other content, is refused.
     */
    @Test
    public void testAListThisRunReplacesNeverRefuses() throws Exception {
        File host = writePolicy("host.policy", proxyGrant("a-dl"), proxyGrant("b-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(host, outDir);

        // same name, other content (a re-audit): both lists are replaced, so no refusal
        writePolicy("host.policy", proxyGrant("a-dl"), proxyGrant("b-dl"),
            "grant codebase \"file:/lib/a-dl.jar\" {",
            "    permission java.util.PropertyPermission \"java.home\", \"read\";", "};");
        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(host, outDir);
        assertEquals(2, r.proxyCount());
        assertTrue(readFile(new File(outDir, "a-dl/META-INF/PERMISSIONS.LIST")).contains("java.home"));
        assertTrue(r.notes().toString(), r.notes().isEmpty());

        // another policy replacing the same artefact: no refusal; but b-dl survives unreplaced
        File c = writePolicy("c.policy", proxyGrant("a-dl"));
        try {
            new ProxyPolicyGenerator().generate(c, outDir);
            fail("b-dl is a leftover from host.policy");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("b-dl (from host.policy)"));
            assertFalse(e.getMessage(), e.getMessage().contains("a-dl (from"));
        }
        File d = writePolicy("d.policy", proxyGrant("a-dl"), proxyGrant("b-dl"));
        assertEquals(2, new ProxyPolicyGenerator().generate(d, outDir).proxyCount());
    }

    private static final String OLD_HEADER = "# Generated by ProxyPolicyGenerator (do not edit by hand).";

    /**
     * Invariant: one artefact name is one list.  The two codebases of the real
     * defaultsecuretest.policy.old that name reggie-dl differently sanitise to the
     * one name; the run is refused, names both codebases, and writes nothing:
     * --out and the ceiling are exactly as they were (a refusal after the first
     * list was moved would leave three lists of five and no ceiling).
     */
    @Test
    public void testTwoCodebasesOfOneArtefactNameAreRefusedAndNothingIsWritten() throws Exception {
        String a = "httpmd://${HOST}:9080/${reggie-dl.jar}";
        String b = "httpmd://${HOST}:${reggie-dl.jar}";
        File policy = writePolicy("clash.policy", proxyGrant("jsk-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(policy, outDir);
        File ceiling = new File(policy.getAbsolutePath() + ".grantperm");
        writePolicy("clash.policy", proxyGrant("jsk-dl"),
            "grant codebase \"" + a + "\" {", "    permission java.lang.RuntimePermission \"getClassLoader\";", "};",
            "grant codebase \"" + b + "\" {", "    permission java.lang.RuntimePermission \"setIO\";", "};");
        Map<String, String> before = snapshot(tempDir);
        String ceilingBefore = readFile(ceiling);

        for (boolean clean : new boolean[]{false, true}) {
            try {
                new ProxyPolicyGenerator().clean(clean).generate(policy, outDir);
                fail("clean=" + clean + ": two codebases, one artefact name");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("'reggie-dl'"));
                assertTrue(e.getMessage(), e.getMessage().contains(a + " and " + b));
            }
            assertEquals("clean=" + clean + ": nothing was written", before, snapshot(tempDir));
            assertEquals(ceilingBefore, readFile(ceiling));
        }
        assertEquals("the CLI refuses with rc 1", 1, ProxyPolicyGenerator.run(
            new String[]{"--out", outDir.getPath(), policy.getPath()},
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream())));
    }

    /** Names that differ only in case are one file on Windows and macOS: also refused. */
    @Test
    public void testArtefactNamesThatDifferOnlyInCaseAreRefused() throws Exception {
        File policy = writePolicy("case.policy", proxyGrant("Reggie-dl"), proxyGrant("reggie-dl"));
        try {
            new ProxyPolicyGenerator().generate(policy, new File(tempDir, "out"));
            fail("one file on a case-insensitive file system");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Reggie-dl"));
        }
        assertFalse(new File(tempDir, "out").exists());
    }

    /**
     * --clean never replaces a list with neither marker, and the run says so
     * instead of printing KEPT and overwriting it.
     */
    @Test
    public void testCleanRefusesToReplaceAHandWrittenList() throws Exception {
        File policy = writePolicy("a.policy", proxyGrant("alpha-dl"));
        File outDir = new File(tempDir, "out");
        writeList(outDir, "alpha-dl", "# my own list", RUNTIME_ENTRY);
        File hand = new File(outDir, "alpha-dl/META-INF/PERMISSIONS.LIST");
        Map<String, String> before = snapshot(tempDir);

        try {
            new ProxyPolicyGenerator().clean(true).generate(policy, outDir);
            fail("the list is somebody's, and this run would replace it");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("[alpha-dl]"));
            assertTrue(e.getMessage(), e.getMessage().contains("with or without --clean"));
        }
        assertTrue(readFile(hand).startsWith("# my own list"));
        assertEquals("and nothing else was written", before, snapshot(tempDir));
        try {
            new ProxyPolicyGenerator().generate(policy, outDir);
            fail("without --clean too");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("alpha-dl (no provenance line)"));
        }
        assertEquals(before, snapshot(tempDir));
    }

    /**
     * A list with the old generator line (the committed lists carry it, and no
     * '# Source policy:' line) was written by this tool: this run replaces it
     * with a note, and --clean deletes one it does not replace, with a note.
     */
    @Test
    public void testAListWithTheOldGeneratorLineIsToolWritten() throws Exception {
        File policy = writePolicy("a.policy", proxyGrant("alpha-dl"));
        File outDir = new File(tempDir, "out");
        writeList(outDir, "alpha-dl", OLD_HEADER, RUNTIME_ENTRY);

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().generate(policy, outDir);
        assertTrue(readFile(new File(outDir, "alpha-dl/META-INF/PERMISSIONS.LIST"))
            .contains("# Source policy: a.policy"));
        assertEquals(r.notes().toString(), 1, r.notes().size());
        assertTrue(r.notes().toString(), r.notes().get(0).startsWith("REPLACED pre-provenance list alpha-dl"));

        writeList(outDir, "zzz-dl", OLD_HEADER, RUNTIME_ENTRY);
        try {
            new ProxyPolicyGenerator().generate(policy, outDir);
            fail("zzz-dl survives this run from an unknown policy");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("zzz-dl (pre-provenance list written by this tool)"));
        }
        r = new ProxyPolicyGenerator().clean(true).generate(policy, outDir);
        assertFalse(new File(outDir, "zzz-dl").exists());
        assertTrue(r.notes().toString(), r.notes().toString().contains("REMOVED pre-provenance list zzz-dl"));
    }

    /** --clean also acts when the policy has no proxy grant: it deletes only lists this tool wrote. */
    @Test
    public void testCleanActsOnAPolicyWithNoProxyGrants() throws Exception {
        File policy = writePolicy("a.policy", proxyGrant("alpha-dl"));
        File outDir = new File(tempDir, "out");
        new ProxyPolicyGenerator().generate(policy, outDir);
        writeList(outDir, "old-dl", OLD_HEADER, RUNTIME_ENTRY);
        writeList(outDir, "hand-dl", RUNTIME_ENTRY);
        writePolicy("a.policy", "// every grant removed");

        ProxyPolicyGenerator.Result r = new ProxyPolicyGenerator().clean(true).generate(policy, outDir);

        assertFalse("a list this tool wrote", new File(outDir, "alpha-dl").exists());
        assertFalse("a pre-provenance list this tool wrote", new File(outDir, "old-dl").exists());
        assertTrue("a hand-written list stays", new File(outDir, "hand-dl/META-INF/PERMISSIONS.LIST").isFile());
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("REMOVED alpha-dl") && notes.contains("KEPT hand-dl: "));
    }

    // -------------------------------------------------------------- helpers

    private static String sha256Of(File f) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static String[] hostPolicyLines() {
        return new String[]{
            "grant codebase \"file:/lib/host-dl.jar\" {",
            "    permission java.io.FilePermission \"/home/user/GitHub/JGDMS/dist/host-dl.jar\", \"read\";",
            "    permission java.io.FilePermission \"file:/C:/Users/peter/dist/host-dl.jar\", \"read\";",
            "    permission java.io.FilePermission \"C:\\\\Users\\\\peter\\\\dist\\\\host-dl.jar\", \"read\";",
            "    permission java.io.FilePermission \"\\\\\\\\server\\\\share\\\\host-dl.jar\", \"read\";",
            "    permission au.zeus.jdk.authorization.guards.SerialObjectPermission \"jdk.proxy6.$Proxy11\";",
            "    permission java.io.FilePermission \"data/relative.txt\", \"read\";",
            "    permission java.lang.RuntimePermission \"getClassLoader\";",
            "};"};
    }

    private static String proxyGrant(String artefact) {
        return "grant codebase \"file:/lib/" + artefact + ".jar\" {\n"
            + "    permission java.lang.RuntimePermission \"getClassLoader\";\n"
            + "};";
    }

    private static void assertHeld(List<String> held, String fragment, String reason) {
        for (String line : held) {
            if (line.contains(fragment)) {
                assertTrue(line, line.startsWith("# HELD (" + reason));
                return;
            }
        }
        fail("no HELD line contains " + fragment + ": " + held);
    }

    /** Entry lines of a list, as the loader sees them: not blank, not a comment. */
    private static List<String> liveLines(File list) throws Exception {
        List<String> live = new ArrayList<String>();
        for (String line : Files.readAllLines(list.toPath(), java.nio.charset.Charset.forName("UTF-8"))) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("//")) live.add(t);
        }
        return live;
    }

    private static List<String> linesStartingWith(File f, String prefix) throws Exception {
        List<String> found = new ArrayList<String>();
        for (String line : Files.readAllLines(f.toPath(), java.nio.charset.Charset.forName("UTF-8"))) {
            if (line.startsWith(prefix)) found.add(line);
        }
        return found;
    }

    private static String readFile(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    /** Writes {@code <root>/<holder>/META-INF/PERMISSIONS.LIST}. */
    private static void writeList(File root, String holder, String... lines) throws Exception {
        File list = new File(new File(new File(root, holder), "META-INF"), "PERMISSIONS.LIST");
        list.getParentFile().mkdirs();
        Files.write(list.toPath(), Arrays.asList(lines));
    }

    /** Relative path to content for every file under {@code dir}; directories map to "". */
    private static Map<String, String> snapshot(File dir) throws Exception {
        Map<String, String> m = new TreeMap<String, String>();
        snapshot(dir.toURI(), dir, m);
        return m;
    }

    private static void snapshot(java.net.URI root, File f, Map<String, String> m) throws Exception {
        String rel = root.relativize(f.toURI()).getPath();
        File[] kids = f.listFiles();
        if (kids == null) {
            m.put(rel, readFile(f));
            return;
        }
        m.put(rel, "");
        for (File k : kids) snapshot(root, k, m);
    }

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
