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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.apache.river.tool.ProxyPolicyGenerator.EntryDiff;
import org.apache.river.tool.ProxyPolicyGenerator.GenerationException;

/**
 * Moves reviewed, generated {@code PERMISSIONS.LIST} files into the
 * {@code -dl} modules' source trees, in two steps with a human between them.
 *
 * <p>A jar's own {@code META-INF/PERMISSIONS.LIST} is its <i>request</i> for
 * permissions: one term of the effective grant, capped by what the jar
 * declares it needs.  So promotion never writes outside the module roots and
 * never widens what was reviewed:
 * <ol>
 *   <li>{@link #plan} reads the generated lists and the module trees and
 *       writes a plan file: per artefact the target path, the entries added and
 *       removed against the checked-in list, and the dropped and held notes.
 *       It changes nothing under the module roots.</li>
 *   <li>{@link #apply} treats the plan file as a record of what the human read,
 *       never as an instruction.  It re-derives the plan in memory from the
 *       generated lists and the module roots and writes only an approved
 *       artefact whose re-derived entry is identical to the one in the plan
 *       file, whose target is a {@code src/main/resources/META-INF/PERMISSIONS.LIST}
 *       under a module root, and which does not remove or hold entries unless
 *       allowed.</li>
 * </ol>
 * Parsing, comparison and validation are those of {@link ProxyPolicyGenerator}.
 */
final class ListPromotion {

