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

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;

/**
 * Factory for {@link LandlordLease} instances.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class LeaseFactory {
    /** The inner proxy for the leases we create */
    final private Landlord landlord;

    /** The <code>Uuid</code> of the server granting the leases */
    final private Uuid landlordUuid;

    /**
     * Create a new <code>LeaseFactory</code> that will create
     * <code>LandlordLease</code>s with the specified
     * <code>Landlord</code> and landlord <code>Uuid</code>.
     * @param landlord the inner proxy that the leases will
     *        use to communicate back to the server.
     * @param landlordUuid a universally unique id that has
     *        been assigned to the server granting of the lease.
     *        Ideally the <code>Uuid</code> {@link 
     *        ReferentUuid#getReferentUuid landlord.getUuid}
     *        would return if <code>landlord</code> implemented
     *        {@link ReferentUuid}. Used
     *        to determine when two leases can be batched together.
     * @throws NullPointerException if either argument is 
     *         <code>null</code>
     */
    public LeaseFactory(Landlord landlord, Uuid landlordUuid) {
	if (landlord == null)
	    throw new NullPointerException("landlord must be non-null");

	if (landlordUuid == null)
	    throw new NullPointerException("landlordUuid must be non-null");

	this.landlord = landlord;
	this.landlordUuid = landlordUuid;
    }

    /**
     * Return a new <code>LandlordLease</code> with the specified
     * initial expiration and cookie using the inner proxy and 
     * <code>Uuid</code> the factory was created with. Will return
     * a {@link ConstrainableLandlordLease} if inner proxy this
     * factory was created with implements {@link RemoteMethodControl}.
     * @param cookie a <code>Uuid</code> that will universally and uniquely 
     *        identify the lease
     * @param expiration the initial expiration time of the lease.
     * @return a new <code>LandlordLease</code>.
     * @throws NullPointerException if the <code>cookie</code> argument is 
     *         <code>null</code>.
     */
    public LandlordLease newLease(Uuid cookie, long expiration) {
	if (landlord instanceof RemoteMethodControl)
	    return new ConstrainableLandlordLease(cookie, landlord,
	        landlordUuid, expiration, null);
	return new LandlordLease(cookie, landlord, landlordUuid, expiration);
    }

    /**
     * Return a <code>TrustVerifier</code> that will verify the 
     * proxies produced by this factory. Currently the verifier
     * returned will be an instance of {@link LandlordProxyVerifier}.
     * @return a new <code>TrustVerifier</code>.
     * @throws UnsupportedOperationException if the {@link Landlord} this
     * factory was created with does not implement both
     * {@link RemoteMethodControl} and {@link TrustEquivalence}.
     */
    public TrustVerifier getVerifier() {
	return new LandlordProxyVerifier(landlord, landlordUuid);
    }
}
