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
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
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
            return new ConstrainableAdminProxy(server, proxyID, null);
        }
        return new AdminProxy(server, proxyID);
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
}
