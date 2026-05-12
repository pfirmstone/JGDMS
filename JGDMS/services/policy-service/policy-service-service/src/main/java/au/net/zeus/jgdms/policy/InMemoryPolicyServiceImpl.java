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
package au.net.zeus.jgdms.policy;

import au.net.zeus.jgdms.policy.proxy.PolicyEventLease;
import au.net.zeus.jgdms.policy.proxy.PolicyUpdateEvent;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.security.Permission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyPermission;
import org.apache.river.api.security.RemotePolicyService;
import org.apache.river.constants.ThrowableConstants;

/**
 * Core server-side implementation of the {@link RemotePolicyService}.
 *
 * <p>This class manages the in-memory set of djinn-wide policy grants.  The
 * administrator submits grants as {@code String[]} in standard Java policy-file
 * syntax; this class parses and validates them, stores the authoritative set,
 * and notifies all registered listeners asynchronously.
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>{@link #currentGrants} and {@link #parsedGrants} are written only
 *       from within {@link #replace} under {@link #grantsLock}; reads of the
 *       volatile references are always safe.</li>
 *   <li>Listener registrations are managed by a
 *       {@link ConcurrentHashMap}; reads and updates are lock-free.</li>
 *   <li>Event delivery is fully asynchronous via a bounded
 *       {@link ThreadPoolExecutor}.</li>
 * </ul>
 *
 * <h2>Access control</h2>
 * {@link #replace} enforces {@link PolicyPermission}{@code ("Remote")} before
 * any grant is parsed or stored.  After parsing, each grant is individually
 * validated by checking that the caller holds the corresponding
 * {@link GrantPermission} via {@link GrantPermission#checkGuard}.
 *
 * @see RemotePolicyService
 * @see ActivatableInMemoryPolicyServiceImpl
 * @since 3.1.1
 */
public class InMemoryPolicyServiceImpl {

    private static final Logger logger =
            Logger.getLogger(InMemoryPolicyServiceImpl.class.getName());

    // -------------------------------------------------------------------------
    // Configuration constants
    // -------------------------------------------------------------------------

    /** Maximum number of concurrent in-flight event dispatches (§16.1 DoS cap). */
    private static final int MAX_CONCURRENT_DISPATCHES = 500;

    /** Hard cap on simultaneous listener registrations (§16.2 DoS cap). */
    private static final int MAX_LISTENER_REGISTRATIONS = 1000;

    /** Maximum listener lease duration: 1 day. */
    private static final long MAX_LISTENER_LEASE_DURATION =
            TimeUnit.DAYS.toMillis(1);

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /**
     * The authoritative wire-format grants.  Written only under
     * {@link #grantsLock}; volatile for lock-free reads.
     */
    private volatile String[] currentGrants = new String[0];

    /**
     * The parsed form of {@link #currentGrants}, used by
     * {@link RemotePolicyService#replace} callers for grant-ceiling validation.
     * Written only under {@link #grantsLock}.
     */
    private volatile PermissionGrant[] parsedGrants = new PermissionGrant[0];

    /**
     * Guards atomic update of {@link #currentGrants} and
     * {@link #parsedGrants} so that a concurrent {@link #getCurrentGrants()}
     * call always sees a consistent pair.
     */
    private final Object grantsLock = new Object();

    /**
     * Active listener registrations: leaseId → ListenerRegistration.
     * Entries are added in {@link #registerForPolicyUpdates} and removed on
     * lease cancellation, expiry, or definite delivery failure.
     */
    private final ConcurrentHashMap<Uuid, ListenerRegistration> listenerRegistrations =
            new ConcurrentHashMap<Uuid, ListenerRegistration>();

    /** Source of monotonically-increasing event IDs. */
    private final AtomicLong nextEventId = new AtomicLong(1L);

    /** Async event-delivery executor (virtual thread per task, JDK 21+). */
    private final ExecutorService eventDispatcher;

    /**
     * Limits the number of concurrently in-flight dispatch tasks to
     * {@link #MAX_CONCURRENT_DISPATCHES}, preventing heap exhaustion when
     * listeners are slow or listeners flood registrations (§16.1).
     */
    private final Semaphore dispatchSemaphore = new Semaphore(MAX_CONCURRENT_DISPATCHES);

