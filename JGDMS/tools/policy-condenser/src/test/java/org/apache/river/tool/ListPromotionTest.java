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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the two-step promotion of generated {@code PERMISSIONS.LIST} files
 * ({@link ListPromotion}): the plan changes nothing and says what would change;
 * the apply writes only what a reviewer approved, and refuses what has gone
 * stale, would remove entries unreviewed, or has no live entry.
 */
public class ListPromotionTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String GET_CL = "(java.lang.RuntimePermission \"getClassLoader\")";
    private static final String JAVA_HOME = "(java.util.PropertyPermission \"java.home\" \"read\")";
    private static final String SET_IO = "(java.lang.RuntimePermission \"setIO\")";

    private File tempDir;
    private File gen;
    private File tree;
    private File plan;
    private File mahaloTarget;
    private File reggieTarget;

    @Before
    public void setUp() throws Exception {
        tempDir = new File(System.getProperty("java.io.tmpdir"),
            "list-promotion-test-" + Thread.currentThread().getId() + "-" + System.nanoTime());
        gen = new File(tempDir, "gen");
        tree = new File(tempDir, "tree");
        plan = new File(tempDir, "plans/promote.plan");
        gen.getParentFile().mkdirs();
        mahaloTarget = module("services/mahalo/mahalo-dl");
        reggieTarget = module("services/reggie/reggie-dl");
    }

    @After
    public void tearDown() {
        ProxyPolicyGenerator.deleteTree(tempDir);
    }

    // ------------------------------------------------------------------ plan

    /**
     * The plan names the target, the entries added and removed against the
     * checked-in list (compared as parsed), NEW FILE for a module without a
     * list, the dropped and held notes, and a reason for what it cannot promote.
     */
    @Test
    public void testPlanListsAddedRemovedNewFileAndNotes() throws Exception {
        write(mahaloTarget, "# licence", GET_CL, "# required for JRMP", SET_IO,
            "(java.lang.reflect.ReflectPermission \"newProxyInPackage.x\" \"\")");
        File policy = policy("audit.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT",
                "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";",
                "permission java.lang.reflect.ReflectPermission \"newProxyInPackage.x\";",
                "permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";",
                "permission java.io.FilePermission \"/home/user/m.jar\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT",
                "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("orphan-dl-4.0.0-SNAPSHOT",
                "permission java.lang.RuntimePermission \"getClassLoader\";"));
        new ProxyPolicyGenerator().generate(policy, gen);
        Files.delete(reggieTarget.toPath()); // reggie-dl exists as a module with no list yet

        ListPromotion.Plan p = makePlan();

        String text = read(plan);
        String mahalo = block(text, "mahalo-dl-4.0.0-SNAPSHOT");
        assertTrue(mahalo, mahalo.contains("  status: PROMOTABLE\n"));
        assertTrue(mahalo, mahalo.contains("  target: " + mahaloTarget.getAbsolutePath() + "\n"));
        assertTrue(mahalo, mahalo.contains("  ADDED: 1\n    + " + JAVA_HOME + "\n"));
        assertTrue(mahalo, mahalo.contains("  REMOVED: 1\n    - " + SET_IO + "\n"));
        assertTrue("unchanged entries are neither added nor removed",
            !mahalo.contains("    + " + GET_CL) && !mahalo.contains("    - " + GET_CL));
        assertTrue(mahalo, mahalo.contains("  note: # DROPPED (admission-gate permission"));
        assertTrue(mahalo, mahalo.contains("  note: # HELD (absolute path on the audit host)"));
        assertTrue(mahalo, mahalo.contains("  note: COMMENT NOT CARRIED OVER"));
        assertTrue(mahalo, mahalo.contains("target-sha256: " + sha256Of(mahaloTarget)));

        String reggie = block(text, "reggie-dl-4.0.0-SNAPSHOT");
        assertTrue(reggie, reggie.contains("  target-sha256: NEW FILE\n"));
        assertTrue(reggie, reggie.contains("  ADDED: 1 (NEW FILE)\n    + " + GET_CL + "\n"));
        assertTrue(reggie, reggie.contains("  REMOVED: 0\n"));

        String orphan = block(text, "orphan-dl-4.0.0-SNAPSHOT");
        assertTrue(orphan, orphan.contains("  status: REFUSED: no -dl module named orphan-dl"));

        assertEquals(3, p.items.size());
        assertTrue(p.summary(), p.summary().contains("3 artefact(s), 2 promotable, 1 refused, +2 -1 entries"));
    }

    /** Invariant: planning changes nothing under the module roots, not a byte. */
    @Test
    public void testPlanChangesNothingInTheSourceTree() throws Exception {
        write(mahaloTarget, GET_CL, SET_IO);
        Files.delete(reggieTarget.toPath()); // a module with no list: planned as NEW FILE
        new ProxyPolicyGenerator().generate(policy("p.policy", proxy("mahalo-dl-4.0.0-SNAPSHOT",
            "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT",
            "permission java.util.PropertyPermission \"java.home\", \"read\";")), gen);
        Map<String, byte[]> before = bytes(tree);

        makePlan();

        assertTrue("the plan is written", plan.isFile());
        assertEquals(before.keySet(), bytes(tree).keySet());
        for (Map.Entry<String, byte[]> e : before.entrySet()) {
            assertArrayEquals(e.getKey(), e.getValue(), bytes(tree).get(e.getKey()));
        }
    }

    @Test
    public void testPlanFileInsideAModuleRootIsRefused() throws Exception {
        generateMahalo();
        try {
            ListPromotion.plan(gen, roots(), NOMAP, new File(tree, "promote.plan"));
            fail("a plan under the module root would change the source tree");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must not change the source tree"));
        }
        assertFalse(new File(tree, "promote.plan").exists());
    }

    /** Invariant: a list with no live entry requests nothing; it is never promoted, whatever the plan file says. */
    @Test
    public void testZeroLiveEntryListIsNeverPromoted() throws Exception {
        write(mahaloTarget, GET_CL, SET_IO);
        File list = new File(gen, "mahalo-dl-4.0.0-SNAPSHOT/META-INF/PERMISSIONS.LIST");
        list.getParentFile().mkdirs();
        Files.write(list.toPath(), Arrays.asList("# Source policy: x.policy",
            "# DROPPED (admission-gate permission): (au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\")"));
        String before = read(mahaloTarget);

        makePlan();
        String block = block(read(plan), "mahalo-dl-4.0.0-SNAPSHOT");
        assertTrue(block, block.contains("  status: REFUSED: zero live entries"));
        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "the plan refuses it");

        // a hand-edited plan claiming PROMOTABLE does not get past the live-entry check
        Files.write(plan.toPath(), read(plan).replace("  status: REFUSED: zero live entries: ",
            "  status: PROMOTABLE\n  x-ignored: ").getBytes(UTF8));
        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "zero live entries");
        assertEquals(before, read(mahaloTarget));
    }

    // ----------------------------------------------------------------- apply

    @Test
    public void testApplyWritesOnlyTheApprovedArtefacts() throws Exception {
        write(mahaloTarget, GET_CL);
        write(reggieTarget, GET_CL);
        File policy = policy("two.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"));
        new ProxyPolicyGenerator().generate(policy, gen);
        makePlan();
        String reggieBefore = read(reggieTarget);

        String report = applyPlan(Arrays.asList("mahalo-dl-4.0.0-SNAPSHOT"), false);

        assertEquals("exactly the generated list is written",
            read(new File(gen, "mahalo-dl-4.0.0-SNAPSHOT/META-INF/PERMISSIONS.LIST")), read(mahaloTarget));
        assertEquals("the artefact that was not approved is untouched", reggieBefore, read(reggieTarget));
        assertTrue(report, report.startsWith("WROTE " + mahaloTarget.getAbsolutePath() + " ("));
        assertTrue(report, report.contains("2 live entries, +1 -0, LF, replaced"));
        assertEquals("one line per artefact written", 1, report.split("\n").length);
    }

    @Test
    public void testApplyRefusesAnArtefactNotInThePlan() throws Exception {
        generateMahalo();
        makePlan();
        assertApplyRefused("mahalo-dl", false, "not in the plan");
    }

    /** Invariant: entries a reviewer has not seen leave the source tree only with --allow-removals. */
    @Test
    public void testApplyRefusesRemovalsUnlessAllowed() throws Exception {
        write(mahaloTarget, GET_CL, SET_IO);
        generateMahalo();
        makePlan();
        String before = read(mahaloTarget);

        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "would be REMOVED");
        assertEquals(before, read(mahaloTarget));

        String report = applyPlan(Arrays.asList("mahalo-dl-4.0.0-SNAPSHOT"), true);
        assertTrue(report, report.contains("+0 -1"));
        assertFalse(read(mahaloTarget).contains("setIO"));
    }

    /** Invariant: nothing is written if any approved artefact is refused. */
    @Test
    public void testApplyIsAllOrNothing() throws Exception {
        write(mahaloTarget, GET_CL);
        write(reggieTarget, GET_CL, SET_IO);
        new ProxyPolicyGenerator().generate(policy("aon.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        makePlan();
        String mahaloBefore = read(mahaloTarget);

        try {
            applyPlan(Arrays.asList("mahalo-dl-4.0.0-SNAPSHOT", "reggie-dl-4.0.0-SNAPSHOT"), false);
            fail("reggie would lose an entry");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nothing was written"));
        }
        assertEquals("the acceptable artefact was not written either", mahaloBefore, read(mahaloTarget));
    }

    /** The list changed after the plan (hash differs), or is newer than the plan: make a new plan. */
    @Test
    public void testApplyRefusesAStalePlan() throws Exception {
        write(mahaloTarget, GET_CL);
        File policy = policy("stale.policy", proxy("mahalo-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";"));
        new ProxyPolicyGenerator().generate(policy, gen);
        makePlan();
        File list = new File(gen, "mahalo-dl-4.0.0-SNAPSHOT/META-INF/PERMISSIONS.LIST");
        long planned = plan.lastModified();
        String before = read(mahaloTarget);

        // content changed, timestamp put back before the plan: only the hash can see it
        policy("stale.policy", proxy("mahalo-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";",
            "permission java.lang.RuntimePermission \"setIO\";"));
        new ProxyPolicyGenerator().clean(true).generate(policy, gen);
        assertTrue(list.setLastModified(planned - 60000));
        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "older than the generated list");

        // same content as planned, but newer than the plan
        policy("stale.policy", proxy("mahalo-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";"));
        new ProxyPolicyGenerator().clean(true).generate(policy, gen);
        makePlan();
        assertTrue(list.setLastModified(plan.lastModified() + 60000));
        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "older than the generated list");
        assertEquals(before, read(mahaloTarget));
    }

    /** The checked-in list was edited since the plan (or a new one appeared): the reviewed diff no longer holds. */
    @Test
    public void testApplyRefusesAChangedTarget() throws Exception {
        write(mahaloTarget, GET_CL);
        Files.delete(reggieTarget.toPath());
        new ProxyPolicyGenerator().generate(policy("t.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        makePlan();

        write(mahaloTarget, GET_CL, "# edited since the plan");
        write(reggieTarget, SET_IO); // planned as NEW FILE, now exists
        String mahaloBefore = read(mahaloTarget);
        String reggieBefore = read(reggieTarget);

        assertApplyRefused("mahalo-dl-4.0.0-SNAPSHOT", false, "changed since the plan");
        assertApplyRefused("reggie-dl-4.0.0-SNAPSHOT", false, "changed since the plan");
        assertEquals(mahaloBefore, read(mahaloTarget));
        assertEquals(reggieBefore, read(reggieTarget));
    }

    /** The target's own line-ending style is kept; a new file gets LF. */
    @Test
    public void testLineEndingsOfTheTargetArePreserved() throws Exception {
        Files.write(mahaloTarget.toPath(), (GET_CL + "\r\n# note\r\n" + SET_IO + "\r\n").getBytes(UTF8));
        Files.write(reggieTarget.toPath(), (GET_CL + "\n").getBytes(UTF8));
        File third = module("services/norm/norm-dl");
        Files.delete(third.toPath());
        new ProxyPolicyGenerator().generate(policy("eol.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("norm-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        makePlan();

        applyPlan(Arrays.asList("mahalo-dl-4.0.0-SNAPSHOT", "reggie-dl-4.0.0-SNAPSHOT",
            "norm-dl-4.0.0-SNAPSHOT"), true);

        String crlf = read(mahaloTarget);
        assertTrue("the CRLF target stays CRLF", crlf.contains("\r\n"));
        assertEquals("and has no bare LF", crlf.split("\n", -1).length, crlf.split("\r\n", -1).length);
        assertTrue(crlf, crlf.contains(GET_CL + "\r\n"));
        String lf = read(reggieTarget);
        assertTrue(lf.contains("\n") && !lf.contains("\r"));
        assertTrue("a new file gets LF", read(third).contains("\n") && !read(third).contains("\r"));
    }

    // ------------------------------------------------------------------- CLI

    @Test
    public void testCliPlanThenApply() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(so, true, "UTF-8");
        PrintStream err = new PrintStream(se, true, "UTF-8");

        assertEquals(0, ProxyPolicyGenerator.run(new String[]{"--out", gen.getPath(),
            "--plan-file", plan.getPath(), "--promote-plan", tree.getPath()}, out, err));
        assertTrue(so.toString("UTF-8"), so.toString("UTF-8").contains("1 artefact(s), 1 promotable"));
        assertTrue(so.toString("UTF-8"), so.toString("UTF-8").contains("nothing under the module roots was changed"));
        String sha = sha256Of(plan);
        assertTrue("the plan's SHA-256 is printed for the reviewer",
            so.toString("UTF-8").contains("plan sha256: " + sha));

        String[] apply = {"--promote-apply", plan.getPath(), "--out", gen.getPath(),
            "--module-roots", tree.getPath()};
        assertEquals("approving nothing is a usage error", 2, ProxyPolicyGenerator.run(
            apply, out, err));
        assertEquals("a missing --approve-plan-sha is a usage error", 2, ProxyPolicyGenerator.run(
            cat(apply, "--approve", "mahalo-dl-4.0.0-SNAPSHOT"), out, err));
        assertEquals("without the module roots there is nothing to re-derive from", 2,
            ProxyPolicyGenerator.run(new String[]{"--promote-apply", plan.getPath(), "--out",
                gen.getPath(), "--approve-plan-sha", sha, "--approve", "mahalo-dl-4.0.0-SNAPSHOT"},
                out, err));
        assertEquals(1, ProxyPolicyGenerator.run(cat(apply, "--approve-plan-sha", sha,
            "--approve", "nope"), out, err));
        assertTrue(se.toString("UTF-8"), se.toString("UTF-8").contains("REFUSED"));
        assertEquals("a wrong SHA-256 is refused", 1, ProxyPolicyGenerator.run(cat(apply,
            "--approve-plan-sha", "0000", "--approve", "mahalo-dl-4.0.0-SNAPSHOT"), out, err));
        assertFalse(read(mahaloTarget).contains(JAVA_HOME));

        so.reset();
        assertEquals(0, ProxyPolicyGenerator.run(cat(apply, "--approve-plan-sha", sha,
            "--approve", "mahalo-dl-4.0.0-SNAPSHOT"), out, err));
        assertTrue(so.toString("UTF-8"), so.toString("UTF-8").contains("WROTE " + mahaloTarget.getAbsolutePath()));
        assertTrue(read(mahaloTarget).contains(JAVA_HOME));
    }

    // ------------------------------------- apply does not trust the plan file

    private static final String M = "mahalo-dl-4.0.0-SNAPSHOT";

    /** The plan's target is edited to a file outside every module root: nothing is created there. */
    @Test
    public void testApplyRefusesATargetRedirectedOutsideTheRoots() throws Exception {
        generateMahalo();
        makePlan();
        File outside = new File(tempDir, "outside/PERMISSIONS.LIST");
        editPlan("target: " + mahaloTarget.getAbsolutePath(), "target: " + outside.getAbsolutePath());
        editPlanRegex("  target-sha256: [0-9a-f]+", "  target-sha256: NEW FILE");

        assertApplyRefused(M, true, "is not under a module root");
        assertFalse(outside.exists());
        assertFalse("not even its directory", outside.getParentFile().exists());
    }

    /** The target is edited to the module's pom.xml (with its true hash), under the root. */
    @Test
    public void testApplyRefusesATargetThatIsNotAPermissionsList() throws Exception {
        generateMahalo();
        makePlan();
        File pom = new File(new File(tree, "services/mahalo/mahalo-dl"), "pom.xml");
        String before = read(pom);
        editPlan("target: " + mahaloTarget.getAbsolutePath(), "target: " + pom.getAbsolutePath());
        editPlanRegex("  target-sha256: [0-9a-f]+", "  target-sha256: " + sha256Of(pom));

        assertApplyRefused(M, true, "does not end with src/main/resources/META-INF/PERMISSIONS.LIST");
        assertEquals(before, read(pom));
    }

    /** 'generated:' is swapped for a superset list (AllPermission) with its own hash. */
    @Test
    public void testApplyRefusesASwappedGeneratedList() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo();
        makePlan();
        File list = new File(gen, M + "/META-INF/PERMISSIONS.LIST");
        File evil = new File(tempDir, "evil/" + M + "/META-INF/PERMISSIONS.LIST");
        evil.getParentFile().mkdirs();
        Files.write(evil.toPath(), (read(list) + "(java.security.AllPermission)\n").getBytes(UTF8));
        editPlan("generated: " + list.getAbsolutePath(), "generated: " + evil.getAbsolutePath());
        editPlanRegex("  generated-sha256: [0-9a-f]+", "  generated-sha256: " + sha256Of(evil));
        String before = read(mahaloTarget);

        assertApplyRefused(M, true, "make a new plan");
        assertEquals(before, read(mahaloTarget));
        assertFalse(read(mahaloTarget).contains("AllPermission"));
    }

    /** Hashes and paths match, but the ADDED entry a reviewer would read is hidden from the plan. */
    @Test
    public void testApplyRefusesAPlanThatHidesAnAddedEntry() throws Exception {
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        makePlan();
        String before = read(mahaloTarget);
        editPlan("    + " + JAVA_HOME + "\n", "");
        editPlan("  ADDED: 2", "  ADDED: 1");

        assertApplyRefused(M, true, "differs from what the planner derives");
        assertEquals(before, read(mahaloTarget));
    }

    /** A planner refusal ("no -dl module") is flipped to PROMOTABLE with a target under the root. */
    @Test
    public void testApplyRefusesARefusedArtefactFlippedToPromotable() throws Exception {
        new ProxyPolicyGenerator().generate(policy("o.policy", proxy("orphan-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        makePlan();
        File planted = new File(tree, "services/orphan/orphan-dl/src/main/resources/META-INF/PERMISSIONS.LIST");
        editPlanRegex("  status: REFUSED: .*", "  status: PROMOTABLE\n  target: " + planted.getAbsolutePath()
            + "\n  target-sha256: NEW FILE");

        assertApplyRefused("orphan-dl-4.0.0-SNAPSHOT", true, "the plan refuses it: no -dl module");
        assertFalse(planted.getParentFile().getParentFile().getParentFile().getParentFile().exists());
    }

    /** A list with no '# Source policy:' line is refused by the plan; flipping the status does not undo it. */
    @Test
    public void testApplyRefusesAListWithoutProvenanceFlippedToPromotable() throws Exception {
        generateMahalo();
        File list = new File(gen, M + "/META-INF/PERMISSIONS.LIST");
        Files.write(list.toPath(), read(list).replaceAll("# Source policy: [^\n]*\n", "").getBytes(UTF8));
        makePlan();
        assertTrue(block(read(plan), M), block(read(plan), M).contains("REFUSED: no provenance line"));
        String before = read(mahaloTarget);
        editPlanRegex("  status: REFUSED: .*", "  status: PROMOTABLE");

        assertApplyRefused(M, true, "no provenance line");
        assertEquals(before, read(mahaloTarget));
    }

    /** A plan file moved under a module root is refused by apply too, as the planner refuses to write one there. */
    @Test
    public void testApplyRefusesAPlanFileUnderAModuleRoot() throws Exception {
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        makePlan();
        File inside = new File(tree, "promote.plan");
        Files.copy(plan.toPath(), inside.toPath());
        String before = read(mahaloTarget);

        try {
            ListPromotion.apply(inside, sha256Of(inside), gen, roots(), NOMAP, Arrays.asList(M), true, true);
            fail("a plan under the source tree");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("lies under module root"));
        }
        assertEquals(before, read(mahaloTarget));
    }

    /** The reviewer's SHA-256 binds apply to the plan file that was read. */
    @Test
    public void testApplyRequiresTheApprovedPlanSha() throws Exception {
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        makePlan();
        String good = sha256Of(plan);
        String before = read(mahaloTarget);
        File roots = tree;
        List<File> r = Collections.singletonList(roots);

        for (String bad : new String[]{null, "", "0000", good.substring(1)}) {
            try {
                ListPromotion.apply(plan, bad, gen, r, NOMAP, Arrays.asList(M), true, true);
                fail("sha " + bad + " must be refused");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("SHA-256 " + good));
            }
        }
        Files.write(plan.toPath(), (read(plan) + "# edited after it was read\n").getBytes(UTF8));
        try {
            ListPromotion.apply(plan, good, gen, r, NOMAP, Arrays.asList(M), true, true);
            fail("a plan edited after the reviewer took its SHA-256");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not the approved"));
        }
        assertEquals(before, read(mahaloTarget));
        assertTrue(ListPromotion.apply(plan, sha256Of(plan), gen, r, NOMAP, Arrays.asList(M), true, true)
            .contains("WROTE"));
    }

    // ------------------------------------------------------- links and junctions

    @Test
    public void testAModuleThatIsALinkIsRefusedAndNotEntered() throws Exception {
        File real = new File(tempDir, "linked/jsk-dl");
        Files.createDirectories(real.toPath());
        Files.write(new File(real, "pom.xml").toPath(), "<project/>".getBytes(UTF8));
        File link = new File(tree, "services/jsk/jsk-dl");
        link.getParentFile().mkdirs();
        org.junit.Assume.assumeTrue("the OS refused to create a link", makeLink(link, real));
        new ProxyPolicyGenerator().generate(policy("j.policy", proxy("jsk-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);

        try {
            makePlan();
            fail("a module that is a link must be refused");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("symbolic link or junction"));
        }
        assertFalse("no plan", plan.exists());
        assertFalse("nothing was created behind the link", new File(real, "src").exists());

        // a link that is not itself a module is not entered, so what is behind it is not found
        Files.delete(link.toPath()); // the link only, never what it points at
        File behind = new File(tempDir, "linked2/bar-dl");
        Files.createDirectories(behind.toPath());
        Files.write(new File(behind, "pom.xml").toPath(), "<project/>".getBytes(UTF8));
        File outer = new File(tree, "services/outer");
        org.junit.Assume.assumeTrue("the OS refused to create a link", makeLink(outer, behind.getParentFile()));
        ProxyPolicyGenerator.deleteTree(new File(gen, "jsk-dl-4.0.0-SNAPSHOT"));
        new ProxyPolicyGenerator().generate(policy("b.policy", proxy("bar-dl-4.0.0-SNAPSHOT",
            "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        ListPromotion.Plan p = makePlan();
        assertEquals(1, p.items.size());
        assertNotNull(p.items.get(0).refusal);
        assertTrue(p.items.get(0).refusal, p.items.get(0).refusal.contains("no -dl module named bar-dl"));
    }

    // ------------------------------------------------------------ atomicity

    @Test
    public void testFailedMoveRestoresTheTargetsAlreadyMoved() throws Exception {
        write(mahaloTarget, GET_CL);
        File reggieModule = new File(tree, "services/reggie/reggie-dl");
        ProxyPolicyGenerator.deleteTree(new File(reggieModule, "src")); // reggie: NEW FILE, no directories
        new ProxyPolicyGenerator().generate(policy("f.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        makePlan();
        final String before = read(mahaloTarget);
        final int[] calls = {0};
        final boolean[] firstMoved = {false};
        ListPromotion.Mover saved = ListPromotion.mover;
        ListPromotion.mover = new ListPromotion.Mover() {
            public void move(java.nio.file.Path from, java.nio.file.Path to) throws java.io.IOException {
                if (++calls[0] == 2) throw new java.io.IOException("injected failure");
                Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                firstMoved[0] = true;
            }
        };
        try {
            applyPlan(Arrays.asList(M, "reggie-dl-4.0.0-SNAPSHOT"), false);
            fail("the second move fails");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("injected failure"));
            assertTrue(e.getMessage(), e.getMessage().contains("restored"));
        } finally {
            ListPromotion.mover = saved;
        }

        assertTrue("the first artefact really was moved before the failure", firstMoved[0]);
        assertEquals("and was restored", before, read(mahaloTarget));
        assertFalse("the new file's directories are gone again", new File(reggieModule, "src").exists());
        assertEquals("no staging directory is left", Arrays.asList("promote.plan"),
            Arrays.asList(plan.getParentFile().list()));
        assertEquals("no temp file beside the target", Arrays.asList("PERMISSIONS.LIST"),
            Arrays.asList(mahaloTarget.getParentFile().list()));
    }

    @Test
    public void testApplyLeavesNothingButTheListInTheSourceTree() throws Exception {
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        makePlan();
        applyPlan(Arrays.asList(M), true);
        assertEquals(Arrays.asList("PERMISSIONS.LIST"), Arrays.asList(mahaloTarget.getParentFile().list()));
        assertEquals(Arrays.asList("promote.plan"), Arrays.asList(plan.getParentFile().list()));
    }

    // ------------------------------------------------------------ held entries

    @Test
    public void testApplyRefusesHeldEntriesUnlessAllowed() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo("permission au.zeus.jdk.authorization.guards.LoadClassPermission \"ALLOW\";",
            "permission java.io.FilePermission \"/home/user/m.jar\", \"read\";");
        ListPromotion.Plan p = makePlan();
        assertTrue(p.summary(), p.summary().contains("held 1, dropped 1"));
        assertTrue(block(read(plan), M), block(read(plan), M).contains("  held: 1\n  dropped: 1\n"));
        String before = read(mahaloTarget);

        assertApplyRefused(M, false, "pass --allow-held");
        assertEquals(before, read(mahaloTarget));
        String report = applyPlan(Arrays.asList(M), false, true);
        assertTrue(report, report.contains("held 1, dropped 1"));
        assertTrue(read(mahaloTarget).contains("# HELD"));
    }

    // ------------------------------------------------------------ the CI gate

    @Test
    public void testCheckFailsOnRemovedOrUnparsableAndWritesNothing() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        Map<String, byte[]> before = bytes(tempDir);

        ListPromotion.CheckResult ok = ListPromotion.check(gen, roots(), NOMAP);
        assertFalse(ok.report, ok.failed);
        assertTrue(ok.report, ok.report.contains("check " + M + ": ADDED 1\n  + " + JAVA_HOME));
        assertEquals("the gate writes no file, plan or otherwise", before.keySet(), bytes(tempDir).keySet());

        write(mahaloTarget, GET_CL, SET_IO);
        ListPromotion.CheckResult removed = ListPromotion.check(gen, roots(), NOMAP);
        assertTrue(removed.report, removed.failed);
        assertTrue(removed.report, removed.report.contains("check FAIL " + M + ": REMOVED 1\n  - " + SET_IO));

        write(mahaloTarget, GET_CL);
        File list = new File(gen, M + "/META-INF/PERMISSIONS.LIST");
        Files.write(list.toPath(), Arrays.asList("# Source policy: x.policy", GET_CL,
            "(java.util.PropertyPermission \"java.home\" \"read\""));
        ListPromotion.CheckResult bad = ListPromotion.check(gen, roots(), NOMAP);
        assertTrue(bad.report, bad.failed);
        assertTrue(bad.report, bad.report.contains("a list does not parse"));
    }

    @Test
    public void testCliPlanCheckExitCodes() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(so, true, "UTF-8");
        PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
        String[] check = {"--out", gen.getPath(), "--promote-plan-check", tree.getPath()};

        assertEquals(0, ProxyPolicyGenerator.run(check, out, err));
        assertTrue(so.toString("UTF-8"), so.toString("UTF-8").contains("ADDED 1"));
        assertFalse(new File(gen, "promote.plan").exists());
        write(mahaloTarget, GET_CL, SET_IO);
        assertEquals(1, ProxyPolicyGenerator.run(check, out, err));
        assertEquals("a plan file makes no sense for the gate", 2, ProxyPolicyGenerator.run(
            cat(check, "--plan-file", plan.getPath()), out, err));
        assertEquals("nor does approving", 2, ProxyPolicyGenerator.run(
            cat(check, "--approve", M), out, err));
    }

    // ------------------------------------------------------------ source kind

    @Test
    public void testPlanSaysWhichKindOfPolicyProducedTheList() throws Exception {
        String g = "permission java.lang.RuntimePermission \"getClassLoader\";";
        File[] outs = new File[5];
        String[] policies = {
            "// recorded by polpAudit\n" + proxy("mahalo-dl-4.0.0-SNAPSHOT", g),
            "// ${jsk.home} template\n" + proxy("mahalo-dl-4.0.0-SNAPSHOT", g),
            proxy("mahalo-dl-4.0.0-SNAPSHOT", g),
            proxy("mahalo-dl-4.0.0-SNAPSHOT", g),
            proxy("mahalo-dl-4.0.0-SNAPSHOT", g)};
        File[] files = new File[5];
        for (int n = 0; n < 5; n++) {
            File dir = new File(tempDir, "kind" + n);
            dir.mkdirs();
            files[n] = new File(dir, "src.policy");
            Files.write(files[n].toPath(), policies[n].getBytes(UTF8));
            outs[n] = new File(dir, "out");
            new ProxyPolicyGenerator().generate(files[n], outs[n]);
        }
        files[3].delete();
        Files.write(files[4].toPath(), "// changed since\n".getBytes(UTF8));
        String[] want = {"polpAudit-generated", "hand-written or template", "unknown (the policy file carries no mark",
            "unknown (source policy src.policy not found", "unknown (source policy src.policy is not the file"};
        for (int n = 0; n < 5; n++) {
            ListPromotion.Plan p = ListPromotion.derive(outs[n], roots(), NOMAP);
            assertTrue(n + ": " + p.items.get(0).sourceKind, p.items.get(0).sourceKind.startsWith(want[n]));
            assertTrue(p.summary(), p.summary().contains("source kind: " + p.items.get(0).sourceKind));
        }
    }

    // ----------------------------------------------------------- artefact map

    @Test
    public void testArtefactMapNamesTheModuleAndUnmappedNamesStayRefused() throws Exception {
        new ProxyPolicyGenerator().generate(policy("q.policy",
            proxy("qa1-mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("qa1-other-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("qa1-gone-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";")), gen);
        ListPromotion.Plan unmapped = ListPromotion.derive(gen, roots(), NOMAP);
        for (ListPromotion.Item i : unmapped.items) {
            assertTrue(i.artefact + ": " + i.refusal, i.refusal.contains("no -dl module named"));
        }

        File map = new File(tempDir, "artefacts.map");
        Files.write(map.toPath(), Arrays.asList("# reviewed by a human", "",
            "qa1-mahalo-dl-4.0.0-SNAPSHOT = mahalo-dl", "qa1-gone-dl-4.0.0-SNAPSHOT = gone-dl"), UTF8);
        Map<String, String> loaded = ListPromotion.loadArtefactMap(map);
        ListPromotion.Plan mapped = ListPromotion.derive(gen, roots(), loaded);
        for (ListPromotion.Item i : mapped.items) {
            if (i.artefact.startsWith("qa1-mahalo")) {
                assertNull(i.refusal, i.refusal);
                assertEquals(mahaloTarget.getAbsoluteFile(), i.target);
            } else if (i.artefact.startsWith("qa1-gone")) {
                assertTrue(i.refusal, i.refusal.contains("no -dl module named gone-dl")
                    && i.refusal.contains("artefact map"));
            } else {
                assertTrue("unmapped stays refused: " + i.refusal, i.refusal.contains("no -dl module named qa1-other-dl"));
            }
        }

        for (String bad : new String[]{"no equals sign", "a = ../etc", "= mahalo-dl", "a = b/c"}) {
            Files.write(map.toPath(), Arrays.asList(bad), UTF8);
            try {
                ListPromotion.loadArtefactMap(map);
                fail("'" + bad + "' must be rejected");
            } catch (ProxyPolicyGenerator.GenerationException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("expected 'sanitised-name = module-dir-name'"));
            }
        }
        Files.write(map.toPath(), Arrays.asList("a = x-dl", "a = y-dl"), UTF8);
        try {
            ListPromotion.loadArtefactMap(map);
            fail("one name mapped twice");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("mapped twice"));
        }
    }

    // ------------------------------------------------------- round 4 findings

    /**
     * R3: a list under --out that holds an entry of a class the generator never writes was
     * edited: the plan refuses it, the gate fails on it and apply refuses it, for each class.
     */
    @Test
    public void testAListHoldingAnExcludedClassIsNotPromotable() throws Exception {
        String[] bad = {
            "(java.security.AllPermission)",
            "(net.jini.security.policy.UmbrellaGrantPermission \"x\")",
            "(net.jini.security.GrantPermission \"java.security.AllPermission\")",
            "(au.zeus.jdk.authorization.guards.LoadClassPermission \"*\")",
            "(net.jini.loader.LoadClassPermission \"ALLOW\")",
            "(java.net.URLPermission \"http://x/a.jar\" \"GET:\")",
            "(net.jini.loader.DownloadPermission)"};
        for (String entry : bad) {
            ProxyPolicyGenerator.deleteTree(gen);
            write(mahaloTarget, GET_CL);
            String before = read(mahaloTarget);
            generateMahalo();
            File list = new File(gen, M + "/META-INF/PERMISSIONS.LIST");
            Files.write(list.toPath(), ("  " + entry + "\n").getBytes(UTF8),
                java.nio.file.StandardOpenOption.APPEND);

            ListPromotion.Plan p = makePlan();
            assertNotNull(entry, p.items.get(0).refusal);
            assertTrue(p.summary(), p.summary().contains("REFUSED, the list was edited: it holds an entry of "));
            assertTrue(entry, ListPromotion.check(gen, roots(), NOMAP).failed);
            assertApplyRefused(M, true, "the list was edited");
            assertEquals(entry, before, read(mahaloTarget));
        }
    }

    /** R5: two artefacts for one module: the second is refused, naming the first. */
    @Test
    public void testTwoArtefactsForOneModuleTargetAreRefused() throws Exception {
        write(mahaloTarget, GET_CL);
        new ProxyPolicyGenerator().generate(policy("v.policy",
            proxy("mahalo-dl-4.0.0", "permission java.lang.RuntimePermission \"getClassLoader\";"),
            proxy("mahalo-dl-4.0.1", "permission java.util.PropertyPermission \"java.home\", \"read\";")),
            gen);
        ListPromotion.Plan p = makePlan();
        assertNull(p.items.get(0).refusal);
        String why = p.items.get(1).refusal;
        assertNotNull(p.summary(), why);
        assertTrue(why, why.contains("ambiguous target") && why.contains("mahalo-dl-4.0.0")
            && why.contains("mahalo-dl-4.0.1"));
        String before = read(mahaloTarget);

        assertApplyRefused("mahalo-dl-4.0.1", false, "ambiguous target");
        try {
            applyPlan(Arrays.asList("mahalo-dl-4.0.1", "mahalo-dl-4.0.0"), true);
            fail("the second artefact is refused, so nothing is written");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nothing was written"));
        }
        assertEquals(before, read(mahaloTarget));
    }

    /**
     * R6: when the move that failed left its target as it was, the message says restored: it
     * must not say NOT restored because rewriting an unchanged read-only file was refused.
     */
    @Test
    public void testFailedMoveOnAnUnchangedReadOnlyTargetIsNotReportedAsNotRestored() throws Exception {
        write(mahaloTarget, GET_CL);
        write(reggieTarget, GET_CL);
        new ProxyPolicyGenerator().generate(policy("ro.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";"),
            proxy("reggie-dl-4.0.0-SNAPSHOT", "permission java.lang.RuntimePermission \"getClassLoader\";",
                "permission java.util.PropertyPermission \"java.home\", \"read\";")), gen);
        makePlan();
        String mahaloBefore = read(mahaloTarget);
        String reggieBefore = read(reggieTarget);
        final int[] calls = {0};
        ListPromotion.Mover saved = ListPromotion.mover;
        ListPromotion.mover = new ListPromotion.Mover() {
            public void move(java.nio.file.Path from, java.nio.file.Path to) throws java.io.IOException {
                if (++calls[0] == 2) throw new java.io.IOException("injected failure");
                Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        };
        assertTrue(reggieTarget.setReadOnly());
        try {
            applyPlan(Arrays.asList(M, "reggie-dl-4.0.0-SNAPSHOT"), true);
            fail("the second move fails");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("injected failure"));
            assertTrue(e.getMessage(), e.getMessage().contains("restored"));
            assertFalse(e.getMessage(), e.getMessage().contains("NOT restored"));
        } finally {
            ListPromotion.mover = saved;
            reggieTarget.setWritable(true);
        }
        assertEquals(mahaloBefore, read(mahaloTarget));
        assertEquals(reggieBefore, read(reggieTarget));
        assertEquals("no staging directory is kept", Arrays.asList("promote.plan"),
            Arrays.asList(plan.getParentFile().list()));
    }

    /** R6: a staging directory left by an interrupted apply is reported by the next plan and apply. */
    @Test
    public void testALeftoverStagingDirectoryIsWarnedAbout() throws Exception {
        generateMahalo("permission java.util.PropertyPermission \"java.home\", \"read\";");
        File left = new File(plan.getParentFile(), ".promote-staging-123");
        assertTrue(left.mkdirs());

        ListPromotion.Plan p = makePlan();

        assertEquals(p.warnings.toString(), 1, p.warnings.size());
        assertTrue(p.warnings.get(0), p.warnings.get(0).contains(".promote-staging-123")
            && p.warnings.get(0).contains("interrupted apply"));
        assertTrue(p.summary(), p.summary().contains("plan WARNING: "));
        assertTrue(read(plan), read(plan).contains("# WARNING: "));
        String report = applyPlan(Arrays.asList(M), false);
        assertTrue(report, report.contains("WARNING: ") && report.contains(".promote-staging-123"));
        assertTrue("it is the operator's to delete", left.isDirectory());
    }

    /**
     * R7: a control or format character in a list read from --out is refused at plan, gate and
     * apply, and is shown escaped: neither the CI log nor the plan file carries the raw character.
     */
    @Test
    public void testControlCharactersInAListAreRefusedAndNeverPrintedRaw() throws Exception {
        write(mahaloTarget, GET_CL);
        generateMahalo();
        File list = new File(gen, M + "/META-INF/PERMISSIONS.LIST");
        Files.write(list.toPath(), ("(java.lang.RuntimePermission \"a\u001b[2Jb\")\n"
            + "(java.lang.RuntimePermission \"c\u202Ed\")\n").getBytes(UTF8),
            java.nio.file.StandardOpenOption.APPEND);

        makePlan();
        String planText = read(plan);
        assertTrue(planText, planText.contains("status: REFUSED: "));
        assertTrue(planText, planText.contains("control or format character"));
        assertTrue(planText, planText.contains("\\u001B[2J"));
        assertEquals("the plan file holds no raw ESC", -1, planText.indexOf('\u001b'));
        assertEquals("nor a raw bidirectional mark", -1, planText.indexOf('\u202e'));
        assertApplyRefused(M, true, "control or format character");

        ByteArrayOutputStream so = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(so, true, "UTF-8");
        int rc = ProxyPolicyGenerator.run(new String[]{"--out", gen.getPath(), "--promote-plan-check",
            tree.getPath()}, out, new PrintStream(new ByteArrayOutputStream()));
        String printed = so.toString("UTF-8");
        assertEquals(printed, 1, rc);
        assertEquals("the CI log holds no raw ESC", -1, printed.indexOf('\u001b'));
        assertEquals(-1, printed.indexOf('\u202e'));
        assertTrue(printed, printed.contains("\\u001B[2J"));
    }

    /** R8: a malformed path argument is a REFUSED message and rc 1, not a stack trace. */
    @Test
    public void testAMalformedPathArgumentIsRefusedNotAStackTrace() throws Exception {
        generateMahalo();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        int rc = ProxyPolicyGenerator.run(new String[]{"--out", gen.getPath(), "--promote-plan-check",
            tree.getPath(), "bad" + (char) 0 + "root"}, new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(se, true, "UTF-8"));
        String err = se.toString("UTF-8");
        assertEquals(err, 1, rc);
        assertTrue(err, err.contains("REFUSED: a path argument is malformed"));
        assertFalse(err, err.contains("\tat "));
    }

    /**
     * A target in a sibling directory whose name merely starts with the root's name is not under
     * the root.  Only the claimed-target check can say so (the render check would also refuse it,
     * with another message), so this pins that check to path components, not to text.
     */
    @Test
    public void testASiblingWhoseNameStartsWithTheRootNameIsNotUnderTheRoot() throws Exception {
        generateMahalo();
        makePlan();
        File sibling = new File(tempDir, "tree-evil/mahalo-dl/src/main/resources/META-INF/PERMISSIONS.LIST");
        editPlan("target: " + mahaloTarget.getAbsolutePath(), "target: " + sibling.getAbsolutePath());
        editPlanRegex("  target-sha256: [0-9a-f]+", "  target-sha256: NEW FILE");

        assertApplyRefused(M, true, "is not under a module root");
        assertFalse(sibling.exists());
        assertFalse(sibling.getParentFile().exists());
    }

    // --------------------------------------------------------------- helpers

    private static final Map<String, String> NOMAP = Collections.emptyMap();

    private List<File> roots() {
        return Collections.singletonList(tree);
    }

    private ListPromotion.Plan makePlan() throws Exception {
        return ListPromotion.plan(gen, roots(), NOMAP, plan);
    }

    private String applyPlan(List<String> approve, boolean allowRemovals) throws Exception {
        return applyPlan(approve, allowRemovals, false);
    }

    private String applyPlan(List<String> approve, boolean allowRemovals, boolean allowHeld)
            throws Exception {
        return ListPromotion.apply(plan, sha256Of(plan), gen, roots(), NOMAP, approve,
            allowRemovals, allowHeld);
    }

    private void editPlan(String from, String to) throws Exception {
        String text = read(plan);
        assertTrue("plan holds " + from + ":\n" + text, text.contains(from));
        Files.write(plan.toPath(), text.replace(from, to).getBytes(UTF8));
    }

    private void editPlanRegex(String regex, String to) throws Exception {
        String text = read(plan);
        String edited = text.replaceFirst(regex, java.util.regex.Matcher.quoteReplacement(to));
        assertFalse("plan matches " + regex + ":\n" + text, text.equals(edited));
        Files.write(plan.toPath(), edited.getBytes(UTF8));
    }

    private static String[] cat(String[] a, String... more) {
        List<String> all = new ArrayList<String>(Arrays.asList(a));
        all.addAll(Arrays.asList(more));
        return all.toArray(new String[0]);
    }

    /** A symbolic link, else on Windows a junction; {@code false} if the OS refuses both. */
    private static boolean makeLink(File link, File target) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
            return true;
        } catch (Exception | Error e) {
            // fall through to a junction
        }
        if (File.separatorChar != '\\') return false;
        try {
            Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.getAbsolutePath(),
                target.getAbsolutePath()).redirectErrorStream(true).start();
            while (p.getInputStream().read() >= 0) { }
            return p.waitFor() == 0 && link.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    private void assertApplyRefused(String artefact, boolean allowRemovals, String reason)
            throws Exception {
        try {
            applyPlan(Arrays.asList(artefact), allowRemovals);
            fail(artefact + " must be refused (" + reason + ")");
        } catch (ProxyPolicyGenerator.GenerationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(reason));
        }
    }

    private void generateMahalo(String... more) throws Exception {
        List<String> perms = new ArrayList<String>();
        perms.add("permission java.lang.RuntimePermission \"getClassLoader\";");
        perms.addAll(Arrays.asList(more));
        new ProxyPolicyGenerator().generate(policy("m.policy",
            proxy("mahalo-dl-4.0.0-SNAPSHOT", perms.toArray(new String[0]))), gen);
    }

    /** Creates a module directory with a pom and an empty list; returns the list file. */
    private File module(String path) throws Exception {
        File dir = new File(tree, path);
        File list = new File(dir, "src/main/resources/META-INF/PERMISSIONS.LIST");
        list.getParentFile().mkdirs();
        Files.write(new File(dir, "pom.xml").toPath(), "<project/>".getBytes(UTF8));
        Files.write(list.toPath(), new byte[0]);
        return list;
    }

    private static void write(File f, String... lines) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        Files.write(f.toPath(), sb.toString().getBytes(UTF8));
    }

    private File policy(String name, String... grants) throws Exception {
        File f = new File(tempDir, name);
        StringBuilder sb = new StringBuilder();
        for (String g : grants) sb.append(g).append('\n');
        Files.write(f.toPath(), sb.toString().getBytes(UTF8));
        return f;
    }

    private static String proxy(String artefact, String... permissions) {
        StringBuilder sb = new StringBuilder("grant codebase \"file:/lib/" + artefact + ".jar\" {\n");
        for (String p : permissions) sb.append("    ").append(p).append('\n');
        return sb.append("};").toString();
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), UTF8);
    }

    /** The text of one ARTEFACT block of a plan file. */
    private static String block(String planText, String artefact) {
        int start = planText.indexOf("ARTEFACT " + artefact + "\n");
        assertTrue("no block for " + artefact + " in " + planText, start >= 0);
        return planText.substring(start, planText.indexOf("\nEND\n", start) + 1);
    }

    private static String sha256Of(File f) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static Map<String, byte[]> bytes(File dir) throws Exception {
        Map<String, byte[]> m = new TreeMap<String, byte[]>();
        collect(dir.toURI(), dir, m);
        return m;
    }

    private static void collect(java.net.URI root, File f, Map<String, byte[]> m) throws Exception {
        String rel = root.relativize(f.toURI()).getPath();
        File[] kids = f.listFiles();
        if (kids == null) {
            m.put(rel, Files.readAllBytes(f.toPath()));
            return;
        }
        m.put(rel, new byte[0]);
        for (File k : kids) collect(root, k, m);
    }
}
