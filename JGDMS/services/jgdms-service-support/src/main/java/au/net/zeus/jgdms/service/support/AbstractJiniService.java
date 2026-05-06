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
package au.net.zeus.jgdms.service.support;

import au.net.zeus.jgdms.proxy.AdminProxy;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
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
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.util.Startable;
import org.apache.river.proxy.CodebaseProvider;
import org.apache.river.reliableLog.LogHandler;
import org.apache.river.reliableLog.ReliableLog;
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
                   JoinAdmin,
                   DestroyAdmin,
                   Remote,
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
    /**
     * Directory for persistent state, or {@code null} for non-persistent operation.
     * When non-null, a {@link ReliableLog} is created in {@link #doStart()} and
     * the ServiceID (plus any subclass state written by
     * {@link #snapshot(ObjectOutputStream)}) is recovered across restarts.
     */
    private final String persistDir;
    /**
     * JAAS login context, or {@code null} when no login is required.
     * When non-null, {@link #start()} performs {@link LoginContext#login()}
     * and runs the start body as the resulting Subject.
     */
    private final LoginContext loginContext;

    // -------------------------------------------------------------------------
    // Volatile post-start fields
    // -------------------------------------------------------------------------

    /** The raw exported server stub (remote reference). Set during {@link #start()}. */
    private volatile Object serverStub;

    /** The smart proxy returned to clients. Set during {@link #start()}. */
    private volatile Object outerProxy;

    /** Stable service identity. Set once during {@link #start()}. */
    private volatile ServiceID serviceId;

    /**
     * The stable Uuid form of {@link #serviceId}, used by the admin proxy.
     * Set once during {@link #start()}, together with {@code serviceId}.
     */
    private volatile Uuid serviceUuid;

    /** Manages discovery and lookup-service registration. Set during {@link #start()}. */
    private volatile JoinManager joiner;

    /**
     * The discovery manager used by the {@link JoinManager}.
     * {@link LookupDiscoveryManager} implements both
     * {@link DiscoveryGroupManagement} and {@link DiscoveryLocatorManagement},
     * so the private helpers {@link #groupMgmt()} and {@link #locatorMgmt()}
     * cast it without duplicating the cast at each call site.
     * Set during {@link #start()}.
     */
    private volatile LookupDiscoveryManager ldm;

    /**
     * Write-ahead transaction log for persistent state.
     * {@code null} when {@link #persistDir} is {@code null}.
     * Created in {@link #doStart()}.
     */
    private volatile ReliableLog log;

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    /** Guards all service calls: rejects requests before start or after shutdown. */
    private final ReadyState readyState = new ReadyState();

    /** Ensures {@link #start()} is idempotent. */
    private boolean started = false;

    /**
     * The Subject under which this service was started, or {@code null} when
     * no JAAS {@link LoginContext} was configured.  Set once during
     * {@link #start()} and consulted during {@link #destroy()} for logout.
     */
    private volatile Subject loginSubject;

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
        this.loginContext          = params.loginContext;
        this.lookupAttrs           = params.lookupAttributes.clone();
        this.initialLookupGroups   = params.lookupGroups.clone();
        this.initialLookupLocators = params.lookupLocators.clone();
        this.codebaseAnnotation    = params.codebaseAnnotation;
        this.certFactoryType       = params.certFactoryType;
        this.certPathEncoding      = params.certPathEncoding;
        this.encodedCerts          = params.encodedCerts.clone();
        this.persistDir            = params.persistDir;
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
     * <p>When a {@code loginContext} is present in the service configuration,
     * this method performs a JAAS {@link LoginContext#login()} and runs the
     * entire start sequence as the resulting
     * {@link javax.security.auth.Subject}.  The Subject is logged out when
     * the service is {@link #destroy destroyed}.
     *
     * @throws Exception if export, JAAS login, or discovery setup fails
     */
    @Override
    public final synchronized void start() throws Exception {
        if (started) return;
        started = true;

        if (loginContext != null) {
            loginContext.login();
            loginSubject = loginContext.getSubject();
            /*
             * Run the start sequence under the login Subject.
             *
             * Subject.callAs establishes the user Subject on the ScopedValue
             * so that BasicInvocationHandler can detect it via Subject.current()
             * and transmit the user principals to remote endpoints.
             */
            Subject.callAs(loginSubject, () -> {
                doStart();
                return null;
            });
        } else {
            doStart();
        }
    }

    /**
     * Internal implementation of the start sequence.  Always called from
     * within the correct security context (either directly or via
     * {@link Subject#callAs}).
     */
    private void doStart() throws Exception {
        // If persistence is configured, create the log and recover state.
        // Recovery populates serviceId (and any subclass state) if this
        // is a restart rather than a fresh start.  Use the concrete class's
        // ClassLoader so that subclass-specific objects deserialize correctly.
        if (persistDir != null) {
            ReliableLog l = new ReliableLog(persistDir, new ServiceLogHandler());
            log = l;
            try {
                l.recover(getClass().getClassLoader()); // no-op on first start; populates serviceId on restart
            } catch (Exception e) {
                // Recovery failed — close the log and propagate so start() fails cleanly
                log = null;
                try { l.close(); } catch (IOException ignore) { /* best-effort */ }
                throw e;
            }
        }

        Object stub = exporter.export(this);
        serverStub = stub;
        logger.log(Level.CONFIG, "{0} exported: {1}",
                new Object[]{getClass().getSimpleName(), stub});

        onExported(stub);

        // Use the recovered UUID (from serviceId) on a restart; generate a
        // fresh one on a first start.
        final Uuid uuid;
        if (serviceId != null) {
            uuid = UuidFactory.create(serviceId.getMostSignificantBits(),
                                      serviceId.getLeastSignificantBits());
            logger.log(Level.CONFIG, "Recovered ServiceID: {0}", serviceId);
        } else {
            uuid = UuidFactory.generate();
            serviceId = new ServiceID(
                    uuid.getMostSignificantBits(),
                    uuid.getLeastSignificantBits());
            logger.log(Level.CONFIG, "Generated ServiceID: {0}", serviceId);
            // Write the initial baseline snapshot so future restarts can
            // recover this ServiceID.
            ReliableLog l = log;
            if (l != null) {
                l.snapshot();
            }
        }

        Object proxy = createProxy(stub, uuid);
        outerProxy = proxy;
        serviceUuid = uuid;

        LookupDiscoveryManager discoveryMgr = new LookupDiscoveryManager(
                initialLookupGroups, initialLookupLocators, null);
        ldm = discoveryMgr;
        joiner = new JoinManager(proxy, lookupAttrs, serviceId, discoveryMgr, null);
        logger.log(Level.INFO, "{0} started, serviceId={1}",
                new Object[]{getClass().getSimpleName(), serviceId});

        onStart(loginSubject);
        readyState.ready();
    }

    /**
     * Destroys this service: terminates discovery, unexports, and — if a
     * JAAS login was performed — logs out.
     *
     * <p>Subclasses may override this method to perform additional cleanup,
     * but must call {@code super.destroy()} to ensure the login session is
     * correctly terminated.
     */
    @Override
    public synchronized void destroy() {
        JoinManager jm = joiner;
        if (jm != null) {
            jm.terminate();
            joiner = null;
        }
        LookupDiscoveryManager l = ldm;
        if (l != null) {
            l.terminate();
            ldm = null;
        }
        try {
            exporter.unexport(true);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Problem unexporting service", e);
        }
        ReliableLog rl = log;
        if (rl != null) {
            log = null;
            try {
                rl.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Problem closing reliable log", e);
            }
        }
        if (loginContext != null) {
            try {
                loginContext.logout();
            } catch (LoginException e) {
                logger.log(Level.WARNING,
                        "Trouble logging out of JAAS login session", e);
            }
            loginSubject = null;
        }
        readyState.shutdown();
    }

    // -------------------------------------------------------------------------
    // Template methods for subclasses
    // -------------------------------------------------------------------------

    /**
     * Called during {@link #start()} after the service is exported, the smart
     * proxy is built, and the {@link JoinManager} is running — but before the
     * service is made visible to callers via {@link ReadyState#ready()}.
     *
     * <p>This hook is always called from within the correct security context:
     * when a JAAS {@link LoginContext} is configured the call executes inside
     * {@link Subject#callAs}, so the authenticated Subject is available via
     * {@link Subject#current()} on this thread.
     * When no login is configured {@code subject} is {@code null}.
     *
     * <p>The default implementation is a no-op.  Override this method if the
     * concrete service needs to initialise Subject-aware infrastructure
     * (thread pools, scheduled tasks, etc.) before accepting remote calls.
     *
     * @param subject the authenticated Subject, or {@code null} if no JAAS
     *                login was performed
     */
    protected void onStart(Subject subject) {
        // default no-op
    }

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
     * {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy}, passing {@code stub} and {@code serviceUuid}
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
    // Persistence template methods
    // -------------------------------------------------------------------------

    /**
     * Writes service-specific state to the persistent snapshot.
     *
     * <p>This method is called by the infrastructure's {@link LogHandler}
     * whenever a snapshot is taken (once on first start to establish a
     * baseline, and subsequently whenever the log grows large enough to
     * warrant compaction).  The base implementation writes the
     * {@link ServiceID}; subclasses should call {@code super.snapshot(out)}
     * first and then write their own fields.
     *
     * <p>The default override is a no-op (the ServiceID is written by the
     * infrastructure regardless).
     *
     * @param out the object output stream for the snapshot; never {@code null}
     * @throws Exception if serialisation fails
     */
    protected void snapshot(ObjectOutputStream out) throws Exception {
        // default no-op — ServiceID is handled by ServiceLogHandler
    }

    /**
     * Reads service-specific state back from a persistent snapshot.
     *
     * <p>This method is called during {@link #start()} recovery, after the
     * infrastructure has read the {@link ServiceID}.  The stream position is
     * exactly where {@link #snapshot(ObjectOutputStream)} left off.
     *
     * <p>The default implementation is a no-op.
     *
     * @param in the object input stream for the snapshot; never {@code null}
     * @throws Exception if deserialisation fails
     */
    protected void recover(ObjectInputStream in) throws Exception {
        // default no-op
    }

    /**
     * Applies an incremental state-change record that was previously written
     * via {@link #logUpdate(Object)}.
     *
     * <p>Called during recovery for each log record written after the last
     * snapshot.  The default implementation is a no-op.
     *
     * @param update the update object previously passed to
     *               {@link #logUpdate(Object)}; may be {@code null}
     * @throws Exception if applying the update fails
     */
    protected void applyUpdate(Object update) throws Exception {
        // default no-op
    }

    /**
     * Records an incremental state-change to the persistent transaction log.
     *
     * <p>Subclasses call this method whenever service state changes that must
     * survive a restart.  If the service is non-persistent (no
     * {@code persistenceDirectory} config entry) this is a no-op.  The
     * {@code update} object must be serializable.
     *
     * <p>After enough log records accumulate the infrastructure may
     * automatically compact the log by taking a new snapshot.
     *
     * @param update the state-change record; must be serializable
     * @throws IOException if writing to the log fails
     */
    protected final void logUpdate(Object update) throws IOException {
        ReliableLog l = log;
        if (l != null) {
            l.update(update);
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: LogHandler implementation
    // -------------------------------------------------------------------------

    /**
     * Handles snapshot and recovery for the persistent {@link ReliableLog}.
     *
     * <p>The base snapshot format is:
     * <ol>
     *   <li>{@code long} — ServiceID most-significant bits</li>
     *   <li>{@code long} — ServiceID least-significant bits</li>
     *   <li>service-specific data written by
     *       {@link AbstractJiniService#snapshot(ObjectOutputStream)}</li>
     * </ol>
     */
    private final class ServiceLogHandler extends LogHandler {

        @Override
        public void snapshot(OutputStream out) throws Exception {
            ObjectOutputStream oos = new ObjectOutputStream(out);
            ServiceID sid = serviceId;
            if (sid == null) {
                throw new IllegalStateException(
                        "snapshot() called before serviceId is set");
            }
            oos.writeLong(sid.getMostSignificantBits());
            oos.writeLong(sid.getLeastSignificantBits());
            AbstractJiniService.this.snapshot(oos);
            oos.flush();
        }

        @Override
        public void recover(InputStream in) throws Exception {
            ObjectInputStream ois = new ObjectInputStream(in);
            long msb = ois.readLong();
            long lsb = ois.readLong();
            serviceId = new ServiceID(msb, lsb);
            AbstractJiniService.this.recover(ois);
        }

        @Override
        public void applyUpdate(Object update) throws Exception {
            AbstractJiniService.this.applyUpdate(update);
        }
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
    public final Object getProxy() {
        return serverStub;
    }

    // -------------------------------------------------------------------------
    // Administrable
    // -------------------------------------------------------------------------

    /**
     * Returns an {@link AdminProxy} for this service.
     *
     * <p>The admin proxy implements {@link JoinAdmin} (allowing clients to
     * modify the lookup-service groups, locators, and attributes the service
     * registers with) and {@link DestroyAdmin} (allowing clients to shut the
     * service down).  If the server stub implements
     * {@link net.jini.core.constraint.RemoteMethodControl} the returned proxy
     * is automatically the constrainable variant.
     *
     * <p>Subclasses may override this method to return a richer admin object,
     * but should ensure the returned object still implements at least
     * {@link JoinAdmin} and {@link DestroyAdmin}.
     *
     * @return an admin proxy for this service
     * @throws RemoteException if the service has not been started
     */
    @Override
    public Object getAdmin() throws RemoteException {
        readyState.check();
        Object stub = serverStub;
        Uuid uuid = serviceUuid;
        if (stub instanceof Remote && stub instanceof JoinAdmin
                && stub instanceof DestroyAdmin && uuid != null) {
            return AdminProxy.create((Remote) stub, uuid);
        }
        return stub;
    }

    // -------------------------------------------------------------------------
    // JoinAdmin — delegates to the JoinManager / LookupDiscoveryManager
    // -------------------------------------------------------------------------

    /** Returns {@link #ldm} cast to {@link DiscoveryGroupManagement}, or {@code null}. */
    private DiscoveryGroupManagement groupMgmt() {
        return ldm;  // LookupDiscoveryManager implements DiscoveryGroupManagement
    }

    /** Returns {@link #ldm} cast to {@link DiscoveryLocatorManagement}, or {@code null}. */
    private DiscoveryLocatorManagement locatorMgmt() {
        return ldm;  // LookupDiscoveryManager implements DiscoveryLocatorManagement
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the {@link JoinManager} for attribute management.
     */
    @Override
    public final Entry[] getLookupAttributes() throws RemoteException {
        readyState.check();
        JoinManager jm = joiner;
        return jm != null ? jm.getAttributes() : lookupAttrs.clone();
    }

    /** {@inheritDoc} */
    @Override
    public final void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        readyState.check();
        JoinManager jm = joiner;
        if (jm != null) {
            jm.addAttributes(attrSets);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void modifyLookupAttributes(Entry[] attrSetTemplates,
                                             Entry[] attrSets) throws RemoteException {
        readyState.check();
        JoinManager jm = joiner;
        if (jm != null) {
            jm.modifyAttributes(attrSetTemplates, attrSets);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final String[] getLookupGroups() throws RemoteException {
        readyState.check();
        DiscoveryGroupManagement gm = groupMgmt();
        return gm != null ? gm.getGroups() : initialLookupGroups.clone();
    }

    /** {@inheritDoc} */
    @Override
    public final void addLookupGroups(String[] groups) throws RemoteException {
        readyState.check();
        DiscoveryGroupManagement gm = groupMgmt();
        if (gm != null) {
            try {
                gm.addGroups(groups);
            } catch (IOException e) {
                throw new RemoteException("addLookupGroups failed", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void removeLookupGroups(String[] groups) throws RemoteException {
        readyState.check();
        DiscoveryGroupManagement gm = groupMgmt();
        if (gm != null) {
            gm.removeGroups(groups);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void setLookupGroups(String[] groups) throws RemoteException {
        readyState.check();
        DiscoveryGroupManagement gm = groupMgmt();
        if (gm != null) {
            try {
                gm.setGroups(groups);
            } catch (IOException e) {
                throw new RemoteException("setLookupGroups failed", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public final LookupLocator[] getLookupLocators() throws RemoteException {
        readyState.check();
        DiscoveryLocatorManagement lm = locatorMgmt();
        return lm != null ? lm.getLocators() : initialLookupLocators.clone();
    }

    /** {@inheritDoc} */
    @Override
    public final void addLookupLocators(LookupLocator[] locators) throws RemoteException {
        readyState.check();
        DiscoveryLocatorManagement lm = locatorMgmt();
        if (lm != null) {
            lm.addLocators(locators);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void removeLookupLocators(LookupLocator[] locators) throws RemoteException {
        readyState.check();
        DiscoveryLocatorManagement lm = locatorMgmt();
        if (lm != null) {
            lm.removeLocators(locators);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void setLookupLocators(LookupLocator[] locators) throws RemoteException {
        readyState.check();
        DiscoveryLocatorManagement lm = locatorMgmt();
        if (lm != null) {
            lm.setLocators(locators);
        }
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
    public final Object getServiceProxy() throws RemoteException {
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
     * <p>Delegates to {@link #getLookupAttributes()}.
     *
     * @return the current attributes; never {@code null}
     * @throws IOException if the service has not been started or a
     *                     communication failure occurs
     */
    @Override
    public final Entry[] getServiceAttributes() throws IOException {
        return getLookupAttributes();
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
    public final ServiceID serviceID() throws IOException {
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
    public final String getClassAnnotation() throws IOException {
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
    public final String getCertFactoryType() throws IOException {
        return certFactoryType;
    }

    /** {@inheritDoc} */
    @Override
    public final String getCertPathEncoding() throws IOException {
        return certPathEncoding;
    }

    /** {@inheritDoc} */
    @Override
    public final byte[] getEncodedCerts() throws IOException {
        return encodedCerts.clone();
    }
}
