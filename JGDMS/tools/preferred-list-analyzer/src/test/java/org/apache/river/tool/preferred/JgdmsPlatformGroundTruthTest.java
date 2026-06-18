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
package org.apache.river.tool.preferred;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Acceptance test: the analyzer, run on the compiled {@code jgdms-platform}
 * module, must reproduce the ground-truth classifications from the 2026-06-17
 * manual audit (SOW &sect;8).
 *
 * <p>Skipped (via {@link org.junit.Assume}) when the compiled module is not
 * available, so the build still passes in an environment where
 * {@code jgdms-platform} has not been built.  Point at an explicit directory
 * with {@code -Djgdms.platform.classes=/path/to/target/classes}.
 */
public class JgdmsPlatformGroundTruthTest {

    private static File platformClasses;
    private Map<String, ClassDecision> byName;

    @BeforeClass
    public static void locate() {
        String prop = System.getProperty("jgdms.platform.classes");
        File f;
        if (prop != null) {
            f = new File(prop);
        } else {
            // surefire runs with user.dir = this module's directory
            f = new File(System.getProperty("user.dir"),
                    "../../jgdms-platform/target/classes");
        }
        try {
            f = f.getCanonicalFile();
        } catch (Exception ignored) { }
        platformClasses = f;
    }

    @Before
    public void analyze() throws Exception {
        assumeTrue("jgdms-platform not compiled at " + platformClasses
                        + " (set -Djgdms.platform.classes=...)",
                platformClasses != null && platformClasses.isDirectory());
        List<ClassDecision> ds = new PreferredAnalyzer().analyzeDirectory(platformClasses);
        byName = new LinkedHashMap<String, ClassDecision>();
        for (ClassDecision d : ds) byName.put(d.getInternalName(), d);
    }

    private ClassDecision get(String n) {
        ClassDecision d = byName.get(n);
        assertNotNull("expected class in analysis: " + n, d);
        return d;
    }

    private void assertDecision(String n, Decision expected) {
        assertEquals(n + " classification", expected, get(n).getAnalysisDecision());
    }

    // ---- §8 PREFER (isolate) ----------------------------------------------

    @Test
    public void uuidFactoryAnalysisIsPreferViaSharedSecureRandom() {
        // UuidFactory keeps one shared SecureRandom (a CSPRNG) by design; its
        // redundant lazy-init lock was dropped, so the only remaining hazard is
        // the contended SecureRandom field. A deliberate-share override
        // (jgdms-platform META-INF/preferred-overrides.txt) demotes it to share
        // in the emitted list -- the build gate enforces that end to end.
        ClassDecision d = get("net/jini/id/UuidFactory");
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
        assertTrue(FixtureSupport.hasKind(d, HazardKind.CONTENDED_STATIC_FIELD));
        assertFalse("lazy-init lock was dropped",
                FixtureSupport.hasKind(d, HazardKind.SYNCHRONIZED_ON_STATIC_FIELD));
    }

    @Test
    public void proxyTrustExporterSharesAfterCleanerFix() {
        // The §8 audit flagged ProxyTrustExporter (static Executor pool + lock).
        // Its WeakReference/ReferenceQueue/reaper-pool machinery was replaced
        // with java.lang.ref.Cleaner, dissolving the hazard, so it now SHAREs
        // with no hazard rather than being preferred.
        ClassDecision d = get("net/jini/security/proxytrust/ProxyTrustExporter");
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue("Cleaner refactor removed all static-state hazards",
                d.getHazards().isEmpty());
        assertFalse(d.isNeedsReview());
    }

    @Test
    public void preferSetIsExactlyUuidFactory() {
        int prefer = 0;
        StringBuilder names = new StringBuilder();
        for (ClassDecision d : byName.values()) {
            if (d.getAnalysisDecision() == Decision.PREFER) {
                prefer++;
                names.append(' ').append(d.getInternalName());
            }
        }
        // After the ProxyTrustExporter Cleaner fix, UuidFactory is the only
        // remaining shared-static isolation hazard the analysis finds. It is in
        // turn recorded as a deliberate share via the override file, so the
        // emitted PREFERRED.LIST is empty (platform needs no preferred classes).
        assertEquals("share-by-default: analysed PREFER set should be exactly "
                + "{UuidFactory}, was:" + names, 1, prefer);
    }

    // ---- §8 CONFLICT (must share; lock-free TODO) -------------------------

    @Test
    public void delegationAbsoluteTimeIsConflict() {
        ClassDecision d = get("net/jini/core/constraint/DelegationAbsoluteTime");
        assertEquals(Decision.CONFLICT, d.getAnalysisDecision());
        assertTrue(d.isCrossBoundary());
        assertTrue(FixtureSupport.hasKind(d, HazardKind.STATIC_SYNCHRONIZED_METHOD));
    }

    // ---- §8 SHARE ---------------------------------------------------------

    @Test
    public void bootstrapInterfacesShare() {
        assertDecision("net/jini/export/ProxyAccessor", Decision.SHARE);
        assertDecision("net/jini/export/CodebaseAccessor", Decision.SHARE);
        assertDecision("net/jini/export/DynamicProxyCodebaseAccessor", Decision.SHARE);
        assertTrue(get("net/jini/export/ProxyAccessor").isCrossBoundary());
    }

    @Test
    public void crossBoundaryValueTypesShare() {
        assertDecision("net/jini/io/MarshalledInstance", Decision.SHARE);
        assertDecision("net/jini/core/event/EventRegistration", Decision.SHARE);
        assertDecision("net/jini/id/UuidFactory$Impl", Decision.SHARE);
        assertTrue(get("net/jini/id/UuidFactory$Impl").isCrossBoundary());
    }

    @Test
    public void serializerClassesShareAndHaveNoHazard() {
        // serialVersionUID-only classes: the source-regex false positive the tool fixes.
        for (String n : new String[]{
                "org/apache/river/api/io/BooleanSerializer",
                "org/apache/river/api/io/ByteSerializer",
                "org/apache/river/api/io/LongSerializer",
                "org/apache/river/api/io/DoubleSerializer"}) {
            ClassDecision d = get(n);
            assertEquals(n + " should SHARE", Decision.SHARE, d.getAnalysisDecision());
            assertTrue(n + " should carry no hazard", d.getHazards().isEmpty());
        }
    }

    @Test
    public void securityAndClassLoadingShareViaReview() {
        // §8: lazy-cache lock guards classify as SHARE (overridable for SPI isolation).
        assertDecision("net/jini/security/Security", Decision.SHARE);
        assertDecision("net/jini/loader/ClassLoading", Decision.SHARE);
        assertDecision("net/jini/export/ServerContext", Decision.SHARE);
        assertTrue("surfaced for review", get("net/jini/security/Security").isNeedsReview());
    }
}
