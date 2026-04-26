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
package org.apache.river.service.support;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.api.util.Startable;
import org.apache.river.proxy.CodebaseProvider;
import org.apache.river.start.lifecycle.LifeCycle;
import org.apache.river.thread.ReadyState;

/**
 * Abstract base class for Jini/JGDMS activatable service implementations.
 *
 * <p>This class encapsulates all infrastructure boilerplate common to every
 * Jini service built on the {@link Startable} / {@link ProxyAccessor}
 * framework:
 * <ul>
 *   <li>Exporting the service via a configurable {@link Exporter}</li>
 *   <li>Building the client-side smart proxy</li>
 *   <li>Generating a stable {@link ServiceID}</li>
 *   <li>Starting discovery and joining lookup services via a
 *       {@link JoinManager}</li>
 *   <li>Implementing {@link ProxyAccessor}, {@link ServiceProxyAccessor},
 *       {@link ServiceAttributesAccessor}, {@link ServiceIDAccessor},
 *       {@link CodebaseAccessor}, and {@link Administrable} correctly</li>
 *   <li>Guarding all service methods with a {@link ReadyState} that
 *       rejects calls before {@link #start()} or after shutdown</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>Subclass {@link JiniServiceParameters}, read service-specific
 *       configuration in the subclass constructor, and let any
 *       {@link net.jini.config.ConfigurationException} propagate naturally.
 *       All validation happens before the service object is created.</li>
 *   <li>Extend {@code AbstractJiniService} and implement the two template
 *       methods:
 *       <ul>
 *         <li>{@link #createProxy(Object, Uuid)} — wrap the exported server stub
 *             in the appropriate smart proxy</li>
 *         <li>{@link #getServiceInterfaces()} — return the remote service
 *             interface(s), used as the codebase fallback</li>
 *       </ul>
 *   </li>
 *   <li>Override {@link #onExported(Object)} if post-export work is needed
 *       (e.g. providing the server stub to a delegate implementation).</li>
 * </ol>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 */
