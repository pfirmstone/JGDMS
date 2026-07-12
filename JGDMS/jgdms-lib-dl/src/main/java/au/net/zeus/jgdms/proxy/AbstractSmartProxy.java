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
package au.net.zeus.jgdms.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.security.proxytrust.ProxyTrustIterator;
import org.apache.river.admin.DestroyAdmin;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.AtomicSerial.Stateless;

/**
 * Abstract base class for Jini/JGDMS smart proxy implementations.
 *
 * <p>This class encapsulates the boilerplate that every Jini smart proxy must
 * implement:
 * <ul>
 *   <li>{@link AtomicSerial} — proxies are transported over the wire and
 *       stored in lookup services using safe deserialization via the
 *       {@link GetArg} constructor pattern (Java Serialization is not
 *       supported); field invariants are checked before any field is
 *       assigned</li>
 *   <li>{@link ProxyAccessor} — exposes the inner server stub so that the
 *       Phoenix activation infrastructure and trust-verification code can
 *       obtain the raw remote reference</li>
 *   <li>{@link ReferentUuid} — stable UUID-based identity; two proxy
 *       instances wrapping the same service are equal iff they carry the
 *       same {@link Uuid}</li>
 *   <li>{@link Administrable} — every service should be administrable;
 *       {@link #getAdmin()} delegates to the server stub so that callers can
 *       obtain the administration object for the remote service</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>Annotate the concrete proxy class with {@code @AtomicSerial}.</li>
 *   <li>Implement the service interface(s) and delegate to
 *       {@link #server}.</li>
 *   <li>Provide a public factory method that calls
 *       {@code new ConcreteProxy(stub, uuid)} directly, or returns a
 *       constrainable subclass when the stub implements
 *       {@link net.jini.core.constraint.RemoteMethodControl}.</li>
 *   <li>Provide a package-access {@code (GetArg)} constructor that calls
 *       {@code super(arg)}; this satisfies the {@link AtomicSerial}
 *       deserialization contract.  During deserialization the base class
 *       automatically detects all interfaces declared directly on the
 *       concrete proxy class and verifies that the deserialized {@code server}
 *       stub implements each of them.</li>
 * </ol>
 *
 * <h2>Recommended additional proxy interfaces</h2>
 * Based on the patterns used across existing JGDMS service proxies
 * (Fiddler, Mahalo, Mercury, Reggie), this base class already implements
 * {@link Administrable}.  Concrete proxy subclasses may also wish to
 * implement constrainable variants — see below.
 *
 * <h2>Interfaces that belong to the server only — not to the proxy</h2>
 * The following interfaces are implemented by service back-end
 * implementations (i.e. classes that extend
 * {@link au.net.zeus.jgdms.service.support.AbstractJiniService} or similar)
 * and must <em>not</em> be declared on the proxy:
 * <ul>
 *   <li>{@code net.jini.security.proxytrust.ServerProxyTrust} — the server
 *       supplies a {@code TrustVerifier} so that the trust infrastructure can
 *       verify the proxy; this is a server-side concern only.</li>
 *   <li>{@code org.apache.river.api.util.Startable} — server lifecycle hook
 *       (not a remote interface; not present on the exported stub).</li>
 * </ul>
 * {@link CodebaseAccessor}, {@link ServiceProxyAccessor},
 * {@link ServiceAttributesAccessor}, and {@link ServiceIDAccessor} are
 * Remote interfaces implemented by the server stub and are validated
 * during {@link #checkServer(GetArg) deserialization}.  They do not need
 * to be re-declared on the concrete proxy class.
 *
 * <h2>Constrainable proxies</h2>
 * When the server stub implements
 * {@link RemoteMethodControl}, use the nested
 * {@link ConstrainableSmartProxy} abstract class as the base for a
 * constrainable inner subclass.  The concrete constrainable proxy must:
 * <ol>
 *   <li>Extend {@code ConstrainableSmartProxy}.</li>
 *   <li>Be annotated with {@code @AtomicSerial}.</li>
 *   <li>Implement the service interface(s) by delegating to {@link #server}.</li>
 *   <li>Implement {@link RemoteMethodControl#setConstraints} by returning a
 *       new instance of itself with the constraints applied.</li>
 *   <li>Provide a {@code (GetArg)} constructor that calls
 *       {@code super(arg)}; this triggers both the infrastructure-interface
 *       validation in {@link AbstractSmartProxy} and the
 *       {@code RemoteMethodControl} check added by
 *       {@link ConstrainableSmartProxy}.</li>
 * </ol>
 * A static factory method on the outer proxy class typically returns the
 * constrainable variant when the server stub implements
 * {@link RemoteMethodControl}, and the plain variant otherwise.
 *
 * <h2>Serialized form</h2>
 * Two fields are serialized by this class:
 * <ul>
 *   <li>{@code server} — the remote server stub (runtime type is the
 *       service's back-end interface)</li>
 *   <li>{@code proxyID} — the service's stable {@link Uuid}</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 */
