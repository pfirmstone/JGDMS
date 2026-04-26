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
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
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
 *       deserialization contract.</li>
 * </ol>
 *
 * <h2>Constrainable proxies</h2>
 * When the server stub implements
 * {@link net.jini.core.constraint.RemoteMethodControl}, create a
 * constrainable inner subclass that also implements
 * {@code RemoteMethodControl} and delegates
 * {@link net.jini.core.constraint.RemoteMethodControl#setConstraints} to a
 * new instance.  The {@code getProxyTrustIterator()} method (called
 * reflectively by {@code BasicJeriTrustVerifier}) should return
 * {@code new SingletonProxyTrustIterator(server)}.
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
        implements Serializable, ProxyAccessor, ReferentUuid {

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
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     * @throws NullPointerException if either argument is {@code null}
     */
    protected AbstractSmartProxy(Object server, Uuid proxyID) {
        if (server == null)  throw new NullPointerException("server");
        if (proxyID == null) throw new NullPointerException("proxyID");
        this.server  = server;
        this.proxyID = proxyID;
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * <p>Reads the {@code server} and {@code proxyID} fields from
     * {@code arg} and validates that neither is {@code null} before
     * delegating to the normal constructor.
     *
     * @param arg the deserialization argument bag; must be non-null
     * @throws IOException if either field is {@code null} or cannot be read
     */
    protected AbstractSmartProxy(GetArg arg) throws IOException {
        this(checkServer(arg), (Uuid) arg.get("proxyID", null));
    }

    // -------------------------------------------------------------------------
    // Deserialization validation
    // -------------------------------------------------------------------------

    private static Object checkServer(GetArg arg) throws IOException {
        Object server  = arg.get("server",  null);
        Uuid   proxyID = (Uuid) arg.get("proxyID", null);
        if (server == null) {
            throw new InvalidObjectException(
                    "server field is null in " + arg.getClass().getName());
        }
        if (proxyID == null) {
            throw new InvalidObjectException(
                    "proxyID field is null in " + arg.getClass().getName());
        }
        return server;
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
     * {@link BasicProxyTrustVerifier} use this method to obtain the raw
     * remote reference.
     *
     * @return the server stub; never {@code null}
     */
    @Override
    public final Object getProxy() {
        return server;
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
}
