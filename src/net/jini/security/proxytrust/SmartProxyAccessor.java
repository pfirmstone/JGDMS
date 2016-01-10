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

import java.io.IOException;
import java.rmi.Remote;

/**
 * Defines a remote interface for obtaining a smart proxy. A service
 * that uses proxies that will not directly be considered trusted by clients
 * can implement its remote object, or a bootstrap remote object, to support
 * this interface to allow clients to authenticate the service prior to 
 * un-marshaling smart proxies. The client typically configures the
 * {@link ProxyTrustVerifier} trust verifier for use with
 * {@link net.jini.security.Security#verifyObjectTrust
 * Security.verifyObjectTrust}; given a bootstrap proxy (which
 * must be an instance of <code>SmartProxyAccessor</code>, <code>ProxyTrust</code> and
 * {@link net.jini.core.constraint.RemoteMethodControl})and after verifying
 * that the bootstrap proxy (or a derivative) is trusted by the client, 
 * obtains a smart proxy from it and then
 * calls the <code>getProxyVerifier</code> method of the bootstrap proxy (or
 * derivative) to obtain a verifier, and then uses that
 * verifier to determine if the un-marshaled service proxy can be trusted. 
 * 
 * A bootstrap proxy {@link java.lang.reflect.Proxy} implementing this interface
 * allows clients to authenticate 3rd party services registered with a lookup
 * service, prior to un-marshaling and Smart Proxy Trust Verification.  
 * The client and service provider trust the lookup service, but may be 
 * unknown to each other.
 * 
 * {@link net.jini.loader.DownloadPermission}
 * should be granted to the bootstrap proxy after authentication and
 * prior to obtaining the smart proxy, and prior to obtaining a TrustVerifier
 * to verify the smart proxy with.
 * 
 * @author Peter Firmstone
 */
public interface SmartProxyAccessor extends Remote {

    /**
     * 
     * @return the smart proxy.
     * @throws IOException
     */
    Object getSmartProxy() throws IOException;
    
}
