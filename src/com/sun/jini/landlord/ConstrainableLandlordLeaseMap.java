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

import net.jini.core.lease.Lease;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;
import com.sun.jini.proxy.ConstrainableProxyUtil;

/**
 * Constrainable sub-class of <code>LandlordLeaseMap</code>.
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final public class ConstrainableLandlordLeaseMap extends LandlordLeaseMap {
    /** 
     * Create a new <code>ConstrainableLandlordLeaseMap</code>.
     * @param landlord Reference to the entity that created 
     *                 <code>lease</code>.  Assumes that any
     *                 necessary method constraints have been
     *                 attached.
     * @param landlordUuid a universally unique id that has been
     *                 assigned to the server granting of the lease.
     *                 Ideally the <code>Uuid</code> {@link
     *                 ReferentUuid#getReferentUuid landlord.getUuid} would
     *                 return if <code>landlord</code> implemented
     *                 {@link ReferentUuid}. Used to determine when
     *                 leases can be added to this map.
     * @param lease    First lease to be placed in the map.  It is
     *                 assumed that <code>canContainKey(lease)</code>
     *                 would be <code>true</code>.  Must work with the 
     *                 landlord protocol.
     * @param duration The duration the lease should be renewed for if 
     *                 <code>renewAll</code> is called.
     * @throws ClassCastException if <code>landlord</code>
     *                 does not implement <code>RemoteMethodControl</code>.
     * @throws NullPointerException if landlord is <code>null</code>.
     */
    ConstrainableLandlordLeaseMap(Landlord landlord, Uuid landlordUuid, 
				  Lease lease, long duration) 
    {
	super(landlord, landlordUuid, lease, duration);
	if (!(landlord instanceof RemoteMethodControl))
	    throw new ClassCastException("landlord must implement " +
					 "RemoteMethodControl");
    }

    // doc inherited from super
    public boolean canContainKey(Object key) {
	if (!super.canContainKey(key))
	    return false;	

	// Same landlord, check to see if we have comparable constraints.
	if (!(key instanceof ConstrainableLandlordLease))
	    return false;

	// The key's constraints
	final MethodConstraints lmc = 
	    ((ConstrainableLandlordLease)key).getConstraints();

	// Our constraints
	final MethodConstraints omc = 
	    ((RemoteMethodControl)landlord()).getConstraints();

	// Are they equivalent (after applying the map)?
	return ConstrainableProxyUtil.equivalentConstraints(
	    lmc, omc, ConstrainableLandlordLease.leaseMapMethodMapArray);

    }
}
