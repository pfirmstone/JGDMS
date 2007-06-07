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

package net.jini.core.constraint;

import java.io.Serializable;

/**
 * Represents a constraint on authentication of the client to the server.
 * <p>
 * Network authentication by a client (to a server) is scoped and controlled
 * by the client's {@link javax.security.auth.Subject}. The client's subject
 * is the current subject associated with the thread making the remote call.
 * The subject for a thread normally is set using
 * {@link javax.security.auth.Subject#doAs Subject.doAs}, and is retrieved
 * from a thread by calling {@link javax.security.auth.Subject#getSubject
 * Subject.getSubject} with the thread's current access control context (given
 * by calling {@link java.security.AccessController#getContext
 * AccessController.getContext}).
 * <p>
 * A client can only authenticate itself in a remote call as some subset of
 * the principals in its <code>Subject</code>, and only if that subject
 * contains the necessary public and/or private credentials required for the
 * authentication mechanism used by the proxy and server implementations.
 * However, additional principals and credentials might be derived as a result
 * of authentication. A client generally must have permission (such as
 * {@link net.jini.security.AuthenticationPermission}) to authenticate itself
 * in a remote call.
 * <p>
 * In the server, the result of authenticating the client typically is
 * represented by a subject containing the subset of authenticated client
 * principals plus any derived principals, and the public credentials used
 * during authentication plus any derived public credentials. This subject
 * typically is used by the server for authorization (access control)
 * decisions; in particular, it is used to decide if the client is permitted
 * to make the remote call. This subject normally does not contain any private
 * credentials, and so cannot be used for authentication in further remote
 * calls, unless {@link Delegation} is used.
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientMaxPrincipal
 * @see ClientMaxPrincipalType
 * @see ClientMinPrincipal
 * @see ClientMinPrincipalType
 * @see Delegation
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
public final class ClientAuthentication
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = -6326974440670504555L;

    /**
     * Authenticate the client to the server. The mechanisms and credentials
     * used to authenticate the client are not specified by this constraint.
     */
    public static final ClientAuthentication YES =
					new ClientAuthentication(true);
    /**
     * Do not authenticate the client to the server, so that the client
     * remains anonymous.
     */
    public static final ClientAuthentication NO =
					new ClientAuthentication(false);

    /**
     * <code>true</code> for <code>YES</code>, <code>false</code> for
     * <code>NO</code>
     *
     * @serial
     */
    private final boolean val;

    /**
     * Simple constructor.
     *
     * @param val <code>true</code> for <code>YES</code>, <code>false</code>
     * for <code>NO</code>
     */
    private ClientAuthentication(boolean val) {
	this.val = val;
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return val ? "ClientAuthentication.YES" : "ClientAuthentication.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     */
    private Object readResolve() {
	return val ? YES : NO;
    }
}
