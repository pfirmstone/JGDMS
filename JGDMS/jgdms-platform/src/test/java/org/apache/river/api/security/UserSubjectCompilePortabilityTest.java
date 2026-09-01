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

package org.apache.river.api.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Structural guards over the JGDMS <em>main</em> sources, in the spirit of
 * {@code org.apache.river.outrigger.SiteFStructuralTest} — "prove the negative"
 * at build time rather than discovering it on a platform nobody runs the tests
 * on.
 *
 * <p>Two invariants:
 *
 * <ol>
 *   <li><b>Compile portability.</b>  JGDMS must still compile on a stock
 *       OpenJDK, where {@code javax.security.auth.UserSubject} does not exist.
 *       No main source may name {@code UserSubject} in <em>code</em> — no
 *       import, no {@code UserSubject.class}, and no {@code UserSubject[].class}
 *       array literal.  Javadoc, comments and string literals are fine (that is
 *       how the reflective lookup names the class), so this guard strips them
 *       before testing.  The identifier {@code UserSubjectSupport} is a
 *       different token and is unaffected.</li>
 *   <li><b>One caller site.</b>  The reflective multi-{@code Subject}
 *       {@code callAs} lookup must appear in exactly one main source.  The
 *       original defect existed precisely because that lookup was duplicated in
 *       two production classes and both copies were wrong, while a third,
 *       correct copy in the test sources kept passing.</li>
 * </ol>
 */
public class UserSubjectCompilePortabilityTest {

    /**
     * {@code UserSubject} as a standalone identifier.  Word boundaries mean this
     * matches {@code UserSubject}, {@code UserSubject.class},
     * {@code UserSubject[]} and the tail of
     * {@code javax.security.auth.UserSubject}, but never {@code UserSubjectSupport},
     * {@code ClientUserSubject}, {@code newUserSubject} or
     * {@code toUserSubjectArray}.
     */
    private static final Pattern USER_SUBJECT_TOKEN =
            Pattern.compile("\\bUserSubject\\b");

    /** The reflective lookup of the multi-Subject overload (Callable first). */
    private static final Pattern MULTI_CALL_AS_LOOKUP =
            Pattern.compile("getMethod\\s*\\(\\s*\"callAs\"\\s*,\\s*Callable\\.class");

    /** Where the single caller site is allowed to live. */
    private static final String SOLE_SITE = "UserSubjectSupport.java";

    // ------------------------------------------------------------------
    // Source discovery
    // ------------------------------------------------------------------

