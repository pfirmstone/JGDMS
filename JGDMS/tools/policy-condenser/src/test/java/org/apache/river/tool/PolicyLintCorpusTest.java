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

import au.net.zeus.jgdms.api.policy.PermissionSets;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.security.auth.x500.X500Principal;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The regression corpus of the general dangerous-policy gate: the adversarial policies of the two
 * Opus reviews (policylint/corpus/r6 and r8) and the files added with the redesign (corpus/n).  The
 * expectations file {@code corpus-expectations.tsv} gives, for each file, the exit status of the
 * default run and of {@code --strict}, and the finding kinds in output order.  The two trunk
 * columns are what the lint of commit 66974f70f answered; they are documentation, not asserted.
 *
 * <p>Invariants: the lint says what the file says (the expectations file, reviewed by hand against
 * the provider); a file the lint passes in the default run grants no stranger a high-severity
 * permission when the production provider is asked questions the lint does not ask itself
 * ({@link #cleanFilesGrantNoStrangerAHighPermission}).
 */
public class PolicyLintCorpusTest {

    private static final String EXPECTATIONS = "/policylint/corpus-expectations.tsv";

    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("policy-lint-corpus").toFile();
    }

    @After
    public void tearDown() {
        ProxyPolicyGenerator.deleteTree(dir);
    }

    static final class Row {
        String file;
        int def;
        int strict;
        String kinds;
        String trunkDef;
        String trunkStrict;
    }

    static List<Row> rows() throws Exception {
        List<Row> res = new ArrayList<Row>();
        InputStream in = PolicyLintCorpusTest.class.getResourceAsStream(EXPECTATIONS);
        assertNotNull("missing " + EXPECTATIONS, in);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            for (String l; (l = r.readLine()) != null;) {
                if (l.length() == 0 || l.startsWith("#")) continue;
                String[] c = l.split("\t", -1);
                Row w = new Row();
                w.file = c[0];
                w.def = Integer.parseInt(c[1]);
                w.strict = Integer.parseInt(c[2]);
                w.kinds = c[3];
                w.trunkDef = c.length > 4 ? c[4] : "";
                w.trunkStrict = c.length > 5 ? c[5] : "";
                res.add(w);
            }
        } finally {
            r.close();
        }
        return res;
    }

    private File materialise(Row row) throws Exception {
        return LintHarness.copyResource("/policylint/corpus/" + row.file, dir, row.file);
    }

    // Every file of the corpus: default and strict exit status and the kinds, from the checked-in
    // expectations.  With -Dlint.corpus.dump=FILE the current answers are written there instead of
    // asserted (used to build the file; the result is reviewed by hand).
    @Test
    public void corpusExitCodesAndKinds() throws Exception {
        String dump = System.getProperty("lint.corpus.dump");
        PrintWriter w = dump == null ? null : new PrintWriter(dump, "UTF-8");
        StringBuilder bad = new StringBuilder();
        int n = 0;
        try {
            File[] all = new File(PolicyLintCorpusTest.class.getResource("/policylint/corpus").toURI())
                    .listFiles();
            List<String> names = new ArrayList<String>();
            for (File g : all) {
                if (!g.isDirectory()) continue;
                for (File f : g.listFiles()) names.add(g.getName() + "/" + f.getName());
            }
            Collections.sort(names);
            List<Row> rows = dump == null ? rows() : new ArrayList<Row>();
            if (dump != null) {
                for (String s : names) {
                    Row r = new Row();
                    r.file = s;
                    rows.add(r);
                }
            } else {
                List<String> have = new ArrayList<String>();
                for (Row r : rows) have.add(r.file);
                Collections.sort(have);
                assertEquals("the expectations file and the corpus directory list the same files",
                        names, have);
            }
            for (Row row : rows) {
                File f = materialise(row);
                LintHarness.Result d = LintHarness.run(f.getPath());
                LintHarness.Result s = LintHarness.run("--strict", f.getPath());
                n++;
                if (w != null) {
                    w.println(row.file + "\t" + d.exit + "\t" + s.exit + "\t" + d.kinds());
                    continue;
                }
                if (d.exit != row.def || s.exit != row.strict || !d.kinds().equals(row.kinds)) {
                    bad.append(row.file).append(": expected ").append(row.def).append('/')
                            .append(row.strict).append(" [").append(row.kinds).append("] but got ")
                            .append(d.exit).append('/').append(s.exit).append(" [").append(d.kinds())
                            .append("]\n");
                }
            }
        } finally {
            if (w != null) w.close();
        }
        assertTrue("corpus too small: " + n, n >= 200);
        assertEquals(bad.toString(), 0, bad.length());
    }

    // The independence evidence.  For each corpus file the lint passes in the default run, the
    // production policy provider is asked about subjects and permissions chosen here, not by the
    // lint: attacker-controlled codebases and callers, against EVERY Permission class that exists
    // in the JDK image and in jgdms-platform (PermissionUniverse), plus every high-severity probe of
    // the catalogue.  The check must see the files it covers (a provider that loads nothing or
    // answers "no" to everything fails it), and it may find no high-severity grant and no grant
    // of a class the catalogue does not know, except those pinned below.

    /** What the cross-check measured. */
    static final class Cross {
        int cleanRows;
        int covered;
        final List<String> notLoaded = new ArrayList<String>();
        long questions;
        long grants;
        boolean canaryGranted;
        final StringBuilder bad = new StringBuilder();
        final java.util.Set<String> uncatalogued = new java.util.TreeSet<String>();
        PermissionUniverse universe;
        int subjects;
        /** The permissions the provider lists, as (file, subject, permission) triples. */
        long listings;
        /** The same, once per file and permission. */
        int listedDistinct;
        int allowListed;
        /** Listed permissions that imply none of the universe's questions. */
        int impliedNoQuestion;
        /** Listed, neither allowed nor implying a question: nothing judges them. */
        final StringBuilder unjudged = new StringBuilder();
        /** The files for which no stranger subject has anything listed. */
        final java.util.Set<String> noListing = new TreeSet<String>();
        final java.util.Set<String> allowUsed = new TreeSet<String>();
        boolean canaryListed;
        /** Distinct listed permissions with the first file, for the allow-list dump. */
        final Map<String, String> dump = new TreeMap<String, String>();
        List<Allow> allow;
        int listSubjects;
        List<PolicyVerdict.Subject> wide;
        List<PolicyVerdict.Subject> probeSubjects;
        List<PolicyVerdict.Subject> listSubjectList;
        List<Permission> high;
    }

    /** An entry of the allow-list: a class, a name pattern and an actions pattern, and why. */
    static final class Allow {
        final String cls;
        final Pattern name;
        final Pattern actions;
        final String why;

        Allow(String cls, String name, String actions, String why) {
            this.cls = cls;
            this.name = Pattern.compile(expand(name));
            this.actions = Pattern.compile(actions);
            this.why = why;
        }

        private static String expand(String regex) {
            String tmp = System.getProperty("java.io.tmpdir");
            while (tmp.length() > 1 && (tmp.endsWith("/") || tmp.endsWith("\\"))) {
                tmp = tmp.substring(0, tmp.length() - 1);
            }
            return regex.replace("${user.home}", Pattern.quote(System.getProperty("user.home")))
                    .replace("${java.io.tmpdir}", Pattern.quote(tmp));
        }

        boolean allows(String c, String n, String a) {
            return cls.equals(c) && name.matcher(n).matches() && actions.matcher(a).matches();
        }

        String key() {
            return cls + "\t" + name.pattern() + "\t" + actions.pattern();
        }
    }

    private static final String ALLOW = "/policylint/cross-check-allow.tsv";
    private static final String NO_LISTING = "/policylint/cross-check-no-listing.tsv";

    /** Non-comment lines of a resource, split at tabs. */
    static List<String[]> table(String resource) throws Exception {
        List<String[]> res = new ArrayList<String[]>();
        InputStream in = PolicyLintCorpusTest.class.getResourceAsStream(resource);
        assertNotNull("missing " + resource, in);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            for (String l; (l = r.readLine()) != null;) {
                if (l.length() == 0 || l.startsWith("#")) continue;
                res.add(l.split("\t", -1));
            }
        } finally {
            r.close();
        }
        return res;
    }

    static List<Allow> allowList() throws Exception {
        List<Allow> res = new ArrayList<Allow>();
        for (String[] c : table(ALLOW)) {
            assertEquals("allow-list line: class, name, actions, why: " + Arrays.toString(c), 4, c.length);
            assertTrue("an allow-list entry gives its reason: " + Arrays.toString(c), c[3].trim().length() > 10);
            res.add(new Allow(c[0], c[1], c[2], c[3]));
        }
        return res;
    }

    /** The listed permission as (class, name, actions); one that could not be resolved gives its stored text. */
    static String[] parts(Permission p) {
        if (p instanceof UnresolvedPermission) {
            UnresolvedPermission u = (UnresolvedPermission) p;
            return new String[]{u.getUnresolvedType(), String.valueOf(u.getUnresolvedName()),
                u.getUnresolvedActions() == null ? "" : u.getUnresolvedActions()};
        }
        String a = p.getActions();
        return new String[]{p.getClass().getName(), String.valueOf(p.getName()), a == null ? "" : a};
    }

    /**
     * Grants of a class the catalogue has no entry for, in files that pass the default run and
     * {@code --strict}.  Pinned: a new catalogue miss in the corpus must be added here knowingly.
     */
    static final String[] EXPECTED_UNCATALOGUED = {
        "n/n80-uncatalogued-loadable-unscoped.policy net.jini.export.ExportPermission"};

    private static PermissionUniverse universe;

    static synchronized PermissionUniverse universe() throws Exception {
        if (universe == null) universe = PermissionUniverse.build();
        return universe;
    }

    private Cross crossCheck(PolicyVerdict provider) throws Exception {
        Cross c = crossCheckSetUp(provider);
        for (Row row : rows()) {
            if (row.def != 0) continue;
            c.cleanRows++;
            checkFile(provider, c, row, materialise(row));
        }
        return c;
    }

    /** The subjects and questions, the control files and the canary checks; no corpus file yet. */
    private Cross crossCheckSetUp(PolicyVerdict provider) throws Exception {
        Cross c = new Cross();
        c.universe = universe();
        List<PolicyVerdict.Subject> wide = new ArrayList<PolicyVerdict.Subject>();
        String cdn = "https://cdn.attacker-evil.example/payload/x.jar";
        Principal[] none = new Principal[0];
        wide.add(new PolicyVerdict.Subject(cdn, none));
        wide.add(new PolicyVerdict.Subject(cdn, new Principal[]{new X500Principal("CN=mallory,O=Evil Corp")}));
        wide.add(new PolicyVerdict.Subject(cdn, new Principal[]{new PolicyVerdict.StrangerPrincipal()}));
        wide.add(new PolicyVerdict.Subject("http://198.51.100.7:9999/x.jar", none));
        wide.add(new PolicyVerdict.Subject("file:/tmp/x.jar", none));
        wide.add(new PolicyVerdict.Subject("jar:https://evil.net/x.jar!/", none));
        // a network share is a stranger, however many slashes the URL is written with
        wide.add(new PolicyVerdict.Subject("file://evil-host/share/x.jar", none));
        wide.add(new PolicyVerdict.Subject("file:////evil-host/share/x.jar", none));
        wide.add(new PolicyVerdict.Subject("file://///evil-host/share/x.jar", none));

        String home = new File(System.getProperty("user.home")).toURI().toString();
        String tmp = new File(System.getProperty("java.io.tmpdir")).toURI().toString();
        String cwd = new File(System.getProperty("user.dir")).toURI().toString();
        String[] urls = {
            "https://evil.net/x.jar", "https://evil.org/x.jar", "http://attacker.example.org/x.jar",
            "http://evil.example.com:8443/x.jar", "http://evil.example.com:80/x.jar",
            "https://sub.evil.io:444/a/b/x.jar", "ftp://files.evil.example.net/x.jar",
            "http://198.51.100.7:9999/x.jar", "jar:https://evil.net/x.jar!/",
            "file:/tmp/x.jar", "file:/x.jar", "file:/C:/x.jar", home + "Downloads/x.jar",
            tmp + "x.jar", cwd + "x.jar", "file:x.jar"};
        Principal[][] who = {none, {new X500Principal("CN=mallory,O=evil")},
            {new PolicyVerdict.StrangerPrincipal()}};
        List<PolicyVerdict.Subject> probeSubjects = new ArrayList<PolicyVerdict.Subject>();
        for (String u : urls) for (Principal[] ws : who) probeSubjects.add(new PolicyVerdict.Subject(u, ws));
        List<Permission> high = new ArrayList<Permission>();
        for (PermissionSets.Probe p : PermissionSets.probes()) {
            if (p.severity >= PermissionSets.HIGH) high.add(p.permission);
        }
        c.subjects = wide.size();
        c.allow = allowList();
        List<PolicyVerdict.Subject> listSubjects = new ArrayList<PolicyVerdict.Subject>(wide);
        listSubjects.addAll(probeSubjects);
        c.listSubjects = listSubjects.size();
        c.wide = wide;
        c.probeSubjects = probeSubjects;
        c.listSubjectList = listSubjects;
        c.high = high;

        // a control file the provider must grant from: if it does not, it cannot see anything
        File control = new File(dir, "control.policy");
        Files.write(control.toPath(), ("grant {\n  permission java.io.FilePermission \"<<ALL FILES>>\","
                + " \"read\";\n};\n").getBytes("UTF-8"));
        for (List<Permission> g : provider.grantedToEach(control, Collections.<String, String>emptyMap(),
                wide, c.universe.permissions)) {
            for (Permission p : g) {
                if (p instanceof java.io.FilePermission && "<<ALL FILES>>".equals(p.getName())
                        && "read".equals(p.getActions())) {
                    c.canaryGranted = true;
                }
            }
        }

        for (List<Permission> g : provider.listedToEach(control, Collections.<String, String>emptyMap(),
                listSubjects)) {
            for (Permission p : g) {
                String[] t = parts(p);
                if ("java.io.FilePermission".equals(t[0]) && "<<ALL FILES>>".equals(t[1])
                        && "read".equals(t[2])) {
                    c.canaryListed = true;
                }
            }
        }

        return c;
    }

    /** Asks the provider the questions and lists what it grants, for one file that passes the lint. */
    private void checkFile(PolicyVerdict provider, Cross c, Row row, File f) throws Exception {
        List<List<Permission>> got;
        List<List<Permission>> gotHigh;
        List<List<Permission>> listed;
        try {
            got = provider.grantedToEach(f, Collections.<String, String>emptyMap(), c.wide,
                    c.universe.permissions);
            gotHigh = provider.grantedToEach(f, Collections.<String, String>emptyMap(), c.probeSubjects, c.high);
            listed = provider.listedToEach(f, Collections.<String, String>emptyMap(), c.listSubjectList);
        } catch (PolicyVerdict.LoadFailure e) {
            c.notLoaded.add(row.file + ": " + e.getMessage());
            return;
        }
        c.covered++;
        judgeListed(c, row, c.listSubjectList, listed);
        c.questions += (long) c.wide.size() * c.universe.permissions.size()
                + (long) c.probeSubjects.size() * c.high.size();
        List<List<Permission>> both = new ArrayList<List<Permission>>(got);
        both.addAll(gotHigh);
        for (List<Permission> granted : both) {
            for (Permission g : granted) {
                c.grants++;
                String cn = g.getClass().getName();
                PermissionSets.Danger d = PermissionSets.danger(cn, g.getName(), g.getActions());
                if (d != null && d.severity >= PermissionSets.HIGH) {
                    c.bad.append(row.file).append(" grants ").append(g).append(" to a stranger: ")
                            .append(d.reason).append('\n');
                }
                if (!PermissionSets.catalogued(cn) && row.strict == 0) {
                    c.uncatalogued.add(row.file + " " + cn);
                }
            }
        }
    }

    /**
     * Judges everything the provider lists for the file: each permission is on the allow-list, or
     * it implies one of the universe's questions (so the question loop above judged it), or it is
     * reported by file, subject and permission.  A listed permission the catalogue rates HIGH is
     * a finding whatever else is true of it.
     */
    private static void judgeListed(Cross c, Row row, List<PolicyVerdict.Subject> subjects,
            List<List<Permission>> listed) {
        Map<String, Permission> distinct = new LinkedHashMap<String, Permission>();
        Map<String, String> firstSubject = new LinkedHashMap<String, String>();
        boolean any = false;
        for (int i = 0; i < listed.size(); i++) {
            for (Permission p : listed.get(i)) {
                any = true;
                c.listings++;
                String[] t = parts(p);
                String key = t[0] + "\t" + t[1] + "\t" + t[2];
                if (!distinct.containsKey(key)) {
                    distinct.put(key, p);
                    firstSubject.put(key, subjects.get(i).url);
                }
            }
        }
        if (!any) c.noListing.add(row.file);
        for (Map.Entry<String, Permission> e : distinct.entrySet()) {
            Permission p = e.getValue();
            String[] t = parts(p);
            c.listedDistinct++;
            PermissionSets.Danger d = PermissionSets.danger(t[0], t[1], t[2]);
            if (d != null && d.severity >= PermissionSets.HIGH) {
                c.bad.append(row.file).append(" lists ").append(Arrays.toString(t)).append(" for ")
                        .append(firstSubject.get(e.getKey())).append(": ").append(d.reason).append('\n');
            }
            boolean question = false;
            for (Permission q : c.universe.permissions) {
                try {
                    if (p.implies(q)) {
                        question = true;
                        break;
                    }
                } catch (RuntimeException ex) {
                    question = true; // cannot tell: the question loop counts it as granted
                    break;
                }
            }
            if (!question) c.impliedNoQuestion++;
            c.dump.put(e.getKey(), row.file + (question ? "	Q" : "	-"));
            Allow allowed = null;
            for (Allow a : c.allow) {
                if (a.allows(t[0], t[1], t[2])) {
                    allowed = a;
                    break;
                }
            }
            if (allowed != null) {
                c.allowListed++;
                c.allowUsed.add(allowed.key());
            } else if (!question) {
                c.unjudged.append(row.file).append(" subject ").append(firstSubject.get(e.getKey()))
                        .append(" is granted ").append(Arrays.toString(t))
                        .append(": not on the allow-list and no universe question sees it\n");
            }
        }
    }

    private static void assertCrossCheck(Cross c) throws Exception {
        PermissionUniverse u = c.universe;
        assertTrue("permission classes found: " + u.classes.size(), u.classes.size() >= 40);
        assertTrue("permission classes constructed: " + u.constructible.size(), u.constructible.size() >= 30);
        assertTrue("questions per file: " + u.permissions.size(), u.permissions.size() >= 1000);
        for (String need : new String[]{"java.sql.SQLPermission", "java.nio.file.LinkPermission",
            "net.jini.export.ExportPermission", "java.io.FilePermission"}) {
            assertTrue(need + " is in the universe", u.has(need));
        }
        assertTrue("the provider granted nothing from a control file that grants all file reads: it"
                + " cannot see what it is asked", c.canaryGranted);
        assertTrue("clean files: " + c.cleanRows, c.cleanRows >= 40);
        assertEquals("the provider covered fewer files than the expectations call clean: " + c.notLoaded,
                c.cleanRows, c.covered);
        assertTrue("no grant was found in any clean file: the cross-check saw nothing", c.grants > 0);
        assertTrue("the provider listed nothing for a control file that grants all file reads: it"
                + " cannot list what it grants", c.canaryListed);
        assertEquals(c.unjudged.toString(), 0, c.unjudged.length());
        // per file: the files for which no stranger subject gets anything listed are exactly the
        // pinned ones (a pinned codeBase, a signer, a digest or a named principal)
        java.util.Set<String> pinned = new TreeSet<String>();
        for (String[] l : table(NO_LISTING)) {
            assertEquals("no-listing line: file, why: " + Arrays.toString(l), 2, l.length);
            assertTrue(l[1].trim().length() > 10);
            pinned.add(l[0]);
        }
        assertEquals("files for which the provider lists nothing to every stranger subject, against"
                + " the pinned list", pinned, c.noListing);
        java.util.Set<String> used = new TreeSet<String>();
        for (Allow a : c.allow) used.add(a.key());
        used.removeAll(c.allowUsed);
        assertTrue("allow-list entries no clean file needs: " + used, used.isEmpty());
        assertEquals(c.bad.toString(), 0, c.bad.length());
        assertEquals("grants of classes the catalogue does not know, in files that pass --strict",
                new java.util.TreeSet<String>(Arrays.asList(EXPECTED_UNCATALOGUED)), c.uncatalogued);
    }

    @Test
    public void cleanFilesGrantNoStrangerAHighPermission() throws Exception {
        PolicyVerdict provider = PolicyVerdict.open("auto");
        Cross c = crossCheck(provider);
        System.out.println("provider cross-check: files covered=" + c.covered + " of clean=" + c.cleanRows
                + " permission-classes=" + c.universe.classes.size() + " constructed="
                + c.universe.constructible.size() + " questions-per-file="
                + (c.subjects * c.universe.permissions.size()) + " (" + c.subjects + " subjects x "
                + c.universe.permissions.size() + " permissions) questions-total=" + c.questions
                + " grants=" + c.grants + " provider=" + provider.providerClass);
        System.out.println("provider listing: permissions listed=" + c.listings + " (file x subject x permission)"
                + " distinct-per-file=" + c.listedDistinct + " allow-listed=" + c.allowListed
                + " implying-no-universe-question=" + c.impliedNoQuestion + " files-with-nothing-listed="
                + c.noListing.size() + " subjects-listed=" + c.listSubjects);
        String dumpTo = System.getenv("LINT_CROSS_DUMP");
        if (dumpTo != null) {
            PrintWriter w = new PrintWriter(dumpTo, "UTF-8");
            try {
                for (Map.Entry<String, String> e : c.dump.entrySet()) w.println(e.getKey() + "	" + e.getValue());
                w.println("#NOLISTING");
                for (String f : c.noListing) w.println(f);
            } finally {
                w.close();
            }
        }
        assertCrossCheck(c);
    }

    // The fixed questions miss a specific-name grant of a class the catalogue does not know: no
    // question names it and the catalogue has no entry to rate.  The listing of what the provider
    // grants must still report it, by file, subject and permission.
    @Test
    public void aSpecificNameGrantOfAnUncataloguedClassIsReported() throws Exception {
        assertFalse("java.awt.AWTPermission is uncatalogued", PermissionSets.catalogued("java.awt.AWTPermission"));
        PolicyVerdict provider = PolicyVerdict.open("auto");
        Cross c = crossCheckSetUp(provider);
        File f = new File(dir, "awt.policy");
        Files.write(f.toPath(), ("grant {\n  permission java.awt.AWTPermission \"createRobot\";\n};\n")
                .getBytes("UTF-8"));
        Row row = new Row();
        row.file = "awt.policy";
        checkFile(provider, c, row, f);
        assertEquals("the provider covered the file", 1, c.covered);
        assertTrue("reported: " + c.unjudged, c.unjudged.toString().contains("awt.policy subject")
                && c.unjudged.toString().contains("createRobot"));
        assertEquals("the fixed questions alone see nothing: " + c.bad, 0, c.bad.length());
    }

    // M8: a provider that answers implies() but lists nothing: the questions look fine, the listing
    // is empty, and the cross-check must not pass on that.
    @Test
    public void crossCheckFailsAgainstAProviderThatListsNothing() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "org.apache.river.tool.stub.ImpliesButListsNothingPolicy";
        try {
            PolicyVerdict stub = PolicyVerdict.open("auto");
            assertEquals("org.apache.river.tool.stub.ImpliesButListsNothingPolicy", stub.providerClass);
            try {
                assertCrossCheck(crossCheck(stub));
            } catch (AssertionError expected) {
                assertTrue("failed for another reason: " + expected.getMessage(),
                        String.valueOf(expected.getMessage()).contains("listed nothing"));
                return;
            }
            fail("the cross-check passed against a provider that lists nothing");
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }

    // M7: a provider that loads every file and grants nothing is the failure the cross-check must
    // notice.  The check is run against a stub of that kind and must fail.
    @Test
    public void crossCheckFailsAgainstAProviderThatGrantsNothing() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "org.apache.river.tool.stub.NothingGrantedPolicy";
        try {
            PolicyVerdict stub = PolicyVerdict.open("auto");
            assertEquals("org.apache.river.tool.stub.NothingGrantedPolicy", stub.providerClass);
            try {
                assertCrossCheck(crossCheck(stub));
            } catch (AssertionError expected) {
                return;
            }
            fail("the cross-check passed against a provider that grants nothing");
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }

    // A provider that cannot load the corpus files is not "nothing granted" either.
    @Test
    public void crossCheckFailsAgainstAProviderThatCannotLoad() throws Exception {
        String saved = PolicyVerdict.dirtyChaiClass;
        PolicyVerdict.dirtyChaiClass = "org.apache.river.tool.stub.RefusingPolicy";
        try {
            PolicyVerdict stub = PolicyVerdict.open("auto");
            try {
                assertCrossCheck(crossCheck(stub));
            } catch (AssertionError | PolicyVerdict.LoadFailure expected) {
                return;
            }
            fail("the cross-check passed against a provider that cannot load a file");
        } finally {
            PolicyVerdict.dirtyChaiClass = saved;
        }
    }
}
