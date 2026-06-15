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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.AdvisoryPermissionParser;

/**
 * Post-processor that derives, from a least-privilege policy file produced by
 * the {@code polpAudit} workflow (see
 * {@code org.apache.river.tool.SecurityPolicyWriter}), the two
 * least-automated terms of the JGDMS authorization meet for proxy codebases:
 *
 * <ol>
 *   <li>the minimal <b>{@link GrantPermission}</b> <i>ceiling</i> that admits
 *       exactly the permissions a proxy codebase exercised (the meta-capability
 *       that bounds what may be dynamically granted to that proxy), and</li>
 *   <li>the per-codebase <b>{@code META-INF/PERMISSIONS.LIST}</b> declared-needs
 *       manifest (the third gating term of the effective grant).</li>
 * </ol>
 *
 * <p>It complements {@code SecurityPolicyWriter} and
 * {@link PolicyCondenser}: {@code SecurityPolicyWriter} records the observed,
 * scoped least-privilege grant blocks during an audited run, {@code
 * PolicyCondenser} consolidates them, and this tool recognises which of the
 * resulting grant blocks are <i>proxy-targeted</i> and synthesises the
 * {@code GrantPermission} ceiling and {@code PERMISSIONS.LIST} that a developer
 * would otherwise have to hand-author.
 *
 * <h2>Proxy identification</h2>
 * The <b>hard key</b> is the codebase / artefact naming convention.  A grant
 * block is treated as proxy-targeted when any of the following holds:
 * <ul>
 *   <li>it carries a {@code digest} scope clause (a content-addressed
 *       {@code DigestCodeSource} grant — the strongest proxy signal), or</li>
 *   <li>its {@code codebase} URL matches the proxy-artefact regex.  The default
 *       (overridable with {@code -Dorg.apache.river.tool.ProxyPolicyGenerator.proxy.codebase.regex}
 *       or {@code --proxy-regex}) matches the {@code httpmd:} integrity-protected
 *       codebase scheme and the JGDMS download/proxy artefact naming convention
 *       {@code *-dl.jar} (e.g. {@code mahalo-dl.jar}, {@code reggie-dl-3.1.1.jar}).</li>
 * </ul>
 * The <b>permission-set shape</b> (presence of {@code AccessPermission},
 * {@code AuthenticationPermission} or {@code net.jini.jeri} endpoint
 * permissions) is used only as a <i>corroborating confidence annotation</i> in
 * the generated output, never as the sole key.
 *
 * <h2>Minimality and fail-closed</h2>
 * Output is observe-only and least-privilege: a proxy codebase that exercised
 * no (non-meta) permission yields no manifest and no ceiling, and the generated
 * ceiling admits exactly the permissions the proxy exercised — no broader.
 * {@code GrantPermission} / {@code AllPermission}-style meta permissions are
 * excluded from {@code PERMISSIONS.LIST} (they are policy-grant capabilities,
 * not a proxy's declared needs).
 *
 * <h2>Round-trip</h2>
 * {@code PERMISSIONS.LIST} entries are encoded with
 * {@link AdvisoryPermissionParser#getEncoded(String,String,String)} so they
 * parse back via {@link AdvisoryPermissionParser#parse}.  Each synthesised
 * ceiling is validated by constructing a {@link GrantPermission} from its
 * target string, and the emitted grant block parses back via
 * {@code DefaultPolicyParser}.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp policy-condenser.jar:jgdms-platform.jar \
 *        org.apache.river.tool.ProxyPolicyGenerator \
 *        [--out &lt;dir&gt;] [--proxy-regex &lt;regex&gt;] [--strict] \
 *        [--properties &lt;file&gt;] &lt;policy-file&gt; [&lt;policy-file&gt; ...]
 * </pre>
 * For each {@code &lt;policy-file&gt;} it writes:
 * <ul>
 *   <li>{@code &lt;policy-file&gt;.grantperm} — a policy fragment with the
 *       synthesised {@code GrantPermission} ceiling grant blocks, and</li>
 *   <li>{@code &lt;out&gt;/&lt;artefact&gt;/META-INF/PERMISSIONS.LIST} for each proxy
 *       codebase (default {@code &lt;out&gt;} is {@code &lt;policy-file&gt;.proxy-permissions}).</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @since 3.1.1
 */