    /**
     * Locates the JGDMS reactor root by climbing from the module basedir until a
     * directory holding both {@code jgdms-platform} and {@code jgdms-jeri} is
     * found.  Fail-closed: a guard that cannot find its target must not pass.
     */
    private static Path reactorRoot() {
        Path p = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 8 && p != null; i++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("jgdms-platform"))
                    && Files.isDirectory(p.resolve("jgdms-jeri"))) {
                return p;
            }
        }
        fail("JGDMS reactor root not found from user.dir="
                + System.getProperty("user.dir"));
        throw new AssertionError("unreachable");
    }

    /** Every {@code src/main/java} source in the reactor, excluding build output. */
    private static List<Path> mainSources() throws IOException {
        Path root = reactorRoot();
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> {
                    String s = root.relativize(p).toString().replace('\\', '/');
                    // Only main sources; never build artefacts (target/classes/
                    // OSGI-OPT/src/... holds copies of these very files).
                    return s.contains("/src/main/java/") && !s.contains("/target/");
                })
                .forEach(out::add);
        }
        assertTrue("the scan found only " + out.size() + " main sources under "
                + root + "; a vacuous scan must not pass", out.size() > 500);
        return out;
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------
    // Comment / literal stripping
    // ------------------------------------------------------------------

    /** Comments and literals both blanked: what remains is code without literals. */
    static String stripCommentsAndLiterals(String src) {
        return strip(src, true);
    }

    /** Only comments blanked: literals survive, so {@code "callAs"} is still matchable. */
    static String stripComments(String src) {
        return strip(src, false);
    }

    /**
     * Replaces the contents of comments and, when {@code stripLiterals}, of string
     * literals, text blocks and char literals with spaces, preserving length and
     * line structure so that what remains is code.
     */
    private static String strip(String src, boolean stripLiterals) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        final int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') { out.append(' '); i++; }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) { out.append("  "); i += 2; }
            } else if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                // Text block: always blanked (its content is never code).
                out.append("   ");
                i += 3;
                while (i < n && !(src.charAt(i) == '"' && i + 2 < n
                        && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) { out.append("   "); i += 3; }
            } else if (c == '"' || c == '\'') {
                final char quote = c;
                out.append(quote);
                i++;
                while (i < n && src.charAt(i) != quote) {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        out.append(stripLiterals ? "  "
                                : "" + src.charAt(i) + src.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (src.charAt(i) == '\n') break;   // unterminated; bail out safely
                    out.append(stripLiterals ? ' ' : src.charAt(i));
                    i++;
                }
                if (i < n && src.charAt(i) == quote) { out.append(quote); i++; }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /** The stripper itself must work, or both guards below are vacuous. */
    @Test
    public void stripperRemovesCommentsAndLiteralsButKeepsCode() {
        String src = "int a; // UserSubject in a line comment\n"
                + "/* UserSubject in a block comment */\n"
                + "String s = \"javax.security.auth.UserSubject\";\n"
                + "Class<?> c = UserSubject.class;\n";
        String stripped = stripCommentsAndLiterals(src);
        assertEquals("exactly one code-level occurrence must survive",
                1, count(USER_SUBJECT_TOKEN, stripped));
        assertTrue("code outside literals must survive", stripped.contains("Class<?> c ="));

        // stripComments keeps literals, so the lookup pattern stays matchable.
        String kept = stripComments(
                "// getMethod(\"callAs\", Callable.class, x) in a comment\n"
                + "m = Subject.class.getMethod(\"callAs\", Callable.class, arrayType);\n");
        assertEquals("the commented-out copy must not count",
                1, count(MULTI_CALL_AS_LOOKUP, kept));
    }

    /**
     * No main source may reference {@code UserSubject} at compile time; the class
     * is absent on a stock OpenJDK, so any such reference breaks the build there.
     */
    @Test
    public void noMainSourceNamesUserSubjectInCode() throws IOException {
        Map<String, Integer> offenders = new LinkedHashMap<>();
        for (Path p : mainSources()) {
            String code = stripCommentsAndLiterals(read(p));
            int hits = count(USER_SUBJECT_TOKEN, code);
            if (hits > 0) offenders.put(p.toString(), hits);
        }
        assertTrue("main sources must not name javax.security.auth.UserSubject at"
                + " compile time (JGDMS must still build on a stock OpenJDK);"
                + " obtain it reflectively via UserSubjectSupport instead."
                + " Offenders: " + offenders,
                offenders.isEmpty());
    }

    /**
     * The reflective multi-Subject {@code callAs} lookup must exist in exactly
     * one main source — duplicating it is what let two wrong copies ship.
     */
    @Test
    public void theMultiSubjectCallAsLookupHasExactlyOneMainSourceSite() throws IOException {
        Map<String, Integer> sites = new LinkedHashMap<>();
        int total = 0;
        for (Path p : mainSources()) {
            // Comments only: the lookup is identified by its "callAs" literal.
            int hits = count(MULTI_CALL_AS_LOOKUP, stripComments(read(p)));
            if (hits > 0) {
                sites.put(p.getFileName().toString(), hits);
                total += hits;
            }
        }
        assertEquals("the reflective multi-Subject callAs lookup must appear in"
                + " exactly one main source; found " + sites,
                1, sites.size());
        assertEquals("...and exactly once within it; found " + sites, 1, total);
        assertTrue("the sole site must be " + SOLE_SITE + "; found " + sites,
                sites.containsKey(SOLE_SITE));
    }

    /**
     * Both production consumers must route through the shared helper rather than
     * re-implementing the reflective call.
     */
    @Test
    public void bothProductionSitesDelegateToTheHelper() {
        Path root = reactorRoot();
        Path[] consumers = {
            root.resolve("jgdms-jeri/src/main/java/net/jini/jeri/BasicInvocationDispatcher.java"),
            root.resolve("jgdms-platform/src/main/java/net/jini/security/SubjectAwareExecutor.java"),
        };
        for (Path p : consumers) {
            assertTrue("missing: " + p, Files.isRegularFile(p));
            String code = stripComments(read(p));
            assertTrue(p.getFileName() + " must delegate to UserSubjectSupport.callAsAll",
                    code.contains("UserSubjectSupport.callAsAll"));
            assertEquals(p.getFileName() + " must not re-implement the reflective lookup",
                    0, count(MULTI_CALL_AS_LOOKUP, code));
        }
    }

    private static int count(Pattern pattern, String s) {
        Matcher m = pattern.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
