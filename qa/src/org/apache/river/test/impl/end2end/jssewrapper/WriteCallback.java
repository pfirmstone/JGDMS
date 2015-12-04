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

package org.apache.river.test.impl.end2end.jssewrapper;

import java.io.ObjectOutputStream;

import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.connection.Connection;
import javax.security.auth.Subject;

public interface WriteCallback {

    /**
     * Callback object accessed by <code>Endpoint.newRequest</code>
     * The <code>writeCallback</code> method is called before any call
     * data is passed by the transport layer of the client. Before
     * the callback is invoked, the <code>OutboundRequest</code>
     * to be returned by <code>Endpoint.newRequest</code> is
     * created and passed to the callback. The
     * the callback method of the <code>ReadCallback</code> object
     * returned by this method will be invoked by the server side of
     * the remote call before any parameters are read by the transport layer.
     *
     * @param requestIt The <code>OutboundRequest</code> which will
     * be returned by the call to <code>OutboundRequest.newRequest</code>
     *
     * @param constraints The <code>InvocationConstraints</code> passed to
     * the <code>Endpoint.newRequest</code> method.
     */
    public ReadCallback writeCallback(OutboundRequest requestIt,
                  InvocationConstraints constraints);
}
