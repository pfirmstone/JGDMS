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
 * The kinds of shared-static-state isolation hazard the analyzer detects from
 * bytecode, grouped by classification criterion (SOW &sect;2 / &sect;4).
 *
 * @see Hazard
 */
public enum HazardKind {

    // ---- Criterion (a): semantic co-mingling of mutable static state -------

    /**
     * A mutable static field that is written outside {@code <clinit>} &mdash;
     * a non-{@code final} static field of a mutable type, or a {@code static
     * final} field of a mutable-container type (a registry, counter or mutable
     * singleton) that accumulates state at runtime rather than being a
     * clinit-only constant lookup table.
     */
    MUTABLE_STATIC_STATE(Hazard.Criterion.A),

    // ---- Criterion (b): lock / blocking coupling ---------------------------

    /** A {@code static synchronized} method (locks on the class monitor). */
    STATIC_SYNCHRONIZED_METHOD(Hazard.Criterion.B),

    /** A {@code synchronized(...)} block whose monitor is a static field. */
    SYNCHRONIZED_ON_STATIC_FIELD(Hazard.Criterion.B),

    /**
     * A static field whose type is a contended / blocking resource
     * ({@code SecureRandom}, a {@code Lock}, {@code Semaphore}, bounded
     * {@code Executor}, {@code BlockingQueue}, {@code CountDownLatch},
     * {@code Phaser}, {@code Condition}, &hellip;).
     */
    CONTENDED_STATIC_FIELD(Hazard.Criterion.B),

    /**
     * A known blocking call made on a code path that holds a static monitor
     * (inside a {@code static synchronized} method or a {@code synchronized}
     * block on a static field) &mdash; the strongest lock-coupling signal.
     */
    BLOCKING_CALL_UNDER_STATIC_LOCK(Hazard.Criterion.B);

    private final Hazard.Criterion criterion;

    HazardKind(Hazard.Criterion criterion) {
        this.criterion = criterion;
    }

    public Hazard.Criterion getCriterion() {
        return criterion;
    }
}
