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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;
import org.apache.river.api.net.Uri;

/**
 * An immutable, serializable record submitted by a client JVM to a
 * {@link JfrTelemetryService} when one or more {@code jdk.VirtualThreadPinned}
 * JFR events are observed, or submitted by the JFR Telemetry Service itself to
 * a {@link au.net.zeus.jgdms.api.codebase.VerdictRegistry} when a codebase
 * crosses the configured pinning threshold.
 *
 * <p>A {@code PinningReport} carries:
 * <ul>
 *   <li>the ordered set of codebase URLs whose classes appeared in the
 *       virtual-thread stack trace at pinning time,</li>
 *   <li>the total carrier-thread-pinned duration in nanoseconds for the
 *       observation window,</li>
 *   <li>the number of {@code jdk.VirtualThreadPinned} events observed,</li>
 *   <li>the wall-clock start and end (epoch-milliseconds) of the observation
 *       window.</li>
 * </ul>
 *
 * <p><strong>No cryptographic signature.</strong>  Both the
 * client-to-telemetry-service path and the telemetry-service-to-registry path
 * are secured by JERI mutual authentication (SPIFFE/SPIRE SVIDs).  Adding a
 * redundant application-level signature would give only marginal benefit while
 * complicating key management.
 *
 * <p><strong>Serialisation safety.</strong>  All fields are validated
 * atomically before the object is constructed using the {@link AtomicSerial}
 * protocol.  The class is {@code final}.
 *
 * @see JfrTelemetryService
 * @see au.net.zeus.jgdms.api.codebase.VerdictRegistry#reportPinning
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
@AtomicSerial
public final class PinningReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of individual {@code jdk.VirtualThreadPinned} events that
     * a single {@code PinningReport} may claim.  A client reporting more than
     * this limit is likely misbehaving; the report is rejected.
     */
    public static final long MAX_EVENT_COUNT = 1_000_000L;

    /**
     * Maximum number of codebase URLs accepted in a single report.
     * Mirrors the limit imposed by other wire types in the JGDMS platform.
     */
    public static final int MAX_CODEBASE_URLS = 256;

    // -------------------------------------------------------------------------
    // @AtomicSerial field-name constants
    // -------------------------------------------------------------------------

    private static final String CODEBASE_URLS   = "codebaseUrls";
    private static final String PINNED_NANOS    = "pinnedNanos";
    private static final String EVENT_COUNT     = "eventCount";
    private static final String PERIOD_START_MS = "periodStartMs";
    private static final String PERIOD_END_MS   = "periodEndMs";

    @SuppressWarnings("unused")
    private static final ObjectStreamField[] serialPersistentFields = serialForm();

    // -------------------------------------------------------------------------
    // @AtomicSerial protocol
    // -------------------------------------------------------------------------

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm(CODEBASE_URLS,   String[].class),
            new SerialForm(PINNED_NANOS,    Long.TYPE),
            new SerialForm(EVENT_COUNT,     Long.TYPE),
            new SerialForm(PERIOD_START_MS, Long.TYPE),
            new SerialForm(PERIOD_END_MS,   Long.TYPE)
        };
    }

    public static void serialize(PutArg arg, PinningReport r) throws IOException {
        arg.put(CODEBASE_URLS,   r.codebaseUrls.clone());
        arg.put(PINNED_NANOS,    r.pinnedNanos);
        arg.put(EVENT_COUNT,     r.eventCount);
        arg.put(PERIOD_START_MS, r.periodStartMs);
        arg.put(PERIOD_END_MS,   r.periodEndMs);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Invariant check — called before any field is assigned
    // -------------------------------------------------------------------------

    private static boolean check(GetArg arg)
            throws IOException, ClassNotFoundException {

        String[] urls = (String[]) arg.get(CODEBASE_URLS, null);
        if (urls == null || urls.length == 0)
            throw new InvalidObjectException(
                    "codebaseUrls must not be null or empty");
        if (urls.length > MAX_CODEBASE_URLS)
            throw new InvalidObjectException(
                    "codebaseUrls exceeds " + MAX_CODEBASE_URLS + " entries");
        Valid.nullElement(urls, "codebaseUrls must not contain null elements");
        for (int i = 0; i < urls.length; i++) {
            try {
                new Uri(urls[i]);
            } catch (URISyntaxException e) {
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] is not a valid RFC3986 URI: "
                                + e.getMessage());
            }
        }

        long pinnedNanos = arg.get(PINNED_NANOS, 0L);
        if (pinnedNanos < 0)
            throw new InvalidObjectException(
                    "pinnedNanos must be non-negative; got " + pinnedNanos);

        long eventCount = arg.get(EVENT_COUNT, 0L);
        if (eventCount < 0)
            throw new InvalidObjectException(
                    "eventCount must be non-negative; got " + eventCount);
        if (eventCount > MAX_EVENT_COUNT)
            throw new InvalidObjectException(
                    "eventCount exceeds " + MAX_EVENT_COUNT + "; got " + eventCount);

        long startMs = arg.get(PERIOD_START_MS, 0L);
        long endMs   = arg.get(PERIOD_END_MS,   0L);
        if (endMs < startMs)
            throw new InvalidObjectException(
                    "periodEndMs (" + endMs + ") must be >= periodStartMs ("
                            + startMs + ')');

        return true;
    }

    // -------------------------------------------------------------------------
    // Serialized fields
    // -------------------------------------------------------------------------

    /**
     * Codebase URLs as RFC3986 URI strings for safe serialization.
     *
     * @serial
     */
    private final String[] codebaseUrls;

    /**
     * Total carrier-thread-pinned duration in nanoseconds for the observation
     * window.
     *
     * @serial
     */
    private final long pinnedNanos;

    /**
     * Number of {@code jdk.VirtualThreadPinned} JFR events observed during the
     * window.
     *
     * @serial
     */
    private final long eventCount;

    /**
     * Wall-clock start of the observation window, in milliseconds since the
     * Unix epoch.
     *
     * @serial
     */
    private final long periodStartMs;

    /**
     * Wall-clock end of the observation window, in milliseconds since the Unix
     * epoch.  Must be {@code >= periodStartMs}.
     *
     * @serial
     */
    private final long periodEndMs;

    /**
     * Transient {@link Uri} cache populated at construction time.
     * Not serialized.
     */
    private final transient Uri[] codebaseUrlCache;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * {@link AtomicSerial} deserialization constructor.  Invariants are
     * checked by {@link #check(GetArg)} before any field is assigned.
     *
     * @param arg the deserialization argument bag
     * @throws IOException            if invariant checks fail
     * @throws ClassNotFoundException if a required class cannot be resolved
     */
    public PinningReport(GetArg arg)
            throws IOException, ClassNotFoundException {
        this(arg, check(arg));
    }

    private PinningReport(GetArg arg, boolean checked)
            throws IOException, ClassNotFoundException {
        this.codebaseUrls   = (String[]) arg.get(CODEBASE_URLS, null);
        this.pinnedNanos    = arg.get(PINNED_NANOS,    0L);
        this.eventCount     = arg.get(EVENT_COUNT,     0L);
        this.periodStartMs  = arg.get(PERIOD_START_MS, 0L);
        this.periodEndMs    = arg.get(PERIOD_END_MS,   0L);
        this.codebaseUrlCache = stringsToUris(this.codebaseUrls);
    }

    /**
     * Public constructor for building a {@code PinningReport} from live JFR
     * event data.
     *
     * @param codebaseUrls  ordered set of codebase URLs (non-null, non-empty,
     *                      at most {@value #MAX_CODEBASE_URLS} elements)
     * @param pinnedNanos   total carrier-thread-pinned duration in nanoseconds
     *                      (non-negative)
     * @param eventCount    number of {@code jdk.VirtualThreadPinned} events
     *                      (non-negative, at most {@value #MAX_EVENT_COUNT})
     * @param periodStartMs wall-clock start of the observation window
     *                      (epoch milliseconds)
     * @param periodEndMs   wall-clock end of the observation window
     *                      (epoch milliseconds, {@code >= periodStartMs})
     * @throws IllegalArgumentException if any argument fails a precondition
     * @throws NullPointerException     if {@code codebaseUrls} is {@code null}
     */
    public PinningReport(Uri[] codebaseUrls,
                         long  pinnedNanos,
                         long  eventCount,
                         long  periodStartMs,
                         long  periodEndMs) {
        if (codebaseUrls == null)
            throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.length == 0)
            throw new IllegalArgumentException("codebaseUrls must not be empty");
        if (codebaseUrls.length > MAX_CODEBASE_URLS)
            throw new IllegalArgumentException(
                    "codebaseUrls exceeds " + MAX_CODEBASE_URLS + " entries");
        for (int i = 0; i < codebaseUrls.length; i++) {
            if (codebaseUrls[i] == null)
                throw new NullPointerException("codebaseUrls[" + i + ']');
        }
        if (pinnedNanos < 0)
            throw new IllegalArgumentException(
                    "pinnedNanos must be non-negative; got " + pinnedNanos);
        if (eventCount < 0)
            throw new IllegalArgumentException(
                    "eventCount must be non-negative; got " + eventCount);
        if (eventCount > MAX_EVENT_COUNT)
            throw new IllegalArgumentException(
                    "eventCount exceeds " + MAX_EVENT_COUNT + "; got " + eventCount);
        if (periodEndMs < periodStartMs)
            throw new IllegalArgumentException(
                    "periodEndMs (" + periodEndMs + ") must be >= periodStartMs ("
                            + periodStartMs + ')');

        this.codebaseUrls    = urisToStrings(codebaseUrls);
        this.pinnedNanos     = pinnedNanos;
        this.eventCount      = eventCount;
        this.periodStartMs   = periodStartMs;
        this.periodEndMs     = periodEndMs;
        this.codebaseUrlCache = codebaseUrls.clone();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered set of codebase URLs whose classes appeared in the
     * virtual-thread stack trace at pinning time.
     *
     * @return an unmodifiable set; never {@code null} or empty
     */
    public Set<Uri> getCodebaseUrls() {
        LinkedHashSet<Uri> result = new LinkedHashSet<Uri>(
                Arrays.asList(codebaseUrlCache));
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the total carrier-thread-pinned duration for this report's
     * observation window, in nanoseconds.
     *
     * @return a non-negative long
     */
    public long getPinnedNanos() {
        return pinnedNanos;
    }

    /**
     * Returns the number of {@code jdk.VirtualThreadPinned} JFR events that
     * contributed to this report.
     *
     * @return a non-negative long at most {@value #MAX_EVENT_COUNT}
     */
    public long getEventCount() {
        return eventCount;
    }

    /**
     * Returns the wall-clock start of the observation window, in milliseconds
     * since the Unix epoch.
     *
     * @return epoch milliseconds
     */
    public long getPeriodStartMs() {
        return periodStartMs;
    }

    /**
     * Returns the wall-clock end of the observation window, in milliseconds
     * since the Unix epoch.
     *
     * @return epoch milliseconds, {@code >= getPeriodStartMs()}
     */
    public long getPeriodEndMs() {
        return periodEndMs;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "PinningReport{urls=" + Arrays.toString(codebaseUrls)
                + ", pinnedNanos=" + pinnedNanos
                + ", eventCount=" + eventCount
                + ", periodMs=[" + periodStartMs + ',' + periodEndMs + "]}";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String[] urisToStrings(Uri[] uris) {
        String[] result = new String[uris.length];
        for (int i = 0; i < uris.length; i++) {
            result[i] = uris[i].toString();
        }
        return result;
    }

    private static Uri[] stringsToUris(String[] urls) throws IOException {
        Uri[] result = new Uri[urls.length];
        for (int i = 0; i < urls.length; i++) {
            try {
                result[i] = new Uri(urls[i]);
            } catch (URISyntaxException e) {
                // Should not happen: check() already validated these strings.
                throw new InvalidObjectException(
                        "codebaseUrls[" + i + "] could not be parsed as URI: "
                                + e.getMessage());
            }
        }
        return result;
    }
}
