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
 * The preferred-class classification decision for a single class.
 *
 * <p>Maps to the {@code Preferred:} value emitted in
 * {@code META-INF/PREFERRED.LIST}: {@link #PREFER} &rarr; {@code true},
 * {@link #SHARE} and {@link #CONFLICT} &rarr; {@code false}.
 *
 * @see DecisionEngine
 */
public enum Decision {

    /**
     * Isolate: the class carries a real shared-static-state isolation hazard
     * (criterion (b), or an accepted criterion (a)) and is <em>not</em> a
     * cross-boundary type, so a downloaded codebase should get its own copy.
     * Emitted as {@code Preferred: true}.
     */
    PREFER(true),

    /**
     * Share (the default): the class has no isolation hazard, or is a
     * cross-boundary value/bootstrap type that must keep a single
     * {@code <C,L>} identity across the loader divide.  Emitted as
     * {@code Preferred: false}.
     */
    SHARE(false),

    /**
     * Conflict: the class is cross-boundary (<em>must</em> share) yet also
     * carries a contention hazard.  Preferring it would break type identity,
     * so it is emitted as {@code Preferred: false} and reported as needing a
     * lock-free remedy (e.g. {@code ThreadLocal}) as a separate follow-up.
     * Emitted as {@code Preferred: false}.
     */
    CONFLICT(false);

    private final boolean preferred;

    Decision(boolean preferred) {
        this.preferred = preferred;
    }

    /** Returns the {@code Preferred:} boolean this decision emits. */
    public boolean isPreferred() {
        return preferred;
    }
}
