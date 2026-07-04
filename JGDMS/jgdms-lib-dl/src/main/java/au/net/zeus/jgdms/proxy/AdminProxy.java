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
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.proxy.ConstrainableProxyUtil;

/**
 * Client-side administration proxy for JGDMS services built on
 * {@code AbstractJiniService}.
 *
 * <p>This proxy is returned by {@code AbstractJiniService.getAdmin()} and
 * implements both {@link JoinAdmin} (controlling which lookup services the
 * service registers with) and {@link DestroyAdmin} (shutting the service
 * down).  All methods delegate to the server stub, which must implement both
 * {@link JoinAdmin} and {@link DestroyAdmin}.
 *
 * <p>The static {@link #create(Remote, Uuid)} factory method automatically
 * returns a {@link ConstrainableAdminProxy} when the server stub implements
 * {@link RemoteMethodControl}, exactly mirroring the pattern used by all
 * other JGDMS service admin proxies.
 *
 * <p>The derived-set factory {@link #create(Remote, Uuid, Class[])} takes the admin
 * interface set a service derives from its implemented interfaces (always
 * {@link JoinAdmin} + {@link DestroyAdmin}, plus any custom admin interface): for
 * the common {@code {JoinAdmin, DestroyAdmin}} set it returns the fixed
 * {@link ConstrainableAdminProxy} (stable {@code @AtomicSerial} wire form); for a
 * larger set it returns a constrainable dynamic {@link java.lang.reflect.Proxy}
 * admin stub (see {@link DynamicAdminProxy}).  Both fail closed when the facet is
 * not a {@link RemoteMethodControl}.
 *
 * <h2>Typed transient fields</h2>
 * After validation, the {@code server} reference is also stored in two
 * transient, typed fields — {@code joinAdmin} and {@code destroyAdmin} — so
 * that every delegation call can be made without an explicit cast.
 *
 * <h2>Serialisation safety</h2>
 * The class is annotated {@link AtomicSerial}.  The {@link GetArg}-based
 * constructor validates that {@code server} implements {@link JoinAdmin} and
 * {@link DestroyAdmin} and that {@code proxyID} is non-null before any field
 * is assigned, satisfying the {@code @AtomicSerial} contract.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 */
