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
import java.util.List;
import java.util.Set;

/**
 * Applies the SOW &sect;2/&sect;3 classification logic to a class's
 * {@link ClassSignals}, producing a {@link ClassDecision}.
 *
 * <h2>Decision logic</h2>
 * <ol>
 *   <li>A class is <b>cross-boundary</b> (must share) if it is a wire value type
 *       ({@code Serializable} / {@code @AtomicSerial}) or a bootstrap-proxy
 *       interface ({@code ProxyAccessor}, {@code CodebaseAccessor},
 *       {@code DynamicProxyCodebaseAccessor}, or a class implementing one).</li>
 *   <li>Hazards are gathered: criterion (b) (locks / blocking coupling) and
 *       criterion (a) (semantic co-mingling of mutable static state).</li>
 *   <li>Then:
 *       <ul>
 *         <li>hazard present <b>and</b> cross-boundary &rarr; {@link
 *             Decision#CONFLICT} (must share, needs a lock-free remedy);</li>
 *         <li>criterion (b) hazard present &rarr; {@link Decision#PREFER};</li>
 *         <li>only criterion (a) &rarr; {@link Decision#PREFER} if {@link
 *             AnalyzerConfig#isPreferOnCriterionA()}, else {@link Decision#SHARE}
 *             and flagged for review;</li>
 *         <li>otherwise &rarr; {@link Decision#SHARE}.</li>
 *       </ul></li>
 * </ol>
 *
 * <p>Note that a {@code static final long serialVersionUID} is just a primitive
 * constant and never produces a hazard, so serializer/value classes that carry
 * only a {@code serialVersionUID} correctly classify as {@link Decision#SHARE}
 * (fixing the source-regex false positive the SOW calls out).
 */
public final class DecisionEngine {

    private final AnalyzerConfig config;

    public DecisionEngine(AnalyzerConfig config) {
        if (config == null) throw new NullPointerException("config");
        this.config = config;
    }

    /**
     * Classifies a single class.
     *
     * @param signals             the gathered bytecode facts; non-null
     * @param serializableClasses the set of internal names known (transitively)
     *                            to be {@code Serializable} across the analyzed
     *                            set; used for the cross-boundary determination
     * @param unresolvedSupertype {@code true} if the class has a superclass that
     *                            is outside the analyzed set, so its
     *                            cross-boundary ({@code Serializable}-via-base)
     *                            status cannot be confirmed; a would-be prefer is
     *                            then downgraded to share+review rather than risk
     *                            silently preferring a wire type
     * @return the decision; never null
     */
    public ClassDecision classify(ClassSignals signals, Set<String> serializableClasses,
                                  boolean unresolvedSupertype) {
        if (signals == null) throw new NullPointerException("signals");

        String name = signals.getInternalName();

        // ---- cross-boundary (must-share) determination ----------------------
        List<String> xReasons = new ArrayList<String>();
        if (signals.isAtomicSerial()) {
            xReasons.add("@AtomicSerial wire type");
        }
        if (serializableClasses != null && serializableClasses.contains(name)) {
            xReasons.add("implements java.io.Serializable (wire value type)");
        }
        if (config.isBootstrapInterface(name)) {
            xReasons.add("bootstrap-proxy interface (cast on both sides of bootstrap)");
        }
        for (String iface : signals.getInterfaces()) {
            if (config.isBootstrapInterface(iface)) {
                xReasons.add("implements bootstrap-proxy interface "
                        + iface.replace('/', '.'));
            }
        }
        boolean crossBoundary = !xReasons.isEmpty();

        // ---- hazard gathering ----------------------------------------------
        List<Hazard> hazards = gatherHazards(signals);

        boolean hasB = false, hasA = false, hasStrongB = false;
        for (Hazard h : hazards) {
            if (h.getCriterion() == Hazard.Criterion.B) {
                hasB = true;
                if (isStrongB(h.getKind())) hasStrongB = true;
            } else {
                hasA = true;
            }
        }

        // ---- decision ------------------------------------------------------
        // Criterion (b) signals are split into "strong" and "weak":
        //   strong = a contended/blocking resource is involved (a static field of
        //            a contended type, or a blocking call made under a static
        //            lock) — the SOW's decisive "especially" case;
        //   weak   = a bare static lock (static synchronized method, or
        //            synchronized block on a static field) with no contended
        //            resource or blocking call inside — typically a lazy-init
        //            guard around a read-mostly cache, which the &sect;8 audit
        //            classified as SHARE (e.g. Security, ClassLoading,
        //            ServerContext).
        //
        // A cross-boundary (must-share) type can never be preferred, so ANY (b)
        // lock coupling on it is the CONFLICT bucket (SOW &sect;2) — flagged for a
        // lock-free remedy.  Otherwise only a strong (b) auto-prefers; a weak (b)
        // alone, or a criterion (a) candidate, defaults to SHARE and is surfaced
        // for review (the override file is where a deliberate isolation decision
        // lives).
        Decision decision;
        boolean needsReview = false;
        String  reviewNote  = null;
        if (crossBoundary) {
            if (hasB) {
                decision = Decision.CONFLICT;   // must share, yet has lock coupling
                needsReview = true;
            } else {
                decision = Decision.SHARE;
            }
        } else if (hasStrongB) {
            if (unresolvedSupertype) {
                // A superclass lives outside the analyzed module, so we cannot
                // confirm the class is not Serializable-via-base (a wire type).
                // Preferring such a type would give the downloaded copy a distinct
                // <C,L> identity and risk a ClassCastException across the loader
                // divide, so do not silently prefer — surface for review instead.
                decision = Decision.SHARE;
                needsReview = true;
                reviewNote = "strong lock/blocking hazard would prefer, but a "
                        + "superclass is outside the analyzed module; its "
                        + "cross-boundary (Serializable) status is unverified, so "
                        + "defaulting to share. Override to prefer if it is not a "
                        + "wire type.";
            } else {
                decision = Decision.PREFER;
            }
        } else if (hasB) {
            decision = Decision.SHARE;          // weak lock only — surface for review
            needsReview = true;
        } else if (hasA) {
            if (config.isPreferOnCriterionA()) {
                decision = Decision.PREFER;
            } else {
                decision = Decision.SHARE;      // default to share, surface (a)
                needsReview = true;
            }
        } else {
            decision = Decision.SHARE;
        }

        ClassDecision cd =
                new ClassDecision(name, decision, hazards, crossBoundary, xReasons, needsReview);
        if (reviewNote != null) cd.setReviewNote(reviewNote);
        return cd;
    }

