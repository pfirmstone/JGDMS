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
 * Remote service interface for the Codebase Downloader (Host 4).
 *
 * <p>The Codebase Downloader is the <em>only</em> host in the Safe-Codebase
 * Audit Pipeline (SCAP) with outbound internet access.  Its responsibilities
 * are:
 * <ul>
 *   <li>Proactively monitoring Jini lookup services for new service
 *       registrations and extracting their codebase URL annotations.</li>
 *   <li>Downloading JAR bytes from discovered or externally submitted
 *       codebase URLs via HTTPS.</li>
 *   <li>Computing the SHA-256 content hash of each downloaded JAR.</li>
 *   <li>Constructing an {@link AnalysisRequest} and pushing it to every
 *       registered {@link BytecodeAnalysisEngine} in the BAE pool.</li>
 *   <li>Forwarding the signed {@link JarAnalysisReport} returned by each
 *       engine to the {@link VerdictRegistry} via
 *       {@link VerdictRegistry#submitReport}.</li>
 * </ul>
 *
 * <p><strong>Isolation invariants:</strong>
 * <ul>
 *   <li>Clients <strong>never</strong> interact with this interface directly;
 *       it is consumed only by operators and by Host 5 (JFR Telemetry) for
 *       ad-hoc re-analysis requests.</li>
 *   <li>The Codebase Downloader has <strong>no</strong> connection to
 *       Host 5 (JFR Telemetry), and Host 5 has no connection back.  The
 *       two hosts communicate only via the shared {@link VerdictRegistry}.</li>
 *   <li>The JAR bytes are <em>never</em> executed or loaded by the
 *       Codebase Downloader; they are passed opaquely to the BAE pool as
 *       raw byte arrays inside an {@link AnalysisRequest}.</li>
 * </ul>
 *
 * @see BytecodeAnalysisEngine
 * @see AnalysisRequest
 * @see VerdictRegistry
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface CodebaseDownloader extends Remote {

    /**
     * Queues the given codebase URLs for download, analysis, and verdict
     * submission.
     *
     * <p>Each URI in {@code codebaseUrls} is processed asynchronously:
     * <ol>
     *   <li>The JAR at the URI is downloaded over HTTPS.</li>
     *   <li>A SHA-256 content hash is computed from the downloaded bytes.</li>
     *   <li>If no verdict has yet been submitted for that content hash,
     *       an {@link AnalysisRequest} is built and sent to every BAE in
     *       the configured pool.</li>
     *   <li>Each signed {@link JarAnalysisReport} returned by a BAE is
     *       forwarded to the {@link VerdictRegistry}.</li>
     * </ol>
     *
     * <p>URIs whose JAR has already been successfully submitted during the
     * current JVM session are silently skipped (deduplicated by content
     * hash).  URIs that were processed longer than the configured
     * recheck-interval ago are re-fetched to detect updated JARs.
     *
     * @param codebaseUrls the set of RFC3986-normalised codebase URIs to
     *        analyse; must be non-null and non-empty
     * @throws IllegalArgumentException if {@code codebaseUrls} is empty
     * @throws NullPointerException     if {@code codebaseUrls} is
     *                                  {@code null}
     * @throws RemoteException          if a communication failure occurs
     */
    void submitForAnalysis(Set<Uri> codebaseUrls) throws RemoteException;
}
