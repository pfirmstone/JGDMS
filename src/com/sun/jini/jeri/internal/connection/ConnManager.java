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
import net.jini.jeri.connection.ConnectionManager;
import net.jini.jeri.connection.OutboundRequestHandle;

/**
 * Defines interface for internal pluggable counterparts of
 * {@link ConnectionManager}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface ConnManager {

    /**
     * Returns an outbound request iterator to use to send a new request
     * corresponding to the specified request handle over the connection
     * endpoint for this instance.  This method is equivalent in function to
     * {@link ConnectionManager#newRequest}.
     *
     * @param handle the outbound request handle
     * @return an outbound request iterator to use to send a new request
     * corresponding to the specified handle
     * @throws NullPointerException if <code>handle</code> is <code>null</code>
     * @throws IllegalArgumentException if the specified handle was not created
     * for use with a connection endpoint equivalent to the endpoint contained
     * by this instance
     */
    OutboundRequestIterator newRequest(OutboundRequestHandle handle);
}
