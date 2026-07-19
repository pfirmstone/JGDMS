/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md}, "Concrete change" item 5 -- the
 * reach-widening forcing function (second board review, MEDIUM, required).
 *
 * <h2>Why this test exists</h2>
 * <p>Before this SOW, {@code Object foo;} in a {@code serialForm()} threw a loud {@link
 * au.net.zeus.jgdms.der.DerException} at schema-generation time -- an accidental but real forcing
 * function that made every {@code Object.class} serial field visible at code-review time (nobody
 * could add one without the build breaking). After this SOW that same declaration silently compiles
 * and works (routed through the closed-subset {@code Any} form). This test is the REPLACEMENT
 * forcing function: it source-scans every {@code src/main} tree in the reactor for {@code new
 * SerialForm(..., Object.class)} sites and fails the build if the SET of sites changes without this
 * test itself being updated -- so a future {@code Object}-typed serial field still forces an
 * explicit, reviewed acknowledgment (memo §8.2: type validation for such a field shifts entirely
 * onto the class's own {@code check(GetArg)}).
 *
 * <h2>Deviation from the SOW's own count -- read before editing the allowlist</h2>
 * <p>The SOW's "Blast radius" section lists 9 sites "besides {@code RemoteEvent.source}" (10
 * total). An exhaustive regex scan of the ACTUAL reactor (2026-07-19, this implementation pass)
 * found <b>12</b> total sites -- the SOW's list missed two, both in {@code jgdms-platform}:
 * {@code net.jini.core.event.EventRegistration.source} and {@code
 * net.jini.core.lookup.ServiceItem.service}. Both are reviewed the same way as the SOW's own 9 (see
 * this implementation's final report for the per-site §8.2 notes); this scanner's allowlist reflects
 * the VERIFIED reactor state (12 sites), not the SOW document's undercount, per this task's
 * instruction to adapt to reality rather than force a mismatch to pass silently.
 */
class ObjectTypedSerialFieldReachTest {

    /**
     * The reviewed allowlist: every {@code new SerialForm(<name>, Object.class)} site in
     * {@code src/main} across the whole reactor, as {@code "<simple-class-name>.<fieldName>"}.
     * Sorted for a stable, readable diff. MUST be updated (with a fresh §8.2 review of the new
     * site) if this test's failure message reports an addition or removal.
     */
    private static final List<String> ALLOWLIST = List.of(
            "AbstractSmartProxy.server",
            "ConsistentMapEntry.key",
            "ConsistentMapEntry.value",
            "EventRegistration.source",
            "EventType.handback",
            "MapSerializer$Ent.key",
            "MapSerializer$Ent.value",
            "RegistrarEvent.serviceItem",
            "RegistrarImpl$EventReg.handback",
            "RemoteEvent.source",
            "RemoteEventData.cookie",
            "ServiceItem.service"
    );

    /** Matches {@code new SerialForm(<anything-but-comma>, Object.class)} (comment/string-stripped source). */
    private static final Pattern SITE_PATTERN =
            Pattern.compile("new\\s+SerialForm\\s*\\(\\s*([^,]+?)\\s*,\\s*Object\\.class\\s*\\)");

    @Test
    void objectTypedSerialFieldSites_matchReviewedAllowlist() throws IOException {
        Path reactorRoot = locateReactorRoot();
        assumeReachable(reactorRoot);

        List<String> found = scanForObjectTypedSerialFields(reactorRoot);
        TreeSet<String> foundSet = new TreeSet<>(found);
        TreeSet<String> allowSet = new TreeSet<>(ALLOWLIST);

        TreeSet<String> added = new TreeSet<>(foundSet);
        added.removeAll(allowSet);
        TreeSet<String> removed = new TreeSet<>(allowSet);
        removed.removeAll(foundSet);

        assertTrue(added.isEmpty() && removed.isEmpty(),
                "Object.class serial-field site set changed -- this is the reach-widening forcing "
                + "function (SOW item 5): review each NEW site's check(GetArg) per memo §8.2 before "
                + "updating ALLOWLIST. Added (unreviewed): " + added
                + "; Removed (no longer present -- update ALLOWLIST if intentional): " + removed);
    }

    /**
     * Fails loudly (rather than silently vacuously passing) if the scan itself found nothing at
     * all where the reactor root WAS locatable -- an empty result on a locatable root means the
     * scan logic is broken, not that the reactor has zero sites (there are 12, including this
     * SOW's own primary target).
     */
    @Test
    void scan_findsAtLeastTheKnownRemoteEventSourceSite() throws IOException {
        Path reactorRoot = locateReactorRoot();
        assumeReachable(reactorRoot);
        List<String> found = scanForObjectTypedSerialFields(reactorRoot);
        assertTrue(found.contains("RemoteEvent.source"),
                "the scan must find RemoteEvent.source -- this SOW's own primary target; found: " + found);
    }

