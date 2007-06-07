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
import javax.security.auth.Subject;

/**
 * Represents one or more communication endpoints on the current
 * (local) host to listen for and receive requests on and a template
 * for producing an <code>Endpoint</code> instance to send requests to
 * those communication endpoints.
 *
 * <p>A <code>ServerEndpoint</code> instance contains the information
 * necessary to listen for requests on the communication endpoints and
 * to produce <code>Endpoint</code> instances.  For example, a
 * TCP-based <code>ServerEndpoint</code> implementation typically
 * contains the TCP port to listen on and the host address to put in
 * the <code>Endpoint</code> instances it produces.  An implementation
 * that supports authentication typically contains the {@link Subject}
 * (if any) to use for server authentication.
 *
 * <p>The {@link #enumerateListenEndpoints enumerateListenEndpoints}
 * method can be invoked to
 *
 * <ul>
 *
 * <li>discover each of the discrete communication endpoints
 * represented by this <code>ServerEndpoint</code>, which are
 * individually represented as {@link ListenEndpoint ListenEndpoint}
 * instances,
 *
 * <li>ensure an active <i>listen operation</i> (see {@link
 * ListenEndpoint#listen ListenEndpoint.listen}) on each discrete
 * communication endpoint by choosing, for each endpoint, either to
 * start a new listen operation or to use an existing one, and
 *
 * <li>obtain an <code>Endpoint</code> instance that corresponds to
 * the chosen listen operations
 *
 * </ul>
 *
 * The obtained <code>Endpoint</code> instance can then be used to
 * send requests to the communication endpoints being listened on as a
 * result of that {@link #enumerateListenEndpoints
 * enumerateListenEndpoints} invocation.
 *
 * <p>Typically, a <code>ServerEndpoint</code> is just used to specify
 * the transport layer implementation to use when exporting a remote
 * object; for example, some constructors of {@link BasicJeriExporter
 * BasicJeriExporter} have a <code>ServerEndpoint</code> parameter to
 * specify the transport layer implementation.  The exporter
 * implementation is then responsible for using the supplied
 * <code>ServerEndpoint</code> to manage listen operations as
 * necessary and to obtain an <code>Endpoint</code> instance for
 * putting in the client-side proxy.
 *
 * <p>All aspects of the underlying communication mechanism that are
 * not specified here are defined by the particular implementation of
 * this interface.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ServerEndpoint extends ServerCapabilities {

    /**
     * Enumerates the communication endpoints represented by this
     * <code>ServerEndpoint</code> by passing the
     * <code>ListenEndpoint</code> for each of them to
     * <code>listenContext</code>, which will ensure an active listen
     * operation on each endpoint, and returns an
     * <code>Endpoint</code> instance corresponding to the listen
     * operations chosen by <code>listenContext</code>.
     *
     * <p>This method uses <code>listenContext</code> to cooperate
     * with the caller to ensure the appropriate listen operations.
     * In this cooperation,
     *
     * <ul>
     *
     * <li>the <code>ServerEndpoint</code> is responsible for
     * declaring to the caller the discrete communication endpoints
     * represented by this <code>ServerEndpoint</code> as
     * <code>ListenEndpoint</code> instances passed to
     * <code>listenContext</code>'s {@link
     * ListenContext#addListenEndpoint addListenEndpoint} method, and
     *
     * <li>the caller is responsible for declaring the active listen
     * operations to be used for this
     * <code>ServerEndpoint.enumerateListenEndpoints</code> invocation
     * corresponding to each discrete communication endpoint as {@link
     * ListenCookie ListenCookie} instances returned from
     * <code>listenContext</code>'s <code>addListenEndpoint</code>
     * method.
     *
     * </ul>
     *
     * For each <code>ListenEndpoint</code>, the caller (through
     * <code>listenContext</code>) may choose to start a new listen
     * operation, or it may choose to reuse a previously started
     * listen operation that it has a <code>ListenCookie</code> for.
     *
     * <p>This method sequentially invokes
     * <code>addListenEndpoint</code> on <code>listenContext</code>
     * once for each discrete communication endpoint represented by
     * this <code>ServerEndpoint</code>, passing the
     * <code>ListenEndpoint</code> representing that communication
     * endpoint.  If any of the invocations of
     * <code>ListenContext.addListenEndpoint</code> throws an
     * exception, this method throws that exception.  Otherwise, this
     * method returns an <code>Endpoint</code> instance that sends
     * requests to be received by the listen operations chosen by
     * <code>listenContext</code>.
     *
     * @param listenContext the <code>ListenContext</code> to pass
     * this <code>ServerEndpoint</code>'s <code>ListenEndpoint</code>
     * instances to
     *
     * @return the <code>Endpoint</code> instance for sending requests
     * to this <code>ServerEndpoint</code>'s communication endpoints
     * being listened on
     *
     * @throws IOException if an I/O exception occurs while attempting
     * to listen for requests on the communication endpoints
     * represented by this <code>ServerEndpoint</code>.  This could
     * occur, for example, if an I/O resource associated with one of
     * the communication endpoints is already in exclusive use, or if
     * there are insufficient I/O resources for the operation.
     *
     * @throws SecurityException if the current security context does
     * not have the permissions necessary to listen for requests on
     * one of the communication endpoints represented by this
     * <code>ServerEndpoint</code>
     *
     * @throws IllegalArgumentException if an invocation of the
     * <code>addListenEndpoint</code> method on the supplied
     * <code>ListenContext</code> returns a <code>ListenCookie</code>
     * that does not correspond to the <code>ListenEndpoint</code>
     * that was passed to it
     *
     * @throws NullPointerException if <code>listenContext</code> is
     * <code>null</code>
     **/
    Endpoint enumerateListenEndpoints(ListenContext listenContext)
	throws IOException;

    /**
     * A callback object for passing to {@link
     * ServerEndpoint#enumerateListenEndpoints
     * ServerEndpoint.enumerateListenEndpoints} to receive the
     * enumerated {@link ServerEndpoint.ListenEndpoint ListenEndpoint}
     * instances and to choose an active listen operation for each of
     * them on behalf of the caller of
     * <code>enumerateListenEndpoints</code>.
     **/
    interface ListenContext {

	/**
	 * Adds <code>listenEndpoint</code> to this
	 * <code>ListenContext</code>'s collection of
	 * <code>ListenEndpoint</code> instances for the
	 * <code>ServerEndpoint</code> it was passed to, starts a
	 * listen operation on <code>listenEndpoint</code> if
	 * necessary, and returns the <code>ListenCookie</code> for an
	 * active listen operation on <code>listenEndpoint</code>.
	 *
	 * <p>The returned <code>ListenCookie</code> must have been
	 * obtained from a <code>ListenHandle</code> returned from
	 * some invocation of {@link
	 * ServerEndpoint.ListenEndpoint#listen ListenEndpoint.listen}
	 * on a <code>ListenEndpoint</code> equivalent to
	 * <code>listenEndpoint</code> by {@link Object#equals
	 * Object.equals}.
	 *
	 * <p>This method may start a new listen operation on
	 * <code>listenEndpoint</code> by invoking its
	 * <code>listen</code> method and returning the
	 * <code>ListenCookie</code> from the resulting
	 * <code>ListenHandle</code>, or it may return a
	 * <code>ListenCookie</code> for a listen operation previously
	 * started (but still active) on an equivalent
	 * <code>ListenEndpoint</code>.  If this method does invoke
	 * <code>listen</code> on <code>listenEndpoint</code> and it
	 * throws an exception, then this method throws that
	 * exception.
	 *
	 * <p>The implementation of this method may invoke {@link
	 * ServerEndpoint.ListenEndpoint#checkPermissions
	 * checkPermissions} on <code>listenEndpoint</code> to verify
	 * that a party that it is operating on behalf of has all of
	 * the security permissions necessary to listen for requests
	 * on <code>listenEndpoint</code>.
	 *
	 * @param listenEndpoint the <code>ListenEndpoint</code> to
	 * add to this <code>ListenContext</code> and to return a
	 * <code>ListenCookie</code> for
	 *
	 * @return a <code>ListenCookie</code> that represents an
	 * active listen operation on <code>listenEndpoint</code>
	 *
	 * @throws IOException if an invocation of <code>listen</code>
	 * on <code>listenEndpoint</code> throws an
	 * <code>IOException</code>
	 *
	 * @throws SecurityException if an invocation of
	 * <code>checkPermissions</code> or <code>listen</code> on
	 * <code>listenEndpoint</code> throws a
	 * <code>SecurityException</code>
	 *
	 * @throws IllegalStateException if this method is invoked
	 * unexpectedly, such as before being passed to
	 * <code>ServerEndpoint.enumerateListenEndpoints</code> or
	 * after the invocation of
	 * <code>ServerEndpoint.enumerateListenEndpoints</code> that
	 * it was created for has returned
	 *
	 * @throws NullPointerException if <code>listenEndpoint</code>
	 * is <code>null</code>
	 **/
	ListenCookie addListenEndpoint(ListenEndpoint listenEndpoint)
	    throws IOException;
    }

    /**
     * Represents a communication endpoint on the current (local) host
     * to listen for and receive requests on.
     *
     * <p>A <code>ListenEndpoint</code> instance contains the
     * information necessary to listen for requests on the
     * communication endpoint.  For example, a TCP-based
     * <code>ListenEndpoint</code> implementation typically contains
     * the TCP port to listen on.  An implementation that supports
     * authentication typically contains the {@link Subject} (if any)
     * to use for server authentication.
     *
     * <p>The {@link #listen listen} method can be used to start a
     * <i>listen operation</i> on the communication endpoint that it
     * represents, during which requests received on the endpoint will
     * be dispatched to a supplied {@link RequestDispatcher}.
     *
     * <p><code>ListenEndpoint</code> instances make up the discrete
     * communication endpoints represented by a
     * <code>ServerEndpoint</code>, and they are passed to a {@link
     * ServerEndpoint.ListenContext ListenContext} as part of a {@link
     * ServerEndpoint#enumerateListenEndpoints
     * ServerEndpoint.enumerateListenEndpoints} invocation.
     *
     * <p>An instance of this interface should implement {@link
     * Object#equals Object.equals} to return <code>true</code> if and
     * only if the argument is equivalent to the instance in trust,
     * content, and function.  The <code>equals</code> method should
     * not invoke comparison methods (such as <code>equals</code>) on
     * any pluggable component without first verifying that the
     * component's implementation is at least as trusted as the
     * implementation of the corresponding component in the
     * <code>equals</code> argument (such as by verifying that the
     * corresponding component objects have the same actual class).
     * If any such verification fails, the <code>equals</code> method
     * should return <code>false</code> without invoking a comparison
     * method on the component.  Furthermore, these guidelines should
     * be recursively obeyed by the comparison methods of each such
     * component for its subcomponents.  To avoid opening a security
     * hole, implementations should only compare object identity
     * (<code>==</code>) of <code>Subject</code> instances, rather
     * than comparing their contents.
     *
     * <p>The equivalence relation of a <code>ListenEndpoint</code>
     * should not be a function of any state in an associated
     * <code>ServerEndpoint</code> that only applies to the
     * <code>ServerEndpoint</code>'s template for producing
     * <code>Endpoint</code> instances.
     **/
    interface ListenEndpoint {

	/**
	 * Verifies that the current security context has all of the
	 * security permissions necessary to listen for requests on
	 * this <code>ListenEndpoint</code>.
	 *
	 * <p>This method should be used when an invocation of
	 * <code>ListenEndpoint.listen</code> is used to receive
	 * requests for a variety of interested parties, each with
	 * potentially different security permissions possibly more
	 * limited than those granted to the code managing the shared
	 * endpoint, so that the managing code can enforce proper
	 * access control for each party.
	 *
	 * @throws SecurityException if the current security context
	 * does not have the permissions necessary to listen for
	 * requests on this <code>ListenEndpoint</code>
	 *
	 * @see InboundRequest#checkPermissions
	 **/
	void checkPermissions();

	/**
	 * Listens for requests received on the communication endpoint
	 * represented by this <code>ListenEndpoint</code>,
	 * dispatching them to the supplied
	 * <code>RequestDispatcher</code> in the form of {@link
	 * InboundRequest} instances.
	 *
	 * <p>This method starts a continuing <i>listen operation</i>
	 * and then immediately returns a <code>ListenHandle</code>
	 * that represents the listen operation that was started.  For
	 * the duration of the listen operation, all requests received
	 * on the communication endpoint will be dispatched to the
	 * supplied <code>RequestDispatcher</code> as
	 * <code>InboundRequest</code> instances.  The returned
	 * <code>ListenHandle</code> can be used to stop the listen
	 * operation and to obtain a {@link
	 * ServerEndpoint.ListenCookie ListenCookie} to identify the
	 * listen operation as the return value of the {@link
	 * ServerEndpoint.ListenContext#addListenEndpoint
	 * ListenContext.addListenEndpoint} method.
	 *
	 * <p>Typically, this method is invoked by a
	 * <code>ListenContext</code> implementation when its
	 * <code>addListenEndpoint</code> method is called as part of
	 * the execution of a
	 * <code>ServerEndpoint.enumerateListenEndpoints</code>
	 * invocation.  The <code>Endpoint</code> instance that can be
	 * used to send requests to the communication endpoints
	 * represented by the <code>ServerEndpoint</code> (including
	 * this <code>ListenEndpoint</code>) is produced by the
	 * <code>ServerEndpoint</code> implementation given, in part,
	 * the <code>ListenCookie</code> obtained from the
	 * <code>ListenHandle</code> returned by this method.
	 *
	 * <p>This method verifies that the current security context
	 * has all of the security permissions necessary to listen for
	 * requests on this <code>ListenEndpoint</code>, as
	 * appropriate to the implementation of this interface (see
	 * {@link #checkPermissions}).  Note that in addition to this
	 * security check, the implementation of this interface may
	 * also perform a further security check per request received
	 * (when the origin of a given request is known), to verify
	 * that the same security context also has all of the
	 * permissions necessary to receive requests from that
	 * particular origin (see {@link
	 * InboundRequest#checkPermissions}).  This interface does not
	 * provide an API for determining when such a security check
	 * has failed; the behavior will be as if the associated
	 * request never occurred.
	 *
	 * <p>Implementations of this method should provide robust
	 * behavior (such as continuing to listen for requests) in the
	 * event that the supplied <code>RequestDispatcher</code>'s
	 * <code>dispatch</code> method throws an unchecked exception.
	 *
	 * <p>Requests may be dispatched in separate threads as
	 * necessary for requests received on this
	 * <code>ListenEndpoint</code> while others are still being
	 * processed by earlier <code>dispatch</code> invocations.
	 * The implementation of this interface must assume that there
	 * may be arbitrary execution dependencies between the
	 * processing of such concurrently received requests, and thus
	 * it must not attempt any particular serialization of their
	 * processing; therefore, a request received must be either
	 * dispatched or rejected within a reasonable period of time,
	 * rather than be queued indefinitely waiting for the return
	 * of earlier <code>dispatch</code> invocations.
	 *
	 * <p>Implementations of this method should generally dispatch
	 * a request in a daemon thread with a security context at
	 * least as restrictive as the one in which this method was
	 * invoked, and without holding visible synchronization locks.
	 *
	 * @param requestDispatcher the <code>RequestDispatcher</code>
	 * to use to dispatch incoming requests received on this
	 * communication endpoint
	 *
	 * @return a <code>ListenHandle</code> that represents the
	 * listen operation that was started
	 *
	 * @throws IOException if an I/O exception occurs while
	 * attempting to listen for requests on this
	 * <code>ListenEndpoint</code>.  This could occur, for
	 * example, if an I/O resource associated with this
	 * communication endpoint is already in exclusive use, or if
	 * there are insufficient I/O resources for the operation.
	 *
	 * @throws SecurityException if the current security context
	 * does not have the permissions necessary to listen for
	 * requests on this <code>ListenEndpoint</code>
	 *
	 * @throws NullPointerException if
	 * <code>requestDispatcher</code> is <code>null</code>
	 **/
	ListenHandle listen(RequestDispatcher requestDispatcher)
	    throws IOException;
    }

    /**
     * Represents a listen operation that has been started on a {@link
     * ServerEndpoint.ListenEndpoint ListenEndpoint}.
     *
     * <p>A <code>ListenHandle</code> is returned from a successful
     * <code>ListenEndpoint.listen</code> invocation to represent the
     * listen operation started by that invocation.  This object can
     * be used to stop the listen operation and to obtain a
     * <code>ListenCookie</code> to identify the listen operation as
     * the return value of the {@link
     * ServerEndpoint.ListenContext#addListenEndpoint
     * ListenContext.addListenEndpoint} method.
     **/
    interface ListenHandle {

	/**
	 * Stops listening for requests on the associated
	 * <code>ListenEndpoint</code>.
	 *
	 * <p>After this method has returned, no more requests will be
	 * dispatched to the <code>RequestDispatcher</code> for the
	 * listen operation represented by this
	 * <code>ListenHandle</code>, and the listen operation is no
	 * longer considered active.  This method frees any resources
	 * associated with the listen operation.
	 *
	 * <p>Invoking this method terminates any requests that have
	 * been received because of the listen operation and
	 * dispatched to the associated <code>RequestDispatcher</code>
	 * but have not yet had their response output stream closed
	 * (see {@link InboundRequest#abort InboundRequest.abort});
	 * subsequent I/O operations on such requests will fail with
	 * an <code>IOException</code>, except some operations that
	 * may succeed because they only affect data in local I/O
	 * buffers.
	 **/
	void close();

	/**
	 * Returns a <code>ListenCookie</code> to identify the listen
	 * operation as the return value of the {@link
	 * ServerEndpoint.ListenContext#addListenEndpoint
	 * ListenContext.addListenEndpoint} method.
	 *
	 * @return a <code>ListenCookie</code> to identify the listen
	 * operation
	 **/
	ListenCookie getCookie();
    }

    /**
     * A cookie to identify a listen operation as the return value of
     * the {@link ServerEndpoint.ListenContext#addListenEndpoint
     * ListenContext.addListenEndpoint} method.
     **/
    interface ListenCookie {
    }
}