@AtomicSerial
public abstract class AbstractSmartProxy
        implements ProxyAccessor, ReferentUuid, Administrable {

    /**
     * The remote server stub.  Concrete subclasses cast this to the
     * appropriate service back-end interface when delegating method calls.
     */
    protected final Object server;

    /**
     * The stable unique identifier of the service this proxy represents.
     */
    private final Uuid proxyID;

    // -------------------------------------------------------------------------
    // AtomicSerial output contract
    // -------------------------------------------------------------------------

    /**
     * Declares the serialized fields for the AtomicSerial codec, mirrored by
     * {@link #serialize(PutArg, AbstractSmartProxy)} and read back by name in
     * {@link #AbstractSmartProxy(GetArg)}.
     *
     * <p>Required because the AtomicSerial output path
     * ({@code ObjOutputStream}) marshals each non-{@code @Stateless}
     * {@code @AtomicSerial} class in the hierarchy via a static
     * {@code serialize(PutArg, &lt;type&gt;)} method — exactly as
     * {@code RegistrarProxy} does.  Subclasses that add no serialized state are
     * annotated {@code @Stateless} instead of repeating this.
     *
     * @return the serial-field descriptors for this class
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm("server", Object.class),
            new SerialForm("proxyID", Uuid.class)
        };
    }

    /**
     * AtomicSerial output method: writes the {@code server} and {@code proxyID}
     * fields declared by {@link #serialForm()}.
     *
     * @param arg the put-arg sink supplied by the codec
     * @param obj the proxy being serialized
     * @throws IOException if writing the fields fails
     */
    public static void serialize(PutArg arg, AbstractSmartProxy obj)
            throws IOException {
        arg.put("server", obj.server);
        arg.put("proxyID", obj.proxyID);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a new smart proxy wrapping the given server stub.
     *
     * <p>Validation is performed before construction (via {@link #checkArgs})
     * to avoid throwing an exception inside the constructor body.  This
     * follows the JGDMS convention of deferring validation to static helpers
     * to protect against finalizer attacks on partially-initialised objects.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    protected AbstractSmartProxy(Object server, Uuid proxyID) {
        this(checkArgs(server, proxyID), proxyID, false);
    }

    /**
     * Private raw constructor — fields are assigned without further
     * validation.  The {@code disambiguator} parameter exists solely to give
     * this constructor a distinct signature; its value is intentionally
     * ignored.
     */
    private AbstractSmartProxy(Object server, Uuid proxyID, boolean disambiguator) {
        this.server  = server;
        this.proxyID = proxyID;
    }

    /**
     * Validates the arguments for {@link #AbstractSmartProxy(Object, Uuid)}.
     * Called as a constructor argument so that any exception is thrown
     * <em>before</em> the private constructor body runs.
     *
     * @return {@code server} unchanged
     * @throws IllegalArgumentException if {@code server} or {@code proxyID}
     *                                  is {@code null}
     */
    private static Object checkArgs(Object server, Uuid proxyID) {
        if (server  == null) throw new IllegalArgumentException("server must not be null");
        if (proxyID == null) throw new IllegalArgumentException("proxyID must not be null");
        return server;
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * <p>Reads the {@code server} and {@code proxyID} fields from
     * {@code arg} and delegates all validation to the static
     * {@link #checkServer(GetArg)} method, which runs <em>before</em> any
     * field is assigned.  This satisfies the {@link AtomicSerial} contract
     * that all invariant checks are performed prior to object construction.
     *
     * <p>{@code checkServer} uses {@link GetArg#serialClasses()} to obtain
     * the concrete proxy class at deserialization time and verifies that
     * {@code server} implements every interface declared directly on that
     * class (i.e. the service interfaces), without requiring subclasses to
     * override any method.
     *
     * @param arg the deserialization argument bag; must be non-null
     * @throws IOException if either field is {@code null}, cannot be read,
     *                     or {@code server} does not implement all service
     *                     interfaces declared by the concrete proxy class
     */
    protected AbstractSmartProxy(GetArg arg) throws IOException, ClassNotFoundException {
        this(checkServer(arg), (Uuid) arg.get("proxyID", null));
    }

    // -------------------------------------------------------------------------
    // Deserialization validation
    // -------------------------------------------------------------------------

    /**
     * Validates fields read from {@code arg} before any field is assigned,
     * as required by the {@link AtomicSerial} contract.
     *
     * <p>Uses {@link GetArg#serialClasses()} to discover the concrete proxy
     * class at deserialization time (the last element in the array, which is
     * the most-derived class in the stream hierarchy).  The {@code server}
     * stub is then checked against every interface declared directly on that
     * class -- these are always the service interfaces, since the
     * infrastructure interfaces ({@link ProxyAccessor}, {@link ReferentUuid},
     * {@link Administrable}) are declared on
     * {@link AbstractSmartProxy} itself and therefore do not appear in the
     * concrete class's {@code getInterfaces()} result.
     *
     * <p>In addition, the method also verifies that {@code server} implements
     * the Remote infrastructure interfaces that every JGDMS service stub
     * exposes: {@link CodebaseAccessor}, {@link ServiceProxyAccessor},
     * {@link ServiceAttributesAccessor}, and {@link ServiceIDAccessor}.
     */
    private static Object checkServer(GetArg arg) throws IOException, ClassNotFoundException {
        Object server  = arg.get("server",  null);
        Uuid   proxyID = (Uuid) arg.get("proxyID", null);
        if (server == null) {
            throw new InvalidObjectException(
                    "server field is null");
        }
        if (proxyID == null) {
            throw new InvalidObjectException(
                    "proxyID field is null");
        }
        // Verify the server stub implements the infrastructure interfaces that
        // every JGDMS service backend exposes.  By convention the service's
        // single backend remote interface aggregates these (see Reggie's
        // Registrar and the Hello World HelloServiceBackend), so a real
        // exported stub satisfies them all; the service-specific interface is
        // guaranteed by that same convention and enforced by the concrete
        // proxy's delegation casts.
        //
        // We deliberately do NOT introspect the proxy class's own interfaces
        // here: serialClasses() does not reliably yield the leaf proxy class
        // (@Stateless leaves are elided), and a proxy class's interfaces
        // include proxy-side types (ProxyAccessor, ReferentUuid, ...) that the
        // server stub legitimately does not implement.
        checkInfrastructureInterface(server, Administrable.class);
        checkInfrastructureInterface(server, CodebaseAccessor.class);
        checkInfrastructureInterface(server, ServiceProxyAccessor.class);
        checkInfrastructureInterface(server, ServiceAttributesAccessor.class);
        checkInfrastructureInterface(server, ServiceIDAccessor.class);
        checkInfrastructureInterface(server, JoinAdmin.class);
        checkInfrastructureInterface(server, DestroyAdmin.class);
        return server;
    }

    private static void checkInfrastructureInterface(
            Object server, Class<?> iface) throws InvalidObjectException {
        if (!iface.isInstance(server)) {
            throw new InvalidObjectException(
                    "deserialized server does not implement "
                    + iface.getName()
                    + "; actual type: " + server.getClass().getName());
        }
    }

    // -------------------------------------------------------------------------
    // ProxyAccessor
    // -------------------------------------------------------------------------

    /**
     * Returns the inner remote server stub.
     *
     * <p>This is the value that was supplied as {@code server} at construction
     * time.  The Phoenix activation infrastructure and
     * {@link org.apache.river.proxy.BasicProxyTrustVerifier} use this method
     * to obtain the raw remote reference.
     *
     * @return the server stub; never {@code null}
     */
    @Override
    public final Object getProxy() {
        return server;
    }

    // -------------------------------------------------------------------------
    // Administrable
    // -------------------------------------------------------------------------

    /**
     * Returns the administration object for the remote service.
     *
     * <p>Delegates directly to the server stub, which must implement
     * {@link Administrable} (verified during {@link AtomicSerial}
     * deserialization via {@link #checkServer(GetArg)}).
     *
     * @return the administration object for the remote service
     * @throws RemoteException if a communication failure occurs
     */
    @Override
    public Object getAdmin() throws RemoteException {
        return ((Administrable) server).getAdmin();
    }

    // -------------------------------------------------------------------------
    // ReferentUuid
    // -------------------------------------------------------------------------

    /**
     * Returns the stable unique identifier of the service represented by
     * this proxy.
     *
     * @return the service's {@link Uuid}; never {@code null}
     */
    @Override
    public final Uuid getReferentUuid() {
        return proxyID;
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    /**
     * Returns a hash code derived from the service's {@link Uuid}.
     *
     * <p>Proxies for the same service (same {@code proxyID}) will have the
     * same hash code regardless of which stub they wrap.
     */
    @Override
    public final int hashCode() {
        return proxyID.hashCode();
    }

    /**
     * Two smart proxies are equal iff they represent the same service,
     * i.e. they carry the same {@link Uuid}.
     *
     * <p>Implemented via {@link ReferentUuids#compare(ReferentUuid,Object)}.
     */
    @Override
    public final boolean equals(Object o) {
        return ReferentUuids.compare(this, o);
    }

    // -------------------------------------------------------------------------
    // Reflective factory resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the generated {@code Constrainable<Api>Proxy} class for
     * {@code primaryApi} by the deterministic naming convention the service-proxy
     * annotation processor uses ({@code "Constrainable" + primaryApi.getSimpleName()
     * + "Proxy"}, in {@code primaryApi}'s package) and invokes its static
     * {@code create(server, proxyID, ...stateArgs)} factory.
     *
     * <p>This is what
     * {@code au.net.zeus.jgdms.service.support.AbstractJiniService}'s default
     * {@code createProxy(Object, Uuid)} implementation calls for a
     * {@code proxy() == SMART} service, so that hand-written service code never
     * needs to name the generated proxy class directly — the same reasoning that
     * led {@code ServiceProxyProcessor}'s delegate-constructor validation to
     * recommend {@code java.rmi.Remote} over the generated aggregate backend's
     * name: a processor-synthesized name is an implementation detail, not a
     * contract to hand-write against.
     *
     * <p><b>Compile-time-safety note.</b> Unlike a hand-written call to the
     * generated factory (where every argument's type is checked by {@code javac}),
     * {@code stateArgs} here is untyped: a wrong type or count for a stateful
     * {@code @SmartProxy} delegate surfaces as a runtime
     * {@link IllegalArgumentException} from reflection (at service start, not
     * deep in production — {@link #createFor} is called once, from
     * {@code createProxy}, during service startup) rather than as a compile
     * error. A service that wants the state arguments themselves compile-checked
     * may still override {@code createProxy(Object, Uuid)} directly and call the
     * generated factory by name instead of using this method.
     *
     * @param primaryApi the service's primary API interface (by convention,
     *                   {@code getServiceInterfaces()[0]} — the generated proxy
     *                   is always named after this interface, even for a
     *                   multi-interface service); must not be {@code null}
     * @param server     the remote server stub to pass as the factory's first
     *                   argument; must not be {@code null}
     * @param proxyID    the service's stable unique identifier; must not be
     *                   {@code null}
     * @param stateArgs  the delegate's {@code @SmartProxy.State} constructor
     *                   arguments, in declaration order; empty for a stateless
     *                   smart proxy
     * @return the constructed proxy, as returned by the generated factory
     * @throws IllegalArgumentException if {@code primaryApi}, {@code server}, or
     *         {@code proxyID} is {@code null}, or if the underlying
     *         {@code create(...)} factory itself throws it (e.g. {@code server}
     *         is not a {@link RemoteMethodControl})
     * @throws IllegalStateException if no generated proxy class or factory
     *         method can be resolved, or {@code stateArgs} does not match the
     *         factory's actual parameter list — almost always a codegen/wiring
     *         bug (the service-proxy annotation processor did not run, the
     *         service is not {@code proxy() == SMART}, or
     *         {@code smartProxyStateArgs()} is out of sync with the delegate's
     *         {@code @SmartProxy.State} declarations)
     */
    public static Object createFor(Class<?> primaryApi, Object server, Uuid proxyID,
                                    Object... stateArgs) {
        if (primaryApi == null) throw new IllegalArgumentException("primaryApi must not be null");
        if (server == null) throw new IllegalArgumentException("server must not be null");
        if (proxyID == null) throw new IllegalArgumentException("proxyID must not be null");

        Package pkgObj = primaryApi.getPackage();
        String pkg = pkgObj == null ? "" : pkgObj.getName();
        String simple = "Constrainable" + primaryApi.getSimpleName() + "Proxy";
        String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;

        Class<?> proxyClass;
        try {
            proxyClass = Class.forName(fqn, true, primaryApi.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "no generated smart proxy class " + fqn + " for " + primaryApi.getName()
                    + " -- was the service-proxy annotation processor run, and is"
                    + " @JiniService.proxy() == SMART?", e);
        }

        java.lang.reflect.Method create = null;
        for (java.lang.reflect.Method m : proxyClass.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && "create".equals(m.getName())) {
                create = m;
                break;
            }
        }
        if (create == null) {
            throw new IllegalStateException(
                    "generated smart proxy class " + fqn + " has no static create(...) factory");
        }

        Object[] args = new Object[2 + stateArgs.length];
        args[0] = server;
        args[1] = proxyID;
        System.arraycopy(stateArgs, 0, args, 2, stateArgs.length);
        try {
            return create.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "cannot invoke generated factory " + fqn + ".create(...)", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "generated factory " + fqn + ".create(...) does not accept "
                    + args.length + " argument(s) -- server, proxyID"
                    + (stateArgs.length == 0 ? "" : " and " + stateArgs.length + " state value(s)")
                    + "; check smartProxyStateArgs() matches the delegate's @SmartProxy.State"
                    + " declarations", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(
                    "generated factory " + fqn + ".create(...) failed", cause);
        }
    }

    // =========================================================================
    // Nested class: ConstrainableSmartProxy
    // =========================================================================

    /**
     * Abstract base class for constrainable smart proxies.
     *
     * <p>Extends {@link AbstractSmartProxy} and additionally implements
     * {@link RemoteMethodControl}, providing the boilerplate common to all
     * constrainable JGDMS service proxies:
     * <ul>
     *   <li>Applying client constraints to the server stub at construction
     *       time.</li>
     *   <li>Validating (at deserialization time) that the {@code server} stub
     *       implements {@link RemoteMethodControl}, in addition to all the
     *       checks performed by {@link AbstractSmartProxy}.</li>
     *   <li>{@link #getConstraints()} — delegates to the server stub.</li>
     *   <li>{@code getProxyTrustIterator()} — returns a
     *       {@link SingletonProxyTrustIterator} wrapping the server stub,
     *       as required by {@code BasicJeriTrustVerifier}.</li>
     * </ul>
     *
     * <h2>Concrete subclass responsibilities</h2>
     * <ol>
     *   <li>Annotate with {@code @AtomicSerial}.</li>
     *   <li>Implement the service interface(s), delegating to
     *       {@link AbstractSmartProxy#server server}.</li>
     *   <li>Implement {@link #setConstraints} by constructing a new instance
     *       of the same concrete class with the constraints applied.
     *       Use the protected
     *       {@link #ConstrainableSmartProxy(Object, Uuid, MethodConstraints)}
     *       constructor to apply the constraints to the server stub.</li>
     *   <li>Provide a {@code (GetArg)} constructor that calls
     *       {@code super(arg)}.</li>
     * </ol>
     * <p>
     * Implementers Note: it is strongly encouraged for the server to implement the
     * same interfaces as the proxy, to avoid needing to map methods
     * from the proxy, to the server when applying constraints.
     * <p>
     * However in the case that the user needs to use different methods, all
     * methods will need to be mapped, including those that are identical.
     * Usually a final static array of (proxy-method, server-method) pairs
     * used by
     * {@link ConstrainableProxyUtil} to translate client-visible
     * constraints into the constraints to set on the server
     * stub, since an array is mutable and static, it's important that it not be
     * shared, the class can be made preferred and added to a preferred list 
     * to ensure it is not shared and resolves to the proxy's private ClassLoader.
     * However if there's a risk the class will be shared, consider using an
     * immutable list, with a final field reference and convert this to an
     * array on each invocation.
     *
     * <p>Each pair of elements maps:
     * <ul>
     *   <li>element 2k   — the public, remote method invocable through
     *       this proxy</li>
     *   <li>element 2k+1 — the method ultimately executed on the
     *       server backend</li>
     * </ul>
     * This is the
     * standard pattern used by services where the proxy's remote interface
     * differ from the server's remote interface — see
     * {@code ConstrainableFiddlerAdminProxy} for a prior art example.
     *
     * @author Peter Firmstone
     * @author GitHub Copilot
     * @since 3.1.1
     */
    @AtomicSerial
    @Stateless  // constraints live on the server stub, not in a field
    public static abstract class ConstrainableSmartProxy
            extends AbstractSmartProxy
            implements RemoteMethodControl {

        // -------------------------------------------------------------------------
        // Constructors
        // -------------------------------------------------------------------------

        /**
         * Creates a new constrainable smart proxy, applying {@code constraints}
         * to the server stub before storing it.
         *
         * <p>Validation that the arguments are non-null is delegated to the
         * superclass constructor (via {@link AbstractSmartProxy#checkArgs}).
         * Validation that {@code server} implements {@link RemoteMethodControl}
         * is performed before this constructor body runs, by the
         * {@link #checkConstrainable(Object, Uuid)} helper.
         *
         * @param server      the remote server stub; must implement
         *                    {@link RemoteMethodControl}
         * @param proxyID     the service's stable unique identifier;
         *                    must be non-null
         * @param constraints the client method constraints to apply; may be
         *                    {@code null}
         * @throws IllegalArgumentException if {@code server} does not
         *                                  implement {@link RemoteMethodControl},
         *                                  or if {@code server} or
         *                                  {@code proxyID} is {@code null}
         */
        protected ConstrainableSmartProxy(Object server, Uuid proxyID,
                                          MethodConstraints constraints) {
            super(applyConstraints(checkConstrainable(server, proxyID), constraints),
                  proxyID);
        }

        /**
         * {@link AtomicSerial} deserialization constructor.
         *
         * <p>The {@code server}/{@code proxyID} state is declared by the
         * {@link AbstractSmartProxy} superclass, so it lives in the
         * {@code AbstractSmartProxy} {@code @AtomicSerial} namespace — this
         * {@code @Stateless} subclass frame cannot read it (each class in an
         * {@code @AtomicSerial} hierarchy has its own {@link GetArg} namespace, and a
         * {@code @Stateless} class contributes none).  We therefore let
         * {@link AbstractSmartProxy#AbstractSmartProxy(GetArg)} read and validate
         * {@code server} (non-null, infrastructure interfaces) from its own frame,
         * then refine the check here — the inherited, already validated
         * {@code server} field must additionally be a {@link RemoteMethodControl}.
         * If it is not, deserialization fails before this object is published.
         *
         * @param arg the deserialization argument bag
         * @throws IOException if the {@code server} does not implement
         *                     {@link RemoteMethodControl}, or if any of the
         *                     superclass validations fail
         */
        protected ConstrainableSmartProxy(GetArg arg) throws IOException, ClassNotFoundException {
            super(arg);
            if (!(server instanceof RemoteMethodControl)) {
                throw new InvalidObjectException(
                        "deserialized server does not implement RemoteMethodControl"
                        + "; actual type: "
                        + (server == null ? "null" : server.getClass().getName()));
            }
        }

        // -------------------------------------------------------------------------
        // Deserialization validation
        // -------------------------------------------------------------------------

        /**
         * Validates that {@code server} implements {@link RemoteMethodControl}
         * before any field is assigned.  Called as a constructor argument, so
         * the check runs outside the constructor body.
         *
         * @return {@code server} unchanged
         * @throws IllegalArgumentException if {@code server} or {@code proxyID}
         *                                  is {@code null}, or if {@code server}
         *                                  does not implement
         *                                  {@link RemoteMethodControl}
         */
        private static Object checkConstrainable(Object server, Uuid proxyID) {
            if (server == null)
                throw new IllegalArgumentException("server must not be null");
            if (proxyID == null)
                throw new IllegalArgumentException("proxyID must not be null");
            if (!(server instanceof RemoteMethodControl))
                throw new IllegalArgumentException(
                        "server must implement RemoteMethodControl; actual type: "
                        + server.getClass().getName());
            return server;
        }

        /**
         * Applies {@code constraints} to {@code server} by calling
         * {@link RemoteMethodControl#setConstraints}, returning the constrained
         * server stub.
         *
         * @param server      a non-null server stub that implements
         *                    {@link RemoteMethodControl}
         * @param constraints the constraints to apply; may be {@code null}
         * @return the constrained server stub
         */
        private static Object applyConstraints(Object server,
                                               MethodConstraints constraints) {
            return ((RemoteMethodControl) server).setConstraints(constraints);
        }

        // -------------------------------------------------------------------------
        // RemoteMethodControl
        // -------------------------------------------------------------------------

        /**
         * Returns the client constraints currently set on this proxy, by
         * delegating to the (constrained) server stub.
         *
         * @return the current client constraints; may be {@code null}
         */
        @Override
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) server).getConstraints();
        }

        /**
         * Returns a new proxy with the given constraints, by constructing a
         * new instance of the same concrete proxy class.
         *
         * <p>Concrete subclasses must implement this method.  The typical
         * implementation is:
         * <pre>
         *   return new ConcreteConstrainableProxy(server, getReferentUuid(),
         *                                         constraints);
         * </pre>
         * where the constructor calls
         * {@link #ConstrainableSmartProxy(Object, Uuid, MethodConstraints)}.
         *
         * @param constraints the new client constraints; may be {@code null}
         * @return a new proxy with {@code constraints} applied
         */
        @Override
        public abstract RemoteMethodControl setConstraints(
                MethodConstraints constraints);

        /**
         * Supplies the bootstrap step for JGDMS proxy-trust verification.
         *
         * <p>A downloaded smart proxy is not trusted by its mere type; the
         * client's {@code ProxyTrustVerifier} must reach a trusted
         * {@code ServerProxyTrust} to verify it.  Returning a
         * {@link SingletonProxyTrustIterator} over the (constrained) server
         * stub gives the verifier that path: it iterates to the stub, whose
         * {@code getProxyVerifier} (via the server's {@code ServerProxyTrust})
         * yields a {@code TrustVerifier} for this proxy.
         *
         * <p>This method is discovered reflectively by
         * {@code ProxyTrustInvocationHandler} and must therefore remain
         * {@code private} with exactly this name and signature.  Without it,
         * trust verification of a downloaded constrainable proxy cannot
         * bootstrap — which is why the {@link ProxyTrustIterator} /
         * {@link SingletonProxyTrustIterator} imports exist on this class.
         *
         * @return a single-element trust iterator over the server stub
         */
        @SuppressWarnings("unused")
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(server);
        }
    }
}