    static final String PLAN_HEADER = "# ProxyPolicyGenerator promotion plan, format 2";
    static final String NEW_FILE = "NEW FILE";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String TARGET_PATH = "src/main/resources/META-INF/PERMISSIONS.LIST";
    private static final Path TARGET_TAIL =
            Paths.get("src", "main", "resources", "META-INF", "PERMISSIONS.LIST");
    private static final Pattern MODULE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /** Moves a staged file into place. */
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    /**
     * Test seam for the final moves of {@link #apply}; the default replaces the
     * target.  The rollback does not go through it.
     */
    static volatile Mover mover = new Mover() {
        public void move(Path from, Path to) throws IOException {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    };

    private ListPromotion() {
    }

    /** One artefact of a plan. */
    static final class Item {
        final String artefact;
        File generated;
        String generatedSha;
        File target;
        /** Hex SHA-256 of the checked-in list, or {@link #NEW_FILE}. */
        String targetSha;
        int live;
        int held;
        int dropped;
        /** Which kind of policy produced the list, or {@code unknown ...}. */
        String sourceKind = "unknown";
        /** The list fails the loader's parse. */
        boolean unparsable;
        /**
         * Why the list cannot be one this tool generated (it holds an entry of a
         * class the generator never writes, or a control character); the CI gate
         * fails on it.  {@code null} if nothing of the kind was found.
         */
        String forbidden;
        final List<String> added = new ArrayList<String>();
        final List<String> removed = new ArrayList<String>();
        final List<String> notes = new ArrayList<String>();
        /** {@code null} if the artefact may be promoted. */
        String refusal;

        Item(String artefact) {
            this.artefact = artefact;
        }

        boolean isNew() {
            return NEW_FILE.equals(targetSha);
        }
    }

    /** The result of {@link #plan}. */
    static final class Plan {
        final List<Item> items = new ArrayList<Item>();
        /** Things the reviewer should know that are not about one artefact. */
        final List<String> warnings = new ArrayList<String>();

        String summary() {
            StringBuilder sb = new StringBuilder();
            int added = 0, removed = 0, promotable = 0;
            for (Item i : items) {
                sb.append("plan ").append(i.artefact).append(": ");
                if (i.refusal != null) {
                    sb.append("REFUSED, ").append(ProxyPolicyGenerator.escaped(i.refusal));
                } else {
                    promotable++;
                    added += i.added.size();
                    removed += i.removed.size();
                    sb.append(i.isNew() ? NEW_FILE : "existing list").append(" ").append(i.target)
                      .append(" +").append(i.added.size()).append(" -").append(i.removed.size());
                }
                sb.append(", held ").append(i.held).append(", dropped ").append(i.dropped)
                  .append(", source kind: ").append(i.sourceKind).append('\n');
            }
            sb.append("plan: ").append(items.size()).append(" artefact(s), ").append(promotable)
              .append(" promotable, ").append(items.size() - promotable).append(" refused, +")
              .append(added).append(" -").append(removed).append(" entries\n");
            for (String w : warnings) sb.append("plan WARNING: ").append(w).append('\n');
            return sb.toString();
        }
    }

    /** The outcome of {@link #check}: the report, and whether the CI gate failed. */
    static final class CheckResult {
        final String report;
        final boolean failed;

        CheckResult(String report, boolean failed) {
            this.report = report;
            this.failed = failed;
        }
    }

    // ------------------------------------------------------------------ plan

    /**
     * Writes the plan for the generated lists under {@code generatedDir}
     * against the {@code -dl} modules under {@code moduleRoots}.  Only
     * {@code planFile} is written.
     *
     * @throws GenerationException if there are no generated lists, or
     *         {@code planFile} lies under a module root
     */
    static Plan plan(File generatedDir, List<File> moduleRoots, Map<String, String> artefactMap,
            File planFile) throws IOException {
        requireOutsideRoots(planFile, moduleRoots, "plan file");
        Plan plan = derive(generatedDir, moduleRoots, artefactMap);
        plan.warnings.addAll(leftoverStaging(planFile));
        write(plan, generatedDir, moduleRoots, planFile);
        return plan;
    }

    /**
     * The CI gate: derives the plan in memory, writes no file, prints the ADDED
     * entries and fails on any artefact with REMOVED entries or a list that
     * does not parse.
     */
    static CheckResult check(File generatedDir, List<File> moduleRoots,
            Map<String, String> artefactMap) throws IOException {
        Plan plan = derive(generatedDir, moduleRoots, artefactMap);
        StringBuilder sb = new StringBuilder(plan.summary());
        boolean failed = false;
        for (Item i : plan.items) {
            if (!i.added.isEmpty()) {
                sb.append("check ").append(i.artefact).append(": ADDED ").append(i.added.size())
                  .append('\n');
                for (String e : i.added) sb.append("  + ").append(ProxyPolicyGenerator.escaped(e)).append('\n');
            }
            if (!i.removed.isEmpty()) {
                failed = true;
                sb.append("check FAIL ").append(i.artefact).append(": REMOVED ")
                  .append(i.removed.size()).append('\n');
                for (String e : i.removed) sb.append("  - ").append(ProxyPolicyGenerator.escaped(e)).append('\n');
            }
            if (i.forbidden != null) {
                failed = true;
                sb.append("check FAIL ").append(i.artefact).append(": the list was edited: ")
                  .append(ProxyPolicyGenerator.escaped(i.forbidden)).append('\n');
            }
            if (i.unparsable) {
                failed = true;
                sb.append("check FAIL ").append(i.artefact).append(": a list does not parse: ")
                  .append(ProxyPolicyGenerator.escaped(i.refusal)).append('\n');
            }
        }
        sb.append(failed ? "check: FAILED\n" : "check: ok\n");
        return new CheckResult(sb.toString(), failed);
    }

    /** The plan as the planner derives it now; reads the trees, changes nothing. */
    static Plan derive(File generatedDir, List<File> moduleRoots, Map<String, String> artefactMap)
            throws IOException {
        Map<String, File> generated = ProxyPolicyGenerator.generatedLists(generatedDir);
        if (generated.isEmpty()) {
            throw new GenerationException("no generated lists under " + generatedDir);
        }
        Map<String, List<File>> modules = new TreeMap<String, List<File>>();
        for (File root : moduleRoots) findModules(realRoot(root), modules);

        Plan plan = new Plan();
        for (Map.Entry<String, File> g : generated.entrySet()) {
            plan.items.add(item(g.getKey(), g.getValue(), generatedDir, modules, artefactMap));
        }
        // Two artefacts for one module (a stale version-suffixed list beside the current one, or
        // two map entries) would write the same file twice and the last approval would win.
        Map<String, Item> byTarget = new LinkedHashMap<String, Item>();
        for (Item i : plan.items) {
            if (i.refusal != null || i.target == null) continue;
            String key = canonicalForWrite(i.target).toString().toLowerCase(java.util.Locale.ROOT);
            Item first = byTarget.get(key);
            if (first == null) {
                byTarget.put(key, i);
            } else {
                i.refusal = "ambiguous target: " + i.target + " is also the target of "
                        + first.artefact + "; promote one of " + first.artefact + " and "
                        + i.artefact + " (remove the stale list from the generated directory)";
            }
        }
        return plan;
    }

    /** A {@code .promote-staging-*} directory beside the plan: what an interrupted apply leaves. */
    static List<String> leftoverStaging(File planFile) {
        List<String> found = new ArrayList<String>();
        File[] kids = planFile.getAbsoluteFile().getParentFile().listFiles();
        if (kids == null) return found;
        Arrays.sort(kids);
        for (File k : kids) {
            if (k.isDirectory() && k.getName().startsWith(".promote-staging-")) {
                found.add(k + " is a leftover staging directory of an interrupted apply: it holds"
                        + " that apply's backups and unmoved new lists and lies outside the module"
                        + " roots, so it left nothing stray under them; but some targets may"
                        + " already have been replaced, so read this plan's +/- against them,"
                        + " then delete the directory by hand");
            }
        }
        return found;
    }

    private static void requireOutsideRoots(File file, List<File> roots, String what)
            throws IOException {
        Path p = canonicalForWrite(file);
        for (File root : roots) {
            if (p.startsWith(realRoot(root))) {
                throw new GenerationException(what + " " + file + " lies under module root "
                        + root + "; a plan must not change the source tree");
            }
        }
    }

    private static Path realRoot(File root) throws IOException {
        try {
            return root.toPath().toRealPath();
        } catch (IOException e) {
            throw new GenerationException("module root " + root + " cannot be resolved: " + e);
        }
    }

    private static Item item(String artefact, File list, File generatedDir,
            Map<String, List<File>> modules, Map<String, String> artefactMap) throws IOException {
        Item item = new Item(artefact);
        item.generated = list.getAbsoluteFile();
        item.generatedSha = ProxyPolicyGenerator.sha256Hex(list);
        item.sourceKind = sourceKind(list, generatedDir);
        List<String> live = ProxyPolicyGenerator.liveEntries(list);
        item.live = live.size();
        item.forbidden = forbidden(list, live);
        for (String line : Files.readAllLines(list.toPath(), UTF8)) {
            if (line.startsWith("# DROPPED")) {
                item.dropped++;
                item.notes.add(line);
            } else if (line.startsWith("# HELD")) {
                item.held++;
                item.notes.add(line);
            }
        }
        String key = artefactMap.containsKey(artefact) ? artefactMap.get(artefact)
                : ProxyPolicyGenerator.moduleKey(modules.keySet(), artefact);
        List<File> mods = modules.get(key);
        if (mods == null) {
            item.refusal = "no -dl module named " + key + " under the module roots"
                    + (artefactMap.containsKey(artefact) ? " (named by the artefact map)" : "");
            return item;
        }
        if (mods.size() > 1) {
            item.refusal = "ambiguous: " + mods.size() + " modules named " + key + ": " + mods;
            return item;
        }
        File module = mods.get(0);
        item.target = new File(module, TARGET_PATH);
        if (!canonicalForWrite(item.target).startsWith(module.toPath())) {
            item.refusal = "the target resolves outside its module " + module
                    + " (a link inside the module)";
            return item;
        }
        if (live.isEmpty()) {
            item.refusal = "zero live entries: an empty list requests the same as none and"
                    + " would remove the reviewed entries; never promoted";
        } else if (!hasProvenance(list)) {
            item.refusal = "no provenance line: not a list this tool generated";
        } else if (item.forbidden != null) {
            item.refusal = "the list was edited: " + item.forbidden;
        } else {
            try {
                ProxyPolicyGenerator.validateList(list);
            } catch (GenerationException e) {
                item.refusal = e.getMessage();
                item.unparsable = true;
            }
        }
        if (item.target.isFile()) {
            item.targetSha = ProxyPolicyGenerator.sha256Hex(item.target);
            EntryDiff d = ProxyPolicyGenerator.compare(live, item.target);
            item.added.addAll(d.added.values());
            item.removed.addAll(d.removed.values());
            item.notes.addAll(handWrittenComments(item.target));
        } else {
            item.targetSha = NEW_FILE;
            item.added.addAll(live);
        }
        return item;
    }

    /**
     * Whether {@code list} holds what the generator never writes: a control or
     * format character on any line (checked as generation checks its values), or
     * a live entry of a class it excludes.  Reuses the generator's own rules.
     *
     * @return the reason, or {@code null}
     */
    private static String forbidden(File list, List<String> live) throws IOException {
        int n = 0;
        for (String line : Files.readAllLines(list.toPath(), UTF8)) {
            n++;
            try {
                ProxyPolicyGenerator.requireNoControl("line " + n + " of the list", line);
            } catch (GenerationException e) {
                return e.getMessage();
            }
        }
        for (String entry : live) {
            String type = ProxyPolicyGenerator.entryType(entry);
            String why = type == null ? null : ProxyPolicyGenerator.excludedReason(type);
            if (why != null) {
                return "it holds an entry of " + type + " (" + why + "), which the generator"
                        + " never writes";
            }
        }
        return null;
    }

    private static boolean hasProvenance(File list) throws IOException {
        for (String line : Files.readAllLines(list.toPath(), UTF8)) {
            if (line.startsWith("# Source policy: ")) return true;
        }
        return false;
    }

    /**
     * Which kind of policy produced a list, from the policy file its header
     * names when that file lies beside the generated lists and is the file the
     * header's SHA-256 describes; otherwise {@code unknown}.  Never a guess: a
     * policy is called a template only if it holds {@code ${...}}, and
     * polpAudit-generated only if a comment names polpAudit.
     */
    static String sourceKind(File list, File generatedDir) throws IOException {
        ProxyPolicyGenerator.ListSource src = ProxyPolicyGenerator.listSource(list);
        if (src.policy == null) return "unknown (no provenance line)";
        if (src.policy.isEmpty() || !new File(src.policy).getName().equals(src.policy)) {
            return "unknown (the recorded policy name is not a plain file name)";
        }
        File base = generatedDir.getAbsoluteFile();
        File policy = new File(base.getParentFile(), src.policy);
        if (!policy.isFile()) policy = new File(base, src.policy);
        if (!policy.isFile()) {
            return "unknown (source policy " + src.policy + " not found beside the lists)";
        }
        if (src.sha256 == null || !src.sha256.equals(ProxyPolicyGenerator.sha256Hex(policy))) {
            return "unknown (source policy " + src.policy + " is not the file the list was made from)";
        }
        boolean audit = false;
        for (String line : Files.readAllLines(policy.toPath(), UTF8)) {
            String t = line.trim();
            if (t.contains("${")) return "hand-written or template policy (holds ${...} placeholders)";
            if ((t.startsWith("//") || t.startsWith("/*") || t.startsWith("*"))
                    && t.contains("polpAudit")) {
                audit = true;
            }
        }
        return audit ? "polpAudit-generated policy (a comment names polpAudit)"
                : "unknown (the policy file carries no mark of its origin)";
    }

    /**
     * Comment lines of a checked-in list after its leading comment block (the
     * licence), which promotion replaces: a reviewer is told what is lost.
     */
    private static List<String> handWrittenComments(File target) throws IOException {
        List<String> found = new ArrayList<String>();
        boolean leading = true;
        for (String line : Files.readAllLines(target.toPath(), UTF8)) {
            String t = line.trim();
            boolean comment = t.startsWith("#") || t.startsWith("//");
            if (t.isEmpty() || (leading && comment)) continue;
            leading = false;
            if (comment) found.add("COMMENT NOT CARRIED OVER (checked-in list): " + t);
        }
        return found;
    }

    /**
     * A directory whose name ends {@code -dl} and that holds a pom or a src
     * directory.  {@code dir} is a real path; a child that is a link or
     * junction (its real path is not {@code dir/name}) is never entered, and a
     * module that is one is refused.
     */
    private static void findModules(Path dir, Map<String, List<File>> found) throws IOException {
        File[] children = dir.toFile().listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File c : children) {
            String n = c.getName();
            if (!c.isDirectory() || n.equals("target") || n.equals("build") || n.equals(".git")
                    || n.equals("node_modules") || n.endsWith(".proxy-permissions")) continue;
            Path expected = dir.resolve(n);
            boolean link = !c.toPath().toRealPath().equals(expected);
            boolean module = n.endsWith("-dl") && (new File(c, "pom.xml").isFile()
                    || new File(c, "src").isDirectory());
            if (link) {
                if (module) {
                    throw new GenerationException("module directory " + c + " is a symbolic link"
                            + " or junction (it resolves to " + c.toPath().toRealPath()
                            + "); refused");
                }
                continue;
            }
            if (module) {
                List<File> mods = found.get(n);
                if (mods == null) {
                    mods = new ArrayList<File>();
                    found.put(n, mods);
                }
                mods.add(expected.toFile());
            } else {
                findModules(expected, found);
            }
        }
    }

