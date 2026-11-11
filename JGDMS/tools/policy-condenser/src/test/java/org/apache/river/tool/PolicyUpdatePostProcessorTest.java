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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Properties;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link PolicyUpdatePostProcessor}.
 *
 * <p>These tests exercise the exclusion-detection and in-place condensing
 * logic without requiring an actual QA test run or git repository.  The git
 * restore step is tested indirectly: excluded policy files retain their
 * original (un-condensed) content even when git is unavailable.
 */
public class PolicyUpdatePostProcessorTest {

    private File tempDir;
    private File qaDir;
    private File srcDir;
    private File harnessDir;
    private File policyDir;

    /** A simple two-grant policy used as input for most tests. */
    private static final String DUPLICATE_GRANTS =
            "grant codebase \"file:/foo.jar\" {\n"
            + "    permission java.util.PropertyPermission \"java.home\", \"read\";\n"
            + "};\n"
            + "grant codebase \"file:/foo.jar\" {\n"
            + "    permission java.util.PropertyPermission \"user.dir\", \"read\";\n"
            + "};\n";

    @Before
    public void setUp() throws Exception {
        tempDir = new File(System.getProperty("java.io.tmpdir"),
                "pupp-test-" + Thread.currentThread().getId() + "-" + System.nanoTime());
        qaDir      = new File(tempDir, "qa");
        srcDir     = new File(qaDir,   "src");
        harnessDir = new File(qaDir,   "harness");
        policyDir  = new File(harnessDir, "policy");

        policyDir.mkdirs();
        srcDir.mkdirs();
    }

    @After
    public void tearDown() {
        deleteTree(tempDir);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static File writePolicyFile(File dir, String filename, String content)
            throws Exception {
        File f = new File(dir, filename);
        f.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.print(content);
        }
        return f;
    }

