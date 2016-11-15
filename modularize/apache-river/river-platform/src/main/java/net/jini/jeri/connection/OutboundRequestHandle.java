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

import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

/**
 * Associates information with a request that is being sent to a
 * {@link ConnectionEndpoint}.  The same handle is associated with all
 * {@link OutboundRequest} instances produced by the same {@link
 * OutboundRequestIterator} (that is, all attempts to send the same
 * request).
 *
 * <p>An <code>OutboundRequestHandle</code> is an opaque cookie
 * provided to a {@link ConnectionManager} by a connection-based
 * {@link Endpoint} implementation (via {@link
 * ConnectionManager#newRequest ConnectionManager.newRequest}) in
 * order to identify the request in later invocations of certain
 * {@link ConnectionEndpoint} and {@link Connection} methods.  The
 * handle encapsulates information about the constraints for the
 * request and any other request-specific information needed by the
 * transport implementation.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface OutboundRequestHandle {
}
