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
package org.apache.river.outrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Unit B3, site&nbsp;F — the "prove the negative" structural guard (design memo
 * B3 &sect;1.5, {@code [RATIFIED Peter 2026-07-26]}). {@code
 * IteratorImpl.nextReps} is the legacy {@code JavaSpaceAdmin} AdminIterator
 * path; it has no filter parameter and MUST remain unreachable from any filtered
 * client operation, so a future refactor that quietly routes a filtered
 * {@code contents}/{@code take} through it fails <b>loudly at build time here</b>
 * rather than silently serving byte-matched-but-unfiltered entries in
 * production.
 *
 * <p>The guard is a <em>mechanism, not a promise</em>: it asserts, over the
 * actual {@code OutriggerServerImpl} source, that (1) the admin iterator is
 * constructed at exactly one site, (2) that site is the admin {@code
 * contents(EntryRep tmpl, Transaction tr)} entry — which carries no filter — and
 * (3) no filtered operation (any method taking a {@code byte[] filterEnvelope})
 * nor the filtered {@code contents}/{@code take} helpers reference
 * {@code IteratorImpl} or {@code nextReps} at all.
 */
public class SiteFStructuralTest {

    private static String source() throws IOException {
        // surefire runs with the module basedir as the working directory.
        final String rel =
            "src/main/java/org/apache/river/outrigger/OutriggerServerImpl.java";
        Path[] candidates = {
            Paths.get(rel),
            Paths.get("services/outrigger/outrigger-service", rel),
            Paths.get(System.getProperty("user.dir", "."), rel),
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            }
        }
        // Fail-closed: a guard that cannot find its target must not silently pass.
        fail("OutriggerServerImpl.java source not found for the site-F structural "
                + "guard (cwd=" + System.getProperty("user.dir") + ")");
        throw new AssertionError("unreachable");
    }

    /** Brace-matched body of the method whose signature contains {@code marker}. */
    private static String methodBody(String src, String marker) {
        int i = src.indexOf(marker);
        assertTrue("marker not found: " + marker, i >= 0);
        int open = src.indexOf('{', i);
        assertTrue("no method body for: " + marker, open >= 0);
        int depth = 0;
        for (int j = open; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, j + 1);
            }
        }
        fail("unbalanced braces from: " + marker);
        throw new AssertionError("unreachable");
    }

    private static int count(String src, String needle) {
        int n = 0, i = 0;
        while ((i = src.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    @Test
    public void adminIteratorIsConstructedAtExactlyOneNonFilteredSite() throws Exception {
        final String src = source();

        // The guarded path really exists (otherwise this guard is vacuous).
        assertTrue("IteratorImpl class must exist", src.contains("class IteratorImpl"));
        assertTrue("nextReps must exist", src.contains("nextReps"));

        // Exactly one construction site for the admin iterator.
        assertEquals("IteratorImpl must be constructed at exactly one site",
                1, count(src, "new IteratorImpl"));

        // ...and that site is the admin contents(EntryRep, Transaction) entry,
        // which takes NO filterEnvelope.
        String adminBody = methodBody(src, "public Uuid contents(EntryRep tmpl, Transaction tr)");
        assertTrue("the sole IteratorImpl construction must be the admin contents path",
                adminBody.contains("new IteratorImpl"));
    }

    @Test
    public void noFilteredOperationReachesTheAdminIterator() throws Exception {
        final String src = source();

        // Every filtered public entry point is a method taking a byte[] filterEnvelope.
        final String sig = "byte[] filterEnvelope)";
        assertTrue("expected filtered operations to exist", src.contains(sig));
        int i = 0, checked = 0;
        while ((i = src.indexOf(sig, i)) >= 0) {
            String body = bodyFrom(src, i);
            assertNoAdminIterator(body, "a filtered operation (byte[] filterEnvelope)");
            checked++;
            i += sig.length();
        }
        assertTrue("no filtered operations were structurally checked", checked >= 8);

        // The filtered contents / bulk-take helpers route through createQuery /
        // ContinuingQuery, never the admin iterator.
        assertNoAdminIterator(methodBody(src, "private MatchSetData doContents("),
                "doContents (filtered contents helper)");
        assertNoAdminIterator(methodBody(src, "private Object doTakeMultiple("),
                "doTakeMultiple (filtered bulk-take helper)");
    }

    /** Brace-matched body of the method whose signature spans {@code index}. */
    private static String bodyFrom(String src, int index) {
        int open = src.indexOf('{', index);
        assertTrue("no body after filtered signature", open >= 0);
        int depth = 0;
        for (int j = open; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, j + 1);
            }
        }
        fail("unbalanced braces after filtered signature");
        throw new AssertionError("unreachable");
    }

    private static void assertNoAdminIterator(String body, String what) {
        if (body.contains("IteratorImpl") || body.contains("nextReps")) {
            fail(what + " must never route to the admin IteratorImpl.nextReps path "
                    + "(site-F guard, design memo B3 §1.5): a filtered op reaching the "
                    + "unfiltered admin iterator would silently serve byte-matched-but-"
                    + "unfiltered entries.");
        }
    }
}
