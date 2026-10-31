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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds unscoped and weakly scoped {@code java.security.AllPermission} grants
 * in {@code java.policy} files.
 *
 * <p>polpAudit only ever adds permissions to the policy it starts from, so an
 * unscoped {@code grant { permission java.security.AllPermission; };} in the
 * starting or the deployed policy masks every recorded permission and defeats
 * least privilege for good.  A grant scoped only by {@code codeBase} is also
 * weak, because a codebase can be asserted remotely.  This tool reads policy
 * text only: it never executes, expands or resolves anything in it, and it
 * never writes a file.
 *
 * <p>Findings:
 * <ul>
 * <li>{@code U1 UNSCOPED_ALLPERMISSION} - a grant with no {@code codeBase},
 *     {@code signedBy}, {@code principal} or {@code digest} clause that holds
 *     {@code java.security.AllPermission};</li>
 * <li>{@code U2 UNSCOPED_META_GRANT} - an unscoped grant holding
 *     {@code net.jini.security.GrantPermission} whose target is or contains
 *     AllPermission or a wildcard implying it, or
 *     {@code UmbrellaGrantPermission} (which implies every GrantPermission);</li>
 * <li>{@code S1 ALLPERMISSION_CODEBASE_ONLY} - AllPermission scoped only by
 *     codeBase and/or signedBy;</li>
 * <li>{@code S2 ALLPERMISSION_SCOPED} - AllPermission scoped by a digest or
 *     principals (informational);</li>
 * <li>{@code P1 UNPARSEABLE} - a file or block the parser cannot read.</li>
 * </ul>
 *
 * <p>Exit status: 0 clean; 1 if any U1, U2 or P1 (also S1 with
 * {@code --strict}; any AllPermission finding including S2 with
 * {@code --audit-input}); 2 usage error.
 *
 * <p>Grant parsing is {@link ProxyPolicyGenerator#parseGrants(String, List)}.
 */
public final class PolicyLint {

    static final String ALL = PermissionSets.ALL_PERMISSION;
    private static final String GRANT_PERMISSION = PermissionSets.GRANT_PERMISSION;
    private static final String UMBRELLA_PERMISSION = PermissionSets.UMBRELLA_GRANT_PERMISSION;
    private static final String[] KINDS = {"U1", "U2", "S1", "S2", "P1"};

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
            if ("U1".equals(kind)) return "UNSCOPED_ALLPERMISSION";
            if ("U2".equals(kind)) return "UNSCOPED_META_GRANT";
            if ("S1".equals(kind)) return "ALLPERMISSION_CODEBASE_ONLY";
            if ("S2".equals(kind)) return "ALLPERMISSION_SCOPED";
            return "UNPARSEABLE";
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
        boolean strict = false, audit = false, tsv = false, hint = false;
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
            } else if (a.equals("-h") || a.equals("--help")) {
                usage(out);
                return 0;
            } else if (a.startsWith("--format=")) {
                String f = a.substring("--format=".length());
                if (f.equals("tsv")) tsv = true;
                else if (f.equals("text")) tsv = false;
                else return usageError(err, "unknown format: " + esc(f));
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
        for (String p : paths) {
            File f = new File(p);
            if (f.isDirectory()) {
                try {
                    walk(f, extra, files);
                } catch (IOException e) {
                    return usageError(err, "cannot walk " + esc(p) + ": " + esc(String.valueOf(e)));
                }
            } else if (f.isFile()) {
                files.add(f);
            } else {
                return usageError(err, "no such file or directory: " + esc(p));
            }
        }
        if (files.isEmpty()) return usageError(err, "no policy files found under the given paths");

        Map<String, List<Finding>> byFile = new TreeMap<String, List<Finding>>();
        for (File f : files) {
            String name = f.getPath();
            if (!byFile.containsKey(name)) byFile.put(name, lint(f, name));
        }

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
        for (Map.Entry<String, List<Finding>> e : byFile.entrySet()) {
            int[] n = new int[KINDS.length];
            for (Finding f : e.getValue()) n[indexOf(f.kind)]++;
            println(out, lead + " " + esc(e.getKey()) + ": " + counts(n));
        }
        boolean fail = total[indexOf("U1")] + total[indexOf("U2")] + total[indexOf("P1")] > 0
                || (strict && total[indexOf("S1")] > 0)
                || (audit && total[indexOf("S1")] + total[indexOf("S2")] > 0);
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
        println(out, "usage: PolicyLint [--strict] [--audit-input] [--format=text|tsv]"
                + " [--glob PATTERN]... [--fix-hint] FILE-OR-DIR...");
        println(out, "  exit 0 clean; 1 on U1, U2 or P1 (S1 with --strict; any AllPermission"
                + " finding with --audit-input); 2 usage error");
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
            if (i > 0) sb.append(' ');
            sb.append(KINDS[i]).append('=').append(n[i]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- walking

    static boolean builtInMatch(String name) {
        if (name.endsWith(".grantperm")) return false;
        return name.endsWith(".policy") || name.endsWith(".policy.old")
                || name.endsWith(".policy.new") || name.equals("policy.all")
                || name.contains(".policy.");
    }

    private static void walk(File dir, final List<PathMatcher> extra, final List<File> into)
            throws IOException {
        final List<File> found = new ArrayList<File>();
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                Path n = d.getFileName();
                if (n != null && n.toString().endsWith(".proxy-permissions")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                Path n = f.getFileName();
                boolean hit = n != null && builtInMatch(n.toString());
                for (int i = 0; !hit && n != null && i < extra.size(); i++) {
                    hit = extra.get(i).matches(n);
                }
                if (hit) found.add(f.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(found);
        into.addAll(found);
    }

    // ------------------------------------------------------------- linting

    static List<Finding> lint(File f, String display) {
        List<Finding> res = new ArrayList<Finding>();
        List<ProxyPolicyGenerator.Problem> problems =
                new ArrayList<ProxyPolicyGenerator.Problem>();
        List<ProxyPolicyGenerator.GrantBlock> grants;
        try {
            grants = ProxyPolicyGenerator.parseGrants(ProxyPolicyGenerator.readText(f), problems);
        } catch (IOException e) {
            res.add(new Finding(display, 0, "P1", "file cannot be read: " + e.getClass().getSimpleName()));
            return res;
        }
        for (ProxyPolicyGenerator.Problem p : problems) {
            res.add(new Finding(display, p.line, "P1", p.reason));
        }
        for (ProxyPolicyGenerator.GrantBlock g : grants) classify(display, g, res);
        Collections.sort(res, new Comparator<Finding>() {
            public int compare(Finding a, Finding b) {
                if (a.line != b.line) return a.line < b.line ? -1 : 1;
                return indexOf(a.kind) - indexOf(b.kind);
            }
        });
        return res;
    }

    private static boolean present(String s) {
        return s != null && s.trim().length() > 0;
    }

    private static void classify(String file, ProxyPolicyGenerator.GrantBlock g,
            List<Finding> res) {
        boolean digest = present(g.digest);
        boolean principal = !g.principals.isEmpty();
        boolean codeScoped = present(g.codebase) || present(g.signedBy);
        boolean unscoped = !digest && !principal && !codeScoped;

        ProxyPolicyGenerator.Perm all = null;
        for (ProxyPolicyGenerator.Perm p : g.permissions) {
            if (ALL.equals(p.type)) {
                all = p;
                break;
            }
        }
        if (all != null) {
            if (unscoped) {
                res.add(new Finding(file, g.line, "U1", "grant with no codeBase, signedBy, principal"
                        + " or digest holds AllPermission (line " + all.line + "): masks every"
                        + " permission polpAudit would record"));
            } else if (digest || principal) {
                res.add(new Finding(file, g.line, "S2", "AllPermission (line " + all.line
                        + ") scoped by " + (digest ? "digest" : "principal")
                        + (digest && principal ? " and principal" : "")));
            } else {
                res.add(new Finding(file, g.line, "S1", "AllPermission (line " + all.line
                        + ") scoped only by codeBase/signedBy ["
                        + (present(g.codebase) ? "codeBase " + g.codebase : "signedBy " + g.signedBy)
                        + "], which can be asserted remotely"));
            }
        }
        if (unscoped) {
            for (ProxyPolicyGenerator.Perm p : g.permissions) {
                if (UMBRELLA_PERMISSION.equals(p.type)) {
                    res.add(new Finding(file, g.line, "U2", "unscoped grant holds"
                            + " UmbrellaGrantPermission (line " + p.line
                            + "), which implies every GrantPermission"));
                } else if (GRANT_PERMISSION.equals(p.type) && grantTargetImpliesAll(p.name)) {
                    res.add(new Finding(file, g.line, "U2", "unscoped grant holds GrantPermission"
                            + " (line " + p.line + ") whose target is or implies AllPermission: "
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
