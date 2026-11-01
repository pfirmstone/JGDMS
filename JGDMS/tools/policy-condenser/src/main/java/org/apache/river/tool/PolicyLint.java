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
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileSystems;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds unscoped and weakly scoped {@code java.security.AllPermission} grants
 * in {@code java.policy} files.
 *
 * <p>polpAudit only ever adds permissions to the policy it starts from, so an
 * unscoped {@code grant { permission java.security.AllPermission; };} in the
 * starting or the deployed policy masks every recorded permission and defeats
 * least privilege for good.  A grant scoped only by {@code codeBase} is also
 * weak, because a codeBase can be asserted remotely.
 *
 * <p>The verdict "does code from a stranger's codebase get AllPermission under this file" is
 * asked of a production policy provider ({@link PolicyVerdict}); text never decides it.  The
 * text scan ({@link ProxyPolicyGenerator#parseGrants(String, List)}) supplies only line numbers
 * and the finding kinds, and where the two disagree the provider wins (U3).  The tool writes no
 * file.
 *
 * <p>Beyond AllPermission the lint is a dangerous-policy gate: the provider is asked about the
 * shared catalogue of dangerous permissions ({@link PermissionSets#probes()}) for the same subjects
 * (D1 to D5), and a directory walk reports files it does not recognise (P2, W1, W2).  The default
 * run fails on the high kinds (U1 U2 U3 U4 P1 S3 D1 D2 D3 D4 P2 P3); {@code --strict} adds the medium ones
 * (S1 S4 D5 D6 W1 W2).
 *
 * <p>A permission class that can be loaded and built but that the catalogue has no entry for (a
 * deployer's own permission, say) is only noted, as a {@code NOTE} line: it never changes the exit
 * status, in default or {@code --strict} mode, in any scope.  A class that cannot be loaded or built
 * stays D6 or high, as below.
 *
 * <p>Two checks, either of which raises a finding.  The symbolic one ({@link PolicyScope}) reads each
 * grant's codeBase pattern, signedBy and principal list and decides whether it can match something
 * an attacker controls (a wildcard host or port, a user-writable {@code file:} path,
 * {@code jrt:/-}, no codeBase, a wildcard principal of an outside-issued class), then applies the
 * catalogue.  The probe one asks the provider about fixed subjects.  A fixed probe leaves other
 * wildcard forms unasked, which is why the first exists.
 *
 * <p>Findings (U1 U2 U3 U4 P1 D1 D3 D4 P2 fail by default):
 * <ul>
 * <li>{@code U1 UNSCOPED_ALLPERMISSION} - a grant with no {@code codeBase},
 *     {@code signedBy}, {@code principal} or {@code digest} clause that holds
 *     {@code java.security.AllPermission};</li>
 * <li>{@code U2 UNSCOPED_META_GRANT} - an unscoped grant holding
 *     {@code net.jini.security.GrantPermission} whose target is or contains
 *     AllPermission or a wildcard implying it, or {@code UmbrellaGrantPermission};</li>
 * <li>{@code U3 REAL_VERDICT_UNSCOPED} - the production provider gives AllPermission to code from
 *     a stranger's codebase although the text looks scoped;</li>
 * <li>{@code U4 PROPERTY_DEPENDENT_SCOPE} - an AllPermission grant whose {@code codeBase} uses a
 *     {@code ${property}} undefined here (see {@code --define}): the production parser drops the
 *     location, so the grant applies to all code;</li>
 * <li>{@code S1 ALLPERMISSION_CODEBASE_ONLY} - scoped only by codeBase and/or signedBy;</li>
 * <li>{@code S2 ALLPERMISSION_SCOPED} - scoped by a digest or by named principals;</li>
 * <li>{@code S3 WILDCARD_PRINCIPAL} - scoped only by {@code principal * *} or a class with a
 *     {@code *} name;</li>
 * <li>{@code S4 CODEBASE_SCOPED_META_GRANT} - a codeBase-scoped GrantPermission implying
 *     AllPermission, or UmbrellaGrantPermission;</li>
 * <li>{@code S5 PLATFORM_MODULE_GRANT} - AllPermission for exactly {@code jrt:/<module>}:
 *     informational;</li>
 * <li>{@code P1 UNPARSEABLE} - a file or block the text scan cannot read, or that the production
 *     parser rejects;</li>
 * <li>{@code D1} a high-severity catalogue permission (not AllPermission) reaches a stranger's
 *     codebase on any scheme; {@code D2} (high) reaches code run by a principal of any class (also what a wildcard SPIFFE, JWT,
 *     Kerberos, X.500 or user principal means), {@code D6} (medium) a permission the lint cannot judge,
 *     {@code P3} (high) a link loop in a walked directory, {@code I1} (info) a grant pinned to one remote host;
 *     {@code D3} (high) reaches code from any {@code file:} location, AllPermission included;
 *     {@code D4} (high) reaches code from any {@code jrt:} module, AllPermission included;
 *     {@code D5} (medium) a medium-severity catalogue permission reaches any of these subjects;</li>
 * <li>{@code P2 UNRECOGNISED_POLICY_FILE} - a file in a walked directory whose name is not a
 *     policy name, whose extension is not plainly unrelated, and that reads like a policy; it is
 *     linted as well;</li>
 * <li>{@code W1 EMPTY_OR_UNREADABLE_DIRECTORY} and {@code W2 UNRECOGNISED_FILE} (medium) - a named
 *     directory that yielded no policy file while another path did, an entry the walk could not
 *     read, and an unrecognised file that does not read like a policy.</li>
 * </ul>
 *
 * <p>Exit status: 0 clean; 1 on U1, U2, U3, U4, P1, P2, P3, S3, D1, D2, D3 or D4 (also S1, S4, D5, D6,
 * W1, W2 with {@code --strict}; any AllPermission finding including S2 and S5 with {@code --audit-input}); 2 usage error or no
 * policy file found.
 */
public final class PolicyLint {

    static final String ALL = PermissionSets.ALL_PERMISSION;
    private static final String GRANT_PERMISSION = PermissionSets.GRANT_PERMISSION;
    private static final String UMBRELLA_PERMISSION = PermissionSets.UMBRELLA_GRANT_PERMISSION;
    private static final String[] KINDS = {"U1", "U2", "U3", "U4", "S1", "S2", "S3", "S4", "S5",
        "P1", "D1", "D2", "D3", "D4", "D5", "P2", "W1", "W2", "P3", "D6", "I1"};
    private static final String[] NAMES = {"UNSCOPED_ALLPERMISSION", "UNSCOPED_META_GRANT",
        "REAL_VERDICT_UNSCOPED", "PROPERTY_DEPENDENT_SCOPE", "ALLPERMISSION_CODEBASE_ONLY",
        "ALLPERMISSION_SCOPED", "WILDCARD_PRINCIPAL", "CODEBASE_SCOPED_META_GRANT",
        "PLATFORM_MODULE_GRANT", "UNPARSEABLE", "DANGEROUS_TO_STRANGER",
        "DANGEROUS_TO_ANY_PRINCIPAL", "DANGEROUS_TO_ANY_FILE_CODE", "DANGEROUS_TO_ANY_PLATFORM_MODULE",
        "BROAD_PERMISSION", "UNRECOGNISED_POLICY_FILE", "EMPTY_OR_UNREADABLE_DIRECTORY",
        "UNRECOGNISED_FILE", "WALK_LOOP_OR_DEPTH", "UNJUDGEABLE_PERMISSION", "PINNED_REMOTE_CODEBASE"};
    /** Kinds that fail the default run; the medium kinds below fail it only with --strict. */
    private static final String[] HIGH_KINDS = {"U1", "U2", "U3", "U4", "P1", "S3", "D1", "D2", "D3",
        "D4", "P2", "P3"};
    private static final String[] MEDIUM_KINDS = {"S1", "S4", "D5", "D6", "W1", "W2"};
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^{}]*)\\}");
    private static final Pattern PLATFORM_MODULE = Pattern.compile("jrt:/[A-Za-z0-9_.]+");

    private static final int FIRST_OPTIONAL = 10;

    private PolicyLint() {}

    /** One reported problem. */
    static final class Finding {
        final String file;
        final int line;
        final String kind;
        final String reason;

        Finding(String file, int line, String kind, String reason) {
            this.file = file;
            this.line = line;
            this.kind = kind;
            this.reason = reason;
        }

        String name() {
            return NAMES[indexOf(kind)];
        }
    }

    /**
     * The findings for one file and the provider's answers (null if the file was unreadable), and
     * the notes: information that never changes the exit status.
     */
    static final class Linted {
        final List<Finding> findings;
        final PolicyVerdict.Answer answer;
        final List<String> notes;

        Linted(List<Finding> findings, PolicyVerdict.Answer answer) {
            this(findings, answer, Collections.<String>emptyList());
        }

        Linted(List<Finding> findings, PolicyVerdict.Answer answer, List<String> notes) {
            this.findings = findings;
            this.answer = answer;
            this.notes = notes;
        }
    }

    public static void main(String[] args) {
        int status;
        try {
            status = run(args, new PrintStream(System.out, false, "UTF-8"),
                    new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        System.out.flush();
        System.exit(status);
    }

    /** Runs the tool; returns the process exit status. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        boolean strict = false, audit = false, tsv = false, hint = false, requireDefined = false;
        String providerMode = "auto";
        Map<String, String> defines = new LinkedHashMap<String, String>();
        List<String> paths = new ArrayList<String>();
        List<PathMatcher> extra = new ArrayList<PathMatcher>();
        boolean optionsDone = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (optionsDone || !a.startsWith("-") || a.equals("-")) {
                paths.add(a);
            } else if (a.equals("--")) {
                optionsDone = true;
            } else if (a.equals("--strict")) {
                strict = true;
            } else if (a.equals("--audit-input")) {
                audit = true;
            } else if (a.equals("--fix-hint")) {
                hint = true;
            } else if (a.equals("--require-defined")) {
                requireDefined = true;
            } else if (a.equals("-h") || a.equals("--help")) {
                usage(out);
                return 0;
            } else if (a.startsWith("--format=")) {
                String f = a.substring("--format=".length());
                if (f.equals("tsv")) tsv = true;
                else if (f.equals("text")) tsv = false;
                else return usageError(err, "unknown format: " + esc(f));
            } else if (a.startsWith("--provider=")) {
                providerMode = a.substring("--provider=".length());
                if (!providerMode.equals("auto") && !providerMode.equals("dirtychai")
                        && !providerMode.equals("platform")) {
                    return usageError(err, "unknown provider: " + esc(providerMode));
                }
            } else if (a.equals("--define") || a.startsWith("--define=")) {
                String d;
                if (a.equals("--define")) {
                    if (++i >= args.length) return usageError(err, "--define needs NAME=VALUE");
                    d = args[i];
                } else {
                    d = a.substring("--define=".length());
                }
                int eq = d.indexOf('=');
                if (eq <= 0) return usageError(err, "--define needs NAME=VALUE, got: " + esc(d));
                defines.put(d.substring(0, eq), d.substring(eq + 1));
            } else if (a.equals("--glob") || a.startsWith("--glob=")) {
                String g;
                if (a.equals("--glob")) {
                    if (++i >= args.length) return usageError(err, "--glob needs a pattern");
                    g = args[i];
                } else {
                    g = a.substring("--glob=".length());
                }
                try {
                    extra.add(FileSystems.getDefault().getPathMatcher("glob:" + g));
                } catch (RuntimeException e) {
                    return usageError(err, "bad --glob pattern: " + esc(g));
                }
            } else {
                return usageError(err, "unknown option: " + esc(a));
            }
        }
        if (paths.isEmpty()) return usageError(err, "give at least one file or directory");

        List<File> files = new ArrayList<File>();
        List<Finding> walkFindings = new ArrayList<Finding>();
        List<String> emptyDirs = new ArrayList<String>();
        for (String p : paths) {
            File f = new File(p);
            if (f.isDirectory()) {
                Walk w;
                try {
                    w = walk(f, extra);
                } catch (IOException e) {
                    return usageError(err, "cannot walk " + esc(p) + ": " + esc(String.valueOf(e)));
                }
                files.addAll(w.policies);
                for (String u : w.unreadable) {
                    walkFindings.add(new Finding(p, 0, "W1", "the walk could not read " + u));
                }
                for (String l : w.loops) {
                    walkFindings.add(new Finding(p, 0, "P3", l));
                }
                for (File u : w.unrecognised) {
                    try {
                        if (u.length() > MAX_BYTES) {
                            walkFindings.add(tooLarge(u.getPath(), u.length()));
                        } else if (looksLikePolicy(u)) {
                            walkFindings.add(new Finding(u.getPath(), 0, "P2", "not a recognised"
                                    + " policy file name, but it reads like a policy and the runtime"
                                    + " loads any name given to java.security.policy: linted here"));
                            files.add(u);
                        } else if (!u.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                            walkFindings.add(new Finding(u.getPath(), 0, "W2", "unrecognised file"
                                    + " in a policy directory, not read as a policy"));
                        }
                    } catch (IOException e) {
                        walkFindings.add(new Finding(u.getPath(), 0, "P2", "unrecognised file that"
                                + " cannot be read, so it cannot be ruled out as a policy: "
                                + e.getClass().getSimpleName()));
                    }
                }
                if (w.policies.isEmpty()) emptyDirs.add(p);
            } else if (f.isFile()) {
                files.add(f);
            } else {
                return usageError(err, "no such file or directory: " + esc(p));
            }
        }
        if (files.isEmpty()) return usageError(err, "no policy files found under the given paths");
        for (String d : emptyDirs) {
            walkFindings.add(new Finding(d, 0, "W1", "directory yielded no recognised policy file"
                    + " although other paths did"));
        }

        PolicyVerdict provider;
        try {
            provider = PolicyVerdict.open(providerMode);
        } catch (IllegalStateException e) {
            return usageError(err, esc(e.getMessage()));
        }

        Map<String, List<Finding>> byFile = new TreeMap<String, List<Finding>>();
        Map<String, PolicyVerdict.Answer> answers = new TreeMap<String, PolicyVerdict.Answer>();
        Map<String, List<String>> notes = new TreeMap<String, List<String>>();
        for (File f : files) {
            String name = f.getPath();
            if (!byFile.containsKey(name)) {
                Linted l = lint(f, name, provider, defines, requireDefined);
                byFile.put(name, l.findings);
                if (l.answer != null) answers.put(name, l.answer);
                if (!l.notes.isEmpty()) notes.put(name, l.notes);
            }
        }
        for (Finding wf : walkFindings) {
            List<Finding> fs = byFile.get(wf.file);
            if (fs == null) {
                fs = new ArrayList<Finding>();
                byFile.put(wf.file, fs);
            }
            fs.add(wf);
        }
        for (List<Finding> fs : byFile.values()) sortFindings(fs);

        int[] total = new int[KINDS.length];
        List<Finding> all = new ArrayList<Finding>();
        for (List<Finding> fs : byFile.values()) all.addAll(fs);
        for (Finding f : all) total[indexOf(f.kind)]++;

        for (Finding f : all) {
            if (tsv) {
                println(out, esc(f.file) + "\t" + f.line + "\t" + f.kind + "\t" + f.name() + "\t"
                        + esc(f.reason));
            } else {
                println(out, esc(f.file) + ":" + f.line + " " + f.kind + " " + f.name() + " "
                        + esc(f.reason));
            }
        }
        String lead = tsv ? "#" : "SUMMARY";
        println(out, (tsv ? "# " : "") + "PROVIDER " + provider.providerClass + " ("
                + esc(provider.note) + ")"
                + (PolicyVerdict.DIRTYCHAI.equals(provider.providerClass) ? ""
                : " [not the DirtyChai provider]"));
        for (Map.Entry<String, List<Finding>> e : byFile.entrySet()) {
            int[] n = new int[KINDS.length];
            for (Finding f : e.getValue()) n[indexOf(f.kind)]++;
            println(out, lead + " " + esc(e.getKey()) + ": " + counts(n));
        }
        for (Map.Entry<String, PolicyVerdict.Answer> e : answers.entrySet()) {
            PolicyVerdict.Answer v = e.getValue();
            for (String note : v.notes) {
                println(out, (tsv ? "# " : "") + "NOTE " + esc(e.getKey()) + ": " + esc(note));
            }
            println(out, (tsv ? "# " : "") + "VERDICT " + esc(e.getKey()) + ": "
                    + (v.loadError != null ? "not loaded (" + esc(v.loadError) + ")"
                    : "stranger=" + v.stranger + " principal=" + v.strangerPrincipal
                    + " file=" + v.fileCode + " platform=" + v.platformModule
                    + " nullCodeSource=" + v.nullCodeSource));
        }
        for (Map.Entry<String, List<String>> e : notes.entrySet()) {
            for (String note : e.getValue()) {
                println(out, (tsv ? "# " : "") + "NOTE " + esc(e.getKey()) + ": " + esc(note));
            }
        }
        int high = 0, weak = 0;
        for (String k : HIGH_KINDS) high += total[indexOf(k)];
        for (String k : MEDIUM_KINDS) weak += total[indexOf(k)];
        int anyScoped = total[indexOf("S1")] + total[indexOf("S2")] + total[indexOf("S3")]
                + total[indexOf("S4")] + total[indexOf("S5")];
        boolean fail = high > 0 || (strict && weak > 0) || (audit && anyScoped > 0);
        println(out, (tsv ? "#" : "TOTAL") + " files=" + byFile.size() + " " + counts(total)
                + " exit=" + (fail ? 1 : 0));

        if (hint) {
            for (Finding f : all) {
                if (f.kind.equals("U1")) printHint(out, f);
            }
        }
        out.flush();
        return fail ? 1 : 0;
    }

    private static void println(PrintStream ps, String s) {
        ps.print(s);
        ps.print((char) 10);
    }

    private static void usage(PrintStream out) {
        println(out, "usage: PolicyLint [--strict] [--audit-input] [--require-defined]"
                + " [--format=text|tsv] [--glob PATTERN]... [--define NAME=VALUE]..."
                + " [--provider=auto|dirtychai|platform] [--fix-hint] FILE-OR-DIR...");
        println(out, "  exit 0 clean; 1 on U1, U2, U3, U4, P1, P2, P3, S3, D1, D2, D3 or D4 (S1, S4, D5,"
                + " D6, W1, W2 with --strict; any AllPermission finding with --audit-input); 2 usage"
                + " error or no file found");
    }

    private static int usageError(PrintStream err, String msg) {
        println(err, "PolicyLint: " + msg);
        usage(err);
        return 2;
    }

    private static int indexOf(String kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return i;
        throw new IllegalArgumentException(kind);
    }

    private static String counts(int[] n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KINDS.length; i++) {
            if (i >= FIRST_OPTIONAL && n[i] == 0) continue; // the newer kinds appear only when found
            if (sb.length() > 0) sb.append(' ');
            sb.append(KINDS[i]).append('=').append(n[i]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- walking

    /** The largest file the lint reads; a larger one is P1, because a policy can be padded to hide a grant. */
    static final long MAX_BYTES = 16L << 20;

    /** How many link hops (symbolic links, Windows junctions) the walk follows. */
    static final int MAX_LINK_DEPTH = 8;

    static boolean builtInMatch(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".policy") || n.endsWith(".policy.old")
                || n.endsWith(".policy.new") || n.equals("policy.all")
                || n.contains(".policy.") || n.endsWith(".grantperm");
    }

    /**
     * The files the walk skips without reading: a README, a Markdown document, a log, a jar, a class
     * file, a PNG.  A {@code .txt} file is skipped only if it holds none of the words
     * {@code grant}, {@code permission} or {@code keystore}.  Every other name is read: an extension
     * does not say what a file is, because the runtime loads any file named by
     * {@code java.security.policy}.
     */
    static boolean ignoredName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("readme") || n.equals("permissions.list") || n.endsWith(".md")
                || n.endsWith(".log")
                || n.endsWith(".jar") || n.endsWith(".class") || n.endsWith(".png");
    }

    /** What a directory walk found. */
    static final class Walk {
        final List<File> policies = new ArrayList<File>();
        /** Regular files that are neither recognised policy names nor on the short ignore list. */
        final List<File> unrecognised = new ArrayList<File>();
        /** Entries the walk could not read. */
        final List<String> unreadable = new ArrayList<String>();
        /** Links followed in a loop or deeper than {@link #MAX_LINK_DEPTH}. */
        final List<String> loops = new ArrayList<String>();
    }

    private static Walk walk(File dir, List<PathMatcher> extra) throws IOException {
        Walk w = new Walk();
        Path root = dir.toPath();
        if (!Files.isReadable(root)) throw new IOException("cannot read " + root);
        walkDir(root, 0, new ArrayList<Path>(), w, extra);
        Collections.sort(w.policies);
        Collections.sort(w.unrecognised);
        return w;
    }

    /**
     * Walks one directory.  Links (symbolic links, and the junctions Windows reports as "other")
     * are followed to {@link #MAX_LINK_DEPTH} hops; a directory that is its own ancestor, by real
     * path, is a loop and is reported instead of entered.
     */
    private static void walkDir(Path dir, int linkDepth, List<Path> ancestors, Walk w,
            List<PathMatcher> extra) {
        Path real;
        try {
            real = dir.toRealPath();
        } catch (IOException e) {
            w.unreadable.add(dir + " (" + e.getClass().getSimpleName() + ")");
            return;
        }
        for (Path a : ancestors) {
            if (a.equals(real)) {
                w.loops.add(dir + " is a link loop back to " + real + ": not followed, anything below"
                        + " it is not linted through this path");
                return;
            }
        }
        ancestors.add(real);
        try {
            java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir);
            try {
                for (Path e : ds) visit(e, linkDepth, ancestors, w, extra);
            } finally {
                ds.close();
            }
        } catch (IOException | java.nio.file.DirectoryIteratorException e) {
            w.unreadable.add(dir + " (" + e.getClass().getSimpleName() + ")");
        } finally {
            ancestors.remove(ancestors.size() - 1);
        }
    }

    private static void visit(Path e, int linkDepth, List<Path> ancestors, Walk w,
            List<PathMatcher> extra) {
        BasicFileAttributes a;
        try {
            a = Files.readAttributes(e, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ex) {
            w.unreadable.add(e + " (" + ex.getClass().getSimpleName() + ")");
            return;
        }
        boolean link = a.isSymbolicLink() || a.isOther();
        if (a.isDirectory() || (link && Files.isDirectory(e))) {
            int depth = linkDepth + (link ? 1 : 0);
            if (depth > MAX_LINK_DEPTH) {
                w.loops.add(e + " is more than " + MAX_LINK_DEPTH + " links deep: not followed");
            } else {
                walkDir(e, depth, ancestors, w, extra);
            }
        } else if (a.isRegularFile() || (link && Files.isRegularFile(e))) {
            Path n = e.getFileName();
            if (n == null) return;
            boolean hit = builtInMatch(n.toString());
            for (int i = 0; !hit && i < extra.size(); i++) hit = extra.get(i).matches(n);
            if (hit) w.policies.add(e.toFile());
            else if (!ignoredName(n.toString())) w.unrecognised.add(e.toFile());
        } else if (link) {
            w.unreadable.add(e + " (a link that leads nowhere)");
        }
    }

    private static Finding tooLarge(String file, long size) {
        return new Finding(file, 0, "P1", "file is " + size + " bytes, over the " + MAX_BYTES
                + "-byte limit: it is not read, so it cannot be ruled out as a padded policy");
    }

    /**
     * Whether an unrecognised file reads like a policy: the runtime loads any file named by
     * {@code java.security.policy}, so a name the walk does not know proves nothing.  The whole
     * file is read (up to {@link #MAX_BYTES}); NUL bytes are dropped so a UTF-16 file matches.
     */
    private static boolean looksLikePolicy(File f) throws IOException {
        byte[] all = Files.readAllBytes(f.toPath());
        StringBuilder sb = new StringBuilder(all.length);
        for (byte x : all) if (x != 0) sb.append((char) (x & 0xff));
        String t = sb.toString().toLowerCase(Locale.ROOT);
        return t.contains("grant") || t.contains("permission") || t.contains("keystore")
                || t.contains("\\u");
    }

    // ------------------------------------------------------------- linting

    /**
     * Lints one file: the text scan for kinds and lines, then the provider for the verdict.
     * {@code provider} null means text only (the verdict rules U3 and the real P1 are then off).
     */
    static Linted lint(File f, String display, PolicyVerdict provider, Map<String, String> defines) {
        return lint(f, display, provider, defines, false);
    }

    static Linted lint(File f, String display, PolicyVerdict provider, Map<String, String> defines,
            boolean requireDefined) {
        List<Finding> res = new ArrayList<Finding>();
        long size = f.length();
        if (size > MAX_BYTES) {
            res.add(tooLarge(display, size));
            return new Linted(res, null);
        }
        List<ProxyPolicyGenerator.Problem> problems =
                new ArrayList<ProxyPolicyGenerator.Problem>();
        List<ProxyPolicyGenerator.GrantBlock> grants;
        try {
            grants = ProxyPolicyGenerator.parseGrants(ProxyPolicyGenerator.readText(f), problems);
        } catch (IOException e) {
            res.add(new Finding(display, 0, "P1", "file cannot be read: " + e.getClass().getSimpleName()));
            return new Linted(res, null);
        }
        for (ProxyPolicyGenerator.Problem p : problems) {
            res.add(new Finding(display, p.line, "P1", p.reason));
        }
        Properties env = new Properties();
        env.putAll(System.getProperties());
        env.putAll(defines);
        env.setProperty("/", File.separator);
        int firstAllLine = 0;
        List<String> principalClasses = new ArrayList<String>();
        for (ProxyPolicyGenerator.GrantBlock g : grants) {
            for (String[] pr : g.principals) {
                if (pr[0] != null && !principalClasses.contains(pr[0])) principalClasses.add(pr[0]);
            }
            classify(display, g, env, defines, requireDefined, res);
            for (ProxyPolicyGenerator.Perm p : g.permissions) {
                if (ALL.equals(p.type) && firstAllLine == 0) firstAllLine = g.line;
            }
        }
        PolicyVerdict.Answer answer = null;
        if (provider != null) {
            List<PermissionSets.Probe> named = new ArrayList<PermissionSets.Probe>();
            for (ProxyPolicyGenerator.GrantBlock g : grants) {
                for (ProxyPolicyGenerator.Perm p : g.permissions) {
                    PermissionSets.Probe pr = PermissionSets.probe(p.type, p.name, p.actions, env);
                    if (pr != null) named.add(pr);
                }
            }
            answer = provider.ask(f, defines, principalClasses, named);
        }
        // the symbolic verdict does not depend on what the provider was asked: either half counts
        List<String> unbuildable = answer == null ? Collections.<String>emptyList() : answer.unbuildable;
        List<String> notes = new ArrayList<String>();
        for (ProxyPolicyGenerator.GrantBlock g : grants) {
            symbolic(display, g, env, unbuildable, res, notes);
        }
        if (answer != null) {
            if (answer.providerOnly) {
                // the parser reads this file and the provider cannot load it: a verdict from the
                // provider is impossible, so the file cannot be called clean
                res.add(new Finding(display, 0, "P1", "the policy provider cannot load a file the parser"
                        + " accepts, so what it grants cannot be asked: " + answer.loadError));
            } else if (answer.loadError != null) {
                // the text scan's own P1 already fails the run; one P1 per cause is enough
                if (!has(res, "P1")) {
                    res.add(new Finding(display, 0, "P1", "the production policy parser rejects"
                            + " this file: " + answer.loadError));
                }
            } else {
                if (answer.stranger && !has(res, "U1") && !has(res, "U3")) {
                    res.add(new Finding(display, firstAllLine > 0 ? firstAllLine : 1, "U3",
                            "the real policy parser grants AllPermission to all code although the"
                            + " text looks scoped (" + provider.providerClass + ")"));
                } else if (answer.strangerPrincipal && !answer.stranger && !has(res, "S3")) {
                    res.add(new Finding(display, firstAllLine > 0 ? firstAllLine : 1, "S3",
                            "the real policy parser grants AllPermission to code run by any"
                            + " authenticated principal"));
                }
                dangerFindings(display, answer, grants, res);
            }
        }
        sortFindings(res);
        return new Linted(res, answer, notes);
    }

    /**
     * The symbolic check of one grant block: whom its scope can reach, then the catalogue applied
     * to each permission it holds.  A permission the catalogue does not know and the lint cannot
     * load or build is "cannot tell": medium (D6) where the scope reaches nobody an attacker
     * controls, high where it does.  A class the lint can load and build but the catalogue has no
     * entry for is only noted ({@code notes}): it may be a deployer's own permission, and the lint
     * does not fail a run for a class it merely does not know.
     */
    private static void symbolic(String display, ProxyPolicyGenerator.GrantBlock g, Properties env,
            Collection<String> unbuildable, List<Finding> res, List<String> notes) {
        PolicyScope.Verdict v = PolicyScope.scope(g, env, unbuildable);
        boolean reaches = v.reach != PolicyScope.Reach.NONE;
        Map<String, List<String>> found = new LinkedHashMap<String, List<String>>();
        boolean holdsOther = false; // a catalogued permission other than AllPermission (S1 covers that)
        for (ProxyPolicyGenerator.Perm p : g.permissions) {
            if (hasSigner(p.signedBy)) continue; // a per-permission signedBy needs a signer too
            String what = p.type.substring(p.type.lastIndexOf('.') + 1)
                    + (p.name == null ? "" : " " + p.name) + (p.actions == null ? "" : " " + p.actions);
            if (ALL.equals(p.type)) {
                String kind = null;
                switch (v.reach) {
                    case STRANGER:
                        if (!has(res, "U1") && !has(res, "U3")) kind = "U3";
                        break;
                    case PRINCIPAL:
                        if (!has(res, "S3")) kind = "S3";
                        break;
                    case FILE:
                        kind = "D3";
                        break;
                    case PLATFORM:
                        kind = "D4";
                        break;
                    default:
                        break;
                }
                if (kind != null) add(found, kind, what);
                continue;
            }
            PermissionSets.Danger d = PermissionSets.danger(p.type, p.name, p.actions, env);
            boolean unknown = false;
            int severity;
            if (d != null) {
                severity = d.severity;
            } else if (!judgeable(p, env) || cannotTell(p, env)) {
                unknown = true;
                severity = reaches ? PermissionSets.HIGH : PermissionSets.MEDIUM;
            } else {
                if (!PermissionSets.catalogued(p.type)) {
                    String note = "line " + p.line + ": permission class " + p.type + " is loadable but"
                            + " not in the catalogue of dangerous permissions: not judged";
                    boolean seen = false;
                    for (String n : notes) seen |= n.contains(" class " + p.type + " ");
                    if (!seen) notes.add(note);
                }
                continue;
            }
            holdsOther = true;
            String kind;
            if (!reaches) {
                kind = unknown ? "D6" : null;
            } else if (severity >= PermissionSets.HIGH) {
                kind = zoneKind(v.reach);
            } else {
                kind = "D5";
            }
            if (kind != null) {
                add(found, kind, what + (unknown ? " [what it grants cannot be judged: the class cannot"
                        + " be loaded or built here, or its implies throws]" : " [" + d.reason + "]"));
            }
        }
        for (Map.Entry<String, List<String>> e : found.entrySet()) {
            List<String> l = e.getValue();
            StringBuilder sb = new StringBuilder("symbolic scope check: ").append(l.size())
                    .append(" permission").append(l.size() == 1 ? "" : "s").append(" granted to ")
                    .append(who(v.reach)).append(" (").append(v.why).append("): ");
            for (int i = 0; i < l.size() && i < 6; i++) {
                if (i > 0) sb.append("; ");
                sb.append(l.get(i));
            }
            if (l.size() > 6) sb.append("; and ").append(l.size() - 6).append(" more");
            res.add(new Finding(display, g.line, e.getKey(), sb.toString()));
        }
        boolean reported = false;
        for (Finding f : res) reported |= f.line == g.line && !f.kind.equals("I1");
        if (v.pinnedRemote && holdsOther && !reported) {
            res.add(new Finding(display, g.line, "I1", "grant is pinned to one remote host (" + v.why
                    + ") and holds permissions worth a second look: confirm the host is yours"));
        }
    }

    private static void add(Map<String, List<String>> m, String kind, String what) {
        List<String> l = m.get(kind);
        if (l == null) {
            l = new ArrayList<String>();
            m.put(kind, l);
        }
        l.add(what);
    }

    private static String zoneKind(PolicyScope.Reach r) {
        switch (r) {
            case STRANGER: return "D1";
            case PRINCIPAL: return "D2";
            case FILE: return "D3";
            default: return "D4";
        }
    }

    private static String who(PolicyScope.Reach r) {
        switch (r) {
            case STRANGER: return "code from a stranger's codebase";
            case PRINCIPAL: return "code run by any authenticated principal of the class";
            case FILE: return "code from a user-writable or broad file: location";
            case PLATFORM: return "code from any jrt: module";
            default: return "a scope that reaches no attacker-controlled subject";
        }
    }

    /**
     * Whether a permission's own {@code implies} throws when asked: an answer the lint cannot read
     * as "no".  The provider path records the same thing for the permissions it lists.
     */
    private static boolean cannotTell(ProxyPolicyGenerator.Perm p, Properties env) {
        String n = p.name;
        if (n != null && n.contains("${")) n = PermissionSets.expand(n, env);
        java.security.Permission perm = PermissionSets.instantiate(p.type, n, p.actions);
        if (perm == null) return false;
        try {
            perm.implies(new RuntimePermission("lint.probe"));
            perm.implies(new java.security.AllPermission());
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Whether the lint can say what a permission grants: its class is loadable (or known by name)
     * and its constructor accepts the name and actions.  A name with an undefined property is the
     * parser's business, not a reason to doubt the class.
     */
    private static boolean judgeable(ProxyPolicyGenerator.Perm p, Properties env) {
        if (PermissionSets.knownByName(p.type)) return true;
        if (!PermissionSets.loadable(p.type)) return false;
        String n = p.name;
        if (n != null && n.contains("${")) {
            n = PermissionSets.expand(n, env);
            if (n == null) return true;
        }
        return PermissionSets.instantiate(p.type, n, p.actions) != null;
    }

    private static void sortFindings(List<Finding> res) {
        Collections.sort(res, new Comparator<Finding>() {
            public int compare(Finding a, Finding b) {
                if (a.line != b.line) return a.line < b.line ? -1 : 1;
                return indexOf(a.kind) - indexOf(b.kind);
            }
        });
    }

    /**
     * Turns the provider's grants of dangerous probes into findings.  A probe an earlier zone
     * already grants is not repeated (a stranger's grant covers every other subject); where
     * AllPermission is granted in a zone the other probes of that zone add nothing.
     */
    private static void dangerFindings(String display, PolicyVerdict.Answer a,
            List<ProxyPolicyGenerator.GrantBlock> grants, List<Finding> res) {
        String[] zones = {PolicyVerdict.ZONE_STRANGER, PolicyVerdict.ZONE_PRINCIPAL,
            PolicyVerdict.ZONE_FILE, PolicyVerdict.ZONE_PLATFORM};
        String[] who = {"code from a stranger's codebase", "code run by an authenticated principal"
            + " of any class", "code from any file: location", "code from any jrt: module"};
        String[] highKind = {"D1", "D2", "D3", "D4"};
        for (int z = 0; z < zones.length; z++) {
            if (a.hit(PolicyVerdict.ZONE_STRANGER, "AllPermission") && z > 0) break;
            boolean allHere = a.hit(zones[z], "AllPermission");
            List<PolicyVerdict.Hit> high = new ArrayList<PolicyVerdict.Hit>();
            List<PolicyVerdict.Hit> medium = new ArrayList<PolicyVerdict.Hit>();
            for (PolicyVerdict.Hit h : a.hits) {
                if (!h.zone.equals(zones[z])) continue;
                if (z > 0 && a.hit(PolicyVerdict.ZONE_STRANGER, h.id)) continue;
                boolean isAll = "AllPermission".equals(h.id);
                if (coveredByAllFiles(a, zones[z], h.id)) continue;
                if (allHere && !isAll) continue;
                if (isAll && z <= 1) continue; // U3 and S3 report AllPermission to these subjects
                if (h.severity >= PermissionSets.HIGH) high.add(h);
                else medium.add(h);
            }
            if (!high.isEmpty() && !has(res, highKind[z])) {
                res.add(new Finding(display, lineOf(grants, high), highKind[z], describe(high,
                        who[z], "dangerous permission")));
            }
            if (!medium.isEmpty() && !has(res, "D5")) {
                res.add(new Finding(display, lineOf(grants, medium), "D5", describe(medium,
                        who[z], "broad permission")));
            }
        }
    }

    /** A path probe adds nothing where {@code <<ALL FILES>>} with the same action is granted. */
    private static boolean coveredByAllFiles(PolicyVerdict.Answer a, String zone, String id) {
        if (!id.startsWith("FilePermission ") || id.startsWith("FilePermission <<ALL FILES>> ")) {
            return false;
        }
        String action = id.substring(id.lastIndexOf(' ') + 1);
        return a.hit(zone, "FilePermission <<ALL FILES>> " + action);
    }

    private static String describe(List<PolicyVerdict.Hit> hits, String who, String what) {
        StringBuilder sb = new StringBuilder();
        sb.append(hits.size()).append(' ').append(what).append(hits.size() == 1 ? "" : "s")
                .append(" granted to ").append(who).append(" (").append(hits.get(0).subject)
                .append("): ");
        for (int i = 0; i < hits.size() && i < 6; i++) {
            if (i > 0) sb.append("; ");
            sb.append(hits.get(i).id).append(" [").append(hits.get(i).reason).append(']');
        }
        if (hits.size() > 6) sb.append("; and ").append(hits.size() - 6).append(" more");
        return sb.toString();
    }

    /** The first grant block holding a permission of a granted probe's class; else line 1. */
    private static int lineOf(List<ProxyPolicyGenerator.GrantBlock> grants,
            List<PolicyVerdict.Hit> hits) {
        for (ProxyPolicyGenerator.GrantBlock g : grants) {
            for (ProxyPolicyGenerator.Perm p : g.permissions) {
                for (PolicyVerdict.Hit h : hits) {
                    if (h.permissionClass.equals(p.type)) return g.line;
                }
            }
        }
        return 1;
    }

    private static boolean has(List<Finding> res, String kind) {
        for (Finding f : res) if (f.kind.equals(kind)) return true;
        return false;
    }

    private static boolean present(String s) {
        return s != null && s.trim().length() > 0;
    }

    /** True if signedBy names at least one alias: the production parser splits it on commas. */
    private static boolean hasSigner(String s) {
        if (s == null) return false;
        for (String t : s.split(",")) if (t.trim().length() > 0) return true;
        return false;
    }

    private static boolean wildcard(String[] principal) {
        return principal[1] == null || "*".equals(principal[1]) || "*".equals(principal[0]);
    }

    /** Names of {@code ${property}} references in {@code s} that {@code env} does not define. */
    static List<String> undefinedProperties(String s, Properties env) {
        List<String> res = new ArrayList<String>();
        if (s == null) return res;
        Matcher m = PROPERTY.matcher(s);
        while (m.find()) {
            String name = m.group(1);
            if (env.getProperty(name) == null && !res.contains(name)) res.add(name);
        }
        return res;
    }

    private static void classify(String file, ProxyPolicyGenerator.GrantBlock g, Properties env,
            Map<String, String> defines, boolean requireDefined, List<Finding> res) {
        boolean digest = present(g.digest);
        boolean principal = !g.principals.isEmpty();
        boolean signer = hasSigner(g.signedBy);
        boolean codeScoped = present(g.codebase) || signer;
        boolean unscoped = !digest && !principal && !codeScoped;
        boolean wild = false;
        for (String[] pr : g.principals) wild |= wildcard(pr);

        ProxyPolicyGenerator.Perm all = null;
        for (ProxyPolicyGenerator.Perm p : g.permissions) {
            if (ALL.equals(p.type)) {
                all = p;
                break;
            }
        }
        boolean meta = false;
        for (ProxyPolicyGenerator.Perm p : g.permissions) {
            if (UMBRELLA_PERMISSION.equals(p.type)
                    || (GRANT_PERMISSION.equals(p.type) && grantTargetImpliesAll(p.name))) {
                meta = true;
            }
        }
        if (all != null) {
            if (unscoped) {
                res.add(new Finding(file, g.line, "U1", "grant with no codeBase, signedBy, principal"
                        + " or digest holds AllPermission (line " + all.line + "): masks every"
                        + " permission polpAudit would record"));
            } else if (digest) {
                res.add(new Finding(file, g.line, "S2", "AllPermission (line " + all.line
                        + ") scoped by digest" + (principal ? " and principal" : "")));
            } else if (principal) {
                if (wild && PolicyScope.scope(g, env, Collections.<String>emptyList()).reach
                        == PolicyScope.Reach.PRINCIPAL) {
                    res.add(new Finding(file, g.line, "S3", "AllPermission (line " + all.line
                            + ") scoped by a wildcard principal, which matches any authenticated"
                            + " subject"));
                } else if (wild) {
                    res.add(new Finding(file, g.line, "S2", "AllPermission (line " + all.line
                            + ") scoped by a wildcard principal and by a signer, a digest or a"
                            + " codeBase that is not attacker-controlled"));
                } else {
                    res.add(new Finding(file, g.line, "S2", "AllPermission (line " + all.line
                            + ") scoped by principal"));
                }
            } else if (!signer && PLATFORM_MODULE.matcher(g.codebase.trim()).matches()) {
                res.add(new Finding(file, g.line, "S5", "AllPermission (line " + all.line
                        + ") for platform module " + g.codebase.trim() + ": platform code is"
                        + " privileged without a grant while it is alone on the stack"));
            } else {
                res.add(new Finding(file, g.line, "S1", "AllPermission (line " + all.line
                        + ") scoped only by codeBase/signedBy ["
                        + (present(g.codebase) ? "codeBase " + g.codebase : "signedBy " + g.signedBy)
                        + "], which can be asserted remotely" + propertySources(g.codebase, defines)));
            }
        }
        if (requireDefined) {
            Properties only = new Properties();
            only.putAll(defines);
            only.setProperty("/", File.separator);
            List<String> missing = undefinedProperties(g.codebase, only);
            if (!missing.isEmpty()) {
                res.add(new Finding(file, g.line, "U4", "--require-defined: codeBase " + g.codebase
                        + " uses propert" + (missing.size() == 1 ? "y " : "ies ") + missing
                        + " not given with --define: the lint cannot know the runtime's value, and an"
                        + " undefined one makes the production parser drop the location"));
            }
        } else if ((all != null || meta) && !digest && !principal && !signer) {
            List<String> undefined = undefinedProperties(g.codebase, env);
            if (!undefined.isEmpty()) {
                res.add(new Finding(file, g.line, "U4", "codeBase " + g.codebase + " uses undefined"
                        + " propert" + (undefined.size() == 1 ? "y " : "ies ") + undefined
                        + ": the production parser then drops the location and the grant applies"
                        + " to ALL code (a DirtyChai fail-open; define it with --define NAME=VALUE"
                        + " as the runtime does)"));
            }
        }
        if (unscoped || (codeScoped && !digest && !principal)) {
            String kind = unscoped ? "U2" : "S4";
            for (ProxyPolicyGenerator.Perm p : g.permissions) {
                if (UMBRELLA_PERMISSION.equals(p.type)) {
                    res.add(new Finding(file, g.line, kind, (unscoped ? "unscoped" : "codeBase-scoped")
                            + " grant holds UmbrellaGrantPermission (line " + p.line
                            + "), which implies every GrantPermission"));
                } else if (GRANT_PERMISSION.equals(p.type) && grantTargetImpliesAll(p.name)) {
                    res.add(new Finding(file, g.line, kind, (unscoped ? "unscoped" : "codeBase-scoped")
                            + " grant holds GrantPermission (line " + p.line
                            + ") whose target is or implies AllPermission: "
                            + (p.name.length() > 80 ? p.name.substring(0, 80) : p.name)));
                }
            }
        }
    }

    /**
     * True if a GrantPermission target contains AllPermission or a class-name
     * wildcard ({@code *}, {@code -} or {@code a.b.*}) that would cover it.
     */
    static boolean grantTargetImpliesAll(String target) {
        if (target == null) return false;
        if (target.contains("AllPermission")) return true;
        String t = target.trim();
        if (t.startsWith("delim=") && t.length() > "delim=".length()) {
            t = t.substring("delim=".length() + 1);
        }
        for (String entry : t.split(";")) {
            String e = entry.trim();
            int sp = 0;
            while (sp < e.length() && !Character.isWhitespace(e.charAt(sp))) sp++;
            String cls = e.substring(0, sp);
            if (cls.equals("*") || cls.equals("-")) return true;
            if (cls.endsWith(".*") && ALL.startsWith(cls.substring(0, cls.length() - 1))) return true;
        }
        return false;
    }

    /**
     * Where each {@code ${property}} of a codeBase got its value here: a property the runtime does
     * not define the same way turns a scoped grant into another one, so the names are printed.
     */
    private static String propertySources(String codebase, Map<String, String> defines) {
        if (codebase == null) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = PROPERTY.matcher(codebase);
        List<String> seen = new ArrayList<String>();
        while (m.find()) {
            String name = m.group(1);
            if (seen.contains(name) || name.equals("/")) continue;
            seen.add(name);
            sb.append(sb.length() == 0 ? " [codeBase properties: " : ", ").append(name).append(' ')
                    .append(defines.containsKey(name) ? "(--define)"
                    : System.getProperty(name) != null ? "(lint JVM system property)" : "(undefined)");
        }
        return sb.length() == 0 ? "" : sb.append(']').toString();
    }

    private static void printHint(PrintStream out, Finding f) {
        println(out, "--- fix-hint " + esc(f.file) + ":" + f.line);
        println(out, "# Start from an empty policy and let polpAudit record what the service uses,");
        println(out, "# or replace the unscoped grant with one scoped by the digest of the jar:");
        println(out, "grant digest \"SHA-256 TODO-hex-digest-of-the-jar\" {");
        println(out, "    // TODO: only the permissions polpAudit recorded for that jar");
        println(out, "};");
    }

    // ----------------------------------------------------------- escaping

    /**
     * Replaces control, format, line-separator and unpaired-surrogate
     * characters with {@code \}{@code uXXXX} so policy text cannot rewrite
     * a terminal or forge an output line.
     */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean bad;
            if (Character.isHighSurrogate(c) && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1))) {
                sb.append(c).append(s.charAt(++i));
                continue;
            }
            bad = Character.isISOControl(c) || c == 0x2028 || c == 0x2029
                    || Character.getType(c) == Character.FORMAT || Character.isSurrogate(c)
                    || Character.getType(c) == Character.UNASSIGNED;
            if (bad) sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