    /**
     * Guards the size check + map insertion in
     * {@link #registerForPolicyUpdates} to prevent TOCTOU races when multiple
     * threads race to register near the {@link #MAX_LISTENER_REGISTRATIONS} cap.
     */
    private final Object registrationLock = new Object();

    /** Reference to the lease-sweep daemon thread; interrupted on {@link #shutdown()}. */
    private volatile Thread leaseSweepThread;

    /**
     * Parser used to convert String[] grants to PermissionGrant[].
     * DefaultPolicyParser is effectively thread-safe (stateless per parse call).
     */
    private final DefaultPolicyParser policyParser;

    /**
     * The exported server stub, set by {@link #setEventSource} after export.
     * Used as the event source in {@link PolicyUpdateEvent} objects and as
     * the server reference in {@link PolicyEventLease} objects.
     */
    private volatile RemotePolicyService eventSource;

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /** State for one event-listener registration. */
    private static final class ListenerRegistration {
        final Uuid              leaseId;
        final long              eventId;
        final AtomicLong        seqNum;
        volatile long           leaseExpiration;
        final RemoteEventListener listener;
        final MarshalledInstance  handback;

        ListenerRegistration(Uuid leaseId,
                             long eventId,
                             long leaseExpiration,
                             RemoteEventListener listener,
                             MarshalledInstance handback) {
            this.leaseId         = leaseId;
            this.eventId         = eventId;
            this.seqNum          = new AtomicLong(0L);
            this.leaseExpiration = leaseExpiration;
            this.listener        = listener;
            this.handback        = handback;
        }
    }

    /**
     * Delivers a single {@link PolicyUpdateEvent} to one listener.
     * Uses {@link ThrowableConstants} to classify failures: definite failures
     * cancel the registration; transient failures are logged and left for the
     * next event.
     */
    private final class SendPolicyUpdateTask implements Runnable {

        private final ListenerRegistration reg;
        private final long                 seqNum;

        SendPolicyUpdateTask(ListenerRegistration reg, long seqNum) {
            this.reg    = reg;
            this.seqNum = seqNum;
        }

