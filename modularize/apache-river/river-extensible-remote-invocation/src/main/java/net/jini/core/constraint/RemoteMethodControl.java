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

/**
 * Defines an interface to proxies for setting constraints to control remote
 * method calls. If a proxy implements this interface, then the semantics of
 * this interface apply to all calls through all remote methods defined by the
 * proxy; there is no mechanism to exempt remote methods or remote interfaces
 * from these semantics.
 * <p>
 * Constraints for a remote call come from two sources:
 * <ul>
 * <li>Constraints imposed by the server (including any minimum constraints
 * imposed by the communication mechanism used between the proxy and server)
 * <li>Constraints placed on a proxy by the client
 * </ul>
 * The server constraints are controlled by the proxy implementation; they are
 * not exposed to the client, and might vary in ways unknown to the client
 * (for example, vary by method or over time). The client should set the
 * constraints it wants rather than assuming that the server imposes
 * particular constraints. Client constraints placed on a proxy apply to all
 * remote calls made through that particular proxy by any thread, and the
 * client can specify different constraints for each remote method.
 * <p>
 * A remote call will be performed only if the combined requirements (from
 * both sources) can be satisfied. If the combined requirements cannot be
 * satisfied, a {@link java.rmi.ConnectIOException} will be thrown by the
 * remote call, typically containing (but not required to contain) a nested
 * {@link net.jini.io.UnsupportedConstraintException}.
 * In addition to the requirements, both client and server preferences will
 * be satisfied, to the extent possible.
 * <p>
 * Note that constraints imposed by the communication mechanism must be
 * factored into the requirements. For example, if the only explicit
 * requirement is <code>Delegation.YES</code>, but the communication
 * mechanism always requires client authentication, then effectively a
 * <code>ClientAuthentication.YES</code> requirement exists, and so the
 * <code>Delegation.YES</code> requirement must also be satisfied.
 * <p>
 * The constraint mechanisms are designed such that client constraints do not
 * weaken server constraints, and vice versa. However, it is certainly
 * possible to specify conflicting constraints. Preferences that conflict
 * with requirements are ignored, and if preferences conflict with each other
 * it is arbitrary as to which (if any) are satisfied, but if there are
 * conflicting requirements the remote call will not be made.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public interface RemoteMethodControl {
    /**
     * Returns a new copy of this proxy with the client constraints set to the
     * specified constraints. These constraints completely replace (in the
     * copy) any client constraints previously placed on this proxy; calling
     * the {@link #getConstraints getConstraints} method of the copy returns
     * the identical constraints instance. The original proxy is not modified.
     * A <code>null</code> value is interpreted as mapping all methods to
     * empty constraints (one that has no requirements and no preferences).
     * For any given remote call, the specific client requirements and
     * preferences to be satisfied are given by the return value of invoking
     * the {@link MethodConstraints#getConstraints getConstraints} method of
     * the specified {@link MethodConstraints} instance with a
     * {@link java.lang.reflect.Method} object representing the remote method.
     * <p>
     * Client constraints placed on a proxy are included in the serialized
     * state of the proxy. This allows third-party services to be transparent
     * to the client's needs. For example, if remote object <code>s1</code>
     * obtains a proxy for remote object <code>s2</code>, and passes that
     * proxy to remote object <code>s3</code>, expecting <code>s3</code> to
     * invoke a remote method on <code>s2</code>, then <code>s1</code> can
     * control that call by placing its constraints directly on the proxy
     * before passing it to <code>s3</code>. If <code>s3</code> does not
     * wish to be transparent in this way, then it should explicitly replace
     * the client constraints on received proxies with whatever constraints
     * are appropriate to implement its own policy.
     *
     * @param constraints client constraints, or <code>null</code>
     * @return a new copy of this proxy with the client constraints set to the
     * specified constraints
     * @see #getConstraints
     */
    RemoteMethodControl setConstraints(MethodConstraints constraints);

    /**
     * Returns the client constraints placed on this proxy. The return
     * value can be <code>null</code>, which is interpreted as mapping all
     * methods to empty constraints (one that has no requirements and no
     * preferences).
     *
     * @return the client constraints, or <code>null</code>
     * @see #setConstraints
     */
    MethodConstraints getConstraints();
}
