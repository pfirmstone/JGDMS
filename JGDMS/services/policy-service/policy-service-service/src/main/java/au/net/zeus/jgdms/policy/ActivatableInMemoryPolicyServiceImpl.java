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

import au.net.zeus.jgdms.policy.proxy.RemotePolicyServiceBackend;
import au.net.zeus.jgdms.policy.proxy.RemotePolicyServiceProxy;
import au.net.zeus.jgdms.service.annotation.JiniService;
import au.net.zeus.jgdms.service.annotation.ProxyType;
import au.net.zeus.jgdms.service.support.AbstractJiniService;
import au.net.zeus.jgdms.service.support.JiniServiceParameters;
import java.io.IOException;
import java.rmi.RemoteException;
import javax.security.auth.Subject;
import net.jini.activation.arg.ActivationID;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.security.RemotePolicyService;
import org.apache.river.start.lifecycle.LifeCycle;

/**
 * Activatable, Jini-aware wrapper around {@link InMemoryPolicyServiceImpl}.
 *
 * <p>This class adds the full Jini service infrastructure to the core
 * implementation: it reads all configuration from a Jini {@link Configuration},
 * exports itself via a Jini exporter, builds a
 * {@link RemotePolicyServiceProxy} for clients, and registers with lookup
 * services.  All infrastructure boilerplate is inherited from
 * {@link AbstractJiniService}.
 *
 * <p>Service-specific configuration is validated eagerly by
 * {@link PolicyServiceParameters} before the service object is constructed.
 *
 * <h2>SPIFFE identity</h2>
 * This service is provisioned with SVID
 * {@code spiffe://jgdms.example.org/host/policy}.  The bootstrap policy
 * ({@code SpiffePolicyFile}) grants {@link org.apache.river.api.security.PolicyPermission}
 * {@code ("Remote")} only to the admin SVID
 * {@code spiffe://jgdms.example.org/admin/policy}.  All other callers are
 * rejected by the security manager before {@link #replace} stores anything.
 *
 * <h2>Configuration component</h2>
 * All entries are read from component {@value #COMPONENT}.
 * Common infrastructure entries are documented in {@link JiniServiceParameters}.
 *
 * <h2>Activatable constructor</h2>
 * The {@code (ActivationID, String[])} constructor satisfies the Phoenix
 * activation-group contract.
 *
 * <h2>Non-activatable constructor</h2>
 * The {@code (String[], LifeCycle)} constructor supports the
 * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter} framework.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @see InMemoryPolicyServiceImpl
 * @see RemotePolicyService
 * @see JiniServiceParameters
 * @see AbstractJiniService
 * @since 3.1.1
 */
@JiniService(
        api      = RemotePolicyService.class,   // the service (remote) API interface
        proxy    = ProxyType.SMART,             // wraps the stub in a generated smart proxy
        codebase = true)                        // ships a downloadable -dl proxy jar
public class ActivatableInMemoryPolicyServiceImpl
        extends AbstractJiniService
        implements RemotePolicyServiceBackend {

    /** Configuration component name for this service. */
    static final String COMPONENT = "au.net.zeus.jgdms.policy";

    /** The core implementation to which all RemotePolicyService calls are delegated. */
    private final InMemoryPolicyServiceImpl impl;

    // -------------------------------------------------------------------------
    // Public constructors
    // -------------------------------------------------------------------------

    /**
     * Activatable constructor.  Required by Phoenix.
     *
     * @param activationID the activation ID assigned by the activation system
     * @param data         configuration arguments for
     *                     {@link ConfigurationProvider#getInstance}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableInMemoryPolicyServiceImpl(ActivationID activationID,
                                                String[] data)
            throws Exception {
        this(new PolicyServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             ActivatableInMemoryPolicyServiceImpl.class.getClassLoader()),
                     activationID),
             null);
    }

    /**
     * Non-activatable constructor for use with
     * {@code NonActivatableServiceDescriptor} / {@code ServiceStarter}.
     *
     * @param configArgs configuration arguments for
     *                   {@link ConfigurationProvider#getInstance}
     * @param lifeCycle  lifecycle callback; may be {@code null}
     * @throws ConfigurationException if a mandatory configuration entry is
     *                                missing or invalid
     * @throws Exception              if construction otherwise fails
     */
    public ActivatableInMemoryPolicyServiceImpl(String[] configArgs,
                                                LifeCycle lifeCycle)
            throws Exception {
        this(new PolicyServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             ActivatableInMemoryPolicyServiceImpl.class.getClassLoader()),
                     null),
             lifeCycle);
    }

    /**
     * Internal constructor: receives pre-validated parameters.
     */
    private ActivatableInMemoryPolicyServiceImpl(PolicyServiceParameters params,
                                                 LifeCycle lifeCycle)
            throws IOException {
        super(params, lifeCycle);
        this.impl = new InMemoryPolicyServiceImpl();
    }

    // -------------------------------------------------------------------------
    // AbstractJiniService template methods
    // -------------------------------------------------------------------------

    /**
     * Provides the exported server stub to the core implementation so it can
     * populate event sources and build leases correctly.
     */
    @Override
    protected void onExported(Object stub) throws RemoteException {
        impl.setEventSource((RemotePolicyService) stub);
    }

    @Override
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return RemotePolicyServiceProxy.create((RemotePolicyService) stub, serviceUuid);
    }

    // getServiceInterfaces() is inherited: it reads api() = { RemotePolicyService.class }
    // from the @JiniService annotation above.

    /**
     * Shuts down the event-dispatch thread pool when the service is destroyed.
     */
    @Override
    public synchronized void destroy() {
        impl.shutdown();
        super.destroy();
    }

    // -------------------------------------------------------------------------
    // RemotePolicyService — delegate all calls to the core implementation
    // -------------------------------------------------------------------------

    @Override
    public void replace(String[] grants) throws RemoteException {
        getReadyState().check();
        impl.replace(grants);
    }

    @Override
    public String[] getCurrentGrants() throws RemoteException {
        getReadyState().check();
        return impl.getCurrentGrants();
    }

    @Override
    public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                      MarshalledInstance handback,
                                                      long duration)
            throws IOException {
        getReadyState().check();
        return impl.registerForPolicyUpdates(listener, handback, duration);
    }

    @Override
    public long renewPolicyLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        getReadyState().check();
        return impl.renewPolicyLease(leaseId, duration);
    }

    @Override
    public void cancelPolicyLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        getReadyState().check();
        impl.cancelPolicyLease(leaseId);
    }

    // -------------------------------------------------------------------------
    // Parameter object
    // -------------------------------------------------------------------------

    /**
     * Parameter object for {@link ActivatableInMemoryPolicyServiceImpl}.
     *
     * <p>Reads and validates all common Jini service configuration entries.
     * No service-specific mandatory entries are required beyond those provided
     * by {@link JiniServiceParameters}.
     */
    public static final class PolicyServiceParameters extends JiniServiceParameters {

        /**
         * Reads common Jini service configuration.
         *
         * @param config       the Jini configuration; must be non-null
         * @param activationID the Phoenix activation ID, or {@code null} for
         *                     non-activatable deployments
         * @throws ConfigurationException if any mandatory entry is missing or invalid
         */
        public PolicyServiceParameters(Configuration config,
                                       ActivationID activationID)
                throws ConfigurationException {
            super(config, COMPONENT, activationID, RemotePolicyService.class);
        }
    }
}
