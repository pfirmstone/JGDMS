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
package au.net.zeus.jgdms.api.policy;

import java.io.FilePermission;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The catalogue's reading of a FilePermission name against the JDK's own, on a Windows host.
 * <p>
 * Opus review 2026-11-06 found 14,997 spellings (of 105,694 generated) that {@code java.io.FilePermission}
 * grants over a whole ruled tree and {@code PermissionSets.filePath} read as narrow or as nothing, because
 * the catalogue re-implements WinNTFileSystem, WindowsPathParser and FilePermission by hand.  Rating a
 * hand-written reading against the JDK's own is the only check that does not share the author's idea of
 * the format (a mirror written from the same idea reproduces the idea, not the bug).  The Opus normaliser
 * review of 2026-11-07 then showed this test blind to the device namespace, to {@code *} and to a
 * drive-relative {@code ..} chain, and one-sided; the families below are what it added.
 * <p>
 * Instrument: for each generated spelling, {@code new FilePermission(spelling, action).implies(probe)} for
 * probes in three tiers, and the catalogue must rate every spelling that implies one at least as the tier
 * requires, for read and for write (operator rulings 2026-11-02 and 2026-11-03):
 * <ul>
 * <li><b>deep</b>: a file two directories below a ruled tree ({@code zq81\deep\f.txt}), a name no spelling
 *   contains: the drive root, {@code C:\Users}, another user's home, {@code user.home}, {@code \home},
 *   {@code \home\alice}, {@code \root}, {@code \var\root}, a UNC share and a UNC host.  HIGH.
 * <li><b>direct child of a drive root</b> ({@code C:\zq81.txt}, {@code \zq81.txt}): reached by a {@code *}
 *   name as well.  Read at least MEDIUM, write HIGH: the ruling that a root's own files stay MEDIUM.
 *   {@code *} over any other directory has no ruling (the catalogue rates {@code C:\Users\*},
 *   {@code C:\Users\alice\*} and {@code \\server\share\*} 0 and {@code ${user.home}${/}*} MEDIUM to read and
 *   HIGH to write; operator question Q2 is open), so those spellings are generated and not scored.  A {@code *}
 *   under a device root is not covered here either; the unit test pins it HIGH.
 * <li><b>above the current directory</b> ({@code C:..\..\zq81\deep\f.txt} and the same without a drive):
 *   read at least MEDIUM, write HIGH, the rating of any name that leaves the working directory.  This is the
 *   family a revert of F3 fails.  FilePermission also compares a relative name by its form resolved against the
 *   working directory, so a chain as long as that directory is deep reaches the canonical trees as well, which
 *   depends on where the test runs (review F7): chain spellings are scored only against these probes.
 * </ul>
 * Device-namespace probes are the OS aliases of those trees ({@code \\.\Volume{guid}\...},
 * {@code \\.\GLOBALROOT\Device\HarddiskVolumeN\...}, {@code \\.\UNC\h\s\...}, {@code \\.\GLOBALROOT\Device\Mup\...}),
 * direct and behind one {@code \\.\X\..\} hop, because the JDK clamps {@code ..} at {@code \\.\X\} and Win32
 * does not; a spelling that implies one reaches the tree through the OS.  Narrow controls (a named directory
 * below a ruled tree, in every non-device spelling) must stay unrated and must imply no probe, so that a
 * catalogue that rates everything HIGH fails.  Device-namespace names are not controls: the rule is that
 * every recursive one is HIGH.
 * <p>
 * Scope line: the same bed as the review's PathDiff, a smaller generator; the JDK side is lexical
 * ({@code jdk.io.permissionsUseCanonicalPath=false}), so this is a second instrument on the catalogue, not a
 * second bed for the OS.  What reaches the OS target is OS evidence, and this test does not produce it: the
 * 2026-11-06 PathDiff verified the drive, root-relative, long-prefix, volume, trailing-dot and UNC families
 * with Files.isSameFile, and the Opus normaliser review verified the GLOBALROOT, {@code UNC.}, {@code .} and
 * {@code ..}-hop device families live (with HarddiskVolume8, the volume behind C: on its host; PathDiff's
 * check with HarddiskVolume3 failed).  The zero volume GUID and HarddiskVolume3 used here are stand-ins:
 * ASSUMPTION that they behave as the verified names.  The Documents and Settings junction, the
 * {@code \\localhost\C$} admin share and the reach of a relative {@code ..} chain to the drive from the working
 * directory (review F7) are neither generated nor scored.  The junction and the admin share are rated by the
 * operator ruling of 2026-11-07, which the JDK cannot corroborate; {@link #testTheOsAliasesAreRatedByRulingAndNotScoredAgainstTheJdk}
 * pins both facts.
 * <p>
 * Tier: fast (seconds), default suite, skipped (not passed) off Windows.
 */
public class PermissionSetsFilePermissionDifferentialTest {

    private static final String FILE = "java.io.FilePermission";
    private static final String[] ACTIONS = {"read", "write"};
    private static final String VOLUME = "Volume{00000000-0000-0000-0000-000000000000}";
    private static final String BACK = "\\";
    private static final String FWD = "/";
    private static final String[] DEEP = {"zq81", "deep", "f.txt"};
    private static final String DIRECT = "zq81.txt";

    private static final int TIER_DEEP = 0;
    private static final int TIER_DIRECT_ROOT = 1;
    private static final int TIER_ABOVE_CWD = 2;
    private static final String[] TIER_NAMES = {"deep", "direct-child-of-drive-root", "above-cwd"};

    /** The least severity the ruling gives a spelling that reaches the tier; {@code action} 0 is read, 1 write. */
    private static int required(int tier, int action) {
        return tier == TIER_DEEP || action == 1 ? PermissionSets.HIGH : PermissionSets.MEDIUM;
    }

    /** A ruled whole tree, or a narrow control: a drive or a UNC share or host, and the names below it. */
    private static final class Tree {
        final String id;
        final String letter;
        final List<String> segs;
        final boolean unix;
        final String host;
        final String share;
        final boolean narrow;

        Tree(String id, String letter, boolean unix, String host, String share, boolean narrow, String... segs) {
            this.id = id;
            this.letter = letter;
            this.unix = unix;
            this.host = host;
            this.share = share;
            this.narrow = narrow;
            this.segs = Arrays.asList(segs);
        }

        boolean unc() {
            return host != null;
        }
    }

    /** One generated name, the forms it was built from, and the probe groups the JDK is asked about. */
    private static final class Spelling {
        final String text;
        final Set<String> tags = new LinkedHashSet<String>();
        final boolean narrow;
        final String[] groups;

        Spelling(String text, boolean narrow, String... groups) {
            this.text = text;
            this.narrow = narrow;
            this.groups = groups;
        }
    }

    private static final class Probe {
        final String tree;
        final String path;
        final int tier;
        final FilePermission[] perm = new FilePermission[ACTIONS.length];

        Probe(String tree, String path, int tier) {
            this.tree = tree;
            this.path = path;
            this.tier = tier;
            for (int i = 0; i < ACTIONS.length; i++) perm[i] = new FilePermission(path, ACTIONS[i]);
        }
    }

    private static final class Coverage {
        final Set<String> trees = new LinkedHashSet<String>();
        final Set<Integer> tiers = new TreeSet<Integer>();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean lexical() {
        return !"true".equalsIgnoreCase(Security.getProperty("jdk.io.permissionsUseCanonicalPath"))
            && !"true".equalsIgnoreCase(System.getProperty("jdk.io.permissionsUseCanonicalPath"));
    }

    private static List<Tree> ruledTrees() {
        List<Tree> t = new ArrayList<Tree>();
        t.add(new Tree("DRIVE", "C", false, null, null, false));
        t.add(new Tree("USERS", "C", false, null, null, false, "Users"));
        t.add(new Tree("ONEHOME", "C", false, null, null, false, "Users", "alice"));
        String home = System.getProperty("user.home", "");
        if (home.length() > 3 && Character.isLetter(home.charAt(0)) && home.charAt(1) == ':'
                && home.charAt(2) == '\\') {
            t.add(new Tree("USERHOME", home.substring(0, 1), false, null, null, false,
                home.substring(3).split("\\\\")));
        }
        t.add(new Tree("HOME", "C", true, null, null, false, "home"));
        t.add(new Tree("HOMEU", "C", true, null, null, false, "home", "alice"));
        t.add(new Tree("ROOT", "C", true, null, null, false, "root"));
        t.add(new Tree("VARROOT", "C", true, null, null, false, "var", "root"));
        t.add(new Tree("SHARE", null, false, "server", "share", false));
        t.add(new Tree("HOST", null, false, "server", "zq82", false));
        return t;
    }

    /** Named directories below a ruled tree: rated 0 by ruling, in every spelling that is not a device name. */
    private static List<Tree> narrowTrees() {
        List<Tree> t = new ArrayList<Tree>();
        t.add(new Tree("N-DRIVE", "C", false, null, null, true, "data", "app"));
        t.add(new Tree("N-HOME", "C", false, null, null, true, "Users", "alice", "Documents"));
        t.add(new Tree("N-UNIX", "C", true, null, null, true, "home", "alice", "work"));
        t.add(new Tree("N-ROOT", "C", true, null, null, true, "root", "work"));
        t.add(new Tree("N-SHARE", null, false, "server", "share", true, "dir"));
        return t;
    }

    private static String join(String sep, List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append(sep).append(n);
        return sb.toString();
    }

    // ------------------------------------------------------------------ the JDK side

    private static List<String> probeBases(Tree t) {
        List<String> roots = new ArrayList<String>();
        if (t.unc()) {
            roots.add("\\\\" + t.host + "\\" + t.share);
        } else {
            roots.add(t.letter + ":");
            roots.add("");
        }
        List<String> bases = new ArrayList<String>();
        for (String r : roots) {
            bases.add(r + join(BACK, t.segs));
            for (int i = 0; i < t.segs.size(); i++) {
                List<String> dotted = new ArrayList<String>(t.segs);
                dotted.set(i, dotted.get(i) + ".");
                bases.add(r + join(BACK, dotted));
            }
        }
        return bases;
    }

    private static final String[] DEV_X = {"pipe", "a", ".", "UNC", "UNC.", VOLUME, "GLOBALROOT", "GLOBALROOT."};

    /** Device-namespace aliases of the ruled trees: {tree, name in a grant, name in a probe, root family}. */
    private static List<String[]> deviceAliases() {
        List<String[]> l = new ArrayList<String[]>();
        String[] volumes = {VOLUME, "GLOBALROOT/Device/HarddiskVolume3", "GLOBALROOT./Device/HarddiskVolume3",
            "GLOBALROOT/device/HarddiskVolume3", "GLOBALROOT/./Device/HarddiskVolume3", "./" + VOLUME};
        String[][] below = {{"DRIVE", ""}, {"USERS", "/Users"}, {"ONEHOME", "/Users/alice"}};
        for (String v : volumes) {
            for (String[] b : below) l.add(new String[]{b[0], v + b[1], v + b[1], v});
        }
        String[] hosts = {"UNC/server", "UNC./server", "./UNC/server", "GLOBALROOT/Device/Mup/server",
            "GLOBALROOT/device/Mup/server"};
        for (String h : hosts) {
            l.add(new String[]{"SHARE", h + "/share", h + "/share", h});
            l.add(new String[]{"HOST", h, h + "/zq82", h});
        }
        return l;
    }

    private static String deep(String base) {
        return base + join(BACK, Arrays.asList(DEEP));
    }

    private static void add(Map<String, List<Probe>> m, String group, Probe p) {
        List<Probe> l = m.get(group);
        if (l == null) m.put(group, l = new ArrayList<Probe>());
        l.add(p);
    }

    /** Probes by group: "" for names that are not in the device namespace, "D" for its direct names, else the hop. */
    private static Map<String, List<Probe>> probes(List<Tree> trees) {
        Map<String, List<Probe>> m = new LinkedHashMap<String, List<Probe>>();
        for (Tree t : trees) {
            for (String b : probeBases(t)) add(m, "", new Probe(t.id, deep(b) , TIER_DEEP));
        }
        for (String root : new String[]{"C:", ""}) add(m, "", new Probe("DRIVE", root + BACK + DIRECT, TIER_DIRECT_ROOT));
        for (String[] a : deviceAliases()) {
            String body = a[2].replace(FWD, BACK);
            add(m, "D", new Probe(a[0], deep("\\\\.\\" + body), TIER_DEEP));
            for (String x : DEV_X) {
                add(m, x.toLowerCase(Locale.ROOT), new Probe(a[0], deep("\\\\.\\" + x + "\\..\\" + body), TIER_DEEP));
            }
        }
        for (int k : CHAINS) {
            add(m, "chain", new Probe("ABOVE-CWD", deep("C:" + parents(BACK, k)), TIER_ABOVE_CWD));
            add(m, "chain", new Probe("ABOVE-CWD", deep(parents(BACK, k)), TIER_ABOVE_CWD));
        }
        return m;
    }

    private static String expandForJdk(String spelling) {
        return spelling.replace("${user.home}", System.getProperty("user.home")).replace("${/}", BACK);
    }

    private static Coverage covered(Spelling sp, int action, Map<String, List<Probe>> probes) {
        FilePermission grant = new FilePermission(expandForJdk(sp.text), ACTIONS[action]);
        Coverage c = new Coverage();
        for (String g : sp.groups) {
            List<Probe> l = probes.get(g);
            if (l == null) continue;
            for (Probe p : l) {
                if (grant.implies(p.perm[action])) {
                    c.trees.add(p.tree);
                    c.tiers.add(p.tier);
                }
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ the generator

    private static final int[] CHAINS = {1, 2, 6};

    /** {@code k} parent names joined by the separator. */
    private static String parents(String s, int k) {
        StringBuilder sb = new StringBuilder("..");
        for (int i = 1; i < k; i++) sb.append(s).append("..");
        return sb.toString();
    }

    /** Non-device root prefixes of a tree under one separator, each with its tag, ending before the first name. */
    private static Map<String, String> roots(Tree t, String s) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (t.id.equals("HOST")) return m;
        if (t.unc()) {
            String hs = t.host + s + t.share;
            m.put("unc-plain", s + s + hs + s);
            m.put("unc-three-separators", s + s + s + hs + s);
            m.put("unc-long", s + s + "?" + s + "UNC" + s + hs + s);
            m.put("unc-three-separators-long", s + s + s + "?" + s + "UNC" + s + hs + s);
            return m;
        }
        String d = t.letter + ":";
        m.put("plain", d + s);
        m.put("slash-before-drive", s + d + s);
        m.put("three-separators", s + s + s + d + s);
        m.put("long", s + s + "?" + s + d + s);
        m.put("three-separators-long", s + s + s + "?" + s + d + s);
        m.put("double-long", s + s + "?" + s + s + s + "?" + s + d + s);
        // one separator only: two in a row are a UNC prefix, and a name "\\data\\app" is a share, not a directory
        if (t.unix || t.id.equals("DRIVE") || t.id.equals("N-DRIVE")) m.put("root-relative", s.substring(0, 1));
        return m;
    }

    private static List<String[]> tails(String s) {
        List<String[]> l = new ArrayList<String[]>();
        l.add(new String[]{"wildcard", "-"});
        l.add(new String[]{"wildcard-dot", "-" + s + "."});
        l.add(new String[]{"wildcard-parent-chain", "-" + s + "x" + s + ".."});
        l.add(new String[]{"wildcard-trailing-separator", "-" + s});
        l.add(new String[]{"star", "*"});
        return l;
    }

    private static final String[] SEPARATORS = {BACK, FWD, BACK + BACK, FWD + BACK};

    private static void add(Map<String, Spelling> out, String text, boolean narrow, String[] groups, String... tags) {
        if (text.toLowerCase(Locale.ROOT).contains("zq")) throw new IllegalStateException(text);
        Spelling sp = out.get(text);
        if (sp == null) {
            sp = new Spelling(text, narrow, groups);
            out.put(text, sp);
        }
        for (String tag : tags) {
            if (tag != null) sp.tags.add(tag);
        }
    }

    private static List<Spelling> spellings(List<Tree> trees) {
        Map<String, Spelling> out = new LinkedHashMap<String, Spelling>();
        String[] plain = {""};
        for (Tree t : trees) {
            // a doubled separator is collapsed by File.normalize, so the prefix tests see it after the collapse
            for (String s : SEPARATORS) {
                for (Map.Entry<String, String> r : roots(t, s).entrySet()) {
                    for (int dot = 0; dot < 3; dot++) {
                        if (dot > 0 && (t.segs.isEmpty() || t.narrow)) continue;
                        if (dot == 2 && t.segs.size() == 1) continue;
                        List<String> names = new ArrayList<String>(t.segs);
                        if (dot > 0) {
                            int i = dot == 1 ? 0 : names.size() - 1;
                            names.set(i, names.get(i) + ".");
                        }
                        for (String[] tail : tails(s)) {
                            if (t.narrow && tail[0].equals("star")) continue;
                            String path = names.isEmpty() ? "" : join(s, names).substring(s.length()) + s;
                            add(out, r.getValue() + path + tail[1], t.narrow, plain, r.getKey(), tail[0],
                                dot == 0 ? null : dot == 1 ? "trailing-dot-first" : "trailing-dot-last");
                        }
                    }
                }
            }
            if (t.id.equals("USERHOME")) {
                for (String home : new String[]{"${user.home}${/}", "${user.home}/"}) {
                    for (String[] tail : tails(BACK)) add(out, home + tail[1], false, plain, "home-property", tail[0]);
                }
            }
        }
        return new ArrayList<Spelling>(out.values());
    }

    /** Device-namespace grants: the alias alone, behind a device name, and behind a device name and a "..". */
    private static List<Spelling> deviceSpellings() {
        Map<String, Spelling> out = new LinkedHashMap<String, Spelling>();
        for (String s : SEPARATORS) {
            for (String[] a : deviceAliases()) {
                List<String[]> forms = new ArrayList<String[]>();
                forms.add(new String[]{"device-direct", a[1], null, "D"});
                for (String x : DEV_X) {
                    forms.add(new String[]{"device-nohop", x + "/" + a[1], x, x.toLowerCase(Locale.ROOT)});
                    forms.add(new String[]{"device-hop", x + "/../" + a[1], x, x.toLowerCase(Locale.ROOT)});
                }
                for (String[] f : forms) {
                    String[] groups = f[3].equals("D") ? new String[]{"D"} : new String[]{"D", f[3]};
                    for (String[] tail : tails(s)) {
                        if (tail[0].equals("star") || tail[0].equals("wildcard-trailing-separator")) continue;
                        String text = s + s + "." + s + f[1].replace(FWD, s) + s + tail[1];
                        add(out, text, false, groups, f[0], f[2] == null ? null : "dev-x:" + f[2], "dev-alias:" + a[3],
                            tail[0]);
                    }
                }
            }
        }
        return new ArrayList<Spelling>(out.values());
    }

    /** Names that leave the working directory: a run of parent names, with and without a drive. */
    private static List<Spelling> chainSpellings() {
        Map<String, Spelling> out = new LinkedHashMap<String, Spelling>();
        String[] plain = {"chain"};
        for (String s : SEPARATORS) {
            Map<String, String> starts = new LinkedHashMap<String, String>();
            starts.put("relative-chain", "");
            starts.put("drive-relative-chain", "C:");
            starts.put("slash-before-drive-chain", s + "C:");
            starts.put("three-separators-chain", s + s + s + "C:");
            for (Map.Entry<String, String> r : starts.entrySet()) {
                for (int k : CHAINS) {
                    for (String[] tail : tails(s)) {
                        if (tail[0].equals("star") || tail[0].equals("wildcard-trailing-separator")) continue;
                        add(out, r.getValue() + parents(s, k) + s + tail[1], false, plain, r.getKey(), tail[0],
                            "chain-" + k);
                    }
                }
            }
        }
        return new ArrayList<Spelling>(out.values());
    }

    // ------------------------------------------------------------------ the tests

    private static List<String> mustBeCovered() {
        List<String> l = new ArrayList<String>(Arrays.asList("plain", "slash-before-drive", "three-separators", "long",
            "three-separators-long", "double-long", "root-relative", "home-property", "unc-plain",
            "unc-three-separators", "unc-long", "unc-three-separators-long", "wildcard", "wildcard-dot",
            "wildcard-parent-chain", "wildcard-trailing-separator", "star", "trailing-dot-first", "trailing-dot-last",
            "device-direct", "device-nohop", "device-hop", "relative-chain", "drive-relative-chain",
            "slash-before-drive-chain", "three-separators-chain"));
        for (String x : DEV_X) l.add("dev-x:" + x);
        for (String[] a : deviceAliases()) {
            if (!l.contains("dev-alias:" + a[3])) l.add("dev-alias:" + a[3]);
        }
        return l;
    }

    /**
     * Every spelling the JDK grants over a ruled whole tree is rated as the ruling requires, for read and for
     * write, and no narrow control is rated.  The failure message carries the counts, the rejection rate with
     * its denominator and the first misses.
     */
    @Test
    public void testEverySpellingTheJdkGrantsOverAWholeTreeIsRated() {
        Assume.assumeTrue("Windows only: the JDK reads these names by the Windows rules", windows());
        Assume.assumeTrue("lexical FilePermission only", lexical());
        List<Tree> ruled = ruledTrees();
        List<Tree> narrow = narrowTrees();
        List<Tree> all = new ArrayList<Tree>(ruled);
        all.addAll(narrow);
        Map<String, List<Probe>> probes = probes(ruled);
        List<Spelling> spellings = spellings(all);
        spellings.addAll(deviceSpellings());
        spellings.addAll(chainSpellings());
        int probeCount = 0;
        for (List<Probe> l : probes.values()) probeCount += l.size();
        int rows = 0, covered = 0, missed = 0, controls = 0, overRated = 0, controlsCovered = 0;
        int[] byTier = new int[TIER_NAMES.length];
        Map<String, int[]> byTag = new TreeMap<String, int[]>();
        List<String> misses = new ArrayList<String>();
        for (Spelling sp : spellings) {
            for (int a = 0; a < ACTIONS.length; a++) {
                rows++;
                Coverage c = covered(sp, a, probes);
                PermissionSets.Danger d = PermissionSets.danger(FILE, sp.text, ACTIONS[a]);
                int sev = d == null ? 0 : d.severity;
                if (sp.narrow) {
                    controls++;
                    if (!c.trees.isEmpty()) controlsCovered++;
                    if (sev != 0 || !c.trees.isEmpty()) {
                        overRated++;
                        if (misses.size() < 40) {
                            misses.add("CONTROL [" + sp.text + "] " + ACTIONS[a] + " severity " + sev + " tags "
                                + sp.tags + " grants " + c.trees);
                        }
                    }
                    continue;
                }
                if (c.tiers.isEmpty()) continue;
                covered++;
                int need = 0;
                for (int tier : c.tiers) {
                    need = Math.max(need, required(tier, a));
                    byTier[tier]++;
                }
                boolean miss = sev < need;
                if (miss) {
                    missed++;
                    if (misses.size() < 40) {
                        misses.add("[" + sp.text + "] " + ACTIONS[a] + " severity " + sev + " needs " + need + " tags "
                            + sp.tags + " grants " + c.trees);
                    }
                }
                for (String tag : sp.tags) {
                    int[] n = byTag.get(tag);
                    if (n == null) byTag.put(tag, n = new int[2]);
                    n[0]++;
                    if (miss) n[1]++;
                }
            }
        }
        String summary = "REJECTION RATE (rated below the ruling): " + missed + " / " + covered
            + " JDK-covered rows = " + (covered == 0 ? "n/a" : String.format(Locale.ROOT, "%.2f%%", 100.0 * missed / covered))
            + "; narrow controls rated or covered: " + overRated + " / " + controls
            + " rows; population " + spellings.size() + " generated spellings x " + ACTIONS.length + " actions = " + rows
            + " rows; trees " + all.size() + ", probes " + probeCount + ", covered rows by tier "
            + Arrays.asList(TIER_NAMES) + " = " + Arrays.toString(byTier) + "; JDK " + System.getProperty("java.home")
            + " (" + System.getProperty("java.version") + "); missed/covered by tag " + describe(byTag);
        System.out.println(summary);
        assertTrue("generator produced nothing: " + summary, spellings.size() > 1000);
        for (String tag : mustBeCovered()) {
            int[] n = byTag.get(tag);
            assertTrue("no spelling tagged " + tag + " is covered by the JDK, the instrument is blind to it: "
                + summary, n != null && n[0] > 0);
        }
        for (int tier = 0; tier < TIER_NAMES.length; tier++) {
            assertTrue("no row reaches the " + TIER_NAMES[tier] + " tier: " + summary, byTier[tier] > 0);
        }
        assertEquals("a narrow control implies a probe, so it is not narrow: " + summary, 0, controlsCovered);
        assertEquals(summary + "\nFirst misses:\n" + String.join("\n", misses), 0, missed + overRated);
    }

    private static String describe(Map<String, int[]> byTag) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, int[]> e : byTag.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()[1]).append('/').append(e.getValue()[0]).append(' ');
        }
        return sb.append('}').toString();
    }

    /**
     * The JDK side is not constant: it covers the names that are whole trees, the direct children of a root
     * and the parent chains, and not the narrow ones, so a pass above is not a pass of an instrument that
     * sees nothing.
     */
    @Test
    public void testTheJdkSideCoversWholeTreesAndNotNarrowOnes() {
        Assume.assumeTrue("Windows only", windows());
        Assume.assumeTrue("lexical FilePermission only", lexical());
        Map<String, List<Probe>> probes = probes(ruledTrees());
        String[] groups = {"", "D", "pipe", "unc", "a"};
        for (String whole : new String[]{"C:\\-", "C:\\Users\\-", "C:\\Users\\alice\\-", "C:\\Users.\\alice\\-",
                "C:\\-\\.", "\\\\\\?\\C:\\Users\\-", "\\\\.\\UNC\\server\\share\\-", "\\home\\-", "\\\\server\\share\\-",
                "\\\\.\\pipe\\UNC\\server\\share\\-", "\\\\.\\a\\Volume{00000000-0000-0000-0000-000000000000}\\-"}) {
            assertFalse(whole, covered(new Spelling(whole, false, groups), 0, probes).trees.isEmpty());
        }
        assertEquals("C:\\* reaches a root's own files", new TreeSet<Integer>(Arrays.asList(TIER_DIRECT_ROOT)),
            covered(new Spelling("C:\\*", false, groups), 0, probes).tiers);
        assertEquals("C:..\\..\\- leaves the working directory", new TreeSet<Integer>(Arrays.asList(TIER_ABOVE_CWD)),
            covered(new Spelling("C:..\\..\\-", false, "chain"), 0, probes).tiers);
        for (String narrow : new String[]{"C:\\Users\\alice\\Documents\\-", "C:\\Users\\bob\\-", "C:\\data\\-",
                "\\\\server\\share\\dir\\-", "C:\\Users\\alice\\*", "C:\\Users\\*", "\\\\server\\-", "\\\\.\\UNC\\other\\-"}) {
            assertTrue(narrow, covered(new Spelling(narrow, false, groups), 0, probes).trees.isEmpty());
        }
    }

    /**
     * Operator ruling 2026-11-07 (Opus review 2026-11-06, F7): {@code C:\Documents and Settings\...} is read as
     * {@code C:\Users\...} and {@code \\host\C$\...} as {@code C:\...}, on any host.  This is not a differential
     * and does not say the JDK agrees: {@code FilePermission} compares names lexically and knows neither a
     * junction nor an admin share, so no spelling below is covered by any probe of the ruled trees, which is why
     * the generators above do not produce them.  Each grant is shown to be a real wildcard (it implies a file
     * below its own name, so the instrument is not blind for want of a wildcard), the JDK to imply none of the
     * ruled trees, and the catalogue to rate it HIGH to read and to write by the ruling.  The premise, that the OS
     * resolves the alias to the tree, is the review's measurement ({@code Files.isSameFile}, live), not this test's.
     */
    @Test
    public void testTheOsAliasesAreRatedByRulingAndNotScoredAgainstTheJdk() {
        Assume.assumeTrue("Windows only: the JDK reads these names by the Windows rules", windows());
        Assume.assumeTrue("lexical FilePermission only", lexical());
        Map<String, List<Probe>> probes = probes(ruledTrees());
        String[] groups = {"", "D", "pipe", "unc", "a"};
        String below = "\\zq81\\deep\\f.txt";
        String[][] aliases = {
            {"C:\\Documents and Settings\\-", "C:\\Documents and Settings" + below},
            {"C:\\Documents and Settings\\alice\\-", "C:\\Documents and Settings\\alice" + below},
            {"\\Documents and Settings\\-", "\\Documents and Settings" + below},
            {"\\\\localhost\\C$\\-", "\\\\localhost\\C$" + below},
            {"\\\\localhost\\C$\\Users\\-", "\\\\localhost\\C$\\Users" + below},
            {"\\\\h\\d$\\Users\\alice\\-", "\\\\h\\d$\\Users\\alice" + below},
            {"\\\\h\\C$\\Documents and Settings\\-", "\\\\h\\C$\\Documents and Settings" + below}};
        for (String[] a : aliases) {
            for (int act = 0; act < ACTIONS.length; act++) {
                assertTrue(a[0] + " is a wildcard", new FilePermission(a[0], ACTIONS[act])
                    .implies(new FilePermission(a[1], ACTIONS[act])));
                assertTrue(a[0] + " " + ACTIONS[act] + ": the JDK reads the alias as a name of its own",
                    covered(new Spelling(a[0], false, groups), act, probes).trees.isEmpty());
                PermissionSets.Danger d = PermissionSets.danger(FILE, a[0], ACTIONS[act]);
                assertNotNull(a[0] + " " + ACTIONS[act], d);
                assertEquals(a[0] + " " + ACTIONS[act], PermissionSets.HIGH, d.severity);
            }
        }
        // the aliases of a named directory are as narrow as the directory, and the ordinary share is no drive
        for (String narrow : new String[]{"C:\\Documents and Settings\\alice\\Documents\\-",
                "\\\\localhost\\C$\\Users\\alice\\Documents\\-", "\\\\h\\share\\Users\\-", "\\\\h\\ADMIN$\\Users\\-"}) {
            assertTrue(narrow, covered(new Spelling(narrow, false, groups), 0, probes).trees.isEmpty());
            assertNull(narrow, PermissionSets.danger(FILE, narrow, "read"));
        }
    }
}
