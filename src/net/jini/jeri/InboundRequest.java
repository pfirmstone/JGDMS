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
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.security.AuthenticationPermission;

/**
 * Represents a request that is being received and the corresponding
 * response to be sent in reply.
 *
 * <p>An <code>InboundRequest</code> can be used to read in the
 * contents of the request and write out the response.
 *
 * @author Sun Microsystems, Inc.
 * @see OutboundRequest
 * @see RequestDispatcher
 * @since 2.0
 **/
public interface InboundRequest {

    /**
     * Verifies that the current security context has all of the
     * security permissions necessary to receive this request.
     *
     * <p>This method should be used when a particular shared
     * mechanism is used to receive requests for a variety of
     * interested parties, each with potentially different security
     * permissions possibly more limited than those granted to the
     * code managing the shared mechanism, so that the managing code
     * can verify proper access control for each party.
     *
     * <p>For example, a TCP-based <code>InboundRequest</code>
     * implementation typically implements this method by invoking the
     * {@link SecurityManager#checkAccept checkAccept} method of the
     * current security manager (if any) with the client's host
     * address and TCP port.  An implementation that supports
     * authentication typically checks that the current security
     * context has the necessary {@link AuthenticationPermission} with
     * <code>accept</code> action.
     *
     * @throws SecurityException if the current security context does
     * not have the permissions necessary to receive this request
     **/
    void checkPermissions();

    /**
     * Verifies that this request satisfies the transport layer
     * aspects of all of the specified requirements, and returns the
     * requirements that must be at least partially implemented by
     * higher layers in order to fully satisfy the specified
     * requirements.  This method may also return preferences that
     * must be at least partially implemented by higher layers in
     * order to fully satisfy some of the specified preferences.
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
     * <p>For any {@link ConstraintAlternatives} in the specified
     * constraints, this method should only return a corresponding
     * constraint if all of the alternatives satisfied by this request
     * need to be at least partially implemented by higher layers in
     * order to be fully satisfied.
     *
     * <p>The constraints actually in force may cause conditional
     * constraints to have to be satisfied.  For example, if the only
     * requirement specified is {@link Delegation#YES Delegation.YES}
     * but the client was in fact authenticated, then the client must
     * also have delegated to the server.
     *
     * <p>The constraints passed to this method may include
     * constraints based on relative time.
     *
     * @param constraints the constraints that must be satisfied
     *
     * @return the constraints that must be at least partially
     * implemented by higher layers
     *
     * @throws UnsupportedConstraintException if the transport layer
     * aspects of any of the specified requirements are not satisfied
     * by this request
     *
     * @throws NullPointerException if <code>constraints</code> is
     * <code>null</code>
     **/
    InvocationConstraints checkConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException;

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
     * Returns an <code>InputStream</code> to read the request data
     * from.  The sequence of bytes produced by reading from the
     * returned stream will be the sequence of bytes received as the
     * request data.  When the entirety of the request has been
     * successfully read, reading from the stream will indicate an
     * EOF.
     *
     * <p>Invoking the <code>close</code> method of the returned
     * stream will cause any subsequent read operations on the stream
     * to fail with an <code>IOException</code>, although it will not
     * terminate this request as a whole; in particular, the response
     * may still be subsequently written to the stream returned by the
     * <code>getResponseOutputStream</code> method.  After
     * <code>close</code> has been invoked on both the returned stream
     * and the stream returned by
     * <code>getResponseOutputStream</code>, the implementation may
     * free all resources associated with this request.
     * 
     * <p>If this method is invoked more than once, it will always
     * return the identical stream object that it returned the first
     * time (although the stream may be in a different state than it
     * was upon return from the first invocation).
     *
     * @return the input stream to read request data from
     **/
    InputStream getRequestInputStream();

    /**
     * Returns an <code>OutputStream</code> to write the response data
     * to.  The sequence of bytes written to the returned stream will
     * be the sequence of bytes sent as the response.
     *
     * <p>After the entirety of the response has been written to the
     * stream, the stream's <code>close</code> method must be invoked
     * to ensure complete delivery of the response.  It is possible
     * that none of the data written to the returned stream will be
     * delivered before <code>close</code> has been invoked (even if
     * the stream's <code>flush</code> method has been invoked at any
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
     * @return the output stream to write response data to
     **/
    OutputStream getResponseOutputStream();

    /**
     * Terminates this request, freeing all associated resources.
     *
     * <p>This method may be invoked at any stage of the processing of
     * the request.
     *
     * <p>After this method has been invoked, I/O operations on the
     * streams returned by the <code>getRequestInputStream</code> and
     * <code>getResponseOutputStream</code> methods will fail with an
     * <code>IOException</code>, except some operations that may
     * succeed because they only affect data in local I/O buffers.
     *
     * <p>If this method is invoked before the <code>close</code>
     * method has been invoked on the stream returned by
     * <code>getResponseOutputStream</code>, there is no guarantee
     * that any or none of the data written to the stream so far will
     * be delivered; the implication of such an invocation of this
     * method is that the user is no longer interested in the
     * successful delivery of the response.
     **/
    void abort();
}
