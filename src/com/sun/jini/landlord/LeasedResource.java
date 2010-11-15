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

import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;

/**
 * Server side representation of a lease
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see LeasePeriodPolicy
 * @since 2.0
 */
public interface LeasedResource {
    /**
     * Changes the expiration time of the lease.
     * @param newExpiration The new expiration time in milliseconds
     *                      since the beginning of the epoch
     */
    public void setExpiration(long newExpiration);

    /**
     * Returns the expiration time of the lease.
     * @return The expiration time in milliseconds since the beginning 
     *         of the epoch
     */
    public long getExpiration();

    /**
     * Returns the universally unique identifier associated with this
     * lease. Any proxies for this lease that implement {@link ReferentUuid} 
     * should return this object from their
     * {@link ReferentUuid#getReferentUuid getReferentUuid}
     * method and should base their implementation of <code>equals</code> on
     * this object.
     */
    public Uuid getCookie();
}