    private static File writeTdFile(File dir, String filename, String... lines)
            throws Exception {
        File f = new File(dir, filename);
        f.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            for (String line : lines) {
                pw.println(line);
            }
        }
        return f;
    }

    private static void writeExclusionsFile(File dir, String... basenames)
            throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, "policy-update-exclusions.properties")))) {
            pw.println("# protected policy files");
            for (String name : basenames) {
                pw.println(name);
            }
        }
    }

    private static String readFile(File f) throws Exception {
        byte[] bytes = Files.readAllBytes(f.toPath());
        return new String(bytes, Charset.defaultCharset());
    }

    private static int countGrants(File policyFile) throws Exception {
        PolicyParser parser = new DefaultPolicyParser();
        Collection<PermissionGrant> grants =
                parser.parse(policyFile.toURI().toURL(), new Properties());
        return grants.size();
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteTree(c);
        }
        f.delete();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * A policy file that is NOT in the exclusion set should be condensed
     * in-place: the original file is replaced with the merged content.
     */
    @Test
    public void testNonExcludedFileIsCondensedInPlace() throws Exception {
        writeExclusionsFile(policyDir /* no excluded names */);
        File pf = writePolicyFile(policyDir, "normaltest.policy", DUPLICATE_GRANTS);

        int grantsBefore = countGrants(pf);
        assertEquals("Input should have 2 grants (duplicates)", 2, grantsBefore);

        new PolicyUpdatePostProcessor(qaDir).run();

        // After condensing, two duplicate grants become one.
        int grantsAfter = countGrants(pf);
        assertEquals("Condensed file should have 1 merged grant", 1, grantsAfter);

        // No leftover .con file.
        assertFalse(".con file should be cleaned up",
                new File(pf.getAbsolutePath() + ".con").exists());
    }

    /**
     * A policy file whose basename is listed in
     * {@code policy-update-exclusions.properties} must NOT be condensed.
     * When git is unavailable the file may not be restored, but it must at
     * least not be modified by the condenser.
     */
    @Test
    public void testExcludedFileByExclusionsPropertiesIsNotCondensed()
            throws Exception {
        writeExclusionsFile(policyDir, "protected.policy");
        File pf = writePolicyFile(policyDir, "protected.policy", DUPLICATE_GRANTS);
        String originalContent = readFile(pf);

        new PolicyUpdatePostProcessor(qaDir).run();

        // Condensing must not touch this file.
        String afterContent = readFile(pf);
        assertEquals("Excluded policy file content must not be changed by condenser",
                originalContent, afterContent);
    }

    /**
     * A policy file whose basename is referenced by a {@code .td} file with
     * {@code policy.no.update=true} must NOT be condensed.
     */
    @Test
    public void testExcludedFileByTdAnnotationIsNotCondensed() throws Exception {
        writeExclusionsFile(policyDir /* no hard-coded names */);

        // Create a test directory with a .td file that marks the policy.
        File testDir = new File(srcDir, "org/example/mytest");
        File pf = writePolicyFile(testDir, "restrictedtest.policy", DUPLICATE_GRANTS);
        writeTdFile(testDir, "MySecurityExceptionTest.td",
                "testClass=MySecurityExceptionTest",
                "testPolicyfile=restrictedtest.policy",
                "policy.no.update=true");

        String originalContent = readFile(pf);

        new PolicyUpdatePostProcessor(qaDir).run();

        String afterContent = readFile(pf);
        assertEquals("Policy file annotated via .td must not be condensed",
                originalContent, afterContent);
    }

    /**
     * A {@code .td} file with {@code policy.no.update=true} that uses a
     * {@code <url:...>} wrapper for the policy path should still contribute
     * the correct basename to the exclusion set.
     */
    @Test
    public void testTdWithUrlWrappedPolicyfileIsExcluded() throws Exception {
        writeExclusionsFile(policyDir /* no hard-coded names */);

        File testDir = new File(srcDir, "org/example/urltest");
        File pf = writePolicyFile(testDir, "urlwrapped.policy", DUPLICATE_GRANTS);
        writeTdFile(testDir, "UrlWrappedTest.td",
                "testClass=UrlWrappedTest",
                "testPolicyfile=<url:org/example/urltest/urlwrapped.policy>",
                "policy.no.update=true");

        String originalContent = readFile(pf);

        new PolicyUpdatePostProcessor(qaDir).run();

        String afterContent = readFile(pf);
        assertEquals("Policy file referenced via <url:...> must not be condensed",
                originalContent, afterContent);
    }

    /**
     * When both excluded and non-excluded policy files exist, the processor
     * should condense only the non-excluded ones.
     */
    @Test
    public void testMixedFilesOnlyCondensesNonExcluded() throws Exception {
        writeExclusionsFile(policyDir, "noupdate.policy");

        File excluded = writePolicyFile(policyDir, "noupdate.policy", DUPLICATE_GRANTS);
        File normal   = writePolicyFile(policyDir, "normal.policy",   DUPLICATE_GRANTS);

        String excludedBefore = readFile(excluded);

        new PolicyUpdatePostProcessor(qaDir).run();

        // Normal file condensed (2 → 1 grants).
        assertEquals("Normal policy file should be condensed", 1, countGrants(normal));
        // Excluded file untouched.
        assertEquals("Excluded policy file should be unchanged",
                excludedBefore, readFile(excluded));
    }

    /**
     * A {@code .td} file without {@code policy.no.update=true} must NOT
     * contribute its {@code testPolicyfile} to the exclusion set.
     */
    @Test
    public void testTdWithoutMarkerDoesNotExclude() throws Exception {
        writeExclusionsFile(policyDir /* no hard-coded names */);

        File testDir = new File(srcDir, "org/example/normaltest");
        File pf = writePolicyFile(testDir, "updatable.policy", DUPLICATE_GRANTS);
        writeTdFile(testDir, "NormalTest.td",
                "testClass=NormalTest",
                "testPolicyfile=updatable.policy");
        // No policy.no.update line.

        new PolicyUpdatePostProcessor(qaDir).run();

        // Should have been condensed.
        assertEquals("Non-annotated policy file should be condensed", 1, countGrants(pf));
    }

    /**
     * A hand-written list (neither a provenance line nor the old generator line) is
     * never replaced or deleted, with or without {@code -Dpolicy.update.proxy.clean};
     * a list with the old generator line was written by this tool and is replaced
     * by the default run.
     */
    @Test
    public void testProxyOutputHoldingAnUnrecordedList() throws Exception {
        writeExclusionsFile(policyDir /* no excluded names */);
        File pf = writePolicyFile(policyDir, "proxy.policy",
                "grant codebase \"file:/lib/mahalo-dl.jar\" {\n"
                + "    permission java.lang.RuntimePermission \"getClassLoader\";\n"
                + "};\n");
        String legacy = "(java.lang.RuntimePermission \"legacy\")\n";
        File list = writePolicyFile(
                new File(pf.getAbsolutePath() + ".proxy-permissions/mahalo-dl/META-INF"),
                "PERMISSIONS.LIST", legacy);

        new PolicyUpdatePostProcessor(qaDir).run();
        assertEquals("a list without provenance is refused, not overwritten",
                legacy, readFile(list));

        System.setProperty("policy.update.proxy.clean", "true");
        try {
            new PolicyUpdatePostProcessor(qaDir).run();
        } finally {
            System.clearProperty("policy.update.proxy.clean");
        }
        assertEquals("--clean does not replace a hand-written list either", legacy, readFile(list));

        String old = "# Generated by ProxyPolicyGenerator (do not edit by hand).\n" + legacy;
        java.nio.file.Files.write(list.toPath(), old.getBytes("UTF-8"));
        new PolicyUpdatePostProcessor(qaDir).run();
        String regenerated = readFile(list);
        assertTrue(regenerated, regenerated.contains("# Source policy: proxy.policy"));
        assertFalse(regenerated, regenerated.contains("\"legacy\""));
    }

    /**
     * qa/build.xml was once rewritten with mixed and doubled line ends (CR CR LF
     * and a bare LF among CRLF) that git cannot normalise: it must be all LF or
     * all CRLF, and no CR may stand alone.
     */
    @Test
    public void testQaBuildFileHasOneConsistentLineEndingStyle() throws Exception {
        File build = new File("../../../qa/build.xml");
        org.junit.Assume.assumeTrue("qa/build.xml is not beside this module", build.isFile());
        byte[] b = java.nio.file.Files.readAllBytes(build.toPath());
        int cr = 0, lf = 0, crlf = 0, crcr = 0;
        for (int i = 0; i < b.length; i++) {
            if (b[i] == '\r') {
                cr++;
                if (i + 1 < b.length && b[i + 1] == '\r') crcr++;
            } else if (b[i] == '\n') {
                lf++;
                if (i > 0 && b[i - 1] == '\r') crlf++;
            }
        }
        assertEquals("no CR CR", 0, crcr);
        assertEquals("every CR is the first half of a CRLF", cr, crlf);
        assertTrue("all LF or all CRLF, not a mixture: " + crlf + " CRLF of " + lf + " line ends",
                crlf == 0 || crlf == lf);
    }

    // ---------------------------------------------------------------- logging

    /** Collects the messages the post-processor logs at WARNING or above. */
    private static final class Warnings extends java.util.logging.Handler {
        final java.util.List<String> messages = new java.util.ArrayList<String>();
        private final java.util.logging.Formatter formatter = new java.util.logging.SimpleFormatter();
        private final java.util.logging.Logger logger =
                java.util.logging.Logger.getLogger(PolicyUpdatePostProcessor.class.getName());

        Warnings() {
            logger.addHandler(this);
        }

        @Override
        public void publish(java.util.logging.LogRecord r) {
            if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                messages.add(r.getLevel() + " " + formatter.formatMessage(r));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }

        boolean any(String... fragments) {
            for (String m : messages) {
                boolean all = true;
                for (String f : fragments) all &= m.contains(f);
                if (all) return true;
            }
            return false;
        }
    }

    private static final String PROXY_POLICY =
            "grant codebase \"file:/lib/mahalo-dl.jar\" {\n"
            + "    permission java.lang.RuntimePermission \"getClassLoader\";\n"
            + "    permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";\n"
            + "    permission java.io.FilePermission \"/home/user/mahalo-dl.jar\", \"read\";\n"
            + "};\n";

    /**
     * The only channel that tells a policy-update user what the generator
     * dropped, held back or found stale is a WARNING log: assert it.
     */
    @Test
    public void testDroppedHeldAndStaleNotesAreLoggedAtWarning() throws Exception {
        writeExclusionsFile(policyDir /* no excluded names */);
        File pf = writePolicyFile(policyDir, "proxy.policy", PROXY_POLICY);
        Warnings warnings = new Warnings();
        try {
            new PolicyUpdatePostProcessor(qaDir).run();
            assertTrue(warnings.messages.toString(), warnings.any(
                "WARNING proxy.policy: DROPPED mahalo-dl: (au.zeus.jdk.authorization.guards.LoadClassPermission"));
            assertTrue(warnings.messages.toString(), warnings.any(
                "WARNING proxy.policy: HELD mahalo-dl: (java.io.FilePermission \"/home/user/mahalo-dl.jar\""));

            // a list of the same source and content that this run does not produce
            File outDir = new File(pf.getAbsolutePath() + ".proxy-permissions");
            File orphan = new File(outDir, "zzz-dl/META-INF/PERMISSIONS.LIST");
            orphan.getParentFile().mkdirs();
            java.nio.file.Files.copy(new File(outDir, "mahalo-dl/META-INF/PERMISSIONS.LIST").toPath(),
                orphan.toPath());
            warnings.messages.clear();

            new PolicyUpdatePostProcessor(qaDir).run();
            assertTrue(warnings.messages.toString(), warnings.any(
                "WARNING proxy.policy: STALE zzz-dl: a list from proxy.policy"));
        } finally {
            warnings.close();
        }
    }

    /** A refusal is logged with its reason and says the earlier lists are unchanged. */
    @Test
    public void testGeneratorRefusalIsLoggedAtWarningWithItsReason() throws Exception {
        writeExclusionsFile(policyDir /* no excluded names */);
        File pf = writePolicyFile(policyDir, "proxy.policy", PROXY_POLICY);
        File list = new File(pf.getAbsolutePath()
                + ".proxy-permissions/mahalo-dl/META-INF/PERMISSIONS.LIST");
        list.getParentFile().mkdirs();
        String legacy = "(java.lang.RuntimePermission \"legacy\")\n";
        java.nio.file.Files.write(list.toPath(), legacy.getBytes("UTF-8"));
        Warnings warnings = new Warnings();
        try {
            new PolicyUpdatePostProcessor(qaDir).run();
            assertTrue(warnings.messages.toString(), warnings.any("WARNING ProxyPolicyGenerator refused",
                "proxy.policy", "mahalo-dl (no provenance line)", "earlier lists are unchanged"));
        } finally {
            warnings.close();
        }
        assertEquals(legacy, readFile(list));
    }

    /** A list directory that no policy processed in this run regenerates is reported. */
    @Test
    public void testOrphanedProxyPermissionsDirectoryIsReported() throws Exception {
        writeExclusionsFile(policyDir /* no excluded names */);
        File gone = new File(policyDir, "removed.policy.proxy-permissions/old-dl/META-INF/PERMISSIONS.LIST");
        gone.getParentFile().mkdirs();
        java.nio.file.Files.write(gone.toPath(), "(java.lang.RuntimePermission \"x\")\n".getBytes("UTF-8"));
        Warnings warnings = new Warnings();
        try {
            new PolicyUpdatePostProcessor(qaDir).run();
            assertTrue(warnings.messages.toString(), warnings.any(
                "WARNING ORPHAN", "removed.policy.proxy-permissions", "[old-dl]"));
        } finally {
            warnings.close();
        }
    }
}
