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

import java.net.URL;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Remote service interface for a Bytecode Analysis Engine (BAE).
 *
 * <p>A BAE is a <em>high-risk</em> service: it downloads and deep-parses
 * untrusted bytecode, and must therefore be treated as potentially
 * compromisable at any time.  To contain that risk it runs in a dedicated
 * Phoenix activation group with restrictive security permissions (no
 * write-back to external services, no access to the {@link VerdictRegistry}'s
 * signing keys).
 *
 * <p><strong>Isolation invariants:</strong>
 * <ul>
 *   <li>The BAE holds only its own analysis key-pair.  It never holds the
 *       registry's private key.</li>
 *   <li>The BAE's network permissions must not allow direct communication
 *       with clients; only outbound connections to codebase HTTP(S) servers
 *       (to download JARs) and an inbound/outbound connection to the
 *       {@link VerdictRegistry} are required.</li>
 *   <li>A Phoenix crash of the BAE's activation group is itself a security
 *       signal: Phoenix must submit a {@link CrashReport} to the
 *       {@link VerdictRegistry} for the codebase that was under analysis at
 *       the time of the crash.</li>
 * </ul>
 *
 * <p><strong>Multiplicity.</strong> Multiple independent BAE instances may be
 * registered with the same {@link VerdictRegistry}.  The registry's quorum
 * policy (e.g. requiring verdicts from ≥ K of N registered engines before
 * issuing a {@link VerdictType#SAFE} {@link RegistryVerdict}) defends against
 * a single compromised engine.
 *
 * <p><strong>Client interaction.</strong> Clients do <em>not</em> interact
 * with this interface.  Only the {@link VerdictRegistry} invokes
 * {@link #requestAnalysis}.  Clients only trust
 * {@link RegistryVerdict} objects signed by the registry.
 *
 * @see VerdictRegistry
 * @see SignedVerdict
 * @see CrashReport
 * @since 3.1.1
 */
public interface BytecodeAnalysisEngine extends Remote {

    /**
     * Requests that the engine download and analyse the JARs identified by
     * {@code codebaseUrls} and submit a {@link SignedVerdict} to the
     * registered {@link VerdictRegistry}.
     *
     * <p>This method returns as soon as the analysis request has been accepted
     * (i.e. it is asynchronous with respect to the verdict submission).  The
     * engine is responsible for submitting the resulting {@link SignedVerdict}
     * to the {@link VerdictRegistry} independently.
     *
     * <p>If the engine's JVM crashes during analysis, Phoenix must submit a
     * {@link CrashReport} for {@code codebaseUrls} directly to the
     * {@link VerdictRegistry} — bypassing this interface entirely.
     *
     * @param codebaseUrls the ordered set of codebase URLs to analyse;
     *        must be non-null and non-empty
     * @throws IllegalArgumentException if {@code codebaseUrls} is empty
     * @throws NullPointerException     if {@code codebaseUrls} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void requestAnalysis(Set<URL> codebaseUrls) throws RemoteException;
}
