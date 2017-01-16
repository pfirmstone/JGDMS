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

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Produces {@link OutboundRequest} instances to use for attempting to
 * send a particular request to a remote communication endpoint.
 *
 * <p>As long as {@link #hasNext hasNext} returns <code>true</code>,
 * the {@link #next next} method can be invoked to initiate an attempt
 * to make the request.  If successful, the <code>next</code> method
 * returns an <code>OutboundRequest</code> to use to write the request
 * data and read the response.
 *
 * <p>If the request communication attempt fails, such as if the
 * <code>next</code> invocation throws an exception or if a subsequent
 * I/O operation on the returned <code>OutboundRequest</code> or its
 * streams throws an exception, then if <code>hasNext</code> returns
 * <code>true</code> again, the <code>next</code> method can be
 * invoked again to retry the request attempt.  This process of
 * retrying failed request attempts can repeat as long as
 * <code>hasNext</code> returns <code>true</code> after the previous
 * failed request attempt.
 *
 * <p>Note that it is the user's responsibility to abstain from
 * retrying a request attempt if doing so might violate any applicable
 * guarantees of <i>at most once</i> execution semantics (invoking
 * {@link OutboundRequest#getDeliveryStatus getDeliveryStatus} on the
 * previous <code>OutboundRequest</code> might aid in making that
 * determination).
 *
 * <p>A typical <code>OutboundRequestIterator</code> is likely to
 * support making only one request attempt, in which case after one
 * invocation of <code>next</code> (successful or not),
 * <code>hasNext</code> will return <code>false</code>.  Reasons that
 * an <code>OutboundRequestIterator</code> might support multiple
 * request attempts include:
 *
 * <ul>
 *
 * <li>if the remote endpoint implementation features multiple
 * communication mechanism alternatives to attempt or
 *
 * <li>if the implementation can ascertain that the nature of the
 * previous request attempt failure indicates that it would be very
 * unlikely to reoccur in a subsequent attempt.
 *
 * </ul>
 *
 * The <code>hasNext</code> method should not return <code>true</code>
 * after successive failed request attempts indefinitely.  The request
 * retry mechanism provided by <code>OutboundRequestIterator</code> is
 * not intended for implementing a strategy of general retry of the
 * same communication mechanism after indeterminate failures.
 *
 * <p>Note that it is permitted, although unlikely, for an
 * <code>OutboundRequestIterator</code>'s <code>hasNext</code> method
 * to never return <code>true</code>, in which case the
 * <code>OutboundRequestIterator</code> does not support initiating
 * even one attempt to send the request.
 *
 * <p>An <code>OutboundRequestIterator</code> is not guaranteed to be
 * safe for concurrent use by multiple threads.
 *
 * @author Sun Microsystems, Inc.
 * @see Endpoint
 * @since 2.0
 **/
public interface OutboundRequestIterator {

    /**
     * Returns <code>true</code> if this iterator supports making at
     * least one more attempt to communicate the request, and
     * <code>false</code> otherwise.
     *
     * <p>If this method returns <code>true</code>, then it is
     * guaranteed that the next invocation of {@link #next next} on
     * this iterator will not throw a {@link NoSuchElementException}.
     *
     * <p>If <code>next</code> has been invoked on this iterator and
     * the previous invocation of <code>next</code> returned an
     * <code>OutboundRequest</code>, then this method should not be
     * invoked until that <code>OutboundRequest</code> has been used
     * to attempt to communicate the request and a failure has
     * occurred.
     *
     * <p>The security context in which this method is invoked may be
     * used for subsequent verification of security permissions; see
     * the <code>next</code> method specification for more details.
     *
     * @return <code>true</code> if this iterator supports making
     * another attempt to communicate the request, and
     * <code>false</code> otherwise
     **/
    boolean hasNext();

    /**
     * Initiates an attempt to communicate the request to the remote
     * endpoint.
     *
     * <p>After an invocation of {@link #hasNext hasNext} returns
     * <code>true</code>, it is guaranteed that the next invocation of
     * this method will not throw a {@link NoSuchElementException}.
     *
     * <p>If successful, this method returns an
     * <code>OutboundRequest</code> to use to write the request data
     * and read the response.  Even if this method throws an {@link
     * IOException} or a {@link SecurityException}, the iteration of
     * attempts to communicate the request may continue with another
     * invocation of <code>hasNext</code>.
     *
     * <p>The implementation verifies that the user's security context
     * has all of the security permissions necessary to communicate
     * the current request attempt with the remote endpoint and to
     * satisfy any required constraints, as appropriate to the
     * implementation of this interface.  The implementation is
     * allowed, however, to indicate failure of such a permission
     * check by either throwing a <code>SecurityException</code> from
     * this method or, after returning an <code>OutboundRequest</code>
     * from this method, throwing a <code>SecurityException</code>
     * from some subsequent operation on the
     * <code>OutboundRequest</code> or its streams.  If such a
     * <code>SecurityException</code> is thrown, request data must not
     * have been transmitted to the server (that is, if an
     * <code>OutboundRequest</code> has been returned, its {@link
     * OutboundRequest#getDeliveryStatus getDeliveryStatus} method
     * must return <code>false</code>), and the client's identity must
     * not have been revealed to the server.
     *
     * <p>Also, the implementation of this method or the returned
     * <code>OutboundRequest</code> must eventually verify that the
     * client and server have the requisite principals and credentials
     * to satisfy any required constraints and if not, throw an
     * <code>IOException</code>.  If such an <code>IOException</code>
     * is thrown, request data must not have been transmitted to the
     * server.
     *
     * <p>In verifying any such permission requirement or credential,
     * the implementation is allowed to use the security context in
     * effect for this or any previous invocation of a method on this
     * iterator or the security context in effect for any operation on
     * the <code>OutboundRequest</code> returned by this or any
     * previous invocation of this method on this iterator.
     * Therefore, this iterator and the <code>OutboundRequest</code>
     * instances that it produces should be used in a uniform security
     * context.
     *
     * @return the <code>OutboundRequest</code> to use to write the
     * request data and read the response
     *
     * @throws NoSuchElementException if this iterator does not
     * support making another attempt to communicate the request (that
     * is, if <code>hasNext</code> would return <code>false</code>)
     *
     * @throws IOException if an I/O exception occurs while performing
     * this operation; in this event, the recipient may have received
     * an indication of the request initiation attempt
     *
     * @throws SecurityException if the user does not have the
     * permissions necessary to communicate with the remote endpoint
     **/
    OutboundRequest next() throws IOException;
}
