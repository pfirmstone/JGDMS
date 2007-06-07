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

import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.security.TrustVerifier;

/**
 * Defines a remote interface for obtaining a proxy trust verifier. A service
 * that uses proxies that will not directly be considered trusted by clients
 * can implement its remote object, or a bootstrap remote object, to support
 * this interface to allow clients to verify that the proxies can be trusted
 * as proxies for the service. The client typically configures the
 * {@link ProxyTrustVerifier} trust verifier for use with
 * {@link net.jini.security.Security#verifyObjectTrust
 * Security.verifyObjectTrust}; given a service proxy,
 * <code>ProxyTrustVerifier</code> obtains from it a bootstrap proxy (which
 * must be an instance of both <code>ProxyTrust</code> and
 * {@link net.jini.core.constraint.RemoteMethodControl}), and after verifying
 * that the bootstrap proxy (or a derivative) is trusted by the client, calls
 * the <code>getProxyVerifier</code> method of the bootstrap proxy (or
 * derivative) to obtain a verifier, and then uses that
 * verifier to determine if the original service proxy can be trusted. Other
 * trust verifiers may also make use of <code>ProxyTrust</code>.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface ProxyTrust extends Remote {

    /**
     * Returns a <code>TrustVerifier</code> which can be used to verify that
     * a proxy can be trusted as a proxy for the service; that is, the
     * {@link TrustVerifier#isTrustedObject isTrustedObject} method of the
     * returned verifier can be called with a candidate proxy. The verifier
     * should be able to verify all proxies for the service, including
     * proxies for resources (such as leases and registrations).
     *
     * @return a <code>TrustVerifier</code> which can be used to verify that
     * a proxy can be trusted as a proxy for the service
     * @throws RemoteException if a communication-related exception occurs
     */
    TrustVerifier getProxyVerifier() throws RemoteException;
}
