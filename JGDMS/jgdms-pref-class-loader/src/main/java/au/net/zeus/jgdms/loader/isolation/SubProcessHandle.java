/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One isolated OS subprocess, pooled per distinct remote principal
 * (task&nbsp;T2, requirements&nbsp;#1 and&nbsp;#4).  Shared across all of that
 * principal's smart proxies; spawned on first need and torn down once nothing
 * holds a live reference to anything it hosts.
 *
 * <p><strong>Lifecycle keys off DGC reference-retirement semantics, never a
 * connection close.</strong> JGDMS's JERI DGC (see
 * {@code org.apache.river.jeri.internal.runtime.ObjectTable} /
 * {@code DgcRequestDispatcher} / {@code Target}) retires a per-client reference
 * to a hosted object via <em>either</em> a <em>processed clean call</em>
 * ({@code Target.unreferenced(clientID,...)}, the DGC acknowledgment contract:
 * the client's DGC has confirmed it dropped the reference) <em>or</em> a
 * <em>lease expiry</em> ({@code Target.leaseExpired(clientID)}, driven by the
 * {@code LeaseChecker} timer for a client that stopped renewing &mdash; e.g. a
 * crashed or disconnected client that never sent a clean call).  Both funnel
 * into {@code DgcRequestDispatcher.remove(target, true)} &rarr;
 * {@code unrefCallback.unreferenced()} once the dispatcher's whole id-table is
 * empty.  What they have in common &mdash; and what this handle keys off &mdash;
 * is that they are <em>explicit reference-retirement events</em>, not stream
 * FIN / connection drops.  This handle mirrors that contract:
 * <ul>
 *   <li>{@link #hostReference(Object)} records a live hosted object (a
 *       "dirty");</li>
 *   <li>{@link #referenceRetired(Object, RetirementReason)} is the
 *       reference-retirement signal &mdash; fired for a
 *       {@link RetirementReason#CLEAN_CALL_PROCESSED processed clean call}
 *       <em>or</em> a {@link RetirementReason#LEASE_EXPIRED expired lease}
 *       &mdash; and is the <em>only</em> path that can retire a reference and,
 *       when the last one is retired, tear the subprocess down.  It is
 *       deliberately <em>event-agnostic</em>: adding a new legitimate
 *       retirement trigger (T4 wiring the real dispatcher-level
 *       {@code unrefCallback.unreferenced()}, which already handles both
 *       triggers upstream) is a new {@link RetirementReason} enum constant, not
 *       an API break;</li>
 *   <li>{@link #connectionClosed()} is deliberately inert with respect to
 *       teardown: a dropped connection is <em>not</em> proof the client
 *       released its references, so treating it as such would tear down a
 *       subprocess still holding live references (a use-after-free of the
 *       isolated object). It only logs.</li>
 * </ul>
 *
 * <p><strong>Multiple clients / multiple references.</strong> A real hosted
 * object is referenced per {@code (Target, clientID)}; retirement is per
 * reference, and only the <em>last</em> retirement (which actually empties the
 * live-reference set) tears the subprocess down.  This model preserves that:
 * {@link #hostReference} may be called many times and
 * {@link #referenceRetired} removes one {@code hostedId} at a time &mdash; a
 * single retirement is <em>not</em> assumed to empty the set, so multiple
 * retirements against a still-multiply-referenced subprocess are expected and
 * only the final one triggers teardown.
 *
 * <p><strong>Handout/pin atomicity.</strong> Because the pool hands a handle to
 * a caller and the caller pins its real reference in a <em>separate</em>
 * {@link #hostReference} call, a concurrent last-reference retirement could
 * otherwise tear the handle down inside that window (a check-then-use TOCTOU:
 * the caller receives a handle that dies before it can pin).  {@link #reserve()}
 * closes that window: the pool pins a reservation atomically (under the same
 * lock that guards {@code tornDown}) at the instant it decides to hand the
 * handle out, and teardown is blocked while any reservation is outstanding.  The
 * caller's first {@link #hostReference} consumes one reservation, converting the
 * transient pin into a durable hosted reference.
 *
 * <p>In the live system these callbacks are driven by the JERI DGC dispatcher
 * over the subprocess transport (wiring is task&nbsp;T4); this class models the
 * semantics the pool depends on so the correctness property is testable now.
 *
 * @since 3.1.1
 */
public final class SubProcessHandle {

    private static final Logger logger =
            Logger.getLogger(SubProcessHandle.class.getName());

    /**
     * Why a hosted reference was retired.  Enumerated (rather than a single
     * hardcoded "clean call processed" trigger) so the retirement API is
     * event-agnostic: when task&nbsp;T4 wires this handle to the real
     * dispatcher-level {@code unrefCallback.unreferenced()} callback &mdash;
     * which already handles both a processed clean call and a lease expiry
     * upstream &mdash; the model accepts the lease-driven retirement without a
     * further API break.
     */
    public enum RetirementReason {
        /**
         * The DGC dispatcher processed a clean call for the reference
         * ({@code Target.unreferenced(clientID,...)}): the client's DGC has
         * acknowledged dropping the reference.
         */
        CLEAN_CALL_PROCESSED,
        /**
         * The reference's DGC lease expired ({@code Target.leaseExpired(
         * clientID)}, driven by the {@code LeaseChecker} timer): the client
         * stopped renewing &mdash; e.g. it crashed or its connection dropped and
         * it never sent a clean call.  Without this trigger such a client would
         * leak its subprocess forever.
         */
        LEASE_EXPIRED
    }

    private final IsolationPoolingKey key;
    private final SubProcessLauncher.Spawned spawned;
    /** Invoked exactly once, on teardown, to unregister/evict from the pool. */
    private final Runnable onTeardown;

    private final Object lock = new Object();
    private final Set<Object> liveReferences = new HashSet<Object>();
    private boolean tornDown = false;
    /** True once at least one reference has been hosted; guards premature 0. */
    private boolean everReferenced = false;
    /**
     * Outstanding handout reservations (see {@link #reserve()}).  Each pins the
     * handle across the window between the pool handing it out and the caller
     * pinning its real reference; teardown is blocked while this is &gt; 0.
     */
    private int pendingReservations = 0;

    SubProcessHandle(IsolationPoolingKey key,
                     SubProcessLauncher.Spawned spawned,
                     Runnable onTeardown) {
        this.key = key;
        this.spawned = spawned;
        this.onTeardown = onTeardown;
    }

    /** @return the canonical pooling key this subprocess is keyed on. */
    public IsolationPoolingKey key() {
        return key;
    }

    /** @return this subprocess's fail-closed administrative surface. */
    public SubProcessAdministrable adminSurface() {
        return spawned.adminSurface();
    }

    /**
     * Opens a fresh, dedicated wire-handoff channel to this subprocess
     * (task&nbsp;T4).  See {@link SubProcessLauncher.Spawned#openWireChannel()}
     * for the channel's scope and lifetime.  Deliberately does not reuse
     * {@link #adminSurface()}'s channel (S1).
     *
     * @return a freshly-opened, connected channel to the subprocess
     * @throws IOException if the channel cannot be opened, or if this
     *         subprocess has already been torn down
     */
    public ByteChannel openWireChannel() throws IOException {
        if (!isAlive()) {
            throw new IOException(
                "subprocess for " + key + " already torn down");
        }
        return spawned.openWireChannel();
    }

    /** @return {@code true} until the subprocess has been torn down. */
    public boolean isAlive() {
        synchronized (lock) {
            return !tornDown;
        }
    }

    /** @return the number of live hosted references (tests / diagnostics). */
    public int liveReferenceCount() {
        synchronized (lock) {
            return liveReferences.size();
        }
    }

    /**
     * Atomically pins a handout reservation on this handle, refusing if it has
     * already been torn down.  Called by the pool <em>at the instant it decides
     * to hand this handle to a caller</em>, under the same lock that guards
     * {@code tornDown}, so a concurrent last-reference retirement cannot tear
     * the handle down in the window before the caller pins its real reference
     * (the check-then-use TOCTOU the pool would otherwise expose).  The
     * reservation is released by the caller's first {@link #hostReference}; a
     * torn-down handle refuses, signalling the pool to respawn.
     *
     * @return {@code true} if the reservation was taken (handle live);
     *         {@code false} if the handle is already torn down (respawn needed)
     */
    boolean reserve() {
        synchronized (lock) {
            if (tornDown) {
                return false;
            }
            pendingReservations++;
            return true;
        }
    }

    /**
     * Records a live hosted-object reference (DGC "dirty").  Called when a
     * proxy of this principal is handed to a consumer.  Consumes one
     * outstanding {@link #reserve() reservation} if present, converting the
     * transient handout pin into a durable hosted reference.
     *
     * @param hostedId opaque identity of the hosted object reference
     */
    public void hostReference(Object hostedId) {
        synchronized (lock) {
            if (tornDown) {
                throw new IllegalStateException(
                    "subprocess for " + key + " already torn down");
            }
            everReferenced = true;
            liveReferences.add(hostedId);
            if (pendingReservations > 0) {
                pendingReservations--;
            }
        }
    }

    /**
     * Retires one hosted reference.  This is the only signal that can retire a
     * reference and, when the last live reference is retired, tear the
     * subprocess down &mdash; regardless of <em>why</em> the reference was
     * retired ({@link RetirementReason#CLEAN_CALL_PROCESSED a processed clean
     * call} or {@link RetirementReason#LEASE_EXPIRED an expired lease}); the
     * reason is informational (logging/audit) and does not change the teardown
     * decision.
     *
     * <p>Only the retirement that actually empties the live-reference set tears
     * down: a still-multiply-referenced subprocess survives its non-final
     * retirements.  Teardown is additionally suppressed while any handout
     * {@link #reserve() reservation} is outstanding, so a reference retired out
     * from under an in-flight handout does not destroy a subprocess a caller is
     * about to pin.
     *
     * @param hostedId the reference being retired
     * @param reason   why the reference was retired; must not be {@code null}
     * @return {@code true} if this call triggered teardown
     */
    public boolean referenceRetired(Object hostedId, RetirementReason reason) {
        if (reason == null) throw new NullPointerException("reason");
        Runnable teardownAction = null;
        synchronized (lock) {
            if (tornDown) return false;
            liveReferences.remove(hostedId);
            if (everReferenced && liveReferences.isEmpty()
                    && pendingReservations == 0) {
                tornDown = true;
                teardownAction = onTeardown;
            }
        }
        if (teardownAction != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "Tearing down isolated subprocess for {0}: last live"
                    + " reference retired ({1}).",
                    new Object[] { key, reason });
            }
            try {
                teardownAction.run();
            } finally {
                spawned.shutdown();
            }
            return true;
        }
        return false;
    }

    /**
     * A transport connection to this subprocess closed.  <strong>Intentionally
     * does not tear down.</strong> A connection close is not a DGC
     * acknowledgment and is not proof the client released its references;
     * tearing down here would risk destroying a subprocess still holding live
     * references.  Teardown happens only via
     * {@link #referenceRetired(Object, RetirementReason)}.
     */
    public void connectionClosed() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                "Connection to isolated subprocess {0} closed; NOT tearing"
                + " down (teardown requires a processed DGC clean call, not a"
                + " connection close). Live references: {1}",
                new Object[] { key, Integer.valueOf(liveReferenceCount()) });
        }
    }
}
