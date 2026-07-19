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
package au.net.zeus.jgdms.api.policy;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.security.DefaultPolicyParser;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PolicyPermission;

/**
 * JERI wire interface for the {@code InMemoryPolicyService}.
 *
 * <p>This interface is the remote API through which an authorised administrator
 * (the DirtyChai admin tool, identified by SPIFFE SVID
 * {@code spiffe://jgdms.example.org/admin/policy}) manages djinn-wide
 * {@link PermissionGrant}s.  Nodes in the djinn register as listeners; when
 * the administrator calls {@link #replace} all registered listeners are
 * notified so that their local {@link RemotePolicyProvider} can pull the
 * updated grants via {@link #getCurrentGrants}.
 *
 * <h2>Wire format</h2>
 * Grants are transmitted as {@code String[]} in standard Java policy-file
 * syntax (one {@code grant { ... };} clause per element).  The server parses
 * these strings with {@link DefaultPolicyParser} on receipt; the client never
 * needs to instantiate {@link PermissionGrant} directly, which avoids the
 * {@code @AtomicSerial} requirement on the wire.
 *
 * <h2>Access control</h2>
 * The server enforces {@link PolicyPermission}{@code ("Remote")} on the
 * calling Subject for {@link #replace}.  The bootstrap policy
 * ({@code SpiffePolicyFile}) grants this permission only to the admin SPIFFE
 * identity.
 *
 * <h2>Event model</h2>
 * Clients use a pull-on-notification pattern: the
 * {@link net.jini.core.event.RemoteEvent} delivered via
 * {@link #registerForPolicyUpdates} signals that the grants have changed;
 * clients then call {@link #getCurrentGrants} to obtain the authoritative
 * snapshot.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 * @see RemotePolicyProvider
 * @see PolicyPermission
 */
public interface RemotePolicyService extends Remote {

    /**
     * Replaces all current policy grants with the supplied set.
     *
     * <p>Each element of {@code grants} must be a syntactically valid Java
     * policy grant clause (e.g. {@code grant principal ...  { permission ...; };}).
     * The server parses and validates all grants before applying any change;
     * if parsing or validation fails the existing grants remain intact.
     *
     * <p>The calling Subject must hold {@link PolicyPermission}{@code ("Remote")}
     * and {@link net.jini.security.GrantPermission} for every permission
     * contained in the grants.  The security manager enforces these constraints
     * before any grant is stored.
     *
     * <p>On success, all registered listeners are notified asynchronously.
     *
     * @param grants policy grant clauses in standard Java policy-file syntax;
     *               must not be {@code null}; individual elements must not be
     *               {@code null}
     * @throws RemoteException if a communication error occurs
     * @throws SecurityException if the caller lacks the required permissions
     * @throws IllegalArgumentException if any element cannot be parsed as a
     *                                  valid grant clause
     */
    void replace(String[] grants) throws RemoteException;

    /**
     * Returns the current set of policy grant clauses stored by this service.
     *
     * <p>Each element is a single {@code grant { ... };} clause in standard
     * Java policy-file syntax.  The returned array is a snapshot; subsequent
     * calls to {@link #replace} do not affect a previously returned array.
     *
     * @return the current grants; never {@code null}; may be empty
     * @throws RemoteException if a communication error occurs
     */
    String[] getCurrentGrants() throws RemoteException;

    /**
     * Registers a listener to be notified whenever the policy grants change.
     *
     * <p>When the grants are replaced via {@link #replace}, the service
     * delivers a {@code PolicyUpdateEvent} to the registered listener.  The
     * event carries only the event ID, sequence number, and opaque handback;
     * clients should call {@link #getCurrentGrants} to fetch the new grants.
     *
     * <p>The returned {@link EventRegistration} contains a
     * {@link net.jini.core.lease.Lease} that must be periodically renewed to
     * keep the registration alive.
     *
     * @param listener  the listener to be notified; must not be {@code null}
     * @param handback  opaque object returned in every event; may be
     *                  {@code null}
     * @param duration  requested lease duration in milliseconds; the server
     *                  may grant a shorter duration
     * @return the event registration containing the lease and event ID
     * @throws IOException if a communication error occurs or the server
     *                     refuses the registration
     */
    EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                               MarshalledInstance handback,
                                               long duration) throws IOException;

    /**
     * Renews the lease associated with the given registration.
     *
     * @param leaseId  the cookie that identifies the lease to renew
     * @param duration the requested renewal duration in milliseconds
     * @return the actual duration granted by the server
     * @throws UnknownLeaseException if {@code leaseId} is not recognised
     * @throws RemoteException       if a communication error occurs
     */
    long renewPolicyLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException;

    /**
     * Cancels the lease associated with the given registration, removing the
     * listener from the notification set.
     *
     * @param leaseId the cookie that identifies the lease to cancel
     * @throws UnknownLeaseException if {@code leaseId} is not recognised
     * @throws RemoteException       if a communication error occurs
     */
    void cancelPolicyLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException;
}
