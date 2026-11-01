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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
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
 * run fails on the high kinds (U1 U2 U3 U4 P1 D1 D3 D4 P2); {@code --strict} adds the medium ones
 * (S1 S3 S4 D2 D5 W1 W2).
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
 *     codebase on any scheme; {@code D2} (medium) reaches code run by a principal of any class;
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
 * <p>Exit status: 0 clean; 1 on U1, U2, U3, U4 or P1 (also S1, S3, S4 with {@code --strict}; any
 * AllPermission finding including S2 and S5 with {@code --audit-input}); 2 usage error or no
 * policy file found.
 */
public final class PolicyLint {

    static final String ALL = PermissionSets.ALL_PERMISSION;
    private static final String GRANT_PERMISSION = PermissionSets.GRANT_PERMISSION;
    private static final String UMBRELLA_PERMISSION = PermissionSets.UMBRELLA_GRANT_PERMISSION;
    private static final String[] KINDS = {"U1", "U2", "U3", "U4", "S1", "S2", "S3", "S4", "S5",
        "P1", "D1", "D2", "D3", "D4", "D5", "P2", "W1", "W2"};
    private static final String[] NAMES = {"UNSCOPED_ALLPERMISSION", "UNSCOPED_META_GRANT",
        "REAL_VERDICT_UNSCOPED", "PROPERTY_DEPENDENT_SCOPE", "ALLPERMISSION_CODEBASE_ONLY",
        "ALLPERMISSION_SCOPED", "WILDCARD_PRINCIPAL", "CODEBASE_SCOPED_META_GRANT",
        "PLATFORM_MODULE_GRANT", "UNPARSEABLE", "DANGEROUS_TO_STRANGER",
        "DANGEROUS_TO_ANY_PRINCIPAL", "DANGEROUS_TO_ANY_FILE_CODE", "DANGEROUS_TO_ANY_PLATFORM_MODULE",
        "BROAD_PERMISSION", "UNRECOGNISED_POLICY_FILE", "EMPTY_OR_UNREADABLE_DIRECTORY",
        "UNRECOGNISED_FILE"};
    /** Kinds that fail the default run; the medium kinds below fail it only with --strict. */
    private static final String[] HIGH_KINDS = {"U1", "U2", "U3", "U4", "P1", "D1", "D3", "D4", "P2"};
    private static final String[] MEDIUM_KINDS = {"S1", "S3", "S4", "D2", "D5", "W1", "W2"};
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

    /** The findings for one file and the provider's answers (null if the file was unreadable). */
    static final class Linted {
        final List<Finding> findings;
        final PolicyVerdict.Answer answer;

