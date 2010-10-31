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
package com.sun.jini.landlord;

import com.sun.jini.lease.AbstractLeaseMap;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.util.ConcurrentModificationException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMapException;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;

/**
 * Implementation of <code>LeaseMap</code> for <code>LandlordLease</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LandlordLease
 * @see net.jini.core.lease.LeaseMap
 * @since 2.0
 */
public class LandlordLeaseMap extends AbstractLeaseMap {
    /** The landlord which this map will talk to. */
    private final Landlord landlord;

    /**
     * The <code>Uuid</code> of the {@link #landlord}. Used
     * to determine if a lease can be placed in this map.
     */
    private final Uuid landlordUuid;

    /**
     * Create a new <code>LandlordLeaseMap</code>.
     * @param landlord Owner of the resource <code>lease</code> is for.
     * @param landlordUuid a universally unique id that has been
     *                 assigned to the server granting of the lease.
     *                 Ideally the <code>Uuid</code> {@link
     *                 ReferentUuid#getReferentUuid landlord.getUuid} would
     *                 return if <code>landlord</code> implemented
     *                 {@link ReferentUuid}. Used to determine when
     *                 two leases can be batched together. 
     * @param lease    first lease to be placed in the map.  It is
     *                 assumed that <code>canContainKey(lease)</code>
     *                 would be <code>true</code>.  Must work with the 
     *                 landlord protocol.
     * @param duration the duration the lease should be renewed for if 
     *                 <code>renewAll</code> is called
     * @throws NullPointerException if <code>landlord</code> or 
     *                 <code>landlordUuid</code> is <code>null</code>.
     */
    LandlordLeaseMap(Landlord landlord, Uuid landlordUuid, Lease lease,
		     long duration) 
    {
	super(lease, duration);
	if (landlord == null)
	    throw new NullPointerException("Landlord must be non-null");

	if (landlordUuid == null)
	    throw new NullPointerException("landlordUuid must be non-null");

	this.landlord = landlord;
	this.landlordUuid = landlordUuid;
    }

    // inherit doc comment
    public boolean canContainKey(Object key) {
	if (key instanceof LandlordLease) {
  	    return landlordUuid.equals(((LandlordLease)key).landlordUuid());
	}

	return false;
    }

    // inherit doc comment
    public void cancelAll() throws LeaseMapException, RemoteException {
	Uuid[] cookies = new Uuid[size()];
	LandlordLease[] leases = new LandlordLease[cookies.length];
	Iterator it = keySet().iterator();
	for (int i = 0; it.hasNext(); i++) {
	    LandlordLease lease = (LandlordLease) it.next();
	    leases[i] = lease;
	    cookies[i] = lease.cookie();
	}

	final Map rslt = landlord.cancelAll(cookies);

	if (rslt == null) {
	    // Everything worked out, normal return
	    return;
	} else {
	    // Some the leases could not be canceled, generate a
	    // LeaseMapException
	    
	    // translate the cookie->exception map into a
	    // lease->exception map

	    int origSize = rslt.size();
	    for (int i = 0; i < cookies.length; i++) {
		Object exception = rslt.remove(cookies[i]); 
		// remove harmless if not in map 

		if (exception != null) {	     // if it was in map
		    rslt.put(leases[i], exception);  // put back as lease
		    remove(leases[i]);		     // remove from this map
		}
	    }

	    if (origSize != rslt.size())	// some cookie wasn't found
		throw new ConcurrentModificationException();
	    
	    throw new LeaseMapException(
	        "Failure canceling one or more leases", rslt);
	}
    }


    // inherit doc comment
    public void renewAll() throws LeaseMapException, RemoteException {
	Uuid[] cookies = new Uuid[size()];
	long[] extensions = new long[cookies.length];
	LandlordLease[] leases = new LandlordLease[cookies.length];
	Iterator it = keySet().iterator();
	for (int i = 0; it.hasNext(); i++) {
	    LandlordLease lease = (LandlordLease) it.next();
	    leases[i] = lease;
	    cookies[i] = lease.cookie();
	    extensions[i] = ((Long) get(lease)).longValue();
	}

	long now = System.currentTimeMillis();
	Landlord.RenewResults results = 
	    landlord.renewAll(cookies, extensions);

	Map bad = null;
	int d = 0;
	for (int i = 0; i < cookies.length; i++) {
	    if (results.granted[i] != -1) {
		long newExp = now + results.granted[i];
		if (newExp < 0) // Overflow, set to Long.MAX_VALUE
		    newExp = Long.MAX_VALUE;
		    
		leases[i].setExpiration(newExp);
	    } else {
		if (bad == null) {
		    bad = new HashMap(results.denied.length +
				      results.denied.length / 2);
		}
		Object badTime = remove(leases[i]);   // remove from this map
		if (badTime == null)		      // better be in there
		    throw new ConcurrentModificationException();
		bad.put(leases[i], results.denied[d++]);// add to "bad" map
	    }
	}

	if (bad != null)
	    throw new LeaseMapException("renewing", bad);
    }

    /** Return the landlord. */
    Landlord landlord() {
	return landlord;
    }
}