@AtomicSerial
public class AdminProxy
        implements JoinAdmin, DestroyAdmin, Serializable, ReferentUuid, ProxyAccessor {

    private static final long serialVersionUID = 1L;

    /**
     * The server stub. Must implement {@link JoinAdmin} and {@link DestroyAdmin}.
     *
     * @serial
     */
    final Remote server;

    /**
     * The service UUID used for equality comparisons.
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Typed view of {@link #server} as {@link JoinAdmin}.
     * Transient — re-initialised in every constructor path.
     */
    transient final JoinAdmin joinAdmin;

    /**
     * Typed view of {@link #server} as {@link DestroyAdmin}.
     * Transient — re-initialised in every constructor path.
     */
    transient final DestroyAdmin destroyAdmin;

    /**
     * Serial form for the atomic/DER codecs. Mirrors the fields read by the
     * {@code (GetArg)} constructor; the {@code joinAdmin}/{@code destroyAdmin}
     * views are transient and rebuilt from {@code server}.
     */
    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("server", Remote.class),
            new SerialForm("proxyID", Uuid.class)
        };
    }

    /** {@code @AtomicSerial} write contract (required by the atomic write engine). */
    public static void serialize(PutArg arg, AdminProxy o) throws IOException {
        arg.put("server", o.server);
        arg.put("proxyID", o.proxyID);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Package-private helper: reflectively obtain a Method, throwing Error if absent
    // -------------------------------------------------------------------------

    static Method getMethod(Class<?> iface, String name, Class<?>... params) {
        try {
            return iface.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw (NoSuchMethodError) new NoSuchMethodError(e.getMessage()).initCause(e);
        }
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates an admin proxy, returning a {@link ConstrainableAdminProxy}
     * when the server stub also implements {@link RemoteMethodControl}.
     *
     * @param server  the server stub; must implement {@link JoinAdmin} and
     *                {@link DestroyAdmin}, and must be non-null
     * @param proxyID the service UUID; must be non-null
     * @return a new admin proxy instance
     * @throws IllegalArgumentException if {@code server} does not implement
     *         {@link JoinAdmin} or {@link DestroyAdmin}
     */
    public static AdminProxy create(Remote server, Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            // Preserve the stub's existing constraints (mirrors the generated smart
            // proxy create); passing null would call setConstraints(null) and discard
            // them.
            MethodConstraints mc = ((RemoteMethodControl) server).getConstraints();
            return new ConstrainableAdminProxy(server, proxyID, mc);
        }
        return new AdminProxy(server, proxyID);
    }

    /**
     * Creates an admin proxy for a caller-supplied set of admin interfaces.
     *
     * <p>This is the derived-set entry point used by
     * {@code AbstractJiniService}: the concrete service derives its admin
     * interface set (always {@link JoinAdmin} + {@link DestroyAdmin}, plus any
     * custom admin interface the implementation declares) and passes it here.
     *
     * <ul>
     *   <li>When {@code adminIfaces} is exactly {@code {JoinAdmin, DestroyAdmin}}
     *       (order-insensitive) the fixed {@link ConstrainableAdminProxy} is
     *       returned — its {@code @AtomicSerial} wire form is unchanged, so this is
     *       the stable common case.</li>
     *   <li>A larger set (a service declaring a custom non-{@code Remote} admin
     *       interface) is <strong>PARKED / fail-closed</strong>: see
     *       {@link DynamicAdminProxy} for why the dynamic multi-admin-interface wire
     *       form is not shipped, and this method throws
     *       {@link UnsupportedOperationException} rather than returning a proxy that
     *       cannot round-trip under the {@code @AtomicSerial}/Constrainable-only wire
     *       regime.</li>
     * </ul>
     *
     * <p>Fail-closed: {@code server} MUST implement {@link RemoteMethodControl}
     * (every JGDMS service stub exported through JERI does); otherwise an
     * {@link IllegalArgumentException} is thrown rather than degrading to a
     * non-constrainable proxy.
     *
     * @param server      the admin facet stub; must implement
     *                    {@link RemoteMethodControl} and every interface in
     *                    {@code adminIfaces}; must be non-null
     * @param proxyID     the service UUID; must be non-null
     * @param adminIfaces the derived admin interface set; must be non-null and
     *                    contain at least {@link JoinAdmin} and {@link DestroyAdmin}
     * @return a constrainable admin proxy
     * @throws IllegalArgumentException if {@code server} is not a
     *         {@link RemoteMethodControl}, or does not implement every interface in
     *         {@code adminIfaces}
     * @throws UnsupportedOperationException if {@code adminIfaces} is larger than
     *         exactly {@code {JoinAdmin, DestroyAdmin}} (the parked dynamic wire form)
     */
    public static Object create(Remote server, Uuid proxyID, Class<?>[] adminIfaces) {
        if (server == null) throw new IllegalArgumentException("server cannot be null");
        if (proxyID == null) throw new IllegalArgumentException("proxyID cannot be null");
        if (!(server instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                    "service must be exported with a constrainable endpoint: "
                    + "admin facet does not implement RemoteMethodControl");
        }
        if (adminIfaces == null) {
            throw new IllegalArgumentException("adminIfaces cannot be null");
        }
        for (Class<?> a : adminIfaces) {
            if (!a.isInstance(server)) {
                throw new IllegalArgumentException(
                        "admin facet must implement " + a.getName());
            }
        }
        if (isExactlyJoinAndDestroy(adminIfaces)) {
            // Common case: the stable fixed constrainable wire form.  Preserve the
            // facet's CURRENT constraints — e.g. a stronger adminConstraints (design
            // decision D4) that AbstractJiniService.createAdminProxy applied to the
            // facet — rather than clearing them by passing null (which would call
            // setConstraints(null) and silently discard the admin-only authentication).
            MethodConstraints mc = ((RemoteMethodControl) server).getConstraints();
            return new ConstrainableAdminProxy(server, proxyID, mc);
        }
        // PARKED (fail-closed): a larger admin set would need the dynamic
        // java.lang.reflect.Proxy admin form (DynamicAdminProxy), whose Serializable
        // (non-@AtomicSerial) handler has no sound wire form under
        // AtomicMarshalInputStream (the @AtomicSerial/Constrainable-only regime).
        // Rather than ship a proxy that cannot round-trip, refuse fail-closed until
        // the multi-admin-interface wire form is reviewed.  See DynamicAdminProxy.
        throw new UnsupportedOperationException(
                "admin interface sets larger than {JoinAdmin, DestroyAdmin} are not "
                + "yet supported: the dynamic admin proxy wire form is parked for "
                + "review (see AdminProxy.DynamicAdminProxy). Declared admin set: "
                + java.util.Arrays.toString(adminIfaces));
    }

    /**
     * Returns {@code true} iff {@code ifaces} is exactly the set
     * {@code {JoinAdmin, DestroyAdmin}} (order-insensitive, duplicates ignored).
     */
    private static boolean isExactlyJoinAndDestroy(Class<?>[] ifaces) {
        Set<Class<?>> set = new LinkedHashSet<>();
        for (Class<?> c : ifaces) {
            set.add(c);
        }
        return set.size() == 2
                && set.contains(JoinAdmin.class)
                && set.contains(DestroyAdmin.class);
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    AdminProxy(Remote server, Uuid proxyID) {
        this(checkArgs(server, proxyID), proxyID, false);
    }

    /**
     * Private raw constructor — fields are assigned without further
     * validation.  The {@code disambiguator} parameter exists solely to give
     * this constructor a distinct signature; its value is intentionally
     * ignored.
     */
    private AdminProxy(Remote server, Uuid proxyID, boolean disambiguator) {
        this.server = server;
        this.proxyID = proxyID;
        // These casts are always safe: every constructor path flows through
        // checkArgs() or checkFields() which verify that server implements both
        // JoinAdmin and DestroyAdmin before reaching this point.
        this.joinAdmin = (JoinAdmin) server;
        this.destroyAdmin = (DestroyAdmin) server;
    }

    /** AtomicSerial deserialization constructor. */
    AdminProxy(GetArg arg) throws IOException, ClassNotFoundException {
        this(checkFields(arg), (Uuid) arg.get("proxyID", null), false);
    }

    private static Remote checkArgs(Remote server, Uuid proxyID) {
        if (server == null) throw new IllegalArgumentException("server cannot be null");
        if (!(server instanceof JoinAdmin)) {
            throw new IllegalArgumentException("server must implement JoinAdmin");
        }
        if (!(server instanceof DestroyAdmin)) {
            throw new IllegalArgumentException("server must implement DestroyAdmin");
        }
        if (proxyID == null) throw new IllegalArgumentException("proxyID cannot be null");
        return server;
    }

    private static Remote checkFields(GetArg arg) throws IOException, ClassNotFoundException {
        Remote server = (Remote) arg.get("server", null);
        if (server == null) {
            throw new InvalidObjectException("server cannot be null");
        }
        if (!(server instanceof JoinAdmin)) {
            throw new InvalidObjectException("server must implement JoinAdmin");
        }
        if (!(server instanceof DestroyAdmin)) {
            throw new InvalidObjectException("server must implement DestroyAdmin");
        }
        if (arg.get("proxyID", null) == null) {
            throw new InvalidObjectException("proxyID cannot be null");
        }
        return server;
    }

    // -------------------------------------------------------------------------
    // JoinAdmin — delegates to transient joinAdmin (no cast needed)
    // -------------------------------------------------------------------------

    @Override
    public Entry[] getLookupAttributes() throws RemoteException {
        return joinAdmin.getLookupAttributes();
    }

    @Override
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        joinAdmin.addLookupAttributes(attrSets);
    }

    @Override
    public void modifyLookupAttributes(Entry[] attrSetTemplates, Entry[] attrSets)
            throws RemoteException {
        joinAdmin.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    @Override
    public String[] getLookupGroups() throws RemoteException {
        return joinAdmin.getLookupGroups();
    }

    @Override
    public void addLookupGroups(String[] groups) throws RemoteException {
        joinAdmin.addLookupGroups(groups);
    }

    @Override
    public void removeLookupGroups(String[] groups) throws RemoteException {
        joinAdmin.removeLookupGroups(groups);
    }

    @Override
    public void setLookupGroups(String[] groups) throws RemoteException {
        joinAdmin.setLookupGroups(groups);
    }

    @Override
    public LookupLocator[] getLookupLocators() throws RemoteException {
        return joinAdmin.getLookupLocators();
    }

    @Override
    public void addLookupLocators(LookupLocator[] locators) throws RemoteException {
        joinAdmin.addLookupLocators(locators);
    }

    @Override
    public void removeLookupLocators(LookupLocator[] locators) throws RemoteException {
        joinAdmin.removeLookupLocators(locators);
    }

    @Override
    public void setLookupLocators(LookupLocator[] locators) throws RemoteException {
        joinAdmin.setLookupLocators(locators);
    }

    // -------------------------------------------------------------------------
    // DestroyAdmin — delegates to transient destroyAdmin (no cast needed)
    // -------------------------------------------------------------------------

    @Override
    public void destroy() throws RemoteException {
        destroyAdmin.destroy();
    }

    // -------------------------------------------------------------------------
    // ReferentUuid / ProxyAccessor / equals / hashCode
    // -------------------------------------------------------------------------

    @Override
    public Uuid getReferentUuid() {
        return proxyID;
    }

    @Override
    public Object getProxy() {
        return server;
    }

    @Override
    public boolean equals(Object o) {
        return ReferentUuids.compare(this, o);
    }

    @Override
    public int hashCode() {
        return proxyID.hashCode();
    }

    // -------------------------------------------------------------------------
    // Constrainable inner class
    // -------------------------------------------------------------------------

    /**
     * Constrainable variant of the admin proxy, returned by
     * {@link AdminProxy#create} when the server stub implements
     * {@link RemoteMethodControl}.
     *
     * <p>Implements the full JERI constraint-translation pattern:
     * <ul>
     *   <li>{@link #setConstraints} translates caller-visible constraints to
     *       server-side method names via {@link #methodMapArray} and applies
     *       them to the underlying server stub.</li>
     *   <li>{@link #getConstraints} returns the caller-visible constraints
     *       (the logical view, not the translated view).</li>
     *   <li>The {@link GetArg}-based deserialization constructor calls
     *       {@link ConstrainableProxyUtil#verifyConsistentConstraints} to
     *       confirm that the constraints on the deserialized server stub are
     *       consistent with the stored {@code methodConstraints}.</li>
     * </ul>
     */
    @AtomicSerial
    @AtomicSerial.Stateless // methodConstraints is derived from server.getConstraints(), not a wire field
    static final class ConstrainableAdminProxy extends AdminProxy
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;
        
        /**
         * The client-visible method constraints placed on this proxy.
         * May be {@code null}, meaning all methods have empty constraints.
         *
         * @serial
         */
        private final MethodConstraints methodConstraints;

        /**
         * Constructs a new {@code ConstrainableAdminProxy}.
         *
         * @param server            the server stub; must implement
         *                          {@link RemoteMethodControl}, {@link JoinAdmin},
         *                          and {@link DestroyAdmin}
         * @param proxyID           the service UUID
         * @param methodConstraints the client-visible constraints (may be {@code null})
         */
        ConstrainableAdminProxy(Remote server, Uuid proxyID,
                                MethodConstraints methodConstraints) {
            super(constrainServer(checkConstrainable(server), methodConstraints), proxyID);
            this.methodConstraints = methodConstraints;
        }

        /** AtomicSerial deserialization constructor. */
        ConstrainableAdminProxy(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg, checkConstrainable(arg));
        }

        private ConstrainableAdminProxy(GetArg arg, MethodConstraints mc)
                throws IOException, ClassNotFoundException {
            super(arg);
            this.methodConstraints = mc;
        }

        /**
         * Validates the deserialized server stub and extracts/verifies the
         * method constraints.  Called before any field assignment.
         */
        private static MethodConstraints checkConstrainable(GetArg arg)
                throws IOException, ClassNotFoundException {
            Remote server = (Remote) arg.get("server", null);
            if (!(server instanceof RemoteMethodControl)) {
                throw new InvalidObjectException(
                        "server must implement RemoteMethodControl");
            }
            return ((RemoteMethodControl) server).getConstraints();
        }

        /** Pre-construction validation for direct (non-deserialization) path. */
        private static Remote checkConstrainable(Remote server) {
            if (!(server instanceof RemoteMethodControl)) {
                throw new IllegalArgumentException(
                        "server must implement RemoteMethodControl");
            }
            return server;
        }

        /**
         * Returns a copy of {@code server} with translated method constraints
         * applied.  Translating {@code null} constraints clears them.
         */
        private static Remote constrainServer(Remote server,
                                              MethodConstraints constraints) {
            return (Remote) ((RemoteMethodControl) server).setConstraints(constraints);
        }

        /**
         * Returns a new proxy with the specified client constraints.
         * The constraints are translated to server-method form via
         * {@link #methodMapArray} before being applied to the server stub.
         */
        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new ConstrainableAdminProxy(server, proxyID, constraints);
        }

        /**
         * Returns the client-visible constraints on this proxy, or
         * {@code null} if none have been set.
         */
        @Override
        public MethodConstraints getConstraints() {
            return methodConstraints;
        }

        /**
         * Returns a proxy trust iterator that yields this object's server.
         * Found reflectively by {@code BasicJeriTrustVerifier}.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(server);
        }
    }

    // -------------------------------------------------------------------------
    // Dynamic admin proxy — for admin interface sets larger than {JoinAdmin,
    // DestroyAdmin}
    // -------------------------------------------------------------------------

    /**
     * <strong>PARKED — NOT SHIPPED (unreached; retained for review).</strong>
     *
     * <p>This is the intended dynamic admin form for admin interface sets larger than
     * {@code {JoinAdmin, DestroyAdmin}} (a service that also declares a custom
     * non-{@code Remote} {@code FooAdmin}).  It is <em>not</em> reachable:
     * {@link AdminProxy#create(Remote, Uuid, Class[])} throws
     * {@link UnsupportedOperationException} for such sets rather than returning one of
     * these.  The reason is the wire form: the client-facing object is a
     * {@link java.lang.reflect.Proxy} whose {@link InvocationHandler} is this
     * <em>plain {@link Serializable}</em> (NOT {@code @AtomicSerial}) class.  Under
     * {@code AtomicMarshalInputStream} — the DER/atomic engine that is the only
     * sanctioned wire path in the {@code @AtomicSerial}/Constrainable-only regime —
     * such a handler has no sound reconstruction: it can only travel via the engine's
     * deprecated "best-effort" reflective {@code Serializable} path (which the regime
     * is removing), and on the write side, because this handler is a
     * {@link ProxyAccessor}, it is intercepted by the {@code ProxySerializer}
     * codebase-substitution mechanism intended for downloaded smart proxies, not for a
     * shared {@code @AtomicSerial} admin proxy.  Shipping it would weaken the
     * wire-format invariant, so it is fail-closed pending Peter's design review of a
     * constrainable, {@code @AtomicSerial} multi-admin-interface wire form.
     *
     * <p>A constrainable, {@link Serializable} delegating {@link InvocationHandler}
     * backing a {@link java.lang.reflect.Proxy} admin stub over a caller-supplied
     * admin interface set (used when the derived admin set is larger than
     * {@code {JoinAdmin, DestroyAdmin}} — e.g. a service that also implements a
     * custom {@code FooAdmin}).
     *
     * <p>The client-facing object is a {@code java.lang.reflect.Proxy} that
     * implements {@code adminIfaces} plus {@link RemoteMethodControl},
     * {@link ReferentUuid} and {@link ProxyAccessor}; every call delegates to
     * {@code server} — the admin facet, which already implements those interfaces
     * over the shared JERI export.  Identity is UUID-based (via {@code proxyID}),
     * matching {@link AdminProxy}.
     *
     * <p>The handler is {@link Serializable}: the client-side {@code Proxy}
     * serialises by writing this handler (which writes {@code server} and
     * {@code proxyID}) plus its interface list, and is reconstituted as an
     * equivalent {@code Proxy} on the receiver — no per-service admin proxy class
     * is required.  {@code adminIfaces} carries the interface set so the receiver
     * rebuilds the same shape.
     */
    static final class DynamicAdminProxy
            implements InvocationHandler, ReferentUuid, ProxyAccessor, Serializable {

        private static final long serialVersionUID = 1L;

        /** The admin facet stub; implements every interface in {@link #adminIfaces}
         *  plus {@link RemoteMethodControl}. */
        private final Remote server;
        /** The service UUID (identity). */
        private final Uuid proxyID;
        /** The admin interface set the client proxy exposes. */
        private final Class<?>[] adminIfaces;

        private DynamicAdminProxy(Remote server, Uuid proxyID, Class<?>[] adminIfaces) {
            this.server = server;
            this.proxyID = proxyID;
            this.adminIfaces = adminIfaces.clone();
        }

        /**
         * Builds the client-facing dynamic admin {@link java.lang.reflect.Proxy}.
         *
         * @param server      the admin facet stub (constrainable; implements every
         *                    interface in {@code adminIfaces})
         * @param proxyID     the service UUID
         * @param adminIfaces the admin interface set to expose
         * @return a {@code Proxy} over {@code adminIfaces} + {@code RemoteMethodControl}
         *         + {@code ReferentUuid} + {@code ProxyAccessor}
         */
        static Object create(Remote server, Uuid proxyID, Class<?>[] adminIfaces) {
            DynamicAdminProxy handler =
                    new DynamicAdminProxy(server, proxyID, adminIfaces);
            return Proxy.newProxyInstance(
                    handlerLoader(server, adminIfaces),
                    handler.proxyInterfaces(),
                    handler);
        }

        /**
         * The interfaces the client-facing {@code Proxy} implements: the admin set
         * plus the framework interfaces {@code RemoteMethodControl},
         * {@code ReferentUuid} and {@code ProxyAccessor}.
         */
        private Class<?>[] proxyInterfaces() {
            Set<Class<?>> set = new LinkedHashSet<>();
            for (Class<?> a : adminIfaces) {
                set.add(a);
            }
            set.add(RemoteMethodControl.class);
            set.add(ReferentUuid.class);
            set.add(ProxyAccessor.class);
            return set.toArray(new Class<?>[0]);
        }

        private static ClassLoader handlerLoader(Remote server, Class<?>[] adminIfaces) {
            ClassLoader cl = server.getClass().getClassLoader();
            return cl != null ? cl : DynamicAdminProxy.class.getClassLoader();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Class<?> decl = method.getDeclaringClass();
            String name = method.getName();
            // java.lang.Object methods
            if (decl == Object.class) {
                switch (name) {
                    case "hashCode": return proxyID.hashCode();
                    case "equals":   return ReferentUuids.compare(proxy, args[0]);
                    case "toString": return "DynamicAdminProxy[" + proxyID + " " + server + "]";
                    default:         return method.invoke(this, args);
                }
            }
            // ReferentUuid
            if (decl == ReferentUuid.class) {
                return proxyID;
            }
            // ProxyAccessor
            if (decl == ProxyAccessor.class) {
                return server;
            }
            // RemoteMethodControl.setConstraints returns a NEW admin proxy carrying
            // a re-constrained facet; getConstraints and other RMC methods delegate.
            if (decl == RemoteMethodControl.class && "setConstraints".equals(name)) {
                Remote reconstrained =
                        (Remote) ((RemoteMethodControl) server).setConstraints(
                                (MethodConstraints) args[0]);
                return create(reconstrained, proxyID, adminIfaces);
            }
            // All admin (and remaining RemoteMethodControl) methods delegate to the
            // facet, which implements them over the shared export.
            return method.invoke(server, args);
        }

        @Override
        public Uuid getReferentUuid() {
            return proxyID;
        }

        @Override
        public Object getProxy() {
            return server;
        }

        /**
         * Returns a proxy trust iterator that yields this handler's server.
         * Found reflectively by {@code BasicJeriTrustVerifier}.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(server);
        }
    }
}
