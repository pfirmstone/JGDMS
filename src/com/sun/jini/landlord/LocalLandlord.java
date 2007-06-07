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

import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;

/**
 * Interface that defines the basic landlord primitives (renew and
 * cancel) as local methods.  Classes already implementing 
 * <code>Landlord</code> should be able to implement this interface
 * simply by adding it the the <code>implements</code> clause of their
 * class declaration.
 * <p>
 * Some users may want to implement this interface using an adaptor
 * class. This could be useful in situations where the landlord's
 * cancel and renew methods don't do quite the right thing for batch
 * lease operations
 * <p>
 * This interface gives local landlord utilities access to the cancel
 * and renew methods.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.landlord.Landlord 
 * @since 2.0
 */
public interface LocalLandlord {
    /**
     * Renew the lease that is associated with the given cookie.
     *
     * @param cookie   an object that universally and uniquely identifies a
     *                 lease granted by this <code>LocalLandlord</code>
     * @param duration the duration in milliseconds the client
     *                 wants the lease renewed for
     * @return The new duration the lease should have
     * @throws LeaseDeniedException if the landlord is unwilling to
     *         renew the lease
     * @throws UnknownLeaseException if landlord does not know about
     *         a lease with the specified <code>cookie</code>
     */
    public long renew(Uuid cookie, long duration)
	throws LeaseDeniedException, UnknownLeaseException;
       
    /**
     * Cancel the lease that is associated with the given cookie.
     *
     * @param cookie   an object that universally and uniquely identifies a
     *                 lease granted by this <code>LocalLandlord</code>
     * @throws UnknownLeaseException if landlord does not know about
     *         a lease with the specified <code>cookie</code>
     */
    public void cancel(Uuid cookie) throws UnknownLeaseException;
}
 
