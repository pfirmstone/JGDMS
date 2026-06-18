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

/**
 * A single piece of evidence that a class carries a shared-static-state
 * isolation hazard, as defined by the classification criteria in the
 * Preferred-Class Analyzer SOW (&sect;2).
 *
 * <p>Each hazard belongs to one of two criteria:
 * <ul>
 *   <li>{@link Criterion#B Criterion (b)} &mdash; lock / blocking coupling.
 *       Objective and decisive: drives a {@link Decision#PREFER} decision when
 *       the class is not cross-boundary.</li>
 *   <li>{@link Criterion#A Criterion (a)} &mdash; semantic co-mingling of
 *       mutable static state.  Detected from bytecode but whose
 *       <em>per-deployment significance</em> is a human judgement, so by
 *       default it is surfaced for review rather than driving an automatic
 *       prefer (see {@link AnalyzerConfig#isPreferOnCriterionA()}).</li>
 * </ul>
 *
 * @see HazardKind
 * @see DecisionEngine
 */
public final class Hazard {

    /** Which classification criterion a {@link HazardKind} belongs to. */
    public enum Criterion {
        /** Semantic co-mingling of mutable static state (SOW &sect;2(a)). */
        A,
        /** Lock / blocking coupling (SOW &sect;2(b)). */
        B
    }

    private final HazardKind kind;
    private final String     detail;

    /**
     * @param kind   the kind of hazard; must be non-null
     * @param detail a human-readable description of the specific evidence
     *               (e.g. the offending field or method); must be non-null
     */
    public Hazard(HazardKind kind, String detail) {
        if (kind == null)   throw new NullPointerException("kind");
        if (detail == null) throw new NullPointerException("detail");
        this.kind   = kind;
        this.detail = detail;
    }

    public HazardKind getKind() {
        return kind;
    }

    /** The classification criterion this hazard belongs to. */
    public Hazard.Criterion getCriterion() {
        return kind.getCriterion();
    }

    /** A human-readable description of the specific evidence. */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "(" + kind.getCriterion() + ") " + kind + ": " + detail;
    }
}