    // -------------------------------------------------------------------------
    // Scan implementation.
    // -------------------------------------------------------------------------

    private static List<String> scanForObjectTypedSerialFields(Path reactorRoot) throws IOException {
        List<String> hits = new ArrayList<>();
        Files.walkFileTree(reactorRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                // Skip build output, VCS, and non-source directories for speed; qa/src is
                // deliberately NOT a "src/main" path segment so it is naturally excluded below
                // without needing a special case here.
                if (name.equals("target") || name.equals(".git") || name.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String p = file.toString().replace('\\', '/');
                if (p.endsWith(".java") && p.contains("/src/main/")) {
                    scanFile(file, hits);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return hits;
    }

    private static void scanFile(Path file, List<String> hits) throws IOException {
        String stripped = stripCommentsAndStrings(Files.readString(file));
        if (!stripped.contains("Object.class")) {
            return; // fast skip -- avoids paying regex cost on every source file in the reactor
        }
        List<Integer> matchStarts = new ArrayList<>();
        List<String> fieldExprs = new ArrayList<>();
        Matcher m = SITE_PATTERN.matcher(stripped);
        while (m.find()) {
            matchStarts.add(m.start());
            fieldExprs.add(m.group(1).trim());
        }
        if (matchStarts.isEmpty()) {
            return;
        }
        List<String> classLabels = enclosingClassLabelsAt(stripped, matchStarts, file);
        for (int i = 0; i < matchStarts.size(); i++) {
            String fieldName = resolveFieldName(fieldExprs.get(i), stripped);
            hits.add(classLabels.get(i) + "." + fieldName);
        }
    }

    /**
     * Resolves the field-name argument to a display name: a string literal {@code "foo"} yields
     * {@code foo}; a bare identifier (a {@code static final String} constant, e.g. {@code SOURCE})
     * is resolved by finding {@code <TYPE> <IDENT> = "<value>"} earlier in the same (stripped)
     * source; falls back to the raw expression if neither matches (keeps the scan total/never
     * silently drops a site).
     */
    private static String resolveFieldName(String fieldExpr, String strippedSource) {
        if (fieldExpr.startsWith("\"") && fieldExpr.endsWith("\"") && fieldExpr.length() >= 2) {
            return fieldExpr.substring(1, fieldExpr.length() - 1);
        }
        Pattern constDecl = Pattern.compile(
                "\\b" + Pattern.quote(fieldExpr) + "\\s*=\\s*\"([^\"]*)\"");
        Matcher cm = constDecl.matcher(strippedSource);
        if (cm.find()) {
            return cm.group(1);
        }
        return fieldExpr; // fail visible (an odd expression shows up verbatim), never silently dropped
    }

    private static final Pattern CLASS_DECL =
            Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /**
     * Resolves the innermost enclosing {@code class} name (qualified {@code Outer$Inner} for a
     * nested class, e.g. {@code MapSerializer$Ent}, {@code RegistrarImpl$EventReg}) for EACH
     * position in {@code matchStarts} (assumed ascending), via a single brace-depth-tracking pass
     * over {@code strippedSource} -- NOT a "last N class declarations seen" heuristic, which is
     * unsound whenever a sibling nested class is declared (and closed) before the target one
     * (e.g. {@code RegistrarImpl} declares over a dozen sibling {@code private static class ...}
     * log-record types before {@code EventReg}). Falls back to the file's own simple name if no
     * class declaration encloses a given position (should not happen for valid Java source).
     */
    private static List<String> enclosingClassLabelsAt(String strippedSource,
                                                         List<Integer> matchStarts, Path file) {
        List<String> results = new ArrayList<>(Collections.nCopies(matchStarts.size(), null));
        Matcher classScanner = CLASS_DECL.matcher(strippedSource);
        List<int[]> classDeclStarts = new ArrayList<>(); // [declStart] in source order
        List<String> classNames = new ArrayList<>();
        while (classScanner.find()) {
            classDeclStarts.add(new int[]{classScanner.start()});
            classNames.add(classScanner.group(1));
        }

        // Single forward pass tracking brace depth and a stack of (openDepth, name) for classes
        // whose declaration has been seen but whose closing '}' has not yet been reached.
        java.util.Deque<int[]> depthStack = new java.util.ArrayDeque<>(); // [depthAtOpen] parallel to nameStack
        java.util.Deque<String> nameStack = new java.util.ArrayDeque<>();
        int depth = 0;
        int nextClassIdx = 0;
        int nextMatchIdx = 0;
        int n = strippedSource.length();
        for (int pos = 0; pos < n && nextMatchIdx < matchStarts.size(); pos++) {
            // Record the label for every match position reached at the CURRENT stack state,
            // before processing this position's character (matches never coincide with braces).
            while (nextMatchIdx < matchStarts.size() && matchStarts.get(nextMatchIdx) == pos) {
                results.set(nextMatchIdx, labelFor(nameStack, file));
                nextMatchIdx++;
            }
            char c = strippedSource.charAt(pos);
            if (c == '{') {
                // If a class declaration started at or before this brace and hasn't been pushed
                // yet, this is its opening brace (the first '{' after "class Name" that isn't
                // part of a generic/implements clause -- approximated by "nearest '{' after the
                // most recently seen not-yet-pushed class decl", which holds for this codebase's
                // formatting).
                if (nextClassIdx < classDeclStarts.size()
                        && classDeclStarts.get(nextClassIdx)[0] < pos
                        && (nextClassIdx == classDeclStarts.size() - 1
                            || classDeclStarts.get(nextClassIdx + 1)[0] > pos)) {
                    nameStack.push(classNames.get(nextClassIdx));
                    depthStack.push(new int[]{depth});
                    nextClassIdx++;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (!depthStack.isEmpty() && depthStack.peek()[0] == depth) {
                    depthStack.pop();
                    nameStack.pop();
                }
            }
        }
        // Any remaining matches past the end of the scanned text (shouldn't happen) fall back.
        while (nextMatchIdx < matchStarts.size()) {
            results.set(nextMatchIdx, labelFor(nameStack, file));
            nextMatchIdx++;
        }
        return results;
    }

    private static String labelFor(java.util.Deque<String> nameStack, Path file) {
        if (nameStack.isEmpty()) {
            String fn = file.getFileName().toString();
            return fn.endsWith(".java") ? fn.substring(0, fn.length() - 5) : fn;
        }
        // Stack is innermost-first (Deque used as a LIFO stack via push/pop); reverse for
        // outer-to-inner Outer$Inner labelling, keeping only the outermost + innermost when
        // more than 2 levels deep (this codebase's sites never nest more than 2 deep, and a
        // 3+ level qualifier would just be noisier without disambiguating anything further).
        java.util.List<String> ordered = new ArrayList<>(nameStack);
        Collections.reverse(ordered);
        if (ordered.size() == 1) {
            return ordered.get(0);
        }
        return ordered.get(0) + "$" + ordered.get(ordered.size() - 1);
    }

    /**
     * Crude source scrubber: removes {@code //} line comments, block comments, and String/char
     * literals so the scan matches executable code only. Mirrors the equivalent helper in
     * {@code CollectionOrderingTest} (this project's existing declaration-usage scanner
     * precedent).
     */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"') {
                out.append(c);
                i++;
                while (i < n && src.charAt(i) != '"') {
                    out.append(src.charAt(i));
                    if (src.charAt(i) == '\\' && i + 1 < n) { i++; out.append(src.charAt(i)); }
                    i++;
                }
                if (i < n) { out.append('"'); i++; }
            } else if (c == '\'') {
                i++;
                while (i < n && src.charAt(i) != '\'') { if (src.charAt(i) == '\\') i++; i++; }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // -------------------------------------------------------------------------
    // Reactor-root discovery (mirrors CollectionOrderingTest's locate() convention, generalised
    // to find the reactor ROOT directory rather than one specific file).
    // -------------------------------------------------------------------------

    /**
     * Walks up from the current working directory looking for the JGDMS reactor root: a directory
     * containing both a {@code jgdms-der} and a {@code services} subdirectory (this module's own
     * parent and a sibling that only exists at the true root). Returns {@code null} if not found
     * within 6 levels (a build layout without source on the working directory, e.g. running from
     * an assembled jar) -- callers must treat that as "skip, not fail" (see
     * {@link #assumeReachable}), matching {@code CollectionOrderingTest}'s existing precedent.
     */
    private static Path locateReactorRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("jgdms-der")) && Files.isDirectory(dir.resolve("services"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static void assumeReachable(Path reactorRoot) {
        org.junit.jupiter.api.Assumptions.assumeTrue(reactorRoot != null,
                "reactor root not found on the working-directory ancestor chain (build layout "
                + "without source on the working directory) -- skipping, not failing");
    }
}
