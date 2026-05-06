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
package au.net.zeus.jgdms.api.telemetry;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;
import org.apache.river.api.net.Uri;

/**
 * Remote service interface for the JFR Telemetry Service (Host 5 in the
 * five-host SCAP pipeline).
 *
 * <p>The JFR Telemetry Service is the <em>reactive</em> component in the safe
 * codebase architecture:
 * <ul>
 *   <li>It is a passive receiver — it never initiates connections to client
 *       JVMs.</li>
 *   <li>It aggregates {@code jdk.VirtualThreadPinned} JFR events reported by
 *       client JVMs over authenticated JERI connections.</li>
 *   <li>When the cumulative pinned-carrier duration or event count for a
 *       codebase URL set crosses a configurable threshold, it submits an
 *       aggregate {@link PinningReport} to the Verdict Registry
 *       ({@link au.net.zeus.jgdms.api.codebase.VerdictRegistry#reportPinning}),
 *       which treats it as a {@link au.net.zeus.jgdms.api.codebase.VerdictType#DANGEROUS}
 *       verdict.</li>
 * </ul>
 *
 * <h2>Architecture position</h2>
 * <pre>
 * Host 1 — Jini Lookup Service
 * Host 2 — BAE Pool         (stateless bytecode analysis)
 * Host 3 — Verdict Registry (authoritative verdicts, signing key)
 * Host 4 — Codebase Downloader (proactive; outbound internet)
 * Host 5 — JFR Telemetry Service (reactive; receives VirtualThreadPinned events)
 * </pre>
 *
 * <p>Host 4 ↔ Host 5: no direct connection.  A JFR event flood from
 * misbehaving clients cannot DoS the proactive JAR-download pipeline.
 *
 * <h2>Client usage</h2>
 * <pre>{@code
 * // Inside a JFR event stream handler:
 * RecordingStream rs = new RecordingStream();
 * rs.onEvent("jdk.VirtualThreadPinned", e -> {
 *     // Identify which codebase the pinning came from (via stack trace /
 *     // classloader metadata) then build a PinningReport:
 *     PinningReport report = new PinningReport(
 *             codebaseUris,
 *             e.getDuration().toNanos(),
 *             1L,
 *             System.currentTimeMillis(),
 *             System.currentTimeMillis());
 *     telemetryService.reportPinning(report);
 * });
 * rs.startAsync();
 * }</pre>
 *
 * <h2>Security</h2>
 * The service is provisioned with SPIFFE SVID
 * {@code spiffe://jgdms.example.org/host/telemetry}.  All callers must
 * authenticate with a trusted client SVID.  The service-side policy should
 * restrict {@link #reportPinning} to the {@code spiffe://…/client/<id>}
 * principal pattern.
 *
 * @see PinningReport
 * @see au.net.zeus.jgdms.api.codebase.VerdictRegistry
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public interface JfrTelemetryService extends Remote {

    /**
     * Records one or more {@code jdk.VirtualThreadPinned} JFR events from
     * a client JVM.
     *
     * <p>The service aggregates reports per codebase URL set.  When the
     * cumulative pinned-nanosecond total or event count for a codebase
     * crosses the service's configured threshold the service submits a
     * consolidated {@link PinningReport} to the Verdict Registry.
     *
     * <p>Clients are expected to batch reports locally (e.g. every 30 s or
     * whenever a local threshold is reached) rather than submitting
     * every individual JFR event.
     *
     * @param report an immutable, pre-validated record; must be non-null
     * @throws NullPointerException if {@code report} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    void reportPinning(PinningReport report) throws RemoteException;

    /**
     * Returns the current aggregate carrier-thread-pinned duration (in
     * nanoseconds) for the given codebase URL set since the last sweep.
     *
     * <p>This is a monitoring / administration endpoint.  Returning {@code 0}
     * means either no pinning has been observed or the aggregator has been
     * swept recently.
     *
     * @param codebaseUrls the codebase URL set to query; must be non-null
     * @return total pinned nanoseconds, or {@code 0} if unknown
     * @throws NullPointerException if {@code codebaseUrls} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    long getPinnedNanos(Set<Uri> codebaseUrls) throws RemoteException;

    /**
     * Returns the current aggregate {@code jdk.VirtualThreadPinned} event
     * count for the given codebase URL set since the last sweep.
     *
     * @param codebaseUrls the codebase URL set to query; must be non-null
     * @return total event count, or {@code 0} if unknown
     * @throws NullPointerException if {@code codebaseUrls} is {@code null}
     * @throws RemoteException      if a communication failure occurs
     */
    long getPinCount(Set<Uri> codebaseUrls) throws RemoteException;
}
