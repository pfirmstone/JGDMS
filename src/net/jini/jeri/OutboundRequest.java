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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;

/**
 * Represents a request that is being sent and the corresponding
 * response received in reply.
 *
 * <p>An <code>OutboundRequest</code> can be used to write out the
 * contents of the request and to read in the response.
 *
 * <p>The communication protocol used by the implementation of this
 * interface must guarantee that for each instance of this interface,
 * any request data must only be delivered to the recipient (in the
 * form of an <code>InboundRequest</code> passed to {@link
 * RequestDispatcher#dispatch RequestDispatcher.dispatch}) <i>at most
 * once</i>.  The {@link #getDeliveryStatus getDeliveryStatus} method
 * can be used to determine whether or not at least partial delivery
 * of the request might have occurred.
 *
 * <p>When finished using an <code>OutboundRequest</code>, in order to
 * allow the implementation to free resources associated with the
 * request, users should either invoke <code>close</code> on the
 * streams returned by the <code>getRequestOutputStream</code> and
 * <code>getResponseInputStream</code> methods, or invoke the
 * <code>abort</code> method.
 *
 * 
 * @see InboundRequest
 * @since 2.0
 **/
public interface OutboundRequest {

    /**
     * Populates the supplied collection with context information
     * representing this request.
     *
     * @param context the context collection to populate
     *
     * @throws NullPointerException if <code>context</code> is
     * <code>null</code>
     *
     * @throws UnsupportedOperationException if <code>context</code>
     * is unmodifiable and if any elements need to be added
     **/
    void populateContext(Collection context);

    /**
     * Returns the requirements that must be at least partially
     * implemented by higher layers in order to fully satisfy the
     * requirements for this request.  This method may also return
     * preferences that must be at least partially implemented by
     * higher layers in order to fully satisfy some of the preferences
     * for this request.
     *
     * <p>For any given constraint, there must be a clear delineation
     * of which aspects (if any) must be implemented by the transport
     * layer.  This method must not return a constraint (as a
     * requirement or a preference, directly or as an element of
     * another constraint) unless this request implements all of those
     * aspects.  Also, this method must not return a constraint for
     * which all aspects must be implemented by the transport layer.
     * Most of the constraints in the {@link net.jini.core.constraint}
     * package must be fully implemented by the transport layer and
     * thus must not be returned by this method; the one exception is
     * {@link Integrity}, for which the transport layer is responsible
     * for the data integrity aspect and higher layers are responsible
     * for the code integrity aspect.
     *
     * <p>For any {@link ConstraintAlternatives} in the constraints
     * for this request, this method should only return a
     * corresponding constraint if all of the alternatives satisfied
     * by this request need to be at least partially implemented by
     * higher layers in order to be fully satisfied.
     *
     * @return the constraints for this request that must be partially
     * or fully implemented by higher layers
     **/
    InvocationConstraints getUnfulfilledConstraints();

    /**
     * Returns an <code>OutputStream</code> to write the request data
     * to.  The sequence of bytes written to the returned stream will
     * be the sequence of bytes sent as the body of this request.
     *
     * <p>After the entirety of the request has been written to the
     * stream, the stream's <code>close</code> method must be invoked
     * to ensure complete delivery of the request.  It is possible
     * that none of the data written to the returned stream will be
     * delivered before <code>close</code> has been invoked (even if
     * the stream's <code>flush</code> method had been invoked at any
     * time).  Note, however, that some or all of the data written to
     * the stream may be delivered to (and processed by) the recipient
     * before the stream's <code>close</code> method has been invoked.
     *
     * <p>After the stream's <code>close</code> method has been
     * invoked, no more data may be written to the stream; writes
     * subsequent to a <code>close</code> invocation will fail with an
     * <code>IOException</code>.
     *
     * <p>If this method is invoked more than once, it will always
     * return the identical stream object that it returned the first
     * time (although the stream may be in a different state than it
     * was upon return from the first invocation).
     *
     * @return the output stream to write request data to
     **/
    OutputStream getRequestOutputStream();

    /**
     * Returns an <code>InputStream</code> to read the response data
     * from.  The sequence of bytes produced by reading from the
     * returned stream will be the sequence of bytes received as the
     * response data.  When the entirety of the response has been
     * successfully read, reading from the stream will indicate an
     * EOF.
     *
     * <p>Users of an <code>OutboundRequest</code> must not expect any
     * data to be available from the returned stream before the
     * <code>close</code> method has been invoked on the stream
     * returned by <code>getRequestOutputStream</code>; in other
     * words, the user's request/response protocol must not require
     * any part of a request to be a function of any part of its
     * response.
     *
     * <p>It is possible, however, for data to be available from the
     * returned stream before the <code>close</code> method has been
     * invoked on, or even before the entirety of the request has been
     * written to, the stream returned by
     * <code>getRequestOutputStream</code>.  Because such an early
     * response might indicate, depending on the user's
     * request/response protocol, that the recipient will not consider
     * the entirety of the request, perhaps due to an error or other
     * abnormal condition, the user may wish to process it
     * expeditiously, rather than continuing to write the remainder of
     * the request.
     *
     * <p>Invoking the <code>close</code> method of the returned
     * stream will cause any subsequent read operations on the stream
     * to fail with an <code>IOException</code>, although it will not
     * terminate this request as a whole; in particular, the request
     * may still be subsequently written to the stream returned by the
     * <code>getRequestOutputStream</code> method.  After
     * <code>close</code> has been invoked on both the returned stream
     * and the stream returned by <code>getRequestOutputStream</code>,
     * the implementation may free all resources associated with this
     * request.
     * 
     * <p>If this method is invoked more than once, it will always
     * return the identical stream object that it returned the first
     * time (although the stream may be in a different state than it
     * was upon return from the first invocation).
     *
     * @return the input stream to read response data from
     **/
    InputStream getResponseInputStream();

    /**
     * Returns <code>false</code> if it is guaranteed that no data
     * written for this request has been processed by the recipient.
     * This guarantee remains valid until any subsequent I/O operation
     * has been attempted on this request.
     *
     * If this method returns <code>true</code>, then data written for
     * this request may have been at least partially processed by the
     * recipient (the <code>RequestDispatcher</code> receiving the
     * corresponding <code>InboundRequest</code>).
     *
     * @return <code>false</code> if data written for this request has
     * definitely not been processed by the recipient, and
     * <code>true</code> if data written for this request may have
     * been at least partially processed by the recipient
     **/
    boolean getDeliveryStatus();

    /**
     * Terminates this request, freeing all associated resources.
     *
     * <p>This method may be invoked at any stage of the processing of
     * the request.
     *
     * <p>After this method has been invoked, I/O operations on the
     * streams returned by the <code>getRequestOutputStream</code> and
     * <code>getResponseInputStream</code> methods will fail with an
     * <code>IOException</code>, except some operations that may
     * succeed because they only affect data in local I/O buffers.
     *
     * <p>If this method is invoked before the <code>close</code>
     * method has been invoked on the stream returned by
     * <code>getRequestOutputStream</code>, there is no guarantee that
     * any or none of the data written to the stream so far will be
     * delivered; the implication of such an invocation of this method
     * is that the user is no longer interested in the successful
     * delivery of the request.
     **/
    void abort();
}