        @Override
        public void run() {
            // Expiry check: skip delivery if the lease has expired.
            if (System.currentTimeMillis() > reg.leaseExpiration) {
                listenerRegistrations.remove(reg.leaseId);
                return;
            }
            PolicyUpdateEvent event = new PolicyUpdateEvent(
                    eventSource, reg.eventId, seqNum, reg.handback);
            try {
                reg.listener.notify(event);
            } catch (Throwable t) {
                switch (ThrowableConstants.retryable(t)) {
                    case ThrowableConstants.BAD_OBJECT:
                        if (t instanceof Error) throw (Error) t;
                        // fall through — definite failure
                    case ThrowableConstants.BAD_INVOCATION:
                    case ThrowableConstants.UNCATEGORIZED:
                        listenerRegistrations.remove(reg.leaseId);
                        logger.log(Level.INFO,
                                "Cancelled listener registration after definite "
                                + "delivery failure", t);
                        break;
                    default:
                        // ThrowableConstants.INDEFINITE — transient; leave intact
                        logger.log(Level.FINE,
                                "Transient failure delivering PolicyUpdateEvent; "
                                + "registration retained", t);
                        break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code InMemoryPolicyServiceImpl}.
     */
    public InMemoryPolicyServiceImpl() {
        this.policyParser = new DefaultPolicyParser();
        this.eventDispatcher = Executors.newVirtualThreadPerTaskExecutor();
        startLeaseSweepDaemon();
    }

    /**
     * Starts a daemon virtual thread that sweeps expired listener registrations
     * every 60 seconds (§16.2 Option B).  Without this, entries only leave the
     * map during event delivery; if no events fire, the map would grow without
     * bound.
     */
    private void startLeaseSweepDaemon() {
        leaseSweepThread = Thread.ofVirtual()
              .name("JGDMS-PolicyService-LeaseSweep")
              .start(() -> {
                  while (!Thread.currentThread().isInterrupted()) {
                      try {
                          Thread.sleep(Duration.ofMinutes(1));
                      } catch (InterruptedException e) {
                          return;
                      }
                      long now = System.currentTimeMillis();
                      listenerRegistrations.values().removeIf(r -> r.leaseExpiration < now);
                  }
              });
    }

    // -------------------------------------------------------------------------
    // Post-export initialisation
    // -------------------------------------------------------------------------

    /**
     * Provides the exported server stub so that events and leases carry the
     * correct remote reference.  Must be called once after the service is
     * exported and before any remote calls are accepted.
     *
     * @param stub the exported server stub; must not be {@code null}
     */
    public void setEventSource(RemotePolicyService stub) {
        if (stub == null) throw new NullPointerException("stub");
        this.eventSource = stub;
    }

    // -------------------------------------------------------------------------
    // RemotePolicyService operations
    // -------------------------------------------------------------------------

    /**
     * Replaces the current grants with those supplied.
     *
     * <ol>
     *   <li>Enforces {@link PolicyPermission}{@code ("Remote")} on the caller.</li>
     *   <li>Parses each string as a Java policy grant clause.</li>
     *   <li>Validates the caller holds {@link GrantPermission} for every
     *       permission in every parsed grant.</li>
     *   <li>Atomically stores the new grants.</li>
     *   <li>Asynchronously notifies all registered listeners.</li>
     * </ol>
     *
     * @param grants policy grant clauses; must not be {@code null}
     * @throws RemoteException      if a communication error occurs
     * @throws SecurityException    if the caller lacks the required permissions
     * @throws IllegalArgumentException if any string cannot be parsed
     */
    public void replace(String[] grants) throws RemoteException {
        if (grants == null) throw new NullPointerException("grants");

        // 1. Enforce PolicyPermission("Remote") on the calling Subject.
        //    The JERI transport has already authenticated the caller;
        //    the security manager checks that the caller's ProtectionDomain
        //    implies this permission.
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new PolicyPermission("Remote"));
        }

        // 2. Assemble the full policy text and parse it.
        StringBuilder sb = new StringBuilder();
        for (String g : grants) {
            if (g == null) throw new NullPointerException("null element in grants");
            sb.append(g).append('\n');
        }

        Collection<PermissionGrant> parsed;
        try {
            parsed = policyParser.parse(
                    new StringReader(sb.toString()),
                    System.getProperties());
        } catch (SecurityException e) {
            throw e; // propagate unchanged
        } catch (Exception e) {
            throw new RemoteException("Failed to parse policy grants", e);
        }

        // 3. Validate the caller holds GrantPermission for every permission.
        validateGrants(parsed);

        // 4. Atomically store the new grants.
        PermissionGrant[] newParsed =
                parsed.toArray(new PermissionGrant[parsed.size()]);
        String[] newGrants = grants.clone();

        synchronized (grantsLock) {
            parsedGrants  = newParsed;
            currentGrants = newGrants;
        }

        logger.log(Level.INFO, "Policy grants replaced: {0} grant(s)", newGrants.length);

        // 5. Notify listeners asynchronously.
        dispatchUpdateEvent();
    }

    /**
     * Returns the current wire-format grants.
     *
     * @return defensive copy of the current grants; never {@code null}
     */
    public String[] getCurrentGrants() {
        String[] snapshot = currentGrants; // copy volatile reference
        return snapshot.clone();
    }

    /**
     * Registers a listener for policy-update notifications.
     *
     * @param listener the listener to notify; must not be {@code null}
     * @param handback opaque handback for the listener; may be {@code null}
     * @param duration requested lease duration in milliseconds
     * @return the event registration
     * @throws RemoteException if the registration limit ({@value #MAX_LISTENER_REGISTRATIONS})
     *         has been reached
     */
    public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                      MarshalledInstance handback,
                                                      long duration)
            throws RemoteException {
        if (listener == null) throw new NullPointerException("listener");
        // §16.2 Option A: synchronized check + put prevents TOCTOU races that
        // could allow the map to slightly exceed MAX_LISTENER_REGISTRATIONS.
        synchronized (registrationLock) {
            if (listenerRegistrations.size() >= MAX_LISTENER_REGISTRATIONS) {
                throw new RemoteException("listener registration limit reached");
            }
            if (duration <= 0) duration = MAX_LISTENER_LEASE_DURATION;
            long granted = Math.min(duration, MAX_LISTENER_LEASE_DURATION);
            long expiration = System.currentTimeMillis() + granted;

            Uuid leaseId  = UuidFactory.generate();
            long eventId  = nextEventId.getAndIncrement();

            ListenerRegistration reg = new ListenerRegistration(
                    leaseId, eventId, expiration, listener, handback);
            listenerRegistrations.put(leaseId, reg);

            PolicyEventLease lease = new PolicyEventLease(eventSource, leaseId, expiration);
            return new EventRegistration(eventId, eventSource, lease, 0L);
        }
    }

