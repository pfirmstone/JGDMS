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

import java.util.List;

/**
 * Renders the human-readable analysis report (SOW &sect;6): the preferred set
 * with its evidence, the CONFLICT list (must-share-yet-contends &rarr; lock-free
 * TODO), the overrides applied and any divergences, and the needs-review set.
 * This is the artifact a security reviewer reads, so the <em>why</em> is made
 * legible.
 */
public final class AnalysisReport {

    private AnalysisReport() { }

    /**
     * Renders the report for a set of decisions and an (optional) override
     * application result.
     *
     * @param decisions      the analyzer decisions (overrides already applied)
     * @param overrideResult the override application result, or {@code null}
     * @return the report text
     */
    public static String render(List<ClassDecision> decisions,
                                OverrideFile.Result overrideResult) {
        StringBuilder sb = new StringBuilder();

        int prefer = 0, share = 0, conflict = 0, review = 0, overridden = 0;
        for (ClassDecision d : decisions) {
            switch (d.getAnalysisDecision()) {
                case PREFER:   prefer++;   break;
                case CONFLICT: conflict++; break;
                default:       share++;    break;
            }
            if (d.isNeedsReview()) review++;
            if (d.isOverridden())  overridden++;
        }

        sb.append("Preferred-Class Analysis Report\n");
        sb.append("===============================\n\n");
        sb.append("Analyzed classes : ").append(decisions.size()).append('\n');
        sb.append("  PREFER (true)  : ").append(prefer).append('\n');
        sb.append("  SHARE  (false) : ").append(share).append('\n');
        sb.append("  CONFLICT       : ").append(conflict)
          .append("  (must share, yet contends -- lock-free remedy needed)\n");
        sb.append("  needs review   : ").append(review).append('\n');
        sb.append("  overrides      : ").append(overridden).append('\n');

        // ---- PREFER -------------------------------------------------------
        sb.append("\n-- PREFER (isolate; Preferred: true) ----------------------------------\n");
        boolean any = false;
        for (ClassDecision d : decisions) {
            if (d.getAnalysisDecision() != Decision.PREFER) continue;
            any = true;
            sb.append('\n').append(d.getInternalName().replace('/', '.')).append('\n');
            for (Hazard h : d.getHazards()) {
                sb.append("    ").append(h).append('\n');
            }
        }
        if (!any) sb.append("  (none)\n");

        // ---- CONFLICT -----------------------------------------------------
        sb.append("\n-- CONFLICT (must share; needs lock-free remedy; do NOT prefer) ------\n");
        any = false;
        for (ClassDecision d : decisions) {
            if (d.getAnalysisDecision() != Decision.CONFLICT) continue;
            any = true;
            sb.append('\n').append(d.getInternalName().replace('/', '.')).append('\n');
            sb.append("    cross-boundary: ").append(d.getCrossBoundaryReasons()).append('\n');
            for (Hazard h : d.getHazards()) {
                if (h.getCriterion() == Hazard.Criterion.B) {
                    sb.append("    ").append(h).append('\n');
                }
            }
        }
        if (!any) sb.append("  (none)\n");

        // ---- needs review -------------------------------------------------
        sb.append("\n-- NEEDS REVIEW (defaulted to SHARE; override to prefer if deliberate) -\n");
        any = false;
        for (ClassDecision d : decisions) {
            if (!d.isNeedsReview() || d.getAnalysisDecision() != Decision.SHARE) continue;
            any = true;
            sb.append('\n').append(d.getInternalName().replace('/', '.')).append('\n');
            for (Hazard h : d.getHazards()) {
                sb.append("    ").append(h).append('\n');
            }
        }
        if (!any) sb.append("  (none)\n");

        // ---- overrides ----------------------------------------------------
        if (overrideResult != null) {
            sb.append("\n-- OVERRIDES APPLIED --------------------------------------------------\n");
            if (overrideResult.getApplied().isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (ClassDecision d : overrideResult.getApplied()) {
                    sb.append("  ").append(d.getInternalName().replace('/', '.'))
                      .append(" = ").append(d.isPreferred());
                    if (d.getOverrideReason() != null) {
                        sb.append("   # ").append(d.getOverrideReason());
                    }
                    sb.append('\n');
                }
            }
            if (!overrideResult.getDivergent().isEmpty()) {
                sb.append("\n  divergent from analysis:\n");
                for (ClassDecision d : overrideResult.getDivergent()) {
                    sb.append("    ").append(d.getInternalName().replace('/', '.'))
                      .append(": analysis=").append(d.getAnalysisDecision().isPreferred())
                      .append(" override=").append(d.isPreferred()).append('\n');
                }
            }
            if (!overrideResult.getUnmatched().isEmpty()) {
                sb.append("\n  WARNING -- override entries matching no analyzed class (stale?):\n");
                for (String s : overrideResult.getUnmatched()) {
                    sb.append("    ").append(s).append('\n');
                }
            }
        }

        return sb.toString();
    }
}