public abstract class AbstractJiniService
        implements ProxyAccessor,
                   Startable,
                   Administrable,
                   CodebaseAccessor,
                   ServiceProxyAccessor,
                   ServiceAttributesAccessor,
                   ServiceIDAccessor {

    private static final Logger logger =
            Logger.getLogger(AbstractJiniService.class.getName());

    // -------------------------------------------------------------------------
    // Infrastructure fields — set from JiniServiceParameters in constructor
    // -------------------------------------------------------------------------

    private final Exporter exporter;
    private final Entry[] lookupAttrs;
    private final String[] initialLookupGroups;
    private final net.jini.core.discovery.LookupLocator[] initialLookupLocators;
    private final String codebaseAnnotation;
    private final String certFactoryType;
    private final String certPathEncoding;
    private final byte[] encodedCerts;
    private final LifeCycle lifeCycle;

    // -------------------------------------------------------------------------
    // Volatile post-start fields
    // -------------------------------------------------------------------------

    /** The raw exported server stub (remote reference). Set during {@link #start()}. */
    private volatile Object serverStub;

    /** The smart proxy returned to clients. Set during {@link #start()}. */
    private volatile Object outerProxy;

    /** Stable service identity. Set once during {@link #start()}. */
    private volatile ServiceID serviceId;

    /** Manages discovery and lookup-service registration. Set during {@link #start()}. */
    private volatile JoinManager joiner;

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    /** Guards all service calls: rejects requests before start or after shutdown. */
    private final ReadyState readyState = new ReadyState();

    /** Ensures {@link #start()} is idempotent. */
    private boolean started = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Initialises all infrastructure fields from a pre-validated
     * {@link JiniServiceParameters} instance.
     *
     * <p>Because all configuration reading and validation is performed by
     * {@link JiniServiceParameters} before this constructor is reached, no
     * exceptions can originate here.
     *
     * @param params    validated parameter object; must be non-null
     * @param lifeCycle lifecycle callback for non-activatable deployments;
     *                  may be {@code null}
     */
    protected AbstractJiniService(JiniServiceParameters params,
                                  LifeCycle lifeCycle) {
        if (params == null) throw new NullPointerException("params");
        this.exporter              = params.exporter;
        this.lookupAttrs           = params.lookupAttributes.clone();
        this.initialLookupGroups   = params.lookupGroups.clone();
        this.initialLookupLocators = params.lookupLocators.clone();
        this.codebaseAnnotation    = params.codebaseAnnotation;
        this.certFactoryType       = params.certFactoryType;
        this.certPathEncoding      = params.certPathEncoding;
        this.encodedCerts          = params.encodedCerts.clone();
        this.lifeCycle             = lifeCycle;
    }

    // -------------------------------------------------------------------------
    // Startable
    // -------------------------------------------------------------------------

    /**
     * Exports this service, builds the client proxy, generates or restores
     * the {@link ServiceID}, and joins Jini lookup services.
     *
     * <p>This method is idempotent: subsequent invocations return immediately.
     *
     * @throws Exception if export or discovery setup fails
     */
    @Override
    public synchronized void start() throws Exception {
        if (started) return;
        started = true;

        Object stub = exporter.export(this);
        serverStub = stub;
        logger.log(Level.CONFIG, "{0} exported: {1}",
                new Object[]{getClass().getSimpleName(), stub});

        onExported(stub);

        Uuid uuid = UuidFactory.generate();
        Object proxy = createProxy(stub, uuid);
        outerProxy = proxy;

        if (serviceId == null) {
            serviceId = new ServiceID(
                    uuid.getMostSignificantBits(),
                    uuid.getLeastSignificantBits());
            logger.log(Level.CONFIG, "Generated ServiceID: {0}", serviceId);
        }

        LookupDiscoveryManager ldm = new LookupDiscoveryManager(
                initialLookupGroups, initialLookupLocators, null);
        joiner = new JoinManager(proxy, lookupAttrs, serviceId, ldm, null);
        logger.log(Level.INFO, "{0} started, serviceId={1}",
                new Object[]{getClass().getSimpleName(), serviceId});

        readyState.ready();
    }

    // -------------------------------------------------------------------------
    // Template methods for subclasses
    // -------------------------------------------------------------------------

    /**
     * Called immediately after this service is exported, before the smart
     * proxy is built and before discovery starts.
     *
     * <p>Subclasses may override this to perform post-export setup, e.g.
     * providing the exported server stub to a delegate implementation.
     * The default implementation is a no-op.
     *
     * @param stub the exported server stub returned by the exporter;
     *             never {@code null}
     * @throws RemoteException if post-export setup fails
     */
    protected void onExported(Object stub) throws RemoteException {
        // default no-op
    }

    /**
     * Wraps the raw exported server stub in the service's smart client proxy.
     *
     * <p>Implementations should return an instance of a class that extends
     * {@link org.apache.river.proxy.AbstractSmartProxy}, passing {@code stub} and {@code serviceUuid}
     * to the superclass constructor.  This ensures the proxy carries the
     * stable service UUID needed for correct {@code equals()} / {@code hashCode()}
     * behaviour and {@link net.jini.id.ReferentUuid} identity.
     *
     * @param stub        the exported server stub; never {@code null}
     * @param serviceUuid the stable unique identifier generated for this
     *                    service instance; never {@code null}
     * @return the smart proxy to advertise in lookup services; must be
     *         non-null
     */
    protected abstract Object createProxy(Object stub, Uuid serviceUuid);

    /**
     * Returns the remote service interfaces implemented by this service.
     *
     * <p>The first element is used as the fallback argument to
     * {@link CodebaseProvider#getClassAnnotation(Class)} when no explicit
     * codebase annotation has been configured.
     *
     * <p>A service may implement more than one remote interface; returning
     * all of them here allows future infrastructure to register the service
     * under each interface in the lookup service.
     *
     * @return the service interface classes; must be non-null and non-empty
     */
    protected abstract Class<?>[] getServiceInterfaces();

    // -------------------------------------------------------------------------
    // ReadyState access for subclasses
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ReadyState} guard used to protect service methods.
     *
     * <p>Subclasses should call {@code getReadyState().check()} at the start
     * of every remotely-accessible method to ensure the service is running.
     *
     * @return the ready-state guard; never {@code null}
     */
    protected final ReadyState getReadyState() {
        return readyState;
    }

    // -------------------------------------------------------------------------
    // ProxyAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the exported server stub (raw remote reference).
     *
     * <p>Used by the Phoenix activation infrastructure to obtain a reference
     * to this service after re-activation.  May return {@code null} before
     * {@link #start()} is called.
     *
     * @return the server stub, or {@code null} if not yet started
     */
    @Override
    public Object getProxy() {
        return serverStub;
    }

    // -------------------------------------------------------------------------
    // Administrable
    // -------------------------------------------------------------------------

    /**
     * Returns the administration object for this service.
     *
     * <p>Returns the exported server stub, which implements
     * {@link ServiceAttributesAccessor}, {@link ServiceIDAccessor},
     * {@link ServiceProxyAccessor}, and {@link CodebaseAccessor} —
     * all of the interfaces needed for remote administration.
     * Subclasses may override this to return a richer admin proxy.
     *
     * @return the exported server stub as the administration object
     * @throws RemoteException if the service has not been started
     */
    @Override
    public Object getAdmin() throws RemoteException {
        readyState.check();
        return serverStub;
    }

    // -------------------------------------------------------------------------
    // ServiceProxyAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the smart proxy that clients use to interact with this service.
     *
     * @return the outer smart proxy
     * @throws RemoteException if the service has not been started yet
     */
    @Override
    public Object getServiceProxy() throws RemoteException {
        readyState.check();
        return outerProxy;
    }

    // -------------------------------------------------------------------------
    // ServiceAttributesAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the lookup attributes currently registered with Jini lookup
     * services.
     *
     * @return the current attributes; never {@code null}
     * @throws IOException if the service has not been started or a
     *                     communication failure occurs
     */
    @Override
    public Entry[] getServiceAttributes() throws IOException {
        readyState.check();
        JoinManager jm = joiner;
        return jm != null ? jm.getAttributes() : lookupAttrs.clone();
    }

    // -------------------------------------------------------------------------
    // ServiceIDAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ServiceID} of this service.
     *
     * @return the service identity; non-null once the service has started
     * @throws IOException if the service has not been started
     */
    @Override
    public ServiceID serviceID() throws IOException {
        readyState.check();
        return serviceId;
    }

    // -------------------------------------------------------------------------
    // CodebaseAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the codebase annotation for the service's proxy class.
     *
     * <p>Falls back to {@link CodebaseProvider#getClassAnnotation} for the
     * first class returned by {@link #getServiceInterfaces()} when no explicit
     * codebase annotation has been configured.
     *
     * @return the codebase annotation; never {@code null}
     * @throws IOException if a communication failure occurs
     */
    @Override
    public String getClassAnnotation() throws IOException {
        if (codebaseAnnotation != null && !codebaseAnnotation.isEmpty()) {
            return codebaseAnnotation;
        }
        Class<?>[] ifaces = getServiceInterfaces();
        if (ifaces == null || ifaces.length == 0) {
            throw new IllegalStateException(
                    "getServiceInterfaces() must return a non-null, non-empty array");
        }
        return CodebaseProvider.getClassAnnotation(ifaces[0]);
    }

    /** {@inheritDoc} */
    @Override
    public String getCertFactoryType() throws IOException {
        return certFactoryType;
    }

    /** {@inheritDoc} */
    @Override
    public String getCertPathEncoding() throws IOException {
        return certPathEncoding;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getEncodedCerts() throws IOException {
        return encodedCerts.clone();
    }
}
