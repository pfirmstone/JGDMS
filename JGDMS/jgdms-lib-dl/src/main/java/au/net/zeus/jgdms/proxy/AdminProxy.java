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
            return new ConstrainableAdminProxy(server, proxyID);
        }
        return new AdminProxy(server, proxyID);
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    AdminProxy(Remote server, Uuid proxyID) {
        if (server == null) throw new IllegalArgumentException("server cannot be null");
        if (!(server instanceof JoinAdmin)) {
            throw new IllegalArgumentException("server must implement JoinAdmin");
        }
        if (!(server instanceof DestroyAdmin)) {
            throw new IllegalArgumentException("server must implement DestroyAdmin");
        }
        if (proxyID == null) throw new IllegalArgumentException("proxyID cannot be null");
        this.server = server;
        this.proxyID = proxyID;
    }

    /** AtomicSerial deserialization constructor. */
    AdminProxy(GetArg arg) throws IOException {
        this(checkFields(arg), (Uuid) arg.get("proxyID", null));
    }

    private static Remote checkFields(GetArg arg) throws IOException {
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
    // JoinAdmin
    // -------------------------------------------------------------------------

    @Override
    public Entry[] getLookupAttributes() throws RemoteException {
        return ((JoinAdmin) server).getLookupAttributes();
    }

    @Override
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        ((JoinAdmin) server).addLookupAttributes(attrSets);
    }

    @Override
    public void modifyLookupAttributes(Entry[] attrSetTemplates, Entry[] attrSets)
            throws RemoteException {
        ((JoinAdmin) server).modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    @Override
    public String[] getLookupGroups() throws RemoteException {
        return ((JoinAdmin) server).getLookupGroups();
    }

    @Override
    public void addLookupGroups(String[] groups) throws RemoteException {
        ((JoinAdmin) server).addLookupGroups(groups);
    }

    @Override
    public void removeLookupGroups(String[] groups) throws RemoteException {
        ((JoinAdmin) server).removeLookupGroups(groups);
    }

    @Override
    public void setLookupGroups(String[] groups) throws RemoteException {
        ((JoinAdmin) server).setLookupGroups(groups);
    }

    @Override
    public LookupLocator[] getLookupLocators() throws RemoteException {
        return ((JoinAdmin) server).getLookupLocators();
    }

    @Override
    public void addLookupLocators(LookupLocator[] locators) throws RemoteException {
        ((JoinAdmin) server).addLookupLocators(locators);
    }

    @Override
    public void removeLookupLocators(LookupLocator[] locators) throws RemoteException {
        ((JoinAdmin) server).removeLookupLocators(locators);
    }

    @Override
    public void setLookupLocators(LookupLocator[] locators) throws RemoteException {
        ((JoinAdmin) server).setLookupLocators(locators);
    }

    // -------------------------------------------------------------------------
    // DestroyAdmin
    // -------------------------------------------------------------------------

    @Override
    public void destroy() throws RemoteException {
        ((DestroyAdmin) server).destroy();
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
     * Constrainable variant of the admin proxy, returned by {@link #create}
     * when the server stub implements {@link RemoteMethodControl}.
     */
    @AtomicSerial
    static final class ConstrainableAdminProxy extends AdminProxy
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;

        ConstrainableAdminProxy(Remote server, Uuid proxyID) {
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
            Remote server = (Remote) arg.get("server", null);
            if (!(server instanceof RemoteMethodControl)) {
                throw new InvalidObjectException(
                        "server must implement RemoteMethodControl");
            }
            return arg;
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            Remote constrained = (Remote)
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
