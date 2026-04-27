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

/**
 * Client-side administration proxy for JGDMS services built on
 * {@code AbstractJiniService}.
 *
 * <p>This proxy is returned by {@code AbstractJiniService.getAdmin()} and
 * implements both {@link JoinAdmin} (controlling which lookup services the
 * service registers with) and {@link DestroyAdmin} (shutting the service
 * down).  All methods delegate to the server stub, which is typed as
 * {@link JiniServiceServer}.
 *
 * <p>The static {@link #create(JiniServiceServer, Uuid)} factory method
 * automatically returns a {@link ConstrainableAdminProxy} when the server
 * stub implements {@link RemoteMethodControl}, exactly mirroring the pattern
 * used by all other JGDMS service admin proxies.
 *
 * <h2>Serialisation safety</h2>
 * The class is annotated {@link AtomicSerial}.  The {@link GetArg}-based
 * constructor validates that {@code server} and {@code proxyID} are non-null
 * before any field is assigned, satisfying the {@code @AtomicSerial} contract.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 * @since 3.1.1
 * @see JiniServiceServer
 */
@AtomicSerial
public class AbstractJiniServiceAdminProxy
        implements JoinAdmin, DestroyAdmin, Serializable, ReferentUuid, ProxyAccessor {

    private static final long serialVersionUID = 1L;

    /**
     * The server stub.
     *
     * @serial
     */
    final JiniServiceServer server;

    /**
     * The service UUID used for equality comparisons.
     *
     * @serial
     */
    final Uuid proxyID;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates an admin proxy, returning a {@link ConstrainableAdminProxy}
     * when the server stub also implements {@link RemoteMethodControl}.
     *
     * @param server  the server stub; must be non-null
     * @param proxyID the service UUID; must be non-null
     * @return a new admin proxy instance
     */
    public static AbstractJiniServiceAdminProxy create(JiniServiceServer server,
                                                       Uuid proxyID) {
        if (server instanceof RemoteMethodControl) {
            return new ConstrainableAdminProxy(server, proxyID);
        }
        return new AbstractJiniServiceAdminProxy(server, proxyID);
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    AbstractJiniServiceAdminProxy(JiniServiceServer server, Uuid proxyID) {
        if (server == null) throw new IllegalArgumentException("server cannot be null");
        if (proxyID == null) throw new IllegalArgumentException("proxyID cannot be null");
        this.server = server;
        this.proxyID = proxyID;
    }

    /** AtomicSerial deserialization constructor. */
    AbstractJiniServiceAdminProxy(GetArg arg) throws IOException {
        this(checkFields(arg), (Uuid) arg.get("proxyID", null));
    }

    private static JiniServiceServer checkFields(GetArg arg) throws IOException {
        JiniServiceServer server = (JiniServiceServer) arg.get("server", null);
        if (server == null) {
            throw new InvalidObjectException("server cannot be null");
        }
        if (arg.get("proxyID", null) == null) {
            throw new InvalidObjectException("proxyID cannot be null");
        }
        return server;
    }

    // -------------------------------------------------------------------------
    // JoinAdmin
    // -------------------------------------------------------------------------

    @Override
    public Entry[] getLookupAttributes() throws RemoteException {
        return server.getLookupAttributes();
    }

    @Override
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        server.addLookupAttributes(attrSets);
    }

    @Override
    public void modifyLookupAttributes(Entry[] attrSetTemplates, Entry[] attrSets)
            throws RemoteException {
        server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    @Override
    public String[] getLookupGroups() throws RemoteException {
        return server.getLookupGroups();
    }

    @Override
    public void addLookupGroups(String[] groups) throws RemoteException {
        server.addLookupGroups(groups);
    }

    @Override
    public void removeLookupGroups(String[] groups) throws RemoteException {
        server.removeLookupGroups(groups);
    }

    @Override
    public void setLookupGroups(String[] groups) throws RemoteException {
        server.setLookupGroups(groups);
    }

    @Override
    public LookupLocator[] getLookupLocators() throws RemoteException {
        return server.getLookupLocators();
    }

    @Override
    public void addLookupLocators(LookupLocator[] locators) throws RemoteException {
        server.addLookupLocators(locators);
    }

    @Override
    public void removeLookupLocators(LookupLocator[] locators) throws RemoteException {
        server.removeLookupLocators(locators);
    }

    @Override
    public void setLookupLocators(LookupLocator[] locators) throws RemoteException {
        server.setLookupLocators(locators);
    }

    // -------------------------------------------------------------------------
    // DestroyAdmin
    // -------------------------------------------------------------------------

    @Override
    public void destroy() throws RemoteException {
        server.destroy();
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
     * Constrainable variant of the admin proxy, returned when the server stub
     * implements {@link RemoteMethodControl}.
     */
    @AtomicSerial
    static final class ConstrainableAdminProxy extends AbstractJiniServiceAdminProxy
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;

        ConstrainableAdminProxy(JiniServiceServer server, Uuid proxyID) {
            super(server, proxyID);
            if (!(server instanceof RemoteMethodControl)) {
                throw new IllegalArgumentException(
                        "server must implement RemoteMethodControl");
            }
        }

        /** AtomicSerial deserialization constructor. */
        ConstrainableAdminProxy(GetArg arg) throws IOException {
            super(checkConstrainable(arg));
        }

        private static GetArg checkConstrainable(GetArg arg) throws IOException {
            JiniServiceServer server = (JiniServiceServer) arg.get("server", null);
            if (!(server instanceof RemoteMethodControl)) {
                throw new InvalidObjectException(
                        "server must implement RemoteMethodControl");
            }
            return arg;
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            JiniServiceServer constrained = (JiniServiceServer)
                    ((RemoteMethodControl) server).setConstraints(constraints);
            return new ConstrainableAdminProxy(constrained, proxyID);
        }

        @Override
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) server).getConstraints();
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
