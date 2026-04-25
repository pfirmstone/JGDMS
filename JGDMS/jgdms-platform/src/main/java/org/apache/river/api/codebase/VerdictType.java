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
package org.apache.river.api.codebase;

/**
 * The outcome of a codebase safety assessment performed by a
 * {@link BytecodeAnalysisEngine}.
 *
 * <p>A {@code VerdictType} is embedded in a {@link SignedVerdict} that the
 * analysis engine transmits to a {@link VerdictRegistry}.  The registry
 * aggregates verdicts from one or more engines according to a configurable
 * quorum policy and publishes an authoritative {@link RegistryVerdict} to
 * clients.
 *
 * @since 3.1.1
 * @author GitHub Copilot
 */
public enum VerdictType {

    /**
     * The codebase passed all checks performed by the analysis engine.
     * The registry requires verdicts from a quorum of independent engines
     * before issuing a {@code SAFE} {@link RegistryVerdict}.
     */
    SAFE,

    /**
     * The codebase failed one or more checks.  A single {@code DANGEROUS}
     * verdict from any engine is sufficient for the registry to immediately
     * publish a {@code DANGEROUS} {@link RegistryVerdict} (fail-safe
     * semantics).  A {@link CrashReport} submitted by a Phoenix crash
     * reporter is also treated as an implicit {@code DANGEROUS} verdict.
     */
    DANGEROUS,

    /**
     * The analysis engine could not reach a definitive conclusion for this
     * codebase (e.g. network error while downloading the JARs, or a class of
     * bytecode patterns that the engine does not yet handle).  An
     * {@code INCONCLUSIVE} verdict does not count toward the {@code SAFE}
     * quorum.
     */
    INCONCLUSIVE
}
