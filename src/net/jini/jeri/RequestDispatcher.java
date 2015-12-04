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

package net.jini.jeri;

/**
 * A callback object for processing inbound requests.
 *
 * <p>Requests received on a {@link ServerEndpoint.ListenEndpoint
 * ListenEndpoint} will be dispatched to the instance of this
 * interface that was passed to the endpoint's {@link
 * ServerEndpoint.ListenEndpoint#listen listen} method.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface RequestDispatcher {

    /**
     * Processes an inbound request.
     *
     * <p>The supplied <code>InboundRequest</code> is used to read the
     * request data and to write the response.  The request is
     * processed in the current thread; this method does not return
     * until it is done processing the request.
     *
     * <p>After the invocation of this method completes (either by
     * returning normally or by throwing an exception), the supplied
     * <code>InboundRequest</code> will be automatically terminated
     * (see {@link InboundRequest#abort}).  If this method completes
     * before the <code>close</code> method has been invoked on the
     * stream returned by the request's {@link
     * InboundRequest#getResponseOutputStream getResponseOutputStream}
     * method, there is no guarantee that any or none of the data
     * written to the stream will be delivered; the implication is
     * that the implementation of this method is no longer interested
     * in the successful delivery of the response.
     *
     * @param request the <code>InboundRequest</code> to use to read
     * the request data and write the response
     *
     * @throws NullPointerException if <code>request</code> is
     * <code>null</code>
     **/
    void dispatch(InboundRequest request);
}