    /**
     * A "strong" criterion (b) hazard decisively involves a contended/blocking
     * resource and auto-prefers a non-cross-boundary class; a "weak" (b) hazard
     * is a bare static lock that is surfaced for review instead (SOW &sect;8:
     * lazy-cache lock guards classify as SHARE).
     */
    private static boolean isStrongB(HazardKind kind) {
        return kind == HazardKind.CONTENDED_STATIC_FIELD
                || kind == HazardKind.BLOCKING_CALL_UNDER_STATIC_LOCK;
    }

    private List<Hazard> gatherHazards(ClassSignals signals) {
        List<Hazard> hazards = new ArrayList<Hazard>();

        // --- criterion (b): lock / blocking coupling ---
        for (String m : signals.getStaticSynchronizedMethods()) {
            hazards.add(new Hazard(HazardKind.STATIC_SYNCHRONIZED_METHOD,
                    "static synchronized method " + m));
        }
        for (String f : signals.getSynchronizedOnStaticFields()) {
            hazards.add(new Hazard(HazardKind.SYNCHRONIZED_ON_STATIC_FIELD,
                    "synchronized block on static field " + f));
        }
        for (StaticFieldInfo f : signals.getStaticFields()) {
            String type = f.getInternalType();
            if (type == null) continue;
            boolean contended = config.isContendedFieldType(type);
            boolean executor  = config.isExecutorFieldType(type)
                    && !signals.getVirtualThreadExecutorFields().contains(f.getName());
            if (contended || executor) {
                hazards.add(new Hazard(HazardKind.CONTENDED_STATIC_FIELD,
                        "static field " + f.getName() + " of contended type "
                                + type.replace('/', '.')));
            }
        }
        for (String ev : signals.getBlockingUnderStaticLock()) {
            hazards.add(new Hazard(HazardKind.BLOCKING_CALL_UNDER_STATIC_LOCK, ev));
        }

        // --- criterion (a): semantic co-mingling of mutable static state ---
        for (StaticFieldInfo f : signals.getStaticFields()) {
            // Skip fields already reported as a contended (b) hazard to avoid
            // double-counting (e.g. a lazily-initialised SecureRandom).
            String t = f.getInternalType();
            boolean alreadyContended = config.isContendedFieldType(t)
                    || (config.isExecutorFieldType(t)
                        && !signals.getVirtualThreadExecutorFields().contains(f.getName()));
            if (alreadyContended) continue;
            if (isCriterionAField(f)) {
                String type = (f.getInternalType() == null)
                        ? f.getDescriptor()
                        : f.getInternalType().replace('/', '.');
                String why = f.isFinal()
                        ? "static final container " + f.getName()
                          + " mutated at runtime (registry/cache)"
                        : "mutable static field " + f.getName()
                          + " reassigned at runtime (mutable singleton)";
                hazards.add(new Hazard(HazardKind.MUTABLE_STATIC_STATE,
                        why + " [" + type + "]"));
            }
        }

        return hazards;
    }

    /**
     * Criterion (a): a static field carrying mutable state whose contents are
     * potentially per-deployment meaningful.  Benign read-mostly types
     * (loggers, reflected members, constants) are excluded; clinit-only
     * constant tables are excluded by requiring runtime mutation/reassignment.
     */
    private boolean isCriterionAField(StaticFieldInfo f) {
        String type = f.getInternalType();
        if (type != null && config.isBenignFieldType(type)) {
            return false;
        }
        if (f.isFinal()) {
            // an immutable reference: only a hazard if its contents are a
            // runtime-mutated container (a registry), not a clinit-only table.
            return config.isMutableContainerType(type) && f.isMutatedOutsideClinit();
        }
        // a reassignable (non-final) static reference/counter mutated at runtime.
        return f.isWrittenOutsideClinit();
    }
}
