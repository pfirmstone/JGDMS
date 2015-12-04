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
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import net.jini.core.constraint.InvocationConstraints;

/**
 * References a remote object at a remote communication endpoint to
 * send requests to.
 *
 * <p>An <code>ObjectEndpoint</code> instance contains the information
 * necessary to identify the remote object and to send requests to the
 * remote object.
 *
 * <p>The {@link #newCall newCall} method can be used to send a
 * request to the remote object that this object references.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ObjectEndpoint {

    /**
     * Returns an <code>OutboundRequestIterator</code> to use to send
     * a new remote call to the referenced remote object using the
     * specified constraints.
     *
     * <p>The constraints must be the complete, absolute constraints
     * for the remote call, combining any client and server
     * constraints for the remote method being invoked, with no
     * relative time constraints.
     *
     * <p>For each {@link OutboundRequest} produced by the returned
     * <code>OutboundRequestIterator</code>, after writing the request
     * data and before reading any response data, {@link #executeCall
     * executeCall} must be invoked to execute the call.
     *
     * @param constraints the complete, absolute constraints
     *
     * @return an <code>OutboundRequestIterator</code> to use to send
     * a new remote call to the referenced remote object
     *
     * @throws NullPointerException if <code>constraints</code> is
     * <code>null</code>
     **/
    public OutboundRequestIterator newCall(InvocationConstraints constraints);

    /**
     * Synchronously executes a remote call in progress to the
     * identified remote object, so that the response can be read.
     *
     * <p>This method should be passed an <code>OutboundRequest</code>
     * that was produced by an <code>OutboundRequestIterator</code>
     * returned from this object's {@link #newCall newCall} method.
     * This method must be invoked after writing the request data to
     * and before reading any response data from the
     * <code>OutboundRequest</code>.
     *
     * <p>If the remote call was successfully executed (such that the
     * response data may now be read) this method returns
     * <code>null</code>.  This method returns a non-<code>null</code>
     * <code>RemoteException</code> to indicate a
     * <code>RemoteException</code> that the remote call should fail
     * with.  For example, if the referenced object does not exist at
     * the remote endpoint, a {@link NoSuchObjectException} will be
     * returned.  This method throws an <code>IOException</code> for
     * other communication failures.
     *
     * @param call the remote call to execute, produced by an
     * <code>OutboundRequestIterator</code> that was returned from
     * <code>newCall</code>
     *
     * @return <code>null</code> on success, or a
     * <code>RemoteException</code> if the remote call should fail
     * with that <code>RemoteException</code>
     *
     * @throws IOException if an I/O exception occurs while performing
     * this operation
     *
     * @throws NullPointerException if <code>call</code> is
     * <code>null</code>
     **/
    public RemoteException executeCall(OutboundRequest call)
	throws IOException;
}
