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

import static org.apache.river.tool.preferred.FixtureSupport.analyzeFixtures;
import static org.apache.river.tool.preferred.FixtureSupport.decision;
import static org.apache.river.tool.preferred.FixtureSupport.hasKind;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

/**
 * Per-hazard-kind and per-share-kind unit tests against compiled bytecode
 * fixtures (SOW &sect;8).
 */
public class DecisionEngineTest {

    private static final AnalyzerConfig DEF = AnalyzerConfig.defaults();

    // ---- share kinds -------------------------------------------------------

    @Test
    public void instanceOnlyShares() {
        ClassDecision d = decision(analyzeFixtures(DEF, "InstanceOnly"), "InstanceOnly");
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertFalse(d.isNeedsReview());
        assertTrue(d.getHazards().isEmpty());
    }

    @Test
    public void serialVersionUidIsNotAHazard() {
        ClassDecision d = decision(analyzeFixtures(DEF, "SerialVersionUidOnly"),
                "SerialVersionUidOnly");
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue("serialVersionUID must not be a hazard", d.getHazards().isEmpty());
        assertFalse(d.isCrossBoundary());
    }

    @Test
    public void serializableValueIsCrossBoundaryShare() {
        ClassDecision d = decision(analyzeFixtures(DEF, "SerializableValue"),
                "SerializableValue");
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue(d.isCrossBoundary());
    }

    @Test
    public void bootstrapInterfaceImplementorIsCrossBoundaryShare() {
        AnalyzerConfig cfg = AnalyzerConfig.builder()
                .bootstrapInterface(FixtureSupport.FIX + "FakeBootstrap")
                .build();
        Map<String, ClassDecision> m =
                analyzeFixtures(cfg, "FakeBootstrap", "ImplementsBootstrap");
        ClassDecision d = decision(m, "ImplementsBootstrap");
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue(d.isCrossBoundary());
    }

    // ---- criterion (b): lock / blocking coupling ---------------------------

    @Test
    public void staticSynchronizedMethodIsWeakBShareReview() {
        ClassDecision d = decision(analyzeFixtures(DEF, "StaticSyncMethod"),
                "StaticSyncMethod");
        assertTrue(hasKind(d, HazardKind.STATIC_SYNCHRONIZED_METHOD));
        assertEquals(Decision.SHARE, d.getAnalysisDecision()); // bare lock -> review
        assertTrue(d.isNeedsReview());
    }

    @Test
    public void synchronizedOnStaticFieldIsWeakBShareReview() {
        ClassDecision d = decision(analyzeFixtures(DEF, "SyncOnStaticField"),
                "SyncOnStaticField");
        assertTrue(hasKind(d, HazardKind.SYNCHRONIZED_ON_STATIC_FIELD));
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue(d.isNeedsReview());
    }

    @Test
    public void secureRandomFieldIsStrongBPrefer() {
        ClassDecision d = decision(analyzeFixtures(DEF, "SecureRandomField"),
                "SecureRandomField");
        assertTrue(hasKind(d, HazardKind.CONTENDED_STATIC_FIELD));
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
    }

    @Test
    public void executorPoolFieldIsStrongBPrefer() {
        ClassDecision d = decision(analyzeFixtures(DEF, "ExecutorPoolField"),
                "ExecutorPoolField");
        assertTrue(hasKind(d, HazardKind.CONTENDED_STATIC_FIELD));
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
    }

    @Test
    public void blockingCallUnderStaticLockIsStrongBPrefer() {
        ClassDecision d = decision(analyzeFixtures(DEF, "BlockingUnderStaticLock"),
                "BlockingUnderStaticLock");
        assertTrue(hasKind(d, HazardKind.BLOCKING_CALL_UNDER_STATIC_LOCK));
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
    }

    // ---- criterion (a): semantic co-mingling -------------------------------

    @Test
    public void staticFinalMutableMapIsCriterionAShareReview() {
        ClassDecision d = decision(analyzeFixtures(DEF, "StaticFinalMutableMap"),
                "StaticFinalMutableMap");
        assertTrue(hasKind(d, HazardKind.MUTABLE_STATIC_STATE));
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue(d.isNeedsReview());
    }

    @Test
    public void nonFinalStaticSingletonIsCriterionAShareReview() {
        ClassDecision d = decision(analyzeFixtures(DEF, "NonFinalStaticSingleton"),
                "NonFinalStaticSingleton");
        assertTrue(hasKind(d, HazardKind.MUTABLE_STATIC_STATE));
        assertEquals(Decision.SHARE, d.getAnalysisDecision());
        assertTrue(d.isNeedsReview());
    }

    @Test
    public void criterionAPreferredWhenConfigured() {
        AnalyzerConfig cfg = AnalyzerConfig.builder().preferOnCriterionA(true).build();
        ClassDecision d = decision(analyzeFixtures(cfg, "StaticFinalMutableMap"),
                "StaticFinalMutableMap");
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
    }

    // ---- finding #1: cross-boundary safety guard ---------------------------

    @Test
    public void strongBWithUnresolvedSuperDowngradesToReview() {
        // superclass (java.util.EventObject) is outside the analyzed set, so the
        // Serializable-via-base status is unverifiable: must NOT silently prefer.
        ClassDecision d = decision(analyzeFixtures(DEF, "StrongBExternalSuper"),
                "StrongBExternalSuper");
        assertTrue(hasKind(d, HazardKind.CONTENDED_STATIC_FIELD)); // strong (b) present
        assertEquals(Decision.SHARE, d.getAnalysisDecision());     // but downgraded
        assertTrue(d.isNeedsReview());
        assertNotNull("downgrade should explain itself", d.getReviewNote());
    }

    @Test
    public void strongBWithResolvedSuperChainStillPrefers() {
        // entire super chain (StrongBLocalSuper -> LocalBase -> Object) is in-set,
        // so the guard must not fire.
        ClassDecision d = decision(
                analyzeFixtures(DEF, "StrongBLocalSuper", "LocalBase"),
                "StrongBLocalSuper");
        assertEquals(Decision.PREFER, d.getAnalysisDecision());
        assertNull(d.getReviewNote());
    }
}
