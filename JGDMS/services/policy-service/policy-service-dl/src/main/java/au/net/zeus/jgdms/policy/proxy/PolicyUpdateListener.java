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
package au.net.zeus.jgdms.policy.proxy;

import java.io.IOException;
import java.io.StringReader;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lease.LeaseListener;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.RemotePolicyProvider;
import org.apache.river.api.security.RemotePolicyService;

/**
 * Client-side {@link RemoteEventListener} that subscribes to policy-grant
 * change notifications from a {@link RemotePolicyService}, applies them
 * locally to a {@link RemotePolicyProvider}, and manages its lease via a
 * {@link LeaseRenewalManager}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct with a {@link RemotePolicyService} proxy and a
 *       {@link RemotePolicyProvider} instance.</li>
 *   <li>Call {@link #start()} to export the listener, subscribe for
 *       policy-update events, and perform an initial grant pull.</li>
 *   <li>The listener receives {@link PolicyUpdateEvent} callbacks from the
 *       server and keeps the local policy provider up-to-date.</li>
 *   <li>Call {@link #stop()} to cancel the lease and unexport the listener.</li>
 * </ol>
 *
 * <h2>Event model (pull-on-notification)</h2>
 * {@link PolicyUpdateEvent} is notification-only — it carries no grant
 * payload, just a sequence number.  On receipt the listener calls
 * {@link RemotePolicyService#getCurrentGrants()} to pull the authoritative
 * {@code String[]} snapshot, parses it via {@link DefaultPolicyParser}, and
 * feeds the resulting {@link PermissionGrant}[] into
 * {@link RemotePolicyProvider#replace(PermissionGrant[])}.
 *
 * <h2>Sequence-gap detection</h2>
 * The sequence number supplied in each {@link PolicyUpdateEvent} is
 * monotonically increasing per registration.  A gap (i.e. received seqNum is
 * greater than {@code lastSeqNum + 1}) means one or more {@code replace()}
 * calls were missed.  The correct response is still a full
 * {@link RemotePolicyService#getCurrentGrants()} re-pull (pull model means no
 * state reconstruction is needed), but the gap is logged at
 * {@link Level#WARNING}.
 *
 * <h2>Lease management</h2>
 * A {@link LeaseRenewalManager} is used to automatically renew the lease
 * returned in the {@link EventRegistration}.  If renewal fails with an
 * {@link UnknownLeaseException} (e.g. after a server restart), the listener
 * re-subscribes from scratch via {@link #resubscribe()}.
 *
 * <h2>Export</h2>
 * The listener is exported as a JERI remote object via
 * {@link BasicJeriExporter} with {@link TcpServerEndpoint#getInstance(int)
 * TcpServerEndpoint.getInstance(0)} (OS-assigned port) so that the server can
 * call {@link #notify(RemoteEvent)} back across the wire.
 *
 * <h2>Thread safety</h2>
 * {@link #notify(RemoteEvent)} may be called from multiple JERI dispatch
 * threads concurrently.  {@link #syncGrants()} is effectively idempotent —
 * last writer wins — which is correct because
 * {@link RemotePolicyService#getCurrentGrants()} always returns a consistent
 * snapshot.
 *
 * @author GitHub Copilot
 * @since 3.1.1
 * @see RemotePolicyService
 * @see RemotePolicyProvider
 * @see PolicyUpdateEvent
 */
public class PolicyUpdateListener implements RemoteEventListener, LeaseListener {

    private static final Logger logger =
            Logger.getLogger(PolicyUpdateListener.class.getName());

    /** Default backoff time in milliseconds between re-subscribe attempts. */
    private static final long INITIAL_BACKOFF_MS = 1_000L;

    /** Maximum backoff time in milliseconds during re-subscribe. */
    private static final long MAX_BACKOFF_MS = 60_000L;

    private final RemotePolicyService service;
    private final RemotePolicyProvider localPolicy;
    private final LeaseRenewalManager leaseManager;
    private final Exporter exporter;
    private final long leaseDuration;

    /** Last received sequence number per registration; -1 means none received. */
    private final AtomicLong lastSeqNum = new AtomicLong(-1L);

