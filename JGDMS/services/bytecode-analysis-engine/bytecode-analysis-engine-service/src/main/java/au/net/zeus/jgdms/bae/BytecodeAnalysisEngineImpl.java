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
package au.net.zeus.jgdms.bae;

import java.security.PrivateKey;
import java.util.concurrent.atomic.AtomicBoolean;
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;

/**
 * Server-side implementation of the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class runs in a dedicated Phoenix activation group with restrictive
 * security permissions.  Per JGDMS-STD-002 the BAE (Host 2) is an
 * <strong>untrusted</strong> component and must satisfy two isolation
 * invariants:
 * <ul>
 *   <li><strong>No outbound network access.</strong> The engine never fetches
 *       JAR bytes from a URL; the JAR bytes are <em>pushed</em> to it.</li>
 *   <li><strong>No direct connection to the Verdict Registry (Host 3).</strong>
 *       The engine never calls {@link VerdictRegistry} methods; it merely
 *       returns a signed {@link JarAnalysisReport} to its caller, who is
 *       responsible for submitting it to the registry via
 *       {@link VerdictRegistry#submitReport(String, JarAnalysisReport)}.  This
 *       implementation holds <em>no</em> registry reference, structurally
 *       enforcing the absence of a Host&nbsp;2&nbsp;-&gt;&nbsp;Host&nbsp;3
 *       path.</li>
 * </ul>
 *
 * <h2>Supported analysis model — push</h2>
 * {@link #analyzeJar(AnalysisRequest)} is the only supported analysis entry
 * point.  The caller (the Codebase Downloader, Host 4, or the JFR Telemetry
 * Service, Host 5) supplies the JAR bytes directly via
 * {@link AnalysisRequest#getJarBytes()}; the engine deep-parses them with the
 * ASM-based {@link JarAnalyzer}, signs the resulting {@link JarAnalysisReport}
 * with its private key, and returns it.  No outbound connection is made.
 *
 * @see BytecodeAnalysisEngine
 * @see JarAnalyzer
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class BytecodeAnalysisEngineImpl implements BytecodeAnalysisEngine {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Engine's private key, used to sign {@link JarAnalysisReport} objects. */
    private final PrivateKey enginePrivateKey;

    /** JCA standard name of the signature algorithm (e.g. {@code "SHA256withRSA"}). */
    private final String sigAlgorithm;

    /** ASM-based JAR analyzer that implements the push-model analysis. */
    private final JarAnalyzer jarAnalyzer;

    /**
     * Fast flag checked by {@link #analyzeJar} to reject new requests once
     * shutdown has been requested.
     */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code BytecodeAnalysisEngineImpl}.
     *
     * @param enginePrivateKey the engine's private key for signing reports;
     *                         must be non-null
     * @param sigAlgorithm     JCA standard name of the signature algorithm
     *                         (e.g. {@code "SHA256withRSA"}); must be non-null
     *                         and non-empty
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code sigAlgorithm} is empty
     */
    public BytecodeAnalysisEngineImpl(PrivateKey enginePrivateKey,
                                      String sigAlgorithm) {
        if (enginePrivateKey == null)  throw new NullPointerException("enginePrivateKey");
        if (sigAlgorithm == null)      throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty())    throw new IllegalArgumentException("sigAlgorithm must not be empty");

        this.enginePrivateKey  = enginePrivateKey;
        this.sigAlgorithm      = sigAlgorithm;
        this.jarAnalyzer       = new JarAnalyzer(enginePrivateKey, sigAlgorithm);
    }

    // -------------------------------------------------------------------------
    // BytecodeAnalysisEngine — push model (the only supported path)
    // -------------------------------------------------------------------------

    /**
     * Analyses the JAR described by {@code request} synchronously using the
     * ASM-based {@link JarAnalyzer} and returns a signed
     * {@link JarAnalysisReport}.
     *
     * <p>The caller (Codebase Downloader, Host 4, or JFR Telemetry Service,
     * Host 5) supplies the JAR bytes directly; the engine never makes outbound
     * network connections and never contacts the Verdict Registry.  It is the
     * caller's responsibility to submit the returned report to the registry via
     * {@link VerdictRegistry#submitReport(String, JarAnalysisReport)}.
     *
     * @param request the analysis request; must be non-null
     * @return the signed report; never {@code null}
     * @throws AnalysisException    if a non-transient failure occurs
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalStateException if the engine is shutting down
     */
    @Override
    public JarAnalysisReport analyzeJar(AnalysisRequest request)
            throws AnalysisException {
        if (request == null) throw new NullPointerException("request");
        if (shutdownRequested.get()) {
            throw new IllegalStateException(
                    "BytecodeAnalysisEngine is shutting down; "
                    + "new analysis requests are not accepted");
        }
        return jarAnalyzer.analyze(request);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initiates shutdown of this engine.  After this call,
     * {@link #analyzeJar(AnalysisRequest)} rejects new requests with
     * {@link IllegalStateException}.
     *
     * <p>{@link #analyzeJar} is synchronous and the engine owns no background
     * threads, so shutdown is immediate and idempotent.
     */
    public void shutdown() {
        shutdownRequested.set(true);
    }

    /**
     * Returns {@code true} if shutdown has been initiated on this engine.
     *
     * @return {@code true} if shutdown has been requested
     */
    public boolean isShutdown() {
        return shutdownRequested.get();
    }
}
