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

package net.jini.security.proxytrust;

import java.rmi.RemoteException;
import net.jini.security.TrustVerifier;

/**
 * Defines a local interface to obtain a proxy trust verifier. A service that
 * uses proxies that will not directly be considered trusted by clients
 * can export a remote object that is an instance of this
 * interface to allow clients to verify that the proxies can be trusted as
 * proxies for the service. The intention is that a remote call to the
 * {@link ProxyTrust#getProxyVerifier ProxyTrust.getProxyVerifier} method of
 * a trusted bootstrap proxy will be implemented (on the server side) by
 * delegating to the corresponding method of this local interface.
 * {@link ProxyTrustExporter} is one example of this form of delegation.
 * {@link net.jini.jeri.ProxyTrustILFactory} is another example.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@Deprecated
public interface ServerProxyTrust {

    /**
     * Returns a <code>TrustVerifier</code> that can be used to verify that
     * a proxy can be trusted as a proxy for the service; that is, the
     * {@link TrustVerifier#isTrustedObject isTrustedObject} method of the
     * returned verifier can be called with a candidate proxy. The verifier
     * should be able to verify all proxies for the service, including
     * proxies for resources (such as leases and registrations).
     *
     * @return a <code>TrustVerifier</code> that can be used to verify that
     * a proxy can be trusted as a proxy for the service
     * @throws RemoteException if a communication-related exception occurs
     * @throws UnsupportedOperationException if the server is not configured
     * for trust verification
     */
    TrustVerifier getProxyVerifier() throws RemoteException;
}