    private volatile EventRegistration registration;
    private volatile Remote stub;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code PolicyUpdateListener} with a default
     * {@link BasicJeriExporter} (TCP, OS-assigned port) and lease duration of
     * {@link Lease#FOREVER}.
     *
     * @param service     the remote policy service proxy; must not be
     *                    {@code null}
     * @param localPolicy the local policy provider to update; must not be
     *                    {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public PolicyUpdateListener(RemotePolicyService service,
                                RemotePolicyProvider localPolicy) {
        this(service, localPolicy,
                new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
                        new BasicILFactory(), false, true),
                Lease.FOREVER);
    }

    /**
     * Creates a {@code PolicyUpdateListener} with a custom exporter and lease
     * duration.
     *
     * @param service       the remote policy service proxy; must not be
     *                      {@code null}
     * @param localPolicy   the local policy provider to update; must not be
     *                      {@code null}
     * @param exporter      the exporter to use when making this listener
     *                      callable from the server; must not be {@code null}
     * @param leaseDuration requested lease duration in milliseconds;
     *                      {@link Lease#FOREVER} is allowed (the server caps
     *                      to its maximum)
     * @throws NullPointerException if {@code service}, {@code localPolicy}, or
     *                              {@code exporter} is {@code null}
     */
    public PolicyUpdateListener(RemotePolicyService service,
                                RemotePolicyProvider localPolicy,
                                Exporter exporter,
                                long leaseDuration) {
        if (service == null) throw new NullPointerException("service");
        if (localPolicy == null) throw new NullPointerException("localPolicy");
        if (exporter == null) throw new NullPointerException("exporter");
        this.service = service;
        this.localPolicy = localPolicy;
        this.leaseManager = new LeaseRenewalManager();
        this.exporter = exporter;
        this.leaseDuration = leaseDuration;
    }

    /**
     * Package-private constructor for unit tests that supply a
     * {@link LeaseRenewalManager} directly.
     */
    PolicyUpdateListener(RemotePolicyService service,
                         RemotePolicyProvider localPolicy,
                         Exporter exporter,
                         long leaseDuration,
                         LeaseRenewalManager leaseManager) {
        if (service == null) throw new NullPointerException("service");
        if (localPolicy == null) throw new NullPointerException("localPolicy");
        if (exporter == null) throw new NullPointerException("exporter");
        if (leaseManager == null) throw new NullPointerException("leaseManager");
        this.service = service;
        this.localPolicy = localPolicy;
        this.leaseManager = leaseManager;
        this.exporter = exporter;
        this.leaseDuration = leaseDuration;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Exports this listener, subscribes for policy-update events, and performs
     * an initial grant pull.
     *
     * <p>This method must be called before any events can be received.  Calling
     * {@code start()} more than once without an intervening {@link #stop()} is
     * a no-op.
     *
     * @throws IOException if exporting this listener or registering with the
     *                     remote service fails
     */
    public synchronized void start() throws IOException {
        if (stub != null) {
            logger.fine("PolicyUpdateListener.start() called but already started; ignoring.");
            return;
        }
        stub = exporter.export(this);
        logger.fine("PolicyUpdateListener exported; registering with RemotePolicyService.");

        registration = service.registerForPolicyUpdates(
                (RemoteEventListener) stub, null, leaseDuration);

        leaseManager.renewFor(registration.getLease(), leaseDuration, this);

        logger.fine("PolicyUpdateListener registered; performing initial grant pull.");
        syncGrants();
    }

    /**
     * Cancels the lease and unexports this listener.
     *
     * <p>After {@code stop()} returns, no further {@link #notify} callbacks
     * will be invoked.  Calling {@code stop()} when not started is a no-op.
     */
    public synchronized void stop() {
        if (stub == null) return;

        // Cancel the lease.
        if (registration != null) {
            try {
                leaseManager.cancel(registration.getLease());
            } catch (UnknownLeaseException | RemoteException e) {
                logger.log(Level.FINE, "Exception cancelling policy event lease on stop; ignoring.", e);
            }
            registration = null;
        }

        // Unexport this listener.
        try {
            exporter.unexport(true);
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception unexporting PolicyUpdateListener; ignoring.", e);
        }
        stub = null;
        logger.fine("PolicyUpdateListener stopped.");
    }

    // -------------------------------------------------------------------------
    // RemoteEventListener
    // -------------------------------------------------------------------------

    /**
     * Called by the server when the policy grants have been replaced.
     *
     * <p>This method:
     * <ol>
     *   <li>Casts the event to {@link PolicyUpdateEvent}.</li>
     *   <li>Checks the sequence number against the last seen value; logs a
     *       {@link Level#WARNING} if a gap is detected.</li>
     *   <li>Calls {@link #syncGrants()} to pull and apply the updated
     *       grants.</li>
     * </ol>
     *
     * @param event the remote event; expected to be a {@link PolicyUpdateEvent}
     * @throws RemoteException never thrown; declared for interface compatibility
     */
    @Override
    public void notify(RemoteEvent event) throws RemoteException {
        if (!(event instanceof PolicyUpdateEvent)) {
            logger.warning("PolicyUpdateListener.notify() received unexpected event type: "
                    + event.getClass().getName() + "; ignoring.");
            return;
        }

        long seqNum = event.getSequenceNumber();
        long prev = lastSeqNum.getAndSet(seqNum);

        if (prev >= 0 && seqNum > prev + 1) {
            logger.warning("PolicyUpdateListener: sequence gap detected — expected "
                    + (prev + 1) + " but received " + seqNum
                    + "; missed " + (seqNum - prev - 1) + " update(s). Performing full re-pull.");
        } else {
            logger.fine("PolicyUpdateListener: received PolicyUpdateEvent seqNum=" + seqNum);
        }

        syncGrants();
    }

    // -------------------------------------------------------------------------
    // LeaseListener (LeaseRenewalManager callback)
    // -------------------------------------------------------------------------

    /**
     * Called by the {@link LeaseRenewalManager} when it cannot renew the lease.
     *
     * <p>If the cause is an {@link UnknownLeaseException} (e.g. after a server
     * restart), this method triggers a re-subscription attempt.  All other
     * failures are logged at {@link Level#SEVERE} but do not trigger
     * re-subscription.
     *
     * @param e the lease renewal failure event
     */
    @Override
    public void notify(LeaseRenewalEvent e) {
        Throwable cause = e.getException();
        if (cause instanceof UnknownLeaseException) {
            logger.warning("Policy event lease lost (UnknownLeaseException); attempting re-subscribe.");
            // IMPORTANT: resubscribe() contains an infinite sleep/retry loop.
            // It must NOT run on the LeaseRenewalManager's internal thread,
            // or it will block all other lease renewals managed by that LRM.
            Thread t = new Thread(this::resubscribe, "policy-resubscribe");
            t.setDaemon(true);
            t.start();
        } else {
            logger.log(Level.SEVERE,
                    "PolicyUpdateListener: lease renewal failed with non-recoverable exception; "
                            + "policy updates will not be received until the application is restarted.", cause);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Pulls the current grant set from the server, parses it, and installs it
     * in the local policy provider.
     *
     * <p>This method is safe to call concurrently — last writer wins, which is
     * correct because {@link RemotePolicyService#getCurrentGrants()} always
     * returns a consistent snapshot.
     */
    void syncGrants() {
        try {
            String[] grants = service.getCurrentGrants();
            if (grants == null) {
                logger.warning("PolicyUpdateListener: getCurrentGrants() returned null; skipping sync.");
                return;
            }
            String policyText = String.join("\n", grants);
            DefaultPolicyParser parser = new DefaultPolicyParser();
            Collection<PermissionGrant> parsed =
                    parser.parse(new StringReader(policyText), System.getProperties());
            localPolicy.replace(parsed.toArray(new PermissionGrant[0]));
            logger.fine("PolicyUpdateListener: grants synced (" + parsed.size() + " grants applied).");
        } catch (RemoteException e) {
            logger.log(Level.WARNING,
                    "PolicyUpdateListener: RemoteException while syncing grants; will retry on next notification.", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "PolicyUpdateListener: IOException while applying grants to local policy.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "PolicyUpdateListener: Unexpected exception while syncing grants.", e);
        }
    }

    /**
     * Attempts to re-subscribe after a lease loss, using exponential backoff
     * when the service is unreachable.
     *
     * <p>Clears the existing registration and calls {@link #start()} again.
     * Retries indefinitely with exponential backoff up to
     * {@value #MAX_BACKOFF_MS} ms between attempts.
     */
    void resubscribe() {
        // Clear old state under lock.
        synchronized (this) {
            registration = null;
            lastSeqNum.set(-1L); // reset so gap detection is fresh after re-registration
            // Unexport and clear stub so start() can re-export.
            if (stub != null) {
                try {
                    exporter.unexport(true);
                } catch (Exception ex) {
                    logger.log(Level.FINE,
                            "Exception unexporting listener during resubscribe; ignoring.", ex);
                }
                stub = null;
            }
        }

        long backoff = INITIAL_BACKOFF_MS;
        // Retry indefinitely with exponential backoff until either:
        // (a) start() succeeds and returns normally, or
        // (b) the thread is interrupted (e.g. the JVM is shutting down).
        // An infinite loop is acceptable here because without a live lease the
        // local policy would silently become stale; aggressive retry is safer.
        while (true) {
            try {
                start();
                logger.warning("PolicyUpdateListener: re-subscribe succeeded.");
                return;
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "PolicyUpdateListener: re-subscribe failed; retrying in " + backoff + " ms.", e);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warning("PolicyUpdateListener: re-subscribe interrupted; giving up.");
                    return;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
    }
}
