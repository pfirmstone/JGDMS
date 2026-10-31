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
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link PolicyLint}.  Each test states the invariant it defends:
 * an unscoped AllPermission is always found, a scoped or commented one is
 * classified as such and never as unscoped, and the exit status follows the
 * documented matrix.
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
                        && l.matches(".*:\\d+ [USP]\\d .*")) {
                    r.add(l);
                }
            }
            return r;
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

    // A tool that flagged every unscoped GrantPermission as U2 fails here.
    @Test
    public void u2NotRaisedForNarrowTargetOrScopedBlock() throws Exception {
        Run r = lintFile("grant {\n permission net.jini.security.GrantPermission"
                + " \"java.io.FilePermission \\\"/tmp/-\\\", \\\"read\\\"\";\n};\n"
                + "grant codeBase \"file:/x.jar\" {\n permission net.jini.security.GrantPermission"
                + " \"java.security.AllPermission\";\n};\n");
        assertEquals(r.out, 0, r.findings().size());
        assertEquals(0, r.exit);
    }

    // S1: a tool that treated a codeBase-only AllPermission as S2 (or U1) fails; the codeBase must be shown.
    @Test
    public void s1CodebaseOnlyAndSignedByOnly() throws Exception {
        Run r = lintFile("grant codeBase \"file:${jgdms.lib}/x.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant signedBy \"duke\" {\n permission java.security.AllPermission;\n};\n"
                + "grant signedBy \"duke\", codeBase \"file:/a/-\" {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, 3, r.count("S1"));
        assertEquals(0, r.count("S2"));
        assertEquals(0, r.count("U1"));
        assertTrue(r.out, r.out.contains("codeBase file:${jgdms.lib}/x.jar"));
        assertEquals(0, r.exit);
    }

    // S2: a tool that reported a digest- or principal-scoped grant as S1 fails.
    @Test
    public void s2DigestAndPrincipalScoped() throws Exception {
        Run r = lintFile("grant codeBase \"file:/a.jar\" digest \"SHA-256 00ff\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant principal javax.security.auth.x500.X500Principal \"CN=a\" {\n"
                + " permission java.security.AllPermission;\n};\n"
                + "grant codeBase \"file:/a.jar\" principal * * {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(r.out, 3, r.count("S2"));
        assertEquals(0, r.count("S1"));
        assertEquals(0, r.exit);
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
    }

    // A tool that scanned for the substring would flag the comment text of the deploy policy.
    @Test
    public void commentedAllPermissionIsNotAFinding() throws Exception {
        Run r = lintFile("/* Do NOT grant AllPermission\n * grant { permission java.security.AllPermission; };\n */\n"
                + "// grant { permission java.security.AllPermission; };\n"
                + "grant codeBase \"file:/a.jar\" digest \"SHA-256 aa\" {\n"
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
        assertTrue(r.out, r.out.contains("TOTAL files=1 U1=0 U2=0 S1=0 S2=0 P1=0 exit=0"));
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

    // Keyword case, CRLF, BOM and keystore lines must not change the result.
    @Test
    public void caseCrlfBomAndKeystore() throws Exception {
        String text = "keystore \"file:${user.home}/.keystore\", \"JKS\";\r\n"
                + "GRANT CodeBase \"file:${x}/y.jar\" {\r\n  PERMISSION java.security.AllPermission;\r\n};\r\n"
                + "Grant {\r\n  Permission java.security.AllPermission;\r\n};\r\n";
        byte[] body = text.getBytes("UTF-8");
        byte[] bom = new byte[body.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bom, 3, body.length);
        File f = write("bom.policy", bom);
        Run r = lint(f.getPath());
        assertEquals(r.out, 1, r.count("S1"));
        assertEquals(r.out, 1, r.count("U1"));
        assertTrue(r.out, r.out.contains("bom.policy:5 U1 "));
        assertTrue(r.out, r.out.contains("bom.policy:2 S1 "));
        assertEquals(0, r.count("P1"));
    }

    // Exit matrix: a tool that ignored --strict or --audit-input, or applied either to the wrong kind, fails a row.
    @Test
    public void exitCodeMatrix() throws Exception {
        String s1 = "grant codeBase \"file:/a\" {\n permission java.security.AllPermission;\n};\n";
        String s2 = "grant digest \"SHA-256 aa\" {\n permission java.security.AllPermission;\n};\n";
        String clean = "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n";
        String p1 = "grant {\n";
        String u2 = "grant { permission net.jini.security.GrantPermission \"*\"; };\n";
        Object[][] rows = {
            // text, strict, audit, expected exit
            {clean, false, false, 0}, {clean, true, true, 0},
            {UNSCOPED_ALL, false, false, 1}, {UNSCOPED_ALL, true, false, 1}, {UNSCOPED_ALL, false, true, 1},
            {u2, false, false, 1}, {u2, true, true, 1},
            {p1, false, false, 1}, {p1, true, true, 1},
            {s1, false, false, 0}, {s1, true, false, 1}, {s1, false, true, 1}, {s1, true, true, 1},
            {s2, false, false, 0}, {s2, true, false, 0}, {s2, false, true, 1}, {s2, true, true, 1},
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
        assertEquals(2, lint(dir.getPath()).exit);  // empty directory: nothing was checked
        assertEquals(2, lint("--glob").exit);
    }

    // Policy text must not reach the terminal raw: the codeBase below carries ESC, NUL and U+2028.
    @Test
    public void controlCharactersAreEscaped() throws Exception {
        Run r = lintFile("grant codeBase \"file:/a\u001b[31m\u0000 FAKE.jar\" {\n"
                + " permission java.security.AllPermission;\n};\n");
        assertEquals(1, r.count("S1"));
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
        List<String> names = new ArrayList<String>();
        for (String l : r.findings()) {
            names.add(new File(l.replaceAll("^(.*):\\d+ [USP]\\d .*$", "$1")).getName());
        }
        java.util.Collections.sort(names);
        assertEquals(r.out, Arrays.asList("a.policy", "b.policy.old", "c.policy.new", "d.policy.bak",
                "e.policy", "policy.all"), names);
        assertTrue(r.out, r.out.contains("TOTAL files=6 "));
        // an explicit file is linted whatever its name; --glob adds to the walk
        assertEquals(1, lint(new File(dir, "notes.txt").getPath()).exit);
        Run g = lint("--glob", "*.txt", dir.getPath());
        assertTrue(g.out, g.out.contains("notes.txt:1 U1 "));
        assertFalse(g.out, g.out.contains("Readme.md"));
        assertFalse(g.out, g.out.contains("h.policy"));
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

    // --format=tsv: five tab-separated fields per finding; summaries are '#' lines.
    @Test
    public void tsvFormat() throws Exception {
        Run r = lintFile(UNSCOPED_ALL, "--format=tsv");
        String[] lines = r.out.split("\n");
        String[] cols = lines[0].split("\t");
        assertEquals(r.out, 5, cols.length);
        assertEquals("1", cols[1]);
        assertEquals("U1", cols[2]);
        assertEquals("UNSCOPED_ALLPERMISSION", cols[3]);
        assertTrue(r.out, lines[1].startsWith("# "));
        assertTrue(r.out, lines[2].startsWith("# files=1 "));
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
    }
}
