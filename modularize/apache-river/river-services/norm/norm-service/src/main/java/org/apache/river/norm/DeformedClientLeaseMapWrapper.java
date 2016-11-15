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
package org.apache.river.norm;

import java.util.HashMap;
import java.util.Map;
import java.rmi.RemoteException;

import net.jini.core.lease.LeaseException;
import net.jini.core.lease.LeaseMapException;

import org.apache.river.lease.AbstractLeaseMap;
import java.util.concurrent.ConcurrentMap;
import net.jini.core.lease.Lease;

/**
 * An implementation of LeaseMap that holds exactly one lease.  Used when
 * we have a deformed lease that we won't be able to batch with anyone
 * else and we want to call ClientLeaseWrapper.renew() instead of
 * LeaseMap.renewAll().
 * <p>
 * Provides hooks for synchronization and data associated with each
 * client lease while allowing us to use
 * <code>LeaseRenewalManager</code>.  Objects of this class are
 * returned by <code>createLeaseMap</code> calls made on
 * <code>ClientLeaseWrapper</code> objects that are deformed.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientLeaseWrapper 
 */
class DeformedClientLeaseMapWrapper extends AbstractLeaseMap {
    private static final long serialVersionUID = 1L;

    /**
     * Create a DeformedClientLeaseMapWrapper.
     * @param lease a Wrapper for the lease that wants to be renewed.
     *              May be deformed.
     * @param duration the duration to associate with lease
     */
    DeformedClientLeaseMapWrapper(ClientLeaseWrapper lease, long duration) {
	super(new HashMap<Lease, Long>(1), lease, duration);	
    }

    // inherit javadoc
    public void cancelAll() {
	throw new UnsupportedOperationException(
	     "ClientLeaseMapWrapper.cancelAll: " + 
	     "LRS should not being canceling client leases");
    }

    // inherit javadoc
    public void renewAll() throws LeaseMapException, RemoteException {
	if (map instanceof ConcurrentMap){
            renewAl();
        } else {
            synchronized (mapLock){
                renewAl();
            }
        }
    }
    
    private void renewAl() throws LeaseMapException, RemoteException {
        ClientLeaseWrapper l = 
	    (ClientLeaseWrapper) (map.keySet().iterator().next());
	long d = ( (Long)(map.get(l))).longValue();
	try {
	    l.renew(d);
	} catch (LeaseException e) {
	    final Map<Lease, Throwable> m = new HashMap<Lease, Throwable>(1);
	    m.put(l, e);
	    throw new LeaseMapException(e.getMessage(), m); 
	} 
    }

    // inherit javadoc
    public boolean canContainKey(Object key) {
	// DeformedClientLeaseMapWrapper can only be created with exactly one
	// lease, and currently they can only contain one lease, so
	// return false unless the lease passed in is the one we already
	// have.
	return map.containsKey(key);
    }
}