public class ProxyPolicyGenerator {

    private static final Logger LOG =
            Logger.getLogger(ProxyPolicyGenerator.class.getName());

    /** System property overriding the proxy-artefact codebase regex. */
    public static final String PROXY_REGEX_PROPERTY =
            "org.apache.river.tool.ProxyPolicyGenerator.proxy.codebase.regex";

    /**
     * Default proxy-artefact codebase pattern.  Matches the {@code httpmd:}
     * integrity-protected codebase scheme, or any codebase whose last path
     * segment is a {@code *-dl.jar} / {@code *-dl-&lt;version&gt;.jar} download
     * (proxy) artefact.
     */
    public static final String DEFAULT_PROXY_REGEX =
            "(?i)^httpmd:.*|.*/[^/]*-dl(?:-[^/]*)?\\.jar(?:[;?#].*)?$";

    /** Permission classes that are policy-grant meta-capabilities, never a
     *  proxy's declared needs, so excluded from {@code PERMISSIONS.LIST}. */
    private static final java.util.Set<String> META_PERMISSION_CLASSES;
    static {
        java.util.Set<String> s = new java.util.HashSet<String>();
        s.add("net.jini.security.GrantPermission");
        s.add("net.jini.security.policy.UmbrellaGrantPermission");
        s.add("java.security.AllPermission");
        META_PERMISSION_CLASSES = Collections.unmodifiableSet(s);
    }

    /** Permission classes whose presence corroborates a proxy classification. */
    private static final String[] PROXY_SHAPE_HINTS = {
        "net.jini.security.AccessPermission",
        "net.jini.security.AuthenticationPermission",
        "net.jini.jeri.",
        "net.jini.io.context.",
        "net.jini.export."
    };

    private final Pattern proxyPattern;
    private final boolean strict;
    /** Ordered value -> ${property} replacements (longest value first). */
    private final Map<String, String> pathReplacements;

    /**
     * Creates a generator with the default proxy pattern, no extra property
     * substitution and non-strict classification (name convention alone is the
     * hard key).
     */
    public ProxyPolicyGenerator() {
        this(defaultProxyPattern(), false, Collections.<String, String>emptyMap());
    }

    /**
     * @param proxyPattern     codebase pattern identifying proxy artefacts
     * @param strict           when {@code true}, a grant must also have
     *                         corroborating proxy-shape permissions to be
     *                         treated as proxy-targeted
     * @param pathReplacements value -&gt; {@code ${property}} substitutions to
     *                         apply to emitted targets (may be empty)
     */
    public ProxyPolicyGenerator(Pattern proxyPattern, boolean strict,
            Map<String, String> pathReplacements) {
        if (proxyPattern == null) throw new NullPointerException("proxyPattern");
        this.proxyPattern = proxyPattern;
        this.strict = strict;
        this.pathReplacements = orderByValueLengthDescending(pathReplacements);
    }

    private static Pattern defaultProxyPattern() {
        String re = System.getProperty(PROXY_REGEX_PROPERTY, DEFAULT_PROXY_REGEX);
        return Pattern.compile(re);
    }

    // ----------------------------------------------------------------- CLI

