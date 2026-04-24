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

package net.jini.lookup;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * JERI (Jini Extensible Remote Invocation) service interface for hosting
 * proxies in an isolated JVM.  Implementations of this interface accept a
 * proxy in marshalled form, instantiate it inside a per-codebase isolated
 * {@link ClassLoader}, wrap it with a JERI remote reflection proxy, and
 * return a remote reference that the client can use to invoke the proxy
 * exclusively over JERI — preventing direct local bytecode execution.
 *
 * <p>This interface is exported via JERI and is intended to be deployed as
 * an activatable service (see {@code ProxyHostActivatable}).  Clients
 * obtain a reference to a {@code RemoteProxyHost} through configuration and
 * pass it to a {@link ProxyIsolationFilter}.</p>
 *
 * <p>Thread safety: implementations must be safe for concurrent use by
 * multiple threads.</p>
 *
 * @see ProxyIsolationFilter
 * @since 3.1
 */
public interface RemoteProxyHost extends Remote {

    /**
     * Instantiates the proxy contained in {@code marshalledProxy} inside
     * an isolated {@link ClassLoader} within this JVM, wraps it with a JERI
     * remote invocation handler so that all method calls are dispatched over
     * the network, and returns the resulting JERI proxy stub to the caller.
     *
     * <p>The caller is responsible for ensuring that the bytes were obtained
     * from a trusted source.  The implementation should enforce whatever
     * security policies are appropriate for the hosting environment.</p>
     *
     * @param marshalledProxy the proxy to isolate, serialised to a byte
     *        array via {@link net.jini.io.MarshalledInstance} (or an
     *        equivalent marshalled form).  Must not be {@code null}.
     * @param codebase        the space-separated list of URL strings from
     *        which the proxy's class definitions may be downloaded.  May be
     *        {@code null} if no remote class loading is required.
     * @return a JERI proxy stub for the isolated proxy; all method
     *         invocations on the returned object are forwarded to the proxy
     *         running inside this host JVM over JERI.  The returned object
     *         implements all the same service interfaces as the original
     *         proxy.  Never {@code null}.
     * @throws RemoteException if a communication failure prevents the
     *         operation from completing (indefinite failure — the caller
     *         may retry later).
     * @throws ClassNotFoundException if the proxy's class cannot be loaded
     *         from the provided {@code codebase} (definite failure for this
     *         codebase).
     */
    Object isolateProxy(byte[] marshalledProxy, String codebase)
            throws RemoteException, ClassNotFoundException;
}