    // ---------------------------------------------------------- artefact map

    /**
     * Reads a reviewed, checked-in map of generated artefact names that cannot
     * be guessed to the {@code -dl} module directory they belong to: lines
     * {@code sanitised-name = module-dir-name}; blank lines and {@code #}
     * comments are skipped.
     */
    static Map<String, String> loadArtefactMap(File file) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        int n = 0;
        for (String line : Files.readAllLines(file.toPath(), UTF8)) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            String k = eq < 0 ? "" : t.substring(0, eq).trim();
            String v = eq < 0 ? "" : t.substring(eq + 1).trim();
            if (k.isEmpty() || !MODULE_NAME.matcher(v).matches()
                    || v.contains("..") || k.indexOf('/') >= 0 || k.indexOf('\\') >= 0) {
                throw new GenerationException(file + ":" + n
                        + ": expected 'sanitised-name = module-dir-name', got: " + t);
            }
            String old = map.put(k, v);
            if (old != null && !old.equals(v)) {
                throw new GenerationException(file + ":" + n + ": " + k + " is mapped twice ("
                        + old + ", " + v + ")");
            }
        }
        return map;
    }

    // ------------------------------------------------------------ plan file

    private static String render(Item i) {
        StringBuilder sb = new StringBuilder();
        sb.append("ARTEFACT ").append(i.artefact).append('\n');
        sb.append("  status: ").append(i.refusal == null ? "PROMOTABLE"
                : "REFUSED: " + ProxyPolicyGenerator.escaped(i.refusal))
          .append('\n');
        sb.append("  generated: ").append(i.generated).append('\n');
        sb.append("  generated-sha256: ").append(i.generatedSha).append('\n');
        sb.append("  source kind: ").append(i.sourceKind).append('\n');
        sb.append("  held: ").append(i.held).append('\n');
        sb.append("  dropped: ").append(i.dropped).append('\n');
        if (i.target != null) {
            sb.append("  target: ").append(i.target).append('\n');
            sb.append("  target-sha256: ").append(i.targetSha).append('\n');
            sb.append("  live-entries: ").append(i.live).append('\n');
            sb.append("  ADDED: ").append(i.added.size())
              .append(i.isNew() ? " (" + NEW_FILE + ")" : "").append('\n');
            for (String e : i.added) sb.append("    + ").append(ProxyPolicyGenerator.escaped(e)).append('\n');
            sb.append("  REMOVED: ").append(i.removed.size()).append('\n');
            for (String e : i.removed) sb.append("    - ").append(ProxyPolicyGenerator.escaped(e)).append('\n');
            for (String n : i.notes) sb.append("  note: ").append(ProxyPolicyGenerator.escaped(n)).append('\n');
        }
        sb.append("END\n");
        return sb.toString();
    }

    private static void write(Plan plan, File generatedDir, List<File> roots, File planFile)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(PLAN_HEADER).append('\n');
        sb.append("# Created: ").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .format(new Date())).append('\n');
        sb.append("# Generated lists: ").append(generatedDir.getAbsolutePath()).append('\n');
        sb.append("# Module roots: ").append(roots).append('\n');
        sb.append("# This plan changed nothing under the module roots.\n");
        for (String w : plan.warnings) sb.append("# WARNING: ").append(w).append('\n');
        sb.append("# A list is the jar's own request for permissions: review each artefact below\n");
        sb.append("# against the audit before approving it.  This file is a record of what you\n");
        sb.append("# read, not an instruction: apply re-derives every approved artefact from the\n");
        sb.append("# generated lists and the module roots and refuses one that differs from it.\n");
        sb.append("# Apply with the same --out and module roots, and the SHA-256 of this file:\n");
        sb.append("#   --promote-apply <this file> --approve-plan-sha <sha> --approve <artefact>[,...]\n");
        sb.append("# An artefact with REMOVED entries also needs --allow-removals, one with held\n");
        sb.append("# entries --allow-held.\n");
        for (Item i : plan.items) sb.append('\n').append(render(i));
        File parent = planFile.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create directory: " + parent);
        }
        Files.write(planFile.toPath(), sb.toString().getBytes(UTF8));
    }

    /** The ARTEFACT blocks of plan-file text, by artefact, each as {@link #render} writes it. */
    private static Map<String, String> readBlocks(File planFile, byte[] bytes) throws IOException {
        String[] lines = new String(bytes, UTF8).split("\r?\n", -1);
        if (lines.length == 0 || !PLAN_HEADER.equals(lines[0])) {
            throw new GenerationException(planFile + " is not a promotion plan (format 2)");
        }
        Map<String, String> blocks = new LinkedHashMap<String, String>();
        String name = null;
        StringBuilder cur = null;
        for (String line : lines) {
            if (line.startsWith("ARTEFACT ")) {
                name = line.substring("ARTEFACT ".length()).trim();
                cur = new StringBuilder();
                cur.append(line).append('\n');
            } else if (cur != null) {
                cur.append(line).append('\n');
                if (line.equals("END")) {
                    blocks.put(name, cur.toString());
                    cur = null;
                }
            }
        }
        return blocks;
    }

    private static String field(String block, String key) {
        String prefix = "  " + key + ": ";
        for (String line : block.split("\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return null;
    }

    // ----------------------------------------------------------------- apply

    /**
     * Writes the approved artefacts of a plan into their module trees.  The
     * plan file is never trusted for what to write: it must be the file whose
     * SHA-256 the reviewer approved, and every approved artefact is re-derived
     * from {@code generatedDir} and {@code moduleRoots} and must equal its
     * entry in the plan file.  Every approved artefact is checked first and
     * none is written if any is refused.  The new content is staged under the
     * plan file's directory, then moved into place; if a move fails the targets
     * already moved are restored from backups kept in the staging directory.
     *
     * @return one line per artefact written
     * @throws GenerationException if the plan is not the approved one, an
     *         artefact is unknown, refused, stale, differs from its re-derived
     *         entry, targets anything but a {@code PERMISSIONS.LIST} under a
     *         module root, would remove entries without {@code allowRemovals}
     *         or hold entries without {@code allowHeld}
     */
    static String apply(File planFile, String approvedSha, File generatedDir,
            List<File> moduleRoots, Map<String, String> artefactMap, List<String> approve,
            boolean allowRemovals, boolean allowHeld) throws IOException {
        byte[] planBytes = Files.readAllBytes(planFile.toPath());
        String planSha = ProxyPolicyGenerator.sha256Hex(planBytes);
        if (approvedSha == null || !planSha.equalsIgnoreCase(approvedSha.trim())) {
            throw new GenerationException("the plan file " + planFile + " has SHA-256 " + planSha
                    + (approvedSha == null ? "; --approve-plan-sha is required"
                        : ", not the approved " + approvedSha.trim())
                    + "; the plan you read is not the plan being applied");
        }
        requireOutsideRoots(planFile, moduleRoots, "plan file");
        Map<String, String> blocks = readBlocks(planFile, planBytes);
        Map<String, Item> derived = new LinkedHashMap<String, Item>();
        for (Item i : derive(generatedDir, moduleRoots, artefactMap).items) {
            derived.put(i.artefact, i);
        }
        List<Path> roots = new ArrayList<Path>();
        for (File r : moduleRoots) roots.add(realRoot(r));

        List<String> problems = new ArrayList<String>();
        List<Item> toWrite = new ArrayList<Item>();
        for (String name : approve) {
            Item i = check(name, blocks.get(name), derived.get(name), planFile, roots,
                    allowRemovals, allowHeld, problems);
            if (i != null) toWrite.add(i);
        }
        if (!problems.isEmpty()) {
            throw new GenerationException("nothing was written: " + problems);
        }
        StringBuilder warn = new StringBuilder();
        for (String w : leftoverStaging(planFile)) warn.append("WARNING: ").append(w).append('\n');
        return write(toWrite, planFile) + warn;
    }

    /** @return the re-derived item, or {@code null} after adding the reasons to {@code problems} */
    private static Item check(String name, String block, Item d, File planFile, List<Path> roots,
            boolean allowRemovals, boolean allowHeld, List<String> problems) throws IOException {
        String who = name + ": ";
        if (block == null) {
            problems.add(who + "not in the plan " + "(a plan file lists no such artefact)");
            return null;
        }
        if (d == null) {
            problems.add(who + "no generated list of that name any more; make a new plan");
            return null;
        }
        String claimed = field(block, "target");
        if (claimed != null) {
            String why = targetProblem(new File(claimed), roots);
            if (why != null) {
                problems.add(who + "the plan file's target " + claimed + " " + why);
                return null;
            }
        }
        if (d.refusal != null) {
            problems.add(who + "the plan refuses it: " + d.refusal);
            return null;
        }
        String why = targetProblem(d.target, roots);
        if (why != null) {
            problems.add(who + "the target " + d.target + " " + why);
            return null;
        }
        if (planFile.lastModified() < d.generated.lastModified()
                || !d.generatedSha.equals(field(block, "generated-sha256"))) {
            problems.add(who + "the plan is older than the generated list " + d.generated
                    + "; make a new plan");
            return null;
        }
        if (!d.targetSha.equals(field(block, "target-sha256"))) {
            problems.add(who + "the target " + d.target + " changed since the plan was made"
                    + "; make a new plan");
            return null;
        }
        if (!render(d).equals(block)) {
            problems.add(who + "the plan file's entry differs from what the planner derives from "
                    + "the generated list and the module roots now; make a new plan and read it");
            return null;
        }
        if (d.held > 0 && !allowHeld) {
            problems.add(who + d.held + " held entr" + (d.held == 1 ? "y" : "ies")
                    + " (host- or run-specific, written commented out); review them and"
                    + " pass --allow-held");
            return null;
        }
        if (!d.removed.isEmpty() && !allowRemovals) {
            problems.add(who + d.removed.size() + " checked-in entr"
                    + (d.removed.size() == 1 ? "y" : "ies") + " would be REMOVED "
                    + d.removed + "; review them and pass --allow-removals");
            return null;
        }
        return d;
    }

    /**
     * @return why {@code target} may not be written, or {@code null} if its
     *         canonical path is under a canonical module root and ends in
     *         {@code src/main/resources/META-INF/PERMISSIONS.LIST}
     */
    private static String targetProblem(File target, List<Path> roots) throws IOException {
        Path canon = canonicalForWrite(target);
        boolean under = false;
        for (Path r : roots) {
            if (canon.startsWith(r)) under = true;
        }
        if (!under) return "is not under a module root (resolves to " + canon + ")";
        if (!canon.endsWith(TARGET_TAIL)) {
            return "does not end with " + TARGET_PATH + " (resolves to " + canon + ")";
        }
        return null;
    }

    /** The real path of {@code f}, resolving its deepest existing ancestor; {@code f} need not exist. */
    static Path canonicalForWrite(File f) throws IOException {
        Path p = f.getAbsoluteFile().toPath().normalize();
        LinkedList<Path> rest = new LinkedList<Path>();
        Path existing = p;
        while (existing != null && !Files.exists(existing)) {
            rest.addFirst(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) return p;
        Path real = existing.toRealPath();
        for (Path r : rest) real = real.resolve(r);
        return real;
    }

    private static final class Staged {
        final Item item;
        final String eol;
        final File fresh;
        final File backup;
        /** Highest directory this run created for the target, or {@code null}. */
        File createdDir;

        Staged(Item item, String eol, File fresh, File backup) {
            this.item = item;
            this.eol = eol;
            this.fresh = fresh;
            this.backup = backup;
        }
    }

    private static String write(List<Item> items, File planFile) throws IOException {
        File staging = new File(planFile.getAbsoluteFile().getParentFile(),
                ".promote-staging-" + System.nanoTime());
        boolean keep = false;
        try {
            if (!staging.mkdirs()) throw new IOException("Unable to create directory: " + staging);
            List<Staged> staged = new ArrayList<Staged>();
            int n = 0;
            for (Item i : items) {
                n++;
                String eol = "\n";
                File backup = null;
                if (i.target.isFile()) {
                    byte[] cur = Files.readAllBytes(i.target.toPath());
                    if (!i.targetSha.equals(ProxyPolicyGenerator.sha256Hex(cur))) {
                        throw new GenerationException(i.artefact + ": the target " + i.target
                                + " changed while promoting; nothing was written");
                    }
                    eol = eolOf(cur);
                    backup = new File(staging, "backup-" + n);
                    Files.write(backup.toPath(), cur);
                } else if (!i.isNew()) {
                    throw new GenerationException(i.artefact + ": the target " + i.target
                            + " is gone; nothing was written");
                }
                byte[] gen = Files.readAllBytes(i.generated.toPath());
                if (!i.generatedSha.equals(ProxyPolicyGenerator.sha256Hex(gen))) {
                    throw new GenerationException(i.artefact + ": the generated list changed while"
                            + " promoting; nothing was written");
                }
                String text = new String(gen, UTF8).replace("\r\n", "\n");
                if (!"\n".equals(eol)) text = text.replace("\n", eol);
                File fresh = new File(staging, "new-" + n);
                Files.write(fresh.toPath(), text.getBytes(UTF8));
                staged.add(new Staged(i, eol, fresh, backup));
            }

            StringBuilder sb = new StringBuilder();
            List<Staged> attempted = new ArrayList<Staged>();
            for (Staged s : staged) {
                attempted.add(s);
                try {
                    File dir = s.item.target.getParentFile();
                    s.createdDir = highestMissing(dir);
                    if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                        throw new IOException("Unable to create directory: " + dir);
                    }
                    mover.move(s.fresh.toPath(), s.item.target.toPath());
                } catch (IOException | RuntimeException e) {
                    String restored = rollback(attempted);
                    keep = restored.startsWith("NOT");
                    throw new GenerationException("moving " + s.item.target + " failed (" + e
                            + "); " + restored + (keep ? "; backups are kept in " + staging : ""));
                }
                Item i = s.item;
                sb.append("WROTE ").append(i.target).append(" (").append(i.artefact).append(": ")
                  .append(i.live).append(" live entries, +").append(i.added.size())
                  .append(" -").append(i.removed.size()).append(", ")
                  .append("\r\n".equals(s.eol) ? "CRLF" : "LF").append(", ")
                  .append(i.isNew() ? NEW_FILE : "replaced").append("; held ").append(i.held)
                  .append(", dropped ").append(i.dropped).append(")\n");
            }
            return sb.toString();
        } finally {
            if (!keep) ProxyPolicyGenerator.deleteTree(staging);
        }
    }

    private static File highestMissing(File dir) {
        File missing = null;
        for (File d = dir.getAbsoluteFile(); d != null && !d.exists(); d = d.getParentFile()) {
            missing = d;
        }
        return missing;
    }

    /** Restores every attempted target from its backup, or removes what was newly created. */
    private static String rollback(List<Staged> attempted) {
        List<String> restored = new ArrayList<String>();
        List<String> failed = new ArrayList<String>();
        for (Staged s : attempted) {
            try {
                if (s.backup != null) {
                    // The item whose move failed may be untouched: copying over it is then
                    // needless, and would report a failure on a target that never changed.
                    if (!sameBytes(s.backup, s.item.target)) {
                        Files.copy(s.backup.toPath(), s.item.target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.deleteIfExists(s.item.target.toPath());
                    if (s.createdDir != null) ProxyPolicyGenerator.deleteTree(s.createdDir);
                }
                restored.add(s.item.artefact);
            } catch (IOException | RuntimeException e) {
                failed.add(s.item.artefact + " (" + e + ")");
            }
        }
        return failed.isEmpty() ? "restored " + restored
                : "NOT restored: " + failed + "; restored " + restored;
    }

    private static boolean sameBytes(File a, File b) throws IOException {
        return b.isFile() && Arrays.equals(Files.readAllBytes(a.toPath()),
                Files.readAllBytes(b.toPath()));
    }

    /** CRLF if the bytes have more CRLF line ends than bare LF ones, else LF. */
    static String eolOf(byte[] bytes) {
        int crlf = 0, lf = 0;
        for (int n = 0; n < bytes.length; n++) {
            if (bytes[n] == '\n') {
                if (n > 0 && bytes[n - 1] == '\r') crlf++; else lf++;
            }
        }
        return crlf > lf ? "\r\n" : "\n";
    }
}