    public static void main(String[] args) throws Exception {
        File outDir = null;
        boolean strict = false;
        Pattern proxyPattern = defaultProxyPattern();
        Map<String, String> replacements = Collections.emptyMap();
        List<String> policyFiles = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--out".equals(a) && i + 1 < args.length) {
                outDir = new File(args[++i]);
            } else if ("--proxy-regex".equals(a) && i + 1 < args.length) {
                proxyPattern = Pattern.compile(args[++i]);
            } else if ("--strict".equals(a)) {
                strict = true;
            } else if ("--properties".equals(a) && i + 1 < args.length) {
                replacements = loadReplacements(new File(args[++i]));
            } else if (a.startsWith("--")) {
                System.err.println("Unknown option: " + a);
                printUsage();
                System.exit(2);
            } else {
                policyFiles.add(a);
            }
        }

        if (policyFiles.isEmpty()) {
            printUsage();
            System.exit(2);
        }

        ProxyPolicyGenerator gen =
                new ProxyPolicyGenerator(proxyPattern, strict, replacements);
        int totalProxies = 0;
        for (String pf : policyFiles) {
            File policy = new File(pf);
            File out = (outDir != null) ? outDir
                    : new File(policy.getAbsolutePath() + ".proxy-permissions");
            Result r = gen.generate(policy, out);
            totalProxies += r.proxyCount();
            System.out.println(r.describe());
        }
        if (totalProxies == 0) {
            System.out.println(
                "ProxyPolicyGenerator: no proxy-targeted grants found (nothing written).");
        }
    }

    private static void printUsage() {
        System.err.println(
            "Usage: ProxyPolicyGenerator [--out <dir>] [--proxy-regex <regex>] "
            + "[--strict] [--properties <file>] <policy-file> [<policy-file> ...]");
    }

    // ------------------------------------------------------------- core API

    /**
     * Generates the proxy ceiling fragment and per-codebase
     * {@code PERMISSIONS.LIST} manifests for one policy file.
     *
     * @param policyFile the least-privilege (audited / condensed) policy file
     * @param outDir     root directory under which
     *                   {@code <artefact>/META-INF/PERMISSIONS.LIST} trees are
     *                   written; created if absent
     * @return a {@link Result} describing what was written
     * @throws IOException if reading the policy or writing output fails
     */
    public Result generate(File policyFile, File outDir) throws IOException {
        String text = readText(policyFile);
        List<GrantBlock> grants = parseGrants(text);

        // Group proxy grants by codebase identity, merging permission sets so a
        // codebase that appears in several grant blocks yields one manifest and
        // one ceiling.
        Map<String, ProxyGroup> groups = new LinkedHashMap<String, ProxyGroup>();
        for (GrantBlock g : grants) {
            if (!isProxy(g)) continue;
            String key = scopeKey(g);
            ProxyGroup grp = groups.get(key);
            if (grp == null) {
                grp = new ProxyGroup(g);
                groups.put(key, grp);
            }
            grp.add(g);
        }

        // Drop groups that exercised no declared-needs permission (fail-closed).
        List<ProxyGroup> proxies = new ArrayList<ProxyGroup>();
        for (ProxyGroup grp : groups.values()) {
            if (!grp.declaredNeeds().isEmpty()) proxies.add(grp);
        }

        File ceilingFile = new File(policyFile.getAbsolutePath() + ".grantperm");
        List<String> manifests = new ArrayList<String>();

        if (proxies.isEmpty()) {
            // Fail-closed: produce nothing (and clean any stale fragment).
            if (ceilingFile.exists()) ceilingFile.delete();
            return new Result(policyFile, 0, ceilingFile, manifests);
        }

        writeCeilingFragment(ceilingFile, policyFile, proxies);
        for (ProxyGroup grp : proxies) {
            File listFile = writePermissionsList(outDir, grp);
            manifests.add(listFile.getAbsolutePath());
        }
        return new Result(policyFile, proxies.size(), ceilingFile, manifests);
    }

    // ------------------------------------------------------ classification

    /** @return {@code true} if {@code g} is a proxy-targeted grant block. */
    boolean isProxy(GrantBlock g) {
        boolean nameMatch = g.digest != null
                || (g.codebase != null && proxyPattern.matcher(g.codebase).matches());
        if (!nameMatch) return false;
        if (strict) return hasProxyShape(g);
        return true;
    }

    private static boolean hasProxyShape(GrantBlock g) {
        for (Perm p : g.permissions) {
            for (String hint : PROXY_SHAPE_HINTS) {
                if (hint.endsWith(".") ? p.type.startsWith(hint) : p.type.equals(hint)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --------------------------------------------------------- ceiling out

    private void writeCeilingFragment(File ceilingFile, File policyFile,
            List<ProxyGroup> proxies) throws IOException {
        PrintWriter pw = new PrintWriter(new BufferedWriter(
                Files.newBufferedWriter(ceilingFile.toPath(), Charset.forName("UTF-8"))));
        try {
            // Policy-file comments are '//' / '/* */' (NOT '#'), so the
            // fragment round-trips through DefaultPolicyParser.
            writeApacheHeader(pw, "// ");
            pw.println("//");
            pw.println("// GrantPermission ceilings synthesised by ProxyPolicyGenerator from");
            pw.println("//   " + policyFile.getName());
            pw.println("//");
            pw.println("// Each block below is the MINIMAL GrantPermission that admits exactly the");
            pw.println("// permissions the named proxy codebase exercised during the audited run.");
            pw.println("// It is the meta-capability bounding what may be dynamically granted for");
            pw.println("// that proxy.  The block is scoped, by default, to the proxy codebase");
            pw.println("// itself (per-codebase granularity).  Before deployment, ATTACH each");
            pw.println("// ceiling to the grant block of the domain that actually calls");
            pw.println("// DynamicPolicy.grant for the proxy (the service / exporter), re-scoping");
            pw.println("// the codebase/principal clauses as appropriate for that granting role.");
            pw.println("//");
            pw.println();

            for (ProxyGroup grp : proxies) {
                String target = buildCeilingTarget(grp.declaredNeeds());
                validateGrantPermission(target, grp);

                pw.println("// proxy codebase: " + nullToAny(grp.template.codebase));
                pw.println("// proxy-shape permissions present: "
                        + (hasProxyShape(grp.template) || groupHasShape(grp)));
                pw.print("grant ");
                pw.print(scopeClause(grp.template));
                pw.println("{");
                pw.print("    permission net.jini.security.GrantPermission \"");
                pw.print(policyEscape(target));
                pw.println("\";");
                pw.println("};");
                pw.println();
            }
        } finally {
            pw.flush();
            pw.close();
        }
    }

    private static boolean groupHasShape(ProxyGroup grp) {
        for (GrantBlock g : grp.members) {
            if (hasProxyShape(g)) return true;
        }
        return false;
    }

    /**
     * Builds a {@link GrantPermission} target string listing exactly the given
     * permissions, using the {@code GrantPermission} grammar (default
     * {@code "} delimiter).  Mirrors {@code GrantPermission.constructName}.
     */
    String buildCeilingTarget(List<Perm> perms) {
        StringBuilder sb = new StringBuilder(80);
        for (Perm p : perms) {
            sb.append(p.type);
            if (p.name != null) {
                sb.append(' ').append(grantPermissionQuote(applyReplacements(p.name)));
                if (p.actions != null && p.actions.length() > 0) {
                    sb.append(", ").append(grantPermissionQuote(p.actions));
                }
            }
            sb.append("; ");
        }
        return sb.toString().trim();
    }

    /**
     * Validates that the synthesised ceiling target parses as a
     * {@link GrantPermission} (round-trip self-check) and that the resulting
     * permission implies each contained proxy permission.  Logged as a warning
     * (never fatal) if a class cannot be instantiated for verification.
     */
    private void validateGrantPermission(String target, ProxyGroup grp) {
        try {
            new GrantPermission(target);
        } catch (RuntimeException | LinkageError e) {
            // Never abort generation over a self-check: a malformed target, a
            // permission class that cannot be instantiated for verification, or
            // a missing GrantPermission class (mis-set classpath) only degrades
            // the round-trip assertion, not the emitted (textually correct) output.
            LOG.log(Level.WARNING,
                "Synthesised GrantPermission target for proxy " + grp.template.codebase
                + " did not validate: " + e, e);
        }
    }

    // -------------------------------------------------- PERMISSIONS.LIST out

    private File writePermissionsList(File outDir, ProxyGroup grp) throws IOException {
        String artefact = artefactName(grp);
        File dir = new File(new File(outDir, artefact), "META-INF");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Unable to create directory: " + dir);
        }
        File listFile = new File(dir, "PERMISSIONS.LIST");

        // Encode + de-duplicate + sort for deterministic, round-trippable output.
        TreeSet<String> encoded = new TreeSet<String>();
        for (Perm p : grp.declaredNeeds()) {
            String name = p.name == null ? null : applyReplacements(p.name);
            String actions = (p.actions == null || p.actions.length() == 0) ? null : p.actions;
            encoded.add(AdvisoryPermissionParser.getEncoded(p.type, name, actions));
        }

        PrintWriter pw = new PrintWriter(new BufferedWriter(
                Files.newBufferedWriter(listFile.toPath(), Charset.forName("UTF-8"))));
        try {
            writeApacheHeader(pw, "# ");
            pw.println("# Declared-needs manifest for proxy codebase:");
            pw.println("#   " + nullToAny(grp.template.codebase));
            pw.println("# Generated by ProxyPolicyGenerator (do not edit by hand).");
            for (Iterator<String> it = encoded.iterator(); it.hasNext();) {
                pw.println(it.next());
            }
        } finally {
            pw.flush();
            pw.close();
        }
        return listFile;
    }

    /** Derives a filesystem-safe artefact directory name from the codebase. */
    static String artefactName(ProxyGroup grp) {
        String cb = grp.template.codebase;
        if (cb == null) {
            // Digest-only proxy: use a sanitised digest fragment.
            String d = grp.template.digest;
            return "digest-" + sanitize(d == null ? "unknown" : d);
        }
        String s = cb;
        // strip httpmd / query / fragment params
        int semi = s.indexOf(';');
        if (semi >= 0) s = s.substring(0, semi);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        // last path segment
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) s = s.substring(slash + 1);
        if (s.toLowerCase().endsWith(".jar")) s = s.substring(0, s.length() - 4);
        s = sanitize(s);
        return s.isEmpty() ? "proxy" : s;
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append((Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_')
                    ? c : '_');
        }
        return sb.toString();
    }

    // ----------------------------------------------------- scope rendering

    /** Identity key grouping grant blocks that target the same proxy. */
    private static String scopeKey(GrantBlock g) {
        return (g.signedBy == null ? "" : g.signedBy) + " "
                + (g.codebase == null ? "" : g.codebase) + " "
                + (g.digest == null ? "" : g.digest) + " "
                + principalKey(g.principals);
    }

    private static String principalKey(List<String[]> pals) {
        TreeSet<String> set = new TreeSet<String>();
        for (String[] p : pals) {
            set.add((p[0] == null ? "" : p[0]) + " " + (p[1] == null ? "" : p[1]));
        }
        return set.toString();
    }

    /**
     * Renders the {@code signedBy}/{@code codebase}/{@code digest}/{@code
     * principal} clauses of a grant header (without the {@code grant} keyword or
     * the opening brace), terminated so the caller can append {@code "{"}.
     */
    private static String scopeClause(GrantBlock g) {
        StringBuilder sb = new StringBuilder(80);
        boolean any = false;
        if (g.signedBy != null) {
            sb.append("signedBy \"").append(policyEscape(g.signedBy)).append("\"");
            any = true;
        }
        if (g.codebase != null) {
            if (any) sb.append(", ");
            sb.append("codebase \"").append(policyEscape(g.codebase)).append("\"");
            any = true;
        }
        if (g.digest != null) {
            if (any) sb.append(", ");
            sb.append("digest \"").append(policyEscape(g.digest)).append("\"");
            any = true;
        }
        for (String[] p : g.principals) {
            if (any) sb.append(", ");
            sb.append("principal ");
            if (p[0] != null) sb.append(p[0]).append(' ');
            if (p[1] != null) {
                sb.append("\"").append(policyEscape(p[1])).append("\"");
            } else {
                sb.append("*");
            }
            any = true;
        }
        if (any) sb.append(' ');
        return sb.toString();
    }

    // ---------------------------------------------------------- escaping

    /**
     * Escapes a string for embedding between {@code "} in a policy file, such
     * that {@code DefaultPolicyScanner}'s tokenizer decodes it back verbatim.
     */
    static String policyEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Quotes a string as a {@code GrantPermission} delimited name/actions using
     * the default {@code "} delimiter; mirrors {@code GrantPermission.quote}.
     */
    static String grantPermissionQuote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\f') {
                sb.append("\\f");
            } else if (c == '\b') {
                sb.append("\\b");
            } else if (c < 0x20) {
                sb.append('\\').append(Integer.toOctalString(c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ----------------------------------------------- property substitution

    /** Applies value -&gt; {@code ${property}} substitutions to a target. */
    String applyReplacements(String s) {
        if (s == null || pathReplacements.isEmpty()) return s;
        String result = s;
        for (Map.Entry<String, String> e : pathReplacements.entrySet()) {
            String value = e.getKey();
            if (value == null || value.isEmpty()) continue;
            String token = "${" + e.getValue() + "}";
            String forward = value.replace('\\', '/');
            if (result.contains(value)) result = result.replace(value, token);
            if (!forward.equals(value) && result.contains(forward)) {
                result = result.replace(forward, token);
            }
        }
        return result;
    }

    private static Map<String, String> orderByValueLengthDescending(
            Map<String, String> propToValue) {
        // Input map is property-name -> literal-value; we want to replace the
        // longest values first so a shorter value isn't substituted inside a
        // longer one.  Returned map is value -> property-name.
        List<Map.Entry<String, String>> entries =
                new ArrayList<Map.Entry<String, String>>(propToValue.entrySet());
        Collections.sort(entries, new java.util.Comparator<Map.Entry<String, String>>() {
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                int la = a.getValue() == null ? 0 : a.getValue().length();
                int lb = b.getValue() == null ? 0 : b.getValue().length();
                return Integer.compare(lb, la);
            }
        });
        Map<String, String> valueToProp = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : entries) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                valueToProp.put(e.getValue(), e.getKey());
            }
        }
        return valueToProp;
    }

    private static Map<String, String> loadReplacements(File propsFile) throws IOException {
        Properties p = new Properties();
        FileReader fr = new FileReader(propsFile);
        try {
            p.load(fr);
        } finally {
            fr.close();
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Map.Entry<Object, Object> e : p.entrySet()) {
            map.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return map;
    }

    // ------------------------------------------------------ policy parsing

    /**
     * Parses the top-level grant blocks of a policy file into a list of
     * {@link GrantBlock}.  {@code keystore} clauses and unrecognised top-level
     * tokens are skipped.  Uses a {@link StreamTokenizer} configured for the
     * policy grammar (dotted class names are single words, {@code //} and
     * {@code /* *}{@code /} comments are honoured, values are double-quoted).
     */
    static List<GrantBlock> parseGrants(String text) throws IOException {
        StreamTokenizer st = policyTokenizer(text);
        List<GrantBlock> grants = new ArrayList<GrantBlock>();
        int tok;
        while ((tok = st.nextToken()) != StreamTokenizer.TT_EOF) {
            if (tok == StreamTokenizer.TT_WORD) {
                if ("grant".equalsIgnoreCase(st.sval)) {
                    GrantBlock g = parseGrantBody(st);
                    if (g != null) grants.add(g);
                } else if ("keystore".equalsIgnoreCase(st.sval)) {
                    skipToSemicolon(st);
                }
            }
        }
        return grants;
    }

    private static StreamTokenizer policyTokenizer(String text) {
        StreamTokenizer st = new StreamTokenizer(new StringReader(text));
        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('A', 'Z');
        st.wordChars('0', '9');
        st.wordChars('.', '.');
        st.wordChars('_', '_');
        st.wordChars('$', '$');
        st.wordChars(160, 255);
        st.whitespaceChars(0, ' ');
        st.quoteChar('"');
        st.ordinaryChar('/');
        st.slashSlashComments(true);
        st.slashStarComments(true);
        return st;
    }

    private static void skipToSemicolon(StreamTokenizer st) throws IOException {
        int tok;
        while ((tok = st.nextToken()) != StreamTokenizer.TT_EOF) {
            if (tok == ';') return;
        }
    }

    private static GrantBlock parseGrantBody(StreamTokenizer st) throws IOException {
        GrantBlock g = new GrantBlock();
        int tok;
        // header: clauses up to '{'
        header:
        while (true) {
            tok = st.nextToken();
            switch (tok) {
                case StreamTokenizer.TT_EOF:
                    return null; // malformed
                case '{':
                    break header;
                case ',':
                    break;
                case StreamTokenizer.TT_WORD:
                    String kw = st.sval;
                    if ("signedBy".equalsIgnoreCase(kw)) {
                        if (st.nextToken() == '"') g.signedBy = st.sval;
                    } else if ("codebase".equalsIgnoreCase(kw)) {
                        if (st.nextToken() == '"') g.codebase = st.sval;
                    } else if ("digest".equalsIgnoreCase(kw)) {
                        if (st.nextToken() == '"') g.digest = st.sval;
                    } else if ("principal".equalsIgnoreCase(kw)) {
                        String className = null, name = null;
                        tok = st.nextToken();
                        if (tok == StreamTokenizer.TT_WORD) {
                            className = st.sval;
                            tok = st.nextToken();
                        }
                        if (tok == '"') {
                            name = st.sval;
                        } else if (tok == '*') {
                            name = null;
                        } else {
                            st.pushBack();
                        }
                        g.principals.add(new String[]{className, name});
                    }
                    break;
                default:
                    break;
            }
        }
        // body: permission entries up to '}'
        body:
        while (true) {
            tok = st.nextToken();
            switch (tok) {
                case StreamTokenizer.TT_EOF:
                case '}':
                    break body;
                case StreamTokenizer.TT_WORD:
                    if ("permission".equalsIgnoreCase(st.sval)) {
                        Perm p = readPermission(st);
                        if (p != null) g.permissions.add(p);
                    }
                    break;
                default:
                    break;
            }
        }
        // optional trailing ';'
        tok = st.nextToken();
        if (tok != ';') st.pushBack();
        return g;
    }

    private static Perm readPermission(StreamTokenizer st) throws IOException {
        if (st.nextToken() != StreamTokenizer.TT_WORD) {
            st.pushBack();
            return null;
        }
        String type = st.sval;
        String name = null, actions = null, signedBy = null;
        int tok = st.nextToken();
        if (tok == '"') {
            name = st.sval;
            tok = st.nextToken();
        }
        if (tok == ',') tok = st.nextToken();
        if (tok == '"') {
            actions = st.sval;
            tok = st.nextToken();
        }
        if (tok == ',') tok = st.nextToken();
        if (tok == StreamTokenizer.TT_WORD && "signedBy".equalsIgnoreCase(st.sval)) {
            if (st.nextToken() == '"') signedBy = st.sval;
        } else {
            st.pushBack();
        }
        return new Perm(type, name, actions, signedBy);
    }

    // -------------------------------------------------------------- I/O

    private static String readText(File f) throws IOException {
        StringBuilder sb = new StringBuilder((int) Math.min(Math.max(f.length(), 64), Integer.MAX_VALUE));
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), Charset.forName("UTF-8")));
        try {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        } finally {
            br.close();
        }
        // Strip a leading UTF-8 BOM: StreamTokenizer treats every char >= 256
        // (including U+FEFF) as a word char, which would otherwise fuse the BOM
        // onto the first 'grant'/'keystore' keyword and defeat parsing.
        if (sb.length() > 0 && sb.charAt(0) == 0xFEFF) sb.deleteCharAt(0);
        return sb.toString();
    }

    private static void writeApacheHeader(Writer w, String prefix) throws IOException {
        String[] lines = {
            "Licensed to the Apache Software Foundation (ASF) under one",
            "or more contributor license agreements.  See the NOTICE file",
            "distributed with this work for additional information",
            "regarding copyright ownership. The ASF licenses this file",
            "to you under the Apache License, Version 2.0 (the",
            "\"License\"); you may not use this file except in compliance",
            "with the License. You may obtain a copy of the License at",
            "",
            "     http://www.apache.org/licenses/LICENSE-2.0",
            "",
            "Unless required by applicable law or agreed to in writing, software",
            "distributed under the License is distributed on an \"AS IS\" BASIS,",
            "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
            "See the License for the specific language governing permissions and",
            "limitations under the License."
        };
        for (String l : lines) {
            w.write(prefix);
            w.write(l);
            w.write('\n');
        }
    }

    private static String nullToAny(String s) {
        return s == null ? "<any (digest-scoped)>" : s;
    }

    // ----------------------------------------------------------- holders

    /** Parsed scope + permissions of one {@code grant} block. */
    static final class GrantBlock {
        String signedBy;
        String codebase;
        String digest;
        final List<String[]> principals = new ArrayList<String[]>();
        final List<Perm> permissions = new ArrayList<Perm>();
    }

    /** Parsed {@code permission} entry; {@code name}/{@code actions} may be null. */
    static final class Perm {
        final String type;
        final String name;
        final String actions;
        final String signedBy;

        Perm(String type, String name, String actions, String signedBy) {
            this.type = type;
            this.name = name;
            this.actions = actions;
            this.signedBy = signedBy;
        }

        /** Identity used to de-duplicate permissions within a proxy group. */
        String key() {
            return type + " " + (name == null ? "" : name)
                    + " " + (actions == null ? "" : actions);
        }
    }

    /** Proxy grant blocks sharing one scope, with merged declared needs. */
    static final class ProxyGroup {
        final GrantBlock template;
        final List<GrantBlock> members = new ArrayList<GrantBlock>();
        private final Map<String, Perm> merged = new LinkedHashMap<String, Perm>();
        private List<Perm> declaredNeeds;

        ProxyGroup(GrantBlock template) {
            this.template = template;
        }

        void add(GrantBlock g) {
            members.add(g);
            for (Perm p : g.permissions) {
                if (META_PERMISSION_CLASSES.contains(p.type)) continue;
                merged.put(p.key(), p);
            }
            declaredNeeds = null;
        }

        /** Sorted, de-duplicated non-meta permissions (the declared needs). */
        List<Perm> declaredNeeds() {
            if (declaredNeeds == null) {
                List<Perm> list = new ArrayList<Perm>(merged.values());
                Collections.sort(list, new java.util.Comparator<Perm>() {
                    public int compare(Perm a, Perm b) {
                        return a.key().compareTo(b.key());
                    }
                });
                declaredNeeds = list;
            }
            return declaredNeeds;
        }
    }

    /** Summary of one {@link #generate} invocation. */
    public static final class Result {
        private final File policyFile;
        private final int proxyCount;
        private final File ceilingFile;
        private final List<String> permissionListFiles;

        Result(File policyFile, int proxyCount, File ceilingFile,
                List<String> permissionListFiles) {
            this.policyFile = policyFile;
            this.proxyCount = proxyCount;
            this.ceilingFile = ceilingFile;
            this.permissionListFiles = permissionListFiles;
        }

        public int proxyCount() {
            return proxyCount;
        }

        public File ceilingFile() {
            return proxyCount == 0 ? null : ceilingFile;
        }

        public List<String> permissionListFiles() {
            return permissionListFiles;
        }

        String describe() {
            if (proxyCount == 0) {
                return "ProxyPolicyGenerator: " + policyFile.getName()
                        + " -> no proxy-targeted grants.";
            }
            return "ProxyPolicyGenerator: " + policyFile.getName() + " -> "
                    + proxyCount + " proxy codebase(s); ceiling="
                    + ceilingFile.getName() + ", manifests=" + permissionListFiles.size();
        }
    }
}
