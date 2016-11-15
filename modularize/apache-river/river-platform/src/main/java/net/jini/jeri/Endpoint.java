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

import net.jini.core.constraint.InvocationConstraints;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * Represents a remote communication endpoint to send requests to.
 *
 * <p>An <code>Endpoint</code> instance contains the information
 * necessary to send requests to the remote endpoint.  For example, a
 * TCP-based <code>Endpoint</code> implementation typically contains
 * the remote host address and TCP port to connect to.
 *
 * <p>The {@link #newRequest newRequest} method can be used to send a
 * request to the remote endpoint that this object represents.
 *
 * <p>An instance of this interface should be serializable and should
 * implement {@link Object#equals Object.equals} to return
 * <code>true</code> if and only if the argument is equivalent to the
 * instance in trust, content, and function.  That is, the
 * <code>equals</code> method should be a sufficient substitute for
 * {@link TrustEquivalence#checkTrustEquivalence
 * TrustEquivalence.checkTrustEquivalence}.  But unlike
 * <code>checkTrustEquivalence</code>, the <code>equals</code> method
 * cannot assume that the implementations of any of the invoked
 * instance's pluggable component objects are trusted (whether
 * pluggable through public APIs or deserialization).  Therefore, the
 * <code>equals</code> method should not invoke comparison methods
 * (such as <code>equals</code>) on any such component without first
 * verifying that the component's implementation is at least as
 * trusted as the implementation of the corresponding component in the
 * <code>equals</code> argument (such as by verifying that the
 * corresponding component objects have the same actual class).  If
 * any such verification fails, the <code>equals</code> method should
 * return <code>false</code> without invoking a comparison method on
 * the component.  Furthermore, these guidelines should be recursively
 * obeyed by the comparison methods of each such component for its
 * subcomponents.
 *
 * <p>If an <code>Endpoint</code> is an instance of {@link
 * TrustEquivalence}, then its <code>equals</code> method must obey
 * the above guidelines and its <code>checkTrustEquivalence</code>
 * method must not return <code>true</code> if its <code>equals</code>
 * method would not return <code>true</code> for the same argument.
 *
 * <p>All aspects of the underlying communication mechanism that are not
 * specified here are defined by the particular implementation of this
 * interface.
 *
 * @author Sun Microsystems, Inc.
 * @see ServerEndpoint
 * @since 2.0
 **/
public interface Endpoint {

    /**
     * Returns an <code>OutboundRequestIterator</code> to use to send
     * a new request to this remote endpoint using the specified
     * constraints.
     *
     * <p>The constraints must be the complete, absolute constraints
     * for the request.
     *
     * @param constraints the complete, absolute constraints
     *
     * @return an <code>OutboundRequestIterator</code> to use to send
     * a new request to this remote endpoint
     *
     * @throws NullPointerException if <code>constraints</code> is
     * <code>null</code>
     **/
    OutboundRequestIterator newRequest(InvocationConstraints constraints);
}