    /**
     * Renews the lease identified by {@code leaseId}.
     *
     * @param leaseId  the lease cookie
     * @param duration the requested renewal duration
     * @return the actual duration granted
     * @throws UnknownLeaseException if no registration exists for {@code leaseId}
     */
    public long renewPolicyLease(Uuid leaseId, long duration)
            throws UnknownLeaseException {
        ListenerRegistration reg = listenerRegistrations.get(leaseId);
        if (reg == null) throw new UnknownLeaseException(leaseId.toString());
        long granted = Math.min(duration, MAX_LISTENER_LEASE_DURATION);
        reg.leaseExpiration = System.currentTimeMillis() + granted;
        return granted;
    }

    /**
     * Cancels the lease identified by {@code leaseId}, removing the listener.
     *
     * @param leaseId the lease cookie
     * @throws UnknownLeaseException if no registration exists for {@code leaseId}
     */
    public void cancelPolicyLease(Uuid leaseId) throws UnknownLeaseException {
        ListenerRegistration reg = listenerRegistrations.remove(leaseId);
        if (reg == null) throw new UnknownLeaseException(leaseId.toString());
    }

    /**
     * Returns the parsed {@link PermissionGrant}s currently stored by this
     * service.  Intended for use by a co-located {@link
     * org.apache.river.api.security.RemotePolicyProvider} that wraps this
     * service.
     *
     * @return defensive copy of the current parsed grants; never {@code null}
     */
    public PermissionGrant[] getParsedGrants() {
        PermissionGrant[] snapshot = parsedGrants; // copy volatile reference
        return snapshot.clone();
    }

    /**
     * Shuts down the event-dispatch executor and the lease-sweep daemon thread.
     * Should be called on service destroy.
     */
    public void shutdown() {
        eventDispatcher.shutdown();
        Thread sweeper = leaseSweepThread;
        if (sweeper != null) sweeper.interrupt();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that the calling thread's security context holds
     * {@link GrantPermission} for every permission in each of the supplied
     * grants.  Throws {@link SecurityException} on first failure.
     */
    private static void validateGrants(Collection<PermissionGrant> grants)
            throws SecurityException {
        for (PermissionGrant grant : grants) {
            Collection<Permission> permCol = grant.getPermissions();
            Permission[] perms = permCol.toArray(new Permission[permCol.size()]);
            for (Permission p : perms) {
                if (p == null) throw new NullPointerException(
                        "null Permission in PermissionGrant");
            }
            new GrantPermission(perms).checkGuard(null);
        }
    }

    /**
     * Schedules asynchronous delivery of a {@link PolicyUpdateEvent} to all
     * currently registered listeners.
     */
    private void dispatchUpdateEvent() {
        for (ListenerRegistration reg : listenerRegistrations.values()) {
            long seqNum = reg.seqNum.incrementAndGet();
            // §16.1: acquire a permit before submitting; skip rather than queue
            // unboundedly when the executor is saturated.
            if (!dispatchSemaphore.tryAcquire()) {
                logger.log(Level.WARNING,
                        "Event dispatch semaphore exhausted; skipping listener {0}",
                        reg.leaseId);
                continue;
            }
            try {
                eventDispatcher.submit(withSemaphoreRelease(new SendPolicyUpdateTask(reg, seqNum)));
            } catch (Exception e) {
                dispatchSemaphore.release();
                logger.log(Level.WARNING,
                        "Could not submit PolicyUpdateEvent dispatch task", e);
            }
        }
    }

    /**
     * Wraps a task so that {@link #dispatchSemaphore} is released in a
     * {@code finally} block regardless of how the task completes.
     */
    private Runnable withSemaphoreRelease(Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                dispatchSemaphore.release();
            }
        };
    }
}
