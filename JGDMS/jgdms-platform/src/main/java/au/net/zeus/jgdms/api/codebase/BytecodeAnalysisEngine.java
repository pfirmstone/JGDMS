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
package au.net.zeus.jgdms.api.codebase;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;
import org.apache.river.api.net.Uri;

/**
 * Remote service interface for a Bytecode Analysis Engine (BAE).
 *
 * <p>A BAE is a <em>high-risk</em> service: it deep-parses untrusted bytecode,
 * and must therefore be treated as potentially compromisable at any time.  To
 * contain that risk it runs in a dedicated Phoenix activation group with
 * restrictive security permissions:
 * <ul>
 *   <li>No outbound network access (the JAR bytes are <em>pushed</em> to the
 *       engine via {@link #analyzeJar(AnalysisRequest)} — it never fetches
 *       URLs).</li>
 *   <li>No access to the {@link VerdictRegistry}'s signing keys.</li>
 *   <li>Inbound JERI connections only from the Codebase Downloader
 *       (Host 4) and the JFR Telemetry Service (Host 5).</li>
 * </ul>
 *
 * <p><strong>Isolation invariants:</strong>
 * <ul>
 *   <li>The BAE holds only its own analysis key-pair.  It never holds the
 *       registry's private key.</li>
 *   <li>The BAE never fetches JAR bytes from the network; the caller
 *       provides them via {@link AnalysisRequest#getJarBytes()}.</li>
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
 * with this interface.  Only the Codebase Downloader (Host 4) and the JFR
 * Telemetry Service (Host 5) call {@link #analyzeJar}.  Clients only trust
 * {@link RegistryVerdict} objects signed by the registry.
 *
 * @see VerdictRegistry
 * @see AnalysisRequest
 * @see JarAnalysisReport
 * @see SignedVerdict
 * @see CrashReport
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface BytecodeAnalysisEngine extends Remote {

    /**
     * Analyses the JAR described by {@code request} and returns a signed
     * {@link JarAnalysisReport}.
     *
     * <p>The engine:
     * <ol>
     *   <li>Parses all {@code .class} entries from
     *       {@link AnalysisRequest#getJarBytes()} using the ASM bytecode
     *       library (never calls {@link Class#forName} or the class loader).</li>
     *   <li>Runs {@code ClinitBlockingVisitor} over each class, performing a
     *       BFS call-graph traversal from {@code <clinit>} up to
     *       {@link AnalysisRequest#getMaxBfsDepth()} hops.</li>
     *   <li>Runs {@code AtomicSerialComplianceVisitor} over each class.</li>
     *   <li>Detects circular {@code <clinit>} dependency cycles across all
     *       classes in the JAR.</li>
     *   <li>Signs the resulting {@link JarAnalysisReport} with its private
     *       key.</li>
     * </ol>
     *
     * <p>Any class file that the ASM parser cannot parse is treated as
     * {@link ClinitVerdict#BLOCKING} + {@link AtomicSerialVerdict#MISSING_CONSTRUCTOR}
     * (fail-secure).
     *
     * <p>This method is synchronous: it blocks until the analysis is complete
     * and the signed report has been assembled.  It is the caller's
     * responsibility to submit the returned report to the
     * {@link VerdictRegistry} via
     * {@link VerdictRegistry#submitReport(String, JarAnalysisReport)}.
     *
     * @param request the analysis request containing JAR bytes, content hash,
     *                and configuration; must be non-null
     * @return the signed analysis report; never {@code null}
     * @throws AnalysisException   if a non-transient analysis failure occurs
     *                             (e.g. the bytes cannot be read as a JAR,
     *                             or the engine's key cannot sign the report)
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    JarAnalysisReport analyzeJar(AnalysisRequest request)
            throws AnalysisException, RemoteException;

    /**
     * Requests that the engine trigger re-analysis of the JARs identified by
     * the given content hashes.  This is an asynchronous fire-and-forget
     * method: it returns as soon as the request is queued.  The engine will
     * eventually call {@link #analyzeJar} for each hash (after obtaining
     * the JAR bytes via an appropriate source) and submit the result to the
     * {@link VerdictRegistry}.
     *
     * <p>This method is retained for compatibility with the JFR Telemetry
     * Service (Host 5), which requests re-analysis based on
     * {@code jdk.VirtualThreadPinned} events without supplying JAR bytes
     * directly.
     *
     * <p><em>Note:</em> New deployments should prefer
     * {@link #analyzeJar(AnalysisRequest)}, which gives the caller full
     * control over the JAR bytes and avoids outbound network access from the
     * BAE host.
     *
     * @param codebaseUrls the ordered set of RFC3986-normalised codebase URIs
     *        to re-analyse; must be non-null and non-empty
     * @throws IllegalArgumentException if {@code codebaseUrls} is empty
     * @throws NullPointerException     if {@code codebaseUrls} is {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException;
}
