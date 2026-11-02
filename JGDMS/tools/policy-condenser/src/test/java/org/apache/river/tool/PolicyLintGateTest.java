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
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * One test per item of the redesign of the general dangerous-policy gate (Opus review of
 * 66974f70f).  Each states the invariant it defends: a grant that reaches code or a caller an
 * attacker controls and holds a catalogued dangerous permission fails the default run, whatever
 * wildcard form the scope is written in; a grant that does not reach such a subject stays clean.
 * The tests use only the command line of {@code PolicyLint}, so they can be run against the lint
 * they replace.
 */
public class PolicyLintGateTest {

    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("policy-lint-gate").toFile();
    }

    @After
    public void tearDown() {
        ProxyPolicyGenerator.deleteTree(dir);
    }

    private File fx(String name) throws Exception {
        return LintHarness.copyResource("/policylint/corpus/" + name + ".policy", dir, "fx/" + name + ".policy");
    }

    private File write(String name, String text) throws Exception {
        File f = new File(dir, name);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes("UTF-8"));
        return f;
    }

    /** Default and strict exit status and the kinds of a corpus fixture, as "def/strict kinds". */
    private String verdict(String fixture, String... more) throws Exception {
        File f = fx(fixture);
        String[] a = new String[more.length + 1];
        System.arraycopy(more, 0, a, 0, more.length);
        a[more.length] = f.getPath();
        String[] s = new String[a.length + 1];
        s[0] = "--strict";
        System.arraycopy(a, 0, s, 1, a.length);
        LintHarness.Result d = LintHarness.run(a);
        LintHarness.Result t = LintHarness.run(s);
        return d.exit + "/" + t.exit + " " + d.kinds();
    }

    private void expect(String fixture, String want, String... more) throws Exception {
        assertEquals(fixture, want, verdict(fixture, more));
    }

    // 1. A wildcard host outside the fixed probe hosts reaches a stranger: anyone who can register
    // a name under *.net or serve on port 8443.  Pinned hosts do not; a pinned host holding
    // something worth a look is INFO.
    @Test
    public void wildcardHostsFailTheDefaultRun() throws Exception {
        expect("n/n01-https-net-ccl", "1/1 D1");
        expect("n/n02-http-exorg-allperm", "1/1 U3 S1");
        expect("n/n03-http-port-wild-setsm", "1/1 D1");
        expect("n/n04-ftp-star-ccl", "1/1 D1");
        expect("n/n07-https-port-range-ccl", "1/1 D1");
        expect("n/n64-jar-https-wild-ccl", "1/1 D1");
        expect("r8/h01-https-star-org", "1/1 U3 S1");
        expect("r8/h02-http-star-exorg", "1/1 U3 S1");
        expect("r8/h03-http-star-port", "1/1 U3 S1");
        expect("r8/h04-https-star-net-ccl", "1/1 D1");
        // the clean cases: a pinned host with a narrow permission, and a pinned host with INFO
        expect("n/n05-https-pinned-narrow", "0/0 ");
        expect("n/n06-https-pinned-getclassloader", "0/0 I1");
    }

    // 2. A wildcard principal of a class an outside party issues names is a stranger, whether or not
    // the lint can build such a principal; a namespace prefix of a trust domain is not.
    @Test
    public void wildcardPrincipalsOfIssuedClassesFail() throws Exception {
        expect("r8/q05-jwtprincipal-star", "1/1 S3");
        expect("r8/q06-kerberos-star", "1/1 S3");
        expect("r8/q07-spiffe-star-setsm", "1/1 D2");
        expect("r8/q08-jwt-star-ccl", "1/1 D2");
        expect("r8/q09-krb-star-ccl", "1/1 D2");
        expect("r8/q03-princ-star-setsm", "1/1 P1 D2"); // wildcard principal class: refused by the scanner
        expect("r8/q04-x500star-files", "1/1 D2");
        expect("n/n13-spiffe-bare-star-allperm", "1/1 S3");
        // medium permissions stay strict-only
        expect("n/n14-x500-star-medium", "0/1 D5");
        // a named trust-domain namespace is pinned by the issuer
        expect("n/n12-spiffe-prefix-namespace-ccl", "0/0 ");
        // a named principal is not a stranger
        expect("r8/q01-princ-unloadable", "0/0 ");
    }

    // 2b. A class the lint can load but cannot make from a name still gets a principal (a generated
    // class of the same name), and the provider is asked about it.  Without it this grant is clean.
    @Test
    public void principalStandInIsAskedAbout() throws Exception {
        LintHarness.Result r = LintHarness.run(fx("n/n10-unbuildable-principal-star-ccl").getPath());
        assertEquals(r.out, "D2", r.kinds());
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("NOTE") && r.out.contains("generated class of the same name"));
    }

    // 2c. Where no principal can be made at all (the class is in java.* and has no constructor),
    // the grant is treated as reaching a stranger instead of a NOTE and exit 0.
    @Test
    public void principalThatCannotBeMadeFailsClosed() throws Exception {
        LintHarness.Result r = LintHarness.run(fx("n/n11-java-principal-star-ccl").getPath());
        assertEquals(r.out, "D2", r.kinds());
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("can be neither made nor stood in for"));
    }

    // 3. FilePermission names are expanded (--define and the lint's properties) and normalised.
    @Test
    public void filePermissionNamesAreExpanded() throws Exception {
        expect("r8/f10-userhome-dash-write", "1/1 D1");
        expect("r8/f11-userhome-star-write", "1/1 D1");
        expect("r8/f15-dotdot-write", "1/1 D1");
        expect("r8/f16-dotdot-fwd-write", "1/1 D1");
        expect("r8/f17-javahome-write", "1/1 D1");
        expect("r8/f23-users-dotdot-write", "1/1 D1");
        expect("n/n20-tmpdir-star-write", "0/1 D5");   // a temp directory's own files: medium
        expect("n/n22-dotdot-root-write", "1/1 D1");
        expect("n/n24-javahome-conf-write", "1/1 D1");
        expect("n/n25-userdir-dash-execute", "1/1 D1");
        expect("n/n26-home-read-medium", "0/1 D5");
        // an unresolvable property in a file target is the root; defined, it is an ordinary path
        expect("n/n21-undefined-prop-write", "1/1 D1");
        expect("n/n21-undefined-prop-write", "0/0 ", "--define", "catalina.home=/opt/tomcat/work");
        // a subdirectory of the home is an application's own space
        expect("n/n23-home-subdir-write-clean", "0/0 ");
    }

    // 4. GrantPermission in the delim= form, and any form GrantPermission's own parser accepts.
    @Test
    public void grantPermissionForms() throws Exception {
        expect("n/n30-grantperm-delim-ccl", "1/1 D1");
        expect("n/n31-grantperm-assertprincipal", "1/1 D1");
        expect("n/n32-grantperm-star", "1/1 U2 D1");
        expect("n/n33-grantperm-java-io-star", "1/1 D1");
        expect("n/n35-grantperm-delim-quote-semicolon", "1/1 D1");
        expect("r8/gp03-grantperm", "1/1 D1");
        expect("r8/gp05-grantperm", "1/1 D1");
        expect("r8/gp06-grantperm", "1/1 D1");
        expect("r8/gp09-grantperm", "1/1 D1");
        expect("n/n34-grantperm-narrow-clean", "0/0 ");
    }

    // 5. The catalogue gaps; each read as clean on trunk.
    @Test
    public void catalogueGaps() throws Exception {
        String[] high = {"r8/j01-assertprincipal-star", "r8/j02-privcred-star", "r8/j18-netperm",
            "r8/j19-sslperm", "r8/r08-rt-accessClassInPackage_jdk_internal_misc",
            "r8/p02-property-policy-write", "r8/c10-sec-clearProviderProperties_S", "r8/j11-mbean-star",
            "r8/j09-mgmt-control", "r8/j10-jdk-attach", "r8/j12-mbeantrust", "r8/j04-loadclass-star",
            "r8/j05-dc-loadclass", "n/n40-property-security-manager-write", "n/n41-serialobject-star",
            "n/n44-mbean-invoke-star", "n/n45-privcred-wild-name", "n/n46-assertprincipal-prefix"};
        for (String f : high) {
            String v = verdict(f);
            assertTrue(f + " " + v, v.startsWith("1/1 D1"));
        }
        String[] medium = {"r8/r14-rt-setIO", "r8/r04-rt-getClassLoader", "r8/r12-rt-modifyThread",
            "r8/r13-rt-modifyThreadGroup", "r8/r18-rt-writeFileDescriptor", "r8/r19-rt-readFileDescriptor",
            "r8/r22-rt-charsetProvider", "r8/r23-rt-enableContextClassLoaderOverride",
            "r8/r24-rt-manageProcess", "r8/c01-sec-getPolicy", "r8/c03-sec-createPolicy_JavaPolicy",
            "r8/j13-delegation", "r8/j14-servicepermission", "r8/j15-downloadperm", "n/n43-loadclass-allow"};
        for (String f : medium) assertEquals(f, "0/1 D5", verdict(f));
        expect("n/n42-serialobject-named-clean", "0/0 ");
        expect("n/n47-assertprincipal-exact-clean", "0/0 ");
    }

    // 5b/7. A permission class the lint cannot load or build is "cannot tell": medium (strict) where
    // the scope reaches no stranger, high where it does; it no longer reads as clean.
    @Test
    public void unknownPermissionClassIsNeverClean() throws Exception {
        expect("n/n50-unknown-class-unscoped", "1/1 D1");
        expect("n/n52-unknown-class-https-wild", "1/1 D1");
        expect("n/n51-unknown-class-pinned", "0/1 D6");
        expect("r8/j07-unknown-class", "1/1 D1");
        // a GrantPermission target its own parser rejects is not "not loadable"
        LintHarness.Result r = LintHarness.run(fx("n/n33-grantperm-java-io-star").getPath());
        assertFalse(r.out, r.out.contains("GrantPermission is not loadable"));
    }

    // 8. A permission whose implies throws: cannot tell, so fail closed.
    @Test
    public void impliesThatThrowsIsCannotTell() throws Exception {
        expect("n/n53-throws-unscoped", "1/1 D1");
        expect("n/n54-throws-pinned", "0/1 D6");
    }

    // 9. codeBases in user-writable places and the broad forms.
    @Test
    public void codeBasesInWritablePlacesFail() throws Exception {
        expect("r6/a35-userhome", "1/1 S1 D3");
        expect("r8/k08-userhome-ccl", "1/1 D3");
        expect("r8/k09-tmpdir-all", "1/1 S1 D3");
        expect("n/n60-userdir-codebase-ccl", "1/1 D3");
        expect("n/n61-tmpdir-codebase-allperm", "1/1 S1 D3");
        expect("n/n62-relative-codebase-ccl", "1/1 D3");
        expect("r8/k04-jar-file-dash", "1/1 D3");
        expect("r8/k06-file-star", "1/1 D3");
        expect("r8/k07-file-slash", "1/1 D3");
        // clean: a fixed application directory
        expect("n/n63-file-opt-app-ccl-clean", "0/0 ");
    }

    // The clean cases of the brief stay clean.
    @Test
    public void cleanCasesStayClean() throws Exception {
        expect("r8/ok1-jrt-javabase", "0/0 S5");
        expect("r8/ok2-fileread-dir", "0/0 ");
        File deploy = new File("../../../deploy/policy/host1-lookup.policy");
        Assume.assumeTrue(deploy.isFile());
        LintHarness.Result r = LintHarness.run(deploy.getPath());
        assertEquals(r.out, 0, r.exit);
        assertEquals(r.out, 0, LintHarness.run("--strict", deploy.getPath()).exit);
        assertEquals(r.out, "", r.kinds());
    }

    // 6. The directory walk lints what is in the files, not what they are called.
    @Test
    public void walkLintsAnyFileThatReadsLikeAPolicy() throws Exception {
        String all = "grant {\n permission java.security.AllPermission;\n};\n";
        for (String name : new String[]{"all.txt", "conf.properties", "server.xml", "x.dat", "noext", "p.json"}) {
            File d = new File(dir, "w-" + name);
            write("w-" + name + "/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission"
                    + " \"x\", \"read\";\n};\n");
            write("w-" + name + "/" + name, all);
            LintHarness.Result r = LintHarness.run(d.getPath());
            assertEquals(name + "\n" + r.out, 1, r.exit);
            assertTrue(name + "\n" + r.out, r.count("P2") == 1 && r.count("U1") == 1);
        }
        // the short ignore list stays ignored; a .txt without the words is not a finding
        write("ign/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        write("ign/README", all);
        write("ign/notes.md", all);
        write("ign/app.log", all);
        write("ign/lib.jar", all);
        write("ign/C.class", all);
        write("ign/p.png", all);
        write("ign/plain.txt", "release notes only\n");
        LintHarness.Result r = LintHarness.run("--strict", new File(dir, "ign").getPath());
        assertEquals(r.out, 0, r.exit);
        assertEquals(r.out, "", r.kinds());
    }

    // 6b. Padding does not hide a grant: the whole file is read; a file over the 16 MiB cap is P1.
    @Test
    public void walkReadsTheWholeFileAndCapsItsSize() throws Exception {
        StringBuilder pad = new StringBuilder(2200000);
        for (int i = 0; i < 2200000; i++) pad.append(' ');
        write("pad/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        write("pad/server.pol", pad + "grant {\n permission java.security.AllPermission;\n};\n");
        LintHarness.Result r = LintHarness.run(new File(dir, "pad").getPath());
        assertEquals(r.out, 1, r.exit);
        assertEquals(r.out, 1, r.count("P2"));
        assertEquals(r.out, 1, r.count("U1"));
        // over the hard cap: not read, P1
        write("big/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        for (String name : new String[]{"huge.policy", "huge.dat"}) {
            RandomAccessFile raf = new RandomAccessFile(new File(dir, "big/" + name), "rw");
            try {
                raf.setLength((16L << 20) + 1);
            } finally {
                raf.close();
            }
        }
        LintHarness.Result b = LintHarness.run(new File(dir, "big").getPath());
        assertEquals(b.out, 1, b.exit);
        assertEquals(b.out, 2, b.count("P1"));
    }

    // 6c. .grantperm files and *.proxy-permissions directories are linted like policies.
    @Test
    public void grantpermAndProxyPermissionsAreLinted() throws Exception {
        String all = "grant {\n permission java.security.AllPermission;\n};\n";
        write("gp/x.policy.grantperm", all);
        write("gp/y.proxy-permissions/hide.policy", all);
        LintHarness.Result r = LintHarness.run(new File(dir, "gp").getPath());
        assertEquals(r.out, 1, r.exit);
        assertEquals(r.out, 2, r.count("U1"));
        assertTrue(r.out, r.out.contains("x.policy.grantperm:") && r.out.contains("hide.policy:"));
    }

    private boolean link(File link, File target) throws Exception {
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.getPath(), target.getPath())
                        .redirectErrorStream(true).start();
                p.getInputStream().close();
                return p.waitFor() == 0 && link.exists();
            }
            Files.createSymbolicLink(link.toPath(), target.toPath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 6d. Links (Windows junctions, symbolic links) are followed; a loop is a finding.
    @Test
    public void linkLoopIsAFinding() throws Exception {
        File w = new File(dir, "loop");
        write("loop/good.policy", "grant codeBase \"file:/a\" {\n permission java.io.FilePermission \"x\", \"read\";\n};\n");
        File sub = new File(w, "sub");
        assertTrue(sub.mkdirs());
        Assume.assumeTrue("cannot create a link here", link(new File(sub, "up"), w));
        LintHarness.Result r = LintHarness.run(w.getPath());
        assertEquals(r.out, 1, r.count("P3"));
        assertEquals(r.out, 1, r.exit);
        assertTrue(r.out, r.out.contains("link loop"));
    }

    // 6e. A file below a link is linted (the walk used to skip the link silently), and a chain of
    // more than eight links is reported, not followed.
    @Test
    public void linksAreFollowedToADepthLimit() throws Exception {
        String all = "grant {\n permission java.security.AllPermission;\n};\n";
        File w = new File(dir, "chain");
        assertTrue(w.mkdirs());
        File store = new File(dir, "store");
        for (int i = 0; i <= 10; i++) assertTrue(new File(store, "t" + i).mkdirs());
        write("store/t0/near.policy", all);
        write("store/t10/far.policy", all);
        Assume.assumeTrue("cannot create a link here", link(new File(w, "l0"), new File(store, "t0")));
        for (int i = 0; i < 10; i++) {
            assertTrue(link(new File(store, "t" + i + "/l" + (i + 1)), new File(store, "t" + (i + 1))));
        }
        LintHarness.Result r = LintHarness.run(w.getPath());
        assertTrue(r.out, r.out.contains("near.policy:"));            // one link deep: linted
        assertFalse(r.out, r.out.contains("far.policy:"));            // ten links deep: not followed
        assertEquals(r.out, 1, r.count("P3"));
        assertTrue(r.out, r.out.contains("more than 8 links deep"));
        assertEquals(r.out, 1, r.exit);
        // a single link to a directory holding a policy is linted
        File v = new File(dir, "once");
        assertTrue(v.mkdirs());
        assertTrue(link(new File(v, "l"), new File(store, "t0")));
        LintHarness.Result o = LintHarness.run(v.getPath());
        assertTrue(o.out, o.out.contains("near.policy:") && o.count("U1") >= 1);
    }

    // The independence of the two halves, for the record: a grant that only the symbolic half can see
    // (no fixed probe host matches the wildcard) and one that only the provider half can see.
    @Test
    public void eitherHalfRaisesAFinding() throws Exception {
        // symbolic only: the fixed probe hosts end in .com, an IP and :8443 on evil.example.com
        LintHarness.Result s = LintHarness.run(fx("n/n01-https-net-ccl").getPath());
        assertTrue(s.out, s.out.contains("symbolic scope check"));
        // provider only: a permission class that implies a dangerous one by what it does
        LintHarness.Result p = LintHarness.run(
                new File(PolicyLintGateTest.class.getResource("/policylint/g13-trojan-class.policy").toURI()).getPath());
        assertEquals(p.out, "D1", p.kinds());
        assertFalse(p.out, p.out.contains("symbolic scope check"));
        assertEquals(Arrays.asList(1), Arrays.asList(p.exit));
    }

    // ---------------------------------------------------------------- Opus follow-ups (round 10)

    // L1. Permission classes the catalogue missed.  The operator's position: the lint is a general
    // dangerous-policy gate, so a catalogue miss must not read as clean.  High: the holder defines
    // classes, uses off-heap memory, acts as another subject, drives the flight recorder or the
    // debugger.  Medium (strict only): links, socket options, the JDBC global settings.
    @Test
    public void classesTheCatalogueMissedAreCatalogued() throws Exception {
        String[] high = {"n/n81-defineclass-unscoped", "n/n82-nativememory-unscoped",
            "n/n83-subjectdelegation-unscoped", "n/n84-flightrecorder-unscoped", "n/n85-jdi-unscoped",
            "n/n92-defineclass-star-unscoped"};
        for (String f : high) expect(f, "1/1 D1");
        String[] medium = {"n/n86-linkperm-unscoped", "n/n87-networkperm-unscoped",
            "n/n88-sql-setsyncfactory-unscoped", "n/n89-sql-setnetworktimeout-unscoped",
            "r8/j17-sqlperm"};
        for (String f : medium) expect(f, "0/1 D5");
        // the scope still decides: pinned stays INFO, a wildcard host is a stranger
        expect("n/n90-defineclass-pinned-https", "0/0 I1");
        expect("n/n91-defineclass-wild-https", "1/1 D1");
    }

    // L2 (operator ruling 2026-11-01).  A loadable class the catalogue does not know may be an
    // application permission, and deployers define their own: it is a NOTE, never a finding, and
    // it never changes the exit status, in any scope.  A class the lint cannot load stays D6/D1.
    @Test
    public void uncataloguedLoadableClassIsInformationOnly() throws Exception {
        String cls = "net.jini.export.ExportPermission";
        File f = fx("n/n80-uncatalogued-loadable-unscoped");
        for (String[] a : new String[][]{{f.getPath()}, {"--strict", f.getPath()}}) {
            LintHarness.Result r = LintHarness.run(a);
            assertEquals(r.out, 0, r.exit);
            assertEquals(r.out, "", r.kinds());
            assertTrue(r.out, r.out.contains("NOTE") && r.out.contains(cls));
        }
        File wild = write("wild.policy", "grant codeBase \"https://*.example.com/-\" {\n  permission "
                + cls + " \"exportRemoteInterface.*\";\n};\n");
        LintHarness.Result w = LintHarness.run("--strict", wild.getPath());
        assertEquals(w.out, 0, w.exit);
        assertTrue(w.out, w.out.contains("NOTE") && w.out.contains(cls));
        // a class the catalogue knows, with a harmless name, is not noted
        File known = write("known.policy", "grant {\n  permission java.util.PropertyPermission"
                + " \"user.language\", \"read\";\n};\n");
        LintHarness.Result k = LintHarness.run("--strict", known.getPath());
        assertEquals(k.out, 0, k.exit);
        assertFalse(k.out, k.out.contains("NOTE"));
        // the class the lint cannot load is still judged: unchanged from the commit before
        expect("n/n50-unknown-class-unscoped", "1/1 D1");
        expect("n/n51-unknown-class-pinned", "0/1 D6");
    }

    // L3. A file the parser accepts and the provider cannot load is a finding (P1), not "nothing
    // granted"; the cross-check helper refuses the same file instead of answering "no".
    @Test
    public void providerThatCannotLoadWhatTheParserAcceptsIsAFinding() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "org.apache.river.tool.stub.RefusingPolicy";
        try {
            File f = write("clean.policy", "grant codeBase \"file:/opt/app/-\" {\n  permission"
                    + " java.util.PropertyPermission \"user.language\", \"read\";\n};\n");
            LintHarness.Result r = LintHarness.run(f.getPath());
            assertEquals(r.out, 1, r.exit);
            assertEquals(r.out, "P1", r.kinds());
            assertTrue(r.out, r.out.contains("provider cannot load a file the parser accepts"));
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }

    @Test
    public void grantedToRefusesAFileTheProviderCannotLoad() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "org.apache.river.tool.stub.RefusingPolicy";
        try {
            PolicyVerdict provider = PolicyVerdict.open("auto");
            File f = write("any.policy", "grant {\n  permission java.security.AllPermission;\n};\n");
            try {
                provider.grantedTo(f, java.util.Collections.<String, String>emptyMap(),
                        "https://evil.example.com/x.jar", new java.security.Principal[0],
                        java.util.Arrays.<java.security.Permission>asList(
                                new java.security.AllPermission()));
                fail("a provider that cannot load the file must not be read as granting nothing");
            } catch (IllegalStateException expected) {
                assertTrue(String.valueOf(expected), expected.getMessage().contains("cannot load"));
            }
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }

    // L4. A file: codeBase with a host is a network share, not a local directory; a scheme the lint
    // does not recognise is attacker-controlled (fail closed); jar: is judged by what it wraps.
    @Test
    public void remoteShareCodeBasesAreStrangers() throws Exception {
        expect("n/n70-file-host-share-ccl", "1/1 D1");
        expect("n/n71-file-host-share-allperm", "1/1 U3 S1");
        expect("n/n72-file-unc-fourslash-ccl", "1/1 D1");
        expect("n/n79-file-host-only-ccl", "1/1 D1");
        expect("n/n77-jar-file-host-share-ccl", "1/1 D1");
        // local forms stay clean
        expect("n/n73-file-localhost-ccl-clean", "0/0 ");
        expect("n/n74-file-empty-host-ccl-clean", "0/0 ");
    }

    // Windows opens file:////host/share and every longer run of slashes as the UNC share
    // \host\share, so the only local forms are one slash, three slashes (empty authority) and
    // //localhost.  A pinned local directory that is in fact a share is a stranger.
    @Test
    public void slashRunsOfAFileCodeBaseDecideLocalOrShare() throws Exception {
        expect("n/n93-file-1slash-ccl-clean", "0/0 ");
        expect("n/n74-file-empty-host-ccl-clean", "0/0 ");
        expect("n/n73-file-localhost-ccl-clean", "0/0 ");
        expect("n/n72-file-unc-fourslash-ccl", "1/1 D1");
        expect("n/n94-file-5slash-ccl", "1/1 D1");
        expect("n/n95-file-6slash-ccl", "1/1 D1");
        expect("n/n96-file-7slash-ccl", "1/1 D1");
        expect("n/n97-file-127001-ccl", "1/1 D1");
        expect("n/n98-unc-backslash-ccl", "1/1 D1");
        expect("n/n99-jar-file-5slash-ccl", "1/1 D1");
        expect("n/n100-jar-file-7slash-ccl", "1/1 D1");
        expect("n/n101-file-localhost-then-host-ccl", "1/1 D1");
    }

    private static PolicyScope.Kind kindOf(String codeBase) {
        return PolicyScope.classify(codeBase, new java.util.Properties()).kind;
    }

    @Test
    public void slashRunTable() {
        String tail = "fileserver.example/share/-";
        PolicyScope.Kind[] want = new PolicyScope.Kind[8];
        want[1] = PolicyScope.Kind.FILE_PINNED;
        want[2] = PolicyScope.Kind.REMOTE_WILD;
        want[3] = PolicyScope.Kind.FILE_PINNED;
        for (int n = 4; n <= 7; n++) want[n] = PolicyScope.Kind.REMOTE_WILD;
        for (int n = 1; n <= 7; n++) {
            StringBuilder sl = new StringBuilder();
            for (int i = 0; i < n; i++) sl.append('/');
            assertEquals("file:" + sl + tail, want[n], kindOf("file:" + sl + tail));
            assertEquals("jar:file:" + sl + "x/a.jar", want[n],
                    kindOf("jar:file:" + sl + "fileserver.example/share/a.jar!/"));
            if (n == 2 || n >= 4) {
                assertEquals("scheme-less " + sl, PolicyScope.Kind.REMOTE_WILD, kindOf(sl + tail));
            }
        }
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("file://localhost/opt/app/-"));
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("file://LOCALHOST/opt/app/-"));
        assertEquals(PolicyScope.Kind.REMOTE_WILD, kindOf("file://127.0.0.1/opt/app/-"));
        assertEquals(PolicyScope.Kind.REMOTE_WILD,
                kindOf("file://localhost//fileserver.example/share/-"));
        String bs = "\\";
        assertEquals(PolicyScope.Kind.REMOTE_WILD,
                kindOf(bs + bs + "fileserver.example" + bs + "share" + bs + "-"));
        assertEquals(PolicyScope.Kind.REMOTE_WILD,
                kindOf("file:///" + bs + bs + "fileserver.example" + bs + "share" + bs + "-"));
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("file:/" + bs + bs + "opt" + bs + "app" + bs + "-"));
    }

    // Opus review 2026-11-02, finding 1.  After localhost only one slash may follow, and the path
    // part of a file: URL is percent-decoded (%2F and %5C are separators) before the slash rule is
    // applied; a malformed escape fails closed.  Every row is (codeBase, kind).
    private static void table(Object[][] rows) {
        for (Object[] r : rows) {
            assertEquals((String) r[0], r[1], kindOf((String) r[0]));
        }
    }

    @Test
    public void localhostIsLocalOnlyBeforeASinglePathSlash() {
        PolicyScope.Kind loc = PolicyScope.Kind.FILE_PINNED;
        PolicyScope.Kind rem = PolicyScope.Kind.REMOTE_WILD;
        String b = "\\";
        table(new Object[][]{
            {"file://localhost/opt/app/-", loc},
            {"file://LOCALHOST/opt/app/-", loc},
            {"file://localhost/localhost/share/-", loc},
            {"jar:file://localhost/opt/app/a.jar!/", loc},
            {"file://localhost//host/share/-", rem},
            {"file://localhost///host/share/-", rem},
            {"file://localhost////host/share/-", rem},
            {"file://localhost/" + b + b + "host" + b + "share/-", rem},
            {"file://localhost" + b + b + "host" + b + "share/-", rem},
            {"file://localhost" + b + b + b + b + "host" + b + "share/-", rem},
            {"file://localhost/" + b + "host/share/-", rem},
            {"jar:file://localhost///host/share/a.jar!/", rem},
            {"jar:file://localhost/" + b + b + "host" + b + "share/a.jar!/", rem},
            {"jar:file://localhost//host/share/a.jar!/", rem},
            {"file://localhost:80/opt/-", rem},
            {"file://LocalHost./opt/-", rem},
            {"file://%6Cocalhost/opt/-", rem},
        });
    }

    @Test
    public void percentEncodedSeparatorsAreDecodedBeforeTheSlashRule() {
        PolicyScope.Kind rem = PolicyScope.Kind.REMOTE_WILD;
        table(new Object[][]{
            {"file:/%2F%2Fhost/share/-", rem},
            {"file:/%2Fhost/share/-", rem},
            {"file:///%2Fhost/share/-", rem},
            {"file:/%5C%5Chost/share/-", rem},
            {"file:/%5Chost/share/-", rem},
            {"file:%2F%2Fhost/share/-", rem},
            {"file:%5C%5Chost%5Cshare/-", rem},
            {"file:/%2f%2fhost/share/-", rem},
            {"file://localhost/%2Fhost/share/-", rem},
            {"file://localhost/%5C%5Chost/share/-", rem},
            {"jar:file:/%2F%2Fhost/share/a.jar!/", rem},
            {"jar:file:/%5C%5Chost/share/a.jar!/", rem},
            {"jar:file:///%2Fhost/share/a.jar!/", rem},
            {"jar:jar:file:/%2F%2Fhost/share/a.jar!/b.jar!/", rem},
        });
    }

    @Test
    public void ordinaryEscapesAreDecidedAfterDecoding() {
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("file:/opt/%61pp/-"));
        assertEquals(kindOf("file:/C:/x/-"), kindOf("file:/%43:/x/-"));
        assertEquals(kindOf("file:/A:/x/-"), kindOf("file:/%41:/x/-"));
        assertEquals(kindOf("file:/opt/../x/-"), kindOf("file:/opt/%2e%2e/x/-"));
        assertEquals(kindOf("file:/opt/%2E%2E/x/-"), kindOf("file:/opt/%2e%2e/x/-"));
        assertEquals(kindOf("file:/opt/a b/-"), kindOf("file:/opt/a%20b/-"));
        assertEquals(kindOf("file:/opt/\u00e9/-"), kindOf("file:/opt/%C3%A9/-"));
        // the escape is decoded to a temp directory name, which makes the codeBase user-writable
        assertEquals(PolicyScope.Kind.FILE_WRITABLE, kindOf("file:/%74mp/x/-"));
        assertEquals(PolicyScope.Kind.FILE_WRITABLE, kindOf("file:/var/%74mp/x/-"));
        // a plain path is not a URL: no decoding
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("/opt/app%2Fx/-"));
        assertEquals(PolicyScope.Kind.FILE_PINNED, kindOf("/opt/app%zz/-"));
    }

    @Test
    public void aMalformedEscapeInAFileCodeBaseIsAStranger() {
        PolicyScope.Kind rem = PolicyScope.Kind.REMOTE_WILD;
        table(new Object[][]{
            {"file:/opt/%zz/-", rem},
            {"file:/opt/%/-", rem},
            {"file:/opt/100%", rem},
            {"file:/opt/%4", rem},
            {"file:/opt/%4/-", rem},
            {"file:/opt/%G1/-", rem},
            {"file:///opt/%zz/-", rem},
            {"file://localhost/opt/%zz/-", rem},
            {"jar:file:/opt/%zz/a.jar!/", rem},
            {"jar:jar:file:/opt/%/a.jar!/b.jar!/", rem},
        });
    }

    @Test
    public void everySlashRunKeepsItsResult() {
        PolicyScope.Kind loc = PolicyScope.Kind.FILE_PINNED;
        PolicyScope.Kind rem = PolicyScope.Kind.REMOTE_WILD;
        String bs = "\\";
        table(new Object[][]{
            {"file:/opt/x/-", loc}, {"file:/C:/x/-", loc}, {"file:///C:/x/-", loc},
            {"file:///opt/x/-", loc}, {"file://localhost/C:/x/-", loc},
            {"file://" + "/opt/x/-", loc},
            {"file://host/share/-", rem}, {"file://127.0.0.1/share/-", rem},
            {"file:////host/share/-", rem}, {"file://///host/share/-", rem},
            {"file://////host/share/-", rem}, {"file:///////host/share/-", rem},
            {"file://C:/x/-", rem}, {"file:////C:/x/-", rem},
            {"//opt/x/-", rem}, {bs + bs + "host" + bs + "share" + bs + "-", rem},
            {"jar:file:/opt/x/a.jar!/", loc}, {"jar:file:///C:/x/a.jar!/", loc},
            {"jar:file:////host/share/a.jar!/", rem}, {"jar:file://host/share/a.jar!/", rem},
        });
    }

    @Test
    public void unrecognisedCodeBaseSchemesFailClosed() throws Exception {
        expect("n/n75-unknown-scheme-ccl", "1/1 D1");
        expect("n/n76-jar-unknown-nested-ccl", "1/1 D1");
        expect("n/n78-jar-nested-jar-https-ccl", "1/1 D1");
    }
}
