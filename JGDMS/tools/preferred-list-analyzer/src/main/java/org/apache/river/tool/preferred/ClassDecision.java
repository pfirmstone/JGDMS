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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The analyzer's classification of a single class: the {@link Decision}, the
 * {@link Hazard} evidence behind it, whether the class is a cross-boundary
 * (must-share) type, and any human {@linkplain #getOverrideValue() override}
 * applied on top of the analysis.
 *
 * @see DecisionEngine
 * @see OverrideFile
 */
public final class ClassDecision {

    private final String       internalName;
    private final Decision     analysisDecision;
    private final List<Hazard> hazards;
    private final boolean      crossBoundary;
    private final List<String> crossBoundaryReasons;
    private final boolean      needsReview;

    // Override state (applied by OverrideFile.apply over the analysis).
    private Boolean overrideValue;   // null = no override; else the forced Preferred:
    private String  overrideReason;

    ClassDecision(String internalName, Decision analysisDecision, List<Hazard> hazards,
                  boolean crossBoundary, List<String> crossBoundaryReasons,
                  boolean needsReview) {
        this.internalName         = internalName;
        this.analysisDecision     = analysisDecision;
        this.hazards              = Collections.unmodifiableList(new ArrayList<Hazard>(hazards));
        this.crossBoundary        = crossBoundary;
        this.crossBoundaryReasons = Collections.unmodifiableList(
                new ArrayList<String>(crossBoundaryReasons));
        this.needsReview          = needsReview;
    }

    /** JVM internal class name, e.g. {@code net/jini/id/UuidFactory}. */
    public String getInternalName() {
        return internalName;
    }

    /** The resource entry name, e.g. {@code net/jini/id/UuidFactory.class}. */
    public String getResourceName() {
        return internalName + ".class";
    }

    /** The decision the analysis reached, before any override. */
    public Decision getAnalysisDecision() {
        return analysisDecision;
    }

    /** The hazards (evidence) found, in detection order. Never null. */
    public List<Hazard> getHazards() {
        return hazards;
    }

    public boolean hasCriterionA() {
        for (Hazard h : hazards) if (h.getCriterion() == Hazard.Criterion.A) return true;
        return false;
    }

    public boolean hasCriterionB() {
        for (Hazard h : hazards) if (h.getCriterion() == Hazard.Criterion.B) return true;
        return false;
    }

    /** True if the class is a cross-boundary type that must keep a single identity. */
    public boolean isCrossBoundary() {
        return crossBoundary;
    }

    public List<String> getCrossBoundaryReasons() {
        return crossBoundaryReasons;
    }

    /**
     * True if the class should be surfaced to a human reviewer &mdash; either a
     * criterion (a) candidate that defaulted to share, or a {@link
     * Decision#CONFLICT}.  These are the rows the override file is meant to
     * resolve.
     */
    public boolean isNeedsReview() {
        return needsReview;
    }

    // ---- override application ----------------------------------------------

    void applyOverride(boolean value, String reason) {
        this.overrideValue  = Boolean.valueOf(value);
        this.overrideReason = reason;
    }

    /** The override {@code Preferred:} value, or {@code null} if not overridden. */
    public Boolean getOverrideValue() {
        return overrideValue;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public boolean isOverridden() {
        return overrideValue != null;
    }

    /**
     * True if a human override forces a different {@code Preferred:} value than
     * the analysis would have emitted &mdash; a divergence worth reporting.
     */
    public boolean isOverrideDivergent() {
        return overrideValue != null
                && overrideValue.booleanValue() != analysisDecision.isPreferred();
    }

    /** The final {@code Preferred:} boolean: the override if present, else the analysis. */
    public boolean isPreferred() {
        return (overrideValue != null)
                ? overrideValue.booleanValue()
                : analysisDecision.isPreferred();
    }

    @Override
    public String toString() {
        return internalName + " -> " + (isPreferred() ? "true" : "false")
                + " (" + (isOverridden() ? "override" : analysisDecision) + ")";
    }
}
