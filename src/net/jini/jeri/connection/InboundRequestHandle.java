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

package net.jini.jeri.connection;

import net.jini.jeri.ServerEndpoint;

/**
 * Associates information with a request that is being received on a
 * {@link ServerConnection}.
 *
 * <p>An <code>InboundRequestHandle</code> is an opaque cookie
 * provided to a {@link ServerConnectionManager} by a connection-based
 * {@link ServerEndpoint} implementation, via {@link
 * ServerConnection#processRequestData
 * ServerConnection.processRequestData}, in order to identify the
 * request in later invocations of certain {@link ServerConnection}
 * methods.  The handle encapsulates any request-specific information
 * needed by the transport implementation.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface InboundRequestHandle {
}
