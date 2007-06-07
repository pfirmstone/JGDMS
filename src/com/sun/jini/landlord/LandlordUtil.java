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

import java.util.Map;
import java.util.ArrayList;

import net.jini.core.lease.LeaseException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import com.sun.jini.landlord.Landlord.RenewResults;


/**
 * Static methods useful for implementing landlords.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class LandlordUtil {
    /**
     * Call <code>landlord.renew()</code> for each object in 
     * <code>cookie[]</code>, passing <code>cookie[i]</code> and
     * <code>durations[i]</code>.  Gather the results and 
     * exceptions into a <code>RenewResults</code> object.
     *
     * @param landlord   A grantor of leases.
     * @param cookies an array of <code>Uuid</code>s, each uniquely and
     *                universally identifying a lease
     * @param durations an array of longs, each representing a
     *                  duration in milliseconds that the client
     *                  wants the lease associated with the object
     *                  from the corresponding element of
     *                  <code>cookies</code> renewed for.
     * @return A <code>RenewResults</code> object that contains the new
     *         duration of each lease that was successfully renewed or
     *         the exception encountered for each lease that could not
     *         be renewed.
     */ 
    public static RenewResults renewAll(LocalLandlord landlord,
					Uuid[] cookies, long[] durations) 
    {
	final int count = cookies.length;
	final long[] granted = new long[count];
	final ArrayList exceptions = new ArrayList(count);

	for (int i = 0; i < count; i++) {
	    try {
		granted[i] = landlord.renew(cookies[i], durations[i]);
	    } catch (LeaseException le) {
		// Set flag for client-side handling
		granted[i] = -1;
		exceptions.add(le);
	    }
	}

	if(exceptions.size() == 0)  {
	    return new Landlord.RenewResults(granted);
	} else {
	    // Note: Can't just cast exceptions.toArray() to Exception[]
	    final Exception[] es = new Exception[exceptions.size()];
	    return new RenewResults(granted,
                (Exception[])exceptions.toArray(es));
	}
    }

    /**
     * Call <code>landlord.cancel()</code> for each object in 
     * <code>cookies[]</code>, passing <code>cookies[i]</code>.
     *
     * @param landlord a grantor of leases
     * @param cookies an array of <code>Uuid</code>s, each uniquely and
     *                universally identifying a lease
     * @return If no exceptions are encountered, return
     *         <code>null</code>, otherwise, return a Map that 
     *         for each failed <code>cancel()</code> call maps
     *         the passed cookie object to the exception that 
     *         was generated
     */
    public static Map cancelAll(LocalLandlord landlord, Uuid[] cookies) {
	final int count = cookies.length;

	Map map = null;
	for (int i = 0; i < count; i++) {
	    try {
		landlord.cancel(cookies[i]);
	    } catch (UnknownLeaseException e) {
		if (map == null)
		    map = new java.util.HashMap();
		map.put(cookies[i], e);
	    }
	}
	
	return map;
    }
}
