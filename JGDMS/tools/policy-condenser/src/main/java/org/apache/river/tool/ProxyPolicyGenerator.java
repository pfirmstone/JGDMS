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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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
 * <h2>PERMISSIONS.LIST safety</h2>
 * A generated list is a <i>request</i> made by the proxy jar, so it is held to
 * what a reviewer can promote:
 * <ul>
 *   <li>admission-gate permissions ({@code LoadClassPermission},
 *       {@code URLPermission}, {@code DownloadPermission}) are never emitted:
 *       the proxy provider grants them after verifying the codebase, and a jar
 *       must not request its own admission.  They stay in the ceiling.</li>
 *   <li>entries tied to the audit host or run (absolute paths, {@code file:}
 *       URLs, {@code jdk.proxyN.$ProxyM} class names, unexpanded {@code ${...}}
 *       placeholders) are written commented out with a reason, or fail the run
 *       under {@code --fail-on-held}.</li>
 *   <li>a group left with no live entry gets no list: an empty list requests
 *       the same as none, so it is skipped and reported ({@code --fail-on-empty}
 *       makes it a failure).</li>
 *   <li>the header records the source policy and its SHA-256, generator version
 *       and date, and the grant digest; a directory holding a list this run
 *       does not replace and that came from a different source policy, or
 *       from the same file name with different content, is refused unless
 *       {@code --clean}, which deletes only lists carrying that header.</li>
 *   <li>a control character in any value that reaches the header or an entry
 *       fails the run: a newline there would start a live line.</li>
 *   <li>every written line is parsed back with
 *       {@link AdvisoryPermissionParser#parse}; the loader drops a jar's whole
 *       list silently on one bad line, so a bad line fails generation here.</li>
 *   <li>lists are staged and validated in a temporary directory beside
 *       {@code --out} and moved into place only when all of them validated.</li>
 * </ul>
 *
 * <h2>Promotion</h2>
 * {@code --promote-plan} and {@code --promote-apply} (see {@link ListPromotion})
 * move reviewed generated lists into the {@code -dl} modules' source trees in
 * two steps, the first of which changes nothing.  Apply re-derives the plan
 * from the same roots and the generated lists and trusts nothing in the plan
 * file; {@code --promote-plan-check} is the CI gate and writes nothing.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp policy-condenser.jar:jgdms-platform.jar \
 *        org.apache.river.tool.ProxyPolicyGenerator \
 *        [--out &lt;dir&gt;] [--proxy-regex &lt;regex&gt;] [--strict] [--clean] \
 *        [--fail-on-held] [--fail-on-empty] [--diff-against &lt;dir&gt;] \
 *        [--properties &lt;file&gt;] &lt;policy-file&gt; [&lt;policy-file&gt; ...]
 *
 *   ProxyPolicyGenerator --out &lt;dir&gt; [--plan-file &lt;file&gt;] [--artefact-map &lt;file&gt;] --promote-plan &lt;module-root&gt;...
 *   ProxyPolicyGenerator --out &lt;dir&gt; [--artefact-map &lt;file&gt;] --promote-plan-check &lt;module-root&gt;...
 *   ProxyPolicyGenerator --promote-apply &lt;plan-file&gt; --approve-plan-sha &lt;sha&gt; --out &lt;dir&gt; \
 *        [--artefact-map &lt;file&gt;] --module-roots &lt;module-root&gt;... \
 *        --approve &lt;artefact&gt;[,...] [--allow-removals] [--allow-held]
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

    /**
     * Admission-gate permission classes, never emitted into a proxy jar's
     * {@code PERMISSIONS.LIST}: they admit the jar's own code to be downloaded
     * and defined, and {@code PreferredProxyCodebaseProvider} grants them itself
     * once the codebase is verified, so a jar that requests them is requesting
     * its own admission.  The two {@code LoadClassPermission} names are the
     * DirtyChai class as recorded by {@code polpAudit} and the name the provider
     * uses for its unresolved grant.
     */
    private static final Set<String> GATE_PERMISSION_CLASSES =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "au.zeus.jdk.authorization.guards.LoadClassPermission",
            "net.jini.loader.LoadClassPermission",
            "java.net.URLPermission",
            "net.jini.loader.DownloadPermission")));

    /** Why a gate class is dropped; also the reason promotion refuses a list that holds one. */
    private static final String GATE_REASON = "admission-gate permission: the proxy provider grants "
        + "it after verifying the codebase, a jar must not request its own admission";

    /** Why a meta class never reaches a list. */
    private static final String META_REASON = "policy-grant meta-capability: never a proxy's"
        + " declared need";

    private static final String FILE_PERMISSION = "java.io.FilePermission";

    /** Absolute Windows drive or UNC path, optionally as a {@code file:} URL. */
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile(
            "(?i)(?:file:/{0,3})?(?:[a-z]:[/\\\\]|\\\\\\\\)");

    private static final Pattern FILE_URL = Pattern.compile("(?i)file:");

    /** {@code ${name.jar}}: a property named after the jar it holds. */
    private static final Pattern JAR_PROPERTY = Pattern.compile("\\$\\{([^${}/]+)\\.jar\\}");

    /** A JDK-generated proxy class name; the number depends on load order. */
    private static final Pattern GENERATED_PROXY_CLASS =
            Pattern.compile("jdk\\.proxy\\d+\\.\\$Proxy\\d+");

    /** First words of the comment that marks a ceiling fragment as this tool's. */
    private static final String CEILING_MARKER =
            "// GrantPermission ceilings synthesised by ProxyPolicyGenerator";

    /** Header line recording the policy a list was generated from. */
    private static final String SOURCE_POLICY_PREFIX = "# Source policy: ";

    /**
     * First words of the header line that lists carry from before provenance
     * existed: such a list was written by this tool, from an unrecorded policy.
     */
    private static final String OLD_HEADER_PREFIX = "# Generated by ProxyPolicyGenerator";

    /** Header line recording the SHA-256 of that policy file's bytes. */
    private static final String SOURCE_SHA_PREFIX = "# Source policy SHA-256: ";

    /** Version of the header/entry layout; lists without a provenance line are 1. */
    static final String LIST_FORMAT = "3";

    private static final Charset UTF8 = Charset.forName("UTF-8");

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
    private boolean clean;
    private boolean failOnHeld;
    private boolean failOnEmpty;
    private File diffAgainst;
    private Clock clock = Clock.systemDefaultZone();

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

    /**
     * Whether to delete every list already under the output directory before
     * writing, instead of refusing a directory that holds a list from a
     * different (or unrecorded) source policy.  Applied only when something is
     * to be written.
     */
    public ProxyPolicyGenerator clean(boolean clean) {
        this.clean = clean;
        return this;
    }

    /**
     * Whether a host- or run-specific entry (one that would be written as
     * {@code # HELD}) fails the run.  Independent of {@code strict}, which only
     * decides what counts as a proxy.
     */
    public ProxyPolicyGenerator failOnHeld(boolean failOnHeld) {
        this.failOnHeld = failOnHeld;
        return this;
    }

    /**
     * Whether a proxy group left with no live entry fails the run.  Off, such a
     * group gets no list and a {@code NO LIST WRITTEN} note.
     */
    public ProxyPolicyGenerator failOnEmpty(boolean failOnEmpty) {
        this.failOnEmpty = failOnEmpty;
        return this;
    }

    /**
     * Compares each generated list with the checked-in list of the same
     * artefact under {@code checkedInDir} and reports the added and removed
     * entries in {@link Result#diff()}.  The directory is only read.
     */
    public ProxyPolicyGenerator diffAgainst(File checkedInDir) {
        this.diffAgainst = checkedInDir;
        return this;
    }

    /** Test seam: the date written into the list header. */
    ProxyPolicyGenerator clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    private static Pattern defaultProxyPattern() {
        String re = System.getProperty(PROXY_REGEX_PROPERTY, DEFAULT_PROXY_REGEX);
        return Pattern.compile(re);
    }

    // ----------------------------------------------------------------- CLI

    public static void main(String[] args) throws Exception {
        int rc = run(args, System.out, System.err);
        if (rc != 0) System.exit(rc);
    }

    /**
     * Runs the command line.
     *
     * @return 0 on success, 1 if generation, planning or promotion was refused
     *         or failed, 2 on a usage error
     */
    static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
        try {
            return runChecked(args, out, err);
        } catch (java.nio.file.InvalidPathException e) {
            err.println("ProxyPolicyGenerator: REFUSED: a path argument is malformed: "
                    + escaped(e.getMessage()));
            return 1;
        }
    }

    private static int runChecked(String[] args, PrintStream out, PrintStream err)
            throws IOException {
        File outDir = null;
        File diffDir = null;
        File planFile = null;
        File applyPlan = null;
        File artefactMap = null;
        String approvePlanSha = null;
        boolean strict = false;
        boolean clean = false;
        boolean failOnHeld = false;
        boolean failOnEmpty = false;
        boolean allowRemovals = false;
        boolean allowHeld = false;
        boolean promotePlan = false;
        boolean promoteCheck = false;
        List<String> approve = new ArrayList<String>();
        List<File> moduleRoots = new ArrayList<File>();
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
            } else if ("--clean".equals(a)) {
                clean = true;
            } else if ("--fail-on-held".equals(a)) {
                failOnHeld = true;
            } else if ("--fail-on-empty".equals(a)) {
                failOnEmpty = true;
            } else if ("--diff-against".equals(a) && i + 1 < args.length) {
                diffDir = new File(args[++i]);
            } else if ("--properties".equals(a) && i + 1 < args.length) {
                replacements = loadReplacements(new File(args[++i]));
            } else if ("--plan-file".equals(a) && i + 1 < args.length) {
                planFile = new File(args[++i]);
            } else if ("--promote-plan".equals(a) || "--promote-plan-check".equals(a)
                    || "--module-roots".equals(a)) {
                if ("--promote-plan".equals(a)) promotePlan = true;
                if ("--promote-plan-check".equals(a)) promoteCheck = true;
                while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    for (String root : args[++i].split(Pattern.quote(File.pathSeparator))) {
                        if (!root.isEmpty()) moduleRoots.add(new File(root));
                    }
                }
            } else if ("--promote-apply".equals(a) && i + 1 < args.length) {
                applyPlan = new File(args[++i]);
            } else if ("--approve-plan-sha".equals(a) && i + 1 < args.length) {
                approvePlanSha = args[++i];
            } else if ("--artefact-map".equals(a) && i + 1 < args.length) {
                artefactMap = new File(args[++i]);
            } else if ("--allow-held".equals(a)) {
                allowHeld = true;
            } else if ("--approve".equals(a) && i + 1 < args.length) {
                for (String name : args[++i].split(",")) {
                    if (!name.trim().isEmpty()) approve.add(name.trim());
                }
            } else if ("--allow-removals".equals(a)) {
                allowRemovals = true;
            } else if (a.startsWith("--")) {
                err.println("Unknown option: " + a);
                printUsage(err);
                return 2;
            } else {
                policyFiles.add(a);
            }
        }

        if (promotePlan || promoteCheck || applyPlan != null) {
            Promotion p = new Promotion();
            p.plan = promotePlan;
            p.check = promoteCheck;
            p.roots = moduleRoots;
            p.outDir = outDir;
            p.planFile = planFile;
            p.applyPlan = applyPlan;
            p.approvePlanSha = approvePlanSha;
            p.artefactMap = artefactMap;
            p.approve = approve;
            p.allowRemovals = allowRemovals;
            p.allowHeld = allowHeld;
            p.policyFiles = policyFiles;
            return runPromotion(p, out, err);
        }
        if (policyFiles.isEmpty()) {
            printUsage(err);
            return 2;
        }

        ProxyPolicyGenerator gen =
                new ProxyPolicyGenerator(proxyPattern, strict, replacements)
                        .clean(clean).diffAgainst(diffDir)
                        .failOnHeld(failOnHeld).failOnEmpty(failOnEmpty);
        int rc = 0;
        int totalProxies = 0;
        for (String pf : policyFiles) {
            File policy = new File(pf);
            File dir = (outDir != null) ? outDir
                    : new File(policy.getAbsolutePath() + ".proxy-permissions");
            try {
                Result r = gen.generate(policy, dir);
                totalProxies += r.proxyCount();
                out.println(r.describe());
                for (String note : r.notes()) err.println(note);
                out.print(r.diff());
            } catch (GenerationException e) {
                err.println("ProxyPolicyGenerator: " + policy.getName()
                        + ": REFUSED: " + e.getMessage());
                rc = 1;
            }
        }
        if (totalProxies == 0 && rc == 0) {
            out.println(
                "ProxyPolicyGenerator: no proxy-targeted grants found (nothing written).");
        }
        return rc;
    }

    /** The arguments of the promotion modes. */
    private static final class Promotion {
        boolean plan;
        boolean check;
        List<File> roots;
        File outDir;
        File planFile;
        File applyPlan;
        String approvePlanSha;
        File artefactMap;
        List<String> approve;
        boolean allowRemovals;
        boolean allowHeld;
        List<String> policyFiles;
    }

    private static int runPromotion(Promotion p, PrintStream out, PrintStream err)
            throws IOException {
        boolean apply = p.applyPlan != null;
        int modes = (p.plan ? 1 : 0) + (p.check ? 1 : 0) + (apply ? 1 : 0);
        boolean usage = modes != 1 || !p.policyFiles.isEmpty() || p.outDir == null
                || p.roots.isEmpty()
                || (apply && (p.approve.isEmpty() || p.planFile != null))
                || (!apply && (!p.approve.isEmpty() || p.approvePlanSha != null
                    || p.allowRemovals || p.allowHeld))
                || (p.check && p.planFile != null);
        if (usage) {
            printUsage(err);
            return 2;
        }
        if (apply && p.approvePlanSha == null) {
            err.println("ProxyPolicyGenerator: --approve-plan-sha is required: the SHA-256 that"
                    + " --promote-plan printed for the plan you read.");
            printUsage(err);
            return 2;
        }
        try {
            Map<String, String> map = p.artefactMap == null
                    ? Collections.<String, String>emptyMap()
                    : ListPromotion.loadArtefactMap(p.artefactMap);
            if (p.plan) {
                File target = p.planFile != null ? p.planFile : new File(p.outDir, "promote.plan");
                ListPromotion.Plan plan = ListPromotion.plan(p.outDir, p.roots, map, target);
                out.print(escaped(plan.summary()));
                out.println("ProxyPolicyGenerator: plan written to " + target
                        + "; nothing under the module roots was changed.");
                out.println("plan sha256: " + sha256Hex(target)
                        + "  (pass as --approve-plan-sha to --promote-apply)");
            } else if (p.check) {
                ListPromotion.CheckResult r = ListPromotion.check(p.outDir, p.roots, map);
                out.print(escaped(r.report));
                return r.failed ? 1 : 0;
            } else {
                out.print(escaped(ListPromotion.apply(p.applyPlan, p.approvePlanSha, p.outDir,
                        p.roots, map, p.approve, p.allowRemovals, p.allowHeld)));
            }
            return 0;
        } catch (GenerationException e) {
            err.println("ProxyPolicyGenerator: REFUSED: " + escaped(e.getMessage()));
            return 1;
        }
    }

    private static void printUsage(PrintStream err) {
        err.println(
            "Usage: ProxyPolicyGenerator [--out <dir>] [--proxy-regex <regex>] "
            + "[--strict] [--clean] [--fail-on-held] [--fail-on-empty] "
            + "[--diff-against <dir>] [--properties <file>] "
            + "<policy-file> [<policy-file> ...]");
        err.println(
            "       ProxyPolicyGenerator --out <dir> [--plan-file <file>] "
            + "[--artefact-map <file>] --promote-plan <module-root>...");
        err.println(
            "       ProxyPolicyGenerator --out <dir> [--artefact-map <file>] "
            + "--promote-plan-check <module-root>...");
        err.println(
            "       ProxyPolicyGenerator --promote-apply <plan-file> --approve-plan-sha <sha> "
            + "--out <dir> [--artefact-map <file>] --module-roots <module-root>... "
            + "--approve <artefact>[,<artefact>...] [--allow-removals] [--allow-held]");
    }

    // ------------------------------------------------------------- core API

    /**
     * Generates the proxy ceiling fragment and per-codebase
     * {@code PERMISSIONS.LIST} manifests for one policy file.
     *
     * <p>Everything is decided and validated before {@code outDir} or the
     * ceiling file is touched: the lists are staged in a temporary directory
     * beside {@code outDir} and moved in only once all of them validated.  A
     * refusal therefore leaves {@code outDir} and the ceiling exactly as they
     * were.  The final moves are not atomic as a group: an {@link IOException}
     * from one of them can leave some lists moved.
     *
     * @param policyFile the least-privilege (audited / condensed) policy file
     * @param outDir     root directory under which
     *                   {@code <artefact>/META-INF/PERMISSIONS.LIST} trees are
     *                   written; created if absent
     * @return a {@link Result} describing what was written
     * @throws GenerationException if nothing was written because the run was
     *         refused: {@code --fail-on-held} and a host- or run-specific
     *         entry, {@code --fail-on-empty} and a group with no live entry,
     *         a control character in a value, a directory holding lists from
     *         another source policy, an output that would overwrite a
     *         checked-in list, or a written line that does not parse
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
        ListSource source = new ListSource(policyFile.getName(), sha256Hex(policyFile));
        requireNoControl("policy file name", source.policy);

        if (proxies.isEmpty()) {
            // Fail-closed: produce nothing (and clean any stale fragment).  Lists
            // already under outDir are reported, not refused: nothing is written.
            List<String> notes = new ArrayList<String>();
            if (ceilingFile.exists()) {
                if (isOurCeiling(ceilingFile)) {
                    ceilingFile.delete();
                } else {
                    notes.add("KEPT " + ceilingFile.getName() + ": it was not written by"
                            + " ProxyPolicyGenerator, so it is not deleted");
                }
            }
            if (clean) {
                removeLists(outDir, notes, null);
            }
            for (Map.Entry<String, ListSource> e : existingLists(outDir).entrySet()) {
                notes.add(staleNote(e.getKey(), e.getValue(), source));
            }
            return new Result(policyFile, 0, ceilingFile, manifests, notes, "", 0, 0, 0);
        }

        // Decide every list before writing anything, so a refusal leaves the
        // output directory as it was.
        List<ListPlan> plans = new ArrayList<ListPlan>();
        for (ProxyGroup grp : proxies) plans.add(plan(grp, outDir));
        // One artefact name is one list: two codebases that sanitise to the same name would be
        // staged to one path (the second move fails part-way), and merging them would widen a list.
        // The key ignores case, as a Windows or macOS file system does.
        Map<String, ListPlan> byName = new LinkedHashMap<String, ListPlan>();
        for (ListPlan plan : plans) {
            ListPlan first = byName.put(plan.artefact.toLowerCase(java.util.Locale.ROOT), plan);
            if (first != null) {
                throw new GenerationException("two codebases map to the one artefact name '"
                        + first.artefact + "'"
                        + (first.artefact.equals(plan.artefact) ? "" : " / '" + plan.artefact + "'")
                        + ": " + nullToAny(first.group.template.codebase) + " and "
                        + nullToAny(plan.group.template.codebase)
                        + "; they are never merged (a union would widen a list). Nothing was written;"
                        + " give the policy distinct jar names, or generate them from separate policies");
            }
        }
        List<String> notes = new ArrayList<String>();
        for (ListPlan plan : plans) plan.addNotes(notes);

        // An empty list requests the same as no list (AdvisoryDynamicPermissions
        // .DEFAULT_PERMISSIONS is empty), so promoting one would silently remove
        // the reviewed entries of the list it replaces.  Write none.
        List<ListPlan> writable = new ArrayList<ListPlan>();
        List<ListPlan> empty = new ArrayList<ListPlan>();
        for (ListPlan plan : plans) {
            if (plan.emit.isEmpty()) {
                empty.add(plan);
                notes.add("NO LIST WRITTEN: all " + (plan.dropped.size() + plan.held.size())
                        + " entries dropped or held for " + plan.artefact);
            } else {
                writable.add(plan);
            }
        }

        if (failOnHeld) {
            for (ListPlan plan : plans) {
                if (!plan.held.isEmpty()) {
                    throw new GenerationException("--fail-on-held: " + plan.artefact + " has "
                            + plan.held.size() + " host- or run-specific entr"
                            + (plan.held.size() == 1 ? "y" : "ies") + ": "
                            + plan.held.keySet());
                }
            }
        }
        if (failOnEmpty && !empty.isEmpty()) {
            List<String> names = new ArrayList<String>();
            for (ListPlan plan : empty) names.add(plan.artefact);
            throw new GenerationException("--fail-on-empty: no live entry left for " + names);
        }

        if (ceilingFile.exists() && !isOurCeiling(ceilingFile)) {
            throw new GenerationException(ceilingFile + " exists and was not written by"
                    + " ProxyPolicyGenerator; it is never overwritten, move it away first");
        }

        Map<String, ListSource> existing = existingLists(outDir);
        // A list with neither marker is somebody's hand-written file: this tool never replaces or
        // deletes one.  Without --clean the foreign-list check below already refuses it.
        List<String> handWritten = new ArrayList<String>();
        for (Map.Entry<String, ListSource> e : existing.entrySet()) {
            if (e.getValue().handWritten() && planned(writable, e.getKey())) {
                handWritten.add(e.getKey());
            }
        }
        if (clean && !handWritten.isEmpty()) {
            throw new GenerationException(outDir + " holds " + handWritten + ", which this run"
                    + " would replace but which carries neither a '" + SOURCE_POLICY_PREFIX.trim()
                    + "' line nor a '" + OLD_HEADER_PREFIX + "' line, so this tool did not write"
                    + " it; it never replaces or deletes such a file, with or without --clean."
                    + " Move it away first");
        }
        if (!clean) {
            List<String> foreign = new ArrayList<String>();
            for (Map.Entry<String, ListSource> e : existing.entrySet()) {
                ListSource found = e.getValue();
                if (found.policy == null && found.oldHeader && planned(writable, e.getKey())) {
                    notes.add("REPLACED pre-provenance list " + e.getKey() + ": it carries the old '"
                            + OLD_HEADER_PREFIX + "' line, so this tool wrote it");
                } else if (found.policy == null) {
                    foreign.add(e.getKey() + (found.oldHeader
                            ? " (pre-provenance list written by this tool)" : " (no provenance line)"));
                } else if (planned(writable, e.getKey())) {
                    // This run replaces it, whatever it was made from: only a list
                    // that survives the run unreplaced can be mixed with the new ones.
                    continue;
                } else if (!found.policy.equals(source.policy)) {
                    foreign.add(e.getKey() + " (from " + found.policy + ")");
                } else if (!source.sha256.equals(found.sha256)) {
                    foreign.add(e.getKey() + " (from " + found.policy + " with different content: "
                            + shortSha(found.sha256) + " there, " + shortSha(source.sha256) + " here)");
                } else {
                    notes.add(staleNote(e.getKey(), found, source));
                }
            }
            if (!foreign.isEmpty()) {
                throw new GenerationException(outDir + " already holds lists not generated"
                        + " from " + source.policy + " as it is now and not replaced by this run: "
                        + foreign + "; remove them by hand or pass --clean (which removes only"
                        + " lists this tool wrote)");
            }
        }

        String diff = "";
        if (diffAgainst != null) diff = diff(plans, diffAgainst);

        // Stage beside outDir (same file system, so the moves are renames),
        // validate, and only then touch outDir or the ceiling.
        File parent = outDir.getAbsoluteFile().getParentFile();
        long stamp = System.nanoTime();
        File staging = new File(parent, "." + outDir.getName() + ".staging-" + stamp);
        File stagedCeiling = new File(ceilingFile.getAbsolutePath() + ".staging-" + stamp);
        try {
            if (!staging.mkdirs()) throw new IOException("Unable to create directory: " + staging);
            writeCeilingFragment(stagedCeiling, policyFile, proxies);
            Map<ListPlan, File> staged = new LinkedHashMap<ListPlan, File>();
            for (ListPlan plan : writable) {
                File stagedList = new File(new File(new File(staging, plan.artefact),
                        "META-INF"), "PERMISSIONS.LIST");
                writePermissionsList(plan, stagedList, source);
                validateList(stagedList);
                staged.put(plan, stagedList);
            }
            if (clean) removeLists(outDir, notes, writable);
            for (Map.Entry<ListPlan, File> e : staged.entrySet()) {
                moveInto(e.getValue(), e.getKey().file);
                manifests.add(e.getKey().file.getAbsolutePath());
            }
            moveInto(stagedCeiling, ceilingFile);
        } finally {
            deleteTree(staging);
            stagedCeiling.delete();
        }
        return new Result(policyFile, proxies.size(), ceilingFile, manifests, notes, diff,
                countEntries(plans, false), countEntries(plans, true), empty.size());
    }

    private static void moveInto(File from, File to) throws IOException {
        File dir = to.getAbsoluteFile().getParentFile();
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Unable to create directory: " + dir);
        }
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    private static String staleNote(String artefact, ListSource found, ListSource current) {
        if (found.policy != null && found.policy.equals(current.policy)
                && current.sha256.equals(found.sha256)) {
            return "STALE " + artefact + ": a list from " + current.policy
                    + " that this run did not regenerate";
        }
        return "STALE " + artefact + ": a list "
                + (found.policy == null ? "with no provenance line" : "from " + found.policy
                    + " (" + shortSha(found.sha256) + ")")
                + ", not from " + current.policy + " (" + shortSha(current.sha256)
                + "); this run did not regenerate it";
    }

    private static String shortSha(String sha) {
        return sha == null ? "no SHA-256" : "sha256 " + sha.substring(0, Math.min(12, sha.length()));
    }

    private static boolean planned(List<ListPlan> plans, String artefact) {
        for (ListPlan plan : plans) {
            if (plan.artefact.equals(artefact)) return true;
        }
        return false;
    }

    private static int countEntries(List<ListPlan> plans, boolean held) {
        int n = 0;
        for (ListPlan plan : plans) n += held ? plan.held.size() : plan.dropped.size();
        return n;
    }

    /** Lower-case hex SHA-256 of a file's bytes. */
    static String sha256Hex(File f) throws IOException {
        return sha256Hex(Files.readAllBytes(f.toPath()));
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    /**
     * Rejects a control character in a value that reaches a list header or an
     * entry: a newline there would end the comment line and start a live one.
     */
    static void requireNoControl(String what, String value) throws GenerationException {
        if (value == null) return;
        for (int i = 0; i < value.length(); ) {
            int c = value.codePointAt(i);
            if (Character.isISOControl(c) || c == 0x2028 || c == 0x2029
                    || Character.getType(c) == Character.FORMAT) {
                throw new GenerationException("control or format character U+"
                        + String.format("%04X", c) + " at index " + i + " of the " + what
                        + "; it could start a live line in PERMISSIONS.LIST or reorder what a"
                        + " reviewer reads");
            }
            i += Character.charCount(c);
        }
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
        PrintWriter pw = lfWriter(ceilingFile);
        try {
            // Policy-file comments are '//' / '/* */' (NOT '#'), so the
            // fragment round-trips through DefaultPolicyParser.
            writeApacheHeader(pw, "// ");
            pw.println("//");
            pw.println(CEILING_MARKER + " from");
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

    /**
     * Sorts a proxy group's declared needs into the entries to emit, the gate
     * permissions to drop and the host- or run-specific entries to hold back.
     * The name goes through {@link #applyReplacements} first, as it always did,
     * so a host value the operator mapped with {@code --properties} is judged
     * as the placeholder it becomes.
     */
    ListPlan plan(ProxyGroup grp, File outDir) throws GenerationException {
        requireNoControl("codebase", grp.template.codebase);
        requireNoControl("digest", grp.template.digest);
        for (GrantBlock g : grp.members) {
            requireNoControl("signedBy of the grant for " + grp.template.codebase, g.signedBy);
            for (String[] pal : g.principals) {
                requireNoControl("principal class of the grant for " + grp.template.codebase, pal[0]);
                requireNoControl("principal name of the grant for " + grp.template.codebase, pal[1]);
            }
        }
        for (Perm p : grp.declaredNeeds()) {
            requireNoControl("signedBy of permission " + p.type, p.signedBy);
            requireNoControl("permission type " + p.type, p.type);
            requireNoControl("name of permission " + p.type, p.name);
            requireNoControl("actions of permission " + p.type, p.actions);
        }
        String artefact = artefactName(grp);
        ListPlan plan = new ListPlan(grp, artefact, new File(
                new File(new File(outDir, artefact), "META-INF"), "PERMISSIONS.LIST"));
        for (Perm p : grp.declaredNeeds()) {
            String name = p.name == null ? null : applyReplacements(p.name);
            String actions = (p.actions == null || p.actions.length() == 0) ? null : p.actions;
            String entry = encodeEntry(p.type, name, actions);
            String hostSpecific = hostSpecificReason(p.type, name);
            if (GATE_PERMISSION_CLASSES.contains(p.type)) {
                plan.dropped.put(entry, GATE_REASON);
            } else if (hostSpecific != null) {
                plan.held.put(entry, hostSpecific);
            } else {
                plan.emit.add(entry);
            }
        }
        return plan;
    }

    /** Encodes one entry; the single place a line of the list is formed. */
    String encodeEntry(String type, String name, String actions) {
        return AdvisoryPermissionParser.getEncoded(type, name, actions);
    }

    /**
     * @return why an entry with this name belongs to the audit host or run
     *         rather than to the proxy, or {@code null} if it does not
     */
    static String hostSpecificReason(String type, String name) {
        if (name == null) return null;
        if (name.contains("${")) {
            return "unexpanded ${...} placeholder; the loader does not expand placeholders"
                    + " in PERMISSIONS.LIST";
        }
        if (FILE_URL.matcher(name).lookingAt()) {
            return "file: URL naming a location on the audit host";
        }
        if (WINDOWS_ABSOLUTE.matcher(name).lookingAt()
                || (FILE_PERMISSION.equals(type) && name.startsWith("/"))) {
            return "absolute path on the audit host";
        }
        if (GENERATED_PROXY_CLASS.matcher(name).find()) {
            return "generated proxy class name; the number depends on class-load order";
        }
        return null;
    }

    private File writePermissionsList(ListPlan plan, File listFile, ListSource source)
            throws IOException {
        File dir = listFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Unable to create directory: " + dir);
        }
        ProxyGroup grp = plan.group;

        PrintWriter pw = lfWriter(listFile);
        try {
            writeApacheHeader(pw, "# ");
            pw.println("# Declared-needs manifest for proxy codebase:");
            pw.println("#   " + nullToAny(grp.template.codebase));
            if (grp.template.digest != null) {
                pw.println("# Proxy codebase digest: " + grp.template.digest);
            }
            pw.println("# Generated by ProxyPolicyGenerator (do not edit by hand).");
            pw.println(SOURCE_POLICY_PREFIX + source.policy);
            pw.println(SOURCE_SHA_PREFIX + source.sha256);
            pw.println("# Generator: ProxyPolicyGenerator " + version()
                    + ", list format " + LIST_FORMAT);
            pw.println("# Generated: " + LocalDate.now(clock));
            pw.println("# Entries: " + plan.emit.size() + " live, " + plan.dropped.size()
                    + " dropped, " + plan.held.size() + " held back");
            for (Map.Entry<String, String> e : plan.dropped.entrySet()) {
                pw.println("# DROPPED (" + e.getValue() + "): " + e.getKey());
            }
            for (Map.Entry<String, String> e : plan.held.entrySet()) {
                pw.println("# HELD (" + e.getValue() + "): " + e.getKey());
            }
            for (Iterator<String> it = plan.emit.iterator(); it.hasNext();) {
                pw.println(it.next());
            }
        } finally {
            pw.flush();
            pw.close();
        }
        return listFile;
    }

    /** A UTF-8 writer whose lines end in LF on every platform, as the licence header's do. */
    private static PrintWriter lfWriter(File f) throws IOException {
        return new PrintWriter(new BufferedWriter(Files.newBufferedWriter(f.toPath(), UTF8))) {
            @Override
            public void println() {
                write('\n');
            }
        };
    }

    private static String version() {
        Package p = ProxyPolicyGenerator.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "(unpackaged build)" : v;
    }

    /**
     * Parses each entry line of a written list as the loader does
     * ({@code PreferredClassLoader}: blank, {@code #} and {@code //} lines are
     * skipped).  The loader catches one bad line, logs it at CONFIG and drops
     * the jar's whole list, so a bad line must stop generation here.  A class
     * the generator's JVM cannot load parses to an {@code UnresolvedPermission}
     * and is accepted, as it is by the loader.
     */
    static void validateList(File listFile) throws IOException {
        ClassLoader loader = ProxyPolicyGenerator.class.getClassLoader();
        int n = 0;
        for (String line : Files.readAllLines(listFile.toPath(), UTF8)) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
            try {
                AdvisoryPermissionParser.parse(line, loader);
            } catch (RuntimeException | LinkageError e) {
                throw new GenerationException(listFile + ":" + n + ": the loader would drop"
                        + " the whole list, entry does not parse (" + e.getMessage() + "): " + line);
            }
        }
    }

    // ------------------------------------------------------------ provenance

    /** Where a list came from: the policy file name and the SHA-256 of its bytes. */
    static final class ListSource {
        /** {@code null} for a list with no provenance line. */
        final String policy;
        /** {@code null} for a list with no SHA-256 line. */
        final String sha256;

        /** The list carries the header line of lists written before provenance existed. */
        final boolean oldHeader;

        ListSource(String policy, String sha256) {
            this(policy, sha256, false);
        }

        ListSource(String policy, String sha256, boolean oldHeader) {
            this.policy = policy;
            this.sha256 = sha256;
            this.oldHeader = oldHeader;
        }

        /** Neither marker of this tool's own lists: somebody's file. */
        boolean handWritten() {
            return policy == null && !oldHeader;
        }
    }

    /**
     * The generated list file of each artefact directory directly under
     * {@code dir}, by directory name.
     */
    static Map<String, File> generatedLists(File dir) {
        Map<String, File> found = new TreeMap<String, File>();
        File[] children = dir.listFiles();
        if (children == null) return found;
        for (File child : children) {
            File list = new File(new File(child, "META-INF"), "PERMISSIONS.LIST");
            if (list.isFile()) found.put(child.getName(), list);
        }
        return found;
    }

    /**
     * The generated lists already under {@code outDir}, by artefact directory
     * name, each with the source its header records; a list that records none
     * (from before provenance) has a {@link ListSource} with {@code null} fields.
     */
    static Map<String, ListSource> existingLists(File outDir) throws IOException {
        Map<String, ListSource> found = new TreeMap<String, ListSource>();
        for (Map.Entry<String, File> e : generatedLists(outDir).entrySet()) {
            found.put(e.getKey(), listSource(e.getValue()));
        }
        return found;
    }

    /**
     * Deletes the lists {@link #existingLists} finds that this tool wrote (they
     * carry the provenance header), and the directories that empties.  A list
     * without it is somebody's hand-written file: it is kept and noted.
     */
    private static void removeLists(File outDir, List<String> notes, List<ListPlan> replaced)
            throws IOException {
        for (Map.Entry<String, File> e : generatedLists(outDir).entrySet()) {
            File list = e.getValue();
            ListSource src = listSource(list);
            if (src.handWritten()) {
                // generate() has already refused a run that replaces such a list, so this is true
                notes.add("KEPT " + e.getKey() + ": a list with neither a '"
                        + SOURCE_POLICY_PREFIX.trim() + "' line nor a '" + OLD_HEADER_PREFIX
                        + "' line was not written by this tool, so --clean does not delete it");
                continue;
            }
            File meta = list.getParentFile();
            if (list.delete()) {
                meta.delete();
                meta.getParentFile().delete();
                if (src.policy == null) {
                    notes.add((replaced != null && planned(replaced, e.getKey())
                            ? "REPLACED" : "REMOVED") + " pre-provenance list " + e.getKey()
                            + ": it carries the old '" + OLD_HEADER_PREFIX
                            + "' line, so this tool wrote it");
                } else if (replaced == null) {
                    notes.add("REMOVED " + e.getKey() + ": a list from " + src.policy + " (--clean)");
                }
            }
        }
    }

    /** The source a single list records; {@code null} fields where it records none. */
    static ListSource listSource(File list) throws IOException {
        String policy = null;
        String sha = null;
        boolean oldHeader = false;
        for (String line : Files.readAllLines(list.toPath(), UTF8)) {
            if (line.startsWith(OLD_HEADER_PREFIX)) oldHeader = true;
            if (policy == null && line.startsWith(SOURCE_POLICY_PREFIX)) {
                policy = line.substring(SOURCE_POLICY_PREFIX.length()).trim();
            } else if (sha == null && line.startsWith(SOURCE_SHA_PREFIX)) {
                sha = line.substring(SOURCE_SHA_PREFIX.length()).trim();
            }
        }
        return new ListSource(policy, sha, oldHeader);
    }

    /**
     * @return why the generator never emits a permission of this class, so a list
     *         that holds one has been edited; {@code null} if it may appear
     */
    static String excludedReason(String type) {
        if (META_PERMISSION_CLASSES.contains(type)) return META_REASON;
        if (GATE_PERMISSION_CLASSES.contains(type)) return GATE_REASON;
        return null;
    }

    /**
     * The type of an entry line, read as the loader reads it; {@code null} if
     * the line has none.
     */
    static String entryType(String entry) {
        int n = entry.length();
        int pos = 0;
        while (pos < n && Character.isWhitespace(entry.charAt(pos))) pos++;
        if (pos >= n || entry.charAt(pos) != '(') return null;
        pos++;
        while (pos < n && Character.isWhitespace(entry.charAt(pos))) pos++;
        int begin = pos;
        while (pos < n && !Character.isWhitespace(entry.charAt(pos)) && entry.charAt(pos) != ')') pos++;
        return pos == begin ? null : entry.substring(begin, pos);
    }

    /**
     * {@code s} with every control and format character, other than a newline,
     * shown as a backslash-u escape: output that can carry a name read from a
     * file must not carry the terminal controls or bidirectional marks in it.
     */
    static String escaped(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if (c != '\n' && (Character.isISOControl(c) || c == 0x2028 || c == 0x2029
                    || Character.getType(c) == Character.FORMAT)) {
                sb.append(String.format("\\u%04X", c));
            } else {
                sb.appendCodePoint(c);
            }
        }
        return sb.toString();
    }

    /** Whether {@code f} is a ceiling fragment this tool wrote. */
    private static boolean isOurCeiling(File f) throws IOException {
        for (String line : Files.readAllLines(f.toPath(), UTF8)) {
            if (line.startsWith(CEILING_MARKER)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ diff

    /** Strips a trailing {@code -<version>} such as {@code -4.0.0-SNAPSHOT}. */
    private static final Pattern VERSION_SUFFIX =
            Pattern.compile("-\\d+(?:\\.\\d+)*(?:-[A-Za-z0-9]+)*$");

    /**
     * Prints, per planned artefact, the live entries added to and removed from
     * the checked-in list of the same name.  Entries are compared as parsed
     * (type, name, actions), so {@code (X "n")} equals {@code (X "n" "")}.
     * Reads {@code checkedInDir} only, and refuses a plan whose output file is
     * one of the checked-in lists.
     */
    String diff(List<ListPlan> plans, File checkedInDir) throws IOException {
        Map<String, List<File>> checkedIn = new TreeMap<String, List<File>>();
        collectLists(checkedInDir, checkedIn);
        Set<String> targets = new HashSet<String>();
        for (List<File> files : checkedIn.values()) {
            for (File f : files) targets.add(f.getCanonicalPath());
        }
        for (ListPlan plan : plans) {
            if (targets.contains(plan.file.getCanonicalPath())) {
                throw new GenerationException(plan.file + " is a checked-in list under "
                        + checkedInDir + "; --out must not point into the lists being compared");
            }
        }

        StringBuilder sb = new StringBuilder();
        Set<String> matched = new HashSet<String>();
        for (ListPlan plan : plans) {
            String key = moduleKey(checkedIn.keySet(), plan.artefact);
            List<File> files = checkedIn.get(key);
            if (files == null) {
                sb.append("diff ").append(plan.artefact).append(": no checked-in list under ")
                  .append(checkedInDir).append("; all ").append(plan.emit.size())
                  .append(" live entries are additions\n");
                for (String e : plan.emit) sb.append("  + ").append(e).append('\n');
                continue;
            }
            if (files.size() > 1) {
                sb.append("diff ").append(plan.artefact).append(": ambiguous, ")
                  .append(files.size()).append(" checked-in lists named ").append(key)
                  .append(": ").append(files).append('\n');
                continue;
            }
            matched.add(key);
            EntryDiff d = compare(plan.emit, files.get(0));
            sb.append("diff ").append(plan.artefact).append(" vs ")
              .append(relative(checkedInDir, files.get(0))).append(": +").append(d.added.size())
              .append(" -").append(d.removed.size()).append(" =").append(d.common).append('\n');
            for (String e : d.added.values()) sb.append("  + ").append(e).append('\n');
            for (String e : d.removed.values()) sb.append("  - ").append(e).append('\n');
        }
        List<String> unmatched = new ArrayList<String>();
        for (String key : checkedIn.keySet()) {
            if (!matched.contains(key)) unmatched.add(key);
        }
        if (!unmatched.isEmpty()) {
            sb.append("diff: checked-in lists with no generated counterpart: ")
              .append(unmatched).append('\n');
        }
        return sb.toString();
    }

    /** The entries a list gains and loses against a checked-in list. */
    static final class EntryDiff {
        /** Canonical key to entry text. */
        final Map<String, String> added = new TreeMap<String, String>();
        /** Canonical key to the checked-in line, marked where it does not parse. */
        final Map<String, String> removed = new TreeMap<String, String>();
        int common;
    }

    /**
     * Compares live entries with those of a checked-in list, as parsed: an
     * entry with an empty actions string equals one with none.
     */
    static EntryDiff compare(Collection<String> liveEntries, File checkedInList)
            throws IOException {
        EntryDiff d = new EntryDiff();
        for (String e : liveEntries) d.added.put(canonicalKey(e), e);
        for (String t : liveEntries(checkedInList)) {
            String k = canonicalKey(t);
            if (d.added.remove(k) != null) {
                d.common++;
            } else {
                d.removed.put(k, k.startsWith("!") ? t + "   [does not parse]" : t);
            }
        }
        return d;
    }

    /** The entry lines of a list as the loader sees them: trimmed, not blank, not a comment. */
    static List<String> liveEntries(File list) throws IOException {
        List<String> live = new ArrayList<String>();
        for (String line : Files.readAllLines(list.toPath(), UTF8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
            live.add(t);
        }
        return live;
    }

    /**
     * The key under which a generated artefact is looked up among module
     * names: its own name, else the name without a trailing {@code -<version>}.
     */
    static String moduleKey(Collection<String> moduleNames, String artefact) {
        return moduleNames.contains(artefact) ? artefact
                : VERSION_SUFFIX.matcher(artefact).replaceFirst("");
    }

    /**
     * A key equal for entries that parse to the same type, name and actions;
     * {@code "!"}-prefixed raw text where the entry does not parse.
     */
    static String canonicalKey(String entry) {
        try {
            Permission p = AdvisoryPermissionParser.parse(entry,
                    ProxyPolicyGenerator.class.getClassLoader());
            if (p instanceof UnresolvedPermission) {
                UnresolvedPermission u = (UnresolvedPermission) p;
                return u.getUnresolvedType() + "|" + nz(u.getUnresolvedName())
                        + "|" + nz(u.getUnresolvedActions());
            }
            return p.getClass().getName() + "|" + nz(p.getName()) + "|" + nz(p.getActions());
        } catch (RuntimeException e) {
            return "!" + entry;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * Finds every {@code META-INF/PERMISSIONS.LIST} under {@code dir}, keyed by
     * artefact: the module directory of {@code <module>/src/main/resources},
     * else the directory holding {@code META-INF}.  Build output ({@code target},
     * {@code build}), {@code .git} and generated {@code *.proxy-permissions}
     * trees are not checked-in lists and are skipped.
     */
    private static void collectLists(File dir, Map<String, List<File>> found) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File c : children) {
            String n = c.getName();
            if (c.isDirectory()) {
                if (n.equals("target") || n.equals("build") || n.equals(".git")
                        || n.endsWith(".proxy-permissions")) continue;
                collectLists(c, found);
            } else if (n.equals("PERMISSIONS.LIST")
                    && "META-INF".equals(c.getParentFile().getName())) {
                String artefact = artefactDirName(c.getParentFile().getParentFile());
                List<File> files = found.get(artefact);
                if (files == null) {
                    files = new ArrayList<File>();
                    found.put(artefact, files);
                }
                files.add(c);
            }
        }
    }

    private static String artefactDirName(File holder) {
        if (holder != null && "resources".equals(holder.getName())) {
            File main = holder.getParentFile();
            File src = main == null ? null : main.getParentFile();
            File module = src == null ? null : src.getParentFile();
            if (main != null && "main".equals(main.getName()) && src != null
                    && "src".equals(src.getName()) && module != null) {
                return module.getName();
            }
        }
        return holder == null ? "" : holder.getName();
    }

    private static String relative(File root, File f) {
        return root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
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
        // ${/} is the file separator property: it hides the last path segment
        s = s.replace("${/}", "/");
        // last path segment
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) s = s.substring(slash + 1);
        // a segment such as ${reggie-dl.jar} or ${HOST}:${jsk-dl.jar} names the jar by the
        // property that holds it: the last ${name.jar} group is the jar
        Matcher jar = JAR_PROPERTY.matcher(s);
        String unwrapped = null;
        while (jar.find()) unwrapped = jar.group(1);
        if (unwrapped != null) s = unwrapped;
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

    /** What one proxy's {@code PERMISSIONS.LIST} will hold, decided before it is written. */
    static final class ListPlan {
        final ProxyGroup group;
        final String artefact;
        final File file;
        /** Live entries, sorted. */
        final TreeSet<String> emit = new TreeSet<String>();
        /** Gate permissions left out: entry to reason. */
        final TreeMap<String, String> dropped = new TreeMap<String, String>();
        /** Host- or run-specific entries written commented out: entry to reason. */
        final TreeMap<String, String> held = new TreeMap<String, String>();

        ListPlan(ProxyGroup group, String artefact, File file) {
            this.group = group;
            this.artefact = artefact;
            this.file = file;
        }

        void addNotes(List<String> notes) {
            for (Map.Entry<String, String> e : dropped.entrySet()) {
                notes.add("DROPPED " + artefact + ": " + e.getKey() + " -- " + e.getValue());
            }
            for (Map.Entry<String, String> e : held.entrySet()) {
                notes.add("HELD " + artefact + ": " + e.getKey() + " -- " + e.getValue());
            }
        }
    }

    /**
     * Generation, planning or promotion was refused: see the message.  For
     * {@link #generate} the output directory and the ceiling are as they were.
     * Distinct from an {@link IOException} on reading or writing a file.
     */
    public static final class GenerationException extends IOException {
        private static final long serialVersionUID = 1L;

        GenerationException(String message) {
            super(message);
        }
    }

    /** Summary of one {@link #generate} invocation. */
    public static final class Result {
        private final File policyFile;
        private final int proxyCount;
        private final File ceilingFile;
        private final List<String> permissionListFiles;
        private final List<String> notes;
        private final String diff;
        private final int droppedCount;
        private final int heldCount;
        private final int noListCount;

        Result(File policyFile, int proxyCount, File ceilingFile,
                List<String> permissionListFiles, List<String> notes, String diff,
                int droppedCount, int heldCount, int noListCount) {
            this.policyFile = policyFile;
            this.proxyCount = proxyCount;
            this.ceilingFile = ceilingFile;
            this.permissionListFiles = permissionListFiles;
            this.notes = notes;
            this.diff = diff;
            this.droppedCount = droppedCount;
            this.heldCount = heldCount;
            this.noListCount = noListCount;
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

        /** One line per gate permission dropped, entry held back, group left without a list, or stale list. */
        public List<String> notes() {
            return notes;
        }

        /** The {@code --diff-against} report; empty when not requested. */
        public String diff() {
            return diff;
        }

        public int droppedCount() {
            return droppedCount;
        }

        public int heldCount() {
            return heldCount;
        }

        /** Proxy groups left with no live entry, for which no list was written. */
        public int noListCount() {
            return noListCount;
        }

        String describe() {
            if (proxyCount == 0) {
                return "ProxyPolicyGenerator: " + policyFile.getName()
                        + " -> no proxy-targeted grants.";
            }
            return "ProxyPolicyGenerator: " + policyFile.getName() + " -> "
                    + proxyCount + " proxy codebase(s); ceiling="
                    + ceilingFile.getName() + ", manifests=" + permissionListFiles.size()
                    + ", dropped=" + droppedCount + ", held=" + heldCount
                    + ", no-list=" + noListCount;
        }
    }
}
