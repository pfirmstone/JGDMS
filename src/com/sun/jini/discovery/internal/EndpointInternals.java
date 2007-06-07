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

package com.sun.jini.discovery.internal;

import com.sun.jini.jeri.internal.connection.ConnManagerFactory;
import com.sun.jini.jeri.internal.connection.ServerConnManager;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.connection.OutboundRequestHandle;

/**
 * Provides back-door interface used by EndpointDiscoveryClient and
 * EndpointDiscoveryServer for performing non-public endpoint operations.
 */
public interface EndpointInternals {

    /**
     * Causes the given endpoint not to connect sockets it obtains from its
     * socket factory.
     */
    void disableSocketConnect(Endpoint endpoint);

    /**
     * Sets the ConnManagerFactory used by the given endpoint to produce
     * ConnManagers for managing connections.
     */
    void setConnManagerFactory(Endpoint endpoint,
			       ConnManagerFactory factory);

    /**
     * Sets the ServerConnManager used by the given endpoint to manage accepted
     * connections.
     */
    void setServerConnManager(ServerEndpoint endpoint,
			      ServerConnManager manager);

    /**
     * Returns any constraints that must be partially or fully implemented by
     * higher layers for the outbound request represented by the given handle.
     */
    InvocationConstraints getUnfulfilledConstraints(
			      OutboundRequestHandle handle);
}
