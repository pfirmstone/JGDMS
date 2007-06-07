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

package com.sun.jini.jeri.internal.connection;

import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;

/**
 * Creates {@link ConnManager} instances which delegate directly to
 * {@link ConnectionManager}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class BasicConnManagerFactory implements ConnManagerFactory {

    /**
     * Returns <code>ConnManager</code> containing a
     * <code>ConnectionManager</code> constructed with the given {@link
     * ConnectionEndpoint}; the {@link ConnManager#newRequest newRequest}
     * method of the returned <code>ConnManager</code> delegates directly to
     * the corresponding method of the contained
     * <code>ConnectionManager</code>.
     *
     * @throws NullPointerException if <code>endpoint</code> is
     * <code>null</code>
     */
    public ConnManager create(final ConnectionEndpoint endpoint) {
	return new ConnManager() {

	    private final ConnectionManager manager =
		new ConnectionManager(endpoint);

	    public OutboundRequestIterator newRequest(
						OutboundRequestHandle handle)
	    {
		return manager.newRequest(handle);
	    }
	};
    }
}
