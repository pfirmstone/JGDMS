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

    // Operator ruling 2026-11-02: the corpus expectations are the answers of DirtyChai's own policy
    // provider and parser.  A run on another JDK (or a stale build) must fail loudly, naming the cause,
    // and not with a handful of unrelated mismatches.
    static final String DIRTYCHAI_PROVIDER = "au.zeus.jdk.authorization.policy.ConcurrentPolicyFile";

    /** A wildcard principal class: the DirtyChai parser (issue 236) and the platform parser reject it. */
    static final String WILDCARD_PRINCIPAL_PROBE =
            "grant principal * * {\n  permission java.lang.RuntimePermission \"x\";\n};\n";

    /** The provider class a lint run answered with, from its {@code PROVIDER} line; null if it has none. */
    static String providerLine(String lintOutput) {
        for (String l : lintOutput.split("\n")) {
            if (l.startsWith("PROVIDER ") || l.startsWith("# PROVIDER ")) return l.trim();
        }
        return null;
    }

    /** Fails unless {@code providerClass} (with the provider's note) is DirtyChai's own provider. */
    static void requireDirtyChaiProvider(String providerClass, String note) {
        if (DIRTYCHAI_PROVIDER.equals(providerClass)) return;
        fail("the policy provider that answered is " + providerClass + ", not " + DIRTYCHAI_PROVIDER
                + ": run on the DirtyChai JDK and pass --add-exports"
                + " java.base/au.zeus.jdk.authorization.policy=ALL-UNNAMED (the pom profile"
                + " dirtychai-policy-export arranges it).  Provider note: " + note);
    }

    /** As above, from the {@code PROVIDER} line a lint run printed. */
    static void requireDirtyChaiProviderFrom(String lintOutput) {
        String line = providerLine(lintOutput);
        if (line == null) fail("the lint printed no PROVIDER line, so the provider that answered is unknown");
        String[] w = line.replaceFirst("^# ", "").split("\\s+", 3);
        requireDirtyChaiProvider(w.length > 1 ? w[1] : "", w.length > 2 ? w[2] : "");
    }

    /** Fails with the cause if the lint run on {@link #WILDCARD_PRINCIPAL_PROBE} found no P1. */
    static void requireWildcardPrincipalRejected(LintHarness.Result probe) {
        if (probe.count("P1") == 0) {
            fail("the loaded DirtyChai/platform parser accepts a wildcard principal class: wrong or stale"
                    + " JDK (the expectations of r6/a04, r6/a33, r8/q03 and r8/z02 need the P1 of the"
                    + " parser of DirtyChai issue 236).  Lint said: " + probe.out);
        }
    }

    /** Runs the probe and applies both requirements. */
    void requireRightProviderAndParser() throws Exception {
        File p = new File(dir, "wildcard-principal-probe.policy");
        Files.write(p.toPath(), WILDCARD_PRINCIPAL_PROBE.getBytes("UTF-8"));
        LintHarness.Result r = LintHarness.run(p.getPath());
        requireDirtyChaiProviderFrom(r.out);
        requireWildcardPrincipalRejected(r);
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
        requireRightProviderAndParser();
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
        /** Per allow-list entry (by key): the corpus files whose listing it matched. */
        final Map<String, java.util.Set<String>> entryFiles = new TreeMap<String, java.util.Set<String>>();
        /** Listed permissions that an entry allows, in a file that entry does not name. */
        final StringBuilder outsideList = new StringBuilder();
        boolean canaryListed;
        /**
         * The D5 findings of the lint's own run, per corpus file; each names the permissions it is about
         * ({@code PolicyLint.Finding.permissions}).
         */
        final Map<String, List<PolicyLint.Finding>> d5 = new TreeMap<String, List<PolicyLint.Finding>>();
        /** Listed permissions accepted by the rule (catalogue MEDIUM and a D5 finding naming it), by file. */
        final java.util.Set<String> autoAccepted = new TreeSet<String>();
        /** Listed MEDIUM permissions that no D5 finding of the lint names: provider and lint disagree. */
        final StringBuilder ratingMismatch = new StringBuilder();
        /** Distinct listed permissions with the first file, for the allow-list dump. */
        final Map<String, String> dump = new TreeMap<String, String>();
        List<Allow> allow;
        int listSubjects;
        List<PolicyVerdict.Subject> wide;
        List<PolicyVerdict.Subject> probeSubjects;
        List<PolicyVerdict.Subject> listSubjectList;
        List<Permission> high;
    }

    /**
     * An entry of the allow-list: a class, a name pattern and an actions pattern; the corpus files
     * that may grant it (exact paths relative to the corpus, never a glob: operator ruling
     * 2026-11-02); and why that grant to a stranger is accepted for those files.
     */
    static final class Allow {
        final String cls;
        final Pattern name;
        final Pattern actions;
        final List<String> files;
        final String why;

        Allow(String cls, String name, String actions, String files, String why) {
            this.cls = cls;
            this.name = Pattern.compile(expand(name));
            this.actions = Pattern.compile(actions);
            this.files = new ArrayList<String>();
            for (String f : files.split(";")) {
                if (f.trim().length() > 0) this.files.add(f.trim());
            }
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

        /** Whether the file is one this entry names (an exact path). */
        boolean names(String file) {
            return files.contains(file);
        }

        String key() {
            return cls + "\t" + name.pattern() + "\t" + actions.pattern();
        }
    }

    /** Reasons shorter than this are boilerplate. */
    static final int MIN_REASON = 25;

    /**
     * What is wrong with the entries' own text: a reason that is empty, shorter than
     * {@link #MIN_REASON}, equal to another entry's, or that does not cite EVERY fixture the entry
     * names by its stem as a whole word (a stem inside a longer word, or one stem repeated for
     * several files, does not count; operator ruling 2026-11-03); no files; a file reference that is not the exact path of a fixture (a glob, or, when
     * {@code corpus} is given, a path that is no corpus file).
     */
    static List<String> allowListProblems(List<Allow> entries) {
        return allowListProblems(entries, null);
    }

    /** As {@link #allowListProblems(List)}; {@code corpus}, if not null, is the set of fixture paths. */
    static List<String> allowListProblems(List<Allow> entries, java.util.Set<String> corpus) {
        List<String> res = new ArrayList<String>();
        Map<String, Allow> seen = new LinkedHashMap<String, Allow>();
        for (Allow a : entries) {
            String why = a.why.trim();
            String id = a.cls + " " + a.name.pattern() + " " + a.actions.pattern();
            if (why.length() == 0) {
                res.add(id + ": empty reason");
            } else if (why.length() < MIN_REASON) {
                res.add(id + ": boilerplate reason (shorter than " + MIN_REASON + "): " + why);
            }
            Allow other = seen.get(why);
            if (other != null) {
                res.add(id + ": boilerplate reason, identical to the one of " + other.cls + " "
                        + other.name.pattern() + ": " + why);
            } else {
                seen.put(why, a);
            }
            if (a.files.isEmpty()) res.add(id + ": names no file");
            java.util.Set<String> words = new java.util.HashSet<String>();
            for (String w : why.split("[\\s(),;:\"']+")) {
                words.add(w.replaceFirst("[.]+$", ""));
            }
            for (String f : a.files) {
                if (f.indexOf('*') >= 0 || f.indexOf('?') >= 0 || f.indexOf('[') >= 0) {
                    res.add(id + ": file reference " + f + " is a glob; only exact paths are allowed");
                    continue;
                }
                if (corpus != null && !corpus.contains(f)) {
                    res.add(id + ": file reference " + f + " is no corpus fixture");
                    continue;
                }
                String stem = f.substring(f.lastIndexOf('/') + 1).replaceFirst("[.]policy$", "");
                if (!words.contains(stem)) {
                    res.add(id + ": the reason does not cite the fixture " + f + " by its stem " + stem
                            + " as a whole word");
                }
            }
        }
        return res;
    }

    /** Every file an entry names must be one whose listing the entry matched; else the reference is stale. */
    static List<String> staleReferences(List<Allow> entries, Map<String, java.util.Set<String>> entryFiles) {
        List<String> res = new ArrayList<String>();
        for (Allow a : entries) {
            java.util.Set<String> got = entryFiles.get(a.key());
            if (got == null) got = Collections.<String>emptySet();
            for (int i = 0; i < a.files.size(); i++) {
                if (!got.contains(a.files.get(i))) {
                    res.add(a.cls + " " + a.name.pattern() + " names " + a.files.get(i)
                            + ", which does not list a permission the entry matches (stale file reference)");
                }
            }
        }
        return res;
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
            assertEquals("allow-list line: class, name, actions, files, why: " + Arrays.toString(c), 5, c.length);
            res.add(new Allow(c[0], c[1], c[2], c[3], c[4]));
        }
        List<String> problems = allowListProblems(res, corpusNames());
        assertTrue("allow-list entries: " + problems, problems.isEmpty());
        return res;
    }

    /** The fixtures of the corpus, as paths relative to it ({@code r8/f05-allfiles-readlink.policy}). */
    static java.util.Set<String> corpusNames() throws Exception {
        java.util.Set<String> names = new TreeSet<String>();
        File[] all = new File(PolicyLintCorpusTest.class.getResource("/policylint/corpus").toURI()).listFiles();
        for (File g : all) {
            if (!g.isDirectory()) continue;
            for (File f : g.listFiles()) names.add(g.getName() + "/" + f.getName());
        }
        return names;
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
        for (PolicyLint.Finding fi : PolicyLint.lint(f, row.file, provider,
                Collections.<String, String>emptyMap()).findings) {
            if ("D5".equals(fi.kind)) {
                List<PolicyLint.Finding> l = c.d5.get(row.file);
                if (l == null) c.d5.put(row.file, l = new ArrayList<PolicyLint.Finding>());
                l.add(fi);
            }
        }
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
     * Judges everything the provider lists for the file.  First, a listed permission the catalogue
     * rates HIGH is a finding whatever else is true of it: no row and no rule below accepts it.
     * Then each permission is on the allow-list (an explicit row naming this file, with its own
     * reason), or it is rated MEDIUM by the catalogue and a D5 finding of the lint for the file
     * names this very permission (the provider and the lint agree: no row needed), or it implies
     * one of the universe's questions (so the question loop above judged it), or it is reported by
     * file, subject and permission.  A MEDIUM permission that no D5 finding of the file names, though
     * the file has a D5 for another permission, is a disagreement and a finding (operator ruling
     * 2026-11-03: the rule is per permission, not per file).
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
            int matching = 0;
            for (Allow a : c.allow) {
                if (!a.allows(t[0], t[1], t[2])) continue;
                matching++;
                java.util.Set<String> fs = c.entryFiles.get(a.key());
                if (fs == null) c.entryFiles.put(a.key(), fs = new TreeSet<String>());
                fs.add(row.file);
                if (allowed == null && a.names(row.file)) allowed = a;
            }
            boolean medium = d != null && d.severity == PermissionSets.MEDIUM;
            boolean named = medium && lintNames(c, row.file, p);
            if (medium && !named) {
                c.ratingMismatch.append(row.file).append(" lists ").append(Arrays.toString(t))
                        .append(", which the catalogue rates MEDIUM, but no D5 finding of the lint for the file"
                        + " names it\n");
            }
            if (allowed != null) {
                c.allowListed++;
                c.allowUsed.add(allowed.key());
            } else if (matching > 0) {
                // an exemption written for other files must not cover this one
                c.outsideList.append(row.file).append(" is granted ").append(Arrays.toString(t))
                        .append(", which only entries for other files allow\n");
            } else if (named) {
                c.autoAccepted.add(row.file + "\t" + t[0] + " " + t[1] + " " + t[2]);
            } else if (!question) {
                c.unjudged.append(row.file).append(" subject ").append(firstSubject.get(e.getKey()))
                        .append(" is granted ").append(Arrays.toString(t))
                        .append(": not on the allow-list and no universe question sees it\n");
            }
        }
    }

    /** Whether a D5 finding of the lint for the file names the permission. */
    private static boolean lintNames(Cross c, String file, Permission p) {
        List<PolicyLint.Finding> l = c.d5.get(file);
        if (l == null) return false;
        for (PolicyLint.Finding f : l) if (f.names(p)) return true;
        return false;
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
        assertEquals(c.outsideList.toString(), 0, c.outsideList.length());
        assertEquals(c.ratingMismatch.toString(), 0, c.ratingMismatch.length());
        List<String> stale = staleReferences(c.allow, c.entryFiles);
        assertTrue("allow-list file references: " + stale, stale.isEmpty());
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
        requireRightProviderAndParser();
        PolicyVerdict provider = PolicyVerdict.open("auto");
        requireDirtyChaiProvider(provider.providerClass, provider.note);
        Cross c = crossCheck(provider);
        System.out.println("provider cross-check: files covered=" + c.covered + " of clean=" + c.cleanRows
                + " permission-classes=" + c.universe.classes.size() + " constructed="
                + c.universe.constructible.size() + " questions-per-file="
                + (c.subjects * c.universe.permissions.size()) + " (" + c.subjects + " subjects x "
                + c.universe.permissions.size() + " permissions) questions-total=" + c.questions
                + " grants=" + c.grants + " provider=" + provider.providerClass);
        System.out.println("provider listing: permissions listed=" + c.listings + " (file x subject x permission)"
                + " distinct-per-file=" + c.listedDistinct + " allow-listed=" + c.allowListed
                + " auto-accepted(MEDIUM+D5)=" + c.autoAccepted.size() + " explicit-rows=" + c.allow.size()
                + " implying-no-universe-question=" + c.impliedNoQuestion + " files-with-nothing-listed="
                + c.noListing.size() + " subjects-listed=" + c.listSubjects);
        String dumpTo = System.getenv("LINT_CROSS_DUMP");
        if (dumpTo != null) {
            PrintWriter w = new PrintWriter(dumpTo, "UTF-8");
            try {
                for (Map.Entry<String, String> e : c.dump.entrySet()) w.println(e.getKey() + "	" + e.getValue());
                for (Map.Entry<String, java.util.Set<String>> e : c.entryFiles.entrySet()) {
                    w.println("#ENTRY	" + e.getKey() + "	" + e.getValue());
                }
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

    // Opus review 2026-11-02, finding 3.  The allow-list is checked by what it says (own reason, named
    // files) as well as by what it matches.
    private static PolicyLintCorpusTest.Allow entry(String name, String files, String why) {
        return new Allow("java.lang.RuntimePermission", name, "", files, why);
    }

    @Test
    public void everyAllowEntryNeedsItsOwnSpecificReason() throws Exception {
        String why1 = "exitVM lets a stranger stop the VM (r16-rt-exitVM), a denial of service only.";
        String why2 = "setIO lets a stranger redirect the console of the VM (r14-rt-setIO), accepted as D5.";
        assertEquals(Collections.<String>emptyList(), allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", why1),
                entry("setIO", "r8/r14-rt-setIO.policy", why2))));
        assertFalse("identical reasons", allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", why1),
                entry("exitVM\\.0", "r8/r16-rt-exitVM.policy", why1))).isEmpty());
        assertFalse("empty reason", allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", ""))).isEmpty());
        assertFalse("blank reason", allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", "   "))).isEmpty());
        assertFalse("short reason", allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", "r16-rt-exitVM D5 rated"))).isEmpty());
        assertFalse("reason that cites none of its fixtures", allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", "exitVM lets a stranger stop the VM, a denial of service."))).isEmpty());
        assertFalse("no files", allowListProblems(Arrays.asList(entry("exitVM", "", why1))).isEmpty());
        assertEquals(Collections.<String>emptyList(), allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy", why1),
                entry("setIO", "r8/r14-rt-setIO.policy", why2)), corpusNames()));
    }

    // Opus review finding 3; operator ruling 2026-11-03: every file of a row is cited by the reason, by
    // its stem as a whole word.  One cited stem among several files, or one stem repeated, is not enough.
    @Test
    public void aReasonMustCiteEveryFixtureOfItsRowByItsStem() throws Exception {
        String two = "r8/r16-rt-exitVM.policy;r8/r17-rt-exitVM_0.policy";
        String both = "exitVM and exitVM.0 let a stranger stop the VM (r16-rt-exitVM, r17-rt-exitVM_0), a denial of service.";
        assertEquals(Collections.<String>emptyList(),
                allowListProblems(Arrays.asList(entry("exitVM", two, both)), corpusNames()));
        List<String> one = allowListProblems(Arrays.asList(entry("exitVM", two,
                "exitVM lets a stranger stop the VM (r16-rt-exitVM) and so does its sibling file, a denial of service.")),
                corpusNames());
        assertEquals("one cited stem among two files: " + one, 1, one.size());
        assertTrue(one.get(0), one.get(0).contains("r17-rt-exitVM_0"));
        List<String> repeated = allowListProblems(Arrays.asList(entry("exitVM", two,
                "exitVM lets a stranger stop the VM (r16-rt-exitVM), again r16-rt-exitVM, a denial of service.")),
                corpusNames());
        assertEquals("the same stem repeated: " + repeated, 1, repeated.size());
        // a stem inside a longer word is not a citation of the fixture
        assertFalse("stem inside a word", allowListProblems(Arrays.asList(entry("exitVM", "r8/r16-rt-exitVM.policy",
                "exitVM lets a stranger stop the VM (xr16-rt-exitVM2), a denial of service.")), corpusNames()).isEmpty());
        assertFalse("stem as a prefix of a word", allowListProblems(Arrays.asList(entry("exitVM",
                "r8/r16-rt-exitVM.policy", "exitVM lets a stranger stop the VM (r16-rt-exitVM_old), a denial.")),
                corpusNames()).isEmpty());
        // a row of one file needs its stem too; a row of one file with the stem cited passes
        assertFalse(allowListProblems(Arrays.asList(entry("exitVM", "r8/r16-rt-exitVM.policy",
                "exitVM lets a stranger stop the VM, a denial of service only.")), corpusNames()).isEmpty());
        assertEquals(Collections.<String>emptyList(), allowListProblems(Arrays.asList(entry("exitVM",
                "r8/r16-rt-exitVM.policy", "exitVM lets a stranger stop the VM, see r16-rt-exitVM.")), corpusNames()));
    }

    // Opus review 2026-11-02, finding 2, and the operator's ruling: a file reference is the exact path
    // of an existing fixture.  A glob, even one with a slash in it, covers files the entry does not name.
    @Test
    public void globFileReferencesAreRejected() throws Exception {
        String why = "exitVM lets a stranger stop the VM (r16-rt-exitVM), a denial of service only.";
        for (String g : new String[]{"*", "**", "*/*", "r8/*", "n/*.policy", "r8/r16-rt-exitVM.polic?",
            "r8/r1[6]-rt-exitVM.policy", "r8/r16-rt-exitVM.policy;*/*"}) {
            assertFalse("glob " + g + " without the corpus", allowListProblems(Arrays.asList(
                    entry("exitVM", g, why))).isEmpty());
            assertFalse("glob " + g + " with the corpus", allowListProblems(Arrays.asList(
                    entry("exitVM", g, why)), corpusNames()).isEmpty());
        }
        List<String> p = allowListProblems(Arrays.asList(entry("exitVM", "r8/*", why)), corpusNames());
        assertTrue(p.toString(), p.toString().contains("glob"));
    }

    @Test
    public void aFileReferenceMustBeAnExistingFixture() throws Exception {
        String why = "exitVM lets a stranger stop the VM (r16-rt-exitVM), a denial of service only.";
        List<String> p = allowListProblems(Arrays.asList(
                entry("exitVM", "r8/r16-rt-exitVM.policy;r8/no-such-fixture.policy", why)), corpusNames());
        assertEquals(p.toString(), 1, p.size());
        assertTrue(p.toString(), p.get(0).contains("no-such-fixture"));
    }

    // Operator ruling 2026-11-02: the run must be against DirtyChai's provider and parser, and say so.
    @Test
    public void aRunOnTheWrongProviderFailsLoudly() {
        requireDirtyChaiProviderFrom("SUMMARY x\nPROVIDER " + DIRTYCHAI_PROVIDER + " (production provider)\n");
        requireDirtyChaiProviderFrom("# PROVIDER " + DIRTYCHAI_PROVIDER + " (production provider)\n");
        for (String out : new String[]{
            "PROVIDER org.apache.river.api.security.ConcurrentPolicyFile (fell back from x: IllegalAccessError)"
                + " [not the DirtyChai provider]\n",
            "PROVIDER sun.security.provider.PolicyFile (n)\n", "no provider line here\n", ""}) {
            try {
                requireDirtyChaiProviderFrom(out);
            } catch (AssertionError expected) {
                String m = String.valueOf(expected.getMessage());
                assertTrue(m, m.contains("add-exports") || m.contains("no PROVIDER line"));
                continue;
            }
            fail("accepted a run of: " + out);
        }
    }

    @Test
    public void aParserThatAcceptsAWildcardPrincipalFailsWithItsCause() {
        LintHarness.Result accepts = new LintHarness.Result();
        accepts.out = "p.policy:1 S3 principal * the policy grants AllPermission to any principal\n";
        try {
            requireWildcardPrincipalRejected(accepts);
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "accepts a wildcard principal class: wrong or stale JDK"));
            LintHarness.Result rejects = new LintHarness.Result();
            rejects.out = "p.policy:0 P1 - the production policy parser rejects this file: principal *\n";
            requireWildcardPrincipalRejected(rejects);
            return;
        }
        fail("a parser that accepts principal * passed");
    }

    // The real run, not a stand-in: the lint on the probe answers with the DirtyChai provider and P1.
    @Test
    public void theLoadedProviderAndParserAreDirtyChais() throws Exception {
        requireRightProviderAndParser();
    }

    // A listed permission the catalogue rates HIGH is a finding although a row names the file and the
    // file is D5: nothing accepts it.
    @Test
    public void aHighRatedPermissionIsNeverAcceptedByARowOrTheRule() throws Exception {
        Cross c = new Cross();
        c.universe = universe();
        c.allow = Arrays.asList(entry("setSecurityManager", "r8/r14-rt-setIO.policy",
                "setSecurityManager is named by a row for a fixture, r14-rt-setIO, and must still fail."));
        d5(c, "r8/r14-rt-setIO.policy", "java.lang.RuntimePermission", "setIO");
        List<PolicyVerdict.Subject> subjects = Arrays.asList(
                new PolicyVerdict.Subject("https://evil.net/x.jar", new Principal[0]));
        List<List<Permission>> listed = new ArrayList<List<Permission>>();
        listed.add(Arrays.<Permission>asList(new RuntimePermission("setSecurityManager")));
        Row row = new Row();
        row.file = "r8/r14-rt-setIO.policy";
        judgeListed(c, row, subjects, listed);
        assertTrue("HIGH reported: " + c.bad, c.bad.toString().contains("setSecurityManager"));
        // and without a row, in a D5 file, the rule does not take it either
        Cross d = new Cross();
        d.universe = universe();
        d.allow = Collections.<Allow>emptyList();
        d5(d, row.file, "java.lang.RuntimePermission", "setSecurityManager");
        judgeListed(d, row, subjects, listed);
        assertTrue("HIGH reported: " + d.bad, d.bad.toString().contains("setSecurityManager"));
        assertTrue("not auto-accepted: " + d.autoAccepted, d.autoAccepted.isEmpty());
    }

    // The automatic rule: MEDIUM in the catalogue and D5 from the lint, for the file.  Either alone
    // is not enough.
    @Test
    public void aMediumPermissionIsAcceptedOnlyWhereTheLintReportsD5() throws Exception {
        List<PolicyVerdict.Subject> subjects = Arrays.asList(
                new PolicyVerdict.Subject("https://evil.net/x.jar", new Principal[0]));
        List<List<Permission>> listed = new ArrayList<List<Permission>>();
        listed.add(Arrays.<Permission>asList(new RuntimePermission("setIO")));
        Row row = new Row();
        row.file = "r8/r14-rt-setIO.policy";
        Cross yes = new Cross();
        yes.universe = universe();
        yes.allow = Collections.<Allow>emptyList();
        d5(yes, row.file, "java.lang.RuntimePermission", "setIO");
        judgeListed(yes, row, subjects, listed);
        assertEquals("accepted by the rule", 1, yes.autoAccepted.size());
        assertEquals(yes.ratingMismatch.toString() + yes.unjudged, 0,
                yes.ratingMismatch.length() + yes.unjudged.length() + yes.bad.length());
        Cross no = new Cross();
        no.universe = universe();
        no.allow = Collections.<Allow>emptyList();
        judgeListed(no, row, subjects, listed);
        assertTrue("not accepted without D5: " + no.autoAccepted, no.autoAccepted.isEmpty());
        assertTrue("reported as a disagreement: " + no.ratingMismatch,
                no.ratingMismatch.toString().contains("setIO"));
        // a permission the catalogue does not rate is not taken by the rule, D5 or not
        Cross clean = new Cross();
        clean.universe = universe();
        clean.allow = Collections.<Allow>emptyList();
        d5(clean, row.file, "java.lang.RuntimePermission", "getenv.PATH");
        List<List<Permission>> other = new ArrayList<List<Permission>>();
        other.add(Arrays.<Permission>asList(new RuntimePermission("getenv.PATH")));
        judgeListed(clean, row, subjects, other);
        assertTrue("getenv.PATH is not rated: " + clean.autoAccepted, clean.autoAccepted.isEmpty());
    }

    /** A D5 finding of the lint for {@code file} that names one permission (class, name, no actions). */
    private static void d5(Cross c, String file, String type, String name) {
        PolicyLint.Finding f = new PolicyLint.Finding(file, 1, "D5", "test finding");
        f.permissions.add(new String[]{type, name, null});
        List<PolicyLint.Finding> l = c.d5.get(file);
        if (l == null) c.d5.put(file, l = new ArrayList<PolicyLint.Finding>());
        l.add(f);
    }

    // Opus review 2026-11-02, finding 5; operator ruling 2026-11-03: the rule is per permission.  A
    // MEDIUM permission in a file whose D5 finding is about ANOTHER permission is a disagreement.
    @Test
    public void aMediumPermissionInAFileWhoseD5NamesAnotherPermissionFails() throws Exception {
        List<PolicyVerdict.Subject> subjects = Arrays.asList(
                new PolicyVerdict.Subject("https://evil.net/x.jar", new Principal[0]));
        List<List<Permission>> listed = new ArrayList<List<Permission>>();
        listed.add(Arrays.<Permission>asList(new RuntimePermission("setIO"), new RuntimePermission("exitVM.1"),
                new java.net.SocketPermission("*", "connect")));
        Row row = new Row();
        row.file = "r8/r14-rt-setIO.policy";
        Cross c = new Cross();
        c.universe = universe();
        c.allow = Collections.<Allow>emptyList();
        d5(c, row.file, "java.lang.RuntimePermission", "setIO");
        judgeListed(c, row, subjects, listed);
        assertEquals("only setIO, which the D5 names: " + c.autoAccepted, 1, c.autoAccepted.size());
        assertTrue(c.autoAccepted.toString(), c.autoAccepted.iterator().next().contains("setIO"));
        assertTrue("exitVM.1 is reported: " + c.ratingMismatch, c.ratingMismatch.toString().contains("exitVM.1"));
        assertTrue("SocketPermission * is reported: " + c.ratingMismatch,
                c.ratingMismatch.toString().contains("SocketPermission"));
        assertFalse("setIO is not: " + c.ratingMismatch,
                c.ratingMismatch.toString().contains("RuntimePermission, setIO,"));
        // a D5 finding that names a wildcard the listed permission is covered by (or covers) counts for it
        Cross w = new Cross();
        w.universe = universe();
        w.allow = Collections.<Allow>emptyList();
        d5(w, row.file, "java.lang.RuntimePermission", "exitVM.*");
        List<List<Permission>> one = new ArrayList<List<Permission>>();
        one.add(Arrays.<Permission>asList(new RuntimePermission("exitVM.*")));
        judgeListed(w, row, subjects, one);
        assertEquals(w.ratingMismatch.toString(), 0, w.ratingMismatch.length());
        assertEquals(1, w.autoAccepted.size());
    }

    // The lint's D5 finding carries EVERY permission it is about, also those beyond the six its text
    // shows, from the symbolic check and with the provider alike.
    @Test
    public void aD5FindingNamesEveryMediumPermissionItReports() throws Exception {
        File p = new File(dir, "many.policy");
        String[] names = {"setIO", "exitVM.1", "modifyThread", "modifyThreadGroup", "manageProcess",
            "charsetProvider", "getClassLoader", "setContextClassLoader", "shutdownHooks"};
        StringBuilder sb = new StringBuilder("grant {\n");
        for (String n : names) sb.append("  permission java.lang.RuntimePermission \"").append(n).append("\";\n");
        sb.append("};\n");
        Files.write(p.toPath(), sb.toString().getBytes("UTF-8"));
        for (PolicyVerdict provider : new PolicyVerdict[]{null, PolicyVerdict.open("auto")}) {
            PolicyLint.Linted l = PolicyLint.lint(p, "many.policy", provider, Collections.<String, String>emptyMap());
            PolicyLint.Finding d5 = null;
            for (PolicyLint.Finding f : l.findings) if ("D5".equals(f.kind)) d5 = f;
            assertNotNull("a D5 finding", d5);
            for (String n : names) {
                assertTrue(n + " is named", d5.names(new RuntimePermission(n)));
            }
            assertFalse("a permission the file does not hold", d5.names(new RuntimePermission("getenv.PATH")));
            assertFalse("a permission of another class", d5.names(new java.net.SocketPermission("*", "connect")));
        }
    }

    @Test
    public void anAllowEntryCoversOnlyTheFilesItNames() throws Exception {
        Cross c = new Cross();
        c.universe = universe();
        c.allow = Arrays.asList(entry("exitVM", "r8/r16-rt-exitVM.policy",
                "exitVM lets a stranger stop the VM (r16-rt-exitVM), a denial of service only."));
        List<PolicyVerdict.Subject> subjects = Arrays.asList(
                new PolicyVerdict.Subject("https://evil.net/x.jar", new Principal[0]));
        List<List<Permission>> listed = new ArrayList<List<Permission>>();
        listed.add(Arrays.<Permission>asList(new RuntimePermission("exitVM")));
        Row own = new Row();
        own.file = "r8/r16-rt-exitVM.policy";
        judgeListed(c, own, subjects, listed);
        assertEquals("the named file is allowed: " + c.outsideList + c.unjudged, 0,
                c.outsideList.length() + c.unjudged.length());
        assertEquals(1, c.allowListed);
        Row other = new Row();
        other.file = "r8/z99-new-file.policy";
        judgeListed(c, other, subjects, listed);
        assertTrue("a file the entry does not name inherits nothing: " + c.outsideList,
                c.outsideList.toString().contains("z99-new-file"));
        assertEquals(1, c.allowListed);
        // a second entry that does name the new file covers it
        c.allow = Arrays.asList(c.allow.get(0), entry("exitVM", "r8/z99-new-file.policy",
                "exitVM is wanted by the new file (z99-new-file), stopping the VM only."));
        c.outsideList.setLength(0);
        judgeListed(c, other, subjects, listed);
        assertEquals(c.outsideList.toString(), 0, c.outsideList.length());
    }

    @Test
    public void aStaleFileReferenceFails() {
        List<Allow> a = Arrays.asList(entry("exitVM", "r8/r16-rt-exitVM.policy;r8/gone.policy;n/n90-x.policy",
                "exitVM lets a stranger stop the VM (r16-rt-exitVM), a denial of service only."));
        Map<String, java.util.Set<String>> got = new TreeMap<String, java.util.Set<String>>();
        got.put(a.get(0).key(), new TreeSet<String>(Arrays.asList("r8/r16-rt-exitVM.policy", "n/n90-x.policy")));
        List<String> stale = staleReferences(a, got);
        assertEquals(stale.toString(), 1, stale.size());
        assertTrue(stale.get(0), stale.get(0).contains("r8/gone.policy"));
        got.get(a.get(0).key()).add("r8/gone.policy");
        assertEquals(Collections.<String>emptyList(), staleReferences(a, got));
        assertEquals("an entry nothing matched has only stale references", 3,
                staleReferences(a, new TreeMap<String, java.util.Set<String>>()).size());
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
