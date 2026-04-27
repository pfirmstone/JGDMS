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
package au.net.zeus.jgdms.vr.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.lease.AbstractLease;
import org.apache.river.lease.AbstractLeaseMap;

/**
 * Client-side lease proxy for a
 * {@link au.net.zeus.jgdms.api.codebase.VerdictRegistry} event-listener
 * registration.
 *
 * <p>Instances are created by the server and delivered to clients inside the
 * {@link net.jini.core.event.EventRegistration} returned from
 * {@link VerdictRegistry#registerVerdictListener}.  Clients use the standard
 * {@link net.jini.core.lease.Lease} interface; the implementation forwards
 * renewals and cancellations back to the server via the
 * {@link VerdictRegistry#renewEventLease} and
 * {@link VerdictRegistry#cancelEventLease} remote methods.
 *
 * @see VerdictRegistry#registerVerdictListener
 * @since 3.1.1
 */
@AtomicSerial
public final class VerdictEventLease extends AbstractLease {

    private static final long serialVersionUID = 1L;

    private static final String SERVER   = "server";
    private static final String LEASE_ID = "leaseId";

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(SERVER,   VerdictRegistry.class),
            new SerialForm(LEASE_ID, Uuid.class)
        };
    }

    public static void serialize(PutArg arg, VerdictEventLease l) throws IOException {
        arg.put(SERVER,   l.server);
        arg.put(LEASE_ID, l.leaseId);
        arg.writeArgs();
    }

    /** Remote reference to the VerdictRegistry server. @serial */
    private final VerdictRegistry server;

    /** Cookie that identifies this registration's lease on the server. @serial */
    private final Uuid leaseId;

    private static GetArg check(GetArg arg)
            throws IOException, ClassNotFoundException {
        VerdictRegistry srv = arg.get(SERVER, null, VerdictRegistry.class);
        if (srv == null) throw new InvalidObjectException("server must not be null");
        Uuid id = arg.get(LEASE_ID, null, Uuid.class);
        if (id == null) throw new InvalidObjectException("leaseId must not be null");
        return arg;
    }

    /**
     * Deserialization constructor.
     *
     * @param arg deserialization state provided by {@link AtomicSerial}
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if a required class cannot be found
     */
    public VerdictEventLease(GetArg arg)
            throws IOException, ClassNotFoundException {
        super(check(arg));
        this.server  = arg.get(SERVER,   null, VerdictRegistry.class);
        this.leaseId = arg.get(LEASE_ID, null, Uuid.class);
    }

    /**
     * Constructs a {@code VerdictEventLease}.
     *
     * @param server     remote reference to the VerdictRegistry; must be
     *                   non-null
     * @param leaseId    the cookie that identifies this lease on the server;
     *                   must be non-null
     * @param expiration the absolute expiration time in milliseconds since
     *                   the epoch
     * @throws NullPointerException if {@code server} or {@code leaseId} is
     *                              {@code null}
     */
    public VerdictEventLease(VerdictRegistry server,
                              Uuid leaseId,
                              long expiration) {
        super(expiration);
        if (server  == null) throw new NullPointerException("server");
        if (leaseId == null) throw new NullPointerException("leaseId");
        this.server  = server;
        this.leaseId = leaseId;
    }

    /**
     * Returns the duration actually granted by the server, which
     * {@link AbstractLease#renew} uses to update the local expiration cache.
     */
    @Override
    protected long doRenew(long duration)
            throws UnknownLeaseException, LeaseDeniedException, RemoteException {
        return server.renewEventLease(leaseId, duration);
    }

    @Override
    public void cancel()
            throws UnknownLeaseException, RemoteException {
        server.cancelEventLease(leaseId);
    }

    /**
     * Two {@code VerdictEventLease} instances can be batched if they refer
     * to the same server endpoint.
     */
    @Override
    public boolean canBatch(Lease lease) {
        return (lease instanceof VerdictEventLease)
                && server.equals(((VerdictEventLease) lease).server);
    }

    @SuppressWarnings("unchecked")
    @Override
    public LeaseMap createLeaseMap(long duration) {
        return new VerdictLeaseMap(this, duration);
    }

    @Override
    public int hashCode() {
        return leaseId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VerdictEventLease)) return false;
        VerdictEventLease other = (VerdictEventLease) obj;
        return leaseId.equals(other.leaseId);
    }

    // -------------------------------------------------------------------------
    // Inner class: batch lease map for VerdictEventLease instances
    // -------------------------------------------------------------------------

    /**
     * {@link LeaseMap} implementation for batching {@link VerdictEventLease}
     * renewals and cancellations.  Renewal and cancellation are delegated to
     * the individual leases' single-lease remote methods; if any fail, the
     * remaining operations still proceed, with all failures reported together
     * as a {@link LeaseMapException}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class VerdictLeaseMap extends AbstractLeaseMap {

        VerdictLeaseMap(VerdictEventLease lease, long duration) {
            super(lease, duration);
        }

        @Override
        public boolean canContainKey(Object key) {
            if (!(key instanceof VerdictEventLease)) return false;
            // All leases in the map must share the same server.
            // Peek at the first entry to get the reference server.
            Iterator<?> it = map.keySet().iterator();
            if (!it.hasNext()) return true;
            VerdictEventLease first = (VerdictEventLease) it.next();
            return first.server.equals(((VerdictEventLease) key).server);
        }

        @Override
        public void renewAll() throws LeaseMapException, RemoteException {
            List<Throwable> exceptions = null;
            List<Lease> failedLeases  = null;
            for (Object obj : map.entrySet()) {
                Map.Entry entry = (Map.Entry) obj;
                VerdictEventLease lease    = (VerdictEventLease) entry.getKey();
                long              duration = (Long) entry.getValue();
                try {
                    lease.renew(duration);
                } catch (Exception e) {
                    if (exceptions == null) {
                        exceptions = new ArrayList<Throwable>();
                        failedLeases = new ArrayList<Lease>();
                    }
                    exceptions.add(e);
                    failedLeases.add(lease);
                }
            }
            if (exceptions != null) {
                throw new LeaseMapException("renewAll failed for some leases",
                        buildExMap(failedLeases, exceptions));
            }
        }

        @Override
        public void cancelAll() throws LeaseMapException, RemoteException {
            List<Throwable> exceptions = null;
            List<Lease> failedLeases  = null;
            for (Object keyObj : new ArrayList<Object>(map.keySet())) {
                VerdictEventLease lease = (VerdictEventLease) keyObj;
                try {
                    lease.cancel();
                    map.remove(lease);
                } catch (Exception e) {
                    if (exceptions == null) {
                        exceptions = new ArrayList<Throwable>();
                        failedLeases = new ArrayList<Lease>();
                    }
                    exceptions.add(e);
                    failedLeases.add(lease);
                }
            }
            if (exceptions != null) {
                throw new LeaseMapException("cancelAll failed for some leases",
                        buildExMap(failedLeases, exceptions));
            }
        }

        private static HashMap<Lease, Throwable> buildExMap(
                List<Lease> leases, List<Throwable> exceptions) {
            HashMap<Lease, Throwable> result =
                    new HashMap<Lease, Throwable>(leases.size());
            for (int i = 0; i < leases.size(); i++) {
                result.put(leases.get(i), exceptions.get(i));
            }
            return result;
        }
    }
}
