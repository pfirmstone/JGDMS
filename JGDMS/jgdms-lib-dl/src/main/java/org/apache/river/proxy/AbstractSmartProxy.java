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
package org.apache.river.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
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
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Abstract base class for Jini/JGDMS smart proxy implementations.
 *
 * <p>This class encapsulates the boilerplate that every Jini smart proxy must
 * implement:
 * <ul>
 *   <li>{@link Serializable} — proxies are transported over the wire and
 *       stored in lookup services</li>
 *   <li>{@link AtomicSerial} — safe deserialization using the
 *       {@link GetArg} constructor pattern; field invariants are checked
 *       before any field is assigned</li>
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
 * {@link org.apache.river.service.support.AbstractJiniService} or similar)
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
        implements Serializable, ProxyAccessor, ReferentUuid, Administrable {

    private static final long serialVersionUID = 1L;

    /**
     * The remote server stub.  Concrete subclasses cast this to the
     * appropriate service back-end interface when delegating method calls.
     *
     * @serial
     */
    protected final Object server;

    /**
     * The stable unique identifier of the service this proxy represents.
     *
     * @serial
     */
    private final Uuid proxyID;

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
    protected AbstractSmartProxy(GetArg arg) throws IOException {
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
     * infrastructure interfaces ({@link Serializable}, {@link ProxyAccessor},
     * {@link ReferentUuid}, {@link Administrable}) are declared on
     * {@link AbstractSmartProxy} itself and therefore do not appear in the
     * concrete class's {@code getInterfaces()} result.
     *
     * <p>In addition, the method also verifies that {@code server} implements
     * the Remote infrastructure interfaces that every JGDMS service stub
     * exposes: {@link CodebaseAccessor}, {@link ServiceProxyAccessor},
     * {@link ServiceAttributesAccessor}, and {@link ServiceIDAccessor}.
     */
    private static Object checkServer(GetArg arg) throws IOException {
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
        // Verify the server stub implements each service interface declared
        // directly on the concrete proxy class.
        Class<?>[] classes = arg.serialClasses();
        if (classes != null && classes.length > 0) {
            Class<?> concreteClass = classes[classes.length - 1];
            for (Class<?> iface : concreteClass.getInterfaces()) {
                if (!iface.isInstance(server)) {
                    throw new InvalidObjectException(
                            "deserialized server does not implement "
                            + iface.getName()
                            + "; actual type: " + server.getClass().getName());
                }
            }
        }
        // Verify the server stub implements the Remote infrastructure
        // interfaces that every JGDMS service (built on AbstractJiniService)
        // exports.
        checkInfrastructureInterface(server, Administrable.class);
        checkInfrastructureInterface(server, CodebaseAccessor.class);
        checkInfrastructureInterface(server, ServiceProxyAccessor.class);
        checkInfrastructureInterface(server, ServiceAttributesAccessor.class);
        checkInfrastructureInterface(server, ServiceIDAccessor.class);
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

    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException(
                "no data found when deserializing " + getClass().getName());
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
     *
     * @author Peter Firmstone
     * @author GitHub Copilot
     * @since 3.1.1
     */
    @AtomicSerial
    public static abstract class ConstrainableSmartProxy
            extends AbstractSmartProxy
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;

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
         * <p>Validates that the deserialized {@code server} implements
         * {@link RemoteMethodControl} <em>before</em> delegating to
         * {@link AbstractSmartProxy#AbstractSmartProxy(GetArg)}, which performs
         * all infrastructure-interface checks.  Both validations run before any
         * field is assigned, satisfying the {@link AtomicSerial} contract.
         *
         * @param arg the deserialization argument bag
         * @throws IOException if the {@code server} does not implement
         *                     {@link RemoteMethodControl}, or if any of the
         *                     superclass validations fail
         */
        protected ConstrainableSmartProxy(GetArg arg) throws IOException {
            super(checkConstrainable(arg));
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
         * Validates that the deserialized {@code server} field implements
         * {@link RemoteMethodControl} before passing {@code arg} to the
         * superclass deserialization constructor.
         *
         * @return {@code arg} unchanged
         * @throws InvalidObjectException if the server does not implement
         *                                {@link RemoteMethodControl}
         */
        private static GetArg checkConstrainable(GetArg arg) throws IOException {
            Object server = arg.get("server", null);
            if (!(server instanceof RemoteMethodControl)) {
                throw new InvalidObjectException(
                        "deserialized server does not implement RemoteMethodControl"
                        + "; actual type: "
                        + (server == null ? "null" : server.getClass().getName()));
            }
            return arg;
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

        // -------------------------------------------------------------------------
        // ProxyTrust support
        // -------------------------------------------------------------------------

        /**
         * Returns a {@link ProxyTrustIterator} containing the server stub.
         *
         * <p>This method is called reflectively by
         * {@code BasicJeriTrustVerifier} to obtain the trust verifier for this
         * proxy.  It must remain {@code private} so that it is found via
         * reflection but cannot be called directly by client code.
         *
         * @return a singleton iterator wrapping the server stub
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(server);
        }
    }
}
