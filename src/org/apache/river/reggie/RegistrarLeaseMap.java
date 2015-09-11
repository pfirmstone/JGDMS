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
package org.apache.river.reggie;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import org.apache.river.lease.AbstractIDLeaseMap;

/**
 * The LeaseMap implementation class for registrar leases.  Clients only see
 * instances via the LeaseMap interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class RegistrarLeaseMap extends AbstractIDLeaseMap<RegistrarLease> {

    private static final long serialVersionUID = 2L;

    /**
     * The registrar.
     *
     * @serial
     */
    final Registrar server;
    /**
     * The registrar's service ID.
     *
     * @serial
     */
    final ServiceID registrarID;

    /** Simple constructor */
    RegistrarLeaseMap(RegistrarLease lease, long duration) {
	this(lease.getRegistrar(), lease, duration);
    }

    /** Constructor used by ConstrainableRegistrarLeaseMap */
    RegistrarLeaseMap(Registrar server, RegistrarLease lease, long duration) {
	super();
	this.server = server;
	registrarID = lease.getRegistrarID();
        put(lease, Long.valueOf(duration));
    }

    /** Any RegistrarLease from the same server can be in the map */
    public boolean canContainKey(Object key) {
	return (key instanceof RegistrarLease &&
		registrarID.equals(((RegistrarLease) key).getRegistrarID()));
    }

    // This method's javadoc is inherited from an interface of this class
    public void renewAll() throws LeaseMapException, RemoteException {
        if (isEmpty()) return;
        List<RegistrarLease> leases = new LinkedList<RegistrarLease>();
        List<Object> regIDS = new LinkedList<Object>();
        List<Uuid> leaseIDS = new LinkedList<Uuid>();
        List<Long> dur = new LinkedList<Long>();
        
        Iterator<Map.Entry<RegistrarLease,Long>> itera = entrySet().iterator();
        while ( itera.hasNext()) {
            Map.Entry<RegistrarLease,Long> e = itera.next();
            RegistrarLease lease = e.getKey();
            leases.add(lease);
            regIDS.add(lease.getRegID());
            leaseIDS.add(lease.getReferentUuid());
            dur.add(e.getValue());
        }
        Object[] regIDs = regIDS.toArray(new Object[regIDS.size()]);
        Uuid[] leaseIDs = leaseIDS.toArray(new Uuid [leaseIDS.size()]);
        long[] durations = new long[dur.size()];
        Iterator<Long> it = dur.iterator();
        int i = 0;
        while (it.hasNext()){
            durations [i] = it.next();
            i++;
        }
        
        //TODO finish below, watch out for results.
        RenewResults results = server.renewLeases(regIDs, leaseIDs, durations);
        long now = System.currentTimeMillis();
        Map<Lease,Exception> emap = (results.exceptions != null) ?
                       new HashMap<Lease,Exception>(2 * results.exceptions.length + 1) : null;
        i = 0;
        int j = 0;
        for (Iterator<RegistrarLease> iter = leases.iterator(); iter.hasNext(); i++) {
            RegistrarLease e = iter.next();
            long duration = results.durations[i];
            if (duration >= 0) {
                e.setExpiration(duration + now);
            } else {
                emap.put(e, results.exceptions[j++]);
                remove(e);
            }
        }
        if (emap != null)
            throw new LeaseMapException("lease renewal failures", emap);
    }

    // This method's javadoc is inherited from an interface of this class
    @SuppressWarnings("unchecked")
    public void cancelAll() throws LeaseMapException, RemoteException {
        // finish by copying above.
        if (isEmpty()) return;
        List<RegistrarLease> leases = new LinkedList<RegistrarLease>();
        List regIDs = new LinkedList();
        List<Uuid> leaseIDs = new LinkedList<Uuid>();
        int i = 0;
        for (Iterator<RegistrarLease> iter = keySet().iterator(); iter.hasNext(); i++) {
            RegistrarLease ls = iter.next();
            leases.add(ls);
            regIDs.add(ls.getRegID());
            leaseIDs.add(ls.getReferentUuid());
        }
        Exception[] exceptions = server.cancelLeases(
                regIDs.toArray(), 
                leaseIDs.toArray(new Uuid[leaseIDs.size()])
                );
        if (exceptions == null) return;
        i = 0;
        Map<Lease,Exception> emap = new HashMap<Lease,Exception>(exceptions.length);
        for (Iterator<RegistrarLease> iter = leases.iterator(); iter.hasNext(); i++) {
            Lease ls = (Lease)iter.next();
            Exception ex = exceptions[i];
            if (ex != null) {
                emap.put(ls, ex);
                remove(ls);
            }
        }
        throw new LeaseMapException("lease cancellation failures", emap);
    }
}
