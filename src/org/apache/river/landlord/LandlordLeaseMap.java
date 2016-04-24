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
package org.apache.river.landlord;

import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import org.apache.river.api.io.Valid;
import org.apache.river.lease.AbstractIDLeaseMap;

/**
 * Implementation of <code>LeaseMap</code> for <code>LandlordLease</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LandlordLease
 * @see net.jini.core.lease.LeaseMap
 * @since 2.0
 */
public class LandlordLeaseMap extends AbstractIDLeaseMap {
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
        // Constructor Exception attack not possible, exceptions thrown 
        // prior to super object creation.
	this(checkLandlord(landlord), checkLandlordUuid(landlordUuid), checkLease(lease));
        put(lease, duration); // Guaranteed not to throw an exception.
    }
    
    static Lease checkLease(Lease lease) throws ClassCastException{
        if (!(lease instanceof LandlordLease)) throw 
                new ClassCastException("Lease must be of type LandlordLease");
        return lease;
    }
    
    static Uuid checkLandlordUuid(Uuid landlordUuid) throws NullPointerException{
        if (landlordUuid == null)
	    throw new NullPointerException("landlordUuid must be non-null");
        return landlordUuid;
    }
    
    static Landlord checkLandlord( Landlord landlord) throws NullPointerException{
        if (landlord == null)
	    throw new NullPointerException("Landlord must be non-null");
        return landlord;
    }
    
    private LandlordLeaseMap(Landlord landlord, Uuid landlordUuid, Lease lease){
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
        Map rslt;
	List<Uuid> cookies;
        List<LandlordLease> leases;
        cookies = new LinkedList<Uuid>();
        leases = new LinkedList<LandlordLease>();
        Iterator<LandlordLease> it = keySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            LandlordLease lease = (LandlordLease) it.next();
            leases.add(lease);
            cookies.add(lease.cookie());
        }
        
        Uuid[] cookiesA = cookies.toArray(new Uuid[cookies.size()]);
        rslt = landlord.cancelAll(cookiesA);
	if (rslt == null) {
	    // Everything worked out, normal return
	    return;
	} else {
            try {
                rslt = Valid.copyMap(rslt, new HashMap(rslt.size()), Uuid.class, UnknownLeaseException.class); // In case the map was serialized.
            } catch (InvalidObjectException ex) {
                throw new RemoteException("Invalid map returned: ", ex);
            }
            LandlordLease[] leasesA = leases.toArray(new LandlordLease[leases.size()]);
	    // Some the leases could not be canceled, generate a
	    // LeaseMapException
	    
	    // translate the cookie->exception map into a
	    // lease->exception map

	    int origSize = rslt.size();
            int len = cookiesA.length;
	    for (int i = 0; i < len; i++) {
		Object exception = rslt.remove(cookiesA[i]); 
		// remove harmless if not in map 

		if (exception != null) {	     // if it was in map
		    rslt.put(leasesA[i], exception);  // put back as lease
		    remove(leasesA[i]);		     // remove from this map
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
        List<Uuid> cookies;
	List<Long> extensions;
	List<LandlordLease> leases;
        cookies = new LinkedList<Uuid>();
        extensions = new LinkedList<Long>();
        leases = new LinkedList<LandlordLease>();
        Iterator it = keySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            LandlordLease lease = (LandlordLease) it.next();
            leases.add(lease);
            cookies.add(lease.cookie());
            extensions.add(get(lease));
        }
        long[] extensionsA = new long[extensions.size()];
        Iterator<Long> ite = extensions.iterator();
        for ( int i = 0; ite.hasNext(); i++){
            extensionsA[i] = ite.next().longValue();
        }
        Uuid [] cookiesA = cookies.toArray(new Uuid[cookies.size()]);
        LandlordLease [] leasesA = leases.toArray(new LandlordLease[leases.size()]);
	long now = System.currentTimeMillis();
	Landlord.RenewResults results = landlord.renewAll(cookiesA, extensionsA);
	Map bad = null;
	int d = 0;
        int len = cookiesA.length;
	for (int i = 0; i < len; i++) {
	    if (results.getGranted(i) != -1) {
		long newExp = now + results.getGranted(i);
		if (newExp < 0) // Overflow, set to Long.MAX_VALUE
		    newExp = Long.MAX_VALUE;
		    
		leasesA[i].setExpiration(newExp);
	    } else {
		if (bad == null) {
		    bad = new HashMap();
		}
		Object badTime = remove(leasesA[i]);   // remove from this map
		if (badTime == null)		      // better be in there
		    throw new ConcurrentModificationException();
		bad.put(leasesA[i], results.getDenied(d++));// add to "bad" map
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

