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
import net.jini.id.Uuid;

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
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
    public static Landlord.RenewResults renewAll(LocalLandlord landlord,
					Uuid[] cookies, long[] durations) 
    {
        org.apache.river.landlord.Landlord.RenewResults results =
	org.apache.river.landlord.LandlordUtil.renewAll(landlord, cookies, durations);
        int len = cookies.length;
        long [] granted = new long[len];
        Exception [] denied = new Exception[len];
        for (int i = 0; i<len ; i++){
            granted[i] = results.getGranted(i);
            denied[i] = results.getDenied(i);
        }
        
        return new Landlord.RenewResults(granted, denied);
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
	return org.apache.river.landlord.LandlordUtil.cancelAll(landlord, cookies);
    }
}
