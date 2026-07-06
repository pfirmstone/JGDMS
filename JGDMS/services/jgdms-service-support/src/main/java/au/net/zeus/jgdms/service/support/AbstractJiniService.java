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
import au.net.zeus.jgdms.service.annotation.JiniService;
import au.net.zeus.jgdms.service.annotation.ProxyType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.CodebaseAccessor;
import net.jini.export.CodebaseDigestUtil;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.activation.arg.ActivationID;
import net.jini.config.ConfigurationProvider;
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
 *   <li>Annotate the concrete service class with
 *       {@link au.net.zeus.jgdms.service.annotation.JiniService @JiniService},
 *       naming its {@code api()} interface(s) (or leaving {@code api()} empty to
 *       have them inferred).  {@link #getServiceInterfaces()} reads this at
 *       construction — a class with no {@code @JiniService} fails fast.</li>
 *   <li>Subclass {@link JiniServiceParameters}, read service-specific
 *       configuration in the subclass constructor, and let any
 *       {@link net.jini.config.ConfigurationException} propagate naturally.
 *       All validation happens before the service object is created.</li>
 *   <li>Extend {@code AbstractJiniService}.  A
 *       {@link au.net.zeus.jgdms.service.annotation.ProxyType#DYNAMIC} service
 *       needs no template-method override at all; a
 *       {@link au.net.zeus.jgdms.service.annotation.ProxyType#SMART} service
 *       overrides {@link #createProxy(Object, Uuid)} to wrap the exported server
 *       stub in its generated smart proxy.</li>
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

    /**
     * The four JGDMS bootstrap-accessor interfaces.  Each extends {@link Remote},
     * so without an explicit exclusion the {@link #classify service-interface
     * inference} would mistake them for the service API.  They carry the framework's
     * bootstrap contract (proxy/serviceID/attributes/codebase accessors), not the
     * service's own API, so they are subtracted from the inferred api set (design
     * decision D6).
     */
    private static final Set<String> BOOTSTRAP_ACCESSORS = Set.of(
            "net.jini.lookup.ServiceProxyAccessor",
            "net.jini.lookup.ServiceIDAccessor",
            "net.jini.lookup.ServiceAttributesAccessor",
            "net.jini.export.CodebaseAccessor");

    /**
     * The non-{@link Remote} framework interfaces that are neither service API nor
     * admin-facet interfaces: {@link Administrable} (stays on the thin service stub,
     * deliberately absent from the admin facet — the canonical Jini contract is that
     * {@code Administrable.getAdmin()} returns a proxy that is
     * {@code JoinAdmin}/{@code DestroyAdmin} but NOT {@code Administrable}),
     * {@link net.jini.core.constraint.RemoteMethodControl}, {@link Startable},
     * {@link ProxyAccessor} (a server-lifecycle SPI that {@code AbstractJiniService}
     * itself implements — NOT an administrative interface), and the bare
     * {@code Remote} marker.  Everything else non-{@code Remote} the impl implements
     * is an admin-style interface reached via {@code getAdmin()} (design decision D6).
     *
     * <p>{@code ProxyAccessor} MUST be listed here: {@code AbstractJiniService}
     * directly implements it, so without this exclusion every service's derived admin
     * set would be {@code {JoinAdmin, DestroyAdmin, ProxyAccessor}} rather than exactly
     * {@code {JoinAdmin, DestroyAdmin}}, forcing the common case off the stable
     * {@code ConstrainableAdminProxy} wire form.
     *
     * @see #classify(Class)
     */
    private static final Set<String> NON_ADMIN_NON_REMOTE = Set.of(
            "net.jini.admin.Administrable",
            "net.jini.core.constraint.RemoteMethodControl",
            "org.apache.river.api.util.Startable",
            "net.jini.export.ProxyAccessor",
            "java.rmi.Remote");

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

    /**
     * Cached per-JAR flat digest array; {@code null} until computed in
     * {@link #doStart()}, or if the codebase annotation contains no JAR URLs.
     */
    private volatile byte[] codebaseDigestFlat;

    /**
     * Per-JAR start-byte offsets into {@link #codebaseDigestFlat};
     * {@code null} when {@link #codebaseDigestFlat} is {@code null}.
     */
    private volatile int[] codebaseDigestOffsets;

    /**
     * Algorithm used to compute {@link #codebaseDigestFlat}
     * (e.g. {@code "SHA-256"}); {@code null} when no digest was computed.
     */
    private volatile String codebaseDigestAlgorithm;
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

    /**
     * The service (remote) interfaces this service advertises, resolved once from
     * the {@link JiniService} annotation on the concrete class and cached (never
     * recomputed per call, and no {@link ThreadLocal} — this codebase targets
     * virtual threads).  Resolved eagerly in the constructor so a missing
     * {@code @JiniService} fails fast at construction rather than at first use.
     *
     * @see #getServiceInterfaces()
     */
    private final Class<?>[] serviceInterfaces;

    /**
     * The admin facet interfaces derived once from the concrete class's interface
     * closure (see {@link #resolveAdminInterfaces()}, design decision D1) and cached.
     * Always contains {@link JoinAdmin} and {@link DestroyAdmin}; may contain
     * additional admin interfaces a subclass declares.  Consulted by
     * {@link #adminFacetInterfaces()} when building the admin facet in
     * {@link #createAdminProxy(Object, Uuid)}.
     */
    private final Class<?>[] adminIfaces;

    /**
     * Optional method constraints applied to the admin facet (design decision D4),
     * or {@code null} to inherit the server reference's constraints unchanged.
     * Read from the {@code adminConstraints} config entry via
     * {@link JiniServiceParameters}.
     */
    private final MethodConstraints adminConstraints;

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

    /**
     * The admin facet proxy returned by {@link #getAdmin()} (design decision D3).
     * Built once in {@link #doStart()} — right after the server stub and
     * {@link #serviceUuid} are set — and cached; {@link #getAdmin()} simply returns
     * it after a {@link ReadyState} check.  {@code null} until {@link #doStart()}.
     */
    private volatile Object adminProxy;

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
        this.adminConstraints      = params.adminConstraints;
        this.lifeCycle             = lifeCycle;
        this.serviceInterfaces     = resolveServiceInterfaces();
        this.adminIfaces           = resolveAdminInterfaces();
    }

    /**
     * The single, shared interface classifier (design decision D6): partitions the
     * interfaces an {@code implClass} implements into the service <em>api</em> set,
     * the <em>admin</em> facet set, and the framework <em>infra</em> remainder,
     * under one allowlist rule reused verbatim by both {@link #resolveServiceInterfaces()}
     * and {@link #resolveAdminInterfaces()} (and mirrored name-for-name by the
     * annotation processor's {@code ServiceModel}).
     *
     * <ul>
     *   <li><b>api</b>   = {implemented interfaces that extend {@link Remote}} minus
     *       the four {@link #BOOTSTRAP_ACCESSORS bootstrap accessors} minus
     *       {@code Remote} itself.</li>
     *   <li><b>admin</b> = {implemented non-{@code Remote} interfaces} minus
     *       {@link #NON_ADMIN_NON_REMOTE} ({@link Administrable},
     *       {@code RemoteMethodControl}, {@link Startable}, {@code Remote}) — i.e.
     *       {@link JoinAdmin}, {@link DestroyAdmin}, and ANY custom non-{@code Remote}
     *       admin interface the impl declares (a {@code FooAdmin}), all reached via
     *       {@code Administrable.getAdmin()}.</li>
     *   <li><b>infra</b> = everything else (the bootstrap accessors,
     *       {@code Administrable}, {@code RemoteMethodControl}, {@code Startable},
     *       {@code Remote}).</li>
     * </ul>
     *
     * <p>The whole interface closure of {@code implClass} is walked (super-interfaces
     * and superclasses included), so {@code JoinAdmin}/{@code DestroyAdmin} declared
     * on {@code AbstractJiniService} itself, and any custom admin interface declared
     * on a subclass, are all seen.
     *
     * @param implClass the concrete service implementation class
     * @return the classification of {@code implClass}'s interfaces
     */
    static Classification classify(Class<?> implClass) {
        Set<Class<?>> all = new LinkedHashSet<>();
        collectAllInterfaces(implClass, all);
        Set<Class<?>> api = new LinkedHashSet<>();
        Set<Class<?>> admin = new LinkedHashSet<>();
        for (Class<?> iface : all) {
            String name = iface.getName();
            if ("java.rmi.Remote".equals(name)) {
                continue;
            }
            if (Remote.class.isAssignableFrom(iface)) {
                // Remote interface: a service API unless it is a bootstrap accessor.
                if (!BOOTSTRAP_ACCESSORS.contains(name)) {
                    api.add(iface);
                }
            } else if (!NON_ADMIN_NON_REMOTE.contains(name)) {
                // Non-Remote interface that is not framework scaffolding: admin.
                admin.add(iface);
            }
        }
        return new Classification(
                api.toArray(new Class<?>[0]),
                admin.toArray(new Class<?>[0]));
    }

    /**
     * The result of {@link #classify(Class)}: the service {@code api} interfaces and
     * the {@code admin}-facet interfaces.  Both arrays are shared internally and must
     * not be mutated by callers.
     */
    static final class Classification {
        final Class<?>[] api;
        final Class<?>[] admin;
        Classification(Class<?>[] api, Class<?>[] admin) {
            this.api = api;
            this.admin = admin;
        }
    }

    /**
     * Resolves the service (remote) interfaces from the {@link JiniService}
     * annotation on the concrete service class.
     *
     * <p>Walks up the superclass chain so that a subclass of an already-annotated
     * service still finds the annotation (the annotation is not
     * {@link java.lang.annotation.Inherited}, and {@code @Inherited} would in any
     * case only cover the direct concrete class, not intermediate abstract bases).
     * If {@link JiniService#api()} is non-empty it is returned verbatim; otherwise
     * ALL service interfaces are inferred via the shared {@link #classify(Class)
     * classifier} (design decisions D2, D6) — multi-interface inference: every
     * implemented {@code Remote} interface that is not a bootstrap accessor is
     * registered, and ambiguity is no longer an error.
     *
     * @return the resolved, non-empty service interface array
     * @throws IllegalStateException if the concrete class (or an ancestor) carries
     *         no {@code @JiniService}, or if inference yields no interfaces
     */
    private Class<?>[] resolveServiceInterfaces() {
        Class<?> concrete = getClass();
        JiniService ann = null;
        for (Class<?> c = concrete; c != null && c != Object.class; c = c.getSuperclass()) {
            ann = c.getAnnotation(JiniService.class);
            if (ann != null) {
                break;
            }
        }
        if (ann == null) {
            throw new IllegalStateException(
                    "service implementation " + concrete.getName()
                    + " (or an ancestor) must be annotated with @"
                    + JiniService.class.getName()
                    + "; getServiceInterfaces() now derives the service API from it");
        }
        Class<?>[] api = ann.api();
        if (api != null && api.length > 0) {
            return api.clone();
        }
        // A translating SMART proxy (proxy=SMART with a distinct, non-empty protocol[])
        // exposes client interfaces via the downloaded proxy that the server impl does
        // NOT implement — the impl implements the wire/protocol interface(s). Inference
        // from the impl would misadvertise those wire interfaces, so require api() to be
        // declared explicitly here.  protocol() is now Class<?>[] (default {}); reaching
        // this branch means api() is already empty, so ANY non-empty protocol[] is
        // distinct from the (empty) api set and triggers the fail-fast.
        Class<?>[] protocol = ann.protocol();
        if (ann.proxy() == ProxyType.SMART && protocol != null && protocol.length > 0) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < protocol.length; i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(protocol[i].getName());
            }
            throw new IllegalStateException(
                    "@" + JiniService.class.getSimpleName() + " on " + concrete.getName()
                    + " is a translating SMART proxy (proxy=SMART, protocol=[" + names
                    + "]) with an empty api(); the client service interface(s) cannot be inferred from the"
                    + " implementation, which implements the wire/protocol interface(s). Declare api() explicitly.");
        }
        // Infer ALL service interfaces via the shared classifier (D2, D6): every
        // implemented Remote interface minus the bootstrap accessors and Remote.
        Class<?>[] inferred = classify(concrete).api;
        if (inferred.length == 0) {
            throw new IllegalStateException(
                    "@" + JiniService.class.getSimpleName() + " on " + concrete.getName()
                    + " has an empty api() and no service interface could be inferred"
                    + " (the class implements only infrastructure interfaces); declare"
                    + " api() explicitly");
        }
        return inferred;
    }

    /**
     * Derives the admin facet's interface set (design decision D5/D6) via the shared
     * {@link #classify(Class) classifier}.  The set is every non-{@code Remote}
     * interface the concrete class's closure declares minus the framework
     * scaffolding ({@link Administrable}, {@code RemoteMethodControl},
     * {@link Startable}, {@code Remote}).  What survives is {@link JoinAdmin},
     * {@link DestroyAdmin} (always present — {@code AbstractJiniService} directly
     * implements them), and any additional admin interface a subclass declares (a
     * custom {@code FooAdmin}), picked up automatically.
     *
     * @return the admin facet interface classes; never {@code null}, and in
     *         practice never empty (always contains {@code JoinAdmin} and
     *         {@code DestroyAdmin})
     */
    private Class<?>[] resolveAdminInterfaces() {
        return classify(getClass()).admin;
    }

    /**
     * Recursively collects every interface (super-interfaces included) implemented
     * by {@code cl} and its superclasses into {@code out}, each once.
     *
     * @param cl  the class whose interface closure to walk
     * @param out the accumulating set (also the recursion guard)
     */
    private static void collectAllInterfaces(Class<?> cl, Set<Class<?>> out) {
        if (cl == null || cl == Object.class) {
            return;
        }
        for (Class<?> iface : cl.getInterfaces()) {
            if (out.add(iface)) {
                collectAllInterfaces(iface, out);
            }
        }
        collectAllInterfaces(cl.getSuperclass(), out);
    }

    /**
     * Convenience non-activatable constructor for services with no
     * service-specific configuration entries.
     *
     * <p>This constructor eliminates the boilerplate public constructors that
     * every simple service previously had to copy-paste.  It creates a
     * {@link DefaultJiniServiceParameters} from the supplied arguments and
     * delegates to
     * {@link #AbstractJiniService(JiniServiceParameters, LifeCycle)}.
     *
     * <p>Services that need to read additional configuration entries must
     * supply a custom {@link JiniServiceParameters} subclass and use the
     * {@link #AbstractJiniService(JiniServiceParameters, LifeCycle)} constructor
     * directly.
     *
     * <h2>Usage</h2>
     * <pre>
     * public MyServiceImpl(String[] configArgs, LifeCycle lifeCycle)
     *         throws Exception {
     *     super(configArgs, lifeCycle, COMPONENT, MyService.class, MyServiceImpl.class);
     * }
     * </pre>
     *
     * @param configArgs       configuration arguments passed to
     *                         {@link ConfigurationProvider#getInstance}
     * @param lifeCycle        lifecycle callback; may be {@code null}
     * @param component        the configuration component name for this
     *                         service (e.g. {@code "com.example.myservice"})
     * @param serviceInterface the primary remote interface of the service;
     *                         used to build a default exporter and for
     *                         codebase fallback
     * @param serviceImpl      the concrete service implementation class (this
     *                         subclass's own {@code .class} literal); its derived
     *                         admin set (design decision D5) becomes the default
     *                         exporter's dispatch-only set so that any custom
     *                         non-{@code Remote} admin interface also dispatches
     * @throws Exception if configuration reading or parameter validation
     *                   fails
     */
    protected AbstractJiniService(String[] configArgs,
                                  LifeCycle lifeCycle,
                                  String component,
                                  Class<?> serviceInterface,
                                  Class<?> serviceImpl)
            throws Exception {
        this(new DefaultJiniServiceParameters(
                     ConfigurationProvider.getInstance(
                             configArgs,
                             serviceInterface.getClassLoader()),
                     component, null, serviceInterface, serviceImpl),
             lifeCycle);
    }

    /**
     * Convenience activatable constructor for services with no
     * service-specific configuration entries.
     *
     * <p>This constructor eliminates the boilerplate activatable constructor
     * that every simple service previously had to copy-paste.  It creates a
     * {@link DefaultJiniServiceParameters} from the supplied arguments and
     * delegates to
     * {@link #AbstractJiniService(JiniServiceParameters, LifeCycle)}.
     *
     * <p>Services that need to read additional configuration entries must
     * supply a custom {@link JiniServiceParameters} subclass and use the
     * {@link #AbstractJiniService(JiniServiceParameters, LifeCycle)} constructor
     * directly.
     *
     * <h2>Usage</h2>
     * <pre>
     * public MyServiceImpl(ActivationID activationID, String[] data)
     *         throws Exception {
     *     super(activationID, data, COMPONENT, MyService.class, MyServiceImpl.class);
     * }
     * </pre>
     *
     * @param activationID     the activation ID assigned by the Phoenix
     *                         activation system
     * @param data             configuration arguments passed to
     *                         {@link ConfigurationProvider#getInstance}
     * @param component        the configuration component name for this
     *                         service (e.g. {@code "com.example.myservice"})
     * @param serviceInterface the primary remote interface of the service;
     *                         used to build a default exporter and for
     *                         codebase fallback
     * @param serviceImpl      the concrete service implementation class (this
     *                         subclass's own {@code .class} literal); its derived
     *                         admin set (design decision D5) becomes the default
     *                         exporter's dispatch-only set so that any custom
     *                         non-{@code Remote} admin interface also dispatches
     * @throws Exception if configuration reading or parameter validation
     *                   fails
     */
    protected AbstractJiniService(ActivationID activationID,
                                  String[] data,
                                  String component,
                                  Class<?> serviceInterface,
                                  Class<?> serviceImpl)
            throws Exception {
        this(new DefaultJiniServiceParameters(
                     ConfigurationProvider.getInstance(
                             data,
                             serviceInterface.getClassLoader()),
                     component, activationID, serviceInterface, serviceImpl),
             null);
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

        // Pre-compute codebase digests for use by getCodebaseDigest() /
        // getDigestOffsets().  Failures are logged as a warning but do not
        // abort startup; the getters will simply return null in that case.
        try {
            CodebaseDigestUtil.Result dr =
                    CodebaseDigestUtil.compute(getClassAnnotation(), "SHA-256");
            if (dr != null) {
                codebaseDigestFlat      = dr.getFlatDigest();
                codebaseDigestOffsets   = dr.getOffsets();
                codebaseDigestAlgorithm = dr.getAlgorithm();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "{0}: could not pre-compute codebase digest — "
                    + "getCodebaseDigest() will return null",
                    getClass().getSimpleName());
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

        // Build the admin facet once (design decision D3): a separate proxy over
        // the SAME single JERI export as the thin service stub, carrying the admin
        // interfaces (JoinAdmin/DestroyAdmin/derived) that the service stub itself
        // deliberately does NOT expose.  Cached for getAdmin().
        adminProxy = createAdminProxy(stub, uuid);

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
     * Wraps the raw exported server stub in the service's client proxy.
     *
     * <p>The default implementation returns the {@code stub} unchanged — the
     * correct behaviour for a {@link au.net.zeus.jgdms.service.annotation.ProxyType#DYNAMIC}
     * service (JGDMS-STD-009 §6 shapes 1 &amp; 2), whose exported JERI stub is
     * itself the client proxy.
     *
     * <p>A {@link au.net.zeus.jgdms.service.annotation.ProxyType#SMART} service
     * overrides this to return its generated smart proxy — an instance of a class
     * that extends {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy}, passing
     * {@code stub} and {@code serviceUuid} to the superclass constructor so the
     * proxy carries the stable service UUID needed for correct {@code equals()} /
     * {@code hashCode()} behaviour and {@link net.jini.id.ReferentUuid} identity.
     *
     * @param stub        the exported server stub; never {@code null}
     * @param serviceUuid the stable unique identifier generated for this
     *                    service instance; never {@code null}
     * @return the proxy to advertise in lookup services; must be non-null
     */
    protected Object createProxy(Object stub, Uuid serviceUuid) {
        return stub;
    }

    /**
     * Returns the remote service (API) interfaces this service advertises.
     *
     * <p>Resolved once from the {@link JiniService @JiniService} annotation on the
     * concrete service class (see {@link #resolveServiceInterfaces()}) and cached:
     * if {@link JiniService#api()} is non-empty it is returned; otherwise the
     * interfaces are inferred from the class's implemented interfaces minus the
     * JGDMS infrastructure ones.  The concrete class (or an ancestor) MUST carry
     * {@code @JiniService}, or construction fails.
     *
     * <p>The first element is used as the fallback argument to
     * {@link CodebaseProvider#getClassAnnotation(Class)} when no explicit
     * codebase annotation has been configured.
     *
     * @return the service interface classes; non-null and non-empty
     */
    protected final Class<?>[] getServiceInterfaces() {
        return serviceInterfaces.clone();
    }

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
     * Returns the admin facet for this service — the canonical Jini
     * {@link Administrable} contract.
     *
     * <p>The returned proxy is a <em>separate</em> object from the service proxy:
     * it implements {@link JoinAdmin} (modifying the lookup-service groups,
     * locators, and attributes the service registers with), {@link DestroyAdmin}
     * (shutting the service down), any additional admin interface the concrete
     * class declares, and {@link net.jini.core.constraint.RemoteMethodControl}
     * (constrainable) — but it does NOT implement {@link Administrable} or the
     * bootstrap accessors.  Conversely, the service proxy itself is
     * {@link Administrable} but NOT {@code JoinAdmin}/{@code DestroyAdmin}.
     *
     * <p>The facet is built once in {@link #doStart()} (design decision D3) and
     * cached; this method simply performs a {@link ReadyState} check and returns
     * the cached instance.  It shares the service's single JERI export — the admin
     * methods still dispatch over that one export (see
     * {@link #createAdminProxy(Object, Uuid)}), so there is no second endpoint.
     *
     * @return the cached admin facet for this service
     * @throws RemoteException if the service has not been started
     */
    @Override
    public Object getAdmin() throws RemoteException {
        readyState.check();
        return adminProxy;
    }

    /**
     * Builds the admin facet: a proxy that carries the admin interfaces
     * ({@link #adminFacetInterfaces()}) which the thin service stub deliberately
     * does not expose, dispatching over the SAME single JERI export as the service
     * stub (design decision D2 — a FACET over the same server reference, NOT a
     * second export).
     *
     * <p>For the default {@link au.net.zeus.jgdms.service.annotation.ProxyType#DYNAMIC}
     * shape the exported {@code serverStub} is a {@link java.lang.reflect.Proxy}
     * whose invocation handler holds the {@link net.jini.jeri.ObjectEndpoint}.  This
     * method creates a second {@code Proxy} over that same handler (hence the same
     * endpoint) implementing the admin interfaces, then wraps it in the
     * {@code @AtomicSerial} {@link AdminProxy} (which returns its constrainable
     * variant because the facet implements {@code RemoteMethodControl}).  Because
     * the admin interfaces' methods were registered on the server invocation
     * dispatcher (they are in the exporter's {@code extra} set — only stripped from
     * the client stub's cast set), calls through the facet dispatch over the single
     * export.
     *
     * <p>When an {@code adminConstraints} config entry is present (design decision
     * D4) it is applied to the facet via
     * {@link net.jini.core.constraint.RemoteMethodControl#setConstraints}, letting
     * administration require stronger authentication than ordinary service calls
     * without a second endpoint.
     *
     * <p>This {@code protected} method is a documented seam: a future change could
     * override it to opt into a physically separate export for administration, and
     * a {@link au.net.zeus.jgdms.service.annotation.ProxyType#SMART} service whose
     * {@code serverStub} is not a dynamic {@code Proxy} may override it to build the
     * facet from its own server reference.
     *
     * @param serverStub the exported server stub (for DYNAMIC services a
     *                   {@link java.lang.reflect.Proxy}); never {@code null}
     * @param id         the stable service UUID; never {@code null}
     * @return the admin facet proxy, or {@code serverStub} unchanged if a facet
     *         could not be built (e.g. the stub is not a dynamic proxy and this
     *         method was not overridden)
     */
    protected Object createAdminProxy(Object serverStub, Uuid id) {
        if (serverStub == null || id == null
                || !Proxy.isProxyClass(serverStub.getClass())) {
            // Not a dynamic-proxy export we can re-facet; a SMART service that
            // needs an admin facet should override this method.
            return serverStub;
        }
        InvocationHandler handler = Proxy.getInvocationHandler(serverStub);
        ClassLoader cl = serverStub.getClass().getClassLoader();
        Remote facet = (Remote) Proxy.newProxyInstance(
                cl, adminFacetInterfaces(), handler);
        if (adminConstraints != null && facet instanceof RemoteMethodControl) {
            facet = (Remote) ((RemoteMethodControl) facet)
                    .setConstraints(adminConstraints);
        }
        return AdminProxy.create(facet, id, adminIfaces.clone());
    }

    /**
     * Returns the interface set the admin facet {@link java.lang.reflect.Proxy}
     * implements: {@link Remote} (so the facet is assignable to the {@code Remote}
     * field of {@link AdminProxy} — the admin interfaces themselves do not extend
     * {@code Remote}), {@link RemoteMethodControl} (constrainable), and the derived
     * admin interfaces ({@link #adminIfaces} from {@link #resolveAdminInterfaces()},
     * always {@link JoinAdmin} + {@link DestroyAdmin} + any custom admin interface).
     *
     * @return the admin facet's proxy interface set; never {@code null} or empty
     */
    protected final Class<?>[] adminFacetInterfaces() {
        Set<Class<?>> ifaces = new LinkedHashSet<>();
        ifaces.add(Remote.class);
        ifaces.add(RemoteMethodControl.class);
        for (Class<?> a : adminIfaces) {
            ifaces.add(a);
        }
        return ifaces.toArray(new Class<?>[0]);
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

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code "SHA-256"} once the codebase digest has been
     * successfully pre-computed during {@link #start()}, or {@code null} if
     * the annotation contained no JAR URLs or the computation failed.
     */
    @Override
    public final String getCodebaseDigestAlgorithm() throws IOException {
        return codebaseDigestAlgorithm;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the flat per-JAR digest array pre-computed during
     * {@link #start()}, or {@code null} if not available.
     */
    @Override
    public final byte[] getCodebaseDigest() throws IOException {
        byte[] d = codebaseDigestFlat;
        return d != null ? d.clone() : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the per-JAR byte offsets pre-computed during
     * {@link #start()}, or {@code null} if not available.
     */
    @Override
    public final int[] getDigestOffsets() throws IOException {
        int[] o = codebaseDigestOffsets;
        return o != null ? o.clone() : null;
    }
}