        Linted(List<Finding> findings, PolicyVerdict.Answer answer) {
            this.findings = findings;
            this.answer = answer;
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
                for (File u : w.unrecognised) {
                    try {
                        if (looksLikePolicy(u)) {
                            walkFindings.add(new Finding(u.getPath(), 0, "P2", "not a recognised"
                                    + " policy file name, but it reads like a policy and the runtime"
                                    + " loads any name given to java.security.policy: linted here"));
                            files.add(u);
                        } else {
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
        for (File f : files) {
            String name = f.getPath();
            if (!byFile.containsKey(name)) {
                Linted l = lint(f, name, provider, defines, requireDefined);
                byFile.put(name, l.findings);
                if (l.answer != null) answers.put(name, l.answer);
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
        println(out, "  exit 0 clean; 1 on U1, U2, U3, U4, P1, P2, D1, D3 or D4 (S1, S3, S4, D2, D5,"
                + " W1, W2 with --strict; any AllPermission finding with --audit-input); 2 usage"
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

    static boolean builtInMatch(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".grantperm")) return false;
        return n.endsWith(".policy") || n.endsWith(".policy.old")
                || n.endsWith(".policy.new") || n.equals("policy.all")
                || n.contains(".policy.");
    }

    /** Extensions of files that are plainly not policies; any other unrecognised file is reported. */
    private static final String[] UNRELATED = {"txt", "md", "markdown", "html", "htm", "xml", "xsd",
        "json", "yaml", "yml", "jar", "class", "java", "properties", "log", "sh", "bat", "cmd", "ps1",
        "png", "jpg", "jpeg", "gif", "svg", "ico", "pdf", "zip", "gz", "tgz", "tar", "bz2", "xz",
        "7z", "sha1", "sha256", "sha512", "md5", "asc", "sig", "crt", "cer", "pem", "jks", "p12",
        "list", "iml", "css", "js", "ts", "gitignore", "gitkeep", "grantperm"};

    static boolean unrelatedExtension(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = n.substring(dot + 1);
        for (String u : UNRELATED) if (u.equals(ext)) return true;
        return false;
    }

    /** What a directory walk found. */
    static final class Walk {
        final List<File> policies = new ArrayList<File>();
        /** Regular files that are neither recognised policy names nor of an unrelated extension. */
        final List<File> unrecognised = new ArrayList<File>();
        /** Entries the walk could not read. */
        final List<String> unreadable = new ArrayList<String>();
    }

    private static Walk walk(File dir, final List<PathMatcher> extra) throws IOException {
        final Walk w = new Walk();
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                Path n = d.getFileName();
                if (n != null && n.toString().toLowerCase(Locale.ROOT).endsWith(".proxy-permissions")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                Path n = f.getFileName();
                if (n == null) return FileVisitResult.CONTINUE;
                boolean hit = builtInMatch(n.toString());
                for (int i = 0; !hit && i < extra.size(); i++) {
                    hit = extra.get(i).matches(n);
                }
                if (hit) w.policies.add(f.toFile());
                else if (!unrelatedExtension(n.toString())) w.unrecognised.add(f.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
                w.unreadable.add(f.toString() + " (" + e.getClass().getSimpleName() + ")");
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(w.policies);
        Collections.sort(w.unrecognised);
        return w;
    }

    /**
     * Whether an unrecognised file reads like a policy: the runtime loads any file named by
     * {@code java.security.policy}, so a name the walk does not know proves nothing.
     */
    private static boolean looksLikePolicy(File f) throws IOException {
        byte[] b = new byte[1 << 20];
        int n = 0;
        java.io.InputStream in = Files.newInputStream(f.toPath());
        try {
            for (int r; n < b.length && (r = in.read(b, n, b.length - n)) > 0;) n += r;
        } finally {
            in.close();
        }
        String t = new String(b, 0, n, "ISO-8859-1").toLowerCase(Locale.ROOT);
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
                    if (p.name != null && p.name.contains("${")) continue; // expanded by the parser
                    PermissionSets.Probe pr = PermissionSets.probe(p.type, p.name, p.actions);
                    if (pr != null) named.add(pr);
                }
            }
            answer = provider.ask(f, defines, principalClasses, named);
            if (answer.loadError != null) {
                // the text scan's own P1 already fails the run; one P1 per cause is enough
                if (!has(res, "P1")) {
                    res.add(new Finding(display, 0, "P1", "the production policy parser rejects"
                            + " this file: " + answer.loadError));
                }
            } else {
                if (answer.stranger && !has(res, "U1")) {
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
        return new Linted(res, answer);
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
                if (z > 0 && a.hit(PolicyVerdict.ZONE_STRANGER, h.probe.id)) continue;
                boolean isAll = "AllPermission".equals(h.probe.id);
                if (coveredByAllFiles(a, zones[z], h.probe.id)) continue;
                if (allHere && !isAll) continue;
                if (isAll && z <= 1) continue; // U3 and S3 report AllPermission to these subjects
                if (h.probe.severity >= PermissionSets.HIGH) high.add(h);
                else medium.add(h);
            }
            if (!high.isEmpty()) {
                res.add(new Finding(display, lineOf(grants, high), highKind[z], describe(high,
                        who[z], "dangerous permission")));
            }
            if (!medium.isEmpty()) {
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
            sb.append(hits.get(i).probe.id).append(" [").append(hits.get(i).probe.reason).append(']');
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
                    if (h.probe.permission.getClass().getName().equals(p.type)) return g.line;
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
                if (wild) {
                    res.add(new Finding(file, g.line, "S3", "AllPermission (line " + all.line
                            + ") scoped by a wildcard principal, which matches any authenticated"
                            + " subject"));
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
