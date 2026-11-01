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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link PolicyLint}.  Each test states the invariant it defends: the AllPermission
 * verdict is the production provider's, the text scan only classifies and locates, a scoped or
 * commented grant is never reported as unscoped, and the exit status follows the documented
 * matrix.
 */
public class PolicyLintTest {

    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("policy-lint-test").toFile();
    }

    @After
    public void tearDown() {
        ProxyPolicyGenerator.deleteTree(dir);
    }

    private static final class Run {
        int exit;
        String out;
        String err;

        List<String> findings() {
            List<String> r = new ArrayList<String>();
            for (String l : out.split("\n")) {
                if (l.length() > 0 && !l.startsWith("SUMMARY") && !l.startsWith("TOTAL")
                        && !l.startsWith("#") && !l.startsWith("---")
                        && l.matches(".*:\\d+ [A-Z]\\d+ .*")) {
                    r.add(l);
                }
            }
            return r;
        }

        /** The kinds in output order (file, then line), e.g. "P1 U1". */
        String kinds() {
            StringBuilder sb = new StringBuilder();
            for (String l : findings()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(l.replaceAll("^.*:\\d+ ([A-Z]\\d+) .*$", "$1"));
            }
            return sb.toString();
        }

        String verdictLine() {
            for (String l : out.split("\n")) if (l.startsWith("VERDICT ")) return l;
            return "";
        }

        String providerLine() {
            for (String l : out.split("\n")) if (l.startsWith("PROVIDER ")) return l;
            return "";
        }

        int count(String kind) {
            int n = 0;
            for (String l : findings()) if (l.matches(".*:\\d+ " + kind + " .*")) n++;
            return n;
        }
    }

    private File write(String name, String text) throws Exception {
        return write(name, text.getBytes("UTF-8"));
    }

    private File write(String name, byte[] bytes) throws Exception {
        File f = new File(dir, name);
        f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        try {
            o.write(bytes);
        } finally {
            o.close();
        }
        return f;
    }

    private Run lint(String... args) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Run r = new Run();
        r.exit = PolicyLint.run(args, new PrintStream(o, true, "UTF-8"),
                new PrintStream(e, true, "UTF-8"));
        r.out = o.toString("UTF-8");
        r.err = e.toString("UTF-8");
        return r;
    }

    private Run lintFile(String text, String... opts) throws Exception {
        File f = write("t.policy", text);
        List<String> a = new ArrayList<String>(Arrays.asList(opts));
        a.add(f.getPath());
        return lint(a.toArray(new String[0]));
    }

    private static final String UNSCOPED_ALL =
            "grant {\n    permission java.security.AllPermission;\n};\n";

    // ------------------------------------------------------------ the review fixtures

    /**
     * One row per fixture of the Opus review (src/test/resources/policylint): name, kinds in output
     * order, and what the production provider said for code from http://evil.example.com
     * (stranger) and for the same code run by an arbitrary X500Principal: {stranger, principal},
     * null when the production parser rejects the file.  The provider values are the review's
     * real-probe2.txt, measured with the DirtyChai ConcurrentPolicyFile.
     */
    private static final Object[][] FIXTURES = {
        {"c1-unscoped.policy", "U1", new boolean[]{true, true}},
        {"c2-scoped.policy", "S1", new boolean[]{false, false}},
        {"f01-undef-prop.policy", "U3 U4 S1", new boolean[]{true, true}},
        {"f02-jskhome-unset.policy", "U3 U4 S1", new boolean[]{true, true}},
        {"f03-lone-slash.policy", "U1", new boolean[]{true, true}},
        {"f04-keystore-nosemi.policy", "U1", new boolean[]{true, true}},
        {"f05-signedby-comma.policy", "U1", new boolean[]{true, true}},
        {"f06-principal-wild.policy", "P1 S3", null},
        {"f07-file-dash.policy", "S1 D3", new boolean[]{false, false}},
        {"f08-bom.policy", "P1 U1", null},
        {"f09-digest-emptyhex.policy", "S2", new boolean[]{false, false}},
        {"f10-codebase-empty.policy", "U1", new boolean[]{false, false}},
        {"f11-no-semicolon.policy", "U1", new boolean[]{true, true}},
        {"f12-grantperm-scoped.policy", "S4", new boolean[]{false, false}},
        {"f13-comments-fp.policy", "", new boolean[]{false, false}},
        {"f14-uppercase.policy", "U1", new boolean[]{true, true}},
        {"f15-split-comments.policy", "U1", new boolean[]{true, true}},
        {"f17-principal-x500-anyname.policy", "S3", new boolean[]{false, true}},
        {"f18-signedby-unknown.policy", "S1", new boolean[]{false, false}},
        {"f19-keystore-semi.policy", "U1", new boolean[]{true, true}},
        {"f20-single-quote.policy", "U1 P1", null},
        {"f21-prop-defined.policy", "S1 D3", new boolean[]{false, false}},   // the home is user-writable
        {"f22-slash-after-cb.policy", "S1", new boolean[]{false, false}},
        {"f23-keystore-type-nosemi.policy", "U1", new boolean[]{true, true}},
        {"f24-kspwurl.policy", "P1 U1", null},
        {"f25-signedby-empty.policy", "U1", new boolean[]{true, true}},
        {"f26-nested-brace.policy", "P1 U1", null},
        {"f27-early-close.policy", "P1", null},
    };

    private File fixture(String name) throws Exception {
        InputStream in = PolicyLintTest.class.getResourceAsStream("/policylint/" + name);
        assertNotNull("missing test resource " + name, in);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
            return write("fx/" + name, b.toByteArray());
        } finally {
            in.close();
        }
    }

    private void checkFixtures(String provider) throws Exception {
        for (Object[] row : FIXTURES) {
            String name = (String) row[0];
            String kinds = (String) row[1];
            boolean[] real = (boolean[]) row[2];
            if (name.equals("f06-principal-wild.policy") && !provider.equals("platform")) {
                // KNOWN DIVERGENCE (2026-11-01): jgdms-platform's scanner refuses a wildcard
                // principal CLASS (P1); DirtyChai's scanner still accepts it, but its provider now matches nothing.
                // Delete this branch when DirtyChai's scanner refuses it too.
                kinds = "S3";
                real = new boolean[]{false, false};
            }
            File f = fixture(name);
            Run r = lint("--provider=" + provider, f.getPath());
            String why = name + " [" + provider + "]\n" + r.out;
            assertEquals(why, kinds, r.kinds());
            boolean fails = kinds.matches(".*\\b(U\\d|P[123]|D[1-4]|S3)\\b.*");
            assertEquals(why, fails ? 1 : 0, r.exit);
            assertEquals(why, kinds.isEmpty() ? 0 : 1,
                    lint("--provider=" + provider, "--strict", "--audit-input", f.getPath()).exit);
            String v = r.verdictLine();
            if (real == null) {
                assertTrue(why, v.contains("not loaded ("));
            } else {
                assertTrue(why, v.contains("stranger=" + real[0] + " principal=" + real[1] + " "));
            }
        }
    }

    // Each fixture's kinds, exit status and provider answers; a tool that decided by text, or that
    // read f01-f05 as scoped, fails the U-rows; one that scored f06/f17 S2 fails the S3 rows.
    @Test
    public void reviewFixturesWithPlatformProvider() throws Exception {
        checkFixtures("platform");
    }

    // The DirtyChai provider must give the same table as jgdms-platform's (they are textually the
    // same parser; this is the measurement).  Skipped unless the package is exported to the test
    // JVM (--add-exports java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED).
    @Test
    public void providersAgreeOnFixtures() throws Exception {
        try {
            PolicyVerdict.open("dirtychai");
        } catch (IllegalStateException e) {
            Assume.assumeNoException(e);
        }
        checkFixtures("dirtychai");
        checkFixtures("auto");
    }

    // The fallback: with the DirtyChai class absent, auto uses jgdms-platform and says so; a forced
    // DirtyChai provider is a usage error, not a silent downgrade.
    @Test
    public void providerAbsentFallsBackAndSaysSo() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "no.such.DirtyChaiProvider";
        try {
            File f = write("t.policy", UNSCOPED_ALL);
            Run r = lint(f.getPath());
            assertEquals(r.out, 1, r.exit);
            assertTrue(r.out, r.providerLine().contains(PolicyVerdict.PLATFORM));
            assertTrue(r.out, r.providerLine().contains("fell back from no.such.DirtyChaiProvider"));
            assertTrue(r.out, r.providerLine().contains("[not the DirtyChai provider]"));
            assertTrue(r.out, r.verdictLine().contains("stranger=true"));
            assertEquals(2, lint("--provider=dirtychai", f.getPath()).exit);
            assertEquals(1, lint("--provider=platform", f.getPath()).exit);
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }

    // ------------------------------------------------------------ the three review survivors

    // MX1: a missing path next to a good one is a usage error; skipping it would exit 0 here.
    @Test
    public void missingPathNextToGoodPathExitsTwo() throws Exception {
        File good = write("clean.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission"
                + " \"x\", \"read\";\n};\n");
        Run r = lint(good.getPath(), new File(dir, "typo.policy").getPath());
        assertEquals(r.out + r.err, 2, r.exit);
        assertTrue(r.err, r.err.contains("no such file or directory"));
        assertEquals("", r.out);
        assertEquals(0, lint(good.getPath()).exit);
    }

    // MX2: a file that cannot be read is a P1 finding, never a clean file.
    @Test
    public void unreadableFileIsP1() throws Exception {
        File asFile = new File(dir, "sub.policy");
        assertTrue(asFile.mkdir()); // reading a directory throws IOException on every platform
        PolicyLint.Linted l = PolicyLint.lint(asFile, "sub.policy", null,
                Collections.<String, String>emptyMap());
        assertEquals(1, l.findings.size());
        assertEquals("P1", l.findings.get(0).kind);
        assertEquals(0, l.findings.get(0).line);
        assertTrue(l.findings.get(0).reason, l.findings.get(0).reason.startsWith("file cannot be read"));
        assertNull(l.answer);
    }

    // The same through run() for a file the OS refuses to read (not possible for root or on Windows).
    @Test
    public void lockedFileIsP1() throws Exception {
        File locked = write("locked.policy", UNSCOPED_ALL);
        Assume.assumeTrue(locked.setReadable(false) && !locked.canRead());
        Run r = lint(locked.getPath());
        assertEquals(r.out, "P1", r.kinds());
        assertEquals(1, r.exit);
    }

    // MX3: a lone '/' comments out the rest of the line, as in the production scanner.  Here the
    // codeBase vanishes, the grant is unscoped, and the text scan alone says U1 (not S1, and not
    // U3: the provider and the text agree).
    @Test
    public void loneSlashCommentsOutTheLine() throws Exception {
        Run r = lintFile("grant / codeBase \"http://good.example.com/ok.jar\"\n{\n"
                + "    permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, "U1", r.kinds());
        assertTrue(r.out, r.out.contains("t.policy:1 U1 "));
        // '/' inside a comment word and after the codeBase string changes nothing else
        r = lintFile("grant codeBase \"http://good.example.com/ok.jar\" / x\n{\n"
                + "    permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, "S1", r.kinds());
    }

    // ------------------------------------------------------------ U1 / U2

    // U1: a wrong tool that reported U1 on the permission line (2) instead of the grant line (1) would fail the
    // line assertion; one that reports U2 for it fails the kind assertion.
    @Test
    public void u1UnscopedAllPermission() throws Exception {
        Run r = lintFile(UNSCOPED_ALL);
        assertEquals(1, r.exit);
        assertEquals(r.out, 1, r.findings().size());
        assertTrue(r.out, r.findings().get(0).endsWith(
                "t.policy:1 U1 UNSCOPED_ALLPERMISSION grant with no codeBase, signedBy, principal or"
                + " digest holds AllPermission (line 2): masks every permission polpAudit would record"));
        assertEquals(1, r.count("U1"));
    }

    @Test
    public void u1WithNameAndActionsAndMultiLinePermission() throws Exception {
        Run r = lintFile("grant\n{\n  permission\n   java.security.AllPermission\n"
                + "     \"<all permissions>\",\n     \"<all actions>\";\n};\n");
        assertEquals(1, r.exit);
        assertEquals(1, r.count("U1"));
    }

    // U2: a tool that only matched the exact string "java.security.AllPermission" would miss the wildcard and Umbrella.
    @Test
    public void u2UnscopedMetaGrants() throws Exception {
        Run r = lintFile("grant {\n permission net.jini.security.GrantPermission\n"
                + "     \"java.security.AllPermission\";\n};\n"
                + "grant {\n permission net.jini.security.GrantPermission \"java.security.*\";\n};\n"
                + "grant {\n permission net.jini.security.GrantPermission \"*\";\n};\n"
                + "grant {\n permission net.jini.security.policy.UmbrellaGrantPermission;\n};\n");
        assertEquals(1, r.exit);
        assertEquals(r.out, 4, r.count("U2"));
        assertEquals(0, r.count("U1"));
    }

    // A tool that flagged every unscoped GrantPermission as U2, or a narrow scoped one as S4, fails here.
    @Test
    public void u2NotRaisedForNarrowTarget() throws Exception {
        Run r = lintFile("grant {\n permission net.jini.security.GrantPermission"
                + " \"java.io.FilePermission \\\"/tmp/-\\\", \\\"read\\\"\";\n};\n"
                + "grant codeBase \"file:/x.jar\" {\n permission net.jini.security.GrantPermission"
                + " \"java.io.FilePermission \\\"/tmp/-\\\", \\\"read\\\"\";\n};\n");
        assertEquals(r.out, 0, r.findings().size());
        assertEquals(0, r.exit);
    }

    // ------------------------------------------------------------ U3 / U4 and --define

    // U3: when the provider says a stranger holds AllPermission and the text found no U1, the real
    // verdict is reported with the file and the reason; a tool without the rule reports only U4/S1.
    @Test
    public void u3RealVerdictOverridesTheText() throws Exception {
        Run r = lintFile("grant codeBase \"file:${no.such.prop}/lib/x.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, "U3 U4 S1", r.kinds());
        assertEquals(1, r.exit);
        String u3 = r.findings().get(0);
        assertTrue(u3, u3.contains("t.policy:1 U3 REAL_VERDICT_UNSCOPED ") && u3.contains("AllPermission"));
        // no U3 on top of U1: the text already said so
        assertEquals("U1", lintFile(UNSCOPED_ALL).kinds());
        // and none when the provider says the codeBase holds
        assertEquals("S1", lintFile("grant codeBase \"file:/a.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n").kinds());
    }

    // U4 names the property and fails by default; a defined property or ${/} is not U4.
    @Test
    public void u4UndefinedPropertyInCodeBase() throws Exception {
        Run r = lintFile("grant codeBase \"file:${jsk.home}${/}lib${/}*\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant codeBase \"file:${user.home}${/}x.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant codeBase \"file:${a.one}/${a.two}/x.jar\", digest \"SHA-256:00ff\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant codeBase \"file:${gone}/x.jar\" {\n"
                + " permission net.jini.security.GrantPermission \"java.security.AllPermission\";\n};\n"
                + "grant codeBase \"file:${gone}/x.jar\" {\n"
                + " permission java.io.FilePermission \"x\", \"read\";\n};\n");
        assertEquals(r.out, 2, r.count("U4")); // lines 1 and 10, not 4, 7 or 13
        assertTrue(r.out, r.out.contains("t.policy:1 U4 PROPERTY_DEPENDENT_SCOPE codeBase"
                + " file:${jsk.home}${/}lib${/}* uses undefined property [jsk.home]"));
        assertTrue(r.out, r.out.contains("t.policy:10 U4 "));
        assertFalse(r.out, r.out.contains("t.policy:4 U4"));
        assertFalse(r.out, r.out.contains("t.policy:7 U4"));
        assertFalse(r.out, r.out.contains("t.policy:13 U4"));
        assertEquals(1, r.exit);
    }

    // --define supplies what the runtime would; it reaches the provider and the text check alike,
    // and the JVM's own properties are left as they were.
    @Test
    public void defineOption() throws Exception {
        File f = write("t.policy", "grant codeBase \"file:${no.such.prop}/lib/x.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertNull(System.getProperty("no.such.prop"));
        Run r = lint("--define", "no.such.prop=/opt/lib", f.getPath());
        assertEquals(r.out, "S1", r.kinds());
        assertEquals(0, r.exit);
        assertTrue(r.out, r.verdictLine().contains("stranger=false principal=false"));
        assertNull(System.getProperty("no.such.prop"));
        assertEquals("S1", lint("--define=no.such.prop=/opt/lib", f.getPath()).kinds());
        // the value may contain '=' ; the name may not be empty; a bare name is a usage error
        assertEquals("S1", lint("--define", "no.such.prop=/opt/a=b", f.getPath()).kinds());
        assertEquals(2, lint("--define", "noequals", f.getPath()).exit);
        assertEquals(2, lint("--define", "=v", f.getPath()).exit);
        assertEquals(2, lint(f.getPath(), "--define").exit);
        // a define for another name does not rescue the grant
        assertEquals("U3 U4 S1", lint("--define", "other=/x", f.getPath()).kinds());
        assertNull(System.getProperty("other"));
    }

    // ------------------------------------------------------------ S1 .. S5

    // S1: a tool that treated a codeBase-only AllPermission as S2 (or U1) fails; the codeBase must be shown.
    @Test
    public void s1CodebaseOnlyAndSignedByOnly() throws Exception {
        Run r = lintFile("grant codeBase \"file:${jgdms.lib}/x.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant signedBy \"duke\" {\n permission java.security.AllPermission;\n};\n"
                + "grant signedBy \"duke\", codeBase \"file:/a/-\" {\n"
                + " permission java.security.AllPermission;\n};\n", "--define", "jgdms.lib=/opt/lib");
        assertEquals(r.out, 3, r.count("S1"));
        assertEquals(0, r.count("S2"));
        assertEquals(0, r.count("U1"));
        assertTrue(r.out, r.out.contains("codeBase file:${jgdms.lib}/x.jar"));
        assertEquals(0, r.exit);
    }

    // S2: a tool that reported a digest- or named-principal-scoped grant as S1, or a wildcard one as S2, fails.
    @Test
    public void s2DigestAndNamedPrincipalScoped() throws Exception {
        Run r = lintFile("grant codeBase \"file:/a.jar\" digest \"SHA-256:00ff\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant principal javax.security.auth.x500.X500Principal \"CN=a\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant digest \"SHA-256:00ff\" principal * * {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, "S2 S2 S2", r.kinds());
        assertEquals(0, r.exit);
    }

    // S3: every wildcard principal form; a tool that only tested for "principal * *" misses two.
    @Test
    public void s3WildcardPrincipals() throws Exception {
        String a = " {\n permission java.security.AllPermission;\n};\n";
        Run r = lintFile("grant principal * *" + a
                + "grant principal javax.security.auth.x500.X500Principal *" + a
                + "grant principal * \"CN=a\"" + a
                + "grant codeBase \"file:/a.jar\" principal * *" + a
                + "grant principal javax.security.auth.x500.X500Principal \"CN=a\"" + a);
        // the fourth is pinned by its codeBase: a wildcard principal there reaches no stranger
        assertEquals(r.out, "S3 S3 S3 S2 S2", r.kinds());
        assertEquals(1, r.exit);
        assertEquals(1, lintFile("grant principal * *" + a, "--strict").exit);
        assertEquals(1, lintFile("grant principal * *" + a, "--audit-input").exit);
    }

    // S4: a codeBase-scoped GrantPermission that implies AllPermission, or Umbrella; digest-scoped and
    // narrow ones are not reported.
    @Test
    public void s4CodebaseScopedMetaGrant() throws Exception {
        String text = "grant codeBase \"file:/x.jar\" {\n permission net.jini.security.GrantPermission"
                + " \"java.security.AllPermission\";\n};\n"
                + "grant codeBase \"file:/y.jar\" {\n permission net.jini.security.GrantPermission \"*\";\n};\n"
                + "grant signedBy \"duke\" {\n permission net.jini.security.policy.UmbrellaGrantPermission;\n};\n"
                + "grant digest \"SHA-256:00ff\" {\n permission net.jini.security.GrantPermission"
                + " \"java.security.AllPermission\";\n};\n";
        Run r = lintFile(text);
        assertEquals(r.out, "S4 S4 S4", r.kinds());
        assertEquals(0, r.exit);
        assertTrue(r.out, r.out.contains("t.policy:1 S4 CODEBASE_SCOPED_META_GRANT codeBase-scoped"
                + " grant holds GrantPermission (line 2)"));
        assertEquals(1, lintFile(text, "--strict").exit);
        assertEquals(1, lintFile(text, "--audit-input").exit);
    }

    // S5: exactly jrt:/<module> is informational; a path below it or a wildcard is still S1.
    @Test
    public void s5PlatformModuleGrant() throws Exception {
        String a = " {\n permission java.security.AllPermission;\n};\n";
        Run r = lintFile("grant codeBase \"jrt:/java.sql\"" + a
                + "grant codeBase \"jrt:/java.sql/-\"" + a
                + "grant codeBase \"jrt:/-\"" + a
                + "grant codeBase \"jrt:/java.sql\", signedBy \"duke\"" + a);
        // jrt:/- is every module: AllPermission there is D4 (fails by default), jrt:/java.sql alone is clean
        assertEquals(r.out, "S5 S1 S1 D4 S1", r.kinds());
        assertTrue(r.out, r.out.contains("t.policy:1 S5 PLATFORM_MODULE_GRANT AllPermission (line 2)"
                + " for platform module jrt:/java.sql"));
        assertEquals(1, r.exit);
        String only = "grant codeBase \"jrt:/java.sql\"" + a;
        assertEquals(0, lintFile(only).exit);
        assertEquals(0, lintFile(only, "--strict").exit);
        assertEquals(1, lintFile(only, "--audit-input").exit);
        assertTrue(lintFile(only).out, lintFile(only).out.contains("S5=1"));
    }

    // P1: a tool that skipped an unterminated grant would report nothing and exit 0.
    @Test
    public void p1Unparseable() throws Exception {
        assertEquals(1, lintFile("grant {\n permission java.security.AllPermission;\n").exit);
        Run r = lintFile("grant codeBase \"file:/a\" \n");
        assertEquals(1, r.count("P1"));
        assertEquals(1, r.exit);
        r = lintFile("grant { permission ; };\n");
        assertEquals(r.out, 1, r.count("P1"));
        r = lintFile("grat { permission java.security.AllPermission; };\n");
        assertEquals(r.out, 1, r.count("P1"));
        r = lintFile("grant { permision java.security.AllPermission; };\n");
        assertEquals(r.out, 1, r.count("P1"));
        assertEquals(1, r.exit);
        // a keystore clause with no URL is a P1 and does not swallow the grant after it
        r = lintFile("keystore\n" + UNSCOPED_ALL);
        assertEquals(r.out, "P1 U1", r.kinds());
    }

    // The production parser rejects a BOM, keystorePasswordURL and a nested brace; the lint says so.
    @Test
    public void productionParserRejectionIsP1() throws Exception {
        Run r = lint(fixture("f08-bom.policy").getPath());
        assertTrue(r.out, r.out.contains("f08-bom.policy:0 P1 UNPARSEABLE the production policy parser"
                + " rejects this file: InvalidFormatException"));
        assertEquals(1, r.exit);
        assertEquals("P1 U1", lint(fixture("f24-kspwurl.policy").getPath()).kinds());
    }

    // keystore needs no ';' and takes at most a URL and a type: the next grant is still read (F1).
    @Test
    public void keystoreWithoutSemicolonDoesNotHideTheNextGrant() throws Exception {
        Run r = lintFile("keystore \"file:/nonexistent.jks\"\n" + UNSCOPED_ALL);
        assertEquals(r.out, "U1", r.kinds());
        assertTrue(r.out, r.out.contains("t.policy:2 U1 "));
        r = lintFile("keystore \"file:/nonexistent.jks\", \"JKS\"\n" + UNSCOPED_ALL + "keystore \"x\";\n");
        assertEquals(r.out, "U1", r.kinds());
        assertTrue(r.out, r.out.contains("t.policy:2 U1 "));
    }

    // signedBy "," names no signer, so the grant is unscoped (F4); "a, b" does name some.
    @Test
    public void signedByWithNoAliasIsUnscoped() throws Exception {
        String a = " {\n permission java.security.AllPermission;\n};\n";
        assertEquals("U1", lintFile("grant signedBy \",\"" + a).kinds());
        assertEquals("U1", lintFile("grant signedBy \" , \"" + a).kinds());
        assertEquals("S1", lintFile("grant signedBy \"a, b\"" + a).kinds());
    }

    // ------------------------------------------------------------ comments, noise, formats

    // A tool that scanned for the substring would flag the comment text of the deploy policy.
    @Test
    public void commentedAllPermissionIsNotAFinding() throws Exception {
        Run r = lintFile("/* Do NOT grant AllPermission\n * grant { permission java.security.AllPermission; };\n */\n"
                + "// grant { permission java.security.AllPermission; };\n"
                + "grant codeBase \"file:/a.jar\" digest \"SHA-256:aa\" {\n"
                + "  // permission java.security.AllPermission;\n"
                + "  /* permission java.security.AllPermission; */\n"
                + "  permission java.util.PropertyPermission \"x\", \"read\";\n};\n");
        assertEquals(r.out, 0, r.findings().size());
        assertEquals(0, r.exit);
    }

    // A tool that matched on a regex over the whole text would flag these.
    @Test
    public void falsePositiveFixtures() throws Exception {
        Run r = lintFile("grant principal javax.security.auth.x500.X500Principal \"CN=a\" {\n"
                + " permission java.io.FilePermission \"/tmp/-\", \"read\";\n};\n"
                + "grant {\n permission java.util.PropertyPermission \"java.security.AllPermission\", \"read\";\n"
                + "};\n"
                + "grant codeBase \"file:/AllPermission/java.security.AllPermission.jar\" {\n"
                + " permission java.lang.RuntimePermission \"getenv.*\";\n};\n");
        assertEquals(r.out, 0, r.findings().size());
        assertEquals(0, r.exit);
        assertTrue(r.out, r.out.contains("TOTAL files=1 U1=0 U2=0 U3=0 U4=0 S1=0 S2=0 S3=0 S4=0 S5=0"
                + " P1=0 exit=0"));
    }

    // Braces/semicolons in strings must neither close a block early nor hide the later one: U1 must land on line 5.
    @Test
    public void quotedBracesAndSemicolonsDoNotConfuseBlocks() throws Exception {
        Run r = lintFile("grant codeBase \"file:/a{b};c/-\" {\n"
                + " permission java.util.PropertyPermission \"}; grant { permission java.security.AllPermission; };\","
                + " \"read\";\n};\n\n" + UNSCOPED_ALL);
        assertEquals(r.out, 1, r.findings().size());
        assertTrue(r.out, r.findings().get(0).contains("t.policy:5 U1 "));
    }

    // Keyword case and CRLF must not change the result.
    @Test
    public void caseAndCrlf() throws Exception {
        String text = "keystore \"file:${user.home}/.keystore\", \"JKS\";\r\n"
                + "GRANT CodeBase \"file:${x}/y.jar\" {\r\n  PERMISSION java.security.AllPermission;\r\n};\r\n"
                + "Grant {\r\n  Permission java.security.AllPermission;\r\n};\r\n";
        Run r = lintFile(text, "--define", "x=/x");
        assertEquals(r.out, "S1 U1", r.kinds());
        assertTrue(r.out, r.out.contains("t.policy:5 U1 "));
        assertTrue(r.out, r.out.contains("t.policy:2 S1 "));
    }

    // A BOM is stripped by the text scan, so lines are right, but the production parser rejects it.
    @Test
    public void bomIsReportedAsP1AndStillLocated() throws Exception {
        byte[] body = UNSCOPED_ALL.getBytes("UTF-8");
        byte[] bom = new byte[body.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bom, 3, body.length);
        Run r = lint(write("bom.policy", bom).getPath());
        assertEquals(r.out, "P1 U1", r.kinds());
        assertTrue(r.out, r.out.contains("bom.policy:1 U1 "));
    }

    // Exit matrix: a tool that ignored --strict or --audit-input, or applied either to the wrong kind, fails a row.
    @Test
    public void exitCodeMatrix() throws Exception {
        String s1 = "grant codeBase \"file:/a\" {\n permission java.security.AllPermission;\n};\n";
        String s2 = "grant digest \"SHA-256:aa\" {\n permission java.security.AllPermission;\n};\n";
        String s3 = "grant principal * * {\n permission java.security.AllPermission;\n};\n";
        String s4 = "grant codeBase \"file:/a\" {\n permission net.jini.security.GrantPermission"
                + " \"java.security.AllPermission\";\n};\n";
        String s5 = "grant codeBase \"jrt:/java.sql\" {\n permission java.security.AllPermission;\n};\n";
        String u3 = "grant codeBase \"file:${undefined.here}\" digest \"SHA-256:aa\" {\n"
                + " permission java.io.FilePermission \"x\", \"read\";\n};\n"
                + "grant codeBase \"file:${undefined.here}\" {\n permission java.security.AllPermission;\n};\n";
        String clean = "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n";
        String p1 = "grant {\n";
        String u2 = "grant { permission net.jini.security.GrantPermission \"*\"; };\n";
        Object[][] rows = {
            // text, strict, audit, expected exit
            {clean, false, false, 0}, {clean, true, true, 0},
            {UNSCOPED_ALL, false, false, 1}, {UNSCOPED_ALL, true, false, 1}, {UNSCOPED_ALL, false, true, 1},
            {u2, false, false, 1}, {u2, true, true, 1},
            {u3, false, false, 1}, {u3, true, true, 1},
            {p1, false, false, 1}, {p1, true, true, 1},
            {s1, false, false, 0}, {s1, true, false, 1}, {s1, false, true, 1}, {s1, true, true, 1},
            {s2, false, false, 0}, {s2, true, false, 0}, {s2, false, true, 1}, {s2, true, true, 1},
            {s3, false, false, 1}, {s3, true, false, 1}, {s3, false, true, 1}, {s3, true, true, 1},
            {s4, false, false, 0}, {s4, true, false, 1}, {s4, false, true, 1}, {s4, true, true, 1},
            {s5, false, false, 0}, {s5, true, false, 0}, {s5, false, true, 1}, {s5, true, true, 1},
        };
        for (Object[] row : rows) {
            List<String> opts = new ArrayList<String>();
            if ((Boolean) row[1]) opts.add("--strict");
            if ((Boolean) row[2]) opts.add("--audit-input");
            Run r = lintFile((String) row[0], opts.toArray(new String[0]));
            assertEquals("text=" + row[0] + " strict=" + row[1] + " audit=" + row[2] + "\n" + r.out,
                    row[3], r.exit);
        }
    }

    // Usage errors are 2, not 1: a CI gate must tell "bad invocation" from "bad policy".
    @Test
    public void usageErrorsExitTwo() throws Exception {
        assertEquals(2, lint().exit);
        assertEquals(2, lint("--bogus", dir.getPath()).exit);
        assertEquals(2, lint(new File(dir, "missing.policy").getPath()).exit);
        assertEquals(2, lint("--format=xml", dir.getPath()).exit);
        assertEquals(2, lint("--provider=stock", dir.getPath()).exit);
        assertEquals(2, lint(dir.getPath()).exit);  // empty directory: nothing was checked
        assertEquals(2, lint("--glob").exit);
    }

    // Policy text must not reach the terminal raw: the codeBase below carries ESC, NUL and U+2028.
    @Test
    public void controlCharactersAreEscaped() throws Exception {
        Run r = lintFile("grant codeBase \"file:/a\u001b[31m\u0000 FAKE.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, 1, r.count("S1"));
        assertTrue(r.out, r.out.contains("a\\u001B[31m\\u0000\\u2028FAKE.jar"));
        for (char c : (r.out + r.err).toCharArray()) {
            assertTrue("raw control char U+" + Integer.toHexString(c),
                    c == '\n' || (c >= 0x20 && c != 0x7f && !(c >= 0x80 && c < 0xa0) && c != 0x2028
                    && c != 0x2029));
        }
        assertEquals("\\u0001\\u202E\\uD800", PolicyLint.esc("\u0001‮\ud800"));
    }

    // The directory walk: a tool without the .proxy-permissions or .grantperm exclusions reports f2 and f3.
    @Test
    public void directoryWalkSelectsAndSkips() throws Exception {
        write("a.policy", UNSCOPED_ALL);
        write("b.policy.old", UNSCOPED_ALL);
        write("c.policy.new", UNSCOPED_ALL);
        write("policy.all", UNSCOPED_ALL);
        write("d.policy.bak", UNSCOPED_ALL);
        write("sub/e.policy", UNSCOPED_ALL);
        write("f.policy.grantperm", UNSCOPED_ALL);
        write("g.policy.proxy-permissions/h.policy", UNSCOPED_ALL);
        write("g.policy.proxy-permissions/META-INF/PERMISSIONS.LIST", "(java.security.AllPermission)\n");
        write("notes.txt", UNSCOPED_ALL);
        write("Readme.md", UNSCOPED_ALL);
        Run r = lint(dir.getPath());
        java.util.SortedSet<String> set = new java.util.TreeSet<String>();
        for (String l : r.findings()) {
            set.add(new File(l.replaceAll("^(.*):\\d+ [A-Z]\\d+ .*$", "$1")).getName());
        }
        List<String> names = new ArrayList<String>(set);
        // .grantperm files, files under *.proxy-permissions and a .txt that holds a grant are linted
        // too; a README, a .md and PERMISSIONS.LIST (a different grammar) are on the ignore list
        assertEquals(r.out, Arrays.asList("a.policy", "b.policy.old", "c.policy.new", "d.policy.bak",
                "e.policy", "f.policy.grantperm", "h.policy", "notes.txt", "policy.all"), names);
        assertTrue(r.out, r.out.contains("TOTAL files=9 "));
        // an explicit file is linted whatever its name; --glob adds to the walk
        assertEquals(1, lint(new File(dir, "notes.txt").getPath()).exit);
        Run g = lint("--glob", "*.txt", dir.getPath());
        assertTrue(g.out, g.out.contains("notes.txt:1 U1 "));
        assertFalse(g.out, g.out.contains("Readme.md"));
    }

    // Output order is file then line, whatever order the files were named.
    @Test
    public void outputSortedByFileThenLine() throws Exception {
        File b = write("b.policy", UNSCOPED_ALL + UNSCOPED_ALL);
        File a = write("a.policy", UNSCOPED_ALL);
        Run r = lint(b.getPath(), a.getPath());
        List<String> f = r.findings();
        assertEquals(r.out, 3, f.size());
        assertTrue(f.get(0).contains("a.policy:1 "));
        assertTrue(f.get(1).contains("b.policy:1 "));
        assertTrue(f.get(2).contains("b.policy:4 "));
    }

    // --format=tsv: five tab-separated fields per finding; provider, summaries and verdicts are '#' lines.
    @Test
    public void tsvFormat() throws Exception {
        Run r = lintFile(UNSCOPED_ALL, "--format=tsv");
        String[] lines = r.out.split("\n");
        String[] cols = lines[0].split("\t");
        assertEquals(r.out, 5, cols.length);
        assertEquals("1", cols[1]);
        assertEquals("U1", cols[2]);
        assertEquals("UNSCOPED_ALLPERMISSION", cols[3]);
        assertTrue(r.out, lines[1].startsWith("# PROVIDER "));
        assertTrue(r.out, lines[2].startsWith("# "));
        assertTrue(r.out, r.out.contains("\n# VERDICT "));
        assertTrue(r.out, r.out.contains("\n# files=1 "));
    }

    // --fix-hint prints text for U1 only and edits nothing.
    @Test
    public void fixHintPrintsOnlyAndNeverEdits() throws Exception {
        String s1 = "grant codeBase \"file:/a\" {\n permission java.security.AllPermission;\n};\n";
        File f = write("h.policy", UNSCOPED_ALL + s1);
        byte[] before = Files.readAllBytes(f.toPath());
        Run r = lint("--fix-hint", f.getPath());
        assertEquals(1, r.out.split("--- fix-hint", -1).length - 1);
        assertTrue(r.out, r.out.contains("grant digest \"SHA-256 TODO-hex-digest-of-the-jar\" {"));
        assertTrue(r.out, r.out.contains("h.policy:1"));
        assertArrayEquals(before, Files.readAllBytes(f.toPath()));
        assertFalse(lint(f.getPath()).out.contains("fix-hint"));
    }

    // A policy that is only a license header and plain grants is clean and linted (not skipped).
    @Test
    public void cleanFileCountsAsLinted() throws Exception {
        Run r = lintFile("// header\ngrant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\","
                + " \"read\";\n};\n");
        assertEquals(0, r.exit);
        assertTrue(r.out, r.out.contains("TOTAL files=1 "));
        assertTrue(r.out, r.verdictLine().contains("stranger=false"));
    }

    // ------------------------------------------------ the general dangerous-policy gate

    private Run lintFixture(String name, String... opts) throws Exception {
        List<String> a = new ArrayList<String>(Arrays.asList(opts));
        a.add(fixture(name).getPath());
        return lint(a.toArray(new String[0]));
    }

    private static String grantText(java.security.Permission p) {
        StringBuilder sb = new StringBuilder("grant {\n permission ").append(p.getClass().getName());
        if (p.getName() != null) sb.append(" \"").append(p.getName().replace("\\", "\\\\")
                .replace("\"", "\\\"")).append('"');
        if (p.getActions() != null && p.getActions().length() > 0) {
            sb.append(", \"").append(p.getActions()).append('"');
        }
        return sb.append(";\n};\n").toString();
    }

    // Gap (a): the probe tries https and the other schemes.  A tool that asked only for
    // http://evil.example.com reports S1 and exits 0 here.
    @Test
    public void httpsWildcardCodebaseGrantsAStranger() throws Exception {
        for (String f : new String[]{"g01-https-wild.policy", "g02-https-com.policy"}) {
            Run r = lintFixture(f);
            assertEquals(f + r.out, "U3 S1", r.kinds());
            assertEquals(f + r.out, 1, r.exit);
            assertTrue(f + r.out, r.verdictLine().contains("stranger=true"));
        }
        // ftp and a bare IP are strangers too
        assertEquals(1, lintFile("grant codeBase \"ftp://*/-\" {\n permission java.security.AllPermission;\n};\n").exit);
        assertEquals(1, lintFile("grant codeBase \"http://*:8080/-\" {\n permission java.security.AllPermission;\n};\n").exit);
    }

    // Gap (b): a principal of any class.  The old probe used an X500Principal only, so a
    // UserPrincipal wildcard answered principal=false although every UserPrincipal is granted.
    @Test
    public void principalOfAnyClassIsProbed() throws Exception {
        for (String f : new String[]{"g03-userprincipal-star.policy", "g04-x500-quoted-star.policy"}) {
            Run r = lintFixture(f);
            assertEquals(f + r.out, "S3", r.kinds());
            assertTrue(f + r.out, r.verdictLine().contains("principal=true"));
        }
        String rt = " {\n permission java.lang.RuntimePermission \"setSecurityManager\";\n};\n";
        // A wildcard principal CLASS is refused by the policy scanner (2026-11-01): the file does
        // not load, so the lint reports it as unparseable and fails the run.
        for (String who : new String[]{"* *", "* \"CN=alice\""}) {
            Run r = lintFile("grant principal " + who + rt, "--provider=platform");
            assertTrue(who + r.out, r.kinds().contains("P1"));
            assertEquals(who, 1, r.exit);
        }
        for (String who : new String[]{"com.sun.security.auth.UserPrincipal *",
            "javax.security.auth.x500.X500Principal *", "javax.security.auth.x500.X500Principal \"*\""}) {
            Run r = lintFile("grant principal " + who + rt);
            assertEquals(who + r.out, "D2", r.kinds());
            assertEquals(who, 1, r.exit);
            assertEquals(who, 1, lintFile("grant principal " + who + rt, "--strict").exit);
        }
        // a named principal is not a stranger
        assertEquals("", lintFile("grant principal com.sun.security.auth.UserPrincipal \"alice\""
                + rt).kinds());
    }

    // Gap (c): file:/-, jrt:/- and jar: carrying a dangerous permission fail the default run;
    // AllPermission for exactly one platform module stays clean.
    @Test
    public void broadFileAndPlatformCodebasesCount() throws Exception {
        Run r = lintFixture("g05-file-dash-write.policy");
        assertEquals(r.out, "D3", r.kinds());
        assertEquals(r.out, 1, r.exit);
        r = lintFixture("g06-jrt-dash.policy");
        assertEquals(r.out, "S1 D4", r.kinds());
        assertEquals(r.out, 1, r.exit);
        r = lintFixture("f07-file-dash.policy");
        assertEquals(r.out, "S1 D3", r.kinds());
        assertEquals(1, r.exit);
        String all = " {\n permission java.security.AllPermission;\n};\n";
        assertEquals("S1 D3", lintFile("grant codeBase \"file:${/}-\"" + all).kinds());
        // the legitimate platform grant, and a specific local directory, are clean
        r = lintFixture("c3-jrt-java-base.policy");
        assertEquals(r.out, "S5", r.kinds());
        assertEquals(r.out, 0, lintFixture("c3-jrt-java-base.policy", "--strict").exit);
        assertEquals(0, lintFile("grant codeBase \"file:/opt/app/lib/-\"" + all).exit);
    }

    // The catalogue: unscoped grants of each dangerous family, with the kind and the exit codes.
    @Test
    public void dangerousPermissionsGrantedToStrangers() throws Exception {
        Object[][] rows = {
            // fixture, kinds, exit, strict exit
            {"g07-allfiles-write-unscoped.policy", "D1 D5", 1, 1},
            {"g08-allfiles-read-unscoped.policy", "D5", 0, 1},
            {"g09-setsecuritymanager.policy", "D1", 1, 1},
            {"g10-securitypermission-star.policy", "D1 D5", 1, 1},
            {"g11-multi-escalation.policy", "D1", 1, 1},
            {"g12-grantperm-setpolicy.policy", "D1", 1, 1},
            {"g14-insertprovider-bc.policy", "D1", 1, 1},
            {"c4-fileread-dir.policy", "", 0, 0},
        };
        for (Object[] row : rows) {
            Run r = lintFixture((String) row[0]);
            assertEquals(row[0] + "\n" + r.out, row[1], r.kinds());
            assertEquals(row[0] + "\n" + r.out, row[2], r.exit);
            assertEquals((String) row[0], row[3], lintFixture((String) row[0], "--strict").exit);
        }
        Object[][] inline = {
            {"java.net.SocketPermission \"*\", \"connect,accept,listen\"", "D5"},
            {"java.util.PropertyPermission \"*\", \"read,write\"", "D1"},
            {"java.lang.RuntimePermission \"createClassLoader\"", "D1"},
            {"java.lang.RuntimePermission \"loadLibrary.*\"", "D1"},
            {"java.lang.RuntimePermission \"defineClassInPackage.foo\"", "D1"},
            {"java.lang.RuntimePermission \"getProtectionDomain\"", "D5"},
            {"java.lang.reflect.ReflectPermission \"suppressAccessChecks\"", "D1"},
            {"java.security.SecurityPermission \"setPolicy\"", "D1"},
            {"java.security.SecurityPermission \"removeProvider.SUN\"", "D1"},
            {"java.io.FilePermission \"<<ALL FILES>>\", \"execute\"", "D1"},
            {"java.io.FilePermission \"<<ALL FILES>>\", \"delete\"", "D1"},
            {"org.apache.river.api.io.DeSerializationPermission \"MARSHALLED\"", "D1"},
            {"net.jini.security.GrantPermission \"java.lang.RuntimePermission \\\"setSecurityManager\\\"\"", "D1"},
            {"net.jini.security.policy.UmbrellaGrantPermission", "U2 D1"},
            {"java.lang.RuntimePermission \"getenv.*\"", ""},
            {"java.util.PropertyPermission \"java.version\", \"read\"", ""},
            {"java.io.FilePermission \"/opt/app/-\", \"read,write\"", ""},
            {"java.net.SocketPermission \"*:1024-\", \"connect\"", "D5"},
            {"java.net.SocketPermission \"app.example.org:8080\", \"connect\"", ""},
        };
        for (Object[] row : inline) {
            Run r = lintFile("grant {\n permission " + row[0] + ";\n};\n");
            assertEquals(row[0] + "\n" + r.out, row[1], r.kinds());
        }
        // a write grant on a filesystem root or on the whole current directory (- or *)
        java.io.File root = java.io.File.listRoots()[0];
        String rootTree = (root.getPath() + "-").replace("\\", "\\\\");
        Run r = lintFile("grant {\n permission java.io.FilePermission \"" + rootTree
                + "\", \"write\";\n};\n");
        assertEquals(rootTree + r.out, "D1", r.kinds());
        assertEquals("D1", lintFile("grant {\n permission java.io.FilePermission \"-\", \"write\";\n};\n")
                .kinds());
    }

    // Every probe of the catalogue is found when granted on its own, in the kind of its severity:
    // a probe the provider is not asked about, or asked with the wrong severity, fails its row.
    @Test
    public void everyCatalogueProbeIsFoundWhenGranted() throws Exception {
        List<au.net.zeus.jgdms.api.policy.PermissionSets.Probe> probes =
                au.net.zeus.jgdms.api.policy.PermissionSets.probes();
        assertTrue(probes.size() > 30);
        for (au.net.zeus.jgdms.api.policy.PermissionSets.Probe p : probes) {
            if (p.id.equals("AllPermission")) continue;
            Run r = lintFile(grantText(p.permission), "--provider=platform");
            String want = p.severity >= au.net.zeus.jgdms.api.policy.PermissionSets.HIGH ? "D1" : "D5";
            assertEquals(p.id + "\n" + r.out, want, r.kinds().replace("U2 ", ""));
            // the provider, asked about the probe by itself, grants it to a stranger: the half of
            // the gate that does not read the text sees it too
            File f = write("probe.policy", grantText(p.permission));
            assertEquals(p.id, 1, PolicyVerdict.open("platform").grantedTo(f,
                    Collections.<String, String>emptyMap(), "https://evil.example.com/x.jar",
                    new java.security.Principal[0], Collections.singletonList(p.permission)).size());
        }
    }

    // An unknown permission class is judged by what the provider shows it implies, not by name.
    @Test
    public void unknownPermissionClassIsFoundByWhatItImplies() throws Exception {
        for (String provider : new String[]{"platform", "auto"}) {
            Run r = lintFixture("g13-trojan-class.policy", "--provider=" + provider);
            assertEquals(r.out, "D1", r.kinds());
            assertEquals(r.out, 1, r.exit);
            assertTrue(r.out, r.out.contains("RuntimePermission setSecurityManager"));
        }
    }

    // Severities and exits: high kinds fail the default run, medium ones only --strict.
    @Test
    public void newKindsFollowTheSeverityExitRules() throws Exception {
        Object[][] rows = {
            // text, default exit, strict exit
            {"grant {\n permission java.lang.RuntimePermission \"setSecurityManager\";\n};\n", 1, 1},   // D1
            {"grant principal * * {\n permission java.lang.RuntimePermission \"setSecurityManager\";\n};\n", 1, 1}, // D2
            {"grant codeBase \"file:/-\" {\n permission java.lang.RuntimePermission \"setSecurityManager\";\n};\n", 1, 1}, // D3
            {"grant codeBase \"jrt:/-\" {\n permission java.lang.RuntimePermission \"setSecurityManager\";\n};\n", 1, 1}, // D4
            {"grant {\n permission java.net.SocketPermission \"*\", \"connect\";\n};\n", 0, 1},        // D5
        };
        for (Object[] row : rows) {
            assertEquals(row[0] + lintFile((String) row[0]).out, row[1], lintFile((String) row[0]).exit);
            assertEquals((String) row[0], row[2], lintFile((String) row[0], "--strict").exit);
        }
    }

    // Gap (d): the walk matches names case-insensitively, reports files it does not recognise,
    // and warns about a directory that yielded nothing.
    @Test
    public void walkMatchesPolicyNamesCaseInsensitively() throws Exception {
        write("d/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        write("d/java.Policy", UNSCOPED_ALL);
        write("d/SERVER.POLICY.OLD", UNSCOPED_ALL);
        Run r = lint(new File(dir, "d").getPath());
        assertEquals(r.out, 2, r.count("U1"));
        // recognised by name: not P2, which would otherwise catch them as odd names
        assertEquals(r.out, 0, r.count("P2"));
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("TOTAL files=3 "));
    }

    @Test
    public void unrecognisedPolicyLookingFilesFailTheGate() throws Exception {
        write("d/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        write("d/server.pol", UNSCOPED_ALL);
        write("d/all.policy~", UNSCOPED_ALL);
        write("d/noext", UNSCOPED_ALL);
        write("d/notes.txt", UNSCOPED_ALL);
        write("d/blob.dat", "just some bytes, no policy words here\n");
        Run r = lint(new File(dir, "d").getPath());
        assertEquals(r.out, 4, r.count("P2"));
        assertEquals(r.out, 4, r.count("U1"));   // the unrecognised files are linted too, a .txt among them
        assertEquals(r.out, 1, r.count("W2"));
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("notes.txt:0 P2 "));
        assertTrue(r.out, r.out.contains("server.pol:0 P2 UNRECOGNISED_POLICY_FILE "));
        // a directory whose only odd file is not policy-like: a strict-only warning
        write("e/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        write("e/blob.dat", "no policy here\n");
        Run w = lint(new File(dir, "e").getPath());
        assertEquals(w.out, "W2", w.kinds());
        assertEquals(w.out, 0, w.exit);
        assertEquals(w.out, 1, lint("--strict", new File(dir, "e").getPath()).exit);
    }

    @Test
    public void emptyDirectoryNextToGoodFileWarns() throws Exception {
        File good = write("good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        File empty = new File(dir, "empty");
        assertTrue(empty.mkdirs());
        Run r = lint(good.getPath(), empty.getPath());
        assertEquals(r.out, "W1", r.kinds());
        assertEquals(r.out, 0, r.exit);
        assertEquals(r.out, 1, lint("--strict", good.getPath(), empty.getPath()).exit);
        // alone it is still a usage error: nothing was checked
        assertEquals(2, lint(empty.getPath()).exit);
    }

    @Test
    public void unreadableDirectoryWarns() throws Exception {
        Assume.assumeFalse("cannot chmod on Windows", System.getProperty("os.name").startsWith("Windows"));
        File good = write("d/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        File locked = new File(dir, "d/locked");
        assertTrue(locked.mkdirs());
        write("d/locked/x.policy", UNSCOPED_ALL);
        assertTrue(locked.setReadable(false) && locked.setExecutable(false));
        try {
            Assume.assumeFalse("running as a user that ignores modes", locked.canRead());
            Run r = lint("--strict", new File(dir, "d").getPath());
            assertEquals(r.out, 1, r.count("W1"));
            assertEquals(r.out, 1, r.exit);
        } finally {
            locked.setReadable(true);
            locked.setExecutable(true);
        }
    }

    // Gap (e): property names are printed, and --require-defined makes every codeBase property
    // be given with --define whatever the grant holds.
    @Test
    public void requireDefinedAndPropertyNames() throws Exception {
        String scoped = "grant codeBase \"file:${a.b}/-\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n";
        assertEquals("", lintFile(scoped).kinds());
        assertEquals(0, lintFile(scoped).exit);
        Run r = lintFile(scoped, "--require-defined");
        assertEquals(r.out, "U4", r.kinds());
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("[a.b]"));
        assertEquals("", lintFile(scoped, "--require-defined", "--define", "a.b=/opt").kinds());
        // a lint-JVM system property does not count as defined under --require-defined
        String sys = "grant codeBase \"file:/opt/${java.specification.version}/-\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n";
        assertEquals("", lintFile(sys).kinds());
        assertEquals("U4", lintFile(sys, "--require-defined").kinds());
        // S1 names the properties and where each value came from
        String all = "grant codeBase \"file:${a.b}/${java.specification.version}/-\" {\n permission java.security.AllPermission;\n};\n";
        Run s = lintFile(all, "--define", "a.b=/opt");
        assertEquals(s.out, "S1", s.kinds());
        assertTrue(s.out, s.out.contains("a.b (--define)"));
        assertTrue(s.out, s.out.contains("java.specification.version (lint JVM system property)"));
    }

    // The policies the repository ships (deploy/policy) raise no high-severity finding: the default
    // run exits 0.  Their only findings are D5 for SocketPermission to any host and for /tmp/*, a medium kind.
    @Test
    public void deployPoliciesStayClean() throws Exception {
        File deploy = new File("../../../deploy/policy");
        Assume.assumeTrue(deploy.isDirectory());
        Run r = lint(deploy.getPath());
        assertEquals(r.out, 0, r.exit);
        assertEquals(r.out, 0, r.count("U1") + r.count("U2") + r.count("U3") + r.count("U4")
                + r.count("P1") + r.count("P2") + r.count("S1") + r.count("S3") + r.count("D1")
                + r.count("D2") + r.count("D3") + r.count("D4") + r.count("W1") + r.count("W2"));
        for (String l : r.findings()) {
            assertTrue(l, l.contains(" D5 ") && (l.contains("SocketPermission") || l.contains("/tmp/*")));
        }
    }
}
