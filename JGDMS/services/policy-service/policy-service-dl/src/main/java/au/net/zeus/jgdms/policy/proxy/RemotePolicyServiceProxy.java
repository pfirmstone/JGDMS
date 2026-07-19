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
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;
import au.net.zeus.jgdms.api.policy.RemotePolicyService;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link RemotePolicyService}.
 *
 * <p>Downloaded to client JVMs; forwards all {@link RemotePolicyService} method
 * calls to the remote server-side implementation over a JERI transport channel.
 *
 * <p>Use the {@link #create(RemotePolicyService, Uuid)} factory method rather
 * than constructing directly; the factory automatically returns a
 * {@link ConstrainableRemotePolicyServiceProxy} when the server stub implements
 * {@link RemoteMethodControl}.
 *
 * @see RemotePolicyService
 * @see AbstractSmartProxy
 * @since 3.1.1
 */
@AtomicSerial
@Stateless  // no own serialized state; server + proxyID live on AbstractSmartProxy
public abstract class RemotePolicyServiceProxy
        extends AbstractSmartProxy
        implements RemotePolicyService {

    /**
     * Factory method — ALWAYS returns a
     * {@link ConstrainableRemotePolicyServiceProxy}, and fails closed when the
     * server stub does not implement {@link RemoteMethodControl}.  This class is
     * {@code abstract} so the constrainable form is the only concrete wire proxy
     * — a client that requested Integrity/ServerAuthentication/Confidentiality
     * can never be handed a plain proxy that silently dropped them.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @return the constrainable proxy instance
     * @throws IllegalArgumentException if {@code server} does not implement
     *         {@link RemoteMethodControl}
     */
    public static AbstractSmartProxy create(RemotePolicyService server, Uuid proxyID) {
        if (!(server instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                    "service must be exported with a constrainable endpoint: "
                    + "server does not implement RemoteMethodControl");
        }
        // Preserve the constraints already configured on the exported stub;
        // passing null would call setConstraints(null) and discard them.
        MethodConstraints serverConstraints =
                ((RemoteMethodControl) server).getConstraints();
        return new ConstrainableRemotePolicyServiceProxy(
                server, proxyID, serverConstraints);
    }

    /**
     * Creates a new proxy wrapping the given server stub.  Only invoked by
     * subclass constructors — this class is abstract.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    protected RemotePolicyServiceProxy(RemotePolicyService server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.  Only chained to by the
     * concrete subclass — this class is abstract and is never itself a wire
     * instance.
     *
     * <p>{@link AbstractSmartProxy} validates that the deserialized
     * {@code server} stub implements every interface declared on this concrete
     * proxy class; no additional check is needed here.
     *
     * @param arg the deserialization argument bag
     * @throws IOException if deserialization validation fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    protected RemotePolicyServiceProxy(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(arg);
    }

    @Override
    public void replace(String[] grants) throws java.rmi.RemoteException {
        ((RemotePolicyService) server).replace(grants);
    }

    @Override
    public String[] getCurrentGrants() throws java.rmi.RemoteException {
        return ((RemotePolicyService) server).getCurrentGrants();
    }

    @Override
    public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                      MarshalledInstance handback,
                                                      long duration)
            throws IOException {
        return ((RemotePolicyService) server).registerForPolicyUpdates(
                listener, handback, duration);
    }

    @Override
    public long renewPolicyLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, java.rmi.RemoteException {
        return ((RemotePolicyService) server).renewPolicyLease(leaseId, duration);
    }

    @Override
    public void cancelPolicyLease(Uuid leaseId)
            throws UnknownLeaseException, java.rmi.RemoteException {
        ((RemotePolicyService) server).cancelPolicyLease(leaseId);
    }

    // =========================================================================
    // Nested class: ConstrainableRemotePolicyServiceProxy
    // =========================================================================

    /**
     * Constrainable subclass of {@link RemotePolicyServiceProxy}.
     *
     * <p>Provides full {@link RemoteMethodControl} support.  Instances are
     * produced by the {@link RemotePolicyServiceProxy#create} factory when the
     * server stub implements {@link RemoteMethodControl}.
     *
     * @since 3.1.1
     */
    @AtomicSerial
    @Stateless  // no own serialized state
    public static final class ConstrainableRemotePolicyServiceProxy
            extends AbstractSmartProxy.ConstrainableSmartProxy
            implements RemotePolicyService {

        private static final long serialVersionUID = 1L;

        /**
         * Creates a new constrained proxy.
         *
         * @param server      the remote server stub
         * @param proxyID     the service's stable unique identifier
         * @param constraints the client method constraints; may be {@code null}
         */
        public ConstrainableRemotePolicyServiceProxy(RemotePolicyService server,
                                                     Uuid proxyID,
                                                     MethodConstraints constraints) {
            super(server, proxyID, constraints);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * @param arg the deserialization argument bag
         * @throws IOException if deserialization validation fails
         * @throws ClassNotFoundException if a required class cannot be found
         */
        public ConstrainableRemotePolicyServiceProxy(GetArg arg)
                throws IOException, ClassNotFoundException {
            super(arg);
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableRemotePolicyServiceProxy(
                    (RemotePolicyService) server, getReferentUuid(), constraints);
        }

        @Override
        public void replace(String[] grants) throws java.rmi.RemoteException {
            ((RemotePolicyService) server).replace(grants);
        }

        @Override
        public String[] getCurrentGrants() throws java.rmi.RemoteException {
            return ((RemotePolicyService) server).getCurrentGrants();
        }

        @Override
        public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                          MarshalledInstance handback,
                                                          long duration)
                throws IOException {
            return ((RemotePolicyService) server).registerForPolicyUpdates(
                    listener, handback, duration);
        }

        @Override
        public long renewPolicyLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, java.rmi.RemoteException {
            return ((RemotePolicyService) server).renewPolicyLease(leaseId, duration);
        }

        @Override
        public void cancelPolicyLease(Uuid leaseId)
                throws UnknownLeaseException, java.rmi.RemoteException {
            ((RemotePolicyService) server).cancelPolicyLease(leaseId);
        }
    }
}
